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
 **  Right-clicking anywhere on the header bar brings up a dialog with a checkbox for 
 **  each of the columns that may be displayed in the table. All currently displayed columns are 
 **  already checked.  Columns are displayed and hidden by checking and unchecking the 
 **  corresponding checkboxes. 
 *
 *  NOTE: SortingTableModel.forward is used to relay table model events to STable, however if it
 * receives 'unknown' table model events, it will generate a 'update everything' event instead.
 * This is due to various parts of the application sending incorrect events or event ranges. 
 * A side effect of the 'update everything' event is that user selections are discarded.  If you
 * are using STable and finding that your selections disappear when you don't think they should,
 * you could be sending incorrect (or at least unrecognized) table model events.
 *
 **/

package edu.asu.jmars.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.plaf.basic.BasicTableUI;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.Util;
import edu.asu.jmars.util.stable.BooleanCellEditor;
import edu.asu.jmars.util.stable.BooleanCellRenderer;
import edu.asu.jmars.util.stable.DoubleCellEditor;
import edu.asu.jmars.util.stable.FilteringColumnModel;
import edu.asu.jmars.util.stable.IntegerCellEditor;
import edu.asu.jmars.util.stable.NumberCellRenderer;
import edu.asu.jmars.util.stable.Sorter;
import edu.asu.jmars.util.stable.SortingTableModel;
import edu.asu.jmars.util.stable.TextCellEditor;
import edu.asu.jmars.util.stable.TextCellRenderer;

/** 
 * A JTable that provides column filtering and sorting. There are several
 * differences from a normal JTable:
 * <ul>
 * <li>The TableColumnModel is initialized to FilteringColumnModel, so the user
 * can choose which columns they want to see.
 * <li>Client code should not call setModel(). Instead it should call
 * setUnsortedTableModel().
 * <li>ColumnClickListener is attached to the JTableHeader to display the
 * column dialog on a right click (only if the column model is an instance of
 * FilteringColumnModel), and change the sort on a left click.
 * <li>Default editors and renderers are set for Boolean, Integer, Double, and
 * String. Other types exist in the edu.asu.jmars.util.stable package, but they
 * are not general and must be added by the clients.
 * <li>The header renderers will always be used to reflect the current column
 * sorts, even on a custom JTableHeader.
 * </ul>
 */
public class STable extends JTable implements HierarchyListener {
	private TableModel unsortedTableModel;
	private SortingTableModel sortedTableModel;
	private Sorter sorter;
	private ColumnClickListener clickListener = new ColumnClickListener();
	private NoColumnDialogListener noColumnClickHandler = new NoColumnDialogListener();
	private SortChangeHandler sortChangeHandler = new SortChangeHandler();

	private TableCellRenderer defaultHeaderRenderer;
	private TableCellRenderer[] headerAsc = new TableCellRenderer[] {
		getHeaderRenderer("resources/FirstIncrease.png"),
		getHeaderRenderer("resources/SecondIncrease.png"),
	};
	private TableCellRenderer[] headerDesc = new TableCellRenderer[] {
		getHeaderRenderer("resources/FirstDecrease.png"),
		getHeaderRenderer("resources/SecondDecrease.png")
	};

	public STable() {
		super ();

		setUI(new FixedUI());

		// Create the sorter
		sorter = new Sorter ();

		// Use FilteringColumnModel instead of the JTable default
		setColumnModel(new FilteringColumnModel());

		// Setting the table header attaches the click listener
		setTableHeader(getTableHeader());

		// Keeps a no-columns-shown mouse listener on the STable's parent
		this.addHierarchyListener(this);

		// Set up default support for displaying and editing
		setTypeSupport(Boolean.class, new BooleanCellRenderer(), new BooleanCellEditor());
		setTypeSupport(Integer.class, new NumberCellRenderer(), new IntegerCellEditor());
		setTypeSupport(Double.class, new NumberCellRenderer(), new DoubleCellEditor());
		setTypeSupport(String.class, new TextCellRenderer(), new TextCellEditor());
	}

	public void hierarchyChanged(HierarchyEvent e) {
		noColumnClickHandler.setParent (getParent());
	}

