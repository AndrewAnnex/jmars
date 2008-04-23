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


package edu.asu.jmars.layer.threed;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.LViewFactory;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.layer.LViewFactory.NotAvailableError;

public class ThreeDFactory extends LViewFactory {
	
	{
		try {
			// Test for the availability of Java3D
			Class.forName("javax.media.j3d.TriangleStripArray", false, ClassLoader.getSystemClassLoader());
			// Test for the availability of the vector math libraries
			Class.forName("javax.vecmath.Vector3f", false, ClassLoader.getSystemClassLoader());
		} catch (Exception e) {
			throw  new NotAvailableError();
		}
	}
	
	
	private ThreeDSettings settings = new ThreeDSettings();

	public ThreeDFactory() {
	}

	/** Create a default 3D layer - currently the user must manually add this layer */
	public Layer.LView createLView() {
		return null;
	}
	
	// Supply the proper name and description.
	public String getName() {
		return "3D Layer";
	}
	
	public String getDesc() {
		return "A layer for rendering a scene in 3D";
	}
	
	/** Create a new 3D layer  - uses MOLA topographic elevation by default */
	public void createLView(Callback callback) {
		callback.receiveNewLView(createLView(new StartupParameters()));
	}
	
	/** Restore a 3D layer if parmBlock has the right type, otherwise creates a new default layer */
	public Layer.LView recreateLView(SerializedParameters parmBlock) {
		if (parmBlock instanceof StartupParameters) {
			//	return new LView with previously saved parms
			return createLView((StartupParameters)parmBlock);
		} else {
			// return new LView with default parms
			return createLView(new StartupParameters());
		}
	}
	
	/** Creates a 3D layer and view from the given startup parameters */
	private Layer.LView createLView(StartupParameters parms) {
		// create a new layer to be shared by all the views
		ThreeDLayer layer = new ThreeDLayer(parms);
		// create the main view
		ThreeDLView view = new ThreeDLView(layer, settings);
		// by default, the main view provides the viewing area
		layer.setActiveView(view);
		// silliness for session serializing
		view.originatingFactory = this;
		// cause an immediate view change
		view.setVisible(true);
		return view;
	}
}
