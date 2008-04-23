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
import java.awt.color.ColorSpace;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RescaleOp;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;

import edu.asu.jmars.util.Util;

public class GrayRescaleToByteOp implements BufferedImageOp {
	float scaleFactor;
	float offset;

	public GrayRescaleToByteOp(float scaleFactor, float offset) {
		this.scaleFactor = scaleFactor;
		this.offset = offset;
	}

	public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
		int w = src.getWidth();
		int h = src.getHeight();
		
		SampleModel outModel = new BandedSampleModel(DataBuffer.TYPE_BYTE, w, h, destCM.getNumComponents());
		WritableRaster outRaster = Raster.createWritableRaster(outModel, null);
		BufferedImage outImage = new BufferedImage(destCM, outRaster, destCM.isAlphaPremultiplied(), null);
		
		return outImage;
	}

	public BufferedImage filter(BufferedImage src, BufferedImage dest) {

		int w = src.getWidth();
		int h = src.getHeight();
		ColorModel cm = src.getColorModel();
		
		if (!((cm instanceof DirectColorModel) || (cm instanceof ComponentColorModel)))
			throw new IllegalArgumentException("Unsupported color model :"+cm.getClass().getName()+".");

		ColorModel destCM;
		if (dest == null){
			destCM = new ComponentColorModel(
					Util.getLinearGrayColorSpace(), cm.hasAlpha(), false,
					cm.hasAlpha()? ColorModel.TRANSLUCENT: ColorModel.OPAQUE, DataBuffer.TYPE_BYTE);
			dest = createCompatibleDestImage(src, destCM);
		}
		else {
			destCM = dest.getColorModel();
		}

		Raster srcRaster = src.getRaster();
		int srcTransferType = srcRaster.getTransferType();

		switch(srcTransferType){
		case DataBuffer.TYPE_BYTE:
		case DataBuffer.TYPE_SHORT:
		case DataBuffer.TYPE_USHORT:
		case DataBuffer.TYPE_INT:
			// Do the rescale the fast way
			RescaleOp rescaleOp = new RescaleOp(scaleFactor, offset, getRenderingHints());
			rescaleOp.filter(
					src.getRaster().createWritableChild(0, 0, w, h, 0, 0, getBands(cm.getNumColorComponents())),
					dest.getRaster().createWritableChild(0, 0, w, h, 0, 0, getBands(destCM.getNumColorComponents())));

			// TODO This getAlphaRaster() only takes care of the situation when we have
			// either a DirectColorModel or a ComponentColorModel, other ColorModels are not
			// handled. This comment applies to all other Stages where alpha is handled in
			// this fashion.
			if (src.getAlphaRaster() != null && dest.getAlphaRaster() != null){
				// If alpha is present in both source and target, scale it appropriately.
				// TODO This may not be correct alpha scale for short or int
				double alphaScale = 255.0 / (Math.pow(2.0, cm.getComponentSize(cm.getNumComponents()-1))-1);

				RescaleOp alphaRescaleOp = new RescaleOp((float)alphaScale, 0.0f, null);
				alphaRescaleOp.filter(src.getAlphaRaster(), dest.getAlphaRaster());
			}
			else if (dest.getAlphaRaster() != null){
				// If alpha not there in source, set the target alpha to opaque.
				fillRaster(dest.getAlphaRaster(), 255);
			}
			break;

		case DataBuffer.TYPE_FLOAT:
		case DataBuffer.TYPE_DOUBLE:
			// Do the rescale the slow way
			double[] srcPixel = new double[cm.getNumColorComponents()];
			int[] destPixel = new int[cm.getNumColorComponents()];
			double destPixelValue;
			int maxZ = Math.min(srcPixel.length, destPixel.length);
			WritableRaster destRaster = dest.getRaster();
			for(int j=0; j<h; j++){
				for(int i=0; i<w; i++){
					srcRaster.getPixel(i, j, srcPixel);
					for(int z=0; z<maxZ; z++){
						destPixelValue = (srcPixel[z] * scaleFactor + offset);
						if (destPixelValue < 0)
							destPixelValue = 0;
						else if (destPixelValue > 255)
							destPixelValue = 255;
						destPixel[z] = (int)destPixelValue;
					}
					destRaster.setPixel(i, j, destPixel);
				}
			}

			if (dest.getAlphaRaster() != null){
				// If alpha not there in the source, set the target alpha to opaque.
				fillRaster(dest.getAlphaRaster(), 255);
			}

			break;
		default:
			throw new IllegalArgumentException("Unhandled src image data (transfer) type "+srcTransferType);
		}

		return dest;
	}

	private void fillRaster(WritableRaster raster, int val){
		int w = raster.getWidth();
		int h = raster.getHeight();
		int[] alpha = new int[w*h];
		Arrays.fill(alpha, val);
		raster.setPixels(0, 0, w, h, alpha);
	}

	private int[] getBands(int count) {
		int[] bands = new int[count];
		for (int i = 0; i < bands.length; i++)
			bands[i] = i;
		return bands;
	}
	
	public Rectangle2D getBounds2D(BufferedImage src) {
		return src.getData().getBounds();
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
