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
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import javax.swing.*;
import javax.swing.event.*;

public abstract class BaseGlass extends JPanel {
	private static final DebugLog log = DebugLog.instance();
	final LViewManager myVMan;
	final LViewManager mainVMan;
	private static final DecimalFormat decF = new DecimalFormat("0.00");

	BaseGlass(LViewManager myVMan, LViewManager mainVMan) {
		this.myVMan = myVMan;
		this.mainVMan = mainVMan;
		setOpaque(false);
		setToolTipText("");

		MouseInputListener mouseHandler = createMouseHandler();
		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
	}

	abstract MouseInputListener createMouseHandler();

	void proxy(MouseEvent e) {
		//use duplicate so as not to mess with the original object
		//- which is passed around.
		MouseEvent e1 = new WrappedMouseEvent(e);

		double worldX = myVMan.getProj().screen.toWorld(e1.getPoint()).getX();
		int scale = 360 * myVMan.magnify;
		e1.translatePoint(-(int) Math.floor(worldX / 360) * scale, 0);
		Layer.LView view = myVMan.getActiveLView();
		if (myVMan != mainVMan)
			view = view.getChild();
		if (view != null && view.isVisible() && view.handleMouseEvent(e))
			view.processEvent(e1);
	}

	public static String formatCoord(Point2D coord, DecimalFormat f,
			String xLabel, String yLabel) {
		StringBuffer buff = new StringBuffer(20);

		double x = coord.getX();
		double xa = Math.abs(x);
		if (x > 0)
			buff.append("  ");
		if (xa < 100)
			buff.append("  ");
		if (xa < 10)
			buff.append("  ");
		buff.append(f.format(x));
		buff.append(xLabel);

		double y = coord.getY();
		double ya = Math.abs(y);
		if (y > 0)
			buff.append("  ");
		if (ya < 100)
			buff.append("  ");
		if (ya < 10)
			buff.append("  ");
		buff.append(f.format(y));
		buff.append(yLabel);

		return buff.toString();
	}

	String formatCoordTime(Point2D coord) {
		StringBuffer buff = new StringBuffer(20);

		buff.append(TimeField.etToDefault(coord.getX()));
		buff.append(" ");

		double y = coord.getY();
		double ya = Math.abs(y);
		if (y > 0)
			buff.append("  ");
		if (ya < 100)
			buff.append("  ");
		if (ya < 10)
			buff.append("  ");
		buff.append(decF.format(y));
		buff.append("deg\t");

		return buff.toString();
	}

	private static final boolean FASTPAN_DEBUG = Config.get("fastpan.debug",
			false);
	private static final int FASTPAN_MSECS = Config.get("fastpan.msecs", 0);
	private static final int FASTPAN_MSECS_INIT = Config.get(
			"fastpan.msecs.init", 0);
	FastPan fastPan = new FastPan();

	class FastPan {
		private BufferedImage image;
		private BufferedImage imageSaved;
		private int x = 0;
		private int y = 0;
		private int id = 0;
		private boolean disableNormalPainting = false;
		Timer timer;
		TimerTask pendingTask;

		synchronized void beg() {
			setOpaque(false);
			disableNormalPainting = true;
			x = 0;
			y = 0;
			int w = getWidth();
			int h = getHeight();
			if (imageSaved == null || imageSaved.getWidth() != w
					|| imageSaved.getHeight() != h) {
				log.println("RECREATING IMAGE");
				imageSaved = Util.newBufferedImageOpaque(w, h);
			}
			image = imageSaved;
			Graphics g = image.getGraphics();
			g.setColor(getBackground());
			g.fillRect(0, 0, w, h);
			myVMan.print(g);
			if (FASTPAN_DEBUG) {
				g.setColor(new Color(255, 0, 0, 127));
				g.fillRect(0, 0, getWidth(), getHeight());
			}
			g.dispose();
			disableNormalPainting = false;
			setOpaque(true);
			++id;
		}

		synchronized void end(int xOff, int yOff) {
			if (x == 0 && y == 0) {
				x = (getWidth() + 1) / 2 - xOff;
				y = (getHeight() + 1) / 2 - yOff;
			}

			if (FASTPAN_MSECS != 0) {
				if (pendingTask != null)
					pendingTask.cancel();
				if (timer == null)
					timer = new Timer(true);
				timer.schedule(pendingTask = new TaskShowFake(++id),
						FASTPAN_MSECS_INIT);
			}

			Point p = new Point((getWidth() + 1) / 2 - x, (getHeight() + 1) / 2 - y);
			Point2D pt = myVMan.getProj().screen.toWorld(p);
			// Have the LViews offset their data by the offset determined by fastPan.end()
			Main.testDriver.offsetToWorld(pt);
		}

		private class TaskShowFake extends TimerTask {
			private int oldID;

