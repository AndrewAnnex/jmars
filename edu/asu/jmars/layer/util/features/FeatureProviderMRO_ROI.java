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
import java.text.NumberFormat;
import java.util.*;

import javax.swing.*;

import edu.asu.jmars.*;
import edu.asu.jmars.util.*;

	

/** 
 * A class for saving or loading features to a ROI shape file.
 *
 */

public class FeatureProviderMRO_ROI implements FeatureProvider {
	private static DebugLog log = DebugLog.instance();

	public String getExtension () {
		return ".roif";
	}

	public String getDescription () {
		return "MRO ROI File";
	}

	public boolean isFileBased () {
		return true;
	}

	ProjObj po;

	public FeatureProviderMRO_ROI() {
		// Junit testing does not run inside JMARS.  The following 
		// is needed to get the proper spatial<->world conversions. 
		if (Main.PO == null){
			po = new ProjObj.Projection_OC( 0,0);
		} else {
			po = Main.PO;
		}
	}

	public static final Field  SITE_ID        = new Field( "SiteID",       Integer.class, true);
	public static final Field  SITE_NAME      = new Field( "SiteName",    String.class, true);
	public static final Field  INSTRUMENT     = new Field( "Instrument",    String.class, true);
	public static final Field  PRIORITY       = new Field( "Priority",    Integer.class, true);
	public static final Field  LSUBSMIN       = new Field( "LsubSmin",   Double.class, true);
	public static final Field  LSUBSMAX       = new Field( "LsubSmax",  Double.class, true);
	public static final Field  INCIDENCE_MIN  = new Field( "IncidenceMin",   Double.class, true);
	public static final Field  INCIDENCE_MAX  = new Field( "IncidenceMax",  Double.class, true);
	public static final Field  EMISSION_MIN   = new Field( "EmissionMin", Double.class, true);
	public static final Field  EMISSION_MAX   = new Field( "EmissionMax",  Double.class, true);
	public static final Field  ROLL_MIN       = new Field( "RollMin", Double.class, true); 
	public static final Field  ROLL_MAX       = new Field( "RollMax", Double.class, true);
	public static final Field  COMMENT        = Field.FIELD_LABEL; 

	public static final int SITEID_INDEX = 0;
	public static final int SITENAME_INDEX = 1;
	public static final int INSTRUMENT_INDEX = 2;
	public static final int PRIORITY_INDEX = 3;
	public static final int LSUBSMIN_INDEX = 4;
	public static final int LSUBSMAX_INDEX = 5;
	public static final int INCIDENCE_MIN_INDEX = 6;
	public static final int INCIDENCE_MAX_INDEX = 7;
	public static final int EMISSION_MIN_INDEX = 8;
	public static final int EMISSION_MAX_INDEX = 9;
	public static final int ROLL_MIN_INDEX = 10;
	public static final int ROLL_MAX_INDEX = 11;
	public static final int COMMENT_INDEX = 12;
	public static final int POLY_INDEX = 13;
	
	public static final Field [] fieldArray = {
		SITE_ID,     
		SITE_NAME,     
		INSTRUMENT,    
		PRIORITY,
		LSUBSMIN,
		LSUBSMAX,
		INCIDENCE_MIN,
		INCIDENCE_MAX,
		EMISSION_MIN,
		EMISSION_MAX,
		ROLL_MIN,
		ROLL_MAX,
		COMMENT
	};

	public FeatureCollection load(String name) 
	{
		File file = filterFile(name);
		SingleFeatureCollection fc = null;
		// read lines from the file and place them into the FeatureTable.
		try {
			BufferedReader inStream = new BufferedReader(new FileReader( file.getAbsolutePath()));
			if (processHeader(inStream, name) == false) {
				log.aprintln("Cannot read invalid ROI file.");
			} else {
				// process the lines of the file and add them to the featureTable.
				fc = processRecords(inStream, file.toString());
			}
		} catch (FileNotFoundException fnfe) {
			JOptionPane.showMessageDialog(null, "Error loading MRO_ROI file: "
				+ file.getName() + "\n");
		}
		return fc;
	}

