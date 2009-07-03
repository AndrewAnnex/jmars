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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.Layer.LView;
import edu.asu.jmars.layer.shape2.ShapeLayer.LEDStateProcessing;
import edu.asu.jmars.layer.shape2.ShapeLayer.LoadData;
import edu.asu.jmars.layer.shape2.ShapeLayer.LoadListener;
import edu.asu.jmars.layer.util.features.CalculatedField;
import edu.asu.jmars.layer.util.features.FPath;
import edu.asu.jmars.layer.util.features.Feature;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.FeatureEvent;
import edu.asu.jmars.layer.util.features.FeatureListener;
import edu.asu.jmars.layer.util.features.FeatureMouseHandler;
import edu.asu.jmars.layer.util.features.FeatureProvider;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.ShapeRenderer;
import edu.asu.jmars.layer.util.features.SingleFeatureCollection;
import edu.asu.jmars.layer.util.features.Style;
import edu.asu.jmars.layer.util.features.StyleSource;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.LineType;
import edu.asu.jmars.util.ObservableSetListener;
import edu.asu.jmars.util.SerializingThread;
import edu.asu.jmars.util.Util;

public class ShapeLView extends LView implements FeatureListener, ObservableSetListener<Feature> {
    private static DebugLog log = DebugLog.instance();
    static final int DRAWING_BUFFER_INDEX = 0;
    static final int SELECTION_BUFFER_INDEX = 1;
    
    private ShapeLayer shapeLayer;
    private ShapeFocusPanel focusPanel;
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
	
