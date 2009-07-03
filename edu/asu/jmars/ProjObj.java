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


package edu.asu.jmars;

import edu.asu.jmars.graphics.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;


public abstract class ProjObj
 {
	private static final DebugLog log = DebugLog.instance();

	protected	final double	noelsNumber=352;
	protected	final double	noelsHorizontalNumber=360;
	protected	final double	bensNumber= 88;
	
	public abstract double getBucketWidth(double ppd);
	public abstract double getBucketHeight(double ppd);
	public abstract double getServerOffsetX();
	public abstract double getInitial_X();
	public abstract double getInitial_Y();
	public abstract double getXMin(double x);
	public abstract double getUnitWidth();
	public abstract double getUnitHeight();
	public abstract String getProjectType();
	public abstract String getProjectionSpecialParameters();
	public abstract double getCircumfrence();
	public abstract double getDelta(double ppd);
	public abstract double upperLimit();
	public abstract double lowerLimit();
	public abstract double getCenterLon();
	public abstract double getCenterLat();
	public abstract Point2D getProjectionCenter();

	public abstract Point2D convSpatialToWorld(Point2D orig);
	public abstract Point2D convWorldToSpatial(Point2D orig);
   public Point2D convWorldToSpatialFromProj(MultiProjection proj, Point2D orig) { return orig; }

	public Point2D convSpatialToWorld(double x, double y)
	 {
		return  convSpatialToWorld(new Point2D.Double(x,y));
	 }

	public Point2D convWorldToSpatial(double x, double y)
	 {
		return  convWorldToSpatial(new Point2D.Double(x,y));
	 }

	public static class Projection_OC extends ProjObj
	 {
		private final HVector up;
		private final double  upLon;
		private final double  upLat;
		private double  projCenterLon;
		private double  projCenterLat;
		private final HVector center;
		private double initialX;
		private double initialY;
		private static double ROUND = Config.get("projection.round", 0);

		/**
		 ** Constructs from an arbitrary "up direction", rounded
		 ** according to config file parameters.
		 **
		 ** @param up The desired screen-up direction for the
		 ** projection (the y-axis of world coordinates).
		 ** @param round Indicates whether or not to round the
		 ** vector. Rounding is performed to the nearest
		 ** longitude/latitude multiple of the config value for
		 ** "projection.round".
		 **/
		
		public double getUpLon() {
			return upLon;
		}
		
		public double getUpLat() {
			return upLat;
		}
		
		public Projection_OC(HVector up, boolean round)
		 {
         log.println("Incoming UP: "+up.lon()+" , "+up.lat());

			double lon = up.lon();
			double lat = up.lat();
			if(round  &&  ROUND > 0)
			 {
				log.println("ROUND = " + ROUND);
				log.println("got: lon = " + lon);
				log.println("     lat = " + lat);
				lon = Util.roundToMultiple(lon, ROUND);
				lat = Util.roundToMultiple(lat, ROUND);
				if(90-Math.abs(lat) < 0.001) {
					log.println("I'm setting lon to 0 now");
					lon = 0;
				}
				up = new HVector(lon, lat);
				log.println("now: lon = " + lon);
				log.println("     lat = " + lat);
			 }

			this.upLon = lon;
			this.upLat = lat;
			this.up = up.unit();
			this.center = upLat >= 0
				? new HVector(180 + upLon, 90 - upLat)
				: new HVector(      upLon, 90 + upLat);


                        log.println("Up lon/lat {"+this.up.lon()+" , "+this.up.lat()+"}");
                        log.println("Cen lon/lat {"+this.center.lon()+" , "+this.center.lat()+"}");
                        log.println("Up Vector: "+this.up);
                        log.println("Cen Vector: "+this.center);
      

		 }

		/**
		 ** Constructs from an arbitrary "up direction", rounded
		 ** according to config file parameters.
		 **
		 ** @param up The desired screen-up direction for the
		 ** projection (the y-axis of world coordinates).
		 **/
		public Projection_OC(HVector up)
		 {
			this(up, true);
		 }

		/**
		 ** Constructs from an arbitrary centerpoint of the
		 ** projection, oriented to north-up. All arguments are in
		 ** degrees.
		 **/
		public Projection_OC(double centerLon, double centerLat)
		 {
			this(centerLat >= 0
				 ? new HVector(180 + centerLon, 90 - centerLat)
				 : new HVector(      centerLon, 90 + centerLat));

			projCenterLon = centerLon; //This is Western leading coords!
			projCenterLat = centerLat;
//We need to round our values as well
			if(ROUND > 0) {
				projCenterLon = Util.roundToMultiple(projCenterLon, ROUND);
				projCenterLat = Util.roundToMultiple(projCenterLat, ROUND);
			}

			Point2D initialPt = convSpatialToWorld(centerLon, centerLat);
			initialX = initialPt.getX();
			initialY = initialPt.getY();
			log.println("initial = " + initialX + "\t" + initialY);
		 }

		/** Returns the projection center in east-lon ocentric lat */
		public Point2D getProjectionCenter()
		{
			// Return E-Leading longitude for now
			return(new Point2D.Double(((360.-projCenterLon % 360.) % 360.),projCenterLat));
		}

		public double getCenterLon()
		 {
			return  center.lon();
		 }

		public double getCenterLat()
		 {
			return  center.lat();
		 }

 		public Point2D convWorldToSpatialFromProj(MultiProjection proj,
												  Point2D orig)
		 {
			return  proj.world.toSpatial(orig);
		 }

		public double getBucketWidth(double ppd)
		 {
				return(noelsHorizontalNumber/ppd);
		 }

		public double getBucketHeight(double ppd)
		 {
			if (ppd >= 4.0)
				return(noelsNumber/ppd);
			else
				return(bensNumber);
		 }

		public double getServerOffsetX() { return  0.0; }
		public double getInitial_X()			{ return (initialX);	}
		public double getInitial_Y()			{ return (initialY);			}
		public double getXMin(double x)		{ return ( (x < 0 ? ((360-Math.abs(x)%360.)%360.) : x % 360.));			}
		public double getUnitWidth()			{ return (1.0);		}
		public double getUnitHeight()			{ return (1.0);			}

		public String getProjectType()		{ return ("OC");			}
		public double getCircumfrence()		{ return (360.0);			}
		public double getDelta(double ppd)				{ return (1.0/ppd);			}
		public double upperLimit() { return (bensNumber);}
		public double lowerLimit() { return -(bensNumber);}

		// Locate the center and the "upward" direction vectors
		public HVector getCenter()
		 {
			return  (HVector) center.clone();
		 }
		public HVector getUp()
		 {
			return  (HVector) up.clone();
		 }

		public String getProjectionSpecialParameters()
		 {
			HVector up = getUp();
			String pars =
				"&TRACK_centerLat=888&TRACK_centerLon=888" +
				"&TRACK_upLat=" + upLat +
				"&TRACK_upLon=" + upLon + "&TRACK_format=c";
			return  pars;
		 }

		/**
		 ** Takes a point in degrees left-right/up-down and outputs lat/long.
		 **/
		public Point2D convWorldToSpatial(Point2D orig)
		 {
			double x = Math.toRadians(orig.getX());
			double y = Math.toRadians(orig.getY());

			// Calculate the converted point's position as y degrees in
			// the center->up direction and x degrees about up
			HVector pt =
				getCenter().mul(Math.cos(y))
				.add(getUp().mul(Math.sin(y)))
				.rotate(getUp(), x);

			Point2D spat = new Point2D.Double(Math.toDegrees(lon_of(pt)) % 360,
											  Math.toDegrees(lat_of(pt)));
			return  spat;
		 }

		/**
		 * @param orig The point to convert; the x-axis is the west-leading
		 * longitude, the y-axis is the ocentric latitude.
		 * @return The point in world coordinates (this map projection's two-axis
		 * Euclidian coordinate system.)
		 */
		public Point2D convSpatialToWorld(Point2D orig)
		 {
			HVector pt = marsll2vector(Math.toRadians(orig.getX()),
									   Math.toRadians(orig.getY()));
			HVector up = getUp();
			HVector center = getCenter();

			HVector noZ = pt.sub( up.mul(up.dot(pt)) );

			double x = lon_of(new HVector(noZ.dot(center),
										  noZ.dot(center.cross(up)),
										  0));

//			double y = Math.asin(up.dot(pt)); <-- numerically unstable, NANs!!
			double y = Math.PI/2 - up.unitSeparation(pt);

			return  new Point2D.Double(Math.toDegrees(x) % 360.0,
                                                   Math.toDegrees(y));
		 }
		
	 }

	public static class Projection_SOM extends ProjObj
	 {
		public Projection_SOM(double start)
		 {
			start = Math.round(start*10) / 10.0;
			log.println("Initial ET: "
						+ Math.round(start) + "." + Math.round(start*10)%10);
			serverOffsetX = start;
		 }

		public Point2D getProjectionCenter()
		{
			return (new Point2D.Double(serverOffsetX,0.));
		}

		public double getCenterLon()
		 {
			return  Double.NaN;
		 }

		public double getCenterLat()
		 {
			return  Double.NaN;
		 }

		public double getBucketWidth(double ppd)
		 {
			int iVal;
			double val;

			if (ppd >= 8.0)
				val= noelsNumber / ppd / 360.0 * 7200.0;
			else
				val= bensNumber / ppd / 360.0 * 7200.0;


			iVal = (int)Math.ceil(val); //First, round up to nearest int;

			if (iVal % 2 != 0) //iVal is odd

				iVal++; //now it's even

			return((double)iVal);
/*
			if (ppd >= 8.0)
				return((((noelsNumber/ppd)/360.0)*7200.0));
			else
				return((((bensNumber/ppd)/360.0)*7200.0));
*/

		 }

		public double getBucketHeight(double ppd)
		 {
			if (ppd >= 4.0)
				return(noelsNumber/ppd);
			else
				return(bensNumber);
		 }

		private double serverOffsetX = 0;
		public double getServerOffsetX() { return  serverOffsetX; }
		public double getInitial_X()			{ return (0.0);	}
		public double getInitial_Y()			{ return (0.0);			}
		public double getXMin(double x)		{ return (x);			}
		public double getUnitWidth()			{ return (20.0);		}
		public double getUnitHeight()			{ return (1.0);			}
		public String getProjectType()		{ return ("SOM");			}
		public double getCircumfrence()		{ return (7200.0);			}
		public double getDelta(double ppd)				{ return (2.0);			}
		public double upperLimit() { return (bensNumber);}
		public double lowerLimit() { return -(bensNumber);}

		public String getProjectionSpecialParameters()
		 {
			return("&TRACK_format=c");
		 }

		public Point2D convSpatialToWorld(Point2D orig)
		 {
			// for now
			return  orig;
		 }

		public Point2D convWorldToSpatial(Point2D orig)
		 {
			// for now
			return  orig;
		 }

		public Point2D convWorldToSpatialFromProj(MultiProjection proj, Point2D orig)
		 {
                    Point2D.Double ret = new Point2D.Double(0,0);

                    GridDataStore grid = GridDataStore.forProj(proj);

                    HVector hv = proj_world_toHVector(grid, orig);
                    if ( hv != null ) {
                      ret.x = Math.toDegrees(ProjObj.lon_of(hv));
                      ret.y = Math.toDegrees(ProjObj.lat_of(hv));
                    } else {
                      //just return orig for not
                      ret.x = orig.getX();
                      ret.y = orig.getY();
                    }

                    return ret;
  		 }



                 //temp functions stolen from SpatialGraphicsSpOb class - which the
                //comments say belongs in the projection object. Until it is relocated,
                //i will use this bastardized one.
                private HVector proj_world_toHVector(GridDataStore grid, Point2D w)
                {
                    HVector result = null;

                    try {
                      Point cell = grid.getCellPoint(w);
                      HVector sw = grid.getGridData(cell.x  , cell.y  );
                      HVector se = grid.getGridData(cell.x+1, cell.y  );
                      HVector ne = grid.getGridData(cell.x+1, cell.y+1);
                      HVector nw = grid.getGridData(cell.x  , cell.y+1);

                    Point2D offset = grid.getCellOffset(w, cell);
                    result = interpolate(offset, sw, se, ne, nw);

                    } catch ( Exception e ) { }

                    return  result;
                }

                private static HVector interpolate(Point2D offset,
                                                   HVector sw, HVector se,
                                                   HVector ne, HVector nw)
                {
                  HVector s_normal = sw.cross(se).unit();
                  HVector n_normal = nw.cross(ne).unit();
                  HVector e_normal = ne.cross(se).unit();
                  HVector w_normal = nw.cross(sw).unit();

                  HVector nperc_axis = s_normal.cross(n_normal).unit();
                  HVector eperc_axis = w_normal.cross(e_normal).unit();

                  double ns_maxangle = Math.acos(s_normal.dot(n_normal));
                  double ew_maxangle = Math.acos(e_normal.dot(w_normal));

                  //// ABOVE: pre-calculatable / BELOW: dynamic ////

                  double n_angle = ns_maxangle * offset.getY();
                  HVector ns_interp_normal = s_normal.rotateP(nperc_axis, n_angle);

                  double e_angle = ew_maxangle * offset.getX();
                  HVector ew_interp_normal = w_normal.rotateP(eperc_axis, e_angle);

                  HVector pt = ew_interp_normal.cross(ns_interp_normal).unit();

                  // Make sure we have the right sign on pt
                  if(pt.dot(nw) >= 0)
                          return  pt;
                  else
                          return  pt.neg();
                }


	 }

	////////////// BEGIN internal cylindrical routines /////////////

	public static HVector marsll2vector(Point2D pt)
	 {
		return  marsll2vector(pt.getX(), pt.getY());
	 }
	
	/**
	 * @param lon Radians west of the prime meridian
	 * @param lat Radians north of the equator
	 * @return The {@link HVector} on the unit sphere for this lon/lat position
	 */
	public static HVector marsll2vector(double lon, double lat)
	 {
		return  new HVector(Math.cos(lat)*Math.cos(-lon),
							Math.cos(lat)*Math.sin(-lon),
							Math.sin(lat));
	 }

	public static double lat_of(HVector p)
	 {
		return  Math.asin(p.unit().z);
	 }

	public static double lon_of(HVector p)
	 {
		if(p.y > 0)
			return  Math.PI * 2 - Math.atan2(p.y, p.x);

		else if(p.y < 0)
			return  -Math.atan2(p.y, p.x);

		else if(p.x < 0)
			return  Math.PI;

		else
			return  0;

	 }

	////////////// END internal cylindrical routines /////////////

	public static void main(String[] av)
	 throws Throwable
	 {
		if(av.length == 1)
		 {
			dumpGrid(av[0]);
			System.exit(0);
		 }
		else if(av.length == 2)
		 {
			ProjObj.Projection_OC po =
				new Projection_OC(Double.parseDouble(av[0]),
								  Double.parseDouble(av[1]));
			System.exit(0);
		 }

		ProjObj.Projection_OC po =
			new Projection_OC(new HVector(Double.parseDouble(av[0]),
										  Double.parseDouble(av[1])));
		BufferedReader in =
			new BufferedReader(new InputStreamReader(System.in));

		System.out.println("up = " +
						   po.getUp().lon() + " " +
						   po.getUp().lat());
		System.out.println("center = " +
						   po.getCenter().lon() + " " +
						   po.getCenter().lat());

		for(;;)
		 {
			double x = Double.parseDouble(in.readLine());
			double y = Double.parseDouble(in.readLine());

			Point2D w2s = po.convWorldToSpatial(x, y);
			System.out.println("W -> S\t" + new HVector(w2s));

			String cmd = "make_track" +
				" up_lon " + av[0] +
				" up_lat " + av[1] +
				" xmin " + x +
				" ymin " + y +
				" format c";
			cmd = "wget -q -O - 'http://jmars.asu.edu/internal/make_track.phtml?" +
				"format=c" +
				"&upLon=" + av[0] +
				"&upLat=" + av[1] +
				"&xmin=" + x +
				"&ymin=" + y +
				"'";
			System.out.println(cmd);
			Process p = Runtime.getRuntime().exec(cmd);

			p.waitFor();
			InputStream is = p.getInputStream();
//			System.out.println(is);
			System.out.println("track\t" +
							   new BufferedReader(new InputStreamReader(is))
								.readLine());
			p.destroy();
		 }
	 }

	private static void dumpGrid(String url)
	 {
		String args = url.substring(url.indexOf('?')+1);
		StringTokenizer tok = new StringTokenizer(args, "&=");
		double xmin = 0;
		double ymin = 0;
		double xdelta = 1;
		double ydelta = 1;
		int xcount = 1;
		int ycount = 1;
		double upLat = 999;
		double upLon = 999;

		while(tok.hasMoreTokens())
		 {
			String label = tok.nextToken().intern();
			double val = Double.parseDouble(tok.nextToken());

			if     (label == "xmin"  ) xmin   = val;
			else if(label == "ymin"  ) ymin   = val;
			else if(label == "xdelta") xdelta = val;
			else if(label == "ydelta") ydelta = val;
			else if(label == "xcount") xcount = (int) Math.round(val);
			else if(label == "ycount") ycount = (int) Math.round(val);
			else if(label == "upLat" ) upLat  = val;
			else if(label == "upLon" ) upLon  = val;
			else System.err.println("UNKNOWN LABEL: " + label);
		 }

		ProjObj.Projection_OC po =
			new Projection_OC(new HVector(upLon, upLat));

		for(int x=0; x<xcount; x++)
			for(int y=0; y<ycount; y++)
			 {
				Point2D world = new Point2D.Double(xmin + xdelta*x,
												   ymin + ydelta*y);
				Point2D spatial = po.convWorldToSpatial(world);
				HVector v = new HVector(spatial);
				System.out.println(v);
			 }
	 }
 }
