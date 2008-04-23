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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

/**
 * WMSMapServer stores properties for a WMS Map Server.
 */
public class WMSMapServer implements MapServer, Serializable {
	private static DebugLog log = DebugLog.instance();
	private static final long serialVersionUID = -691994868567732767L;
	
	/** The name of the default map server */
	public static String DEFAULT_NAME = Config.get("map.default.server");
	
	/** Prefix for all MapServer attributes stored in jmars.config */
	public static final String prefix = "map.map_server";
	
	/** Gets the jmars.config key that stores this attribute for this MapServer */
	private static String getAttrKey(String name, String attr) {
		return prefix + "." + name + "." + attr;
	}
	
	// Object properties, constructors, and accessors
	
	private String name;
	private boolean userDefined;
	private int timeout;
	private int maxRequests;
	private URI uri;
	private String title;
	
	/** Transient since GetMap service URL can be recovered from the capabilities document */
	private transient WMSCapabilities capabilities;
	/** Listeners who care about changes in this MapServer */
	private transient List<MapServerListener> listeners = new LinkedList<MapServerListener>();
	/** Cached map sources for this server */
	private transient List<MapSource> sources = new LinkedList<MapSource>();
	
	/** Create a new MapServer object, loading capabilities immediately */
	public WMSMapServer(String newURL, int newTimeout, int newMaxRequests) {
		name = newURL.replaceAll("[^0-9a-zA-Z]", "_").replaceAll("_+", "_");
		try {
			uri = new URI(newURL);
		} catch (URISyntaxException e) {
			log.println("Failed to create new server from URL " + newURL);
			log.println(e);
			throw new IllegalArgumentException("Invalid URL: " + e.getMessage(), e);
		}
		timeout = newTimeout;
		maxRequests = newMaxRequests;
		userDefined = true;
		loadCapabilities(false);
	}
	
	/**
	 * Load a MapServer from jmars.config. The capabilities are loaded from disk.
	 * @param savedName The canonic name of the server. This must contain no spaces
	 * and be unique within this jmars.config.
	 */
	public WMSMapServer(String serverName) {
		load(serverName);
		loadCapabilities(false);
	}
	
	/**
	 * Loads a MapServer from jmars.config, suffixing the URL with the given string.
	 * The capabilities are loaded from disk if available.
	 */
	public WMSMapServer(String serverName, String urlSuffix) {
		load(serverName);
		uri = getURI(urlSuffix);
		loadCapabilities(false);
	}
	
	/**
	 * Returns a unique (canonic) name for this server.
	 * 
	 * This will be a compressed form of the server URL for custom servers.
	 * 
	 * This value must NOT be null.
	 */
	public final String getName() {
		return name;
	}
	
	/**
	 * Flag indicates whether this MapServer is custom or not. When its true, it
	 * means the server was added by the user, can be removed by the user, and
	 * is saved in a writable jmars.config.
	 */
	public final boolean isUserDefined() {
		return userDefined;
	}
	
	/** Timeout in milliseconds */
	public final int getTimeout() {
		return timeout;
	}
	
	/** Maximum number of times downloads from this server should be retried */
	public final int getMaxRequests() {
		return maxRequests;
	}
	
	/** Get the Capabilities service URI */
	public final URI getURI() {
		return uri;
	}
	
