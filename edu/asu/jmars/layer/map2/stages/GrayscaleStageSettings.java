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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import edu.asu.jmars.layer.map2.AbstractStageSettings;
import edu.asu.jmars.layer.map2.Stage;
import edu.asu.jmars.layer.map2.StageView;
import edu.asu.jmars.util.DebugLog;

public class GrayscaleStageSettings extends AbstractStageSettings implements Cloneable, Serializable {
	
	private static final DebugLog log = DebugLog.instance();
	
	public static final String stageName = "Grayscale";
	
	public static final String propMin = "min";
	public static final String propMax = "max";
	public static final String propAutoMinMax = "auto";
	
	double minValue, maxValue;
	boolean autoMinMax;

	public GrayscaleStageSettings() {
		minValue = Double.POSITIVE_INFINITY;
		maxValue = Double.NEGATIVE_INFINITY;
		autoMinMax = true;
	}

	public Stage createStage() {
		GrayscaleStage s = new GrayscaleStage(this);
		addPropertyChangeListener(s);
		return s;
	}

	public StageView createStageView() {
		GrayscaleStageView v = new GrayscaleStageView(this);
		addPropertyChangeListener(v);
		return v;
	}
	
	public String getStageName(){
		return stageName;
	}

	public void setMinValue(double newMinValue){
		if (newMinValue != minValue) {
			double oldMinValue = minValue;
			minValue = newMinValue;
			log.println("Setting new min value from "+oldMinValue+" to "+newMinValue);
			firePropertyChangeEvent(propMin, new Double(oldMinValue), new Double(minValue));
		}
	}
	
	public void setMaxValue(double newMaxValue){
		if (newMaxValue != maxValue) {
			double oldMaxValue = maxValue;
			maxValue = newMaxValue;
			log.println("Setting new max value from "+oldMaxValue+" to "+newMaxValue);
			firePropertyChangeEvent(propMax, new Double(oldMaxValue), new Double(maxValue));
		}
	}
	
	public void setAutoMinMax(boolean newAutoMinMax){
		autoMinMax = newAutoMinMax;
		log.println("Setting new auto min/max value from "+(!newAutoMinMax)+" to "+newAutoMinMax);
		firePropertyChangeEvent(propAutoMinMax, new Boolean(!newAutoMinMax), new Boolean(newAutoMinMax));
	}
	
	public double getMinValue(){
		return minValue;
	}
	
	public double getMaxValue(){
		return maxValue;
	}
	
	public boolean getAutoMinMax(){
		return autoMinMax;
	}
	
	public Object clone() throws CloneNotSupportedException {
		GrayscaleStageSettings s = (GrayscaleStageSettings)super.clone();
		return s;
	}
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}
}
