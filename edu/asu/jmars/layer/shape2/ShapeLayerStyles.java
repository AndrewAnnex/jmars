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


package edu.asu.jmars.layer.shape2;

import java.awt.Color;

import edu.asu.jmars.layer.util.features.Style;
import edu.asu.jmars.layer.util.features.Styles;
import edu.asu.jmars.util.LineType;

public class ShapeLayerStyles extends Styles {
	public Style<Color> selLineColor;
	public Style<Number> selLineWidth;
	
	public ShapeLayerStyles() {
		super();
		showLabels.setConstant(true);
		fillPolygons.setConstant(true);
		showVertices.setConstant(false);
		lineColor.setConstant(Color.white);
		fillColor.setConstant(Color.red);
		labelColor.setConstant(Color.white);
		showLineDir.setConstant(false);
		lineDash.setConstant(new LineType());
		lineWidth.setConstant(1);
		pointSize.setConstant(3);
		selLineColor = new Style<Color>("Selected Line Color", Color.yellow);
		selLineWidth = new Style<Number>("Selected Line Width", 3);
	}
	
	/** copy constructor */
	public ShapeLayerStyles(ShapeLayerStyles styles) {
		super(styles);
	}
}

