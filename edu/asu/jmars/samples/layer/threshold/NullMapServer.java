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


package edu.asu.jmars.samples.layer.threshold;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.asu.jmars.layer.map2.MapServer;
import edu.asu.jmars.layer.map2.MapServerListener;
import edu.asu.jmars.layer.map2.MapSource;

class NullMapServer implements MapServer {
	List<MapServerListener> listeners = new ArrayList<MapServerListener>();
	Map<String, MapSource> srcs = new LinkedHashMap<String, MapSource>();

	public List<MapSource> getMapSources() {
		return new ArrayList<MapSource>(srcs.values());
	}

	public URI getMapURI() {
		return null;
	}

	public int getMaxRequests() {
		return 1;
	}

	public String getName() {
		return "Null";
	}

	public MapSource getSourceByName(String name) {
		return srcs.get(name);
	}

	public int getTimeout() {
		return 3000;
	}

	public String getTitle() {
		return "Null MapServer";
	}

	public URI getURI() {
		return null;
	}

	public boolean isUserDefined() {
		return true;
	}

	public void loadCapabilities(boolean cached) {}

	public void add(MapSource source) {
		srcs.put(source.getName(), source);
		fireMapSourceChanged(source, MapServerListener.Type.ADDED);
	}

	public void remove(String name) {
		MapSource removed = srcs.remove(name);
		fireMapSourceChanged(removed, MapServerListener.Type.REMOVED);
	}

	public void addListener(MapServerListener l) {
		listeners.add(l);
	}

	public void removeListener(MapServerListener l) {
		listeners.remove(l);
	}
	
	public void fireMapSourceChanged(MapSource source, MapServerListener.Type changeType){
		for(MapServerListener l: new ArrayList<MapServerListener>(listeners)){
			l.mapChanged(source, changeType);
		}
	}

	public void load(String serverName) {}
	public void save() {}
	public void delete() {}

}
