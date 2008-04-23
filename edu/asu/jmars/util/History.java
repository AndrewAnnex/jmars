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

public class History {
	public static final int DEFAULT_HISTORY_SIZE = -1;
	
	// List of List of Object
	private LinkedList versions = new LinkedList ();
	private int version;
	private boolean busy = false;
	private int maxFrames;

	/**
	 * Creates a History log with an unbounded number of history
	 * frames.
	 */
	public History () {
		this(DEFAULT_HISTORY_SIZE);
	}
	
	/**
	 * Creates a History log with the specified maximum number of 
	 * history frames. If the maximum size is negative the log is
	 * unbounded in size.
	 * 
	 * @param maxHistoryFrames Maximum number of history frames,
	 *        negative means unbounded. 
	 */
	public History(int maxHistoryFrames){
		version = -1;
		maxFrames = maxHistoryFrames;
	}

	/**
	 * Returns true if an undo or redo operation is currently going on.
	 */
	 public boolean versionChanging () {
		 return busy;
	 }

	/**
	 * Mark the end of the current version and the start of another.
	 * Kills any forward versions. This does nothing if there are no
	 * changes since the last mark.
	 */
	public void mark () {
		if (busy)
			return;
		
		// trim any successive versions
		while (version < versions.size ()-1)
			versions.removeLast ();

		// create new bin if no current bin, or bin has at least one change
		if (version == -1 || ((LinkedList)versions.getLast ()).size() > 0) {
			versions.add (new LinkedList ());
			if ((maxFrames >= 0) && (versions.size() > maxFrames))
				versions.removeFirst();
			else
				version ++;
		}
	}

	/**
	 * Callback used by each Versionable to add an Object to the current
	 * version's changes. If the current version is not the latest, this
	 * method will call mark() to start a new version to hold the change,
	 * disposing any newer versions.
	 * <b>CAUTION:</b> If any Versionable calling this method does so during
	 * an undo() or redo(), the change will not be logged. It is considered
	 * poor form to attempt to log changes during an undo() or redo(), but
	 * doing so is ignored silently.
	 */
	public void addChange (Versionable versioned, Object data) {
		if (busy)
			return;
		
		if (version == -1 || version < versions.size()-1)
			mark ();

		// add changes at front so forward iterators undo in the right order
		if (version > -1){
			LinkedList current = (LinkedList) versions.get(version);
			current.addFirst (new Change (versioned, data));
		}
	}

	/**
	 * <bold>CAUTION:</bold>This method should not be used publically. It has
	 * been exposed for testing only.
	 * @return Current list of changes accumulated so far as an unmodifiable List.
	 */
	public List getCurrentChanges(){
		if (version == -1)
			return null;
		else
			return Collections.unmodifiableList((LinkedList)versions.get(version));
	}

	/**
	 * Undo all changes back to the previous mark
	 */
	public void undo () {
		// can only undo when there is more to undo
		if (version < 0)
			return;

		try {
			busy = true;

			LinkedList current = (LinkedList) versions.get(version);
			// undo all changes in order
			for (Iterator it = current.iterator (); it.hasNext(); ) {
				Change change = (Change)it.next ();
				change.versionable.undo (change.data);
			}
			version --;
		}
		finally {
			busy = false;
		}
	}

	/**
	 * Redo all changes up to the next mark
	 */
	public void redo  () {
		// can only redo when there is more to redo
		if (version >= versions.size ()-1)
			return;
		
		try {
			busy = true;
			
			version ++;
			LinkedList current = (LinkedList) versions.get(version);
			// redo all changes in reverse order
			ListIterator it = current.listIterator (current.size());
			while (it.hasPrevious()) {
				Change change = (Change)it.previous ();
				change.versionable.redo (change.data);
			}
		}
		finally {
			busy = false;
		}
	}

	class Change {
		public final Versionable versionable;
		public final Object data;
		public Change (Versionable versionable, Object data) {
			this.versionable = versionable;
			this.data = data;
		}
	}
}
