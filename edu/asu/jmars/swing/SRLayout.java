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
 * @(#)SRLayout.java	1.02 2 Jan 2002
 *
 * Copyright John Redmond (John.Redmond@mq.edu.au).
 * 
 * This software is freely available for commercial and non-commercial purposes.
 * Acknowledgement of its source would be appreciated, but is not required.
 *
 */

package edu.asu.jmars.swing;
// package au.com.pegasustech.demos.layout;

//import java.util.*;
import java.awt.*;

/**
 * Scaled Row Layout: 
 * For information on usage, see <a href="http://www.javaworld.com/javaworld/javatips/jw-javatip129.html">
http://www.javaworld.com/javaworld/javatips/jw-javatip129.html</a>
 */
public class SRLayout extends SGLayout {

  /**
   * Creates a layout with the specified number of columns
   * and default alignments and gaps.
   * <p>
   * horizontal gaps are set to 0 and
   * X- and Y-alignments are set to FILL.
   * @param     cols   the columns.
   */
  public SRLayout(int columns) {
    super(1, columns);
  }

  /**
   * Creates a layout with the specified number of columns
   * and specified gap.
   * <p>
   * horizontal and vertical gaps are set to 0 and
   * X- and Y-alignments are set to FILL.
   * @param     cols   the columns.
   * @param     gap   the horizontal gap, in pixels.
   */
  public SRLayout(int columns, int gap) {
    super(1, columns, FILL, FILL, gap, 0);
  }

  /**
   * Creates a layout with the specified number of columns
   * and specified alignments and gaps.
   * <p>
   * horizontal and vertical gaps are set to 0 and
   * X- and Y-alignments are set to FILL.
   * @param     cols   the columns.
   * @param     gap   the horizontal gap, in pixels.
   */
  public SRLayout(int columns, int hAlignment, int vAlignment, int gap) {
    super(1, columns, hAlignment, vAlignment, gap, 0);
  }

  /**
   * Set up alignment for a specific cell.
   * <p>
   * @param     index the cell number.
   * @param     hAlignment  the X-alignment for the cell.
   * @param     vAlignment  the Y-alignment for the cell.
   */
  public void setAlignment(int column, int hAlignment, int vAlignment) {
    setColumnAlignment(column, hAlignment, vAlignment);
  }

  /**
   * Set up scale value for a specific cell.
   * <p>
   * @param     index the cell number.
   * @param     scale  the scale value for the column.
   */
  public void setScale(int column, double scale) {
    setColumnScale(column, scale);
  }
}
