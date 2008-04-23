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
import java.awt.*;

class UserGridSettings extends LViewSettings
 {
	Integer   rgbcolor;
	Double  spacing;
	Boolean visible;

	UserGridSettings(Color c, double d, boolean b ) {
	   rgbcolor = new Integer(c.getRGB());
	   spacing = new Double(d);
	   visible = new Boolean(b);

	}
 }
