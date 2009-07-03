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


package edu.asu.jmars.layer.util.features;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.event.MouseInputAdapter;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj.Projection_OC;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.MultiProjection;
import edu.asu.jmars.layer.WrappedMouseEvent;
import edu.asu.jmars.layer.shape2.ShapeLayer;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.Util;


/**
 *  Defines mouse behavior in the JMARS Shape Framework. Layers may define what behaviors 
 *  are allowed by sending in the or'ed list of behaviors in "definedBehavior".
 *
 * @author James Winburn with copious help from Noel Gorelick, Saadat Anwar, and Eric Engle 
 *              5/2006 MSFF-ASU
 */

public class FeatureMouseHandler extends MouseInputAdapter {
	private final ShapeLayer shapeLayer;
	private Layer.LView         lview;

	// various and sundry variables used by the class.
	private Point2D             mouseLast       = null;
	private Point2D             mouseDown       = null;
	private Point2D             mouseCurr       = null;
	private DecimalFormat       decimal         = new DecimalFormat("0.00");
	// if these are set, then we will be editing a vertex.
	private Feature             selectedVertexFeature = null;
	private Point2D             selectedVertex  = null;

	private boolean             drawSelectionRectangleOK  = false;
	private boolean             drawSelectionGhostOK      = false;
	private boolean             drawVertexBoundingLinesOK = false;

	private boolean             addPointsOK         = false;
	private boolean             addLinesOK          = false;
	private boolean             addPolysOK          = false;

	private boolean             moveFeaturesOK      = false;
	private boolean             deleteFeaturesOK    = false;

	private boolean             moveVertexOK         = false;
	private boolean             addVertexOK         = false;
	private boolean             deleteVertexOK      = false;

	private boolean             changeModeOK        = false;
	private boolean             zorderOK            = false;

	// context menu items.
	private JRadioButtonMenuItem popupButton;
	private JRadioButtonMenuItem addModeRadioButton;
	private JRadioButtonMenuItem selectModeRadioButton;    
	private JMenuItem            deletePointMenuItem;
	private JMenuItem            addPointMenuItem;
	private JMenuItem            zOrderMenuItem;
	private JMenuItem            deleteRowMenuItem;
	
	// variables used by the ContextMenu controllers.
	private Rectangle2D rect;
	private Point2D     worldPt;

	// cursors to be set up.
	ImageIcon img1    = new ImageIcon(Main.getResource("resources/pencil.png"));
	Image     pointer = img1.getImage(); 
	ImageIcon img2    = new ImageIcon(Main.getResource("resources/vertex.png"));
	Image     vertex  = img2.getImage();
	Toolkit   tk = Toolkit.getDefaultToolkit();
	private final Cursor     ADD_CURSOR       = tk.createCustomCursor(pointer, new Point(0,30), "Add");    
	private final Cursor     VERTEX_CURSOR    = tk.createCustomCursor(vertex,  new Point(15,15), "Vertex");
	private final Cursor     DEFAULT_CURSOR   = new Cursor(Cursor.DEFAULT_CURSOR);
	private final Cursor     PERIMETER_CURSOR = new Cursor(Cursor.CROSSHAIR_CURSOR);
	private final Cursor     SELECT_CURSOR    = new Cursor(Cursor.HAND_CURSOR);
	private final int        TOLERANCE = 5; 
	private final int        VERTEX_BOX_SIDE = 4; // vertex box side in pixels
	private final int        PROXIMITY_BOX_SIDE = 3; // proxmity box side in pixels
	private final int        STROKE_WIDTH = 2; // stroke width in pixels
	
	// If running in JMARS, this will set the cursor.
	// It does nothing otherwise. 
	private void setCursor( Cursor c){
		if (lview!=null){
			lview.setCursor( c);
		}
	}
	
	
	// If running in JMARS, this will cause paintComponent
	// to be called.  It does nothing otherwise.
	private void repaint(){
		if (lview!=null){
			lview.repaint();
		}
	}




	/**
	 * Defines the mode in which the layer is running.
	 * This is public as external classes will need this
	 * information.
	 * 
	 * Note that the module always starts in ADD_FEATURE_MODE.
	 */
	public static final int SELECT_FEATURE_MODE = 0;
	public static final int ADD_FEATURE_MODE    = 1;
	public static final int MOVE_FEATURE_MODE   = 2;

	int mode = SELECT_FEATURE_MODE;

	public int getMode(){
		return mode;
	}
	public void setMode( int m){
		if (m==SELECT_FEATURE_MODE){
			setCursor( SELECT_CURSOR);
		} else if (m==ADD_FEATURE_MODE){
			setCursor( ADD_CURSOR);
		} else {
			setCursor( DEFAULT_CURSOR);
		}
		mode = m;
	}



	/**
	 * Allowable behavior in this class is defined
	 * by or'ing the behavior flags in the "definedBehavior"
	 * arguement in the constructor.
	 */
	public static final int ALLOW_ADDING_POINTS     = 1;
	public static final int ALLOW_ADDING_LINES      = 2;
	public static final int ALLOW_ADDING_POLYS      = 4;

	public static final int ALLOW_MOVING_FEATURES   = 8;
	public static final int ALLOW_DELETE_FEATURES   = 16;

	public static final int ALLOW_MOVING_VERTEX     = 32;
	public static final int ALLOW_DELETING_VERTEX   = 64;
	public static final int ALLOW_ADDING_VERTEX     = 128;

	public static final int ALLOW_CHANGE_MODE       = 256;
	public static final int ALLOW_ZORDER            = 512;

	// Gets the multi-projection used. 
	private MultiProjection getProj() {
		if (lview != null)
			// Normal case, used in JMars.
			return lview.getProj();
		
		// Abnormal case, used for JUnit tests.
		return MultiProjection.getIdentity();
	}

	// TODO: This is a very limited interface, it should be extended to include other
	// defaults available from ShapeFocusPanel fields. 
	public interface SelColor {
		Color getColor();
	}
	
