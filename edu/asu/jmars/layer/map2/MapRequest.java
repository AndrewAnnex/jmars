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

import java.awt.geom.Rectangle2D;

import edu.asu.jmars.ProjObj;

/**
 * Holds the current data for one Pipeline of a MapChannel request. In this
 * case the worldExtent is the unwrapped world extent.
 * 
 * The source, worldExtent, ppd, and projection properties are immutable.
 * 
 * Other portions of the code rely on this immutability, so this MUST not change.
 */
public final class MapRequest {
	private MapSource source;
	private Rectangle2D worldExtent;
	private int ppd;
	private ProjObj projection;
	
	public MapRequest(MapSource newSource, Rectangle2D newExtent, int newScale, ProjObj newProjection) {
		source = newSource;
		if (source==null) {
			throw new IllegalArgumentException("Map source is null");
		}
		worldExtent = newExtent;
		ppd = newScale;
		projection = newProjection;
	}
	
	public MapSource getSource() {
		return source;
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
	
	private boolean cancelled = false;
	
	// This is called by MapProcessor when the view or other part of the channel changes, making this 
	// Data request no longer necessary;
	public void cancelRequest() {
		cancelled=true;
	}
	
	public boolean isCancelled() {
		return cancelled;
	}
}
