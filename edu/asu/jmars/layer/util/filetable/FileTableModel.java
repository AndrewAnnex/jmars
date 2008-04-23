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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.table.AbstractTableModel;

import edu.asu.jmars.layer.util.features.Feature;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.FeatureEvent;
import edu.asu.jmars.layer.util.features.FeatureListener;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.SingleFeatureCollection;
import edu.asu.jmars.util.History;
import edu.asu.jmars.util.Versionable;
import edu.asu.jmars.util.DebugLog;

public class FileTableModel extends AbstractTableModel
	implements FeatureListener, Versionable
{
	private static DebugLog log = DebugLog.instance();
	
	/**
	 * List of FeatureCollections that back this TableModel.
	 */
	private FCC fcc = new FCC();
	
	/**
	 * Current modification status of the FeatureCollection. A FeatureCollection
	 * is marked modified every time the FileTableModel is notified of a change
	 * made to the FeatureCollection.
	 */
	private Map touched = new HashMap();
	
	/**
	 * Marks the current default FeatureCollection. This FeatureCollection serves
	 * as the target for all add-Feature operations.
	 */
	private SingleFeatureCollection defaultFC = null;

	/**
	 * Listeners listening to the DefaultChangedEvent.
	 */
	private List listeners = new LinkedList();

	/**
	 * History object to log changes to
	 */
	private History history;

	/**
	 * Column names, their types and default widths (for lack of a better place).
	 */
	public static final Object[][] columns = new Object[][] {
		{"*",        Boolean.class,  new Integer(1)}, // default indicator
		{"File",     String.class,  new Integer(200)},
		{"Features", Integer.class, new Integer(50)},
		{"Touched",  Boolean.class, new Integer(50)},
	};
	
	/**
	 * Column index enumerations.
	 */
	public static final int COL_IDX_DEFAULT_INDICATOR = 0;
	public static final int COL_IDX_FILE = 1;
	public static final int COL_IDX_COUNT = 2;
	public static final int COL_IDX_TOUCHED = 3;
	
	/**
	 * Provider name use when FeatureProvider is null for a given Feature.
	 */
	public static final String NULL_PROVIDER_NAME = "(untitled)";
	
	/**
	 * Default constructor.
	 */
	public FileTableModel() {
		super();
	}

	
	/**
	 * Realization of AbstractTableModel.getColumnCount().
	 */
	public int getColumnCount() {
		return columns.length;
	}

	/**
	 * Realization of AbstractTableModel.getRowCount().
	 */
	public int getRowCount() {
		return fcc.size();
	}

	/**
	 * Realization of AbstractTableModel.getValueAt(int,int).
	 */
	public Object getValueAt(int row, int col) {
		FeatureCollection fc = (FeatureCollection)fcc.get(row);
		switch(col){
		case COL_IDX_DEFAULT_INDICATOR:
			return (fc == defaultFC)? Boolean.TRUE: Boolean.FALSE;
		case COL_IDX_FILE:
			if (fc.getFilename() == null)
				return NULL_PROVIDER_NAME;
			else
				return fc.getFilename();
		case COL_IDX_COUNT:
			return new Integer(fc.getFeatures().size());
		case COL_IDX_TOUCHED:
			return touched.get(fc);
		}
		return null;
	}

	/**
	 * Overrides AbstractTableModel.getColumnClass(int).
	 */
	public Class getColumnClass(int col){
		return (Class)columns[col][1];
	}
	
	/**
	 * Overrides AbstractTableModel.getColumnName(int).
	 */
	public String getColumnName(int col){
		return (String)columns[col][0];
	}
	
	/**
	 * Overrides AbstractTableModel.isCellEditable(int,int).
	 */
	public boolean isCellEditable(int row, int col){
		return false;
	}
	
	
	/**
	 * Public interface to add a new FeatureCollection to the FileTable.
	 * @param fc
	 */
	public void add(SingleFeatureCollection fc){
		int idx = fcc.size();
		fcc.add(fc);
		touched.put(fc, Boolean.FALSE);
		if (history != null)
			history.addChange (this, new AddChange (fc));
		fc.addListener(this);
		if (defaultFC == null){
			defaultFC = fc;
			fireDefaultChangeEvent(defaultFC);
		}
		fireTableRowsInserted(idx,idx);
		// NOTE: No notfication needs to be sent to the MFC since
		// additions do not change the contents of the MFC.
	}
	
	/**
	 * Public interface to remove a FeatureCollection from the FileTable.
	 * <strong>Warning:</strong>This interface does not take care of keeping the
	 * untitledFeatureCollection in the FileTable. To ensure the proper behavior
	 * of keeping the untitledFeatureCollection in the FileTable use
	 * {@link FileTable#removeAll(List)}.
	 * 
	 * @param fc FeatureCollection to remove.
	 * @return true if the removal was successful.
	 */
	public boolean remove(SingleFeatureCollection fc){
		int idx = fcc.indexOf(fc);
		if (idx < 0)
			return false;

		history.addChange (this, new RemoveChange (fc));
		fc.removeListener(this);
		boolean result = fcc.remove(fc);
		touched.remove(fc);
		fireTableRowsDeleted(idx,idx);

		if (fc == defaultFC){
			if (idx >= fcc.size())
				idx = fcc.size()-1;
			defaultFC = (idx >= 0)? (SingleFeatureCollection)fcc.get(idx): null;
			fireDefaultChangeEvent(defaultFC);
			if (idx >= 0)
				fireTableRowsUpdated(idx, idx);
		}
		
		// NOTE: No need to send a notification to the MFC since the MFC
		// will be listening to the selection changes.
		return result;
	}
	
	/**
	 * Public interface to remove a collection of FeatureCollection from
	 * the FileTable.
	 * <strong>Warning:</strong>This interface does not take care of keeping the
	 * untitledFeatureCollection in the FileTable. To ensure the proper behavior
	 * of keeping the untitledFeatureCollection in the FileTable use
	 * {@link FileTable#removeAll(List)}.
	 *  
	 * @param fcl List of FeatureCollection objects.
	 * @return true if the entire removal was successful, false otherwise.
	 */
	public boolean removeAll(List fcl){
		int[] indices = new int[fcl.size()];
		boolean defaultDeleted = false;
		
		// Carry out the removal and collect deleted indices.
		Iterator li = fcl.iterator();
		int i=0;
		while(li.hasNext()){
			FeatureCollection fc = (FeatureCollection)li.next();
			fc.removeListener(this);
			indices[i] = fcc.indexOf(fc);
			if (fc == defaultFC)
				defaultDeleted = true;
			i++;
		}

		List intersection = new LinkedList (fcc);
		intersection.retainAll (fcl);
		for (Iterator it=intersection.iterator(); it.hasNext(); ) {
			SingleFeatureCollection sfc = (SingleFeatureCollection)it.next();
			history.addChange (this, new RemoveChange (sfc));
		}
		boolean result = fcc.removeAll(fcl);
		touched.keySet().removeAll(fcl);
		
		// Sort the collected indices for which we have to fire deleteRow events.
		Arrays.sort(indices);
		
		// Batch contiguous indices to generate the minimal amount of 
		// rowDeleted events. 
		// Process this list of indices in the reverse order. This will ensure
		// minimal disturbance in the data itself and will require the least
		// amount of redraws. Stop when a negative index is hit.
		int run = (indices.length > 0)? 1: 0;
		for(i=indices.length-2; i>=0; i--){
			if (indices[i] != (indices[i+1]-1) || indices[i] < 0){
				fireTableRowsDeleted(indices[i+1], indices[i+1]+run-1);
				run=0;
			}
			if (indices[i] < 0)
				break;
			
			run++;
		}
		if (run > 0 && indices[i+1] >= 0)
			fireTableRowsDeleted(indices[i+1], indices[i+1]+run-1);
		
		if (defaultDeleted){
			int defaultIndex = indices[indices.length-1];
			if (defaultIndex >= fcc.size())
				defaultIndex = fcc.size()-1;
			defaultFC = (defaultIndex >= 0)? (SingleFeatureCollection)fcc.get(defaultIndex): null;
			fireDefaultChangeEvent(defaultFC);
			if (defaultIndex >= 0)
				fireTableRowsUpdated(defaultIndex, defaultIndex);
		}
		
		return result;
	}
	
	/**
	 * Returns the entire collection of FeatureCollections which is a part
	 * of this FileTableModel.
	 * 
	 * @return The array of FeatureCollections that is a part of this FileTableModel. 
	 */
	public FeatureCollection[] getAll(){
		return (FeatureCollection[])fcc.toArray(new FeatureCollection[0]);
	}

	/**
	 * Realization of FeatureEvent interface. Listens to changes made to
	 * the FeatureCollections that make rows of this table. Changes the
	 * modification status of a Feature on receiving modification
	 * FeatureEvents.
	 * <strong>CAUTION:</strong>FeaturEvents must be from a 
	 * SingleFeatureCollection.
	 */
	public void receive(FeatureEvent e) {
		if (history.versionChanging())
			return;

		Set modified = new HashSet();
		Feature feat;

		switch(e.type){
		case FeatureEvent.ADD_FEATURE:
			if (e.source != null)
				modified.add(e.source);
			else
				log.println("Orphan AddFeature Event encountered.");
			break;
		case FeatureEvent.REMOVE_FEATURE:
			if (e.source != null)
				modified.add(e.source);
			else
				log.println("Orphan RemoveFeature Event encountered.");
			break;
		case FeatureEvent.CHANGE_FEATURE:
			for(Iterator li = e.features.iterator(); li.hasNext(); ){
				feat = (Feature)li.next();
				if (modified.contains(feat.getOwner()))
					continue;
				if (!(e.fields.size() == 1 && e.fields.contains(Field.FIELD_SELECTED)))
					modified.add(feat.getOwner());
			}
			break;
		case FeatureEvent.ADD_FIELD:
		case FeatureEvent.REMOVE_FIELD:
			if (e.source != null)
				modified.add(e.source);
			else
				log.println("Orphan Add/Remove Field Event encountered.");
			break;
		default:
			log.aprintln("Invalid event type "+e.type+" encountered. Ignoring!");
		}

		// Notify the table model of the modification by firing an updated event
		// on the view (JTable).
		for(Iterator li = modified.iterator(); li.hasNext(); ){
			SingleFeatureCollection fc = (SingleFeatureCollection)li.next();
			int idx = fcc.indexOf(fc);
			if (idx > -1) {
				setTouched(idx, true);
			}
			else
				log.println("Orphan FeatureCollection encountered: "+fc);
		}

		// TODO Propagate event forward as a FileTable event 
	}

	/**
	 * Returns the FeatureCollection at the specified index.
	 * 
	 * @param index Index of the FeatureCollection requested.
	 * @return The FeatureCollection at the given index or null if
	 *         the given index is out of bounds.
	 */
	public SingleFeatureCollection get(int index){
		if (index < 0 || index >= fcc.size())
			return null;
		return (SingleFeatureCollection)fcc.get(index);
	}
	
	/**
	 * Returns whether the FeatureCollection at the given index
	 * is marked modified.
	 * 
	 * @param index Index of the FeatureCollection requested.
	 * @return true if the FeatureCollection has been marked as
	 *         modified, false if it is either not marked as
	 *         modified or if the given index is out of bounds.
	 */
	public boolean getTouched(int index){
		if (index < 0 || index >= fcc.size())
			return false;
		
		return ((Boolean)touched.get(fcc.get(index))).booleanValue();
	}
	
	/**
	 * Similar as {@linkplain #getTouched(int)} except that this method
	 * works on FeatureCollections as compared to their indices.
	 * 
	 * @param fc FeatureCollection to get the modification status of.
	 * @return Modification status of the FeatureCollection if it exists
	 *         in the TableModel or false otherwise.
	 */
	public boolean getTouched(FeatureCollection fc){
		return getTouched(fcc.indexOf(fc));
	}

	/**
	 * Sets the modification status of the FeatureCollection at the
	 * specified index.
	 * 
	 * @param index Index of the FeatureCollection to operate upon.
	 * @param flag Modification status to set.
	 */
	public void setTouched(int index, boolean flag){
		if (index < 0 || index >= fcc.size())
			return;

		SingleFeatureCollection fc = (SingleFeatureCollection)fcc.get(index); 
		boolean oldFlag = getTouched (fc);
		touched.put(fc, flag? Boolean.TRUE: Boolean.FALSE);
		fireTableRowsUpdated(index, index);

		if (history != null)
			history.addChange(this, new TouchedChange(fc, oldFlag, flag));
	}

	/**
	 * Sets the modification status of the given FeatureCollection.
	 * @param fc FeatureCollection to operate upon.
	 * @param flag Modification status to set.
	 */
	public void setTouched(FeatureCollection fc, boolean flag){
		setTouched(fcc.indexOf(fc), flag);
	}

	/**
	 * Returns the current default FeatureCollection, which will
	 * receive all the add Feature requests.
	 */
	public SingleFeatureCollection getDefaultFeatureCollection(){
		return defaultFC;
	}

	/**
	 * Returns the index of the default FeatureCollection.
	 * @return index of the defaultFeatureCollection or -1 if there
	 *         is no default set.
	 */
	public int getDefaultFeatureCollectionIndex(){
		return fcc.indexOf(defaultFC);
	}

	/**
	 * Set the default FeatureCollection. The specified FeatureCollection
	 * must already be a part of the collection of FeatureCollection
	 * stored in the FileTableModel. If that is not the case, the
	 * default is unset.
	 * 
	 * @param fc FeatureCollection to set as default.
	 */
	public void setDefaultFeatureCollection(SingleFeatureCollection fc){
		setDefaultFeatureCollection(fcc.indexOf(fc));
	}
	
	/**
	 * Same as {@linkplain #setDefaultFeatureCollection(FeatureCollection)}
	 * with the exception that this call takes the index of the FeatureCollection
	 * as compared to the FeatureCollection itself.
	 * 
	 * @param index Index of the FeatureCollection to set as the default.
	 */
	public void setDefaultFeatureCollection(int index){
		SingleFeatureCollection oldDefault = defaultFC;
		
		int defaultIndex = fcc.indexOf(defaultFC);
		if (defaultIndex >= 0)
			fireTableRowsUpdated(defaultIndex, defaultIndex);
		
		defaultFC = (index >= 0 && index < fcc.size())? (SingleFeatureCollection)fcc.get(index): null;  
		defaultIndex = fcc.indexOf(defaultFC);
		
		fireDefaultChangeEvent(defaultFC);
		if (defaultIndex >= 0)
			fireTableRowsUpdated(defaultIndex, defaultIndex);
		
		if (history != null)
			history.addChange(this, new DefaultChange(oldDefault, defaultFC));
	}
	
	/**
	 * Fires default FeatureCollection change event.
	 * 
	 * @param defaultFC New default FeatureCollection.
	 */
	public void fireDefaultChangeEvent(SingleFeatureCollection fc){
		DefaultChangedEvent e = null;
		
		for(Iterator i=listeners.iterator(); i.hasNext(); ){
			DefaultChangedListener l = (DefaultChangedListener)i.next();
			if (e == null)
				e = new DefaultChangedEvent(fc, fcc.indexOf(fc));
			l.defaultChanged(e);
		}
	}
	
	/**
	 * Adds a DefaultChangedEvent listener.
	 */
	public void addDefaultChangedListener(DefaultChangedListener l){
		listeners.add(l);
	}
	
	/**
	 * Removes a DefaultChangedEvent listener.
	 * 
	 * @return true if the removal was successful.
	 */
	public boolean removeDefaultChangedListener(DefaultChangedListener l){
		return listeners.remove(l);
	}
	
	/**
	 * Returns a list of all DefaultChangedEvent listeners registered
	 * with this FileTableModel.
	 * 
	 * @return A list of all DefaultChangedEvent listeners.
	 */
	public List getDefaultChangedListeners(){
		return Collections.unmodifiableList(listeners);
	}

	/**
	 * Set the history to log changes to
	 */
	 public void setHistory (History history) {
		 this.history = history;
	 }

	/**
	 * Undo the given change
	 */
	public void undo (Object change) {
		if (change instanceof AddChange) {
			remove (((AddChange)change).fc);
			return;
		}
		if (change instanceof RemoveChange) {
			RemoveChange ch = (RemoveChange) change;
			add (ch.fc);
			setTouched (ch.fc, ch.dirty);
			return;
		}
		if (change instanceof TouchedChange) {
			TouchedChange c = (TouchedChange)change;
			setTouched(c.fc, c.before);
			return;
		}
		if (change instanceof DefaultChange) {
			setDefaultFeatureCollection(((DefaultChange)change).oldDefault);
			return;
		}
		log.println("Unhandled object "+(change==null? "null": change.getClass().getName())+" encountered.");
	}

	/**
	 * Redo the given change
	 */
	public void redo (Object change) {
		if (change instanceof AddChange) {
			AddChange ch = (AddChange) change;
			add (ch.fc);
			return;
		}
		if (change instanceof RemoveChange) {
			remove (((RemoveChange)change).fc);
			return;
		}
		if (change instanceof TouchedChange) {
			TouchedChange c = (TouchedChange)change;
			setTouched(c.fc, c.after);
			return;
		}
		if (change instanceof DefaultChange) {
			setDefaultFeatureCollection(((DefaultChange)change).newDefault);
			return;
		}
		log.println("Unhandled object "+(change==null? "null": change.getClass().getName())+" encountered.");
	}

	/*
	 * Change container objects to send to a History log. 
	 */

	class AddChange {
		public final SingleFeatureCollection fc;
		public AddChange (SingleFeatureCollection fc) {
			this.fc = fc;
		}
	}

	class RemoveChange {
		public final SingleFeatureCollection fc;
		public final boolean dirty;
		public RemoveChange (SingleFeatureCollection fc) {
			this.fc = fc;
			this.dirty = getTouched (fc);
		}
	}

	public class DefaultChange {
		public final SingleFeatureCollection oldDefault;
		public final SingleFeatureCollection newDefault;

		public DefaultChange(SingleFeatureCollection oldDefault, SingleFeatureCollection newDefault){
			this.oldDefault = oldDefault;
			this.newDefault = newDefault;
		}
	}

	public class TouchedChange {
		public final SingleFeatureCollection fc;
		public final boolean before;
		public final boolean after;

		public TouchedChange(SingleFeatureCollection fc, boolean before, boolean after){
			this.fc = fc;
			this.before = before;
			this.after = after;
		}
	}
}
