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

import edu.asu.jmars.*;
import edu.asu.jmars.graphics.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import edu.asu.jmars.swing.ValidClipboard;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;


/**
 * Base view implementation for stamp image layer.  Unless subclassed, it
 * supports EDR versions of THEMIS IR/VIS stamps (this functionality is
 * still implemented but no longer actively used in any released JMARS 
 * software versions).
 * <p>
 * Subclasses must override the {@link #createFilledStampFocus}
 * method to create filled-stamp UI panels which support loading of
 * images other than that supported by {@link PdsImage}.
 * 
 * @see edu.asu.jmars.layer.stamp.PdsImageFactory
 */
public class StampLView extends Layer.LView implements MouseInputListener
{
	private static final String VIEW_SETTINGS_KEY = "stamp";
	private static final String CFG_BROWSER_CMD_KEY = "stamp_browser";
	private static final String URL_TAG = "%URL%";
	private static final int MIN_TEXT_COLUMNS = 35;
	private static final String STAMP_COLUMN_NAME = "_stamp";

	private static DebugLog log = DebugLog.instance();

	private static final int proximityPixels = 8;
	private static final int STAMP_RENDER_REPAINT_COUNT_MIN = 1;
	private static final int STAMP_RENDER_REPAINT_COUNT_MAX = 10;
	private static final int STAMP_RENDER_REPAINT_COUNT_BASE = 10;

	private static final int MILLIS_TO_DECIDE_TO_POPUP = 300;
	private static final int MILLIS_TO_POPUP = 700;
    
    private static final int OUTLINE_BUFFER_IDX = 1;
    private static final int SELECTED_OUTLINE_BUFFER_IDX = 2;
    
    protected static final Color NO_SEL_COLOR = new Color(0, 0, 0, 0);

	protected Object stampsLock = new Object();
	protected Object stampsArrivedLock = new Object();
	protected Object drawLock = new Object();
	protected Object redrawLock = new Object();
	protected Stamp[] stamps;
	protected String name;
	protected String[] initialColumns;

	protected Color unsColor;
	protected Color selColor;
	protected Color filColor;
	protected boolean hideOutlines = false;

	private Object receiveTimeLock = new Object();
	private long lastReceiveTime = 0;
	private FilledStamp[] filled;
	private Stamp selectedStamps[];
    private boolean ignoreTableSelectionChanges = false;
    private boolean dualSelectionInProgress = false;
    
    // stamp outline drawing state
    private Stamp[] lastStamps;
    private Color lastUnsColor;
    private int projHash = 0;

	protected MyFilledStampFocus focusFilled = null;
	protected boolean restoreStampsCalled = false;

	protected StampParameters initialParms = new StampParameters();
	protected StampSettings settings = new StampSettings();
	protected boolean settingsLoaded = false;


	protected StampLView(Layer parent, StampFactory factory)
	{
		super(parent);
		originatingFactory = factory;
        setBufferCount(3);

        focusFilled = createFilledStampFocus();
    }

	public StampLView(String name,
	                  Layer parent,
	                  String[] initialColumns,
	                  Color unsColor, 
	                  StampFactory factory)
	{
		this(name, parent, initialColumns, unsColor, 
		     new Color(unsColor.getRGB() & 0xFFFFFF, true),
		     factory);
		storeParms(parent);
	}

	public StampLView(String name,
	                  Layer parent,
	                  String[] initialColumns,
	                  Color unsColor,
	                  Color filColor, 
	                  StampFactory factory)
	{
		super(parent);
		originatingFactory = factory;
        setBufferCount(3);

        focusFilled = createFilledStampFocus();
	    
		this.name = name;
		this.initialColumns = initialColumns;
		this.unsColor = unsColor;
		this.filColor = filColor;
		this.selColor = new Color(~unsColor.getRGB());
		addMouseListener(this);
		addMouseMotionListener(this);
		storeParms(parent);
	}

	public final String getName()
	{
		return  name;
	}
    
    /**
     * In student version of JMARS, the stamp view should initially
     * be disabled in the Main Window; for all other versions,
     * it is enabled.
     */
    public boolean mainStartEnabled()
    {
        if (Main.isStudentApplication())
            return false;
        else
            return true;
    }
    
    /**
     * In student version of JMARS, the stamp view should initially
     * be disabled in the Panner Window; for all other versions,
     * it is enabled.
     */
    public boolean pannerStartEnabled()
    {
        if (Main.isStudentApplication())
            return false;
        else
            return true;
    }
    
    /**
     * Replaces current stamp layer associated with view with a new
     * layer defined by the specified SQL query parameters and layer
     * description.
     * 
     * @param sqlParms SQL-related description of layer including
     * query and cache-database-related parameters.
     * @param layerDesc Description of layer for use with {@link StampFactory.AddLayerWrapper}.
     *
     * @return Returns <code>true</code> if the replacment layer is different
     * than the old layer; otherwise, <code>false</code> is returned.
     */
    synchronized protected boolean replaceLayer(SqlStampLayer.SqlParameters sqlParms,
                                                StampFactory.LayerDescription layerDesc)
    {
        boolean changed = false;
        
        StampFactory factory = (StampFactory)originatingFactory;
        if (factory == null) {
            log.aprintln("null factory reference");
            return false;
        }
        
        Layer newLayer = factory.getLayer(sqlParms, layerDesc);
        if (newLayer == null)
            log.aprintln("new layer is null");
        else if (newLayer != getLayer()) {
            // set layer in view, etc.
            setLayer(newLayer);
            storeParms(newLayer);
            
            // Update child
            StampLView childLView = (StampLView)getChild();
            if (childLView != null)
                childLView.replaceLayer(sqlParms, layerDesc);
            
            changed = true;
        }
        
        return changed;
    }

	// Override to create own subclasses of MyFilledStampFocus
	//  as part of this classes constructor.
	protected MyFilledStampFocus createFilledStampFocus()
	{
        // By default, an old-style THEMIS layer (EDR, not BTR/ABR)
        // image factory is used in the focus panel.
		return new MyFilledStampFocus(this, new PdsImageFactory());
	}

	/**
	 * Override to handle special needs.
	 */
	public SerializedParameters getInitialLayerData()
	{
		// Recopy data needed to create initial layer/view.
		storeParms(null);
		return initialParms;
	}


	protected MyFocus myFocus;

