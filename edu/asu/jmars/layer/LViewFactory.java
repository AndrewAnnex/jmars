// Copyright 2008, Arizona Board of Regents
// on behalf of Arizona State University
// 
// Prepared by the Mars Space Flight Facility, Arizona State University,
// Tempe, AZ.
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.


package edu.asu.jmars.layer;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;

/**
 ** Encapsulates a mechanism for creating new LView objects, and for
 ** internally managing the layers they're linked to. Every factory
 ** must implement, at a minimum, {@link #createLView()}. Factories
 ** requiring user interaction for parameters must implement {@link
 ** #createLView(LViewFactory.Callback)} as well. Finally, most
 ** factories will want to implement {@link #getName} and {@link
 ** #getDesc} to be friendly to the user.
 **
 ** <p>To "add a layer to application", ultimately, requires only a
 ** new LViewFactory be added to {@link #factoryList}. This is the
 ** sole entry point that the application uses to query and create
 ** layers for the user. Typically, an addition to this list will also
 ** involve the subclassing of {@link LViewFactory}, {@link Layer},
 ** and {@link Layer.LView} for the new layer type. Strictly speaking,
 ** however, the application's "perception" of what layers are
 ** available is dictated solely through the <code>factoryList</code>.
 **
 ** <p>Because creation of a new layer/view often requires user
 ** interaction with a dialog, the creation can be done
 ** asynchronously. Clients of LViewFactory can either create a
 ** "default" version of a view (kind of a kludge to facilitate
 ** getting useful data from the start of the application)
 ** synchronously by invoking {@link #createLView()}. Or they can
 ** invoke asynchronous creation with user interaction by using {@link
 ** #createLView(LViewFactory.Callback)}.
 **
 ** <p>The default superclass implementation of the callback
 ** asynchronous version actually just calls the synchronous version,
 ** so simple factories don't have to implement both methods if they
 ** don't want/need to.
 **
 ** <p>Clients of LViewFactory will generally not instantiate
 ** LViewFactory objects directly... instead, the static {@link
 ** #factoryList} member will be used to locate and use a factory
 **/
