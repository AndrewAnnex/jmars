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

import javax.swing.JPanel;

public abstract class FieldFactory<E extends Field> {
	private String name;
	private Class<?> fieldType;
	private Class<?> dataType;
	public FieldFactory(String name, Class<?> fieldType, Class<?> dataType) {
		this.name = name;
		this.fieldType = fieldType;
		this.dataType = dataType;
	}
	public String getName() {
		return name;
	}
	public Class<?> getFieldType() {
		return fieldType;
	}
	public Class<?> getDataType() {
		return dataType;
	}
	public String toString() {
		return name;
	}
	/** Creates a new default field */
	public abstract E createField(FeatureCollection fc, Field f);
	/**
	 * Creates a new editor for editing the given field, which should be
	 * returned by {@link #createField(FeatureCollection, Field)}.
	 */
	public abstract JPanel createEditor(Field source);
}