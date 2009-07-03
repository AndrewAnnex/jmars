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


package edu.asu.jmars.layer.stamp;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.Dimension2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputListener;
import javax.swing.table.TableColumn;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.LViewManager;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.MultiProjection;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.layer.WrappedMouseEvent;
import edu.asu.jmars.layer.stamp.StampLayer.StampSelectionListener;
import edu.asu.jmars.layer.stamp.StampLayer.StampTask;
import edu.asu.jmars.layer.stamp.StampLayer.Status;
import edu.asu.jmars.layer.stamp.chart.ChartView;
import edu.asu.jmars.layer.stamp.chart.ProfileLineCueingListener;
import edu.asu.jmars.layer.stamp.chart.ProfileLineDrawingListener;
import edu.asu.jmars.layer.stamp.focus.FilledStampFocus;
import edu.asu.jmars.layer.stamp.focus.StampFocusPanel;
import edu.asu.jmars.layer.stamp.functions.RenderFunction;
import edu.asu.jmars.swing.ValidClipboard;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.Util;
import edu.asu.jmars.util.stable.FilteringColumnModel;


/**
 * Base view implementation for stamp image layer.  
 */

public class StampLView extends Layer.LView implements StampSelectionListener
{
	private static final String VIEW_SETTINGS_KEY = "stamp";

	static DebugLog log = DebugLog.instance();

	private static final int proximityPixels = 8;
	private static final int STAMP_RENDER_REPAINT_COUNT_MIN = 1;
	private static final int STAMP_RENDER_REPAINT_COUNT_MAX = 10;
	private static final int STAMP_RENDER_REPAINT_COUNT_BASE = 10;

    public static final int OUTLINE_BUFFER_IDX = 1;
    public static final int SELECTED_OUTLINE_BUFFER_IDX = 2;
    
	public StampShape[] stamps;

	public FilledStamp[] filled;
	
	// This is the list of stamps that are in the view and selected
	private List<StampShape> selectedStamps=new ArrayList<StampShape>();
	
    // stamp outline drawing state
    private StampShape[] lastStamps;
    private Color lastUnsColor;
    private int projHash = 0;

	public FilledStampFocus focusFilled = null;
	protected boolean restoreStampsCalled = false;

	protected boolean settingsLoaded = false;

	private AddLayerWrapper wrapper;
	
	public StampLayer stampLayer;
	
	/** Line for which the profile is to be plotted */
	public Shape profileLine;
	/** Stores the profile line and manages mouse events in relation to it */
	private ProfileLineDrawingListener profileLineMouseListener = null;
	/** Stores the cue position and manages mouse events in relation to it */
	private ProfileLineCueingListener profileLineCueingListener = null;
	/** Name of this layer, automatically created when the layer does not have a defined name */

	public StampLView(StampFactory factory, StampLayer parent, AddLayerWrapper wrapper) {
		this(parent, wrapper, false);
		originatingFactory = factory;
	}
	
	public StampLView(StampLayer parent, AddLayerWrapper wrapper, boolean isChild)
	{	
		super(parent);
		
		stampLayer = parent;
		
		if (!isChild) {
			stampLayer.addSelectionListener(this);
		}
		
        setBufferCount(3);

        this.wrapper = wrapper;
	    if (wrapper==null) {
	    	this.wrapper = new AddLayerWrapper(stampLayer.getSettings().getInstrument());
	    }
        
		if (!isChild) {
			focusFilled = createFilledStampFocus();
		}
		
		MouseHandler mouseHandler = new MouseHandler();
		
		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);
		
		if (stampLayer.getInstrument().equalsIgnoreCase("themis")) {
			// Add mouse listener for profile line updates
			profileLineMouseListener = new ProfileLineDrawingListener(this);
			addMouseListener(profileLineMouseListener);
			addMouseMotionListener(profileLineMouseListener);
			addKeyListener(profileLineMouseListener);
	
			profileLineCueingListener = new ProfileLineCueingListener(this);
			addMouseMotionListener(profileLineCueingListener);
		}
		
