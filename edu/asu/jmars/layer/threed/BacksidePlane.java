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
 * a simple enough shape consisting of a quadrilateral with a lovely Odeon theater-like coloring.
 *
 * @author: James Winburn MSFF-ASU 11/04
 */
package edu.asu.jmars.layer.threed;

import edu.asu.jmars.util.*;

import java.awt.*;
import java.awt.image.*;
import javax.vecmath.*;
import javax.media.j3d.*;
import com.sun.j3d.utils.geometry.*;
import com.sun.j3d.utils.image.*;

public class BacksidePlane extends Shape3D {

    private static DebugLog log = DebugLog.instance();

    BacksidePlane( float w, float h, float m) {
        this.setGeometry(createGeometry( w, h, m));
        this.setAppearance( new Appearance());
    }    

    Geometry createGeometry(float depthWidth, float depthHeight, float mean){

        QuadArray plane = new QuadArray(4, GeometryArray.COORDINATES | GeometryArray.COLOR_3);

	float right        = -depthWidth/2.0f;
	float left         =  depthWidth/2.0f;
	float bottom       =  depthHeight/2.0f;
	float top          = -depthHeight/2.0f;

        Point3f p = new Point3f();
        p.set( left, top,  mean);
        plane.setCoordinate(0, p);
        p.set( left, bottom,  mean);
        plane.setCoordinate(1, p);
        p.set(  right, bottom,  mean);
        plane.setCoordinate(2, p);
        p.set(  right, top, mean);
        plane.setCoordinate(3, p);

	plane.setColor(0, new Color3f(1.0f, 0.5f, 0.5f));
	plane.setColor(1, new Color3f(0.5f, 0.0f, 0.5f));
	plane.setColor(2, new Color3f(0.5f, 0.5f, 0.5f));
	plane.setColor(3, new Color3f(0.5f, 0.5f, 0.5f));

        return plane;
    }


}



