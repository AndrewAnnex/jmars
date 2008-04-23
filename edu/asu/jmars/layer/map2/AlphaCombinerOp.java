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

import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.Raster;
import java.awt.image.RasterOp;
import java.awt.image.WritableRaster;

/**
 * Alpha combiner RasterOp for the use of CompositeStages such as RGB and HSV.
 * It is used to generate alpha raster given a three-band input alpha raster.
 * Target pixel is set to zero if any of the input band values at the corresponding
 * source pixel is zero. Otherwise, it is a linear weighting average of the alphas
 * of all bands.
 */
public class AlphaCombinerOp implements RasterOp {

	/**
	 * Alpha-combine bands of a normalized alpha-pixel to a normalized
	 * alpha value.
	 * @param normPixel Alpha-pixel (3-bands) with each band's value between 0 and 1.
	 * @return Combined alpha value between 0 and 1.
	 */
	public static float alphaCombine(float[] normPixel){
		float mul = 1;
		float outAlpha = 0;
		for(int d=0; d < normPixel.length; d++){
			mul *= normPixel[d];
			outAlpha += normPixel[d] / normPixel.length;
		}
		
		return outAlpha * (mul > 0? 1: 0);
	}
	
	/**
	 * Integer counterpart of {@link #alphaCombine(float[])}.
	 * @param pixel Alpha-pixel (3-bands) with each band's value between 0 and 255.
	 * @return Combined alpha value between 0 and 255.
	 */
	public static int alphaCombine(int[] pixel){
		int mul = 1;
		float outAlpha = 0;
		for(int d=0; d < pixel.length; d++){
			mul *= pixel[d];
			outAlpha += ((float)pixel[d])/(float)pixel.length; 
		}
		
		return (int)outAlpha * (mul > 0? 1: 0);
	}
	
	public AlphaCombinerOp(){
	}
	
	public WritableRaster createCompatibleDestRaster(Raster src) {
		return src.createChild(
				src.getMinX(), src.getMinY(), src.getWidth(), src.getHeight(),
				src.getMinX(), src.getMinY(), new int[]{ 0 })
				.createCompatibleWritableRaster();
	}

	public WritableRaster filter(Raster src, WritableRaster dst) {
		if (dst == null)
			dst = createCompatibleDestRaster(src);
		
		int w = src.getWidth();
		int h = src.getHeight();
        int[] srcPixel = null;
        int[] dstPixel = new int[1];
        
        for(int r=0; r<h; r++){
        	for(int c=0; c<w; c++){
        		srcPixel = src.getPixel(c+src.getMinX(), r+src.getMinY(), srcPixel);
        		dstPixel[0] = alphaCombine(srcPixel);
        		dst.setPixel(c+dst.getMinX(), r+dst.getMinY(), dstPixel);
        	}
        }
        
        return dst;
	}

	public Rectangle2D getBounds2D(Raster src) {
		return src.getBounds();
	}

	public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
		if (dstPt == null)
			dstPt = new Point2D.Double();
		dstPt.setLocation(srcPt);
		
		return dstPt;
	}

	public RenderingHints getRenderingHints() {
		return null;
	}

}
