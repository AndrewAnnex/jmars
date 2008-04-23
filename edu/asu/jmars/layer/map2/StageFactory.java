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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.asu.jmars.layer.map2.stages.ColorStretcherStage;
import edu.asu.jmars.layer.map2.stages.ColorStretcherStageSettings;
import edu.asu.jmars.layer.map2.stages.ContourStageSettings;
import edu.asu.jmars.layer.map2.stages.GrayscaleStage;
import edu.asu.jmars.layer.map2.stages.GrayscaleStageSettings;

/**
 * Factory to create instances of Stages.
 */
public class StageFactory {
	static StageFactory instance;

	List singleIOStages;

	protected StageFactory(){
		singleIOStages = new ArrayList();
		singleIOStages.add(getStageInstance(ColorStretcherStageSettings.stageName));
		singleIOStages.add(getStageInstance(GrayscaleStageSettings.stageName));
		singleIOStages.add(getStageInstance(ContourStageSettings.stageName));
	}
	
	/**
	 * Returns a StageFactory instance.
	 */
	public static StageFactory instance(){
		if (instance == null)
			instance = new StageFactory();
		return instance;
	}
	
	/**
	 * Returns an unmodifiable list of single input single output
	 * Stages.
	 */
	public List getAllSingleIOStages(){
		return Collections.unmodifiableList(singleIOStages);
	}
	
	/**
	 * Returns a new instance of a Stage given the stage name.
	 * The stage name comes from the static class variable from
	 * within the stages. Only a handful of single input/output 
	 * stages are supported at this time.
	 */
	public static Stage getStageInstance(String name){
		if (name.equals(GrayscaleStageSettings.stageName)){
			return (new GrayscaleStageSettings()).createStage();
		}
		else if (name.equals(ColorStretcherStageSettings.stageName)){
			return (new ColorStretcherStageSettings()).createStage();
		}
		else if (name.equals(ContourStageSettings.stageName)){
			return (new ContourStageSettings()).createStage();
		}
		
		return null;
	}
}
