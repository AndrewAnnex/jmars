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
 **  An extention to a JTable that allows the sorting of rows by multiple columns and the 
 **  selection of columns to display.
 **
 **  Sorting is accomplished by left-clicking on a column.  The first time a column is clicked, the
 **  rows of the table are sorted ascendingly by the values of that column. The second time the 
 **  column is clicked, the rows are sorted descendingly.  The third left-click on the column 
 **  returns the rows to their unsorted state.
 **  
 **  It is possible to do a secondary sort of the rows.  After sorting the rows by one column 
 **  (a primary sort), holding down the shift key while left-clicking in a different column will 
 **  sort the rows by that column but only within the individual sort cells of the first sort. 
 **  For example, assume a table consisting of the following rows:
 **             1 b
 **             2 c
 **             1 a
 **             2 a
 **  If the rows were sorted by the first column, they would be something like:
 **             1 b
 **             1 a
 **             2 c
 **             2 a
 **  Running a secondary sort of the rows on the second column would result in:
 **             1 a
 **             1 b
 **             2 a
 **             2 c
 **  Note that the secondary sort does not change the order of the rows with respect to the
 **  first column.
 **  
 **  Sorting by a secondary key without having first sorted by a primary key is treated like
 **  a primary sort.
 **
 **  Sorting is accomplished via a shuttlesort, which is similar to a qsort.  It might not be 
 **  as efficient as a qsort, but it is stable.
 **
 **  Right-clicking anywhere on the header bar brings up a n on-modal dialog with a checkbox for 
 **  each of the columns that may be displayed in the table. All currently displayed columns are 
 **  already checked.  Columns are displayed and hidden by checking and unchecking the 
 **  corresponding checkboxes. 
 **
 **  This class was abstracted from the JMARS stamp layer by James Winburn MSFF-ASU  Nov.2004
 **/
package edu.asu.jmars.util;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.geom.*;
import gnu.trove.*;

import edu.asu.jmars.util.*;
import edu.asu.jmars.*;
import edu.asu.jmars.swing.*;


public class SortingTable extends JTable
{
    private static DebugLog log = DebugLog.instance();

    private JScrollPane         scrollPane;
    private DefaultTableModel   tableModel = null;
    private JDialog             columnDialog;
    private SortableTableSorter sorter;
    private JTableHeader        tableHeader;
    private TableCellRenderer   defaultHeaderRenderer; 
    private TIntIntHashMap      map;
    private TIntIntHashMap      keyMap;

    private Object              copySemaphore = new Object();
    private Object              rowSemaphore = new Object();


    // A sorting table keeps tabs on the classes of all columns.
    // Note that this implies that any custom table model is defined, unless 
    // it wants to completely circumvent this mechanism, it should leave 
    // getColumnClass() unimplemented.
    public  HashMap             colClassMap    = new HashMap();

    // the table row key.  This is incremented each time a row is added. 
    // It is static because we want to keep the key of a row unique across 
    // ALL tables.
    static private int key = 0;



    public SortingTable(DefaultTableModel model) 
    {
	setTableModel( model);
	initTable();
    }


    public SortingTable( String [] c){
	setTableModel( new SortingTableModel());

	if (c!=null ){
	    for (int i=0; i<c.length; i++){
		addColumn( c[i], true, true);
	    }
	}

	initTable();
    }
	

    public SortingTable(String [] c, boolean[] v)
    {
	setTableModel( new SortingTableModel());

	if (c!=null ){
	    if (v!=null){
		for (int i=0; i<c.length; i++){
		    addColumn( c[i], v[i], true);
		}
	    } else {
		for (int i=0; i<c.length; i++){
		    addColumn( c[i], true, true);
		}
	    }
	}

	initTable();
    }


    public SortingTable(String [] c, boolean[] v, boolean[] s)
    {
	setTableModel( new SortingTableModel());

	if (c!=null ){
	    if (v!=null){
		if (s!=null){
		    for (int i=0; i<c.length; i++){
			addColumn( c[i], v[i], s[i]);
		    }
		} else {
		    for (int i=0; i<c.length; i++){
			addColumn( c[i], v[i], true);
		    }
		}
	    } else {
		if (s!=null){
		    for (int i=0; i<c.length; i++){
			addColumn( c[i], true, s[i]);
		    }
		} else {
		    for (int i=0; i<c.length; i++){
			addColumn( c[i], true, true);
		    }
		}
	    }
	}


	initTable();
    } 

	public void setColumnClass( String name, Class cl){
		colClassMap.put( name, cl);
	}

    /** 
     * The standard TableModel of a SortableTable. 
     */
     public class SortingTableModel extends DefaultTableModel 
    {
	    public Class getColumnClass( int col){
		    Class cl = (Class)colClassMap.get(getColumnName(col));
		    if (cl != null){
			    return cl;
		    } else {
			    if (getRowCount()<1 || getValueAt( 0, col)==null){
				    return Object.class;
			    } else {
				    return getValueAt( 0, col).getClass();
			    }
		    }
	    }

    }





    //----------------------------//
    //      General methods       //
    //----------------------------//


    /**
     * If the table structure is changed (adding or removing a column), the
     * "form" must be reset  (renderers, editors, and column widths).
     * Classes may overwrite this method to accomplish this. 
     */
    public void initialize(){}



    /**
     ** returns the current table model.
     **/
    public AbstractTableModel getTableModel(){
	return tableModel;
    }


    /**
     ** returns the panel associated with this Sorting Table.
     **/
    public JScrollPane getPanel(){
	return scrollPane;
    }


    /**
     * sets the rows and cols of the table to the rows and cols of the specified table.
     */
    public void copy( SortingTable newTable)
    {
	    synchronized( copySemaphore){
		    // initialize old table.
		    clearTable();
		    
		    // add the columns of the new table to the old.
		    for (int i=0; i< newTable.getTableModel().getColumnCount(); i++){
			    String name = newTable.getTableModel().getColumnName(i);
			    addColumn( name, newTable.getTableModel().getColumnClass(i));
			    setColumnVisible(  name, newTable.getColumnVisibility(name));
			    setColumnShowable( name, newTable.getColumnShowability( name));
		    }
		    
		    // add the rows of the new table to the old.
		    Object [][] newRows = newTable.getAllRows();
		    addRows( newRows);
		    
		    updateDisplayedColumns();
	    }
    }

	


