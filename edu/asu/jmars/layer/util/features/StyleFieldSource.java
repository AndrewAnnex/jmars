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

/**
 * Provides style values directly from attributes on the style, or from a
 * given default when the style attribute is not defined
 */
public final class StyleFieldSource<E extends Object> implements StyleSource<E> {
	private static final long serialVersionUID = 1L;
	private final Field f;
	private final E defaultValue;
	public StyleFieldSource(Field f, E defaultValue) {
		this.f = f;
		this.defaultValue = defaultValue;
	}
	public E getValue(Feature feature) {
		if (feature == null) {
			return defaultValue;
		} else {
			Object out = feature.getAttribute(f);
			if (out != null) {
				return (E)out;
			} else {
				return defaultValue;
			}
		}
	}
	public Field getField() {
		return f;
	}
}

