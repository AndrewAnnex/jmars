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


package edu.asu.jmars.layer.map;

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;

public class NumBackFactory extends LViewFactory
 {
	private static ArrayList existingLayers = new ArrayList();
	private static DebugLog log = DebugLog.instance();

	// Implement the default factory entry point
	public Layer.LView createLView()
	 {
		return(null);
	 }

	// Implement the main factory entry point.
	public void createLView(Callback callback)
	 {
		NumBackPropertiesDiag nbpd=new NumBackPropertiesDiag(new JFrame(),true);

                if (nbpd.Canceled())
                  return;

		//Get the needed parameters
		BDialogParameterBlock pars = nbpd.getParameters();


		// Return to the callback a view based on those parameters
		callback.receiveNewLView(createLView(pars));
	 }

	static void deleteLayers()
	 {
		existingLayers.clear();
	 }

	// Internal utility method
	private Layer.LView createLView(BDialogParameterBlock pars)
	 {
		NumBackLayer layer;


		// Check if we already have a layer for the given parameters

		if (existingLayers.size() > 0) { // there already exists a layer...therefore ALL
				// further adds should be re-routed to the internal add for this layer
			layer = (NumBackLayer) existingLayers.get(0);

                        if (layer.myLView == null) { // Layer was "deleted" at some point
                                                     //We have to act as if we're creating from scratch
                                                     //Except we don't want to add a new Layer object
			   layer.myLView = new NumBackLView(layer,true);
	                   layer.myLView.originatingFactory = this;
			   layer.myLView.setVisible(true);

			   return (layer.myLView)  ;
                        }

			layer.myLView.addDataSet((NumBackDialogParameterBlock)pars);

		}

		else
		 {
			// Nope... better add one
			log.println("CREATING new NumBackLayer");
			layer = new NumBackLayer(pars);
			existingLayers.add(layer);

			// Create a BackLView
			layer.myLView = new NumBackLView(layer,true);
	                layer.myLView.originatingFactory = this;
			layer.myLView.setVisible(true);

			return (layer.myLView)  ;
		}

		return(null);

	 }

     //used to restore a view from a save state
     public Layer.LView recreateLView(SerializedParameters parmBlock)
     {
        if ( parmBlock == null )
          return createLView();

        return createLView((BDialogParameterBlock) parmBlock);
     }

	// Supply the proper name and description.
	public String getName()
	 {
		return ("Numerical Map");
	 }
	public String getDesc()
	 {

		return("A background layer that gives context by displaying " +
			   "the numbers from map data such as MOLA elevation, or non-image maps");
	 }

	NumBackPropertiesDiag bpd;

	protected JMenuItem[] createMenuItems(Callback callback)
	 {
		try
		 {
			NumBackPropertiesDiag.PRESELECT_CUSTOM = false;
			if(bpd == null)
				bpd = new NumBackPropertiesDiag(Main.mainFrame, false, false);
			return new JMenuItem[]{createMenuItemImpl(callback)};
		 }
		catch(Throwable e)
		 {
			// Just in case... we fall-back to the default behavior
			log.aprintln(e);
			log.aprintln("The map type tree-building failed from the above!");
			return  super.createMenuItems(callback);
		 }
	 }

	private JMenuItem createMenuItemImpl(final Callback callback)
	 {
		JMenu menu = new JMenu(getName());

		for(int i=0; i<bpd.masterItems.length; i++)
		 {
			final int ii = i;
			String title = bpd.masterItems[i];

			// Special case for generic "custom" map option
			if(title.equalsIgnoreCase("custom"))
			 {
				menu.add(
					new AbstractAction("Custom...")
					 {
						public void actionPerformed(ActionEvent e)
						 {
							createLView(callback);
							bpd = null;
							Main.testDriver.getLManager().refreshAddMenu();
						 }
					 }
					);
				continue;
			 }

			menu.add(
				new AbstractAction(title)
				 {
					public void actionPerformed(ActionEvent e)
					 {
						bpd.cb.setSelectedIndex(ii);
						bpd.okButton.doClick();
						Layer.LView view = createLView(bpd.getParameters());
						if(view != null)
							callback.receiveNewLView(view);
					 }
				 }
				);
		 }

		NumBackPropertiesDiag.PRESELECT_CUSTOM = true;

		return  menu;
	 }
 }
