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


package edu.asu.jmars;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.EventListenerList;
import javax.swing.plaf.basic.BasicComboBoxUI;

import edu.asu.jmars.swing.PasteField;
import edu.asu.jmars.swing.TimeField;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.TimeException;
import edu.asu.jmars.util.Util;

public class LocationManager
 extends JPanel
 {
	private static final DecimalFormat f = new DecimalFormat("0.###");

	public static final double INITIAL_X      = Main.PO.getInitial_X();
	public static final double INITIAL_Y      = Main.PO.getInitial_Y();
	public static final int    INITIAL_MAX_ZOOM_POWER;
        public static final int    ZOOM_MULTIPLIER;
        public static final int    INITIAL_ZOOM_SELECT;
	public static final String actionCommandSetLocation = "set location";

	public static final Integer zoomFactors[];

	private static DebugLog log = DebugLog.instance();

	static {
		INITIAL_MAX_ZOOM_POWER = 16;
		ZOOM_MULTIPLIER = 1;
		INITIAL_ZOOM_SELECT = 5;

		zoomFactors = new Integer[INITIAL_MAX_ZOOM_POWER];
		for (int i = 0; i < INITIAL_MAX_ZOOM_POWER; i++){
			zoomFactors[i] = new Integer(1<<(i*ZOOM_MULTIPLIER));
		}
	}


	protected JPanel inputPanel = null;                      // panel containing locInputPanel and zoomInputPanel
	protected JButton panLeftButton, panRightButton = null;  // left and right buttons
	protected JTextField locInputField = null;               // location input field
	protected JPanel locInputFieldLabel = null;              // label of the input field
	protected JPanel locInputPanel = null;                   // panel containing location field and associates

	protected JLabel zoomLabel = null;                       // zoom-selector's label
	protected JComboBox zoomSelector = null;                 // zoom factor selector
	protected JPanel zoomInputPanel = null;                  // zoom selector and its label's panel

	private Point2D loc = null;                            // default location
	protected int zoomFactor = 16;                            // default zoom
	protected java.util.List lls = null;                     // list of layer listeners

	/**
	 ** Does the work of {@link #setLocation} and {@link #setZoom} in
	 ** one call, except that it always propagates.
	 **/
	public void setLocationAndZoom(Point2D newLoc, int zoomFactor)
	 {
		setLocation(newLoc, false);
		setZoom(zoomFactor);
	 }

	/**
	 ** Public access point for externally setting the location.
	 **
	 ** @param newLoc The new location point, which will be reflected
	 ** in the input field.
	 ** @param propagate If true, the location change will be
	 ** propagated to all location listeners; if false, the change
	 ** will not be propagated.
	 **/
	public void setLocation(Point2D newLoc, boolean propagate)
	 {
		// set the new location
		loc.setLocation(newLoc);

		refreshLocationString();
		log.println("External location set: " + loc);

		if(propagate)
			propagateLocationAndZoom();
	 }

	public boolean isValidZoomFactor(int zoomFactor)
	 {
		return  zoomFactorIndex(zoomFactor) != -1;
	 }

	private int zoomFactorIndex(int zoomFactor)
	 {
		for(int i=0; i<zoomFactors.length; i++)
			if(zoomFactors[i].intValue() == zoomFactor)
				return  i;
		return  -1;
	 }

	/**
	 ** Doesn't propagate to the listeners.
	 **/
	public void setZoom(int zoomFactor)
	 {
		int index = zoomFactorIndex(zoomFactor);
		if(index == -1)
		 {
			log.aprintln("BAD ZOOM FACTOR RECEIVED: " + zoomFactor);
			return;
		 }
		zoomSelector.setSelectedIndex(index);
		this.zoomFactor = zoomFactor;
	 }

	/**
	 ** Replaces whatever current text is in the location textbox with
	 ** the programmatically-generated one.
	 **/
	public void refreshLocationString()
	 {
		locInputField.setText(getLocString());
	 }

	private String getLocString()
	 {
		return  currentConverter().worldToText(loc);
	 }

	public Point2D getLoc() {

          return loc;

        }

	private void bootStrap(){
		loc.setLocation(INITIAL_X, INITIAL_Y);
		log.println("bootStrap(): " + loc);
		zoomFactor = 32;
	}

	private void setSize(JComponent obj, int w, int h)
	 {
		Dimension d = new Dimension(w, h);
		obj.setMinimumSize(d);
		obj.setMaximumSize(d);
		obj.setPreferredSize(d);
	 }

	private List converters = new ArrayList(
		Collections.singleton((LocationConverter) new DefaultCylConverter ()));

	public void addConverter(LocationConverter tl)
	 {
		if(!"true".equals(Config.get("reproj")))
			return;
		converters.add(tl);
		redoInputFieldLabel();
	 }

	public void removeConverter(LocationConverter tl)
	 {
		converters.remove(tl);
		redoInputFieldLabel();
	 }

	public LocationConverter currentConverter()
	 {
		int idx = 0;
		if(converterCombo != null)
			idx = converterCombo.getSelectedIndex();
		return  (LocationConverter) converters.get(idx);
	 }

	private EventListenerList listenerList = new EventListenerList();

	public void addLocationConverterListener(LocationConverterListener l)
	 {
		listenerList.add(LocationConverterListener.class, l);
	 }

	public void removeLocationConverterListener(LocationConverterListener l)
	 {
		listenerList.remove(LocationConverterListener.class, l);
	 }

	protected void fireConverterChanged(LocationConverter newConverter)
	 {
		LocationConverterEvent event = null;
		EventListener[] listeners = listenerList.getListeners(
			LocationConverterListener.class);
		for(int i=listeners.length-2; i>=0; i-=2)
		 {
			if(event == null)
				event = new LocationConverterEvent(this, newConverter);
			((LocationConverterListener)listeners[i+1])
				.converterChanged(event);
		 }
     }

	ConverterComboBox converterCombo;

	private void redoInputFieldLabel()
	 {
		locInputFieldLabel.removeAll();
		converterCombo = null;
		if(converters.size() == 1)
			locInputFieldLabel.add(new JLabel(getConverterNames()[0] + " "));
		else
			locInputFieldLabel.add(converterCombo = new ConverterComboBox());
		validate();
	 }

	private String[] getConverterNames()
	 {
		String[] names = new String[converters.size()];
		for(int i=0; i<names.length; i++)
			names[i] =
				" " + ((LocationConverter) converters.get(i)).getConverterName() + ":";
		return  names;
	 }

	private class ConverterComboBox extends JComboBox
	 {
		ConverterComboBox()
		 {
			super(getConverterNames());
			setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
			addActionListener(
				new ActionListener()
				 {
					public void actionPerformed(ActionEvent e)
					 {
						refreshLocationString();
						fireConverterChanged(currentConverter());
					 }
				 }
				);
			setSelectedIndex(getItemCount() - 1);
		 }

		public void updateUI()
		 {
			setUI(new BasicComboBoxUI());
		 }
	 }

	/**
	 ** Attempts to initiate a reprojection based on the current
	 ** contents of the location text field.
	 **/
	public void reprojectFromText()
	 {
		Point2D newWorld =
			currentConverter().reprojectFromText(locInputField.getText());
		log.println(newWorld);
		if(newWorld != null)
			setLocation(newWorld, true);
	 }

	public void reprojectUpVectorFromText()
	 {
		String rawText = locInputField.getText();
		Point2D upLonLat = DefaultCylConverter.textToSpatial(rawText);
		if(upLonLat == null)
			return;
		HVector upVector = new HVector(upLonLat);
		ProjObj po = new ProjObj.Projection_OC(upVector);
		Main.setProjection(po);
		// JMARS west lon => USER east lon
		double lon = (360 - upLonLat.getX()) % 360;
		double lat = Util.roundToMultiple(upLonLat.getY(), 0.001);
		Main.setTitle(null,
					  "UP: " +
					  f.format(lon) + "E " +
					  f.format(lat) + "N");
	 }

	public LocationManager( Point2D initialLocation ){

		setLayout(new BorderLayout());

		lls = new Vector();
		loc = new Point2D.Double();
		bootStrap();

                if ( initialLocation != null ) {
                  loc.setLocation(initialLocation);
		  log.println("initializing(): " + initialLocation);
                }


		/* create the vbox containing the input fields */
		inputPanel = new JPanel();
		inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
//		add(inputPanel, BorderLayout.CENTER);
		add(new JLabel("  "), BorderLayout.CENTER);

		/* create the hbox containing the text field and its label */
		locInputPanel = new JPanel();
		locInputPanel.setLayout(new BoxLayout(locInputPanel, BoxLayout.X_AXIS));
		add(locInputPanel, BorderLayout.WEST);

		/* create the label for the location input field */
		locInputFieldLabel = new JPanel();
		redoInputFieldLabel();
		locInputFieldLabel.setToolTipText("Specify new image center.");
		locInputPanel.add(locInputFieldLabel);

		/* create the location input field */
		locInputField = new PasteField(getLocString(), 20);
		locInputField.setActionCommand(actionCommandSetLocation);
		locInputField.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				// reset the message line
				Main.setStatus(null);

				// read input from locInputField and propagate it
				readInputLoc();
			}
		});
		locInputPanel.add(locInputField);

