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
import java.io.*;
import java.util.*;

import javax.swing.*;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.*;

// this means that the GML.java class needs to be moved to edu.asu.jmars.util
//import edu.asu.jmars.layer.shape.*;
        

/**
 * A class for saving or loading features to a GML shape file. * The file to
 * either save or load to is fetched within the methods themselves.
 */
public class FeatureProviderGML implements FeatureProvider {
	public String getExtension () {
		return ".gml";
	}

	public String getDescription () {
		return "GML Files";
	}

	public boolean isFileBased () {
		return true;
	}

	private DebugLog log = DebugLog.instance();

	public static final Field ID        = new Field( "id",           String.class, false);

	public FeatureCollection load(String name) {
		// Build a new FeatureProvider inside a new FeatureCollection
		SingleFeatureCollection fc = new SingleFeatureCollection();

		// Build the List of Features for the FeatureCollection.
		final File file = filterFile(name);
		GML.Feature[] gmlFeatures = GML.File.read(file.getAbsolutePath());
		if ( gmlFeatures == null || gmlFeatures.length < 1) {
			return null;
		} else {
			List featureList = new ArrayList();
			for (int i=0; i < gmlFeatures.length; i++) {
				GML.Feature gmlFeature = gmlFeatures[i];
				Feature     newFeature = new Feature();
				try {
					String featureTypeString = FeatureUtil.getFeatureTypeString(gmlFeatureTypeToFeatureType(gmlFeature.getType()));
					GeneralPath gp = gmlFeature.getGeneralPath();
					FPath path = new FPath (gp, FPath.SPATIAL_EAST);
					newFeature.setAttributeQuiet( ID,                       gmlFeature.getId());
					newFeature.setAttributeQuiet( Field.FIELD_LABEL,        gmlFeature.getDescription());
					newFeature.setAttributeQuiet( Field.FIELD_FEATURE_TYPE, featureTypeString);
					newFeature.setAttributeQuiet( Field.FIELD_PATH,         path.getSpatialWest());

					// Add the feature to the list.
					featureList.add( newFeature);
				}
				catch(Exception ex){
					log.aprintln("Feature "+i+" generated an error. Ignoring. Message: "+ex.getMessage());
				}
			}
			fc.addFeatures( featureList);
			return fc;
		}
	}

	public int gmlFeatureTypeToFeatureType(int gmlFeatureType){
		switch(gmlFeatureType){
			case GML.POINT: return FPath.TYPE_POINT;
			case GML.LINE: return FPath.TYPE_POLYLINE;
			case GML.POLYGON: return FPath.TYPE_POLYGON;
		}
		return FPath.TYPE_NONE;
	}

	public int featureTypeToGmlFeatureType(int featureType){
		switch(featureType){
			case FPath.TYPE_POINT: return GML.POINT;
			case FPath.TYPE_POLYLINE: return GML.LINE;
			case FPath.TYPE_POLYGON: return GML.POLYGON;
		}
		return GML.NONE;
	}

	public boolean isRepresentable(FeatureCollection fc){
		return true;
	}
	public File[] getExistingSaveToFiles(FeatureCollection fc, String name){
		File file = filterFile(name);
		if (file.exists())
			return new File[]{file};
		return new File[]{};
	}

	// writes out the specified features to the specified file.
	public int save(FeatureCollection fc, String name) {
		if (fc==null || fc.getFeatures()==null || fc.getFeatures().size()<1){
			JOptionPane.showMessageDialog(Main.getLManager(),
						      "Selection contains no rows. Save aborted.");
			return 0;
		}

		File file = filterFile(name);

		// convert the shape framework Features to GML.Feature objects.
		List gmlFeatures = new ArrayList();
		int i=1;
		for (Iterator fi = fc.getFeatures().iterator(); fi.hasNext(); i++){
			Feature f = (Feature)fi.next();

			int gmlShape = featureTypeToGmlFeatureType(f.getPath().getType());

			FPath path = (FPath)f.getAttribute( Field.FIELD_PATH);
			GeneralPath gp = path.getSpatialEast().getGeneralPath();

			String id = (String) f.getAttribute(ID);

			String desc = (String) f.getAttribute(Field.FIELD_LABEL);
			GML.Feature feature = new GML.Feature(gmlShape, gp, id, desc);
			gmlFeatures.add(feature);
		}
		// write the features out.
		int result = GML.File.write((GML.Feature[])gmlFeatures.toArray(new GML.Feature[0]), file.getAbsolutePath());
		if (result>0){
			JOptionPane.showMessageDialog( null, "Wrote " + result + " features to " + file.toString() + "\n");
		} 
		return result;
	}
	
	
	// If the file does not end with the file type extention, it should
	// be added to the end of the file name.
	private File filterFile( String fileName){
		if (!fileName.endsWith(getExtension()))
			fileName += getExtension();
		return new File(fileName);
	}
}
 


