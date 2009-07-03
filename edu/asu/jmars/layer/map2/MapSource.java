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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Serializable;

import edu.asu.jmars.util.Config;

/**
 * A MapSource describes a map that can be retrieved from a remote map server.
 * 
 * A MapSource's MapAttr property can be initialized with default values, and change
 * once when the map server responds with a sample of the map to inspect. This is only
 * done as a fallback when we have no foreknowledge of the type of data. The result is
 * that users of this MapSource who care about the type of data should register
 * listeners on the MapChannel in the MapAttr.
 * 
 * If multiple instances of a MapSource could be produced for the same map during a
 * session, then the {@link #equals(Object)} and {@link #hashCode()} methods must be
 * implemented appropriately.
 */
public interface MapSource extends Serializable {
	/** Name of the default map in the default map server */
	public static final String DEFAULT_NAME = Config.get("map.default.source");
	
	/**
	 * Returns a canonic name for this {@link MapSource} within the scope of 
	 * its {@link MapServer}. In other words, this is the unique identifier
	 * by which the {@link MapServer} knows this {@link MapSource}.
	 * This value must NOT be null.
	 */
	public String getName();
	
	/**
	 * Human readable title of the {@link MapSource}. If unavailable it is advisable to
	 * return the value returned by {@link #getName()}.
	 */
	public String getTitle();
	
	/**
	 * Human readable abstract associated with the data available through this {@link MapSource}.
	 * @return Abstract text, may be <code>null</code>.
	 */
	public String getAbstract();
	
	/**
	 * Returns an array of structural categories to which this {@link MapSource}
	 * belongs. Each outer array entry is one category. Each inner array entry
	 * is the series of levels from root to parent for this map source.
	 * 
	 * For example:
	 * <pre>
	 * { &quot;MARS&quot;, &quot;TES&quot;, &quot;Mineral Maps&quot; }
	 * </pre>
	 * 
	 * puts this {@link MapSource} into the Mineral Maps sub-sub-category of TES
	 * sub-category of MARS top-level category. These categories are used mainly
	 * in organizing maps in the UI.
	 * 
	 * @return Non-null array of arrays of category names in increasing order of
	 *         refinement.
	 */
	public String[][] getCategories();
	
	/**
	 * MapServer this {@link MapSource} is attached to.
	 */
	public MapServer getServer();
	
	/**
	 * Returns the {@link MapAttr}, or <code>null</code> if it has not been resolved yet. In that
	 * case, callers should call {@link #getMapAttr(MapAttrReceiver)}.
	 */
	public MapAttr getMapAttr();
	
	/**
	 * Returns the {@link MapAttr} asynchronously by passing it to the given callback
	 * some time later on the AWT thread.
	 */
	public void getMapAttr(final MapAttrReceiver receiver);
	
	/**
	 * The numeric keyword will cause a map to be requested in vicar format,
	 * otherwise in png
	 * 
	 * TODO: this usage of 'numeric' is GROSS, wrong, and may possibly be used
	 * by other servers to mean something else! Get rid of it and deduce map
	 * request types using the original design (using the strongest format
	 * supported both by the server and client.)
	 */
	public boolean hasNumericKeyword();
	
	/**
	 * @return The east leading longitude / ocentric latitude bounds of this map
	 *         source. The x axis should be in the half open range [0,540).
	 */
	public Rectangle2D getLatLonBoundingBox();
	
	/**
	 * Returns the MIME-type that the data for this MapSource should be requested in.
	 * For example, "image/png" for graphical images, "image/vicar" for numeric data.
	 * The call should NOT return <code>null</code>.
	 */
	public String getMimeType();
	
	/**
	 * Returns an array of ignore values, one for each band. The value will be
	 * NaN for each band that has no ignore value.
	 */
	public double[] getIgnoreValue();
	
	/**
	 * Returns the maximum PPD scale at which this map source can be produced,
	 * or {@link Double#POSITIVE_INFINITY}.
	 */
	public double getMaxPPD();
	
	/**
	 * Fetches image data for the specified {@link MapTileRequest}.
	 * Implementations should abort if {@link Thread#interrupt()} is called
	 * on the invoking thread.
	 * @param mapTileRequest The request parameters
	 * @return Returns the image for the given request from this source.
	 * @throws RetryableException if a transient error occurred, such as a
	 * connection failure.
	 * @throws NonRetryableException if a permanent error occurred, such as
	 * this layer not found on the server.
	 */
	public BufferedImage fetchTile(MapRequest mapTileRequest) throws RetryableException, NonRetryableException;
	
	/**
	 * Returns true if this map is not known to be well mosaiced already, and
	 * false if the user shouldn't be able to bump the map around.
	 */
	public boolean isMovable();
	
	/** @return The nudging offset in east-leading lon / ocentric latitude */
	public Point2D getOffset();
	
	/** @param offset The new nudging offset in east-leading lon / ocentric latitude */
	public void setOffset(Point2D offset);
}

