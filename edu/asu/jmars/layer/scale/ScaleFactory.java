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


package edu.asu.jmars.layer.scale;

import edu.asu.jmars.layer.LViewFactory;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.layer.Layer.LView;

public class ScaleFactory extends LViewFactory {
	public LView createLView() {
		return null;
	}

	public void createLView(Callback callback) {
		// Return to the callback a view based on those parameters
		callback.receiveNewLView(realCreateLView(new ScaleParameters()));
	}

	// Internal utility method
	private Layer.LView realCreateLView(ScaleParameters params) {
		// Create a BackLView
		ScaleLayer layer = new ScaleLayer();
		layer.initialLayerData = params;
		Layer.LView view = new ScaleLView(true, layer, params);
		view.originatingFactory = this;
		view.setVisible(true);

		return view;
	}

	public String getName() {
		return ("Map Scalebar");
	}

	public String getDesc() {
		return ("A layer which provides a scale marker on the map");
	}

	public LView recreateLView(SerializedParameters parmBlock) {
		if (parmBlock instanceof ScaleParameters) {
			return realCreateLView((ScaleParameters)parmBlock);
		} else {
			return realCreateLView(new ScaleParameters());
		}
	}
}