	/**
	 * constructor
	 * @param fc - the FeatureCollection to be added to.     Cannot be null.
	 * @param lview - the LView associated with the module.  Null if not running in JMARS.
	 * @param selectColor - the color of the selection line.  This is abstract to allow users 
	 *            to change the color dynamically.
	 * @param definedBehavior - an or'ed combination of behaviors that this module should
	 *            be sensitive to.
	 * @param history - History or undo log.
	 */
	public FeatureMouseHandler(ShapeLayer shapeLayer, Layer.LView lview, int definedBehavior) {
		this.shapeLayer = shapeLayer;
		this.lview = lview;
		
		addPointsOK = (definedBehavior & ALLOW_ADDING_POINTS)==ALLOW_ADDING_POINTS;
		addLinesOK = (definedBehavior & ALLOW_ADDING_LINES)==ALLOW_ADDING_LINES;
		addPolysOK = (definedBehavior & ALLOW_ADDING_POLYS)==ALLOW_ADDING_POLYS;
		moveFeaturesOK = (definedBehavior & ALLOW_MOVING_FEATURES)==ALLOW_MOVING_FEATURES;
		deleteFeaturesOK = (definedBehavior & ALLOW_DELETE_FEATURES)==ALLOW_DELETE_FEATURES;
		moveVertexOK = (definedBehavior & ALLOW_MOVING_VERTEX)==ALLOW_MOVING_VERTEX;
		addVertexOK = (definedBehavior & ALLOW_ADDING_VERTEX)==ALLOW_ADDING_VERTEX;
		deleteVertexOK = (definedBehavior & ALLOW_DELETING_VERTEX)==ALLOW_DELETING_VERTEX;
		changeModeOK = (definedBehavior & ALLOW_CHANGE_MODE)==ALLOW_CHANGE_MODE;
		zorderOK = (definedBehavior & ALLOW_ZORDER)==ALLOW_ZORDER;
		
		setupContextMenu();
	}


	// a list of the points created when drawing. The point could represent
	// just a point or the vertex of a polyline or polygon
	private java.util.List points = new ArrayList();
	
	private int mouseContext = -1;
	
	
	/**
	 * If there was a mouseClick in selection mode, as opposed to a mousePressed/mouseReleased pair,
	 * do a selection on the topmost feature.
	 */
	public void mouseClicked(MouseEvent e){
		mouseContext = MouseEvent.MOUSE_CLICKED;
		switch(getMode()){
			case SELECT_FEATURE_MODE:
				mouseCurr = getProj().screen.toSpatial(((WrappedMouseEvent)e).getRealPoint());
				Rectangle2D rect = getProj().getClickBox(getProj().spatial.toWorld(mouseCurr), PROXIMITY_BOX_SIDE);    
				
				if ( (e.getModifiers() & InputEvent.CTRL_MASK) !=0)
					toggleTopmostFeature( rect);
				else
					selectTopmostFeature( rect);
				mouseLast = null;
				break;
				
			case MOVE_FEATURE_MODE:
				// If a mouse-press put us into move-feature mode, snap out of it 
				// if it turns out that we weren't moving at all.
				setMode(SELECT_FEATURE_MODE);
				break;
		}
	}


	/**
	 * adding a feature is done here.
	 */
	public void mousePressed(MouseEvent e)
	{
		mouseContext = MouseEvent.MOUSE_PRESSED;
		mouseDown = getProj().screen.toSpatial(((WrappedMouseEvent)e).getRealPoint());

		if ( getMode()==ADD_FEATURE_MODE){
			// add mode processing happens in the mouseReleased portion.
		}
		
		// Find out if we need to go into MOVE_FEATURE_MODE
		else if (getMode()==SELECT_FEATURE_MODE && (e.getModifiers() & InputEvent.CTRL_MASK) == 0){
			Rectangle2D rect = getProj().getClickBox(getProj().spatial.toWorld(mouseDown), PROXIMITY_BOX_SIDE);

			for (Feature f: shapeLayer.getSelections()) {
				// if the cursor is over a vertex, it is to be moved or deleted.
				Point2D vertex = getIntersectingVertex( f, rect);
				if ((moveVertexOK || deleteVertexOK) && vertex != null){
					selectedVertex        = vertex;
					selectedVertexFeature = f;
					break;
				} else {
					// cursor is NOT over a vertex, the entire selection will be moved.
					selectedVertex = null;
					selectedVertexFeature = null;
					if (moveFeaturesOK && f.getPath().getWorld().intersects(rect)) {
						setMode( MOVE_FEATURE_MODE);
						break;
					}
				}	
			}
		}
		mouseLast = mouseCurr;
	}

	private void updateLength(){
		double angDistance = getProj().spatial.distance(mouseDown, mouseCurr);
		double linDistance = angDistance * 3390.0 * 2*Math.PI / 360.0;
		Main.setStatus(Util.formatSpatial(mouseCurr) + "\tdist = " +
			       decimal.format(angDistance) + "deg = " +
			       decimal.format(linDistance) + "km");
	}
    
	// all we need to do when moving the mouse is select the appropriate cursor.
	public void mouseMoved(MouseEvent e)
	{
		mouseContext = MouseEvent.MOUSE_MOVED;
		mouseCurr = getProj().screen.toSpatial(((WrappedMouseEvent)e).getRealPoint());
		switch( getMode()){
		case ADD_FEATURE_MODE:
			// If we're just moving the cursor around after having defined a point, 
			// update the panel showing the length of the line
			if (points.size() > 0 && mouseDown != null){
				updateLength();
			}
			setCursor( ADD_CURSOR);
			
			// redraw the feature built so far
			repaint();
			break;
			
		//case MOVE_FEATURE_MODE:
		//	setCursor( DEFAULT_CURSOR);
		//	break;
		
		case SELECT_FEATURE_MODE:
			
			// Change cursor according to the current proximity to the selected polygons.
			Rectangle2D rect = getProj().getClickBox(getProj().spatial.toWorld(mouseCurr), PROXIMITY_BOX_SIDE);

			// If moving vertices is permitted and any features are selected 
			// in the table, check if the cursor is hovering over a vertex or an outline.
			for (Feature f: shapeLayer.getSelections()) {
				if (f.getPath().getType() != FPath.TYPE_POINT) {
					if ((deleteVertexOK || moveVertexOK) && getIntersectingVertex( f, rect) != null){
						setCursor( VERTEX_CURSOR);
						return;
					}
					if (addVertexOK && f.getPath().getWorld().intersects(rect)){
						setCursor( PERIMETER_CURSOR);
						return;
					}
				}
			}

			// no vertex operation detected.  set cursor to a simple selection.
			setCursor( SELECT_CURSOR);
			break;
		}
		mouseLast = mouseCurr;
	}
	

