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

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

import edu.asu.jmars.util.LineType;

/**
 * Provides all styles used for rendering features; styles have a global
 * value, or are retrieved from a feature one at a time. The field to
 * retrieve, and a default to use when the field is not defined in the
 * feature's attribute map, must be supplied in the latter case.
 * 
 * Styles subclasses and all style properties on them must be public
 * final.  The public requirement is hard and fast; not making them
 * public will cause problems in some parts of the rendering system.
 * The final requirement is simply best practice due to how they are
 * used; the compiler will be able to optimize final fields better.
 */
public class Styles {
	public Style<Boolean> antialias = new Style<Boolean>("Antialias", false);
	public Style<FPath> geometry = new Style<FPath>("Geometry", Field.FIELD_PATH, null);
	public Style<Number> pointSize = new Style<Number>("Point Size", Field.FIELD_POINT_SIZE, 3);
	public Style<Boolean> showVertices = new Style<Boolean>("Show Vertices", true);
	public Style<Number> vertexSize = new Style<Number>("Vertex Size", 3);
	
	public Style<Boolean> showLabels = new Style<Boolean>("Show Labels", true);
	public Style<String> labelText = new Style<String>("Label Text", Field.FIELD_LABEL, "");
	public Style<Color> labelColor = new Style<Color>("Label Color", Field.FIELD_LABEL_COLOR, Color.WHITE);
	
	public Style<Number> lineWidth = new Style<Number>("Line Width", Field.FIELD_LINE_WIDTH, 1);
	public Style<Color> lineColor = new Style<Color>("Line Color", Field.FIELD_DRAW_COLOR, Color.WHITE);
	public Style<LineType> lineDash = new Style<LineType>("Line Style", Field.FIELD_LINE_DASH, new LineType()); // solid
	public Style<Boolean> showLineDir = new Style<Boolean>("Show Line Direction", false);
	
	public Style<Boolean> fillPolygons = new Style<Boolean>("Fill Polygons", true);
	public Style<Color> fillColor = new Style<Color>("Fill Color", Field.FIELD_FILL_COLOR, Color.RED);
	
	public Styles() {}
	
	/** copy constructor, makes a shallow copy except for public Style fields which are cloned */
	public Styles(Styles styles) {
		try {
			for (java.lang.reflect.Field f: getClass().getFields()) {
				Object o = f.get(styles);
				if (o instanceof Style<?>) {
					f.set(this, new Style<Object>((Style)o));
				} else {
					f.set(this, o);
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Unable to create copy of styles object", e);
		}
	}
	
	/** Returns the public styles of this and all child classes as a set */
	public final Set<Style<? extends Object>> getStyles() {
		Set<Style<?>> styles = new LinkedHashSet<Style<?>>();
		for (java.lang.reflect.Field f: getClass().getFields()) {
			try {
				Object o = f.get(this);
				if (Style.class.isInstance(o)) {
					styles.add((Style<?>)o);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return styles;
	}
	
	/** Returns the fields from all public styles */
	public final Set<Field> getFields() {
		Set<Field> out = new LinkedHashSet<Field>();
		for (Style<?> s: getStyles()) {
			if (s.getSource() instanceof StyleFieldSource) {
				out.add(((StyleFieldSource<?>)s.getSource()).getField());
			}
		}
		return out;
	}
}

