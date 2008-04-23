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

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import edu.asu.jmars.util.Util;

/**
 * This read-only feature provider loads data from the results of an SQL
 * statement executed in the CTX postgresql database on db2.mars.asu.edu, e.g.:
 * 
 * <pre>
 * SELECT * FROM ctx NATURAL INNER JOIN ctx_webmap
 * </pre>
 * 
 * It picks out the rationale_desc column for the label and the points column to
 * build the shape. This gives the shapes the same nice curves that the webmap
 * has.
 * 
 * TODO: Before this can be added to the feature providers list in jmars.config,
 * it needs a proper data source. A server side script should probably pass
 * through the results of a select for records added or changed since the
 * client's last request.
 */
public class FeatureProviderCTX implements FeatureProvider {
	public String getDescription() {
		return "CTX outlines";
	}
	
	public File[] getExistingSaveToFiles(FeatureCollection fc, String baseName) {
		return null;
	}
	
	public String getExtension() {
		return null;
	}
	
	public boolean isFileBased() {
		return false;
	}

	public boolean isRepresentable(FeatureCollection fc) {
		return false;
	}
	
	private static String[] split(String line) {
		String[] values = line.split("\\|");
		for (int i = 0; i < values.length; i++) {
			values[i] = values[i].trim();
		}
		return values;
	}
	
	public FeatureCollection load(String fileName) {
		FeatureCollection fc = new SingleFeatureCollection();
		String[] lines;
		try {
			lines = Util.readLines(new FileInputStream(new File("ctx_points.sql")));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		String[] headers = split(lines[0]);
		Pattern isNumeric = Pattern.compile("^[0-9\\.\\+-]+$");
		int labelIdx = Arrays.asList(headers).indexOf("rationale_desc");
		int pointIdx = Arrays.asList(headers).indexOf("points");
		Field[] others = new Field[headers.length];
		List<Feature> features = new LinkedList<Feature>();
		for (int i = 2; i < lines.length; i++) {
			Feature f = new Feature();
			String[] values = split(lines[i]);
			if (values.length != headers.length) {
				continue;
			}
			try {
				for (int j = 0; j < headers.length; j++) {
					if (j == labelIdx) {
						f.setAttribute(Field.FIELD_LABEL, values[j]);
					} else if (j == pointIdx) {
						String[] coords = values[j].replaceAll("[^0-9\\+-\\.]", "").split(",");
						float[] fcoords = new float[coords.length];
						for (int k = 0; k < coords.length; k++) {
							fcoords[k] = Float.parseFloat(coords[k]);
						}
						FPath path = new FPath(fcoords, false, FPath.SPATIAL_WEST, true);
						f.setAttribute(Field.FIELD_PATH, path);
					} else {
						if (isNumeric.matcher(values[j]).matches()) {
							if (others[j] == null) {
								others[j] = new Field(headers[j], Double.class);
							}
							f.setAttribute(others[j], Double.parseDouble(values[j]));
						} else {
							if (others[j] == null) {
								others[j] = new Field(headers[j], String.class);
							}
							f.setAttribute(others[j], values[j]);
						}
					}
				}
				features.add(f);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		fc.addFeatures(features);
		return fc;
	}
	
	public int save(FeatureCollection fc, String fileName) {
		return 0;
	}
}
