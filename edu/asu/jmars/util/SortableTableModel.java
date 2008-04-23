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


/**
 * A sorter for TableModels. The sorter has a model (conforming to TableModel)
 * and itself implements TableModel. TableSorter does not store or copy
 * the data in the TableModel, instead it maintains an array of
 * integers which it keeps the same size as the number of rows in its
 * model. When the model changes it notifies the sorter that something
 * has changed eg. "rowsAdded" so that its internal array of integers
 * can be reallocated. As requests are made of the sorter (like
 * getValueAt(row, col) it redirects them to its model via the mapping
 * array. That way the TableSorter appears to hold another copy of the table
 * with the rows in a different order. The sorting algorthm used is stable
 * which means that it does not move around rows when its comparison
 * function returns 0 to denote that they are equivalent.
 *
 * Adapted for use in JMARS.  The only real change to the code was the implementation
 * of a secondary sort, which was (rather curiously) left half-way implemented.
 *
 * This class was previously known as SortableTableSorter.  A copy was created/modified
 * to avoid impacting other layers using the original deprecated SortingTable.
 * 
 * Modification to this class are intended to get back on track of using a single version
 * of the truth maintained by TableModel events, not copies of the data tied together
 * via handling of UI events.
 */
package edu.asu.jmars.util;

import java.util.Date;
import java.util.Vector;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;


