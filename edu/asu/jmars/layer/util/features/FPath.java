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

import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.ReferenceMap;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.ProjectionEvent;
import edu.asu.jmars.layer.ProjectionListener;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.SPolygon;
import edu.asu.jmars.util.Util;

/**
 * Defines an immutable path of vertices and some convenience methods to convert
 * between the world and spatial east/west coordinate systems. Several common
 * computations are also provided.
 */
public final class FPath {
	// NOTE! To disable caching, just set this to false
	private static final boolean caching = true;
	
	/** This Feature represents an undefined shape. */
	public static final int TYPE_NONE = 0;
	/** This Feature represents a single point. */
	public static final int TYPE_POINT = 1;
	/** This Feature represents an unclosed polyline with 2 or more points. */
	public static final int TYPE_POLYLINE = 2;
	/** This Feature represents a closed polygon with 3 or more points. */
	public static final int TYPE_POLYGON = 3;
	
	public static final int WORLD = 0;
	public static final int SPATIAL_EAST = 1;
	public static final int SPATIAL_WEST = 2;
	private static Map cache;

	static {
		if (caching) {
			cache = newCache();
			Main.addProjectionListener(new ProjectionListener () {
				public void projectionChanged (ProjectionEvent e) {
					cache = newCache();
				}
			});
		}
	}

	static Map newCache () {
		return Collections.synchronizedMap(new ReferenceMap (ReferenceMap.WEAK, ReferenceMap.SOFT));
	}
	
	private static final NumberFormat nf = new DecimalFormat("0.###");

	private final int coordSystem;
	private final Point2D[] vertices;
	private final GeneralPath gp;
	private final boolean closed;

	/**
	 * Creates a new Path from the given point array, where each point
	 * is stored as
	 * lat lon lat lon ... (if latFirst is true, or)
	 * lon lat lon lat ... (if latFirst is false)
	 * @param coordSystem One of WORLD, SPATIAL_EAST, or SPATIAL_WEST
	 * @param closed If true, this represents a closed polygon
	 */
	public FPath (float[] coords, boolean latFirst, int coordSystem, boolean closed) {
		this.vertices = coordsToVertices(coords, latFirst);
		this.gp = verticesToGp(vertices, closed);
		this.closed = closed;
		this.coordSystem = coordSystem;
	}

	/**
	 * Creates a new FPath with the given vertices.
	 * @param vertices These Point2D values are copied into the internal
	 * immutable Point2D class.
	 * @param coordSystem One of WORLD, SPATIAL_EAST, or SPATIAL_WEST
	 * @param closed If true, this represents a closed polygon
	 */
	public FPath (Point2D[] vertices, int coordSystem, boolean closed) {
		this.vertices = createVertices(vertices);
		this.gp = verticesToGp (vertices, closed);
		this.coordSystem = coordSystem;
		this.closed = closed;
	}

	// TODO: copy 'points' into new instances of Point2D subclass that is itself
	// immutable, do the same thing with GeneralPath (or pull the class type up
	// to Polygon or some similar immutable) and _stop the cloning_ in
	// getVertices() and getGeneralPath().
	private static final Point2D[] createVertices (Point2D[] vertices) {
		Point2D[] copy = new Point2D[vertices.length];
		for (int i = 0; i < copy.length; i++)
			copy[i] = (Point2D)vertices[i].clone();
		return copy;
	}

	/**
	 * Creates a new FPath from the given GeneralPath, which must contain
	 * a single connected polygon, line, or point. If it does not, the
	 * resulting FPath will be empty.
	 */
	public FPath (GeneralPath path, int coordSystem) {
		Boolean[] closed = new Boolean[1];
		Point2D[] points = gpToVertices(path, closed);
		if (points == null) {
			this.vertices = new Point2D[0];
			this.gp = new GeneralPath();
			this.closed = false;
		} else {
			this.gp = (GeneralPath)path.clone();
			this.vertices = points;
			this.closed = closed[0].booleanValue();
		}
		this.coordSystem = coordSystem;
	}

	/**
	 * @return WORLD, SPATIAL_WEST, or SPATIAL_EAST.
	 */
	public int getCoordSystem () {
		return coordSystem;
	}

	/**
	 * If true, the polygon is a closed figure. If false, the polygonal points
	 * represent a series of lines, but not a closed polygon.
	 */
	public boolean getClosed () {
		return closed;
	}

