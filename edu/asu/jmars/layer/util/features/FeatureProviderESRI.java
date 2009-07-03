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

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.JOptionPane;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.dataAccess.shape.EsriPointList;
import com.bbn.openmap.dataAccess.shape.EsriPolygon;
import com.bbn.openmap.dataAccess.shape.EsriPolygonList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.dataAccess.shape.input.*;
import com.bbn.openmap.dataAccess.shape.output.*;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMPoint;
import com.bbn.openmap.omGraphics.OMPoly;
import com.bbn.openmap.proj.ProjMath;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.BidiMap;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.LineType;


/** 
 * A class for loading/saving features from ESRI shape file.
 * <p>
 * If this FeatureProvider has been loaded from, then subsequent
 * save operations will only save the type of features that was loaded
 * during the most recent preceeding load. All other data is ignored.
 * <p>
 * If this FeatureProvider hasn't been loaded from, then subsequent
 * save operations will save all the data to their respective files.
 * <p>
 * Note that ESRI format requires point, polygon, poly-line data to 
 * be stored in their individual files.
 * <p>
 * Input data is assumed to be in East leading spherical coordinates.
 * The load routine converts this data from East leading to JMars
 * World coordinates before returning.
 * <p>
 * The save operation converts the data back from World coordinates
 * to East leading spherical coordinates.
 * <p>
 * public methods:
 *    FeatureCollection load()
 *    int save(  FeatureCollection )
 */
public class FeatureProviderESRI implements FeatureProvider {
	private static DebugLog log = DebugLog.instance();

	public String getExtension () {
		return ".shp";
	}

	public String getDescription () {
		return "ESRI Shape File";
	}

	public boolean isFileBased () {
		return true;
	}

	/**
	 * Value to output for a null field value.
	 */
	public static final String NULL_STRING = "null";

	private EsriProperties props = new EsriProperties();

	/**
	 * Contains properties of an ESRI file known at load time that are needed at
	 * save time. They're stored by feature collection in a weak hash map so
	 * they are reaped if the collection goes away.
	 */
	class EsriProperties {
		/** Type of features loaded from file */
		private Map fcToType = new WeakHashMap();
		/** Field to ColumnDesc map set when loaded from file */
		private Map fcToDesc = new WeakHashMap();

		public void addProperties(FeatureCollection fc, int type, Map descs) {
			fcToType.put(fc, new Integer(type));
			fcToDesc.put(fc, Collections.unmodifiableMap(descs));
		}

		public int getShapeType(FeatureCollection fc) {
			Integer type = (Integer)fcToType.get(fc);
			return (type == null ? SHAPE_TYPE_NONE : type.intValue());
		}

		public Map getColumnDesc(FeatureCollection fc) {
			Map desc = (Map)fcToDesc.get(fc);
			return (desc == null ? new HashMap() : desc);
		}
	}

	// These must correspond to the types that the ESRI files contain.
	public static final int SHAPE_TYPE_NONE     = 0;
	public static final int SHAPE_TYPE_POINT    = 1;
	public static final int SHAPE_TYPE_POLYLINE = 3;
	public static final int SHAPE_TYPE_POLYGON  = 5;

	public static final String SHAPE_STRING_NONE     = FeatureUtil.TYPE_STRING_INVALID;
	public static final String SHAPE_STRING_POINT    = FeatureUtil.TYPE_STRING_POINT;
	public static final String SHAPE_STRING_POLYLINE = FeatureUtil.TYPE_STRING_POLYLINE;
	public static final String SHAPE_STRING_POLYGON  = FeatureUtil.TYPE_STRING_POLYGON;

	public static final Field FEATURE_TYPE = Field.FIELD_FEATURE_TYPE;
	public static final Field LABEL        = Field.FIELD_LABEL;
	public static final Field SHOW_LABELS  = Field.FIELD_SHOW_LABEL;
	public static final Field LABEL_COLOR  = Field.FIELD_LABEL_COLOR;
	public static final Field LINE_COLOR   = Field.FIELD_DRAW_COLOR;
	public static final Field LINE_TYPE    = Field.FIELD_LINE_DASH;
	public static final Field LINE_DIR     = Field.FIELD_LINE_DIRECTED;
	public static final Field LINE_WIDTH   = Field.FIELD_LINE_WIDTH;
	public static final Field POINT_SIZE   = Field.FIELD_POINT_SIZE;
	public static final Field FILL_COLOR   = Field.FIELD_FILL_COLOR;
	public static final Field FILL_POLY    = Field.FIELD_FILL_POLYGON;
	public static final Field AREA         = new Field( "Area",         Double.class, true);
	public static final Field LAT          = new Field( "Latitude",     Double.class, true);
	public static final Field LON          = new Field( "Longitude",    Double.class, true);
	public static final Field FILE         = new Field( "file",         String.class, true);
	
	/**
	 * Reserved bidirectional internal names to external name mapping.
	 */
	public static final BidiMap intToExtNames;
	static {
		BidiMap names = new BidiMap();
		names.add(SHOW_LABELS.name, "showlbl");
		names.add(LABEL_COLOR.name, "lblclr");
		names.add(LINE_COLOR.name, "lineclr");
		names.add(FILL_COLOR.name, "fillclr");
		names.add(LINE_WIDTH.name, "lwidth");
		names.add(POINT_SIZE.name, "ptsize");
		names.add(LINE_DIR.name, "ldir");
		names.add(LINE_TYPE.name, "ltype");
		names.add(FILL_POLY.name, "filled");
		intToExtNames = names;
	};
	
