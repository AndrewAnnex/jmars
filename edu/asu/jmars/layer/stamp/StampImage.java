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

import edu.asu.jmars.*;
import edu.asu.jmars.graphics.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import com.keypoint.*;


/**
 * Abstract class defining framework for creating instances of 
 * stamp images from data contained in files, URLs, and any {@link Stamp} 
 * which contains a valid URL.  Stamp images may be single- or
 * multi-banded.
 * 
 * It is recommended that subclass constructors automatically cache images by
 * as separate bands to disk using the {@link #storeCachedImage(int)} method.
 * The stamp image drawing framework implemented by {@link StampLView} routinely
 * calls the {@link #releaseImage(int)} method in this class to release image
 * bands from memory after drawing for memory management reasons.  If a subclass
 * does not override the latter method to prevent this behavior, then it must also 
 * handle retrieving image bands from disk cache via {@link #getCachedImage(int)}
 * as part of its {@link #getImage(int)} implementation.
 * 
 * @see edu.asu.jmars.layer.stamp.StampImageFactory
 * @see #getImage(int)
 * @see #releaseImage(int)
 * @see #getCachedImage(int)
 * @see #storeCachedImage(int)
 */
public abstract class StampImage implements ImageConstants
{ 
    protected static final int DEFAULT_MIN_RENDER_PPD = 4;
    protected static final String CACHED_IMAGE_SUFFIX = ".png";
    protected static final String CACHED_RASTER_IMAGE_SUFFIX = ".dat";
    public static final String STAMP_CACHE = Main.getJMarsPath() + "stamps" + System.getProperty("file.separator");
    private static final DebugLog log = DebugLog.instance();
    
    static {
    	File cache = new File(STAMP_CACHE);
    	if (!cache.exists()) {
    		if (!cache.mkdirs()) {
    			throw new IllegalStateException("Unable to create stamp cache directory, check permissions in " + Main.getJMarsPath());
    		}
    	} else if (!cache.isDirectory()) {
    		throw new IllegalStateException("Stamp cache cannot be created, found regular file at " + STAMP_CACHE);
    	}
    }
    
    protected String filename;
    protected String IMAGE_TYPE = "";        // Used for naming cached image files, etc.
    protected String IMAGE_FRAME_TYPE = "";  // Used for naming cached image frame files, etc.
    protected ArrayList bandList;
    protected int bandNumbers[];
    protected int bandCount;
    protected String label;
    
    protected BufferedImage images[] = null;
    protected UniformImage uniformImages[] = null;
    
    protected Frame frameCache[][];
    protected GridDataStore.Cell frameCells[][];
    protected int frameWidth;
    protected int frameHeight;
    protected int lastFrameHeight;
    protected int frameCount = 0;
    
    protected int userRotateFlip = IMAGE_NORMAL;

    
    public String getLabel()
    {
        return  label;
    }
    
    /**
     ** Subclasses should override this if they need to
     ** modify/filter filename property in some way prior
     ** to its use in #getCachedImageName or #getCachedImageFrameName
     ** methods
     **/
    public String getFilename()
    {
        return filename;
    }
    
    /**
     ** Applies current image rotation/flipping state to specified
     ** set of image or image frame coordinates.
     **
     ** IMPORTANT NOTE!! - This method must be called from any subclass 
     ** implementation of {@link #getPoints} method that supports image rotation 
     ** or flipping.
     **
     ** @param corners Four-element array of corner points in following
     ** order: 
     ** <ul>          
     ** <li>   corners[0]   lower left corner coordinates (SW)
     ** <li>   corners[1]   lower right corner coordinates (SE)
     ** <li>   corners[2]   upper left corner coordinates (NW)
     ** <li>   corners[3]   upper right corners coordinates (NE)
     ** </ul>
     **
     ** @return Four-element array of corners points in same above
     ** order but with elements swapped as needed to apply the current
     ** image orientation.
     **
     ** @see #rotateFlip
     ** @see #getPoints
     **/
    public Point2D[] getOrientedPoints(Point2D[] corners)
    {
        if (corners == null ||
                corners.length != 4)
            throw new IllegalArgumentException("null or improperly sized array of corner points");
        
        Point2D[] newCorners = new Point2D[4];
        
        switch (userRotateFlip) {
        case IMAGE_NORMAL:
            // No corner swaps
            newCorners[0] = corners[0];
            newCorners[1] = corners[1];
            newCorners[2] = corners[2];
            newCorners[3] = corners[3];
            break;
        case IMAGE_ROTATED_180:
            // Swapping SW and NE, SE and NW
            newCorners[0] = corners[3];
            newCorners[1] = corners[2];
            newCorners[2] = corners[1];
            newCorners[3] = corners[0];
            break;
        case IMAGE_HFLIPPED:
            // Swapping SW and SE, NW and NE
            newCorners[0] = corners[1];
            newCorners[1] = corners[0];
            newCorners[2] = corners[3];
            newCorners[3] = corners[2];
            break;
        case IMAGE_VFLIPPED:
            // Swapping SW and NW, SE and NE
            newCorners[0] = corners[2];
            newCorners[1] = corners[3];
            newCorners[2] = corners[0];
            newCorners[3] = corners[1];
            break;
        default:
            log.aprintln("bad internal image orientation state: " + userRotateFlip);
        break;
        }
        
        return newCorners;
    }
    
