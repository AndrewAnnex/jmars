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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;

import edu.asu.jmars.layer.DataReceiver;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.FeatureProviderFactory;
import edu.asu.jmars.layer.util.features.FeatureUtil;
import edu.asu.jmars.layer.util.filetable.FileTable;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.History;

public class ShapeLayer extends Layer {
	public static final String PRESET_KEY_SHOW_LABELS = "Show Labels";
	public static final String PRESET_KEY_FILL_POLYGONS = "Fill Polygons";
	public static final String PRESET_KEY_SHOW_VERTICES = "Show Vertices";
	public static final String PRESET_KEY_DEFAULT_LINE_COLOR = "Line Color";
	public static final String PRESET_KEY_DEFAULT_FILL_COLOR = "Fill Color";
	public static final String PRESET_KEY_DEFAULT_LABEL_COLOR = "Label Color";
	public static final String PRESET_KEY_DEFAULT_LINE_DIRECTED = "Line Direction";
	public static final String PRESET_KEY_DEFAULT_LINE_TYPE = "Line Type";
	public static final String PRESET_KEY_DEFAULT_LINE_WIDTH = "Line Width";
	public static final String PRESET_KEY_DEFAULT_POINT_SIZE = "Point Size";
	public static final String PRESET_KEY_SELECTED_LINE_COLOR = "Selected Line Color";
	public static final String PRESET_KEY_SELECTED_LINE_WIDTH = "Selected Line Width";
	public static final String PRESET_KEY_LINE_WIDTH = "Line Width";
	public static final String PRESET_KEY_POINT_SIZE = PRESET_KEY_DEFAULT_POINT_SIZE;
	public static final String PRESET_KEY_SHOW_PROGRESS = "Show Progress";
	public static final String PRESET_KEY_ANTIALIASING = "Antialiasing";
	
	Map presets = new HashMap();
	
	/** History size is obtained from the specified key. */
	public static final String CONFIG_KEY_HISTORY_SIZE = "shape.history_size";

    /** FeatureCollection for this Layer */
    private FeatureCollection mfc;
   
    /** FileTable for this layer */
    // TODO: this should be owned by the focus panel, NOT the layer!
	private FileTable fileTable;

    /** History for changes to 'mfc' */
    private History history;
    
    /** StateChange listeners. */
    private List listeners = new LinkedList();
    
    /** Keep track of states. */
    private LinkedList statusLEDStack = new LinkedList();

    /** Factory for producing FeatureProviders */
    private FeatureProviderFactory providerFactory;

    public static final Integer STATUS_FILE_IO = new Integer(3);
    public static final Integer STATUS_PROCESSING = new Integer(2);
    public static final Integer STATUS_DRAWING = new Integer(1);
    public static final Integer STATUS_ALL_DONE = new Integer(0);
    public static final Integer STATUS_UNKNOWN = new Integer(-1);
    
    /*
     * Status to color mapping
     */
    protected static Map statusLEDColor;
    static {
    	statusLEDColor = new HashMap();
    	statusLEDColor.put(LEDStateFileIO.class, Color.RED);
    	statusLEDColor.put(LEDStateProcessing.class, Color.ORANGE);
    	statusLEDColor.put(LEDStateDrawing.class, Color.YELLOW);
    	statusLEDColor.put(LEDStateAllDone.class, Color.GREEN.darker());
    	statusLEDColor.put(LEDStateUnknown.class, Color.GRAY);
    }

	
	public ShapeLayer() {
		super();

	    String[] providers = Config.getArray("shape.featurefactory");
	    providerFactory = new FeatureProviderFactory(providers);
		
		initPresets();

		// Put an all-done status on the status stack.
		statusLEDStack.add(new LEDStateAllDone());

		// Tie FileTable, MultiFeatureCollection and FeatureTable together.
		fileTable = new FileTable();

		this.mfc = fileTable.getMultiFeatureCollection ();
		// Initialize a history object that will contain a log of all undoable changes.
		int historySize = Config.get(CONFIG_KEY_HISTORY_SIZE, 10);
		history = new History(historySize);
	}

	public FileTable getFileTable() {
		return fileTable;
	}

