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
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public interface FeatureCollection {
	/**
	 * Returns an unmodifiable view of the Features. If features must be added,
	 * use addFeature(); if features must be removed, use removeFeature().
	 */
	public abstract List getFeatures();
	
	/**
	 * Returns the count of Features stored in this collection.
	 */
	public abstract int getFeatureCount();

	/**
	 * Adds the given Feature to the collection. If any of the fields on this
	 * Feature are not part of the schema, they will be added. If the given
	 * feature is already contained in a FeatureCollection, a clone of the
	 * argument Feature will be added instead of the reference given.
	 */
	public abstract void addFeature(Feature f);

	/**
	 * Adds the given Collection of Feature instances to the collection. If any
	 * of the fields in this Feature are not part of the schema, they will be
	 * added. If any Feature has previously been added to a FeatureCollection,
	 * an attribute clone is inserted instead.
	 */
	public abstract void addFeatures(Collection c);

	/**
	 * Removes the given Feature from the collection. The schema is not
	 * modified in response to this removal.
	 * The given Feature can subsequently be added to another FeatureCollection
	 * without a clone being created.
	 */
	public abstract void removeFeature(Feature f);

	/**
	 * Removes the given collection of Features from the collection. The schema
	 * is not modified in response to this removal.
	 * The given Feature can subsequently be added to another FeatureCollection
	 * without a clone being created.
	 */
	public abstract void removeFeatures(Collection c);

	/**
	 * Return the position of the given feature.
	 */
	public abstract int featurePosition(Feature f);

	/**
	 * Get the feature by position.
	 */
	public abstract Feature getFeature(int pos);

	/**
	 * Set the given position to the given feature. Deletes the Feature at the
	 * given position and replaces it with the given feature. If the given
	 * feature is not part of a FeatureCollection it will be set to this one.
	 * If position is not valid, this method has no effect.
	 */
	public abstract void setFeature(int pos, Feature after);
	
	/**
	 * Move Features up/down by a certian number of indices.
	 * 
	 * @param at Current location of Features.
	 * @param n  Move amount, negative for move up, positive for move down.
	 * @return true if the move occurred, false otherwise.
	 */
	public boolean move(int[] at, int n);

	/**
	 * Moves Features to the top or bottom.
	 * 
	 * @param at Current location of Features.
	 * @param top true for move to top, false for move to bottom. 
	 * @return true if the move occurred, false otherwise.
	 */
	public boolean move(int[] at, boolean top);
	
	/**
	 * Returns a ListIterator over the Feature instances in this collection.
	 * The add() and remove() methods notify listeners.
	 */
	public abstract ListIterator featureIterator();

	/**
	 * Returns an unmodifiable view of the schema. If fields must be added,
	 * use addField(); if fields must be removed, use removeField().
	 */
	public abstract List getSchema();

	/**
	 * Add the given Field to the schema.
	 * Notifies all listeners.
	 */
	public abstract void addField(Field f);

	/**
	 * Remove the given Field from the schema and any defined values from the
	 * attribute map of each Feature in this FeatureCollection.
	 */
	public abstract void removeField(Field f);

	/**
	 * Return the provider that supplied the data.
	 */
	public abstract FeatureProvider getProvider();

	/**
	 * Set the provider that supplied the data.
	 */
	public abstract void setProvider(FeatureProvider provider);

	/**
	 * Return the filename that was used to load the data from the provider.
	 */
	public abstract String getFilename();

	/**
	 * Set the filename that was given to the load() method of the provider.
	 */
	public abstract void setFilename(String fileName);

	/**
	 * Set cells in multiple Fields in multiple Features.
	 * @param features Map of Feature to [Map of Field to Object].
	 */
	public abstract void setAttributes(Map features);

	/**
	 * Set the value of multiple Fields for the given Feature.
	 * @param feature The Feature to set values into.
	 * @param fields The map of Field to Object values.
	 */
	public abstract void setAttributes(Feature feature, Map fields);

	/**
	 * Set the value of multiple Features for the given Field.
	 * @param field The Field to set values into.
	 * @param features The map of Feature to Object values.
	 */
	public abstract void setAttributes(Field field, Map features);

	/**
	 * Adds the given listener. Subsequent schema and feature events will be
	 * sent to this listener as well as any other registered listeners.
	 * <bold>CAUTION:</bold>
	 * The implementers must guarantee that the event listeners are processed
	 * in the order in which they are added. Thus, the first listener added
	 * using this interface should get the FeatureEvent before the second
	 * listener added.
	 */
	public abstract void addListener(FeatureListener l);

	/**
	 * Removes the given listener. Subsequent schema and feature events will
	 * not be sent to this listener.
	 */
	public abstract void removeListener(FeatureListener l);
	
	/**
	 * Returns an unmodifiable List of Feature and Schema event listeners
	 * currently registered with this FeatureCollection.
	 */
	public abstract List getListeners();
}