    // -------------------------//
    //      COLUMN methods      //
    // -------------------------//

    /**
     * adds a column with the table.  The column will be added to the underlying TableModel
     * and will be displayed.
     */
    public void addColumn( String name){
	addColumn( name, true, true);
    }


    /**
     * adds a column to the table but specifies a class for the column.
     * The column will be visible and showable, but these can be turned
     * off with a call to setColumnVisible() and setColumnShowable().
     */
    public void addColumn( String name, Class cl){
	colClassMap.put( name, cl);
	addColumn( name, true, true);
    }

 
    /**
     * adds a column to the table.  The column will be added to the underlying TableModel
     * but it will only be displayed if "status" is true.
     */
    public synchronized void addColumn(String colName, boolean status , boolean showState)
    {
	// First check that the proposed new column does not already exist in the table.
	// If it does, just return.
	for (int i =0; i < tableModel.getColumnCount(); i++){
	    if (colName.equalsIgnoreCase( tableModel.getColumnName(i))){
		return;
	    }
	}
	
	// Get the selected rows.  The adding-a-column process de-selects the rows but 
	// they will be restored because we save them here. 
	int [] selectedRows = getSelectedRows();

	int oldLength = tableModel.getColumnCount();
	
	// setup new column names.
	int rows = tableModel.getRowCount();

	// add the column.
	tableModel.addColumn( colName);

	int newLength = tableModel.getColumnCount();

	// set up a TableColumn for the new column.
	try {
	    TableColumn col = getColumn( colName);
	    setTableColumn( colName, col);
	} catch (IllegalArgumentException iae){
	    log.println("unable to add TableColumn for column: " + colName);
	}

	// If the column is not viewable, it should not be displayed.
	if (showState == false){
	    status = false;
	}

	// setup the various states of the new column.
	setColumnVisibility(  colName, status);
	setColumnShowability( colName, showState);

	// reset the sorter.
	sorter.reallocateIndexes();

	updateDisplayedColumns();


	// reset the selection of the rows. By doing this, the selection listeners will be 
	// notified that things have changed.
	setSelectedRows( selectedRows);
    }
 


   /**
     ** removes the column (if it exists) from the table.
     **/
    public synchronized void removeColumn( String colName)
    {

	    colClassMap.remove( colName);

	// Get the selected rows.  The remove-a-column process de-selects the rows but 
	// they will be restored because we save them here. 
	int [] selectedRows = getSelectedRows();

	// Get the index of the column to remove.
	int columnIndex = getColumnIndex( colName);
	if (columnIndex == -1){
	    return;
	}

	// remove the column from the visible representation of the table,
	// if it exists.
	try {
	    TableColumn col = getColumn( colName);
	    getColumnModel().removeColumn( col);
	} catch (IllegalArgumentException iae){
	    // This just means that the column is currently not being displayed.  
	    // Not to worry.
	}


	// get a new array of names.
	int newColCount = tableModel.getColumnCount()-1;
	Object  [] newColumnNames     = new String[ newColCount];
	for (int i=0; i < columnIndex; i++){
	    newColumnNames[i]    = tableModel.getColumnName(i);
	}
	for (int i=columnIndex; i<newColumnNames.length; i++){
	    newColumnNames[i]    = tableModel.getColumnName(i+1);
	}

	// remove the column from the various lists.
	removeTableColumn( colName);
	removeColumnVisibility(  colName);
	removeColumnShowability( colName);

	// remove the column from the data.
	ArrayList newData = new ArrayList();
	for (int row=0; row< tableModel.getRowCount(); row++){
	    Object [] oldRow = getRow(row);
	    Object [] newRow = new Object[ oldRow.length -1];
	    for (int i=0; i < columnIndex; i++){
		newRow[i] = oldRow[i];
	    }
	    for (int i=columnIndex; i<newColCount; i++){
		newRow[i]=oldRow[i+1];
	    }
	    newData.add( newRow);
	}

	// stuff the new data into the table.
	Object [][] newDataArray = (Object[][])newData.toArray( new Object[0][newColCount]);
	tableModel.setDataVector( newDataArray, newColumnNames );

	// reset the sorter.
	sorter.reallocateIndexes();

	// reset the selection of the rows. By doing this, the selection listeners will be 
	// notified that things have changed.
	setSelectedRows( selectedRows);

	updateDisplayedColumns();

    }
 


    /**
     ** sets the cell renderer for the specified column.  This differs from the
     ** standard setCellRenderer in that it will set the renderer of a column which
     ** is currently removed from the table.  Later, if the column should be added 
     ** back to the table, it will be rendered with the renderer set here.
     **/
    public synchronized void setCellRenderer( String name, TableCellRenderer dtcr){
	TableColumn col = getTableColumn( name);
	if (col!=null){
	    col.setCellRenderer( dtcr);
	}
    }


    /**
     ** sets the cell editor for the specified column.  This differs from the
     ** standard setCellEditor in that it will set the editor of a column which
     ** is currently removed from the table.  Later, if the column should be added 
     ** back to the table, it will be edited with the editor set here.
     **
     ** The policy regarding edits is that all columns are un-editable unless 
     ** editors are set up for the SPECIFIC column.
     **/
    public synchronized void setCellEditor( String name, TableCellEditor dtcr){
	for (int index=0; index< tableModel.getColumnCount(); index++){
	    if (tableModel.getColumnName(index).equals(name)){
		if (dtcr != null){
		    TableColumn tc = getTableColumn( name);
		    tc.setCellEditor( dtcr);
		}
		return;
	    }
	}
    }


    /**
     * A column that is not showable does not appear in the ColumnsDialog 
     * Such a column isn't just invisible, but cannot be made visible.
     */
    public synchronized void setColumnShowable( String name, boolean showable){
	setColumnShowability( name, showable);
	setupColumnsDialog();
    }

	

	
    /**
     *  sets the statuses of the columns and hides them from the table if
     *  the status is false.
     */
    public synchronized void setColumnStatus( boolean [] s){
	for (int i=0; i< tableModel.getColumnCount() && i<s.length; i++){
	    String name = tableModel.getColumnName(i);
	    TableColumn col = getTableColumn( name);
	    if (col==null){
		log.println("cannot set column status for column: " + tableModel.getColumnName(i));
	    } else {
		if (s[i]==false && getColumnVisibility(name)==true){
		    getColumnModel().removeColumn( col);
		} else if  (s[i]==true && getColumnVisibility( name)==false) {
		    getColumnModel().addColumn( col);
		}
		setColumnVisibility( name, s[i]);
	    }
		
	}
    }