    /** 
     ** Rotates or flips image as specified relative to current 
     ** stamp image orientation.  Actual result of rotation/flipping
     ** is not realized until calls are made to #getImageFrame method.
     **
     ** @param operation legal operation values are {@link #IMAGE_ROTATED_180}, 
     ** {@link #IMAGE_HFLIPPED}, {@link #IMAGE_VFLIPPED}.
     **
     ** @see #getPoints
     ** @see #getOrientedPoints
     **/
    synchronized public void rotateFlip(int band, int operation)
    {
        if (operation != IMAGE_ROTATED_180 &&
                operation != IMAGE_HFLIPPED &&
                operation != IMAGE_VFLIPPED) {
            log.aprintln("illegal rotate/flip operation code: " + operation);
            return;
        }
        
        // Dump cached image frame data and geometry point data.  Must
        // dump the image frames BEFORE the geometry data due to the
        // implementation of releaseImage() method.
        releaseImage(band, true);
        log.println("released images");
        clearPointsCache();
        log.println("cleared geometry points cache");
        
        // Determine new image state
        userRotateFlip = IMAGE_RESULT_MAP[userRotateFlip][operation];
        IMAGE_FRAME_TYPE = getImageFrameType(userRotateFlip);
        log.println("new image frame type: " + IMAGE_FRAME_TYPE);
    }
    
    /**
     ** Returns current image orientation.
     **
     ** @return returns one of following values:
     ** <ul>
     ** <li>{@link #IMAGE_NORMAL}
     ** <li>{@link #IMAGE_ROTATED_180}
     ** <li>{@link #IMAGE_HFLIPPED}
     ** <li>{@link #IMAGE_VFLIPPED}
     ** </ul>
     **/
    public int getOrientation()
    {
        return userRotateFlip;
    }
    
    /** 
     ** Returns image frame label for specified stamp image orientation.
     **
     ** NOTE: Any subclass which implements image rotate/flipping must override
     ** this method.
     **
     ** @param imageState legal state values are {@link #IMAGE_NORMAL},
     ** {@link #IMAGE_ROTATED_180}, {@link #IMAGE_HFLIPPED}, and {@link #IMAGE_VFLIPPED}.
     **/
    protected String getImageFrameType(int imageState)
    {
        return IMAGE_FRAME_TYPE;
    }
    
    /**
     ** Returns projected geometry points for image frames.
     ** <p>
     ** IMPORTANT NOTE!! - Any subclass implementation that supports 
     ** image rotation or flipping must call {@link #getOrientedPoints} 
     ** as appropriate to rotate/flip corner points.
     **
     ** @return array of points organized as follows:
     **
     ** <ul>
     ** <li>    element #    Description
     ** <li>
     ** <li>    0 to 3        single frame defining bounds of entire image
     ** <li>
     ** <li>        0        - lower left corner coordinates
     ** <li>        1        - lower right corner coordinates
     ** <li>        2        - upper left corner coordinates
     ** <li>        3        - upper right corner coordinates
     ** <li>
     ** <li>    4n to 4n+3   n-th subframe of image (n >= 1)
     ** <li>
     ** <li>        4n      - lower left corner coordinates
     ** <li>        4n+1    - lower right corner coordinates
     ** <li>        4n+2    - upper left corner coordinates
     ** <li>        4n+3    - upper right corner coordinates
     ** <li>
     ** <li>    Frames are organized from upper part of image (frame 1)
     ** <li>    to lower part of image (frame n).
     ** <li>    
     ** <li> Note:   Returns an array of at least two frames: one for
     ** <li>         whole image, and another for at least one subframe
     ** <li>         (which may simply be a duplicate of the whole image frame).
     ** </ul>
     **
     ** @see #getOrientedPoints
     ** @see #rotateFlip
     **/
    public abstract Point2D[] getPoints();
    
    
    /**
     ** Returns projected geometry points for image frames based
     ** on band; subclasses should override as needed to support
     ** any band-varying behavior.  Default implementation is
     ** equivalent to {@link #getPoints}.
     ** <p>
     ** IMPORTANT NOTE!! - Any subclass implementation that supports 
     ** image rotation or flipping must call {@link #getOrientedPoints} 
     ** as appropriate to rotate/flip corner points.
     **
     ** @return array of points organized as described for #getPoints()
     **
     ** @see #getOrientedPoints
     ** @see #rotateFlip
     **/
    public Point2D[] getPoints(int band)
    {
        return getPoints();
    }
    
    /**
     ** Subclass must override this method if it implements any
     ** caching scheme as part of the #getPoints method implementation.
     **/
    public void clearPointsCache()
    {
    }
    
