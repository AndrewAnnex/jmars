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
import java.awt.image.*;


/**
 * Factory for creating instances of {@link VikingImage} from
 * image data contained in files, URLs, and any {@link Stamp} 
 * which contains a valid URL.
  * 
 * @author hoffj MSFF-ASU
*/
public class VikingImageFactory extends GenericImageFactory
{
    private static final DebugLog log = DebugLog.instance();
    private static final String VIKING_SRC_CACHING_STR = Config.get("viking.src_caching");
    private static boolean srcImageCaching = true;

    static
    {
        try {
            // Determine whether to enable/disable caching of original source image data
            // for Viking stamps.
            if (VIKING_SRC_CACHING_STR != null) {
                if (VIKING_SRC_CACHING_STR.trim().equalsIgnoreCase("true"))
                    srcImageCaching = true;
                else if (VIKING_SRC_CACHING_STR.trim().equalsIgnoreCase("false"))
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
    public VikingImageFactory()
    {
        super();
    }

    /**
     * @see edu.asu.jmars.layer.stamp.GenericImageFactory#createImage()
     */
    protected StampImage createImage()
    {
        return new VikingImage();
    }

    /**
     * @see edu.asu.jmars.layer.stamp.GenericImageFactory#createImage(java.awt.image.BufferedImage, java.lang.String)
     */
    protected GenericImage createImage(BufferedImage image, String filename)
    {
        return new VikingImage(image, filename);
    }

    /**
     * Returns whether or not caching of source image data is enabled. 
     */
    protected boolean isSrcImageCachingEnabled()
    {
        return srcImageCaching;
    }
    
}
