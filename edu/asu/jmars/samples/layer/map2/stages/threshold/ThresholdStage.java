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


package edu.asu.jmars.samples.layer.map2.stages.threshold;

import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.Serializable;

import edu.asu.jmars.layer.map2.AbstractStage;
import edu.asu.jmars.layer.map2.MapAttr;
import edu.asu.jmars.layer.map2.MapData;
import edu.asu.jmars.util.Util;

/**
 * A very simple thresholding stage. The threshold value is modifiable
 * in the view for this stage.
 */
public class ThresholdStage extends AbstractStage implements Cloneable, Serializable {
	public ThresholdStage(ThresholdSettings settings){
		super(settings);
	}
	
	public MapAttr[] consumes(int inputNumber) {
		if (inputNumber != 0)
			throw new IllegalArgumentException();
		
		return new MapAttr[]{ MapAttr.SINGLE_BAND };
	}

	public int getInputCount() {
		return 1;
	}

	public MapAttr produces() {
		return MapAttr.SINGLE_BAND;
	}
	
	public ThresholdSettings getSettings(){
		return ((ThresholdSettings)super.getSettings());
	}
	
	public MapData process(int inputNumber, MapData data, Area changedArea) {
		if (inputNumber != 0)
			throw new IllegalArgumentException();
		
		double threshold = getSettings().getThreshold();
		BufferedImage inImage = data.getImage();
		Raster inRaster = inImage.getRaster();
		
		int w = inImage.getWidth();
		int h = inImage.getHeight();

		BufferedImage outImage = Util.createGrayscaleImage(w, h, true, BufferedImage.OPAQUE, null);
		WritableRaster outRaster = outImage.getRaster();
		
		// Fill output image via thresholding
		for(int j=0; j<h; j++){
			for(int i=0; i<w; i++){
				outRaster.setSample(i, j, 0, (inRaster.getSampleDouble(i, j, 0) >= threshold)? 255: 0);
			}
		}

		// Set the output changed area, which can be smaller/larger
		// than the input changed area.
		changedArea.reset();
		changedArea.add(new Area(data.getRequest().getExtent()));
		
		// Finally return a safe output image that the receiver can 
		// modify with impunity.
		return data.getDeepCopyShell(outImage);
	}

	public String getStageName(){
		return getSettings().getStageName();
	}
	
	public Object clone() throws CloneNotSupportedException {
		ThresholdStage stage = (ThresholdStage)super.clone();
		return stage;
	}
}