    /**
     ** Generates a new set of geometry points to subdivide a single 
     ** frame of geometry points into the specified number of divisions.
     ** Frames are created along lower-to-upper axis.
     ** <p>
     ** NOTE: This method uses the approximate interpolation scheme
     **       present in the HVector class.  Should probably only be used
     **       for client-side image subtiling purposes when more accurate
     **       data is unavailable from an image geometry database, etc.
     **
     ** @param pts  an array of four points corresponding to the four
     **             corners of an image frame:
     ** 
     ** <ul>          
     ** <li>          points[0]   lower left corner coordinates
     ** <li>          points[1]   lower right corner coordinates
     ** <li>          points[2]   upper left corner coordinates
     ** <li>          points[3]   upper right corners coordinates
     ** </ul>
     **
     ** @param frameSizeFactor  scaling factor for size of each frame along
     **                         divided axis between 0 and 1; e.g., 0.5
     **                         will divide image into two frames.
     **
     **                         If factor does not divide 1 evenly, last
     **                         frame will be sized to the fractional residual.
     **
     ** @return array of points organized as described for #getPoints(),
     **         whole image frame + subframes
     **
     **/
    protected Point2D[] getFakeFramePoints(Point2D[] pts, double frameSizeFactor)
    {
        if (pts == null ||
                pts.length != 4 ||
                frameSizeFactor <= 0 ||
                frameSizeFactor > 1)
        {
            log.aprintln("bad parameters");
            return null;
        }
        
        int numFrames = (int)Math.ceil(1 / frameSizeFactor);
        final int offset = 4;
        Point2D[] newPoints = new Point2D[numFrames * 4 + offset];
        
        log.println("generating fake geometry frames");
        log.println("numFrames=" + numFrames + " frameSizeFactor=" + frameSizeFactor);
        
        if (newPoints != null) {
            // Copy whole image frame
            for (int i=0; i < 4; i++)
                newPoints[i] = pts[i];
            
            // Convert image frame geometry to vector form.
            HVector ll = new HVector(pts[0]);
            HVector lr = new HVector(pts[1]);
            HVector ul = new HVector(pts[2]);
            HVector ur = new HVector(pts[3]);
            
            // Prepare uppper part of first subframe.
            Point2D nextUL = newPoints[2];
            Point2D nextUR = newPoints[3];
            
            // Create image subframe geometry points through
            // interpolation from whole image frame.  Do this
            // for all but the last subframe.  Frames start
            // from upper part of image.
            for (int i=0; i < numFrames-1; i++) {
                // Find lower left/right corners for new subframe
                HVector newLL = ul.interpolate(ll, frameSizeFactor * (i+1));
                HVector newLR = ur.interpolate(lr, frameSizeFactor * (i+1));
                
                // Store geometry for new subframe
                newPoints[i*4 + offset]   = newLL.toLonLat(null);
                newPoints[i*4 + offset+1] = newLR.toLonLat(null);
                newPoints[i*4 + offset+2] = nextUL;
                newPoints[i*4 + offset+3] = nextUR;
                
                // Prepare upper part of next subframe
                nextUL = newPoints[i*4 + offset];
                nextUR = newPoints[i*4 + offset+1];
            }
            
            // Create last subframe using residual part of whole image frame
            newPoints[(numFrames-1) * 4 + offset]   = newPoints[0];
            newPoints[(numFrames-1) * 4 + offset+1] = newPoints[1];
            newPoints[(numFrames-1) * 4 + offset+2] = nextUL;
            newPoints[(numFrames-1) * 4 + offset+3] = nextUR;
        }
        
        return newPoints;
    }
    
    /**
     ** Returns an array of strings containing the available bands in
     ** the image.
     **/
    public String[] getBands()
    {
        return  (String[]) bandList.toArray(new String[0]);
    }
    
    public String getBand(int n)
    {
        return  (String) bandList.get(n);
    }
    
    /**
     ** Maps internal band index into actual band number used
     ** in PDS image file, SQL database, etc.
     **/
    public int getBandNumber(int index)
    {
        if (bandNumbers != null &&
                index < bandNumbers.length)
            return bandNumbers[index];
        else
            return -1;
    }
    
    /**
     * Returns number of separate image bands contained in
     * this instance.
     */
    public int getBandCount()
    {
        return  bandList.size();
    }

    /**
     * Returns image for the specified band number.
     * <p>
     * It is recommended that subclass implementations handle retrieving 
     * image bands from disk cache via {@link #getCachedImage(int)} whenever
     * a valid band is not in memory.
     * <p>
     * The primary usage of this method is by the default implemenation of the
     * {@link #getUniformImage(int)} method to retrieve individual pixels
     * for constructing image frames.  Once rendered image frames have been created
     * (and cached), there is generally no need for full image bands to be
     * kept in memory.  However, it must be possible to retrieve them from
     * disk as needed to create new image frames at various resolutions.
     *  
     * @param band desired image band; for single-banded images, this should
     * be 0.
     * 
     * @see #getUniformImage(int) 
     * @see #createImageFrame(int, int, int, int)
     * @see #releaseImage(int)
     * @see #getCachedImage(int)
     * @see #storeCachedImage(int)
     * 
     * @return Returns image band from disk cache if available; otherwise,
     * returns <code>null</code>.
     */
    public abstract BufferedImage getImage(int band);
    
    /**
     * Releases resources (memory) associated with the image at the
     * particular band.
     *
     * @param band desired image band; for single-banded images, this should
     * be 0.
     *  
     * @see #getImage(int)
     * @see #getCachedImage(int)
     * @see #storeCachedImage(int)
     */
    synchronized public void releaseImage(int band)
    {
        releaseImage(band, false);
    }
    
