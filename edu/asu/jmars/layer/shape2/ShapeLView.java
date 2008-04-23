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


package edu.asu.jmars.layer.shape2;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.table.TableColumn;

import java.awt.Graphics;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.ProjectionEvent;
import edu.asu.jmars.layer.ProjectionListener;
import edu.asu.jmars.layer.Layer.LView;
import edu.asu.jmars.layer.shape2.ShapeLayer;
import edu.asu.jmars.layer.util.features.FeatureEvent;
import edu.asu.jmars.layer.util.features.FeatureListener;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.ShapeRenderer;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.LineType;
import edu.asu.jmars.util.SerializingThread;

import edu.asu.jmars.layer.util.features.FeatureMouseHandler;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;


public class ShapeLView extends LView implements ProjectionListener, FeatureListener, StateChangeListener {
	
    private static DebugLog log = DebugLog.instance();

	private static final Set keysForViewSettingsFromLayer;
	static {
		Set vslKeys = new HashSet();
		vslKeys.add(ShapeLayer.PRESET_KEY_SELECTED_LINE_WIDTH);
		vslKeys.add(ShapeLayer.PRESET_KEY_LINE_WIDTH);
		vslKeys.add(ShapeLayer.PRESET_KEY_POINT_SIZE);
		vslKeys.add(ShapeLayer.PRESET_KEY_SHOW_LABELS);
		vslKeys.add(ShapeLayer.PRESET_KEY_FILL_POLYGONS);
		vslKeys.add(ShapeLayer.PRESET_KEY_SHOW_VERTICES);
		vslKeys.add(ShapeLayer.PRESET_KEY_DEFAULT_LINE_COLOR);
		vslKeys.add(ShapeLayer.PRESET_KEY_DEFAULT_FILL_COLOR);
		vslKeys.add(ShapeLayer.PRESET_KEY_DEFAULT_LABEL_COLOR);
		vslKeys.add(ShapeLayer.PRESET_KEY_SELECTED_LINE_COLOR);
		vslKeys.add(ShapeLayer.PRESET_KEY_SHOW_PROGRESS);
		
		keysForViewSettingsFromLayer = Collections.unmodifiableSet(vslKeys);
	}
	
	// TODO relocate to correct place and get keys from ShapeLayer
	// TODO add support for multiple attribute set at a time, which
	//      is useful while restoring saved prefs.
	private static final Set drawAllStateVarNames;
	private static final Set drawSelStateVarNames;
	static {
		Set s;
		s = new HashSet();
		s.add(ShapeLayer.PRESET_KEY_SHOW_LABELS);
		s.add(ShapeLayer.PRESET_KEY_FILL_POLYGONS);
		s.add(ShapeLayer.PRESET_KEY_SHOW_VERTICES);
		s.add(ShapeLayer.PRESET_KEY_LINE_WIDTH);
		s.add(ShapeLayer.PRESET_KEY_POINT_SIZE);
		s.add(ShapeLayer.PRESET_KEY_ANTIALIASING);
		drawAllStateVarNames = Collections.unmodifiableSet(s);
		
		s = new HashSet();
		s.add(ShapeLayer.PRESET_KEY_SHOW_LABELS);
		s.add(ShapeLayer.PRESET_KEY_FILL_POLYGONS);
		s.add(ShapeLayer.PRESET_KEY_SELECTED_LINE_COLOR);
		s.add(ShapeLayer.PRESET_KEY_SHOW_VERTICES);
		s.add(ShapeLayer.PRESET_KEY_SELECTED_LINE_WIDTH);
		s.add(ShapeLayer.PRESET_KEY_POINT_SIZE);
		s.add(ShapeLayer.PRESET_KEY_ANTIALIASING);
		drawSelStateVarNames = Collections.unmodifiableSet(s);
	}

    static final int DRAWING_BUFFER_INDEX = 0;
    static final int SELECTION_BUFFER_INDEX = 1;

    private ShapeLayer shapeLayer;
    private ShapeFocusPanel focusPanel;
    
    private String layerName = "Shape Layer";

    
    private FeatureMouseHandler featureMouseHandler;


	/**
	 * Time-stamps for two different types of requests/unit-of-works.
	 * Index 0 is for all-Feature draw requests and index 1 is for
	 * selected-Feature redraw requests only. These time-stamps are
	 * used to figure out if a previously enqueued drawing request has
	 * been superceeded by a newer request or not.
	 * 
	 * @see #draw(boolean)
	 * @see #drawThread
	 * @see DrawingUow
	 */
	volatile private long[] drawReqTS = new long[] {0l, 0l};
	
