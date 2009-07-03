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


package edu.asu.jmars.layer.util.features;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import edu.asu.jmars.util.Util;

/**
 * An in-memory spatial index over a given FeatureCollection.  Queries may return
 * records that are not strictly within the requested area, so for exact results,
 * a subsequent overlap test should be performed.
 * 
 * This index is threadsafe. Any number of queries may run separately, but
 * modification will block until all queries finish, and then block subsequent
 * queries until the changes are finished.
 * 
 * This index adds itself as a listener to the given feature collection and keeps
 * itself up to date with changes.  This does prevent querying while FeatureEvent
 * objects are being dispatched, and disconnect() should be called when this index
 * is no longer in use so it may be garbage collection.
 */
public final class MemoryFeatureIndex implements FeatureIndex, FeatureListener {
	public static final Iterator<Feature> emptyIter = new ArrayList<Feature>(0).iterator();
	private final FeatureCollection fc;
	private final Set<Feature> features = new LinkedHashSet<Feature>();
	
	public MemoryFeatureIndex(FeatureCollection fc) {
		this.fc = fc;
		fc.addListener(this);
	}
	
	public void disconnect() {
		fc.removeListener(this);
	}
	
	private volatile int numReaders = 0;
	private volatile int numWriters = 0;
	
	/** Blocks until there are no write operations, then starts a read operation */
	private synchronized void startReader() throws InterruptedException {
		while (numWriters > 0) {
			wait();
		}
		numReaders ++;
	}
	/** Stops a read operation and notifies all waiting operations */
	private synchronized void stopReader() {
		numReaders --;
		notifyAll();
	}
	/** Blocks until there are no write or read operations, then starts a write operation */
	private synchronized void startWriter() throws InterruptedException {
		while (numReaders > 0 || numWriters > 0) {
			wait();
		}
		numWriters ++;
	}
	/** Stops a write operation and notifies all waiting operations */
	private synchronized void stopWriter() {
		numWriters --;
		notifyAll();
	}
	
	/**
	 * Performs a brute force search through the feature collection looking for
	 * records that overlap the query in world coordinates, and returns an
	 * iterator over a collection of the results.
	 */
	public Iterator<Feature> queryUnwrappedWorld(Rectangle2D rect) {
		try {
			startReader();
			Rectangle2D[] rects = Util.toWrappedWorld(rect);
			List<Feature> matches = new ArrayList<Feature>();
			for (Feature f: features) {
				for (Rectangle2D r: rects) {
					// TODO: cache bounds on FPath instead of using GeneralPath's, which is computed every time
					Rectangle2D bound = f.getPath().getWorld().getGeneralPath().getBounds2D();
					if (overlap(r, bound)) {
						matches.add(f);
						break;
					}
				}
			}
			return matches.iterator();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return emptyIter;
		} finally {
			stopReader();
		}
	}
	
	/** Returns true if these unwrapped world coordinate rectangles overlap modulo 360 */
	private static boolean overlap(Rectangle2D a, Rectangle2D b) {
		return a.getMinX() < b.getMaxX() && a.getMaxX() > b.getMinX() &&
			a.getMinY() < b.getMaxY() && a.getMaxY() > b.getMinY();
	}
	
	public void receive(final FeatureEvent e) {
		Runnable task = null;
		switch (e.type) {
		case FeatureEvent.ADD_FEATURE:
			task = new Runnable() {
				public void run() {
					features.addAll(e.features);
				}
			};
			break;
		case FeatureEvent.REMOVE_FEATURE:
			task = new Runnable() {
				public void run() {
					for (Feature f: (List<Feature>)e.features) {
						features.remove(f);
					}
				}
			};
			break;
		case FeatureEvent.CHANGE_FEATURE:
			if (e.fields.contains(Field.FIELD_PATH)) {
				task = new Runnable() {
					public void run() {
						for (Feature f: (Collection<Feature>)e.valuesBefore.values()) {
							features.remove(f);
						}
						features.addAll(e.valuesBefore.keySet());
					}
				};
			}
			break;
		}
		if (task != null) {
			// TODO: need to put this into a serial execution threadpool
			// and make queries block unless the queue is empty; that
			// will allow a fast return from this method without the
			// risk of drawing the wrong features or features in the wrong
			// state.
			try {
				startWriter();
				task.run();
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			} finally {
				stopWriter();
			}
		}
	}
}

/*
// TODO: start on dot product index
// Returns [mindots, maxdots] where mindots is the minimum dot product
// between each axis and this feature, and maxdots is the maximum dot
// product between this axis and the feature.
//
private static final int[][] getIndexValues(Feature f) {
	float[] coords = f.getPath().getSpatialWest().getCoords(false);
	HVector temp = new HVector();
	int[] minDots = new int[axes.length];
	int[] maxDots = new int[axes.length];
	for (int i = 0; i < coords.length; i+=2) {
		temp.fromLonLat(coords[i], coords[i+1]);
		for (int j = 0; j < axes.length; j++) {
			double dot = axes[j].separation(temp);
			minDots[j] = Math.min(minDots[j], (int)Math.floor(dot));
			maxDots[j] = Math.max(maxDots[j], (int)Math.ceil(dot));
		}
	}
	return new int[][]{minDots,maxDots};
}

private static final HVector[] axes = {
	HVector.X_AXIS,
	HVector.Y_AXIS,
	HVector.Z_AXIS
};
*/