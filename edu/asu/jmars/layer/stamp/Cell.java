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


package edu.asu.jmars.layer.stamp;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.PrintWriter;
import java.io.StringWriter;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj;
import edu.asu.jmars.ProjObj.Projection_OC;
import edu.asu.jmars.graphics.SpatialGraphicsSpOb;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.Util;

/**
 * Copied and trimmed from GridDataStore.Cell
 **/
public final class Cell
 {
	// What order were they just specified in?
	public final boolean clockwise;
	// All of the border points, as a 5-point closed quadrilateral chain
	public final HVector[] chain;

	// The normals of each cell wall, pointing inwards.
	public final HVector s;
	public final HVector n;
	public final HVector e;
	public final HVector w;

	double magw; // magnitude of w
	double mags; // magnitude of s
	
	// Interpolation axes
	public final HVector wePlane;
	public final HVector snPlane;
	// Interpolation data values
	public final double wePlaneSpan;
	public final double snPlaneSpan;
	
	public final Projection_OC proj;

	public Point2D uninterpolate(HVector pt, Point2D unitPt)
	 {
		if(unitPt == null)
			unitPt = new Point2D.Double();

		HVector pt_we = wePlane.cross(pt).unit();
		HVector pt_sn = snPlane.cross(pt).unit();

		double x = pt_we.separation(w) / wePlaneSpan;
		double y = pt_sn.separation(s) / snPlaneSpan;

		if(pt.dot(w) < 0) x = -x;
		if(pt.dot(s) < 0) y = -y;

		unitPt.setLocation(x, y);
		return  unitPt;
	 }

	double wex;
	double wey;
	double wez;
	
	double snx;
	double sny;
	double snz;
	
	public final Point2D uninterpolateFast(final HVector pt, final Point2D unitPt)
	 {
		
		wex = wePlane.y * pt.z - wePlane.z * pt.y;
		wey = wePlane.z * pt.x - wePlane.x * pt.z;
		wez = wePlane.x * pt.y - wePlane.y * pt.x;

		final double wn = Math.sqrt(wex*wex + wey*wey + wez*wez);
		
		wex /= wn;
		wey /= wn;
		wez /= wn;

		double x;
		
		if(wn == 0  ||  magw == 0)
			x=0;

		double dp = wex * w.x + wey * w.y + wez * w.z;

		if(dp > 0)
		 {
			x = doStuffPos(wex, wey, wez, w.x, w.y, w.z);
		 }
		else if(dp < 0)
		 {
			x = doStuffNeg(wex, wey, wez, w.x, w.y, w.z);
		 }
		else
			x = Math.PI / 2;		
		
		snx = snPlane.y * pt.z - snPlane.z * pt.y;
		sny = snPlane.z * pt.x - snPlane.x * pt.z;
		snz = snPlane.x * pt.y - snPlane.y * pt.x;

		final double sn = Math.sqrt(snx*snx + sny*sny + snz*snz);
		
		snx /= sn;
		sny /= sn;
		snz /= sn;
				
		double y;
		
		if(sn == 0  ||  mags == 0)
			y=0;

		dp = snx * s.x + sny * s.y + snz * s.z;

		if(dp > 0)
		 {
			y = doStuffPos(snx, sny, snz, s.x, s.y, s.z);
		 }
		else if(dp < 0)
		 {
			y = doStuffNeg(snx, sny, snz, s.x, s.y, s.z);
		 }
		else
			y = Math.PI / 2;

		//
			
		x /= wePlaneSpan;
		y /= snPlaneSpan;

		if(pt.x * w.x + pt.y * w.y + pt.z * w.z < 0) x = -x;
		if(pt.x * s.x + pt.y * s.y + pt.z * s.z < 0) y = -y;

		unitPt.setLocation(x, y);
		return  unitPt;
	 }
	
	public static final double getMag(final double x, final double y, final double z) {
		return Math.sqrt(x*x + y*y + z*z);
	}
	
	public static final double doStuffPos(double x1, double y1, double z1, double x2, double y2, double z2) {
		final double xx = x1 - x2;
		final double yy = y1 - y2;
		final double zz = z1 - z2;
		final double dxp = getMag(xx, yy, zz);
		final double tmp = dxp / 2;

		return 2 * Math.atan(tmp / Math.sqrt(1 - tmp*tmp));		
	}
	
	public static final double doStuffNeg(double x1, double y1, double z1, double x2, double y2, double z2) {		 
		final double xx = x1 + x2;
		final double yy = y1 + y2;
		final double zz = z1 + z2;
		final double dxp = getMag(xx, yy, zz);
		final double tmp = dxp / 2;

		return Math.PI - 2 * Math.atan(tmp / Math.sqrt(1 - tmp*tmp));
	}
	
	
	public boolean dead;
	public Cell(HVector sw,
				HVector se,
				HVector ne,
				HVector nw, Projection_OC proj)
	 {
		this.proj = proj;
		this.dead = sw.x == 0  &&  sw.y == 0  &&  sw.z == 0;

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

		wePlane = e.cross(w).unit();
		snPlane = n.cross(s).unit();
		wePlaneSpan = Math.PI - e.separation(w);
		snPlaneSpan = Math.PI - s.separation(n);
		
		magw = Math.sqrt(w.x*w.x + w.y*w.y + w.z*w.z);
		mags = Math.sqrt(s.x*s.x + s.y*s.y + s.z*s.z);
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

	private Rectangle2D worldBounds;
	public Rectangle2D getWorldBounds()
	 {
		if(worldBounds != null)
			return  worldBounds;

		Point2D[] chainW = new Point2D[chain.length];
		for(int i=0; i<chainW.length; i++)
			chainW[i] =
				proj.convSpatialToWorld(chain[i].toLonLat(null));

		Range x = new Range();
		Range y = new Range();

		HVector up = proj.getUp();

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

		worldBounds = new Rectangle2D.Double(x.min,
											 y.min,
											 x.max-x.min,
											 y.max-y.min);
		return  worldBounds;
	 }

 }