	/**
	 * dragging the mouse involves the positioning of the "Selection Ghost" which can
	 * be a line or a box.
	 */
	public void mouseDragged(MouseEvent e)
	{
		mouseContext = MouseEvent.MOUSE_DRAGGED;
		mouseCurr = getProj().screen.toSpatial(((WrappedMouseEvent)e).getRealPoint());
		
		switch (getMode()){
			
		case ADD_FEATURE_MODE:
			// drag the point just added if there is one
			if (mouseDown != null){
				updateLength();
				repaint();
			}
			break;
			
		case MOVE_FEATURE_MODE:
			drawSelectionGhostOK = true;
			repaint();
			break;
			
		case SELECT_FEATURE_MODE:
			
			// Feature/vertex is being dragged.
			if ( selectedVertex != null && selectedVertexFeature != null){
				// vertex drag
				if (selectedVertexFeature.getPath().getType()==FPath.TYPE_POINT){ // point
					moveVertex( selectedVertexFeature, selectedVertex, mouseCurr);
				} 
				else {  // polylines and polygones
					drawVertexBoundingLinesOK = true;
					repaint();
					mouseLast = mouseCurr;
				}
			}
			else {
				// (multi) polygon drag
				if (mouseDown == null){
					return;
				}
				drawSelectionRectangleOK = true;
				repaint();
				mouseLast = mouseCurr;
			}
			break;
		}
	}
    
	// The actions for moving and selecting is done in mouseReleased. 
	public void mouseReleased(MouseEvent e)
	{
		mouseContext = MouseEvent.MOUSE_RELEASED;
		drawSelectionRectangleOK  = false;
		drawSelectionGhostOK      = false;
		drawVertexBoundingLinesOK = false;
		
		mouseCurr = getProj().screen.toSpatial(((WrappedMouseEvent)e).getRealPoint());
		double ppd = getProj().getPPD();
		
		switch (getMode()) {
		case ADD_FEATURE_MODE:
			int featureType = FPath.TYPE_NONE;
			
			// If there is only one point defined and the user clicked on it, insert a point.
			if (addPointsOK && points.size()==1 && intersects(mouseCurr, (Point2D)points.get(0), TOLERANCE/ppd))
				featureType = FPath.TYPE_POINT; 
			
			// if the user clicked on the last point, insert a polyline.
			else if (addLinesOK && points.size() > 0 && intersects(mouseCurr, (Point2D)points.get(points.size()-1), TOLERANCE/ppd))
				featureType = FPath.TYPE_POLYLINE;
			
			// if the user clicked on the first point, complete the polygon.
			else if (addPolysOK && points.size() > 0 && intersects(mouseCurr, (Point2D)points.get(0), TOLERANCE/ppd))
				featureType = FPath.TYPE_POLYGON;
			
			// if we can add polys or lines, add the point to the array of points.
			else if (addLinesOK || addPolysOK)
				points.add(mouseCurr);
			
			// barf at the user.
			else
				Toolkit.getDefaultToolkit().beep();
			
			// Make a new history frame.
			if (featureType != FPath.TYPE_NONE){
				if (shapeLayer.getHistory() != null)
					shapeLayer.getHistory().mark();
				addFeature(featureType, points);
			}
			break;
			
			
		case MOVE_FEATURE_MODE:
			Map features = new HashMap();
			for (Feature f: shapeLayer.getSelections()) {
				Point2D[] vertices = f.getPath().getSpatialWest().getVertices();
				vertices = this.offsetVertices(vertices, mouseDown, mouseCurr);
				features.put(f, new FPath(vertices, FPath.SPATIAL_WEST, f.getPath().getClosed()));
			}
			if (features.size() > 0) {
				// Make a new history frame.
				if (shapeLayer.getHistory() != null)
					shapeLayer.getHistory().mark();
				shapeLayer.getFeatureCollection().setAttributes( Field.FIELD_PATH, features);
			}
			mouseLast = null;
			setMode( SELECT_FEATURE_MODE);
			break;
			
		case SELECT_FEATURE_MODE :
			// If we are editing a vertex, process appropriately.
			if (moveVertexOK && selectedVertex != null && selectedVertexFeature != null){
				// Make a new history frame.
				if (shapeLayer.getHistory() != null)
					shapeLayer.getHistory().mark();

				moveVertex(selectedVertexFeature, selectedVertex, mouseCurr);
				selectedVertex = null;
				selectedVertexFeature = null;
				mouseLast = null;
				repaint();
				return;
			}
			
			// We have no selected vertex. continue processing as though this is a simple select.
			// features that intersect the selectRectangle are marked as selected and drawn as handles. 
			// If the mouse was pressed on a different point than it was released,
			// do a selectionRectangle selection, selecting ALL the features within
			// the extent of the rectangle.
			
			// If the two points are the same, mouseClick will alrady have processed 
			// the selection.
			// We can't do it here, because pressing the mouse button while over
			// a selected feature sends the app into move feature mode.
			if (!mouseLast.equals(mouseDown)){
				//selectRectangle.setFrameFromDiagonal( getProj().spatial.toScreen(mouseLast),
				//		getProj().spatial.toScreen(mouseDown));
				//Rectangle2D rect = getProj().screen2world(selectRectangle);
				Rectangle2D rect = new Rectangle2D.Double();
				rect.setFrameFromDiagonal(getProj().spatial.toWorld(mouseLast),
						getProj().spatial.toWorld(mouseDown));
				if ((e.getModifiers() & MouseEvent.CTRL_MASK) == MouseEvent.CTRL_MASK)
					toggleFeatures(rect);
				else
					selectFeatures( rect);
				mouseLast = null;
			}
		}
	}

