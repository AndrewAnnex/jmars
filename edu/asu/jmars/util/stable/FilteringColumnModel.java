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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;

import edu.asu.jmars.Main;

/**
 * Extends DefaultTableColumnModel to let the user filter which columns they
 * want to see via a column dialog. This class doesn't know about the component
 * it's connected to, so the containing class must call showDialog() in response
 * to some user action. This class aims to behave like DefaultTableColumnModel
 * if the column dialog is never shown.
 */
public class FilteringColumnModel extends DefaultTableColumnModel {
	/**
	 * All the columns, visible or not
	 */
	private List<TableColumn> allColumns = new ArrayList<TableColumn>();

	/**
	 * Column dialog for selecting which columns are visible.
	 */
	private ColumnDialog columnDialog = new ColumnDialog((Frame)Main.testDriver.getLManager(), this);

	// Convert from visible index to allColumns index; the 'first available'
	// index in tableColumns is translated to the 'first available' index in
	// allColumns.
	private int visToAll(int visIndex) {
		if (visIndex < 0 || visIndex > tableColumns.size())
			return -1;
		else if (visIndex == tableColumns.size())
			return allColumns.size();
		else
			return allColumns.indexOf(getColumn(visIndex));
	}

	// return intersection of a and b, in a's order
	private List<?> intersection(List<?> a, List<?> b) {
		List<?> copyA = new ArrayList<Object>(a);
		copyA.retainAll(b);
		return copyA;
	}

	/**
	 * Returns unmodifiable List of all columns, visible or not.
	 */
	public List<TableColumn> getAllColumns () {
		return Collections.unmodifiableList (allColumns);
	}

	/**
	 * Returns the TableColumn associated with the given identifier. The search
	 * is done over visible and hidden TableColumns.
	 * @return null if the identifier isn't on at least one TableColumn, or the
	 * reference to the first one found.
	 */
	public TableColumn getColumn (Object identifier) {
		for (TableColumn column: allColumns)
			if (column.getIdentifier().equals(identifier))
				return column;
		return null;
	}

	/**
	 * Returns the TableColumn associated with the given identifier. The search
	 * is done over visible and hidden TableColumns.
	 * @return null if the identifier isn't on at least one TableColumn, or the
	 * reference to the first one found.
	 */
	public TableColumn getVisColumn (Object identifier) {
		for (int i = 0; i < super.getColumnCount(); i++) {
			TableColumn column = super.getColumn(i);
			if (column.getIdentifier().equals(identifier))
				return column;
		}
		return null;
	}

	//
	// TableColumnModel overrides
	//

	/**
	 * Adds a column to the list of allColumns but does not show it. Does
	 * nothing if the given column is already in the model.
	 */
	public void addColumn(TableColumn col) {
		if (!allColumns.contains(col)) {
			super.addColumn(col);
			allColumns.add(col);
			columnDialog.buildCheckboxes();
		}
	}

	public void removeAllColumns() {
        for (Object tc : allColumns) {
            super.removeColumn((TableColumn)tc);
        }
		allColumns.clear();		
		columnDialog.buildCheckboxes();
	}
	
	public void removeColumn(TableColumn column) {
		allColumns.remove(column);
		super.removeColumn(column);
		columnDialog.buildCheckboxes();
	}

	public void moveColumn(int tableFrom, int tableTo) {
		// must call visToAll before moving the columns
		int allFrom = visToAll(tableFrom);
		int allTo = visToAll(tableTo);
		// must do this no matter what
		super.moveColumn(tableFrom, tableTo);
		// the rest of this method proceed only when the index _has_ changed
		if (tableFrom == tableTo)
			return;
		// do nothing if allFrom is not a valid position
		if (allFrom < 0 || allFrom >= allColumns.size())
			return;
		// update both column lists
		allColumns.add(allTo, allColumns.remove(allFrom));
		columnDialog.buildCheckboxes();
	}