	/**
	 * Sets the table header as done by JTable, and also connects a
	 * MouseListener to show the column dialog on a right click, or change the
	 * sort on a left click.
	 * Don't pass null as the new header value.
	 */
	public void setTableHeader (JTableHeader header) {
		// move the listener from the old table header to the new one
		if (clickListener != null) {
			JTableHeader oldHeader = getTableHeader();
			if (oldHeader != null)
				oldHeader.removeMouseListener (clickListener);
			header.addMouseListener(clickListener);
		}

		// set the new header and get the new 'unsorted' header renderer
		super.setTableHeader (header);
		defaultHeaderRenderer = header.getDefaultRenderer();
	}

	/**
	 * Clients should not set the TableModel directly. Instead, call
	 * setUnsortedTableModel().
	 */
	public void setModel (TableModel model) {
		super.setModel (model);
	}

	/**
	 * When columns are added, the JTableHeader renderers are updated so the
	 * ascending/descending icons are correct (since a hidden sorted column
	 * needs the renderer reset.)
	 */
	public void columnAdded (TableColumnModelEvent e) {
		super.columnAdded(e);
		updateHeaders ();
	}

	/**
	 * When columns are removed, the sorted columns are intersected with the
	 * model columns, and any sorted columns lost there are removed from the
	 * sorter.
	 */
	public void columnRemoved (TableColumnModelEvent e) {
		super.columnRemoved(e);
		List modelColumns = getAllColumns ();
		List sortColumns = new LinkedList(sorter.getSorts());
		sortColumns.removeAll (modelColumns);
		if (sortColumns.size() > 0)
			sorter.removeSorts(sortColumns);
	}

	/**
     * Override of JTable's createDefaultColumnsFromModel to handle the case when
     * the columns in the FilteringColumnModel change.
     */
    public void createDefaultColumnsFromModel() {
        TableModel m = getModel();
        if (m != null) {
            // Remove any current columns
        	TableColumnModel tcm = getColumnModel();
        	
        	if (tcm instanceof FilteringColumnModel) {
	            FilteringColumnModel fcm = (FilteringColumnModel) tcm;
	            fcm.removeAllColumns(); 
        	}
            // Create new columns from the data model info
            for (int i = 0; i < m.getColumnCount(); i++) {
                TableColumn newColumn = new TableColumn(i);
                addColumn(newColumn);
            }
        }
    }
	
	/**
	 * * returns the current table model.
	 */
	public TableModel getUnsortedTableModel() {
		return unsortedTableModel;
	}

	/**
	 * Sets the unsorted TableModel. A SortingTableModel is created around it,
	 * and set on the JTable. The unsorted model is then shared with the Sorter.
	 * Finally, the SortedTableModel becomes a listener on sort changes.
	 */
	public void setUnsortedTableModel(TableModel tm) {
		// Remove prior sort change listeners
		if (sortedTableModel != null)
			sorter.removeListener(sortedTableModel);
		sorter.removeListener(sortChangeHandler);

		// Wrap a sorting model around the unsorted model
		unsortedTableModel = tm;
		sortedTableModel = new SortingTableModel (sorter);
		sorter.setModel(tm);
		sortedTableModel.setModel(tm);

		// Sort changes must be processed by STable last to retain selections
		// on a sort change
		sorter.addListener(sortedTableModel);
		sorter.addListener (sortChangeHandler);

		// Finally, tell the JTable about its new TableModel
		super.setModel(sortedTableModel);
	}

	/**
	 * Returns the Sorter used by this STable.
	 */
	public Sorter getSorter () {
		return sorter;
	}

	/**
	 * Convenience method for setting the renderer and editor for the given type.
	 * @param type Class of cell that will be affected.
	 * @param renderer Renderer to draw cells of the given type.
	 * @param editor Editor to edit cells of the given type.
	 */
	public void setTypeSupport (Class type, TableCellRenderer renderer, TableCellEditor editor) {
		setDefaultRenderer(type, renderer);
		setDefaultEditor(type, editor);
	}

	/**
	 * Returns an unmodifiable view of the Class -> TableCellEditor map.
	 */
	public Map getDefaultEditorsByColumnClass () {
		return Collections.unmodifiableMap (this.defaultEditorsByColumnClass);
	}

	/**
	 * Returns an unmodifiable view of the Class -> TableCellRenderer map.
	 */
	public Map getDefaultRenderersByColumnClass () {
		return Collections.unmodifiableMap (this.defaultRenderersByColumnClass);
	}

