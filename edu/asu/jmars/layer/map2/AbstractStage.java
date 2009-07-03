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

import java.io.Serializable;


public abstract class AbstractStage implements Stage, Cloneable, Serializable {
	private static final long serialVersionUID = -6243770128040291243L;
	
	private StageSettings stageSettings;
	
	public AbstractStage(StageSettings stageSettings){
		this.stageSettings = stageSettings;
	}
	
	public StageSettings getSettings(){
		return stageSettings;
	}
	
	/**
	 * Returns the name of this stage.
	 */
	public String getStageName(){
		String name = getClass().getName();
		
		int idx;
		if ((idx = name.lastIndexOf('.')) > -1)
			name = name.substring(idx+1);
		
		return name;
	}
	
	public boolean canTake(int inputNumber, MapAttr mapAttr){
		return mapAttr.isCompatible(consumes(inputNumber));
	}
	
	public String toString(){
		return getStageName();
	}
	
	public Object clone() throws CloneNotSupportedException {
		AbstractStage s = (AbstractStage)super.clone();
		s.stageSettings = (StageSettings)s.stageSettings.clone();
		return s;
	}
}
