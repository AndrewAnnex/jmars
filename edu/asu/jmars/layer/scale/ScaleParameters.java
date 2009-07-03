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

import java.awt.Color;
import java.awt.Font;

import edu.asu.jmars.layer.SerializedParameters;

public class ScaleParameters implements SerializedParameters {
	private static final long serialVersionUID = 1L;
	public int offsetX = -10;
	public int offsetY = -10;
	public boolean isMetric;
	public Color fontOutlineColor;
	public Color fontFillColor;
	public Color barColor;
	public Color tickColor;
	public int numberOfTicks;
	public int width;
	public Font labelFont;

	public ScaleParameters() {
		isMetric = true;
		fontOutlineColor = Color.black;
		fontFillColor = Color.white;
		barColor = Color.black;
		tickColor = Color.black;
		numberOfTicks = 10;
		width = 30;
		labelFont = new Font("Arial", Font.BOLD, 16);
	}
}
