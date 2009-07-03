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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.ProjectionEvent;
import edu.asu.jmars.layer.ProjectionListener;

/**
 * Realizes the FeatureRenderer for the ShapeLayer. The ShapeLayer uses this
 * Renderer to render Features to the screen or the off-line buffer.
 * 
 * Methods in this class which take a Graphics2D as a parameter require a World
 * Graphics2D.
 * 
 * The ShapeRenderer uses an instance of {@link Styles} to get all of the info
 * it requires for rendering.  This distinction between feature attributes and
 * styles allows using a renderer with a fixed set of understood attributes
 * with any kind of Feature by mapping attributes to styles through the Styles
 * class.
 * 
 * During {@link #drawAll(Graphics2D, Collection)}, a progress notification is
 * sent to each ProgressListener registered with the Renderer. This notification
 * is generated on every draw.
 * 
 * A {@link #drawAll(Graphics2D, Collection)} may be aborted in middle by
 * issuing a {@link #stopDrawing()}. This call has no effect when the
 * ShapeRenderer is not currently drawing.
 */
public class ShapeRenderer implements ProjectionListener {
	private static final Point2D labelOffset = new Point2D.Float(3f,3f);
	
	/**
	 * Owner LView.
	 */
	private Layer.LView lView;

	/**
	 * Flag indicating that the ShapeRenderer has been instructed to abandon
	 * further drawing within the {@link #drawAll(Graphics2D, Collection)} loop.
	 */
	private volatile boolean stopDrawing = false;

	/**
	 * Flag indicating that the ShapeRenderer is currently within a
	 * {@link #drawAll(Graphics2D, Collection)} loop.
	 */
	private volatile boolean isDrawing = false;

	/**
	 * List of {@link ProgressListener}s.
	 */
	private List<ProgressListener> listeners = new ArrayList<ProgressListener>();
	
	/**
	 * BasicStroke's end-cap style.
	 */
	private static final int defaultStrokeCapStyle = BasicStroke.CAP_BUTT;

	/**
	 * BasicStroke's end-join style.
	 */
	private static final int defaultStrokeJoinStyle = BasicStroke.JOIN_MITER;

	/**
	 * BasicStroke's miter-limit.
	 */
	private static final float defaultMiterLimit = 10.0f;

	/**
	 * Height of the arrowhead from the base (in pixels).
	 */
	private static final double ahHeight = 15;

	/**
	 * Width of the arrowhead base (in pixels).
	 */
	private static final double ahWidth = 12;

	/**
	 * Half the width of the arrowhead base (in pixels).
	 */
	private static final double ahHalfWidth = ahWidth / 2.0;
	
	Styles styles = new Styles();
	private final Font font;
	
	/**
	 * A standard arrowhead based on the static width and height parameters. The
	 * arrowhead is aligned with the x-axis with its tip at the origin and the
	 * base of the triangle extending in the negative-X direction.
	 */
	private static final GeneralPath arrowHead;
	static {
		// Populate the default arrowhead as a head aligned with the X-axis
		// pointing at (0,0)
		GeneralPath ah = new GeneralPath();
		ah.moveTo(0, 0);
		ah.lineTo(-(float) ahHeight, -(float) ahHalfWidth);
		ah.lineTo(-(float) ahHeight, (float) ahHalfWidth);
		ah.closePath();
		arrowHead = ah;
	}

	/**
	 * Number of iterations between checking the {@link #stopDrawing} flag.
	 * Reading of <code>volatile</code> data has been conjectured to being
	 * slow.
	 */
	private static final int defaultStopDrawingCheckCount = 10;

	/**
	 * Creates an instance of the ShapeRenderer object. The instance takes the
	 * owner LView as a parameter.
	 * 
	 * @param lView
	 *            Owning LView.
	 */
	public ShapeRenderer(Layer.LView lView) {
		super();
		this.lView = lView;
		font = lView.getFont().deriveFont(Font.BOLD);
		Main.addProjectionListener(this);
	}
	
