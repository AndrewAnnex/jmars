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

/** all registered listeners will be notified when the custom maps list changes */
public interface MapServerListener {
	/**
	 * <ul>
	 * <li>ADDED: a map source was added
	 * <li>REMOVED: a map source was removed
	 * <li>UPDATED: a property on an existing map source was changed
	 * </ul>
	 */
	enum Type {ADDED, REMOVED, UPDATED};
	/**
	 * Called when a custom map is added or removed. This only signals that a
	 * change occurred, it does not describe the change.
	 */
	void mapChanged (MapSource source, Type changeType);
}
