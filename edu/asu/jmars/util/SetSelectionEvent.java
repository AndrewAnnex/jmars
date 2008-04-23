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


package edu.asu.jmars.util;

import java.util.*;

/**
 ** An event that characterizes a change in the current selection. The
 ** change is limited to a particular set of objects.
 ** SetSelectionListeners will generally query the source of the event
 ** for the new selected status of each potentially [un]selected
 ** object.
 **/
public class SetSelectionEvent extends EventObject
{
	private Set changedObjects;
	private boolean isAdjusting;
	
	/**
	 ** Represents a change in selection status for the objects in the
	 ** given set.
	 **
	 ** @param changedObjects The objects whose status changed (a
	 ** shallow copy is made of the set).
	 ** @param isAdjusting An indication that this is one of a rapid
	 ** series of events.
	 **/
	public SetSelectionEvent(Object source, Set changedObjects, boolean isAdjusting)
	{
		super(source);
		this.changedObjects =
			Collections.unmodifiableSet( new HashSet(changedObjects) );
		this.isAdjusting = isAdjusting;
	}
	
	/**
	 ** Returns a list of the objects whose selection status
	 ** changed. The returned set is immutable.
	 **/
	public Set getChanges()
	{
		return  changedObjects;
	}
	
	/**
	 ** Returns true if this is one of a multiple of change events.
	 **/
	public boolean getValueIsAdjusting()
	{
		return  isAdjusting;
	}
}