	/**
	 * Returns magnified dash-pattern by copying the input dash-pattern and
	 * scaling it with the LView PPD magnification. If the input dash-pattern is
	 * null, a null is returned.
	 * 
	 * @param dashPattern
	 *            The dash pattern to scale.
	 * @return Scaled dash pattern.
	 */
	protected float[] getMagnifiedDashPattern(float[] dashPattern) {
		int magnification = getMagnification();

		if (dashPattern != null) {
			dashPattern = (float[]) dashPattern.clone();
			for (int i = 0; i < dashPattern.length; i++)
				dashPattern[i] /= magnification;
		}

		return dashPattern;
	}

	/**
	 * Returns line-width scaled according to the current LView (PPD)
	 * magnification.
	 * 
	 * @param lineWidth
	 *            Line width to magnify.
	 * @return Magnified line width.
	 */
	protected float getMagnifiedLineWidth(double lineWidth) {
		int magnification = getMagnification();
		return (float)lineWidth / magnification;
	}

	/**
	 * Returns TextOffset according to current LView (PPD) magnification.
	 * 
	 * @param offset
	 *            Text offset to magnify.
	 * @return Magnified text offset.
	 */
	protected Point2D getMagnifiedTextOffset(Point2D offset) {
		double magnification = getMagnification();
		return new Point2D.Double(offset.getX() / magnification, offset.getY()
				/ magnification);
	}

	public Styles getStyles() {
		return styles;
	}
	
	public void setStyles(Styles styles) {
		this.styles = styles;
	}
	
