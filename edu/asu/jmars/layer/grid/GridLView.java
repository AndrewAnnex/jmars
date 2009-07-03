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


package edu.asu.jmars.layer.grid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj;
import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.swing.PasteField;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

public class GridLView extends Layer.LView
 {
    private static DebugLog log = DebugLog.instance();

	private class GridSettings
	 {
		private Color   color;
		private double  spacing;
		private boolean[] visible = new boolean[2];

		// Cached data - same for both main & panner views
		private double cachedSpacing = 0;
		private ProjObj cachedProj = null;
		private GeneralPath[] cachedLines = new GeneralPath[0];
		
		public GridSettings(Color color, double spacing, boolean mainVisible, boolean pannerVisible){
			this.color = color;
			this.spacing = spacing;
			this.visible[0] = mainVisible;
			this.visible[1] = pannerVisible;
		}
		
		public synchronized void setColor(Color newColor){
			color = newColor;
		}
		public synchronized Color getColor(){
			return color;
		}
		
		public synchronized void setSpacing(double newSpacing){
			spacing = newSpacing;
		}
		public synchronized double getSpacing(){
			return spacing;
		}
		
		public synchronized boolean isVisible(boolean main){
			return visible[main? 0: 1];
		}
		public synchronized void setVisible(boolean main, boolean visibility){
			visible[main? 0: 1] = visibility;
		}
		
		public GeneralPath[] getLines(ProjObj proj){
			if (cachedSpacing != spacing || cachedProj != proj){
				if (spacing == 0)
					cachedLines = new GeneralPath[0];
				else
					cachedLines = generateLines(spacing);
				
				cachedSpacing = spacing;
				cachedProj = proj;
			}
			
			return cachedLines;
		}
	 }

	private GridSettings major = new GridSettings(Color.black, 10.0, true, true);
	private GridSettings minor = new GridSettings(Color.gray, 2.0, false, false);

	/**
        * Override to update view specifc settings
        */
	protected void updateSettings(boolean saving)
	 {
		boolean isMain = getChild() == null;
		
		if ( saving ) {

		   viewSettings.put("major", new UserGridSettings(major.getColor(), major.getSpacing(), major.isVisible(isMain)));
		   viewSettings.put("minor", new UserGridSettings(minor.getColor(), minor.getSpacing(), minor.isVisible(isMain)));

		} else {

		   if ( viewSettings.containsKey("major") ) {

			  UserGridSettings maj = (UserGridSettings) viewSettings.get("major");
			  major.setColor(new Color(maj.rgbcolor.intValue()));
			  major.setSpacing(maj.spacing.doubleValue());
			  major.setVisible(isMain, maj.visible.booleanValue());

		   }

		   if ( viewSettings.containsKey("minor") ) {
			  UserGridSettings min = (UserGridSettings) viewSettings.get("minor");
			  minor.setColor(new Color(min.rgbcolor.intValue()));
			  minor.setSpacing(min.spacing.doubleValue());
			  minor.setVisible(isMain, min.visible.booleanValue());
		   }
		}
	 }

	/**
	 ** Draws a screen-coordinate line, using a spatial graphics
	 ** context
	 **/
	private void drawLine(int x1, int y1,
						  int x2, int y2)
	 {
		Graphics2D g2 = getOnScreenG2();
		prepare(g2);
		g2.setXORMode(Color.gray);
		g2.setStroke(new BasicStroke(0));
		Graphics2D g2s = getProj().createSpatialGraphics(g2);
		Point2D down = getProj().screen.toSpatial(x1, y1);
		Point2D curr = getProj().screen.toSpatial(x2, y2);
		g2s.draw(new Line2D.Double(down, curr));
		g2s.dispose();
	 }

	public GridLView()
	 {
		super(null);

		MouseInputListener mouseHandler =
			new MouseInputAdapter()
			 {
				Point mouseDown = null;

				public void mousePressed(MouseEvent e)
				 {
					if(!SwingUtilities.isRightMouseButton(e))
						mouseDown = e.getPoint();
				 }

				public void mouseReleased(MouseEvent e)
				 {
					if(mouseLast != null)
					 {
						drawLine(mouseDown.x, mouseDown.y,
								 mouseLast.x, mouseLast.y);
						mouseLast = null;
					 }
					mouseDown = null;
				 }

				DecimalFormat f = new DecimalFormat("0.00");

				Point mouseLast = null;
				public void mouseDragged(MouseEvent e)
				 {
					// Don't catch menu popup drags
					if(mouseDown == null)
						return;

					Point mouseCurr = e.getPoint();

					if(mouseLast != null)
						drawLine(mouseDown.x, mouseDown.y,
								 mouseLast.x, mouseLast.y);
					drawLine(mouseDown.x, mouseDown.y,
							 mouseCurr.x, mouseCurr.y);

					Point2D down = getProj().screen.toWorld(mouseDown);
					Point2D curr = getProj().screen.toWorld(mouseCurr);
					down = Main.PO.convWorldToSpatial(down);
					curr = Main.PO.convWorldToSpatial(curr);

					double[] distances = Util.angularAndLinearDistanceS(down, curr, getProj());
					Main.setStatus(Util.formatSpatial(curr) + "\tdist = " +
								   f.format(distances[0]) + "deg = " +
								   f.format(distances[1]) + "km");

					mouseLast = mouseCurr;
				 }
/*
 * DEBUG CODE for examining the GridDataStore at runtime.
 *
				public void mouseClicked(MouseEvent e)
				 {
					SpatialGraphicsSpOb g2spatial =
						(SpatialGraphicsSpOb) getProj().createSpatialGraphics(
							getOffScreenG2());
					GridDataStore grid = g2spatial.grid;
					Point cellIdx = grid.getCellPoint(
						getProj().screen.toWorld(e.getPoint()));
					log.aprintln("---------- " + cellIdx.x + ", " + cellIdx.y);
					log.aprintln(grid.getCell(cellIdx.x, cellIdx.y));
					log.aprintln("gridX = " + g2spatial.minGridX + " to " + g2spatial.maxGridX);
					log.aprintln("gridY = " + g2spatial.minGridY + " to " + g2spatial.maxGridY);
				 }
*/
			 }
			;
		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
	 }

         /**
          * This is good - build the focus panel on demand instead of during the view's constructor
          * That way, the saved session variables get propogated properly.
          */
	public FocusPanel getFocusPanel()
	 {
		if(focusPanel == null)
		 {
			focusPanel = new FocusPanel(this);
			if(getChild() != null)
				focusPanel.add(new GridPanel());
		 }
		return  focusPanel;
	 }

	private static final int PRECISION = 1 << 16;

	/**
	 ** Layer-less view; simply returns null, since it's never used.
	 **
	 ** @return null
	 **/
	protected Object createRequest(Rectangle2D where)
	 {
		return  null;
	 }

	/**
	 ** Layer-less view; simply returns without doing anything, since
	 ** it's never called.
	 **/
	public void receiveData(Object layerData)
	 {
		log.aprintln("PROGRAMMER: SOMETHING'S OFF!");
		log.aprintStack(5);
	 }

	protected Layer.LView _new()
	 {
		GridLView newLView = new GridLView();
		
		// Share the major/minor settings from the very first LView
		newLView.major = major;
		newLView.minor = minor;
		
		return newLView;
	 }

	/**
	 ** Draws in lat/lon lines offscreen.
	 **/
	protected void viewChangedPost()
	 {
		redrawGrid();
	 }

	/**
	 ** Maintains an id to keep track of redraws, so that as soon as a
	 ** new one is issued, the older ones die. Without this, we end up
	 ** overlapping our redraws when the user pans/zooms too quickly.
	 **/
	private void redrawGrid()
	 {
		int id;
		synchronized(viewChangeIDLock)
		 {
			id = ++lastViewChangeID;
		 }
		redrawGrid(id);
	 }
	private int lastViewChangeID = 0;
	private Object viewChangeIDLock = new Object();

	private boolean oldViewChange(int id)
	 {
		// Should really be synchronized over viewChangeIDLock, but
		// it's only ever called while under a redrawLock, so we'll
		// pretend it's safe.
		return  lastViewChangeID != id;
	 }

	private void redrawGrid(int id)
	 {
		synchronized(redrawLock)
		 {
			clearOffScreen();
			// Determine how many periods to draw (360-degree horizontal boxes)
			double minX = viewman.getProj().getWorldWindow().getMinX();
			double maxX = viewman.getProj().getWorldWindow().getMaxX();

			long minPeriod = (long) Math.floor(minX / 360);
			long maxPeriod = (long) Math.ceil (maxX / 360);
			int count = (int) (maxPeriod - minPeriod);

			// Create graphics contexts for each period
			Graphics2D g2 = getOffScreenG2Raw();
			prepare(g2);
			if(g2 == null)
				return;
			g2.setStroke(getProj().getWorldStroke(1));
			Graphics2D[] copies = new Graphics2D[count];
			for(int i=0; i<count; i++)
			 {
				copies[i] = (Graphics2D) g2.create();
				copies[i].translate(360*(i+minPeriod), 0);
				copies[i].clip(new Rectangle(0, -90, 360, 180));
			 }

			// Actually draw the grid elements
			drawLatLon(id, copies, minor);
			drawLatLon(id, copies, major);

			if(!oldViewChange(id))
				repaint();
		 }
	 }
	private Object redrawLock = new Object();

	private GeneralPath[] generateLines(double spacing){
		log.println("spacing = " + spacing);
		log.printStack(3);
		int expected = (int) Math.ceil(360/spacing + 180/spacing);
		log.println("Expecting " + expected + " paths");
		ArrayList paths = new ArrayList(expected);
		GeneralPath gp = new GeneralPath();

		final double prec = 2;

		// Lines of latitude
		for(double lat=-90+spacing; lat-0.001<=+90-spacing; lat+=spacing)
		 {
			gp.reset();

			Point2D start = worldPt(0, lat);
			gp.moveTo((float) start.getX(),
					  (float) start.getY());

			for(double lon=prec; lon-0.0000001<=360; lon+=prec)
				connectTo(gp, worldPt(lon, lat));

			paths.add(gp.clone());
		 }

		// Lines of longitude
		for(double lon=0; lon<360; lon+=spacing)
		 {
			gp.reset();

			Point2D start = worldPt(lon, -90);
			gp.moveTo((float) start.getX(),
					  (float) start.getY());

			for(double lat=-90+prec; lat-0.0000001<=+90; lat+=prec)
				connectTo(gp, worldPt(lon, lat));

			paths.add(gp.clone());
		 }

		log.println("Generated " + paths.size() + " paths");

		return  (GeneralPath[]) paths.toArray(new GeneralPath[0]);
	}

	private void drawLatLon(int id, Graphics2D[] g2s, GridSettings settings)
	 {
		if(settings.isVisible(getChild() != null))
		 {
			log.printStack(3);
			GeneralPath[] lines = settings.getLines(Main.PO);

			// Draw them all
			log.println("Drawing " + lines.length +
						" paths to " + g2s.length + " graphics contexts");
			for(int i=0; i<g2s.length; i++)
			 {
				g2s[i].setColor(settings.getColor());
				for(int j=0; j<lines.length; j++)
				 {
					if(oldViewChange(id))
						return;
					g2s[i].draw(lines[j]);
				 }
			 }
		 }
	 }


	private void print(GeneralPath gp)
	 {
		PathIterator iter = gp.getPathIterator(null);
		log.println("-----------");
		float[] pt = new float[2];
		DecimalFormat f = new DecimalFormat("0.00");
		while(!iter.isDone())
		 {
			iter.currentSegment(pt);
			log.print(f.format(pt[0]) + "," + f.format(pt[1]) + "  ");
			iter.next();
		 }
		log.print('\n');
	 }

	private void connectTo(GeneralPath gp, Point2D next)
	 {
		//log.println("------------------------------------------");
		//log.println("connectTo " + next);
		Point2D curr = gp.getCurrentPoint();
		if(next.distanceSq(curr) < 90*90)
			gp.lineTo((float)next.getX(),
					  (float)next.getY());
		else
		 {
			/**
			 ** We have a discontinuity, because we've drawn past the
			 ** current period's box. We solve this by drawing
			 ** twice... once from the real curr point to a fake next
			 ** point, and again from a fake curr point to the real
			 ** next point. These fake points are outside the box,
			 ** positioned to emulate wrap-around from one side of the
			 ** box to another.
			 **/

			// Fake x, if necessary
			double fakeCurrX = curr.getX();
			double fakeNextX = next.getX();
			if(Math.abs(fakeCurrX - fakeNextX) > 180)
			 {
				//log.println("Faking X");
				fakeCurrX += curr.getX()<180 ? 360 : -360;
				fakeNextX += next.getX()<180 ? 360 : -360;
			 }

			// Fake y, if necessary
			double fakeCurrY = curr.getY();
			double fakeNextY = next.getY();
			if(Math.abs(fakeCurrY - fakeNextY) > 90)
			 {
				//log.println("Faking Y");
				fakeCurrY += curr.getY()<0 ? 180 : -180;
				fakeNextY += next.getY()<0 ? 180 : -180;
			 }

			// Draw from the real curr to the fake next
			gp.lineTo((float) fakeNextX,
					  (float) fakeNextY);

			// Draw from the fake curr to the real next
			gp.moveTo((float) fakeCurrX,
					  (float) fakeCurrY);
			gp.lineTo((float) next.getX(),
					  (float) next.getY());

			//log.println("line from " + curr.getX() + ", " + curr.getY());
			//log.println("       to " + fakeNextX   + ", " + fakeNextY  );
			//log.println("  move to " + fakeCurrX   + ", " + fakeCurrY  );
			//log.println("  line to " + next.getX() + ", " + next.getY());
		 }
	 }

	// Handy utility
	private Point2D worldPt(double lon, double lat)
	 {
		return  Main.PO.convSpatialToWorld(new Point2D.Double(lon, lat));
	 }

	private class SettingsPanel
	 extends JPanel
	 implements ActionListener
	 {
		GridSettings settings;

		JTextField   txtSpacing;
		JButton      btnColor;
		JCheckBox    chkMain;
		JCheckBox    chkPanner;

		SettingsPanel(final String title,
					  GridSettings settings)
		 {
			this.settings = settings;

			// Set up the layout and borders
			setLayout(new GridLayout(0, 1, 0, 5));
			setBorder(
				BorderFactory.createCompoundBorder(
					BorderFactory.createTitledBorder(title),
					BorderFactory.createEmptyBorder(5, 5, 5, 5)
					)
				);

			// Spacing text field
			txtSpacing = new PasteField(String.valueOf(settings.getSpacing()), 4);
			txtSpacing.addActionListener(this);
			add(txtSpacing);

			// Color chooser button
			btnColor = new JButton(" ");
			btnColor.setBackground(settings.getColor());
			btnColor.addActionListener(this);
			add(btnColor);

			// Main checkbox
			chkMain = new JCheckBox("Main Window", settings.isVisible(true));
			chkMain.addActionListener(this);
			add(chkMain);

			// Panner checkbox
			chkPanner = new JCheckBox("Panner", settings.isVisible(false));
			chkPanner.addActionListener(this);
			add(chkPanner);
		 }

		// Handles all events from user
		public void actionPerformed(ActionEvent e)
		 {
			Object source = e.getSource();
			// Track what needs redrawing, based on what changed
			boolean redrawMain   = false;
			boolean redrawPanner = false;

			// Handle spacing value changes
			if(source == txtSpacing)
			 {
				double spacing;
				try
				 {
					// Note: parseDouble may throw NumberFormatException
					spacing = Double.parseDouble(txtSpacing.getText().trim());

					// Check to see if it divides evenly
					if(90.0 / spacing != Math.floor(90.0 / spacing))
						throw  new NumberFormatException();
				 }
				catch(NumberFormatException ex)
				 {
					// Handle problems by beeping and showing a dialog
					String oldValue = String.valueOf(settings.getSpacing());
					Toolkit.getDefaultToolkit().beep();
					JOptionPane.showMessageDialog(
						this,
						"Grid spacing value must be a number that divides " +
						"evenly into 90:\n\t" + txtSpacing.getText(),
						"Illegal value",
						JOptionPane.ERROR_MESSAGE);

					// Restore the old value and highlight it.
					txtSpacing.setText(oldValue);
					txtSpacing.setSelectionStart(0);
					txtSpacing.setSelectionEnd(oldValue.length());
					txtSpacing.requestFocus();

					return;
				 }

				// Spacing propagates to the main window and the panner
				settings.setSpacing(spacing);

				// Determine what needs to be redrawn, based on this change
				redrawMain = chkMain.isSelected();
				redrawPanner = chkPanner.isSelected();
			 }

			// Handle color chooser changes
			else if(source == btnColor)
			 {
				Color newColor = JColorChooser.showDialog(
					Main.mainFrame,
					"Choose a gridline color",
					settings.getColor());

				if(newColor != null)
				 {
					// Reflect the new color
					btnColor.setBackground(newColor);

					// Color propagates to the main window and the panner
					settings.setColor(newColor);

					// Only repaint those whose lines are visible
					redrawMain = chkMain.isSelected();
					redrawPanner = chkPanner.isSelected();
				 }
			 }

			// Handle main checkbox changes
			else if(source == chkMain)
			 {
				settings.setVisible(true, chkMain.isSelected());
				redrawMain = true;
			 }

			// Handle panner checkbox changes
			else if(source == chkPanner)
			 {
				settings.setVisible(false, chkPanner.isSelected());
				redrawPanner = true;
			 }

			// Based on what changed, trigger appropriate redraws
			if(redrawMain)
				redrawGrid();
			if(redrawPanner)
				((GridLView) getChild()).redrawGrid();
		 }
	 }

	private class GridPanel
	 extends JPanel
	 {
		GridPanel()
		 {
			add(new SettingsPanel("Major Lines", major));
			add(new SettingsPanel("Minor Lines", minor));
		 }

	 }

	public String getName()
	{
		return "Lat/Lon Grid";
	}
    private static void prepare(Graphics2D g2)
     {
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
							RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
							RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
     }
 }
