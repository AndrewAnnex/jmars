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

import edu.asu.jmars.layer.map2.msd.WrappedMapSource;
import edu.asu.jmars.layer.map2.stages.composite.CompositeStage;
import java.util.EventObject;

public class PipelineModelEvent extends EventObject {
	public static final int COMP_CHANGED = 1;
	public static final int CHILDREN_ADDED = 2;
	public static final int CHILDREN_REMOVED = 3;
	public static final int CHILDREN_CHANGED = 4;
	public static final int FORWARDED_EVENT = 5;
	
	private int eventType;
	private CompositeStage compStage;
	private int[] childIndices;
	private WrappedMapSource[] children;
	private PipelineLegModelEvent wrappedEvent;
	
	public PipelineModelEvent(PipelineModel source, int eventType, CompositeStage compStage, int[] childIndices, WrappedMapSource[] children){
		super(source);
		this.eventType = eventType;
		if (eventType == FORWARDED_EVENT)
			throw new IllegalArgumentException("Invalid event type in constructor "+eventType);
		this.compStage = compStage;
		this.childIndices = childIndices;
		this.children = children;
	}
	
	public PipelineModelEvent(PipelineModel source, PipelineLegModelEvent wrappedEvent){
		super(source);
		this.eventType = FORWARDED_EVENT;
		this.wrappedEvent = wrappedEvent;
	}
	
	public int getEventType(){
		return eventType;
	}
	
	public CompositeStage getCompStage(){
		return compStage;
	}
	
	public int[] getChildIndices(){
		return childIndices;
	}
	
	public WrappedMapSource[] getChildren(){
		return children;
	}
	
	public PipelineLegModelEvent getWrappedEvent(){
		return wrappedEvent;
	}
}