public class SortableTableModel extends AbstractTableModel implements TableModelListener 
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1695903769986006459L;
	
	private static DebugLog log = DebugLog.instance();
	
	int             sortedIndexes[];   // indirection table.  pointers to data rows sorted by N keys
	int             unsortedIndexes[]; // reverse of sortedIndexes.  Given a sorted index, return the original row
	Vector          sortingColumns  = new Vector();
	Vector          columnDirection = new Vector();
	
	
	// constructors
	
	public SortableTableModel() {
		sortedIndexes = new int[0]; // for consistency
		unsortedIndexes = new int[0];
	}
	
	public SortableTableModel(TableModel model) {
		setModel(model);
	}
	
	
	protected TableModel model;
	
	public TableModel getModel(){
		return model;
	}
	
	// By default, implement TableModel by forwarding all messages
	// to the model.
	
	
	public int getRowCount() {
		return (model == null) ? 0 : model.getRowCount();
	}
	
	public int getColumnCount() {
		return (model == null) ? 0 : model.getColumnCount();
	}
	
	public String getColumnName(int aColumn) {
		return (model == null) ? null : model.getColumnName(aColumn);
	}
	
	public Class getColumnClass(int aColumn) {
		if ( model == null ||
				model.getRowCount()<1 || 
				model.getValueAt( 0, aColumn)==null ){
			return Object.class;
		}
		return model.getValueAt( 0, aColumn).getClass();
	}
	
	public boolean isCellEditable(int row, int column) {
		return (model == null) ? false : model.isCellEditable(row, column);
	}
	
	//
	// Implementation of the TableModelListener interface,
	//
	// By default forward all events to all the listeners.
	public void tableChanged(TableModelEvent e) {
		fireTableChanged(e);
	}
	
	
	/**
	 * set the superclass's TableModel. Note that the
	 * "getter" methods for the model are sensitive to the sorted 
	 * state.
	 */
	public void setModel(TableModel model) {
		this.model = model;
		model.addTableModelListener(this);
		reallocateIndexes();
	}
	
	public Object getValueAt(int aRow, int aColumn) {
		checkModel();
		return model.getValueAt(sortedIndexes[aRow], aColumn);
	}
	
	public void setValueAt(Object aValue, int aRow, int aColumn) {
		checkModel();
		model.setValueAt(aValue, sortedIndexes[aRow], aColumn);
	}
	
	/**
	 * If the rows of the table are changed in any way, the index array must 
	 * be reset or sorting will be completely screwed.
	 */
	public void reallocateIndexes() {
		int rowCount = model.getRowCount();
		
		// Set up a new array of indexes with the right number of elements
		// for the new data model.
		sortedIndexes = new int[rowCount];
		
		// Initialise with the identity mapping.
		for (int row = 0; row < rowCount; row++) {
			sortedIndexes[row] = row;
		}
		unsortedIndexes = new int[rowCount];
		updateUnsortedIndexes();
	}
	
	/** 
	 * sort the table by the inputted column and in the inputted direction.
	 */
	public void sortByColumn(int column, boolean ascending) {
		sortingColumns.removeAllElements();
		sortingColumns.addElement( new Integer(column));
		
		columnDirection.removeAllElements();
		columnDirection.addElement( new Boolean(ascending));
		
		sort();
		tableChanged(new TableModelEvent(this));
	}
	
	
	/**
	 * sort the table by the inputted column and in the inputted direction, then
	 * do a secondary sort by another column.
	 */
	public void sortByColumn(int column1, boolean ascending1, int column2, boolean ascending2) {
		sortingColumns.removeAllElements();
		sortingColumns.addElement( new Integer(column1));
		sortingColumns.addElement( new Integer(column2));
		
		columnDirection.removeAllElements();
		columnDirection.addElement( new Boolean(ascending1));
		columnDirection.addElement( new Boolean(ascending2));
		
		sort();
	}
	

	// compare two rows of the table by the value of a column. 
	private int compareRowsByColumn(int row1, int row2, int column) 
	{
		// sort by the column
		
		// Check for nulls.
		Object o1 = model.getValueAt(row1, column);
		Object o2 = model.getValueAt(row2, column);
		
		// If both values are null, return 0.
		if (o1 == null && o2 == null) {
			return 0;
		} else if (o1 == null) { // Define null less than everything.
			return -1;
		} else if (o2 == null) {
			return 1;
		}
		
		/*
		 * We copy all returned values from the getValue call in case
		 * an optimised model is reusing one object to return many
		 * values.  The Number subclasses in the JDK are immutable and
		 * so will not be used in this way but other subclasses of
		 * Number might want to do this to save space and avoid
		 * unnecessary heap allocation.
		 */
		Class type = model.getColumnClass(column);
		if (type.getSuperclass() == java.lang.Number.class) {
			Number n1 = (Number)model.getValueAt(row1, column);
			double d1 = n1.doubleValue();
			Number n2 = (Number)model.getValueAt(row2, column);
			double d2 = n2.doubleValue();
			if (d1 < d2) {
				return -1;
			} else if (d1 > d2) {
				return 1;
			} else {
				return 0;
			}
		} else if (type == java.util.Date.class) {
			Date d1 = (Date)model.getValueAt(row1, column);
			long n1 = d1.getTime();
			Date d2 = (Date)model.getValueAt(row2, column);
			long n2 = d2.getTime();
			if (n1 < n2) {
				return -1;
			} else if (n1 > n2) {
				return 1;
			} else {
				return 0;
			}
		} else if (type == String.class) {
			String s1 = (String)model.getValueAt(row1, column);
			String s2 = (String)model.getValueAt(row2, column);
			int result = s1.compareTo(s2);
			if (result < 0) {
				return -1;
			} else if (result > 0) {
				return 1;
			} else {
				return 0;
			}
		} else if (type == Boolean.class) {
			Boolean bool1 = (Boolean)model.getValueAt(row1, column);
			boolean b1    = bool1.booleanValue();
			Boolean bool2 = (Boolean)model.getValueAt(row2, column);
			boolean b2    = bool2.booleanValue();
			if (b1 == b2) {
				return 0;
			} else if (b1) { // Define false < true
				return 1;
			} else {
				return -1;
			}
		} else {
			Object v1 = model.getValueAt(row1, column);
			String s1 = v1.toString();
			Object v2 = model.getValueAt(row2, column);
			String s2 = v2.toString();
			int result = s1.compareTo(s2);
			if (result < 0) {
				return -1;
			} else if (result > 0) {
				return 1;
			} else {
				return 0;
			}
		}
	}
	
	
	private int compare(int row1, int row2) {
		for (int level = 0; level < sortingColumns.size(); level++) {
			Integer column    = (Integer)sortingColumns.elementAt(level);
			boolean ascending = ((Boolean)columnDirection.elementAt(level)).booleanValue();
			int result = compareRowsByColumn(row1, row2, column.intValue());
			if (result != 0) {
				return ascending ? result : -result;
			}
		}
		return 0;
	}
	
	
	// Check that the indices correspond to the rows.  This appears to be a completely
	// pointless exercise, since a race condition, if it happens, can still happen
	// between checkModel() and the subsequent operation.
	private void checkModel() {	
		if (sortedIndexes.length != model.getRowCount()) {
			log.aprintln("Internal error: sorting indices do NOT correspond TableModel rows.  Race condition or lost event detected.");
//			reallocateIndexes();
//			sort();
		}
	}
	
	
	// sort the rows of the table by sorting the array of indices..
	private void sortInternal() {
		checkModel();
		shuttlesort((int[])sortedIndexes.clone(), sortedIndexes, 0, sortedIndexes.length);
		updateUnsortedIndexes();
	}
	private void sort() {
		sortInternal();
		this.fireTableDataChanged();
	}
	
	// update the sorting indices.
	private void updateUnsortedIndexes(){
		for (int i=0; i< sortedIndexes.length; i++){
			unsortedIndexes[ sortedIndexes[i] ] = i;
		}
	}
	
	/**
	 * Return the table to its full upright position by restoring the identity index.
	 * Fire off an event indicating the table data has changed so that any renderers
	 * can update themselves.
	 *
	 */
	public void unsort() {
		for(int i = 0; i<sortedIndexes.length; ++i) {
			sortedIndexes[i] = i;
			unsortedIndexes[i] = i;
		}
		this.fireTableDataChanged();
	}
	
	// This is a home-grown implementation which we have not had time
	// to research - it may perform poorly in some circumstances. It
	// requires twice the space of an in-place algorithm and makes
	// NlogN assigments shuttling the values between the two
	// arrays. The number of compares appears to vary between N-1 and
	// NlogN depending on the initial order but the main reason for
	// using it here is that, unlike qsort, it is stable.
	private void shuttlesort(int from[], int to[], int low, int high) {
		if (high - low < 2) {
			return;
		}
		int middle = (low + high)/2;
		shuttlesort(to, from, low, middle);
		shuttlesort(to, from, middle, high);
		
		int p = low;
		int q = middle;
		
		/* This is an optional short-cut; at each recursive call,
		 check to see if the elements in this subset are already
		 ordered.  If so, no further comparisons are needed; the
		 sub-array can just be copied.  The array must be copied rather
		 than assigned otherwise sister calls in the recursion might
		 get out of sinc.  When the number of elements is three they
		 are partitioned so that the first set, [low, mid), has one
		 element and and the second, [mid, high), has two. We skip the
		 optimisation when the number of elements is three or less as
		 the first compare in the normal merge will produce the same
		 sequence of steps. This optimisation seems to be worthwhile
		 for partially ordered lists but some analysis is needed to
		 find out how the performance drops to Nlog(N) as the initial
		 order diminishes - it may drop very quickly.  */
		
		if (high - low >= 4 && compare(from[middle-1], from[middle]) <= 0) {
			for (int i = low; i < high; i++) {
				to[i] = from[i];
			}
			return;
		}
		
		// A normal merge.
		for (int i = low; i < high; i++) {
			if (q >= high || (p < middle && compare(from[p], from[q]) <= 0)) {
				to[i] = from[p++];
			}
			else {
				to[i] = from[q++];
			}
		}
	}	
	public int getUnsortedIndex(int sortedPosition) {
		return this.unsortedIndexes[sortedPosition];
	}

	public int getSortedIndex(int unsortedPosition) {
		return this.sortedIndexes[unsortedPosition];
	}
	/**
	 * If a row we're currently sorting on changes, translate the
	 * event to be a 'change the world' event by returning a null so
	 * the parent doesn't propagate the original event (optimization) and
	 * call sort().  In this case, convert the event to a 'null', since
	 * an identical event will be caused at the end of the sort() method.
	 * 
	 * This seems like a horrible hack which could be removed by having
	 * an internalSort() method which fires off no events, but is a bit
	 * more subtle than that.  A sort needs to translate a row event to a 
	 * whole table changed event.  An update/insert/delete of a single row in 
	 * an un-sorted state should stay as is.  This approach sacrifices purity
	 * of design for easier to get correct implementation.
	 * 
	 * @param e
	 * @return
	 */

	public TableModelEvent checkForInterest(TableModelEvent e) {

		// If we were the source of the event (e.g, fired out of sort()), don't
		// keep processing it.  Otherwise, see if we need to re-sort, or at the
		// very least update our indexes.
		if(e.getSource() != this) { 
			if( e.getType() == TableModelEvent.INSERT ||
					e.getType() == TableModelEvent.DELETE) {
				log.println("insert/delete causing world to change.");
				reallocateIndexes(); // no ability to grow, so have to re-build
				if(sortingColumns.size() > 0) {
					log.println("Re-sorting.");
					sort();
					return null; // consume the event, we'll fire our own at end of sort
				} 
				// fall through: return the original event
				
			} else if( (sortingColumns.size() > 0) && e.getType() == TableModelEvent.UPDATE ) {
				// updates may cause a re-paint the world.  See if it's
				// either all columns, or one we care about.
				int col = e.getColumn();
				if(col == TableModelEvent.ALL_COLUMNS || 
						sortingColumns.contains(new Integer(col)) ) {
					log.println("Update to interesting column causing world to change.");
					log.println("Re-sorting.");
					sort();
					return null;
				}
				// fall through: return the original event
			}
		}
		// nope, no need to mutate the event.
		//System.out.println("Table update event not changed.");
		return e;
	}
}
