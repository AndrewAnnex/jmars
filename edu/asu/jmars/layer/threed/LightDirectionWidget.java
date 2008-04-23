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
 *
 * Defines a widget that allows for the re-positioning of a directional light.
 *
 * @author James Winburn MSFF-ASU 11/04 
 *  
 */
package edu.asu.jmars.layer.threed;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import javax.vecmath.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.media.j3d.*;
import com.sun.j3d.utils.image.*;
import com.sun.j3d.utils.behaviors.vp.*;
import com.sun.j3d.utils.universe.*;
import com.sun.j3d.utils.geometry.*;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;

abstract class LightDirectionWidget extends JPanel{

    private final float      lightZ       = 1.2f;
    private final Color3f    initialColor = new Color3f(1.0f, 1.0f, 1.0f);
    private final Vector3f   initPosition = new Vector3f(0.0f, 0.0f, lightZ);
  
    private JPanel           holdingPanel = null;
    private PointLight       lightD1      = new PointLight();



    /**
     * This is what the widget is all about. The light direction of the scene is 
     * connected to the widget by defining this abstract method. When the widget is 
     * rotated at all, this method is called and the light direction of the external 
     * light is changed as well. 
     */
    abstract void setLightDirection( float x, float y, float z);

    
    /**
     * sets the color of the light that appears in the widget.
     */
    public void setColor( Color3f c) {
	lightD1.setColor( c);
    }


    /**
     * defines whether the widget should be enabled or not.
     */
    public void setEnabled( boolean b){
	lightD1.setEnable( b);
    }

    
    /**
     * sets the position of the light of the widget.  
     * This is mostly used during initialization.
     */
    public void setPosition( float x, float y){
	lightD1.setPosition( x, y, lightZ);
    }



    // builds the widget.
    private BranchGroup createScene(){
	BranchGroup scene = new BranchGroup();

	// build the appearance
	Appearance appear = new Appearance();
	Material material = new Material();
	material.setShininess(1000.0f);
	material.setAmbientColor(0.2f, 0.2f, 0.2f);
	material.setSpecularColor(0.0f, 0.0f, 0.0f);
	appear.setMaterial(material);
	
	scene.addChild(new Sphere(0.9f, Sphere.GENERATE_NORMALS, 60, appear));
	
	AmbientLight lightA = new AmbientLight();
	lightA.setInfluencingBounds(new BoundingSphere());
	scene.addChild(lightA);
	
	lightD1.setPosition( initPosition.x, initPosition.y, initPosition.z);
	lightD1.setAttenuation( 1.0f, 0.0f, 0.0f);
	lightD1.setColor( initialColor);
	lightD1.setCapability(DirectionalLight.ALLOW_DIRECTION_WRITE);
	lightD1.setCapability(DirectionalLight.ALLOW_COLOR_WRITE);
	lightD1.setCapability( DirectionalLight.ALLOW_STATE_WRITE);	
	lightD1.setCapability( DirectionalLight.ALLOW_STATE_READ);	
	lightD1.setInfluencingBounds(new BoundingSphere());
	scene.addChild(lightD1);
		
	return scene;
    }

    
    /**
     * constructor
     */
    public LightDirectionWidget ()
    {
        GraphicsConfiguration gc = SimpleUniverse.getPreferredConfiguration();
	if(gc == null)
	    throw  new RuntimeException("SORRY... for now, the JMARS 3D layer doesn't work with some multi-monitor configurations and/or graphics cards. We're working on it!");
	Canvas3D canvas3D = new Canvas3D(gc);

	setLayout(new BorderLayout());
	add("Center", canvas3D);
	
	BranchGroup scene = createScene();
	scene.compile();
	
	SimpleUniverse u = new SimpleUniverse(canvas3D);
	
	// This will move the ViewPlatform back a bit so the
	// objects in the scene can be viewed.
	u.getViewingPlatform().setNominalViewingTransform();
	
	u.addBranchGraph(scene);
	
	// set up behavior.
	PlatformBehavior platform = new PlatformBehavior(canvas3D);
	platform.setSchedulingBounds(new BoundingSphere());
	u.getViewingPlatform().setViewPlatformBehavior(platform);
	
	// build the panel that contains the scene.
	holdingPanel = null;
	holdingPanel = new JPanel();
	holdingPanel.setLayout( new BorderLayout());
	holdingPanel.setBackground( Color.black);
	holdingPanel.setPreferredSize( new Dimension(150,150));
	holdingPanel.add( canvas3D, BorderLayout.CENTER);

	// We must do this little dance to make sure that the widget
	// displays ONLY when the the focus panel gets focus.
	addAncestorListener( new AncestorAdapter(){
		public void ancestorAdded(AncestorEvent e){
		    add( holdingPanel, BorderLayout.CENTER);
		    edu.asu.jmars.Main.getLManager().pack();
		}
		public void ancestorRemoved(AncestorEvent e){
		    remove( holdingPanel);
		    edu.asu.jmars.Main.getLManager().pack();
		}
	    });
	
    }

   

