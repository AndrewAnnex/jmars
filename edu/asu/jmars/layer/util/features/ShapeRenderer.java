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
import java.awt.Shape;
import java.awt.Stroke;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.ProjectionEvent;
import edu.asu.jmars.layer.ProjectionListener;
import edu.asu.jmars.layer.util.features.ProgressListener;
import edu.asu.jmars.util.LineType;

/**
 * Realizes the FeatureRenderer for the ShapeLayer. The ShapeLayer uses this
 * Renderer to render Features to the screen or the off-line buffer.
 * 
 * Methods in this class which take a Graphics2D as a parameter require a World
 * Graphics2D.
 * 
 * The ShapeRenderer pays attention to the following attributes within the
 * Feature object:
 * <ul>
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_DRAW_COLOR}</li>
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_FILL_COLOR}</li>
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_FONT}</li>
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_LABEL}</li>
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_LABEL_COLOR}</li>
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_LINE_WIDTH}</li>
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_LINE_DASH}</li>
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_LINE_DIRECTED}</li>
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_PATH}</li>
 * This is a required attribute.
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_SELECTED}</li>
 * This is a required attribute.
 * <li>{@link edu.asu.jmars.layer.util.features.Feature#FIELD_SHOW_LABEL}</li>
 * </ul>
 * 
 * The ShapeRenderer is cognizant with the following styling attributes:
 * <ul>
 * <li>{@link #STYLE_KEY_DRAW_COLOR}</li>
 * <li>{@link #STYLE_KEY_DRAW_SELECTED_ONLY}</li>
 * Not available on a per-Feature basis.
 * <li>{@link #STYLE_KEY_FILL_COLOR}</li>
 * <li>{@link #STYLE_KEY_FILL_POLYGONS}</li>
 * <li>{@link #STYLE_KEY_FONT}</li>
 * <li>{@link #STYLE_KEY_LABEL}</li>
 * <li>{@link #STYLE_KEY_LABEL_COLOR}</li>
 * <li>{@link #STYLE_KEY_LABEL_OFFSET}</li>
 * <li>{@link #STYLE_KEY_LINE_DASH}</li>
 * <li>{@link #STYLE_KEY_LINE_WIDTH}</li>
 * <li>{@link #STYLE_KEY_POINT_SIZE}</li>
 * <li>{@link #STYLE_KEY_SHOW_LABELS}</li>
 * <li>{@link #STYLE_KEY_SHOW_LINE_DIRECTION}</li>
 * <li>{@link #STYLE_KEY_SHOW_VERTICES}</li>
 * <li>{@link #STYLE_KEY_STROKE}</li>
 * </ul>
 * 
 * The ShapeRenderer maps the Feature attribute Field values to style
 * attributes. Any style attributes which are not mapped by the the Feature
 * attributes are filled-in from default style. The resulting style has all the
 * styling attributes. If the user has also specified overrides for the style
 * attributes, these are applied to the style. This gives us the effective
 * style. In short, the effective style is derived from default style overlaid
 * with Feature->Style mapping overlaid with style overrides.
 * <p>
 * Use {@link #setDefaultAttribute(String, Object)} to modify the styling
 * defaults and {@link #setOverrideAttribute(String, Object)} to modify the
 * overrides.
 * <p>
 * The ShapeRenderer also allows the user to override the Field to request the
 * <em>path</em> attribute from the Feature object. This takes care of the
 * situation when the <em>path</em> attribute is stored in Spatial coordinates
 * in the Feature object and there is a derived attribute, say,
 * <em>world_path</em> which contains the world coordinates for the Feature
 * path.
 * <p>
 * During {@link #drawAll(Graphics2D, Collection)}, a progress notification is
 * sent to each ProgressListener registered with the Renderer. This notification
 * is generated on every draw.
 * <p>
 * A {@link #drawAll(Graphics2D, Collection)} may be aborted in middle by
 * issuing a {@link #stopDrawing()}. This call has no effect when the
 * ShapeRenderer is not currently drawing.
 * <p>
 * 
 * @author saadat
 * 
 */
public class ShapeRenderer implements FeatureRenderer, ProjectionListener {

