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


package edu.asu.jmars.places;

import java.util.Comparator;
import java.util.Set;

/**
 * Manages a collection of places and provides for adding (which can also be
 * used to replace) and removing elements.
 * 
 * Provides Set access to places, since other data structures are not
 * implementable by most stores.
 */
public interface PlaceStore extends Set<Place> {
	/**
	 * Set the sorter for ordering elements in the store; the default is by name
	 */
	void setComparator(Comparator<Place> sorter);
}