	public static final Set reservedNames = Collections.unmodifiableSet(intToExtNames.rightKeys());

	
	/**
	 * gets files to load then reads in the features contained in those files. 
	 */

	public FeatureCollection load(String name) {
		final FileName fileName = new FileName();
		fileName.setLoadFiles(name);

		// open only if all files exist 
		if(!fileName.allShapeFilesExist()) {
			return null;
		}

		// get the shape type of the file.
		int shapeType    = fileName.getShapeType();
		String shapeTypeString = getShapeTypeString(shapeType);

		ArrayList    shpList          = readShpShx( fileName);
		DbfTableModel tableModel;
		try {
			tableModel = new DbfTableModel(new DbfInputStream(new FileInputStream(fileName.getDbf())));
		}
		catch(Exception ex){
			log.aprintln(ex.getMessage());
			return null;
		}

		// Build a FeatureCollection.
		SingleFeatureCollection fc = new SingleFeatureCollection();

		// Setup default schema
		fc.addField( Field.FIELD_PATH);

		// Create a set of field names so that we don't end up with duplicate names. 
		Map predefNames = new HashMap();
		for(Iterator i=fc.getSchema().iterator(); i.hasNext(); ){
			Field field = (Field)i.next();
			predefNames.put(field.name.toLowerCase(), field);
		}

		Map columnDesc = new HashMap();

		// Create a map of Field->Column-number.
		Map fieldIndex = new HashMap();

		// Add any extra columns the file might have to the schema.
		int tableColCount = tableModel.getColumnCount();
		for (int columnIndex=0; columnIndex< tableColCount; columnIndex++){
			// NOTE: Don't use tableModel.getColumnClass(col) as it does a getValueAt(0,col).getClass()
			// which fails when there are zero rows in the table.
			
			ColumnDesc fd = ColumnDesc.extractFromDbfTableModel(tableModel, columnIndex);
			Field field = (Field)predefNames.get(fd.iName.toLowerCase());
			
			if (field == null){
				field = new Field(fd.iName, fd.iClass, true);
				predefNames.put(fd.iName.toLowerCase(), field);
				fc.addField(field);
				columnDesc.put(field, fd);
			}
			fieldIndex.put(field, new Integer(columnIndex));
		}

		// Check that the sizes of the shp and dbf files match.
		int dbfRows = tableModel.getRowCount();
		int shpRows = shpList.size();
		if (dbfRows != shpRows){
			log.aprintln("dbfTable rows (" + dbfRows + ") != shpList rows (" + shpRows + ")");
			return null;
		}
		
		// build features and add them to the FeatureCollection.
		List schema = fc.getSchema();
		ArrayList featureList = new ArrayList();
		for (int i=0; i< dbfRows; i++){
			Feature f  = new Feature();
			boolean closed = (shapeType==SHAPE_TYPE_POLYGON);
			FPath path = new FPath ((float[])shpList.get(i), true, FPath.SPATIAL_EAST, closed);
			f.setAttributeQuiet( Field.FIELD_PATH, path.getSpatialWest());

			// Add all the other columns to the Feature.
			// Note that we cannot simply test if the field is contains in the schema 
			// because we have to process those "other" columns.
			Iterator si = schema.iterator();
			while (si.hasNext()){
				Field field = (Field)si.next();
				try {
					if (field.equals( Field.FIELD_PATH)){
					} else if ( field.equals( FILE)){
						f.setAttributeQuiet( FILE, fileName.shpFileName);
					} else if (field.equals( FEATURE_TYPE)){
						f.setAttributeQuiet( FEATURE_TYPE, shapeTypeString);
					} else {
						Integer indexObj = (Integer)fieldIndex.get(field); 
						int index = indexObj == null? -1: indexObj.intValue();
						if (index >= 0){
							Object val = tableModel.getValueAt(i, index);
							Object nullVal = tableModel.getEmptyDefaultForType(tableModel.getType(index));
							if (nullVal != null && nullVal.equals(val))
								val = null;
							
							if (val != null){
								if (field.type == String.class)
									f.setAttributeQuiet( field, (String)val);
								else if (field.type == Boolean.class)
									f.setAttributeQuiet( field, new Boolean((String)val));
								else if (field.type == Double.class)
									f.setAttributeQuiet( field, new Double(((Number)val).doubleValue()));
								else if (field.type == Integer.class)
									f.setAttributeQuiet( field, new Integer(((Number)val).intValue()));
								else if (field.type == Color.class)
									f.setAttributeQuiet( field, stringToColor((String)val));
								else if (field.type == LineType.class)
									f.setAttributeQuiet( field, new LineType((String)val));
							}
						}
					}
				}
				catch (NumberFormatException exc){
					log.aprintln("Processing field "+field.name+": "+exc.getMessage());
				}
				catch (ClassCastException exc){
					log.aprintln("Processing field "+field.name+": "+exc.getMessage());
				}
			}
			
		
			featureList.add( f);
		}

		fc.addFeatures( featureList);

		props.addProperties(fc, shapeType, columnDesc);

		return fc;
	}

