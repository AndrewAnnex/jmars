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


package edu.asu.jmars.util.stable;

import java.util.Comparator;

import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * If a client adds instances of this class to the STable TableColumnModel,
 * it will use the included Comparator for sorting instead of relying on
 * cells to implement Comparable.
 */
public class ComparableTableColumn extends TableColumn {
	Comparator columnComparator;
	public ComparableTableColumn (Comparator comparator) {
		super ();
		this.columnComparator = comparator;
	}
	public ComparableTableColumn (int modelIndex, Comparator comparator) {
		super (modelIndex);
		this.columnComparator = comparator;
	}
	public ComparableTableColumn (int modelIndex, int width, Comparator comparator) {
		super (modelIndex, width);
		this.columnComparator = comparator;
	}
	public ComparableTableColumn (int modelIndex, int width, TableCellRenderer renderer, TableCellEditor editor, Comparator comparator) {
		super (modelIndex, width, renderer, editor);
		this.columnComparator = comparator;
	}
	public Comparator getComparator () {
		return columnComparator;
	}
}