	// read header lines, checking for a valid file.
	// return whether the file is a valid ROI file.
	private boolean processHeader( BufferedReader inStream, String fileName){
		boolean fileVerified = false;
		boolean firstLine    = true;
		String  inputLine    = null;
		
		// while there are header lines left to process...
		do {
			
			// read the next line.
			try{
				inputLine = inStream.readLine().trim();
			} catch (IOException ioe){
				log.println("Error reading file " + fileName + ": " + ioe.getMessage());
				log.printStack(-1);
				break;
			}
			
			// process the line.
			if (inputLine != null && inputLine.length() > 0) {
				
				// if a line starts with '##' we have come to the end of the header.
				if (inputLine.startsWith( "##")) {
					break;
				}
				
				// process the non-terminal header lines. 
				// If the FIRST line is "FILE_TYPE:ROI", then the file is verified as a legitimate ROI file.
				// Currently, this is the only header line we are interested in.
				else if (inputLine.startsWith( "#")) {
					String [] keyValue = inputLine.substring(1).split( "[ :\\t]");
					if (firstLine==true) {
						int lineOK=0;
						for (int i=0; i<keyValue.length; i++){
							if (keyValue[i].trim().equals("FILE_TYPE") && lineOK==0){
								lineOK++;
							}
							if (keyValue[i].trim().equals("ROI") && lineOK==1){
								lineOK++;
							}
						}
						if (lineOK==2){
							fileVerified =true;
							firstLine    =false;
						}
					}
				}
			}
			
		} while (inputLine != null);
		
		return fileVerified;
	}
	
	public static String escapeText(String text){
		if (text == null)
			return null;
		
		text = text.replaceAll("\\\\", "\\\\\\\\");
		text = text.replaceAll(",", "\\,");
		return text;
	}
	public static String unescapeText(String text){
		if (text == null)
			return null;
		
		text = text.replaceAll("\\\\,", ",");
		text = text.replaceAll("\\\\\\\\", "\\\\");
		return text;
	}
	
	// read records and place them in the FeatureTable.
	// returns the number of rows read in.
	public SingleFeatureCollection  processRecords( BufferedReader inStream, String fileName){
		String          inputLine        = null;

		// Build a FeatureCollection.
		SingleFeatureCollection fc = new SingleFeatureCollection();
		
		// Setup schema
		fc.addField(Field.FIELD_FEATURE_TYPE);
		fc.addField(Field.FIELD_PATH);
		for(int i=SITEID_INDEX; i<=COMMENT_INDEX; i++)
			fc.addField(fieldArray[i]);
		
		// Build the List of Features for the FeatureCollection.
		java.util.List featureList = new  ArrayList();

		do {
			// read the next line.
			try{
				inputLine = inStream.readLine();
			} catch (IOException ioe){
				log.println("Error reading file " + fileName + ": " + ioe.getMessage());
				log.printStack(-1);
				break;
			}
			
			if (inputLine != null && inputLine.length() > 0) {
				// skip blank lines and comment lines.
				inputLine = inputLine.trim();
				if (inputLine.length()==0 || inputLine.startsWith("#")){
					continue;
				}

				// begin building a new feature.
				Feature feature = new Feature();
				feature.setAttribute( Field.FIELD_FEATURE_TYPE, FeatureUtil.TYPE_STRING_POLYGON);
				
				// parse out line into ROI row
				String [] roiLine = inputLine.split("(?<!\\\\),");

				// set the non-path fields.
				for(int i=SITEID_INDEX; i<=COMMENT_INDEX; i++){
					try {
						Object val = null;
						Field field = fieldArray[i];

						if (roiLine[i].length() == 0)
							val = null;
						else if (field.type == String.class)
							val = unescapeText(roiLine[i]);
						else if (field.type == Integer.class)
							val = new Integer(roiLine[i]);
						else if (field.type == Double.class)
							val = new Double(roiLine[i]);
						else
							log.aprintln("Unhandled field type "+field.type+" encountered.");
						
						if (val != null)
							feature.setAttributeQuiet(field, val);
					}
					catch(Exception e){
						log.println("Exception occurred while setting field "+fieldArray[i].name+": "+e.getMessage());
					}
				}

				// Build the path of the feature.

				// must have a whole number of lat/lon pairs and at least one pair
				int polySize = roiLine.length - POLY_INDEX;
				if (polySize < 2 || polySize % 2 != 0) {
					continue;
				}

				// read east-leading lat/lon values
				Point2D[] vertices = new Point2D[polySize/2];
				try {
					for (int i = 0; i < vertices.length; i ++) {
						// read strings
						String lat = roiLine[i*2 + POLY_INDEX + 0].trim();
						String lon = roiLine[i*2 + POLY_INDEX + 1].trim();

						// parse latitude
						float latVal = Float.parseFloat(lat);
						if (Math.abs(latVal) > 90.0)
							throw new NumberFormatException("Illegal latitude value: " + vertices[i]);

						// parse longitude (converting west-leading to east-leading as necessary)
						boolean west = lon.endsWith("W");
						if (west)
							lon = lon.substring(0, lon.length() - 1);
						float lonVal = Float.parseFloat(lon);
						if (west)
							lonVal = (float)FeatureUtil.lonNorm(-lonVal);

						// add new point
						vertices[i] = new Point2D.Float(lonVal, (float)Util.ographic2ocentric(latVal));
					}
				} catch (NumberFormatException e) {
					log.aprintln("Skipping bad row: " + e.getMessage());
					continue;
				}

				FPath path = new FPath (vertices, FPath.SPATIAL_EAST, true);
				feature.setAttribute(Field.FIELD_PATH, path.getSpatialWest());

				// add the fully built feature to the list.
				featureList.add(feature);
			}
		} while (inputLine != null);

		// Set the features of the FeatureCollection.
		fc.addFeatures( featureList);
		
		return fc;
	}
	
