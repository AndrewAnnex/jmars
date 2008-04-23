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


package edu.asu.jmars.ruler;

import java.io.Serializable;

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class RulerStepSlider extends JSlider {
	public RulerStepSlider(int min, int max, int step, int initial){
		super(min, max, initial);
		setMajorTickSpacing(step);
		setSnapToTicks(true);
		setPaintTicks(true);
		setPaintLabels(true);
	
		getModel().addChangeListener(new ChangeListener(){
			public void stateChanged(ChangeEvent e) {
				RulerManager.Instance.notifyRulerOfViewChange();
			}
		});
	}
	
	public Settings getSettings(){
		Settings s = new Settings(
				getMinimum(),
				getMaximum(),
				getMajorTickSpacing(),
				getValue());
		
		return s;
	}
	
	public void restoreSettings(Settings s){
		setMinimum(s.min);
		setMaximum(s.max);
		setMajorTickSpacing(s.step);
		setValue(s.initial);
	}
	
	public final class Settings implements Cloneable, Serializable {
		public Settings(){
			min = -10;
			max =  10;
			step = 2;
			initial = 0;
		}
		
		public Settings(int min, int max, int step, int initial){
			this.min = min;
			this.max = max;
			this.step = step;
			this.initial = initial;
		}
		public int min;
		public int max;
		public int step;
		public int initial;
	}
}
