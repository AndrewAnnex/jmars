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

import java.awt.Color;
import java.io.Serializable;

import javax.vecmath.Vector3f;

import edu.asu.jmars.layer.LViewSettings;

/**
 * a class for abstracting the settings for the application.  This is serializable so that
 * a JMARS layer may save or load the attributes directly (i.e. without the necessity of 
 * an intermediate class.)
 */
class ThreeDSettings extends LViewSettings implements Serializable
{
    /** should the directional light be on? */
    boolean             directionalLightBoolean   = false;
    
    /** the direction of the directional light.*/
    Vector3f            directionalLightDirection = new Vector3f(0.0f, 0.0f, 1.0f);

    /** the color of the directional light */
    Color               directionalLightColor     = new Color( 128,128,128);

    /** the color of the background. */
    Color               backgroundColor           = new Color( 0, 0, 0);

    /** the initial exaggeration of the scene. */
    String              zScaleString              = "1.0";

    /** should a backplane be defined? */
    boolean             backplaneBoolean          = false;
}

