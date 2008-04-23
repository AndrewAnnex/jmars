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
import java.io.Serializable;
import java.util.Arrays;

import edu.asu.jmars.layer.map2.MapAttr;
import edu.asu.jmars.layer.map2.MapData;

public class RGBBandExtractorStage extends BandExtractorStage implements Cloneable, Serializable {
	private static final long serialVersionUID = -6723372818363997617L;
	
	public RGBBandExtractorStage(RGBBandExtractorStageSettings settings){
		super(settings);
	}
	
	public MapData process(int inputNumber, MapData mapData, Area changedArea){
		RGBBandExtractorStageSettings settings = (RGBBandExtractorStageSettings)getSettings();
		
		String band = settings.getSelectedBand();
		int index = Arrays.asList(settings.getBands()).indexOf(band);
		if (index < 0)
			throw new IllegalArgumentException("Invalid band \""+band+"\" encountered.");
		
		int shift = (2-index)*8;
		BufferedImage image = mapData.getImage();
		int w = image.getWidth();
		int h = image.getHeight();
		BufferedImage outImage = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
		for(int j=0; j<h; j++){
			for(int i=0; i<w; i++){
				int rgb = image.getRGB(i, j);
				//int alpha = rgb & 0xff000000;
				rgb = (rgb >> shift) & 0xff;
				//rgb = alpha | rgb << 16 | rgb << 8 | rgb;
				//outImage.setRGB(i, j, rgb);
				outImage.getRaster().setSample(i, j, 0, rgb);
			}
		}
		
		return mapData.getDeepCopyShell(outImage);
	}
	
	public boolean canTake(int inputNumber, MapAttr mapAttr){
		if (mapAttr.isColor() || mapAttr.isGray())
			return true;
		
		return false;
	}
	
	public MapAttr[] consumes(int inputNumber){
		return new MapAttr[]{ MapAttr.COLOR, MapAttr.GRAY };
	}
	
	public MapAttr produces(){
		return MapAttr.GRAY;
	}
	
	public String getStageName(){
		return getSettings().getStageName();
	}
}