	/**
	 * Return a list of files that will be read out by this FeatureProvider
	 * given the file-name fragment.
	 * 
	 * @param file A file name fragment.
	 * @return An array of file names that may get read as a result of load().
	 */
	public String[] getSourceFiles(String file){
		FileName fn = new FileName();
		fn.setFiles(file);
		return fn.getLoadFiles();
	}
	
	// on LOADING:
	// Each element in the list is a float[] of LatLon values
	// in decimal degrees. The size of the list should equal
	// the number of features in the shapeFile and the float[]
	
	// can be used to create shapes or generalpaths for each feature. 
	
	private ArrayList readShpShx( FileName fileName)
	{
		ArrayList list = new ArrayList();
		InputStream    inputStream    = null;
		ShpInputStream shpInputStream = null;
		ShxInputStream shxInputStream = null;
		
		try  {
			inputStream     = new FileInputStream( fileName.getShp());
			shpInputStream  = new ShpInputStream( inputStream);
			shxInputStream  = new ShxInputStream( new FileInputStream( fileName.getShx()));
			
			//the iterator over an OMGraphicList  
			Iterator iterator = shpInputStream.getGeometry( shxInputStream.getIndex()).iterator();
		
			while(iterator.hasNext()) {
				
		       
				// In OpenMap Polygons and PolyLines are in terms of radians, but 
				// Points are in terms of decimal degrees. (It's just the way it is with OpenMap)).
				// So convert the lat lon values to degrees
				// Note: the value in the shape file is in deg's
				// but OpenMap converts it to radians. If 
				// in the future GeoTools is used :)  then the conversion
				// will no longer be needed since GeoTools does
				// 'the right thing'
				OMGraphic omg = (OMGraphic)iterator.next();
				
				//POINT
				if(fileName.getShapeType() == SHAPE_TYPE_POINT) {
					OMPoint omPoint     = (OMPoint)omg;
					float[] latLonArray = new float[]{omPoint.getLat(), omPoint.getLon()};
					list.add(latLonArray);
				}
				else { //PolyGon and PolyLine
					//Some polygons are nested or at least thats the way OpenMap handles them. 
					// Some of the converted 2107*.shp files from USGS are that way.
					// So need to see if the OMGraphic is a EsriPolygonList or a EsriPolygon
					// Of course, we have no way of dealing with nested polys, so just take
					// the first one.
					if(omg instanceof EsriPolygonList) {
						EsriPolygonList innerList = (EsriPolygonList)omg;
						Iterator innerIterator = innerList.iterator();
						//while(innerIterator.hasNext()) {
						if (innerIterator.hasNext()) {
							OMPoly  innerPoly   = (OMPoly)innerIterator.next();
							float[] latLonArray = ProjMath.arrayRadToDeg( innerPoly.getLatLonArray());
							list.add(latLonArray);
						}
					}
					else  {//its a single polygon
						OMPoly omPoly = (OMPoly)omg;
						float[] latLonArray = ProjMath.arrayRadToDeg(omPoly.getLatLonArray());
						list.add(latLonArray);
					}
				}
			}
			inputStream.close();
			shxInputStream.close();

		} catch (FileNotFoundException e) {
			System.out.println("Error reading SHP file: " + e);
			e.printStackTrace();
			return null;
		} catch(Exception e) {
			System.out.println("Error reading SHP file: " + e);
			e.printStackTrace();
			return null;
		}
		
		return list;
	}

	/**
	 * Returns true if either this FeatureCollection was not loaded, or the
	 * set of feature types that were loaded equal the set of feature types
	 * we have now.
	 */
	public boolean isRepresentable(FeatureCollection fc) {
		int type = props.getShapeType(fc);
		if (type == SHAPE_TYPE_NONE)
			return true;

		int[] featTypes = FeatureUtil.getRepresentedFeatureTypes(fc.getFeatures());
		return (featTypes.length == 1 && featTypes[0] == type);
	}

	/**
	 * Returns a list of Files that will get overwritten during save.
	 * @param fc The FeatureCollection that will be subsequently saved
	 *           via a call to save(FeatureCollection).
	 * @return A list of File objects.
	 */
	public File[] getExistingSaveToFiles(FeatureCollection fc, String fname) {
		int shapeType = props.getShapeType(fc);
		FileName fileName = new FileName(shapeType);
		fileName.setSaveFiles(fname);
		List existing = new ArrayList();
		File file;

		int[] featTypes = new int[0];
		if (shapeType == SHAPE_TYPE_NONE)
			featTypes = getRepresentedShapeTypes(fc.getFeatures());
		else 
			featTypes = new int[]{ shapeType };
		
		for(int i=0; i<featTypes.length; i++){
			if ((file = new File(fileName.getSaveShp(featTypes[i]))).exists())
				existing.add(file);
			if ((file = new File(fileName.getSaveShx(featTypes[i]))).exists())
				existing.add(file);
			if ((file = new File(fileName.getSaveDbf(featTypes[i]))).exists())
				existing.add(file);
		}

		return (File[])existing.toArray(new File[0]);
	}
	
	private int[] getRepresentedShapeTypes(Collection c){
		int[] featTypes = FeatureUtil.getRepresentedFeatureTypes(c);
		if (featTypes == null)
			return null;

		int[] shapeTypes = new int[featTypes.length];
		for(int i=0; i<featTypes.length; i++)
			shapeTypes[i] = featureTypeToShapeType(featTypes[i]);
		
		return shapeTypes;
	}
	