    /**
     * Releases resources (memory) associated with the image at the
     * particular band.
     *
     * @param eraseFrames if 'true', not only projected image frames
     * but also all image frame data of any kind is erased.
     *
     * @see #getImage(int)
     * @see #getCachedImage(int)
     * @see #storeCachedImage(int)
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
        
        if (uniformImages != null)
            uniformImages[band] = null;
        
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
    
    
    /**
     ** Verifies that the specified frame point geometry information
     ** matches the image size data and calculated framesize.
     **
     ** @see #createImageFrame
     **/
    public boolean validGeometryInfo(Point2D[] pts)
    {
        return true;
    }
    
    /**
     ** Returns "how long" (actually the number of unprojected frames)
     ** it would take to render the given band on the given
     ** MultiProjection.
     **/
    public int getRenderImageTime(int band, MultiProjection proj, int renderPPD)
    {
        Point2D[] pts = getPoints(band);
        Shape worldWin = proj.getWorldWindowMod();
        
        int time = 0;
        for(int i=4; i<pts.length; i+=4) {
            final int maxPPD = getMaxRenderPPD();
            Frame frame = fastGetImageFrame(band, i/4 - 1);
            
            // Check whether frame exists and already has an image cached
            // at sufficient resolution and with correct projection.
            if (frame != null &&
                    ( ( renderPPD > frame.renderPPD && 
                            maxPPD > frame.renderPPD) ||
                            !frame.isImageCached() ||
                            frame.projHash != Main.PO.getProjectionSpecialParameters().hashCode()
                    ) &&
                    worldWin.intersects(frame.where))
            {
                ++time;
            }
            else if (frame == null) {
                // If the frame did not already exist, create a frame
                // at minimal resolution just to check whether it
                // intersects the projection window.
                frame = getImageFrame(band, i/4 - 1, getMinRenderPPD(), worldWin);
                
                if(worldWin.intersects(frame.where))
                    ++time;
            }
        }
        return  time;
    }
    
    
    /**
     ** Renders the image for a particular band, onto the given
     ** (world-coordinate) graphics context.
     **/
    public void renderImage(int band,
                            Graphics2D wg2,
                            BufferedImageOp op,
                            MultiProjection proj,
                            StampProgressMonitor pm,
                            int renderPPD)
    {
        try {
            if(Main.inTimeProjection()) {
                log.aprintln("CAN'T RENDER IN TIME PROJECTION!");
                log.aprintStack(4);
                return;
            }
            
            pm.incrementStamp(filename);
            
            Point2D[] pts = getPoints(band);
            UniformImage image = getUniformImage(band);
            if(image == null)
                return;
            
            Shape worldWinMod = proj.getWorldWindowMod();
            Rectangle2D worldWin = proj.getWorldWindow();
            final double mod = 360;
            final double min = worldWin.getMinX();
            final double max = worldWin.getMaxX();
            final double base = Math.floor(min / mod) * mod;
            final int count =
                (int) Math.ceil (max / mod) -
                (int) Math.floor(min / mod);
            
            Rectangle imageRange = new Rectangle(0, 0, image.getWidth(), 256);
            Rectangle2D.Double where = new Rectangle2D.Double();
            
            for(int i=4; i<pts.length; i+=4) {
                Frame frame = getImageFrame(band, i/4 - 1, renderPPD, worldWinMod);
                
                boolean takesTime = !frame.isImageCached() && worldWin.intersects(frame.where);
                
                where.setRect(frame.where);
                double origX = where.x;
                int start = where.getMaxX() < mod ? 0 : -1;
                for(int m=start; m<count; m++) {
                    where.x = origX + base + mod*m;
                    if(worldWin.intersects(where)) {
                        Graphics2D g2 = (Graphics2D) wg2.create();
                        g2.transform(Util.image2world(frame.getImage(), where));
                        g2.drawImage(frame.getImage(), op, 0, 0);
                    }
                }
                
                if(takesTime)
                    pm.incrementTime();
                
                if(pm.isCanceled())
                    return;
            }
        }
        catch (Exception e) {
            log.aprintln(e);
        }
    }
    
    
    
