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
 **/
package edu.asu.jmars.layer.util.features;

import java.awt.Rectangle;
import java.util.*;

import javax.swing.ListSelectionModel;
import javax.swing.event.*;

import edu.asu.jmars.swing.STable;
import edu.asu.jmars.util.stable.Sorter;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

/**
 * Adapts selection changes between a FeatureCollection and a
 * ListSelectionModel, using a Sorter to convert between sorted and unsorted
 * coordinates, and the FeatureTableModel to ignore those events that are the
 * result of the tableModel's actions. *
 * <p>
 * When the FeatureTableModel is changing the ListSelectionModel through the
 * JTable, FeatureSelectionListener should ignore the generated events.
 * <p>
 * When the FeatureSelectionListener is changing the ListSelectionModel to match
 * the FeatureCollection's selections, it should ignore the return events.
 */
public class FeatureSelectionListener implements FeatureListener, ListSelectionListener
{
	static final DebugLog log = DebugLog.instance();

	private STable table;
	private FeatureCollection fc;
	private ListSelectionModel listModel;
	private Sorter sorter;
	private boolean listening = true;

	/**
	 * It is strongly recommended that setFeatureSelectionsToTable() be called
	 * soon after construction to sync the feature collection and the selection
	 * model.
	 */
	public FeatureSelectionListener(STable table, FeatureCollection fc) {
		this.table = table;
		this.listModel = table.getSelectionModel();
		this.fc = fc;
		this.sorter = table.getSorter();

		listModel.addListSelectionListener(this);
	}

	/**
	 * Update table row selection from the <em>selected</em> attribute
	 * in each Feature object.
	 */
	public void setFeatureSelectionsToTable () {
		listening = false;
		listModel.setValueIsAdjusting(true);
		try {
			listModel.clearSelection();

			int[] selectIdx = new int[fc.getFeatures().size()];
			int selectSize = 0, pos = 0;
			for (Iterator fi=fc.featureIterator(); fi.hasNext(); pos ++) {
				Boolean isSelected = (Boolean)((Feature)fi.next()).getAttribute(Field.FIELD_SELECTED);
				if (isSelected != null && isSelected.booleanValue())
					selectIdx[selectSize++] = sorter.sortRow(pos);
			}

			int[][] binned = Util.binRanges (selectIdx, selectSize);
			for (int i = 0; i < binned.length; i++)
				listModel.addSelectionInterval(binned[i][0],binned[i][1]);
		} finally {
			listening = true;
			listModel.setValueIsAdjusting(false);
		}
	}

	/**
	 * Remove this instance from listening to the underlying
	 * FeatureCollection and ListSelectionModel.
	 */
	public void disconnect () {
		listModel.removeListSelectionListener (this);
	}

	/**
	 * When the selected state of a Feature disagrees with the selection model
	 * in the table, update the Feature. Ignores events while the
	 * FeatureTableModel or this is sending events to the ListSelectionModel.
	 */
	/**
	 *  TODO: make this work right, and remove this note.
	 *  CAUTION: The removeSelectionInterval() calls in the receive()
	 *  method do not appear to cause proper changes in the lead & anchor
	 *  indices of the DefaultSelectionModel. As a consequence, when a
	 *  bunch of Features are removed from the FeatureCollection backing
	 *  the STable, the next ListSelectionEvent generated
	 *  still wants to go through the entire range that was in the 
	 *  TableModel before the removal.
	 */
	public void valueChanged(ListSelectionEvent e){
		if (! listening || ((table.getUnsortedTableModel() instanceof FeatureTableModel)
		&& ((FeatureTableModel)table.getUnsortedTableModel()).isSending ())) {
			return;
		}

		if (e.getValueIsAdjusting()){
			return;
		}

		listening = false;
		try {
			// only process events that are entirely good
			int first = e.getFirstIndex();
			int last  = e.getLastIndex();
			int maxSize = sorter.getSize () - 1;
			if (last > maxSize)
				last = maxSize;
			if (first < 0 || last < 0) {
				log.aprintln ("Invalid event from listModel");
				return;
			}

			Map toModify = new LinkedHashMap();

			for (int sortedIdx = first; sortedIdx <= last; sortedIdx++) {
				int unsortedIdx = sorter.unsortRow (sortedIdx);
				Feature feat = (Feature)fc.getFeatures().get(unsortedIdx);
				boolean tableSel = listModel.isSelectedIndex(sortedIdx);
				Boolean featureSel = (Boolean)feat.getAttribute(Field.FIELD_SELECTED);
				if (featureSel == null) {
					featureSel = Boolean.FALSE;
				}

				if (featureSel.booleanValue() != tableSel) {
					toModify.put(feat, tableSel ? Boolean.TRUE : Boolean.FALSE);
				}
			}

			if (!toModify.isEmpty()) {
				fc.setAttributes(Field.FIELD_SELECTED, toModify);
			}
		} finally {
			listening = true;
		}
	}

