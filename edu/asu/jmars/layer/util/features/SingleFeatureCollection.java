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

import edu.asu.jmars.layer.util.filetable.FileTableModel;
import edu.asu.jmars.util.*;

/**
 * Manages the schema, event notification, and change logging for a collection
 * of Feature objects. The FeatureCollection provides access to a List of
 * Fields, a List of Features, a FeatureProvider the Features came from, and
 * the listeners to be notified of changes.
 * <p>A Feature object cannot exist in two FeatureCollections. If a Feature is
 * contained in a FeatureCollection when it's added somewhere else, a shallow
 * clone of the attributes is added, instead.
 */
public class SingleFeatureCollection implements FeatureCollection, Versionable {
	private static final DebugLog log = DebugLog.instance();
	
	// 'this' for inner classes
	final SingleFeatureCollection self = this;
	// list of Feature instances
	private List features = new ArrayList ();
	// list of schema Fields
	private List schema = new LinkedList ();
	// list of event listeners
	private List listeners = new LinkedList ();
	// provider of these features
	private FeatureProvider provider;
	// history log
	private History history;
	// ensures setAttributes calls report affected fields in schema order
	private Comparator schemaComp = new Comparator () {
		public boolean equals (Object o) { return this.equals(o); }
		public int compare (Object o1, Object o2) {
			if (schema.indexOf((Field)o1) < schema.indexOf((Field)o2))
				return -1;
			if (schema.indexOf((Field)o1) > schema.indexOf((Field)o2))
				return 1;
			return 0;
		}
	};

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#getFeatures()
	 */
	public List getFeatures () {
		return Collections.unmodifiableList (features);
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#getFeatureCount()
	 */
	public int getFeatureCount(){
		return features.size();
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#addFeature(edu.asu.jmars.layer.util.features.Feature)
	 */
	public void addFeature (Feature f) {
		addFeature(features.size(), f);
	}
	
	private void addFeature(int index, Feature f){
		if (index < 0 || index > features.size()){
			log.println("Cannot add feature "+f+" at index "+index+" where collection size is "+features.size());
			return;
		}
		
		if (f.owner != null)
			f = f.clone ();
		f.owner = this;
		features.add (index, f);
		Set newSet = new LinkedHashSet (f.attributes.keySet ());
		newSet.removeAll (schema);
		if (newSet.size () > 0) {
			schema.addAll (newSet);
			notify (new FeatureEvent (FeatureEvent.ADD_FIELD, this, null, null,
					new LinkedList (newSet)));
		}
		notify (new FeatureEvent (FeatureEvent.ADD_FEATURE, this,
				Collections.singletonList (f), null, null));
	}
	
	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#addFeatures(java.util.Collection)
	 */
	public void addFeatures (Collection c) {
		Set addSet = new LinkedHashSet ();
		for (Feature f: (Collection<Feature>)c) {
			if (f.owner != null)
				f = f.clone ();
			f.owner = this;
			features.add(f);
			addSet.addAll (f.attributes.keySet ());
		}
		// subtract fields we have from the addSet and add the remainder
		addSet.removeAll (schema);
		if (addSet.size () > 0) {
			schema.addAll (addSet);
			notify (new FeatureEvent (FeatureEvent.ADD_FIELD, this,
					null, null, new LinkedList (addSet)));
		}
		// notify of new Features
		notify (new FeatureEvent (FeatureEvent.ADD_FEATURE, this,
				new LinkedList (c), null, null));
	}
	
	private void addFeatures(Integer[] indices, Feature[] features){
		if (indices == null || features == null || indices.length != features.length)
			return;
		
		// Add to internal Feature list in ascending order.
		TreeMap indexToFeature = new TreeMap();
		for(int i=0; i<indices.length; i++)
			indexToFeature.put(indices[i], features[i]);
		
		Set addedFields = new LinkedHashSet();
		List addedFeatures = new LinkedList();
		
		for(Iterator i=indexToFeature.entrySet().iterator(); i.hasNext(); ){
			Map.Entry me = (Map.Entry)i.next();
			
			int index = ((Integer)me.getKey()).intValue();
			Feature feature = (Feature)me.getValue();
			if (index < 0 || index > this.features.size()){
				log.println("Feature "+feature+" is beyond ("+index+") features list bounds ("+this.features.size()+")");
				continue;
			}
			
			if (feature.owner != null && feature.owner != this)
				feature = (Feature)feature.clone();
			feature.owner = this;
			
			this.features.add(index, feature);
			addedFeatures.add(feature);
			addedFields.addAll(feature.attributes.keySet());
		}
		// subtract fields we have from the addedFields and add the remainder to schema
		addedFields.removeAll(schema);
		if (!addedFields.isEmpty()){
			schema.addAll (addedFields);
			notify (new FeatureEvent (FeatureEvent.ADD_FIELD, this,
					null, null, new LinkedList (addedFields)));
		}
		// notify of new Features
		notify (new FeatureEvent (FeatureEvent.ADD_FEATURE, this,
				addedFeatures, null, null));
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#removeFeature(edu.asu.jmars.layer.util.features.Feature)
	 */
	public void removeFeature (Feature f) {
		if (features.contains (f)) {
			FeatureEvent fe = new FeatureEvent (FeatureEvent.REMOVE_FEATURE, this, 
					Collections.singletonList (f), null, null);
			features.remove (f);
			if (f.owner == this)
				f.owner = null;
			notify (fe);
		}
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#removeFeatures(java.util.Collection)
	 */
	public void removeFeatures (Collection c) {
		// remove the intersection of features and c
		Set delSet = new LinkedHashSet (features);
		if (!(c instanceof Set))
			c = new HashSet (c);
		delSet.retainAll (c);
		FeatureEvent fe = new FeatureEvent (FeatureEvent.REMOVE_FEATURE, this,
				new LinkedList (delSet), null, null);
		features.removeAll (delSet);
		// unhook each deleted feature from this FeatureCollection
		Iterator delIt = delSet.iterator ();
		while (delIt.hasNext ())
			((Feature)delIt.next ()).owner = null;
		// notify
		notify (fe);
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#featurePosition(edu.asu.jmars.layer.util.features.Feature)
	 */
	public int featurePosition (Feature f) {
		return features.indexOf (f);
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#getFeature(int)
	 */
	public Feature getFeature (int pos) {
		return (Feature) features.get(pos);
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#setFeature(int, edu.asu.jmars.layer.util.features.Feature)
	 */
	public void setFeature (int pos, Feature after) {
		if (pos < 0 || pos >= features.size ())
			return;
		Feature before = (Feature) features.get (pos);
		
		if (after.owner != null && after.owner != self)
			after = (Feature)after.clone();
		after.owner = self;
		
		FeatureEvent fe = new FeatureEvent (FeatureEvent.REMOVE_FEATURE, this,
				Collections.singletonList (before), null, null);
		features.set (pos, after);
		notify (fe);
		notify (new FeatureEvent (FeatureEvent.ADD_FEATURE, this,
				Collections.singletonList (after), null, null));
	}

	/**
	 * Move Features up/down by a certial number of indices.
	 * 
	 * @param at Current location of Features.
	 * @param n  Move amount, negative for move up, positive for move down.
	 * @return true if the move occurred, false otherwise.
	 */
	public boolean move(int[] at, int n){
		if (at.length == 0 || n == 0)
			return false;
		
		int[] idx = (int[])at.clone();
		Arrays.sort(idx);
		
		// Check if the move will push elements beyond limit
		if ((idx[0]+n) < 0 || (idx[idx.length-1]+n) >= features.size())
			return false;
		
		List fc = new ArrayList(idx.length);
		FeatureEvent fe;
		for(int i=idx.length-1; i>=0; i--)
			fc.add((Feature)features.get(idx[i]));
		fe = new FeatureEvent (FeatureEvent.REMOVE_FEATURE, this, fc, null, null);
		features.removeAll(fc);
		notify (fe);

		for(int i=0; i<idx.length; i++)
			features.add(idx[i]+n, (Feature)fc.get(i));
		fe = new FeatureEvent (FeatureEvent.ADD_FEATURE, this, fc, null, null);
		notify (fe);
		
		return true;
	}

	public boolean move(int[] at, boolean top){
		if (at.length == 0)
			return false;
		
		int[] idx = (int[])at.clone();
		Arrays.sort(idx);
		
		List fc = new ArrayList(idx.length);
		FeatureEvent fe;
		for(int i=idx.length-1; i>=0; i--)
			fc.add((Feature)features.get(idx[i]));
		fe = new FeatureEvent(FeatureEvent.REMOVE_FEATURE, this, fc, null, null);
		features.removeAll(fc);
		notify (fe);
		
		for(int i=0; i<idx.length; i++)
			features.add(top? i: features.size(), (Feature)fc.get(i));
		fe = new FeatureEvent (FeatureEvent.ADD_FEATURE, this, fc, null, null);
		notify (fe);
		
		return true;
	}
	
	
	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#featureIterator()
	 */
	public ListIterator featureIterator () {
		return new ListIterator () {
			ListIterator iter = features.listIterator ();
			Feature last = null;
			/**
			 * Add a new Feature at the given position. Notifies all listeners
			 * of the addition. If the given Feature is already part of another
			 * FeatureCollection, a clone is added instead.
			 */
			public void add (Object ref) {
				Feature feature = (Feature) ref;
				if (feature.owner != null)
					feature = (Feature) feature.clone ();
				iter.add (feature);
				self.notify (new FeatureEvent (FeatureEvent.ADD_FEATURE, self,
						Collections.singletonList (feature), null, null));
			}
			public boolean hasNext () {
				return iter.hasNext ();
			}
			public boolean hasPrevious () {
				return iter.hasPrevious ();
			}
			public Object next () {
				return last = (Feature) iter.next ();
			}
			public int nextIndex () {
				return iter.nextIndex ();
			}
			public Object previous () {
				return last = (Feature) iter.previous ();
			}
			public int previousIndex () {
				return iter.previousIndex ();
			}
			/**
			 * Remove the last Feature returned by a call to previous() or
			 * next(). Notifies all listeners of the removal. The removed
			 * Feature can subsequently be added to another FeatureCollection
			 * without being cloned.
			 */
			public void remove () {
				FeatureEvent fe = new FeatureEvent (FeatureEvent.REMOVE_FEATURE, self,
						Collections.singletonList (last), null, null);
				iter.remove ();
				// if last==null, iter.remove() throws IllegalStateException
				last.owner = null;
				self.notify (fe);
			}
			/**
			 * Replace the last Feature returned by a call to previous() or
			 * next() with the given object.
			 * @throws ClassCastException Thrown when the argument is not an
			 * instance of class Feature.
			 */
			public void set (Object ref) {
				FeatureEvent fe = new FeatureEvent (FeatureEvent.REMOVE_FEATURE, self,
						Collections.singletonList (last), null, null);
				Feature feature = (Feature) ref;
				if (feature.owner != null && feature.owner != self)
					feature = (Feature) feature.clone ();
				iter.set (feature);
				// if last==null, iter.remove() throws IllegalStateException
				last.owner = null;
				feature.owner = self;
				self.notify(fe);
				last = feature;
				fe = new FeatureEvent (FeatureEvent.ADD_FEATURE, self,
						Collections.singletonList (feature), null, null);
				self.notify(fe);
			}
		};
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#getSchema()
	 */
	public List getSchema () {
		return Collections.unmodifiableList (schema);
	}

	/**
	 * Removes Fields defined in the schema but not present in any Feature in
	 * the collection.
	 */
	public void packSchema () {
		Set haveSet = new LinkedHashSet ();
		for (Iterator fIt = features.iterator (); fIt.hasNext (); )
			haveSet.addAll (((Feature)fIt.next()).attributes.keySet());
		Set removeSet = new LinkedHashSet (schema);
		removeSet.removeAll (haveSet);
		if (removeSet.size () > 0) {
			FeatureEvent fe = new FeatureEvent (FeatureEvent.REMOVE_FIELD, this, null, null,
					new LinkedList (removeSet));
			schema.removeAll (removeSet);
			notify (fe);
		}
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#addField(edu.asu.jmars.layer.util.features.Field)
	 */
	public void addField (Field f) {
		addField(schema.size(), f);
	}
	
	/**
	 * Add Field at a given location in the schema.
	 */
	private void addField(int index, Field f){
		if (index < 0 || index > schema.size()){
			log.println("Cannot add field "+f+" at index "+index+" in schema of size "+schema.size());
			return;
		}
		
		schema.add(index, f);
		notify (new FeatureEvent(FeatureEvent.ADD_FIELD, this, null, null, Collections.singletonList (f)));
	}

	/**
	 * Add Fields at the specified indices. Both indices and fields are
	 * parallel arrays.
	 */
	private void addFields(Integer[] indices, Field[] fields){
		if (indices == null || fields == null || indices.length != fields.length)
			return;
		
		Map indexToField = new TreeMap();
		for(int i=0; i<indices.length; i++)
			indexToField.put(indices[i], fields[i]);

		List addedFields = new LinkedList();
		for(Iterator i=indexToField.entrySet().iterator(); i.hasNext(); ){
			Map.Entry me = (Map.Entry)i.next();
			
			int index = ((Integer)me.getKey()).intValue();
			Field field = (Field)me.getValue();
			if (index < 0 || index > schema.size()){
				log.println("Field "+field+" is beyond ("+index+") schema bounds ("+schema.size()+")");
				continue;
			}
			schema.add(index, field);
			addedFields.add(field);
		}
		
		if (!addedFields.isEmpty())
			notify (new FeatureEvent(FeatureEvent.ADD_FIELD, this, null, null, addedFields));
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#removeField(edu.asu.jmars.layer.util.features.Field)
	 */
	public void removeField (Field f) {
		if (schema.contains (f)) {
			FeatureEvent fe = new FeatureEvent (FeatureEvent.REMOVE_FIELD, this, null, null,
					Collections.singletonList (f)); 
			schema.remove (f);
			for (Iterator fIt = features.iterator (); fIt.hasNext (); )
				((Feature)fIt.next ()).attributes.remove (f);
			notify (fe);
		}
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#getProvider()
	 */
	public FeatureProvider getProvider () {
		return provider;
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#setProvider(edu.asu.jmars.layer.util.features.FeatureProvider)
	 */
	public void setProvider (FeatureProvider provider) {
		this.provider = provider;
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#setAttributes(java.util.Map)
	 */
	public void setAttributes (Map features) {
		Set modifiedFields = new TreeSet(schemaComp);
		// for each Feature
		Map valuesBefore = new LinkedHashMap();
		Iterator featIt = features.keySet ().iterator ();
		while (featIt.hasNext ()) {
			Feature feature = (Feature) featIt.next ();
			valuesBefore.put(feature,feature.clone());
			Map fields = (Map) features.get (feature);
			// for each Field
			Iterator fldIt = fields.keySet().iterator ();
			while (fldIt.hasNext ()) {
				Field fld = (Field) fldIt.next ();
				feature.attributes.put (fld, fields.get(fld));
				modifiedFields.add(fld);
			}
		}
		// TODO: Fix this so that events are sent on batches of Features with same Field modifications!!!
		notify (new FeatureEvent (FeatureEvent.CHANGE_FEATURE, this,
				new LinkedList(features.keySet ()), valuesBefore,
				new LinkedList(modifiedFields)));
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#setAttributes(edu.asu.jmars.layer.util.features.Feature, java.util.Map)
	 */
	public void setAttributes (Feature feature, Map fields) {
		Map valuesBefore = new LinkedHashMap();
		valuesBefore.put(feature,feature.clone());
		// for each Field
		Iterator fldIt = fields.keySet().iterator ();
		while (fldIt.hasNext ()) {
			Field fld = (Field) fldIt.next ();
			feature.attributes.put (fld, fields.get(fld));
		}
		notify (new FeatureEvent (FeatureEvent.CHANGE_FEATURE, this,
				Collections.singletonList (feature), valuesBefore,
				new LinkedList(fields.keySet())));
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#setAttributes(edu.asu.jmars.layer.util.features.Field, java.util.Map)
	 */
	public void setAttributes (Field field, Map features) {
		Map valuesBefore = new LinkedHashMap();
		// for each Feature
		Iterator featIt = features.keySet ().iterator ();
		while (featIt.hasNext ()) {
			Feature feature = (Feature) featIt.next ();
			valuesBefore.put(feature, feature.clone());
			feature.attributes.put (field, features.get(feature));
			
		}
		// TODO: this has become a bit of a nightmare; SingleFeatureCollection
		// doesn't create all the data here, this type of problem is everywhere
		// and we shouldn't have events this fat. Use a pre- and post- change
		// notification instead of bloating up the event on the off-chance that
		// someone cares. Most listeners won't.
		notify (new FeatureEvent (FeatureEvent.CHANGE_FEATURE, this,
				new LinkedList (features.keySet ()), valuesBefore,
				Collections.singletonList(field)));
	}
	
	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#addListener(edu.asu.jmars.layer.util.features.FeatureListener)
	 */
	public void addListener (FeatureListener l) {
		listeners.add (l);
	}

	/* (non-Javadoc)
	 * @see edu.asu.jmars.layer.util.features.FeatureCollection#removeListener(edu.asu.jmars.layer.util.features.FeatureListener)
	 */
	public void removeListener (FeatureListener l) {
		listeners.remove (l);
	}
	
	public List getListeners(){
		return Collections.unmodifiableList(listeners);
	}

	/**
	 * Notifies all registered listeners of an event.
	 */
	public void notify (FeatureEvent e) {
		if (history != null)
			history.addChange(this, e);

		for (Iterator iter = new ArrayList(listeners).iterator (); iter.hasNext (); )
			((FeatureListener) iter.next ()).receive (e);
	}

	/**
	 * Sets the history log where all history events should go to.
	 * @param history
	 */
	public void setHistory(History history) {
		this.history = history;
	}
	
	/*
	 * (non-Javadoc)
	 * @see edu.asu.jmars.util.Versionable#redo(java.lang.Object)
	 */
	public void redo(Object obj) {
		throw new UnsupportedOperationException("redo not implemented");
	}
	
	/*
	 * (non-Javadoc)
	 * @see edu.asu.jmars.util.Versionable#undo(java.lang.Object)
	 */
	public void undo(Object obj) {
		FeatureEvent e = (FeatureEvent)obj;
		switch(e.type){
		case FeatureEvent.ADD_FEATURE:
			// Remove Features.
			removeFeatures(e.features);
			break;
		case FeatureEvent.REMOVE_FEATURE:
			// Add features back to the same location they were deleted from.
			addFeatures((Integer[])e.getFeatureIndexList().toArray(new Integer[0]),
					(Feature[])e.features.toArray(new Feature[0]));
			break;
		case FeatureEvent.CHANGE_FEATURE:
			Map attrMap = new LinkedHashMap();
			for(Iterator ei=e.valuesBefore.entrySet().iterator(); ei.hasNext(); ){
				Map.Entry me = (Map.Entry)ei.next();
				Map<Field,Object> atts = ((Feature)me.getValue()).attributes;
				Feature feat = (Feature)me.getKey();
				for (Field f: feat.attributes.keySet()) {
					if (!atts.containsKey(f)) {
						atts.put(f, null);
					}
				}
				attrMap.put(feat, atts);
			}
			setAttributes(attrMap);
			break;
		case FeatureEvent.ADD_FIELD:
			// Remove Fields.
			for(Iterator fi=e.fields.iterator(); fi.hasNext(); ){
				removeField((Field)fi.next());
			}
			break;
		case FeatureEvent.REMOVE_FIELD:
			// Add Fields from wherever they were removed from.
			addFields((Integer[])e.getFieldIndexList().toArray(new Integer[0]),
					((Field[])e.fields.toArray(new Field[0])));
			break;
		default:
			log.println("Unhandled FeatureEvent type "+e.type+" encountered. Ignoring!");
		}
	}
	
	/**
	 * Debugging version of toString() method.
	 */
	public String debugToString(){
		String s = "SingleFeatureCollection[";
		if (getFilename() != null)
			s += getFilename();
		else
			s += FileTableModel.NULL_PROVIDER_NAME; 
		
		s += "]";
		
		return s;
	}

	private String fileName;
	public String getFilename() {
		return fileName;
	}
	public void setFilename(String fileName) {
		this.fileName = fileName;
	}
}