    /**
     * gets in index of the column with the inputted name. Note that this is 
     * the index of the column in the data, not the displayed column.
     */
    public synchronized int getColumnIndex( String name)
    {
	for (int i=0; i< tableModel.getColumnCount(); i++){
	    if (tableModel.getColumnName(i).equalsIgnoreCase(name)){
		return i;
	    }
	}
	return -1;
    }




    //---------------------//
    //     ROW methods     //
    //---------------------//


    /**
     ** Adds one row (defined as an array of objects) to the table.
     ** Note that an extra "key" field is added to the row before 
     ** it is actually added to the table's data.
     **/
    public void addRow( Object [] dataRow){
	addRows(new Object[][]{dataRow} );
    }



    /**
     ** Adds rows (defined as a 2D array of objects) to the table.
     **/
    public synchronized void addRows( Object [][] dataRow){

	if (dataRow==null || dataRow.length==0 || dataRow[0]==null){
	    log.println("no rows to be added.");
	    return;
	}

	int modelSize = tableModel.getColumnCount();
	int newSize   = dataRow[0].length;
	if (newSize != modelSize){
	    log.aprintln("Cannot add rows on account of like rowsize=" + newSize + " and modelsize=" + modelSize);
	    return;
	}

	// make new row with appended index column
	for (int j=0;j< dataRow.length; j++){
	    
	    // add row to data.
	    tableModel.addRow( dataRow[j]);

	    // Deal with the key map.
	    int index = tableModel.getRowCount() -1;
	    map.put( key, index);
	    keyMap.put( index, key);
	    key++;
	}

	// reset the sorter.
	sorter.reallocateIndexes();
    }



    /**
     ** Removes the specified row from the table.
     **/
    public void removeRow( int row){
	removeRows( new int[]{row});
    }



	/**
	 ** Removes the rows corresponding to the specified indexes
	 ** from the table.
	 **
	 ** Note that we have to get a list of rows from the 
	 ** array of indexes before anything is removed.
	 ** Since indexes of the table data are updated each time 
	 ** a row is removed, simply removing indexes could mean 
	 ** that the wrong rows are removed.
	 **
	 ** Also note that the array of row indexes to delete are
	 ** assumed to be the raw row indexes (i.e. retrieved with
	 ** getSelectedRows()).
	 **/
	public synchronized void removeRows( int [] rows) {
		getSelectionModel().setValueIsAdjusting(true);
		int [] sortedRows = (int[]) rows.clone ();
		Arrays.sort (sortedRows);
		for (int i = rows.length-1; i >= 0; i--) {
		    // delete the rows from all the places that hold some sort
		    int index = sortedRows[i];
		    if ( index > -1 && index < tableModel.getRowCount()) {
			int key   = keyMap.get(index);
			tableModel.removeRow( index);
			map.remove(key);
			keyMap.remove(index);
		    }
		}

		// reset the sorter.
		sorter.reallocateIndexes();
		getSelectionModel().setValueIsAdjusting(false);
	}



    /**
     ** Removes all the rows of the table.
     **/
    public synchronized void removeAllRows(){
	getSelectionModel().clearSelection();
	map.clear();
	keyMap.clear();
	int rows = tableModel.getRowCount();
	for(int i=0; i<rows; i++){
	    tableModel.removeRow(0);
	}
	sorter.reallocateIndexes();
    }


 
	/**
	 ** Gets the index of the first (and possibly the only) row that was selected.  
	 ** Note that the unsorted row index is returned.
	 **/
	public int getSelectedRow() 
	{
		int [] selectedRows = getSelectedRows();
		if (selectedRows.length==0){
			return -1;
		} else {
			return selectedRows[0];
		}
		//int row = super.getSelectedRow();
		//if (row < 0 ) {
		//	return -1;
		//}
		//return getUnsortedIndex( row);
	}


	Object selectionSemaphore = new Object();

	/**
	 ** Gets the indexes of all the selected rows.
	 ** (N.B. the unsorted indices of the rows are returned.)
	 ** If no rows are selected, a 0-lengthed array is returned.
	 **/
	public int [] getSelectedRows(){
		synchronized( selectionSemaphore){
			int [] rows = super.getSelectedRows();
			if (rows==null || rows.length==0){
				return new int[0];
			}
			for (int i=0; i< rows.length; i++){
				rows[i] = getUnsortedIndex( rows[i]);
			}
			return rows;
		}
	}
	


	/**
	 * sets the selected rows of the table to those specified in the parameter.  
	 * This is the inverse of the getSelectedRows() method.
	 * NOTE: since the table view is being manipulated, "rows" are sorted 
	 * indices.
	 */
	public void setSelectedRows( int [] rows){
		synchronized( selectionSemaphore){
			getSelectionModel().setValueIsAdjusting(true);
			clearSelection();
			if (rows!=null && rows.length>0){
				for (int i=0; i< rows.length; i++){
					addRowSelectionInterval( rows[i], rows[i]);
				}
			}
			getSelectionModel().setValueIsAdjusting(false);
		}
	}


	/**
	 * returns all the rows that are currently selected as an array of arrays of Objects.
	 */
	public Object [][] getAllSelectedRows(){
		synchronized( selectionSemaphore){
			int       numberOfColumns = tableModel.getColumnCount();
			ArrayList list            = new ArrayList();
			int []    rows            = getSelectedRows(); 
			if (rows!=null && rows.length>0){ 
				for (int i=0; i< rows.length; i++){ 
					list.add( getRow( rows[i]));
				} 
			}
			return (Object[][])list.toArray( new Object[0][numberOfColumns]);
		}
	}
	

