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
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

public class BackFactory extends LViewFactory
 {
	private static Map existingLayers = new HashMap();
	private static DebugLog log = DebugLog.instance();

	// Implement the default factory entry point
	public Layer.LView createLView()
	 {
		// Create a default set of parameters
		BDialogParameterBlock pars =
			new BackDialogParameterBlock();

		// Return a view created based on those parameters
		return  createLView(pars);
	 }





	// Implement the main factory entry point.
	public void createLView(Callback callback)
	 {
		BackPropertiesDiag bpd=new BackPropertiesDiag(Main.getLManager(),true);

                if (bpd.Canceled())
                  return;

		//Get the needed parameters
		BDialogParameterBlock pars = bpd.getParameters();

		log.println("pars.choice = "  +((BackDialogParameterBlock)pars).tileChoice);

		// Return to the callback a view based on those parameters
		callback.receiveNewLView(createLView(pars));
	 }


	// Internal utility method
	private Layer.LView createLView(BDialogParameterBlock pars)
	 {
		Layer layer;

		// Check if we already have a layer for the given parameters
		if(existingLayers.containsKey(pars))
			layer = (BackLayer) existingLayers.get(pars);
		else
		 {
			// Nope... better add one
			layer = new BackLayer(pars);
/*
			{
				public Agent new_Agent(BDialogParameterBlock bdpb,
											  Object agentRequest,
												AgentManager.Semephore agentCount)

						 {
							return (new BackAgent((BackDialogParameterBlock)bdpb,
														 agentRequest,agentCount));
						 }
			};
*/


			existingLayers.put(pars, layer);
		 }

		// Create a BackLView
		Layer.LView view = new BackLView(layer);
                view.originatingFactory = this;
		view.setVisible(true);

		return  view;
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
		return ("Graphical Map");
	 }
	public String getDesc()
	 {

		return("A background layer that gives context by rendering " +
			   "map data from Viking, TES, MOLA, THEMIS, etc....");
	 }

	BackPropertiesDiag bpd;

	protected JMenuItem[] createMenuItems(Callback callback)
	 {
		try
		 {
			BackPropertiesDiag.PRESELECT_CUSTOM = false;
			if(bpd == null)
				bpd = new BackPropertiesDiag(Main.mainFrame, false, false);
			return new JMenuItem[]{createMenuItemImpl(callback)};
		 }
		catch(Throwable e)
		 {
			// Just in case... we fall-back to the default behavior
			log.aprintln(e);
			log.aprintln("The map type tree-building failed from the above!");
			return super.createMenuItems(callback);
		 }
	 }

	private static final String[] GROUPINGS = Config.getArray("Gtiles.group");

	private JMenuItem createMenuItemImpl(final Callback callback)
	 {
		JMenu menu = new JMenu(getName());

		int currGroup = 0;
		JMenu[] groupMenus = new JMenu[GROUPINGS.length];
		for(int i=0; i<GROUPINGS.length; i++)
			menu.add(groupMenus[i] = new JMenu(GROUPINGS[i]));

		for(int i=0; i<bpd.masterItems.length; i++)
		 {
			final int ii = i;
			JMenu m;
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

			// First check if we're in a new group
			if(currGroup+1 < GROUPINGS.length  &&
			   title.startsWith(GROUPINGS[currGroup+1]))
			 {
				++currGroup;
				m = groupMenus[currGroup];
				title = title.substring(GROUPINGS[currGroup].length()+1);
			 }
			// Now check if we're still in the same group
			else if(currGroup < GROUPINGS.length  &&
					title.startsWith(GROUPINGS[currGroup]))
			 {
				m = groupMenus[currGroup];
				title = title.substring(GROUPINGS[currGroup].length()+1);
			 }
			// We're in the "default" group, the main menu
			else
				m = menu;

			m.add(
				new AbstractAction(title)
				 {
					public void actionPerformed(ActionEvent e)
					 {
						BackPropertiesDiag bpd = new BackPropertiesDiag(
							Main.mainFrame, false, false);
						bpd.cb.setSelectedIndex(ii);
						bpd.okButton.doClick();
						Layer.LView view = createLView(bpd.getParameters());

						if(view != null)
							callback.receiveNewLView(view);
					 }
				 }
				);
		 }

 		for(int i=0; i<GROUPINGS.length; i++)
 			if(groupMenus[i].getMenuComponentCount() == 0)
 				menu.remove(groupMenus[i]);

		BackPropertiesDiag.PRESELECT_CUSTOM = true;

		return  menu;
	 }
 }
