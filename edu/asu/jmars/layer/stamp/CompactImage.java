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

import java.awt.*;

/** 
 ** The CompactImage class is a thin non-derived replacement
 ** for BufferedImage that that wraps a small subset of
 ** BufferedImage methods around a simple single-band
 ** image data buffer intended for grayscale images.
 **
 ** @see UniformImage
 **/
public class CompactImage extends UniformImage
{
    private final int dataW;
    private final int dataH;
    private final byte[] data;
    private final int offset;

    /*
     * Creates blank (zero-intensity) 8-bit intensity image with 
     * specified width, height, and pixel data.  Image is best
     * suited for grayscale or other single-band use.
     *
     * @param dataW  image width (x-axis)
     * @param dataH  image height (y-axis)
     */
    public CompactImage(int dataW, int dataH)
    {
	this.dataW = dataW;
	this.dataH = dataH;
	this.data = new byte[dataW * dataH];
	this.offset = 0;
    }

    /*
     * Creates 8-bit intensity image with specified width, height, and pixel data.
     * Image is best suited for grayscale or other single-band use.
     *
     * @param dataW  image width (x-axis)
     * @param dataH  image height (y-axis)
     * @param data   image data in single-band, 8-bit, row-major (y-axis-major)
     * format.
     * @param offset byte offset in data buffer to start of image (location 0,0)
     */
    public CompactImage(int dataW, int dataH,
			byte[] data, int offset)
    {
	this.dataW = dataW;
	this.dataH = dataH;
	this.data = data;
	this.offset = offset;
    }

    public int getHeight()
    {
	return dataH;
    }

    public int getWidth()
    {
	return dataW;
    }

    // Returns 24-bit RGB color value at specified pixel location; no alpha
    // component.
    public int getRGB(int x, int y)
	throws Exception
    {
	if (x >= dataW ||
	    y >= dataH ||
	    x < 0 ||
	    y < 0)
	    throw new Exception("Invalid location: x=" + x + " y=" + y);

	int b = data[x + y * dataW + offset] & 0xFF;
	return new Color(b, b, b).getRGB();
    }

    // Returns raw 8-bit pixel value at specified location.
    public byte getPixel(int x, int y)
	throws Exception
    {
	if (x >= dataW ||
	    y >= dataH ||
	    x < 0 ||
	    y < 0)
	    throw new Exception("Invalid location: x=" + x + " y=" + y);

	return (byte)(data[x + y * dataW + offset] & 0xFF);
    }

    // Stores raw 8-bit pixel value at specified location.
    public void setPixel(int x, int y, byte pixel)
	throws Exception
    {
	if (x >= dataW ||
	    y >= dataH ||
	    x < 0 ||
	    y < 0)
	    throw new Exception("Invalid location: x=" + x + " y=" + y);

	data[x + y * dataW + offset] = pixel;
    }
}