		myFocus = null;
	}

	// Stupid.
	protected void setBufferVisible(int i, boolean visible)
	 {
		super.setBufferVisible(i, visible);
	 }
	
	public final String getName()
	{ 
		return  stampLayer.getSettings().getName();
	}
        
	// Override to create own subclasses of MyFilledStampFocus
	//  as part of this classes constructor.
	protected FilledStampFocus createFilledStampFocus()
	{
		return new FilledStampFocus(this);
	}

	/**
	 * Override to handle special needs.
	 */
	public SerializedParameters getInitialLayerData()
	{
		return stampLayer.getSettings();
	}

	public List<StampFilter> getFilters() {
		return wrapper.getFilters();
	}
	
	public StampFocusPanel myFocus;

	public FocusPanel getFocusPanel()
	{
		// Do not create a focus panel for the panner!
		if (stampLayer==null) {
			return null;
		}
		
		if (focusPanel == null)
			focusPanel =
				new DelayedFocusPanel()
				{
					private boolean calledOnceAlready = false;

					public JPanel createFocusPanel()
					{
						if (calledOnceAlready)
							return myFocus;
						synchronized(StampLView.this)
						{
							if (myFocus == null) {
							    myFocus = new StampFocusPanel(StampLView.this, wrapper);
							    calledOnceAlready = true;
							}
							return  myFocus;
						}
					}

					public void run()
					{
						JPanel fp = createFocusPanel();
						removeAll();
						add(fp);
						validate();
						repaint();

						// After creation of the focus panel, restore any rendered stamps 
						// if this is a view restoration and settings have been loaded.
						if (settingsLoaded &&
						    stampLayer.getSettings() != null)
						{
							log.println("calling restoreStamps from createFocusPanel");
							restoreStamps(stampLayer.getSettings().getStampStateList());
						}
					}
				}
				;

		return  focusPanel;
	}

	// Forces creation if it's been delayed
	protected void createFocusPanel()
	{
		final Runnable builder = new Runnable() {
			public void run() {
				( (DelayedFocusPanel) getFocusPanel() ).createFocusPanel();
			}
		};
		try {
			// create it on the AWT thread if we're not already on it
			if (SwingUtilities.isEventDispatchThread()) {
				builder.run();
			} else {
				SwingUtilities.invokeAndWait(builder);
			}
		} catch (Exception e) {
			log.aprintln("Error occurred building the focus panel:");
			log.aprintln(e);
		}
	}
	
	/**
	 * Override to update view specific settings
	 */
	protected synchronized void updateSettings(boolean saving)
	{
		if (saving){
			log.println("saving settings");

			stampLayer.getSettings().setStampStateList(focusFilled.getStampStateList());
			
			FilteringColumnModel colModel = (FilteringColumnModel) myFocus.table.getColumnModel();
			
			Enumeration<TableColumn> colEnum=colModel.getColumns();
			
			String cols[] = new String[colModel.getColumnCount()];
			
			for (int i=0; i<cols.length; i++) {
				cols[i]=colEnum.nextElement().getHeaderValue().toString();
			}
			
			stampLayer.getSettings().setInitialColumns(cols);
			viewSettings.put(VIEW_SETTINGS_KEY, stampLayer.getSettings());
		}
		else
		{
			log.println("loading settings");

			if ( viewSettings.containsKey(VIEW_SETTINGS_KEY) )
			{
				stampLayer.setSettings((StampLayerSettings) viewSettings.get(VIEW_SETTINGS_KEY));
				if (stampLayer.getSettings() != null)
				{
					log.println("lookup of settings via key succeeded");

					if (focusFilled != null)
					{
						// Reload and rerender filled stamps; requires that both MyFocus
						// and MyFilledStampFocus panels exist.
						if (stampLayer.getSettings().getStampStateList() != null &&
						    myFocus != null)
						{
							log.println("calling restoreStamps from updateSettings");
							restoreStamps(stampLayer.getSettings().getStampStateList());
						}
					}
					
					settingsLoaded = true;
				}
				else
				{
					log.println("lookup of settings via key failed");
					stampLayer.setSettings(new StampLayerSettings());
				}
			}
		}
	}
	
	/**
	 * Receive cueChanged events from chartView.
	 * @param worldCuePoint The new point within the profileLine segment boundaries
	 *        where the cue is to be generated.
	 */
	public void cueChanged(Point2D worldCuePoint){
		profileLineCueingListener.setCuePoint(worldCuePoint);
	}
	

	



	public Shape getProfileLine() {
		return profileLine;
	}
	
	/**
	 * Sets line for which profile is to be extracted.
	 * @param newProfileLine Set the new line to be profiled to this line.
	 *        A null value may be passed as this argument to clear the profile line.
	 */
	public void setProfileLine(Shape newProfileLine){

		profileLine = newProfileLine;
		
		if (focusPanel != null && myFocus != null){
			ChartView chartView = myFocus.getChartView();
			if (chartView!=null) {
				chartView.setProfileLine(profileLine, profileLine == null? 1: getProj().getPPD());
			}
		}
	}
    
	public void receiveData(Object layerData)
	{
		if (!isAlive())
			return;

		if (layerData instanceof StampShape[])
		{
				log.println("STARTED receiveData in "
					    + Thread.currentThread().getName());

				stamps = (StampShape[]) layerData;

				if (getChild()!=null) {
					createFocusPanel();
					if (myFocus != null)
					{
						myFocus.dataRefreshed();
					}
				}
				
				// It is not necessary to redraw child view (if any)
				// in this context as both Main and Panner views
				// receive data separately for view changes, etc.
				// Force a redraw of filled stamps
				synchronized (this) {
					lastFilled=null;
					
					redrawEverything(false);
				}
							
				log.println("STOPPED receiveData in "
				            + Thread.currentThread().getName());
				repaint();
		}
		else
			log.aprintln("BAD DATA CLASS: " + layerData.getClass().getName());
	}

    protected void viewChangedPost()
    {
        if (!isVisible())
            setDirty(true);
    }
	
    public void clearOffScreen(int i) {
    	super.clearOffScreen(i);
    }
    	
    	
	public void redrawEverything(boolean redrawChild)
	{
		if (stamps != null) {
            StampTask task = ((StampLayer)getLayer()).startTask();
            task.updateStatus(Status.YELLOW);
            
            // Normal redraw: clear window, draw alpha-filling,
            // draw filled stamps, and then draw outlines.
 //           clearOffScreen(0);

              
            if (getChild()!=null) {
            	drawFilled();
            }
            
            drawOutlines();
            
			if (redrawChild  &&  getChild() != null)
				((StampLView) getChild()).redrawEverything(false);
			task.updateStatus(Status.DONE);
			repaint();
		}
	}
    
	/**
	 * Paints the component using the super's paintComponent(Graphics),
	 * followed by the profile-line drawing onto the on-screen graphics
	 * context.
	 */
	public synchronized void paintComponent(Graphics g) {
		// Don't try to draw unless the view is visible
		if (!isVisible() || viewman2 == null)
			return;
		
		// super.paintComponent draws the back buffers onto the layer panel
		super.paintComponent(g);
		
		// then we draw the profile line on top of the layer panel
		Graphics2D g2 = (Graphics2D) g.create();
		g2 = viewman2.wrapWorldGraphics(g2);
		g2.transform(getProj().getWorldToScreen());
		g2.setStroke(new BasicStroke(0));
		
		if (profileLineMouseListener != null)
			profileLineMouseListener.paintProfileLine(g2);

		if (profileLineCueingListener != null)
			profileLineCueingListener.paintCueLine(g2);
		
		if (profileLine != null){
			g2.setColor(Color.red);
			g2.draw(profileLine);
		}
	}
	
    /**
     * Draws alpha-filling of stamp regions.
     */
	protected void drawAlpha()
	{
			Graphics2D g2 = getOffScreenG2();

			if (g2 == null)
				return;

			g2.setStroke(new BasicStroke(0));
			g2.setPaint(stampLayer.getSettings().getFilledStampColor());
			for (int i=0; i<stamps.length; i++)
			{
				g2.fill(stamps[i].getPath());

				if (i % 1000 == 999)
				{
//					getLayer().setStatus(Color.yellow);
					repaint();
				}
			}
            
			repaint();
	}

    /**
     * Draws stamp outlines in window. 
     * Selected stamp outlines are only drawn if hiding is 
     * not selected.
     * 
	 * Outlines are only redrawn if the projection, 
     * outline colors, or the in-view stamp list have changed since the last drawing 
     * (or if being drawn for the first time).  Otherwise, outlines are simply drawn 
     * to the screen with existing buffer contents.
     *
     * @see #drawSelections 
     */
	public void drawOutlines()
	{
		setBufferVisible(StampLView.OUTLINE_BUFFER_IDX, !stampLayer.getSettings().hideOutlines());
        
		if (!stampLayer.getSettings().hideOutlines()) {
			Color unsColor = stampLayer.getSettings().getUnselectedStampColor();
			
            if (lastStamps != stamps ||
                lastUnsColor != unsColor ||
                projHash != Main.PO.getProjectionSpecialParameters().hashCode())
            {            	
                lastStamps = stamps;
                lastUnsColor = unsColor;
                projHash = Main.PO.getProjectionSpecialParameters().hashCode();
                
                // Draw stamp outlines in their own off screen buffer to speed up outline toggling
                clearOffScreen(OUTLINE_BUFFER_IDX);
    			Graphics2D g2 = getOffScreenG2(OUTLINE_BUFFER_IDX);
    
    			if (g2 == null)
    				return;
    
    			g2.setStroke(new BasicStroke(0));
    			g2.setPaint(unsColor);
    			for (int i=0; i<stamps.length; i++)
    			{
    				g2.draw(stamps[i].getPath());    
    			}
                
            }
		} 
	
        selectedStamps = ((StampLayer)getLayer()).getSelectedStamps();
		drawSelections(selectedStamps, true);
        
        repaint();
	}
	
	// forces drawFilled to rerender filled stamps
	public void clearLastFilled() {
		lastFilled=null;
	}
	
	List<FilledStamp> lastFilled;
	Color lastFillColor;

    /**
     * Draws specified list of filled stamps to the primary buffer
     * of the stamp view's window.  Does not otherwise alter the
     * state of the buffer, so it may be used to overlay filled
     * stamps to the existing drawing buffer contents.
     * <p>
     * NOTE:  Because of the above functionality, the caller must
     * clear the drawing buffer contents if it is desired that the
     * specified filled stamps be the only ones displayed and/or
     * to be certain of a pristine state.
     * 
     * @return Returns <code>true</code> if drawing of stamps completed
     * without interruption because of current redraw thread becoming
     * stale (i.e., stale call to receiveData()) or if there were no
     * filled stamps specifed; returns <code>false</code> if drawing of 
     * stamps was interrupted or if there was some internal error.
     */
	private synchronized boolean drawFilled()
	{
		if (viewman == null) {
			log.aprintln("view manager not available");
			return false;
		}

		final int renderPPD = viewman.getMagnification();

		final List<FilledStamp> allFilledStamps;
		
		if (stampLayer.getSettings().renderSelectedOnly()) {
			allFilledStamps = focusFilled.getFilledSelections();
		} else {
			allFilledStamps = focusFilled.getFilled();
		}
		
		final ArrayList<FilledStamp> filledStamps = new ArrayList<FilledStamp>();
		// Filter out any stamps that aren't in the view
		for (FilledStamp fs : allFilledStamps) {
			for (int i=0; i<stamps.length; i++) {
				if (fs.stamp.getId().equalsIgnoreCase(stamps[i].getId())) {
					filledStamps.add(fs);
				}
			}
		}
				
		final MultiProjection proj = getProj();
		if (proj == null) {
			log.aprintln("null projection");
			return false;
		}

		if (lastFilled!=null && lastFilled.size()==filledStamps.size() 
				&& lastFillColor==stampLayer.getSettings().getFilledStampColor()) {
			List<FilledStamp> tmpList = new ArrayList<FilledStamp>();
			tmpList.addAll(lastFilled);
			
			for (FilledStamp fs: filledStamps) {
				if (tmpList.contains(fs)) {
					tmpList.remove(fs);
				} else {
					break;
				}
			}
			
			// Same stamps as before, don't redraw
			if (tmpList.size()==0) {
				return true;
			}
		}
		
		
//        if (lastFilled != filledStamps ||
//                projHash != Main.PO.getProjectionSpecialParameters().hashCode())
//            {
        lastFilled = filledStamps;
        lastFillColor = stampLayer.getSettings().getFilledStampColor();
//                projHash = Main.PO.getProjectionSpecialParameters().hashCode();
//
//		
				
		final long repaintThreshold = Math.round(Math.min(STAMP_RENDER_REPAINT_COUNT_MAX,
		                                            Math.max(STAMP_RENDER_REPAINT_COUNT_MIN,
		                                                     STAMP_RENDER_REPAINT_COUNT_BASE - 
		                                                     Math.log(renderPPD) / Math.log(2))));
		log.println("Repainting every " + repaintThreshold + " images");

    	Runnable runme = new Runnable() {    		
			public void run() {

				long repaintCount = 0;

				clearOffScreen(0);
				
	            if (stampLayer.getSettings().getFilledStampColor().getAlpha() != 0)
	                drawAlpha();
	            
				ArrayList<FilledStamp> reverseStamps = new ArrayList<FilledStamp>();
				
				ArrayList<StampImage> higherStamps=new ArrayList<StampImage>();
				for (FilledStamp fs : filledStamps) 
		        {
					fs.pdsi.calculateCurrentClip(higherStamps);
					
					higherStamps.add(fs.pdsi);
					reverseStamps.add(fs);
		        }

				// Reverse the order of the stamps so that we can iterate through them starting
				// from last to first
				Collections.reverse(reverseStamps);
				
				for (FilledStamp fs : reverseStamps) 
		        {
					Graphics2D g2 = getOffScreenG2();
					
					Point2D offset = fs.getOffset();
					g2.translate(offset.getX(), offset.getY());
	
					if (fs.pdsi != null) {
					    fs.pdsi.renderImage(g2,
					                fs.getColorMapOp().forAlpha(1),
					                proj,
					                renderPPD, stampLayer.startTask());
						if ((repaintCount % repaintThreshold) == 0)
							repaint();
	
						repaintCount++;
	                    
					}
				}
			    
				// Complete repaint of last few images
				repaint();
			}
    	};
		
		Thread renderThread = new Thread(runme);
		renderThread.start();

//            }
        return true;
	}

	class StampMenu extends JMenu {

		StampShape stamp = null;
		
		StampMenu(StampShape stamp) {
			super("Stamp " + stamp.getId());
			this.stamp=stamp;
		}
		
	    public Component[] getMenuComponents() {
	    	if (!initialized) {
	    		initSubMenu();
	    	} 
	    	
	    	return super.getMenuComponents();
	    }
		
		boolean initialized = false;
		
		private synchronized void initSubMenu() {
			if (initialized) return;
			List<String> supportedTypes = stamp.getSupportedTypes();
			
			for (String supportedType : supportedTypes) {
				JMenuItem renderMenu = new JMenuItem("Render " + supportedType);
				final String type = supportedType;
				renderMenu.addActionListener(new ActionListener() {			
					public void actionPerformed(ActionEvent e) {
					    Runnable runme = new Runnable() {
					        public void run() {
					            focusFilled.addStamp(stamp, type);
					        }
					    };
	
				        SwingUtilities.invokeLater(runme);
					}
				});
				add(renderMenu);
			}
			
			if (supportedTypes.size()==0) {
				JMenuItem noRenderOptions = new JMenuItem("Sorry - there are no rendering options available for this stamp");
				noRenderOptions.setEnabled(false);
				add(noRenderOptions);
			}
			
			JMenuItem webBrowse = new JMenuItem("Web browse " + stamp.getId());
			
			webBrowse.addActionListener(new ActionListener() {			
				public void actionPerformed(ActionEvent e) {
					browse(stamp);
				}			
			});				
			
			add(webBrowse);

			if (stampLayer.getInstrument().equalsIgnoreCase("hirise")) {
				JMenuItem webBrowse2 = new JMenuItem("Launch IAS Viewer for " + stamp.getId());
				
				webBrowse2.addActionListener(new ActionListener() {			
					public void actionPerformed(ActionEvent e) {
						quickView(stamp);
					}			
				});				
				
				add(webBrowse2);
			}
			initialized=true;			
		}
		
	    public MenuElement[] getSubElements() {		
	    	if (!initialized) {
	    		initSubMenu();
	    	}			
	 
	    	return super.getSubElements();
	    }
	    
	    public JPopupMenu getPopupMenu() {
	    	initSubMenu();
	    	return super.getPopupMenu();
	    }
	}
	
	protected Component[] getContextMenuTop(Point2D worldPt)
	{
		List<Component> newItems =
			new ArrayList<Component>( Arrays.asList(super.getContextMenuTop(worldPt)) );

		// See what the user clicked on... leave menu unchanged if nothing.
		List<StampShape> list = findStampsByWorldPt(worldPt);
		if (list == null)
			return null;

		if (list.size()>0) {
		
			JMenu viewMenu = new JMenu("View " + stampLayer.getInstrument() + " Stamps");
			
			for (final StampShape stamp : list)
			{
				StampMenu sub = new StampMenu(stamp);
								
				viewMenu.add(sub);
			}

			newItems.add(0, viewMenu);
		}
		
		// Check for selected stamps and offer option of loading/rendering these.
		final int[] rowSelections = myFocus.getSelectedRows();
		if (rowSelections != null && rowSelections.length > 0) {
			
			JMenu loadSelectedStamps = new JMenu("Render Selected " + stampLayer.getInstrument() + " Stamps");
			
			final List<StampShape> selectedStamps = stampLayer.getSelectedStamps();
			
			List<String> imageTypes = new ArrayList<String>();

			boolean btr=false;
			boolean abr=false;
			
			try {
				String idList="";
				for (StampShape stamp : selectedStamps) {
					idList+=stamp.getId()+",";
				}
				
				if (idList.endsWith(",")) {
					idList=idList.substring(0,idList.length()-1);
				}
				
				String typeLookupStr = StampLayer.stampURL+"ImageTypeLookup";
						
				String data = "id="+idList+"&instrument="+stampLayer.getInstrument()+"&format=JAVA"+stampLayer.getAuthString()+StampLayer.versionStr;
				
				URL url = new URL(typeLookupStr);
				URLConnection conn = url.openConnection();
				conn.setDoOutput(true);
				OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
				wr.write(data);
				wr.flush();
				wr.close();
				
				ObjectInputStream ois = new ObjectInputStream(conn.getInputStream());
															
				List<String> supportedTypes = (List<String>)ois.readObject();
				for (String type : supportedTypes) {
					if (type.equalsIgnoreCase("BTR")) {
						btr=true;
					} else if (type.equalsIgnoreCase("ABR")) {
						abr=true;
					} 
					
					imageTypes.add(type);								
				}
				ois.close();
			} catch (Exception e2) {
				e2.printStackTrace();
			}

			
			if (abr && btr) {
				imageTypes.add(0, "ABR / BTR");
			}
			
			for (final String imageType : imageTypes) {
				JMenuItem renderMenu = new JMenuItem("Render Selected as " + imageType);
				renderMenu.addActionListener(new RenderFunction(stampLayer, focusFilled, imageType));
				loadSelectedStamps.add(renderMenu);
			}
									
					
			newItems.add(0, loadSelectedStamps);
	
			
		    JMenuItem copySelected = new JMenuItem("Copy Selected " + stampLayer.getInstrument() + " Stamps to Clipboard");
		    
		    copySelected.addActionListener(new ActionListener() {			
				public void actionPerformed(ActionEvent e) {
					StringBuffer buf = new StringBuffer();
					
				    for (StampShape stamp : selectedStamps) {
				    	buf.append( stamp.getId() );
				        buf.append('\n');
				    }
				       
				    StringSelection sel = new StringSelection(buf.toString());
				    Clipboard clipboard = ValidClipboard.getValidClipboard();
				    if (clipboard == null)
				        log.aprintln("no clipboard available");
				    else {
				        clipboard.setContents(sel, sel);
				        Main.setStatus("Stamp list copied to clipboard");
				           
				        log.println("stamp list copied: " + buf.toString());
				    }
				}			
			});
		    
		    newItems.add(loadSelectedStamps);		    
		    newItems.add(copySelected);
		}
					
		return  (Component[]) newItems.toArray(new Component[0]);
	}


	public String getToolTipText(MouseEvent event)
	{
		try {
			MultiProjection proj = getProj();
			if (proj == null) {
				log.aprintln("null projection");
				return null;
			}

			Point2D mousePoint = event.getPoint();
			Point2D worldPoint = proj.screen.toWorld(mousePoint);
			
			StampShape stamp = findStampByWorldPt(worldPoint);

			if (stamp != null) {
				if (stampLayer.getInstrument().equalsIgnoreCase("themis")) {
				    {
				        String temperatureStr = null;
				        
				        // Display the stamp and point temperature information if available
				        // for the topmost filled stamp.
			            temperatureStr = getTemperatureStringForPoint(mousePoint);
			            
			            if (temperatureStr != null)
			                return temperatureStr;
				    }					
				} 
				
				return stamp.getPopupInfo(true);
			}
		} 
        catch ( Exception ex) {
			//ignore
		}

		return null;			
	}

    // Returns topmost filled stamp (if any) in the filled stamp list 
    // that is intersected at the specified point in HVector space.
    public FilledStamp getIntersectedFilledStamp(Point2D screenPt)
    {
    	
    	List<FilledStamp> filledStamps = focusFilled.getFilled();
    	
        for (FilledStamp fs : filledStamps)
        {
            HVector ptVect = screenPointToHVector(screenPt, fs);
            
            if (ptVect != null)
            {
                StampImage image = fs.pdsi;
                if (image !=null &&
                    image.isIntersected(ptVect))
                    return fs;
            }
        }
        
        return null;
    }
	
	// Used for populating Chart samples
	public double getValueAtPoint(Point2D samplePoint) {
		
		MultiProjection proj = getProj();
		if (proj == null) {
			log.aprintln("null projection");
			return -1;
		}

		Point2D screenPt=proj.getWorldToScreen().transform(samplePoint,
				 null);
		
        FilledStamp fs = getIntersectedFilledStamp(screenPt);
        if (fs != null &&
            fs.pdsi != null)
        {
            // Only return temperature information for an IR stamp
            if (fs.stamp.getId().startsWith("I") && 
            		(fs.pdsi.imageType.startsWith("PBT")
            		|| fs.pdsi.imageType.startsWith("BTR")))
            {
                HVector screenVec = screenPointToHVector(screenPt, fs);
                Point2D imagePt = ((PdsImage)fs.pdsi).getImagePt(screenVec);
                
                if (imagePt != null)
                {
                    double temp = ((PdsImage)fs.pdsi).getTemp(imagePt);
                    return temp;
                }
            }
        }

		return Double.NaN;
	}
	
    protected String getTemperatureStringForPoint(Point2D screenPt)
    {
        String str = null;
        
        FilledStamp fs = getIntersectedFilledStamp(screenPt);
        if (fs != null &&
            fs.pdsi != null)
        {
            log.println("Got filled stamp for point");
            
            // Only return temperature information for an IR stamp
            if (fs.stamp.getId().startsWith("I") && 
            		(fs.pdsi.imageType.startsWith("PBT")
            		|| fs.pdsi.imageType.startsWith("BTR")))
            {
                HVector screenVec = screenPointToHVector(screenPt, fs);
                Point2D imagePt = ((PdsImage)fs.pdsi).getImagePt(screenVec);
                
                if (imagePt != null)
                {
                    StringBuffer buf = new StringBuffer();
                    double temp = ((PdsImage)fs.pdsi).getTemp(imagePt);
                    
                       buf.append("<html>");
                    
                    buf.append("ID: ");
                    buf.append(fs.stamp.getId());
                    
                       buf.append("<br>");
                    
                    buf.append("Temp(K): ");
                    
                    DecimalFormat f = new DecimalFormat("0.0");
                    buf.append(f.format(temp));
                    
                       buf.append("</html>");
                    
                    str = buf.toString();
                    
                    log.println("Got temp(K) for point: " + str);
                }
            }
            else
            {
                // Return just a stamp ID label for non-IR stamps
                StringBuffer buf = new StringBuffer();
                
                   buf.append("<html>");
                
                buf.append("ID: ");
                buf.append(fs.stamp.getId());
                
                   buf.append("</html>");
                
                str = buf.toString();
                
                log.println("Create label for non-IR stamp point: " + str);
            }
            
        }
        
        return str;
    }
	
	public void browse(StampShape stamp)
	{
		String url = null;
		
		try {
			String browseLookupStr = StampLayer.stampURL+"BrowseLookup?id="+stamp.getId()+"&instrument="+stampLayer.getInstrument()+"&format=JAVA"+stampLayer.getAuthString()+StampLayer.versionStr;
					
			ObjectInputStream ois = new ObjectInputStream(new URL(browseLookupStr).openStream());
			
			url = (String)ois.readObject();				
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (url == null) {
		    JOptionPane.showMessageDialog(
                    Main.mainFrame,
                    "Sorry - that browse page is not currently available",
                    "JMARS",
                    JOptionPane.INFORMATION_MESSAGE);			
			return;
		}
        
        // Check for custom browser program chosen by user.  If
        // present, try it.  If it fails, log error and try default
        // browser launch instead.
        boolean customBrowseOK = false;
        String browseCmd = Config.get(StampLayer.CFG_BROWSER_CMD_KEY, null);
        
        if (browseCmd != null &&
            browseCmd.length() > 0)
        {
            int index = browseCmd.toLowerCase().indexOf(StampLayer.URL_TAG.toLowerCase());
            if (index < 0)
                log.aprintln("Missing webpage placeholder " + StampLayer.URL_TAG +
                             " in custom browser command");
            else {
                // Replace the url placeholder in case-insensitive fashion with
                // the webpage reference.  Try to launch custom browser with webpage.
                browseCmd = browseCmd.substring(0, index) + url + 
                            browseCmd.substring(index + StampLayer.URL_TAG.length());
                try {
                    Runtime.getRuntime().exec(browseCmd);
                    customBrowseOK = true;
                    log.aprintln(url);
                }
                catch (Exception e) {
                    log.println(e);
                    log.aprintln("Custom webbrowser command '" + browseCmd + "' failed: " +
                                 e.getMessage());
                    log.aprint("Will launch default webbrowser instead");
                }
            }
        }
        
        if (!customBrowseOK)
    		Util.launchBrowser(url);
	}

	void quickView(StampShape stamp)
	{
		String url = null;
		
		try {
			String browseLookupStr = StampLayer.stampURL+"BrowseLookup?id="+stamp.getId()+"&instrument="+stampLayer.getInstrument()+"&format=JAVA"+stampLayer.getAuthString()+StampLayer.versionStr;
					
			ObjectInputStream ois = new ObjectInputStream(new URL(browseLookupStr).openStream());
			
			url = (String)ois.readObject();
			url = (String)ois.readObject();				

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (url == null)
		    JOptionPane.showMessageDialog(
		                                  Main.mainFrame,
		                                  "Can't determine URL for stamp: "
		                                  + stamp.getId(),
		                                  "JMARS",
		                                  JOptionPane.INFORMATION_MESSAGE);
        
        // Check for custom browser program chosen by user.  If
        // present, try it.  If it fails, log error and try default
        // browser launch instead.
        boolean customBrowseOK = false;
        String browseCmd = Config.get(StampLayer.CFG_BROWSER_CMD_KEY, null);
        
        if (browseCmd != null &&
            browseCmd.length() > 0)
        {
            int index = browseCmd.toLowerCase().indexOf(StampLayer.URL_TAG.toLowerCase());
            if (index < 0)
                log.aprintln("Missing webpage placeholder " + StampLayer.URL_TAG +
                             " in custom browser command");
            else {
                // Replace the url placeholder in case-insensitive fashion with
                // the webpage reference.  Try to launch custom browser with webpage.
                browseCmd = browseCmd.substring(0, index) + url + 
                            browseCmd.substring(index + StampLayer.URL_TAG.length());
                try {
                    Runtime.getRuntime().exec(browseCmd);
                    customBrowseOK = true;
                    log.aprintln(url);
                }
                catch (Exception e) {
                    log.println(e);
                    log.aprintln("Custom webbrowser command '" + browseCmd + "' failed: " +
                                 e.getMessage());
                    log.aprint("Will launch default webbrowser instead");
                }
            }
        }
        
        if (!customBrowseOK)
    		Util.launchBrowser(url);
	}


	private List<StampShape> findStampsByWorldPt(Point2D worldPt)
	{
		if (viewman == null) {
			log.aprintln("view manager not available");
			return null;
		}

		MultiProjection proj = viewman.getProj();
		if (proj == null) {
			log.aprintln("null projection");
			return null;
		}

		Dimension2D pixelSize = proj.getPixelSize();
		if (pixelSize == null) {
			log.aprintln("no pixel size");
			return null;
		}

		double w = proximityPixels * pixelSize.getWidth();
		double h = proximityPixels * pixelSize.getHeight();
		double x = worldPt.getX() - w/2;
		double y = worldPt.getY() - h/2;

		return findStampsByWorldRect(new Rectangle2D.Double(x, y, w, h));
	}

	private List<StampShape> findStampsByWorldRect(Rectangle2D proximity)
	{
		if (stamps == null || proximity == null)
			return null;

		List<StampShape> list = new ArrayList<StampShape>();
		double w = proximity.getWidth();
		double h = proximity.getHeight();
		double x = proximity.getX();
		double y = proximity.getY();

		x -= Math.floor(x/360.0) * 360.0;

		Rectangle2D proximity1 = new Rectangle2D.Double(x, y, w, h);
		Rectangle2D proximity2 = null;
		log.println("proximity1 = " + proximity1);

		// Handle the two cases involving x-coordinate going past
		// 360 degrees:
		// Proximity rectangle extends past 360...
		if (proximity1.getMaxX() >= 360) {
			proximity2 = new Rectangle2D.Double(x-360, y, w, h);
			log.println("proximity2 = " + proximity2);
		}
		// Normalized stamp extends past 360 but
		// proximity rectangle does not...
		else if (proximity1.getMaxX() <= 180) {
			proximity2 = new Rectangle2D.Double(x+360, y, w, h);
			log.println("proximity2 = " + proximity2);
		}

		// Perform multiple proximity tests at the same time
		// to avoid re-sorting resulting stamp list.
		for (int i=0; i<stamps.length; i++) {
			Shape shape = stamps[i].getNormalPath();
			Rectangle2D stampBounds = shape.getBounds2D();
			
			// Do a fast compare with the Rectangle bounds, then do a second
			// more accurate compare if the areas overlap.
			if (stampBounds.intersects(proximity1) ||
        		( proximity2 != null && stampBounds.intersects(proximity2)))
			{
				if (shape.intersects(proximity1) ||
		        		( proximity2 != null && shape.intersects(proximity2)))
					list.add(stamps[i]);				
			}
		}

		return list;
	}

	private StampShape findStampByScreenPt(Point screenPt)
	{
		MultiProjection proj = getProj();
		if (proj == null) {
			log.aprintln("null projection");
			return null;
		}

		Point2D worldPt = proj.screen.toWorld(screenPt);

		return  findStampByWorldPt(worldPt);
	}

	private StampShape findStampByWorldPt(Point2D worldPt)
	{
		if (stamps == null)
			return null;

		if (viewman == null) {
			log.aprintln("view manager not available");
			return null;
		}

		MultiProjection proj = viewman.getProj();
		if (proj == null) {
			log.aprintln("null projection");
			return null;
		}

		Dimension2D pixelSize = proj.getPixelSize();
		if (pixelSize == null) {
			log.aprintln("no pixel size");
			return null;
		}

		double w = proximityPixels * pixelSize.getWidth();
		double h = proximityPixels * pixelSize.getHeight();
		double x = worldPt.getX() - w/2;
		double y = worldPt.getY() - h/2;

		x -= Math.floor(x/360.0) * 360.0;

		Rectangle2D proximity1 = new Rectangle2D.Double(x, y, w, h);
		Rectangle2D proximity2 = null;
		log.println("proximity1 = " + proximity1);

		// Handle the two cases involving x-coordinate going past
		// 360 degrees:
		// Proximity rectangle extends past 360...
		if (proximity1.getMaxX() >= 360) {
			proximity2 = new Rectangle2D.Double(x-360, y, w, h);
			log.println("proximity2 = " + proximity2);
		}
		// Normalized stamp extends past 360 but
		// proximity rectangle does not...
		else if (proximity1.getMaxX() <= 180) {
			proximity2 = new Rectangle2D.Double(x+360, y, w, h);
			log.println("proximity2 = " + proximity2);
		}

		for (int i=0; i<stamps.length; i++)
			if (stamps[i].getNormalPath().intersects(proximity1) ||
			    ( proximity2 != null && stamps[i].getNormalPath().intersects(proximity2)))
				return  stamps[i];

		return  null;
	}

	protected Object createRequest(Rectangle2D where)
	{
		return  where;
	}

	protected Layer.LView _new()
	{
		return new StampLView((StampLayer)getLayer(), null, true);
	}

	public Layer.LView dup()
	{
		StampLView copy = (StampLView) super.dup();

		copy.stamps = this.stamps;

		return  copy;
	}

	public void selectionsChanged() {
        selectedStamps = ((StampLayer)getLayer()).getSelectedStamps();
        
		drawSelections(selectedStamps, true);
	}
	
	public void selectionsAdded(List<StampShape> newStamps) {
		selectedStamps.addAll(newStamps);
		drawSelections(newStamps, false);
	}
	
    /**
     * Draws outlines for stamp selections.  This reside in their own
     * buffer layer.
     * <p>
     * If a redraw is requested, but no stamps are specified, then any
     * existing selected stamp outlines are cleared.
     * 
     * @param ss        List of stamps to be drawn (partial or all);
     * may be <code>null</code> (useful for clearing all selections
     * in combination with <code>redraw</code> parameter.
     * 
     * @param redraw    If <code>true</code>, then drawn selections are
     * cleared and are completely redrawn using the specified stamps as
     * the complete selection list.  Otherwise, buffer is not cleared
     * and the stamp list represents a partial selection/deselection.
     */
	private void drawSelections(List<StampShape> selectedStamps, boolean redraw)
	{
		if (getChild()!=null) {
			((StampLView)getChild()).drawSelections(selectedStamps, redraw);
		}
	    if (redraw)
            clearOffScreen(SELECTED_OUTLINE_BUFFER_IDX);
            
		if (selectedStamps == null || selectedStamps.size() < 1) {
			repaint();
		    return;
        }

		Graphics2D g2 = getOffScreenG2(SELECTED_OUTLINE_BUFFER_IDX);
		if (g2 == null) {
			return;
		}
        
        g2.setComposite(AlphaComposite.Src);
        g2.setStroke(new BasicStroke(0));
		g2.setColor(new Color(~stampLayer.getSettings().getUnselectedStampColor().getRGB()));

        for (StampShape selectedStamp : selectedStamps) {
			g2.draw(selectedStamp.getPath());
        }
        repaint();
	}

	public void panToStamp(StampShape s)
	{
	    centerAtPoint(new Point2D.Double(s.getBounds2D().getCenterX(),
	                                     s.getBounds2D().getCenterY()));
	    
		stampLayer.clearSelectedStamps();
		stampLayer.addSelectedStamp(s);
	}

	/**
	 *  Called to restore rendered stamps during view restoration after a program 
	 *  restart.  Only one successful call is allowed.
	 *
	 * @param stampStateList  List of stamp IDs to be restored and related state
	 *                        information.
	 */
	protected synchronized void restoreStamps(FilledStamp.State[] stampStateList)
	{
		log.println("entering");

		if (stampStateList != null &&
		    !restoreStampsCalled)
		{
			restoreStampsCalled = true;

			// Add stamps to focus panel's list in reverse order;
			// the addStamp operation has a push-down behavior and
			// we want to reestablish the same order as in the list
			// of stamp IDs.
			if (focusFilled != null) {
				log.println("processing stamp ID list of length " + stampStateList.length);
				log.println("with view stamp list of length " + (stamps == null ? 0 : stamps.length));
                
                StampShape[] stampList = new StampShape[stampStateList.length];
				FilledStamp.State[] stateList = new FilledStamp.State[stampStateList.length];

                StampLayer layer = (StampLayer)getLayer();
                int count = 0;
				for (int i=stampStateList.length - 1; i >= 0; i--) {
					log.println("looking for stamp ID " + stampStateList[i].id);

					StampShape s = layer.getStamp(stampStateList[i].id.trim());
					if (s != null) {
						stampList[count] = s;
						stateList[count] = stampStateList[i];

						count++;
						log.println("found stamp ID " + stampStateList[i].id);
					}
				}

				addSelectedStamps(stampList, stateList);
				log.println("actually loaded " + count + " stamps");
			}
		}

		log.println("exiting");
	}

	/** Adds specified stamps from stamp table list.
	 **
	 ** @param rows                    selected stamp indices in using *unsorted* row numbers
	 **/
	protected void addSelectedStamps(final int[] rows)
	{
	    if (rows != null &&
            rows.length > 0)
	    {
	        StampShape[] selectedStamps = new StampShape[rows.length];
	        for (int i=0; i < rows.length; i++)
	            if (rows[i] >= 0)
	                selectedStamps[i] = stamps[rows[i]];
	            
	        addSelectedStamps(selectedStamps, null);
	    }
	}

	/** Adds specified stamps from stamp table list.
	 **
	 ** @param selectedStamps          selected stamps to be added
	 ** @param stampStateList          stamp state information array with elements corresponding 
	 **                                    to order of indices in rows parameter; may be 'null'.
	 **/
	protected void addSelectedStamps(final StampShape[] selectedStamps,
	                                 final FilledStamp.State[] stampStateList)
	{
		if (selectedStamps != null && selectedStamps.length > 0)
			{
			
			
				
				final Runnable runner = new Runnable() {
					public void run() {
						focusFilled.addStamps(selectedStamps, stampStateList, null);						
					}
				};

				try {
					Thread t = new Thread(runner);
					t.start();
				} catch (Exception e) {
					log.aprintln(e);
				}
			}
	}
	
	// Used to draw all loaded stamps at once; useful after adding
	// multiple stamps.  Displays a progress dialog during a group image
	// frame creation process to bridge the time gap before the image
	// projection progress dialog is displayed.
	protected void drawStampsTogether()
	{
	    if (focusFilled != null) {
	        if (viewman == null) {
	            log.aprintln("view manager not available");
	            return;
	        }
	        
	        redrawEverything(true);
	    }
	}

	class MouseHandler implements MouseInputListener {
		protected void drawSelectionRect(Rectangle2D rect)
		{
			Graphics2D g2 = (Graphics2D) getGraphics();
			if (g2 != null) {
				g2.setStroke(new BasicStroke(2));
				g2.setXORMode(Color.gray);
				g2.draw(rect);

				log.println("drawing rectangle (" + rect.getMinX() + "," + rect.getMinY()+ ") to (" 
					    + rect.getMaxX() + "," + rect.getMaxY() + ")");
			}
		}

		// These three methods are required to implement the interface
		public void mouseEntered(MouseEvent e){};
		public void mouseExited(MouseEvent e){} ;
		public void mouseMoved(MouseEvent e){};
		
		public void mouseClicked(MouseEvent e)
		{
			// Pass thru for profile cueing
			if (e.isShiftDown()) {
				return;
			}
			
			if (stamps != null) {			
				StampShape stamp = findStampByScreenPt(e.getPoint());
				if (myFocus != null){
					if (!e.isControlDown()) {
						stampLayer.clearSelectedStamps();
					}
					if (stamp!=null) {
						stampLayer.toggleSelectedStamp(stamp);
					}
				}
			}
		}

	    protected boolean mouseDragged = false;
		protected Point mouseDown = null;
		protected Rectangle2D curSelectionRect = null;

		public void mousePressed(MouseEvent e)
		{
			// Pass thru for profile cueing
			if (e.isShiftDown()) {
				return;
			}

			// Initial drawing of rubberband stamp selection box.
			mouseDown = ((WrappedMouseEvent)e).getRealPoint();

			curSelectionRect = new Rectangle2D.Double(mouseDown.x, mouseDown.y, 0, 0);
			drawSelectionRect(curSelectionRect);
	        mouseDragged = false;
		}

		public void mouseDragged(MouseEvent e)
		{
			// Pass thru for profile cueing
			if (e.isShiftDown()) {
				return;
			}


			// Update drawing of rubberband stamp selection box.
			if (curSelectionRect != null && mouseDown != null) {
				Point curPoint = ((WrappedMouseEvent)e).getRealPoint();

				drawSelectionRect(curSelectionRect);
				curSelectionRect.setRect(mouseDown.x, mouseDown.y, 0, 0);
				curSelectionRect.add(curPoint);
				drawSelectionRect(curSelectionRect);
	            mouseDragged = true;
			}
		}

		public void mouseReleased(final MouseEvent e)
		{ 
			// Pass thru for profile cueing
			if (e.isShiftDown()) {
				return;
			}

			// Select stamps inside rubberband stamp selection box.
			if (mouseDragged && curSelectionRect != null && mouseDown != null) {
				drawSelectionRect(curSelectionRect);

	            StampTask task = ((StampLayer)getLayer()).startTask();
	            task.updateStatus(Status.YELLOW);
	            
	            getFocusPanel().repaint();
	            
	            MultiProjection proj = getProj();
	            if (proj == null) {
	                log.aprintln("null projection");
	                return;
	            }
	            
	            Point curPoint = ((WrappedMouseEvent)e).getRealPoint();
	            
	            Point2D worldPt1 = proj.screen.toWorld(mouseDown);
	            Point2D worldPt2 = proj.screen.toWorld(curPoint);
	            
	            double offset = Main.PO.getServerOffsetX();
	            
	            worldPt1.setLocation(worldPt1.getX() + offset, worldPt1.getY());
	            
	            worldPt2.setLocation(worldPt2.getX() + offset, worldPt2.getY());
	            
	            final Rectangle2D worldBounds = new Rectangle2D.Double(worldPt1.getX(), worldPt1.getY(), 0, 0);
	            worldBounds.add(worldPt2);
	            
	            List<StampShape> selectedList = findStampsByWorldRect(worldBounds);
                        		
        		if (!e.isControlDown()) {
        			stampLayer.clearSelectedStamps();
        		}
        		stampLayer.toggleSelectedStamps(selectedList);
//        		stampLayer.addSelectedStamps(selectedList);
        		
        		task.updateStatus(Status.DONE);
        		getFocusPanel().repaint();

	            mouseDragged = false;
				mouseDown = null;
				curSelectionRect = null;
			}
		}

		
	}
	
    // Converts a screen position (e.g., mouse position) to an HVector
    // coordinate that includes correction for the offset shift of a
    // filled stamp at the current pixel resolution.
    protected HVector screenPointToHVector(Point2D screenPt, FilledStamp fs)
    {
        HVector vec = null;
        
        if (screenPt != null &&
            fs != null)
        {
            Point2D worldPt = getProj().screen.toWorld(screenPt);
            Point2D offset = fs.getOffset();
            
            worldPt.setLocation( worldPt.getX() - offset.getX(),
                                 worldPt.getY() - offset.getY());
            
            vec = getProj().world.toHVector(worldPt);
        }
        
        return vec;
    }

    
    public void requestFocus() {
    	requestFocusInWindow(true);
    }

	public void viewCleanup()
	{
		stamps=null;

		filled=null;
		
		selectedStamps.clear();
		
	    lastStamps=null;

	    if (focusFilled!=null) {
	    	focusFilled.dispose();
	    }
	    
	    if (myFocus!=null) {
	    	myFocus.dispose();
	    }
	    
		stampLayer.dispose();	
	}
} // end: class StampLView