	/**
	 * Offsets <code>vertices</code> according to the motion from the
	 * <code>from</code> point to the <code>to</code> point.<p>
	 * Vertices are offset horizontally around the <code>up</code> vector
	 * according to the signed angle between the from-up and to-up planes.<p>
	 * Vertices are offset vertically within the vertex-up plane according to
	 * the signed difference in the angle between the <code>from</code> 
	 * and <code>to</code> vectors from the <code>up</code> vector.<p>
	 * <em>Note:</em>The <code>from<code> and <code>to<code> are arbitrary
	 * points not linked with the <code>vertices</code> in any way.
	 * <em>Caveat:</em>Pole crossing polygons will get deformed.<p>
	 * <bold>This code is untested on polygons greater than 180 degrees.</bold>
	 * 
	 * @param vertices Vertices to offset (in spatial west coordinates). 
	 * @param from Start point of movement not linked to vertices (in spatial west).
	 * @param to End point of movement not linked to vertices (in spatial west).
	 * @return Offseted vertices (in spatial west).
	 */
	private Point2D[] offsetVerticesOC(Point2D[] vertices, Point2D from, Point2D to){
		/*
		 * From & To vectors from the two (spatial) points determining the direction
		 * of motion.
		 * Up vector from the oblique-cylindrical projection.
		 * Left vector is the normal to the (From, Up) plane.
		 */
		HVector vFrom = getProj().spatial.toHVector(from).unit();
		HVector vTo = getProj().spatial.toHVector(to).unit();
		HVector up = ((Projection_OC)Main.PO).getUp().unit();
		HVector left = vFrom.cross(up).unit();
		
		/*
		 * From and To direction vectors in within the (from, up) and
		 * (to, up) frames respectively. Each of these vectors is perpendicular
		 * to the up vector. Both lie in the plane normal to the up vector. 
		 */
		HVector hFromDir = up.cross(vFrom.cross(up));
		HVector hToDir = up.cross(vTo.cross(up));
		
		/*
		 * Compute angular separation between from & up, and to & up vectors
		 * with proper sign w.r.t. the hemisphere they belong to. Their 
		 * difference will give us the vertical angle of rotation (which is
		 * around the left vector).
		 */
		double va1 = ((hFromDir.dot(vFrom)<0)?-1:1) * vFrom.separation(up);
		double va2 = ((hToDir.dot(vTo)<0)?-1:1) * vTo.separation(up);
		
		/*
		 * Vertical & horizontal separation angles between from & to vectors.
		 * These are signed rotation angles around the left vector and up vector
		 * respectively.
		 */
		double vAngle = -(va2-va1);
		double hAngle = ((left.dot(hToDir)>=0)?-1:1) * hFromDir.separation(hToDir);
		
		Point2D[] out = new Point2D[vertices.length]; // Output points.
		
		// Anchor point which determines which hemisphere other points falls in.
		HVector v0Dir = null;
		if (vertices.length > 0){
			/*
			 * Our anchor is in the (vertex[0], up) plane. It is exactly 90 degrees
			 * from the up vector. Assume it is coming out of the screen, then the
			 * screen partitions the two hemispheres. 
			 */
			HVector v = getProj().spatial.toHVector(vertices[0]).unit();
			v0Dir = up.cross(v.cross(up)).unit();
		}
		
		for(int i=0; i<vertices.length; i++){
			/*
			 * Rotate every vertex in the horizontal and vertical directions
			 * according to the angles determined above.
			 */
			HVector v = getProj().spatial.toHVector(vertices[i]).unit();
			out[i] = new Point2D.Double();
			
			/*
			 * If a point is in the hemisphere towards us (+ve dot product) then
			 * the vertical rotation is positive, otherwise, it is negative. This
			 * ensures that any polygon that goes over the up vector comes back
			 * over the up vector when pulled back.
			 */
			double vAngleSign = (v0Dir.dot(v) > 0)? 1: -1;
			v.rotate(v.cross(up).unit(), vAngleSign * vAngle).rotate(up, hAngle).toLonLat(out[i]);
		}
		
		return out;
	}
	
	/**
	 * Offsets <code>vertices</code> according to the <em>world</em> coordinate
	 * displacement between the <code>from</code> and <code>to</code> points.
	 * <em>Note:</em>The <code>from<code> and <code>to<code> are arbitrary
	 * points not linked with the <code>vertices</code> in any way.
	 * <em>Note:</em>Pole crossing polygons will get deformed.
	 * 
	 * @param vertices Vertices to offset (in spatial west coordinates). 
	 * @param from Start point of movement not linked to vertices (in spatial west).
	 * @param to End point of movement not linked to vertices (in spatial west).
	 * @return Offseted vertices (in spatial west).
	 */
	private Point2D[] offsetVerticesFallback(Point2D[] vertices, Point2D from, Point2D to){
		Point2D startPoint = getProj().spatial.toWorld(from);
		Point2D endPoint   = getProj().spatial.toWorld(to);
		double dx = endPoint.getX() - startPoint.getX();
		dx -= ((int)(dx/360))*360;
		double dy = endPoint.getY() - startPoint.getY();
		FPath path = new FPath(vertices, FPath.SPATIAL_WEST, false); 
		return path.getWorld().translate(dx,dy).getSpatialWest().getVertices();
	}
	
	/**
	 * Driver to choose the appropriate method for vertex offsetting.
	 * @see #offsetVerticesOC(Point2D[], Point2D, Point2D)
	 * @see #offsetVerticesOC(Point2D[], Point2D, Point2D)
	 */
	private Point2D[] offsetVertices(Point2D[] vertices, Point2D from, Point2D to){
		if (Main.PO instanceof Projection_OC)
			return offsetVerticesOC(vertices, from, to);
		return offsetVerticesFallback(vertices, from, to);
	}
	
	/**
	 * clears all the fields of the mouse so that lines will not be set up half way.
	 */
	public void  initializeSelectionLine(){
		points.clear();
		mouseLast=mouseDown=mouseCurr=null;
	}
	
	
	
	/**
	 *  Returns whether the points intersect each other within the specified tolarance.
	 *  All values are in spatial coordinates.
	 */
	private boolean intersects( Point2D p1, Point2D p2, double tolarance){
		HVector v1 = getProj().spatial.toHVector(p1);
		HVector v2 = getProj().spatial.toHVector(p2);
		
		if (Math.toDegrees(v1.separation(v2)) <= tolarance)
			return true;
		
		return false;
	}
	
	
	
