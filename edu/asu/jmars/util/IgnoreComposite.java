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

import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Each pixel of the SRC not transparent or equal to the ignore color will be
 * written over the corresponding pixel in DSTIN, and all transparent and ignore
 * pixels will allow DSTIN to show through.
 */
public final class IgnoreComposite implements Composite {
	private final int ignore;
	public IgnoreComposite(Color ignore) {
		this.ignore = ignore.getRGB();
	}
	private final class IgnoreCompCtx implements CompositeContext {
		private final ColorModel sourceCM;
		public IgnoreCompCtx(ColorModel source) {
			this.sourceCM = source;
		}
		public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
			Object srcPixel = src.getDataElements(0, 0, null);
			Object dstPixel = dstIn.getDataElements(0, 0, null);
			for (int i = dstOut.getWidth()-1; i >= 0; i--) {
				for (int j = dstOut.getHeight()-1; j >=0; j--) {
					try {
						src.getDataElements(i, j, srcPixel);
						if (sourceCM.getAlpha(srcPixel) == 0 || ignore == sourceCM.getRGB(srcPixel)) {
							dstIn.getDataElements(i, j, dstPixel);
							dstOut.setDataElements(i, j, dstPixel);
						} else {
							dstOut.setDataElements(i, j, srcPixel);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		public void dispose() {
		}
	}
	public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
		return new IgnoreCompCtx(srcColorModel);
	}
}

