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

import edu.asu.jmars.util.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.border.*;
import java.text.*;
import javax.vecmath.*;
import javax.media.j3d.*;
import com.sun.j3d.utils.behaviors.vp.*;

public class PlatformBehavior extends ViewPlatformAWTBehavior {

    private static DebugLog log = DebugLog.instance();

    private final Vector3f zeroVector  = new Vector3f( 0.0f, 0.0f, 0.0f);

    private Point2D     pressPoint    = new Point2D.Float(0,0);
    private boolean     firstShift    = true;
    private boolean     firstNoShift  = true;

    private PlatformProperties props  = new PlatformProperties();
    private Vector3f    transVect     = new Vector3f();
    private Vector3f    angleVect     = new Vector3f();
    private Transform3D tVect         = new Transform3D();
    private Transform3D tXRot         = new Transform3D();
    private Transform3D tYRot         = new Transform3D();
    private Transform3D tZRot         = new Transform3D();
    private Transform3D tTrans        = new Transform3D();
    private AxisAngle4d y_axisAngle4d = new AxisAngle4d();
    private AxisAngle4d x_axisAngle4d = new AxisAngle4d();
    private AxisAngle4d z_axisAngle4d = new AxisAngle4d();


    /**
     * constructor
     */
    public PlatformBehavior(Canvas3D aCanvas, PlatformProperties p)
    {
	super(aCanvas,MOUSE_MOTION_LISTENER|MOUSE_LISTENER);
	aCanvas.requestFocus();

	props.loc      = p.loc;
	props.rot      = p.rot;
	props.deltaRot = p.deltaRot;
	props.deltaLoc = p.deltaLoc;
    }
    
    
    public PlatformProperties getProps(){
	return props;
    }
    
    /**
     * this method is required by the superclass.  It takes the parameters of 
     * the transform that have been set up by other methods and does the deed
     * of translating and rotation the scene.
     */
    protected void integrateTransforms()
    {
	transVect.set( (props.loc.x + props.deltaLoc.x),
		       (props.loc.y + props.deltaLoc.y),
		       (props.loc.z + props.deltaLoc.z));
	tTrans.set(transVect);	

	angleVect.set( -(props.deltaRot.x + props.rot.x), 
		       props.deltaRot.y + props.rot.y,
		       props.deltaRot.z + props.rot.z);

	y_axisAngle4d.set(1.0,0.0,0.0, angleVect.y);
	x_axisAngle4d.set(0.0,1.0,0.0, angleVect.x);
	z_axisAngle4d.set(0.0,0.0,1.0, angleVect.z);
	tYRot.set( y_axisAngle4d);
	tXRot.set( x_axisAngle4d);
	tZRot.set( z_axisAngle4d);

	tVect.set( zeroVector);
	tVect.mul( tZRot);
	tVect.mul( tXRot);
	tVect.mul( tYRot);
	tVect.mul( tTrans);

	targetTransform = tVect;
	vp.getViewPlatformTransform().setTransform(tVect);
    }


    /**
     * resets the scene to its orientation when it was first created.
     */
    public void goHome(){
	props.reset();

    }

 
    // reqd as part of parent class, but all events are procesed elsewhere
    protected void processAWTEvents(java.awt.AWTEvent[] events) { }


    // gets the "current" point and initialize things
    public void mousePressed(MouseEvent me){
	pressPoint.setLocation( me.getPoint());
	firstShift = true;
	firstNoShift = true;
    }

    // deltas the initial point position, sets the new at-point, and initializes the
    // transitory point positions.
    public void mouseReleased(MouseEvent me){

	props.loc.add( props.deltaLoc );
	props.deltaLoc.set( 0.0f, 0.0f, 0.0f);

	props.rot.add( props.deltaRot);
	props.deltaRot.set( 0.0f, 0.0f, 0.0f);
	
	integrateTransforms();
    }

    // translates the scene based on where the mouse is dragged to  
    public void mouseDragged ( MouseEvent me) {

	Point2D dragPoint = new Point2D.Float((float)me.getPoint().getX(), (float)me.getPoint().getY());
	
	boolean leftButtonPressed   = (me.getModifiers() & MouseEvent.BUTTON1_MASK) != 0;
	boolean middleButtonPressed = (me.getModifiers() & MouseEvent.BUTTON2_MASK) != 0;
	boolean rightButtonPressed  = (me.getModifiers() & MouseEvent.BUTTON3_MASK) != 0;
	

	// if the shift key is down, translate.
	if (me.isShiftDown()){ 
	    // If this is the first time we have done this since the last
	    // mouse press or release, then initialize the press point.
	    if (firstShift == true) {
		mouseReleased( me );
		mousePressed( me);
		firstShift        = false;
	    }
	    
	    // translate along X and Y.  Because the widget might be rotated, we 
	    // have to rotate the deltas as well.
	    if (leftButtonPressed){
		props.deltaLoc.x = (float)(pressPoint.getX() - dragPoint.getX());
		props.deltaLoc.y = (float)(dragPoint.getY() - pressPoint.getY());
	    }
	    // translate along Z
	    else if (rightButtonPressed) {
		props.deltaLoc.z = (float)(pressPoint.getY() - me.getPoint().getY());
	    }
	}
		
	// no buttons pressed, rotate.
	else { 
	    // Left button rotates about the X-axis or Y-axis. Right button rotates
	    // about the Z.
	    
	    // if this is the first time this block has been run since key  pressed or
	    // the mouse button was either pressed or released, then reset the pressed point.
	    if (firstNoShift == true) {
		mouseReleased(me);
		mousePressed(me);
		firstNoShift      = false;
	    }

	    // rotate about the X and Y axes.
	    if (leftButtonPressed) {
		props.deltaRot.x = (float)Math.toRadians((float)(pressPoint.getX() - dragPoint.getX()));
		props.deltaRot.y = (float)Math.toRadians((float)(pressPoint.getY() - dragPoint.getY()));
	    } 

	    // rotate about the Z axis.
	    else if (rightButtonPressed){
		props.deltaRot.z = (float)Math.toRadians((float)(pressPoint.getX() - dragPoint.getX()));
	    }
	    
	}


	// Noel request: middle button is overloaded to zoom, regardless of any key-press state.
	if (middleButtonPressed){
	    props.deltaLoc.z = (float)(pressPoint.getY() - me.getPoint().getY());
	}

	// Time to draw the donuts.
	integrateTransforms();
    }

}
