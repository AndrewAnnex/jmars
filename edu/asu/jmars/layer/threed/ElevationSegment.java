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


package edu.asu.jmars.layer.threed;


import javax.vecmath.*;
import javax.media.j3d.*;
import com.sun.j3d.utils.geometry.*;


//import edu.asu.jmars.util.*;

//import java.awt.*;
import java.awt.image.*;
import com.sun.j3d.utils.image.*;

/**
 * ElevationSegment is a specialization of Shape3d. It creates a
 * 3D map of terrain data using an interleaved triangle strip array.
 *  Terrain data is passed into ElevationSegment as a two dimensional
 *  array of elevations.  These are converted into a series of triangle
 * strips to represent their geometry.
 *
 * @author  Mark Pendergast
 * @version 1.0 February 2003
 */

public class ElevationSegment extends Shape3D {

    /** meters between columns of data */
    private float deltaX=0;
    
    /** meters between rows of data */
    private float deltaY=0;
    
    /** first column logical coordinate*/
    private float xStart=0;
    
    /** first row logical coordinate*/
    private float yStart=0;
    
    // vertex data 
    private float[] vertexData;
    private static final int FLOATS_PER_VERTEX= 9;
    private static final int COLOR_OFFSET = 0;
    private static final int NORMAL_OFFSET = 3;
    private static final int COORD_OFFSET = 6;

    
    private InterleavedTriangleStripArray  tStrip;
    
    /**
     * Constructor is reponsible for setting up the appearance/material,
     * computing the triangle strip values (colors, normals, coordinates)
     * stored in vertexData, then creating the actual JAVA 3D geometry.
     *
     *  @param elevations  two dimensional array of terrain elevation data in meters
     *  @param imageData   two dimensional array of terrain colors
     *  @param stopRow     last row of data to use from elevations
     *  @param stopColumn  last column of data to use from elevations
     *  @param exageration amount to exagerate(multiply) elevations by
     *  @param lowX        starting x coordinate in meters
     *  @param highX       stopping x coordinate in meters
     *  @param lowY        starting y coordinate in meters
     *  @param highY       stopping y coordinate in meters
     */
    public ElevationSegment( 
			    int           elevations[][],   
			    Color3f       imageData[][], 
			    int           stopRow, 
			    int           stopColumn, 
			    float         exageration, 
			    float         lowX, 
			    float         highX, 
			    float         lowY, 
			    float         highY 
			     ) {

	// process the 2D elevation array
	int numberOfRows = (int)Math.ceil(stopRow+1);
	int numberOfCols = (int)Math.ceil(stopColumn+1);
	xStart = lowX;
	yStart = lowY;
	deltaX = (highX-lowX)/stopColumn;
	deltaY = (highY-lowY)/stopRow;
	
	// first create an interleaved array of colors,  normals, and points
	try {
	    vertexData = null;
	    vertexData = new float[FLOATS_PER_VERTEX*(numberOfRows)*2*(numberOfCols-1)];
	}
	catch (OutOfMemoryError e){
	    System.out.println("Elevation segment: memory allocation failure");
	    return;
	}

	// populate vertexData a strip at a time
	int row, col; // indexes into the elevations array
	int i;        // index into vertexData

	for( col = 0, i = 0; col <= stopColumn-1; col++) {
	    for(row = 0; row <= stopRow; row++){

		if(row+1 > stopRow){ // always use last data line to prevent seams
		    row = stopRow;
		}
		setColor(     i+COLOR_OFFSET,   imageData[row][col]);
		setCoordinate(i+COORD_OFFSET,   elevations, row,col,exageration);
		i += FLOATS_PER_VERTEX;

		int c = col;
		if(c+1 > stopColumn-1){ // always use last data line to prevent seams
		    c = stopColumn-1;
		}

		setColor(     i+COLOR_OFFSET,   imageData[row][c+1]);
		setCoordinate(i+COORD_OFFSET,   elevations, row,c+1,exageration);
		i += FLOATS_PER_VERTEX;
	    }
	}


	// create an array of the number of vertices in each strip.
	int[] stripCounts = new int[numberOfCols-1];
	for(int strip = 0; strip < numberOfCols-1; strip++){
	    stripCounts[strip] = (numberOfRows)*2;
	}

	// Create and set the geometry
	tStrip = null;
	tStrip = new InterleavedTriangleStripArray(vertexData.length/FLOATS_PER_VERTEX,
						   GeometryArray.COORDINATES|
						   GeometryArray.COLOR_3|
						   GeometryArray.NORMALS|
						   GeometryArray.BY_REFERENCE|
						   GeometryArray.INTERLEAVED,
						   stripCounts);

	tStrip.setInterleavedVertices(vertexData);
	tStrip.generateNormals(true);
	setGeometry(tStrip);
    }




    // store coordinate data into vertex data array
    //
    //  @param elevations  array of elevations
    //  @param i           index into vertexData to store coordinate
    //  @param row         elevation row
    //  @param col         elevation column
    //  @param stopColumn  first column used in elevations
    //  @param exageration elevation exageration factor
    private void setCoordinate(int i, int[][] elevations, int row, int col, float exageration) {
	vertexData[i]   = (float)((col*deltaX)+xStart);
	vertexData[i+1] = (float)((row*deltaY)+yStart);
	vertexData[i+2] = elevations[row][col]*exageration;
    }



    // store color data into vertex data array, compute color based
    // on the elevation's distance between min and max elevations
    private void setColor(int i, Color3f imageValue){
	vertexData[i]   = imageValue.x;
	vertexData[i+1] = imageValue.y;
	vertexData[i+2] = imageValue.z;
    }


}
