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
import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.Hashtable;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;

import edu.asu.jmars.layer.LManager;
import edu.asu.jmars.layer.LViewFactory;
import edu.asu.jmars.layer.LViewManager;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.ruler.BaseRuler;
import edu.asu.jmars.ruler.RulerManager;
import edu.asu.jmars.swing.TabLabel;
import edu.asu.jmars.util.DebugLog;

public class TestDriverLayered extends JPanel
{
    private static DebugLog log = DebugLog.instance();
    
    public  LocationManager locMgr;
    public  LViewManager    mainWindow;
    private LViewManager    panner;
    public  LManager	lmanager;
    
    protected TabLabel statusBar;
    protected JSplitPane splitPane;
    
    public static final int    INITIAL_MAIN_ZOOM;
    public static final int    INITIAL_PANNER_ZOOM;
    static {
	if ( Main.isStudentApplication() ) {
	    INITIAL_MAIN_ZOOM = 16;
	    INITIAL_PANNER_ZOOM = 4;
	} else {
	    INITIAL_MAIN_ZOOM = 32;
	    INITIAL_PANNER_ZOOM = 8;
	}
    }
    
    boolean ignorePreviousState = false;    
    
    public TestDriverLayered()
    {
	//if we have switched projects or from student mode, etc then ignore saved starting place and views.
	boolean wasTimeProjection = Main.userProps.getPropertyBool("TimeProjection", false);
	boolean wasStudentApp = Main.userProps.getPropertyBool("StudentApplication", false);
	boolean ignorePreviousState = false;
	if ( wasTimeProjection && ! Main.inTimeProjection() ||
	     ! wasTimeProjection && Main.inTimeProjection() ||
	     wasStudentApp && ! Main.isStudentApplication() ||
	     ! wasStudentApp && Main.isStudentApplication() ) {

	    ignorePreviousState = true;
	}

	// location manager - look for a save initial value
	String initialX = Main.userProps.getProperty("Initialx", "");
	String initialY = Main.userProps.getProperty("Initialy", "");
	String serverOffset = Main.userProps.getProperty("ServerOffset", "");

	locMgr = new LocationManager(Main.initialWorldLocation);

	//Time passed in on command line has precedence
	if ( ! ignorePreviousState && !Main.timeOnCommandLine && ( initialX != "" && initialY != "" ) ) {

	    Point2D.Double pt = null;

	    try {

		double offsetX = Double.parseDouble(serverOffset) - Main.PO.getServerOffsetX();
		double newX = Double.parseDouble(initialX) + offsetX;

		pt = new Point2D.Double(newX, (new Double(initialY)).doubleValue());
	    } catch ( Exception ex) {
		//ignore error so default is simply null
	    }
	    if ( pt != null )
		locMgr.setLocation(pt, false);
	}

	// LViewManager - first get saved values
	int mainZoom = Main.userProps.getPropertyInt("MainZoom", INITIAL_MAIN_ZOOM);
	int pannerZoom = Main.userProps.getPropertyInt("PannerZoom", INITIAL_PANNER_ZOOM);

	locMgr.setZoom(mainZoom);
	mainWindow = new LViewManager(locMgr, mainZoom);
	panner	   = new LViewManager(locMgr, mainWindow, pannerZoom);
	
	// Create the status bar
	statusBar = new TabLabel(" ");
	
	statusBar.setFont(new JTextField().getFont());
	statusBar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

	// lay them out
	setLayout(new BorderLayout());
	setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
	mainWindow.setMinimumSize(new Dimension( 20,  20));
	panner	  .setMinimumSize(new Dimension( 20,  20));
	panner	.setPreferredSize(new Dimension(100, 100));
	splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,mainWindow, panner);
	splitPane.setResizeWeight(1.0);
	splitPane.setBorder(new CompoundBorder(
			BorderFactory.createEmptyBorder(10,0,10,0),
			BorderFactory.createBevelBorder(BevelBorder.LOWERED))
	    );

	int height = Main.userProps.getPropertyInt("SplitPaneHeight", 400);
	int width =  Main.userProps.getPropertyInt("SplitPaneWidth",  500);
	int div = Main.userProps.getPropertyInt("MainDividerLoc", splitPane.getDividerLocation());
	splitPane.setPreferredSize( new Dimension( width, height));
	splitPane.setDividerLocation(div);

