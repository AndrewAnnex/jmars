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


package edu.asu.jmars.layer.util;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;

import edu.asu.jmars.layer.map2.MyVicarReaderWriter;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.Util;
import edu.asu.jmars.util.VicarException;

public final class InMemElevationSource implements ElevationSource {
	private final DebugLog log = DebugLog.instance();
	
	static InMemElevationSource instance = null;
	
	public static InMemElevationSource getInstance(){
		if (instance == null){
			try {
				instance = new InMemElevationSource();
			}
			catch(Exception ex){
				throw new RuntimeException(ex);
			}
		}
		return instance;
	}

	public static final String altitudeFileUrlString = Config.get("themis2.alt.file.url",
			"http://jmars.asu.edu/internal/mola_16ppd_topo.vic");
	public static final int DEFAULT_ELEVATION = 3396; // 3396 Km 
	
	private final BufferedImage elevationImage;
	private final Raster elevationData;
	private final double base, multiplier;
	private final int ppd;
	private final int minVal, maxVal;
	
	private InMemElevationSource() throws IOException, VicarException {
		log.aprint("Retrieving mola elevation data (from: "+altitudeFileUrlString+"). This may take some time ... ");
		File altFile = Util.getCachedFile(altitudeFileUrlString, true);
		log.aprintln("done.");
		//try {
			elevationImage = MyVicarReaderWriter.read(altFile);
		//}
		//catch(Exception ex){
		//	log.aprintln("ERROR! Unable to load altitude data from: "+altitudeFileUrlString+".");
		//	elevationImage = new BufferedImage(36, 18, BufferedImage.TYPE_BYTE_GRAY);
		//}
		elevationData = elevationImage.getRaster();
		base = 3396d; //elevationData.getProperty("base");
		multiplier = 1/1000.0d; //1000; //elevationData.getProperty("multiplier");
		ppd = elevationImage.getWidth()/360; //elevationData.getProperty("ppd");
		minVal = min(elevationData);
		maxVal = max(elevationData);
	}
	
	private static int min(Raster r){
		final int w = r.getWidth();
		final int h = r.getHeight();
		
		int minVal = Integer.MAX_VALUE;
		for(int y = 0; y < h; y++)
			for(int x = 0; x < w; x++)
				minVal = Math.min(minVal, r.getSample(x, y, 0));
		
		return minVal;
	}
	
	private static int max(Raster r){
		final int w = r.getWidth();
		final int h = r.getHeight();
		
		int maxVal = Integer.MIN_VALUE;
		for(int y = 0; y < h; y++)
			for(int x = 0; x < w; x++)
				maxVal = Math.max(maxVal, r.getSample(x, y, 0));
		
		return maxVal;
	}
	
	public int getPPD(){
		return ppd;
	}
	
	public double getMinElevation(){
		return (minVal * multiplier) + base;
	}
	
	public double getMaxElevation(){
		return (maxVal * multiplier) + base;
	}
	
	public double getElevation(HVector v) {
		return getElevation(new HVector[]{ v })[0];
	}

	public double[] getElevation(HVector[] v) {
		double[] alts = new double[v.length];
		for(int i=0; i<v.length; i++){
			double lon = v[i].lonE() % 360;
			double lat = v[i].latC();
			int x = (int) Math.round(lon*(360.0/361.0) * ppd);
			int y = (int) Math.round((90-lat)*(180.0/181.0) * ppd);
			alts[i] = (elevationData.getSample(x, y, 0) * multiplier) + base;
		}
		return alts;
	}

}
