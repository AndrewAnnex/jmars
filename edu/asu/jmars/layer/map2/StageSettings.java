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

import java.beans.PropertyChangeListener;
import java.io.Serializable;

/**
 * Storage for shared settings of a Stage. Multiple Stages which want
 * to get to the same data will use the same {@link StageSettings} object.
 * Listeners can be added to listen to property changes. 
 */
public interface StageSettings extends Cloneable, Serializable {
	public Stage createStage();
	public StageView createStageView();
	public String getStageName();
	public void addPropertyChangeListener(PropertyChangeListener l);
	public void removePropertyChangeListener(PropertyChangeListener l);
	public Object clone() throws CloneNotSupportedException;
}
