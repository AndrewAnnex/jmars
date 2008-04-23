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


package edu.asu.jmars;

import edu.asu.jmars.util.SetProxy;

/**
 * Allows layers to process arguments in a custom way. Implementations must
 * have a no-arg constructor. 
 */
public interface MainArgProcessor {
	/**
	 * Processes the given arguments.
	 * @return If true, the Main class should quit immediately.
	 */
	boolean process(String[] args);
	/**
	 * This will be called before process() is called to supply a proxy to the
	 * caller's properties, since implementations of this class may need deep
	 * access without being in the same package.
	 */ 
	void setProxy(SetProxy proxy);
}