	add(locMgr,    BorderLayout.NORTH);
	add(splitPane, BorderLayout.CENTER);
	add(statusBar, BorderLayout.SOUTH);
    }

    public Dimension getMainLViewManagerSize() {
	return mainWindow.getSize();
    }
    
    public void setMainLViewManagerSize(Dimension d) {
	mainWindow.setSize(d);
	mainWindow.validate();
	mainWindow.repaintChildVMan();
    }
    
    public void dumpMainLViewManagerJpg(String filename) {
	mainWindow.dumpJpg(filename);
    }
    
    public void dumpMainLViewManagerPNG(String filename) {
        mainWindow.dumpPNG(filename);
    }
    
    public LManager getLManager() {
	return lmanager;
    }
    
    

   public void loadLManagerState() 
    {
       //make big if not preset
       if(!Main.userProps.setWindowPosition(lmanager))
	{
	   Dimension currSize = lmanager.getSize();
	   lmanager.setSize(currSize.width, 500);
	}
	
       lmanager.validate();

       try
	{
	   lmanager.setDockingStates(
	       Main.userProps.getProperty("LManager.tabDocking"));
	}
       catch(Exception e)
	{
	   log.aprintln(e);
	   log.aprintln("Failed to set layer manager tab positions");
	}
	
       if(!Main.userProps.wasWindowShowing(lmanager))
	   lmanager.setVisible(false);
    }


	// Get the properties of any defined rulers and general ruler properties.
	public void loadRulersState()
	{
		
		Hashtable allRulerSettings = (Hashtable)edu.asu.jmars.Main.userProps.loadUserObject( "AllRulerSettings");
		if (allRulerSettings != null){
			RulerManager.Instance.loadSettings( allRulerSettings);
		}

		int rulerCount = Main.userProps.getPropertyInt("RulerCount", 0);
		for (int j=0; j < rulerCount; j++) {
			String rulerLabel = "Ruler" + String.valueOf(j);
			String rulerName = edu.asu.jmars.Main.userProps.getProperty( rulerLabel, "");
			BaseRuler ruler = (BaseRuler)RulerManager.Instance.getRuler( rulerName);
			if (ruler!=null){
				Hashtable rulerSettings = (Hashtable)edu.asu.jmars.Main.userProps.loadUserObject( rulerLabel + "Settings");
				ruler.loadSettings( rulerSettings);
				if (j==0){
					Hashtable settings = (Hashtable)edu.asu.jmars.Main.userProps.loadUserObject( "BaseRulerSettings");
					ruler.loadBaseRulerSettings( settings);
				}
			} 
		}
	}
	
	// Save the properties of any defined rulers and general ruler properties.
	public void saveRulersState()
	{
		Hashtable allRulerSettings = RulerManager.Instance.saveSettings();
		if (allRulerSettings != null){
			Main.userProps.saveUserObject( "AllRulerSettings", allRulerSettings);
		}

		int rulerCount = RulerManager.Instance.rulerList.size();
		Main.userProps.setPropertyInt("RulerCount", rulerCount);
		for (int j=0; j < rulerCount; j++) {
			BaseRuler ruler = (BaseRuler)RulerManager.Instance.rulerList.get(j);
			Main.userProps.setProperty( "Ruler" + String.valueOf(j), ruler.getClass().getName());
			Hashtable rulerSettings = ruler.saveSettings();
			Main.userProps.saveUserObject( "Ruler" + String.valueOf(j) + "Settings", rulerSettings);
			if (j==0){
				Hashtable settings = ruler.saveBaseRulerSettings();
				Main.userProps.saveUserObject( "BaseRulerSettings", settings);
			}
		}
	}


    public void saveState()
    {
	// save the general JMARS stuff.
	Main.userProps.saveWindowPosition(lmanager);
	try
	 {
		Main.userProps.setProperty("LManager.tabDocking",
								   lmanager.getDockingStates());
	 }
	catch(Exception e)
	 {
		log.aprintln(e);
		log.aprintln("Failed to save layer manager tab locations");
	 }
	Main.userProps.setProperty("SplitPaneHeight", String.valueOf(splitPane.getSize().height));
	Main.userProps.setProperty("SplitPaneWidth", String.valueOf(splitPane.getSize().width));
	Main.userProps.setProperty("jmars.user", Main.USER);
	Main.userProps.setPropertyBool("TimeProjection",     Main.inTimeProjection());
	Main.userProps.setPropertyBool("StudentApplication", Main.isStudentApplication());
	Main.userProps.setPropertyInt("MainDividerLoc",	     splitPane.getDividerLocation());
	Main.userProps.setPropertyInt("MainZoom",	 mainWindow.getMagnification());
	Main.userProps.setPropertyInt("PannerZoom",	 panner.getMagnification());
	Main.userProps.setProperty("Initialx",		 String.valueOf( locMgr.getLoc().getX() ));
	Main.userProps.setProperty("Initialy",		 String.valueOf( locMgr.getLoc().getY() ));
	// note: JMARS west lon => USER east lon
	Main.userProps.setProperty("Projection_lon",	     String.valueOf((360-Main.PO.getCenterLon())%360));
	Main.userProps.setProperty("Projection_lat",	     String.valueOf(Main.PO.getCenterLat()));
	Main.userProps.setProperty("ServerOffset",	 String.valueOf(Main.PO.getServerOffsetX()));
	
	// Set the general ruler properties.
	RulerManager.Instance.saveSettings();
	Main.userProps.saveUserObject("RulerSettings",	     RulerManager.Instance.rulerSettings);

	// Set the properties of any defined rulers.
	saveRulersState();

	// Set the properties of any defined views. 
	Main.userProps.setPropertyInt("ViewCount", mainWindow.viewList.size());
	Iterator iterViews = mainWindow.viewList.iterator();
	int i=1;
	while(iterViews.hasNext()) {
	    Layer.LView lview = (Layer.LView) iterViews.next();
	    
	    if(lview.originatingFactory == null)
		continue;
	    Main.userProps.setProperty("View" + String.valueOf(i), lview.originatingFactory.getClass().getName());
	    
	    //Store the views starting parms in a file if available
	    SerializedParameters parms = lview.getInitialLayerData();
	    if ( parms != null ) {
		Main.userProps.saveUserObject("View" + String.valueOf(i) + "Parms", parms);
	    }
	    
	    //Store the views current settings in a file if available
	    Hashtable sparms = lview.getViewSettings();
	    if ( sparms != null ) {
		Main.userProps.saveUserObject("View" + String.valueOf(i) + "Settings", sparms);
	    }
	    i++;
	}
    }
    

	// builds the views if there were any defined in the application properties, 
	public void buildViews()
	{
		//Determine if there were saved views
		int viewCnt = Main.userProps.getPropertyInt("ViewCount", 0);

		// If we aren't getting things from an init file, build default
		// views.
		if ( viewCnt == 0 || ignorePreviousState ) {
			// Simulate some simple factory function results for now
			Iterator iter = LViewFactory.factoryList.iterator();

			// For right now: just create one default version of every
			// possible view
			while(iter.hasNext()){
				Layer.LView view;
				LViewFactory factory = (LViewFactory) iter.next();
				view=factory.createLView();
				if(view != null) {
					mainWindow.viewList.add(view);
				}
			}

		}

		// If we ARE getting views from an init file, build them now.
		else {
			Layer.LView view = null;
			for ( int i=1; i <=viewCnt; i++ ) {

				//Look for a serialized initial parameter block and start the view with the
				//data if present.
				String factoryName = Main.userProps.getProperty("View" + String.valueOf(i), "");
				LViewFactory factory = LViewFactory.getFactoryObject(factoryName);

				if ( factory != null ) {
					SerializedParameters obj = 
						(SerializedParameters) Main.userProps.loadUserObject("View" + String.valueOf(i) + "Parms");
					view = factory.recreateLView(obj);
					if (view != null) {
						Hashtable sobj = 
							(Hashtable) Main.userProps.loadUserObject("View" + String.valueOf(i) + "Settings");
						if ( sobj != null ){
							view.setViewSettings(sobj);
						}
						mainWindow.viewList.add(view);
					}
				}
			}
		}
	} // end: buildViews()
    
	/** Recenters all LViewManagers to a new location given by p */
	public void offsetToWorld(Point2D p) {
		mainWindow.getGlassPanel().offsetToWorld(p);
		panner.getGlassPanel().offsetToWorld(p);
		
		locMgr.setLocation(p, false);
		mainWindow.setLocationAndZoom(p, mainWindow.getMagnification());
	}
} // end: class TestDriverLayered
