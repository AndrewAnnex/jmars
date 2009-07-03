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


package edu.asu.jmars.util;

import java.io.Serializable;

/**
 * A class for handling types of lines in a SortingTable.
 * This class holds a dashed-pattern. It came into being because
 * ESRI shape files have a line-type attribute in them. This
 * had to be encoded for both the SortingTable which can handle
 * LineType as an editable attribute as well as the Feature
 * object which needs it.
 * 
 * @see edu.asu.jmars.layer.util.features.Feature
 * 
 * @author James Winburn MSFF-ASU 4/06
 */
public class LineType implements Serializable {
	int patternId;

	/**
	 * Raw dash-patterns to create Stroke objects.
	 * @see java.awt.BasicStroke#BasicStroke(float, int, int, float, float[], float)
	 */
	private static final float[][] RAW_DASH_PATTERNS = new float[][]{
		null,
		{ 2, 2 },
		{ 6, 3 },
		{ 12, 3, 3, 3 },
	};
	
	private static final String[] DASH_PATTERN_NAMES = new String[] {
		"Solid",
		"Dotted",
		"Dashed",
		"Dash-dot"
	};
	
	/**
	 * Pattern id for the null pattern.
	 */
	public static final int PATTERN_ID_NULL     = 0;
	
	/**
	 * Pattern id for 2-on, 2-off pattern.
	 */
	public static final int PATTERN_ID_2_2      = 1;
	
	/**
	 * Pattern id for 6-on, 3-off pattern.
	 */
	public static final int PATTERN_ID_6_3      = 2;
	
	/**
	 * Pattern id for 12-on, 3-off, 3-on, 3-off pattern.
	 */
	public static final int PATTERN_ID_12_3_3_3 = 3;
	
	/**
	 * Default constructor. Creates a solid line pattern.
	 */
	public LineType() {
		patternId = PATTERN_ID_NULL;
	}
	
	/**
	 * Constructor which takes a pattern-id. Currently pattern-ids
	 * zero to three are defined. Out of range values are accepted
	 * with the resulting pattern defaulting to solid.
	 *  
	 * @param patternId One of the supported pattern-ids.
	 */
	public LineType(int patternId){
		this.patternId = patternId;
	}
	
	/**
	 * Constructor which interprets the specified String as an
	 * integer pattern-id.
	 * 
	 * @param patternIdString Textual representation of a pattern-id.
	 */
	public LineType(String patternIdString){
		this.patternId = Integer.parseInt(patternIdString);
	}
	
	/**
	 * Returns the pattern-id.
	 * 
	 * @return Pattern id.
	 */
	public int getType(){
		return patternId;
	}
	
	/**
	 * Returns the dash-pattern that this object represents.
	 * If the current pattern-id is out of range, {@link #PATTERN_ID_NULL}
	 * is assumed.
	 * 
	 * @return dash-pattern that this object represents.
	 */
	public float[] getDashPattern() {
		if (patternId < 0 || patternId >= RAW_DASH_PATTERNS.length)
			return null;
		
		return RAW_DASH_PATTERNS[patternId];
	}
	
	/**
	 * Return some format of textual representation for this object.
	 */
	public String toString(){
		return ""+patternId;
	}
}