	/**
	 * Returns a copy of the vertices of this path.
	 */
	public Point2D[] getVertices () {
		Point2D[] result = new Point2D[vertices.length];
		for (int i = 0; i < vertices.length; i++)
			result[i] = (Point2D)vertices[i].clone();
		return result;
	}

	/**
	 * Returns a copy of the GeneralPath for this path.
	 */
	public GeneralPath getGeneralPath () {
		return (GeneralPath) gp.clone();
	}

	/**
	 * Returns the lat/lon coordinates as a series of doubles, like
	 * lat lon lat lon ... (if latFirst is true, or)
	 * lon lat lon lat ... (if latFirst is false)
	 */
	public float[] getCoords(boolean latFirst) {
		float[] latLons = new float[vertices.length * 2];

		int x = (latFirst ? 1 : 0);
		int y = (latFirst ? 0 : 1);

		for (int i = 0; i < vertices.length*2; i += 2) {
			latLons[i + x] = (float) vertices[i/2].getX();
			latLons[i + y] = (float) vertices[i/2].getY();
		}

		return latLons;
	}
	
	/**
	 * Returns the current type of the Feature's path attribute.
	 * The GeneralPath is examined each time this method is called, so the
	 * result should be retained externally if it is needed in many places.
	 * @return One of the public field values:
	 * <ul>
	 * <li>TYPE_NONE
	 * <li>TYPE_POINT
	 * <li>TYPE_POLYLINE
	 * <li>TYPE_POLYGON
	 * </ul>
	 */
	public int getType () {
		if (vertices.length < 1)
			return TYPE_NONE;
		else if (vertices.length < 2)
			return TYPE_POINT;
		else if (closed)
			return TYPE_POLYGON;
		else
			return TYPE_POLYLINE;
	}
	
	/**
	 * Returns the vector average for spatial paths, or the positional average
	 * for world paths.
	 */
	public Point2D getCenter () {
		switch (coordSystem) {
		case SPATIAL_EAST:
		case SPATIAL_WEST:
			// vector average
			double lon, lat;
			HVector sum = new HVector (0,0,0);
			for (int i = 0; i < vertices.length; i++) {
				lon = coordSystem==SPATIAL_EAST ? -vertices[i].getX() : vertices[i].getX();
				lat = vertices[i].getY();
				sum = sum.add(new HVector (lon, lat));
			}
			lon = coordSystem==SPATIAL_EAST ? FeatureUtil.lonNorm(-sum.lon()) : sum.lon();
			lat = sum.lat();
			return new Point2D.Double (lon, lat);
		case WORLD:
			// positional average
			Point2D[] v = Util.normalize360(vertices);
			Point2D.Double c = new Point2D.Double();
			for (int i = 0; i < v.length; i++) {
				c.x += v[i].getX();
				c.y += v[i].getY();
			}
			if (v.length > 1) {
				c.x /= v.length;
				c.y /= v.length;
			}
			return c;
		default:
			return null;
		}
	}

	/**
	 * Returns the spherical area in square kilometers. Currently returns 0.0
	 * for world-coordinate polygons, and open spatial polygons.
	 */
	public double getArea () {
		switch (coordSystem) {
		case SPATIAL_EAST:
		case SPATIAL_WEST:
			return closed ? Util.sphericalArea(vertices) * Util.MARS_MEAN * Util.MARS_MEAN : 0.0;
		case WORLD:
			// TODO
		default:
			return 0.0;
		}
	}

	/**
	 * TODO: Test this!
	 * 
	 * Returns true if this FPath encloses the given point. The given point must
	 * be in the same coordinate system as this path.
	 */
	public boolean contains (Point2D p) {
		switch (coordSystem) {
		case WORLD:
			return gp.contains(p);
		case SPATIAL_EAST:
			return new SPolygon(gp).contains(new HVector(-p.getX(),p.getY()));
		case SPATIAL_WEST:
			return new SPolygon(gp).contains(new HVector(p.getX(),p.getY()));
		default:
			return false;
		}
	}

