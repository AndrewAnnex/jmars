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
import java.awt.image.*;
import java.net.*;


/**
 * Factory for creating instances of {@link MocImage} from
 * image data contained in files, URLs, and any {@link Stamp} 
 * which contains a valid URL.  Supports specialized 
 * tile-based image caching mechanism used within MocImage class.
 * 
 * @author hoffj MSFF-ASU
 */
public class MocImageFactory extends GenericImageFactory
{
    private static final DebugLog log = DebugLog.instance();
    private static final String MOC_SRC_CACHING_STR = Config.get("moc.src_caching");
    private static boolean srcImageCaching = true;
    

    static
    {
        try {
            // Determine whether to enable/disable caching of original source image data
            // for MOC stamps.
            if (MOC_SRC_CACHING_STR != null) {
                if (MOC_SRC_CACHING_STR.trim().equalsIgnoreCase("true"))
                    srcImageCaching = true;
                else if (MOC_SRC_CACHING_STR.trim().equalsIgnoreCase("false"))
                    srcImageCaching = false;
            }
            
        }
        catch (Exception e) {
            // ignore
        }
    }
    
    /**
     * 
     */
    public MocImageFactory()
    {
        super();
    }

    /**
     * @see edu.asu.jmars.layer.stamp.GenericImageFactory#createImage()
     */
    protected StampImage createImage()
    {
        return new MocImage();
    }

    /**
     * @see edu.asu.jmars.layer.stamp.GenericImageFactory#createImage(java.awt.image.BufferedImage, java.lang.String)
     */
    protected GenericImage createImage(BufferedImage image, String filename)
    {
        return new MocImage(image, filename);
    }

    /**
     * Returns whether or not caching of source image data is enabled. 
     */
    protected boolean isSrcImageCachingEnabled()
    {
        return srcImageCaching;
    }
    
    /**
     * Loads image from URL; pops up progress monitor dialog as needed.  Supports
     * specialized tile-based caching mechanism used in {@link MocImage}.
     * 
     * @param url Any valid URL supported by {@link URL} class.
     * @param parentComponent UI component to reference for dialog creation 
     * purposes; may be null.
     */
    public StampImage load(URL url, Component parentComponent)
    {
        MocImage mocImage = null;
        
        log.println("MocImage: loading " + url);
        
        if (url == null)
            return null;
        
        try {
            // Check for cached image on disk first; otherwise
            // load it from named URL.
            boolean alreadyCached = false;
            String basename = urlToBasename(url);
            BufferedImage image = loadImage(getCachedName(basename));
            
            if (image == null)
                image = loadImage(url, parentComponent);
            else
                alreadyCached = true;
            
            if (image != null) {
                mocImage = new MocImage(image, basename);
                
                if (mocImage != null) {
                    mocImage.storeCachedImage(0, !alreadyCached && srcImageCaching);
                    
                    // Due to memory constraints and multi-stamp
                    // loading scenarios, we immediately release
                    // the image after caching it.
                    mocImage.releaseImage(0);
                }
            }
        }
        catch(Throwable e) {
            log.aprintln(e);
            log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + url);
        }
        
        return mocImage;
    }
    
    /**
     * Loads image from file; pops up progress monitor dialog as needed.  Supports
     * specialized tile-based caching mechanism used in {@link MocImage}.
     * 
     * @param fname Any valid filename.
     * @param parentComponent UI component to reference for dialog creation 
     * purposes; may be null.
     */
    public StampImage load(String fname, Component parentComponent)
    {
        MocImage mocImage = null;
        
        log.println("MocImage: loading " + fname);
        
        if (fname == null)
            return null;
        
        try {
            // Check for cached image on disk first; otherwise
            // load it from named URL.
            boolean alreadyCached = false;
            String basename = fileToBasename(fname);
            BufferedImage image = loadImage(getCachedName(basename));
            
            if (image == null)
                image = loadImage(fname, parentComponent);
            else
                alreadyCached = true;
            
            if (image != null) {
                mocImage = new MocImage(image, basename);
                
                if (mocImage != null) {
                    mocImage.storeCachedImage(0, !alreadyCached && srcImageCaching);
                    
                    // Due to memory constraints and multi-stamp
                    // loading scenarios, we immediately release
                    // the image after caching it.
                    mocImage.releaseImage(0);
                }
            }
        }
        catch(Throwable e) {
            log.aprintln(e);
            log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + fname);
        }
        
        return mocImage;
    }
    
    /**
     * Loads image from URL reference contained in {@link Stamp} instance; 
     * pops up progress monitor dialog as needed.  Supports
     * specialized tile-based caching mechanism used in {@link MocImage}.
     * 
     * @param s stamp containing valid URL.
     * @param parentComponent UI component to reference for dialog creation 
     * purposes; may be null.
     */
    public StampImage load(Stamp s, Component parentComponent)
    {
        MocImage mocImage = null;
        
        if (s == null ||
            s.pdsImageUrl == null)
            return  null;
        
        log.println("MocImage: loading " + s.pdsImageUrl);
        try {
            boolean alreadyCached = false;
            BufferedImage image = loadImage(getCachedName(getStrippedFilename(s.id)));
            if (image == null)
                image = loadImage(s.pdsImageUrl, parentComponent);
            else
                alreadyCached = true;
            
            if (image != null) {
                mocImage = new MocImage(image, s.id);
                
                if (mocImage != null) {
                    mocImage.storeCachedImage(0, !alreadyCached && srcImageCaching);
                    
                    // Due to memory constraints and multi-stamp
                    // loading scenarios, we immediately release
                    // the image after caching it.
                    mocImage.releaseImage(0);
                }
            }
        }
        catch (Throwable e) {
            log.aprintln(e);
            log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + s.pdsImageUrl);
        }
        
        return mocImage;
    }
    
}
