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


package edu.asu.jmars.layer.util.features;

import java.awt.geom.*;
import java.awt.*;

import edu.asu.jmars.util.LineType;

/**
 * A name, type, and editable state.
 * The name, type, and editable are publically accessible fields, but they
 * are final and thus unmodifiable after a Field is instantiated.
 * Equality is defined as having the same name and type, so a Set of Field
 * instances can have multiple fields with the same name and different types.
 */
public class Field {
	/**
	 * All Feature instances should have a path and type.
	 */

	/**
	 * Spatial coordinates in the west-leading areo-centric coordinate system.
	 */
	public static final Field FIELD_PATH = new Field("path",GeneralPath.class,true);
	public static final Field FIELD_DRAW_COLOR = new Field("Line Color",java.awt.Color.class,true);
	public static final Field FIELD_FILL_COLOR = new Field("Fill Color",java.awt.Color.class,true);
	public static final Field FIELD_LABEL_COLOR = new Field("Label Color",Color.class,true);
	public static final Field FIELD_LINE_WIDTH = new Field("Line Width",Double.class,true);
	public static final Field FIELD_POINT_SIZE = new Field("Point size",Integer.class,true);
	public static final Field FIELD_LINE_DASH = new Field("Line Type",LineType.class,true);
	public static final Field FIELD_LINE_DIRECTED = new Field("Line Arrow",Boolean.class,true);
	public static final Field FIELD_FONT = new Field("font",Font.class,false);
	public static final Field FIELD_LABEL = new Field("Label",String.class,true);
	public static final Field FIELD_SELECTED = new Field("selected",Boolean.class,true);
	public static final Field FIELD_SHOW_LABEL = new Field("Show Labels",Boolean.class,true);
	public static final Field FIELD_FILL_POLYGON = new Field("Fill Poly",Boolean.class,true);
	public static final Field FIELD_FEATURE_TYPE = new Field("Feature", String.class, false);

	public static final Field[] DEFAULT_FIELDS = {
		FIELD_FEATURE_TYPE, FIELD_PATH, FIELD_SELECTED, FIELD_LABEL,
		FIELD_DRAW_COLOR, FIELD_FILL_COLOR, FIELD_LABEL_COLOR,
		FIELD_LINE_WIDTH, FIELD_LINE_DASH, FIELD_LINE_DIRECTED,
		FIELD_FILL_POLYGON, FIELD_SHOW_LABEL, FIELD_POINT_SIZE
	};

	/**
	 * The name of the field, usually what is displayed in a table header.
	 */
	public final String name;

	/**
	 * The type of the field. Note that the boxed primitives Integer, Double,
	 * etc must be used in place of real primitives, due to the limitations of
	 * Java 1.4.
	 */
	public final Class type;

	/**
	 * An editable Field usually represents real data in some Feature source,
	 * and an uneditable Field is normally a calculated value (like polygonal
	 * area). Note that editable refers to whether the user should be able to
	 * edit the value; The system will still be able to modify attributes of
	 * this Field.
	 */
	public final boolean editable;

	/**
	 * Create a new Field with the given name and type. The Field will be
	 * editable.
	 */
	public Field (String name, Class type) {
		this (name, type, true);
	}

	/**
	 * Create a new Field with the given name, type, and editable state.
	 */
	public Field (String name, Class type, boolean editable) {
		this.name = name;
		this.type = type;
		this.editable = editable;
	}

	/**
	 * Defines equality as the intersection of name and type equality.
	 */
	public boolean equals (Object o) {
		if (o == null || !(o instanceof Field))
			return false;

		Field f = (Field)o;
		return this.name.equals (f.name) && this.type.equals (f.type);
	}

	/**
	 * Defines hash value as sum of name and type hashes.
	 */
	public int hashCode () {
		return this.name.hashCode () + this.type.hashCode ();
	}
	
	
	/**
	 * Debugging version of toString() method.
	 */
	public String toString(){
		return name;
	}
}