	/**
	 * Owner LView.
	 */
	private Layer.LView lView;

	/**
	 * Default style, gap-fill for attributes not specified by the Feature
	 * object.
	 */
	private HashMap defaultAttrs = new HashMap();

	/**
	 * Type of the value associated with each style attribute.
	 */
	private HashMap attrTypes = new HashMap();

	/**
	 * Overrides for various style attributes. These override both the default
	 * as well as Feature object specified attributes.
	 */
	private HashMap overrideAttrs = new HashMap();

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
	private List listeners = new LinkedList();

	/**
	 * Clipping rectangles used to discard data outside viewing boundary
	 * trivially.
	 * <p>
	 * <b>Caution:</b> Do not use this value directly, use the accessor method
	 * instead.
	 */
	private Rectangle2D[] clippingRectangles = null;
	
	// These are our defaults
	/**
	 * Default draw color or line color.
	 */
	public static final Color defaultDrawColor = Color.WHITE;

	/**
	 * Default polygon fill color.
	 */
	public static final Color defaultFillColor = Color.RED;

	/**
	 * Default label color.
	 */
	public static final Color defaultLabelColor = Color.WHITE;

	/**
	 * Default line width for drawing polygonal outlines. This attribute is a
	 * special in the way it is used. When used in the context of DefaultStyle,
	 * it is interpreted as minimum line width. When used in the the context of
	 * OverrideStyle, it is interpreted as the maximum line width.
	 */
	public static final Float defaultLineWidth = new Float(0); // single-pixel

	/**
	 * Default point size for drawing point shapes.
	 */
	public static final Integer defaultPointSize = new Integer(3);
	
	// line

	/**
	 * Default line dashing pattern index for polygonal outlines.
	 * 
	 * @see edu.asu.jmars.util.LineType
	 */
	public static final LineType defaultLineDash = new LineType(); // solid

	// line

	/**
	 * Default Stroke for drawing polygonal outlines. In reality this default
	 * value is not reused ever. The Stroke value is recomputed on each Feature
	 * draw.
	 */
	public static final Stroke defaultStroke = new BasicStroke(0);

	// TODO Figure out a better way of getting the default Font
	// GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()[0]; does
	// not work
	/**
	 * Default Font for drawing labels. It is currently set to "Ariel-Bold-14"
	 * for lack of a better mechanism to get the default system Font that I know
	 * about.
	 */
	public static final Font defaultFont = Font.decode("Ariel-Bold-14");

	/**
	 * Default label string.
	 */
	public static final String defaultLabelString = "";

	/**
	 * Default offset (in pixels) of the label from the center of Feature.
	 */
	public static final Point2D defaultLabelOffsetPixels = new Point2D.Float(
			3.0f, 0.0f); // not a user customizable param

	/**
	 * Default control flag controlling whether a Feature object will be drawn
	 * with exagerated vertices or not.
	 */
	public static final Boolean defaultShowVertices = Boolean.TRUE; // draw

	// thick
	// vertex
	// boundary

	/**
	 * Default control flag controlling whether a polyline Feature object which
	 * has direction information, will be drawing with a tail end Arrow.
	 */
	public static final Boolean defaultShowLineDirection = Boolean.FALSE;

	/**
	 * Default control flag controlling whether label on the Feature object will
	 * be rendered or not.
	 */
	public static final Boolean defaultShowLabels = Boolean.TRUE;

	/**
	 * Default control flag controlling whether closed polygonal Feature objects
	 * will be rendered filled.
	 */
	public static final Boolean defaultFillPolygons = Boolean.TRUE;

	/**
	 * Length (in pixels) of the side of the box used to represent an exagerated
	 * vertex. In other words, this is the length of the side of the box drawn
	 * around a vertex such that the vertex stands out from the line which
	 * contains it.
	 */
	public static final int defaultVertexBoxSide = 3;

	/**
	 * BasicStroke's end-cap style.
	 */
	public static final int defaultStrokeCapStyle = BasicStroke.CAP_BUTT;

	/**
	 * BasicStroke's end-join style.
	 */
	public static final int defaultStrokeJoinStyle = BasicStroke.JOIN_MITER;

