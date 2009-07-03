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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.asu.jmars.util.Util;

/**
 * A set of attributes for a specific feature and a reference to the owning
 * FeatureCollection. The FeatureCollection contains the schema, event
 * listeners, and history logging tools for every Feature.
 * <p>The Feature class contains a map of attributes; for each unique Field,
 * an Object can be stored.
 * <p>This class also contains a back reference to the collection that
 * owns it. Feature instances are contained in exactly one FeatureCollection.
 * This tight coupling between container and contained element greatly
 * simplifies three needs of Feature:
 * <ul>
 * <li>Schema; each Feature in a FeatureCollection is homogeneous, so the
 * schema is stored on the collection instead of on the feature.
 * <li>Events: each change to a Feature is sent to listeners registered on the
 * FeatureCollection. Changes to the collection's Features or the schema are
 * also sent to the listeners.
 * <li>History: Feature instances share a single History instance through the
 * FeatureCollection, ensuring the entire collection and contents are
 * versioned correctly.
 * </ul>
 */
public class Feature {
	// package-private collection that 'owns' this Feature
	public SingleFeatureCollection owner;
	// package-private map of Field to Object
	public Map<Field,Object> attributes = new LinkedHashMap<Field,Object>();

	/**
	 * Default constructor creates a Feature object with no defined attributes.
	 */
	public Feature () {
	}
	
	/**
	 * Return the collection that owns this Feature object.
	 */
	public FeatureCollection getOwner(){
		return owner;
	}

	/**
	 * Return a read-only view of the Set of keys to the attribute map.
	 */
	public Set<Field> getKeys () {
		return Collections.unmodifiableSet (attributes.keySet ());
	}

	/**
	 * Return the attribute for the given Field.
	 */
	public Object getAttribute (Field f) {
		return attributes.get (f);
	}

	/**
	 * Set the value of a given attribute. If the given Field is not defined
	 * on the containing collection's schema, it will be added.
	 */
	public void setAttribute (Field f, Object value) {
		Feature valueBefore = (Feature)this.clone();
		setAttributeQuiet (f, value);
		
		if (owner != null) {
			owner.notify (new FeatureEvent (FeatureEvent.CHANGE_FEATURE, owner, 
					Collections.singletonList (this),
					Collections.singletonMap(this, valueBefore),
					Collections.singletonList(f)));
		}
	}
	
	/**
	 * Set the value of a given attribute without notifying. Useful if
	 * multiple attributes are going to be set on a single Feature, since the
	 * last call can be directed at setAttribute() to send one event for the
	 * many changes (although generally such an operation should be done with
	 * FeatureCollection's setAttributes() method.
	 * <p>Note that if an owner is defined and the given Field is not defined
	 * on the owner's schema, a FIELDADDED event is still sent.
	 */
	public void setAttributeQuiet (Field f, Object value) {
		attributes.put (f, value);
		if (owner != null) {
			// if this field isn't already in the schema, add it and notify
			if (! owner.getSchema ().contains (f))
				owner.addField (f);
		}
	}
	
	/**
	 * Set the values of multiple attributes. The owner is notified if 
	 * the quiet parameter is false.
	 * 
	 * @param fieldMap
	 * @param quiet
	 */
	public void setAttributes(Map fieldMap, boolean quiet){
		Feature valueBefore = (Feature)this.clone();
		attributes.putAll(fieldMap);
		if (!quiet && owner != null){
			owner.notify(new FeatureEvent(FeatureEvent.CHANGE_FEATURE, owner,
					Collections.singletonList(this),
					Collections.singletonMap(this, valueBefore),
					new ArrayList(fieldMap.keySet())));
		}
	}

	/**
	 * Clones the attribute map <i>only</i>; the Feature must be attached to a
	 * FeatureCollection to set the owner.
	 */
	public Feature clone () {
		Feature f = new Feature();
		f.attributes.putAll(this.attributes);
		return f;
	}
	
	/**
	 * Returns true if the Feature is equal to another Feature.
	 * <bold>CAUTION:</bold>Exposing this as the Object.equals(Object o)
	 * makes a whole bunch of stuff slower.
	 */
	public boolean equals(Feature o){
		if (!(o instanceof Feature))
			return false;
		
		Feature f = (Feature)o;
		return (f.owner == owner)
			&& f.attributes.equals(attributes);
	}
	
	/**
	 * Debugging version of toString() method.
	 */
	public String debugToString(){
		List attr = new ArrayList();
		
		for(Iterator i=attributes.keySet().iterator(); i.hasNext(); ){
			Object key = i.next();
			attr.add(""+key+"="+attributes.get(key));
		}
		
		return "Feature[owner="+owner+","+Util.join(",", (String[])attr.toArray(new String[0]))+"]";
	}

	/**
	 * Since the path is used so frequently, this particular resource has get/set
	 * properties.
	 */
	public FPath getPath() {
		return (FPath) getAttribute(Field.FIELD_PATH);
	}

	/**
	 * Since the path is used so frequently, this particular resource has get/set
	 * properties.
	 */
	public void setPath (FPath path) {
		setAttribute(Field.FIELD_PATH, path);
	}
}
