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

import java.util.*;

import edu.asu.jmars.util.History;

/**
 * Describes a change to a FeatureCollection. Features can be added, removed,
 * or changed, and Fields can be added or removed. The type and affected class
 * variables are final, so once set by the constructor they cannot be changed.
 * 
 * The FeatureEvent is a dual purpose object. It is also used as the 
 * history state in the {@link History} object. Thus, it is supposed to contain
 * a complete representation of the change being made. This is currently not
 * true.
 *  
 * TODO Add MOVE_FEATURE event type that properly encapsulates the old and new
 *      position of the Features.
 * TODO Include a copy of modified attributes in addition to the old attributes
 *      in a CHANGE_FEATURE event. This is required for proper implementation
 *      of the {@link History#redo()}redo operation.
 */
public class FeatureEvent {
	/**
	 * Features were added.
	 */
	public static final int ADD_FEATURE = 0;

	/**
	 * Features were removed.
	 */
	public static final int REMOVE_FEATURE = 1;

	/**
	 * Features were changed.
	 */
	public static final int CHANGE_FEATURE = 2;

	/**
	 * Fields were added.
	 */
	public static final int ADD_FIELD = 3;

	/**
	 * Fields were removed.
	 */
	public static final int REMOVE_FIELD = 4;

	/**
	 * One of the above enumerated types.
	 */
	public final int type;
	
	/**
	 * The source FeatureCollection which contains/contained the
	 * Features/Fields.
	 */
	public final FeatureCollection source;

	/**
	 * List of Feature instances affected by this change.
	 */
	public final List features;

	/**
	 * Indices of Features affected by this change. For removed Features
	 * these are the indices when all of the removed Features were still 
	 * in the list. While for added Features these are the indices after
	 * the Features were added.
	 */
	public final Map featureIndices;

	/**
	 * Map<Feature,Feature.clone> before the Features were updated.
	 */
	public final Map valuesBefore;

	/**
	 * List of Field instances affected by this change.
	 * TODO: Wrap this variable with accessors, since it is not final anymore.
	 */
	public List fields;
	
	/**
	 * List of Field indices affected by this change. For removed Fields
	 * these are the indices when all of the removed Fields were still
	 * in the schema. While for added Fields these are the indices after
	 * the Fields were added.
	 * TODO: Wrap this variable with accessors, since it is not final anymore.
	 */
	public Map fieldIndices;
	
	/**
	 * Create a new FeatureEvent with the given type and affected lists.
	 * These are publically accessible fields, but are final and so cannot
	 * be modified once they are set here.
	 */
	// TODO: remove indices from arguments, and compute them here!
	// then remove index computation from all the calling code, since the given source
	// needs to be the frame in which the indices are valid. Use Saadat's rad indexing
	// code for this.
	public FeatureEvent (int type, FeatureCollection source, List features,
			Map valuesBefore, List fields) {
		this.type = type;
		this.source = source;
		this.features = features;
		this.valuesBefore = valuesBefore;
		this.fields = fields;
		
		// Populate the feature and field indices if the FeatureCollection is set.
		if (source != null && features != null)
			featureIndices = FeatureUtil.getFeatureIndices(source.getFeatures(), features);
		else
			featureIndices = null;

		if (source != null && fields != null)
			fieldIndices = FeatureUtil.getFieldIndices(source.getSchema(), fields);
		else
			fieldIndices = null;
	}
	
	public List getFeatureIndexList(){
		List indices = new LinkedList();
		for(Iterator i=features.iterator(); i.hasNext(); )
			indices.add(featureIndices.get(i.next()));
		
		return indices;
	}
	
	public List getFieldIndexList(){
		List indices = new LinkedList();
		for(Iterator i=fields.iterator(); i.hasNext(); )
			indices.add(fieldIndices.get(i.next()));
		
		return indices;
	}
	
	public SortedMap getIndexToFeatureMap(){
		if (featureIndices == null)
			return null;
		
		SortedMap map = new TreeMap();
		
		for(Iterator ei=featureIndices.entrySet().iterator(); ei.hasNext(); ){
			Map.Entry me = (Map.Entry)ei.next();
			map.put(me.getValue(), me.getKey());
		}
		
		return map;
	}

