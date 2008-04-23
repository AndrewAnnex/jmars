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
import java.io.*;
import java.net.*;


/**
 * Abstract factory defining framework for creating instances of 
 * {@link GenericImage} subclasses from standard industry
 * image formats contained in* files, URLs, and any {@link Stamp} 
 * which contains a valid URL.
 * <p>
 * Supports any image format that is supported by the 
 * {@link java.awt.Toolkit#createImage(String)} and
 * {@link java.awt.Toolkit#createImage(URL)} methods.
 * <p>
 * Subclasses must implement the {@link #createImage(BufferedImage, String)} method 
 * to provide standard way to create instances of a {@link GenericImage}
 * subclass.  This method is used exclusively throughout this class for all image
 * creation.  Additionally, the {@link #createImage()} method must be implemented
 * to support naming functionality in {@link #getCachedName} method.
 * <p>
 * Subclasses derived from this class typically support loading of single-band
 * images, but there is no imposed restriction on the number of bands/images
 * which may be contained in files.  However, any subclass which supports
 * multiple bands must override all of the <code>load()</code> methods
 * it intends to support.
 */
public abstract class GenericImageFactory extends StampImageFactory
{
    private static final DebugLog log = DebugLog.instance();
    
    /**
     * 
     */
    public GenericImageFactory()
    {
        super();
    }

    /**
     * Subclasses must implement this to return an empty
     * (no loaded image) instance.  Such instances are 
     * used in conjunction with the {@link #getCachedName(String)}
     * method.
     */
    protected abstract StampImage createImage();
    
    /**
     * Subclasses must implement this to return a valid
     * instance for the image that has been loaded with the
     * specified base image name.
     * 
     * @param image A loaded image.
     * @param filename Base name of image file (no directory path);
     * typically to be used as an internal ID. 
     */
    protected abstract GenericImage createImage(BufferedImage image, String filename);

    
    /**
     * Returns whether or not caching of source image data is enabled. 
     */
    protected abstract boolean isSrcImageCachingEnabled();
    
    /**
     * Loads image from URL.
     * 
     * @param url Any valid URL supported by {@link URL} class.
     */
    public StampImage load(URL url)
    {
        return load(url, null);
    }
    
    /**
     * Loads image from URL; pops up progress monitor dialog as needed.
     * 
     * @param url Any valid URL supported by {@link URL} class.
     * @param parentComponent UI component to reference for dialog creation 
     * purposes; may be null.
     */
    public StampImage load(URL url, Component parentComponent)
    {
        GenericImage genericImage = null;
        
        log.println("GenericImage: loading " + url);
        
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
                genericImage = createImage(image, basename);
                
                if (genericImage != null &&
                    !alreadyCached && 
                    isSrcImageCachingEnabled())
                    genericImage.storeCachedImage(0);
            }
        }
        catch(Throwable e) {
            log.aprintln(e);
            log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + url);
        }
        
        return genericImage;
    }
    
    /**
     * Loads image from file.
     * 
     * @param fname name of file.
     */
    public StampImage load(String fname)
    {
        return load(fname, null);
    }
    
    /**
     * Loads image from file; pops up progress monitor dialog as needed.
     * 
     * @param fname name of file.
     * @param parentComponent UI component to reference for dialog creation 
     * purposes; may be null.
     */
    public StampImage load(String fname, Component parentComponent)
    {
        GenericImage genericImage = null;
        
        log.println("GenericImage: loading " + fname);
        
        if (fname == null)
            return null;
        
        try {
            // Check for cached image on disk first; otherwise
            // load it from named file.
            boolean alreadyCached = false;
            String basename = fileToBasename(fname);
            BufferedImage image = loadImage(getCachedName(basename));
            
            if (image == null)
                image = loadImage(fname, parentComponent);
            else
                alreadyCached = true;
            
            if (image != null) {
                genericImage = createImage(image, basename);
                
                if (genericImage != null &&
                    !alreadyCached && 
                    isSrcImageCachingEnabled())
                    genericImage.storeCachedImage(0);
            }
        }
        catch(Throwable e) {
            log.aprintln(e);
            log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + fname);
        }
        
        return genericImage;
    }
    
    /**
     * Loads image from reference contained in {@link Stamp} instance.
     * 
     * @param s stamp containing valid URL reference.
     */
    public StampImage load(Stamp s)
    {
        return load(s, null);
    }
    
    /**
     * Loads image from reference contained in {@link Stamp} instance; 
     * pops up progress monitor dialog as needed.
     * 
     * @param s stamp containing valid URL reference.
     * @param parentComponent UI component to reference for dialog creation 
     * purposes; may be null.
     */
    public StampImage load(Stamp s, Component parentComponent)
    {
        GenericImage genericImage = null;
        
        if (s == null ||
            s.pdsImageUrl == null)
            return  null;
        
        log.println("GenericImage: loading " + s.pdsImageUrl);
        try {
            boolean alreadyCached = false;
            BufferedImage image = loadImage(getCachedName(getStrippedFilename(s.id)));
            if (image == null)
                image = loadImage(s.pdsImageUrl, parentComponent);
            else
                alreadyCached = true;
            
            if (image != null) {
                genericImage = createImage(image, s.id);
                
                if (genericImage != null &&
                    !alreadyCached && 
                    isSrcImageCachingEnabled())
                    genericImage.storeCachedImage(0);
            }
        }
        catch (Throwable e) {
            log.aprintln(e);
            log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + s.pdsImageUrl);
        }
        
        return genericImage;
    }
    
    /**
     * Returns base filename from URL, stripped of both path and suffix.
     */
    protected static String urlToBasename(URL url)
    {
        String basename = null;
        
        if (url != null)
            basename = fileToBasename(url.getFile());
        
        return basename;
    }
    
    /**
     * Returns base filename, stripped of both path and suffix
     */
    protected static String fileToBasename(String filename)
    {
        String basename = null;
        
        if (filename != null) {
            File file = new File(filename);
            if (file != null) {
                basename = file.getName();
                
                if (basename != null) {
                    int idx = basename.lastIndexOf('.');
                    if (idx > 0)
                        basename = basename.substring(0, idx);
                }
            }
        }
        
        return basename;
    }
    
    /**
     * Subclasses must override if they need special handling for
     * creation of filenames for cached images.  The default 
     * implementation calls {@link #createImage()} to obtain
     * an empty (unloaded image) instance of the subclass, and
     * then it calls #getCachedImageName(int, String)} with the
     * specified base filename.
     * 
     * @param basename Base text string used to form cached
     * filenames, i.e., typically the image ID or similar
     * unique identifier.
     * 
     * @see #createImage()
     * @see GenericImage#getCachedImageName(int, String)
     */
    protected String getCachedName(String basename)
    {
        StampImage empty = createImage();
        return empty.getCachedImageName(0, basename);
    }
    
    /**
     * Returns a filename that has been stripped of any
     * '/' characters; useful for converting stamp IDs
     * that may contain such characters into useable
     * base filenames.
     */
    protected static String getStrippedFilename(String fname)
    {
        String stripped = null;
        
        if (fname != null) {
            StringBuffer buf = new StringBuffer();
            if (buf != null) {
                char c;
                
                for (int i=0; i < fname.length(); i++)
                    if ( (c = fname.charAt(i)) != '/' )
                        buf.append(c);
                    
                stripped = buf.toString();
            }
        }
        
        return stripped;
    }
    
}
