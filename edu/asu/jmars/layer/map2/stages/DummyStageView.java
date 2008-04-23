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

import javax.swing.JLabel;
import javax.swing.JPanel;

import edu.asu.jmars.layer.map2.StageSettings;
import edu.asu.jmars.layer.map2.StageView;

public class DummyStageView implements StageView, PropertyChangeListener {
	StageSettings settings;
	JPanel stagePanel;
	
	public DummyStageView(StageSettings settings){
		this.settings = settings;
		this.stagePanel = createUI();
		this.settings.addPropertyChangeListener(this);
	}
	
	public StageSettings getSettings() {
		return settings;
	}
	
	private JPanel createUI(){
		JPanel p = new JPanel(new BorderLayout());
		p.add(new JLabel());
		return p;
	}

	public JPanel getStagePanel() {
		return stagePanel;
	}

	public void propertyChange(PropertyChangeEvent e){
	}
}
