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

import java.io.Serializable;

import edu.asu.jmars.layer.util.features.FPath;

/**
 * Provides a serializable shape for the shape layer to put into and read from
 * sessions.
 * 
 * It currently converts to and from {@link FPath}, storing the closed flag and
 * the coordinates as an array of east-lon ocentric-lat float values.
 * 
 * This is a separate class precisely so allow switching the session path
 * storage to store other things for e.g. support of multi-part shapes.
 */
public final class ShapePath implements Serializable {
	private boolean closed;
	private float[] coords;
	public ShapePath(FPath path) {
		this.closed = path.getClosed();
		this.coords = path.getSpatialEast().getCoords(false);
	}
	public FPath getPath() {
		return new FPath(coords, false, FPath.SPATIAL_EAST, closed);
	}
}

