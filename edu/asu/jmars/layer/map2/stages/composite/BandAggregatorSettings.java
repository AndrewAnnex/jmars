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


package edu.asu.jmars.layer.map2.stages.composite;


import edu.asu.jmars.layer.map2.AbstractStageSettings;
import edu.asu.jmars.layer.map2.Stage;
import edu.asu.jmars.layer.map2.StageView;
import edu.asu.jmars.layer.map2.stages.DummyStageView;

public class BandAggregatorSettings extends AbstractStageSettings {
	public static final String stageName = "Aggregator";
	public static final String PROP_INPUT_COUNT = "inputCount";
	
	private int numInputs;
	private String[] inputNames;

	public BandAggregatorSettings(int inputCount){
		this.numInputs = inputCount;
		this.inputNames = makeNames(this.numInputs);
	}
	
	public void setInputCount(int numInputs){
		this.numInputs = numInputs;
		this.inputNames = makeNames(this.numInputs);
		firePropertyChangeEvent(PROP_INPUT_COUNT, new Integer(numInputs), new Integer(this.numInputs));
	}

	public int getInputCount() {
		return numInputs;
	}

	public String getInputName(int inputNumber) {
		return inputNames[inputNumber];
	}

	public String[] getInputNames() {
		return inputNames;
	}
	
	private static String[] makeNames(int bandCount) {
		String[] names = new String[bandCount];
		for (int i = 0; i < names.length; i++)
			names[i] = "input_" + i;
		return names;
	}
	
	public Stage createStage() {
		return new BandAggregator(this);
	}

	public StageView createStageView() {
		return new DummyStageView(this);
	}

	public String getStageName() {
		return stageName;
	}

	public Object clone() throws CloneNotSupportedException {
		BandAggregatorSettings s = (BandAggregatorSettings)super.clone();
		s.inputNames = (String[])inputNames.clone();
		return s;
	}
}
