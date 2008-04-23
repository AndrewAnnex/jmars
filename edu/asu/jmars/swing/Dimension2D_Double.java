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

import java.awt.geom.Dimension2D;
import java.io.*;


/**
 ** For some reason, there is an abstract Dimension2D but no
 ** Dimension2D.Double or Dimension2D.Float, which is very
 ** annoying. Here's an implementation.
 **/
public class Dimension2D_Double extends Dimension2D implements Serializable
 {
	public double width;
	public double height;

   private void writeObject(ObjectOutputStream s) throws IOException {
      s.defaultWriteObject();
      s.writeObject(new Double(width));
      s.writeObject(new Double(height));
   }

   private void readObject(ObjectInputStream s) throws IOException  {
      try{
         s.defaultReadObject();
			Double tmp;
			tmp=(Double)s.readObject();
			width=tmp.doubleValue();
			tmp=(Double)s.readObject();
			height=tmp.doubleValue();
      }
      catch (ClassNotFoundException e)
      {
      }
   }



	public Dimension2D_Double()
	 {
		this(0,0);
	 }

	public Dimension2D_Double(Dimension2D old)
	 {
		this(old.getWidth(), old.getHeight());
	 }

	public Dimension2D_Double(double w, double h)
	 {
		width = w;
		height = h;
	 }

	public double getWidth()
	 {
		return  width;
	 }

	public double getHeight()
	 {
		return  height;
	 }

	public void setSize(double w, double h)
	 {
		width = w;
		height = h;
	 }

	public int hashCode()
	 {
		return new Double(width).hashCode() ^ new Double(height).hashCode();
	 }

	public boolean equals(Object obj)
	 {
		if(obj == null)
			return  false;
		if(obj.getClass() != getClass())
			return  false;
		Dimension2D_Double d = (Dimension2D_Double) obj;
		return  this.width  == d.width
			&&  this.height == d.height;
	 }

	public String toString()
	 {
		return  "Dimension2D[" + width + "," + height + "]";
	 }
 }
