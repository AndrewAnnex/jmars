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

import java.io.Serializable;

import edu.asu.jmars.layer.map2.AbstractStageSettings;
import edu.asu.jmars.layer.map2.Stage;
import edu.asu.jmars.layer.map2.StageView;
import edu.asu.jmars.util.DebugLog;

public class ThresholdSettings extends AbstractStageSettings implements Cloneable, Serializable {
	public static final DebugLog log = DebugLog.instance();
	
	public static final String stageName = "Threshold";
	public static final String propThresholdValue = "threshold value";
	
	double threshold;

	public ThresholdSettings(){
		threshold = 100;
	}
	
	public Stage createStage() {
		return new ThresholdStage(this);
	}

	public StageView createStageView() {
		return new ThresholdStageView(this);
	}

	public synchronized double getThreshold(){
		return threshold;
	}
	
	public synchronized void setThreshold(double newThreshold){
		double oldThreshold = threshold;
		threshold = newThreshold;
		log.println("Setting threshold value from "+oldThreshold+" to "+newThreshold);
		firePropertyChangeEvent(propThresholdValue, new Double(oldThreshold), new Double(newThreshold));
	}
	
	public String getStageName() {
		return stageName;
	}

	public Object clone() throws CloneNotSupportedException {
		ThresholdSettings s = (ThresholdSettings)super.clone();
		return s;
	}
}
