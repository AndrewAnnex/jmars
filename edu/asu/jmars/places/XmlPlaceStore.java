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


package edu.asu.jmars.places;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

import com.thoughtworks.xstream.XStream;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

/**
 * Care should be taken not to open more than one XmlPlaceStore, since each one
 * takes control of the data store.
 */
public class XmlPlaceStore extends AbstractSet<Place> implements PlaceStore {
	public static final Comparator<Place> defaultNameComparator = new Comparator<Place>() {
		public int compare(Place p1, Place p2) {
			return p1.getName().compareToIgnoreCase(p2.getName());
		}
	};
	private static final String PLACE_EXT = ".jpf";
	private static final File placesRoot = new File(Main.getJMarsPath() + "places");
	/** Returns the file for a given place */
	private static File getFile(Place place) {
		if (place.getName().contains(File.separator)) {
			throw new IllegalArgumentException("Name may not contain path separator '" + File.separatorChar + "'");
		}
		return new File(placesRoot.getAbsolutePath() + File.separatorChar + place.getName() + PLACE_EXT);
	}
	
	private DebugLog log = DebugLog.instance();
	private TreeSet<Place> places = new TreeSet<Place>(defaultNameComparator);
	
	public XmlPlaceStore() {
		super();
		load();
	}
	
	public void setComparator(Comparator<Place> sorter) {
		TreeSet<Place> newTree = new TreeSet<Place>(sorter);
		newTree.addAll(places);
		places = newTree;
	}
	
	public Comparator<? super Place> getComparator() {
		return places.comparator();
	}
	
	private void load() {
		if (placesRoot.exists()) {
			if (!placesRoot.isDirectory()) {
				throw new IllegalStateException(
					placesRoot.getAbsolutePath() +
					" exists, but is not a directory, please rename and try creating places again.");
			}
		} else {
			placesRoot.mkdirs();
		}
		places.clear();
		XStream xstream = new XStream() {
			protected boolean useXStream11XmlFriendlyMapper() {
				return true;
			}
		};
		for (File placeFile: placesRoot.listFiles()) {
			if (placeFile.isFile() && placeFile.canRead() && placeFile.getName().endsWith(PLACE_EXT)) {
				try {
					places.add((Place)xstream.fromXML(new FileInputStream(placeFile)));
				} catch (Exception e) {
					log.aprintln("Error reading place: " + placeFile);
					e.printStackTrace();
				}
			}
		}
	}
	
	public Iterator<Place> iterator() {
		return new XmlPlaceIterator(places.iterator());
	}
	
	public boolean add(Place place) {
		// remove the place if it already exists
		boolean has = places.contains(place);
		if (has) {
			try {
				removeFile(place);
				places.remove(place);
			} catch (RuntimeException e) {
				log.aprintln("Unable to update place " + place);
				throw e;
			}
		}
		// try to create the file, to make sure the name is acceptable
		File placeFile = getFile(place);
		try {
			if (!placeFile.createNewFile()) {
				throw new IllegalStateException("Couldn't create file for place " + place + ", bad filename?");
			}
		} catch (IOException e) {
			throw new IllegalStateException("Error saving place " + place, e);
		}
		// add the place
		XStream xstream = new XStream() {
			protected boolean useXStream11XmlFriendlyMapper() {
				return true;
			}
		};
		try {
			FileOutputStream fos = new FileOutputStream(placeFile);
			xstream.toXML(place, fos);
			places.add(place);
			fos.close();
		} catch (Exception e) {
			throw new IllegalStateException("Error saving place " + place, e);
		}
		return has;
	}
	
	public int size() {
		return places.size();
	}
	
	/**
	 * The remove method ALSO removes the associated file from disk. If anything
	 * goes wrong, in-memory changes are discarded and this store is reloaded
	 * from disk.
	 */
	private void removeFile(Place place) {
		try {
			File placeFile = getFile(place);
			if (!placeFile.delete()) {
				throw new IllegalStateException("Unable to delete " + placeFile);
			}
		} catch (RuntimeException e) {
			log.aprintln("Failure removing place, reloading from disk");
			load();
			throw e;
		}
	}

	/**
	 * Wraps a given place iterator, but defers the remove method to
	 * {@link XmlPlaceStore#removeFile(Place)}.
	 */
	private class XmlPlaceIterator implements Iterator<Place> {
		private final Iterator<Place> it;
		private Place last;
		public XmlPlaceIterator(Iterator<Place> it) {
			this.it = it;
		}
		public boolean hasNext() {
			return it.hasNext();
		}
		public Place next() {
			return last = it.next();
		}
		public void remove() {
			removeFile(last);
			it.remove();
			last = null;
		}
	}
}