	/**
	 * returns all the rows of the table as an array of arrays of Objects.
	 */
	public Object [][] getAllRows(){
		synchronized( rowSemaphore){
			int  rows      = tableModel.getRowCount();
			int  cols      = tableModel.getColumnCount();
			ArrayList list = new ArrayList();
			for (int i=0; i<rows; i++){
				list.add( getRow(i));
			} 
			
			return (Object[][])list.toArray( new Object[0][cols]);
		}
	}
		
   
	/**
	 ** Gets the row at the specified index. 
	 ** Note that a copy of the row is retrieved, rather than a pointer to the 
	 ** row.  
	 **/
	public Object [] getRow( int row)
	{
		synchronized( rowSemaphore){
			Object [] dataRow = new Object[tableModel.getColumnCount()];
			for (int i=0; i< tableModel.getColumnCount(); i++){
				dataRow[i] = tableModel.getValueAt( row, i);
			}
			return dataRow;
		}
	}
	

    /**
     ** Returns the index of the specified row in the table data. 
     ** As this method does not deal with selected rows, the index
     ** returned is unaware of any sorting that may have occured on
     ** the data.
     **/
    public synchronized int getIndex( Object [] row){
	if (row==null){
	    return -1;
	}
	for (int i=0; i< tableModel.getRowCount(); i++){
	    Object [] tableRow = (Object []) getRow(i);
	    boolean foundRow = true;
	    for (int j=0; j< row.length; j++){
		if (tableRow[j]==null && row[j]==null){
		    continue;
		}
		if (tableRow[j]==null || row[j]==null ||
		    !tableRow[j].equals( row[j])){
		    foundRow=false;
		    break;
		}
	    }
	    if (foundRow==true){
		return i;
	    }
	}
	return -1;
    }



     /**
     ** returns the index of the row in the displayed table 
     ** that corresponds to the index of the row in the 
     ** raw table data. 
     **
     ** Returns the inputted row value if it is less than 0.
     **/
    public synchronized int getSortedIndex( int row){
	if (row <0){
	    return row;
	}
	return sorter.unsortedIndexes[row];
    }

    /**
     ** assuming that the inputted index is a sorted index, returns what 
     ** would be the unsorted index.  This is the obverse of getSortedIndex().
     **/
    public synchronized int getUnsortedIndex( int row) {
	return sorter.indexes[row];
    }



    /**
     ** Changes the row at the specific index in the table to the specified row.
     **
     ** @param unsortedIdx - Index of row to replace, in the unsorted coordinate
     ** system.
     ** @param dataRow  - Array of Object values.
     **/
    public synchronized void changeRow( int unsortedIdx, Object [] dataRow){
	    if (unsortedIdx<0 || unsortedIdx >= tableModel.getRowCount()){
		    return;
	    }
	
	    for (int i=0; i<tableModel.getColumnCount(); i++){
		    tableModel.setValueAt( dataRow[i], unsortedIdx, i);
	    }
    }


    //----------------------------------------------------------//
    //  SORT ROWS...the raison d'etre of the entire class. //
    //----------------------------------------------------------//


     /*
     * A right-click brings up a popup dialog (non-model, of course) that allows users 
     * to specify which columns are displayed in the table.
     *
     * A left-click sorts the rows by the column that was clicked in.  Sorting can be 
     * ascending or descending.  The first left-click does an ascending sort.  The second 
     * left-click sorts descendingly.  A third left-click returns the rows to their unsorted
     * order.
     *
     * Holding down the shift key while left-clicking does a secondary sort (either ascending
     * or descending) on the rows.  If a primary sorting column has not been previously 
     * specified the clicked column becomes the primary sorting column and the rows are 
     * sorted accordingly.
     */
    int         primaryColumn             = -1;
    int         primaryModelColumn        = -1;
    boolean     primaryColumnAscending    = true;
    int         secondaryColumn           = -1;
    int         secondaryModelColumn      = -1;
    boolean     secondaryColumnAscending  = true;
    final int   UNSORTED                  = 0;
    final int   ASCENDING                 = 1;
    final int   DESCENDING                = 2;
    int         primarySortState          = UNSORTED;
    int         secondarySortState        = UNSORTED;
    boolean     primarySort               = true;
    int         sortColumn                = -1;
    TableColumn primeCol                  = null;
    Object      sortingSemaphore          = new Object();


    /**
     * updates the order of the rows in a table according to the last sort 
     * that was done.
     */
    public void resortRows(){
	    synchronized( sortingSemaphore){
		    sortRows( false);
	    }
    }