	/**
	 * deletes the most-recently defined point in the selection line and repaints whatever 
	 * is left of the line.  If the selection line consists of a single point, the entire 
	 * selection line is deleted.
	 */
	public void deleteLastVertex(){
		
		if (points.size()>0){
			points.remove( points.size()-1);
			if (points.size()==0){
				mouseDown = mouseLast=null;
			} else {
				mouseDown = (Point2D)points.get( points.size()-1);
			}
			repaint();
		}
	}
	
	
	
	/**
	 * Removes the @param worldPoint from the feature.
	 * Assumes the point is in world coordinates.
	 * If the feature is a line it must have at least 2 points  after 
	 * the delete. If the feature is a polygon it must have at least 3 points
	 * after the delete.
	 * 
	 * @param spatialPoint
	 */
	public void deleteVertex(Feature f, Point2D spatialPoint)
	{
		// Get world coordinate vertices
		Point2D[] vertices = f.getPath().getSpatialWest().getVertices();

		// Remove the deleted point
		LinkedHashSet orderedSet = new LinkedHashSet (Arrays.asList(vertices));
		orderedSet.remove(spatialPoint);
		vertices = (Point2D[]) orderedSet.toArray(new Point2D[0]);

		// Make a new FPath
		FPath path = new FPath (vertices, FPath.SPATIAL_WEST, f.getPath().getClosed());

		// Set the new FPath
		// Make a new history frame.
		if (shapeLayer.getHistory() != null)
			shapeLayer.getHistory().mark();
		f.setPath(path.getSpatialWest());
	}

	/**
	 * Adds a vertex to the feature at the specified worldPoint.
	 * "rect" specifies a small rectangle around the worldPoint.
	 * This is used in determining between which vertices the
	 * new vertex is to be placed.
	 */
	public void addVertex(Feature f, Point2D worldPoint, Rectangle2D rect)
	{
		int[] indices = getBoundingIndices(f, worldPoint, rect);
		if (indices==null)
			return;

		// Get List of world-coordinate vertices
		Point2D[] vertArray = f.getPath().getWorld().getVertices();
		List vertices = new ArrayList (Arrays.asList(vertArray));

		// Add new world-coordinate vertex and convert to Point2D[]
		vertices.add (indices[1], worldPoint);
		Point2D[] newPoints = (Point2D[])vertices.toArray(new Point2D[0]);

		// Create new spatial-west-coordinate FPath and set it
		FPath path = new FPath (newPoints, FPath.WORLD, f.getPath().getClosed());
		
		// Make a new history frame.
		if (shapeLayer.getHistory() != null)
			shapeLayer.getHistory().mark();

		f.setPath(path.getSpatialWest());
	}

	/**
	 * ShapeLView sometimes needs to know the immediately adjacent vertices
	 * since a pair of lines need to be drawn
	 * note: the vertices are in world so convert to screen.
	 * This is called from the ShapeLView's paintComponent()
	 */
	public void drawVertexBoundingLines( Graphics2D g2world){
		if (!drawVertexBoundingLinesOK || selectedVertexFeature == null){
			return;
		}
		
		Point2D[] vertices = getBoundingVertices( selectedVertexFeature, selectedVertex);
		for(int i=0; i<vertices.length; i++)
			vertices[i] = getProj().spatial.toWorld(vertices[i]);
		
		g2world.setColor(shapeLayer.getStyles().lineColor.getValue(null));
		g2world.setStroke( new BasicStroke(((float)STROKE_WIDTH)/getProj().getPPD()));
		if (vertices.length==2){
			
			// figgur out what the current position should be. It might be 360 degrees off. 
			// This check must be done in world coordinates.
			Point2D midVert = getProj().spatial.toWorld(mouseCurr);
			if ( (vertices[0].getX() - midVert.getX() > 180)  || (vertices[1].getX() - midVert.getX() > 180) ){
				midVert.setLocation( midVert.getX() + 360, midVert.getY() );
			} else if ( (midVert.getX()- vertices[0].getX() > 180)  || (midVert.getX() - vertices[1].getX() > 180) ){
				midVert.setLocation( midVert.getX() - 360, midVert.getY() );
			} 
			
			Point2D left  = vertices[0];
			Point2D right = vertices[1];
			Point2D mid   = midVert;
			
			g2world.draw(new Line2D.Double(left, mid));
			g2world.draw(new Line2D.Double(mid, right));
		}
		
		else if (vertices.length==1) {
			// figgur out what the current position should be. It might be 360 degrees off. 
			// This check must be done in world coordinates.
			Point2D midVert = getProj().spatial.toWorld(mouseCurr);
			if ( (vertices[0].getX() - midVert.getX() > 180)){
				midVert.setLocation( midVert.getX() + 360, midVert.getY() );
			} else if ( (midVert.getX()- vertices[0].getX() > 180)){
				midVert.setLocation( midVert.getX() - 360, midVert.getY() );
			} 
			
			Point2D left  = vertices[0];
			Point2D mid   = midVert;
			
			g2world.draw(new Line2D.Double(left, mid));
		}
	}
	