	/**
	 * TODO: Test this!
	 * 
	 * We use the same routines in the graphicswrapped and in the GUI code. If
	 * we dont, what we see can be different from how the mouse controller
	 * reacts. That's a Bad Thing. So, in for a penny, in for a pound.
	 * 
	 * Returns true if the given rect intersects this path. The rect must be in
	 * the same coordinate system as this path. A single point path is given an
	 * area of a millionth of a unit square in world coordinates, since the Util
	 * intersection methods compare the shape to the rect and not the other way
	 * around.
	 */
	public boolean intersects(Rectangle2D rect)
	{
		if (vertices.length < 1)
			return false;
		switch (coordSystem) {
		case WORLD:
			Shape[] shapes;
			if (vertices.length == 1) {
				double x = vertices[0].getX();
				double y = vertices[0].getY();
				shapes = new Shape[] {Util.normalize360(new Rectangle2D.Double(x,y,0.001,0.001))};
			} else if (!closed) {
				shapes = new Line2D[vertices.length-1];
				for (int i = 1; i < vertices.length; i++)
					shapes[i-1] = Util.normalize360(new Line2D.Double (vertices[i-1], vertices[i]));
			} else {
				shapes = new Shape[] {Util.normalize360(gp)};
			}
			return Util.intersects360(rect, shapes).length > 0;
		case SPATIAL_WEST:
		case SPATIAL_EAST:
			return SPolygon.area(new SPolygon(gp), new SPolygon(rect)) > 0.0;
		default:
			return false;
		}
	}

	/**
	 * Translates this path by the given delta. This just calls translate(double,double).
	 */
	public FPath translate (Point2D delta) {
		return translate (delta.getX(), delta.getY());
	}

	/**
	 * Translates this path by the given xy delta. The returned path is a completely new
	 * immutable path based on this path and shifted accordingly.
	 */
	// TODO: methods like this already done on Polygon, maybe replace vertices and gp with
	// a single polygon instance. In fact, FPath can _be_ a polygon instance.
	public FPath translate (double x, double y) {
		Point2D[] newVerts = new Point2D[vertices.length];
		for (int i = 0; i < newVerts.length; i++) {
			newVerts[i] = new Point2D.Double(
				FeatureUtil.lonNorm(vertices[i].getX() + x),
				vertices[i].getY() + y);
		}
		return new FPath (newVerts, coordSystem, closed);
	}

	/**
	 * Convenience method that calls convertTo(SPATIAL_WEST)
	 */
	public FPath getSpatialWest () {
		return convertTo (SPATIAL_WEST);
	}

	/**
	 * Convenience method that calls convertTo(SPATIAL_EAST)
	 */
	public FPath getSpatialEast () {
		return convertTo (SPATIAL_EAST);
	}

	/**
	 * Convenience method that calls convertTo(WORLD)
	 */
	public FPath getWorld () {
		return convertTo (WORLD);
	}

	/**
	 * Returns this FPath in the requested coordinate system.
	 */
	public FPath convertTo(int coordSystem) {
		return (caching ? convertTo_cache (coordSystem) : convertTo_impl(coordSystem));
	}

	// This operation is inverted by the public method, getCoords()
	private static final Point2D[] coordsToVertices (float[] coords, boolean latFirst) {
		Point2D[] points = new Point2D[coords.length/2];
		int x = (latFirst ? 1 : 0);
		int y = (latFirst ? 0 : 1);
		for (int i = 0; i < coords.length/2; i++)
			points[i] = new Point2D.Double (coords[i*2 + x], coords[i*2 + y]);
		return points;
	}

	// Using array of Boolean as a second return value; ewww
	// Didn't this type of operation bother anyone? I mean, this is proof that we
	// don't support GeneralPath, and just sample down to it.
	// TODO: use Polygon instead, if we can.
	private static final Point2D[] gpToVertices (GeneralPath path, Boolean[] closed) {
		closed[0] = Boolean.FALSE;
		boolean error = false;
		List points = new ArrayList();
		for (PathIterator it=path.getPathIterator(null); !it.isDone(); it.next()) {
			double[] coords = new double[6];
			switch (it.currentSegment(coords)) {
			case PathIterator.SEG_MOVETO:
				if (points.size() == 0)
					points.add (new Point2D.Double(coords[0], coords[1]));
				else
					error = true;
				break;
			case PathIterator.SEG_LINETO:
				if (! closed[0].booleanValue())
					points.add(new Point2D.Double(coords[0], coords[1]));
				else
					error = true;
				break;
			case PathIterator.SEG_CLOSE:
				closed[0] = Boolean.TRUE;
				break;
			default:
				error = true;
			}
			if (error)
				return null;
		}
		return (Point2D[])points.toArray(new Point2D[0]);
	}

	private static final GeneralPath verticesToGp (Point2D[] points, boolean closed) {
		GeneralPath gp = new GeneralPath ();
		if (points.length > 0) {
			gp.moveTo((float)points[0].getX(), (float)points[0].getY());
			for (int i = 1; i < points.length; i++)
				gp.lineTo((float)points[i].getX(), (float)points[i].getY());
			if (closed)
				gp.closePath();
		}
		return gp;
	}