	private int featureTypeToShapeType(int ft){
		switch(ft){
			case FPath.TYPE_POINT: return SHAPE_TYPE_POINT;
			case FPath.TYPE_POLYGON: return SHAPE_TYPE_POLYGON;
			case FPath.TYPE_POLYLINE: return SHAPE_TYPE_POLYLINE;
		}
		return SHAPE_TYPE_NONE;
	}
	
	private int shapeTypeToFeatureType(int st){
		switch(st){
			case SHAPE_TYPE_POINT: return FPath.TYPE_POINT;
			case SHAPE_TYPE_POLYGON: return FPath.TYPE_POLYGON;
			case SHAPE_TYPE_POLYLINE: return FPath.TYPE_POLYLINE;
		}
		return FPath.TYPE_NONE;
	}
		
    // on SAVING
    // saves the selected features of ALL types to appropriate files.
    public int save( FeatureCollection fc, String fname) {
		int shapeType = props.getShapeType(fc);
	    FileName fileName = new FileName(shapeType);
	    fileName.setSaveFiles(fname);

	    String shapeString = "";

	    List features = fc.getFeatures();

	    int totalCount = 0;
	    int[] saveShapeTypes = new int[]{
    			SHAPE_TYPE_POINT,
    			SHAPE_TYPE_POLYLINE,
    			SHAPE_TYPE_POLYGON,
    	};

	    for(int i=0; i<saveShapeTypes.length; i++){
	    	if (rowsExist( features, saveShapeTypes[i])){
	    		if (shapeType == SHAPE_TYPE_NONE || (shapeType == saveShapeTypes[i])){
	    			int featureCount = saveFeatures( fileName, fc, saveShapeTypes[i]);
	    			if (featureCount>0){
	    				shapeString +=  featureCount + " ";
	    				shapeString += getShapeTypeString(saveShapeTypes[i]);
	    				shapeString += "(s) were saved to " + 
	    				fileName.getSaveShp(saveShapeTypes[i]) +"\n";
	    			}
	    			else {
	    				shapeString += "Failed to save " + (-featureCount) + " ";
	    				shapeString += getShapeTypeString(saveShapeTypes[i]);
	    				shapeString += "(s) to " + fileName.getSaveShp(saveShapeTypes[i]) +"\n";
	    			}
	    			totalCount += featureCount;
	    		}
	    		else {
	    			int featureCount = FeatureUtil.countFeatures(fc.getFeatures(), shapeTypeToFeatureType(saveShapeTypes[i]));
	    			if (featureCount > 0){
	    				shapeString += featureCount + " ";
	    				shapeString += getShapeTypeString(saveShapeTypes[i]);
	    				shapeString += "(s) were NOT saved\n";
	    			}
	    		}
	    	}
	    }
	    JOptionPane.showMessageDialog(Main.getLManager(), shapeString + "\n");
	    return totalCount;
    }

    private String getShapeTypeString(int shapeType){
		switch(shapeType){
			case SHAPE_TYPE_POINT:
				return SHAPE_STRING_POINT;
			case SHAPE_TYPE_POLYLINE:
				return SHAPE_STRING_POLYLINE;
			case SHAPE_TYPE_POLYGON:
				return SHAPE_STRING_POLYGON;
		}
		return SHAPE_STRING_NONE;
    }

    // on SAVING:
    // checks if there are rows of the specified type in the array of features.
    private boolean rowsExist( java.util.List features, int shapeType){
    	Iterator fi = features.iterator();
    	while (fi.hasNext()){
    		Feature feature = (Feature)fi.next();
    		int pathType  = feature.getPath().getType();
    		if (shapeType == featureTypeToShapeType(pathType))
    			return true;
    	}
    	return false;
    }
    
    private float[] setLonBetweenNeg180And180(float[] coords, boolean latFirst){
    	float[] out = (float[])coords.clone();
    	for(int i=latFirst?1:0; i<out.length; i+=2){
    		if (out[i] > 180.0)
    			out[i] = out[i]-360.0f;
    		if (out[i] < -180.0)
    			out[i] = out[i]+360.0f;
    	}
    	return out;
    }

