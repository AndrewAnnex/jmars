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


package edu.asu.jmars.graphics;

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 ** A simple but indispensible class for caching groundtrack data. A
 ** GridDataStore is requested for a particular projection object,
 ** using the static {@link #forProj} method. No public constructors
 ** are provided... GridDataStore objects MUST be obtained through
 ** this static call.If an object already exists that is compatible
 ** with the given projection, then it's returned. Otherwise, a new
 ** one is created and stored.
 **
 ** <p>A GridDataStore behaves like an array of HVector points. Points
 ** are retrieved by calling {@link #getGridData}. You must supply a
 ** cell grid coordinate as arguments, a pair of integers. You can
 ** determine what cell contains a given world coordinate using {@link
 ** #getCellPoint}, the lower-left corner the cell is returned as an
 ** integer pair.
 **
 ** <p>To determine where within that cell that a given world
 ** coordinate lies, use one of the <code>getCellOffset</code>
 ** methods. Two such methods are supplied. One takes a world
 ** coordinate point and returns the offset. The other method takes a
 ** world coordinate point AND its cell point (as returned by
 ** getCellPoint for the given world coordinate), which is more
 ** efficient if the user has already obtained the cell point.
 **
 ** <p>The reverse operations are straightforward. Given a cell point
 ** and an offset, you may obtain the specified point's world
 ** coordinate using one of the two <code>getWorldPoint</code>
 ** methods.
 **
 ** <p>INTERNAL NOTES: The track server is easy to spot in the source
 ** file. Inside <code>forProj</code> is the method for determining
 ** how to space out the grid and where to base it from (i.e. what
 ** world point is at the zero i/j index). Note that things are
 ** currently set to make the grid spaced 128 pixels worth of world
 ** coordinate distance in both x and y. This seemed a reasonable
 ** first shot. Also note that the <code>gridsByPixelSize</code> map
 ** maintains the existing data store objects. It's currently indexed
 ** simply on the height of the pixel from the projection (basically
 ** the ppd, in a roundabout but simple-to-code way).
 **
 ** <p>HELPFUL ILLUSTRATION:
 **
 ** <pre>
 **      ...      ...      ...      ...      ...
 **       |        |        |        |        |
 ** ...---+--------+--------+--------+--------+---...
 **       |        |        |        |        |
 **       |****W   |        |        |        |
 **       |    *   |        |        |        |
 **       |    *   |        |        |        |
 ** ...---C--------+--------+--------+--------+---...
 **       |        |        |        |        |
 **       |        |        |        |        |
 **       |        |        |        |        |
 **       |        |        |        |        |
 ** ...---+--------+--------+--------+--------+---...
 **       |        |        |        |        |
 **       |        |        |        |        |
 **       |        |        |        |        |
 **       |        |        |        |        |
 ** ...---+--------+--------+--------+--------+---...
 **       |        |        |        |        |
 **      ...      ...      ...      ...      ...
 ** </pre>
 **
 ** <p>For the above grid, C is the cell point (it's an i/j index
 ** pair) corresponding to the world coordinate point W. The asterisks
 ** are the offset values for point W within cell C. The x and y
 ** offset are each values in the range [0:1] that indicate how far
 ** from left-to-right and bottom-to-top the point is within its cell.
 **/
public final class GridDataStore
 {
	private static DebugLog log = DebugLog.instance();

	public static final String TRACK_SERVER = Config.get("groundtrack");
	private static Map gridsByPixelSize = new HashMap();

	/**
	 ** Data structure for holding description of a grid cell. Dead
	 ** data structure, no real methods (save for the constructor).
	 **/
	public static final class Cell
	 {
		// Each of the cell's border points
		public final HVector sw;
		public final HVector se;
		public final HVector nw;
		public final HVector ne;
		// What order were they just specified in?
		public final boolean clockwise;
		// All of the border points, as a 5-point closed quadrilateral chain
		public final HVector[] chain;

		// The normals of each cell wall, pointing inwards.
		public final HVector s;
		public final HVector n;
		public final HVector e;
		public final HVector w;
		// The 4 normals in sequence, corresponding somewhat to 'chain' above
		public final HVector[] norms;

		// Interpolation axes
		public final HVector wePlane;
		public final HVector snPlane;
		// Interpolation data values
		public final double wePlaneSpan;
		public final double snPlaneSpan;

		// Debugging
		public int i;
		public int j;

		// Spatial indexing stuff
		public final int minXS;
		public final int minYS;
		public final int maxXS;
		public final int maxYS;

		// Adapted (and improved) from SpatialGraphicsSpOb.uninterpolate().
		public Point2D uninterpolate(HVector pt, Point2D unitPt)
		 {
			if(unitPt == null)
				unitPt = new Point2D.Double();

			HVector pt_we = wePlane.cross(pt).unit();
			HVector pt_sn = snPlane.cross(pt).unit();

			double x = pt_we.separation(w) / wePlaneSpan;
			double y = pt_sn.separation(s) / snPlaneSpan;

/**
 ** Note: I would think that BOTH of the below conditional negation
 ** statements are necessary. However, for reasons unknown, the x one
 ** must be performed and the y one must NOT. You get graphics
 ** glitches in the rendering of framelets of stamp data otherwise.
 **/
			if(pt.dot(w) < 0) x = -x;
			if(pt.dot(s) < 0) y = -y;

			unitPt.setLocation(x, y);
			return  unitPt;
		 }

		public boolean dead;
		public Cell(HVector sw,
					HVector se,
					HVector ne,
					HVector nw)
		 {
			this.dead = sw.x == 0  &&  sw.y == 0  &&  sw.z == 0;
			this.sw = sw;
			this.se = se;
			this.ne = ne;
			this.nw = nw;

			chain = new HVector[] { sw, se, ne, nw, sw };

			HVector _s = sw.cross(se).unit(); // the "real" s is declared final
			clockwise = _s.dot(ne) < 0;

			if(clockwise)
			 {
				s = _s.neg();
				e = ne.cross(se).unit();
				n = nw.cross(ne).unit();
				w = sw.cross(nw).unit();
			 }
			else
			 {
				s = _s;
				e = se.cross(ne).unit();
				n = ne.cross(nw).unit();
				w = nw.cross(sw).unit();
			 }

			norms = new HVector[] { s, e, n, w };

			wePlane = e.cross(w).unit();
			snPlane = n.cross(s).unit();
			wePlaneSpan = Math.PI - e.separation(w);
			snPlaneSpan = Math.PI - s.separation(n);

			SpatialGraphicsSpOb.CellRange range = getSpatialCellRange();

			this.minXS = range.minX;
			this.minYS = range.minY;
			this.maxXS = range.maxX;
			this.maxYS = range.maxY;
		 }

		/**
		 ** Convenience method for constructing super cells. For
		 ** vertically-related super cells of width 1 ONLY. Takes
		 ** advantage of numerous shared data members.
		 **/
		Cell(Cell sCell,
			 Cell nCell)
		 {
			this.sw = sCell.sw;
			this.se = sCell.se;
			this.ne = nCell.ne;
			this.nw = nCell.nw;

			chain = new HVector[] { sw, se, ne, nw, sw };

			clockwise = sCell.clockwise; // shared

			s = sCell.s;
			e = sCell.e; // shared
			n = nCell.n;
			w = nCell.w; // shared

			norms = new HVector[] { s, e, n, w };

			wePlane = sCell.wePlane; // shared
			snPlane = n.cross(s).unit();
			wePlaneSpan = sCell.wePlaneSpan; // shared
			snPlaneSpan = Math.PI - s.separation(n);

			SpatialGraphicsSpOb.CellRange range = getSpatialCellRange();

			this.minXS = range.minX;
			this.minYS = range.minY;
			this.maxXS = range.maxX;
			this.maxYS = range.maxY;
		 }

		private static final class Range
		 {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			void absorb(double val)
			 {
				if(!Double.isInfinite(min)  &&  Math.abs(val-min) > 180.0)
					if(val > 180)
					 {
						min += 360;
						max += 360;
					 }
					else
						val += 360;

				if(val < min)
					min = val;
				if(val > max)
					max = val;
			 }
		 }
		public boolean containsPole;

		private SpatialGraphicsSpOb.CellRange getSpatialCellRange()
		 {
			Range x = new Range();
			Range y = new Range();

			HVector up = new HVector(0, 0, 1);

			Point2D[] chainS = new Point2D[chain.length];
			for(int i=0; i<chainS.length; i++)
				chainS[i] = chain[i].toLonLat(null);

			for(int i=0; i<4; i++)
			 {
				x.absorb(chainS[i].getX());
				y.absorb(chainS[i].getY());

				// Stuff used to decide if the y range needs refinement
				HVector a = chain[i];
				HVector b = chain[i+1];
				HVector norm = a.cross(b).unit();
				HVector axis = norm.cross(up).unit();

				// Determine if we have an extreme latitude condition
				if(Util.sign(axis.dot(a)) == -Util.sign(axis.dot(b)))
				 {
					// Extreme condition: calculate the outer boundary
					double extreme = Math.toDegrees(Math.abs(
						Math.asin(norm.cross(axis).z)));
					y.absorb(a.z >= 0 ? extreme : -extreme);
				 }
			 }

			// Determine if we have an extreme longitude condition
			containsPole = true;
			int sign0 = Util.sign(norms[0].dot(up));
			if(sign0 != 0)
			for(int i=1; i<4; i++)
			 {
				int sign = Util.sign(norms[i].dot(up));
				if(sign == 0  ||  sign != sign0)
				 {
					containsPole = false;
					break;
				 }
			 }
			if(containsPole)
			 {
				x.min = 0;
				x.max = 360;
				if(y.min < 0) y.min = -90;
				else          y.max = +90;
			 }
			else if(x.max - x.min > 180)
			 {
				double t = x.min;
				x.min = x.max;
				x.max = t;
			 }

			if(Double.isInfinite(x.min)  ||
			   Double.isInfinite(x.max)  ||
			   Double.isInfinite(y.min)  ||
			   Double.isInfinite(y.max))
				log.aprintln("------------------------------------------------------------------------------------------------------------------------------------------------------- INFINITIES! " + x.min + x.max + y.min + y.max);

			SpatialGraphicsSpOb.CellRange range = new SpatialGraphicsSpOb.CellRange();
			range.minX = (int) Math.floor(x.min) / SpatialGraphicsSpOb.BINSZ_X;
			range.maxX = (int) Math.floor(x.max) / SpatialGraphicsSpOb.BINSZ_X;
			range.minY = (int)    floor90(y.min) / SpatialGraphicsSpOb.BINSZ_Y;
			range.maxY = (int)    floor90(y.max) / SpatialGraphicsSpOb.BINSZ_Y;

			return  range;
		 }

		private Rectangle2D worldBounds;
		public Rectangle2D getWorldBounds()
		 {
			if(worldBounds != null)
				return  worldBounds;

			Point2D[] chainW = new Point2D[chain.length];
			for(int i=0; i<chainW.length; i++)
				chainW[i] =
					Main.PO.convSpatialToWorld(chain[i].toLonLat(null));

			Range x = new Range();
			Range y = new Range();

			HVector up = ( (ProjObj.Projection_OC) Main.PO ).getUp();

			for(int i=0; i<4; i++)
			 {
				x.absorb(chainW[i].getX());
				y.absorb(chainW[i].getY());

				// Stuff used to decide if the y range needs refinement
				HVector a = chain[i];
				HVector b = chain[i+1];
				HVector norm = a.cross(b).unit();
				HVector axis = norm.cross(up).unit();

				// Determine if we have an extreme condition
				if(Util.sign(axis.dot(a)) == -Util.sign(axis.dot(b)))
				 {
					// Extreme condition: calculate the outer boundary
					double extreme = Math.toDegrees(Math.abs(
						Math.asin(norm.cross(axis).dot(up))));
					y.absorb(a.dot(up) >= 0 ? extreme : -extreme);
				 }
			 }

			if(Double.isInfinite(x.min)  ||
			   Double.isInfinite(x.max)  ||
			   Double.isInfinite(y.min)  ||
			   Double.isInfinite(y.max))
				log.aprintln("------------------------------------------------------------------------------------------------------------------------------------------------------- INFINITIES! " + x.min + x.max + y.min + y.max);
			worldBounds = new Rectangle2D.Double(x.min,
												 y.min,
												 x.max-x.min,
												 y.max-y.min);
			return  worldBounds;
		 }

		public boolean equals(Object x)
		 {
			if(x == null)
				return  false;

			if(!(x instanceof Cell))
				return  false;

			Cell c = (Cell) x;

			return  c.sw == sw
				&&  c.se == se
				&&  c.ne == ne
				&&  c.nw == nw;
		 }

		public String toString()
		 {
			StringWriter buff = new StringWriter();
			PrintWriter bout = new PrintWriter(buff);

			bout.println(".sw = " + sw);
			bout.println(".se = " + se);
			bout.println(".ne = " + ne);
			bout.println(".nw = " + nw);

			bout.println(".clockwise = " + clockwise);

			bout.println(".s = " + s);
			bout.println(".n = " + n);
			bout.println(".w = " + w);
			bout.println(".e = " + e);

			bout.println(".wePlane = " + wePlane);
			bout.println(".snPlane = " + snPlane);

			bout.println(".wePlaneSpan = " + Math.toDegrees(wePlaneSpan));
			bout.println(".snPlaneSpan = " + Math.toDegrees(snPlaneSpan));

			bout.println(System.identityHashCode(sw) + " / " +
						 System.identityHashCode(se) + " / " +
						 System.identityHashCode(ne) + " / " +
						 System.identityHashCode(nw) );

			bout.println("XS = " + minXS + " to " + maxXS);
			bout.println("YS = " + minYS + " to " + maxYS);

			bout.flush();

			return  buff.toString();
		 }

		public String borderToString()
		 {
			StringWriter buff = new StringWriter();
			PrintWriter bout = new PrintWriter(buff);

			bout.println("\t.sw = " + sw);
			bout.println("\t.se = " + se);
			bout.println("\t.ne = " + ne);
			bout.println("\t.nw = " + nw);

			bout.flush();

			return  buff.toString();
		 }
	 }

	/**
	 ** Primary entry point for obtaining GridDataStore objects.
	 **/
	public static GridDataStore forProj(MultiProjection proj)
	 {
		if (!(proj.getProjection() instanceof ProjObj.Projection_SOM)) {
			log.aprintln("PROGRAMMER: Grids only implemented for time!");
			return null;
		}

		Dimension2D pixel = proj.getPixelSize();
		Double key = null;//new Double(pixel.getHeight());

		GridDataStore grid = (GridDataStore) gridsByPixelSize.get(key);
		if(grid == null)
		 {
			Rectangle2D win = proj.getWorldWindow();
			double xDelta = 400;//pixel.getWidth() * 128;
			double yDelta = 20;//pixel.getHeight() * 128;
			double xBase =
				Util.roundToMultiple(
					win.getX() + Main.PO.getServerOffsetX(),
					xDelta * XCOUNT)
				- Main.PO.getServerOffsetX();
			double yBase = 0;
			log.println("Creating new GridDataStore");
			log.println("win = " + win);
			log.println("xDelta = " + xDelta);
			log.println("yDelta = " + yDelta);
			log.println("xBase = " + xBase);
			log.println("yBase = " + yBase);
			grid = new GridDataStore(TRACK_SERVER,
									 xBase, yBase, xDelta, yDelta);
			gridsByPixelSize.put(key, grid);
		 }

		return  grid;
	 }

	/**
	 ** Control the chunk size.
	 **/
	private static final int XCOUNT = 20;
	private static final int YCOUNT = 10;

	/**
	 ** For a given i,j point in the grid, returns a "base point" (or
	 ** key) for the chunk of data that the given point falls in.
	 **/
	private static Point createKey(int i, int j)
	 {
		double _i = i + XCOUNT / 2;
		double _j = j + YCOUNT / 2;
		return  new Point((int) Math.floor(_i / XCOUNT)*XCOUNT - XCOUNT/2,
						  (int) Math.floor(_j / YCOUNT)*YCOUNT - YCOUNT/2);
	 }

	/**
	 ** For a given base point, returns another base point that is i,j
	 ** base points over in the x,y directions.
	 **/
	private static Point nextKey(Point key, int i, int j)
	 {
		return  new Point(key.x + i * XCOUNT,
						  key.y + j * YCOUNT);
	 }


///////////////////////////////////////////////////////////////////////////
// THE ABOVE IS ALL STATIC STUFF, THE BELOW IS ALL NON-STATIC
///////////////////////////////////////////////////////////////////////////

	/**
	 ** Internally, grid data is grabbed in chunks (not one point at a
	 ** time). The size of each chunk is given by XCOUNT and YCOUNT.
	 ** The chunks are maintained as a sparse array, implemented by a
	 ** map keyed on each chunk's coordinates.
	 **/
	private Map gridChunks = new HashMap(); // grid cell corner points, OLD
	private Map gridCellChunks = new HashMap(); // grid cell data structures

	private String server;
	double xBase, xDelta;
	double yBase, yDelta;

	GridDataStore(String server,
				  double xBase,  double yBase,
				  double xDelta, double yDelta)
	 {
		log.println("xDelta = " + xDelta);
		log.println("yDelta = " + yDelta);
		log.println("xBase = " + xBase);
		log.println("yBase = " + yBase);

		this.server = server;
		this.xBase = xBase;
		this.yBase = yBase;
		this.xDelta = xDelta;
		this.yDelta = yDelta;

		if(HVector.INPUT_usesLatLon)
			throw  new Error(
				"/////////////////////////////////////////////////////////\n" +
				"/////////////////////////////////////////////////////////\n" +
				"/////////////////////////////////////////////////////////\n" +
				"//// THE APPLICATION CAN'T RUN IN ITS CURRENT STATE! ////\n" +
				"////   Tell Michael: INPUT_usesLatLon is set wrong.  ////\n" +
				"/////////////////////////////////////////////////////////\n" +
				"/////////////////////////////////////////////////////////\n" +
				"/////////////////////////////////////////////////////////\n"
				);
	 }


	/**
	 ** Given the base point for a chunk of data, goes out and
	 ** retrieves that data from the server. Returns null on error.
	 **/
	private Cell[][] createCellChunk(Point ijBase)
	 {
		double xMin = ijBase.x * xDelta + xBase;
		double yMin = ijBase.y * yDelta + yBase;
/*
		log.println("ijBase.x = " + ijBase.x);
		log.println("xDelta = " + xDelta);
		log.println("xBase = " + xBase);
*/
		HVector[][] swPointData = getChunk(        ijBase       );
		HVector[][] sePointData = getChunk(nextKey(ijBase, 1, 0));
		HVector[][] nePointData = getChunk(nextKey(ijBase, 1, 1));
		HVector[][] nwPointData = getChunk(nextKey(ijBase, 0, 1));
		Cell[][] chunk = new Cell[XCOUNT][YCOUNT];

		// Excludes the east-most column
		for(int i=0; i<XCOUNT-1; i++)
		 {
			// Excludes the north-most row
			for(int j=0; j<YCOUNT-1; j++)
				chunk[i][j] = new Cell(swPointData[i  ][j  ],
									   swPointData[i+1][j  ],
									   swPointData[i+1][j+1],
									   swPointData[i  ][j+1] );
			// The north-most row
			final int j = YCOUNT - 1;
			chunk[i][j] = new Cell(swPointData[i  ][j],
								   swPointData[i+1][j],
								   nwPointData[i+1][0],
								   nwPointData[i  ][0] );
		 }

		// The east-most column, excludes the most-north-east cell
		for(int j=0; j<YCOUNT-1; j++)
		 {
			final int i = XCOUNT - 1;
			chunk[i][j] = new Cell(swPointData[i][j  ],
								   sePointData[0][j  ],
								   sePointData[0][j+1],
								   swPointData[i][j+1] );
		 }
		// The most-north-east cell
		final int i = XCOUNT - 1;
		final int j = YCOUNT - 1;
		chunk[i][j] = new Cell(swPointData[i][j],
							   sePointData[0][j],
							   nePointData[0][0],
							   nwPointData[i][0] );

		return  chunk;
	 }

	private static final HVector[][] zeroChunk;
	static
	 {
		HVector zeroVector = new HVector(0,0,0);
		zeroChunk = new HVector[XCOUNT][YCOUNT];
		for(int i=0; i<XCOUNT; i++)
			for(int j=0; j<YCOUNT; j++)
				zeroChunk[i][j] = zeroVector;
	 }

	/**
	 ** Returns a semi-deep copy of the array zeroChunk (still shallow
	 ** w.r.t. the elements themselves). This is better than
	 ** zeroChunk.clone(), which is shallow w.r.t. the rows.
	 **/
	private static HVector[][] makeZeroChunk()
	 {
		HVector[][] copy = new HVector[XCOUNT][YCOUNT];
		for(int i=0; i<XCOUNT; i++)
			copy[i] = (HVector[]) zeroChunk[0].clone();
		return  copy;
	 }

	private URL getRemoteUrl(double xMin, double yMin)
	 {
		String query =
			"format=c" +
			"&xmin=" + xMin +
			"&xcount=" + XCOUNT +
			"&xdelta=" + xDelta +
			"&ymin=" + yMin +
			"&ycount=" + YCOUNT +
			"&ydelta=" + yDelta +
			"&key=" + Main.KEY;

		// Create the url
		URL url;
		try
		 {
			url = new URL(server + query);
		 }
		catch(MalformedURLException e)
		 {
			log.aprintln("PROGRAMMER: Bad url " + server + query);
			log.println(e);
			return  null;
		 }
		log.println("Using url " + url);

		return  url;
	 }

	private String getLocalFilename(double xMin, double yMin)
	 {
		String fname = "GRIDS/g_" + Math.round(xMin) + "_" + Math.round(yMin);
		log.println("Using fname " + fname);
		return  fname;
	 }

	/**
	 ** Given the base point for a chunk of data, goes out and
	 ** retrieves that data from the server. Returns dummy data on
	 ** error (all zero vectors).
	 **/
	private HVector[][] createChunk(Point ijBase)
	 {
		HVector[][] chunk;

//		log.println("ijBase = " + ijBase);

		double xMin = ijBase.x * xDelta + xBase + Main.PO.getServerOffsetX();
		double yMin = ijBase.y * yDelta + yBase;

//		log.println("xMin = " + xMin);
//		log.println("yMin = " + yMin);

		chunk = getRemoteChunk(xMin, yMin);

		if(chunk == null)
			chunk = zeroChunk;

		return  chunk;
	 }

	/**
	 ** Used by createChunk. Returns null if the data can't be found
	 ** locally.
	 **/
	private HVector[][] getLocalChunk(double xMin, double yMin)
	 {
		try
		 {
			String fname = getLocalFilename(xMin, yMin);

			BufferedReader fin = new BufferedReader(
				new InputStreamReader(
					new FileInputStream(fname)
					 )
				 );

			return  readChunk(fin, fname);
		 }
		catch(IOException e)
		 {
			return  null;
		 }
	 }

	/**
	 ** Used by createChunk. Returns null if the url can't be opened.
	 **/
	private HVector[][] getRemoteChunk(double xMin, double yMin)
	 {
		URL url = getRemoteUrl(xMin, yMin);
		if(url == null)
			return  null;

		// Create a connection to that url
		BufferedReader fin;
		for(int i=0; i<4; i++)
			// If we fail to open the url, the for() loop guarantees
			// that we retry at least 4 times before giving up and
			// declaring an error.
			try
			 {
				fin = new BufferedReader(new InputStreamReader(
					url.openStream()));
					
				return  readChunk(fin, url.toString());
			 }
			catch(Throwable e)
			 {
				log.aprintln("ERROR OPENING URL" + url);
				log.aprintln("(will retry " + (4-i) + " more times)");
				log.println(e);
				try
				 {
					Thread.sleep(100);
				 }
				catch(Throwable ex)
				 {
				 }
			 }

		return  null;
	 }

	/**
	 ** Given a file reader, returns a chunk that's read from it.
	 ** Returns partially-zeroed data if errors occur.
	 **/
	private HVector[][] readChunk(BufferedReader fin,
								  String source)
	 throws IOException
	 {
		// Read the data
		HVector[][] chunk = makeZeroChunk();

		int i=0, j=0;
		try
		 {
			for(i=0; i<XCOUNT; i++)
				for(j=0; j<YCOUNT; j++)
					chunk[i][j] = HVector.readExc(fin);
		 }
		catch(IOException e)
		 {
			log.aprintln("ERROR WHILE READING " + i+","+j + " FROM " + source);
			log.aprintln(e);
			throw  e;
		 }

		// Return the data
		return  chunk;
	 }

	/**
	 ** Given a world coordinate, returns the cell index point for
	 ** this grid that covers that coordinate.
	 **/
	public final Point getCellPoint(Point2D w)
	 {
		return  new Point((int) Math.floor((w.getX() - xBase) / xDelta),
						  (int) Math.floor((w.getY() - yBase) / yDelta));
	 }

	/**
	 ** Given a world coordinate, returns the offset (an x/y pair in
	 ** the range [0:1]) specifying proportionally where the
	 ** coordinate lies within its world coordinate grid cell.
	 **/
	public final Point2D getCellOffset(Point2D w)
	 {
		return  getCellOffset(w, getCellPoint(w));
	 }

	/**
	 ** Given a world coordinate, returns the offset (an x/y pair in
	 ** the range [0:1]) specifying proportionally where the
	 ** coordinate lies within its world coordinate grid cell. If the
	 ** user already has the cell point, they can use this function
	 ** without incurring extra overhead to find the cell point.
	 **/
	public final Point2D getCellOffset(Point2D w, Point c)
	 {
		return  new Point2D.Double((w.getX() - xBase) / xDelta - c.x,
								   (w.getY() - yBase) / yDelta - c.y);
	 }

	private final Point2D zeroOffset = new Point2D.Double(0, 0);

	/**
	 ** Given an offset and a cell index point, returns the
	 ** corresponding world coordinate. A null offset is interpreted
	 ** as zero.
	 **/
	public final Point2D getWorldPoint(Point c, Point2D offset)
	 {
		if(offset == null)
			offset = zeroOffset;
		return  new Point2D.Double((c.x + offset.getX()) * xDelta + xBase,
								   (c.y + offset.getY()) * yDelta + yBase);
	 }
	/**
	 ** Given an offset and a cell index point, returns the
	 ** corresponding world coordinate. A null offset is interpreted
	 ** as zero.
	 **/
	public final Point2D getWorldPoint(int i, int j, Point2D offset)
	 {
		if(offset == null)
			offset = zeroOffset;
		return  new Point2D.Double((i + offset.getX()) * xDelta + xBase,
								   (j + offset.getY()) * yDelta + yBase);
	 }

	/**
	 ** Returns the first y index that refers to a grid point at or
	 ** above 80 degrees.
	 **/
	public final int getMaxY()
	 {
		return  (int) Math.floor((80.0 - yBase) / yDelta);
	 }

	/**
	 ** Returns the first y index that refers to a grid point at or
	 ** above -80 degrees.
	 **/
	public final int getMinY()
	 {
		return  (int) Math.floor((-80 - yBase) / yDelta);
	 }

	/**
	 ** Given a world rectangle, returns a bracketing rectangle of
	 ** grid cells.
	 **/
	public final Rectangle getCellRange(Rectangle2D w)
	 {
		int minX = (int) Math.floor((w.getX() - xBase) / xDelta);
		int minY = (int) Math.floor((w.getY() - yBase) / yDelta);

		int maxX = (int) Math.ceil((w.getMaxX() - xBase) / xDelta);
		int maxY = (int) Math.ceil((w.getMaxY() - yBase) / yDelta);

		return  new Rectangle(minX,
							  minY,
							  maxX - minX,
							  maxY - minY);
	 }

	/**
	 ** Given a cell index point, returns the chunk corresponding to
	 ** it. Generates the chunk if necessary.
	 **/
	private synchronized HVector[][] getChunk(Point key)
	 {
		HVector[][] data = (HVector[][]) gridChunks.get(key);

		// If we don't have it yet, generate and store that data
		if(data == null)
		 {
			gridChunks.put(key, data = createChunk(key));
//			log.println("Stored into " + key);
		 }

		return  data;
	 }

	/**
	 ** Given a cell index point, returns the 3-space vector
	 ** corresponding to that grid point.
	 **/
	public final HVector getGridData(int i, int j)
	 {
		Point key = createKey(i, j);

		HVector[][] data = getChunk(key);

		// Finally, return the requested point
		return  data[i - key.x][j - key.y];
	 }
//  		try
//  		 {
//  			return  data[i - key.x][j - key.y];
//  		 }
//  		catch(ArrayIndexOutOfBoundsException e)
//  		 {
//  			log.aprintln("data.length = " + data.length);
//  			log.aprintln("i = " + i);
//  			log.aprintln("key.x = " + key.x);
//  			if(i - key.x < data.length  &&  i - key.x > 0)
//  			 {
//  				log.aprintln("data[i-key.x].length = " + data[i-key.x].length);
//  				log.aprintln("j = " + j);
//  				log.aprintln("key.y = " + key.y);
//  			 }
//  			else
//  				log.aprintln("j omitted");
//  			throw  e;
//  		 }

	/**
	 ** Given a cell index point, returns the chunk corresponding to
	 ** it. Generates the chunk if necessary.
	 **/
	private synchronized Cell[][] getCellChunk(Point key)
	 {
		Cell[][] data = (Cell[][]) gridCellChunks.get(key);

		// If we don't have it yet, generate and store that data
		if(data == null)
			gridCellChunks.put(key, data = createCellChunk(key));

		return  data;
	 }

	/**
	 ** Given a cell index point, returns the Cell data structure
	 ** describing that index point's grid cell.
	 **/
	public final Cell getCell(int i, int j)
	 {
		Point key = createKey(i, j);

		Cell[][] data = getCellChunk(key);

		// Finally, return the requested point
		return  data[i - key.x][j - key.y];
	 }

	/**
	 ** Given a cell range, returns the Cell data structure describing
	 ** that supercell.
	 **/
	public final Cell getSuperCell(Rectangle cellRange)
	 {
		if(cellRange.width != 1)
		 {
			log.aprintln("///////////////////////////////////////////\n" +
						 "///////////////////////////////////////////\n" +
						 "// MICHAEL: BAD SUPERCELL CONSTRUCTED!!! //\n" +
						 "///////////////////////////////////////////\n" +
						 "///////////////////////////////////////////"   );
			return  null;
		 }

		int x = cellRange.x;
		int y = cellRange.y;
		int w = cellRange.width;
		int h = cellRange.height;

		return  new Cell(getCell(x, y  ),
						 getCell(x, y+h-1));
//  		return  new Cell(getGridData(x  , y  ),
//  						 getGridData(x+w, y  ),
//  						 getGridData(x+w, y+h),
//  						 getGridData(x  , y+h) );
	 }

	/////////////////////////////////////////////////////////////////////////
	// FOR GENERATION OF LOCAL GRID FILES FROM SERVER DATA
	/////////////////////////////////////////////////////////////////////////

	public static void generateLocalFiles(Rectangle2D worldRange)
	 throws IOException
	 {
		// Ensure the GRIDS directory exists
		File gridDir = new File("GRIDS");
		if(!gridDir.isDirectory())
		 {
			log.aprintln("Creating GRIDS directory...");
			if(!gridDir.mkdir())
			 {
				log.aprintln("Unable to create GRIDS directory.");
				System.exit(-1);
			 }
		 }

		// Create an appropriate GridDataStore object
		double xDelta = 400;
		double yDelta = 20;
		double xBase =
			Util.roundToMultiple(
				worldRange.getX() + Main.PO.getServerOffsetX(),
				xDelta * XCOUNT)
			- Main.PO.getServerOffsetX();
		double yBase = 0;
		GridDataStore grid = new GridDataStore(TRACK_SERVER,
											   xBase, yBase, xDelta, yDelta);

		Rectangle cellRange = grid.getCellRange(worldRange);
		Point baseKey = createKey(cellRange.x, cellRange.y);

		log.println("worldRange = " + worldRange);
		log.println("cellRange = " + cellRange);
		log.println("baseKey = " + baseKey);

		int keyWidth  = cellRange.x + cellRange.width  - baseKey.x;
		int keyHeight = cellRange.y + cellRange.height - baseKey.y;
		int iCount = (int) Math.ceil( keyWidth  / (double) XCOUNT );
		int jCount = (int) Math.ceil( keyHeight / (double) YCOUNT );

		System.out.println("Creating " +
						   iCount + " x " +
						   jCount + " = " +
						   iCount*jCount + " grid files.");

		for(int i=0; i<iCount; i++)
			for(int j=0; j<jCount; j++)
			 {
				Point key = nextKey(baseKey, i, j);

				log.println("-----------------------------");
				log.println("i,j = " + i + "," + j);
				log.println("key = " + key);

				double x = key.x * xDelta + xBase + Main.PO.getServerOffsetX();
				double y = key.y * yDelta + yBase;

				log.println("x = " + x);
				log.println("y = " + y);

				Util.urlToDisk1(grid.getRemoteUrl(x, y).toString(),
								grid.getLocalFilename(x, y));
			 }
	 }

	private static final int floor90(double y)
	 {
		int result = (int) Math.floor(y) + 90;
		if(result < 180)
			return  result;
		return  179;
	 }

	/////////////////////////////////////////////////////////////////////////
	// ALL OF BELOW: Driver to test
	/////////////////////////////////////////////////////////////////////////

	/**
	 ** @deprecated Code used as a simple test driver.
	 **/
	public static void main(String[] av)
	 throws IOException
	 {
		DebugLog.readFile(".debugrc");
		log.println("Creating");
		GridDataStore grid =
			new GridDataStore(av[0],
							  Double.parseDouble(av[1]),
							  Double.parseDouble(av[2]),
							  Double.parseDouble(av[3]),
							  Double.parseDouble(av[4]));

		log.println("For loop");
		for(int i=0; i<XCOUNT/2; i++)
			for(int j=0; j<YCOUNT/2; j++)
			 {
				HVector v = grid.getGridData(i,j);
				System.out.println(v);
			 }

	 }
 }
