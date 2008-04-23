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


package edu.asu.jmars.swing;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * A serializable version of Rectangle2D.
 */
public class SerializableRectangle2D extends Rectangle2D implements Cloneable, Serializable {
	private static final long serialVersionUID = 1L;

	private Rectangle2D rect;
	
	public SerializableRectangle2D() {
		rect = new Rectangle2D.Double();
	}

	public SerializableRectangle2D(Rectangle2D rect) {
		this();
		this.rect.setFrame(rect);
	}

	public SerializableRectangle2D createIntersection(Rectangle2D r) {
		return new SerializableRectangle2D(rect.createIntersection(r));
	}

	public SerializableRectangle2D createUnion(Rectangle2D r) {
		return new SerializableRectangle2D(rect.createUnion(r));
	}

	public int outcode(double x, double y) {
		return rect.outcode(x, y);
	}

	public void setRect(double x, double y, double w, double h) {
		rect.setRect(x, y, w, h);
	}

	public double getHeight() {
		return rect.getHeight();
	}

	public double getWidth() {
		return rect.getWidth();
	}

	public double getX() {
		return rect.getX();
	}

	public double getY() {
		return rect.getY();
	}

	public boolean isEmpty() {
		return rect.isEmpty();
	}
	
	public Object clone() {
		SerializableRectangle2D r = (SerializableRectangle2D)super.clone();
		r.rect = (Rectangle2D)r.rect.clone();
		return r;
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeDouble(rect.getMinX());
		out.writeDouble(rect.getMinY());
		out.writeDouble(rect.getWidth());
		out.writeDouble(rect.getHeight());
	}
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		rect = new Rectangle2D.Double(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble());
	}
}