	// on SAVING:
	// save the array of features to the file.
	// The number of features that were actually saved is maintained and returned.
	private int saveFeatures( FileName fileName, FeatureCollection fc, int shapeType)
	{
		java.util.List features = fc.getFeatures();
		Iterator fi;
		Feature feature;
		int featureShapeType;

		int featureCount = 0;
		try {
			EsriGraphicList esriGraphicList = null;
			switch(shapeType){
				case SHAPE_TYPE_POINT: esriGraphicList = new EsriPointList(); break;
				case SHAPE_TYPE_POLYLINE: esriGraphicList = new EsriPolylineList(); break;
				case SHAPE_TYPE_POLYGON: esriGraphicList = new EsriPolygonList(); break;
				default:
					throw new UnsupportedOperationException("Don't know how to save shapes of type "+shapeType);
			}
			
			for(fi = features.iterator(); fi.hasNext(); ){
				feature = (Feature)fi.next();
				featureShapeType = featureTypeToShapeType(feature.getPath().getType());
				
				if (featureShapeType != shapeType)
					continue;

				FPath sePath = ((FPath) feature.getAttribute(Field.FIELD_PATH)).getSpatialEast();
				// ESRI Shape files like their shape coordinates between -180 & 180 longitudes.
				float[] coords = setLonBetweenNeg180And180(sePath.getCoords(true), true);

				switch(featureShapeType){
					case SHAPE_TYPE_POINT:
						EsriPoint point = new EsriPoint(coords[0], coords[1]);
						esriGraphicList.add(point);
						break;
					case SHAPE_TYPE_POLYLINE:
						EsriPolyline polyline = new EsriPolyline(coords,
								EsriPolygon.DECIMAL_DEGREES, EsriPolygon.LINETYPE_STRAIGHT);
						esriGraphicList.add(polyline);
						break;
					case SHAPE_TYPE_POLYGON:
						EsriPolygon polygon = new EsriPolygon(coords,
								EsriPolygon.DECIMAL_DEGREES, EsriPolygon.LINETYPE_STRAIGHT);
						esriGraphicList.add(polygon);
						break;
					default:
						throw new UnsupportedOperationException("Don't know how to save shapes of type "+featureShapeType);
				}
				
				featureCount++;
			}
			

			// Do the saving now.
			OutputStream os = null;
			
			//The shp file
			os = new FileOutputStream( fileName.getSaveShp( shapeType));
			ShpOutputStream shpOutStream = new ShpOutputStream(os);
			int[][] indexData = shpOutStream.writeGeometry( esriGraphicList);
			os.flush();
			os.close();
			
			//The shx file
			os = new FileOutputStream( fileName.getSaveShx( shapeType));
			ShxOutputStream shxOutStream = new ShxOutputStream(os);
			shxOutStream.writeIndex(indexData, shapeType);
			os.flush();
			os.close();
			
			//The Dbf File
			os = new FileOutputStream( fileName.getSaveDbf( shapeType));
			DbfOutputStream dbfOutStream = new DbfOutputStream(os);
			dbfOutStream.writeModel( getNewDbfTableModel( fc, shapeType));
			os.flush();
			os.close();
			
		} catch (IOException ioe) {
			ioe.printStackTrace();
			return -featureCount;
		}  catch (Exception e) {
			e.printStackTrace();
			return -featureCount;
		}
		return featureCount;
	}
	
	/**
	 * Stores column descriptors in terms of various attributes of field type,
	 * length and scale. This is used on output to keep the format consistent
	 * with the input data.
	 */
	static class ColumnDesc {
		String eName;  // long name of the column (use as database column name)
		String iName; // short name of the column (use as FeatureCollection attribute name)
		Class  iClass;    // internal storage class (within FeatureCollection)
		byte   dbType;    // type identifier for use in the database
		int    length;    // length of the field to use in the database (-1 for N/A)
		byte   scale;     // number of decimal positions (-1 for N/A)
		
		private static final Set jmarsSpecificType;
		private static final Map typeCodeToClass;
		private static final Map classToTypeCode;
		static {
			Set jmarsTypes = new HashSet();
			jmarsTypes.add(Boolean.class);
			jmarsTypes.add(Color.class);
			jmarsTypes.add(LineType.class);
			jmarsSpecificType = Collections.unmodifiableSet(jmarsTypes);
			
			Map tc2class = new HashMap();
			Map class2tc = new HashMap();
			for(Iterator i=jmarsSpecificType.iterator(); i.hasNext(); ){
				Class c = (Class)i.next();
				String name = baseName(c);
				String typeCode = name.substring(0,1);
				tc2class.put(typeCode, c);
				class2tc.put(c, typeCode);
			}
			typeCodeToClass = Collections.unmodifiableMap(tc2class);
			classToTypeCode = Collections.unmodifiableMap(class2tc);
		}

		
		public ColumnDesc(){
			eName = null;
			iName = null;
			iClass = String.class;
			dbType = DbfTableModel.TYPE_CHARACTER;
			length = -1;
			scale = -1;
		}
		
		/**
		 * Extract FormatDesc from the specified column of the dbf table model.
		 */
		public static ColumnDesc extractFromDbfTableModel(DbfTableModel model, int column){
			ColumnDesc fd = new ColumnDesc();
			
			fd.eName = model.getColumnName(column);
			fd.dbType = model.getType(column);
			fd.length = model.getLength(column);
			fd.scale = model.getDecimalCount(column);
			
			String tableColName = model.getColumnName(column);
			Class  tableColClass = String.class;
			
			if (isJMarsSpecificField(tableColName)){
				tableColClass = (Class)typeCodeToClass.get(getColumnTypeCode(tableColName));
				tableColName = getBaseColumnName(tableColName);
			}
			else if (fd.dbType == DbfTableModel.TYPE_NUMERIC)
				if (fd.scale <= 0)
					tableColClass = Integer.class;
				else
					tableColClass = Double.class;
			//else if (fd.dbType == DbfTableModel.TYPE_LOGICAL)
			//	tableColClass = Boolean.class;
			else if (fd.dbType == DbfTableModel.TYPE_CHARACTER || fd.dbType == DbfTableModel.TYPE_MEMO)
				tableColClass = String.class;
			else
				tableColClass = String.class;
			
			// If the field requires external->internal mapping, do it.
			if (intToExtNames.getRight(tableColName) != null)
				tableColName = (String)intToExtNames.getRight(tableColName);
			
			fd.iName = tableColName;
			fd.iClass = tableColClass;
			
			return fd;
		}

