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

import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BandedSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RasterOp;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

public class BinRasterOp implements RasterOp {
	private final double base;
	private final double step;
	
	public BinRasterOp(double base, double step){
		this.base = base;
		this.step = step;
	}

	public WritableRaster createCompatibleDestRaster(Raster src) {
		SampleModel outSm = new BandedSampleModel(DataBuffer.TYPE_FLOAT, src.getWidth(), src.getHeight(), src.getNumBands());
		return WritableRaster.createWritableRaster(outSm, null);
	}

	public WritableRaster filter(Raster src, WritableRaster dest) {
		if (dest == null)
			dest = createCompatibleDestRaster(src);

		int w = src.getWidth();
		int h = src.getHeight();
		
		double[] dArray = null;
		
		for(int y=0; y<h; y++){
			for(int x=0; x<w; x++){
				dArray = src.getPixel(x, y, (double[])dArray);
				for(int z=0; z<dArray.length; z++)
					dArray[z] = (Math.rint((dArray[z] - base)/step)) * step;
				dest.setPixel(x, y, dArray);
			}
		}
		
		return dest;
	}

	public Rectangle2D getBounds2D(Raster src) {
		return src.getBounds();
	}

	public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
		if (dstPt == null)
			dstPt = new Point2D.Double();
		
		dstPt.setLocation(srcPt);
		
		return srcPt;
	}

	public RenderingHints getRenderingHints() {
		return null;
	}

}
