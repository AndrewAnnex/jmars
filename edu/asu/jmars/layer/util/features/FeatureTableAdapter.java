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

import java.awt.Color;

import javax.swing.JTable;
import javax.swing.event.ChangeEvent;

import edu.asu.jmars.swing.STable;
import edu.asu.jmars.util.History;
import edu.asu.jmars.util.LineType;
import edu.asu.jmars.util.ObservableSet;
import edu.asu.jmars.util.stable.*;

/**
 * Produces an STable for viewing features. Editors and renderers are added for
 * color and line type. Column auto resizing is disabled. A FeatureTableModel is
 * created around the given FeatureCollection. A FeatureSelectionListener is
 * added to keep the STable's ListSelectionModel and the FIELD_SELECTED
 * attributes on Features in the FeatureCollection in sync. This will listen on
 * the FeatureCollection and send events on to the FeatureTableModel and then
 * the FeatureSelectionListener.
 */
public class FeatureTableAdapter implements FeatureListener
{
	private STable fst;
	private FeatureCollection fc;
	private FeatureSelectionListener fsl;
	private FeatureTableModel ftm;
	private History history;
	
	public FeatureTableAdapter (FeatureCollection fc, ObservableSet<Feature> selections, History history) {
		this.fc = fc;
		this.history = history;
		
		// create and customize an STable for our needs
		fst = new STable () {
			// marks a history frame when the table cell editor is used
			public void editingStopped(ChangeEvent e) {
				FeatureTableAdapter.this.history.mark();
				super.editingStopped(e);
			}
		};
		
		fst.setAutoCreateColumnsFromModel(false);
		fst.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		fst.setTypeSupport(Color.class, new ColorCellRenderer(), new ColorCellEditor());
		fst.setTypeSupport(LineType.class, new LineTypeTableCellRenderer(), new LineTypeCellEditor());

		fsl = new FeatureSelectionListener (fst, fc, selections);
		ftm = new FeatureTableModel (fc, (FilteringColumnModel)fst.getColumnModel(), fsl);
		fst.setUnsortedTableModel(ftm);

		fsl.setFeatureSelectionsToTable();

		// This class receives events on behalf of the table and selection
		// listener and forwards to guarrantee that the table gets them first.
		fc.addListener (this);
	}

	/**
	 * @return The STable component produced by this instance.
	 */
	public STable getTable () {
		return fst;
	}

	/**
	 * Sends incoming events from the FeatureCollection on to the FeatureTableModel
	 * and FeatureSelectionListener (in that order, since JTable can't make selections
	 * on data that doesn't exist yet.)
	 */
	public void receive (FeatureEvent event) {
		ftm.receive(event);
	}

	/**
	 * Clear various listeners as part of the cleanup.
	 */
	public void disconnect() {
		fc.removeListener (this);
		fsl.disconnect ();
	}
}