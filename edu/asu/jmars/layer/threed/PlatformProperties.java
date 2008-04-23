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
 * controls the rotation and translation behavior of the scene in the ThreeD layer viewer.
 *
 * @author James Winburn MSFF-ASU 11/04
 */
package edu.asu.jmars.layer.threed;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;
import java.text.*;
import javax.vecmath.*;
import javax.media.j3d.*;
import com.sun.j3d.utils.behaviors.vp.*;

public class PlatformProperties {
    static float    HOME_XANGLE = 0;
    static float    HOME_YANGLE = (float)Math.PI;
    static float    HOME_ZANGLE = 0;
    static float    HOME_X      = 0;
    static float    HOME_Y      = 0;
    static float    HOME_Z      = 900;

    public Vector3f    loc         = null;
    public Vector3f    rot         = null;
    public Vector3f    deltaRot    = null;
    public Vector3f    deltaLoc    = null;

    public PlatformProperties (){
	loc         = new Vector3f(HOME_X,HOME_Y,HOME_Z);
	rot         = new Vector3f(HOME_XANGLE, HOME_YANGLE, HOME_ZANGLE);
	deltaRot    = new Vector3f(0.0f, 0.0f, 0.0f);
	deltaLoc    = new Vector3f(0.0f, 0.0f, 0.0f);
    }
    

    public void reset(){
	loc.set( HOME_X, HOME_Y, HOME_Z);
	deltaLoc.set( 0.0f, 0.0f, 0.0f);
	rot.set( HOME_XANGLE, HOME_YANGLE, HOME_ZANGLE);
	deltaRot.set( 0.0f, 0.0f, 0.0f);
    }
}