    /**
     ** Not quite sure what this does: see implementation in
     ** PdsImage. -- Joel Hoff
     **/
    public int[] getHistogram(int band)
    {
        return null;
    }
    
    
    /** 
     * This method is used to provide uniform access to image band pixels.
     * The default implementation returns/creates a thin wrapper around a
     * {@link BufferedImage} instance returned by the {@link #getImage(int)}
     * method.
     * 
     * The primary usage of this method is to retrieve individual pixels
     * for constructing image frames.  Once rendered image frames have been created
     * (and cached), there is generally no need for {@link UniformImage} instances
     * to be kept in memory.  However, it must be possible to recreate them
     * as necessary to create new image frames at various resolutions.
     *  
     * It is recommended that subclasses override the default implementation
     * if possible to provide better performance or storage than the general-purpose
     * solution afforded here.
     * 
     * @see UniformImage
     * @see #getImage(int)
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
                uniformImages[band] = new UniformImage(getImage(band));
        }
        catch(Throwable e) {
            log.aprintln("Failed to create image due to:");
            log.aprintln(e);
            return  null;
        }
        
        return  uniformImages[band];
    }
    
    protected String getCachedImageName(int band)
    {
        return getCachedImageName(band, getFilename());
    }
    
    protected String getCachedImageName(int band, String fname)
    {
        if (fname != null &&
                StampImage.STAMP_CACHE != null)
            return StampImage.STAMP_CACHE + fname + "_" + band + "_" + IMAGE_TYPE + CACHED_IMAGE_SUFFIX;
        else
            return null;
    }
    
    protected String getCachedRasterImageName(int band)
    {
        return getCachedRasterImageName(band, getFilename());
    }
    
    protected String getCachedRasterImageName(int band, String fname)
    {
        if (fname != null &&
                StampImage.STAMP_CACHE != null)
            return StampImage.STAMP_CACHE + fname + "_" + band + "_" + IMAGE_TYPE + CACHED_RASTER_IMAGE_SUFFIX;
        else
            return null;
    }

    /**
     * Stores specified image band to disk cache.
     *
     * @param band desired image band; for single-banded images, this should
     * be 0.
     *  
     * @see #getImage(int)
     * @see #releaseImage(int)
     * @see #getCachedImage(int)
     * @see #getCachedImageName(int)
     */
    protected void storeCachedImage(int band)
    {
        if(band >= bandCount) {
            log.aprintln("In " + filename + ", band " +
                         band + "/" + bandCount + " doesn't exist!");
            return;
        }
        
        if (images != null &&
                images[band] != null)
        {
            String fname = getCachedImageName(band);
            log.println("before saving PNG image");
            savePngImage(fname, images[band]);
            log.println("after saving PNG image");
        }
    }
    
    /**
     * Retrieves specified image band from disk cache.
     *
     * @param band desired image band; for single-banded images, this should
     * be 0.
     *  
     * @see #getImage(int)
     * @see #releaseImage(int)
     * @see #getCachedImageName(int)
     * @see #storeCachedImage(int)
     * 
     * @return Returns image band from disk cache if available; otherwise,
     * returns <code>null</code>.
     */
    protected BufferedImage getCachedImage(int band)
    {
        if(band >= bandCount) {
            log.aprintln("In " + filename + ", band " +
                         band + "/" + bandCount + " doesn't exist!");
            return  null;
        }
        
        return StampImageFactory.loadImage(getCachedImageName(band));
    }
    
    // Returns 'true' if image is stored OK, 'false' otherwise.
    protected boolean savePngImage(String fname, BufferedImage image)
    {
        final int COMPRESSION_LEVEL = 1;  // From a ZIP compression range of 0-9.
        boolean status = false;
        
        if (fname != null &&
                image != null) {
            try {
                File outputFile = new File(fname);
                
                if (outputFile != null) {
                    if (outputFile.exists() &&
                            outputFile.isDirectory()) {
                        log.aprintln("Could not store image: " + fname + " is a directory");
                        return false;
                    }
                    
                    outputFile = null;
                    PngEncoder encoder = new PngEncoderB(image, PngEncoder.ENCODE_ALPHA, 
                                                         PngEncoder.FILTER_NONE, COMPRESSION_LEVEL);
                    
                    if (encoder != null) {
                        log.println("before encoding PNG");
                        byte[] buf = encoder.pngEncode();
                        log.println("after encoding PNG");
                        FileOutputStream out = new FileOutputStream(fname);
                        
                        if (buf != null &&
                                out != null) {
                            log.println("writing " + buf.length + " bytes");
                            out.write(buf);
                            out.flush();
                            log.println("done writing to file");
                            
                            status = true;
                            log.println("stored cached image " + fname);
                        }
                        else
                            log.aprintln("could not create output file");
                        
                        if (out != null)
                            out.close();
                    }
                    else
                        log.aprintln("could not create encoder");
                }
            }
            catch (Throwable e) {
                log.aprintln(e);
            }
        }
        
        return status;
    } 
    