	public boolean isRepresentable(FeatureCollection fc){
		int[] featTypes = FeatureUtil.getRepresentedFeatureTypes(fc.getFeatures());
		return (featTypes.length == 1 && featTypes[0] == FPath.TYPE_POLYGON);
	}
	public File[] getExistingSaveToFiles(FeatureCollection fc, String name) {
		File file = filterFile(name);
		if (file.exists())
			return new File[]{file};
		return new File[]{};
	}

	public int save(FeatureCollection fc, String name)
	{
		File file = filterFile(name);
		int  featuresSaved = 0;
		int  notSaved = 0;
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(3);

		// save the rows out to the file.
		try {
			BufferedWriter  outStream = new BufferedWriter(new FileWriter( file.getAbsolutePath()));
			
			// write the header.
			outStream.write( "#FILE_TYPE: ROI\n");
			outStream.write( "##\n");
			
			// write rows 
			Iterator fi = fc.getFeatures().iterator();
			while (fi.hasNext()){
				Feature feature = (Feature)fi.next();

				// only process polygons.
				if (feature.getPath().getType() != FPath.TYPE_POLYGON) {
					notSaved++;
					continue;
				}

				// We build a list of strings to output and write them in a single go.
				List vals = new ArrayList();
				
				// Add field values to the list being built, use empty string for values that
				// we don't have.
				for(int i=SITEID_INDEX; i<=COMMENT_INDEX; i++){
					Object v = feature.getAttribute(fieldArray[i]);
					if (fieldArray[i].type == String.class && v != null)
						v = escapeText((String)v);
					vals.add(v==null?"":v.toString());
				}

				// Add vertices to this list.
				FPath path = (FPath)feature.getAttribute(Field.FIELD_PATH);
				Point2D[] pts = path.getSpatialEast().getVertices();
				for(int i=0; i<pts.length; i++){
					vals.add(nf.format(Util.ocentric2ographic(pts[i].getY())));
					vals.add(nf.format(pts[i].getX()));
				}

				outStream.write(Util.join(",", (String[])vals.toArray(new String[0]))+"\n");

				featuresSaved++;
			}

			outStream.flush();
			outStream.close();

		}  catch (Exception e) {
			JOptionPane.showMessageDialog( null, "Error saving MRO_ROI file: " + file.getName() + "\n");
			return 0;
		}

		JOptionPane.showMessageDialog( null,
				"" + featuresSaved + " ROI(s) were saved to " + file.getName() + "\n"+
				(notSaved==0?"": ""+ notSaved +" ROI(s) were not saved."));
		return featuresSaved;
	}

	// If the file does not end with the file type extention, it should
	// be added to the end of the file name.
	private File filterFile( String fileName){
		if (!fileName.endsWith( getExtension ())){
			fileName += getExtension ();
		}
		return new File( fileName);
	}

} // end:  class FeatureProviderMRO_ROI.java 
 