    /**
     * Sorts the rows of the table.  If newSort is true, this is considered a new sort
     * in which case the sort state counters are updated.  If newSort is false, a 
     * previous sort is simply recreated and no counters are updated.
     */
    private void sortRows( boolean newSort){
	if (sortColumn == -1){
	    return;
	}
		
	// Get the selected rows.  The sorting process de-selects the rows but 
	// they will be restored because we save them here. 
	int [] selectedRows = getSelectedRows();

	// Clear all current header decorations.
	for (int i=0; i< tableModel.getColumnCount(); i++){
	    TableColumn col = getTableColumn( tableModel.getColumnName( i));
	    if (col==null){
		log.println("cannot set header for column: " + tableModel.getColumnName(i));
		return;
	    } else {
		col.setHeaderRenderer( defaultHeaderRenderer);
	    }
	}

	// if this is a primary sort or the primary column is not defined for a secondary sort
	// then do a primary sort.
	if (primarySort == true || primaryColumn == -1){
	    secondaryColumn      = -1;
	    secondarySortState   = UNSORTED;
	    if (newSort == true) {
		if (primaryModelColumn != convertColumnIndexToModel(sortColumn)){
		    primarySortState = ASCENDING;
		} else {
		    primarySortState = (primarySortState + 1) % 3;
		}
		if (sortColumn != -1){
		    primaryColumn        = sortColumn;
		}
	    }
	    if (primaryColumn == -1){
		return;
	    }

	    primeCol            = getTableColumn( getColumnName( primaryColumn));
	    if (primeCol==null){
		log.println("sorting cannot get prime sorting column: " + getColumnName( primaryColumn));
		return;
	    }
	    primaryModelColumn  = convertColumnIndexToModel(primaryColumn);

	    switch (primarySortState) {
	    case ASCENDING:
		primaryColumnAscending = true;
		sorter.sortByColumn( primaryModelColumn, primaryColumnAscending);
		break;
	    case DESCENDING:
		primaryColumnAscending = false;
		sorter.sortByColumn( primaryModelColumn, primaryColumnAscending);
		break;
	    default: // unsort
		sorter.sortByKey( keyMap);
		break;
	    }
	} else {  // do a secondary sort.
	    if (newSort == true) {
		if (secondaryModelColumn != convertColumnIndexToModel(sortColumn)){
		    secondarySortState = ASCENDING;
		} else {
		    secondarySortState = (secondarySortState + 1) % 3;
		}
	    }
	    TableColumn secondaryCol = null;
	    secondaryCol            = getTableColumn( getColumnName( sortColumn));
	    if (secondaryCol==null){
		log.println("sorting cannot get prime sorting column: " + getColumnName( sortColumn));
		return;
	    }
	    secondaryModelColumn  = convertColumnIndexToModel(sortColumn);

	    switch (secondarySortState) {
	    case ASCENDING:
		secondaryColumnAscending = true;
		sorter.sortByColumn( primaryModelColumn,  primaryColumnAscending,
				     secondaryModelColumn,secondaryColumnAscending );
		secondaryCol.setHeaderRenderer( new SecondIncreaseDecorator());
		break;
	    case DESCENDING:
		secondaryColumnAscending = false;
		sorter.sortByColumn( primaryModelColumn, primaryColumnAscending,
				     secondaryModelColumn,secondaryColumnAscending );
		secondaryCol.setHeaderRenderer( new SecondDecreaseDecorator());
		break;
	    default: // unsort
		sorter.sortByKey( keyMap);
		secondaryCol.setHeaderRenderer( defaultHeaderRenderer);
		break;
	    }


	}

	// We should draw the primary column decoration regardless of whether we did a primary 
	// or a secondary sort.
	switch (primarySortState) {
	case ASCENDING:
	    primeCol.setHeaderRenderer( new FirstIncreaseDecorator());
	    break;
	case DESCENDING:
	    primeCol.setHeaderRenderer( new FirstDecreaseDecorator());
	    break;
	default: // unsort
	    primeCol.setHeaderRenderer( defaultHeaderRenderer);
	    break;
	}


	// reset the selection of the rows. By doing this, the selection listeners will be 
	// notified that things have changed.
	//setSelectedRows( selectedRows);
	if (selectedRows.length > 0){
	    getSelectionModel().setValueIsAdjusting(true);
	    clearSelection();
	    for (int i=0; i<selectedRows.length; i++){
		int row = getSortedIndex( selectedRows[i]);
		addRowSelectionInterval( row, row);
	    }
	    getSelectionModel().setValueIsAdjusting(false);
	}
    }




    //----------------------------------------//
    //      The SortingTable's PRIVATES.      //
    //----------------------------------------//


    // Removes all the data from the TableModel and clears all the displayed columns.
    // Note to implementors: we can't just set up a new table model because listeners
    // might be set up with the old one.
    private void clearTable()
    {
	clearColumnVisibility();
	clearColumnShowability();
	map.clear();
	keyMap.clear();
	sorter.reallocateIndexes();

	Object  []  newColumnNames = (Object[])new String[0];
	Object [][] newDataArray   = new Object[0][0];
	tableModel.setDataVector( newDataArray, newColumnNames );
    }
	


    // sets the TableModel of the table and a bunch of other stuff relating 
    // to setting up a SortingTable.
    private void setTableModel( DefaultTableModel tm){
	tableModel = null;
	tableModel = tm;
	sorter = new SortableTableSorter( (TableModel)tableModel);
	super.setModel( sorter);
	setAutoResizeMode( JTable.AUTO_RESIZE_OFF);

	// Set up the TableColumns.
	clearTableColumns();
	for (int i=0; i< tableModel.getColumnCount(); i++){
	    String colName = tableModel.getColumnName(i);
	    try {
		TableColumn col = getColumn( colName);
		setTableColumn( colName, col);
	    } catch (IllegalArgumentException iae) {
		log.println("Unable to find column during setup: " + colName);
		log.printStack(-1);
	    }
	}

	// set up the column states.
	clearColumnShowability();
	clearColumnVisibility();
	for (int i=0; i< tableModel.getColumnCount(); i++){
	    String name = tableModel.getColumnName(i);
	    setColumnVisibility( name, true);
	    setColumnShowability( name, true);
	}


	// set up the map for retrieving the index of the rows.
	map    = null;
	map    = new TIntIntHashMap();
	keyMap = null;
	keyMap = new TIntIntHashMap();
	for (int i=0 ;i< tableModel.getRowCount(); i++){
	    key++;
	    map.put( key, i);
	    keyMap.put( i, key);
	}

	setupColumnsDialog();


	// Make sure that nothing is editable unless an editor has specifically been set up 
	// for a column.
	/*
	setDefaultEditor( java.lang.Object.class, null);
	setDefaultEditor( java.lang.Number.class, null);
	setDefaultEditor( java.awt.Color.class, null);
	setDefaultEditor( java.lang.Integer.class, null);
	setDefaultEditor( java.lang.Double.class, null);
	setDefaultEditor( String.class, null);
	setDefaultEditor( LineType.class, null);
	setDefaultEditor( java.lang.Boolean.class, null);
	*/
	defaultEditorsByColumnClass.clear(); 
    }



    // sets whether a column should be visible or hidden.
    private void setColumnVisible( String name, boolean vis){
	int index = getColumnIndex( name);
	if (index== -1){
	    return;
	} else {
	    setColumnVisibility( name, vis);
	}
	if (vis==true && isColumnVisible( name)==false){
	    getColumnModel().addColumn( getTableColumn( name));
	}
	if (vis==false && isColumnVisible( name)==true){
	    getColumnModel().removeColumn( getTableColumn( name));
	}
    }


    // returns whether the inputted column is currently displayed.
    private boolean isColumnVisible( String name){
	TableColumn col;
	try {
	    // column is displayed
	    col = getColumn( name);
	    return true;
	} catch (IllegalArgumentException iae){
	    // column is not currently displayed.
	    return false;
	}
    }