	// Note: this method is not synchronized so as to prevent
	// certain deadlock conditions in the AWT event thread.  As
	// implemented, an instance of the LManager class will
	// always be the first to call this method.
	public FocusPanel getFocusPanel()
	{
		if (focusPanel == null)
			focusPanel =
				new DelayedFocusPanel()
				{
					private boolean calledOnceAlready = false;

					public JPanel createFocusPanel()
					{
					    // SUMMARY:  The code below prevents a classic 
					    // two-resource deadlock problem, one in which
					    // one thread locks resources in the order A-B, 
					    // and another thread locks in the order B-A.  
					    // There is no way to alter this ordering without 
					    // re-architecting the entire stamp layer/view, 
					    // hence the kludgey solution below.
					    //
						// The implementation of the synchronized block
						// below prevents race conditions from having
						// any negative effect.  This conditional is
						// tested outside the synchronization block so
						// as to prevent deadlocks in the AWT event thread.
						//
						// Technically, there is still a potential deadlock
						// scenario if this method had never been called
						// before the *first* time the AWT event thread
						// calls it.  In practice, this circumstance does
						// not happen (not a happy answer, but the other
						// resource involved in the deadlock we are
						// preventing is the AWT event queue).
						if (calledOnceAlready)
							return myFocus;
						synchronized(StampLView.this)
						{
							if (myFocus == null) {
							    myFocus = new MyFocus(StampLView.this, (StampLView) getChild());
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
						    settings != null)
						{
							log.println("calling restoreStamps from createFocusPanel");
							restoreStamps(settings.stampStateList);
						}
					}
				}
				;

		return  focusPanel;
	}

	// Forces creation if it's been delayed
	protected void createFocusPanel()
	{
		( (DelayedFocusPanel) getFocusPanel() ).createFocusPanel();
	}

	/**
	 * Override to update view specific settings
	 */
	protected synchronized void updateSettings(boolean saving)
	{
		if (saving){
			log.println("saving settings");

			//
			// MyFilledStampFocus settings
			//
			settings.stampOutlinesHiddenSelected = focusFilled.stampOutlinesHiddenSelected();
			settings.stampSingleImageSelected = focusFilled.stampSingleImageSelected();
			settings.stampStateList = focusFilled.getStampStateList();

			log.println("stamp ID list = ");
			for (int i=0; i < settings.stampStateList.length; i++)
				log.println(settings.stampStateList[i].id);
			log.println("stored " + settings.stampStateList.length + " stamps");

			viewSettings.put(VIEW_SETTINGS_KEY, settings);
		}
		else
		{
			log.println("loading settings");

			if ( viewSettings.containsKey(VIEW_SETTINGS_KEY) )
			{
				settings = (StampSettings) viewSettings.get(VIEW_SETTINGS_KEY);
				if (settings != null)
				{
					log.println("lookup of settings via key succeeded");

					//
					// MyFilledStampFocus settings
					//
					if (focusFilled != null)
					{
						focusFilled.setStampOutlinesHiddenSelected(settings.stampOutlinesHiddenSelected);
						focusFilled.setStampSingleImageSelected(settings.stampSingleImageSelected);

						// Reload and rerender filled stamps; requires that both MyFocus
						// and MyFilledStampFocus panels exist.
						if (settings.stampStateList != null &&
						    myFocus != null)
						{
							log.println("calling restoreStamps from updateSettings");
							restoreStamps(settings.stampStateList);
						}
					}
		    
					settingsLoaded = true;
				}
				else
				{
					log.println("lookup of settings via key failed");
					settings = new StampSettings();
				}
			}
		}
	}


	public void receiveData(Object layerData)
	{
		if (!isAlive())
			return;

		// Check that the view change time of the last processed request/data is 
		// not greater than that of the current request.  If it is, ignore 
		// received data as it is obsolete.
		//
		// Note: If the timestamp is the same, allow processing to
		// proceed.
		long time = getViewChangeTime();

		// Check for valid timestamp
		if (time >= 0)
        {
			synchronized (receiveTimeLock)
            {
				if (time >= lastReceiveTime)
					lastReceiveTime = time;
				else {
					log.println("ABORTED STALE receiveData in "
						    + Thread.currentThread().getName());
					return;
				}
			}
		}
		else
			log.aprintln("received non-view-change call");

		if (layerData instanceof Stamp[])
		{
			// The 'stampsLock' synchronization is large grain here
			// between both parent and child instances to avoid conflicts
			// involving AWT code for both the status pane indicator 
			// and the main stamp list.
			synchronized(stampsLock)
			{
				log.println("STARTED receiveData in "
					    + Thread.currentThread().getName());

				// Check again for a stale data due to possible time delay
				// in getting stampsLock.
				if (time >= 0 &&
				    time < lastReceiveTime) {
				    log.println("ABORTED STALE receiveData in "
				                + Thread.currentThread().getName());
					return;
				}

				getLayer().setStatus(Color.yellow);

				// IMMEDIATELY after setting the stamp data, notify any thread 
				// waiting for the arrival of stamps.
				synchronized(stampsArrivedLock)
				{
					stamps = pruneStamps( (Stamp[]) layerData );
					stampsArrivedLock.notifyAll();
				}

				Runnable r = new Runnable(){
					public void run(){
						createFocusPanel();
						if (myFocus != null)
						{
							dualSelectionInProgress = true;
							myFocus.table.dataRefreshed();
							dualSelectionInProgress = false;
						}

						// It is not necessary to redraw child view (if any)
						// in this context as both Main and Panner views
						// receive data separately for view changes, etc.
						focusFilled.performRedrawAll(null, false);

						log.println("STOPPED receiveData in "
						            + Thread.currentThread().getName());
						getLayer().setStatus(Util.darkGreen);
						repaint();
					}
				};
				SwingUtilities.invokeLater(r);
			}
		}
		else
			log.aprintln("BAD DATA CLASS: " + layerData.getClass().getName());
	}

    protected void viewChangedPost()
    {
        // Check that the view change time of the last processed request/data is 
        // not greater than that of the current request.  If it is, this is a
        // stale call.
        long time = getViewChangeTime();
        if (time >= 0 &&
            time < lastReceiveTime)
            return;
        
        // If a data-update occurred while the stamp view was not visible,
        // then undo the dirty-clearing effects which normally occur in
        // Layer.LView's view-change-process (after a call-chain which
        // includes receiveData() method).
        //
        // This fixes an error in the Student version of JMARS, which
        // starts with stamp view disabled in both main and panner
        // windows.  Without this fix, simply turning Main/Panner on
        // is not enough to see stamp outlines displayed (a manual
        // window resize is necessary).
        if (!isVisible())
            setDirty(true);
    }

	private Stamp[] pruneStamps(Stamp[] stamps)
	{
		if (!Main.inTimeProjection())
			return  stamps;

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

		// These basically function as pointers... long story, but
		// they must be arrays, and they must be final.
		final boolean[] visible = { false };

		// This looks really silly, I know, but it's the most
		// efficient and convenient way to re-use the necessary logic
		// from the SpatialGraphicsSpOb class without a LOT of work.
		Graphics2D fakeG2 = new SpatialGraphicsSpOb(
			new Graphics2DAdapter()
			{
				public void draw(Shape sh) // sh is ALWAYS a Line2D object
				{
					visible[0] = true;
				}
			},
			getProj(),
			true
			);

		List pruned = new ArrayList(stamps.length);
		for (int i=0; i<stamps.length; i++)
		{
			fakeG2.draw(stamps[i].getPath());
			if (visible[0])
			{
				pruned.add(stamps[i]);
				visible[0] = false;
			}
		}

		return  (Stamp[]) pruned.toArray(new Stamp[0]);
	}

	protected void redrawEverything(boolean redrawChild)
	{
		redrawEverything(redrawChild, null);
	}

	protected void redrawEverything(boolean redrawChild, FilledStamp[] newFilled)
	{
		// Synchronization of redrawEverything() calls occurs across
		// all stamp view instances that are created via the dup() method
		// on a parent view instance.  This addresses some race conditions
		// between the Main and Panner windows as well as within the Panner
		// window (such as a receiveData() call that causes a redraw that
		// overlaps with the Main window's StampLView instance calling the Panner's
		// StampLView instance as a child redraw).
		synchronized(redrawLock)
		{
			if (newFilled != null)
				filled = newFilled;

			// In the case where a redraw occurs during a stamp view
			// restore at startup, it is possible for a redrawEverthing()
			// call to be made to the child (Panner) before it has 
			// received its own first copy of stamp data via receiveData().
			// Don't redraw in this case as the pending/future receiveData()
			// call for the child will eventually carry out its own redraw.
			if (stamps != null) {
				getLayer().setStatus(Color.yellow);
                
                // Check for new stamps since last redraw.  If present,
                // then only draw the new stamps in the window.
                //
                // New stamps are always displayed on top due to the add order.
                //
                Stamp[] newStamps = focusFilled.getNewStamps();
                if (newStamps != null &&
                    newStamps.length > 0)
                {
                    // Draw new filled stamps without altering current
                    // window contents (either alpha-filling, outlines, or previous
                    // filled stamps).
                    //
                    // Clear new stamps status afterwards.
                    drawFilled(getNewFilled(filled, newStamps), false);
                    focusFilled.clearNewStamps();
                }
                else {
                    // Normal redraw: clear window, draw alpha-filling,
                    // draw filled stamps, and then draw outlines.
                    clearOffScreen(0);
                    if (filColor.getAlpha() != 0)
                        drawAlpha();
                    
                    drawFilled();
                    if (!hideOutlines)
                        drawOutlines();
                        
                }
                
				if (redrawChild  &&  getChild() != null)
					((StampLView) getChild()).redrawEverything(false);
				getLayer().setStatus(Util.darkGreen);
				repaint();
			}
		}
	}
    
    /**
     * Returns list of which of the specified filled stamps 
     * are new since the last redraw, if any.  Filled stamps
     * are new if they are included in the specified list of 
     * stamps.
     * 
     * @param fss List of candidate filled stamps.
     * @param ss List of stamps to be used as filter for "new"
     * stamps.
     * 
     * @return Never returns <code>null</code>; zero-length
     * list means no new filled stamps.
     */
    private FilledStamp[] getNewFilled(FilledStamp[] fss, Stamp[] ss)
    {
        FilledStamp[] newFilled = null;
        
        if (fss != null &&
            ss != null &&
            fss.length > 0 &&
            ss.length > 0) 
        {
            List newFilledList = new ArrayList();
            Set newStamps = new HashSet(Arrays.asList(ss));
            
            for (int i=0; i < fss.length; i++)
                if (fss[i] != null &&
                    newStamps.contains(fss[i].stamp))
                    newFilledList.add(fss[i]);
                    
            if (newFilledList.size() > 0)
                newFilled = (FilledStamp[]) newFilledList.toArray(new FilledStamp[0]);
        }
    
        if (newFilled == null)
            newFilled = new FilledStamp[0];
        
        return newFilled;
    }

    /**
     * Draws alpha-filling of stamp regions.
     */
	protected void drawAlpha()
	{
		synchronized(drawLock)
		{
			Graphics2D g2 = getOffScreenG2();
			g2 = spatialG2Hack(g2);

			if (g2 == null)
				return;

			g2.setStroke(new BasicStroke(0));
			g2.setPaint(filColor);
			for (int i=0; i<stamps.length; i++)
			{
				g2.fill(stamps[i].getPath());

				if (i % 1000 == 999)
				{
					getLayer().setStatus(Color.yellow);
					repaint();
				}
			}
            
			repaint();
		}
	}

    /**
     * Draws stamp outlines (not selected stamps) in window from existing 
     * offscreen buffer contents if available and not stale, i.e., consistent 
     * with the current projection, outline colors, and in-view stamp list.  
     *
     * @see #drawSelections 
     */ 
    protected void drawOutlines()
    {
        drawOutlines(false);
    }
    
    /**
     * Draws stamp outlines (not selected stamps) in window. 
     * Selected stamp outlines are only drawn if hiding is 
     * not selected.
     * 
     * @param redraw If <code>true</code>, then outlines are redrawn from scratch.  
     * If <code>false</code>, then outlines are only redrawn if the projection, 
     * outline colors, or the in-view stamp list have changed since the last drawing 
     * (or if being drawn for the first time).  Otherwise, outlines are simply drawn 
     * to the screen with existing buffer contents.
     *
     * @see #drawSelections 
     */
	protected void drawOutlines(boolean redraw)
	{
		synchronized(drawLock)
		{
            if (redraw ||
                lastStamps != stamps ||
                lastUnsColor != unsColor ||
                projHash != Main.PO.getProjectionSpecialParameters().hashCode())
            {
                lastStamps = stamps;
                lastUnsColor = unsColor;
                projHash = Main.PO.getProjectionSpecialParameters().hashCode();
                
                // Draw stamp outlines in something other than the
                // primary offscreen buffer so that we can speed up
                // outline on/off toggle.
                clearOffScreen(OUTLINE_BUFFER_IDX);
    			Graphics2D g2 = getOffScreenG2(OUTLINE_BUFFER_IDX);
    			g2 = spatialG2Hack(g2);
    
    			if (g2 == null)
    				return;
    
    			g2.setStroke(new BasicStroke(0));
    			g2.setPaint(unsColor);
    			for (int i=0; i<stamps.length; i++)
    			{
    				g2.draw(stamps[i].getPath());
    
    				if (i % 1000 == 999)
    				{
    					getLayer().setStatus(Color.yellow);
                        if (getBufferVisible(OUTLINE_BUFFER_IDX))
                            repaint();
    				}
    			}
                
            }

            if (getBufferVisible(OUTLINE_BUFFER_IDX))
                repaint();
            
            getLayer().setStatus(Util.darkGreen);
            repaint();
		}
	}

    /**
     * Draws all filled stamps to the primary drawing buffer
     * for this stamp view's window.  Interruption of drawing by
     * stale thread checks is allowed.
     * <p>
     * NOTE:  The caller must clear the drawing buffer contents 
     * if it is desired that the specified filled stamps be the 
     * only ones displayed and/or to be certain of a pristine state.
     * 
     * @return Returns <code>true</code> if drawing of stamps completed
     * without interruption because of current redraw thread becoming
     * stale (i.e., stale call to receiveData()); returns <code>false</code>
     * if drawing of stamps was interrupted.
     * 
     * @see #drawFilled(FilledStamp[])
     */
    private boolean drawFilled()
    {
        return drawFilled(filled, false);
    }
    
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
     * @param filled List of filled stamps to be displayed.
     * @param noInterruption If <code>true</code>, then stamps
     * will be drawn without any interruption by stale thread
     * checks; otherwise, interruption is possible.
     * 
     * @return Returns <code>true</code> if drawing of stamps completed
     * without interruption because of current redraw thread becoming
     * stale (i.e., stale call to receiveData()) or if there were no
     * filled stamps specifed; returns <code>false</code> if drawing of 
     * stamps was interrupted or if there was some internal error.
     * 
     * @see #drawFilled()
     */
	private boolean drawFilled(FilledStamp[] filled, boolean noInterruption)
	{
		if (viewman == null) {
			log.aprintln("view manager not available");
			return false;
		}

		final int renderPPD = viewman.getMagnification();
		StampProgressMonitor pm = new StampProgressMonitor(this);

		if (filled == null ||
            filled.length < 1)
			return true;

		MultiProjection proj = getProj();
		if (proj == null) {
			log.aprintln("null projection");
			return false;
		}

		int[] times = new int[filled.length];
		for (int i=filled.length-1; i>=0; i--)
			if (filled[i] !=null &&
			    filled[i].band != -1)
				times[i] = filled[i].pdsi.getRenderImageTime(filled[i].band, proj, renderPPD);
		pm.setTimes(times);

		long repaintThreshold = Math.round(Math.min(STAMP_RENDER_REPAINT_COUNT_MAX,
		                                            Math.max(STAMP_RENDER_REPAINT_COUNT_MIN,
		                                                     STAMP_RENDER_REPAINT_COUNT_BASE - 
		                                                     Math.log(renderPPD) / Math.log(2))));
		long repaintCount = 0;
		log.println("Repainting every " + repaintThreshold + " images");

		long time = getViewChangeTime();
		for (int i=filled.length-1; i>=0; i--) 
        {
			// If interruption is allowed, check whether stamp list with which 
            // we are drawing has become stale; if so, abort drawing.  Check for 
            // valid timestamp (we may not be within a thread spawned via the viewChanged()
			// method.)
			if (!noInterruption &&
                time >= 0 &&
			    time < lastReceiveTime)
            {
			    log.println("ABORTED STALE drawFilled "
			                + Thread.currentThread().getName());
				pm.close();
				repaint();
				return false;
			}

			if (filled[i] != null &&
			    filled[i].band != -1)
			{
				Graphics2D g2 = getOffScreenG2();
				
				Point2D offset = filled[i].getOffset();
				g2.translate(offset.getX(), offset.getY());

				if (filled[i].pdsi != null) {
				    filled[i].pdsi.renderImage(
				                               filled[i].band,
				                               g2,
				                               filled[i].getColorMapOp().forAlpha(1),
				                               proj,
				                               pm,
				                               renderPPD);
					if ((repaintCount % repaintThreshold) == 0)
						repaint();

					repaintCount++;
                    
                    if (focusFilled == null ||
                        focusFilled.imageReleaseEnabled())
                        filled[i].pdsi.releaseImage(filled[i].band);
				}
		    
				if (pm.isCanceled())
					break;
			}
		}
	    
		// Complete repaint of last few images
		repaint();
		pm.close();
        
        return true;
	}

	// Implementation is for Themis stamps.  Subclasses
	// must override if they have a different naming convention for
	// stamp names.
	protected boolean validStampName(String name)
	{
		boolean valid = false;

		if (name != null &&
		    (name.startsWith("I") || name.startsWith("V")))
			valid = true;

		return valid;
	}

	protected Component[] getContextMenuTop(Point2D worldPt)
	{
		List newItems =
			new ArrayList( Arrays.asList(super.getContextMenuTop(worldPt)) );

		// See what the user clicked on... leave menu unchanged if nothing.
		final Stamp[] list = findStampsByWorldPt(worldPt);
		if (list == null)
			return null;

		for (int ii=0; ii<list.length; ii++)
		{
			final int i = ii;
			if (!validStampName(list[i].id))
				continue;

			JMenu sub = new JMenu("Stamp " + list[i].id);
			if (!Main.inTimeProjection() &&
                !Main.isStudentApplication())
				sub.add(new JMenuItem(
                						new AbstractAction("Render " + list[i].id)
                						{
                							public void actionPerformed(ActionEvent e)
                							{
                							    Runnable runme =
                							        new Runnable()
                							        {
                    							        public void run() 
                    							        {
                    							            focusFilled.addStamp(list[i]);
                    							        }
                							        };
                
                							    //new Thread(runme).start();
									    SwingUtilities.invokeLater(runme);
                							}
                						}
						));
			sub.add(new JMenuItem(
                					new AbstractAction("Web browse " + list[i].id)
                					{
                						public void actionPerformed(ActionEvent e)
                						{
                							browse(list[i]);
                						}
                					}
					));

			newItems.add(0, sub);
		}

		// Check for selected stamps in non-time-projection mode
		// and offer option of loading/rendering these.
		final int[] rowSelections = myFocus.table.getSelectedRows();
		if (!Main.inTimeProjection() &&
            !Main.isStudentApplication() &&
		    rowSelections != null && 
		    rowSelections.length > 0)
        {
		    newItems.add(
				 new JMenuItem( new AbstractAction("")
				     {
    					 {
    					     if (StampLView.this instanceof BtrStampLView)
    					         putValue(NAME, "Render Selected Stamps");
    					     else
    					         putValue(NAME, "Load Selected Stamps");
    					 }
			    
    					 public void actionPerformed(ActionEvent e)
    					 {
    					     StampLView.this.addSelectedStamps(rowSelections);
    					 }
				     })
				 );
		}


		// Add copy-selected-stamp-IDs menu item.
		if (rowSelections != null && 
		    rowSelections.length > 0)
        {
		    newItems.add(
				 new JMenuItem( 
					       new AbstractAction("Copy Selected Stamp List to Clipboard")
					       {
    						   public void actionPerformed(ActionEvent e)
    						   {
    						       StringBuffer buf = new StringBuffer();
    						       for (int i = 0; i < rowSelections.length; i++) {
    						           buf.append( myFocus.table.getKey(rowSelections[i]) );
    						           buf.append(' ');
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
					       })
				 );
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

			Point2D mouseWorld = proj.screen.toWorld(event.getPoint());
			return getStringForPoint(mouseWorld, true);
		} 
        catch ( Exception ex) {
			//ignore
		}

		return "";
	}

	/**
	 * Handles change to JMARS configuration parameters; such notifications
	 * are primarily sent by instances of {@link LViewManager}.
	 * <p>
	 * If this view has a child view, it notifies the child of the
	 * change.
	 * 
	 * @see Config
	 * @see LViewManager#configChanged()
	 * @see edu.asu.jmars.layer.ConfigListener#configChanged()
	 */
	public void configChanged()
	{
        log.println("Entered: " + originatingFactory.getName());
        
        Runnable runme = 
            new Runnable()
                {
                    public void run()
                    {
                        createFocusPanel();
                        myFocus.updateBrowseCmd();
                        
                        if (getChild() != null)
                            getChild().configChanged();
                    }
                };
                
        SwingUtilities.invokeLater(runme);
	}
	
	/**
	 * Handles change to JMARS configuration parameters that are
	 * specific to a specified view type; such notifications
	 * are primarily sent by instances of {@link LViewManager}.
     * <p>
     * If this view has a child view, it notifies the child of the
     * change.
	 * 
	 * @param cls Class/superclass of which only views that are instances
	 * respond to the configuration change, i.e., StampLView in this case.
	 * 
	 * @see Config
	 * @see LViewManager#configChanged(Class)
	 * @see edu.asu.jmars.layer.ConfigListener#configChanged(Class)
	 */
	public void configChanged(final Class cls)
	{
        log.println("Entered: " + originatingFactory.getName());
        
        // Handle change notification only if this stamp view is an
        // instance of the specified class.
        if (cls != null &&
            cls.isInstance(this))
        {
            Runnable runme = 
                new Runnable()
                    {
                        public void run()
                        {
                            createFocusPanel();
                            myFocus.updateBrowseCmd();
                            
                            if (getChild() != null)
                                getChild().configChanged(cls);
                        }
                    };
                    
            SwingUtilities.invokeLater(runme);
        }
	}

	private void browse(Stamp stamp)
	{
		String url = stamp.getBrowseUrl();
		if (url == null)
		    JOptionPane.showMessageDialog(
		                                  Main.mainFrame,
		                                  "Can't determine URL for stamp: "
		                                  + stamp.id,
		                                  "JMARS",
		                                  JOptionPane.INFORMATION_MESSAGE);
        
        // Check for custom browser program chosen by user.  If
        // present, try it.  If it fails, log error and try default
        // browser launch instead.
        boolean customBrowseOK = false;
        String browseCmd = Config.get(CFG_BROWSER_CMD_KEY, null);
        
        if (browseCmd != null &&
            browseCmd.length() > 0)
        {
            int index = browseCmd.toLowerCase().indexOf(URL_TAG.toLowerCase());
            if (index < 0)
                log.aprintln("Missing webpage placeholder " + URL_TAG +
                             " in custom browser command");
            else {
                // Replace the url placeholder in case-insensitive fashion with
                // the webpage reference.  Try to launch custom browser with webpage.
                browseCmd = browseCmd.substring(0, index) + url + 
                            browseCmd.substring(index + URL_TAG.length());
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

	protected String getStringForPoint(Point2D worldPoint, boolean showAsHTML)
	{
		if (stamps == null)
			return null;

		Stamp stamp = findStampByWorldPt(worldPoint);

		if ( stamp != null )
			return stamp.getPopupInfo(showAsHTML);

		return null;

	}

	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mouseMoved(MouseEvent e) {}

	public void mouseClicked(MouseEvent e)
	{
		if (stamps == null)
		{
			log.aprintln("STAMPS NOT LOADED YET");
			return;
		}
		Stamp stamp = findStampByScreenPt(e.getPoint());
		if (myFocus != null){
			if (!e.isControlDown()) {
				myFocus.table.clearSelection();
			}
			myFocus.table.toggleSelectedStamp(stamp);
		}
	}


    protected boolean mouseDragged = false;
	protected Point mouseDown = null;
	protected Rectangle2D curSelectionRect = null;

	public void mousePressed(MouseEvent e)
	{
		// Initial drawing of rubberband stamp selection box.
		if (Main.inTimeProjection())
			mouseDown = e.getPoint();
		else
			mouseDown = ((WrappedMouseEvent)e).getRealPoint();

		curSelectionRect = new Rectangle2D.Double(mouseDown.x, mouseDown.y, 0, 0);
		drawSelectionRect(curSelectionRect);
        mouseDragged = false;
	}

	public void mouseDragged(MouseEvent e)
	{
		// Update drawing of rubberband stamp selection box.
		if (curSelectionRect != null &&
		    mouseDown != null) {
			Point curPoint;
			if (Main.inTimeProjection())
				curPoint = e.getPoint();
			else
				curPoint = ((WrappedMouseEvent)e).getRealPoint();

			drawSelectionRect(curSelectionRect);
			curSelectionRect.setRect(mouseDown.x, mouseDown.y, 0, 0);
			curSelectionRect.add(curPoint);
			drawSelectionRect(curSelectionRect);
            mouseDragged = true;
		}
	}

	public void mouseReleased(final MouseEvent e)
	{ 
		// Select stamps inside rubberband stamp selection box.
		if (mouseDragged &&
            curSelectionRect != null &&
		    mouseDown != null) 
        {
			drawSelectionRect(curSelectionRect);

            getLayer().setStatus(Color.yellow);
            getFocusPanel().repaint();
            
            MultiProjection proj = getProj();
            if (proj == null) {
                log.aprintln("null projection");
                return;
            }
            
            Point curPoint;
            if (Main.inTimeProjection())
                curPoint = e.getPoint();
            else
                curPoint = ((WrappedMouseEvent)e).getRealPoint();
            
            Point2D worldPt1 = proj.screen.toWorld(mouseDown);
            Point2D worldPt2 = proj.screen.toWorld(curPoint);
            
            double offset = 0;
            if (!Main.inTimeProjection())
                offset = Main.PO.getServerOffsetX();
            
            worldPt1.setLocation(worldPt1.getX() + offset,
                                 worldPt1.getY());
            
            worldPt2.setLocation(worldPt2.getX() + offset,
                                 worldPt2.getY());
            log.println("worldPt1 = " + worldPt1);
            log.println("worldPt2 = " + worldPt2);
            
            final Rectangle2D worldBounds = new Rectangle2D.Double(worldPt1.getX(), worldPt1.getY(), 0, 0);
            worldBounds.add(worldPt2);
            
            // We clear out the current table selection now rather than later
            // in the Runnable below.  We do this because between this
            // mouseRelease() call and the runnable below is a mouseClicked()
            // call.  The latter needs to be able to process simple mouse click
            // selection without interference from the effects of the Runnable.
            // (There seems to be no way to prevent both mouseClicked() and
            // mouseReleased() from being called...)
            if (myFocus != null)
                myFocus.table.clearSelection();
            
            // NOTE: All processing before this point must occur outside of the
            // delayed Runnable.  There must be some context that is missing
            // (not sure what), otherwise the code below doesn't do anything.
            //
            // Set up the the stamp multi-selection as a Runnable for 
            // later AWT thread processing.  We do this so that the focus
            // panel "yellow" processing indicator has a chance to refresh
            // (for large number of selections).
            Runnable runme = 
                new Runnable()
                {
                    public void run()
                    {
                    	try {
                    		Stamp[] selected = findStampsByWorldRect(worldBounds);
                            
                    		if (myFocus != null)
                    			myFocus.table.toggleSelectedStamps(selected);
                    		
                    		getLayer().setStatus(Util.darkGreen);
                    		getFocusPanel().repaint();
                    	}
                    	catch ( Exception ex ) {
                    		//ignore
                    	}
                    }
                };

            SwingUtilities.invokeLater(runme);

            mouseDragged = false;
			mouseDown = null;
			curSelectionRect = null;
		}
	}

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


	private Stamp[] findStampsByWorldPt(Point2D worldPt)
	{
		if (Main.inTimeProjection())
			return  findStampsByWorldPt_time(worldPt);
		else
			return  findStampsByWorldPt_cyl(worldPt);
	}

	private Stamp[] findStampsByWorldRect(Rectangle2D worldRect)
	{
		if (Main.inTimeProjection())
			return  findStampsByWorldRect_time(worldRect);
		else
			return  findStampsByWorldRect_cyl(worldRect);
	}

	private Stamp[] findStampsByWorldPt_cyl(Point2D worldPt)
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

		return findStampsByWorldRect_cyl(new Rectangle2D.Double(x, y, w, h));
	}

	private Stamp[] findStampsByWorldRect_cyl(Rectangle2D proximity)
	{
		if (stamps == null ||
		    proximity == null)
			return null;

		List list = new ArrayList();
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

		// Perform possibly multiple proximity tests at the same time
		// to avoid re-sorting resulting stamp list.
		for (int i=0; i<stamps.length; i++)
			if (stamps[i].getNormalPath().intersects(proximity1) ||
        		( proximity2 != null && stamps[i].getNormalPath().intersects(proximity2)))
				list.add(stamps[i]);

		return  (Stamp[]) list.toArray(new Stamp[0]);
	}

	private Stamp[] findStampsByWorldPt_time(final Point2D worldPt)
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

		return findStampsByWorldRect_time(new Rectangle2D.Double(worldPt.getX() - w/2,
		                                                         worldPt.getY() - h/2,
		                                                         w, h));
	}

	private Stamp[] findStampsByWorldRect_time(final Rectangle2D proximity)
	{
		if (stamps == null ||
		    proximity == null)
			return null;

		log.println("proximity = " + proximity);

		final List list = new ArrayList();

		// These basically function as pointers... long story, but
		// they must be arrays, and they must be final.
		final boolean[] found = { false };

		// This looks really silly, I know, but it's the most
		// efficient and convenient way to re-use the necessary logic
		// from the SpatialGraphicsSpOb class without a LOT of work.
		Graphics2D fakeG2 = new SpatialGraphicsSpOb(
		                                            new Graphics2DAdapter()
		                                            {
		                                                public void draw(Shape sh) // sh is ALWAYS a Line2D object
		                                                {
		                                                    if (sh.intersects(proximity))
		                                                        found[0] = true;
		                                                }
		                                            },
		                                            getProj(),
		                                            proximity
		                                           );

		for (int i=0; i<stamps.length; i++)
		{
			fakeG2.draw(stamps[i].getPath());
			if (found[0])
			{
				list.add(stamps[i]);
				found[0] = false;
			}
		}

		return  (Stamp[]) list.toArray(new Stamp[0]);
	}

	private Stamp findStampByScreenPt(Point screenPt)
	{
		MultiProjection proj = getProj();
		if (proj == null) {
			log.aprintln("null projection");
			return null;
		}

		Point2D worldPt = proj.screen.toWorld(screenPt);

		return  findStampByWorldPt(worldPt);
	}

	private Stamp findStampByWorldPt(Point2D worldPt)
	{
		if (Main.inTimeProjection())
			return  findStampByWorldPt_time(worldPt);
		else
			return  findStampByWorldPt_cyl(worldPt);
	}

	private Stamp findStampByWorldPt_cyl(Point2D worldPt)
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

	private Stamp findStampByWorldPt_time(final Point2D worldPt)
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

		// All these final variables are used inside the
		// Graphics2DAdapter below.
		final Rectangle2D proximity =
    		    new Rectangle2D.Double(worldPt.getX() - w/2,
    		                           worldPt.getY() - h/2,
    		                           w, h);
		// These basically function as pointers... long story, but
		// they must be arrays, and they must be final.
		final double[] minDistanceSq = { Double.MAX_VALUE };
		final int[] minIndex = { -1 };
		final int[] ii = { -1 };

		// This looks really silly, I know, but it's the most
		// efficient and convenient way to re-use the necessary logic
		// from the SpatialGraphicsSpOb class without a LOT of work.
		Graphics2D fakeG2 = new SpatialGraphicsSpOb(
		                                            new Graphics2DAdapter()
		                                            {
		                                                public void draw(Shape sh) // sh is ALWAYS a Line2D object
		                                                {
		                                                    if (sh.intersects(proximity))
		                                                    {
		                                                        double distanceSq =
		                                                            ((Line2D) sh).ptLineDistSq(worldPt);
		                                                        if (distanceSq < minDistanceSq[0])
		                                                        {
		                                                            minDistanceSq[0] = distanceSq;
		                                                            minIndex[0] = ii[0];
		                                                        }
		                                                    }
		                                                }
		                                            },
		                                            getProj(),
		                                            proximity
                                           		   );

		for (ii[0]=0; ii[0]<stamps.length; ii[0]++)
			fakeG2.draw(stamps[ ii[0] ].getPath());

		if (minIndex[0] == -1)
			return  null;

		return stamps[ minIndex[0] ];
	}

	protected Object createRequest(Rectangle2D where)
	{
		return  where;
	}

	protected Layer.LView _new()
	{
		return  new StampLView(getLayer(), (StampFactory)originatingFactory);
	}

	public Layer.LView dup()
	{
		StampLView copy = (StampLView) super.dup();

		copy.name = this.name;
		copy.initialColumns = this.initialColumns;
		copy.selectedStamps = this.selectedStamps;
		copy.stamps = this.stamps;
		copy.unsColor = this.unsColor;
		copy.filColor = this.filColor;
		copy.selColor = this.selColor;

		// The 'stampsLock' needs to be shared between
		// both parent and child instances to avoid conflicts
		// involving AWT code for both the status pane indicator 
		// and the main stamp list.
		copy.stampsLock = this.stampsLock;

		// Synchronize core redraw/repaint of filled stamps,
		// outlines, and alpha shading across all stamp views.
		copy.redrawLock = this.redrawLock;

		return  copy;
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
     * @param selected  If <code>true</code>, then stamps are
     * drawn in 'selected' color; otherwise, drawn as transparent color.
     * 
     * @param redraw    If <code>true</code>, then drawn selections are
     * cleared and are completely redrawn using the specified stamps as
     * the complete selection list.  Otherwise, buffer is not cleared
     * and the stamp list represents a partial selection/deselection.
     */
	private void drawSelections(Stamp[] ss, boolean selected, boolean redraw)
	{
		// A fine-grain lock is used here to prevent some
		// deadlocks that have occurred when "this" was
		// used for synchronization.
		synchronized(drawLock)
		{
             log.println("redraw = " + redraw);
			 if (redraw)
                clearOffScreen(SELECTED_OUTLINE_BUFFER_IDX);
                
			if (ss == null ||
                ss.length < 1) {
                log.println("no stamps selected/unselected");
			    return;
            }

            log.println((selected ? "selecting " : "unselecting ") + 
                        ss.length +" stamps");
            
			Graphics2D g2 = getOffScreenG2(SELECTED_OUTLINE_BUFFER_IDX);
			g2 = spatialG2Hack(g2);
			if (g2 == null)
				return;

            
            g2.setComposite(AlphaComposite.Src);
            g2.setStroke(new BasicStroke(0));
			g2.setColor(selected ? selColor : NO_SEL_COLOR);

            for (int i=0; i<ss.length; i++) {
				g2.draw(ss[i].getPath());

                if (i % 1000 == 999)
                {
                    if (getBufferVisible(OUTLINE_BUFFER_IDX))
                        repaint();
                }
            }
		}
	}


	/**
	 ** Called when a table selection is triggered, updates the view's
	 ** selection list.
	 **
	 ** globals used: selectedStamps
	 **               getChild()
	 **/
	private void setSelectedStamps(Stamp[] ss)
	{
		createFocusPanel();

		// Although there is no direct access to the stamp
		// table here, there is an implicit need to synchronize
		// calls since both table selection and the call
		// to the same method on the child view below
		// could occur concurrently.
		//
		// Synchronizing on "this" would be bad since it has led to
		// deadlocks in some cases in the AWT event thread.
		synchronized (myFocus.table)
		{
			if (selectedStamps == null)
				selectedStamps = new Stamp[0];

            if (ss == null ||
                ss.length < 1) {
                // No selections -- clear stamp selection draw buffer.
                log.println("Clearing all selections");
                drawSelections(null, true, true);
            }
            else
            {
                boolean redrawNeeded = false;
            	List oldSelection = Arrays.asList(selectedStamps);
            	List newSelection = Arrays.asList(ss);
            	
				// Determine whether there are any stamps to be deselected.
                // If so, we flag that all existing stamps will need to
                // be redrawn.  We do not simply draw over the deselected
                // stamps because of display artifacts that would result
                // anywhere that deselected stamps overlap with selected stamps.
                //
                // Redrawing the stamp selections automatically clears the entire
                // drawing buffer.
				if (oldSelection != null &&
					oldSelection.size() > 0)
				{
					ArrayList unselect = new ArrayList(oldSelection);
					unselect.removeAll(newSelection);
                    
					if (unselect.size() > 0)
						redrawNeeded = true;
				}
				
                // If a redraw is needed because some stamps were deselected, then we
                // must draw ALL selected stamps because of the outline overlaps that would 
                // otherwise result between the deselected stamps and preexisting stamps.  There are
                // no generally less expensive solutions to this since the alternatives
                // involve calculating the intersections of GeneralPath instances.
                //
                // Otherwise, we paint just new stamp selections.
                if (redrawNeeded) {
                    log.println("Redrawing all selections");
                    drawSelections(ss, true, true);
                }
                else {
                	// Paint just the newly-selected stamps
                	ArrayList select = new ArrayList(newSelection);
                	select.removeAll(oldSelection);
                    
                	if (select.size() > 0) {
                        log.println("Drawing new selections");
                		Stamp[] newStamps = (Stamp[]) select.toArray(new Stamp[0]);
                		drawSelections(newStamps, true, false);
                	}
                }
            }

			selectedStamps = ss;
			repaint();

			if (getChild() != null)
				( (StampLView) getChild() ).setSelectedStamps(ss);
		}
	}


	/**
	 ** clears the highlighting of all the stamps in both the main and panner view.
	 **/
	private void clearStamps()
	{
		createFocusPanel();
		synchronized (myFocus.table)
		{
            selectedStamps = null;
			drawSelections(null, false, true);
            
			if (getChild() != null){
				( (StampLView) getChild() ).clearStamps();
			}

			repaint();

			return;
		}

	}



	protected void panToStamp(Stamp s)
	{
	    centerAtPoint(new Point2D.Double(s.getBounds2D().getCenterX(),
	                                     s.getBounds2D().getCenterY()));
		if (myFocus != null){
			myFocus.table.clearSelection();
			myFocus.table.toggleSelectedStamp(s);
		}
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

			// Wait for stamps to be finish being loaded into view.
			try {
				synchronized(stampsArrivedLock)
				{
					while (stamps == null)
						stampsArrivedLock.wait();
				}
			}
			catch (Throwable e) {
				log.println("while waiting for lock release: " + e.getMessage());
				log.println("exiting after exception");
				return;
			}


			// Add stamps to focus panel's list in reverse order;
			// the addStamp operation has a push-down behavior and
			// we want to reestablish the same order as in the list
			// of stamp IDs.
			if (focusFilled != null) {
				log.println("processing stamp ID list of length " + stampStateList.length);
				log.println("with view stamp list of length " + stamps.length);
                
                Stamp[] stampList = new Stamp[stampStateList.length];
				FilledStamp.State[] stateList = new FilledStamp.State[stampStateList.length];

                StampLayer layer = (StampLayer)getLayer();
                int count = 0;
				for (int i=stampStateList.length - 1; i >= 0; i--) {
					log.println("looking for stamp ID " + stampStateList[i].id);

					Stamp s = layer.getStamp(stampStateList[i].id.trim());
					if (s != null) {
						stampList[count] = s;
						stateList[count] = stampStateList[i];

						count++;
						log.println("found stamp ID " + stampStateList[i].id);
					}
				}

				addSelectedStamps(stampList, -1, stateList, 0, 0, false);
				log.println("actually loaded " + count + " stamps");
			}
		}

		log.println("exiting");
	}

	/** Adds specified stamp from stamp table list based on row index.
	 ** This uses the multi-stamp display approach involving the stamp
	 ** progress monitor, so the UI effect is in fact different than
	 ** other methods of adding a single stamp.
	 **
	 ** @param row selected stamp indices in using *unsorted* row numbers
	 **/
	protected void addSelectedStamp(final int row)
	{
	    addSelectedStamps(new int[] {row});
	}
 
	/** Adds specified stamps from stamp table list.
	 **
	 ** @param rows selected stamp indices in using *unsorted* row numbers
	 **/
	protected void addSelectedStamps(final int[] rows)
	{
		addSelectedStamps(rows, -1, MILLIS_TO_DECIDE_TO_POPUP, MILLIS_TO_POPUP);
	}
 
	/** Adds specified stamps from stamp table list.
	 **
	 ** @param rows                    selected stamp indices in using *unsorted* row numbers
	 ** @param bandSelection           band selected to be rendered; -1 means do not render a band.
	 **/
	protected void addSelectedStamps(final int[] rows, final int bandSelection)
	{
		addSelectedStamps(rows, bandSelection, MILLIS_TO_DECIDE_TO_POPUP, MILLIS_TO_POPUP);
	}

	/** Adds specified stamps from stamp table list.
	 **
	 ** @param rows                    selected stamp indices in using *unsorted* row numbers
	 ** @param bandSelection           band selected to be rendered; -1 means do not render a band.
	 ** @param millisToDecideToPopup   parameter for StampProgressMonitor
	 ** @param millisToPopup           parameter for StampProgressMonitor
	 **/
	protected void addSelectedStamps(final int[] rows, final int bandSelection,
	                                 final int millisToDecideToPopup,
	                                 final int millisToPopup)
	{
		addSelectedStamps(rows, bandSelection, null, millisToDecideToPopup, millisToPopup, true);
	}

	/** Adds specified stamps from stamp table list.
	 **
	 ** @param rows            selected stamp indices in using *unsorted* row numbers
	 ** @param stampStateList  stamp state information array with elements corresponding 
	 **                            to order of indices in rows parameter; may be 'null'.
	 ** @param allowThread     flag controlling whether method's operation may be 
	 **                            carried out as a separate thread; 'true' means that
	 **                            a thread will be created if the calling thread is
	 **                            an event dispatch thread.
	 **/
	protected void addSelectedStamps(final int[] rows, final FilledStamp.State[] stampStateList,
	                                 boolean allowThread)
	{
		addSelectedStamps(rows, -1, stampStateList, MILLIS_TO_DECIDE_TO_POPUP, MILLIS_TO_POPUP, 
				  allowThread);
	}

	/** Adds specified stamps from stamp table list.
	 **
	 ** @param rows                    selected stamp indices in using *unsorted* row numbers
	 ** @param stampStateList          stamp state information array with elements corresponding 
	 **                                    to order of indices in rows parameter; may be 'null'.
	 ** @param bandSelection           band selected to be rendered; -1 means do not render a band.
	 ** @param millisToDecideToPopup   parameter for StampProgressMonitor
	 ** @param millisToPopup           parameter for StampProgressMonitor
	 ** @param allowThread             flag controlling whether method's operation may be 
	 **                                    carried out as a separate thread; 'true' means that
	 **                                    a thread will be created if the calling thread is
	 **                                    an event dispatch thread.
	 **
	 ** Note: If the 'stampStateList' and 'bandSelection' parameters are both given meaningful
	 ** specifications, then 'stampStateList' is used and 'bandSelection' is ignored.
	 **/
	protected void addSelectedStamps(final int[] rows, final int bandSelection,
	                                 final FilledStamp.State[] stampStateList,
	                                 final int millisToDecideToPopup, final int millisToPopup,
	                                 boolean allowThread)
	{
	    if (rows != null &&
            rows.length > 0)
	    {
	        Stamp[] selectedStamps = new Stamp[rows.length];
	        for (int i=0; i < rows.length; i++)
	            if (rows[i] >= 0)
	                selectedStamps[i] = stamps[rows[i]];
	            
	        addSelectedStamps(selectedStamps, bandSelection, stampStateList,
	                          millisToDecideToPopup, millisToPopup, allowThread);
	    }
	}

	/** Adds specified stamps from stamp table list.
	 **
	 ** @param selectedStamps          selected stamps to be added
	 ** @param stampStateList          stamp state information array with elements corresponding 
	 **                                    to order of indices in rows parameter; may be 'null'.
	 ** @param bandSelection           band selected to be rendered; -1 means do not render a band.
	 ** @param millisToDecideToPopup   parameter for StampProgressMonitor
	 ** @param millisToPopup           parameter for StampProgressMonitor
	 ** @param allowThread             flag controlling whether method's operation may be 
	 **                                    carried out as a separate thread; 'true' means that
	 **                                    a thread will be created if the calling thread is
	 **                                    an event dispatch thread.
	 **
	 ** Note: If the 'stampStateList' and 'bandSelection' parameters are both given meaningful
	 ** specifications, then 'stampStateList' is used and 'bandSelection' is ignored.
	 **/
	protected void addSelectedStamps(final Stamp[] selectedStamps, final int bandSelection,
	                                 final FilledStamp.State[] stampStateList,
	                                 final int millisToDecideToPopup, final int millisToPopup,
	                                 boolean allowThread)
	{
		if (selectedStamps != null && selectedStamps.length > 0)
			{
				Runnable runme =
					new Runnable()
					{
						public void run()
						{
							// Strange use of negative numbers for initializing StampProgressMonitor
							// and setting progress before actually adding stamps is a necessary
							// hack to get desired behavior of progress dialog appearing IMMEDIATELY
							// and having appropriate progress flow.
							StampProgressMonitor pm = new StampProgressMonitor(StampLView.this,
													   "Loading image data...",
													   "", 0, selectedStamps.length - 1, -2,
													   millisToDecideToPopup, millisToPopup);
							pm.setProgress(-1);
							getLayer().setStatus(Color.yellow);
							
							for (int i = 0; i < selectedStamps.length; i++) {
								// Add stamp without actually drawing it.
								if (selectedStamps[i] != null) {
									if (stampStateList == null)
										focusFilled.addStamp(selectedStamps[i], false, true, true, true, bandSelection);
									else if (stampStateList != null)
										focusFilled.addStamp(selectedStamps[i], stampStateList[i], false,
												     true, true, true);
								}
								
								pm.setProgress(i);
								if (pm.isCanceled())
									break;
							}
							
							pm.close();
							
							// If bands were selected for rendering or stamp state
							// information was provided, draw the stamps.
							if (bandSelection >= 0 ||
							    stampStateList != null) {
								focusFilled.updateStampSelected();
								drawStampsTogether();
							}
							
							getLayer().setStatus(Util.darkGreen);
						}
					}
					;
				
				SwingUtilities.invokeLater(runme);
				//if (allowThread &&
				//SwingUtilities.isEventDispatchThread())
				//    new Thread(runme).start();
				//else
				//     runme.run();
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
	        
	        // Draw stamps only after adding all stamps to list.
	        int numStamps = focusFilled.getNumStamps();
	        StampProgressMonitor pm = new StampProgressMonitor(this,
	                                                           "Preparing image data...",
	                                                           "", 0, numStamps - 1, 0,
	                                                           300, 700);
	        FilledStamp[] fss = new FilledStamp[numStamps];
	        MultiProjection proj = getProj();
	        final int renderPPD = viewman.getMagnification();
	        
	        // Force creation of filled stamp image frames while keeping the user up-to-date.
	        for (int i=0; i < numStamps; i++) {
	            fss[i] = focusFilled.getFilled(i);
	            if (fss[i] != null &&
                    fss[i].pdsi != null)
	                fss[i].pdsi.getRenderImageTime(fss[i].band, proj, renderPPD);
	            
	            pm.setProgress(i);
	            pm.repaint();
	            if (pm.isCanceled())
	                break;
	        }
	        
	        pm.close();
	        
	        redrawEverything(true, fss);
	    }
	}



	protected class MyFocus extends FocusPanel
	{
		StampLView     panner;
		StampTable     table;
		ColorCombo     dropOutline;
		ColorCombo     dropFill;
		ActionListener dropFillAction;
        JLabel         labelBrowseCmd;
		String         layerName;
	
		MyFocus(StampLView parent, StampLView panner)
        {
			this(parent, panner, true);
		}
		
		protected MyFocus(StampLView parent, StampLView panner, boolean createPanel)
        {
			super(parent);
			
			layerName = parent.getName();
			this.panner = panner;

			String [] columnNames = ((StampLayer) getLayer()).getColumnNames();
			table = new StampTable( columnNames, initialColumns);
			table.addMouseListener( new TableMouseAdapter());
			table.getSelectionModel().addListSelectionListener( new TableListSelectionAdapter() );
			table.getTableHeader().addMouseListener( new HeaderMouseListener());

			if (createPanel) {
				setLayout(new GridLayout(1,1));
				JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
                
				tabs.add("Outlines", getFocusUnfilledPanel() );
                if (!Main.isStudentApplication())
				    tabs.add("Rendered", focusFilled);
                
				tabs.add("Settings", getFocusSettingsPanel());
                if (!Main.isStudentApplication())
                    tabs.add("Query", getQueryPanel());
                
				add(tabs, BorderLayout.CENTER);
			}

			// add initial rows the the table
			table.dataRefreshed();
		}
	
		// Defines behavior for clicking in the header of the table.
		// This is defined purely in order to clear selections on a sorting event.
		private class HeaderMouseListener extends MouseAdapter 
        {
			public void mouseClicked(MouseEvent e)
            {
				if (!SwingUtilities.isRightMouseButton(e))
					clearStamps();
			}
		}
		

		// listens for changes in the stamps selected in the unfilled stamp table.
		// 
		// globals used:
		//      stamps
		//      setSelectedStamps()
		//      focusFilled
		// 
		// (JW 3/04 - if a stamp is deselected, this method now correctly sends deselection 
		//  messages to all appropriate objects.)
		// (JW 3/06 - the stamps associated with selected rows are fetched for redrawing.
		//               I have no idea how this could possibly have worked before.)
		protected class TableListSelectionAdapter implements ListSelectionListener {
			public void valueChanged(ListSelectionEvent e)
			{
				if (e.getValueIsAdjusting() || ignoreTableSelectionChanges) {
					return;
				}
				
				synchronized(table) {
					int index = table.getColumnIndex( STAMP_COLUMN_NAME);
					if (index != -1) {
						Stamp[] newSelection;
						int[] rows = table.getSelectedRows();
						if (rows==null || rows.length<1){
							newSelection = new Stamp[0];
						}
						else {
							newSelection = new Stamp[rows.length];
							for (int i=0; i<rows.length; i++) {
								Object [] rowArray = table.getRow( rows[i]);
								newSelection[i] = (Stamp)rowArray[index]; 
							}
						}
						
						setSelectedStamps(newSelection);
						focusFilled.performSelect(true, newSelection, new Stamp[0]);
					}
				}
			}
		}


		// listens for mouse events in the unfilled stamp table.  If a single left-click,
		// the stamp outline in the viewer is hilighted.  If a double left-click, the stamp
		// is hilighted AND the viewers center about the stamp.  If a right-click, the 
		// render/browse popup is brought forth.
		protected class TableMouseAdapter extends MouseAdapter {
			public void mousePressed(MouseEvent e){
				synchronized (table) {
					
					// get the indexes of the sorted rows. 
					final int[] rowSelections = table.getSelectedRows();
					if (rowSelections==null || rowSelections.length==0) {
						return;
					}
					
					// if this was a left click, pan to the selected stamp.
					if (SwingUtilities.isLeftMouseButton(e)){
						if (e.getClickCount() > 1) {
							int row = rowSelections[0];
							Stamp s = table.getStamp( row);
							if (s != null){
								panToStamp( s);
							}
						}
						return;
					} 

					// if this was a right click, bring up the render/browse popup and do the
					// right thing.   (snicker,snicker...get it?  Right thing?)
					JPopupMenu menu = new JPopupMenu();
					if (rowSelections.length == 1) 
                    {
						final int row = rowSelections[0];
						if (row < 0 || Main.inTimeProjection()) {
							return;
						}
                        
                        if (!Main.isStudentApplication())
				menu.add(new JMenuItem( 
						       new AbstractAction("Render " + stamps[row].id){
							       public void actionPerformed(ActionEvent e)
							       {
								       Runnable runme =
									       new Runnable()
									       {
										       public void run() 
										       {
											       focusFilled.addStamp(stamps[row]);
										       }
									       };
								       
								       //new Thread(runme).start();
								       SwingUtilities.invokeLater( runme);
							       }
						       }));
						menu.add(new JMenuItem(
                                   new AbstractAction("Web browse " + stamps[row].id){
                                       public void actionPerformed(ActionEvent e){
                                           browse(stamps[row]);
                                       }
                                   }));
					} 
					else if (!Main.inTimeProjection() &&
                             !Main.isStudentApplication()) {
					    // Handle multiple row selections
					    menu.add(
					             new JMenuItem( new AbstractAction("")
					                            {
					                 {
					                     if (StampLView.this instanceof BtrStampLView)
					                         putValue(NAME, "Render Selected Stamps");
					                     else
					                         putValue(NAME, "Load Selected Stamps");
					                 }
					                 
					                 public void actionPerformed(ActionEvent e)
					                 {
					                     int unsortedRows[] = myFocus.table.getSelectedRows();
					                     StampLView.this.addSelectedStamps(unsortedRows);
					                 }
					              }));
					}

					// Add copy-selected-stamp-IDs menu item.
					if (rowSelections != null && 
					    rowSelections.length > 0)
					{
					    menu.add(
						     new JMenuItem( 
								   new AbstractAction("Copy Selected Stamp List to Clipboard")
								   {
								       public void actionPerformed(ActionEvent e)
								       {
								           StringBuffer buf = new StringBuffer();
								           for (int i = 0; i < rowSelections.length; i++) {
								               buf.append( myFocus.table.getKey(rowSelections[i]) );
								               buf.append(' ');
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
								   })
						     );
					}

					menu.show(e.getComponent(), e.getX(), e.getY());
				} 
			}// end: mousePressed()
				
				
		};// end: class TableMouseAdapter



		// creates a default settings panel. This panel can be added to another panel which contains 
		// additional components.  This is what happens in the Themis stamp layer.
		protected JPanel getFocusSettingsPanel()
        {
			JPanel focusSettings = new JPanel();
		 	focusSettings.setLayout(new BorderLayout());

		 	JPanel focusSettings2 = new JPanel(new GridLayout(0, 2));
			focusSettings2.add(new JLabel("Outline color:"));
			focusSettings2.add( dropOutline = new ColorCombo()
			                    {{
			                        setColor(unsColor);
			                        addActionListener( 
			                                          new ActionListener() {
			                                              public void actionPerformed(ActionEvent e) {
			                                                  StampLView child = (StampLView) getChild();
			                                                  child.unsColor = unsColor = getColor();
			                                                  child.selColor = selColor = new Color(~unsColor.getRGB());
			                                                  if (dropFill.isLinked()) {
			                                                      dropFillAction.actionPerformed(e);
			                                                  }
			                                                  
			                                                  if (!hideOutlines) {
			                                                      drawOutlines();
			                                                      child.drawOutlines();
			                                                  }
			                                              }
			                                          });
			                    }} );
            
			focusSettings2.add(new JLabel("Fill color:"));
			focusSettings2.add( dropFill = new ColorCombo()
			                    {{
			                        setColor(new Color(filColor.getRGB() & 0xFFFFFF, false));
			                        addActionListener(
			                                          dropFillAction =
			                                              new ActionListener()
			                                              {
			                                              public void actionPerformed(ActionEvent e)
			                                              {
			                                                  StampLView child = (StampLView) getChild();
			                                                  int alpha = filColor.getAlpha();
			                                                  child.filColor = filColor =
			                                                      new Color( (alpha<<24) | (getColor().getRGB() & 0xFFFFFF), true);
			                                                  if (alpha != 0){
			                                                      redrawEverything(true);
			                                                  }
			                                              }
			                                              });
			                    }} );
            
			focusSettings2.add(new JLabel("Fill alpha:"));
			focusSettings2.add( new JSlider(0, 255, 0)
			                    {{
			                        setValue(filColor.getAlpha());
			                        addChangeListener( 
			                                          new ChangeListener() {
			                                              public void stateChanged(ChangeEvent e) {
			                                                  if (getValueIsAdjusting()) {
			                                                      return;
			                                                  }
			                                                  StampLView child = (StampLView) getChild();
			                                                  int alpha = getValue();
			                                                  int color = filColor.getRGB() & 0xFFFFFF;
			                                                  child.filColor = filColor = new Color((alpha<<24) | color, true);
			                                                  redrawEverything(true);
			                                              }
			                                          });
			                    }} );
            
			focusSettings2.add(new JLabel("Layer name:"));
			focusSettings2.add( new PasteField(12)
			                    {{
			                        setText(StampLView.this.name);
			                        addActionListener( new ActionListener() {
			                            public void actionPerformed(ActionEvent e) {
			                                StampLView.this.name = getText();
			                                Main.getLManager().updateLabels();
			                            }
			                        });
			                    }});

            // Add customize-browser button with a little extra vertical spacing (one
            // two-column "line" before, one after. 
            focusSettings2.add(new JLabel(" "));
            focusSettings2.add(new JLabel(" "));
			focusSettings2.add(new JButton("Customize Webbrowser...")
                               {{
                                   addActionListener(new ActionListener() {
                                        public void actionPerformed(ActionEvent e)
                                        {
                                            new BrowserChoiceDialog().show();
                                            
                                            // Notify all instances of all stamp view
                                            // types of the configuration change.  This
                                            // includes this stamp view as well.
                                            if (viewman != null)
                                                viewman.configChanged(StampLView.class);
                                        }
                                    });
                               }});
            String browseCmd = Config.get(CFG_BROWSER_CMD_KEY, " ");
            if (browseCmd.trim().length() > 0)
                // Extract program name for browser
                browseCmd = new StringTokenizer(browseCmd).nextToken();
            labelBrowseCmd = new JLabel(browseCmd);
            focusSettings2.add(labelBrowseCmd);
            focusSettings2.add(new JLabel(" "));
            focusSettings2.add(new JLabel(" "));
		
		 	focusSettings.add(focusSettings2, BorderLayout.NORTH);
		 	focusSettings.add(new JLabel(), BorderLayout.CENTER);

			return focusSettings;
		}
			

		// creates and returns a default unfilled stamp panel.
		protected JPanel getFocusUnfilledPanel()
        {
		    JPanel top = new JPanel();
		    top.add(
		        new JButton(
                    new AbstractAction("Find stamp...")
                    {
                        public void actionPerformed(ActionEvent e) 
                        {
                            String id = JOptionPane.showInputDialog(
                                                                    MyFocus.this,
                                                                    "Enter a stamp id:",
                                                                    "Find stamp...",
                                                                    JOptionPane.QUESTION_MESSAGE);
                            if (id == null) {
                                return;
                            }
                            
                            Stamp[] stamps = ((StampLayer) getLayer()).getStamps();
                            for (int i=0; i<stamps.length; i++)
                                if (stamps[i].id.equalsIgnoreCase(id.trim()))
                                {
                                    panToStamp(stamps[i]);
                                    return;
                                }
                                
                            JOptionPane.showMessageDialog(
                                                          MyFocus.this,
                                                          "Can't find the stamp \"" + id
                                                          + "\", are you sure\n"
                                                          + "it meets your layer's selection criteria?",
                                                          "Find stamp...",
                                                          JOptionPane.ERROR_MESSAGE);
                        }
                    } ));
			
		    final JFileChooser fc = new JFileChooser();
		    top.add(new JButton( new AbstractAction("Dump table to file...") {
		        public void actionPerformed(ActionEvent e){
		            File f;
		            do {
		                if (fc.showSaveDialog(MyFocus.this)
		                        != JFileChooser.APPROVE_OPTION)
		                    return;
		                f = fc.getSelectedFile();
		            }
		            while( f.exists() && 
		                    JOptionPane.NO_OPTION == JOptionPane.showConfirmDialog(
		                                                                           MyFocus.this,
		                                                                           "File already exists, overwrite?\n" + f,
		                                                                           "FILE EXISTS",
		                                                                           JOptionPane.YES_NO_OPTION,
		                                                                           JOptionPane.WARNING_MESSAGE
		                    )
		            );
		            try {
		                PrintStream fout =
		                    new PrintStream(new FileOutputStream(f));
		                
		                synchronized(table) {
		                    int rows = table.getRowCount();
		                    int cols = table.getColumnCount();
		                    
		                    // Output the header line
		                    for (int j=0; j<cols; j++)
		                        fout.print(table.getColumnName(j)
		                                   + (j!=cols-1 ? "\t" : "\n"));
		                    
		                    // Output the data
		                    for (int i=0; i<rows; i++)
		                        for (int j=0; j<cols; j++)
		                            fout.print(table.getValueAt(i, j)
		                                       + (j!=cols-1 ? "\t" : "\n"));
		                }
		            } 
                    catch(FileNotFoundException ex){
		                log.aprintln(ex);
		                JOptionPane.showMessageDialog(
		                                              MyFocus.this,
		                                              "Unable to open file!\n" + f,
		                                              "FILE OPEN ERROR",
		                                              JOptionPane.ERROR_MESSAGE
		                );
		            }
		        }
		    }));
            
		    JPanel focusUnfilled = new JPanel(new BorderLayout());
		    focusUnfilled.add(top,    BorderLayout.NORTH);
		    focusUnfilled.add(table.getPanel(), BorderLayout.CENTER);
		    return focusUnfilled;
        }
        
        protected JPanel getQueryPanel()
        {
            JPanel queryPanel = new JPanel();
            if (originatingFactory == null) {
                log.aprintln("null factory reference");
                return queryPanel;
            }
            
            StampFactory factory = (StampFactory)originatingFactory;
            StampFactory.LayerDescription layerDesc = factory.getLayerDescription(getLayer());
            final StampFactory.AddLayerWrapper wrapper = factory.createWrapper(queryPanel, layerDesc, false);
            verticalSquish(queryPanel);
            
            if (wrapper != null)
                wrapper.addActionListener(
                                          new ActionListener()
                                          {
                                              private StampFactory.LayerDescription oldLayerDesc;
                                              
                                              {
                                                  oldLayerDesc = wrapper.getLayerDescription();
                                              }
                                              
                                              public void actionPerformed(ActionEvent e)
                                              {
                                                  if (e == null)
                                                      return;
                                                  
                                                  if (e.getActionCommand() == StampFactory.AddLayerWrapper.OK) {
                                                      // Update layer/view with stamp data from new version of
                                                      // query using parameters from query panel.
                                                      StampFactory.LayerDescription newLayerDesc = wrapper.getLayerDescription();
                                                      SqlStampLayer.SqlParameters sqlParms = wrapper.getSqlParms();
                                                      
                                                      if (replaceLayer(sqlParms, newLayerDesc)) {
                                                          viewChanged();
                                                          Layer.LView childLView = getChild();
                                                          if (childLView != null)
                                                              childLView.viewChanged();
                                                      }
                                                      
                                                      oldLayerDesc = newLayerDesc;
                                                  }
                                                  else if (e.getActionCommand() == StampFactory.AddLayerWrapper.CANCEL) {
                                                      // Restore query panel with old version of query parameters.
                                                      wrapper.updateLayerDescription(oldLayerDesc);
                                                  }
                                                  
                                              }
                                          }
                                         );
            
            return queryPanel;
        }
        
        protected void verticalSquish(JComponent c)
        {
            Dimension d = c.getMinimumSize();
            d.width = c.getMaximumSize().width;
            c.setMaximumSize(d);
        }
        

        /**
         * Handles configuration change impacting the
         * webbrowser customization.
         */
        protected void updateBrowseCmd()
        {
            log.println("Entered: " + originatingFactory.getName());
            
            String browseCmd = Config.get(CFG_BROWSER_CMD_KEY, " ");
            if (browseCmd.trim().length() > 0)
                // Extract program name for browser
                browseCmd = new StringTokenizer(browseCmd).nextToken();
            labelBrowseCmd.setText(browseCmd);
            repaint();
        }

        private JFileChooser fileChooser;
        protected final JFileChooser getFileChooser()
        {
            if (fileChooser == null)
                fileChooser = new JFileChooser();
            
            return fileChooser;
        }
        
        protected class BrowserChoiceDialog implements ActionListener
        {
            private JDialog dialog;
            private JTextField txtCommand;
            private JButton btnBrowse = new JButton("Browse...");
            private JButton btnOK = new JButton("OK");
            private JButton btnClear = new JButton("Clear");
            private JButton btnCancel = new JButton("Cancel");
            
            BrowserChoiceDialog()
            {
                // Retrieve and existing custom browser command setting.
                String browserCmd = Config.get(CFG_BROWSER_CMD_KEY, "");
                
                // Construct dialog contents
                JPanel top = new JPanel();
                String msg = "Please provide command to start preferred webbrowser program.  " +
                             "Include program name with valid directory path (if needed) and " +
                             "any necessary command line options." +
                             "\n  \nFor the command argument " + 
                             "which specifies the webpage to open, use " + URL_TAG + 
                             " as the placeholder." +
                             "\n  \nExample:  mywebbrowser " + URL_TAG;
                msg = Util.lineWrap(msg, 80);
                MultiLabel txtBox = new MultiLabel(msg);
                top.add(txtBox);
                
                JPanel middle = new JPanel();
                middle.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                middle.add(new JLabel("Command:"));
                txtCommand = new PasteField(browserCmd, MIN_TEXT_COLUMNS);
                middle.add(txtCommand);
                
                // Browser program chooser dialog launch button.
                btnBrowse.addActionListener(
                                            new ActionListener()
                                            {
                                                public void actionPerformed(ActionEvent e)
                                                {
                                                    // Show the file chooser
                                                    JFileChooser fc = getFileChooser();
                                                    if (fc.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION)
                                                        return;
                                                    
                                                    txtCommand.setText(fc.getSelectedFile().getPath() + 
                                                                       " " + URL_TAG);
                                                }
                                            }
                                           );
                middle.add(btnBrowse);
                
                JPanel bottom = new JPanel();
                btnOK.addActionListener(this);
                bottom.add(btnOK);
                btnClear.addActionListener(this);
                bottom.add(btnClear);
                btnCancel.addActionListener(this);
                bottom.add(btnCancel);
                
                // Construct the dialog itself
                dialog = new JDialog(Main.getLManager(),
                                     "Webbrowser Preference",
                                     true);
                dialog.getContentPane().setLayout(new BoxLayout(dialog.getContentPane(), BoxLayout.Y_AXIS));
                dialog.getContentPane().add(top);
                dialog.getContentPane().add(middle);
                dialog.getContentPane().add(bottom);
                dialog.pack();
                dialog.setLocation(Main.getLManager().getLocation());
            }
            
            // Does not return until dialog is hidden or disposed of.
            public void show()
            {
                dialog.setVisible(true);
            }
            
            public void actionPerformed(ActionEvent e)
            {
                if(e.getSource() == btnCancel) {
                    dialog.dispose();
                    return;
                }
                else if(e.getSource() == btnClear) {
                    txtCommand.setText("");
                    return;
                }
                else if (e.getSource() == btnOK) {
                    String cmd = txtCommand.getText().trim();
                    
                    if (cmd == null ||
                        cmd.equals("") ||
                        cmd.toLowerCase().equals(URL_TAG.toLowerCase())) 
                    {
                        // Clear custom browser command; will use default.
                        Config.set(CFG_BROWSER_CMD_KEY, "");
                        dialog.dispose();
                        return;
                    }

                    // Verify basic command syntax requirements: The command must have
                    // a program/command name as the first argument, and the URL_TAG placeholder
                    // must appear somewhere in the command string after it.
                    int urlIndex = cmd.toLowerCase().indexOf(URL_TAG.toLowerCase());
                    StringTokenizer tokenizer = new StringTokenizer(cmd);
                    
                    if (tokenizer.countTokens() >= 2 &&
                        !tokenizer.nextToken().equalsIgnoreCase(URL_TAG) &&
                        urlIndex >= 0)
                    {
                        // Replace just the url placeholder with the proper case form.
                        if (urlIndex >= 0) {
                            cmd = cmd.substring(0, urlIndex) + URL_TAG + cmd.substring(urlIndex + URL_TAG.length());
                            Config.set(CFG_BROWSER_CMD_KEY, cmd);
                        }
                        else
                            log.aprintln("bad index for URL placeholder: " + urlIndex);
                    }
                    else {
                        String msg = "Command should have syntax similar to the following: \"mywebbrowser " + 
                                     URL_TAG + "\", where " + URL_TAG + " is the placeholder for a webpage.  " +
                                     "Other command arguments and any command/argument order is permitted, so " + 
                                     "long as the webpage placeholder appears.";
                        msg = Util.lineWrap(msg, 55);
                        
                        JOptionPane.showMessageDialog(
                                                      Main.mainFrame,
                                                      msg,
                                                      "Browser Command Problem",
                                                      JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    
                    dialog.dispose();
                    return;
                }
            }
        }

        
		/**
		 **  The unfilled stamp table.
		 **
		 **  This table allows for sorting (either increasing or decreasing) on any columns in
		 **  the table.  There may be a primary sort or a primary/secondary sort.
		 **   
		 **  Right-clicking on a row in the table allows users to render the stamp corresponding
		 **  to the table row or browse the stamp's webpage. Left-clicking selects a row and highlights
		 **  the stamps outline in the viewing windows.  A double left-click lightlights the outline and 
		 **  centers the viewing windows about the stamp.
		 **/
		protected class StampTable extends SortingTable 
		{
			
			// The name of the key column.  Note that this is layer specific.
			String idColumn = null;
			
			int    stampIndex = -1;

			/**
			 ** constructor
			 **
			 ** @param  columnNames      all POSSIBLE columns.
			 ** @param  initialColumns   columns that should initially appear in the table. 
			 **                          There must be an entry in the columnNames array for each 
			 **                          element of this array.
			 **/
			public StampTable( String [] columnNames, String [] initialColumns)
			{
				super( columnNames, null);
				ToolTipManager.sharedInstance().registerComponent( this );
				setCellRenderer( "start", new TimeRenderer());
				setCellRenderer( "sclk", new TimeRenderer());
				
				// get an array of booleans that indicate whether a particular column 
				// should be be visible or not visible.  If the column appears in 
				// the list of initialcolumns, it is visible.  
				if (columnNames !=null) {
					boolean [] statuses = new boolean[ columnNames.length];	
					for (int i=0; i<columnNames.length; i++){
						statuses[i] = false;
						for (int j=0; j<initialColumns.length; j++){
							if (initialColumns[j].equalsIgnoreCase(columnNames[i])){
								statuses[i]=true;
							}
						}
					}
					setColumnStatus( statuses);
				}
				
				// Set up the "key" column.
				Layer layer = getLayer();
				if (layer instanceof SqlStampLayer &&
				    ((SqlStampLayer)layer).sqlParms != null)
					idColumn = ((SqlStampLayer)layer).sqlParms.primaryKeyName;
				else
					log.aprintln("No stamp key field configured");

				// ALL stamp layers should have a "stamp" column so they can
				// have ready access to the stamp associated with a particular row.
				addColumn( STAMP_COLUMN_NAME, false, false);
				stampIndex = getColumnIndex( STAMP_COLUMN_NAME);
			}
			
			
			/**
			 ** returns the stamp that corresponds to the specified row.
			 ** returns null if the stamp cannot be found.
			 **
			 ** @param row - the table row whose stamp is to be fetched.
			 **/
			public Stamp getStamp( int row){
				Object [] rowArray = getRow( row);
				return (Stamp)rowArray[ stampIndex];
			}
				
			public int getRow( Stamp stamp){
				for (int i=0; i< getTableModel().getRowCount(); i++){
					Stamp s = getStamp( i);
					if (s == stamp){
						return i;
					}
				} 
				return -1;
			}
				
			
			/**
			 ** returns the key value of the specified table row.
			 ** returns null if key column cannot be found.
			 **
			 ** @param row - the row whose key value is to be returned.
			 **/
			public String getKey( int row){
				int index    = table.getColumnIndex( idColumn);
				if (index == -1){
					return (String)null;
				}
				return (String)getTableModel().getValueAt(row,index);
			}


			/**
			 ** returns the key value of the specified table row.
			 ** returns null if the key column cannot be found.
			 **
			 ** @param obj  - the row whose key value is to be returned.
			 **/
			public String getKey( Object [] obj){
				int index = table.getColumnIndex( idColumn);
				if (index == -1){
					return (String)null;
				}
				return (String)obj[index];
			}


			/**
			 ** returns the specified stamp converted to an array of objects that is used 
			 ** by a SortingTable.
			 ** 
			 ** JRW 3/06 - added the stamp of the row as the final column.
			 **
			 ** @param  s - the stamp to be converted.
			 **/
			public Object [] stampToObject( Stamp s) {
			    Object [] obj = new Object[ s.data.length +1];
			    //Object [] obj = new Object[ s.data.length];
				for (int i=0; i< s.data.length; i++){
					obj[i] = s.data[i];
				}
				obj[s.data.length] = (Object)s;
				return obj;
			}
			


			/**
			 ** specifies the tooltip to be displayed whenever the cursor
			 ** halts over a cell.  This is called because the table's panel
			 ** is registered with the tooltip manager in the constructor.
			 **/
			public String getToolTipText(MouseEvent e){
				Point p = e.getPoint();
				int col = columnAtPoint(p);
				int row = rowAtPoint(p);
				if (col == -1  ||  row == -1) {
					return  null;
				}
				String name = getColumnName(col);
				Object value = getValueAt(row, col);
				if (value == null)
					value = "-NULL-";
				return  name + " = " + value;
			}
			

			/**
			 ** The cell renderer for time columns. This is set up in the
			 ** constructor.
			 **/
			class TimeRenderer extends DefaultTableCellRenderer 
            {
				DecimalFormat formatter = new DecimalFormat("#");
                
				public TimeRenderer() { 
					super(); 
				}
                
				public void setValue(Object value) {
					setHorizontalAlignment(JLabel.RIGHT);
					if ( value == null || ((Double)value).isNaN() ) {
						setText( "NaN");
					} else {
						setText( formatter.format(value));
					}
				}
			}


			/**
			 ** updates the stamp table with contents of the stamp array. The 
			 ** length and order of the rows may not be the same after the update,
			 ** but this method maintains the selected rows.
			 **/
			void dataRefreshed() 
			{
				if (stamps==null){
					return;
				}
				
				// don't do any of this if we are dealing with the panner view.
				if (getChild()==null){
					return;
				}

				// get array of selected stamps.
				int []    selectedRows   = table.getSelectedRows();
				Stamp []  selectedStamps = null;
				if (selectedRows != null){
					selectedStamps = new Stamp[ selectedRows.length]; 
					for (int i=0; i< selectedRows.length; i++){
						selectedStamps[i] = getStamp( selectedRows[i]);
					}
				}
				
				// rebuild the table.
				Object [][] rows = new Object[stamps.length][];
				for (int i=0; i<stamps.length; i++){
					rows[i] = stampToObject( stamps[i]);
				}

				removeAllRows(); // <- no change to list
				addRows( rows);  // <- change to list
				
				// reselect any stamps that were selected before.
				if (selectedStamps != null){
					// Improve re-selection performance by temporarily disabling
					// the auto-scrolling and list selection changes behavior.
					setAutoscrolls(false);
					ignoreTableSelectionChanges = true;
					
					for (int i =0; i < selectedStamps.length; i++) {
						// Turn back on auto-scrolling and list selection changes
						// just before the end; otherwise, the changes won't be
						// detected.
						if (i >= selectedStamps.length - 1) {
							ignoreTableSelectionChanges = false;
							setAutoscrolls(true);
						}
						
						int row = getRow( selectedStamps[i]);
						if (row != -1){
							getSelectionModel().addSelectionInterval(row, row);
						}
					}
					
					// This should have been turned back on at the end of the loop, but
					// just in case...
					ignoreTableSelectionChanges = false;
					setAutoscrolls(true);
				}
			}
			
			/**
			 ** Called when the view has a selection status toggled, updates
			 ** the selection list (which cascades to update the view through
			 ** {@link #setSelectedStamps}).
			 **/
			private void toggleSelectedStamp(Stamp s) {
				toggleSelectedStamps(new Stamp[] {s});
			}
			
			/**
			 ** Called when the view has a selection status toggled, updates
			 ** the selection list (which cascades to update the view through
			 ** {@link #setSelectedStamps}).
			 **/
			private void toggleSelectedStamps(Stamp[] ss) {
				if (ss == null)
					return;
				
				setAutoscrolls(false);
				ignoreTableSelectionChanges = true;
				int row = -1;
				
				for (int i=0; i < ss.length; i++) {
					// Re-enable table auto-scrolling and list selection change just 
					// as we are near the end of the stamp toggling (some performance optimization).
					if (i >= ss.length - 1) {
						setAutoscrolls(true);
						ignoreTableSelectionChanges = false;
					}
					
					if (ss[i] != null) {
						// Determine which stamp was clicked
						row = getRow(ss[i]);
						if (row == -1) {
							log.aprintln("stamp not in table: " + ss[i].id);
							continue;
						}
						row = getSortedIndex( row);
						
						// Toggle the row
						changeSelection(row, 0, true, false);
					}
				}
				
				// Extra resetting of table scrolling and list selection behavior
				// just in case the clever performance tweaking above goes awry.... 
				setAutoscrolls(true);
				ignoreTableSelectionChanges = false;
				
				// Scroll to make the toggled row visible for the last
				// stamp in the list.
				if (row >= 0) {
					Rectangle r = getCellRect(row, 0, false).union(
										       getCellRect(row, getColumnCount()-1, false));
					int extra = Math.min(r.height * 3, getPanel().getHeight() / 4);
					r.y -= extra;
					r.height += 2 * extra;
					scrollRectToVisible(r);
				}
				
			}
			
		} // end: inner class StampTable


	} // end: class MyFocus 



	protected void storeParms(Layer parent)
    {
		// Store initial parameters
		if (parent != null) {
			initialParms.sqlParms = ((SqlStampLayer)parent).getSqlParms();
            if (originatingFactory != null)
                initialParms.layerDesc = ((StampFactory)originatingFactory).getLayerDescription(parent);
        }
		
		initialParms.name = name;
		initialParms.initialColumns = initialColumns;
		initialParms.unsColor = unsColor;
		initialParms.filColor = filColor;
	}
	
	// Storage of initial view creation parameters
	public static class StampParameters implements SerializedParameters
    {
        static final long serialVersionUID = 8742030623145671825L;
        
        String name;
        SqlStampLayer.SqlParameters sqlParms;
        String[] initialColumns;
        Color  unsColor;
        Color  filColor;

        // New parameters starting with Version #1 (which is also a
        // new field.  Legacy ".jmars" files have same serialization
        // version UID, but do not have these fields.
        //
        // Note on "revision" field:
        //
        // revision = 0   Indicates a version of StampParameters that was
        //                serialized prior to existence of "revision" and
        //                "layerDesc" fields.  Means that literal SQL query is 
        //                the only available description of the layer's contents.
        //
        // revision = 1   First version with "revision" field defined; indicates
        //                that "layerDesc" has a useful value, i.e., latter field
        //                introduced at same time.  From this version on, the
        //                "layerDesc" field contains an equivalent field description 
        //                of the SQL query.
        // 
        // revision = 2   "Virtual" change that served as temporary functional
        //                placeholder for pending THEMIS3 conversion; non-THEMIS
        //                stamp layers are not impacted.
        //
        // revision = 3   Indicates that stored "sqlParms" is directly compatible with
        //                THEMIS3 database (for THEMIS layers only); non-THEMIS
        //                stamp layers are not impacted.  Any revision value less
        //                than 3 means that all "sqlParms" values are only compatible
        //                with THEMIS 2 (although they can be converted -- see
        //                ThemisStampFactory).
        //                
        int revision = 3;
        StampFactory.LayerDescription layerDesc;
    };
	
	// Storage of user-changeable settings
	protected static class StampSettings extends LViewSettings
    {
		// MyFilledStampFocus settings
		boolean stampOutlinesHiddenSelected;
		boolean stampSingleImageSelected;
		FilledStamp.State[] stampStateList;
	}

	protected class MyFilledStampFocus extends FilledStampFocus 
	{
        MyFilledStampFocus(StampLView parent, StampImageFactory imageFactory)
        {
            super(parent, imageFactory);
        }
        
		protected Point2D getWorldPan(int px, int py) {
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
			
			return  new Point2D.Double(px * pixelSize.getWidth(),
			                           py * pixelSize.getHeight());
		}
		
		protected void performRedrawAll(FilledStamp[] displayed) {
			performRedrawAll(displayed, true);
		}
		
		protected void performRedrawAll(FilledStamp[] displayed, final boolean redrawChild) {
			if (displayed != null)
				filled = displayed;
			
			Runnable doFilled =
				new Runnable()
				{
					public void run()
					{
						redrawEverything(redrawChild);
					}
				}
				;
			SwingUtilities.invokeLater( doFilled);
			//if (SwingUtilities.isEventDispatchThread())
			//	new Thread(doFilled).start();
			//else
			//	doFilled.run();
		}

        /**
         * Redraws or erase stamp outlines (but not selected
         * stamp outlines) as specified.
         */
        protected void redrawOutlines(boolean showOutlines)
        {
            redrawOutlines(showOutlines, true);
        }
        
        /**
         * Redraws or erase stamp outlines (but not selected
         * stamp outlines) as specified.  Redraws child view
         * if specified.
         */
        protected void redrawOutlines(boolean showOutlines, final boolean redrawChild)
        {
            hideOutlines = !showOutlines;
            setBufferVisible(OUTLINE_BUFFER_IDX, showOutlines);
            
            StampLView childView = (StampLView)getChild();
            if (childView != null &&
                hideOutlines != childView.hideOutlines) {
                childView.focusFilled.setStampOutlinesHiddenSelected(hideOutlines);
                
                // Slightly unpleasant hack necessary to get desired behavior
                // without running into other potential problems....
                childView.hideOutlines = hideOutlines;
                childView.setBufferVisible(OUTLINE_BUFFER_IDX, showOutlines);
            }

            drawOutlines();
            if (redrawChild)
                childView.drawOutlines();
        }
        
		protected synchronized void selectOccurred(boolean clearFirst, Stamp[] adds, Stamp[] dels)
        {
			if (!dualSelectionInProgress)
			{
				dualSelectionInProgress = true;
				
				createFocusPanel();
				
				synchronized(myFocus.table)
				{
					if (clearFirst)
						myFocus.table.getSelectionModel().clearSelection();
					
                    if (adds != null)
    					for (int i=0; i<adds.length; i++)
    					{
    					    if (adds[i] != null) {
    					        Object [] obj = myFocus.table.stampToObject( adds[i]);
    					        int row = myFocus.table.getIndex( obj);
    					        row = myFocus.table.getSortedIndex( row);
    					        if (row != -1)
    					            myFocus.table.getSelectionModel().addSelectionInterval(row, row);
    					    }
    					}

                    if (dels != null)
    					for (int i=0; i<dels.length; i++)
    					{
    					    if (dels[i] != null) {
    					        Object [] obj = myFocus.table.stampToObject( dels[i]);
    					        int row = myFocus.table.getIndex( obj);
    					        row = myFocus.table.getSortedIndex( row);
    					        if (row != -1)
    					            myFocus.table.getSelectionModel().removeSelectionInterval(row, row);
    					    }
    					}
				}
				
				dualSelectionInProgress = false;
			}
		}

		// Invoked as a result of table selection, in order to effect
		// selection on the list.
		public synchronized void performSelect(boolean clearFirst,
		                                       Object[] adds,
		                                       Object[] dels)
		{
		    if (!dualSelectionInProgress)
		    {
		        dualSelectionInProgress = true;
		        
		        synchronized(myFocus.table)
		        {
                    // Avoid possible conflicts with filled-focus-panel 
                    // initiated events (if latter, then the selections
                    // below are unnecessary).
                    if (!ignoreChangeEvents)
                    {
    		            ListSelectionModel lsm = listStamps.getSelectionModel();
    		            ignoreChangeEvents = true;
                        
    		            if (clearFirst)
    		                lsm.clearSelection();
    		            
    		            for (int i=0; i<adds.length; i++)
    		            {
    		                int row = listModel.indexOf(adds[i]);
    		                lsm.addSelectionInterval(row, row);
    		            }
    		            
    		            for (int i=0; i<dels.length; i++)
    		            {
    		                int row = listModel.indexOf(dels[i]);
    		                lsm.removeSelectionInterval(row, row);
    		            }
                        
                        ignoreChangeEvents = false;
                        
                        updateStampSelected();
                        if (stampSingleImageSelected())
                            redrawTriggered();
                    }
		        }
		        
		        dualSelectionInProgress = false;
		    }
		}
	} // end: class MyFilledStampFocus

	

} // end: class StampLView
