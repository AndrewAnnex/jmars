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

import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;

import edu.asu.jmars.layer.map2.AbstractStage;
import edu.asu.jmars.layer.map2.MapAttr;
import edu.asu.jmars.layer.map2.MapData;
import edu.asu.jmars.layer.map2.StageSettings;
import edu.asu.jmars.util.Util;

public class BandExtractorStage extends AbstractStage implements Cloneable, Serializable {
	private static final long serialVersionUID = 986482229581280926L;

	public BandExtractorStage(StageSettings settings){
		super(settings);
	}
	
	public String getStageName() {
		return getSettings().getStageName();
	}
	
	public int getInputCount(){
		return 1;
	}

	public MapData process(int inputNumber, MapData data, Area changedArea) {
		BandExtractorStageSettings settings = (BandExtractorStageSettings)getSettings();
		String band = settings.getSelectedBand();
		int bandNumber = Arrays.asList(settings.getBands()).indexOf(band);
		if (bandNumber < 0)
			throw new IllegalArgumentException("Invalid selected band: "+band);
		
		BufferedImage image = data.getImage();
		image.coerceData(false);
		Raster r = image.getRaster();
		r = r.createChild(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight(), r.getMinX(), r.getMinY(), new int[]{ bandNumber });
		
		WritableRaster outRaster = r.createCompatibleWritableRaster();
		ColorModel outCM = new ComponentColorModel(Util.getLinearGrayColorSpace(),
				false, false, BufferedImage.OPAQUE, outRaster.getTransferType());
		BufferedImage outImage = new BufferedImage(outCM, outRaster, outCM.isAlphaPremultiplied(), null);
		
		outImage.getRaster().setRect(r);
		//if (image.getAlphaRaster() != null && outImage.getAlphaRaster() != null)
		//	outImage.getAlphaRaster().setRect(image.getAlphaRaster());
		
		return data.getDeepCopyShell(outImage);
	}

	public MapAttr[] consumes(int inputNumber){
		return new MapAttr[]{ MapAttr.ANY };
	}
	
	public MapAttr produces() {
		return MapAttr.SINGLE_BAND;
	}
	
	public Object clone() throws CloneNotSupportedException {
		BandExtractorStage s = (BandExtractorStage)super.clone();
		return s;
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}
}
