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


package edu.asu.jmars.samples.layer.addpoints;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.asu.jmars.layer.DataReceiver;
import edu.asu.jmars.layer.Layer;

public class AddPointsLayer extends Layer {
	List<Point2D> pts = new ArrayList<Point2D>();
	
	public AddPointsLayer(){
	}
	
	public void addPoint(Point2D newPoint){
		pts.add((Point2D)newPoint.clone());
		broadcast(Collections.unmodifiableList(pts));
	}
	
	public void receiveRequest(Object layerRequest, DataReceiver requester) {
		// Layer can filter the data such that it lives within the layerRequest boundary,
		// if it so chooses.
		broadcast(Collections.unmodifiableList(pts));
	}
}