	/**
	 * BasicStroke's miter-limit.
	 */
	public static final float defaultMiterLimit = 10.0f;

	/**
	 * Height of the arrowhead from the base (in pixels).
	 */
	public static final double ahHeight = 15;

	/**
	 * Width of the arrowhead base (in pixels).
	 */
	public static final double ahWidth = 12;

	/**
	 * Half the width of the arrowhead base (in pixels).
	 */
	public static final double ahHalfWidth = ahWidth / 2.0;

	/**
	 * A standard arrowhead based on the static width and height parameters. The
	 * arrowhead is aligned with the x-axis with its tip at the origin and the
	 * base of the triangle extending in the negative-X direction.
	 */
	public static final GeneralPath arrowHead;
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

	// Styles to be used as keys for the style HashMap built during draw.
	/**
	 * Style key for the drawing/line color. Associated value is a Color object.
	 */
	public static final String STYLE_KEY_DRAW_COLOR = "draw_color";

	/**
	 * Style key for the fill color. Associated value is a Color object.
	 */
	public static final String STYLE_KEY_FILL_COLOR = "fill_color";

	/**
	 * Style key for the label color. Associated value is a Color object.
	 */
	public static final String STYLE_KEY_LABEL_COLOR = "label_color";

	/**
	 * Style key for the line width. Associated value is a Float.
	 */
	public static final String STYLE_KEY_LINE_WIDTH = "line_width";

	/**
	 * Style key for the point size, in pixels. Associated value is an Integer.
	 */
	public static final String STYLE_KEY_POINT_SIZE = "point_size";
	
	/**
	 * Style key for the line dash pattern. Associated value is a
	 * {@link LineType} object.
	 */
	public static final String STYLE_KEY_LINE_DASH = "line_dash";

	/**
	 * Style key for the font used for rendering labels. Associated value is a
	 * Font.
	 */
	public static final String STYLE_KEY_FONT = "font";

	/**
	 * Style key for the label. Associated value is a String.
	 */
	public static final String STYLE_KEY_LABEL = "label";

	/**
	 * Style key for the label offset. Associate value is a Point2D.
	 */
	public static final String STYLE_KEY_LABEL_OFFSET = "text_offset";

	/**
	 * Style key for the line direction control flag. Associated value is a
	 * Boolean.
	 */
	public static final String STYLE_KEY_SHOW_LINE_DIRECTION = "draw_line_directed";

	/**
	 * Style key for the draw vertices control flag. Associated value is a
	 * Boolean.
	 */
	public static final String STYLE_KEY_SHOW_VERTICES = "draw_vertices";

	/**
	 * Style key for the show labels control flag. Associated value is a
	 * Boolean.
	 */
	public static final String STYLE_KEY_SHOW_LABELS = "show_labels";

	/**
	 * Style key for the fill control flag. Associated value is a Boolean.
	 */
	public static final String STYLE_KEY_FILL_POLYGONS = "fill";

	/**
	 * Feature attribute Field to Style key mapping.
	 */
	public static final Object[][] fieldsToStyleKeysEntries = new Object[][] {
			{ Field.FIELD_DRAW_COLOR, STYLE_KEY_DRAW_COLOR },
			{ Field.FIELD_FILL_COLOR, STYLE_KEY_FILL_COLOR },
			{ Field.FIELD_LABEL_COLOR, STYLE_KEY_LABEL_COLOR },
			{ Field.FIELD_LINE_WIDTH, STYLE_KEY_LINE_WIDTH },
			{ Field.FIELD_POINT_SIZE, STYLE_KEY_POINT_SIZE },
			{ Field.FIELD_LINE_DASH, STYLE_KEY_LINE_DASH },
			{ Field.FIELD_LINE_DIRECTED, STYLE_KEY_SHOW_LINE_DIRECTION },
			{ Field.FIELD_FONT, STYLE_KEY_FONT },
			{ Field.FIELD_LABEL, STYLE_KEY_LABEL },
			{ Field.FIELD_SHOW_LABEL, STYLE_KEY_SHOW_LABELS },
			{ Field.FIELD_FILL_POLYGON, STYLE_KEY_FILL_POLYGONS }, };