    // defines the behavior of the widget.
    private class PlatformBehavior extends ViewPlatformAWTBehavior {
	
	private final Vector3f zeroVector   = new Vector3f( 0.0f, 0.0f, 0.0f);
	private final Vector4f inVect       = new Vector4f( 0.0f, 0.0f, 1.0f, 0.0f);
	private Vector3f    rot             = new Vector3f();
	private Vector3f    deltaRot        = new Vector3f();
	private Point2D     pressPoint      = new Point2D.Float(0,0);
	private Vector3f    directionVector = new Vector3f();
	private Vector3f    angleVect       = new Vector3f();
	private Transform3D tVect           = new Transform3D();
	private Transform3D tXRot           = new Transform3D();
	private Transform3D tYRot           = new Transform3D();
	private Vector4f    outVect         = new Vector4f();
	private AxisAngle4d y_axisAngle4d   = new AxisAngle4d();
	private AxisAngle4d x_axisAngle4d  =  new AxisAngle4d();
	
    

	
	public PlatformBehavior(Canvas3D aCanvas) 
	{
	    super(aCanvas,MOUSE_MOTION_LISTENER|MOUSE_LISTENER);
	    aCanvas.requestFocus();
	    rot.set( initPosition);
	}

	// required by the ViewPlatformAWTBehavior interface.
	protected void integrateTransforms()
	{
	    angleVect.set( deltaRot.x+rot.x, 
			   deltaRot.y+rot.y,
			   deltaRot.z+rot.z);
	    
	    y_axisAngle4d.set(1.0,0.0,0.0, angleVect.y);
	    x_axisAngle4d.set(0.0,1.0,0.0, angleVect.x);
	    tXRot.set( x_axisAngle4d);
	    tYRot.set( y_axisAngle4d);
	    
	    tVect.set( zeroVector);
	    tVect.mul( tYRot);
	    tVect.mul( tXRot);
	    
	    tVect.transform(inVect, outVect);

	    outVect.scale( lightZ);
	    lightD1.setPosition( -outVect.x, outVect.y, outVect.z);

	    // We can unmask this line and have the light direction updated continuously
	    // on any move of the widget.  But this gives truly crappy performance. 
	    // Until Noel tells me otherwise, I will not implement it now.
	    //setLightDirection(  outVect.x, outVect.y, outVect.z);

	}
	
	// reqd as part of parent class, but all events are procesed elsewhere
	protected void processAWTEvents(java.awt.AWTEvent[] events) {}
	
	
	// gets the "current" point and initialize things
	public void mousePressed(MouseEvent me){
	    pressPoint.setLocation( me.getPoint());
	}
	
	// updates the light position for this widget then sets the light direction of the 
	// scene to which this widget is connected.
	public void mouseReleased(MouseEvent me){
	    
	    rot.add( deltaRot);
	    deltaRot.set( 0.0f, 0.0f, 0.0f);
	    integrateTransforms();

	    setLightDirection(  outVect.x, outVect.y, outVect.z);
	}
	

	// rotate about the X and Y axes.
	public void mouseDragged ( MouseEvent me) {
	    
	    Point2D dragPoint = new Point2D.Float((float)me.getPoint().getX(), (float)me.getPoint().getY());
	    
	    deltaRot.x = (float)Math.toRadians((float)(pressPoint.getX() - dragPoint.getX()));
	    deltaRot.y = (float)Math.toRadians((float)(dragPoint.getY() - pressPoint.getY()));
	    
	    // Time to draw the donuts.
	    integrateTransforms();
	}

    } // end: class PlatformBehavior

}

