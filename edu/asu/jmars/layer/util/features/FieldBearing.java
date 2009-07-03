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

import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.util.Collections;
import java.util.Set;

import javax.swing.JPanel;

public class FieldBearing extends CalculatedField {
	private static final long serialVersionUID = 1L;
	private static final Set<Field> fields = Collections.singleton(Field.FIELD_PATH);
	public FieldBearing(String name) {
		super(name, Double.class);
	}
	public Set<Field> getFields() {
		return fields;
	}
	public Object getValue(Feature f) {
		GeneralPath gp = f.getPath().getSpatialEast().getGeneralPath();
		float[] coords = new float[6];
		float[] a = new float[2];
		float[] b = new float[2];
		int i = 0;
		for (PathIterator pi = gp.getPathIterator(null); !pi.isDone(); pi.next()) {
			switch (pi.currentSegment(coords)) {
			case PathIterator.SEG_MOVETO:
			case PathIterator.SEG_LINETO:
				if (i > 0) {
					System.arraycopy(b, 0, a, 0, 2);
				}
				System.arraycopy(coords, 0, b, 0, 2);
				i++;
				if (i > 2) {
					return null;
				}
				break;
			case PathIterator.SEG_CLOSE:
				break;
			default:
				return null;
			}
		}
		
		if (i == 2) {
			// the formula for initial compass bearing is:
			// theta = atan2(sin(deltalong)*cos(lat2),cos(lat1)*sin(lat2)-sin(lat1)*cos(lat2)*cos(deltalong))
			// from initial(a to b), final bearing is (initial(b to a)+180)%360
			double dlon = Math.toRadians(a[0] - b[0]);
			double sin1 = Math.sin(Math.toRadians(b[1]));
			double cos1 = Math.cos(Math.toRadians(b[1]));
			double sin2 = Math.sin(Math.toRadians(a[1]));
			double cos2 = Math.cos(Math.toRadians(a[1]));
			double theta = Math.atan2(Math.sin(dlon)*cos2, cos1*sin2-sin1*cos2*Math.cos(dlon));
			return (Math.toDegrees(theta) + 180)%360;
		} else {
			return null;
		}
	}
	public static class Factory extends FieldFactory<FieldBearing> {
		public Factory(String name) {
			super(name, FieldBearing.class, Double.class);
		}
		public JPanel createEditor(Field f) {
			return null;
		}
		public FieldBearing createField(FeatureCollection fc, Field f) {
			return new FieldBearing(getName());
		}
	}
}