	/**
	 * Draws the given Feature onto the specified World Graphics2D. While
	 * drawing, ignore everything that does not fall within our current display
	 * boundaries. In addition pay attention to the controlling flags, such as
	 * "show-label-off", "show-vertices-off", "minimum-line-width" etc.
	 * 
	 * @param g2w
	 *            World Graphics2D to draw into.
	 * @param f
	 *            Feature object to render.
	 */
	public void draw(Graphics2D g2w, Feature f) {
		try {
			g2w.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				styles.antialias.getValue(f)
					? RenderingHints.VALUE_ANTIALIAS_ON
					: RenderingHints.VALUE_ANTIALIAS_OFF);
			
			// Get the req'd path field
			FPath path = styles.geometry.getValue(f);
			GeneralPath p = path.getWorld().getGeneralPath();
			int type = path.getType();
			if (type == FPath.TYPE_NONE || p == null)
				return;

			// Install various pieces of style as needed and draw.

			// Draw filled polygon.
			if (type == FPath.TYPE_POINT) {
				g2w.setColor(styles.fillColor.getValue(f));
				fillVertices(g2w, p, styles.pointSize.getValue(f).intValue());
			} else if (type == FPath.TYPE_POLYGON) {
				g2w.setColor(styles.fillColor.getValue(f));
				if (styles.fillPolygons.getValue(f)) {
					g2w.fill(p);
				}
			}
			
			double lineWidth = styles.lineWidth.getValue(f).doubleValue();
			float[] dashPattern = styles.lineDash.getValue(f).getDashPattern();
			
			// TODO: THIS IS FOR THE FUTURE (WHERE NO MAN HAS GONE BEFORE ...
			// CAPTAIN!)
			// TODO: Add caching here: Cache: <lineWidth,lineDash,ProjObj> ->
			// Stroke
			// TODO: Drop Stroke cache on a Projection change
			Stroke stroke = new BasicStroke(
					getMagnifiedLineWidth(lineWidth),
					defaultStrokeCapStyle, defaultStrokeJoinStyle,
					defaultMiterLimit, getMagnifiedDashPattern(dashPattern), 0);

			g2w.setStroke(stroke);
			g2w.setColor(styles.lineColor.getValue(f));
			if (type == FPath.TYPE_POINT)
				drawVertices(g2w, p, styles.pointSize.getValue(f).intValue());
			else
				g2w.draw(p);

			// Switch to non-patterned stroke to draw vertices and arrows.
			stroke = new BasicStroke(getMagnifiedLineWidth(lineWidth),
					defaultStrokeCapStyle, defaultStrokeJoinStyle,
					defaultMiterLimit, null, 0);

			g2w.setStroke(stroke);

			// Draw vertices.
			if (type != FPath.TYPE_POINT && styles.showVertices.getValue(f))
				drawVertices(g2w, p, styles.vertexSize.getValue(f).intValue());

			// Draw direction arrows.
			if (type == FPath.TYPE_POLYLINE) {
				if (styles.showLineDir.getValue(f)) {
					Line2D lastSeg = getLastSegment(p);
					GeneralPath arrowHead = makeArrowHead(lastSeg);
					g2w.fill(arrowHead);
				}
			}

			// Draw optional text.
			if (styles.showLabels.getValue(f)) {
				String label = styles.labelText.getValue(f);
				Color labelColor = styles.labelColor.getValue(f);
				Point2D center = path.getWorld().getCenter();
				Point2D offset = getMagnifiedTextOffset(labelOffset);
				float x = (float)(center.getX() + offset.getX());
				float y = (float)(center.getY() + offset.getY());
				g2w.setFont(font);
				g2w.setColor(labelColor);
				g2w.drawString(label, x, y);
			}
		} catch (ClassCastException ex) {
			ex.printStackTrace();
		} catch (NullPointerException ex) {
			ex.printStackTrace();
		}
	}
	
	/**
	 * Shared vertex box used for various drawing functions.
	 * 
	 * @see #getSharedVertexBox(Graphics2D)
	 * @see #drawVertices(Graphics2D, GeneralPath)
	 * @see #fillVertices(Graphics2D, GeneralPath)
	 * @see #projectionChanged(ProjectionEvent)
	 */
	private Map sharedVertexBoxes = new HashMap();

	/**
	 * Returns the shared instance of vertex box used for various drawing
	 * routines. The vetex box is constructed using the LView's current
	 * projection and the preset defaultVertexBoxSide.
	 * 
	 * @return A shared instance of vertex box.
	 * 
	 * @see #sharedVertexBoxes
	 * @see #defaultVertexBoxSide
	 * @see #drawVertices(Graphics2D, GeneralPath)
	 * @see #fillVertices(Graphics2D, GeneralPath)
	 */
	private Rectangle2D.Float getSharedVertexBox(int width) {
		Rectangle2D.Float sharedVertexBox = (Rectangle2D.Float)sharedVertexBoxes.get(new Integer(width));
		if (sharedVertexBox == null) {
			sharedVertexBox = new Rectangle2D.Float();
			if (lView == null || lView.getProj() == null)
				sharedVertexBox.setFrame(-width / 2, -width / 2, width, width);
			else
				sharedVertexBox.setFrame(lView.getProj().getClickBox(
						new Point2D.Float(), width));
			
			sharedVertexBoxes.put(new Integer(width), sharedVertexBox);
		}
		
		return sharedVertexBox;
	}

	/**
	 * Draws exagerated vertices for the given GeneralPath. The vertices are
	 * drawn with the help of shared vertex box created by
	 * {@linkplain #getSharedVertexBox()}.
	 * 
	 * @param g2w
	 *            World graphics context in which the drawing takes place.
	 * @param p
	 *            GeneralPath for which exagerated vertices are drawn.
	 * @throws IllegalArgumentException
	 *             if the GeneralPath contains quadratic/cubic segments.
	 */
	private void drawVertices(Graphics2D g2w, GeneralPath p, int width) {
		float[] coords = new float[6];
		Rectangle2D.Float v = getSharedVertexBox(width);

		PathIterator pi = p.getPathIterator(null);
		while (!pi.isDone()) {
			switch (pi.currentSegment(coords)) {
			case PathIterator.SEG_MOVETO:
			case PathIterator.SEG_LINETO:
				v.x = coords[0] - v.width / 2.0f;
				v.y = coords[1] - v.height / 2.0f;
				g2w.draw(v);
				break;
			case PathIterator.SEG_CLOSE:
				break;
			default:
				throw new IllegalArgumentException(
						"drawVertices() called with a GeneralPath with quadratic/cubic segments.");
			}
			pi.next();
		}
	}

	/**
	 * Draws filled exagerated vertices for the given GeneralPath. The vertices
	 * are drawn with the help of shared vertex box created by
	 * {@linkplain #getSharedVertexBox()}.
	 * 
	 * @param g2w
	 *            World graphics context in which the drawing takes place.
	 * @param p
	 *            GeneralPath for which exagerated vertices are drawn.
	 * @throws IllegalArgumentException
	 *             if the GeneralPath contains quadratic/cubic segments.
	 */
	private void fillVertices(Graphics2D g2w, GeneralPath p, int pointWidth) {
		float[] coords = new float[6];
		Rectangle2D.Float v = getSharedVertexBox(pointWidth);

		PathIterator pi = p.getPathIterator(null);
		while (!pi.isDone()) {
			switch (pi.currentSegment(coords)) {
			case PathIterator.SEG_MOVETO:
			case PathIterator.SEG_LINETO:
				v.x = coords[0] - v.width / 2.0f;
				v.y = coords[1] - v.height / 2.0f;
				g2w.fill(v);
				break;
			case PathIterator.SEG_CLOSE:
				break;
			default:
				throw new IllegalArgumentException(
						"drawVertices() called with a GeneralPath with quadratic/cubic segments.");
			}
			pi.next();
		}
	}

	/**
	 * Draw all Features from the given FeatureCollection onto the specified
	 * World Graphics2D. On each draw registered ProgressListeners are notified.
	 * 
	 * @param g2w
	 *            World Graphics2D to draw into.
	 * @param fc
	 *            Collection of Feature objects to draw.
	 */
	public void drawAll(Graphics2D g2w, Collection fc) {
		// TODO: group features by associated styles so we only set things once
		
		isDrawing = true;

		int i = 0, n = fc.size();
		int count = defaultStopDrawingCheckCount;

		Iterator fi = fc.iterator();
		while (fi.hasNext()) {
			// We check every so often to see if the user has requested
			// that the drawing be stopped. Checking on volatile
			// stopDrawing is slow.
			if (++count >= defaultStopDrawingCheckCount) {
				count = 0;
				if (stopDrawing) {
					stopDrawing = false;
					isDrawing = false;
					return;
				}
			}

			draw(g2w, (Feature) fi.next());
			fireProgressEvent(i++, n);
		}

		stopDrawing = false;
		isDrawing = false;
	}

	/**
	 * Tells the Renderer to abandon the drawAll() method. Has no effect when
	 * the Renderer is currently not drawing.
	 */
	public void stopDrawing() {
		if (isDrawing)
			stopDrawing = true;
	}

	/**
	 * Returns a polygon containing the arrowhead for the specified line
	 * segment. The tip of the arrowhead is located at the second of the two
	 * points that make up the line segment.
	 * 
	 * @param lineSeg
	 *            Line segment for which an arrow is desired.
	 * @return Arrow ending at the second point of the line segment.
	 */
	protected GeneralPath makeArrowHead(Line2D lineSeg) {
		final int magnification = getMagnification();
		GeneralPath ah = (GeneralPath) arrowHead.clone();

		double x = lineSeg.getX2() - lineSeg.getX1();
		double y = lineSeg.getY2() - lineSeg.getY1();
		double norm = Math.sqrt(x * x + y * y);
		x /= norm;
		y /= norm;

		// Get angle and put it in the correct half circle.
		double theta = (y < 0) ? -Math.acos(x) : Math.acos(x);

		AffineTransform at = new AffineTransform();

		// Translate it to the end point of the line-segment.
		at.concatenate(AffineTransform.getTranslateInstance(lineSeg.getX2(),
				lineSeg.getY2()));

		// Rotate arrow to align with the given line-segment.
		at.concatenate(AffineTransform.getRotateInstance(theta));

		// Scale according to projection
		at.concatenate(AffineTransform.getScaleInstance(1.0 / magnification,
				1.0 / magnification));

		// Apply rotation and translation.
		ah.transform(at);

		return ah;
	}

	/**
	 * Returns the last line segment from a given GeneralPath. The GeneralPath
	 * must have such a segment, otherwise, an IllegalArgumentException is
	 * thrown.
	 * 
	 * @param p
	 *            GeneralPath for which the last segment is to be returned.
	 * @return The last line segment.
	 * @throws {@link IllegalArgumentException}
	 *             if cubic or quadratic coordinates are encountered, or the
	 *             polygon is a closed polygon or it does not contain enough
	 *             vertices.
	 */
	protected static Line2D getLastSegment(GeneralPath p) {
		PathIterator pi = p.getPathIterator(null);
		Point2D p1 = new Point2D.Double();
		Point2D p2 = new Point2D.Double();
		float[] coords = new float[6];
		int nSegVertices = 0;

		while (!pi.isDone()) {
			switch (pi.currentSegment(coords)) {
			case PathIterator.SEG_MOVETO:
				nSegVertices = 0;
			case PathIterator.SEG_LINETO:
				nSegVertices++;
				p1.setLocation(p2);
				p2.setLocation(coords[0], coords[1]);
				break;
			case PathIterator.SEG_CUBICTO:
			case PathIterator.SEG_QUADTO:
				throw new IllegalArgumentException(
						"getLastSegment() called with cubic/quadratic curve.");
			case PathIterator.SEG_CLOSE:
				throw new IllegalArgumentException(
						"getLastSegment() called with closed polygon.");
			}
			pi.next();
		}

		if (nSegVertices < 2) {
			throw new IllegalArgumentException(
					"getLastSegment() called with a path without a usable segment.");
		}

		return new Line2D.Double(p1, p2);
	}

	/**
	 * Returns first point from the given GeneralPath.
	 * 
	 * @param p
	 *            GeneralPath for which the first point is to be returned.
	 * @return The first point from the GeneralPath.
	 * @throws {@link IllegalArgumentException}
	 *             if there is no such point in the linear segmented input
	 *             GeneralPath.
	 */
	protected static Point2D getFirstPoint(GeneralPath p) {
		PathIterator pi = p.getPathIterator(null);
		float[] coords = new float[6];

		while (!pi.isDone()) {
			switch (pi.currentSegment(coords)) {
			case PathIterator.SEG_MOVETO:
			case PathIterator.SEG_LINETO:
				return new Point2D.Float(coords[0], coords[1]);
			default:
				break;
			}
			pi.next();
		}

		throw new IllegalArgumentException(
				"getFirstPoint() called with a path with no points.");
	}
	
	/**
	 * Returns LView that this Renderer is attached to.
	 * 
	 * @return The LView this Renderer is attached to.
	 */
	public Layer.LView getLView() {
		return lView;
	}
	
	/**
	 * Register a ProgressListener with this Renderer.
	 * 
	 * @param l
	 *            The ProgressListener to add.
	 */
	public void addProgressListener(ProgressListener l) {
		listeners.add(l);
	}
	
	/**
	 * Deregister a ProgressListener with this Renderer. Returns true if the
	 * listeners list contained this listener.
	 * 
	 * @param l
	 *            The ProgressListener to remove.
	 * @return True if the specified listener was found, false otherwise.
	 */
	public boolean removeProgressListener(ProgressListener l) {
		return listeners.remove(l);
	}
	
	/**
	 * Fires a progress update event. This event is transmitted to all the
	 * ProgressListeners.
	 * 
	 * @param i
	 *            Zero-based serial number of the element currently being
	 *            processed.
	 * @param n
	 *            Total number of elements.
	 */
	protected void fireProgressEvent(int i, int n) {
		for (ProgressListener pl: listeners) {
			pl.finished(i, n);
		}
	}
	
	/**
	 * Listen to projection change events.
	 * 
	 * @param e
	 *            The ProjectionEvent.
	 */
	public synchronized void projectionChanged(ProjectionEvent e) {
		// TODO Flush various caches that are on a per projection basis

		// Discard the shared vertex box. We'll build it again when we need it.
		sharedVertexBoxes.clear();
	}
	
	/**
	 * Returns current magnification.
	 * 
	 * @return The current magnification as pixels per degree.
	 */
	private int getMagnification() {
		// TODO: See if MultiProjection object can be linked in directly instead
		// of LView.
		if (lView == null)
			return 1;
		return lView.getProj().getPPD();
	}
	
	/**
	 * Dispose off various object references such that object finalization may
	 * happen correctly.
	 */
	public void dispose() {
		listeners.clear();
		Main.removeProjectionListener(this);
	}
}
