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


package edu.asu.jmars.layer.map2.stages;


import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import edu.asu.jmars.layer.map2.AbstractStage;
import edu.asu.jmars.layer.map2.GrayRescaleToByteOp;
import edu.asu.jmars.layer.map2.MapAttr;
import edu.asu.jmars.layer.map2.MapData;
import edu.asu.jmars.layer.map2.StageUtil;
import edu.asu.jmars.util.DebugLog;

/**
 * Converts the image stored in the input MapData object to a
 * byte image. If the image is already a byte image or lower, it
 * is passed along as it is.
 * 
 * @author saadat
 */
public class GrayscaleStage extends AbstractStage implements Cloneable, Serializable {
	private static final long serialVersionUID = 2L;

	private static DebugLog log = DebugLog.instance();
	
	public GrayscaleStage(GrayscaleStageSettings settings) {
		super(settings);
	}
	
	public String getStageName() {
		return getSettings().getStageName();
	}
	
	public int getInputCount() {
		return 1;
	}
	
	public MapData process(int inputNumber, MapData data, Area changedArea) {
		MapData processedData = null;
		
		BufferedImage image = data.getImage();
		ColorModel cm = image.getColorModel();
		int nBands = cm.getNumColorComponents();
		if (nBands != 1)
			throw new IllegalArgumentException("Input images must be single band images.");
		
		GrayscaleStageSettings s = (GrayscaleStageSettings)getSettings();
		boolean autoMinMax;
		double minValue, maxValue;
		synchronized(s){
			autoMinMax = s.getAutoMinMax();
			minValue = s.getMinValue();
			maxValue = s.getMaxValue();
		}
		
		if (autoMinMax){
			if (setMinMax(data, changedArea)){
				changedArea.reset();
				changedArea.add(data.getValidArea());
				
				synchronized(s){
					minValue = s.getMinValue();
					maxValue = s.getMaxValue();
				}
			}
		}
		
		int[] sampleSizes = data.getImage().getRaster().getSampleModel().getSampleSize();
		boolean hasNon8BitData = sampleSizes[0] > 8;
		
		log.println("GrayscaleStage: "+minValue+","+maxValue+","+autoMinMax);
		if (hasNon8BitData || !autoMinMax){
			// Convert from source # bits to 8-bit data per plane
			image.coerceData(false); // have alpha separated out
			
			double diff = maxValue - minValue;
			double scaleFactor = diff == 0? 0: 255.0 / (maxValue - minValue);
			double offset = diff == 0? 0: -255 * minValue / (maxValue - minValue);
			if (Double.isInfinite(minValue) || Double.isInfinite(maxValue))
				offset = scaleFactor = 0;
			
			GrayRescaleToByteOp rescaleOp = new GrayRescaleToByteOp((float)scaleFactor, (float)offset);
			BufferedImage outImage = rescaleOp.filter(image, null);
			
			processedData = data.getDeepCopyShell(outImage);
		}
		else {
			// Pass the data along, unmodified
			processedData = data;
		}
		
		return processedData;
	}
	
	private double[] getMinMax(MapData mapData, Area changed){
		double[] minMax = new double[]{ Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
		
		Area toProcess = new Area();
		toProcess.add(changed);
		toProcess.intersect(new Area(mapData.getRequest().getExtent()));
		
		if (toProcess.isEmpty())
			return minMax;
		
		BufferedImage image = mapData.getImage();
		Raster r = image.getRaster();
		
		// Narrow to the changed (valid) region.
		Rectangle maskBounds = mapData.getRasterBoundsForWorld(toProcess.getBounds2D());
		Raster mask = StageUtil.getMask(maskBounds.width, maskBounds.height, toProcess.getBounds2D(), toProcess);
		
		double[] pix = new double[r.getNumBands()];
		int b = image.getColorModel().getNumColorComponents();
		
		for(int j=0; j<maskBounds.height; j++){
			for(int i=0; i<maskBounds.width; i++){
				if (mask.getSample(i, j, 0) == 0)
					continue;
				
				r.getPixel(i+maskBounds.x, j+maskBounds.y, pix);
				for(int k=0; k<b; k++){
					if (pix[k] < minMax[0])
						minMax[0] = pix[k];
					if (pix[k] > minMax[1])
						minMax[1] = pix[k];
				}
			}
		}
		
		return minMax;
	}
	
	private boolean setMinMax(MapData mapData, Area changedArea){
		double[] minMax = getMinMax(mapData, changedArea);
		boolean changed = false;
		
		GrayscaleStageSettings s = (GrayscaleStageSettings)getSettings();
		if (minMax[0] < s.getMinValue()){
			s.setMinValue(minMax[0]);
			changed = true;
		}
		if (minMax[1] > s.getMaxValue()){
			s.setMaxValue(minMax[1]);
			changed = true;
		}
		return changed;
	}
	
	public MapAttr[] consumes(int inputNumber){
		return new MapAttr[]{ MapAttr.SINGLE_BAND };
	}
	
	public MapAttr produces(){
		return MapAttr.GRAY;
	}

	public Object clone() throws CloneNotSupportedException {
		GrayscaleStage stage = (GrayscaleStage)super.clone();
		return stage;
	}
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}
}
