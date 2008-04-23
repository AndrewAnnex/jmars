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


/*
 * @(#)PointLayout.java	1.02 2 Jan 2002
 *
 * Copyright John Redmond (John.Redmond@mq.edu.au).
 * 
 * This software is freely available for commercial and non-commercial purposes.
 * Acknowledgement of its source would be appreciated, but is not required.
 * 
 */

package edu.asu.jmars.swing;
// package au.com.pegasustech.demos.layout;

/**
 * Single cell layout: 
 * For information on usage, see <a href="http://www.javaworld.com/javaworld/javatips/jw-javatip129.html">
http://www.javaworld.com/javaworld/javatips/jw-javatip129.html</a>
 */
public class PointLayout extends SGLayout {

  public PointLayout() {
    super(1, 1);
  }

  public PointLayout(int hAlignment, int vAlignment) {
    super(1, 1, hAlignment, vAlignment, 0, 0);
  }

  public void setAlignment(int h, int v) {
    super.setAlignment(h, v, 0, 0);
  }
}
