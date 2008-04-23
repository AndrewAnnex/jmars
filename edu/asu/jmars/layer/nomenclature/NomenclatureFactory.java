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


package edu.asu.jmars.layer.nomenclature;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.util.*;

import javax.swing.*;
public class NomenclatureFactory extends LViewFactory
 {
	private static DebugLog log = DebugLog.instance();

        public static class NomenclatureParameterBlock extends DialogParameterBlock
        {


        }


	// Implement the default factory entry point
	public Layer.LView createLView()
        {
            //do this for now.
	    return null;

	}

	// Implement the main factory entry point.
	public void createLView(Callback callback)
	 {

                // Create a default set of parameters
                    NomenclatureParameterBlock pars =
                            new NomenclatureParameterBlock();

		// Return to the callback a view based on those parameters
		callback.receiveNewLView(realCreateLView());
	 }

	// Internal utility method
	private Layer.LView realCreateLView()
	 {
		// Create a BackLView
		Layer.LView view = new NomenclatureLView(null);
                view.originatingFactory = this;
		view.setVisible(true);

		return  view;
	 }

        //used to restore a view from a save state
        public Layer.LView recreateLView(SerializedParameters parmBlock)
        {
            return realCreateLView();
        }

	// Supply the proper name and description.
	public String getName()
	 {
		return ("Nomenclature");
	 }
	public String getDesc()
	 {
		return("A layer which provides Mars geographic nomenclature");
	 }
 }