	/** Get the capabilities service URI with extra 'name=value' parameters */
	public final URI getURI(String ... args) {
		try {
			String query = uri.getQuery();
			if (query == null)
				query = "";
			if (query.length() > 0 && !query.endsWith("&"))
				query += "&";
			query += Util.join("&", args);
			return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
				uri.getPort(), uri.getPath(), query, uri.getFragment());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Could not construct modified URI: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Get the GetMap service URL. This URL comes from the Capabilities
	 * document, so the blocking IO required to get this URL is done here
	 * on the first call, rather than in the constructor.
	 */
	public final String getMapUrl() {
		return capabilities.getMapURI().toString();
	}
	
	public final String getTitle() {
		return title;
	}
	
	public String toString() {
		return title;
	}
	
	/**
	 * Returns list of MapSource objects. This method caches map sources to avoid
	 * duplicating the expensive download and XML parsing operations.
	 * @param cached If false, or this method is being called for the first time,
	 * the capabilities document will be redownloaded and parsed.
	 */
	public List<MapSource> getMapSources() {
		return Collections.unmodifiableList(sources);
	}
	
	/** Defines equality as identity - other attributes than URL can vary */
	public boolean equals(Object o){
		return o instanceof MapServer && ((MapServer)o).getURI().toString().equals(getURI().toString());
	}
	
	/** Defines equality as identity - other attributes than URL can vary */
	public int hashCode(){
		return getURI().hashCode();
	}
	
	/** Returns the MapSource with the given name, or null if not found */
	public MapSource getSourceByName(String name) {
		for (MapSource source: getMapSources()) {
			if (source.getName().equals(name)) {
				return source;
			}
		}
		return null;
	}
	
	public final void addListener(MapServerListener l) {
		listeners.add(l);
	}
	
	public final void removeListener(MapServerListener l) {
		listeners.remove(l);
	}
	
	/**
	 * Notifies all listeners of a change in the available maps.
	 * For now this is hard-coded to those parts of the user interface that need to know.
	 */
	private final void notifyListeners(MapSource source, MapServerListener.Type changeType) {
		for (MapServerListener l: listeners) {
			l.mapChanged(source, changeType);
		}
	}
	
	/**
	 * Loads capabilities for this server.
	 * 
	 * <ol>
	 * <li>If capabilities have already been loaded and cached data is requested, this method simply returns.
	 * <li>If capabilities haven't been loaded and cached data is requested, capabilities are loaded from disk.
	 * <li>If cached data is not requested, capabilities are loaded from the map server.
	 * </ol>
	 * 
	 * @throws IllegalStateException Thrown when this method cannot update
	 * the capabilities, and cannot leave the capabilities in a consistent
	 * state (such as when loading capabilities for the first time.)
	 */
	public final synchronized void loadCapabilities(boolean cached) {
		if (capabilities != null && cached) {
			return;
		}
		
		// get backup of layers
		List<WMSLayer> oldLayers = Collections.emptyList();
		if (capabilities != null)
			oldLayers = new LinkedList<WMSLayer>(capabilities.getLayers());
		
		WMSCapabilities newCaps = null;
		try {
			// TODO: should put the user's name in here, to prevent causing problems caching
			// multiple JMARS user's custom maps to a single computer user's home directory.
			final String fileName = Main.getJMarsPath() + "wms_" + Util.urlEncode(getName()) + ".xml";
			URI networkURI = getURI("service=wms","wmsver=1.1.1","request=GetCapabilities");
			
			// determine if we're using disk caching
			boolean diskCache = Config.get("map.capabilities.cache", true);
			
			// determine where we'll load from this time
			final File file = new File(fileName);
			boolean diskLoad = diskCache && cached && file.exists();
			log.println("Loading capabilities from " + (diskLoad ? file.toURI() : networkURI));
			
			// try the network if we're not getting from disk
			if (!diskLoad) {
				try {
					HttpURLConnection conn = (HttpURLConnection)networkURI.toURL().openConnection();
					if (diskCache && file.exists()) {
						conn.setIfModifiedSince(file.lastModified());
					}
					conn.connect();
					if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
						log.println("Capabilities updated from network");
						newCaps = new WMSCapabilities(conn.getInputStream());
						if (diskCache) {
							// save on another thread, since it takes a second that we
							// don't want to slow down startup
							final long modTime = conn.getLastModified();
							new Thread(new Runnable() {
								public void run() {
									try {
										synchronized(WMSMapServer.this) {
											capabilities.save(file, modTime);
										}
									} catch (Exception e) {
										log.aprintln("Failure saving capabilities for " +
											WMSMapServer.this.getName() + " to " + fileName + ": " + e.getMessage());
										log.println(e);
									}
								}
							}).start();
						}
					} else if (conn.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
						log.println("Capabilities are already up to date");
					} else {
						log.aprintln("Capabilities update failed with response code: " + conn.getResponseCode());
					}
				} catch (Exception e) {
					log.aprintln("Unable to load from network" + (diskCache ? "; trying locally cached capabilities" : ""));
				}
			}
			
			// try the disk if we didn't load capabilities from network
			if (newCaps == null) {
				try {
					newCaps = new WMSCapabilities(new FileInputStream(file));
				} catch (Exception e) {
					throw new IllegalStateException("Unable to get capabilities for server named " +
						getName() + " from network or disk; check network connection and try again.", e);
				}
			}
			
			// integrate caps with this server
			processSources(oldLayers, newCaps.getLayers(), this);
			
			capabilities = newCaps;
		} catch (Exception e) {
			// log failure and attempt to revert to prior good state
			log.aprintln("Exception getting capabilities: "+ e.getMessage());
			log.aprintln(e);
			if (capabilities != null) {
				log.aprintln("Attempting to restore previous state");
				try {
					processSources(capabilities.getLayers(), oldLayers, this);
				} catch (Exception e2) {
					throw new IllegalStateException("Unable to restore state after failed load: " + e.getMessage());
				}
			} else {
				throw new IllegalStateException("loadCapabilities failed, there are NO capabilities for this server");
			}
		}
	}
	