//		locInputFieldLabel.setLabelFor(locInputField);

		/* create the hbox containing the zoom-selector and its label */
		zoomInputPanel = new JPanel();
		zoomInputPanel.setLayout(new BoxLayout(zoomInputPanel, BoxLayout.X_AXIS));
		add(zoomInputPanel, BorderLayout.EAST);

		/* create the zoom-selector label */
		zoomLabel = new JLabel("Zoom: ");
		zoomLabel.setToolTipText("Select the desired zoom-factor from the list "+
			"or type one in and press Enter.");
		zoomInputPanel.add(zoomLabel);

		/* create the zoom-selection combo-box */
		zoomSelector = new JComboBox(zoomFactors);
		zoomSelector.setSelectedIndex(INITIAL_ZOOM_SELECT);
		zoomSelector.setMaximumRowCount(zoomFactors.length);
		zoomSelector.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				// reset the message line
				Main.setStatus(null);

				// get the new zoom factor
				zoomFactor = zoomFactors[zoomSelector.getSelectedIndex()].intValue();

				log.println("Zoom factor: "+zoomFactor);
				log.println("propagate location+zoom");
				propagateLocationAndZoom();
			}
		});
		zoomInputPanel.add(zoomSelector);
	}

	/**
	 ** Returns the location bar's raw value (whether it's been
	 ** submitted by the user or not).
	 **
	 ** @return null on error
	 **/
	private Point2D getWorldPt()
	 {
		return  currentConverter().textToWorld(locInputField.getText());
	 }

	// parse location input within the locInputField and propagate it
	protected void readInputLoc()
	 {
		Point2D world = getWorldPt();
		if(world == null)
			return;

		loc.setLocation(world.getX(), world.getY());
		propagateLocationAndZoom();
	 }

	private void propagateLocationAndZoom(){

		java.util.ListIterator li = lls.listIterator();

		// dispatch a location and zoom update message to every LocationListener
		while(li.hasNext()){
			LocationListener ll = (LocationListener)li.next();
			ll.setLocationAndZoom(loc, zoomFactor);
		}

		log.println("propagateLocationAndZoom(): new location+zoom propagated to "
			+ lls.size() + " LocationListeners.");
	}

	// Register a LocationListener with the LocationManager.
	// The registered LocationListener immediately receives a location and zoomFactor
	// update.
	public void addLocationListener(LocationListener ll)
	 {
		lls.add(ll);
		ll.setLocationAndZoom(loc, zoomFactor);
		log.println("LocationManager.addLocationListener()");
	 }

	 public boolean removeLocationListener(LocationListener ll){
		log.println("removeLocationListener("+ll+")");
		return lls.remove((Object)ll);
	 }

	 public static void main(String args[]){
		JFrame top = new JFrame("Location Manager");
		top.addWindowListener(
			new WindowAdapter(){
				public void windowClosing(WindowEvent e){
					System.exit(0);
				}
			}
		);

		LocationManager lm = new LocationManager(null);

		top.setContentPane(lm);
		top.setLocation(100,100);
		top.pack();
		top.setVisible(true);
	 }

	private static class DefaultCylConverter
	 implements LocationConverter
	 {
		public Point2D getCenter()
		 {
			return  null;
		 }

		public boolean setCenter(Point2D c)
		 {
			return  false;
		 }

		public String getConverterName()
		 {
			return  "Lon, Lat";
		 }

		public String worldToText(Point2D worldPt)
		 {
			Point2D spatial = Main.PO.convWorldToSpatial(worldPt);

			double x = spatial.getX();
			double y = spatial.getY();

			x = (360 - (x % 360.)) % 360.; // JMARS west lon => USER east lon
			y = Util.roundToMultiple(y, 0.001);
			return  f.format(x) + "E, " + f.format(y);
		 }

		public Point2D textToWorld(String text)
		 {
			Point2D pt = textToSpatial(text);
			if(pt != null)
				pt = Main.PO.convSpatialToWorld(pt);
			return  pt;
		 }

		private static Point2D textToSpatial(String text)
		 {
			StringTokenizer t = new StringTokenizer(text, ",");
			int   nTokens = t.countTokens();

			final String illegalInputFormat =
				"Illegal input format. Should be \"lon [E|W], lat\", with abs(lat) <= 90.0";

			if(nTokens == 2  ||  nTokens == 3)
			 {
				try
				 {
					// center point specified as (lon, lat)
					String lonStr = t.nextToken().trim();
					String dirStr;
					if(nTokens == 3)
						dirStr = t.nextToken().trim();
					else
					 {
						int dirStart = Math.max(lonStr.indexOf('W'),
												lonStr.indexOf('E'));
						// If omitted, default user input to east longitude
						if(dirStart == -1)
							dirStr = "E";
						else
						 {
							dirStr = lonStr.substring(dirStart);
							lonStr = lonStr.substring(0, dirStart);
						 }
					 }

					double lon = Double.parseDouble(lonStr);
					if(dirStr.equalsIgnoreCase("E"))
						lon = -lon; // USER east lon => JMARS west lon
					else if(!dirStr.equalsIgnoreCase("W"))
					 {
						Toolkit.getDefaultToolkit().beep();
						Main.setStatus(illegalInputFormat);
						return  null;
					 }

					double lat = Double.parseDouble(t.nextToken().trim());
					if (Math.abs (lat) > 90.0) {
						Toolkit.getDefaultToolkit().beep();
						Main.setStatus(illegalInputFormat);
						return  null;
					}

					return  new Point2D.Double(lon, lat);
				 }
				catch(NumberFormatException e)
				 {
					log.println(e);
					Toolkit.getDefaultToolkit().beep();
					Main.setStatus("Illegal input. Numbers expected.");
					return  null;
				 }
			 }
			else
			 {
				Toolkit.getDefaultToolkit().beep();
				Main.setStatus(illegalInputFormat);
				return  null;
			 }
		 }

		public String formatTitlebarText_DEFUNCT(String rawText)
		 {
			if(textToWorld(rawText) == null)
				return  null;

			StringTokenizer tok =
				new StringTokenizer(rawText, ", \t");

			if(tok.countTokens() == 2)
			 {
				String lon = tok.nextToken().toUpperCase();
				if(!lon.endsWith("E")  &&  !lon.endsWith("W"))
					lon += "E";
				return  lon + " " + tok.nextToken() + "N";
			 }
			else // then 3
			 {
				return
					tok.nextToken() +
					tok.nextToken().toUpperCase() + " " +
					tok.nextToken() + "N";
			 }
		 }

		public Point2D reprojectFromText(String rawText)
		 {
			Point2D ctrLonLat = textToSpatial(rawText);
			if(ctrLonLat == null)
				return  null;
			ProjObj po = new ProjObj.Projection_OC(ctrLonLat.getX(),
												   ctrLonLat.getY());
			Main.setProjection(po);
			// JMARS west lon => USER east lon
			double lon = (360 - ctrLonLat.getX()) % 360;
			double lat = Util.roundToMultiple(ctrLonLat.getY(), 0.001);
			Main.setTitle(null,
						  f.format(lon) + "E " +
						  f.format(lat) + "N");
			Point2D newWorldPt = po.convSpatialToWorld(ctrLonLat);
			return  newWorldPt;
		 }
	 }

	private static class DefaultTimeConverter
	 implements LocationConverter
	 {
		public Point2D getCenter()
		 {
			return  null;
		 }

		public boolean setCenter(Point2D c)
		 {
			return  false;
		 }

		public String getConverterName()
		 {
			return  "Time, Slew";
		 }

		public String worldToText(Point2D worldPt)
		 {
			double x = worldPt.getX();
			double y = worldPt.getY();

			return  TimeField.etToDefault(x + Main.PO.getServerOffsetX())
				+ ", " + f.format(y);
		 }

		public Point2D textToWorld(String text)
		 {
			StringTokenizer t = new StringTokenizer(text, ",");
			int   nTokens = t.countTokens();

			final String illegalInputFormat =
				"Illegal input format. Should be \"time, deg\".";

			if(nTokens == 1  ||  nTokens == 2)
			 {
				try
				 {
					double x = TimeField.parseTimeToEt(t.nextToken().trim())
						- Main.PO.getServerOffsetX();
					double y = 0;
					if(nTokens == 2)
						y = Double.parseDouble(t.nextToken().trim());
			
					return  new Point2D.Double(x, y);
				 }
				catch(TimeException e)
				 {
					log.println(e);
					Toolkit.getDefaultToolkit().beep();
					Main.setStatus(e.toString());
					return  null;
				 }
				catch(NumberFormatException e)
				 {
					log.println(e);
					Toolkit.getDefaultToolkit().beep();
					Main.setStatus("Illegal input. " +
								   "Bare number expected after comma.");
					return  null;
				 }
			 }
			else
			 {
				Toolkit.getDefaultToolkit().beep();
				Main.setStatus(illegalInputFormat);
				return  null;
			 }
		 }

		public Point2D reprojectFromText(String rawText)
		 {
			return  null;
		 }
	 }
 }
