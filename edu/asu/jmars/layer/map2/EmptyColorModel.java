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


package edu.asu.jmars.layer.map2;

import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;

/** Returns transparent black for all pixels */
public final class EmptyColorModel extends ColorModel {
	public EmptyColorModel() {
		super(32);
	}

	public boolean isCompatibleRaster(Raster raster) {
		return true;
	}

	public boolean isCompatibleSampleModel(SampleModel sm) {
		return true;
	}

	public int getAlpha(int pixel) {
		return 0;
	}

	public int getBlue(int pixel) {
		return 0;
	}

	public int getGreen(int pixel) {
		return 0;
	}

	public int getRed(int pixel) {
		return 0;
	}
}
