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

import java.awt.geom.Point2D;

/** Captures the nudge offset for a named source in a named layer. */
public class MapOffset {
	/** The name of the server as defined by {@link edu.asu.jmars.layer.map2.MapServer#getName()}. */
	public final String serverName;
	/** The name of the source in its server as defined by {@link edu.asu.jmars.layer.map2.MapSource#getName(). */
	public final String mapName;
	/** The map offset in world coordinates, must be paired with a projection to be meaningful */
	public final Point2D worldDelta;
	public MapOffset(String serverName, String mapName, Point2D worldDelta) {
		this.serverName = serverName;
		this.mapName = mapName;
		this.worldDelta = worldDelta;
	}
}
