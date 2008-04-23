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


/**
 * gets information about the altitude of the scene.  This info is taken from the Numback layer.
 *
 * @author: James Winburn MSFF-ASU 11/04
 */
package edu.asu.jmars.layer.threed;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;

import edu.asu.jmars.util.DebugLog;

public class Elevation {
	private static DebugLog log = DebugLog.instance();
	protected int row = 0;
	protected int col = 0;
	protected int[][] map = null;
	private double sigmaValue;
	private double meanZ;

	/**
	 * build the elevation from a Raster.  This is used by the layer when NumBack layer
	 * data is used for altitude information.
	 */
	public Elevation(Raster data) {
		if (data == null) {
			log.aprintln("Hey! There's no Elevation data!");
			return;
		}

		col = data.getWidth();
		row = data.getHeight();

		sigmaValue = 0.0;
		int[] buffer = data.getPixels(0, 0, col, row, (int[]) null);

		map = null;
		map = new int[row][col];
		for (int r = 0; r < row; r++) {
			for (int c = 0; c < col; c++) {
				int value = buffer[r * col + c];
				map[row - r - 1][c] = value;
				sigmaValue += value;
			}
		}

		meanZ = sigmaValue / (double) (row * col);
	}

	/**
	 * build the elevation from a BufferedImage.  This is used by the layer when a WMS map
	 * in a separate layer is used for altitude information.
	 */
	public Elevation(BufferedImage image) {
		if (image == null) {
			log.aprintln("Hey! There's no Elevation data!");
			return;
		}

		sigmaValue = 0.0;
		row = image.getHeight();
		col = image.getWidth();
		map = null;
		map = new int[row][col];
		ColorModel cm = image.getColorModel();
		for (int y = 0; y < row; y++) {
			for (int x = 0; x < col; x++) {
				int pixel = image.getRGB(x, row - y - 1);
				int value = cm.getRed(pixel);
				map[y][x] = value;
				sigmaValue += value;
			}
		}
		meanZ = sigmaValue / (double) (row * col);
	}

	/**
	 * returns the altitude of the scene as a 2D int array.
	 */
	public int[][] getPixelArrayInt() {
		return map;
	}

	/**
	 * returns the width of the scene.
	 */
	public int getWidth() {
		return col;
	}

	/**
	 * returns the height of the scene.
	 */
	public int getHeight() {
		return row;
	}

	/** 
	 * returns the average altitude of the scene.
	 */
	public float getMean() {
		return (float) meanZ;
	}
}
