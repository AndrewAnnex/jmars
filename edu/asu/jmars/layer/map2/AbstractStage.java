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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public abstract class AbstractStage implements Stage, PropertyChangeListener, Cloneable, Serializable {
	private transient List<PropertyChangeListener> propertyChangeListeners;
	private StageSettings stageSettings;
	
	public AbstractStage(StageSettings stageSettings){
		commonInit();
		this.stageSettings = stageSettings;
		this.stageSettings.addPropertyChangeListener(this);
	}
	
	public StageSettings getSettings(){
		return stageSettings;
	}
	
	/*
	 * Various transient data initializations.
	 */
	private void commonInit(){
		propertyChangeListeners = new ArrayList<PropertyChangeListener>();
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
	
	// Forward received events from StageSettings to whoever is listening.
	public void propertyChange(PropertyChangeEvent e) {
		firePropertyChangeEvent(new PropertyChangeEvent(this, e.getPropertyName(), e.getOldValue(), e.getNewValue()));
	}
	
	/**
	 * Adds a PropertyChangeListener to listen to the model changes.
	 * These events are for the benefit of StageView that may be tied
	 * to this stage.<p>
	 * <em>If you would like to get notification of changes
	 * to Stage state initiated by the user, register with the
	 * {@link StageView#addChangeListener(javax.swing.event.ChangeListener)}
	 * instead.</em>
	 */
	public void   addPropertyChangeListener(PropertyChangeListener l){
		propertyChangeListeners.add(l);
	}
	
	/**
	 * Removes a PropertyChangeListener which was listening to model changes.
	 */
	public void   removePropertyChangeListener(PropertyChangeListener l){
		propertyChangeListeners.remove(l);
	}
	
	/**
	 * Fires a {@link PropertyChangeEvent} to all listeners registered
	 * with this stage.
	 * @param e The event to be transmitted to all the listeners.
	 */
	public void firePropertyChangeEvent(final PropertyChangeEvent e){
		final List<PropertyChangeListener> listeners =
			new ArrayList<PropertyChangeListener>(propertyChangeListeners);

		for(PropertyChangeListener l: listeners)
			l.propertyChange(e);
	}
	
	public String toString(){
		return getStageName();
	}
	
	public Object clone() throws CloneNotSupportedException {
		AbstractStage s = (AbstractStage)super.clone();
		s.commonInit();
		s.stageSettings = (StageSettings)s.stageSettings.clone();
		s.stageSettings.addPropertyChangeListener(s);
		return s;
	}
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		commonInit();
	}
}
