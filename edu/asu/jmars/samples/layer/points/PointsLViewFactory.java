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


package edu.asu.jmars.samples.layer.points;

import edu.asu.jmars.layer.LViewFactory;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.layer.Layer.LView;

public class PointsLViewFactory extends LViewFactory {

	public LView createLView() {
		return null;
	}

	public void createLView(Callback callback) {
		// Create LView with defaults
		PointsLView lview = new PointsLView(new PointsLayer());
		lview.originatingFactory = this;
		callback.receiveNewLView(lview);
	}

	public LView recreateLView(SerializedParameters parmBlock) {
		PointsLView lview = new PointsLView(new PointsLayer());
		lview.originatingFactory = this;
		return lview;
	}

	public String getDesc() {
		return "Points LView Description";
	}

	public String getName() {
		return "Points LView";
	}

}
