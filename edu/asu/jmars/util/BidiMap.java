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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BidiMap {
	private Map left = new HashMap ();
	private Map right = new HashMap ();
	/**
	 * Add a new association to the bidi map.
	 * @param left The left key.
	 * @param right The right key.
	 */
	public void add (Object left, Object right) {
		this.left.put(left, right);
		this.right.put(right, left);
	}
	/**
	 * Remove an association by its left key.
	 * @param left The key in the left map of the association to remove.
	 */
	public void removeLeft (Object left) {
		right.remove(this.left.remove (left));
	}
	/**
	 * Remove an association by its right key.
	 * @param right The key in the right map of the association to remove.
	 */
	public void removeRight (Object right) {
		left.remove(this.right.remove(right));
	}
	/**
	 * Returns right key from the left key.
	 */
	public Object getLeft (Object left) {
		return this.left.get(left);
	}
	/**
	 * Return the left key from the right key.
	 */
	public Object getRight (Object right) {
		return this.right.get(right);
	}
	/**
	 * Get the left keys as a set.
	 */
	public Set leftKeys () {
		return left.keySet();
	}
	/**
	 * Get the right keys as a set.
	 */
	public Set rightKeys () {
		return right.keySet();
	}
	/**
	 * Get the size of the map.
	 */
	public int size() {
		return left.size(); // always == right.size()
	}
}
