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

import edu.asu.jmars.util.*;

/**
 * Classes the want to participate in the versioning provided by History
 * must implement this interface. The application must call setHistory()
 * to enable the participant to send change Objects to the History log.
 * Each such change must contain the <i>transition</i> from old state to
 * new state. The undo() and redo() methods must have the transition
 * to move the implementor's state forward or backward in revision.
 */
public interface Versionable {
	void setHistory (History history);
	void undo (Object obj);
	void redo (Object obj);
}