	/**
	 * When the selected state of a Feature disagrees with the selection model
	 * in the table, update the selection model.
	 */
	public void receive( FeatureEvent e)
	{
		if (! listening) {
			return;
		}

		// Only process feature add/change events; when FeatureTableModel sends
		// a tableChanged indicating a deletion, the JTable removes any
		// corresponding selections for us.
		listening = false;
		listModel.setValueIsAdjusting(true);
		try {
			switch (e.type) {
			case FeatureEvent.ADD_FEATURE:
				int[] sorted = new int[e.features.size()];
				int sortedSize = 0;
				for (Iterator li = e.features.iterator(); li.hasNext(); ) {
					Feature f = (Feature)li.next();
					if (f.getAttribute (Field.FIELD_SELECTED) == Boolean.TRUE) {
						int unsortedIdx = ((Integer)e.featureIndices.get(f)).intValue();
						int sortedIndex = sorter.sortRow (unsortedIdx);
						sorted[sortedSize++] = sortedIndex;
					}
				}
				int[][] binned = Util.binRanges (sorted, sortedSize);
				for (int i = 0; i < binned.length; i++)
					listModel.addSelectionInterval (binned[i][0], binned[i][1]);
				break;

			case FeatureEvent.CHANGE_FEATURE:
				if (! e.fields.contains(Field.FIELD_SELECTED))
					return;
				int[] addArr = new int[e.features.size()];
				int[] delArr = new int[e.features.size()];
				int addSize = 0, delSize = 0;
				for (Iterator li = e.features.iterator(); li.hasNext(); ) {
					Feature f = (Feature)li.next();
					int unsortedIdx = ((Integer)e.featureIndices.get(f)).intValue();
					int sortedIndex = sorter.sortRow (unsortedIdx);
					Boolean selected = (Boolean)f.getAttribute(Field.FIELD_SELECTED);
					if (selected == null || !selected.booleanValue()) {
						delArr[delSize++] = sortedIndex;
					} else {
						addArr[addSize++] = sortedIndex;
					}
				}

				if (addSize > 0) {
					int[][] addBins = Util.binRanges (addArr, addSize);
					for (int i = 0; i < addBins.length; i++)
						listModel.addSelectionInterval (addBins[i][0], addBins[i][1]);
					Rectangle rect = table.getCellRect(addBins[addBins.length-1][1], 0, true);
					rect = rect.union(table.getCellRect(addBins[addBins.length-1][1], table.getColumnCount()-1, true));
					table.scrollRectToVisible(rect);
				}

				if (delSize > 0) {
					int[][] delBins = Util.binRanges (delArr, delSize);
					for (int i = 0; i < delBins.length; i++)
						listModel.removeSelectionInterval (delBins[i][0], delBins[i][1]);
				}
				break;
			}
		} finally {
			listening = true;
			listModel.setValueIsAdjusting(false);
		}
	}

	/**
	 * Controls whether FeatureSelectionListener will process or ignore events
	 * from the ListSelectionModel.
	 */
	public void setListening (boolean listen) {
		this.listening = listen;
	}
} // end: class FeatureSelectionListener.java