    // sets up things common to all SortingTables.
    private void initTable()
    {
	updateDisplayedColumns();
	
	// setup listeners
	getTableHeader().addMouseListener( new HeaderMouseListener());
	
	scrollPane = new JScrollPane(this,
				     JScrollPane.  VERTICAL_SCROLLBAR_AS_NEEDED,
				     JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	scrollPane.addMouseListener( new MouseAdapter() {
		public void mouseClicked(MouseEvent e){
		    if(SwingUtilities.isRightMouseButton(e)){
			columnDialog.setVisible(true);
		    } 
		}
	    });
	
	defaultHeaderRenderer = getTableHeader().getDefaultRenderer();
	
	
	// We must do this little dance to make sure that the columnDialog 
	// disappears when the layer is made invisible somehow.
	addAncestorListener( new AncestorAdapter(){
		public void ancestorRemoved(AncestorEvent e){
		    if (columnDialog !=null ){
		        columnDialog.dispose();
		    }
		}
	    });

	try {
		setDefaultRenderer( Class.forName( "java.awt.Color" ), 
				    new ColorCellRenderer() );
		setDefaultRenderer( Class.forName( "java.lang.Boolean" ), 
				    new BooleanCellRenderer() );
		setDefaultRenderer( Class.forName( "java.lang.String" ), 
				    new TextCellRenderer() );
		setDefaultRenderer( Class.forName( "java.lang.Integer" ), 
				    new TextCellRenderer() );
		setDefaultRenderer( Class.forName( "java.lang.Double" ), 
				    new TextCellRenderer() );
        } catch( ClassNotFoundException ex ) {
		log.aprintln("Unable to find class to install a column type renderer");
        }

    }	



	//  The cell renderer for the line type column.
	private class ColorCellRenderer extends JPanel implements TableCellRenderer
	{
		private Color color;
		private boolean    selected;
		
		public Component getTableCellRendererComponent(JTable t, Object value, boolean s, boolean f, int row, int column) {
			selected = s || f;
			color = (Color)value;
			this.repaint();
			return this;
		}
		
		public void paintComponent( Graphics g){
			Graphics2D g2 = (Graphics2D)g;
			Dimension d = getSize();
			if (selected){
				g2.setPaint( getSelectionBackground());
				g2.fill( new Rectangle2D.Double(0,0, d.width, d.height));
			} 
			if (color == null){
				g2.setPaint( Color.lightGray);
				g2.fill( new Rectangle2D.Double(0,0, d.width, d.height));		    
			} else {
				g2.setPaint( Color.darkGray);
				g2.draw( new Rectangle2D.Double(1,1, d.width-4, d.height-4));
				g2.setPaint( Color.white);
				g2.draw( new Rectangle2D.Double(2,2, d.width-4, d.height-4));
				g2.setColor( color );
				g2.fill(new Rectangle2D.Double( 3,3, d.width-6, d.height-6));
			}
		}
	}
	
    
	// The cell editor for color columns.
	public class ColorCellEditor extends javax.swing.AbstractCellEditor implements TableCellEditor {
		Color currentColor;
		
		ColorCellRenderer renderer = new ColorCellRenderer();
		
		public ColorCellEditor() {
			renderer.addMouseListener( new MouseAdapter() {
					public void mousePressed( MouseEvent e) {
						if (e.getClickCount() > 1 && currentColor != null) {
							Color newColor = JColorChooser.showDialog( Main.mainFrame,
												   "Pick a Color",
												   currentColor);
							if (newColor !=null){
								currentColor = newColor;
								fireEditingStopped();
							}

						}
					}
				});
		}
		
		//Implement the one CellEditor method that AbstractCellEditor doesn't.
		public Object getCellEditorValue() {
			return currentColor;
		}
		
		//Implement the one method defined by TableCellEditor.
		public Component getTableCellEditorComponent(JTable t, Object v, boolean i, int r, int c) {
			currentColor = (Color)v;
			return renderer.getTableCellRendererComponent( t,v,i,true,r,c);
		}	
		
	} // end: class ColorCellEditor 



	
	// The cell editor for Boolean columns.
	public class BooleanCellRenderer extends JPanel implements TableCellRenderer
	{
		public Component getTableCellRendererComponent(JTable t, Object value, boolean s, boolean f, int row, int column) {
			setLayout( new BorderLayout());
			removeAll();
			boolean cellValue  = (value==null) ? false: ((Boolean)value).booleanValue();
			JCheckBox box = new JCheckBox( "", cellValue);
			box.setHorizontalAlignment( JCheckBox.CENTER);
			if (s || f){
				box.setBackground( getSelectionBackground());
			} else {
				box.setBackground( Color.white);
			}
			add( box, BorderLayout.NORTH);
			return this;
		}
		
	}
	
	// Editor for Boolean-classed table columns.
	static JCheckBox checkBox = new JCheckBox();
	{
		checkBox.setHorizontalAlignment( JCheckBox.CENTER);
	}
	public class BooleanCellEditor extends DefaultCellEditor  {
		public BooleanCellEditor(){
			super( checkBox);
		}
		public boolean isCellEditable(EventObject e){
			int clickCount = ((MouseEvent)e).getClickCount();
			return (clickCount>1);
		}
	}


	// Renderer for Strings, Integers, and Doubles.
	private class TextCellRenderer extends JPanel implements TableCellRenderer
	{
		public Component getTableCellRendererComponent(JTable t, Object value, boolean s, boolean f, int row, int column) {
			setLayout( new BorderLayout());
			removeAll();
			JTextArea box;
			if (value ==null) {
				box = new JTextArea("");
			} else if (value.getClass() == Integer.class){
				box = new JTextArea( ((Integer)value).toString());
			} else if (value.getClass() == String.class){
				box = new JTextArea( (String)value);
			} else if (value.getClass() == Double.class){
				box = new JTextArea( ((Double)value).toString());
			} else {
				box = new JTextArea();
			}
			box.setLineWrap(true);
			if (s || f){
				box.setBackground( getSelectionBackground() );
			} else {
				box.setBackground( Color.white);
			}
			add( box, BorderLayout.NORTH);
			return this;
		}
	}
	
	
	// Editor for String-classed table columns.
	public class TextCellEditor extends DefaultCellEditor  {
		public TextCellEditor(){
			super( new JTextField());
		}
		public boolean isCellEditable(EventObject e){
			if (e==null){
				return false;
			} 
			int clickCount = ((MouseEvent)e).getClickCount();
			return (clickCount>1);
		}
	}
	
	
	
	
	// Editor for number-classed table columns.
	static JTextField numberTextField = new JTextField();
	{
		numberTextField.setHorizontalAlignment( JTextField.RIGHT);
	}
	

	// Editor for Integer-classed table columns.
	public class IntegerCellEditor extends DefaultCellEditor  {
		public IntegerCellEditor(){
			super( numberTextField);
		}
		public boolean isCellEditable(EventObject e){
			if (e==null){
				return false;
			} 
			int clickCount = ((MouseEvent)e).getClickCount();
			return (clickCount>1);
		}
		
		public Object getCellEditorValue(){
			Integer integerValue;
			String str = (String)super.getCellEditorValue();
			try {
				integerValue = new Integer( str);
			} catch (Exception e) {
				System.out.println("unable to convert inputted string to Integer in column");
				integerValue = new Integer(0);
			}
			return integerValue;
		}
	}



	// Editor for Double-classed table columns.
	public class DoubleCellEditor extends DefaultCellEditor  {
		public DoubleCellEditor(){
			super( numberTextField);
		}
		public boolean isCellEditable(EventObject e){
			if (e==null){
				return false;
			} 
			int clickCount = ((MouseEvent)e).getClickCount();
			return (clickCount>1);
		}
		
		public Object getCellEditorValue(){
			Double doubleValue;
			String str = (String)super.getCellEditorValue();
			try {
				doubleValue = new Double( str);
			} catch (Exception e) {
				System.out.println("unable to convert inputted string to Double in column");
				doubleValue = new Double(0.0);
			}
			return doubleValue;
		}
	}






			

    // Defines behavior for clicking in the header of the table.
    private class HeaderMouseListener extends MouseAdapter {
	public void mouseClicked(MouseEvent e){
	    if(SwingUtilities.isRightMouseButton(e)){
		setupColumnsDialog();
		columnDialog.setVisible(true);
	    } else {
		int shiftPressed = e.getModifiers()&InputEvent.SHIFT_MASK;
		synchronized( sortingSemaphore){
			primarySort      = (shiftPressed == 0);
			sortColumn       = getColumnModel().getColumnIndexAtX( e.getX());
			sortRows( true);
		}
	    }
	}
    } // end: class HeaderMouseListener


    // updates visibility array and updates the popup Columns Dialog.
    private void updateDisplayedColumns(){
	// first clear out all the columns.
	for (int i=0; i< tableModel.getColumnCount(); i++){
	    if (getColumnModel().getColumnCount()>0){
		getColumnModel().removeColumn(getColumnModel().getColumn(0));
	    }
	}
	for (int i=0; i< tableModel.getColumnCount(); i++){
	    String colName = tableModel.getColumnName(i);
	    setColumnVisible( colName, getColumnVisibility( colName));
	}

	// Update the modelIndex of all columns in the tableModel.  If we don't do this and
	// a non-end column is removed, the columns after the one removed will have an 
	// index that is one greater than they should.
	for (int i=0; i< tableModel.getColumnCount(); i++){
	    String name = tableModel.getColumnName(i);
	    TableColumn tc = getTableColumn( name);
	    tc.setModelIndex( i);
	    setTableColumn( name, tc);
	}

	initialize();
	setupColumnsDialog();
    }




    //  A mechanism for controlling the TableColumns associated with each column.
    //  We cannot use the standard JTable.getColumn() because it is associated with
    //  the VISIBLE columns, not the columns in the tableModel.
    private HashMap columns = new HashMap();
    private void setTableColumn( String name, TableColumn col){
	if (col==null){
	    log.println("Attempt to set up null TableColumn for column: " + name );
	} else {
	    columns.put( name, col);
	}
    }
    private void removeTableColumn( String name){
	columns.remove( name);
    }
    private TableColumn getTableColumn( String name){
	return (TableColumn)columns.get( name);
    }
    private void clearTableColumns(){
	columns.clear();
    }



    //  A mechanism for controlling the visibility associated with each column.
    private HashMap columnVis = new HashMap();
    private void setColumnVisibility( String name, boolean b){
	columnVis.put( name, (b==true ? Boolean.TRUE : Boolean.FALSE));
    }
    private boolean getColumnVisibility( String name){
	return ((Boolean)columnVis.get( name)).booleanValue();
    }
    private void removeColumnVisibility( String name){
	columnVis.remove( name);
    }
    private void clearColumnVisibility(){
	columnVis.clear();
    }



    //  A mechanism for controlling the showability associated with each column.
    private HashMap columnShow = new HashMap();
    private void setColumnShowability( String name, boolean b){
	columnShow.put( name, (b==true ? Boolean.TRUE : Boolean.FALSE));
    }
    private boolean getColumnShowability( String name){
	return ((Boolean)columnShow.get( name)).booleanValue();
    }
    private void removeColumnShowability( String name){
	columnShow.remove( name);
    }
    private void clearColumnShowability(){
	columnShow.clear();
    }



    // The following objects and methods deal with a graphical (iconic) display of the direction of
    // a sort (either increasing or decreasing).  Note that there are different icons for a primary
    // sort and a secondary sort.
    private JLabel firstIncreaseIcon  = new JLabel( new ImageIcon(Main.getResource("resources/FirstIncrease.png")));
    private JLabel firstDecreaseIcon  = new JLabel( new ImageIcon(Main.getResource("resources/FirstDecrease.png")));
    private JLabel secondIncreaseIcon = new JLabel( new ImageIcon(Main.getResource("resources/SecondIncrease.png")));
    private JLabel secondDecreaseIcon = new JLabel( new ImageIcon(Main.getResource("resources/SecondDecrease.png")));

    private class FirstIncreaseDecorator implements TableCellRenderer {
	public Component getTableCellRendererComponent (
							JTable table, Object value, 
							boolean isSelected, boolean hasFocus,
							int row, int col) {
			
	    Component c = defaultHeaderRenderer.getTableCellRendererComponent(
				      table, value, isSelected, hasFocus, row, col);

	    return embellishComponent( firstIncreaseIcon, c);
	}
    }

    private class FirstDecreaseDecorator implements TableCellRenderer {
	public Component getTableCellRendererComponent (
							JTable table, Object value, 
							boolean isSelected, boolean hasFocus,
							int row, int col) {
	    Component c = defaultHeaderRenderer.getTableCellRendererComponent(
				      table, value, isSelected, hasFocus, row, col);
	    return embellishComponent( firstDecreaseIcon, c);
	}
    }

    private class SecondIncreaseDecorator implements TableCellRenderer {
	public Component getTableCellRendererComponent (
							JTable table, Object value, 
							boolean isSelected, boolean hasFocus,
							int row, int col) {
	    Component c = defaultHeaderRenderer.getTableCellRendererComponent( 
				      table, value, isSelected, hasFocus, row, col);
	    return embellishComponent( secondIncreaseIcon, c);
	}
    }

    private class SecondDecreaseDecorator implements TableCellRenderer {
	public Component getTableCellRendererComponent (
							JTable table, Object value, 
							boolean isSelected, boolean hasFocus,
							int row, int col) {
	    Component c = defaultHeaderRenderer.getTableCellRendererComponent( 
				      table, value, isSelected, hasFocus, row, col);
	    return embellishComponent( secondDecreaseIcon, c);
	}
    }



    // builds the header with an embedded sorting indicator.
    private Component embellishComponent( JLabel iconLabel, Component c){
	JPanel panel = new JPanel();
	panel.setLayout( new BorderLayout());
	panel.add( c, BorderLayout.CENTER);
	panel.add( iconLabel, BorderLayout.EAST);
	return panel;
    }


	

    // Columns that can be displayed or hidden in the table.
    private MenuCheckBox []  menuCheck;
    private class MenuCheckBox extends JCheckBoxMenuItem {
	final String   label;
	ActionListener action;
	boolean        selected;
	int            index;
		
	public MenuCheckBox( String l, boolean s, int i){
	    super(l);
	    label = l;
	    selected = s;
	    setSelected( s);
	    index    = i;

	    addActionListener( new AbstractAction() {
		    public void actionPerformed ( ActionEvent e) {
			selected = !selected;
			setSelected( selected );
			setColumnVisible( tableModel.getColumnName(index), selected);
		    }
		});
	}
    }





    // Sets up the dialog that allows selecting which columns are displayed in the table.
    private void setupColumnsDialog()
    {
	columnDialog = new JDialog( (JFrame)null, "Columns", false);
	Container dialogPane = columnDialog.getContentPane();
	dialogPane.setLayout( new BorderLayout());

	// Set up list of columns in the middle of the dialog
	JPanel colPanel = new JPanel(); {
	    colPanel.setLayout( new BoxLayout( colPanel, BoxLayout.Y_AXIS));

	    // get an array of menuCheckBox objects, one for each showable column.
	    int showableColumns = 0;
	    for (int i=0; i< tableModel.getColumnCount(); i++){
		if (getColumnShowability( tableModel.getColumnName(i))==true){
		    showableColumns++;
		}
	    }
	    menuCheck = new MenuCheckBox[ showableColumns];

	    for (int i=0, j=0; i< tableModel.getColumnCount(); i++){
		String name = tableModel.getColumnName(i);
		if ( getColumnShowability( name)==true){
		    menuCheck[j] = new MenuCheckBox( name, getColumnVisibility( name), i);
		    colPanel.add( menuCheck[j]);
		    j++;
		}
	    }
	}
	dialogPane.add( new JScrollPane( colPanel), BorderLayout.CENTER);


	// Set up the button panel at the bottom of the panel.
	JPanel buttonPanel = new JPanel(); {
	    JButton  allButton = new JButton( new AbstractAction("Show All") {
		    public void actionPerformed(ActionEvent e) {
			for (int j=0;j< menuCheck.length; j++){
			    String name     = tableModel.getColumnName( menuCheck[j].index);
			    TableColumn col = getTableColumn( name);
			    if (col==null){
				log.println("cannot set up show all button for column: " + name);
			    } else if (menuCheck[j].selected==false) {
				menuCheck[j].setSelected( true);	
				menuCheck[j].selected = true;
				setColumnVisible( name, true);
			    }
			}
		    }
		});
	    JButton nothingButton = new JButton( new AbstractAction("Hide All") {
		    public void actionPerformed(ActionEvent e) {
			for (int j=0;j< menuCheck.length; j++){
			    String name     = tableModel.getColumnName( menuCheck[j].index);
			    TableColumn col = getTableColumn( name);
			    if (col==null){
				log.println("cannot set up hide all button for column: " + name);
			    } else if (menuCheck[j].selected==true) {
				menuCheck[j].setSelected( false);	
				menuCheck[j].selected = false;
				setColumnVisible( name, false);
			    }
			}
		    }
		});
	    JButton okButton = new JButton( new AbstractAction("OK") {
		    public void actionPerformed(ActionEvent e) {
			columnDialog.setVisible(false);
		    }
		});

	    allButton.setFocusPainted(false);
	    nothingButton.setFocusPainted(false);
	    okButton.setFocusPainted(false);
			
	    buttonPanel.setLayout( new GridBagLayout());
	    GridBagConstraints gbc = new GridBagConstraints();
	    gbc.insets = new Insets(5,5,5,5);
	    gbc.gridx = 0;
	    buttonPanel.add( allButton, gbc);
	    gbc.gridx = 1;
	    buttonPanel.add( nothingButton, gbc);
	    gbc.gridy = 1;
	    gbc.gridx = 0;
	    gbc.gridwidth = 2;
	    gbc.insets.top = 0;
	    buttonPanel.add( okButton, gbc);
	}
	dialogPane.add( buttonPanel, BorderLayout.SOUTH);
			
	// Pack it all in, Lads, good and tight.
	columnDialog.pack();


	// Display this dialog in the middle of the screen.
	Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
	Dimension d      = columnDialog.getSize();
	d.width  = Math.min( d.width, (screen.width/2) );
	d.height = Math.min( d.height, (screen.height/2) );
	columnDialog.setSize( d);
	int x            = (screen.width - d.width) / 2;
	int y            = (screen.height - d.height) / 2;
	columnDialog.setLocation(x, y);
    } 


	
} // end: class SortingTable




