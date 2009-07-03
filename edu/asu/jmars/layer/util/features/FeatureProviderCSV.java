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

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;

import edu.asu.jmars.util.Util;

public class FeatureProviderCSV implements FeatureProvider {
	public String getDescription() {
		return "CSV points file";
	}
	
	public File[] getExistingSaveToFiles(FeatureCollection fc, String baseName) {
		String[] names = {baseName, baseName + getExtension()};
		for (String name: names) {
			File f = new File(name);
			if (f.exists()) {
				return new File[]{f};
			}
		}
		return new File[]{};
	}
	
	public String getExtension() {
		return ".csv";
	}
	
	public boolean isFileBased() {
		return true;
	}
	
	public boolean isRepresentable(FeatureCollection fc) {
		return true;
	}
	
	/** the names of longitude columns, in ascending order of preference */
	private static final List<String> lonAliases = Arrays.asList("lon","long","longitude");
	/** the names of latitude columns, in ascending order of preference */
	private static final List<String> latAliases = Arrays.asList("lat","latitude");
	/** the names under which polar radius can be found */
	private static final String[] polarRadiusLabels = {"polar_radius", "c_axis_radius"};
	/** the names under which equat radius can be found */
	private static final String[] equatRadiusLabels = {"equat_radius", "a_axis_radius", "b_axis_radius"};
	/** the names under which longitude direction can be found */
	private static final String[] lonDirLabels = {"lon_dir", "longitude_direction", "positive_longitude_direction"};
	
	private enum Type {
		INT(Integer.class), DBL(Double.class), STR(String.class);
		public final Class<?> c;
		Type(Class<?> c) {
			this.c = c;
		}
		public Object fromString(String s) {
			switch (this) {
			case INT: return new Scanner(s).nextInt();
			case DBL: return new Scanner(s).nextDouble();
			case STR: return s;
			default: return null;
			}
		}
	}
	
	public FeatureCollection load(String fileName) {
		CsvReader csv = null;
		try {
			int lonCol = -1;
			int latCol = -1;
			String[] names = null;
			int[] otherFields = null;
			int otherCount = 0;
			
			// try various delimiters to find the lon and lat columns
			char delim = '\0';
			for (char d: new char[]{',','\t','|'}) {
				lonCol = latCol = -1;
				otherCount = 0;
				csv = new CsvReader(new FileReader(fileName), d);
				csv.setUseComments(true);
				csv.readHeaders();
				names = csv.getHeaders();
				otherFields = new int[names.length];
				
				// find most articulate lon and lat column names
				for (int i = 0; i < names.length; i++) {
					String name = names[i].trim().toLowerCase();
					if (lonAliases.contains(name)) {
						if (lonCol < 0 || lonAliases.indexOf(csv.getHeader(lonCol)) < lonAliases.indexOf(name)) {
							lonCol = i;
						}
					} else if (latAliases.contains(name)) {
						if(latCol < 0 || latAliases.indexOf(csv.getHeader(latCol)) < latAliases.indexOf(name)) {
							latCol = i;
						}
					} else {
						otherFields[otherCount++] = i;
					}
				}
				csv.close();
				
				// if we found our geometry, then stop, otherwise try another delimiter
				if (lonCol >= 0 && latCol >= 0) {
					delim = d;
					break;
				}
			}
			
			if (delim == '\0') {
				throw new IllegalArgumentException("Could not find geometry columns");
			}
			
			// parse the header comments and types in each cell
			Type[] types = new Type[otherCount];
			Arrays.fill(types, Type.INT);
			csv = new CsvReader(new FileReader(fileName), delim);
			double polarRadius = 1;
			double equatRadius = 1;
			boolean west = false;
			while (true) {
				csv.readHeaders();
				int cols = csv.getHeaderCount();
				String row = csv.getRawRecord().trim().toLowerCase().replaceAll("^[ \\t#]+", "");
				Double dvalue = null;
				String svalue = null;
				if (null != (dvalue = getDoubleLabel(polarRadiusLabels, row))) {
					polarRadius = dvalue.doubleValue();
				} else if (null != (dvalue = getDoubleLabel(equatRadiusLabels, row))) {
					if (equatRadius == 1) {
						equatRadius = dvalue.doubleValue();
					} else if (equatRadius != dvalue.doubleValue()) {
						throw new IllegalArgumentException("Two equatorial radii must be equal");
					}
				} else if (null != (svalue = getStringLabel(lonDirLabels, row))) {
					svalue = svalue.trim().toLowerCase();
					if (svalue.equals("w") || svalue.equals("west")) {
						west = true;
					} else if (svalue.equals("e") || svalue.equals("east")) {
						west = false;
					}
				} else if (cols == names.length) {
					// found header record, break out to start parsing normal records
					break;
				}
			}
			
			double scalar;
			if (polarRadius == equatRadius) {
				scalar = 1;
			} else {
				scalar = Math.pow(polarRadius / equatRadius, 2);
			}
			
			// read data records, ignoring all comments
			csv.setUseComments(true);
			int rows = 0;
			while (csv.readRecord()) {
				if (names.length != csv.getColumnCount()) {
					throw new IllegalArgumentException("CSV header count does not match row " + csv.getCurrentRecord());
				}
				for (int i = 0; i < otherCount; i++) {
					Type lastType = types[i];
					if (lastType.compareTo(Type.STR) < 0) {
						String cell = csv.get(otherFields[i]).trim();
						Scanner s = new Scanner(cell);
						Type type;
						if (s.hasNextInt()) {
							type = Type.INT;
						} else if (s.hasNextDouble()) {
							type = Type.DBL;
						} else {
							type = Type.STR;
						}
						if (lastType.compareTo(type) < 0) {
							types[i] = type;
						}
					}
				}
				rows ++;
			}
			csv.close();
			
			// create the fields
			FeatureCollection fc = new SingleFeatureCollection();
			for (int i = 0; i < otherCount; i++) {
				fc.addField(new Field(names[otherFields[i]], types[i].c));
			}
			List<Field> fields = fc.getSchema();
			
			List<Feature> features = new ArrayList<Feature>();
			
			csv = new CsvReader(new FileReader(fileName), delim);
			csv.setUseComments(true);
			csv.readHeaders();
			while (csv.readRecord()) {
				Feature f = new Feature();
				for (int i = 0; i < otherCount; i++) {
					String cell = csv.get(otherFields[i]).trim();
					Object o = types[i].fromString(cell);
					f.setAttribute(fields.get(i), o);
				}
				float[] coords = {
					(float)parseLon(csv.get(lonCol), west),
					(float)parseLat(csv.get(latCol), true, scalar)
				};
				f.setPath(new FPath(coords, false, FPath.SPATIAL_EAST, false));
				features.add(f);
			}
			fc.addFeatures(features);
			return fc;
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException(e.getMessage());
		} finally {
			if (csv != null) {
				csv.close();
			}
		}
	}
	