	public SortedMap getIndexToFieldMap(){
		if (fieldIndices == null)
			return null;
		
		SortedMap map = new TreeMap();
		
		for(Iterator ei=fieldIndices.entrySet().iterator(); ei.hasNext(); ){
			Map.Entry me = (Map.Entry)ei.next();
			map.put(me.getValue(), me.getKey());
		}
		
		return map;
	}

	/**
	 * A simple display of the event.
	 */
	public String toString(){
		String result = "FeatureEvent: ";
		switch (type) {
		case ADD_FEATURE:
			result += "ADD_FEATURE\n";
			break;
		case REMOVE_FEATURE:
			result += "REMOVE_FEATURE\n";
			break;
		case CHANGE_FEATURE:
			result += "CHANGE_FEATURE\n";
			break;
		case ADD_FIELD:
			result += "ADD_FIELD\n";
			break;
		case REMOVE_FIELD:
			result += "REMOVE_FIELD\n";
			break;
		}
		
		if (source != null){
			result += "  source:\n";
			result += "    "+source+"\n";
		}
		
		if (features != null) {
			result += "  features:\n";
			for (Iterator ai = features.iterator(); ai.hasNext(); )
				result += "    " + ai.next() + "\n";
		}

		if (fields != null) {
			result += "  fields:\n";
			for (Iterator ai = fields.iterator(); ai.hasNext(); )
				result += "    " + ai.next() + "\n";
		}

		return result;
	}

	/**
	 * Returns true if the events have the same type, features list, and fields list.
	 */
	public boolean equals (Object featureEvent) {
		if (!(featureEvent instanceof FeatureEvent))
			return false;
		
		FeatureEvent e = (FeatureEvent)featureEvent;
		return e != null
			&&  this.type == e.type
			&&  this.source == e.source
			&& (this.features == null ? e.features==null : this.features.equals (e.features))
			&& (this.fields == null ? e.fields==null : this.fields.equals (e.fields))
			&& (this.featureIndices==null? e.featureIndices==null: this.featureIndices.equals(e.featureIndices))
			&& (this.fieldIndices==null? e.fieldIndices==null: this.fieldIndices.equals(e.fieldIndices))
			&& valuesBeforeEqual(this.valuesBefore,e.valuesBefore);
	}
	
	/**
	 * Hack to compare valuesBefore maps stored in FeatureEvent.
	 * (because we don't override Object.equals() on Feature, but want to use it,
	 * we implement our own Map.equals() here.)
	 * @see Feature#equals(Feature)
	 */
	private boolean valuesBeforeEqual(Map m1, Map m2){
		if (m1 == null && m2 == null)
			return true;
		
		if ((m1 == null && m2 != null) || (m2 == null && m1 != null))
			return false;
		
		if (m1.size() != m2.size())
			return false;
		
		for(Iterator i=m1.keySet().iterator(); i.hasNext(); ){
			Object key = i.next();
			Feature v1 = (Feature)m1.get(key);
			Feature v2 = (Feature)m2.get(key);
			
			if (v1 == null && v2 != null || v2 == null && v1 != null)
				return false;
			
			if (!v1.equals(v2))
				return false;
		}
		
		return true;
	}

	/**
	 * Hashes according to the contract of hashCode() required for use in java.util.Set.
	 */
	public int hashCode () {
		return new Integer (type).hashCode() * 31 * 31 * 31
			+ ((source == null)? 0: this.source.hashCode() * 31 * 31)
			+ ((features == null)? 0: features.hashCode() * 31)
			+ ((fields == null)? 0: fields.hashCode())
			+ ((valuesBefore == null)? 0: valuesBefore.hashCode()) * 31 * 31
			+ ((featureIndices == null)? 0: featureIndices.hashCode()) * 31
			+ ((fieldIndices == null)? 0: fieldIndices.hashCode());
	}
}


