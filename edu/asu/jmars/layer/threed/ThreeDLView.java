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


package edu.asu.jmars.layer.threed;

import java.awt.geom.Rectangle2D;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj;
import edu.asu.jmars.layer.Layer.LView;

/**
 * Listens to view change events and pushes them to the Layer, and manages the
 * focus panel's view settings.
 */
public class ThreeDLView extends LView {
	/** The user controls options */
	private ThreeDSettings settings;
	/** The focus panel */
	private ThreeDFocus focus;
	/** The layer */
	private ThreeDLayer myLayer;
	
	public ThreeDLView(ThreeDLayer parent, ThreeDSettings s) {
		super(parent);
		myLayer = parent;
		settings = s;
	}
	
	protected LView _new() {
		return new ThreeDLView(myLayer, settings);
	}
	
	/**
	 ** Quick flag to indicate that a view has been deleted. This is to
	 ** prevent updates at deletion time from reaching the focus panel
	 ** and causing the 3d window to reappear. (added by Michael as
	 ** quick fix)
	 **/
	boolean isDead = false;
	
	/**
	 * When the parent LView is destroyed, the 3D viewer is removed and the
	 * LView is marked dead so no further updates are sent to the viewer
	 */
	public void viewCleanup() {
		isDead = true;
		if (focusPanel != null) {
			((ThreeDFocus) focusPanel).destroyViewer();
		}
	}
	
	public String getName() {
		return "3D viewer";
	}
	
	/** Get the focus panel of the parent LView, lazily creating it if necessary */
	public ThreeDFocus getFocusPanel() {
		if (getParentLView() != null) {
			return (ThreeDFocus)getParentLView().getFocusPanel();
		}
		if (focus == null) {
			// set the base class reference too!
			focusPanel = focus = new ThreeDFocus(myLayer, this, settings);
		}
		return focus;
	}
	
	/**
	 * Create request to send to the Layer, which will filter on an LView from
	 * which to create the 3D view. This is only called when the layer is visible,
	 * and so requests are only sent when someone explicitly enables a layer,
	 * we disable visibility immediately.
	 */
	protected Object createRequest(Rectangle2D where) {
		if (isAlive()) {
			return new Request(this, getProj().getWorldWindow(), viewman2.getMagnify(), Main.PO);
		} else {
			return null;
		}
	}
	
	/**
	 * These LViews do nothing except forward view changes to the Layer, so the
	 * Layer never sends data back.
	 */
	public synchronized void receiveData(Object layerData) {}
	
	// either saves the settings to the settings file or loads the settings
	// out of the settings file.  Loading only happens if the user specified a config
	// file for JMARS on start up.  
	//
	// This is an overloading of a method in the superclass.
	protected void updateSettings(boolean saving) {
		String key = "ThreeDparms";
		
		// save settings
		if (saving == true) {
			ThreeDFocus fp = (ThreeDFocus) getFocusPanel();
			ThreeDSettings s = fp.getSettings();
			viewSettings.put(key, s);
		}
		
		// load settings
		else {
			if (viewSettings.containsKey(key)) {
				settings = (ThreeDSettings) viewSettings.get(key);
				focusPanel = getFocusPanel();
			}
		}
	}
}

class Request {
	public final ThreeDLView source;
	public final Rectangle2D extent;
	public final int ppd;
	public final ProjObj projection;
	public Request(ThreeDLView source, Rectangle2D extent, int ppd, ProjObj projection) {
		this.source = source;
		this.extent = extent;
		this.ppd = ppd;
		this.projection = projection;
	}
	public String toString() {
		return "MapRequest[source="+source+", extent="+extent+", ppd="+ppd+", proj="+projection+"]";
	}
}
