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


package edu.asu.jmars.layer.util.filetable;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.MultiFeatureCollection;
import edu.asu.jmars.layer.util.features.SingleFeatureCollection;
import edu.asu.jmars.util.History;

/**
 * Implements FileTable for ShapeLayer. This is a GUI component is basically
 * a JTable. It produces a MultiFeatureCollection for FeatureTable use.
 * The FileTable takes SingleFeatureCollection objects and displays them in a
 * tabular form. Fields displayed include:
 * <ul>
 * <li>File</li>
 * Which is the name of the file from the FeatureProvider associated
 * with the SingleFeatureCollection. If there is no FeatureProvider associated
 * with the SingleFeatureCollection, it defaults to "(default)".
 * <li>Features</li>
 * The count of the Feature objects in the FeatureProvider.
 * <li>Touched</li>
 * Signifies the fact that the SingleFeatureCollection has been modified, i.e.
 * Features have been added, removed, or modified.
 * <li>Default Flag</li>
 * A flag that indicates that the Feature has been set as the default
 * receiver for the add Feature operations.  
 * </ul>
 * The FileTable maintains an untitled SingleFeatureCollection which is
 * never removed. This SingleFeatureCollection is there as the initial default
 * SingleFeatureCollection.
 * 
 * @author saadat
 *
 */
