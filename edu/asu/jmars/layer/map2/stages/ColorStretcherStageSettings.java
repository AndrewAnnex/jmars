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

import edu.asu.jmars.layer.map2.AbstractStageSettings;
import edu.asu.jmars.layer.map2.Stage;
import edu.asu.jmars.layer.map2.StageView;
import edu.asu.jmars.swing.ColorMapper;
import edu.asu.jmars.util.DebugLog;

public class ColorStretcherStageSettings extends AbstractStageSettings {
	private static final DebugLog log = DebugLog.instance();
	
	public static String stageName = "Color Stretcher";
	public static final String propCmap = "cmap";
	private ColorMapper.State cmState;
	
	public ColorStretcherStageSettings(){
		cmState = ColorMapper.State.DEFAULT;
	}
	
	public ColorMapper.State getColorMapperState(){
		return cmState;
	}
	
	public void setColorMapperState(ColorMapper.State newCmState){
		ColorMapper.State oldCmState = cmState;
		cmState = newCmState;
		log.println("cmState changed from "+oldCmState+" to "+newCmState);
		firePropertyChangeEvent(propCmap, oldCmState, newCmState);
	}
	
	public Stage createStage() {
		return new ColorStretcherStage(this);
	}

	public StageView createStageView() {
		return new ColorStretcherStageView(this);
	}

	public String getStageName(){
		return stageName;
	}
	
	public Object clone() throws CloneNotSupportedException {
		ColorStretcherStageSettings settings = (ColorStretcherStageSettings)super.clone();
		settings.cmState = (ColorMapper.State)(cmState.clone());
		return settings;
	}
}
