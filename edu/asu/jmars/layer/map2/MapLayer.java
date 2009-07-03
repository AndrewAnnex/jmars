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


package edu.asu.jmars.layer.map2;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import edu.asu.jmars.layer.DataReceiver;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.map2.msd.MapSettingsDialog;
import edu.asu.jmars.layer.map2.stages.composite.HSVComposite;
import edu.asu.jmars.layer.map2.stages.composite.RGBComposite;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

public class MapLayer extends Layer {
	private static DebugLog log = DebugLog.instance();
	final MapSettingsDialog mapSettingsDialog;
	MapFocusPanel focusPanel;
	private String name;
	
	public MapLayer(MapSettingsDialog dialog) {
		this.mapSettingsDialog = dialog;
	}
	
	/**
	 * Current state map; receivers that go to the lowest state are REMOVED so
	 * we don't get thousands of accumulated, pointless entries here
	 */
	private Map<DataReceiver,Color> receiverStates = new HashMap<DataReceiver,Color>();
	/** Set legal colors here, listed from lowest priority to highest */
	private List<Color> orderedStates = Arrays.asList(Util.darkGreen, Color.yellow, Util.darkRed);
	
	public synchronized void monitoredSetStatus(DataReceiver r, Color state) {
		int order = orderedStates.indexOf(state);
		if (order == -1) {
			log.println("Invalid STATUS light update");
			return;
		}
		if (order == 0)
			receiverStates.remove(r);
		else
			receiverStates.put(r, state);
		int max = 0;
		for (DataReceiver dr: receiverStates.keySet()) {
			int cur = orderedStates.indexOf(receiverStates.get(dr));
			if (cur > max)
				max = cur;
		}
		final Color stateColor = (Color)orderedStates.get(max);
		
		// Set the status on the AWT thread.
		SwingUtilities.invokeLater(new Runnable(){
			public void run() {
				setStatus(stateColor);
			}
		});
	}
	
	/** Unused */
	public void receiveRequest(Object layerRequest, DataReceiver requester) {}
	
	public void setName(String name) {
		this.name = name;
	}
	public String getName() {
		return name;
	}
}
