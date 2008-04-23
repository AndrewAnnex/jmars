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


package edu.asu.jmars.util;

import java.awt.*;
import java.awt.geom.*;

public class ProxyShape implements Shape
 {
    protected final Shape sh;

    public ProxyShape(Shape sh)
     {
	this.sh = sh;
     }

    public final Rectangle getBounds()
     {
	return  sh.getBounds();
     }

    public final Rectangle2D getBounds2D()
     {
	return  sh.getBounds2D();
     }

    public final boolean contains(double x, double y)
     {
	return  sh.contains(x, y);
     }

    public final boolean contains(Point2D p)
     {
	return  sh.contains(p);
     }

    public boolean intersects(double x, double y, double w, double h)
     {
	return  sh.intersects(x, y, w, h);
     }

    public boolean intersects(Rectangle2D r)
     {
	return  sh.intersects(r);
     }

    public final boolean contains(double x, double y, double w, double h)
     {
	return  sh.contains(x, y, w, h);
     }

    public final boolean contains(Rectangle2D r)
     {
	return  sh.contains(r);
     }

    public final PathIterator getPathIterator(AffineTransform at)
     {
	return  sh.getPathIterator(at);
     }

    public final PathIterator getPathIterator(AffineTransform at,
					      double flatness)
     {
	return  sh.getPathIterator(at, flatness);
     }
 }
