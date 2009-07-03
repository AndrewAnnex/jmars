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

import java.awt.geom.*;
import java.util.*;

/**
 * Helper methods that several classes in the Feature Framework might make use
 * of.
 */

public class FeatureUtil  {
	/**
	 * This is to prevent some ne'er-do-well from coming in and trying to
	 * instanciate what is supposed to be class of nothing but static methods.
	 */
	private FeatureUtil(){}

	/**
	 * Returns the rectangle of the given width centered on the given point.
	 */
	public static Rectangle2D getClickBox( Point2D p, int width){
		Rectangle2D r =  new Rectangle2D.Float();
		r.setRect( p.getX() - width/2, 
			   p.getY() - width/2, 
			   (double)width, 
			   (double)width);
		return r;
	}

	/**
	 * Returns the world-coordinate Point2D for the first vertex
	 */
	public static Point2D getStartPoint(Feature f) {
		FPath path = (FPath)f.getAttribute(Field.FIELD_PATH);
		Point2D[] vertices = path.getWorld().getVertices();
		return (vertices == null || vertices.length < 1 ? null : vertices[0]);
	}

	/**
	 * Return the world-coordinate Point2D for the average position
	 * of the centers from each Feature in 'fc'.
	 */
	public static Point2D getCenterPoint(Collection fc){
		Point2D.Double center = new Point2D.Double();
		
		for(Iterator i=fc.iterator(); i.hasNext(); ) {
			Feature f = (Feature)i.next();
			Point2D c = f.getPath().getWorld().getCenter();
			center.x += c.getX(); center.y += c.getY();
		}
		if (fc.size() > 1) {
			center.x /= fc.size();
			center.y /= fc.size();
		}
		
		return center;
	}

	/**
	 * Return the Feature types represented in this FeatureCollection.
	 * @return An non-null array of Feature types corresponding to their
	 *         representation in this collection.
	 */
	public static int[] getRepresentedFeatureTypes(Collection features){
		int[] rep = new int[4];
		
		for(Iterator i=features.iterator(); i.hasNext(); ){
			Feature f = (Feature)i.next();
			int pathType = f.getPath().getType();
			
			if (pathType < 0 || pathType >= rep.length)
				throw new Error("Unhandled pathType encountered "+pathType);
			
			rep[pathType]++;
		}
		
		int count = 0;
		for(int i=0; i<rep.length; i++)
			if (rep[i] > 0)
				count ++;
		
		int[] repTypes = new int[count];
		for(int i=0; i<rep.length; i++)
			if (rep[i] > 0)
				repTypes[--count] = i;
		
		return repTypes;
	}

	/**
	 * Returns the count of Features that are of the specified type.
	 * @param features List of Feature objects.
	 * @param featureType Type of feature as returned by {@link Feature#getType()}.
	 * @return Total number of features of the specified featureType found in the
	 *         given collection.
	 */
	public static int countFeatures(Collection features, int featureType){
		int count = 0;
		for(Iterator i=features.iterator(); i.hasNext(); ){
			Feature f = (Feature)i.next();
			if (f.getPath().getType() == featureType)
				count++;
		}
		return count;
	}

	/**
	 * Returns the index of every Feature in the features as it is listed in
	 * featureList. This is equivalent to:
	 * <code>
	 * for each feature in (features){
	 *     feature2IndexMap.put(feature, featureList.indexOf(feature))
	 * }
	 * </code>
	 * @param within Indices within this list are returned.
	 * @param of Feature objects for which indices are to be determined.
	 * @return Map<Feature,Integer>. 
	 */
	// TODO: put this on FeatureCollection
	public static Map getFeatureIndices(List within, Collection of){
		HashSet featureSet = new HashSet(of);
		Map featureIndices = new HashMap();
		Feature feat;
		Iterator fi=within.iterator();
		
		for(int i=0; fi.hasNext(); i++){
			feat = (Feature)fi.next();
			if (featureSet.contains(feat))
				featureIndices.put(feat, new Integer(i));
		}
		
		return featureIndices;
	}
	
	/**
	 * Returns the index of every Field in the fields as it is listed in 
	 * the fieldList. This is equivalent to:
	 * <code>
	 * for each field in (fields){
	 *    field2IndexMap.put(field, fieldList.indexOf(field))
	 * }
	 * </code>
	 * @param within Indices within this list are returned.
	 * @param of Fields for which indices are to be determined.
	 * @return Map<Field,Integer>.
	 */
	// TODO: put this on FeatureCollection
	public static Map getFieldIndices(List within, Collection of){
		HashSet fieldSet = new HashSet(of);
		Map fieldIndices = new HashMap();
		Field field;
		Iterator fi=within.iterator();
		
		for(int i=0; fi.hasNext(); i++){
			field = (Field)fi.next();
			if (fieldSet.contains(field))
				fieldIndices.put(field, new Integer(i));
		}
		
		return fieldIndices;
	}

	/**
	 * Splits a string into floats using any of the characters in the delim
	 * string as delimiters.
	 */
	public static float[] stringToFloats(String s, String delim){
		StringTokenizer tokenizer = new StringTokenizer(s, delim);
		
		ArrayList list = new ArrayList();
		while(tokenizer.hasMoreTokens())
			list.add(new Float(tokenizer.nextToken()));
		
		float[] floats = new float[list.size()];
		for(int i=0; i<list.size(); i++)
			floats[i] = ((Float)list.get(i)).floatValue();
		
		return floats;
	}
	
    public static final String TYPE_STRING_POINT = "point";
    public static final String TYPE_STRING_POLYLINE = "polyline";
    public static final String TYPE_STRING_POLYGON = "polygon";
    public static final String TYPE_STRING_INVALID = "invalid";
    
    /**
     * Returns string giving the type of Feature given the FPath.TYPE_*
     * value as input.
     * 
     * @param type One of the FPath.TYPE_* values.
     * @return String representations of the TYPE_*.
     */
    public static String getFeatureTypeString(int type){
    	switch(type){
    		case FPath.TYPE_POINT: return TYPE_STRING_POINT;
    		case FPath.TYPE_POLYLINE: return TYPE_STRING_POLYLINE;
    		case FPath.TYPE_POLYGON: return TYPE_STRING_POLYGON;
    	}
    	return TYPE_STRING_INVALID;
    }

    /**
     * Returns a longitude in the interval [0.0, 360.0).
     * This method should not be used on longitudes that are
     * more than 360 degrees outside the included interval;
     * it will return the expected result, but performance will
     * be very poor.
     */
	public static double lonNorm (double lon) {
		while (lon < 0.0) lon += 360.0;
		while (lon >= 360.0) lon -= 360.0;
		return lon;
	}
} // end: class FeatureUtil