    /**
     ** Stores image as raster data.
     **
     ** Only useful for temporary caching of image data during a
     ** particular execution cycle, not between program launches.
     **
     ** Returns 'true' if image is stored OK, 'false' otherwise.
     **/
    protected boolean saveRasterImage(String fname, BufferedImage image)
    {
        boolean status = false;
        
        if (fname != null &&
                image != null) {
            try {
                File outputFile = new File(fname);
                
                if (outputFile != null) {
                    if (outputFile.exists() &&
                            outputFile.isDirectory())
                    {
                        log.aprintln("Could not store image: " + fname + " is a directory");
                        return false;
                    }
                    
                    outputFile = null;
                    WritableRaster raster = image.getRaster();
                    
                    if (raster == null) 
                        log.aprintln("could not create raster");
                    else if (raster.getTransferType() != DataBuffer.TYPE_INT)
                        log.aprintln("wrong image type");
                    else {
                        int[] buf = (int[]) raster.getDataElements(0, 0, image.getWidth(), image.getHeight(), null);
                        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fname));
                        
                        if (buf != null &&
                                out != null) {
                            log.println("writing " + (buf.length*4+8) + " bytes");
                            
                            out.writeInt(image.getWidth());
                            out.writeInt(image.getHeight());
                            out.writeObject(buf);
                            out.flush();
                            
                            status = true;
                            log.println("stored cached raster image " + fname);
                        }
                        else
                            log.aprintln("could not create output file");
                        
                        if (out != null)
                            out.close();
                    }
                }
            }
            catch (Throwable e) {
                log.aprintln(e);
            }
        }
        
        return status;
    }
    
    /**
     ** Loads image stored as raster data.
     **
     ** Only useful for temporary caching of image data during a
     ** particular execution cycle, not between program launches.
     **/
    protected BufferedImage loadRasterImage(String fname)
    {
        BufferedImage img = null;
        boolean status = false;
        
        log.println("trying to load " + fname);
        
        if (fname != null) {
            try {
                File inputFile = new File(fname);
                
                if (inputFile != null) {
                    if (inputFile.exists() &&
                            inputFile.isDirectory())
                    {
                        log.aprintln("Could not load image: " + fname + " is a directory");
                        return null;
                    }
                    
                    inputFile = null;
                    ObjectInputStream in = new ObjectInputStream(new FileInputStream(fname));
                    
                    if (in != null) {
                        int width = in.readInt();
                        int height = in.readInt();
                        int[] buf = (int[]) in.readObject();
                        
                        if (buf == null) {
                            log.aprintln("no image data in file " + fname);
                        }
                        else if (width < 1 ||
                                height < 1 ||
                                buf.length != width * height)
                        {
                            log.aprintln("bad image dimensions " + fname);
                            log.aprintln("width = " + width + "  height = " + height + "  data length = " + buf.length);
                        }
                        else {
                            log.println("read " + (buf.length*4+8) + " bytes");
                            
                            img = Util.newBufferedImage(width, height);
                            if (img != null) {
                                WritableRaster raster = img.getRaster();
                                if (raster == null) {
                                    log.aprintln("could not create raster");
                                    img = null;
                                }
                                else if (raster.getTransferType() != DataBuffer.TYPE_INT) {
                                    log.aprintln("wrong image type");
                                    img = null;
                                }
                                else {
                                    raster.setDataElements(0, 0, width, height, buf);
                                    
                                    status = true;
                                    log.println("loaded cached image " + fname);
                                }
                            }
                            else
                                log.aprintln("could not create blank image");
                        }
                    }
                    else
                        log.println("could not open file " + fname);
                    
                    if (in != null)
                        in.close();
                    
                    if (img == null)
                        log.println("could not load image");
                }
            }
            catch (Throwable e) {
                log.aprintln(e);
            }
        }
        
        return img;
    }
    
    
    protected abstract class Frame
    {
        Rectangle2D where;
        int   renderPPD; 
        int   projHash;
        protected BufferedImage dstImage;
        protected String cachedImageFrameFilename = null;
        protected String imageType = "";
        
        abstract BufferedImage getImage();
        
        Frame(Rectangle2D where, int renderPPD, int projHash)
        {
            this.where = where;
            this.renderPPD = renderPPD;
            this.projHash = projHash;
        }
        
        Frame(Rectangle2D where, int renderPPD, int projHash, String imageType)
        {
            this.where = where;
            this.renderPPD = renderPPD;
            this.projHash = projHash;
            this.imageType = imageType;
        }
        
        protected boolean isImageCached()
        {
            return  dstImage != null;
        }
        
        protected void releaseImage()
        {
            if (dstImage != null)
                dstImage = null;
        }
        
        protected void storeCachedImageFrame(int band, int frame, int renderPPD)
        {
            if (dstImage != null) {
                String fname = getCachedImageFrameName(band, frame, renderPPD);
                
                log.println("before saving PNG image");
                if (savePngImage(fname, dstImage))
                    cachedImageFrameFilename = fname;
                log.println("after saving PNG image");
            }
        }
        
        protected BufferedImage getCachedImageFrame(int band, int frame, int renderPPD)
        {
            BufferedImage img = null;
            
            String fname = getCachedImageFrameName(band, frame, renderPPD);
            if (fname != null)
                img = StampImageFactory.loadImage(fname);
            
            return img;
        }
        
        protected String getCachedImageFrameName(int band, int frame, int renderPPD)
        {
            if (getFilename() != null &&
                    StampImage.STAMP_CACHE != null)
                return StampImage.STAMP_CACHE + getFilename() + "_" + projHash + "_" + band + "_" + frame + 
                "_" + renderPPD + "_" + imageType + CACHED_IMAGE_SUFFIX;
            else
                return null;
        }
    }
    
    
    /**
     * Returns the default height of the image frames.
     * 
     * @return Returns the default height of an image frame;
     * returns 0 if there is an error.
     * 
     * @see #getFrameSize(int, int)
     */ 
    protected abstract int getFrameSize();
    
    /**
     * Returns the exact height of the image frame for the specified
     * frame of the specified image band.  Subclasses may need to override;
     * the default implementation calls {@link #getFrameSize()}.
     * 
     * @param band desired image band; for single-banded images, this should
     * be 0. 
     * @param frame frame number, starting from 0.
     * 
     * @return Returns the exact height of the specified image frame;
     * returns 0 if there is an error.
     * 
     * @see #getFrameSize()
     */ 
    protected int getFrameSize(int band, int frame)
    {
        return getFrameSize();
    }
    
    /**
     * Returns the maximum rendering resolution for image frames
     * created from this instance.  Subclass implementations should 
     * address needs for data visualization and performance/memory
     * optimization.
     */
    protected abstract int getMaxRenderPPD();
    
    /**
     * Returns the minimum rendering resolution for image frames
     * created from this instance.  Subclasses should override as
     * needed to address special optimization needs for performance, 
     * memory, or data visualization.
     */
    protected int getMinRenderPPD()
    {
        return DEFAULT_MIN_RENDER_PPD;
    }
    

    /**
     * Returns only a previously-created image frame at its
     * existing resolution.
     */
    synchronized protected Frame fastGetImageFrame(int band, int frame)
    {
        if (frameCache != null)
            return frameCache[band][frame];
        else
            return null;
    }
    
    synchronized protected Frame getImageFrame(int band, int frame, int renderPPD, Shape worldWin)
    {
        return getImageFrame(band, frame, renderPPD, getMaxRenderPPD(), worldWin);
    }
    
    synchronized protected Frame getImageFrame(int band, int frame, int renderPPD, 
                                               int maxRenderPPD, Shape worldWin)
    {
        if (band >= bandCount)
            return null;
        
        if(frameCache == null)
            frameCache = new Frame[bandCount][getPoints(band).length / 4];
        
        if (frameCells == null)
            frameCells = new GridDataStore.Cell[bandCount][getPoints(band).length / 4];
        
        // Check that the target rendering resolution is within the minimum and
        // maximum allowed for this image.  If it is not, bound it to within the
        // valid range.  This prevents unnecessarily creating a new frame 
        // when a current frame already has the minimal/maximal resolution.  This also
        // prevents recreating a frame image at less than minimal resolution when
        // such an image may not be cached in memory but is cached on disk, e.g.,
        // as occurs in the MocImage subclass implementation.
        int minPPD = getMinRenderPPD();
        if (renderPPD < minPPD)
            renderPPD = minPPD;
        else if (renderPPD > maxRenderPPD)
            renderPPD = maxRenderPPD;
        
        // Create/recreate the frame if:
        //
        // 1. It does not exist, OR
        // 2. The frame exists but was rendered under a different projection, OR
        // 3. The frame exists and intersects the current display but 
        //    frame does not have high enough resolution, OR
        // 4. The frame exists and intersects the current display but
        //    the frame image itself has not been rendered yet and the
        //    frame resolution is different (higher/lower) than the desired
        //    resolution (this prevents creating an image at an unnecessarily
        //    high resolution).
        if(frameCache[band][frame] == null ||
                frameCache[band][frame].projHash != Main.PO.getProjectionSpecialParameters().hashCode() ||
                ( worldWin.intersects(frameCache[band][frame].where) &&
                        ( frameCache[band][frame].renderPPD < renderPPD 
                                ||
                                ( !frameCache[band][frame].isImageCached() &&
                                        frameCache[band][frame].renderPPD != renderPPD))))
            frameCache[band][frame] = createImageFrame(band, frame, renderPPD, maxRenderPPD);
        
        return  frameCache[band][frame];
    }
    
    protected Frame createImageFrame(final int band, final int frame, int renderPPD, int maxRenderPPD)
    {
        return createImageFrame(band, frame, renderPPD, maxRenderPPD, 
                                getFrameSize());
    }
    
    protected Frame createImageFrame(final int band, final int frame, 
                                     int renderPPD, final int maxRenderPPD, 
                                     final int frameSize)
    {
        log.println("Band #" + band + " Frame #" + frame);
        
        // Grab the raw image for the band
        final UniformImage srcImage = getUniformImage(band);
        if(srcImage == null) {
            log.aprintln("(" + filename + ") Invalid band number: " + band);
            return  null;
        }
        
        // Grab the frame boundary points
        final Point2D[] pts = getPoints(band);
        frameCount = pts.length / 4 - 1;
        if(frame >= frameCount) {
            log.aprintln("(" + filename + ") Invalid frame number: " + frame);
            return  null;
        }
        
        // Calculate the range of the image data containing our frame
        final int thisFrameSize = getFrameSize(band, frame);
        final Rectangle srcRange;
        try {
            srcRange = new Rectangle(0, frame * frameSize,
                                     srcImage.getWidth(),
                                     thisFrameSize);
            
            if (!validGeometryInfo(pts)) {
                log.aprintln("framesize is incompatible: " +
                             " frameCount=" + frameCount + " frameSize=" + frameSize +
                             " frameCount*frameSize = " + frameCount * frameSize);
                return null;
            }
            
            // Store information for frame src
            frameWidth = srcImage.getWidth();
            if (frame == frameCount-1)
                lastFrameHeight = thisFrameSize;
            else
                frameHeight = thisFrameSize;
        }
        catch (Exception e) {
            log.aprintln(e);
            return null;
        }
        
        frameCells[band][frame] = new GridDataStore.Cell(
                                                         new HVector(pts[(frame+1)*4  ]),
                                                         new HVector(pts[(frame+1)*4+1]),
                                                         new HVector(pts[(frame+1)*4+3]),
                                                         new HVector(pts[(frame+1)*4+2]));
        
        // Target resolution of projected image data
        int minPPD = getMinRenderPPD();
        if (renderPPD < minPPD)
            renderPPD = minPPD;
        else if (renderPPD > maxRenderPPD)
            renderPPD = maxRenderPPD;
        
        log.println("Creating image frame at renderPPD=" + renderPPD);
        log.println(frameCells[band][frame].getWorldBounds());
        Frame ff = new Frame(frameCells[band][frame].getWorldBounds(), renderPPD, 
                             Main.PO.getProjectionSpecialParameters().hashCode(), IMAGE_FRAME_TYPE)
                             {
            synchronized BufferedImage getImage()
            {
                if(dstImage != null &&
                        cachedImageFrameFilename != null &&
                        cachedImageFrameFilename.equals(getCachedImageFrameName(band, frame, this.renderPPD)) )
                    return  dstImage;
                
                dstImage = getCachedImageFrame(band, frame, this.renderPPD);
                if (dstImage != null)
                    return dstImage;
                
                log.println("PROJECTING FRAME " + frame + " OF " + filename);
                
                // Determine the size of the projected frame image
                int dstW = (int) Math.ceil(where.getWidth()  * this.renderPPD);
                int dstH = (int) Math.ceil(where.getHeight() * this.renderPPD);
                log.println(dstW + " x " + dstH);
                dstImage = Util.newBufferedImage(dstW, dstH);
                
                if (dstImage == null) {
                    log.aprintln("out of memory");
                    return null;
                }
                
                
                /////// VARIABLES FOR THE for() LOOP BELOW
                // worldPt: Stores a point location in world coordinates
                // srcPt:   Stores a point location in the source image data
                // unitPt: Stores a point location in the unit square
                Point2D.Double worldPt = new Point2D.Double();
                Point srcPt = new Point();
                Point2D.Double unitPt = new Point2D.Double();
                HVector spatialPt = new HVector();
                
                /////// CONSTANTS FOR THE for() LOOP BELOW
                // unitSquare: For clipping points to within the unit square
                // baseX: Pixel-wise world coordinate origin of the destination
                // baseY: Pixel-wise world coordinate origin of the destination
                Rectangle2D unitSquare = new Rectangle2D.Double(0, 0, 1, 1);
                double baseX = where.getMinX();
                double baseY = where.getMaxY(); // image y coords run top-down
                double X_ZERO = -0.5 / dstW;
                double Y_ZERO = -0.5 / dstH;
                double X_ONE = 1 + 0.5 / dstW;
                double Y_ONE = 1 + 0.5 / dstH;
                
                log.println("where.width = " + where.getWidth() + "  where.height = " + where.getHeight());
                for(int i=0; i<dstW; i++)
                    for(int j=0; j<dstH; j++) {
                        // Destination image coordinate to world coordinates.
                        worldPt.x = baseX + (double) i / this.renderPPD;
                        worldPt.y = baseY - (double) j / this.renderPPD;
                        
                        // Convert from world coordinates to spatial
                        // coordinates at the center of the pixel.
                        spatialPt.fromLonLat(
                                             Main.PO.convWorldToSpatial(worldPt.getX(),
                                                                        worldPt.getY())
                        );
                        
                        // Uninterpolate from spatial coordinates to the unit
                        // square.
                        frameCells[band][frame].uninterpolate(spatialPt, unitPt);
                        
                        if(unitPt.x < 0)
                            if(unitPt.x >= X_ZERO)
                                unitPt.x = 0;
                            else
                                continue;
                        else if(unitPt.x > 1)
                            if(unitPt.x <= X_ONE)
                                unitPt.x = 1;
                            else
                                continue;
                        
                        if(unitPt.y < 0)
                            if(unitPt.y >= Y_ZERO)
                                unitPt.y = 0;
                            else
                                continue;
                        else if(unitPt.y > 1)
                            if(unitPt.y <= Y_ONE)
                                unitPt.y = 1;
                            else
                                continue;
                        
                        /*
                         // If we're outside the unit square, then we're
                          // processing a screen pixel that isn't covered by the
                           // image.
                            if(unitPt.x < 0  ||  unitPt.x > 1  ||
                            unitPt.y < 0  ||  unitPt.y > 1  )
                            continue;
                            */
                        // Finally, convert from unit square coordinates to
                        // source image pixel coordinates.
                        srcPt.setLocation(
                                          (int)(   unitPt.x *(srcRange.width-1) ) + srcRange.x,
                                          (int)((1-unitPt.y)*(srcRange.height-1)) + srcRange.y);
                        
                        // Draw the pixel in the destination buffer!
                        try {
                            dstImage.setRGB(i, j, srcImage.getRGB(srcPt.x,
                                                                  srcPt.y));
                        }
                        catch (Exception e) {
                            log.aprintln("exception in frame #" + frame + " with frameSize=" + frameSize +
                                         " while drawing pixel at i=" + i + " j=" + j);
                            log.aprintln(e);
                            return dstImage;
                        }
                    }
                
                storeCachedImageFrame(band, frame, this.renderPPD);
                return  dstImage;
            }
                             }
        ;
        return  ff;
    }
    
}