	/**
	 * Sets column visibility and if a change was made in the visible
	 * columns, notifies ColumnModel listeners. Two events are sent: the
	 * first adds the column to the end of the model, the second moves it to
	 * the proper index.
	 */
	public void setVisible(TableColumn column, boolean vis) {
		if (!allColumns.contains(column))
			return;
		if (tableColumns.contains(column) && !vis) {
			super.removeColumn(column);
		} else if (!tableColumns.contains(column) && vis) {
			super.addColumn(column);
			int from = tableColumns.indexOf(column);
			int to = intersection(allColumns, tableColumns).indexOf(column);
			super.moveColumn(from, to);
		}
		columnDialog.buildCheckboxes();
	}

	/**
	 * Returns a JDialog for choosing columns. Note that since it is unparented,
	 * the calling code must deal with closing the dialog if it shows it.
	 */
	public JDialog getColumnDialog () {
		return columnDialog;
	}

	/**
	 * Allows selecting which columns are displayed in the table.
	 */
	class ColumnDialog extends JDialog {
		private FilteringColumnModel columnModel;
		private JPanel columnPanel;

		public ColumnDialog(Frame parent, FilteringColumnModel columnModel) {
			super((Frame) parent, "Columns", false);
			this.columnModel = columnModel;
			setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
			getContentPane().setLayout(new BorderLayout());

			// Set up list of columns in the middle of the dialog
			columnPanel = new JPanel();
			columnPanel.setLayout(new BoxLayout(columnPanel,
				BoxLayout.Y_AXIS));
			buildCheckboxes();
			JScrollPane scrollPane = new JScrollPane(columnPanel);
			getContentPane().add(scrollPane, BorderLayout.CENTER);
			scrollPane.setPreferredSize(new Dimension(250,400));

			// Set up the button panel at the bottom of the panel.
			JPanel buttonPanel = new JPanel();
			JButton allButton = new JButton(new AbstractAction("Show All") {
				public void actionPerformed(ActionEvent e) {
					Component[] list = columnPanel.getComponents();
					for (int i = 0; i < list.length; i++) {
						JCheckBox cb = (JCheckBox) list[i];
						if (!cb.isSelected())
							cb.doClick(1);
					}
				}
			});

			JButton nothingButton = new JButton(new AbstractAction(
					"Hide All") {
				public void actionPerformed(ActionEvent e) {
					Component[] list = columnPanel.getComponents();
					for (int i = 0; i < list.length; i++) {
						JCheckBox cb = (JCheckBox) list[i];
						if (cb.isSelected())
							cb.doClick(1);
					}
				}
			});

			JButton okButton = new JButton(new AbstractAction("OK") {
				public void actionPerformed(ActionEvent e) {
					setVisible(false);
				}
			});

			allButton.setFocusPainted(false);
			nothingButton.setFocusPainted(false);
			okButton.setFocusPainted(false);

			buttonPanel.setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.insets = new Insets(5, 5, 5, 5);
			gbc.gridx = 0;
			buttonPanel.add(allButton, gbc);
			gbc.gridx = 1;
			buttonPanel.add(nothingButton, gbc);
			gbc.gridy = 1;
			gbc.gridx = 0;
			gbc.gridwidth = 2;
			gbc.insets.top = 0;
			buttonPanel.add(okButton, gbc);
			getContentPane().add(buttonPanel, BorderLayout.SOUTH);
			pack();

			// Display this dialog in the middle of the screen.
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			Dimension d = getSize();
			d.width = Math.min(d.width, (screen.width / 2));
			d.height = Math.min(d.height, (screen.height / 2));
			setSize(d);
			int x = (screen.width - d.width) / 2;
			int y = (screen.height - d.height) / 2;
			setLocation(x, y);
		}

		public void buildCheckboxes() {
			columnPanel.removeAll();
			for (final TableColumn column: columnModel.getAllColumns()) {
				String name = column.getHeaderValue().toString();
				boolean visible = (null != columnModel.getVisColumn(column.getIdentifier()));
				JCheckBox cb = new JCheckBox (name, null, visible);
				cb.addItemListener(new ItemListener() {
					public void itemStateChanged(ItemEvent e) {
						boolean checked = (e.getStateChange() == ItemEvent.SELECTED);
						columnModel.setVisible(column, checked);
					}
				});
				columnPanel.add(cb);
			}
			validate();
			paint (getGraphics());
		}
	}
}
