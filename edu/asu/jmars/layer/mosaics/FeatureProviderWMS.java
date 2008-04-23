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


package edu.asu.jmars.layer.mosaics;

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.asu.jmars.layer.map2.MapSource;
import edu.asu.jmars.layer.map2.WMSMapServer;
import edu.asu.jmars.layer.map2.WMSMapSource;
import edu.asu.jmars.layer.util.features.FPath;
import edu.asu.jmars.layer.util.features.Feature;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.FeatureProvider;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.SingleFeatureCollection;

public class FeatureProviderWMS implements FeatureProvider {
	public static final Field FIELD_ABSTRACT = new Field("Abstract", String.class, false);
	public static final Field FIELD_MAP_SOURCE = new Field("Map Source", MapSource.class, false);
	
	private Map<Feature,MapSource> featToMapSrc = new HashMap<Feature,MapSource>();
	
	public String getDescription() {
		return "Mosaics";
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

	public FeatureCollection load(String urlString) {
		WMSMapServer ms = new WMSMapServer(urlString, 0, 2);
		FeatureCollection fc = new SingleFeatureCollection();
		featToMapSrc.clear();
		
		List<MapSource> mapSources = ms.getMapSources();
		
		for(MapSource s: mapSources){
			Rectangle2D bbox = ((WMSMapSource)s).getLatLonBoundingBox();
			if (bbox == null)
				continue; // skip features without a (lat,lon) bounding box.

			// TODO: Convert the bbox into ocentric coordinates.
			Feature f = new Feature();
			f.setPath(new FPath(new GeneralPath(bbox), FPath.SPATIAL_EAST));
			f.setAttribute(Field.FIELD_LABEL, s.getTitle());
			f.setAttribute(FIELD_ABSTRACT, s.getAbstract());
			f.setAttribute(FIELD_MAP_SOURCE, s);
			
			fc.addFeature(f);
			featToMapSrc.put(f, s);
		}
		
		return fc;
	}
	
	public Map<Feature,MapSource> getFeatureToMapSourceMap(){
		return Collections.unmodifiableMap(featToMapSrc);
	}

	public int save(FeatureCollection fc, String fileName) {
		throw new UnsupportedOperationException();
	}

}
