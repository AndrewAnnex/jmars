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
import java.util.*;

public final class SpatialGraphicsSpOb
 extends SpatialGraphics2D
 {
	private static DebugLog log = DebugLog.instance();

	private GridDataStore grid;

	private int minGridX;
	private int minGridY;
	private int maxGridX;
	private int maxGridY;
	private AffineTransform fontTransform;
	private Font currentFont;

	static final int BINSZ_X = 10;
	static final int BINSZ_Y = 10;
	static final int BINS_X = 360 / BINSZ_X;
	static final int BINS_Y = 180 / BINSZ_Y;

	static
	 {
		if(360 % BINSZ_X != 0  ||  180 % BINSZ_Y != 0)
			throw new IllegalArgumentException(
				"The BINSZ_[XY] variables must evenly divide into 360 and 180.");
	 }

	static final class CellRange
	 {
		int minX;
		int maxX;
		int minY;
		int maxY;

		CellRange()
		 {
			minX = minY = Integer.MAX_VALUE;
			maxX = maxY = Integer.MIN_VALUE;
		 }

		CellRange(int x, int y)
		 {
			minX = maxX = x;
			minY = maxY = y;
		 }

		void absorb(int x, int y)
		 {
			if(x < minX) minX = x;
			if(y < minY) minY = y;
			if(x > maxX) maxX = x;
			if(y > maxY) maxY = y;
		 }

		void absorb(CellRange range)
		 {
			if(range == null)
				return;
			if(range.minX < minX) minX = range.minX;
			if(range.minY < minY) minY = range.minY;
			if(range.maxX > maxX) maxX = range.maxX;
			if(range.maxY > maxY) maxY = range.maxY;
		 }

		boolean isValid()
		 {
			return  minX <= maxX;
		 }

		public String toString()
		 {
			return "CellRange" +
				"[minX=" + minX +
				" maxX=" + maxX +
				" minY=" + minY +
				" maxY=" + maxY + "]";
		 }
	 }

	/**
	 ** Maps from lon/lat bins to cell range rectangles.
	 **/
	private CellRange[][] spatialIndex = new CellRange[BINS_X][BINS_Y];

	private void initializeRange(Rectangle gridRange)
	 {
		log.println(gridRange);
		minGridX = gridRange.x;
		maxGridX = gridRange.x + gridRange.width;
		minGridY = gridRange.y;
		maxGridY = gridRange.y + gridRange.height;

		// Prevent the grid from stretching too far in the Y direction
		minGridY = Math.max(minGridY, grid.getMinY());
		maxGridY = Math.min(maxGridY, grid.getMaxY());

		// Initialize the spatial index of grid cells
		Rectangle2D rec = new Rectangle2D.Double();
		for(int gi=minGridX; gi<maxGridX; gi++)
			for(int gj=minGridY; gj<maxGridY; gj++)
			 {
				GridDataStore.Cell cell = grid.getCell(gi, gj);
				if(cell.dead)
					continue;

				for(int _i=cell.minXS; _i<=cell.maxXS; _i++)
				 {
					final int i = _i % BINS_X;
					for(int j=cell.minYS; j<=cell.maxYS; j++)
					 {
						if(i < 0) throw new IllegalArgumentException("i = " + i);
						if(j < 0) throw new IllegalArgumentException("j = " + j);
						CellRange indexValue = spatialIndex[i][j];
						if(indexValue == null)
							spatialIndex[i][j] = new CellRange(gi, gj);
						else
							indexValue.absorb(gi, gj);
					 }
				 }
			 }
	 }

	/**
	 ** Given a spatial coordinate line's endpoints, returns a
	 ** bracketing rectangle of grid cells.
	 **
	 ** <p>Utilizes spatial indexing for efficiency, as opposed to a
	 ** brute-force method (effectively operates in O(1) time for
	 ** small shapes).
	 **
	 ** @return A rectangle whose lower bound is inclusive, and upper
	 ** bound exclusive.
	 **/
	private final CellRange getCellRangeFromLine(Point2D sa, Point2D sb,
												 HVector va, HVector vb)
	 {
		double lineMinX = sa.getX();
		double lineMaxX = sb.getX();
		double lineMinY = sa.getY();
		double lineMaxY = sb.getY();

		if(lineMinX > lineMaxX)
			if(lineMinX-lineMaxX > 180)
			 {
				double t = lineMinX;
				lineMinX = lineMaxX;
				lineMaxX = t;
			 }
			else
			 {
				lineMaxX += 360;
			 }
		if(lineMinY > lineMaxY)
		 {
			double t = lineMinY;
			lineMinY = lineMaxY;
			lineMaxY = t;
		 }

		HVector up = new HVector(0, 0, 1);
		HVector norm = va.cross(vb).unit();
		HVector axis = norm.cross(up).unit();

		// Determine if we have an extreme condition
		if(Util.sign(axis.dot(va)) == -Util.sign(axis.dot(vb)))
		 {
			// Extreme condition: calculate the outer boundary
			double extreme = Math.toDegrees(Math.abs(
								   Math.asin(norm.cross(axis).dot(up))));

			if(va.dot(up)>=0) {  if( extreme > lineMaxY) lineMaxY =  extreme; }
			else              {  if(-extreme < lineMinY) lineMinY = -extreme; }
		 }

		int minXS =  (int)Math.floor(lineMinX)       / BINSZ_X;
		int maxXS =  (int)Math.floor(lineMaxX)       / BINSZ_X;
		int minYS = ((int)Math.floor(lineMinY) + 90) / BINSZ_Y;
		int maxYS = ((int)Math.floor(lineMaxY) + 90) / BINSZ_Y;

		if(minYS >= BINS_Y) minYS = BINS_Y - 1;
		if(maxYS >= BINS_Y) maxYS = BINS_Y - 1;

		CellRange range = new CellRange();

		for(int _i=minXS; _i<=maxXS; _i++)
		 {
			final int i = _i % BINS_X;
			for(int j=minYS; j<=maxYS; j++)
				range.absorb(spatialIndex[i][j]);
		 }

		if(range.isValid())
			return  range;

		return  null;
	 }

	private void initializeFont(Dimension2D pixelSize)
	 {
		int ppd = 0;
		double dpi =
			GraphicsEnvironment
			.getLocalGraphicsEnvironment()
			.getDefaultScreenDevice()
			.getDefaultConfiguration()
			.getNormalizingTransform()
			.getScaleY();
		fontTransform =
			AffineTransform.getScaleInstance(dpi * pixelSize.getWidth(),
											 -dpi * pixelSize.getHeight());
	 }

	public SpatialGraphicsSpOb(Graphics2D g2, MultiProjection proj)
	 {
		this(g2, proj, false);
	 }

	/**
	 ** You do NOT want to use this constructor... it's for a rather
	 ** psychotic but useful hack.
	 **/
	public SpatialGraphicsSpOb(Graphics2D g2,
							   MultiProjection proj,
							   Rectangle2D worldRange)
	 {
		this.g2 = g2;
		this.grid = GridDataStore.forProj(proj);
		initializeRange(grid.getCellRange(worldRange));
		initializeFont(proj.getPixelSize());
		SHORT_CIRCUIT_DRAWS = false;
	 }

	/**
	 ** You do NOT want to use this constructor... it's for a rather
	 ** psychotic but useful hack.
	 **/
	public SpatialGraphicsSpOb(Graphics2D g2,
							   MultiProjection proj,
							   boolean short_circuit)
	 {
		this.g2 = g2;
		this.grid = GridDataStore.forProj(proj);

		log.println(proj.getWorldWindow());
		initializeRange(grid.getCellRange(proj.getWorldWindow()));
		initializeFont(proj.getPixelSize());
		SHORT_CIRCUIT_DRAWS = short_circuit;
	 }

	private SpatialGraphicsSpOb(Graphics2D g2,
								GridDataStore grid,
								Rectangle gridRange)
	 {
		this.g2 = g2;
		this.grid = grid;
		initializeRange(gridRange);
		SHORT_CIRCUIT_DRAWS = false;
	 }

	// RETURN VALUES:
	// -1	clipped out of existence
	// 0	neutral, success if ANY +2's are found, otherwise "clipped OoE"
	// +1	success
	// +2	unconditional success, overrides(?) any -1's
	private static int clipLine(HVector a, HVector b,
								HVector clip1, HVector clip2,
								HVector normal)
	 {
		int signA = sign(normal.dot(a));
		int signB = sign(normal.dot(b));

		// Test for AB signs: -- or -0 or 0-
		if(signA + signB < 0)
			return  -1;

		// Test for AB signs: ++ 00 +0 0+
		if(signA == signB  ||  signA+signB != 0)
			return  1;

		HVector c = HVector.intersectGSeg(a, b, clip1, clip2);

		// If the segment satisfies the sign test (and is therefore
		// straddling the clip), but the segment doesn't intersect the
		// clip, then we're in a conundrum. We need more information
		// than is present in this clipLine call: if the segment
		// intersects SOME side of the poly, then we succeed, but if
		// not, then we ultimately fail. So we return a "neutral"
		// indicator.
		if(c == null)
			return  0;

		if(signA < 0)
			a.set(c);
		else // signB < 0
			b.set(c);

		// Unconditional success: we straddled the clip and
		// intersected it.
		return  2;
	 }

	// Projects a line segment into a given quadrilateral. Returns
	// true if the line segment overlaps the quadrilateral, returns
	// false if it doesn't. If it does overlap the quadrilateral, then
	// a and b contain the projected endpoints of the line segment,
	// clipped to the quadrilateral. Otherwise, a and b contain
	// unspecified points.
	private static boolean clipLineToQuad(HVector a,
										  HVector b,
										  HVector[] pts,
										  HVector[] norms)
	 {
		// STATES:
		// 1	"okay", succeeds
		// 0	neutral, need a +2 from clipLine or we fail
		// 2	unconditional success, succeeds
		int state = 1;

		for(int i=0; i<4; i++)
			switch(clipLine(a, b, pts[i], pts[i+1], norms[i]))
			 {

			 case -1:
				return  false;
				//break;

			 case 2:
				state = 2;
				break;

			 case 0:
				if(state == 1)
					state = 0;
				break;

			 }

		return  state != 0;
	 }

	public void drawCells()
	 {
		Point2D zero = new Point();
		Rectangle2D rec = new Rectangle2D.Double();
		for(int i=minGridX; i<maxGridX; i++)
			for(int j=minGridY; j<maxGridY; j++)
			 {
				Point2D sw = grid.getWorldPoint(i  , j  , zero);
				Point2D ne = grid.getWorldPoint(i+1, j+1, zero);
				rec.setFrameFromDiagonal(sw, ne);
				if(grid.getCell(i, j).containsPole)
				 {
					Paint p = g2.getPaint();
					g2.setPaint(new Color(255, 255, 0, 50));
					g2.fill(rec);
					g2.setPaint(p);
				 }
				g2.draw(rec);
			 }
	 }

	private final boolean SHORT_CIRCUIT_DRAWS;

	// Actual draw line implementation, simply iterates over every
	// grid cell and draws the line to it.
	private void drawLineImpl(Point2D sa, Point2D sb)
	 {
		HVector a = proj_spatial_toHVector(sa);
		HVector b = proj_spatial_toHVector(sb);

		final CellRange range = getCellRangeFromLine(sa, sb, a, b);
		if(range == null)
			return;

		for(int i=range.minX; i<=range.maxX; i++)
			for(int j=range.minY; j<=range.maxY; j++)
			 {
				GridDataStore.Cell cell = grid.getCell(i, j);
				if(cell.dead)
					return;

				// Get "local" copies of the segment endpoints, for
				// clipping to this cell.
				HVector aa = (HVector) a.clone();
				HVector bb = (HVector) b.clone();

				// If the cell doesn't clip the line totally, draw it
				if(clipLineToQuad(aa, bb, cell.chain, cell.norms))
				 {
					// Convert aa/bb to quadrilateral coordinates,
					// which span [0:1] within the quadrilateral.
					Point2D wa = new Point2D.Double();
					Point2D wb = new Point2D.Double();
					uninterpolate(aa, cell, wa);
					uninterpolate(bb, cell, wb);

					// Convert the quadrilateral coordinates (in range
					// [0:1]) to actual world coordinates.
					Point2D clippedA = grid.getWorldPoint(i, j, wa);
					Point2D clippedB = grid.getWorldPoint(i, j, wb);

					// Phew! DRAW THE DAMNED THING! Use the "real" g2
					// that we're wrapping.
					g2.draw(new Line2D.Double(clippedA, clippedB));
					if(SHORT_CIRCUIT_DRAWS)
						return;
				 }
			 }
	 }
	 

    public void draw(Shape s)
     {
		if(s instanceof Line2D)
		 {
			Line2D line = (Line2D) s;
			drawLineImpl(line.getP1(), line.getP2());
		 }
		else
		 {
			PathIterator iter = s.getPathIterator(null);
			double[] coords = new double[6];
			Point2D last = new Point2D.Double();
			Point2D start = new Point2D.Double();
			while(!iter.isDone())
			 {
				switch(iter.currentSegment(coords))
				 {
				 case PathIterator.SEG_MOVETO:
					start.setLocation(coords[0],
									  coords[1]);
					break;

				 case PathIterator.SEG_QUADTO:
				 case PathIterator.SEG_CUBICTO:
					log.aprintln("PROGRAMMER: UNHANDLED LINE SEGMENT TYPE!");
				 case PathIterator.SEG_LINETO:
					drawLineImpl(last, new Point2D.Double(coords[0],
														  coords[1]));
					break;

				 case PathIterator.SEG_CLOSE:
					drawLineImpl(last, start);
					break;

				 default:
					log.aprintln("PROGRAMMER: UNKNOWN LINE SEGMENT TYPE!");
					break;
				 }
				last.setLocation(coords[0],
								 coords[1]);
				iter.next();
			 }
		 }
     }

    public void fill(Shape s)
     {
		draw(s);
     }

    public Graphics create()
     {
		try
		 {
			SpatialGraphicsSpOb copy = (SpatialGraphicsSpOb) clone();
			copy.g2 = (Graphics2D) g2.create();
			return  copy;
		 }
		catch(CloneNotSupportedException e)
		 {
			log.aprintln("WAY STRANGE: Clone failed");
			log.aprint(e);
			return  null;
		 }
     }

    public void drawLine(int x1, int y1, int x2, int y2)
     {
		drawLineImpl(new Point(x1, y1),
					 new Point(x2, y2) );
     }

///////////////////////////////////////////////////////////////////////////////
// FUNCTIONS WE CALL BUT WHOSE DEFAULT PROXY IMPLEMENTATIONS WILL SUFFICE
///////////////////////////////////////////////////////////////////////////////

    public void setPaint(Paint paint)
     {
        g2.setPaint(paint);
     }

    public void setStroke(Stroke s)
     {
        g2.setStroke(s);
     }

    public void setColor(Color c)
     {
        g2.setColor(c);
     }

    public Color getColor()
     {
        return  g2.getColor();
     }

    public void setPaintMode()
     {
        g2.setPaintMode();
     }

    public void setXORMode(Color c1)
     {
        g2.setXORMode(c1);
     }

    public Composite getComposite()
    {
        return  g2.getComposite();
    }

    public void setComposite(Composite comp)
    {
        g2.setComposite(comp);
    }
    
	public Font getFont()
	 {
		return  currentFont;
	 }

    public void setFont(Font font)
     {
		currentFont = font;
        g2.setFont(font.deriveFont(fontTransform));
     }

    public void drawString(String str, int x, int y)
     {
		if(currentFont == null)
			setFont(g2.getFont());
		drawString(str, (float) x, (float) y);
     }

    public void drawString(String s, float x, float y)
     {
		if(currentFont == null)
			setFont(g2.getFont());
		Point2D[] wps = spatialToWorlds(new Point2D.Float(x, y));
		for(int i=0; i<wps.length; i++)
			g2.drawString(s, (float) wps[i].getX(), (float) wps[i].getY());
     }

	private static final int sign(double x)
	 {
		if(x < 0)
			return  -1;
		if(x > 0)
			return  +1;
		return  0;
	 }

///////////////////////////////////////////////////////////////////////////////
// TEMPORARY IMPLEMENTATIONS OF FUNCTIONS THAT BELONG IN THE PROJECTION OBJECT
///////////////////////////////////////////////////////////////////////////////

	/**
	 ** Converted from EXT_interpolate() in interpolate.C.
	 **
	 ** <p>The <code>offset</code> parameter is interpreted as x=eperc
	 ** and y=nperc. See interpolate.C for what this function does and
	 ** how it uses its arguments.
	 **/
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

	/**
	 ** Converted from EXT_uninterpolate() in interpolate.C. Still
	 ** does the same thing, just calculates it with cached cross
	 ** products.
	 **
	 ** <p>The return value is interpreted as x=eperc and y=nperc. See
	 ** interpolate.C for what this function does and how it uses its
	 ** arguments.
	 **/
	private static void uninterpolate(HVector pt,
									  GridDataStore.Cell cell,
									  Point2D result)
	 {
		HVector pt_we = cell.wePlane.cross(pt).unit();
		HVector pt_sn = cell.snPlane.cross(pt).unit();

		double x = pt_we.separation(cell.w) / cell.wePlaneSpan;
		double y = pt_sn.separation(cell.s) / cell.snPlaneSpan;

		result.setLocation(x, y);

//		log.println(cell);

		if(Double.isNaN(y))
			log.aprintln("NAN RESULT... STRANGENESS FOR MICHAEL!");
	 }

	HVector proj_world_toHVector(Point2D w)
	 {
		Point cell = grid.getCellPoint(w);
		HVector sw = grid.getGridData(cell.x  , cell.y  );
		HVector se = grid.getGridData(cell.x+1, cell.y  );
		HVector ne = grid.getGridData(cell.x+1, cell.y+1);
		HVector nw = grid.getGridData(cell.x  , cell.y+1);

		Point2D offset = grid.getCellOffset(w, cell);
		HVector result = interpolate(offset, sw, se, ne, nw);

		return  result;
	 }

	private HVector proj_spatial_toHVector(Point2D s)
	 {
		return  ProjObj.marsll2vector(Math.toRadians(s.getX()) ,
									  Math.toRadians(s.getY()) );
	 }

	/**
	 ** Returns true if the quadrilateral described by the given
	 ** normals contains the point.
	 **/
	private static boolean isPointInQuad(HVector a, HVector[] norms)
	 {
		for(int i=0; i<4; i++)
			if(norms[i].dot(a) < 0)
				return  false;
		return  true;
	 }

	public Point2D[] spatialToWorlds(Point2D s)
	 {
		ArrayList worldPoints = new ArrayList();

		HVector a = proj_spatial_toHVector(s);

		Rectangle superRange = new Rectangle(0, minGridY,
											 1, maxGridY-minGridY);
		for(int i=minGridX; i<maxGridX; i++)
		 {
			superRange.x = i;
			GridDataStore.Cell superCell = grid.getSuperCell(superRange);

			// If the supercell contains the point, narrow it down
			// cell-by-cell.
			if(isPointInQuad(a, superCell.norms))
				for(int j=minGridY; j<maxGridY; j++)
				 {
					GridDataStore.Cell cell = grid.getCell(i, j);

					// If the cell contains the point, include it in
					// our return list.
					if(isPointInQuad(a, cell.norms))
					 {
						// Convert aa to quadrilateral coordinates,
						// which span [0:1] within the quadrilateral.
						Point2D qa = new Point2D.Double();
						uninterpolate(a, cell, qa);

						// Convert the quadrilateral coordinates (in range
						// [0:1]) to actual world coordinates.
						Point2D wa = grid.getWorldPoint(i, j, qa);

						// Phew! FINALLY, add the damn thing to the
						// return list. It's only possible get hit one
						// cell out of a supercell, so we can stop
						// searching within this supercell.
						worldPoints.add(wa);
						break;
					 }
				 }
		 }

		return
			(Point2D[]) worldPoints.toArray(new Point2D[worldPoints.size()]);
	 }

	/////////////////////////////////////////////////////////////////////////
	// ALL OF BELOW: Drivers to test with
	/////////////////////////////////////////////////////////////////////////


	private static HVector readvec(BufferedReader fin)
	 {
		return  HVector.read(fin).unit();
	 }


	// MAIN DRIVER: Calls all the others
	public static void main(String[] av)
	 throws Throwable
	 {
		DebugLog.readFile(".debugrc");

		int newlen = Math.max(av.length-1, 0);
		String[] av2 = new String[newlen];
		if(newlen != 0)
			System.arraycopy(av, 1, av2, 0, newlen);

		if(av.length != 0)
			av[0] = av[0].toLowerCase().intern();

		if(av.length == 0  ||  av[0] == "main")
			main_main(av2);

		else if(av[0] == "clipline")
			main_clipLine(av2);

		else if(av[0] == "projlinetoquad")
			main_clipLineToQuad(av2);

		else if(av[0] == "intersectgseg")
			main_intersectGSeg(av2);

		else if(av[0] == "uninterpolate")
			main_uninterpolate(av2);

		else if(av[0] == "separation")
			main_separation(av2);

		else if(av[0] == "profile")
			main_profile(av2);

		else
			System.err.println("Unknown routine: " + av[0]);
	 }




	static void main_main(String[] av)
	 {
		Graphics2D callback =
			new Graphics2DAdapter()
			 {
				public void draw(Shape s)
				 {
					if(s instanceof Line2D)
					 {
						Line2D line = (Line2D) s;
						System.err.println("# segment draw");
						double x = -22656000;
						System.err.println(line.getX1()-x +"\t"+ line.getY1());
						System.err.println(line.getX2()-x +"\t"+ line.getY2());
						System.err.print('\n');
					 }
					else
						System.out.println("WOAH: " + s);
				 }
			 };

		GridDataStore grid =
			new GridDataStore(Config.get("groundtrack") + "?format=c&",
							  -22656000, 0,
							  10, 1);

		Graphics2D g2 = new SpatialGraphicsSpOb(callback, grid,
												new Rectangle(0, 1, 100, 3));

		g2.draw(new Line2D.Double(-22655995, 1.5,
								  -22655505, 3.5 ));
	 }


	static void main_clipLine(String[] av)
	 throws IOException
	 {
		BufferedReader fin =
			new BufferedReader(new InputStreamReader(System.in));

		while(true)
		 {
			HVector a = readvec(fin);
			HVector b = readvec(fin);
			HVector c1 = readvec(fin);
			HVector c2 = readvec(fin);

// BROKEN: signature changed
//			System.out.println(clipLine(a, b, c1, c2));
			System.out.println(a);
			System.out.println(b);
			System.out.println("");
		 }
	 }

	static int clipLine2(HVector a, HVector b, HVector c1, HVector c2)
	 {
		System.out.println("--- clipping with ---");
		System.out.println(c1);
		System.out.println(c2);

		int result = 0;//clipLine(a, b, c1, c2); <-- BROKEN, signature changed

		if(result <= 0)
			System.out.println("--- FAILED result = " + result + " ---");
		else
		 {
			System.out.println("--- result = " + result + " ---");
			System.out.println(a);
			System.out.println(b);
		 }

		System.out.println("");

		return  result;
	 }

	static void main_clipLineToQuad(String[] av)
	 {
		BufferedReader fin =
			new BufferedReader(new InputStreamReader(System.in));

		while(true)
		 {
			HVector sw = readvec(fin);
			HVector se = readvec(fin);
			HVector ne = readvec(fin);
			HVector nw = readvec(fin);
			HVector a = readvec(fin);
			HVector b = readvec(fin);

/***************** BROKEN, clipLine interface has changed
				   boolean done = false;

				   if(!done) done = clipLine2(a, b, sw, se);
				   if(!done) done = clipLine2(a, b, se, ne);
				   if(!done) done = clipLine2(a, b, ne, nw);
				   if(!done) done = clipLine2(a, b, nw, sw);
				   if(!done) System.out.println("SUCCESS");
				   System.out.println("");
*/
		 }
	 }

	static void main_intersectGSeg(String[] av)
	 {
		BufferedReader fin =
			new BufferedReader(new InputStreamReader(System.in));

		while(true)
			System.out.println(HVector.intersectGSeg(readvec(fin),
													 readvec(fin),
													 readvec(fin),
													 readvec(fin) )
				);

	 }

	static void main_uninterpolate(String[] av)
	 {
		BufferedReader fin = 
			new BufferedReader(new InputStreamReader(System.in));

		Point2D result = new Point2D.Double();

		GridDataStore.Cell cell = new GridDataStore.Cell(readvec(fin),
														 readvec(fin),
														 readvec(fin),
														 readvec(fin));

		uninterpolate(readvec(fin),
					  cell,
					  result);

		System.out.println(result.getX() + "\t" +
						   result.getY());
	 }

	static void main_separation(String[] av)
	 {
		BufferedReader fin = 
			new BufferedReader(new InputStreamReader(System.in));

		HVector a;
		HVector b;

		while(true)
		 {
			a = readvec(fin);
			b = readvec(fin);
			log.aprintln(Math.toDegrees(a.separation(b)));
		 }
	 }

	static double xDelta = 5;
	static double yDelta = 5;
	static double xBase = 5;
	static double yBase = 5;
	static final Point2D getWorldPoint(int i, int j, Point2D offset)
	 {
		return  new Point2D.Double((i + offset.getX()) * xDelta + xBase,
								   (j + offset.getY()) * yDelta + yBase);
	 }

	static int i = 1;
	static int j = 2;
	static HVector a = new HVector(i, 2, 2);
	static HVector b = new HVector(i, 3, 3);
	static HVector sw = new HVector(i, 0, 0);
	static HVector se = new HVector(i, 0, 5);
	static HVector ne = new HVector(i, 5, 5);
	static HVector nw = new HVector(i, 5, 0);

	static void profile()
	 {
		HVector aa = (HVector) a.clone();
		HVector bb = (HVector) b.clone();

// BROKEN - signature changed
//		if(clipLineToQuad(aa, bb, sw, se, ne, nw))
		 {
			// Convert aa/bb to quadrilateral coordinates,
			// which span [0:1] within the quadrilateral.
			Point2D wa = new Point2D.Double();
			Point2D wb = new Point2D.Double();
//			uninterpolate(aa, sw, se, ne, nw, wa);
//			uninterpolate(bb, sw, se, ne, nw, wb);

			// Convert the quadrilateral coordinates (in range
			// [0:1]) to actual world coordinates.
			Point2D clippedA = getWorldPoint(i, j, wa);
			Point2D clippedB = getWorldPoint(i, j, wb);

			// Phew! DRAW THE DAMNED THING! Use the "real" g2
			// that we're wrapping.
//			g2.draw(new Line2D.Double(clippedA, clippedB));
		 }
	 }

	public static void main_profile(String[] av)
	 {
		final int count = Integer.parseInt(av[0]);
		while(true)
		 {
			long startTime = System.currentTimeMillis();
			++i;
			++j;
			++sw.x;
			++se.x;
			++ne.x;
			++nw.x;
			for(int i=0; i<count; i++)
				profile();
			long stopTime = System.currentTimeMillis();
			long time = stopTime - startTime;
			System.out.println(time + " / " +
							   count + " = " +
							   time/count + " ms");
		 }
	 }

	public void dumpGridData(OutputStream _out)
	 {
		PrintStream out = new PrintStream(_out);
		out.println("#" + (maxGridX - minGridX));
		out.println("#" + (maxGridY - minGridY));
		for(int i=minGridX; i<maxGridX; i++)
			for(int j=minGridY; j<maxGridY; j++)
			 {
				GridDataStore.Cell cell = grid.getCell(i, j);
				out.println("");
				out.println(cell.sw);
				out.println(cell.se);
				out.println(cell.ne);
				out.println(cell.nw);
				out.println(cell.sw);
			 }
		out.close();
	 }
 }
