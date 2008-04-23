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

/**
 * Implemented by any class that wishes to receive MapData updates from a
 * MapChannel
 */
public interface MapChannelReceiver {
	/**
	 * MapChannel provides MapData to implementors of this interface.
	 * 
	 * This method is always invoked on the AWT event thread.
	 * 
	 * The the resulting MapData object will have a null image if a fatal error
	 * occurred at any point (like the MapSource was invalid or a Stage threw an
	 * exception.)
	 */
	public void mapChanged(MapData mapData);
}