		/**
		 * Get default FormatDesc for a given attribute type from a Feature object.
		 * @param c Attribute's class as obtained from a Feature object.
		 */
		public static ColumnDesc getFormatDesc(Class c, String colName){
			ColumnDesc d = new ColumnDesc();

			d.iName = colName;
			String eName = d.iName;
			
			if (intToExtNames.getLeft(eName) != null)
				eName = (String)intToExtNames.getLeft(eName);
			
			d.iClass = c;
			if (Byte.class.isAssignableFrom(c)){
				d.dbType = DbfTableModel.TYPE_NUMERIC;
				d.length = 4;
				d.scale = 0;
			}
			else if (Short.class.isAssignableFrom(c)){
				d.dbType = DbfTableModel.TYPE_NUMERIC;
				d.length = 6;
				d.scale = 0;
			}
			else if (Integer.class.isAssignableFrom(c)){
				d.dbType = DbfTableModel.TYPE_NUMERIC;
				d.length = 11;
				d.scale = 0;
			}
			else if (Long.class.isAssignableFrom(c)){
				d.dbType = DbfTableModel.TYPE_NUMERIC;
				d.length = 21;
				d.scale = 0;
			}
			else if (Float.class.isAssignableFrom(c)){
				d.dbType = DbfTableModel.TYPE_NUMERIC;
				d.length = 12;
				d.scale = 11;
			}
			else if (Double.class.isAssignableFrom(c)){
				d.dbType = DbfTableModel.TYPE_NUMERIC;
				d.length = 18;
				d.scale = 15;
			}
			else if (Boolean.class.isAssignableFrom(c)){
				d.dbType = DbfTableModel.TYPE_CHARACTER;
				d.length = 5;
				d.scale = -1;
				eName = getQualifiedName(eName, c);
			}
			else if (Color.class.isAssignableFrom(c)){
				d.dbType = DbfTableModel.TYPE_CHARACTER;
				d.length = 15;
				d.scale = -1;
				eName = getQualifiedName(eName, c);
			}
			else if (LineType.class.isAssignableFrom(c)){
				d.dbType = DbfTableModel.TYPE_CHARACTER;
				d.length = 2;
				d.scale = -1;
				eName = getQualifiedName(eName, c);
			}
			else {
				d.dbType = DbfTableModel.TYPE_CHARACTER;
				d.length = -1;
				d.scale = -1;
			}
			
			d.eName = eName;
			
			return d;
		}
		
		private static String getColumnTypeCode(String columnName){
			String[] pcs = columnName.split("_");
			if (pcs.length < 2 || pcs[0].length() != 2)
				return " ";
			
			return pcs[0].substring(1,2);
		}
		
		public static String baseName(Class c){
			String[] pcs = c.getName().split("\\.");
			return pcs[pcs.length-1];
		}
		
		public static String getBaseColumnName(String columnName){
			if (!isJMarsSpecificField(columnName))
				return columnName;
			
			String[] pcs = columnName.split("_", 2);
			return pcs[pcs.length-1];
		}
 		private static String getQualifiedName(String columnName, Class c){
 			String typeCode = (String)classToTypeCode.get(c);
			return "j"+typeCode+"_"+columnName;
		}
		private static boolean isJMarsSpecificField(String columnName){
			String[] pcs = columnName.split("_");
			if (pcs.length >= 2 && pcs[0].length() == 2 && 'j' == pcs[0].charAt(0))
				return true;
			return false;
		}
	}


	private int getMaxFieldLength(FeatureCollection fc, Field field){
		int maxLength = 0;
		
		for(Iterator i=fc.featureIterator(); i.hasNext(); ){
			Feature f = (Feature)i.next();
			Object val;
			
			if ((val = f.getAttribute(field)) != null){
				int len = val.toString().length();
				if (len > maxLength)
					maxLength = len;
			}
		}
		
		return maxLength;
	}
	
	private String replaceUnusable(String fieldName){
		return fieldName.replaceAll(" ", "_");
	}
	
