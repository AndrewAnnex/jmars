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


/**
 * builds the 3D scene from elevation and image data.
 */
package edu.asu.jmars.layer.threed;

import edu.asu.jmars.util.*;

import java.awt.*;
import java.awt.image.*;
import javax.vecmath.*;
import javax.media.j3d.*;
import com.sun.j3d.utils.geometry.*;
import com.sun.j3d.utils.image.*;

public class ElevationModel extends BranchGroup {

    private static DebugLog log = DebugLog.instance();

    private float      depthWidth   = 0;
    private float      depthHeight  = 0;

    // constructor.  Note that there is no argument checking.  It is assumed that the arguments
    // were verified before this class was invoked. 
    public ElevationModel( Elevation el, BufferedImage image, float scale, Appearance appearance) {

	// get depth file stuff.
	int [][] elevations  = el.getPixelArrayInt();
 	depthWidth       = (float)el.getWidth();
	depthHeight      = (float)el.getHeight();

	// get image file stuff.
	int imageHeight = image.getHeight();
	int imageWidth = image.getWidth();
	Color3f[][] imageData = new Color3f[imageHeight][imageWidth];
	ColorModel cm = image.getColorModel();
	for(int y=0; y<imageHeight; y++) {
	    for(int x=0; x<imageWidth; x++){
		int pixel = image.getRGB(x, imageHeight - y -1);
		int r = cm.getRed(pixel);
		int g = cm.getGreen(pixel);
		int b = cm.getBlue(pixel);
		imageData[y][x] = new Color3f(r/255.0f, g/255.0f, b/255.0f);
	    }
	}
	    
	// the dimensions of both files must be the same.
	if(imageHeight != (int)depthHeight || imageWidth != (int)depthWidth){
	    log.aprintln("dimension mismatch: " +
			 " texture map (" + imageWidth + "," + imageHeight + ")  " +
			 " depth map ("   + depthWidth + "," + depthHeight + ")" );
	    return;
	}


	
	float left     = -depthWidth/2.0f;
	float right    =  depthWidth/2.0f;
	float bottom   =  depthHeight/2.0f;
	float top      = -depthHeight/2.0f;
	int   rowRatio = (int)(depthHeight-1);
	int   colRatio = (int)(depthWidth-1);

	// set the geometry of the shape
	ElevationSegment elSeg = new ElevationSegment(elevations, imageData,
						      rowRatio,colRatio, 
						      scale, left, right, bottom, top);

	// set the appearance of the shape
	elSeg.setAppearance( appearance);


	addChild( elSeg);

	// clean up (for garbage collection's sake)
	for(int y=0; y<imageHeight; y++) {
	    for(int x=0; x<imageWidth; x++){
		imageData[y][x] = null;
	    }
	}
	imageData = null;


    } // end: constructor
    
  
    
    public float getModelLength()
    {
	return (float)depthWidth;
    }
    
}



