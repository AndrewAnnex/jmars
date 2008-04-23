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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import edu.asu.jmars.Main;
import edu.asu.jmars.swing.STable;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.stable.Sorter.Listener;

/**
 * <p>Provides STable with persistent view settings outside of the JMARS session
 * file mechanism, and can connect listeners to the table to keep the settings
 * up to date.
 * 
 * <p>Each STable that should be persisted will need its own 'id'. The XML for the
 * table settings will be stored in "~/jmars/tableView<ID>.xml".
 * 
 * <p>When an STable is initially created, calling updateTable() will set the XML
 * settings into the table.
 * 
 * <p>Once connect is called, altering the column order, sort order, sort
 * directions, or show/hide changes will update the XML automatically.
 */
public class STableViewPersister {
	private static DebugLog log = DebugLog.instance();

	// serialization mechanism
	private XStream xstream = new XStream(new DomDriver());

	// used to avoid unecessary saves while still catching them
	private Timer timer = new Timer();

	// the table to save to
	private STable table;

	// the basename of the XML file to load from and save to
	private String filename;

	// listen to table model structure and table model change events
	private TableModelListener tableModelListener = new TableModelListener() {
		public void tableChanged(TableModelEvent e) {
			if (e.getFirstRow() == TableModelEvent.HEADER_ROW
					|| e.getLastRow() == Integer.MAX_VALUE) {
				updateConfig();
			}
		}
	};

	// listen to sort change events
	private Listener sorterListener = new Listener() {
		public void sortChangePre() {
		}

		public void sortChanged() {
			updateConfig();
		}
	};

	// listen to column show/hide/move events
	private TableColumnModelListener columnListener = new TableColumnModelListener() {
		public void columnAdded(TableColumnModelEvent e) {
			updateConfig();
		}

		public void columnMoved(TableColumnModelEvent e) {
			updateConfig();
		}

		public void columnRemoved(TableColumnModelEvent e) {
			updateConfig();
		}

		public void columnSelectionChanged(ListSelectionEvent e) {
		}

		public void columnMarginChanged(ChangeEvent e) {
			updateConfig();
		}
	};

	/**
	 * Construct an instance of the table adapter for the given table and table
	 * ID. This instance can be used to serialize table view settinsg to
	 * 'tableView<ID>.xml', and to save settings changes back to it after
	 * connect() is called.
	 */
	public STableViewPersister(STable table, String configID) {
		this.table = table;
		this.filename = "tableView" + configID + ".xml";
	}

	/**
	 * Set the table's view settings to the last-saved state in the XML file.
	 * 
	 * If anything is wrong with deserialization, this method will catch the
	 * exception, log a message, and move on.
	 */
	public void updateTable() {
		log.println("Loading table settings from " + filename);
		try {
			String fullpath = Main.getJMarsPath() + filename;
			FileInputStream fis = new FileInputStream(fullpath);
			Map settings = (Map) xstream.fromXML(fis);
			table.setViewSettings(settings);
		} catch (FileNotFoundException e) {
			// the first time you use this feature, there won't be a file 
			log.println("Unable to find table view settings file " + filename);
		} catch (Exception e) {
			log.aprintln("Unable to read table view settings from " + filename);
			log.println(e);
		}
	}

	private TimerTask saveTask;

	class SaveTask extends TimerTask {
		public void run() {
			log.println("Saving table settings to " + filename);
			try {
				String fullpath = Main.getJMarsPath() + filename;
				FileOutputStream fos = new FileOutputStream(fullpath);
				xstream.toXML(table.getViewSettings(), fos);
			} catch (FileNotFoundException e) {
				log.println("Unable to update table view settings in "
					+ filename);
			}
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					saveTask = null;
				}
			});
		}
	};

	/**
	 * Updates with settings in the column model.
	 */
	public void updateConfig() {
		// this operation is slightly expensive and we don't need to do it
		// on every change, so we wait a bit to compress rapid updates
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (saveTask == null) {
					timer.schedule(saveTask = new SaveTask(), 1000);
				}
			}
		});
	}

	/**
	 * Connects settings change listeners to the table so jmars.config is
	 * updated with changes in column width, visibility, and order.
	 */
	public void connect() {
		table.getModel().addTableModelListener(tableModelListener);
		table.getSorter().addListener(sorterListener);
		table.getColumnModel().addColumnModelListener(columnListener);
	}

	/**
	 * Disconnects the settings change listeners from the table.
	 */
	public void disconnect() {
		table.getModel().removeTableModelListener(tableModelListener);
		table.getSorter().removeListener(sorterListener);
		table.getColumnModel().removeColumnModelListener(columnListener);
	}
}