	/** Initializes presets. */
	protected void initPresets(){
		presets.put(PRESET_KEY_SHOW_LABELS, Boolean.TRUE);
		presets.put(PRESET_KEY_FILL_POLYGONS, Boolean.TRUE);
		presets.put(PRESET_KEY_SHOW_VERTICES, Boolean.FALSE);
		presets.put(PRESET_KEY_DEFAULT_LINE_COLOR, new Color(255,255,255));
		presets.put(PRESET_KEY_DEFAULT_FILL_COLOR, new Color(255,0,0));
		presets.put(PRESET_KEY_DEFAULT_LABEL_COLOR, new Color(255,255,255));
		presets.put(PRESET_KEY_DEFAULT_LINE_DIRECTED, Boolean.FALSE);
		presets.put(PRESET_KEY_DEFAULT_LINE_TYPE, new Integer(0));
		presets.put(PRESET_KEY_LINE_WIDTH, new Float(1.0f));
		presets.put(PRESET_KEY_POINT_SIZE, new Integer(3));
		presets.put(PRESET_KEY_SELECTED_LINE_COLOR, new Color(255,255,0));
		presets.put(PRESET_KEY_SELECTED_LINE_WIDTH, new Float(3.0f));
		presets.put(PRESET_KEY_SHOW_PROGRESS, Boolean.FALSE);
		presets.put(PRESET_KEY_ANTIALIASING, Boolean.TRUE);
	}

	public void receiveRequest(Object layerRequest, DataReceiver requester) {
		// TODO Auto-generated method stub

	}

	public FeatureCollection getFeatureCollection(){
		return mfc;
	}
	
	/**
	 * Returns the selected features in the FeatureCollection order.
	 */
	public List getSelectedFeatures () {
		return FeatureUtil.getSelectedFeatures(mfc);
	}
	
	/**
	 * Returns the unselected features in the FeatureCollection order.
	 */
	public List getUnselectedFeatures(){
		return FeatureUtil.getUnselectedFeatures(mfc);
	}
	
	public History getHistory(){
		return history;
	}

	// TODO Harden these
	public void setPreset(String key, Object value){
		presets.put(key, value);
		fireStateChangeEvent(Collections.singletonList(key));
	}
	
	public void setPresets(Map presets){
		presets.putAll(presets);
		fireStateChangeEvent(new ArrayList(presets.keySet()));
	}
	
	public Object getPreset(String key){
		return presets.get(key);
	}
	
	public Map getPresets(Set keys){
		Map p = new HashMap();
		for(Iterator ki=keys.iterator(); ki.hasNext(); ){
			Object key = ki.next();
			p.put(key, presets.get(key));
		}
		return p;
	}
	
	/**
	 * Fire off a StateChangeEvent to all the listeners notifying them
	 * that the specified components of the ShapeLayer state have been
	 * modified.
	 *  
	 * @param stateVarNames A List of state variable names.
	 */
	public void fireStateChangeEvent(List stateVarNames){
		StateChangeEvent e = null;
		
		for(Iterator i=listeners.iterator(); i.hasNext(); ){
			if (e == null)
				e = new StateChangeEvent(this, stateVarNames);
			((StateChangeListener)i.next()).stateChanged(e);
		}
	}
	
	public void addStateChangeListener(StateChangeListener l) {
		listeners.add(l);
	}
	public boolean removeStateChangeListener(StateChangeListener l){
		return listeners.remove(l);
	}
	public List getStateChangeListeners(){
		return Collections.unmodifiableList(listeners);
	}

	public void begin(LEDState state){
		final Color c;
		synchronized(statusLEDStack){
			statusLEDStack.add(state);
			Color cc = (Color)statusLEDColor.get(state.getClass());
			if (cc == null)
				cc = (Color)statusLEDColor.get(LEDStateUnknown.class);
			
			c = cc;
		}
		
		Runnable r = new Runnable(){
			public void run(){
				setStatus(c);
			}
		};
		
		if (SwingUtilities.isEventDispatchThread()){
			r.run();
		}
		else {
			SwingUtilities.invokeLater(r);
		}
	}
	
	public void end(LEDState state){
		final Color c;
		
		synchronized(statusLEDStack){
			statusLEDStack.remove(state);
			Color cc = null;
			if (statusLEDStack.size() > 0)
				cc = (Color)statusLEDColor.get(statusLEDStack.getLast().getClass());
			if (cc == null)
				cc = (Color)statusLEDColor.get(LEDStateUnknown.class);
			
			c = cc;
		}
		
		Runnable r = new Runnable(){
			public void run(){
				setStatus(c);
			}
		};
		
		if (SwingUtilities.isEventDispatchThread()){
			r.run();
		}
		else {
			SwingUtilities.invokeLater(r);
		}
	}
	
	public static class LEDStateProcessing extends LEDState {}
	public static class LEDStateFileIO extends LEDState {}
	public static class LEDStateAllDone extends LEDState {}
	public static class LEDStateDrawing extends LEDState {}
	private static class LEDStateUnknown extends LEDState {}
	public abstract static class LEDState {}

	/**
	 * Returns the provider factory created from the FeatureProvider class
	 * names in jmars.config. See FeatureProviderFactory for more info.
	 */
	public FeatureProviderFactory getProviderFactory () {
		return providerFactory;
	}
}
