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


package edu.asu.jmars.layer.map2;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;

/**
 * Describes a type of map data. The data can consist of any number of planes,
 * but each plane must have the same type of data.
 * 
 * Number of bands is the most important property of a MapAttr. Image data of
 * an unexpected data type can always be scaled (e.g. convert float to byte),
 * but there is no standard answer for unexpected band count.
 * 
 * In the worst-case the only way to divine the number of bands will be to
 * actually request a sample and create a MapAttr from it.
 */
public class MapAttr {
	public static enum ImageType {
		TYPE_FAILED,
		TYPE_COLOR,
		TYPE_GRAY,
		TYPE_NUMERIC
	};
	
	/** Value designating any number of color components. */
	public static final int NUM_CC_ANY = -1;

	/** Predefined single band grayscale image MapAttr */
	public static final MapAttr GRAY        = new MapAttr(1,          DataBuffer.TYPE_BYTE);
	/** Predefined three band color image MapAttr */
	public static final MapAttr COLOR       = new MapAttr(3,          DataBuffer.TYPE_BYTE);
	/** Predefined single band image MapAttr */
	public static final MapAttr SINGLE_BAND = new MapAttr(1,          DataBuffer.TYPE_UNDEFINED);
	/** Predefined any configuration image MapAttr */
	public static final MapAttr ANY         = new MapAttr(NUM_CC_ANY, DataBuffer.TYPE_UNDEFINED);

	
	
	/** Data type of each plane */
	private final int dataType;
	
	/** Number of color components */
	private final int numColorComp;
	
	/** True if the map source was initialized with an image sample that was bad */
	private final boolean failed;
	
	/**
	 * Create a MapAttr with the given number of color components and data type
	 * of every component.
	 * @param numColorComp
	 * @param dataType
	 */
	public MapAttr(int numColorComp, int dataType){
		this.numColorComp = numColorComp;
		this.dataType = dataType;
		this.failed = false;
	}
	
	/**
	 * Returns <code>true</code> if a {@link Stage} consuming tgtAttrs can
	 * accept this {@link MapAttr} as input.
	 * @param tgtAttrs Output of {@link Stage#consumes(int)}
	 */
	public boolean isCompatible(MapAttr[] tgtAttrs){
		for(int i=0; i<tgtAttrs.length; i++)
			if (isCompatible(tgtAttrs[i]))
				return true;
		return false;
	}

	/**
	 * Returns <code>true</code> if a Stage taking tgtAttr can accept this {@link MapAttr}
	 * as input.
	 * @param tgtAttr Target {@link Stage}'s {@link MapAttr}.
	 */
	public boolean isCompatible(MapAttr tgtAttr){
		if (!(tgtAttr.getDataType() == DataBuffer.TYPE_UNDEFINED || tgtAttr.getDataType() == getDataType()))
			return false;
		
		if (!(tgtAttr.getNumColorComp() == MapAttr.NUM_CC_ANY || tgtAttr.getNumColorComp() == getNumColorComp()))
			return false;
		
		return true;
	}
	
	/** Creates a MapAttr by inspecting bands, color model, and raster data type */
	public MapAttr(BufferedImage sample) {
		boolean result;
		int colorCount;
		int dataType;
		try {
			colorCount = sample.getColorModel().getNumColorComponents();
			dataType = sample.getSampleModel().getDataType();
			result = false;
		} catch (Exception e) {
			colorCount = NUM_CC_ANY;
			dataType = DataBuffer.TYPE_UNDEFINED;
			result = true;
		}
		this.numColorComp = colorCount;
		this.dataType = dataType;
		this.failed = result;
	}
	
	/**
	 * Get the data type. Has one of the values returned by
	 * {@link java.awt.image.Raster.DataBuffer#getDataType DataBuffer.getDataType()}
	 */
	public int getDataType() {
		return dataType;
	}
	
	/** Returns one of TYPE_GRAY, TYPE_COLOR, or TYPE_NUMERIC */
	private final ImageType getColorType() {
		if (failed)
			return ImageType.TYPE_FAILED;
		if (numColorComp == 1 && dataType == DataBuffer.TYPE_BYTE)
			return ImageType.TYPE_GRAY;
		if (numColorComp == 3 && dataType == DataBuffer.TYPE_BYTE)
			return ImageType.TYPE_COLOR;
		return ImageType.TYPE_NUMERIC;
	}
	
	/** When this is true, callers should NOT attempt to get maps through this MapSource */
	public boolean isFailed() {
		return failed;
	}
	
	public boolean isColor() {
		return getColorType().equals(ImageType.TYPE_COLOR);
	}
	
	public boolean isGray() {
		return getColorType().equals(ImageType.TYPE_GRAY);
	}
	
	public boolean isNumeric() {
		return getColorType().equals(ImageType.TYPE_NUMERIC);
	}
	
	public int getNumColorComp() {
		return numColorComp;
	}
	
	public String toString(){
		String typeName;
		switch(getDataType()){
		case DataBuffer.TYPE_BYTE:   typeName = "byte";   break;
		case DataBuffer.TYPE_SHORT:  typeName = "short";  break;
		case DataBuffer.TYPE_USHORT: typeName = "ushort"; break;
		case DataBuffer.TYPE_INT:    typeName = "int";    break;
		case DataBuffer.TYPE_FLOAT:  typeName = "float";  break;
		case DataBuffer.TYPE_DOUBLE: typeName = "double"; break;
		default:                     typeName = "*"; break;
		}
		
		return "(" + typeName + "," + (getNumColorComp() == NUM_CC_ANY? "*": Integer.toString(getNumColorComp())) + ")";
	}
}