	/**
	 * Get a List view of all columns. If the TableColumnModel is an instance of
	 * FilteringColumnModel, that class' getAllColumns() method is used.
	 */
	private List getAllColumns () {
		if (columnModel instanceof FilteringColumnModel) {
			return ((FilteringColumnModel)columnModel).getAllColumns();
		} else {
			List ret = new LinkedList ();
			for (Enumeration en = getColumnModel().getColumns(); en.hasMoreElements(); )
				ret.add(en.nextElement());
			return ret;
		}
	}

	/**
	 * Updates the header renderers to indicate the current sorts applied.
	 * 
	 * Should be called when the column model shows previously hidden columns
	 * since they could be sorted, and when a header is clicked to change the
	 * sort.
	 */
	private void updateHeaders () {
		for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
			TableColumn column = getColumnModel().getColumn(i);
			int pos = sorter.getSorts().indexOf(column);
			switch (sorter.getDirection(column)) {
			case -1:
				column.setHeaderRenderer(headerDesc[pos]);
				break;
			case 0:
				column.setHeaderRenderer(defaultHeaderRenderer);
				break;
			case 1:
				column.setHeaderRenderer(headerAsc[pos]);
				break;
			}
		}
	}

	private TableCellRenderer getHeaderRenderer(final String resource) {
		return new TableCellRenderer() {
			public Component getTableCellRendererComponent(JTable table,
					Object value, boolean isSelected, boolean hasFocus,
					int row, int col) {
				Component c = defaultHeaderRenderer.getTableCellRendererComponent(
						table, value, isSelected, hasFocus, row, col);
				JPanel panel = new JPanel();
				panel.setLayout(new BorderLayout());
				panel.add(c, BorderLayout.CENTER);
				JLabel label = new JLabel(new ImageIcon(Main.getResource(resource)));
				panel.add(label, BorderLayout.EAST);
				return panel;
			}
		};
	}
	
	/**
	 * Returns view settings of visible columns, sorts and
	 * column order in a serializable map. Columns are identified
	 * by their model indices. See {@link #setViewSettings(Map)}
	 * for how these settings are restored.
	 * @return A non-null map of view settings.
	 */
	public Map getViewSettings(){
		Map m = new HashMap();
		
		if (getColumnModel() instanceof FilteringColumnModel){
			FilteringColumnModel fcm = (FilteringColumnModel)getColumnModel();
			int cc = fcm.getColumnCount();
			Set vis = new LinkedHashSet(cc);
			for(int i=0; i<cc; i++){
				Integer id = new Integer(fcm.getColumn(i).getModelIndex());
				vis.add(id);
			}
			m.put("visibleColumns", vis);
			
			Sorter s = getSorter();
			List sortedColumns = s.getSorts();
			Map sorts = new LinkedHashMap(sortedColumns.size());
			for(Iterator si=sortedColumns.iterator(); si.hasNext(); ){
				TableColumn tc = (TableColumn)si.next();
				Integer id = new Integer(tc.getModelIndex());
				sorts.put(id, new Integer(s.getDirection(tc)));
			}
			m.put("sortOrder", sorts);
		}
		
		TableColumnModel tcm = getColumnModel();
		int cc = tcm.getColumnCount();
		
		Map columnOrder = new LinkedHashMap(cc);
		for(int i=0; i<cc; i++){
			TableColumn tc = tcm.getColumn(i);
			Integer id = new Integer(tc.getModelIndex());
			columnOrder.put(id, new Integer(i));
		}
		m.put("columnOrder", columnOrder);
		
		Map widths = new HashMap(cc);
		for(int i=0; i<cc; i++){
			TableColumn tc = tcm.getColumn(i);
			Integer id = new Integer(tc.getModelIndex());
			widths.put(id, new Integer(tc.getWidth()));
		}
		m.put("columnWidths", widths);
		
		return m;
	}
	
	/**
	 * Apply settings of visible columns, sort order
	 * and column order to this table. Such settings would have been
	 * saved during a previous call to {@link #getViewSettings()}.
	 * @param m Non-null map of settings.
	 */
	public void setViewSettings(Map m){
		TableColumnModel tcm = getColumnModel();
		
		// Create a reverse mapping from column model index to table column
		Map modelIdxToCol = new HashMap();
		if (tcm instanceof FilteringColumnModel){
			for(Iterator it=((FilteringColumnModel)tcm).getAllColumns().iterator(); it.hasNext(); ){
				TableColumn tc = (TableColumn)it.next();
				modelIdxToCol.put(new Integer(tc.getModelIndex()), tc);
			}
		}
		else {
			Enumeration e = tcm.getColumns();
			while(e.hasMoreElements()){
				TableColumn tc = (TableColumn)e.nextElement();
				modelIdxToCol.put(new Integer(tc.getModelIndex()), tc);
			}
		}

		Object o;
		if (tcm instanceof FilteringColumnModel){
			FilteringColumnModel fcm = (FilteringColumnModel)tcm;
			
			o = m.get("visibleColumns");
			if (o instanceof Set){
				Set vis = (Set)o;
				for(Iterator ci=fcm.getAllColumns().iterator(); ci.hasNext(); ){
					TableColumn tc = (TableColumn)ci.next();
					Integer id = new Integer(tc.getModelIndex());
					fcm.setVisible(tc, vis.contains(id));
				}
			}
			else {
				System.err.println("Saved settings for visible columns not of expected type. Ignored!");
			}
			
			o = m.get("sortOrder");
			if (o instanceof Map){
				Sorter s = getSorter();
				s.clearSorts();

				Map sorts = (Map)o;
				for(Iterator si=sorts.keySet().iterator(); si.hasNext(); ){
					Object id = si.next();
					TableColumn tc = (TableColumn)modelIdxToCol.get(id);
					if (tc != null && sorts.get(id) instanceof Integer)
						s.setDirection(tc, ((Integer)sorts.get(id)).intValue());
					else
						System.err.println(tc == null? "Unknown model column "+id: "Non-integer sort direction for model column "+id);
				}
			}
			else {
				System.err.println("Saved settings for sort order not of expected type. Ignored!");
			}
		}
		
		o = m.get("columnOrder");
		if (o instanceof Map){
			Map columnOrder = (Map)o;
			
			for(Iterator oi=columnOrder.keySet().iterator(); oi.hasNext(); ){
				Object id = oi.next();
				TableColumn tc = (TableColumn)modelIdxToCol.get(id);
				if (tc != null && columnOrder.get(id) instanceof Integer)
					tcm.moveColumn(tcm.getColumnIndex(tc.getIdentifier()),
							((Integer)columnOrder.get(id)).intValue());
				else
					System.err.println(tc == null? "Unknown model column "+id: "Non-integer column order for model column "+id);
			}
		}
		else {
			System.err.println("Saved settings for column order not of expected type. Ignored!");
		}
		
		o = m.get("columnWidths");
		if (o instanceof Map){
			Map widths = (Map)o;
			
			for(Iterator wi=widths.keySet().iterator(); wi.hasNext(); ){
				Object id = wi.next();
				TableColumn tc = (TableColumn)modelIdxToCol.get(id);
				if (tc != null && widths.get(id) instanceof Integer)
					tc.setPreferredWidth(((Integer)widths.get(id)).intValue());
				else 
					System.err.println(tc == null? "Unknown model column "+id: "Non-integer column width for model column "+id);
			}
		}
		else {
			System.err.println("Saved settings for column widths not of expected type. Ignored!");
		}
	}
	

	/**
	 * Since ListSelectionModel is so complex, we implement a sort change
	 * listener instead that converts the sorted selection indices into unsorted
	 * indices, allows the sort to happen, and then converts them back into
	 * sorted indices and replaces the model contents.
	 * 
	 * The sortChanged() method also updates the table header renderers.
	 */
	class SortChangeHandler implements Sorter.Listener {
		int[] unsortSelectedRows;
		int size;

		/**
		 * Saves the unsorted selections for later
		 */
		public void sortChangePre () {
			// the sorter size at this point reflects the old row count
			size = sorter.getSize();

			// Get the unsorted selected row indices
			int[] selectedRows = getSelectedRows();
			unsortSelectedRows = new int[selectedRows.length];
			for (int i = 0; i < unsortSelectedRows.length; i++) {
				unsortSelectedRows[i] = sorter.unsortRow(selectedRows[i]);
			}
		}

		/**
		 * Converts the unsorted selections into the selections on the new
		 * sorts, replaces the selections on the model, and updates the table
		 * header.
		 */
		public void sortChanged () {
			// Only update the selections if the row count is the same
			// .. if it changed, deletes are handled and adds don't matter
			// here.
			if (size != sorter.getSize())
				return;

			// get the new sorted indices
			int[] selectedRows = new int[unsortSelectedRows.length];
			for (int i = 0; i < selectedRows.length; i++) {
				selectedRows[i] = sorter.sortRow(unsortSelectedRows[i]);
			}

			int[][] ranges = Util.binRanges(selectedRows);
			if (ranges.length > 0) {
				ListSelectionModel selModel = getSelectionModel();
				// set the selections
				selModel.setValueIsAdjusting(true);
				selModel.clearSelection();
				for (int i = 0; i < ranges.length; i++)
					selModel.addSelectionInterval(ranges[i][0], ranges[i][1]);
				selModel.setValueIsAdjusting(false);

				// ensure last selection is visible
				int row = ranges[ranges.length-1][1];
				Rectangle rect = getCellRect(row, 0, true);
				// and ensure that the column positioning within the viewport does not get reset
				rect = rect.union(getCellRect(row, getColumnCount()-1, true));
				scrollRectToVisible(rect);
			}

			// update the headers
			updateHeaders();
		}
	}

	/**
	 * Right clicks when no columns are shown will reveal the column dialog, if
	 * filtering.
	 */
	class NoColumnDialogListener extends MouseAdapter {
		private Container parent;
		public void setParent (Container p) {
			if (parent == p)
				return;
			if (parent != null)
				parent.removeMouseListener(this);
			parent = p;
			if (parent != null)
				parent.addMouseListener(this);
		}
		public void mouseClicked(MouseEvent e) {
			TableColumnModel m = getColumnModel();
			if (SwingUtilities.isRightMouseButton(e)
			&&  m instanceof FilteringColumnModel
			&&  m.getColumnCount() == 0)
				((FilteringColumnModel)m).getColumnDialog().setVisible(true);
		}
	}

	/**
	 * Mouse header right clicks show the column dialog, if the column model
	 * filters. Left clicks adjust the sort.
	 */
	class ColumnClickListener extends MouseAdapter {
		public void mouseClicked(MouseEvent e) {
			TableColumnModel m = getColumnModel();
			if (SwingUtilities.isRightMouseButton(e)) {
				if (m instanceof FilteringColumnModel) {
					FilteringColumnModel fcm = (FilteringColumnModel)m;
					
					fcm.getColumnDialog().setLocationRelativeTo(e.getComponent());									
					fcm.getColumnDialog().setVisible(true);
				}
			} else if (sorter != null) {
				boolean shiftPressed = (0 != (e.getModifiers() & InputEvent.SHIFT_MASK));
				int columnIndex = m.getColumnIndexAtX(e.getX());
				if (columnIndex < 0) // not a valid column location
					return;
				TableColumn column = m.getColumn(columnIndex);
				if (shiftPressed)
					sorter.addSort(column);
				else
					sorter.setSort(column);
			}
		}
	}

	/**
	 * The JTable appears to set the viewport outside the valid row range when
	 * processing a delete from the TableModel (or, equivalently, it doesn't
	 * always change the viewport prior to the first paint().)
	 * 
	 * BasicTableUI as of JRE 1.4.2_09 is bugged. There is a degenerate case in
	 * paint() where it will render every cell if the first row in the view and
	 * the last row in the view are outside the legal tableModel range. When
	 * this happens because of a delete, we repaint all the remaining rows,
	 * which can take a LONG time.
	 * 
	 * When the viewport is totally disjoint with the table rows, nothing should
	 * be drawn, not everything. So the fixed BasicTableUI's paint() method
	 * returns early if it detects this degenerate case.
	 */
	class FixedUI extends BasicTableUI {
		public void paint(Graphics g, JComponent c) {
			if (table.getRowCount() <= 0 || table.getColumnCount() <= 0)
				return;
			Rectangle clip = g.getClipBounds();
			Point upperLeft = clip.getLocation();
			Point lowerRight = new Point(clip.x + clip.width - 1, clip.y
					+ clip.height - 1);
			int rMin = table.rowAtPoint(upperLeft);
			int rMax = table.rowAtPoint(lowerRight);
			if (rMin != -1 || rMax != -1)
				super.paint(g, c);
		}
	}
} // end: class STable
