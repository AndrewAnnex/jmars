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


package edu.asu.jmars.layer.map2.msd;

import java.beans.PropertyChangeEvent;
import java.util.EventObject;

import edu.asu.jmars.layer.map2.Stage;

public class PipelineLegModelEvent extends EventObject {
	private static final long serialVersionUID = 1L;
	
	public static final int STAGES_ADDED = 1;
	public static final int STAGES_REMOVED = 2;
	public static final int STAGES_REPLACED = 3;
	public static final int STAGE_PARAMS_CHANGED = 4;
	
	private int eventType;
	private int[] stageIndices;
	private Stage[] stages;
	private Stage[] oldStages;
	private PropertyChangeEvent srcEvent;
	
	public PipelineLegModelEvent(PipelineLegModel source, int eventType, int[] stageIndices, Stage[] stages){
		super(source);
		this.eventType = eventType;
		if (eventType == STAGE_PARAMS_CHANGED || eventType == STAGES_REPLACED)
			throw new IllegalArgumentException("Invalid eventType "+eventType+" for the constructor.");
		
		this.stageIndices = stageIndices;
		this.stages = stages;
	}
	
	public PipelineLegModelEvent(PipelineLegModel source, Stage[] oldStages, Stage[] newStages){
		super(source);
		this.eventType = STAGES_REPLACED;
		this.oldStages = oldStages;
		this.stages = newStages;
	}
	
	public PipelineLegModelEvent(PipelineLegModel source, PropertyChangeEvent srcEvent){
		super(source);
		this.eventType = STAGE_PARAMS_CHANGED;
		this.srcEvent = srcEvent;
	}
	
	public int getEventType(){
		return eventType;
	}
	
	public int[] getStageIndices(){
		return stageIndices;
	}
	
	public Stage[] getStages(){
		return stages;
	}
	
	public Stage[] getOldStages(){
		return oldStages;
	}
	
	public PropertyChangeEvent getWrappedEvent(){
		return srcEvent;
	}
}
