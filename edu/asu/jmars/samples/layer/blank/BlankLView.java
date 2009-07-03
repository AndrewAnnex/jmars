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


package edu.asu.jmars.samples.layer.blank;

import java.awt.geom.Rectangle2D;

import edu.asu.jmars.layer.Layer.LView;

public class BlankLView extends LView {
	public BlankLView(BlankLayer layer){
		super(layer);
	}
	
	protected LView _new() {
		// Create a copy of ourself for use in the panner-view.
		return new BlankLView((BlankLayer)getLayer());
	}

	protected Object createRequest(Rectangle2D where) {
		// Build a request object for the layer.
		// The layer will respond back with the data.
		return where;
	}

	public void receiveData(Object layerData) {
		// Process the data returned by the layer.
		// Including displaying the data to the screen.
		Rectangle2D r = (Rectangle2D)layerData;
		System.out.println("Layer returned: [x="+r.getMinX()+",y="+r.getMinY()+",w="+r.getWidth()+",h="+r.getHeight()+"]");
	}
	
	public String getName() {
		return "Blank";
	}

}
