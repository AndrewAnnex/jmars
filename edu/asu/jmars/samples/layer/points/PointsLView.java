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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import edu.asu.jmars.layer.Layer.LView;

/**
 * One step up from BlankLView.
 * This LView shows four dots in the main/panner windows.
 * The points are retrieved from the Layer which stores them
 * in degrees of longitude and latitude.
 *
 */
public class PointsLView extends LView {
	public PointsLView(PointsLayer layer){
		super(layer);
	}
	
	protected LView _new() {
		// Create a copy of ourself for use in the panner-view.
		return new PointsLView((PointsLayer)getLayer());
	}

	protected Object createRequest(Rectangle2D where) {
		// Build a request object for the layer.
		// The layer will respond back with the data.
		return where;
	}

	public void receiveData(Object layerData) {
		// Process the data returned by the layer.
		// Including displaying the data to the screen.
		List<Point2D> pts = (List<Point2D>)layerData;
		drawData(pts);
	}
	
	private void drawData(List<Point2D> pts){
		if (isAlive()){
			clearOffScreen();
			Graphics2D g2 = getOffScreenSpatial();
			float pixelWidth = getProj().getPixelWidth();
			float pixelHeight = getProj().getPixelHeight();
			int sideLengthPixels = 5;

			g2.setColor(Color.yellow);
			g2.setStroke(new BasicStroke(0.0f));
			for(Point2D p: pts){
				g2.fill(new Rectangle2D.Double(p.getX()-(pixelWidth*sideLengthPixels)/2, p.getY() - (pixelHeight*sideLengthPixels)/2,
						pixelWidth*sideLengthPixels, pixelHeight*sideLengthPixels));
			}
			repaint();
		}
	}
	
	public String getName() {
		return "Points";
	}

}
