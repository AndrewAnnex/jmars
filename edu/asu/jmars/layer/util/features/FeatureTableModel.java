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


package edu.asu.jmars.layer.util.features;

import java.util.*;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import edu.asu.jmars.util.stable.ComparableTableColumn;
import edu.asu.jmars.util.stable.FilteringColumnModel;

/**
 * Presents a TableModel view of a FeatureCollection for a JTable's use.
 * All rows are shown to the JTable, but not all columns because some
 * Field objects contain data that cannot be meaningfully displayed in
 * a JTable (see 'hiddenFields' for the list that is suppressed.)
 * 
 * The TableColumnModel is modified directly instead of sending HEADER_ROW
 * events for two reasons:
 * 1) When HEADER_ROW event is received by JTable, it disposes selections,
 * which we don't want, and
 * 2) We have Field-specific styling to apply to the TableColumn objects,
 * so we have to create them.
 * 
 * The fireTableDataChanged() event is sent when the underlying
 * FeatureCollection has a change. This is relatively expensive for small
 * changes, but the worst case is a total redraw of the displayed area of
 * the JTable, which is trivial. The events on the other hand can only
 * hold a single range of values. FeatureCollection events can have hundreds
 * of disjoint ranges, meaning hundreds of events and minutes to process a
 * table update. So, we just update all cells and replace the selections.
 */