	/**
	 * draws a "ghost" representation of all the selected features translated by "point".
	 * This must be called by the LView if it desires to see a selection ghost during
	 * mouse drags.
	 */
	public void drawSelectionGhost(Graphics2D g2world){
		if (! drawSelectionGhostOK)
			return;

		g2world.setColor(Color.DARK_GRAY);
		g2world.setStroke( new BasicStroke(((float)STROKE_WIDTH)/getProj().getPPD()));
		//g2world.translate( deltaX, deltaY);
		
		for(Feature feature: shapeLayer.getSelections()) {
			FPath path = feature.getPath();
			Point2D[] vertices = offsetVertices(path.getSpatialWest().getVertices(), mouseDown, mouseCurr);
			path = new FPath(vertices, FPath.SPATIAL_WEST, path.getClosed());
			
			if (feature.getPath().getType() == FPath.TYPE_POINT) {
				Point2D p = FeatureUtil.getStartPoint(feature);
				p = path.getWorld().getVertices()[0];
				Rectangle2D box = getProj().getClickBox( p, VERTEX_BOX_SIDE);
				g2world.fill(box);
			} else { // polygons and lines
				g2world.draw(path.getWorld().getGeneralPath());
			}
		}
	}
	
	

	
	/**
	 * draws the line that indicates the incomplete feature.  This includes a control point at the first
	 * point where a polygon or a point can be defined and another control point at the last clicked point
	 * where a polyline can be defined.
	 * This is called by ShapeLView's paintComponent().
	 */
	public void drawSelectionLine( Graphics2D g2world){
		g2world.setColor(shapeLayer.getStyles().lineColor.getValue(null));
		g2world.setStroke( new BasicStroke(((float)STROKE_WIDTH)/getProj().getPPD()));

		if (points.size() > 0){
			// draw first click point (the one that defines a polygon.)
			Point2D p   = getProj().spatial.toWorld((Point2D)points.get(0));
			g2world.fill(getProj().getClickBox(p, VERTEX_BOX_SIDE));
		}
		
		if (points.size()>1){
			// draw the polygonal outline.
			FPath path = new FPath((Point2D[])points.toArray(new Point2D[0]), FPath.SPATIAL_WEST, false);
			g2world.draw(path.getWorld().getGeneralPath());
			
			// draw last click point (the one that defines a polyline.)
			Point2D p = getProj().spatial.toWorld((Point2D)points.get(points.size()-1));
			g2world.fill(getProj().getClickBox(p, VERTEX_BOX_SIDE));
		}
		
		if (points.size() > 0){
			g2world.draw(new Line2D.Double(getProj().spatial.toWorld((Point2D)points.get(points.size()-1)),
					getProj().spatial.toWorld(mouseCurr)));
		}
		
		if (mouseContext == MouseEvent.MOUSE_DRAGGED && getMode() != MOVE_FEATURE_MODE && !drawSelectionRectangleOK)
			g2world.fill(getProj().getClickBox(getProj().spatial.toWorld(mouseCurr), VERTEX_BOX_SIDE));
	}
	


	/**
	 * draws a rubber band box allowing the user to select features
	 * This is called from the ShapeLView's paintComponent()
	 */
	public void drawSelectionRectangle( Graphics2D g2world)
	{
		if (drawSelectionRectangleOK==false){
			return;
		}
		
		Rectangle2D selectRectangle = new Rectangle2D.Double();
		selectRectangle.setFrameFromDiagonal(getProj().spatial.toWorld(mouseDown),
				getProj().spatial.toWorld(mouseCurr));
		
		g2world.setColor(Color.white);
		g2world.setStroke( new BasicStroke(((float)STROKE_WIDTH)/getProj().getPPD()));
		g2world.draw(selectRectangle);
	}
	


	// adds a feature to the FeatureCollection.
	void addFeature( int type, List points){
		Feature feature = new Feature();

		// Can only close polygons
		boolean closed = (type == FPath.TYPE_POLYGON);

		// Convert List to array, and get world-coordinates if in JMars
		Point2D[] vertices = (Point2D[])points.toArray(new Point2D[0]);
		for (int i = 0; i < vertices.length; i++) {
			vertices[i] = getProj().spatial.toWorld(vertices[i]);
		}
		
		// Convert vertices to FPath and set spatial west version
		FPath path = new FPath (vertices, FPath.WORLD, closed);
		feature.setPath (path.getSpatialWest());
		feature.setAttribute( Field.FIELD_FEATURE_TYPE, FeatureUtil.getFeatureTypeString(feature.getPath().getType()));
		
		// Make a new history frame.
		if (shapeLayer.getHistory() != null)
			shapeLayer.getHistory().mark();

		try {
			shapeLayer.getFeatureCollection().addFeature( feature);
		}
		catch(UnsupportedOperationException ex){
			JOptionPane.showMessageDialog(lview,
					"Cannot add Feature, since no default FeatureCollection is set.",
					"Error!",
					JOptionPane.ERROR_MESSAGE);
		}
		initializeSelectionLine();
	}
	
	/**
	 * selects features via a bounding box.  The rectangle is a
	 * rubber band type rectangle drawn by the user. Any
	 * feature that intersects this rectangle is flagged as selected.
	 * @param rectangle (assumed never to be null)
	 */
	public void selectFeatures(Rectangle2D rectangle){
		_selectFeature( rectangle, true, false);
	}
	
	public void toggleFeatures(Rectangle2D rectangle){
		_selectFeature( rectangle, true, true);
	}

	// selects (via a bounding box) the topmost feature with respect to Z-order.
	// The rectangle is a rubber band type rectangle drawn by the user.
	// @param rectangle (assumed never to be null)
 	private  void selectTopmostFeature(Rectangle2D rectangle){
		_selectFeature( rectangle, false, false);
	}

	private void toggleTopmostFeature( Rectangle2D rectangle){
		_selectFeature( rectangle, false, true);
	}
	
	// selects features via a bounding box.  The rectangle is a
	// rubber band type rectangle drawn by the user. Any
	// feature that intersects this rectangle is flagged as selected.
	//
	// @param rectangle (assumed never to be null)
	private  void _selectFeature(Rectangle2D rectangle, boolean multipleSelections, boolean toggleIt)
	{
		Iterator<Feature> it = shapeLayer.getIndex().queryUnwrappedWorld(rectangle);
		List<Feature> hits = new ArrayList<Feature>();
		while (it.hasNext()) {
			Feature f = it.next();
			if (f.getPath().getWorld().intersects(rectangle)) {
				hits.add(f);
			}
		}
		
		if (!multipleSelections && hits.size() > 0) {
			Feature f = hits.get(hits.size()-1);
			hits.clear();
			hits.add(f);
		}
		
		Set<Feature> selections = shapeLayer.getSelections();
		
		if (toggleIt) {
			// remove hits that were selected
			Set<Feature> selectedHits = new HashSet<Feature>(hits);
			selectedHits.retainAll(selections);
			selections.removeAll(selectedHits);
			hits.removeAll(selectedHits);
		} else {
			// retain only hits
			selections.retainAll(hits);
		}
		
		// add hits that were not selected
		selections.addAll(hits);
		
		repaint();
	}

