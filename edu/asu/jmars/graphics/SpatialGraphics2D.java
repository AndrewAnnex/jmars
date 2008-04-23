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


package edu.asu.jmars.graphics;

import edu.asu.jmars.*;
import edu.asu.jmars.util.*;
import java.awt.geom.*;

/**
 ** Superclass for all spatial-based graphics contexts. Basically just
 ** supplies an extra utility function for doing spatial to world
 ** conversions.
 **/
public abstract class SpatialGraphics2D
 extends Graphics2DAdapter
 {
	/**
	 ** Given a point in spatial coordinates, returns an array of
	 ** every occurrence of that point within the world-range of this
	 ** graphics context.
	 **/
	public abstract Point2D[] spatialToWorlds(Point2D s);
 }
