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

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.PolyArea;
import edu.asu.jmars.util.Util;

/**
 * Holds the current data for one Pipeline of a MapChannel request.
 * 
 * The request properties are immutable.
 * 
 * The finishedArea, fuzzyArea, finished, and image properties are mutable.
 * The getDeepCopyShell() method copies all properties except image, which
 * is highly mutable. It is strongly suggested that either getImageCopy()
 * be used, and an operation work against the copy, or the calling code
 * synchronize on the result of getImage().
 */
public final class MapData {
	private static DebugLog log = DebugLog.instance();
	
	private final MapRequest request;
	private Area finishedArea;
	private Area fuzzyArea;
	private BufferedImage image;
	private boolean finished;
	
	public MapData(MapRequest request) {
		this.request = request;
		finishedArea = new Area();
		fuzzyArea = new Area();
		image = null;
		finished = false;
	}
	
	public MapRequest getRequest() {
		return request;
	}
	
	public boolean isFinished() {		
		return finished;
	}
	
	public void setFinished(boolean finished) {
		this.finished = finished;
	}

	public Area getFinishedArea() {
		return finishedArea;
	}

	public Area getFuzzyArea() {
		return fuzzyArea;
	}
	
	/** Returns a new Area that contains both fuzzy and finished areas. */
	public Area getValidArea() {
		Area valid = new Area();
		valid.add(finishedArea);
		valid.add(fuzzyArea);
		return valid;
	}
	
	public BufferedImage getImage() {
		return image;
	}
	
	/**
	 * Returns a portion of this MapData's WritableRaster covering the given
	 * world extent. May throw all manner of exceptions if the image isn't
	 * present. If the given extent does not intersect this MapData at all, null
	 * is returned.
	 */
	public WritableRaster getRasterForWorld(Rectangle2D unwrappedWorld) {
		return getRasterForWorld(image.getRaster(), request.getExtent(), unwrappedWorld);
	}
	
	/**
	 * Returns a WritableRaster for the part of the input image (which covers
	 * inputExtent) that lies within outputExtent. If there is no intersection,
	 * this returns null. Any pixel touched by the intersection is in the returned
	 * Raster. The input image and input extent must have the same aspect ratio.
	 */
	public static WritableRaster getRasterForWorld(WritableRaster raster, Rectangle2D inputExtent, Rectangle2D outputExtent) {
		Rectangle overlap = getRasterBoundsForWorld(raster, inputExtent, outputExtent);
		if (overlap.isEmpty())
			return null;
		
		return raster.createWritableChild(overlap.x, overlap.y, overlap.width, overlap.height, 0, 0, null);
	}
	
	/**
	 * Returns a Rectangle (in raster coordinates) for the part of the input image 
	 * (which covers inputExtent) that lies within outputExtent. If there is no intersection,
	 * this returns an empty Rectangle. Any pixel touched by the intersection is in the returned
	 * Raster. The input image and input extent must have the same aspect ratio.
	 */
	public static Rectangle getRasterBoundsForWorld(Raster raster, Rectangle2D inputExtent, Rectangle2D outputExtent){
		Rectangle2D overlap = new Rectangle2D.Double();
		Rectangle2D.intersect(inputExtent, outputExtent, overlap);
		if (overlap.isEmpty()) {
			return new Rectangle();
		} else {
			double xPPD = raster.getWidth() / inputExtent.getWidth();
			double yPPD = raster.getHeight() / inputExtent.getHeight();
			if (Math.abs(xPPD - yPPD) > .00001) {
				log.aprintln("Axes have unequal scale, "
					+ "image=" + raster.getWidth() + "," + raster.getHeight()
					+ " extent=" + inputExtent.getWidth() + "," + inputExtent.getHeight()
					+ " ppd="+xPPD+","+yPPD);
			}
			int width = (int)Math.ceil(overlap.getWidth() * xPPD);
			int height = (int)Math.ceil(overlap.getHeight() * yPPD);
			int x = (int)Math.floor((overlap.getMinX() - inputExtent.getMinX()) * xPPD);
			int y = (int)Math.floor((inputExtent.getMaxY() - overlap.getMaxY()) * yPPD);
			return new Rectangle(x, y, width, height);
		}
	}
	
	public Rectangle getRasterBoundsForWorld(Rectangle2D outputExtent){
		return getRasterBoundsForWorld(getImage().getRaster(), getRequest().getExtent(), outputExtent);
	}
	
