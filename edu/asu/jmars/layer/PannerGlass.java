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
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.text.*;
import javax.swing.*;
import javax.swing.event.*;

public final class PannerGlass extends BaseGlass {
	PannerGlass(final LViewManager pannVMan, final LViewManager mainVMan) {
		super(pannVMan, mainVMan);
	}

	MouseInputListener createMouseHandler() {
		final JPopupMenu popup = new JPopupMenu();
		JMenu sub;
		JMenuItem menuItem;

		sub = new JMenu("Zoom");
		ButtonGroup group = new ButtonGroup();
		for (int i = 0; i < LocationManager.INITIAL_MAX_ZOOM_POWER; i++) {
			final int zoom = 1 << (i * LocationManager.ZOOM_MULTIPLIER);
			menuItem = new JRadioButtonMenuItem(new AbstractAction(zoom
					+ " Pix/Deg") {
				public void actionPerformed(ActionEvent e) {
					myVMan.setMagnify(zoom);
					myVMan.setLocationAndZoom(myVMan.anchorPoint, zoom);
				}
			});
			group.add(menuItem);
			sub.add(menuItem);
			if (zoom == myVMan.magnify)
				menuItem.setSelected(true);
		}
		popup.add(sub);

		MouseInputListener mouseHandler = new MouseInputAdapter() {
			boolean menuVisible = false;

			Point mouseDown = null;

			public void mouseClicked(MouseEvent e) {
				if (!menuVisible
				&& SwingUtilities.isLeftMouseButton(e)
				&& e.getClickCount() == 1) {
					Point p = e.getPoint();
					Point2D world = myVMan.getProj().screen.toWorld(p);
					Main.testDriver.offsetToWorld(world);
				}
				proxy(e);
			}

			public void mousePressed(MouseEvent e) {
				menuVisible = popup.isVisible();
				if (SwingUtilities.isRightMouseButton(e))
					popup.show(PannerGlass.this, e.getX(), e.getY());
				else
					mouseDown = e.getPoint();
				if (SwingUtilities.isMiddleMouseButton(e))
					fastPan.beg();
				if (! menuVisible && SwingUtilities.isLeftMouseButton(e))
					proxy(e);
			}

			public void mouseReleased(MouseEvent e) {
				if (SwingUtilities.isMiddleMouseButton(e)) {
					fastPan.end(e.getX(), e.getY());
					return;
				}

				if (mouseLast != null) {
					drawLine(mouseDown.x, mouseDown.y, mouseLast.x, mouseLast.y);
					mouseLast = null;
				}
				mouseDown = null;
				if (! menuVisible && SwingUtilities.isLeftMouseButton(e))
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

			Point mouseLast = null;

			public void mouseDragged(MouseEvent e) {
				// Don't catch menu popup drags
				if (mouseDown == null)
					return;

				if (SwingUtilities.isMiddleMouseButton(e)) {
					mouseDraggedMiddle(e);
					return;
				}

				Point mouseCurr = e.getPoint();

				if (mouseLast != null)
					drawLine(mouseDown.x, mouseDown.y, mouseLast.x, mouseLast.y);
				drawLine(mouseDown.x, mouseDown.y, mouseCurr.x, mouseCurr.y);

				Point2D downW = myVMan.getProj().screen.toWorld(mouseDown);
				Point2D currW = myVMan.getProj().screen.toWorld(mouseCurr);

				Point2D downS = Main.PO.convWorldToSpatial(downW);
				Point2D currS = Main.PO.convWorldToSpatial(currW);
				double angDistance = myVMan.getProj().spatial.distance(downS, currS);
				double linDistance = angDistance * 3390.0 * 2 * Math.PI / 360.0;

				// JMARS west lon => USER east lon
				currS.setLocation(360 - currS.getX(), currS.getY());

				Main.setStatus(formatCoord(currS, new DecimalFormat("0.00"),
						"E ", "N")
						+ "\tdist = "
						+ f.format(angDistance)
						+ "deg = "
						+ f.format(linDistance) + "km");

				mouseLast = mouseCurr;

				if (! menuVisible && SwingUtilities.isLeftMouseButton(e))
					proxy(e);
			}

			public void mouseDraggedMiddle(MouseEvent e) {
				mouseLast = e.getPoint();
				fastPan.panTo(mouseLast.x - mouseDown.x, mouseLast.y
						- mouseDown.y);
			}
		};
		return mouseHandler;
	}

	public void paintComponent(Graphics g) {
		if (fastPan.paintComponent(g))
			return;

		Graphics2D g2 = (Graphics2D) g;

		g2.transform(myVMan.getProj().getWorldToScreen());

		g2.setStroke(new BasicStroke(0));
		g2.setColor(Color.red);
		g2.draw(mainVMan.getProj().getWorldWindow());
	}

	/**
	 ** Draws a screen-coordinate line, using a spatial graphics
	 ** context (in time mode, this results in a nicely-curved
	 ** geodesic).
	 **/
	private void drawLine(int x1, int y1, int x2, int y2) {
		MultiProjection proj = myVMan.getProj();

		Graphics2D g2 = (Graphics2D) PannerGlass.this.getGraphics();
		g2.transform(proj.getWorldToScreen());
		g2 = myVMan.wrapWorldGraphics(g2);
		g2.setXORMode(Color.gray);
		g2.setStroke(new BasicStroke(0));
		Graphics2D g2s = proj.createSpatialGraphics(g2);

		Point2D down = proj.screen.toSpatial(x1, y1);
		Point2D curr = proj.screen.toSpatial(x2, y2);
		g2s.draw(new Line2D.Double(down, curr));
		g2s.dispose();
	}
}