	/**
	 * Returns this Path converted into the given coordinate coordinate system.
	 * If this Path is already in the requested coordinate system, this
	 * operation simply returns 'this'.
	 * 
	 * @param coordSystem One of WORLD, SPATIAL_EAST, or SPATIAL_WEST
	 */
	private final FPath convertTo_impl (int coordSystem) {
		if (this.coordSystem == coordSystem)
			return this;
		Point2D[] rv = null; // returned vertices
		if (this.coordSystem == SPATIAL_EAST && coordSystem == SPATIAL_WEST)
			rv = toggleLonEastWest(vertices);
		else if (this.coordSystem == SPATIAL_EAST && coordSystem == WORLD)
			rv = spatialToWorld(vertices, true);
		else if (this.coordSystem == SPATIAL_WEST && coordSystem == SPATIAL_EAST)
			rv = toggleLonEastWest(vertices);
		else if (this.coordSystem == SPATIAL_WEST && coordSystem == WORLD)
			rv = spatialToWorld(vertices, false);
		else if (this.coordSystem == WORLD && coordSystem == SPATIAL_EAST)
			rv = worldToSpatial(vertices, true);
		else if (this.coordSystem == WORLD && coordSystem == SPATIAL_WEST)
			rv = worldToSpatial(vertices, false);
		return (rv == null ? null : new FPath (rv, coordSystem, closed));
	}

	/**
	 * Allows caching each FPath to the most-recently-requested coordinate system. For
	 * example if spatial east is repeatedly requested from spatial-west, the
	 * cache does a good job, but if two different coordinate systems are intermittently
	 * requested of the same FPath, the cache will actually degrade
	 * performance. In the case we're worrying about, such alternating coordinate system
	 * requests don't happen, and boy is this approach a small amount of code.
	 */
	private final FPath convertTo_cache (int coordSystem) {
		FPath to = (FPath)cache.get(this);
		if (to == null || to.getCoordSystem() != coordSystem)
			cache.put(this, to=convertTo_impl(coordSystem));
		return to;
	}

	private final Point2D[] toggleLonEastWest (Point2D[] points) {
		Point2D[] newPoints = new Point2D[points.length];
		for (int i = 0; i < points.length; i++)
			newPoints[i] = new Point2D.Double (FeatureUtil.lonNorm(-points[i].getX()), points[i].getY());
		return newPoints;
	}

	/** converts this path to world coordinates, where x values are in the range [0,540) and y values are in the range [-90,90] */
	private final Point2D[] spatialToWorld(Point2D[] points, boolean east) {
		Point2D[] newPoints = new Point2D[points.length];
		if (east)
			points = toggleLonEastWest (points);
		double lastX = 0;
		double minX = Double.POSITIVE_INFINITY;
		for (int i = 0; i < points.length; i++) {
			newPoints[i] = Main.PO.convSpatialToWorld(points[i]);
			double x = newPoints[i].getX();
			if (i > 0 && Math.abs(lastX - x) > 180) {
				x += Math.signum(lastX - x) * 360;
				minX = Math.min(minX, x);
				newPoints[i].setLocation(x, newPoints[i].getY());
			}
			lastX = x;
		}
		if (minX < 0) {
			minX = (int)Math.ceil(-minX/360)*360;
			for (Point2D p: newPoints) {
				p.setLocation(p.getX() + minX, p.getY());
			}
		}
		return newPoints;
	}

	private final Point2D[] worldToSpatial(Point2D[] points, boolean east) {
		Point2D[] newPoints = new Point2D[points.length];
		for (int i = 0; i < points.length; i++)
			newPoints[i] = Main.PO.convWorldToSpatial(points[i]);
		return east ? toggleLonEastWest (newPoints) : newPoints;
	}
	
	public String toString(){
		StringBuffer sbuf = new StringBuffer("FPath[");
		
		String coordSys = "UNKNOWN";
		switch(getCoordSystem()){
		case WORLD: coordSys = "world"; break;
		case SPATIAL_EAST: coordSys = "east"; break;
		case SPATIAL_WEST: coordSys = "west"; break;
		}
		sbuf.append("coordSys="+coordSys+";");
		
		sbuf.append("coords=");
		float[] coords = getCoords(false);
		for(int i=0; i<coords.length; i++){
			if (i > 0)
				sbuf.append(",");
			sbuf.append(nf.format(coords[i]));
		}
		sbuf.append("]");
		return sbuf.toString();
	}
}