	/**
	 * Returns true if the mouse is over a vertex, false otherwise.
	 * This is only meaningful for lines or polygons.
	 * @param feature Feature object to search.
	 * @param worldRect Rectangle (in world coordinates) to use for proximity matching.
	 * @return intersecting vertex in spatial (west) coordinates as it exists in
	 *         the Feature object. This is important for Point2D.equal() match
	 *         elsewhere in the code, since conversion back and forth between
	 *         world and spatial coordinates looses precision.
	 */
	public Point2D getIntersectingVertex( Feature feature, Rectangle2D worldRect){
		if (feature.getPath().getType()== FPath.TYPE_POINT){
			return null;
		}
		Rectangle2D rect1 = new Rectangle2D.Double( worldRect.getX() + 360, worldRect.getY(), worldRect.getWidth(), worldRect.getHeight());
		Rectangle2D rect2 = new Rectangle2D.Double( worldRect.getX() - 360, worldRect.getY(), worldRect.getWidth(), worldRect.getHeight());
		Point2D[] vertices = feature.getPath().getWorld().getVertices();
		for (int i=0; i<vertices.length; i++) {
			if(worldRect.outcode(vertices[i]) == 0 ||
				rect1.outcode(vertices[i]) == 0 ||
				rect2.outcode(vertices[i]) == 0){
				return feature.getPath().getSpatialWest().getVertices()[i];
			}
		} 
		return null;
	}

	/**
	 * Replaces the "from" spatial vertex with the "to" spatial vertex.  
	 * @param spatialFrom Point to be replaced in Spatial West coordinates.
	 * @param spatialTo The replacement point in Spatial West coordinates.
	 */
	public void moveVertex(Feature f, Point2D spatialFrom, Point2D spatialTo) {
		// Get the world-coordinate vertices
		Point2D[] vertices = f.getPath().getSpatialWest().getVertices();

		// If vertex is in this array, replace it with worldPoint
		int index = Arrays.asList(vertices).indexOf(spatialFrom);
		if (index >= 0) {
			// Replace vertex, recreate swPath, set it
			vertices[index] = (Point2D)spatialTo.clone();
			FPath path = new FPath (vertices, FPath.SPATIAL_WEST, f.getPath().getClosed());
			f.setPath (path.getSpatialWest());
		}
	}

	/**
	 * returns the vertices on either side of the specified vertex.
	 * If the specified vertex is NOT a vertex of the feature, an empty
	 * array of vertices is returned.
	 * @param f Feature to search.
	 * @param centerVertex vertex to search in spatial (west) coordinates.
	 * @return zero/one/two vertices adjacent to the specified vertex.
	 */
	public Point2D[] getBoundingVertices( Feature f, Point2D centerVertex)
	{
		if (f.getPath().getType() == FPath.TYPE_POINT)
			return null;

		Point2D[] vertices = f.getPath().getSpatialWest().getVertices();
		int index = Arrays.asList(vertices).indexOf(centerVertex);
		if (index == -1)
			return new Point2D[0];

		// The index is known so now get the vertices on either side.
		// Special cases if index is the first or last element of the array.
		if(index == 0) {
			if (f.getPath().getType()==FPath.TYPE_POLYLINE){
				Point2D[] boundingVertices = new Point2D[1];
				boundingVertices[0] = vertices[index+1];
				return boundingVertices;
			} else { // polygon
				Point2D[] boundingVertices = new Point2D[2];
				boundingVertices[0] = vertices[index+1];
				boundingVertices[1] = vertices[vertices.length-1];
				return boundingVertices;
			}
		}
		else if(index == vertices.length-1) {
			if (f.getPath().getType()== FPath.TYPE_POLYLINE){
				Point2D[] boundingVertices = new Point2D[1];
				boundingVertices[0] = vertices[index-1];
				return boundingVertices;
			} else { // polygon
				Point2D[] boundingVertices = new Point2D[2];
				boundingVertices[0] = vertices[index-1];
				boundingVertices[1] = vertices[0];
				return boundingVertices;
			}
		}
		
		// no special case.
		else { 
			Point2D[] boundingVertices = new Point2D[2];
			boundingVertices[0] = vertices[index+1];
			boundingVertices[1] = vertices[index-1];
			return boundingVertices;
		}
	}

	// If the feature is a line it must have at least 2 points after
	// the delete. If the feature is a polygon it must have at least 3 points
	// after the delete.
	public boolean canDeleteVertex(Feature f) {
		int numPoints = f.getPath().getVertices().length;
		switch (f.getPath().getType()) {
		case FPath.TYPE_POLYGON:
			return numPoints > 3;
		case FPath.TYPE_POLYLINE:
			return numPoints > 2;
		case FPath.TYPE_POINT:
		default:
			return false;
		}
	}

	// returns the indices of the vertices of this feature
	// that bound the rectangle - useful when adding new points  
	// TODO: this is AWFUL and needs rewriting
	private int[] getBoundingIndices(Feature f, Point2D worldPoint, Rectangle2D rect)
	{
		Rectangle2D rect1 = new Rectangle2D.Double( rect.getX() + 360,
							    rect.getY(), 
							    rect.getWidth(), 
							    rect.getHeight());
		Rectangle2D rect2 = new Rectangle2D.Double( rect.getX() - 360, 
							    rect.getY(), 
							    rect.getWidth(), 
							    rect.getHeight());
		Point2D[] vertices = f.getPath().getWorld().getVertices();
		Line2D line;
		for (int i=0; i<vertices.length; i++ ) {
			if(i+1 < vertices.length) {
				line = new Line2D.Double(vertices[i], vertices[i+1]);
				if( rect.intersectsLine(line)  ||
				    rect1.intersectsLine(line) ||
				    rect2.intersectsLine(line) ) {
					return new int[]{i, i+1};
				}
			}
			// need to check the last segment of a polygon, which is 
			// the closing line segment
			else if( f.getPath().getType() == FPath.TYPE_POLYGON && i == vertices.length -1) {
				line = new Line2D.Double(vertices[vertices.length-1], vertices[0]);
				if(rect.intersectsLine(line) ||
				   rect1.intersectsLine(line) ||
				   rect2.intersectsLine(line) ) {
					return new int[]{vertices.length-1,0};
				}
				
			}
		}
		return null;
	}
	




