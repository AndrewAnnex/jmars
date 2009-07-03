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


package edu.asu.jmars.layer;

import edu.asu.jmars.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

public final class MainGlass extends BaseGlass {
	private static DebugLog log = DebugLog.instance();

	protected void transferMenuItems(JPopupMenu popup,
			Component[] contextItems, boolean addSepBefore) {

		if (contextItems != null && contextItems.length > 0) {
			if (addSepBefore)
				popup.add(new JSeparator());

			for (int i = 0; i < contextItems.length; i++) {
				if (contextItems[i] != null)
					popup.add(contextItems[i]);
				else
					log.aprintln("Error: null context menu item.");
			}

			if (!addSepBefore)
				popup.add(new JSeparator());
		}
	}

	protected void transferInactiveMenuItemsTop(JPopupMenu popup,
			Point2D worldPt) {
		//now check for inactive layers
		Iterator iter = mainVMan.viewList.iterator();
		while (iter.hasNext()) {

			Layer.LView view = (Layer.LView) iter.next();

			//we have already handled the active view
			if (mainVMan.getActiveLView().equals(view))
				continue;

			if (view != null && view.isVisible() && view.getChild() != null) {
				Component[] contextItems = view.getContextMenuTop(worldPt);
				transferMenuItems(popup, contextItems, false);
			}
		}
	}

	protected void transferInactiveMenuItems(JPopupMenu popup, Point2D worldPt) {
		//now check for inactive layers
		Iterator iter = mainVMan.viewList.iterator();
		while (iter.hasNext()) {
			Layer.LView view = (Layer.LView) iter.next();
			//we have already handled the active view
			if (mainVMan.getActiveLView().equals(view))
				continue;

			if (view != null && view.isVisible() && view.getChild() != null) {
				Component[] contextItems = view.getContextMenu(worldPt);
				transferMenuItems(popup, contextItems, true);
			}
		}
	}

	public JPopupMenu getPopupMenu(final MouseEvent origEvent,
			final Point2D worldPt) {
		// Set up main menu area
		final JPopupMenu popup = new JPopupMenu();

		Layer.LView view = mainVMan.getActiveLView();

		// Set up top context menu area, if there is one
		if (view != null) {
			Component[] contextItems = view.getContextMenuTop(worldPt);
			transferMenuItems(popup, contextItems, false);
		}

		//look for any other views that might have something to add
		transferInactiveMenuItemsTop(popup, worldPt);

		JMenu sub;
		JMenuItem menuItem;

		menuItem = new JMenuItem("Recenter window");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Point2D worldPt2 = mainVMan.getProj().screen.toWorld(origEvent
						.getPoint());
				mainVMan.locMgr.setLocation(worldPt2, true);
			}
		});
		popup.add(menuItem);

		sub = new JMenu("Zoom & recenter");
		ButtonGroup group = new ButtonGroup();
		for (int i = 0; i < LocationManager.INITIAL_MAX_ZOOM_POWER; i++) {
			final int zoom = 1 << (i * LocationManager.ZOOM_MULTIPLIER);
			menuItem = new JRadioButtonMenuItem(new AbstractAction(zoom
					+ " Pix/Deg") {
				public void actionPerformed(ActionEvent e) {
					mainVMan.locMgr.setLocation(worldPt, false);
					mainVMan.locMgr.setZoom(zoom); // propagates
				}
			});
			group.add(menuItem);
			sub.add(menuItem);
			if (zoom == mainVMan.magnify)
				menuItem.setSelected(true);
		}
		popup.add(sub);

		// Set up context menu area, if there is one at the bottom of the menu
		if (view != null) {
			Component[] contextItems = view.getContextMenu(worldPt);
			transferMenuItems(popup, contextItems, true);
		}

		//look for any other views that might have something to add
		transferInactiveMenuItems(popup, worldPt);

		// Set up any ruler menus
		if (view != null) {
			Component[] contextItems = view.getRulerMenu();
			transferMenuItems(popup, contextItems, true);
		}

		return popup;
	}

	MainGlass(final LViewManager mainVMan) {
		super(mainVMan, mainVMan);
	}

	MouseInputListener createMouseHandler() {
		return new GlassMouseListener();
	}

	public void paintComponent(Graphics g) {
		fastPan.paintComponent(g);
	}

	private class GlassMouseListener implements MouseInputListener {
		JPopupMenu popup = null;

		// All mouse events sequences start with a
		// mousePressed... in order to disable clicks that
		// occur while the popup menu is open, we: 1) record
		// whether the menu was visible during mousePressed
		// and 2) kill any successive events if menuWasVisible
		// is true during them. This works since no mouse
		// event handlers are called unless mousePressed has
		// been called first.
		boolean menuWasVisible = false;

		Point mouseDown;

		public void mouseClicked(MouseEvent e) {
			if (menuWasVisible)
				return;
			if (!SwingUtilities.isRightMouseButton(e))
				proxy(e);

		}

		public void mousePressed(MouseEvent e) {
			mouseDown = e.getPoint();
			menuWasVisible = (popup != null && popup.isVisible());
			if (SwingUtilities.isRightMouseButton(e)) {
				MouseEvent e1 = new MouseEvent((Component) e.getSource(), e
						.getID(), e.getWhen(), e.getModifiers(), e.getX(), e
						.getY(), e.getClickCount(), SwingUtilities
						.isRightMouseButton(e));
				double worldX = mainVMan.getProj().screen.toWorld(
						e1.getPoint()).getX();
				int scale = 360 * mainVMan.magnify;
				e1.translatePoint(-(int) Math.floor(worldX / 360) * scale, 0);

				popup = getPopupMenu(e, mainVMan.getProj().screen.toWorld(e1.getPoint()));
				popup.show(MainGlass.this, e.getX(), e.getY());
				log.println("Menu shown");
			} else if (SwingUtilities.isMiddleMouseButton(e))
				fastPan.beg();
			else if (!menuWasVisible)
				proxy(e);
		}

		public void mouseReleased(MouseEvent e) {
			if (SwingUtilities.isMiddleMouseButton(e)) {
				fastPan.end(e.getX(), e.getY());
				return;
			}

			if (menuWasVisible)
				return;
			if (!SwingUtilities.isRightMouseButton(e))
				proxy(e);
		}

		public void mouseEntered(MouseEvent e) {
			updateLocation(e.getPoint());
			proxy(e);
		}

		public void mouseExited(MouseEvent e) {
			Main.setStatus(null);
			proxy(e);
		}

		DecimalFormat f = new DecimalFormat("0.00");

		public void mouseMoved(MouseEvent e) {
			updateLocation(e.getPoint());
			proxy(e);
		}

		public void mouseDragged(MouseEvent e) {
			if (SwingUtilities.isMiddleMouseButton(e)) {
				Point mouseLast = e.getPoint();
				fastPan.panTo(mouseLast.x - mouseDown.x, mouseLast.y
						- mouseDown.y);
				return;
			}

			if (menuWasVisible)
				return;
			updateLocation(e.getPoint());
			if (!SwingUtilities.isRightMouseButton(e))
				proxy(e);
		}
	}
}
