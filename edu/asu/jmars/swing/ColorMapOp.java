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


package edu.asu.jmars.swing;

import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.image.*;

/**
 * This is a bridge between the ColorMapper and BufferedImage pixels that must
 * be scaled by the mapper. The Java2D LookupOp is the real worker, this only
 * prepares the LookupOp.
 *
 * At static initialization time, a single pixel ARGB image is created to
 * determine what band indices contain the ARGB channels. This result is used
 * to index the ARGB values, which are created by linearly interpolating either
 * the grayscale range, or a sequence of colors if specified in the
 * constructor.
 */
public class ColorMapOp
 {
	private static DebugLog log = DebugLog.instance();

	/**
	 ** The byte-index of each color band in a "native" bitmap's
	 ** 24-bit pixels. To determine which band index of a LookupOp
	 ** affects alpha, for example, use A as an index.
	 **/
	private static final int A,R,G,B;

	/**
	 ** Indicates whether the optimized native BufferedImages use
	 ** premultiplied alphas. This is generally the case under OSX,
	 ** and was added specifically to address some bugs there.
	 **/
	private static final boolean preMultiplied;

	static
	 {
		// Construct a 1x1 image with alpha
		BufferedImage img =
 			GraphicsEnvironment
			.getLocalGraphicsEnvironment()
			.getDefaultScreenDevice()
			.getDefaultConfiguration()
			.createCompatibleImage(1, 1, Transparency.TRANSLUCENT);
		preMultiplied = img.isAlphaPremultiplied();
		img.coerceData(false); // UN-premultiply the pixels, if necessary

		// Create a dummy op to figure out which bands map to which
		// bytes. This will convert any zero-byte pixels to 0x10+band
		// number.
		byte[][] opBands = new byte[4][256];
		opBands[0][0] = 1;
		opBands[1][0] = 2;
		opBands[2][0] = 3;
		opBands[3][0] = 4;

		// Paint in a test pixel with zeros as the ARGB values, then
		// transform it with the op. The result will be an ARGB value
		// composed of bytes that represent op band indices that
		// affected that byte.
		img.setRGB(0, 0, 0);
		LookupOp op = new LookupOp(new ByteLookupTable(0, opBands), null);
		img = op.filter(img, null);

		// For each byte in an ARGB pixel, determine which band number
		// it was affected by in the op.
		int[] argbBands = { -1, -1, -1, -1 };
		int realPixel = img.getRGB(0, 0);
		for(int i=0; i<4; i++)
			argbBands[3-i] = (realPixel >> i*8) & 0xFF;
		A = argbBands[0]-1;
		R = argbBands[1]-1;
		G = argbBands[2]-1;
		B = argbBands[3]-1;

		if(A < 0  ||  R < 0  ||  G < 0  ||  B < 0)
		 {
			log.aprintln("*************************************");
			log.aprintln("*************************************");
			log.aprintln("**** MAJOR IMAGE FUNKINESS, *********");
			log.aprintln("**** TELL MICHAEL!!!!!!!!!! *********");
			log.aprintln("*************************************");
			log.aprintln("*************************************");
			log.aprintln("A=" + A);
			log.aprintln("R=" + R);
			log.aprintln("G=" + G);
			log.aprintln("B=" + B);
			log.aprintStack(-1);
		 }

		log.println("A" + A);
		log.println("R" + R);
		log.println("G" + G);
		log.println("B" + B);
	 }

	private Color[] colors;

	public ColorMapOp()
	 {
	 }

	public ColorMapOp(Color[] colors)
	 {
		this.colors = (Color[]) colors.clone();
	 }

	public ColorMapOp(ColorScale scale)
	 {
		if(!scale.isIdentity())
			colors = scale.getColorMap();
	 }

	public boolean isIdentity()
	 {
		return  colors == null;
	 }

	float _alpha = -1;
	BufferedImageOp _forAlpha;
	public BufferedImageOp forAlpha(float alpha)
	 {
		if(alpha != _alpha)
		 {
			_alpha = alpha;
			_forAlpha = createOp(alpha);
		 }
		return  _forAlpha;
	 }

	private BufferedImageOp createOp(float alpha)
	 {
		byte[][] bytes = new byte[4][256];
		if(isIdentity())
			if(preMultiplied)
				for(int i=0; i<256; i++)
				 {
					bytes[A][i] = (byte) Math.round(alpha * i);
					bytes[R][i] = (byte) Math.round(alpha * i);
					bytes[G][i] = (byte) Math.round(alpha * i);
					bytes[B][i] = (byte) Math.round(alpha * i);
				 }
			else
				for(int i=0; i<256; i++)
				 {
					bytes[A][i] = (byte) Math.round(alpha * i);
					bytes[R][i] = (byte) i;
					bytes[G][i] = (byte) i;
					bytes[B][i] = (byte) i;
				 }
		else
			if(preMultiplied)
				for(int i=0; i<256; i++)
				 {
					Color col = colors[i];
					bytes[A][i] = (byte) Math.round(alpha * i);
					bytes[R][i] = (byte) Math.round(alpha * col.getRed());
					bytes[G][i] = (byte) Math.round(alpha * col.getGreen());
					bytes[B][i] = (byte) Math.round(alpha * col.getBlue());
				 }
			else
				for(int i=0; i<256; i++)
				 {
					Color col = colors[i];
					bytes[A][i] = (byte) Math.round(alpha * i);
					bytes[R][i] = (byte) col.getRed();
					bytes[G][i] = (byte) col.getGreen();
					bytes[B][i] = (byte) col.getBlue();
				 }

		return  new LookupOp(new ByteLookupTable(0, bytes), null);
	 }
 }
