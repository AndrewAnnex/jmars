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


package edu.asu.jmars.layer.stamp;

import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;

import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.swing.ColorCombo;

public class StampLayerSettings implements SerializedParameters {
	static final long serialVersionUID = 8742030623145671825L;
	
	String instrument;
	
	String name;
	String queryStr;
	String[] initialColumns;
	Color  unsColor = new ColorCombo().getColor();
	Color  filColor = new Color(new ColorCombo().getColor().getRGB() & 0xFFFFFF, true);
	
	FilledStamp.State[] stampStateList;
	boolean hideOutlines=false;
	boolean renderSelectedOnly=false;
	
	public StampLayerSettings() {
	}
	
	public StampLayerSettings(String instrument, String[] initialColumns) {
		this.instrument = instrument;
		this.initialColumns = initialColumns;
	}
	
	public Color getUnselectedStampColor() {
		return unsColor;
	}
	
	public void setUnselectedStampColor(Color newColor) {
		unsColor=newColor;
	}
	
	public Color getFilledStampColor() {
		return filColor;
	}
	
	public void setFilledStampColor(Color newColor) {
		filColor=newColor;
	}
	
	public boolean hideOutlines() {
		return hideOutlines;
	}
	
	public boolean renderSelectedOnly() {
		return renderSelectedOnly;
	}
	
	public void setHideOutlines(boolean newSetting) {
		hideOutlines=newSetting;
	}

	public void setRenderSelectedOnly(boolean newSetting) {
		renderSelectedOnly=newSetting;
	}

	public String getName() {
		return name;
	}
	
	public void setName(String newName) {
		name=newName;
	}
	
	public String getInstrument() {
		return instrument;
	}
	
	public FilledStamp.State[] getStampStateList() {
		return stampStateList;
	}
	
	public void setStampStateList(FilledStamp.State[] newStates) {
		stampStateList=newStates;
	}
	
	public void setInitialColumns(String[] newCols) {
		initialColumns=newCols;
	}
	
	// does the normal object read, but once all the necessary info is gathered,
	// fills in fields that may have been missing in older session files
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();
		if (stampStateList != null) {
			for (FilledStamp.State state: stampStateList) {
				if (state.imageType == null) {
					if (instrument.equals("THEMIS")) {
						if (state.id.startsWith("I")) {
							state.imageType = "BTR";
						} else if (state.id.startsWith("V")) {
							state.imageType = "ABR";
						} else {
							state.imageType = "BTR";
						}
					} else {
						state.imageType = instrument.toUpperCase();
					}
				}
			}
		}
	}
}
