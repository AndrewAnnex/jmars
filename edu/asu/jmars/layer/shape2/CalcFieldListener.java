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


package edu.asu.jmars.layer.shape2;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import edu.asu.jmars.layer.util.features.CalculatedField;
import edu.asu.jmars.layer.util.features.Feature;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.FeatureEvent;
import edu.asu.jmars.layer.util.features.FeatureListener;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.util.History;
import edu.asu.jmars.util.Versionable;

/**
 * Manages the calculated fields for the shape layer, both handling version
 * changes in the field => calcfield mapping, and updating features when
 * dependent fields change
 */
public class CalcFieldListener implements Versionable, FeatureListener {
	private final FeatureCollection fc;
	private final Map<Field,CalculatedField> calculatedFields;
	private final History history;
	public CalcFieldListener(FeatureCollection fc, History history) {
		this(fc, history, new HashMap<Field,CalculatedField>());
	}
	public CalcFieldListener(FeatureCollection fc, History history, Map<Field,CalculatedField> calcFields) {
		this.fc = fc;
		this.history = history;
		this.calculatedFields = calcFields;
	}
	public void setCalculatedFields(Map<Field,CalculatedField> calcFields) {
		Map<Boolean,Map<Field,CalculatedField>> change = new HashMap<Boolean,Map<Field,CalculatedField>>();
		change.put(false, new HashMap<Field,CalculatedField>(calculatedFields));
		change.put(true, calcFields);
		history.addChange(this, change);
		redo(change);
	}
	public Map<Field,CalculatedField> getCalculatedFields() {
		return calculatedFields;
	}
	/** unused, history is passed to ctor */
	public void setHistory(History history) {
	}
	public void undo(Object obj) {
		handle(obj, false);
	}
	public void redo(Object obj) {
		handle(obj, true);
	}
	private void handle(Object obj, boolean after) {
		Map<Boolean,Map<Field,CalculatedField>> change = (Map<Boolean,Map<Field,CalculatedField>>)obj;
		calculatedFields.clear();
		calculatedFields.putAll(change.get(after));
	}
	public void receive(FeatureEvent e) {
		Collection<Field> fields = (e.type == FeatureEvent.ADD_FEATURE ? e.source.getSchema() : e.fields);
		if (fields != null && e.features != null) {
			Map<Field,CalculatedField> toUpdate = null;
			for (Field f: calculatedFields.keySet()) {
				CalculatedField c = calculatedFields.get(f);
				if (!Collections.disjoint(fields, c.getFields())) {
					if (toUpdate == null) {
						toUpdate = new HashMap<Field,CalculatedField>();
					}
					toUpdate.put(f, c);
				}
			}
			if (toUpdate != null) {
				updateValues(e.features, toUpdate);
			}
		}
	}
	/** applies auto calculations to the given features and fields */
	public void updateValues(Iterable<Feature> features, Map<Field,CalculatedField> fieldMap) {
		Map<Feature,Map<Field,Object>> newValues = null;
		for (Feature feature: features) {
			boolean send = newValues != null && newValues.size() >= 5000;
			if (send) {
				fc.setAttributes(newValues);
			}
			if (newValues == null || send) {
				newValues = new HashMap<Feature,Map<Field,Object>>();
			}
			Map<Field,Object> row = new HashMap<Field,Object>(fieldMap.size()*2);
			for (Field field: fieldMap.keySet()) {
				row.put(field, fieldMap.get(field).getValue(feature));
			}
			newValues.put(feature, row);
		}
		if (newValues != null && newValues.size() > 0) {
			fc.setAttributes(newValues);
		}
	}
}

