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

import edu.asu.jmars.layer.map2.Stage;

public class HSBBandExtractorStageSettings extends BandExtractorStageSettings {
	public static final String stageName = "HSB Band Extractor";
	public static final String[] bandNames = new String[]{ "Hue", "Saturation", "Value" };

	public HSBBandExtractorStageSettings(String selectedBand){
		super(bandNames, bandName(selectedBand));
	}

	private static String bandName(String inputName){
		if (inputName.toLowerCase().startsWith("h"))
			return bandNames[0];
		if (inputName.toLowerCase().startsWith("s"))
			return bandNames[1];
		if (inputName.toLowerCase().startsWith("v") || inputName.toLowerCase().startsWith("b") || inputName.toLowerCase().startsWith("i"))
			return bandNames[2];
		return null;
	}

	public Stage createStage(){
		return new HSBBandExtractorStage(this);
	}
	
	public String getStageName(){
		return stageName;
	}
}
