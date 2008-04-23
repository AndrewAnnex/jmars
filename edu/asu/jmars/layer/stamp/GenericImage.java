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


package edu.asu.jmars.layer.stamp;

import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;

/**
 * This class is a generic boilerplate for handling stamp images that are loaded
 * only from common industry formats supported by the
 * {@link Toolkit#createImage(java.net.URL)} and {@link Toolkit#createImage(String)}
 * methods.
 * <p> 
 * Subclasses derived from this class typically support single-band
 * images, but there is no imposed restriction on the number of bands/images
 * which may be contained in files. 
 * <p>
 * Subclasses must do one of the following in their implementations:
 * <UL>
 * <LI> Call the {@link #storeCachedImage(int)} in their constructors for each
 * contained image band for later disk retrieval, OR
 * <LI> Override the {@link #releaseImage(int)} method to suppress release
 * of image bands from memory, OR
 * <LI> Override {@link #getImage(int)} to provide some alternate means for
 * reliably retrieving image bands other than from the disk cache, OR
 * <LI> Provide some alternate scheme for ensuring that image bands can properly
 * be retrieved in {@link UniformImage} form via {@link #getUniformImage} method.
 * </UL>
 * <p>
 * For small-to-medium image files, use of the {@link #storeCachedImage} method in the
 * constructor is the recommended solution.  Larger-sized images may require more 
 * elaborate solutions for performance and memory optimization.  The 
 * {@link MocImage} class is a good example of an implementation that addresses
 * the latter issues.
 * <p>
 * A related issue is the matter of image frame sizes.  The default scheme
 * implemented by {@link #getFrameSize()} is well-suited for images in
 * vertical strip form.  However, subclasses with large and/or very wide images
 * may need to override this method to create short, horizontal slices for better
 * memory/performance optimization in rendering, drawing, and cache retrieval
 * of image frames.
 * 
 * @see edu.asu.jmars.layer.stamp.GenericImageFactory
 * 
 * @author hoffj MSFF-ASU
 */
public abstract class GenericImage extends StampImage
{
    private static final DebugLog log = DebugLog.instance();
    
    /**
     * Returns the rendered image corresponding to the specified band.  If
     * the image band is not currently loaded in memory, the
     * {@link #getCachedImage} method is called to attempt to load it from
     * disk cache. 
     * 
     * @param band desired image band; for single-banded images, this should
     * be 0. 
     * @return Returns image if available; returns <code>null</code> if image
     * is not in memory and cannot be loaded from disk cache.
     */
    public BufferedImage getImage(int band)
    {
        if (images == null ||
            band >= bandCount)
            return null;
        
        if (images[0] == null)
            images[0] = getCachedImage(0);
        
        return images[0];
    }
    
    /**
     * Override of parent implementation to support special memory
     * optimization and caching needs. 
     * 
     * @see CachedUniformImage
     * @see #releaseImage(int)
     */
    protected UniformImage getUniformImage(int band)
    {
        if (band >= bandCount)
            log.aprintln("invalid band number: " + band);
        
        if (uniformImages == null)
            uniformImages = new UniformImage[bandCount];
        
        try {
            if(uniformImages[band] == null)
                uniformImages[band] = new CachedUniformImage(getImage(band), getCachedImageName(band));
        }
        catch(Throwable e) {
            log.aprintln("Failed to create image due to:");
            log.aprintln(e);
            return  null;
        }
        
        return  uniformImages[band];
    }
    
    /**
     * Override of parent implementation to support special memory
     * optimization and caching needs. 
     * 
     * @see CachedUniformImage
     * @see #getUniformImage(int)
     */
    synchronized public void releaseImage(int band, boolean eraseFrames)
    {
        if(band >= bandCount) {
            log.aprintln("In " + filename + ", band " +
                         band + "/" + bandCount + " doesn't exist!");
            return;
        }
        
        if (images != null)
            images[band] = null;
        
        if (uniformImages != null &&
                uniformImages[0] != null)
                ((CachedUniformImage)uniformImages[0]).releaseImage();
        
        Point2D[] pts = getPoints(band);
        if (pts != null) {
            int frameCount = pts.length / 4;
            if (frameCache != null)
                for (int i = 0; i < frameCount; i++)
                    if (frameCache[band][i] != null)
                        frameCache[band][i].releaseImage();
                    
            if (eraseFrames)
                frameCache = null;
        }
    }
    
    public boolean validGeometryInfo(Point2D[] pts)
    {
        return true;
    }
    
    /**
     * Returns the minimum of either an image band's width or
     * height as the default height for image frames.
     * 
     * @return Returns the default height of an image frame;
     * returns 0 if there is an error.
     * 
     * @see #getFrameSize(int, int)
     * @see #getUniformImage(int)
     */
    protected int getFrameSize()
    {
        int size = 0;
        
        UniformImage uImg = getUniformImage(0);
        if (uImg != null)
            try {
                size = Math.min(uImg.getWidth(), uImg.getHeight());
            }
            catch (Exception e) {
                log.aprintln("while getting image height/width: " + e.getMessage());
            }
            
        return size;
    }
    
    /**
     * Returns the exact height of the image frame for the specified
     * frame of the specified image band.  Takes into account image bands
     * that may not be sized as exact multiples of the default image frame
     * height.
     * 
     * @param band desired image band; for single-banded images, this should
     * be 0. 
     * @param frame frame number, starting from 0.
     * 
     * @return Returns the exact height of the specified image frame;
     * returns 0 if there is an error.
     * 
     * @see #getFrameSize()
     * @see #getUniformImage(int)
     */
    protected int getFrameSize(int band, int frame)
    {
        int size = getFrameSize();
        int realSize = size;
        
        if (frameCount > 0 &&
            frame == frameCount - 1 &&
            size > 0)
        {
            UniformImage uImg = getUniformImage(0);
            if (uImg == null)
                log.aprintln("called with no image loaded");
            else
            {
                try {
                    int remainder = uImg.getHeight() % size;
                    if (remainder > 0)
                        realSize = remainder;
                }
                catch (Exception e) {
                    log.aprintln("while getting image height: " + e.getMessage());
                    return 0;
                }
            }
        }
        
        return realSize;
    }
    
    /**
     * Returns the maximum rendering resolution for image frames
     * created from this instance.
     */
    protected int getMaxRenderPPD()
    {
        return 512;
    }
    
    private String realFilename = null;

    /**
     * Removes any non-legal filename characters that may appear in
     * stamp image id's and returns result as a filename.
     */
    public String getFilename()
    {
        if (realFilename != null)
            return realFilename;
        else
            realFilename = GenericImageFactory.getStrippedFilename(filename);
        
        return realFilename;
    }
    
}