	public synchronized void addTile(MapTile mapTile) {
		// create image when the first tile is received
		Rectangle2D worldExtent = request.getExtent();
		int ppd = request.getPPD();
		int w = (int)Math.ceil(worldExtent.getWidth()*ppd);
		int h = (int)Math.ceil(worldExtent.getHeight()*ppd);
		if (image == null) {
			image = Util.createCompatibleImage(mapTile.getImage(), w, h);
		}
		
		Point2D offset = request.getSource().getOffset();
		
		// get an extent that includes all the pixels in 'image'
		// Use the offset values (which default to 0.0 for an unnudged map)
		Rectangle2D fixedExtent = new Rectangle2D.Double(worldExtent.getMinX()+offset.getX(),
				worldExtent.getMinY()+offset.getY(), w / (double)ppd, h / (double)ppd);
		
		int outBands = image.getRaster().getNumBands();
		int inBands = mapTile.getImage().getRaster().getNumBands();
		if (outBands != inBands) {
			log.aprintln("Wrong number of bands received for source " + mapTile.getRequest().getSource().getName() +"! Expected " + outBands + ", got " + inBands);
			return;
		}
		
		// for each occurrence of this tile in the requested unwrapped world extent
		Rectangle2D[] worldExtents = Util.toUnwrappedWorld(mapTile.getTileExtent(), fixedExtent);
		for (int i = 0; i < worldExtents.length; i++) {
			Rectangle2D worldTile = worldExtents[i];
			
			WritableRaster source = getRasterForWorld(mapTile.getImage().getRaster(), worldTile, fixedExtent);
			
			worldTile = new Rectangle2D.Double(worldExtents[i].getMinX()-offset.getX(), 
					worldExtents[i].getY()-offset.getY(), worldExtents[i].getWidth(),
					worldExtents[i].getHeight());
			
			Rectangle2D unShiftedExtent = new Rectangle2D.Double(worldExtent.getMinX(),
					worldExtent.getMinY(), w / (double)ppd, h / (double)ppd);
			
			WritableRaster target = getRasterForWorld(image.getRaster(), unShiftedExtent, worldTile);
			
			// get source and target rasters based on world coordinates
			log.println("Updating request " + rect(fixedExtent) + " with tile " + rect(mapTile.getTileExtent()));
			
			if (target == null || source == null) {
				log.println("Unable to extract intersecting area from both images, aborting tile update");
				return;
			}
			
			// copy tile into place
			target.setRect(source);
			
			// update areas
			// This can potentially be reached with cancelled/errored mapTiles... is this safe?
			Area usedTileArea = new Area(worldTile.createIntersection(unShiftedExtent));
			(mapTile.isFinal() ? getFinishedArea() : getFuzzyArea()).add(new Area(usedTileArea));
		}
	}
	
	/**
	 * Returns a WritableRaster for each rectangle in the changed area.
	 */
	public WritableRaster[] getChangedRasters(Area changedArea) {
		Rectangle2D[] rects = new PolyArea(changedArea).getRectangles();
		WritableRaster[] out = new WritableRaster[rects.length];
		int pos = 0;
		for (Rectangle2D rect: rects) {
			out[pos++] = MapData.getRasterForWorld(getImage().getRaster(), getRequest().getExtent(), rect);
		}
		return out;
	}
	
	public static String rect(Rectangle2D r) {
		return r.getMinX() + "," + r.getMinY() + " to " + r.getMaxX() + "," + r.getMaxY();
	}
	
	/** Creates a new MapData instance with clones of all mutable properties */
	public synchronized MapData getDeepCopy(boolean convertToComponentColorModel) {
		BufferedImage bi;
		if (image == null) {
			bi = null;
		} else if (convertToComponentColorModel && !(image.getColorModel() instanceof ComponentColorModel)) {
			bi = asComponentModelImage(image);
		} else {
			bi = new BufferedImage(
				image.getColorModel(),
				image.copyData(null),
				image.isAlphaPremultiplied(), null);
		}
		return getDeepCopyShell(bi);
	}
	
	/** Returns a new MapData object with clones of this MapData's properties, but the given image instead. */
	public synchronized MapData getDeepCopyShell(BufferedImage image) {
		MapData md = new MapData(request);
		md.finished = isFinished();
		md.finishedArea = (Area)getFinishedArea().clone();
		md.fuzzyArea = (Area)getFuzzyArea().clone();
		md.image = image;
		return md;
	}
	
	public static BufferedImage asComponentModelImage(BufferedImage src) {
		ColorModel srcCm = src.getColorModel();
		ColorModel dstCm = new ComponentColorModel(
			srcCm.getColorSpace(),
			srcCm.getComponentSize(),
			srcCm.hasAlpha(),
			false,
			srcCm.getTransparency(),
			srcCm.getTransferType());
		
		WritableRaster dstRaster = dstCm.createCompatibleWritableRaster(src.getWidth(), src.getHeight());
		BufferedImage dst = new BufferedImage(dstCm, dstRaster, dstCm.isAlphaPremultiplied(), null);
		dst.setData(src.getRaster());
		
		return dst;
	}
}
