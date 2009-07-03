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

import java.awt.image.Raster;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.asu.jmars.layer.DataReceiver;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.layer.map2.MapChannel;
import edu.asu.jmars.layer.map2.MapChannelReceiver;
import edu.asu.jmars.layer.map2.MapData;
import edu.asu.jmars.layer.map2.MapRequest;
import edu.asu.jmars.layer.map2.MapServer;
import edu.asu.jmars.layer.map2.MapServerFactory;
import edu.asu.jmars.layer.map2.MapSource;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;

/* Layer data model */
public class ThreeDLayer extends Layer {
	private static DebugLog log = DebugLog.instance();
	
	/** The active view that provides the viewing geometry for the 3D panel */
    private ThreeDLView activeView;
    /** Most recent requests from all views */
    private Map<ThreeDLView, Request> requests = new LinkedHashMap<ThreeDLView,Request>();
    /** Map to use for elevation data */
    private MapSource elevationSource;
    /** Map produer provides maps for current request for current view for current map source */
    private MapChannel mapProducer = new MapChannel();
    /** Map data last received from the map producer */
    private MapData lastUpdate;
    
    public ThreeDLayer(StartupParameters parms) {
		initialLayerData = parms;
		elevationSource = parms.getMapSource();
	}
    
    /** Map to use for elevation data */
    public MapSource getElevationSource() {
    	return elevationSource;
    }
    
    /** Change map to use for elevation data */
    public void setElevationSource(MapSource source) {
    	this.elevationSource = source;
    }
    
	/** The active view that provides the viewing geometry for the 3D panel */
    public ThreeDLView getActiveView() {
    	return activeView;
    }
    
    /** Change the active view that provides the viewing geometry for the 3D panel */
    public void setActiveView(ThreeDLView view) {
    	this.activeView = view;
    }
    
    /** Returns the raster for the current elevation data */
    public Raster getElevationData() {
    	return lastUpdate.getImage().getRaster();
    }
    
    /**
	 * Saves all requests in case the user changes which view drives the 3D
	 * layer, and builds a MapChannel for the active view.
	 * 
	 * The resulting MapData is cached and pushed to the focus panel when
	 * finished.
	 */
    public void receiveRequest(Object layerRequest, DataReceiver requester) {
    	Request request = (Request)layerRequest;
    	Request oldRequest = requests.put(request.source, request);
    	log.println("Old request: " + oldRequest);
    	if (request.source == getActiveView() && elevationSource != null) {
        	mapProducer.setRequest(new MapRequest(elevationSource, request.extent, request.ppd, request.projection));
        	mapProducer.addReceiver(new MapChannelReceiver() {
    			public void mapChanged(MapData mapData) {
    				if (mapData.isFinished()) {
    					lastUpdate = mapData;
    					activeView.getFocusPanel().update();
    					activeView.setVisible(false);
    				}
    			}
        	});
    	}
	}
}

/**
 * Serialiable object to save 3D layer construction state to a JMARS session file.
 * 
 * Since the MapServer/MapSource objects are static data recreated when JMARS is
 * restarted, we store the server name and source name as unique identifiers and
 * look them up during layer reconstruction.
 * 
 * IMPORTANT! do not change the order or content of the instance variables, or
 * users won't be able to restore saved 3D layer sessions.
 */
class StartupParameters extends HashMap<String,String> implements SerializedParameters {
	private transient DebugLog log = DebugLog.instance();
	
	/** Initializes the elevation data from the jmars.config 'threed.default_elevation*' keys */
	public StartupParameters() {
		put("serverName", Config.get("threed.default_elevation.server"));
		put("sourceName", Config.get("threed.default_elevation.source"));
	}
	
	public MapSource getMapSource() {
		MapServer server = MapServerFactory.getServerByName(get("serverName"));
		if (server == null) {
			log.aprintln("Elevation server not accessible");
			return null;
		}
		
		MapSource source = server.getSourceByName(get("sourceName"));
		if (source == null) {
			log.aprintln("Elevation source not accessible");
			return null;
		}
		
		return source;
	}
	
	public void setMapSource(MapSource source) {
		put("serverName", source.getServer().getName());
		put("sourceName", source.getName());
	}
}
