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

import java.util.Collections;
import java.util.Set;

import javax.swing.JPanel;

public class FieldLat extends CalculatedField {
	private static final long serialVersionUID = 1L;
	private static final Set<Field> fields = Collections.singleton(Field.FIELD_PATH);
	public FieldLat(String name) {
		super(name, Double.class);
	}
	public Set<Field> getFields() {
		return fields;
	}
	public Object getValue(Feature f) {
		return f.getPath().getSpatialEast().getCenter().getY();
	}
	public static class Factory extends FieldFactory<FieldLat> {
		public Factory(String name) {
			super(name, FieldLat.class, Double.class);
		}
		public JPanel createEditor(Field f) {
			return null;
		}
		public FieldLat createField(FeatureCollection fc, Field f) {
			return new FieldLat(getName());
		}
	}
}

