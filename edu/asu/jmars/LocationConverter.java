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


package edu.asu.jmars;

import java.awt.geom.*;

/**
 ** Encapsulates the conversion for the LocationManager between JMARS'
 ** internal world coordinate system and another arbitrary system. In
 ** practice, this is used to implement the fake time mode, where time
 ** is the X axis.
 **/
public interface LocationConverter
 {
	/**
	 ** Returns the center point of the views, in the coordinate
	 ** system of this converter. Returns null if the coordinate
	 ** system doesn't provide a center.
	 **/
	public Point2D getCenter();

	/**
	 ** Sets the center point of the views, in the coordinate
	 ** system of this converter. Some converters don't allow
	 ** setting a center, in which case the call has no effect.
	 **
	 ** @return true if the center was successfully changed... false
	 ** otherwise.
	 **/
	public boolean setCenter(Point2D c);

	/**
	 ** Should return a short label for this converter, brief but
	 ** descriptive enough for the user to pick from the list of
	 ** available converters.
	 **/
	public String getConverterName();

	/**
	 ** Converts world coordinates to a user-editable string.
	 **/
	public String worldToText(Point2D worldPt);

	/**
	 ** Converts from user-entered text to world coordinates.
	 **/
	public Point2D textToWorld(String text);

	/**
	 ** Converts from user-entered text to world coordinates AND
	 ** updates the current projection "appropriately". The
	 ** returned world coordinate is valid for the NEW projection,
	 ** not the old one.
	 **
	 ** @return null if reprojection wasn't possible
	 **/
	public Point2D reprojectFromText(String rawText);
 }