	/**
	 * Feature attribute Field to Style key mapping.
	 */
	private static HashMap fieldsToStyleKeysMap = new HashMap();
	static {
		for (int i = 0; i < fieldsToStyleKeysEntries.length; i++)
			fieldsToStyleKeysMap.put(fieldsToStyleKeysEntries[i][0],
					fieldsToStyleKeysEntries[i][1]);
	}

	/**
	 * User modifiable style elements, their default values and the value types.
	 */
	public static final Object[][] styleKeysToDefaults = new Object[][] {
			{ STYLE_KEY_DRAW_COLOR, defaultDrawColor, Color.class },
			{ STYLE_KEY_FILL_COLOR, defaultFillColor, Color.class },
			{ STYLE_KEY_LABEL_COLOR, defaultLabelColor, Color.class },
			{ STYLE_KEY_LINE_WIDTH, defaultLineWidth, Number.class },
			{ STYLE_KEY_POINT_SIZE, defaultPointSize, Number.class },
			{ STYLE_KEY_LINE_DASH, defaultLineDash, LineType.class },
			{ STYLE_KEY_SHOW_LINE_DIRECTION, defaultShowLineDirection, Boolean.class },
			{ STYLE_KEY_FONT, defaultFont, Font.class },
			{ STYLE_KEY_LABEL, defaultLabelString, String.class },
			{ STYLE_KEY_LABEL_OFFSET, defaultLabelOffsetPixels, Point2D.class },
			{ STYLE_KEY_SHOW_VERTICES, defaultShowVertices, Boolean.class },
			{ STYLE_KEY_SHOW_LABELS, defaultShowLabels, Boolean.class },
			{ STYLE_KEY_FILL_POLYGONS, defaultFillPolygons, Boolean.class },
	};

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
		Main.addProjectionListener(this);

