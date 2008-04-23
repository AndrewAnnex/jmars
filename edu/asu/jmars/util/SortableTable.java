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
 **  An extension to a JTable that allows the sorting of rows by multiple columns and the 
 **  selection of columns to display.
 **
 **  Sorting is accomplished by left-clicking on a column.  The first time a column is clicked, the
 **  rows of the table are sorted in ascending order by the values of that column. The second time the 
 **  column is clicked, the rows are sorted in descending order.  The third left-click on the column 
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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import edu.asu.jmars.Main;
import edu.asu.jmars.swing.AncestorAdapter;


public class SortableTable extends JTable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static DebugLog log = DebugLog.instance();
	
	private JScrollPane         scrollPane;
	private AbstractTableModel  tableModel = null;
	private JDialog             columnDialog;
	/**
	 * Note on accessing the sorter: any method of this class which operates on
	 * the sorter needs to be a synchronous method to avoid some other thread 
	 * invoking a sort operation midway through.  Note that callers of this class
	 * get rows coordinates in unsorted order, so are not be affected by sorter 
	 * operations.
	 */
	private SortableTableModel  sorter;
	private TableCellRenderer   defaultHeaderRenderer;
	
	/**
	 * used by the no-forwarding selection model to avoid forwarding programmatic
	 * selection events to other classes.
	 */
	private boolean selectionNotifcationDisabled = false;
	
	public SortableTable(AbstractTableModel model) 
	{
		super();
		setTableModel( model);
		initTable();
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
	 ** sets the cell renderer for the specified column.  This differs from the
	 ** standard setCellRenderer in that it will set the renderer of a column which
	 ** is currently removed from the table.  Later, if the column should be added 
	 ** back to the table, it will be rendered with the renderer set here.
	 **/
	public void setCellRenderer( String name, TableCellRenderer dtcr){
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
	 **/
	public void setCellEditor( String name, TableCellEditor dtcr){
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
	public void setColumnShowable( String name, boolean showable){
		setColumnShowability( name, showable);
		setupColumnsDialog();
	}
	
	/**
	 *  sets the statuses of the columns and hides them from the table if
	 *  the status is false.
	 */
	public void setColumnStatus( boolean [] s){
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
	public int getColumnIndex( String name)
	{
		for (int i=0; i< tableModel.getColumnCount(); i++){
			if (tableModel.getColumnName(i).equalsIgnoreCase(name)){
				return i;
			}
		}
		return -1;
	}

	
	/**
	 ** Gets the index of the first (and possibly the only) row that was selected.  
	 ** note that if the table is sorted at all, the index of the sorted row 
	 ** rather than the unsorted row is returned.
	 **/
	public synchronized int getSelectedRow() {
		int row = super.getSelectedRow();
		if (row < 0 ) {
			return -1;
		}
		return sorter.getUnsortedIndex( row );
	}
	
	
	
	/**
	 ** Gets the indexes of all the selected rows.
	 ** note that if the table is sorted at all, the indexes of the sorted rows 
	 ** rather than the unsorted rows are returned.
	 ** If no rows are selected, an singleton array is returned with a value of -1.
	 **/
	public synchronized int [] getSelectedRows(){
		int [] rows = super.getSelectedRows();
		if (rows==null || rows.length==0){
			return new int[0];
		}
		for (int i=0; i< rows.length; i++){
			rows[i] = sorter.getSortedIndex( rows[i]);
		}
		return rows;
	}
	
	
	
	/**
	 * sets the selected rows of the table to those specified in the parameter.  
	 * This is the inverse of the getSelectedRows() method.
	 */
	public synchronized void setSelectedRows( int [] rows){
		getSelectionModel().setValueIsAdjusting(true);
		clearSelection();
		if (rows!=null && rows.length>0){
			for (int i=0; i< rows.length; i++){
				addRowSelectionInterval( rows[i], rows[i]);
			}
		}
		getSelectionModel().setValueIsAdjusting(false);
	}

	public synchronized void addRowSelectionInterval( int rowStart, int rowEnd ) {
		for(int i = rowStart; i<=rowEnd; ++i) {
			super.addRowSelectionInterval( sorter.getUnsortedIndex(i), sorter.getUnsortedIndex(i));
			log.println("Add selection: mapped unsorted row " + i + " to table position " + sorter.getUnsortedIndex(i));
		}
	}

	public synchronized void removeRowSelectionInterval( int rowStart, int rowEnd ) {
		for(int i = rowStart; i<=rowEnd; ++i) {
			super.removeRowSelectionInterval( sorter.getUnsortedIndex(i), sorter.getUnsortedIndex(i));
			log.println("Remove selection: mapped unsorted row " + i + " to table position " + sorter.getUnsortedIndex(i));
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
	
	
	public void resortRows(){
		sortRows( false);
	}
	
	
	/**
	 * Sorts the rows of the table.  If newSort is true, this is considered a new sort
	 * in which case the sort state counters are updated.  If newSort is false, a 
	 * previous sort is simply recreated and no counters are updated.
	 */
	private synchronized void sortRows( boolean newSort){
		if (sortColumn == -1){
			return;
		}
		
		// Get the selected rows.  The sorting process leaves the selections
		// in the wrong places, so we get rid of the selection and then restore
		// it.
		
		int [] selectedRows = getSelectedRows();
		
		disableSelectionNotification();

		// optimization: don't bother re-painting no-selection, it'll be wiped
		// out in the set.
		
		// clearSelection();
		
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
				sorter.unsort();
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
				sorter.unsort();
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
		
		// look above for how selectedRows are already correctly mapped
		setSelectedRows( selectedRows );
		enableSelectionNotification();
	}

	/**
	 * Depending on whether we're adjusting a selection following a table
	 * re-sort, don't forward selection events to other objects.   They get
	 * the selection in unsorted coordinates, so at best it's an inefficient
	 * no-op, at worst a race condition.
	 * 
	 * @author lplotkin
	 *
	 */

	private final class NoForwardSelectionModel extends DefaultListSelectionModel {

	    /**
		 * 
		 */
		private static final long serialVersionUID = 2120126693674075888L;

		/**
	     * Based on an instance variable in SortableTable, choose to disregard the firinig
	     * of a selection event.   This is useful to disable selection event
	     * propagation in cases of programmatic selection.
	     * 
	     * Assumption: listeners to the JTable's selection model will have
	     * settled down by the time we're done sorting.
	     */
	    protected void fireValueChanged(int firstIndex, int lastIndex, boolean isAdjusting)
	    {
	    	if(selectionNotifcationDisabled) {
	    		return;
	    	} else {
	    		super.fireValueChanged( firstIndex, lastIndex, isAdjusting );
	    	}
	    }
	}
	
	//----------------------------------------//
	//      The SortingTable's PRIVATES.      //
	//----------------------------------------//
	
	
	
	// sets the TableModel of the table and a bunch of other stuff relating 
	// to setting up a SortingTable.
	private void setTableModel( AbstractTableModel model){
		tableModel = model;
		sorter = new SortableTableModel(tableModel);
		super.setModel( sorter );
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
		
		setupColumnsDialog();
		
		// Make sure that nothing is editable unless an editor has specifically been set up 
		// for a column.
		setDefaultEditor( java.lang.Object.class, null);
		setDefaultEditor( java.lang.Number.class, null);
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
		
		// a fancy way to avoid forwarding bogus selection events during sort
		this.setSelectionModel( new NoForwardSelectionModel() );
		
		// the following two lines avoid drawing artifacts during a cell
		// click row selection.
		setFocusable(false);
		setCellSelectionEnabled(true);
	}	
	
	
	// Defines behavior for clicking in the header of the table.
	private class HeaderMouseListener extends MouseAdapter {
		public void mouseClicked(MouseEvent e){
			if(SwingUtilities.isRightMouseButton(e)){
				setupColumnsDialog();
				columnDialog.setVisible(true);
			} else {
				int shiftPressed = e.getModifiers()&InputEvent.SHIFT_MASK;
				primarySort      = (shiftPressed == 0);
				sortColumn       = getColumnModel().getColumnIndexAtX( e.getX());
				sortRows( true);
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
			
			// get an array of menuCheckBox objects, one for each displayable column.
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

	/**
	 * See if the sorter wants to translate the event.  In the case of an update
	 * to a row while sorting, this may mean 'redraw' the world.  Not a very
	 * efficient thing to do when changing a multiple selection in real time,
	 * since that'll cause N redraws.
	 */
	public void tableChanged(TableModelEvent e) {
		/**
		 * If the sorter isn't interested, delegate to the parent.
		 */
		if(sorter != null) {
			e = sorter.checkForInterest(e);
			if(e == null) {
				return;
			}
		}
		super.tableChanged(e);
	}

	/**
	 * Given an unsorted row, turn it into the current table model's sorted row
	 * before passing to the getCellRect function.  Note that simply overriding
	 * getCellRect is not an option -- it's being used internaly by JTable, which will
	 * do all sorts of horrible stuff since it has no idea re: sorted/unsorted coordinate
	 * spaces.
	 */
	public void showRow(int row) {
		super.scrollRectToVisible(super.getCellRect(sorter.getUnsortedIndex(row), 0, true));
	}

	/**
	 * After calling this method, programmatic (or user) selections will NOT fire events to 
	 * listeners.  For best effect call this out of the AWT thread.  Otherwise you risk
	 * losing user selection events.
	 *
	 */
	public void disableSelectionNotification() {
		this.selectionNotifcationDisabled = true;
	} 
	/**
	 * After calling this method, programmatic (or user) selections will once again fire events to 
	 * listeners.  Expected to be called after a call to disableSelectionNotification(), presumably
	 * out of the AWT thread.
	 */
	public void enableSelectionNotification() {
		this.selectionNotifcationDisabled = false;
	}
	
	
	
} // end: class SortingTable