public class FeatureTableModel
	extends AbstractTableModel
	implements FeatureListener
{
	/**
	 * Fields that can be part of the FeatureCollection schema that should
	 * not be part of the TableModel columns.
	 */
	public static final Set defaultHiddenFields;
	static {
		Set hidden = new HashSet();
		hidden.add (Field.FIELD_SELECTED);
		hidden.add (Field.FIELD_PATH);
		defaultHiddenFields = Collections.unmodifiableSet(hidden);
	}

	private FeatureCollection fc;
	private FilteringColumnModel columnModel;
	private FeatureSelectionListener fsa;
	int[] modelToSchema;
	private boolean sending;
	private Map compMap = new HashMap ();
	private final Set hiddenFields;

	/**
	 * Initializes the Fields from the FeatureCollection schema to show as columns
	 * in the TableModel, and adds this as a FeatureCollection listener.
	 */
	public FeatureTableModel(
			FeatureCollection fc,
			FilteringColumnModel columnModel,
			FeatureSelectionListener fsa)
	{
		this(fc, columnModel, fsa, defaultHiddenFields);
	}
	
	public FeatureTableModel(
			FeatureCollection fc,
			FilteringColumnModel columnModel,
			FeatureSelectionListener fsa,
			Set hiddenFields)
	{
		this.fc = fc;
		this.columnModel = columnModel;
		this.fsa = fsa;
		this.hiddenFields = new HashSet(hiddenFields);

		compMap.put (String.class, new StringComp ());

		buildColumnLookups();
		for (Iterator ai = visibleFields (fc.getSchema()).iterator(); ai.hasNext(); ) {
			TableColumn tc = fieldToColumn((Field)ai.next());
			columnModel.addColumn (tc);
		}
	}

	/**
	 * Filters the given List down to Field objects that are not hidden. 
	 */
	private List visibleFields (List fields) {
		List visible = new LinkedList (fields);
		visible.removeAll (hiddenFields);
		return visible;
	}

	/**
	 * Build the column lookup index from TableModel index to schema index.
	 * The fields in the FeatureCollection schema are shown in order, but
	 * some are inappropriate for a JTable and so are hidden here.
	 */
	private void buildColumnLookups () {
		List schema = fc.getSchema();
		List visible = visibleFields (schema);
		modelToSchema = new int[visible.size()];
		int tableModelIndex = 0;
		int schemaIndex = 0;
		for (Iterator it=schema.iterator(); it.hasNext(); schemaIndex++) {
			Field f = (Field) it.next();
			if (! hiddenFields.contains (f)) {
				modelToSchema[tableModelIndex++] = schemaIndex;
			}
		}
	}

	/**
	 * Previous TableColumn model indices may become wrong after a Field is
	 * added or removed from the schema, so find and fix them.
	 */
	private void setColumnModelIndices() {
		List visible = visibleFields (fc.getSchema ());
		List columns = columnModel.getAllColumns();
		for (Iterator it = columns.iterator(); it.hasNext(); ) {
			TableColumn column = (TableColumn)it.next();
			int modelIndex = visible.indexOf (column.getIdentifier());
			if (column.getModelIndex() != modelIndex)
				column.setModelIndex(modelIndex);
		}
	}

	/**
	 * Constructs a new TableColumn from the given Field. The Field is set as
	 * the identifier for later use. Custom styling (such as Field-specific
	 * column sizing) should be done here.
	 */
	private TableColumn fieldToColumn (Field f)
	{
		int modelIndex = visibleFields(fc.getSchema()).indexOf(f);
		Comparator comp = compForField (f);
		TableColumn newCol = new ComparableTableColumn(modelIndex, comp);
		newCol.setIdentifier(f);
		newCol.setHeaderValue(f.name);
		if (f == Field.FIELD_LABEL)
			newCol.setPreferredWidth(150);
		return newCol;
	}

	/**
	 * Returns a Comparator to use for the table cells of the given Field.
	 */
	private Comparator compForField (Field f) {
		if (compMap.containsKey(f.type))
			return (Comparator) compMap.get(f.type);
		else
			return null;
	}

	//
	// FeatureListener implementation
	//

	/**
	 * Notify the JTable of the add/remove/change events from the
	 * FeatureCollection. Column changes are handled directly by this method.
	 */
	public void receive( FeatureEvent e) {
		switch (e.type) {
		case FeatureEvent.ADD_FIELD:
		case FeatureEvent.REMOVE_FIELD:
			buildColumnLookups();
			setColumnModelIndices ();
			for (Iterator ai = visibleFields (e.fields).iterator(); ai.hasNext(); ) {
				if (e.type == FeatureEvent.ADD_FIELD)
					columnModel.addColumn (fieldToColumn ((Field)ai.next()));
				else {
					columnModel.removeColumn (columnModel.getColumn((Field)ai.next()));
				}
			}
			return;
		}

		// exclude the hidden fields
		if (e.fields != null) {
			List fields = new LinkedList (e.fields);
			fields.removeAll (hiddenFields);
			if (fields.size() == 0)
				return;
		}

		sending = true;
		try {
			// fire table changed
			int oldCount, newCount = fc.getFeatureCount();
			switch (e.type) {
			case FeatureEvent.ADD_FEATURE:
				oldCount = newCount - e.features.size();
				if (newCount > 0)
					fireTableRowsInserted(oldCount, newCount-1);
				break;
			case FeatureEvent.CHANGE_FEATURE:
				oldCount = newCount;
				break;
			case FeatureEvent.REMOVE_FEATURE:
				oldCount = newCount + e.features.size();
				if (oldCount > 0)
					fireTableRowsDeleted(newCount, oldCount-1);
				break;
			}
			if (newCount > 0)
				fireTableRowsUpdated(0, newCount-1);

			// update selections through feature selection listener
			fsa.setFeatureSelectionsToTable();
		} finally {
			sending = false;
		}
	} // end: receive()

	/**
	 * Return true while forwarding events from the FeatureCollection
	 * to the JTable.
	 */
	public boolean isSending () {
		return sending;
	}

	//
	// TableModel implementation
	//

	/**
	 * Returns the number of Fields in the schema of the FeatureCollection,
	 * less the number of fields being hidden.
	 */
	public int getColumnCount(){
		return modelToSchema.length;
	}

	/**
	 * returns the number of Features in the associated FeatureCollection.
	 */
	public int getRowCount(){
		return fc.getFeatures().size();
	}

	/**
	 * Returns the value at the row and column in the associated FeatureCollection.
	 */
	public Object getValueAt( int row, int col) {
		Feature feature = (Feature)fc.getFeatures().get(row);
		Field field = (Field)fc.getSchema().get(modelToSchema[col]);
		return feature.getAttribute( field);
	}

	/**
	 * Sets the feature and attribute of the FeatureCollection to the specified value.
	 * Note that we must not set off the FeatureListeners, lest we get into a loop
	 * of notifications.
	 */
	public void setValueAt( Object value, int row, int col){
		Feature feature = (Feature)fc.getFeatures().get(row);
		Field field = (Field)fc.getSchema().get(modelToSchema[col]);
		feature.setAttribute( field, value);
	}

	/**
	 * AbstractTableModel cells are not editable by default. The policy of this
	 * model is that a column is editable if the Field "editable" element is set
	 * to true.  Editors are set up for all ShapeFramework data types.  The user
	 * may override this by supplying their own editor for the column. 
	 * Editors are applied to individual columns with setCellEditor( String, CellEditor).
	 */
	public boolean isCellEditable( int row, int col){
		return ((Field)fc.getSchema().get(modelToSchema[col])).editable;
	}

	/**
	 * returns the class of the column.
	 */
	public Class getColumnClass( int col){
		return ((Field)fc.getSchema().get(modelToSchema[col])).type;
	}

	/** 
	 * Returns the name of the schema Field at the specified position.
	 */
	public String getColumnName(int col){
		return ((Field)fc.getSchema().get(modelToSchema[col])).name;
	}

	class StringComp implements Comparator {
		public int compare (Object o1, Object o2) {
			if (o1 == o2) {
				return 0;
			} else if (o1 == null || !(o1 instanceof String)) {
				return -1;
			} else if (o2 == null || !(o2 instanceof String)) {
				return 1;
			} else {
				return ((String)o1).compareToIgnoreCase((String)o2);
			}
		}
	}
	
	/**
	 * Returns the FeatureCollection backing this FeatureTableModel.
	 * 
	 * @return FeatureCollection backing this TableModel.
	 */
	public FeatureCollection getFeatureCollection(){
		return fc;
	}
} // end: FeatureTableModel