public class FileTable extends JTable
	implements DefaultChangedListener
{
	FileTableModel ftm = new FileTableModel();
	MultiFeatureCollection mfc = new MultiFeatureCollection();
	SingleFeatureCollection untitledFeatureCollection;
	History history;

	public FileTable() {
		super();
		setModel(ftm);
		//getSelectionModel().addListSelectionListener(mfcul);
		getSelectionModel().addListSelectionListener(new FileTableListSelectionListener(ftm, mfc));
		ftm.addDefaultChangedListener(this);

		for(int i=0; i<FileTableModel.columns.length; i++)
			if (i == FileTableModel.COL_IDX_DEFAULT_INDICATOR){
				getColumnModel().getColumn(i).setMaxWidth(((Integer)FileTableModel.columns[i][2]).intValue());
				getColumnModel().getColumn(i).setCellRenderer(new DefaultIndicatorRenderer());
			}
			else {
				getColumnModel().getColumn(i).setPreferredWidth(((Integer)FileTableModel.columns[i][2]).intValue());
			}

		// Add the Untitled SingleFeatureCollection to the SingleFeatureCollections.
		untitledFeatureCollection = new SingleFeatureCollection();
		SingleFeatureCollection.addDefaultFields(untitledFeatureCollection);
		add(untitledFeatureCollection);

		/*
		 *  Change default SingleFeatureCollection on double-click on a row at the first column.
		 *  The request is transmitted to the FileTableModel which then distributes this
		 *  request to all the listeners, including the FileTable, which is a registered
		 *  listener on the FileTableModel.
		 */
		addMouseListener(new MouseAdapter(){
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2){
					int col = columnAtPoint(e.getPoint());
					if (col == FileTableModel.COL_IDX_DEFAULT_INDICATOR){
						int row = rowAtPoint(e.getPoint());
						ftm.setDefaultFeatureCollection(row);
					}
				}
			}
		});
	}

	/**
	 * Adds a SingleFeatureCollection to the FileTable. If this is the first
	 * SingleFeatureCollection added, it automatically becomes the default.
	 * 
	 * @param fc SingleFeatureCollection to add.
	 */
	public void add(SingleFeatureCollection fc){
		ftm.add(fc);
	}

	/**
	 * Removes a SingleFeatureCollection from the FileTable. If the removed
	 * SingleFeatureCollection was the defaultFeatureCollection, the TableModel
	 * choses a new default automatially and broadcasts it via the
	 * DefaultChangedListener interface. This call does not remove the
	 * untitled SingleFeatureCollection.
	 * 
	 * @param fc SingleFeatureCollection to remove.
	 * @return true for successful removal, false otherwise.
	 */
	public boolean remove(SingleFeatureCollection fc){
		// Disallow removal of the untitledFeatureCollection
		if (fc == untitledFeatureCollection)
			return false;
		return ftm.remove(fc);
	}

	/**
	 * Remove selected rows from the FileTable. It will not, however, remove
	 * the untitled SingleFeatureCollection.
	 * 
	 * @return true if everything got removed correctly, false otherwise.
	 */
	public boolean removeSelected(){
		int[] selectedRows = getSelectedRows();

		List removeList = new ArrayList();
		for(int i=0; i<selectedRows.length; i++)
			removeList.add(get(selectedRows[i]));

		return removeAll(removeList);
	}

	/**
	 * Return selected SingleFeatureCollections. In theory we could also get the
	 * supporting SingleFeatureCollections behind the MultiFeatureCollection and
	 * it would be the same thing.
	 * 
	 * @return A list of SingleFeatureCollection objects corresponding to the 
	 *         selected rows in the FileTable.
	 */
	public List getSelectedFeatureCollections(){
		int[] selectedRows = getSelectedRows();

		List list = new ArrayList();
		for(int i=0; i<selectedRows.length; i++)
			list.add(get(selectedRows[i]));

		return list;
	}

	/**
	 * Return all FetureCollections that make up this FileTable.
	 * @return A list of FeatureCollection objects corresponding to all
	 *         rows in the FileTable.
	 */
	public List getFeatureCollections(){
		return Arrays.asList(ftm.getAll());
	}
	/**
	 * Removes the given list of SingleFeatureCollection objects from the FileTable.
	 * If the defaultFeatureCollection is removed, a new default is selected
	 * if one is available. This selection is broadcast via the 
	 * DefaultChangedListener. This call does not remove the untitled 
	 * SingleFeatureCollection. Note that even if the untitledFeatureCollection is
	 * a part of the input list, it is not deleted.
	 * 
	 * @param fcl List of SingleFeatureCollection objects to remove.
	 * @return true if everything in the fcl got removed, false otherwise.
	 */
	public boolean removeAll(List fcl){
		boolean result = false;
		for (Iterator it=fcl.iterator(); it.hasNext(); )
			result |= remove((SingleFeatureCollection)it.next());
		return result;
	}

	/**
	 * Makes the given SingleFeatureCollection the default SingleFeatureCollection
	 * for add Feature operations. The SingleFeatureCollection must already be
	 * a part of the FileTable, otherwise, exceptions are thrown.
	 * 
	 * @param fc SingleFeatureCollection to set as default.
	 */
	public void setDefault(SingleFeatureCollection fc){
		ftm.setDefaultFeatureCollection(fc);
	}

	/**
	 * Returns the default SingleFeatureCollection as set in the MultiFeatureCollection.
	 * @return The defaultFeatureCollection.
	 */
	public SingleFeatureCollection getDefault(){
		return ftm.getDefaultFeatureCollection();
	}

	/**
	 * Returns the SingleFeatureCollection at the specified index or null
	 * for an invalid index.
	 */
	public SingleFeatureCollection get(int index){
		return ftm.get(index);
	}

	/**
	 * Returns whether the specified SingleFeatureCollection has been modified.
	 * @return true if the SingleFeatureCollection is known to be modified, false
	 *          otherwise. The result is false even when the FileTable has
	 *          no knowledge of this SingleFeatureCollection.
	 */
	public boolean getTouched(SingleFeatureCollection fc){
		return ftm.getTouched(fc);
	}

	/**
	 * Sets the <em>modified</em> status of a SingleFeatureCollection. The request
	 * is silently ignored if the SingleFeatureCollection is not a part of this
	 * FileTable.
	 */
	public void setTouched(SingleFeatureCollection fc, boolean flag) {
		ftm.setTouched(fc, flag);
	}

	/**
	 * Returns the MultiFeatureCollection produced by this FileTable. There
	 * is a single instance of this collection in the FileTable, thus
	 * subsequent calls to this method will return the same collection.
	 * This MultiFeatureCollection is automatically kept up to date with the
	 * current selections in the FileTable. This MultiFeatureCollection is
	 * also kept up to date with the current default SingleFeatureCollection as
	 * selected by the user.
	 */
	public MultiFeatureCollection getMultiFeatureCollection(){
		return mfc;
	}

	/*
	public class MFCUpdateListener implements ListSelectionListener {
		public void valueChanged(ListSelectionEvent e) {
			if (e.getValueIsAdjusting())
				return;

			Set selected = new HashSet();
			int[] selectedRows = getSelectedRows();
			for(int i=0; i<selectedRows.length; i++)
				selected.add(ftm.get(selectedRows[i]));

			Set existing = new HashSet(mfc.getSupportingFeatureCollections());
			Set removeSet = new HashSet(existing);
			removeSet.retainAll(selected);
			Set addSet = new HashSet(selected);
			addSet.removeAll(existing);

			for(Iterator i=removeSet.iterator(); i.hasNext(); ){
				mfc.removeFeatureCollection((SingleFeatureCollection)i.next());
			}
			for(Iterator i=addSet.iterator(); i.hasNext(); ){
				mfc.addFeatureCollection((SingleFeatureCollection)i.next());
			}
			// NOTE: There is not deselect event.
		}
	}
	*/

	/**
	 * Tie-up class that links the selection made in the FileTable to the
	 * contents of a MultiFeatureCollection. In other words, this class
	 * keeps the MultiFeatureCollection up to date.
	 * 
	 * @author saadat
	 *
	 */
	public class FileTableListSelectionListener implements ListSelectionListener {
		FileTableModel ftm;
		MultiFeatureCollection mfc;

		/**
		 * Constructs a FileTableListSelectionListener which ties the FileTable, 
		 * FileTableModel, the ListSelectionModel on the FileTable, and the
		 * MultiFeatureCollection.
		 * 
		 * @param ftm FileTableModel to get the selection data from.
		 * @param mfc MultiFeatureCollection to update in response to user selection.
		 */
		public FileTableListSelectionListener(FileTableModel ftm, MultiFeatureCollection mfc){
			this.ftm = ftm;
			this.mfc = mfc;
		}

		/**
		 * Returns an array of row indices corresponding to the rows selected in
		 * the table. The array is guaranteed to be non-null.
		 * 
		 * @param e ListSelectionEvent to get the ListSelectionModel from.
		 * @return A non-null array of user selected indices.
		 */
		private int[] getSelectedRows(ListSelectionEvent e){
			ListSelectionModel lsm = (ListSelectionModel)e.getSource();
			if (lsm.isSelectionEmpty())
				return new int[0];

			int count = 0;
			for(int i=lsm.getMinSelectionIndex(); i<=lsm.getMaxSelectionIndex(); i++){
				if (lsm.isSelectedIndex(i))
					count++;
			}

			int[] selected = new int[count];
			int j = 0;
			for(int i=lsm.getMinSelectionIndex(); i<=lsm.getMaxSelectionIndex(); i++){
				if (lsm.isSelectedIndex(i))
					selected[j++] = i;
			}

			return selected;
		}

		/**
		 * ListSelectionListener interface realization method. Listens to ListSelectionEvents
		 * updates the MultiFeatureCollection appropriately. In order to get update the
		 * MultiFeatureCollection, it has to query the FileTableModel to get the 
		 * SingleFeatureCollections corresponding to the user selected rows.
		 */
		public void valueChanged(ListSelectionEvent e) {
			if (e.getValueIsAdjusting())
				return;
			if (!(e.getSource() instanceof ListSelectionModel))
				return;

			int[] selectedRows = getSelectedRows(e);
			Set selected = new HashSet();
			for(int i=0; i<selectedRows.length; i++)
				selected.add(ftm.get(selectedRows[i]));

			Set existing = new HashSet(mfc.getSupportingFeatureCollections());
			Set removeSet = new HashSet(existing);
			removeSet.removeAll(selected);
			Set addSet = new HashSet(selected);
			addSet.removeAll(existing);

			for(Iterator i=removeSet.iterator(); i.hasNext(); ){
				mfc.removeFeatureCollection((SingleFeatureCollection)i.next());
			}
			for(Iterator i=addSet.iterator(); i.hasNext(); ){
				mfc.addFeatureCollection((SingleFeatureCollection)i.next());
			}
		}
	}

	/**
	 * Tie-up class that links the defaultFeatureCollection selected by
	 * the user to the MultiFeatureCollection. This default value is
	 * produced by the TableModel as a result of UI interaction with
	 * the FileTable.
	 */
	public void defaultChanged(DefaultChangedEvent e) {
		mfc.setDefaultFeatureCollection(e.fc);
	};

	/**
	 * Renderer for the "default" flagging column. It displays a bullet when the
	 * source bullet image is available, or a "*" if the image is unavailable.
	 *  
	 * @author saadat
	 *
	 */
	static class DefaultIndicatorRenderer extends JLabel implements TableCellRenderer {
		private ImageIcon defaultIcon = null; 

		public DefaultIndicatorRenderer(){
			super();
			setOpaque(true);
			setHorizontalAlignment(CENTER);
			setVerticalAlignment(CENTER);

			URL iconURL = Main.getResource("resources/bullet_small.gif");
			if (iconURL != null)
				defaultIcon = new ImageIcon(iconURL, "*");
		}
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			setFont(table.getFont());
			if (isSelected){
				setBackground(table.getSelectionBackground());
				setForeground(table.getSelectionForeground());
			}
			else {
				setBackground(table.getBackground());
				setForeground(table.getForeground());
			}

			boolean isDefault = ((Boolean)value).booleanValue();
			if (isDefault){
				setText(defaultIcon==null? "*": "");
				setIcon(defaultIcon==null? null: defaultIcon);
			}
			else {
				setText(" ");
				setIcon(null);
			}

			if (defaultIcon != null)
				setSize(defaultIcon.getIconWidth(), defaultIcon.getIconHeight());

			return this;
		}
	}

	/* Versionable Interface */

	/**
	 * Sets the history object.
	 * @param history History object to send historical changes to.
	 */
	public void setHistory(History history){
		ftm.setHistory (history);
		untitledFeatureCollection.setHistory(history);
		ftm.setHistory(history);
	}
}
