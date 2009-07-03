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

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.text.MessageFormat;

import edu.asu.jmars.ProjObj;
import edu.asu.jmars.util.DebugLog;

/**
 * Holds the current data for one Pipeline of a MapChannel request. In this
 * case the worldExtent is the unwrapped world extent.
 * 
 * The source, worldExtent, ppd, and projection properties are immutable.
 * 
 * Other portions of the code rely on this immutability, so this MUST not change.
 */
public class MapRequest {
	private static final DebugLog log = DebugLog.instance();
	private final MapSource source;
	private final Rectangle2D worldExtent;
	private final int ppd;
	private final ProjObj projection;
	private boolean cancelled = false;
	
	public MapRequest(MapSource newSource, Rectangle2D newExtent, int newScale, ProjObj newProjection) {
		if (newSource == null) {
			throw new IllegalArgumentException("Map source is null");
		}
		// round the world extent to the nearest pixel at the given ppd (minimum
		// rectangle width/height is 1 so we'll have data to fill the request
		// with)
		int x1 = (int)Math.floor(newExtent.getMinX() * newScale);
		int y1 = (int)Math.floor(newExtent.getMinY() * newScale);
		int x2 = (int)Math.ceil(newExtent.getMaxX() * newScale);
		int y2 = (int)Math.ceil(newExtent.getMaxY() * newScale);
		worldExtent = new RORectangle(
			(double)x1/newScale, (double)y1/newScale,
			(double)(x2-x1)/newScale, (double)(y2-y1)/newScale);
		if (!worldExtent.contains(newExtent)) {
			log.aprintln("worldExtent does not entirely contain newExtent");
		}
		source = newSource;
		ppd = newScale;
		projection = newProjection;
	}
	
	public MapSource getSource() {
		return source;
	}
	
	private static final class RORectangle extends Rectangle2D.Double {
		public void setRect(Rectangle2D r) {
			throw new IllegalStateException("Read only");
		}
		public RORectangle(double x, double y, double width, double height) {
		    this.x = x;
		    this.y = y;
		    this.width = width;
		    this.height = height;
		}
		public void setRect(double x, double y, double w, double h) {
			throw new IllegalStateException("Read only");
		}
		public String toString() {
			return MessageFormat.format(
				"RORectangle [{0,number,#.#####},{1,number,#.#####}]-[{2,number,#.#####},{3,number,#.#####}]",
				getMinX(), getMinY(), getMaxX(), getMaxY());
		}
	}
	
	public Rectangle2D getExtent() {
		return worldExtent;
	}
	
	public int getPPD() {
		return ppd;
	}
	
	public ProjObj getProjection() {
		return projection;
	}
	
	// This is called by MapProcessor when the view or other part of the channel changes, making this 
	// Data request no longer necessary;
	public void cancelRequest() {
		cancelled=true;
	}
	
	public boolean isCancelled() {
		return cancelled;
	}
	
	public int hashCode() {
		return source.hashCode() * 31*31 +
			worldExtent.hashCode() * 31 +
			ppd * 217 +
			projection.hashCode();
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof MapRequest)) {
			return false;
		} else {
			MapRequest mr = (MapRequest)o;
			return mr.source.equals(source) && mr.worldExtent.equals(worldExtent) && mr.projection.equals(projection) && mr.ppd == ppd;
		}
	}
}