public abstract class LViewFactory
 {
	private static DebugLog log = DebugLog.instance();

	public LViewFactory()
	 {
		this(null, null);
	 }

	private String name;
	private String desc;
	protected LViewFactory(String name, String desc)
	 {
		this.name = name;
		this.desc = desc;
	 }

	/**
	 ** The main entry point for querying and using LViewFactory
	 ** objects. A global list of available LViewFactories that
	 ** clients can use to create LView objects.
	 **
	 ** <p>This list is immutable... any attempt to add or remove
	 ** elements will result in an
	 ** <code>UnsupportedOperationException</code> being thrown.
	 **
	 ** <p><i>Implementation note:</i> This list is populated from the
	 ** {@link Config config file}.
	 **/
	public static List factoryList;

	/**
	 ** The main entry point for querying and using LViewFactory
	 ** objects in LManager2, which utilizes a tree structure of
	 ** factories. A global list of available LViewFactories <b>and
	 ** MultiFactories</b> that clients can use to create LView
	 ** objects.
	 **
	 ** <p>This list is immutable... any attempt to add or remove
	 ** elements will result in an
	 ** <code>UnsupportedOperationException</code> being thrown.
	 **
	 ** <p><i>Implementation note:</i> This list is populated from the
	 ** {@link Config config file}.
	 **/
	public static List factoryList2;

	// Fills factoryList properly, mostly by checking the config file.
	static
	 {
		// Create the "real" lists of factories.
		List realList = new ArrayList();
		List realList2 = new ArrayList();

		// Add all the factories listed in the config file.
		for(int i=1; Config.get("factory."+i,null)!=null; i++)
		{
			String line = Config.get("factory."+i);
			StringTokenizer tok = new StringTokenizer(line);
			String name = line;
			if(line.equals(""))
				break;

			try
			{
				String pname = tok.nextToken();
				String cname = tok.nextToken().replace('.','$');
				name = pname + '.' + cname;
				Object factory = Class.forName(name).newInstance();
				realList2.add(factory);
				if(factory instanceof LViewFactory)
					realList.add(factory);
				else
					((MultiFactory) factory).addDescendantsTo(realList);
			}
			catch(NotAvailableError e)
			{
				// TODO: Rather than throwing an exception in a static block during class load, 
				// we should implement something cleaner, such as an abstract 'isAvailable' method 
				// that can be called to determine if the layer should be available to the user based
				// on whatever parameters that layer cares about.

				// Stay silent. If the offending factory wants an
				// error message, it prints the message itself.
			}
			catch(ClassNotFoundException e)
			{
				log.aprintln("UNKNOWN LAYER TYPE IN CONFIG FILE: " +
						i + "=" + name);
			}
			catch(Throwable e)
			{
				log.aprintln("Layer type " + i + "=" + name +
						" unavailable, due to " + e);
				log.aprintln(e);
			}
		}

		// Point the factoryLists to immutable proxies to the
		// realLists.
		factoryList = Collections.unmodifiableList(realList);
		factoryList2 = Collections.unmodifiableList(realList2);
	 }

	/**
	 ** Indicates an {@link LViewFactory} is unavailable due to some
	 ** reason. Generally this would be caused by a missing library or
	 ** something.
	 **/
	public static class NotAvailableError extends Error
	 {
		/**
		 ** Apparently the default constructor is protected if we
		 ** don't explicitly declare one.
		 **/
		public NotAvailableError()
		 {
		 }
	 }

	/**
	 ** A simple listener mechanism for clients of {@link
	 ** LViewFactory} to use to receive created LViews. Probably
	 ** most-usefully implemented as an anonymous inner local class
	 ** within the same stack frame of the client that invokes {@link
	 ** LViewFactory#createLView(LViewFactory.Callback)}.
	 **
	 ** @see LViewFactory
	 **/
	static public interface Callback
	 {
		/**
		 ** When the potentially-asynchronous {@link
		 ** LViewFactory#createLView(LViewFactory.Callback)} method is
		 ** invoked, it returns a new view by calling this method once
		 ** on the callback it was passed.
		 **
		 ** @param newLView the newly-constructed view created a the
		 ** factory.
		 **/
		public void receiveNewLView(Layer.LView newLView);
	 }

	/**
	 ** Clients should invoke this function to trigger the creation of
	 ** a new LView (potentially) with user interaction. The creation
	 ** may be asynchronous (because it may involve dialog boxes and
	 ** other graphic elements), thus the created LView is returned
	 ** via a callback mechanism.
	 **
	 ** <p>Note: the default implementation of this method simply
	 ** invokes the synchronous version of {@link #createLView()} and
	 ** returns the result to the callback. Thus, simple factories
	 ** need not implement this method if they will never need user
	 ** interaction.
	 **
	 ** @param callback The created LView will be passed as a
	 ** parameter to <code>callback.{@link Callback#receiveNewLView
	 ** receiveNewLView(newLView)}</code>.
	 **/
	public void createLView(Callback callback)
	 {
		Layer.LView view = createLView();
		if(view != null)
			callback.receiveNewLView(view);
	 }

	/**
	 * Used to populate the initial list of views on
	 * startup. Factories should implement this function to return an
	 * appropriate view with "default" parameters. Return null to
	 * prevent any view from being added to the initial list from
	 * this factory.
	 *
	 * @return the new LView, or null
	 */
	public abstract Layer.LView createLView();


	/**
	 * Used to start a session using a serialized parameter block. Typically
     * after a session restart.
     * 
     * @return the new LView, or null
	 **/
	public abstract Layer.LView recreateLView(SerializedParameters parmBlock);


	/**
	 * Iterates through the list of valid factory objects and returns the
	 * one of the same name.
	 *
	 * @return the new LViewFactory, or null
	 */
	static public LViewFactory getFactoryObject(String className ) {
		Iterator iterFactory = factoryList.iterator();

		while(iterFactory.hasNext()) {
			LViewFactory lvf = (LViewFactory) iterFactory.next();
			if ( lvf.getClass().getName().compareTo(className) == 0 )
				return lvf;
		}

		return null;
	}


	/**
	 ** Should be defined in implementing classes to be an appropriate
	 ** short name for this factory, acceptable for displaying in a
	 ** list box for a user to choose.
	 **
	 ** @return The default implementation simply returns the derived
	 ** class's name.
	 **/
	public String getName()
	 {
		return  name==null ? getClass().getName() : name;
	 }

	/**
	 ** Should be defined in implementing classes to be an appropriate
	 ** medium-sized description for this factory, acceptable for
	 ** displaying as a comment in a text area.
	 **
	 ** @return The default implementation simply returns the derived
	 ** class's name.
	 **/
	public String getDesc()
	 {
		return  desc==null ? getClass().getName() : desc;
	 }

	/**
	 ** The default implementation simply returns a menu item that
	 ** proxies to {@link #createLView(Callback)}. Sub-classes can
	 ** override this to create more sophisticated behavior (such as
	 ** returning an entire sub-menu).
	 **/
	protected JMenuItem[] createMenuItems(final Callback callback) {
		return new JMenuItem[]{new JMenuItem(new AbstractAction(getName()) {
			public void actionPerformed(ActionEvent e) {
				try {
					createLView(callback);
				}
				catch(Exception ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(null, ex.getMessage(),
							"Error creating \""+getName()+"\" layer.", JOptionPane.ERROR_MESSAGE);
				}
			}
		})};
	 }
 }
