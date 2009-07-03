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

import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.Set;

import javax.swing.JPanel;

import edu.asu.jmars.util.HVector;

public class FieldLength extends CalculatedField {
	private static final long serialVersionUID = 1L;
	private static final Set<Field> fields = Collections.singleton(Field.FIELD_PATH);
	public FieldLength(String name) {
		super(name, Double.class);
	}
	public Set<Field> getFields() {
		return fields;
	}
	public Object getValue(Feature f) {
		Point2D[] points = f.getPath().getSpatialWest().getVertices();
		double km = 0;
		for (int i = 1; i < points.length; i++) {
			HVector a = HVector.intersectMars(HVector.ORIGIN, new HVector(points[i-1]));
			HVector b = HVector.intersectMars(HVector.ORIGIN, new HVector(points[i]));
			km += b.sub(a).norm();
		}
		return km;
	}
	public static class Factory extends FieldFactory<FieldLength> {
		public Factory(String name) {
			super(name, FieldLength.class, Double.class);
		}
		public JPanel createEditor(Field f) {
			return null;
		}
		public FieldLength createField(FeatureCollection fc, Field f) {
			return new FieldLength(getName());
		}
	}
}