		for (int i = 0; i < styleKeysToDefaults.length; i++) {
			defaultAttrs.put(styleKeysToDefaults[i][0],
					styleKeysToDefaults[i][1]);

			attrTypes.put(styleKeysToDefaults[i][0], styleKeysToDefaults[i][2]);
		}
	}

	/**
	 * Returns a mapping of Field to style key for all the attribute fields that
	 * participate in the rendering style process. Note that the two required
	 * Feature fields, i.e. <code>path</code> and <code>selected</code> are
	 * not a part of this map.
	 * 
	 * @return Feature attribute Field to Style key mapping used by the
	 *         ShapeRenderer to construct style.
	 */
	public static Map getFieldToStyleKeysMap() {
		return Collections.unmodifiableMap(fieldsToStyleKeysMap);
	}

	/**
	 * Returns a set of all the style keys.
	 * 
	 * @return All style keys that this renderer knows/cares about.
	 */
	public static Set getAllStyleKeys() {
		Set styleKeys = new HashSet();

		for (int i = 0; i < styleKeysToDefaults.length; i++)
			styleKeys.add(styleKeysToDefaults[i][0]);

		return styleKeys;
	}

	/**
	 * Returns the unadultrated default style.
	 * 
	 * @return Default style.
	 */
	public static Map getDefaultStyle() {
		HashMap defaultStyle = new HashMap();

		for (int i = 0; i < styleKeysToDefaults.length; i++)
			defaultStyle.put(styleKeysToDefaults[i][0],
					styleKeysToDefaults[i][1]);

		return defaultStyle;
	}

	/**
	 * Converts Feature attributes to a style. The feature is constructed in the
	 * following way:
	 * <ol>
	 * <li>Initialize style from defaults.</li>
	 * <li>Override style attributes from Feature attributes.</li>
	 * <li>Override style attributes from Override attributes this is the
	 * effective style.</li>
	 * <li>Reconstruct the <em>stroke</em> attribute based on the effective
	 * style.</li>
	 * </ol>
	 * 
	 * @param feat
	 *            Feature for which a style has to be computed.
	 * @return The style computed from the input Feature.
	 */
	public HashMap getStyle(Feature feat) {
		// Initialize style with defaults
		HashMap style = (HashMap) defaultAttrs.clone();

		Object obj;
		Field f;
		String styleKey;

		// Override non-default style attributes from the Feature
		Iterator fi = fieldsToStyleKeysMap.keySet().iterator();
		while (fi.hasNext()) {
			f = (Field) fi.next();
			styleKey = (String) fieldsToStyleKeysMap.get(f);
			if (styleKey != null && (obj = feat.getAttribute(f)) != null
					&& ((Class) attrTypes.get(styleKey)).isInstance(obj))
				style.put(fieldsToStyleKeysMap.get(f), obj);
		}

		// The STYLE_KEY_LINE_WIDTH is special, it acts as minimum line
		// width when used in default style, and maximum when used in override.
		style.put(STYLE_KEY_LINE_WIDTH, new Double(Math
				.max(((Number) style.get(STYLE_KEY_LINE_WIDTH)).doubleValue(),
						((Number) defaultAttrs.get(STYLE_KEY_LINE_WIDTH))
								.doubleValue())));

		// Override with user specified overrides
		style.putAll(overrideAttrs);

		return style;
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
	protected double getMagnifiedLineWidth(double lineWidth) {
		int magnification = getMagnification();
		return lineWidth / magnification;
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

	/**
	 * Check to see if the given feature overlaps the viewing region.
	 * 
	 * @param f
	 *            Feature object to test.
	 * @return true if the Feature object overlaps the clipping rectangles,
	 *         false otherwise.
	 */
	protected boolean overlapsClippingRectangles(Feature f) {
		FPath path = (FPath)f.getAttribute(Field.FIELD_PATH);
		GeneralPath p = path.getWorld().getGeneralPath();
		if (p == null)
			return false;

		Rectangle2D pathBoundary = normalizeRectangle(p.getBounds2D());
		Rectangle2D[] clipRects = getClippingRectangles();

		if (f.getPathType() == Feature.TYPE_POINT)
			for (int i = 0; i < clipRects.length; i++) {
				if (clipRects[i].contains(pathBoundary.getX(), pathBoundary.getY()))
					return true;
			}
		else
			for (int i = 0; i < clipRects.length; i++) {
				if (clipRects[i].intersects(pathBoundary))
					return true;
			}

		return false;
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
			if (!overlapsClippingRectangles(f))
				return; // skip rectangles not in our clipping boundary.

			// Get the req'd path field
			FPath path = f.getPath();
			GeneralPath p = path.getWorld().getGeneralPath();
			int type = f.getPathType();
			if (type == Feature.TYPE_NONE || p == null)
				return;

			// TODO: think about caching Styles!
			// Reduce Feature to a Style map.
			HashMap style = getStyle(f);

			// Install various pieces of style as needed and draw.

			// Draw filled polygon.
			if (type == Feature.TYPE_POINT) {
				g2w.setColor((Color) style.get(STYLE_KEY_FILL_COLOR));
				fillVertices(g2w, p, ((Integer)style.get(STYLE_KEY_POINT_SIZE)).intValue());
			} else if (type == Feature.TYPE_POLYGON) {
				g2w.setColor((Color) style.get(STYLE_KEY_FILL_COLOR));
				if (((Boolean) style.get(STYLE_KEY_FILL_POLYGONS))
						.booleanValue())
					g2w.fill(p);
			}

			// Draw polygonal outline.
			double lineWidth = ((Number) style.get(STYLE_KEY_LINE_WIDTH)).doubleValue();
			float[] dashPattern = ((LineType) style.get(STYLE_KEY_LINE_DASH)).getDashPattern();

			// TODO: THIS IS FOR THE FUTURE (WHERE NO MAN HAS GONE BEFORE ...
			// CAPTAIN!)
			// TODO: Add caching here: Cache: <lineWidth,lineDash,ProjObj> ->
			// Stroke
			// TODO: Drop Stroke cache on a Projection change
			Stroke stroke = new BasicStroke(
					(float) getMagnifiedLineWidth(lineWidth),
					defaultStrokeCapStyle, defaultStrokeJoinStyle,
					defaultMiterLimit, getMagnifiedDashPattern(dashPattern), 0);

			g2w.setStroke(stroke);
			g2w.setColor((Color) style.get(STYLE_KEY_DRAW_COLOR));
			if (type == Feature.TYPE_POINT)
				drawVertices(g2w, p, ((Integer)style.get(STYLE_KEY_POINT_SIZE)).intValue());
			else
				g2w.draw(p);

			// Switch to non-patterned stroke to draw vertices and arrows.
			stroke = new BasicStroke((float) getMagnifiedLineWidth(lineWidth),
					defaultStrokeCapStyle, defaultStrokeJoinStyle,
					defaultMiterLimit, null, 0);

			g2w.setStroke(stroke);

			// Draw vertices.
			if (type != Feature.TYPE_POINT && ((Boolean) style.get(STYLE_KEY_SHOW_VERTICES)).booleanValue())
				drawVertices(g2w, p, defaultVertexBoxSide);

			// Draw direction arrows.
			if (type == Feature.TYPE_POLYLINE) {
				if (((Boolean) style.get(STYLE_KEY_SHOW_LINE_DIRECTION))
						.booleanValue()) {
					Line2D lastSeg = getLastSegment(p);
					GeneralPath arrowHead = makeArrowHead(lastSeg);
					g2w.fill(arrowHead);
				}
			}

			// Draw optional text.
			if (((Boolean) style.get(STYLE_KEY_SHOW_LABELS)).booleanValue()) {
				String label = (String) style.get(STYLE_KEY_LABEL);
				Color labelColor = (Color) style.get(STYLE_KEY_LABEL_COLOR);
				Font font = (Font) style.get(STYLE_KEY_FONT);
				Point2D labelOffset = (Point2D) style
						.get(STYLE_KEY_LABEL_OFFSET);
				labelOffset = getMagnifiedTextOffset(labelOffset);

				Point2D center = path.getWorld().getCenter();

				g2w.setFont(font);
				g2w.setColor(labelColor);
				g2w.drawString(label, (float) center.getX(), (float) center
						.getY());
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
	 * Aggregate a collection of shapes into one GeneralPath.
	 * 
	 * @param shapes
	 *            An array of Shape objects to be aggregated.
	 * @return A GeneralPath which is an aggregate of all the input shapes.
	 */
	protected GeneralPath aggregateShapes(Shape[] shapes) {
		GeneralPath p = new GeneralPath();

		for (int i = 0; i < shapes.length; i++)
			p.append(shapes[i], false);

		return p;
	}

	/**
	 * Returns true if the value specified for the given key is of the correct
	 * type, false otherwise.
	 * 
	 * @param key
	 *            A style key.
	 * @param value
	 *            The value to be associated with the style key.
	 * @return true if the style key and value pair are compatible, false
	 *         otherwise.
	 */
	protected boolean isCorrectType(String key, Object value) {
		if (key == null || value == null)
			throw new IllegalArgumentException(
					"setDefaultAttribute(): Neither key nor value can be null.");

		Class expectedValueClass = (Class) attrTypes.get(key);
		if (!expectedValueClass.isInstance(value))
			return false;

		return true;
	}

	/**
	 * Set default value for a styling attribute. Null keys or values are not
	 * allowed. If passed an IllegalArgumentException is thrown. In addition,
	 * the types must not deviate from the expected types. If that happens, an
	 * IllegalArgumentException is thrown.
	 * 
	 * @param key
	 *            A style key.
	 * @param value
	 *            The value associated with the style key.
	 * @return The previous value of the style key or null if no such key
	 *         existed.
	 */
	public Object setDefaultAttribute(String key, Object value) {
		// Ensure proper type of attributes
		if (!isCorrectType(key, value))
			throw new IllegalArgumentException(
					"setDefaultAttribute(): Trying to set \"" + key
							+ "\" to value type \"" + value.getClass()
							+ "\". Expected value type \"" + attrTypes.get(key)
							+ "\".");

		return defaultAttrs.put(key, value);
	}

	/**
	 * Get default value for the given styling attribute.
	 * 
	 * @param key
	 *            A style key.
	 * @return The value associated with the style key or null if no such key
	 *         exits.
	 */
	public Object getDefaultAttribute(String key) {
		return defaultAttrs.get(key);
	}

	/**
	 * Set override value for a styling attribute. Null keys or values are not
	 * allowed. If passed an IllegalArgumentException is thrown. In addition,
	 * the value types may not deviate from the expected types. If this happens,
	 * an IllegalArgumentException is thrown.
	 * 
	 * The override values take precedence over the default attribute values as
	 * well as the feature specified values.
	 * 
	 * @param key
	 *            A style key.
	 * @param value
	 *            The value associated with the key.
	 * @return The old value associated with the style key or null if no such
	 *         key,value mapping existed.
	 */
	public Object setOverrideAttribute(String key, Object value) {
		// Ensure proper type of attributes
		if (!isCorrectType(key, value))
			throw new IllegalArgumentException(
					"setOverrideAttribute(): Trying to set \"" + key
							+ "\" to value type \"" + value.getClass()
							+ "\". Expected value type \"" + attrTypes.get(key)
							+ "\".");

		return overrideAttrs.put(key, value);
	}

	/**
	 * Get override value for the given styling attribute.
	 * 
	 * @param key
	 *            A style key.
	 * @return The value associated with the style key.
	 */
	public Object getOverrideAttribute(String key) {
		return overrideAttrs.get(key);
	}

	/**
	 * Removes the specified override styling attribute.
	 * 
	 * @param key
	 *            A style key.
	 * @return The current value of the style key, if any of null if no such key
	 *         exists.
	 */
	public Object removeOverrideAttribute(String key) {
		return overrideAttrs.remove(key);
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
		Iterator li = listeners.iterator();
		while (li.hasNext()) {
			ProgressListener pl = (ProgressListener) li.next();
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

		// Discard the current clipping rectangles. We'll get them again when
		// needed.
		clippingRectangles = null;
	}

	/**
	 * Returns current magnification.
	 * 
	 * @return The current magnification as pixels per degree.
	 */
	public int getMagnification() {
		// TODO: See if MultiProjection object can be linked in directly instead
		// of LView.
		if (lView == null)
			return 1;
		return lView.getProj().getPPD();
	}

	/**
	 * Returns a rectangle derived from the input rectangle such that its x
	 * coordinate is between -180 and 180 with a maximum width of 360.
	 * 
	 * @param rect
	 *            The rectangle to normalize.
	 * @return A new rectangle which is the normalized version of input
	 *         rectangle.
	 */
	protected Rectangle2D normalizeRectangle(Rectangle2D rect) {
		Rectangle2D.Double r1 = new Rectangle2D.Double();
		r1.setFrame(rect);
		if (r1.width > 360) {
			r1.x = -180;
			r1.width = 360;
		} else {
			while (r1.x < -180) {
				r1.x += 360;
			}
			while (r1.x > 180) {
				r1.x -= 360;
			}
		}
		return r1;
	}

	/**
	 * Returns world-coordinate rectangles corresponding to the current viewing
	 * rectangle of the LView. These rectangles are used as clipping boundaries
	 * to discard data trivially if it will not fall within the veiwing
	 * rectangle.
	 * 
	 * @return An array of three clipping rectangles.
	 */
	public synchronized Rectangle2D[] getClippingRectangles() {
		if (clippingRectangles == null) {
			Rectangle2D viewingRect;
			if (lView == null)
				viewingRect = new Rectangle2D.Double(-1000, -1000, 2000, 2000);
			else
				viewingRect = lView.viewman2.getProj().getWorldWindow();

			Rectangle2D r = normalizeRectangle(viewingRect);
			Rectangle2D[] clipRects = new Rectangle2D[3];
			clipRects[0] = r;
			clipRects[1] = new Rectangle2D.Double(r.getX() - 360.0, r.getY(), r
					.getWidth(), r.getHeight());
			clipRects[2] = new Rectangle2D.Double(r.getX() + 360.0, r.getY(), r
					.getWidth(), r.getHeight());

			clippingRectangles = clipRects;
		}
		return clippingRectangles;
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
