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

import java.awt.image.*;

/** 
 ** The UniformImage class is a thin encapsulation of
 ** multiple image formats.  It wraps a small subset of
 ** methods around an underlying instance of one several
 ** image class types.
 **
 ** Instances of this class may only be created as
 ** wrappers around an instance of one of the underlying
 ** supported classes.  Subclasses may provide
 ** equivalent functionality.
 **
 ** Current supported image types are:
 **
 **    @link java.awt.image.BufferedImage
 **/
public class UniformImage
{
    protected BufferedImage bImage = null;

    protected UniformImage()
    {
    }

    // Wraps passed image but does not copy underlying instance.
    public UniformImage(BufferedImage image)
    {
	bImage = image;
    }

    public int getHeight()
	throws Exception
    {
	if (bImage != null)
	    return bImage.getHeight();
	else
	    return -1;
    }

    public int getWidth()
	throws Exception
    {
	if (bImage != null)
	    return bImage.getWidth();
	else
	    return -1;
    }

    // Returns 32-bit RGB color value at specified pixel location; may
    // include alpha component.
    public int getRGB(int x, int y)
	throws Exception
    {
	if (bImage != null)
	    return bImage.getRGB(x, y);
	else
	    throw new Exception("no contained image");
    }
}