	// on SAVING:
	// get a DbfTableModel based on columns from the FeatureTable.
	private DbfTableModel getNewDbfTableModel(FeatureCollection fc, int shapeType)
	{
		java.util.List schema = new ArrayList(fc.getSchema());
		/* remove fields which shouldn't end up in the dbf file */
		schema.remove(Field.FIELD_PATH);
		schema.remove(FeatureProviderESRI.FEATURE_TYPE);
		schema.remove(FeatureProviderESRI.FILE);
		
		// Keep track of field names so that duplicate columns do not exist. 
		Set predefColumnNames = new HashSet();
		DecimalFormat formatter = new DecimalFormat("#");
		
		// Create and configure the output model.
		DbfTableModel model  = new DbfTableModel(schema.size());
		for(int col = 0; col < schema.size(); col++){
			Field field = (Field)schema.get(col);
			
			// Configure columns from the FeatureCollection as it exists right now.
			ColumnDesc fd;

			// Get column descriptor which was stored during load(). If none exists
			// generate a default descriptor using the column-class and column-name.
			fd = (ColumnDesc)props.getColumnDesc(fc).get(field);
			if (fd == null)
				fd = ColumnDesc.getFormatDesc(field.type, field.name);
			
			// Truncate columns longer than 10 characters to 10 characters.
			String fieldName = fd.eName;
			if (fieldName.length() > 10)
				fieldName = fieldName.substring(0,10);
			
			// Replace unusable characters with underbars.
			fieldName = replaceUnusable(fieldName);
			
			// If a duplicate name is encountered, replace the trailing characters
			// with serial numbers.
			int serial = 1;
			int nameLength = fieldName.length();
			while(predefColumnNames.contains(ColumnDesc.getBaseColumnName(fieldName).toLowerCase())){
				String suffix = formatter.format(serial++);
				fieldName = fieldName.substring(0, Math.min(nameLength, 10-suffix.length())) + suffix;
			}
			predefColumnNames.add(ColumnDesc.getBaseColumnName(fieldName).toLowerCase());
			
			model.setColumnName(col, fieldName);
			model.setType(col, fd.dbType);
			
			if (fd.length >= 0)
				model.setLength(col, fd.length);
			else if (fd.dbType == DbfTableModel.TYPE_CHARACTER)
				model.setLength(col, getMaxFieldLength(fc, field));
			
			if (fd.scale >= 0)
				model.setDecimalCount(col, fd.scale);
		}

		// Fill the table model with values.
		int  row = 0;
		for(Iterator fi = fc.featureIterator(); fi.hasNext(); ){
			Feature feature = (Feature)fi.next();
			Point2D center = null; // feature center
			
			// Skip all features that are not of the requested shape type.
			if (featureTypeToShapeType(feature.getPath().getType()) != shapeType)
				continue;
			
			int col = 0;
			ArrayList rec = new ArrayList(schema.size());
			for(Iterator si = schema.iterator(); si.hasNext(); ){
				Field field = (Field)si.next();
				
				Object val = feature.getAttribute(field);
				if (val == null) {
					val = model.getEmptyDefaultForType(model.getType(col));
				}
				
				if (val instanceof Number){
					if (!(val instanceof Double))
						// Openmap's DbfOutputStream.writeRecords(tableModel) has an error in
						// it's code which writes non-Double numeric values.
						val = new Double(((Number)val).doubleValue());
				}
				else if (val instanceof Color)
					val = colorToString((Color)val);
				else if (val instanceof LineType)
					val = val.toString();
				else if (val instanceof Boolean)
					// It also does not handle logical values.
					val = val.toString();
				else
					val = val.toString();
				
				rec.add(val);
				col++;
			}
			model.addRecord(rec);
			row++;
		}
		return model;
	}



    // ------ SAVING AND LOADING-----//



    private String colorToString( Color color){
	return "" + 
	    color.getRed()   + " " + 
	    color.getGreen() + " " + 
	    color.getBlue()  + " " + 
	    color.getAlpha();
    }
    
    private Color stringToColor( String string){
	    if (string == null){
		    return null;
	    }
	    String [] s = string.split(" ");
	    return new Color(Integer.parseInt(s[0]),
			     Integer.parseInt(s[1]),
			     Integer.parseInt(s[2]),
			     Integer.parseInt(s[3]));
    }
    

	

    // an inner class for maintaining the files used in both SAVING and LOADING.
    private class FileName {

	// names of all the files that are saved and loaded.
	private String    shortFileName; // no extention
	private String    shpFileName;   // ".shp" extention
	private String    dbfFileName;   // ".shx" extention
	private String    shxFileName;   // ".dbf" extention
	private int       type = SHAPE_TYPE_NONE;          // the feature type of the files.
	private String [] saveShpFileName = new String[3];   // shape type and ".shp" extention
	private String [] saveDbfFileName = new String[3];   // shape type and ".shx" extention
	private String [] saveShxFileName = new String[3];   // shape type and ".dbf" extention
	
	public FileName(){
	}

	// Constructor with type preset.
	public FileName(int type){
		this.type = type;
	}

	public String getShort(){
	    return shortFileName;
	}
	public String getShp(){
	    return shpFileName;
	}
	public String getShx(){
	    return shxFileName;
	}
	public String getDbf(){
	    return dbfFileName;
	}
	public int getShapeType(){
	    return type;
	}
	public String getSaveShp(int shape){
	    switch (shape){
	    case SHAPE_TYPE_POINT:
		return saveShpFileName[0];
	    case SHAPE_TYPE_POLYLINE:
		return saveShpFileName[1];
	    case SHAPE_TYPE_POLYGON:
		return saveShpFileName[2];
	    default:
		return null;
	    }
	}
	public String getSaveShx( int shape){
	    switch (shape){
	    case SHAPE_TYPE_POINT:
		return saveShxFileName[0];
	    case SHAPE_TYPE_POLYLINE:
		return saveShxFileName[1];
	    case SHAPE_TYPE_POLYGON:
		return saveShxFileName[2];
	    default:
		return null;
	    }
	}
	public String getSaveDbf( int shape){
	    switch (shape){
	    case SHAPE_TYPE_POINT:
		return saveDbfFileName[0];
	    case SHAPE_TYPE_POLYLINE:
		return saveDbfFileName[1];
	    case SHAPE_TYPE_POLYGON:
		return saveDbfFileName[2];
	    default:
		return null;
	    }
	}

	public String[] getLoadFiles(){
		return new String[] {
			getShp(), getShx(), getDbf()
		};
	}

