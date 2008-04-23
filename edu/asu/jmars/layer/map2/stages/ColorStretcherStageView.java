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

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import edu.asu.jmars.layer.map2.StageSettings;
import edu.asu.jmars.layer.map2.StageView;
import edu.asu.jmars.swing.ColorMapper;
import edu.asu.jmars.swing.FancyColorMapper;

public class ColorStretcherStageView implements StageView, PropertyChangeListener {
	ColorStretcherStageSettings settings;
	JPanel stagePanel;
	FancyColorMapper fcm;
	
	public ColorStretcherStageView(ColorStretcherStageSettings settings){
		this.settings = settings;
		this.stagePanel = createUI();
		this.settings.addPropertyChangeListener(this);
	}
	
	public StageSettings getSettings() {
		return settings;
	}

	public JPanel getStagePanel() {
		return stagePanel;
	}

	private JPanel createUI(){
		fcm = new FancyColorMapper();
		fcm.setState(settings.getColorMapperState());
		fcm.addChangeListener(new ChangeListener(){
			public void stateChanged(ChangeEvent e) {
				if (!fcm.isAdjusting()){
					updateColorMapperState();
				}
			}
		});
		JPanel slim = new JPanel(new BorderLayout());
		slim.add(fcm, BorderLayout.NORTH);
		return slim;
	}
	
	private boolean updatingState = false;
	private void updateColorMapperState(){
		if (updatingState)
			return;
		
		updatingState = true;
		try {
			settings.setColorMapperState(fcm.getState());
		}
		finally {
			updatingState = false;
		}
	}

	public void propertyChange(PropertyChangeEvent e) {
		if (updatingState)
			return;
		
		updatingState = true;
		try {
			if (ColorStretcherStageSettings.propCmap.equals(e.getPropertyName())){
				fcm.setState((ColorMapper.State)e.getNewValue());
			}
		}
		finally {
			updatingState = false;
		}
	}
}