	// This is an overwrite of the standarad layer getMenuItems() method.
	public Component [] getMenuItems( Point2D wp)
	{
		if (wp==null){
			return null;
		}
		this.worldPt = wp;
		this.rect    = getProj().getClickBox( worldPt, PROXIMITY_BOX_SIDE);
		
		// build the list of menu items.
		List<JMenuItem> menuList = new ArrayList<JMenuItem>();
		if (changeModeOK){
			// Determine which mode the menu should be in.
			if (getMode() == ADD_FEATURE_MODE){
				popupButton = addModeRadioButton;
				addModeRadioButton.setSelected(true);
			} else if ( getMode() == SELECT_FEATURE_MODE){
				popupButton = selectModeRadioButton;
				selectModeRadioButton.setSelected(true);
			}
			
			//if (popupButton!=null){
			//	popupButton.setSelected(true);
			//}
			menuList.add( addModeRadioButton);
			menuList.add( selectModeRadioButton);
		}

		if (deleteFeaturesOK){
			// One should only be able to delete a selected row if there is
			// in fact at least one row selected.
			if (shapeLayer.getSelections().size() >0){
				deleteRowMenuItem.setEnabled(true);
			} else {
				deleteRowMenuItem.setEnabled(false);
			}
			menuList.add( deleteRowMenuItem);
		}

		if (zorderOK){
		  // The Zorder menu should only be enabled if there is one selection.
		  if (shapeLayer.getSelections().size() >0){
		     zOrderMenuItem.setEnabled(true);
		  } else {
		     zOrderMenuItem.setEnabled(false);
		  }
		  menuList.add(zOrderMenuItem);
		}
		
		if (addVertexOK || deleteVertexOK) {
			boolean addPoints = false;
			boolean delPoints = false;
			for (Iterator<? extends Feature> it = shapeLayer.getIndex().queryUnwrappedWorld(rect); it.hasNext(); ) {
				Feature f = it.next();
				if (shapeLayer.getSelections().contains(f) &&
						f.getPath().getType() != FPath.TYPE_POINT &&
						f.getPath().getWorld().intersects(rect)) {
					if (getIntersectingVertex(f, rect) == null) {
						addPoints = true;
					} else {
						delPoints = true;
					}
				}
			}
			if (addVertexOK) {
				addPointMenuItem.setEnabled(addPoints);
				menuList.add( addPointMenuItem);
			}
			if (deleteVertexOK) {
				deletePointMenuItem.setEnabled(delPoints);
				menuList.add( deletePointMenuItem);
			}
		}
		
		Component[] menuItems = (Component [])menuList.toArray( new Component[0]);
		return menuItems;
	}
    
	// The constructor for the class that sets up the components and 
	// all the behavior for those components.
	private void setupContextMenu(){
		// set up the context menu items.
		addModeRadioButton       = new JRadioButtonMenuItem( "Add Features");
		selectModeRadioButton    = new JRadioButtonMenuItem( "Select Features");
		zOrderMenuItem           = new ZOrderMenu("Z-order", shapeLayer.getFeatureCollection(), shapeLayer.getSelections());
		deletePointMenuItem      = new JMenuItem( "Delete Point");
		addPointMenuItem         = new JMenuItem( "Add Point");
		deleteRowMenuItem        = new JMenuItem( "Delete Selected Features");
		
		ButtonGroup toolSelectButtonGroup = new ButtonGroup();
		toolSelectButtonGroup.add(addModeRadioButton);
		toolSelectButtonGroup.add(selectModeRadioButton);
		switch ( getMode()){
		case FeatureMouseHandler.ADD_FEATURE_MODE:
			popupButton = addModeRadioButton;
			break;
		case FeatureMouseHandler.SELECT_FEATURE_MODE:
			popupButton = selectModeRadioButton;
			break;
		default:
			popupButton = null;
			break;
		}
		
		// defines the behavior of the right-click popup menu items. 
		addModeRadioButton.addItemListener( new ItemListener() {
			public void itemStateChanged(ItemEvent event){
				if (addModeRadioButton.isSelected()){
					if (popupButton != addModeRadioButton){
						popupButton = addModeRadioButton;
						addModeRadioButton.setSelected(true);
						setMode( FeatureMouseHandler.ADD_FEATURE_MODE);
						shapeLayer.getSelections().clear();
					}
				}
			}
		});
		selectModeRadioButton.addItemListener( new ItemListener() {
				public void itemStateChanged(ItemEvent event){
					if (selectModeRadioButton.isSelected()){
						if (popupButton != selectModeRadioButton){
							popupButton = selectModeRadioButton;
							selectModeRadioButton.setSelected(true);
							setMode( FeatureMouseHandler.SELECT_FEATURE_MODE);
							// if we are changing to select mode, we need to get rid
							// of any selection line that is being drawn.
							initializeSelectionLine();
						}
					}
				}
			});
			
		deleteRowMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e){
				if (shapeLayer.getHistory() != null)
					shapeLayer.getHistory().mark();
				// if this action is legal for the dataset, it will cascade into
				// removing the features from the selections set as well
				shapeLayer.getFeatureCollection().removeFeatures(shapeLayer.getSelections());
			}
		});
		
		deletePointMenuItem.addActionListener( new ActionListener(){
			public void actionPerformed(ActionEvent e){
				if (rect==null){
					return;
				}
				for (Feature feature: shapeLayer.getSelections()) {
					if (feature.getPath().getWorld().intersects(rect)) {
						Point2D vertex = getIntersectingVertex( feature,rect);
						if (vertex!=null){
							if (canDeleteVertex( feature)) {
								deleteVertex( feature, vertex);
							} else {
								String message = "Cannot delete point. \n";
								switch (feature.getPath().getType())
								{
								case FPath.TYPE_POLYGON:
									message += "Polygons must have at least three vertices.";
									break;
								case FPath.TYPE_POLYLINE:
									message += "Polylines must have at least two vertices.";
									break;
								case FPath.TYPE_POINT:
									message += "Points must consist of one vertex";
									break;
								}
								JOptionPane.showMessageDialog(Main.getLManager(), message);
							}
						}
					}
				}
			}
		});

		addPointMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (rect == null || worldPt == null)
					return;

				for (Feature feature: shapeLayer.getSelections()) {
					if (feature.getPath().getType() != FPath.TYPE_POINT) {
						if (feature.getPath().getWorld().intersects(rect)) {
							addVertex(feature, worldPt, rect);
							break;
						}
					}
				}
			}
		});
	} // end: private void setupContextMenu()
} // end: class FeatureMouseHandler