	public ShapeLView(ShapeLayer layerParent) {
		super(layerParent);
		
		shapeLayer = layerParent;
		
		// Keep two buffers, one for normal drawing, other for selection drawing.
		setBufferCount(2);
		
		shapeLayer.getFeatureCollection().addListener(this);
		shapeLayer.selections.addListener(this);
		
		drawThread = new SerializingThread("ShapeLViewDrawThread");
		drawThread.start();
		
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

		featureMouseHandler = new FeatureMouseHandler(shapeLayer, this, flags);
		addMouseListener(featureMouseHandler);
		addMouseMotionListener(featureMouseHandler);

		// set up the key listener for deleting vertices and features from the
		// view.
		addKeyListener( new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				int key = e.getKeyCode();
				int mode = featureMouseHandler.getMode();
				if (key == KeyEvent.VK_ESCAPE && mode == FeatureMouseHandler.ADD_FEATURE_MODE) {
					// delete the last vertex defined.
					featureMouseHandler.deleteLastVertex();
				} else if (key == KeyEvent.VK_DELETE && mode == FeatureMouseHandler.SELECT_FEATURE_MODE) {
					shapeLayer.getHistory().mark();
					// delete selected features
					shapeLayer.getFeatureCollection().removeFeatures(shapeLayer.selections);
				}
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
    
	public String getName() {
		return shapeLayer.name;
	}
	
	public void setName(String newName) {
		shapeLayer.name = newName;
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
	public void receiveData(Object layerData){
		if (layerData instanceof ShapeLayer.StylesChange) {
			stylesChanged(((ShapeLayer.StylesChange)layerData).changes);
		}
	}

	protected LView _new() {
		return new ShapeLView(shapeLayer);
	}
	
	public ShapeFocusPanel getFocusPanel(){
		if (focusPanel == null)
			focusPanel = new ShapeFocusPanel(this);
		
		return focusPanel;
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
			if (!Collections.disjoint(shapeLayer.selections, e.features)) {
				draw(true);
			}
			break;
		case FeatureEvent.CHANGE_FEATURE:
			// draw buffers that have used the affected field in the
			// last/current paint operation
			stylesChanged(shapeLayer.getStylesFromFields(e.fields));
			break;
		default:
			log.aprintln("Unhandled FeatureEvent encountered: "+e);
			break;
		}
	}
	
	private void stylesChanged(Set<Style<?>> changed) {
		if (!Collections.disjoint(changed, normStyles)) {
			draw(false);
		}
		if (!Collections.disjoint(changed, selStyles)) {
			draw(true);
		}
		if (getChild() != null) {
			((ShapeLView)getChild()).stylesChanged(changed);
		}
	}
	
	/**
	 * This is an overloading of the updateSettings() method in the superclass.
	 * Either saves the settings to the settings file or loads the settings out
	 * of the settings file.
	 */
	protected void updateSettings(boolean saving) {
		if (saving == true) {
			// save settings into hashtable
			List<Map<String,Object>> layers = new ArrayList<Map<String,Object>>();
			List<FeatureCollection> fcList = shapeLayer.fileTable.getFileTableModel().getAll();
			for (FeatureCollection fc: fcList) {
				Map<String,Object> layer = new HashMap<String,Object>();
				layer.put("id", fcList.indexOf(fc));
				layer.put("calcFields", shapeLayer.calcFieldMap.get(fc).getCalculatedFields());
				FeatureProvider fp = fc.getProvider();
				if (fp != null) {
					// will reload this file from outside the session file
					layer.put("providerClass", fp.getClass());
					layer.put("providerFile", fc.getFilename());
				} else {
					// will reload this file from within the session file
					Field[] schema = ((List<Field>)fc.getSchema()).toArray(new Field[0]);
					layer.put("schema", schema);
					Object[][] values = new Object[fc.getFeatureCount()][];
					for (int i = 0; i < values.length; i++) {
						Feature f = fc.getFeature(i);
						values[i] = new Object[schema.length];
						for (int j = 0; j < schema.length; j++) {
							Object value = f.getAttribute(schema[j]);
							if (value instanceof FPath) {
								value = new ShapePath((FPath)value);
							}
							values[i][j] = value;
						}
					}
					layer.put("values", values);
				}
				layers.add(layer);
			}
			viewSettings.put("layerName", getName());
			viewSettings.put("showProgress", shapeLayer.showProgress);
			viewSettings.put("layers", layers);
			viewSettings.put("styles", shapeLayer.getStyles().getStyles());
			viewSettings.put("defaultID", fcList.indexOf(shapeLayer.fileTable.getFileTableModel().getDefaultFeatureCollection()));
			List<FeatureCollection> selections = shapeLayer.fileTable.getSelectedFeatureCollections();
			int[] selOrder = new int[selections.size()];
			for (int i = 0; i < selOrder.length; i++) {
				selOrder[i] = fcList.indexOf(selections.get(i));
			}
			viewSettings.put("selections", selOrder);
//			viewSettings.put("tableSettings", getFocusPanel().getFeatureTable().getViewSettings());
		} else {
			// get the focus panel early to force creating the feature table
			// before styles and other changes are applied
			final ShapeFocusPanel focus = getFocusPanel();
			// remove existing feature collections - whatever the default was, we are replacing it here
			for (FeatureCollection fc: shapeLayer.getFileTable().getFileTableModel().getAll()) {
				shapeLayer.getFileTable().getFileTableModel().remove(fc);
			}
			// load settings from hashtable
			if (viewSettings.get("layerName") instanceof String) {
				setName((String)viewSettings.get("layerName"));
			}
			if (viewSettings.get("showProgress") instanceof Boolean) {
				shapeLayer.showProgress = (Boolean)viewSettings.get("showProgress");
			}
			if (viewSettings.get("styles") instanceof Set) {
				shapeLayer.applyStyleChanges((Set<Style<?>>)viewSettings.get("styles"));
				focus.clearStyleSelection();
			}
			if (viewSettings.get("layers") instanceof List) {
				List<Map<String,Object>> layers = (List<Map<String,Object>>)viewSettings.get("layers");
				final List<LoadData> sources = new ArrayList<LoadData>();
				final Map<Integer,LoadData> idMap = new HashMap<Integer,LoadData>();
				List<String> errors = new ArrayList<String>();
				for (final Map<String,Object> layer: layers) {
					// inserts a newly-loaded layer into the ShapeLayer, by
					// setting up the calc field listener, adding it to the
					// filetable, selecting it if necessary, and once all
					// sources are loaded, it restores the table settings
					final LoadListener listener = new LoadListener() {
						public void receive(LoadData data) {
							if (layer.get("id") instanceof Integer) {
								idMap.put((Integer)layer.get("id"), data);
							}
							if (layer.get("calcFields") instanceof Map) {
								Map<Field,CalculatedField> calcFields = (Map<Field,CalculatedField>)layer.get("calcFields");
								CalcFieldListener c = new CalcFieldListener(data.fc, shapeLayer.getHistory(), calcFields);
								shapeLayer.calcFieldMap.put(data.fc, c);
								data.fc.addListener(c);
							}
							shapeLayer.fileTable.getFileTableModel().add(data.fc);
							sources.remove(data);
							if (sources.isEmpty()) {
								// when all files have loaded, set the selections, the default collection, and table settings
								if (viewSettings.get("defaultID") instanceof Integer) {
									int defaultID = (Integer)viewSettings.get("defaultID");
									LoadData defaultData = idMap.get(defaultID);
									if (defaultData != null) {
										shapeLayer.fileTable.getFileTableModel().setDefaultFeatureCollection(defaultData.fc);
									}
								}
								if (viewSettings.get("selections") instanceof int[]) {
									int[] selections = (int[])viewSettings.get("selections");
									for (int idx: selections) {
										idx = shapeLayer.fileTable.getFileTableModel().getAll().indexOf(idMap.get(idx).fc);
										if (idx >= 0 && idx < shapeLayer.fileTable.getFileTableModel().getRowCount()) {
											shapeLayer.fileTable.getSelectionModel().addSelectionInterval(idx, idx);
										}
									}
								}
//								if (viewSettings.get("tableSettings") instanceof Map) {
//									SwingUtilities.invokeLater(new Runnable() {
//										public void run() {
//											// do this later so the styles listener can update styles columns in response
//											// to the selection change *before* we restore table settings
//											focus.getFeatureTable().setViewSettings((Map<String,Object>)viewSettings.get("tableSettings"));
//										}
//									});
//								}
							}
						}
					};
					if (layer.get("providerClass") instanceof Class && layer.get("providerFile") instanceof String) {
						try {
							Class<FeatureProvider> fpClass = (Class<FeatureProvider>)layer.get("providerClass");
							String fileName = (String)layer.get("providerFile");
							FeatureProvider fp = fpClass.getConstructor().newInstance();
							LoadData source = new LoadData(fp, fileName);
							sources.add(source);
							shapeLayer.loadSources(Arrays.asList(source), listener);
						} catch (Exception e) {
							String msg = e.getMessage() == null ? "Null error" : e.getMessage();
							errors.add(msg);
							e.printStackTrace();
						}
					} else if (layer.get("schema") instanceof Field[] && layer.get("values") instanceof Object[][]) {
						Field[] schema = (Field[])layer.get("schema");
						Object[][] values = (Object[][])layer.get("values");
						SingleFeatureCollection fc = new SingleFeatureCollection();
						for (Field f: schema) {
							fc.addField(f);
						}
						for (Object[] row: values) {
							Feature f = new Feature();
							for (int i = 0; i < schema.length; i++) {
								Object value = row[i];
								if (value instanceof ShapePath) {
									value = ((ShapePath)value).getPath();
								}
								f.setAttribute(schema[i], value);
							}
							fc.addFeature(f);
						}
						final LoadData load = new LoadData(null, null);
						load.fc = fc;
						sources.add(load);
						// invoke later, so all sources will have been created when the listener is called
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								listener.receive(load);
							}
						});
					} else {
						log.aprintln("Skipping layer with no known collection, found keys " + Arrays.asList(layer.keySet().toArray()));
						continue;
					}
				}
				if (!errors.isEmpty()) {
					JOptionPane.showMessageDialog(null, "Unable to restore all shapes:\n\n" +
						Util.join("\n", errors), "Some shapes could not be restored", JOptionPane.ERROR_MESSAGE);
				}
			}
		}
	}
	
    private boolean isMainView() {
    	return getChild() != null;
    }
    
	// styles used in the last rendering of each buffer
	final Set<Style<?>> normStyles = new HashSet<Style<?>>();
	final Set<Style<?>> selStyles = new HashSet<Style<?>>();
	
	public ShapeRenderer createRenderer(boolean selBuffer) {
		ShapeRenderer sr = new ShapeRenderer(this);
		ShapeLayerStyles styles = shapeLayer.getStyles();
		Set<Style<?>> usedStyles;
		if (selBuffer) {
			styles.showLineDir.setConstant(false);
			styles.showVertices.setConstant(false);
			styles.showLabels.setConstant(false);
			styles.lineColor = styles.selLineColor;
			styles.lineDash.setConstant(new LineType());
			styles.lineWidth = styles.selLineWidth;
			styles.fillPolygons.setConstant(false);
			styles.antialias.setConstant(false);
			styles.fillPolygons.setConstant(false);
			usedStyles = selStyles;
		} else {
			if (!isMainView()) {
				styles.showLabels.setConstant(false);
				styles.showLineDir.setConstant(false);
				styles.showVertices.setConstant(false);
			}
			usedStyles = normStyles;
		}
		usedStyles.clear();
		for (Style<?> s: styles.getStyles()) {
			s.setSource(new LogSource(s, s.getSource(), usedStyles));
		}
		sr.setStyles(styles);
		return sr;
	}

	/**
	 * When the {@link #getValue} method is called, this class adds the
	 * underlying style to the set of styles given to the ctor, to keep track of
	 * which styles are actually used.
	 */
    private static final class LogSource<E> implements StyleSource<E> {
		private static final long serialVersionUID = 1L;
    	private final Style<E> style;
    	private final StyleSource<E> wrappedSource;
    	private final Set<Style<E>> log;
    	public LogSource(Style<E> style, StyleSource<E> wrappedSource, Set<Style<E>> log) {
    		this.style = style;
    		this.wrappedSource = wrappedSource;
    		this.log = log;
    	}
		public E getValue(Feature f) {
			log.add(style);
			return wrappedSource.getValue(f);
		}
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
		private final Iterator<Feature> features;
		private final ShapeRenderer sr;
		private final long timeStamp;
		private final boolean selected;
		private final int featureCount;
		DrawingProgressDialog pd = null;
		
		// don't show the progress dialog for the first five seconds
		private long lastProgress = System.currentTimeMillis() + 2000;
		
		// don't repaint during draw for 1 second
		private long lastPaint = System.currentTimeMillis() + 1000;
		
		/**
		 * Constructs a Drawing Unit of Work for either selected or all the
		 * polygons.
		 * 
		 * @param features the source of features to render, clipped if possible.
		 * @param selected Pass as true to draw selected data only, false for all data.
		 * @param featureCount If > 0, taken to mean the number of calls to features.next(), used by the progress dialog
		 */
		public DrawingUow(Iterator<Feature> features, boolean selected, int featureCount) {
			this.features = features;
			this.selected = selected;
			timeStamp = System.currentTimeMillis();
			sr = createRenderer(selected);
	    	log.println(toString()+" created.");
			this.featureCount = featureCount;
		}
		
		public void run() {
			ShapeLayer.LEDState led = null;
			Graphics2D g2world = getOffScreenG2(selected? 1: 0);
			int position = 0;
			try {
				shapeLayer.begin(led = new ShapeLayer.LEDStateDrawing());
				
				log.println(toString()+" started.");
				
				clearOffScreen(selected? 1: 0);
				
				while (features.hasNext()) {
					Feature f = features.next();
					if (superceeded()){
						log.println(toString()+" superceeded.");
						break;
					}
					
					sr.draw(g2world, f);
					updatePaint();
					updateProgress(position, featureCount);
					position ++;
				}
			} finally {
			    shapeLayer.end(led);
			    sr.dispose();
			    g2world.dispose();
			    if (!superceeded()) {
			    	repaint();
			    }
			    updateProgress(featureCount+1,featureCount);
				log.println(toString()+" done in " + (System.currentTimeMillis() - timeStamp) + " ms");
			}
		}
		
		private void updatePaint() {
			long now = System.currentTimeMillis();
			if (now - lastPaint > 1000) {
				repaint();
				lastPaint = now;
			}
		}
		
		private synchronized void updateProgress(int progress, int total) {
			if (isMainView() && !selected) {
				if (shapeLayer.showProgress && progress < total) {
					if (pd == null) {
						pd = new DrawingProgressDialog(Main.mainFrame, Main.testDriver.mainWindow, 500L);
						pd.setMaximum(total);
					}
					long now = System.currentTimeMillis();
					// update at most twice a second
					if (now - lastProgress > 500) {
						lastProgress = now;
						final int mark = progress;
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								synchronized(DrawingUow.this) {
									if (pd != null) {
										pd.show();
										pd.setValue(mark);
									}
								}
							}
						});
					}
				} else {
					if (pd != null) {
						pd.hide();
						pd = null;
					}
				}
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
	public synchronized void draw(final boolean selected) {
		if (this.isAlive()) {
			drawReqTS[selected? 1: 0] = System.currentTimeMillis();
			Thread processor = new Thread(new Runnable() {
				public void run() {
					final LEDStateProcessing led = new ShapeLayer.LEDStateProcessing();
					shapeLayer.begin(led);
					try {
						Iterator<Feature> results = shapeLayer.getIndex().queryUnwrappedWorld(viewman2.getProj().getWorldWindow());
						Set<Feature> features = new LinkedHashSet<Feature>();
						while (results.hasNext()) {
							Feature f = results.next();
							if (!selected || shapeLayer.selections.contains(f)) {
								features.add(f);
							}
						}
						drawThread.add(new DrawingUow(features.iterator(), selected, features.size()));
					} finally {
						shapeLayer.end(led);
					}
				}
			});
			processor.setPriority(Thread.MIN_PRIORITY);
			processor.setName("ShapeSubsetter" + (isMainView()?"Main":"Panner"));
			processor.start();
		} else {
			setDirty(true);
		}
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
		
		shapeLayer.cleanup();
		
		// Destroy the drawing worker thread.
		drawThread.interruptIfBusy(true);
		drawThread.add(SerializingThread.quitRequest);
	}
	
	public void change(Set<Feature> added, Set<Feature> removed) {
		if ((added != null && added.size() > 0) || (removed != null && removed.size() > 0)) {
			draw(true);
		}
	}
}