			TaskShowFake(int oldID) {
				this.oldID = oldID;
			}

			public void run() {
				synchronized (FastPan.this) {
					if (id == oldID) {
						setOpaque(false);
						repaint();
						timer.schedule(pendingTask = new TaskHideFake(oldID),
								FASTPAN_MSECS);
					}
				}
			}
		}

		private class TaskHideFake extends TimerTask {
			private int oldID;

			TaskHideFake(int oldID) {
				this.oldID = oldID;
			}

			public void run() {
				synchronized (FastPan.this) {
					if (id == oldID) {
						image = null;
						repaint();
					}
				}
			}
		}

		synchronized void panTo(int xOff, int yOff) {
			x = xOff;
			y = yOff;
			repaint();
		}

		synchronized boolean paintComponent(Graphics g) {
			if (disableNormalPainting)
				return true;

			if (isOpaque())
				if (image == null || x != 0 || y != 0) {
					g.setColor(getBackground());
					g.fillRect(0, 0, getWidth(), getHeight());
				}

			if (image != null)
				g.drawImage(fastPan.image, fastPan.x, fastPan.y, null);

			return false;
		}
	}
	
	/**
	 * Shifts all of the views in this LViewManager to be centered on the given
	 * world coordinate position.
	 */
	public void offsetToWorld(Point2D pt) {
		int ppd = myVMan.getProj().getPPD();
		Point2D current = myVMan.getLocationManager().getLoc();
		int w = getWidth();
		int h = getHeight();
		int dx = (int)((current.getX()-pt.getX())*ppd);
		int dy = (int)((current.getY()-pt.getY())*-ppd);
		offsetViews(w,h,dx,dy, myVMan.viewList);
	}
	
	void updateLocation(Point screen) {
		Point2D world = myVMan.getProj().screen.toWorld(screen);
		Point2D spatial = Main.PO.convWorldToSpatialFromProj(myVMan.getProj(),
				world);

		// JMARS west lon => USER east lon
		spatial.setLocation(360 - spatial.getX(), spatial.getY());

		world.setLocation(world.getX() + Main.PO.getServerOffsetX(), world
				.getY());

		Main.setStatus(formatCoord(spatial, decF, "E ", "N\t"));
	}
	
	/** dx,dy go down and to the right, image coordinates go up and to the right */
	public static void offsetViews(int w, int h, int dx, int dy, List views){
		Rectangle copyRect = new Rectangle();
		copyRect.x = Math.max(0,-dx);
		copyRect.y = Math.max(0,-dy);
		copyRect.width = w - Math.abs(dx);
		copyRect.height = h - Math.abs(dy);
		
		Rectangle leftRightRect = new Rectangle();
		Rectangle topBotRect = new Rectangle();
		
		if (dx < 0){
			// Moved left, area on the right to be cleared
			leftRightRect.setBounds(copyRect.width, 0, Math.abs(dx), h);
		}
		else {
			// Moved right, area to the left to be cleared
			leftRightRect.setBounds(0, 0, Math.abs(dx), h);
		}
		
		if (dy < 0){
			// Moved up, area on the bottom to be cleared
			topBotRect.setBounds(0, copyRect.height, w, Math.abs(dy));
		}
		else {
			// Moved down, area on the top to be cleared
			topBotRect.setBounds(0, 0, w, Math.abs(dy));
		}
		
		for (Iterator it = views.iterator(); it.hasNext(); ){
			Layer.LView view = (Layer.LView)it.next();
			if (view != null && view.isVisible() && !view.clearOffScreenOnViewChange()) {
				int nbuf = view.getBufferCount();
				for(int i=0; i<nbuf; i++) {
					Graphics2D g2 = view.getOffScreenG2Direct(i);
					g2.copyArea(copyRect.x, copyRect.y, copyRect.width, copyRect.height, dx, dy);
					Color oldbg = g2.getBackground();
					g2.setBackground(new Color(0,0,0,0));
					g2.clearRect(leftRightRect.x, leftRightRect.y, leftRightRect.width, leftRightRect.height);
					g2.clearRect(topBotRect.x, topBotRect.y, topBotRect.width, topBotRect.height);
					g2.setBackground(oldbg);
				}
			}
		}
	}

	public String getToolTipText(MouseEvent event) {
		LViewManager vman = myVMan;

		// Look for active layer first
		Layer.LView view = vman.getActiveLView();

		int i = -1;
		while (true) {
			if (view != null && view.isVisible() && !view.tooltipsDisabled()) {
				String msg = view.getToolTipText(event);
				if (msg != null && msg.length() > 0)
					return msg;
			}
			if (++i == vman.viewList.size())
				return null;
			view = (Layer.LView) vman.viewList.get(i);
		}
	}
}
