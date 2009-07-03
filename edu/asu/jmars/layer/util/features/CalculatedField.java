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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Computes values using the abstract {@link #getValue(Feature)} method, either
 * by lazily computing them in calls to {@link #get(Feature)} or in a call to
 * {@link #update(FeatureCollection, Collection)}. Once computed, values are
 * stored on the Feature and not recomputed, so to adjust values when dependent
 * fields change, the subclass should return the fields it depends on in
 * {@link #getFields()} and the instance of this class should be added as a
 * listener on the FeatureCollection.
 */
public abstract class CalculatedField extends Field implements FeatureListener {
	private static final long serialVersionUID = 1L;
	public CalculatedField(String name, Class<?> type) {
		super(name, type, false);
	}
	/**
	 * Updates the given feature/field product in the given feature collection, sending one update event at the end
	 * @param fc The collection to update
	 * @param field The field to update (could be this)
	 * @param features The features for which to calculate the field value
	 */
	public void update(FeatureCollection fc, Field field, Collection<Feature> features) {
		Map<Feature,Object> values = new HashMap<Feature,Object>();
		for (Feature f: features) {
			values.put(f, getValue(f));
		}
		fc.setAttributes(field, values);
	}
	/** Returns the fields used by this computed field */
	public abstract Set<Field> getFields();
	public abstract Object getValue(Feature f);
	public void receive(FeatureEvent e) {
		if (e.fields != null && e.features != null && !Collections.disjoint(e.fields, getFields())) {
			update(e.source, this, e.features);
		}
	}
	public Object get(Feature f) {
		if (!f.attributes.containsKey(this)) {
			f.attributes.put(this, getValue(f));
		}
		return f.attributes.get(this);
	}
}

