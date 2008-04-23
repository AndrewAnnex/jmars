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

import java.awt.image.BufferedImage;


/**
 * Extends parent class to address caching and memory optimization
 * needs for wrapped instances of {@link java.awt.image.BufferedImage}.
 * <p>
 * This class only includes the ability to retrieve cached images from
 * disk, not to store them.
 * 
 * @author hoffj MSFF-ASU
 */
public class CachedUniformImage extends UniformImage
{
    protected String cacheFilename;
    
    private final int height;
    private final int width;
    
    /**
     * @param image
     */
    public CachedUniformImage(BufferedImage image, String filename)
    throws Exception
    {
        super(image);
        
        if (image == null)
            throw new Exception("null image");
        if (filename == null ||
            filename.trim().length() < 1)
            throw new Exception("null or empty cache filename");
        
        cacheFilename = filename;
        bImage = image;
        height = image.getHeight();
        width = image.getWidth();
    }

    synchronized public BufferedImage getImage()
    throws Exception
    {
        if (bImage == null) {
            bImage = StampImageFactory.loadImage(cacheFilename);
            
            if (bImage == null)
                throw new Exception("could not retrieve image");
            else if (bImage.getHeight() != height ||
                    bImage.getWidth() != width)
                throw new Exception("retrieved image has wrong dimensions");
        }
        
        return bImage;
    }
    
    synchronized public void releaseImage()
    {
        bImage = null;
    }
    
    public int getHeight()
    {
        return height;
    }
    
    public int getWidth()
    {
        return width;
    }
    
    /**
     * Returns 32-bit RGB color value at specified pixel location; may
     * include alpha component.
     */
    synchronized public int getRGB(int x, int y)
    throws Exception
    {
        // Retrieve image via getImage() to ensure it is loaded properly.
        BufferedImage image = getImage();
        if (image != null)
            return image.getRGB(x, y);
        else
            throw new Exception("no image loaded");
    }
    
}