	private static final DecimalFormat fmt = new DecimalFormat("#.#####");
	
	/**
	 * returns the east longitude from the given string, handling 'E' and 'W'
	 * suffixes, and using the given value of 'west' as the default when no
	 * suffix is present.
	 */
	private static double parseLon(String lon, boolean west) {
		lon = lon.toLowerCase();
		ParsePosition pos = new ParsePosition(0);
		double f = fmt.parse(lon, pos).doubleValue();
		if (west && lon.indexOf("e", pos.getIndex()) >= 0) {
			west = false;
		} else if (!west && lon.indexOf("w", pos.getIndex()) >= 0) {
			west = true;
		}
		if (west) {
			f = -f;
		}
		return f;
	}
	
	/**
	 * returns the ocentric latitude from the given string, handling 'N' and 'S'
	 * suffixes if present, converting the value from ographic to ocentric with
	 * the given ellipsoidal scalar.
	 */
	private static double parseLat(String lat, boolean north, double scalar) {
		lat = lat.toLowerCase();
		ParsePosition pos = new ParsePosition(0);
		double f = fmt.parse(lat, pos).doubleValue();
		if (north && lat.indexOf("s", pos.getIndex()) >= 0) {
			north = false;
		} else if (!north && lat.indexOf("n", pos.getIndex()) >= 0) {
			north = true;
		}
		if (!north) {
			f = -f;
		}
		if (scalar != 1) {
			f = Util.atanD(Util.tanD(f) * scalar);
		}
		return f;
	}

	/**
	 * returns the string value of the given label, as with
	 * {@link #getDoubleValue()} but where value may be any string after the
	 * delimiter.
	 */
	private static String getStringLabel(String[] aliases, String row) {
		Scanner s = new Scanner(row);
		try {
			for (String alias: aliases) {
				if (null != s.findInLine(alias)) {
					if (null != s.findInLine("[\\t =]+")) {
						return s.nextLine();
					}
				}
			}
		} catch (Exception e) {
		}
		return null;
	}
	
	/**
	 * returns the Double value of the given label, which should be in the form
	 * {a}{sep}{value}, where {a} can be any of the identities given by
	 * <code>aliases</code>, {sep} is typically whitespace or an '=' sign, and
	 * {value} is the floating point value of the label.
	 */
	private static Double getDoubleLabel(String[] aliases, String row) {
		Scanner s = new Scanner(row);
		try {
			for (String alias: aliases) {
				if (null != s.findInLine(alias)) {
					if (null != s.findInLine("[\\t =]+")) {
						return s.nextDouble();
					}
				}
			}
		} catch (Exception e) {
		}
		return null;
	}
	
	public int save(FeatureCollection fc, String fileName) {
		CsvWriter csv = null;
		try {
			List<Field> fields = new ArrayList<Field>(fc.getSchema());
			fields.remove(Field.FIELD_PATH);
			String[] otherFields = new String[2 + fields.size()];
			
			// write header
			csv = new CsvWriter(new FileWriter(fileName), '\t');
			otherFields[0] = lonAliases.get(lonAliases.size()-1);
			otherFields[1] = latAliases.get(latAliases.size()-1);
			for (int i = 0; i < fields.size(); i++) {
				otherFields[i+2] = fields.get(i).name;
			}
			csv.writeRecord(otherFields);
			
			// write rows
			int rows = 0;
			DecimalFormat fmt = new DecimalFormat("#.########");
			for (Feature f: (List<Feature>)fc.getFeatures()) {
				Point2D p = f.getPath().getSpatialEast().getCenter();
				otherFields[0] = fmt.format(p.getX());
				otherFields[1] = fmt.format(p.getY());
				int i = 2;
				for (Field field: fields) {
					Object o = f.getAttribute(field);
					otherFields[i++] = o == null ? "" : o.toString();
				}
				csv.writeRecord(otherFields);
				rows ++;
			}
			return rows;
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		} finally {
			if (csv != null) {
				csv.close();
			}
		}
	}
}
