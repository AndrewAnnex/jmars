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


package edu.asu.jmars.places;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.Util;

/**
 * Creates a menu structure like this:
 * 
 * <pre>
 * JMenu: Places
 *   JMenu: Settings
 *     JCheckBoxMenuItem: Restore PPD
 *     JCheckBoxMenuItem: Restore map positions
 *     JCheckBoxMenuItem: Restore projection
 *   JMenuItem: New Place...
 *   JMenu: labelN
 *     JMenuItem: placeN
 *     ...
 *   ...
 * </pre>
 * 
 * Where each <code>placeN</code> has a context menu like this:
 * 
 * <pre>
 * Edit
 * Delete
 * </pre>
 * 
 * The top of the menu is constant. Edits that change the structure of the menu
 * cause the top level menu's children to be removed and the menu contents to be
 * rebuilt, but the top level menu remains fixed so it doesn't impact any higher
 * up portion of the user interface.
 */
public class PlacesMenu {
	private final PlaceStore store;
	private final JMenu top = new JMenu("Places");
	/**
	 * edit dialog, created when first needed so <code>top</code> can be added
	 * to a component hierarchy first
	 */
	private PlaceEditDialog dialog;
	
	private final PlaceSettingAdapter rescale = new PlaceSettingAdapter(
		"places.rescale", false, "Restore PPD Scale");
	private final PlaceSettingAdapter reproject = new PlaceSettingAdapter(
			"places.reproject", true, "Restore Projection");
	private final PlaceSettingAdapter[] settings = new PlaceSettingAdapter[] {
		rescale, reproject
	};
	
	/** Creates a new top level menu for this place store. */
	public PlacesMenu(PlaceStore store) {
		this.store = store;
		buildMenus();
	}
	
	/** Returns the top level menu. Will never change. */
	public JMenuItem getMenu() {
		return top;
	}
	
	/**
	 * Replaces the menu structure inside {@link #top} with the current state of
	 * the place store
	 */
	private void buildMenus() {
		top.removeAll();
		
		JMenuItem newPlace = new JMenuItem("New Place...");
		newPlace.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.CTRL_MASK));
		newPlace.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				edit(new Place());
			}
		});
		top.add(newPlace);
		
		JMenu settingsMenu = new JMenu("Settings");
		for (final PlaceSettingAdapter adapter: settings) {
			final JCheckBoxMenuItem cb = new JCheckBoxMenuItem(adapter.label);
			cb.setSelected(adapter.getValue());
			cb.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					adapter.setValue(cb.isSelected());
				}
			});
			settingsMenu.add(cb);
		}
		top.add(settingsMenu);
		
		if (store.size() > 0) {
			top.addSeparator();
		}
		
		// group places by label
		Map<String,List<Place>> labelGroups = new TreeMap<String,List<Place>>();
		for (Place p: store) {
			for (String label: p.getLabels()) {
				List<Place> labelPlaces = labelGroups.get(label);
				if (labelPlaces == null) {
					labelGroups.put(label, labelPlaces = new ArrayList<Place>());
				}
				labelPlaces.add(p);
			}
		}
		
		// add each label, leaving the contents in original order so newer
		// places are at the bottom
		for (String label: labelGroups.keySet()) {
			JMenu labelMenu = new JMenu(label);
			for (final Place p: labelGroups.get(label)) {
				labelMenu.add(createPlaceMenu(p));
			}
			top.add(labelMenu);
		}
		
		// add items with no labels at the end, also in normal order so
		// newer items are the bottom
		for (Place p: store) {
			if (p.getLabels().size() == 0) {
				top.add(createPlaceMenu(p));
			}
		}
		
		top.invalidate();
		top.validate();
		top.repaint();
	}
	
	/** Creates a menu for this place, complete with tooltip and context menu */
	private JMenuItem createPlaceMenu(final Place p) {
		final JMenuItem pMenu = new JMenuItem(p.getName());
		pMenu.setToolTipText(MessageFormat.format(
			"<html><body>Location: {0}" +
			"<br>Projection: {1}" +
			"<br>PPD: {2}<br>Map Offsets: {3}" +
			"<br><br>Click to go to this place<br>Open context menu for other options</body></html>",
			toString(p.getLonLat()),
			toString(p.getProjCenterLonLat()),
			p.getPpd(),
			p.getMapOffsets().size()));
		
		final JPopupMenu popup = new JPopupMenu("Options for " + p.getName());
		JMenuItem editMenu = new JMenuItem("Edit");
		editMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				edit(p);
			}
		});
		popup.add(editMenu);
		JMenuItem delMenu = new JMenuItem("Delete");
		delMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				delete(p);
			}
		});
		popup.add(delMenu);
		
		pMenu.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if ((e.getModifiers() & ActionEvent.META_MASK) == 0) {
					view(p);
				}
			}
		});
		
		pMenu.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				handle(e);
			}
			public void mouseReleased(MouseEvent e) {
				handle(e);
			}
			public void handle(MouseEvent e) {
				if (e.isPopupTrigger()) {
					e = SwingUtilities.convertMouseEvent(Main.testDriver, e, null);
					popup.show(Main.testDriver, e.getX(), e.getY());
				}
			}
		});
		
		return pMenu;
	}
	
	private static String toString(Point2D p) {
		if (p == null) {
			return "<none>";
		} else {
			return MessageFormat.format("{0,number,#.###}E, {1,number,#.###}N", p.getX(), p.getY());
		}
	}
	
	private void view(Place place) {
		Place.gotoPlace(place, reproject.getValue(), rescale.getValue(), reproject.getValue());
	}
	
	/**
	 * Edits the given place. Changes are only saved if the Save button is
	 * pressed in the dialog. Rebuilds the menu after the dialog closes.
	 */
	private void edit(Place place) {
		if (dialog == null) {
			dialog = new PlaceEditDialog(Main.mainFrame, store);
			Point loc = new Point(
				(Main.mainFrame.getWidth() - dialog.getWidth()) / 2,
				(Main.mainFrame.getHeight() - dialog.getHeight()) / 2);
			dialog.setLocation(loc);
		}
		dialog.setPlace(place);
		dialog.pack();
		dialog.setVisible(true);
		buildMenus();
	}
	
	/** Deletes the given place and rebuilds the menu */
	private void delete(Place place) {
		if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
				Main.testDriver,
				"Are you sure you want to remove the place named " + place.getName(),
				"Confirm removal",
				JOptionPane.YES_NO_OPTION)) {
			try {
				store.remove(place);
			} catch (Exception ex) {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(
					top,
					"Error occurred removing " + place + ":\n\n" +
						Util.foldText(ex.getMessage(), 60, "\n"),
					"Error removing place",
					JOptionPane.ERROR_MESSAGE);
			} finally {
				buildMenus();
			}
		}
	}
	
	/** Ties together a jmars.config key, default value, and a user interface label */
	private static class PlaceSettingAdapter {
		public final String key;
		public final String label;
		private boolean defaultValue;
		public PlaceSettingAdapter(String key, boolean defaultValue, String label) {
			this.key = key;
			this.defaultValue = defaultValue;
			this.label = label;
		}
		public boolean getValue() {
			return Config.get(key, defaultValue);
		}
		public void setValue(boolean value) {
			if (getValue() != value) {
				Config.set(key, value);
			}
		}
	}
}