	// set the filenames and check the files themselves for the shape type.
	// this is used for loading where the files have already been written.
	public  void     setLoadFiles( String fileName){
	    setFiles( fileName);

	    int shapeType = -1;
	    if (shpFileName!=null){
		try {
		    ShpInputStream shpInputStream =
			new ShpInputStream(new FileInputStream( shpFileName));
		    shapeType = shpInputStream.readHeader();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
	    }
	    type = shapeType;
	}

	// set the filenames and specifically set the shape type.
	// This is used for saving where the files have not actually been written yet.
	public  void     setSaveFiles( String fileName){
	    setFiles( fileName);
	}

	// sets all the filenames
	private void setFiles(String fileName){
	    shortFileName = fileName;
	    if (shortFileName.lastIndexOf(".shp")!= -1){
		shortFileName = shortFileName.substring(0, shortFileName.lastIndexOf(".shp"));
	    }
	    if (shortFileName.lastIndexOf(".shx")!= -1){
		shortFileName = shortFileName.substring(0, shortFileName.lastIndexOf(".shx"));
	    }
	    if (shortFileName.lastIndexOf(".dbf")!= -1){
		shortFileName = shortFileName.substring(0, shortFileName.lastIndexOf(".dbf"));
	    }
	    shpFileName   = shortFileName + ".shp";
	    shxFileName   = shortFileName + ".shx";
	    dbfFileName   = shortFileName + ".dbf";
	    if (shortFileName.lastIndexOf( ".polygon") != -1){
		shortFileName = shortFileName.substring(0, shortFileName.lastIndexOf(".polygon"));
	    }
	    if (shortFileName.lastIndexOf( ".line") != -1){
		shortFileName = shortFileName.substring(0, shortFileName.lastIndexOf(".line"));
	    }
	    if (shortFileName.lastIndexOf( ".point") != -1){
		shortFileName = shortFileName.substring(0, shortFileName.lastIndexOf(".point"));
	    }
	    
	    // The file names at save should be the same as the file names at load.
	    // If we knew that the features' type at load was point, then the output file
	    // should not be renamed from the input name.
	    // If we did not know the features' type at load or we didn't do a load ever
	    // then the file names may be altered appropriately to store the three types
	    // of data supported by ESRI.
	    switch(type){
	    case SHAPE_TYPE_POINT:
	    case SHAPE_TYPE_POLYGON:
	    case SHAPE_TYPE_POLYLINE:
		    saveShpFileName[0] = saveShpFileName[1] = saveShpFileName[2]  = shpFileName;
	    	saveShxFileName[0] = saveShxFileName[1] = saveShxFileName[2]  = shxFileName;
	    	saveDbfFileName[0] = saveDbfFileName[1] = saveDbfFileName[2]  = dbfFileName;
	    	break;
	    default:
	    case SHAPE_TYPE_NONE:
		    saveShpFileName[0]   = shortFileName + ".point.shp";
	    	saveShxFileName[0]   = shortFileName + ".point.shx";
	    	saveDbfFileName[0]   = shortFileName + ".point.dbf";
	    	saveShpFileName[1]   = shortFileName + ".line.shp";
	    	saveShxFileName[1]   = shortFileName + ".line.shx";
	    	saveDbfFileName[1]   = shortFileName + ".line.dbf";
	    	saveShpFileName[2]   = shortFileName + ".polygon.shp";
	    	saveShxFileName[2]   = shortFileName + ".polygon.shx";
	    	saveDbfFileName[2]   = shortFileName + ".polygon.dbf";
	    	break;
	    }
	}

	/**
	 * Returns the names of the shape-file pieces that exist already.
	 */
	public String[] getExistingSaveShapeFiles(){
		ArrayList existing = new ArrayList();
		
		if (type == SHAPE_TYPE_NONE){
			for(int i=0; i<saveShpFileName.length; i++){
				if ((new File(saveShpFileName[i])).exists())
					existing.add(saveShpFileName[i]);
				if ((new File(saveShxFileName[i])).exists())
					existing.add(saveShxFileName[i]);
				if ((new File(saveDbfFileName[i])).exists())
					existing.add(saveDbfFileName[i]);
			}
		}
		else {
			if ((new File(getSaveShp(type))).exists())
				existing.add(getSaveShp(type));
			if ((new File(getSaveShx(type))).exists())
				existing.add(getSaveShx(type));
			if ((new File(getSaveDbf(type))).exists())
				existing.add(getSaveDbf(type));
		}
		
		return (String[])existing.toArray(new String[0]);
	}
	
	// on LOADING:
	// Return true if (.shp, .shx, .dbf) files exist.
	// Return false and display a error dialog if not.
	public boolean allShapeFilesExist(){
	    boolean hasShpFile = new File( shpFileName).exists();
	    boolean hasShxFile = new File( shxFileName).exists();
	    boolean hasDbfFile = new File( dbfFileName).exists();
	    if ( hasShpFile && hasShxFile && hasDbfFile){
		return true;
	    } else {
		String  missingFile = "";
		if (!hasShpFile){
		    missingFile += " .shp";
		}
		if (!hasShxFile){
		    missingFile += " .shx";
		}
		if (!hasDbfFile){
		    missingFile += " .dbf";
		}
		JOptionPane.showMessageDialog(Main.getLManager(),
					      "Unable to load shape file: " + getShort() + "\n"
					      + missingFile + " file(s) missing.\n");
		return false;
	    }
	}
    }


}


 


