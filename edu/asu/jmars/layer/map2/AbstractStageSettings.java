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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

public abstract class AbstractStageSettings implements StageSettings {
	private static final long serialVersionUID = -787452580306026300L;
	
	private transient List<PropertyChangeListener> listeners;
	
	public AbstractStageSettings(){
		commonInit();
	}
	
	private void commonInit(){
		listeners = new ArrayList<PropertyChangeListener>();
	}
	
	public final void addPropertyChangeListener(PropertyChangeListener l) {
		listeners.add(l);
	}

	public final void removePropertyChangeListener(PropertyChangeListener l) {
		listeners.remove(l);
	}

	/**
	 * Fires {@link PropertyChangeEvent} on the AWT thread.
	 * @param propertyName Name of the property that changed.
	 * @param oldValue Old value of the property.
	 * @param newValue New value of the property.
	 * 
	 * @see #addPropertyChangeListener(PropertyChangeListener)
	 */
	public final void firePropertyChangeEvent(String propertyName, Object oldValue, Object newValue){
		final PropertyChangeEvent e = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
		final List<PropertyChangeListener> ll = new ArrayList<PropertyChangeListener>(listeners);
		SwingUtilities.invokeLater(new Runnable(){
			public void run(){
				for(PropertyChangeListener l: ll){
					l.propertyChange(e);
				}
			}
		});
	}
	
	public Object clone() throws CloneNotSupportedException {
		AbstractStageSettings s = (AbstractStageSettings)super.clone();
		s.commonInit();
		return s;
	}
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		commonInit();
	}
}