	/**
	 * Adds a new source from the given source
	 */
	public void add(MapSource source) {
		if (source instanceof WMSMapSource) {
			sources.add(source);
			notifyListeners(source, MapServerListener.Type.ADDED);
		} else {
			throw new IllegalStateException("Adding source to server that doesn't own it!");
		}
	}
	
	public void add(WMSLayer layer) {
		log.println("Adding layer [" + layer + "]");
		add(new WMSMapSource(layer.getName(), layer.getTitle(), layer.getAbstract(),
			layer.getCategories(), this, layer.isNumeric(), layer.getLatLonBoundingBox(), layer.getIgnoreValue()));
	}
	
	public void remove(String name) {
		log.println("Removing layer [name: " + name + "]");
		MapSource source = getSourceByName(name);
		if (source == null) {
			throw new IllegalStateException("Couldn't find source to remove with name " + name);
		}
		sources.remove(source);
		notifyListeners(source, MapServerListener.Type.REMOVED);
	}
	
	/** Negotiate differences between old and new layers onto server */
	private static final void processSources(List<WMSLayer> oldLayers, List<WMSLayer> newLayers, WMSMapServer server) {
		// process deletes
		Set<WMSLayer> deleted = new LinkedHashSet<WMSLayer>(oldLayers);
		deleted.removeAll(newLayers);
		for (WMSLayer del: deleted) {
			server.remove(del.getName());
		}
		
		// process inserts
		Set<WMSLayer> added = new LinkedHashSet<WMSLayer>(newLayers);
		added.removeAll(oldLayers);
		for (WMSLayer add: added) {
			server.add(add);
		}
		
		// process changes
		for (WMSLayer left: oldLayers) {
			for (WMSLayer right: newLayers) {
				if (left.equals(right)) {
					if (! left.reallyEquals(right)) {
						server.remove(left.getName());
						server.add(right);
					}
					continue;
				}
			}
		}
	}
	
	// MapServer serialization
	
	/** Loads this MapServer's jmars.config properties */
	public void load(String serverName) {
		name = serverName;
		title = Config.get(getAttrKey(name, "title"), null);
		try {
			uri = new URI(Config.getReplaced(getAttrKey(name, "url"), ""));
		} catch (URISyntaxException e) {
			log.println("Unable to load server named " + serverName + ": " + e.getMessage());
			throw new IllegalArgumentException("Server named " + serverName + " refers to invalid server", e);
		}
		userDefined = Config.get(getAttrKey(name, "custom"), false);
		maxRequests = Config.get(getAttrKey(name, "maxRequests"), 0);
		timeout = Config.get(getAttrKey(name, "timeout"), 0);
		if (maxRequests <= 0 || timeout <= 0) {
			throw new IllegalArgumentException("Mapserver not properly saved: " + serverName);
		}
	}
	
	/** Saves this MapServer's jmars.config properties */
	public void save() {
		if (! userDefined) {
			throw new IllegalStateException("Only custom MapServer objects can be saved");
		}
		Config.set(getAttrKey(name, "title"), getTitle());
		Config.set(getAttrKey(name, "url"), uri.toString());
		Config.set(getAttrKey(name, "custom"), true);
		Config.set(getAttrKey(name, "maxRequests"), ""+maxRequests);
		Config.set(getAttrKey(name, "timeout"), ""+timeout);
	}
	
	/** Removes this MapServer's jmars.config properties */
	public void delete() {
		if (! userDefined) {
			throw new IllegalStateException("Only custom MapServer objects can be deleted");
		}
		Config.set(getAttrKey(name, "title"), (String)null);
		Config.set(getAttrKey(name, "url"), (String)null);
		Config.set(getAttrKey(name, "custom"), (String)null);
		Config.set(getAttrKey(name, "maxRequests"), (String)null);
		Config.set(getAttrKey(name, "timeout"), (String)null);
		Config.set(prefix + "." + name, (String)null);
	}
	
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
	}
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		listeners = new LinkedList<MapServerListener>();
		sources = new LinkedList<MapSource>();
	}
}

