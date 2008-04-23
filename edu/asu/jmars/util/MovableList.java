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
 ** An enhanced version of the {@link java.util.List} interface that
 ** includes atomic facilities for moving elements of the list
 ** around. Basically, this interface is identical to List in every
 ** regard, except for the addition of the {@link #move move} method.
 **/
public interface MovableList extends List
 {
	/**
	 ** Moves an element from one position in the list to another.
	 ** Has the exact same effect as the following sequence of calls:
	 **
	 ** <p><code>Object movedElement = remove(srcIndex);<br>
	 ** add(dstIndex, movedElement);</code>
	 **
	 ** <p>The net effect is that the element at position
	 ** <code>srcIndex</code> is moved to <code>dstIndex</code>, with
	 ** all intervening elements being shifted up or down to make
	 ** room.
	 **
	 ** @param srcIndex the original index of the element to move
	 ** @param dstIndex the desired new index of the element
	 **/
	public void move(int srcIndex, int dstIndex);
 }