	/**
	 * Drawing worker thread. Requests processed by this thread in a 
	 * serial fashion, one by one.
	 */
	SerializingThread drawThread;
	
	// Define the class that allows the mouseHandler to know the current selection line color.
	private class SelectionColor implements FeatureMouseHandler.SelColor {
		public Color getColor(){
			return (Color)shapeLayer.presets.get( ShapeLayer.PRESET_KEY_SELECTED_LINE_COLOR);
		}
	}

	
	public ShapeLView(Layer layerParent) {
		super(layerParent);
		
		shapeLayer = (ShapeLayer)layerParent;
		
		// Keep two buffers, one for normal drawing, other for selection drawing.
		setBufferCount(2);
		
		// Listen to ProjectionEvents
		Main.addProjectionListener(this);
		
		shapeLayer.getFeatureCollection().addListener(this);
		
		drawThread = new SerializingThread("ShapeLViewDrawThread");
		drawThread.start();
		
		shapeLayer.addStateChangeListener(this);

		// Set up the handlers of the mouse.

		int flags =
		   FeatureMouseHandler.ALLOW_ADDING_POINTS     |
		   FeatureMouseHandler.ALLOW_ADDING_LINES      |
		   FeatureMouseHandler.ALLOW_ADDING_POLYS      |
		   FeatureMouseHandler.ALLOW_MOVING_FEATURES   |
		   FeatureMouseHandler.ALLOW_DELETE_FEATURES   |

		   FeatureMouseHandler.ALLOW_MOVING_VERTEX     |
		   FeatureMouseHandler.ALLOW_ADDING_VERTEX     |
		   FeatureMouseHandler.ALLOW_DELETING_VERTEX   |

		   FeatureMouseHandler.ALLOW_ZORDER            |
		   FeatureMouseHandler.ALLOW_CHANGE_MODE;

		featureMouseHandler = new FeatureMouseHandler(shapeLayer.getFeatureCollection(),
			this, new SelectionColor(), flags,
			shapeLayer.getHistory());
		addMouseListener(featureMouseHandler);
		addMouseMotionListener(featureMouseHandler);

		// set up the key listener for deleting vertices and features from the
		// view.
		addKeyListener( new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				int key = e.getKeyCode();
				int mode = featureMouseHandler.getMode();
				if (key == KeyEvent.VK_ESCAPE
						&& mode == FeatureMouseHandler.ADD_FEATURE_MODE)
					// delete the last vertex defined.
					featureMouseHandler.deleteLastVertex();
				if (key == KeyEvent.VK_DELETE
						&& mode == FeatureMouseHandler.SELECT_FEATURE_MODE)
					// delete selected features
					shapeLayer.getFeatureCollection().removeFeatures(
							shapeLayer.getSelectedFeatures());
			}
		});

		// Ensure that we get focus when the mouse is over ShapeLView. We need to do this
		// to enable deleting via a keypress.
		addMouseListener(new MouseAdapter() {
				public void mouseEntered(MouseEvent e) {
					requestFocusInWindow();
				}
			});

		
	}




	/**
	 * When it comes time to repaint the view, all we need to do is redraw the selection line.
	 */
	public void paintComponent(Graphics g){
		super.paintComponent(g);
		if (getChild()!=null){
			Graphics2D g2 = (Graphics2D)g;
			g2.transform(getProj().getWorldToScreen());
			g2 = viewman2.wrapWorldGraphics(g2);

			featureMouseHandler.drawSelectionLine(g2);
			featureMouseHandler.drawSelectionRectangle( g2);
			featureMouseHandler.drawSelectionGhost( g2);
			featureMouseHandler.drawVertexBoundingLines( g2);
		}
	}
    


	public String getName(){
		return layerName;
	}
	
	public void setName(String newName){
		layerName = newName;
		if (Main.getLManager() != null)
			Main.getLManager().updateLabels();
	}

	protected Object createRequest(Rectangle2D where) {
		draw(false); // redraw all data
		draw(true);  // redraw selections
		return null;
	}
	
	/*
	 * (non-Javadoc)
	 * @see edu.asu.jmars.layer.Layer.LView#getContextMenu(java.awt.geom.Point2D)
	 * Adds ShapeLayer functionality to the main and the panner view.
	 */
	protected Component[] getContextMenu(Point2D worldPt){
		if (viewman2.getActiveLView().equals(this) ||
			    viewman2.getActiveLView().equals(getChild()) ){
		    return featureMouseHandler.getMenuItems( worldPt);
		}
		else {
		    return new Component[0];
		}
	}

	/**
	 * Does nothing.
	 */
	public void receiveData(Object layerData){}

	protected LView _new() {
		return new ShapeLView(shapeLayer);
	}
	
	public FocusPanel getFocusPanel(){
		if (focusPanel == null)
			focusPanel = new ShapeFocusPanel(this);
		
		return focusPanel;
	}

	/**
	 * Realizes the ProjectionListener interface.
	 */
	public void projectionChanged(ProjectionEvent e) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Realizes the FeatureListener interface.
	 */
	public void receive(FeatureEvent e) {
		switch(e.type){
		case FeatureEvent.ADD_FIELD:
		case FeatureEvent.REMOVE_FIELD:
			// do nothing - does not pertain to drawing
			break;
		case FeatureEvent.ADD_FEATURE:
		case FeatureEvent.REMOVE_FEATURE:
			// always redraw the data in the drawing buffer
			draw(false);
			draw(true);
			break;
		case FeatureEvent.CHANGE_FEATURE:
			// TODO: How do we handle an alternate "path" field here.
			
			// If modifications are limited to selection changes only
			// then redraw selections only.
			if (e.fields.size() == 1 && e.fields.contains(Field.FIELD_SELECTED)){
				draw(true);
			}
			else {
				// remove fields that are auto-updated, only draw if there are any left
				if (fieldsNeedRedraw (e.fields)) {
					draw(false);
					draw(true);
				}
			}
			break;
		default:
			log.aprintln("Unhandled FeatureEvent encountered: "+e);
		}
	}

	// returns true if the given fields require a redraw in _either_ the
	// main or the selection buffer
	public boolean fieldsNeedRedraw (List fields) {
		Set styleFields = new HashSet (ShapeRenderer.getFieldToStyleKeysMap().keySet());
		styleFields.add (Field.FIELD_PATH);
		styleFields.add (Field.FIELD_SELECTED);
		styleFields.retainAll (fields);
		return (! styleFields.isEmpty ());
	}

	// This is an overloading of the updateSettings() method in the superclass.
	// Either saves the settings to the settings file or loads the settings
	// out of the settings file. Loading only happens if the user specified a
	// config
	// file for JMARS on start up.
	protected void updateSettings(boolean saving) {

		// save settings
		if (saving == true) {
			viewSettings.put("layerName", getName());
			viewSettings.putAll(shapeLayer.getPresets(keysForViewSettingsFromLayer));

			Enumeration colEnum = shapeLayer.getFileTable().getColumnModel().getColumns();
			while (colEnum.hasMoreElements()) {
				TableColumn col = (TableColumn) colEnum.nextElement();
				String name = (String) col.getIdentifier();
				int width = col.getWidth();
				viewSettings.put(name, new Integer(width));
			}
		}

		// load settings
		else {
			if (viewSettings.containsKey("layerName")) {
				setName((String) viewSettings.get("layerName"));
			}
			
			Map vsForLayer = new HashMap();
			for(Iterator ki=keysForViewSettingsFromLayer.iterator(); ki.hasNext(); ){
				Object key = ki.next();
				if (viewSettings.containsKey(key))
					vsForLayer.put(key, viewSettings.get(key));
			}

			Enumeration colEnum = shapeLayer.getFileTable().getColumnModel().getColumns();
			while (colEnum.hasMoreElements()) {
				TableColumn col = (TableColumn) colEnum.nextElement();
				String name = (String) col.getIdentifier();
				if (viewSettings.containsKey(name)) {
					Integer i = (Integer) viewSettings.get(name);
					col.setPreferredWidth(i.intValue());
				}
			}
		}
	}

	
	private List getAllFeaturesInReverse(){
		// NOTE 1:
		// Iterator in the drawing code generates concurrent modification exception
		// if we don't make a copy here.
		// NOTE 2:
		// Drawing should happen in reverse order so that a Feature which appears 
		// towards bottom in the FeatureTable appears at the bottom in the LView.
		List features = new ArrayList(shapeLayer.getFeatureCollection().getFeatures());
		Collections.reverse(features);
		return features;
	}
	
    private boolean isMainView(){
    	if (getChild() == null)
    		return false;
    	
    	return true;
    }

    public ShapeRenderer getSrAll(){
    	ShapeRenderer srAll;
    	
    	srAll = new ShapeRenderer(this);
    	srAll.setDefaultAttribute(ShapeRenderer.STYLE_KEY_FONT, getFont().deriveFont(Font.BOLD));
    	srAll.setDefaultAttribute(ShapeRenderer.STYLE_KEY_LINE_WIDTH, shapeLayer.getPreset(ShapeLayer.PRESET_KEY_LINE_WIDTH));
    	srAll.setDefaultAttribute(ShapeRenderer.STYLE_KEY_POINT_SIZE, shapeLayer.getPreset(ShapeLayer.PRESET_KEY_POINT_SIZE));
    	
		if (shapeLayer.getPreset(ShapeLayer.PRESET_KEY_FILL_POLYGONS).equals(Boolean.FALSE))
			srAll.setOverrideAttribute(ShapeRenderer.STYLE_KEY_FILL_POLYGONS, Boolean.FALSE);
    	if (isMainView()){
    		if (shapeLayer.getPreset(ShapeLayer.PRESET_KEY_SHOW_LABELS).equals(Boolean.FALSE))
    			srAll.setOverrideAttribute(ShapeRenderer.STYLE_KEY_SHOW_LABELS, Boolean.FALSE);
    		if (shapeLayer.getPreset(ShapeLayer.PRESET_KEY_SHOW_VERTICES).equals(Boolean.FALSE))
    			srAll.setOverrideAttribute(ShapeRenderer.STYLE_KEY_SHOW_VERTICES, Boolean.FALSE);
    	}
    	else {
    		srAll.setOverrideAttribute(ShapeRenderer.STYLE_KEY_SHOW_LABELS, Boolean.FALSE);
    		srAll.setOverrideAttribute(ShapeRenderer.STYLE_KEY_SHOW_LINE_DIRECTION, Boolean.FALSE);
    		srAll.setOverrideAttribute(ShapeRenderer.STYLE_KEY_SHOW_VERTICES, Boolean.FALSE);
    	}

    	return srAll;
    }
    
    public ShapeRenderer getSrSel(){
    	ShapeRenderer srSel;
    	
    	srSel = new ShapeRenderer(this);
		srSel.setDefaultAttribute(ShapeRenderer.STYLE_KEY_FONT, getFont().deriveFont(Font.BOLD));
		if (isMainView()){
			srSel.setOverrideAttribute(ShapeRenderer.STYLE_KEY_SHOW_VERTICES, Boolean.TRUE);
		}
		else {
			srSel.setOverrideAttribute(ShapeRenderer.STYLE_KEY_SHOW_VERTICES, Boolean.FALSE);
			srSel.setOverrideAttribute(ShapeRenderer.STYLE_KEY_SHOW_LINE_DIRECTION, Boolean.FALSE);
		}
		srSel.setOverrideAttribute(ShapeRenderer.STYLE_KEY_SHOW_LABELS, Boolean.FALSE);
		srSel.setOverrideAttribute(ShapeRenderer.STYLE_KEY_DRAW_COLOR, shapeLayer.getPreset(ShapeLayer.PRESET_KEY_SELECTED_LINE_COLOR));
		srSel.setOverrideAttribute(ShapeRenderer.STYLE_KEY_LINE_DASH, new LineType());
		srSel.setOverrideAttribute(ShapeRenderer.STYLE_KEY_LINE_WIDTH, shapeLayer.getPreset(ShapeLayer.PRESET_KEY_SELECTED_LINE_WIDTH));
    	srSel.setDefaultAttribute(ShapeRenderer.STYLE_KEY_POINT_SIZE, shapeLayer.getPreset(ShapeLayer.PRESET_KEY_POINT_SIZE));
		srSel.setOverrideAttribute(ShapeRenderer.STYLE_KEY_FILL_POLYGONS, Boolean.FALSE);
    	
    	return srSel;
    }
	
	/**
	 * Drawing Unit of Work. Each such unit of work is executed on a single serializing
	 * thread. This particular unit of work abandons its current drawing loop as soon 
	 * as it determines that another request has superceeded it.
	 * 
	 *  @see ShapeLView#drawReqTS
	 *  @see ShapeLView#drawThread
	 *  @see ShapeLView#draw(boolean)
	 *  @see SerializingThread#add(Runnable)
	 */
	private class DrawingUow implements Runnable {
		Collection fc;
		Graphics2D g2world;
		ShapeRenderer sr;
		DrawingProgressDialog pd;
		boolean antialiasing;
		long timeStamp;
		boolean selected;
		
		/**
		 * Constructs a Drawing Unit of Work for either selected or all the
		 * polygons.
		 * 
		 * @param selected Pass as true to draw selected data only, false for all data.
		 */
		public DrawingUow(boolean selected){
			timeStamp = System.currentTimeMillis();
			this.selected = selected;
			g2world = getOffScreenG2(selected? 1: 0);
			sr = selected? getSrSel(): getSrAll();
			antialiasing = shapeLayer.getPreset(ShapeLayer.PRESET_KEY_ANTIALIASING).equals(Boolean.TRUE);
	    	
	    	fc = selected? shapeLayer.getSelectedFeatures(): getAllFeaturesInReverse();
	    	
	    	log.println(toString()+" created.");
		}
		
		/**
		 * Implementation of the Runnable interface.
		 */
		public void run(){
			ShapeLayer.LEDState led = null;
			try {
				shapeLayer.begin(led = new ShapeLayer.LEDStateDrawing());
				log.println(toString()+" started.");
				
				if (isMainView()){
					Boolean showProgress = (Boolean)shapeLayer.getPreset(ShapeLayer.PRESET_KEY_SHOW_PROGRESS);
					if (showProgress.booleanValue())
						pd = new DrawingProgressDialog(Main.mainFrame, Main.testDriver.mainWindow, 500L);
				}
				
		    	if (pd != null)
		    		pd.setMaximum(fc.size());
				
				clearOffScreen(selected? 1: 0);
				g2world.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						antialiasing? RenderingHints.VALUE_ANTIALIAS_ON: RenderingHints.VALUE_ANTIALIAS_OFF);
				
				for(Iterator fci = fc.iterator(); fci.hasNext(); ){
					if (superceeded()){
						log.println(toString()+" superceeded.");
						break;
					}
					
					sr.draw(g2world, (edu.asu.jmars.layer.util.features.Feature)fci.next());
					
					if (pd != null)
						pd.incValue();
				}
				
			    sr.dispose();
			    g2world.dispose();
			    
			    if (!superceeded())
			    	repaint();
			}
			finally {
				shapeLayer.end(led);
				if (pd != null)
					pd.hide();
				log.println(toString()+" done.");
			}
		}
		
		/**
		 * Determines whether the current request has been superceeded.
		 * It makes this determination based on the time stamp of this unit
		 * of work compared to the time stamp of the latest similar unit
		 * of work.
		 * 
		 * @return True if this request has been superceeded.
		 */
		private boolean superceeded(){
			if (drawReqTS[selected? 1: 0] > timeStamp)
				return true;
			return false;
		}
		
		/**
		 * Returns a string representation of this unit of work.
		 */
		public String toString(){
			return getClass().getName()+"["+
				"ts="+timeStamp+","+
				"selected="+selected+"]";
		}
	}
	
	/**
	 * Submit a request to draw the selected data or all data.
	 * 
	 * @param selected When true only the selected data is redrawn,
	 *                 all data is drawn otherwise.
	 */
	public synchronized void draw(boolean selected){
		if (this.isAlive()) {
			drawReqTS[selected? 1: 0] = System.currentTimeMillis();
			drawThread.add(new DrawingUow(selected));
		}
	}

	public void stateChanged(StateChangeEvent e) {
		Set drawAllSet = new HashSet(e.getStateVarNames());
		drawAllSet.retainAll(drawAllStateVarNames);
		
		Set drawSelSet = new HashSet(e.getStateVarNames());
		drawSelSet.retainAll(drawSelStateVarNames);
		
		if (!drawAllSet.isEmpty())
			draw(false);
		if (!drawSelSet.isEmpty())
			draw(true);
	}
	
	public FeatureMouseHandler getFeatureMouseHandler(){
		return featureMouseHandler;
	}

	/**
	 * Cleanup code at LView destruction.
	 */
	public void viewCleanup(){
		super.viewCleanup();
		
		// Destroy the focus panel.
		if (focusPanel != null)
			focusPanel.dispose();

		// Destroy the drawing worker thread.
		drawThread.add(SerializingThread.quitRequest);
	}
}
