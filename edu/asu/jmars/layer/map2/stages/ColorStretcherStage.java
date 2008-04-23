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
import java.awt.image.ColorConvertOp;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import edu.asu.jmars.layer.map2.AbstractStage;
import edu.asu.jmars.layer.map2.MapAttr;
import edu.asu.jmars.layer.map2.MapData;
import edu.asu.jmars.layer.map2.StageSettings;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

public class ColorStretcherStage extends AbstractStage implements Cloneable, Serializable {
	private static final long serialVersionUID = -1320855669272199638L;

	public static DebugLog log = DebugLog.instance();
	
	public static String inputName = "Input";
	public static final String[] outputNames = new String[] {"Red", "Green", "Blue"};


	public ColorStretcherStage(StageSettings settings){
		super(settings);
	}
	
	public MapAttr[] consumes(int inputNumber){
		return new MapAttr[]{ MapAttr.GRAY, MapAttr.COLOR };
	}
	
	public MapAttr produces() {
		return MapAttr.COLOR;
	}
	
	public String getStageName() {
		return getSettings().getStageName();
	}

	public int getInputCount() {
		return 1;
	}
	
	public MapData process(int inputNumber, MapData data, Area changedArea) {
		BufferedImage image = data.getImage();

		// Create an output image which is compatible with the FancyColorMapper's color map op
		BufferedImage outImage = Util.newBufferedImage(image.getWidth(), image.getHeight());
		ColorConvertOp cco = new ColorConvertOp(null);
		cco.filter(image, outImage);
		
		// TODO: fcm is a Swing object while the Stage is multi-threaded. How do we cope?
		// TODO: Don't know what alpha to use here, "1" seems like a reasonable choice.
		ColorStretcherStageSettings settings = (ColorStretcherStageSettings)getSettings(); 
		settings.getColorMapperState().getColorMapOp().forAlpha(1.0f).filter(outImage, outImage);
		
		return data.getDeepCopyShell(outImage);
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}
}
