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


package edu.asu.jmars.layer.grid;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
public class GridFactory extends LViewFactory
 {
	public Layer.LView createLView()
	 {
		Layer.LView ret = new GridLView();
                ret.originatingFactory = this;
                return ret;
	 }

	//used to restore a view from a save state
	public Layer.LView recreateLView(SerializedParameters parmBlock)
	 {
		return createLView();
	 }

	public String getName()
	 {
		return  "Lat/Lon Grid";
	 }

	public String getDesc()
	 {
		return  "An adjustable grid of latitude/longitude lines.";
	 }
 }
