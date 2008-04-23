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

import java.awt.Graphics2D;
import java.util.Collection;

/**
 * Rendering interface to render Feature objects.
 * 
 * A simple implementation of FeatureRenderer looks like
 * as follows:<p>
 * 
 * The FeatureRenderer pays attention to two attributes in
 * the Feature object. These attributes are lineColorField
 * and fillColorField.<p>
 * 
 * The FeatureRenderer has two defaults defined. One for 
 * line color and another for fill color. Let's assume these
 * are represented by the keys "line-color" and "fill-color"
 * respectively. The FeatureRenderer has defaults for both
 * built into it. These defaults are Color.BLUE for line-color
 * and Color.MAGENTA for fill-color.<p>
 * 
 * Let's assume that we have two Feature object that we want
 * to draw on the screen. f1 has the lineColorField attribute
 * defined as Color.GREEN and fillColorField attribute defined
 * as Color.YELLOW. f2 has the lineColorField undefined, while
 * its fillColorField is defined as Color.ORANGE.<p>
 * 
 * When the Renderer renders f1 and f2, f1 is rendered with a
 * green outline filled with yellow, while f2 is rendered with
 * a blue outline filled with orange.<p>
 * 
 * <b>Note:</b>The Graphics2D which is used as an input to many
 * of the calls below may be a Spatial, World or Screen Graphics2D.
 * Hence, it is the caller's responsibility to ensure correctness
 * of the parameters being passed in.
 * 
 * @author saadat
 *
 */
public interface FeatureRenderer {
	/**
	 * Draws the specified Feature onto the given Graphics2D.
	 * The drawing code may take advantage of various Feature
	 * attributes to come up with its rendered representation
	 * or it may choose to completely ignore them.
	 * <p>
	 * Any Feature object given to the FeatureRenderer is
	 * required to have at least non-null <em>path</em> and
	 * <em>selected</em> attributes.
	 * 
	 * @param g2 Graphics2D to draw into.
	 * @param f Feature to render.
	 */
	void draw(Graphics2D g2, Feature f);
	
	/**
	 * Draws a collection of Feature objects onto the given Graphics2D.
	 * 
	 * @param g2 Graphics2D to draw into.
	 * @param fc A collection of Feature object to render.
	 */
	void drawAll(Graphics2D g2, Collection fc);
	
	/**
	 * Adds a resonable default for certain rendering style attribute.
	 * Let's assume that our FeatureRenderer renders the Features using the
	 * line width given by the line-width attribute of the Feature object.
	 * If the FeatureRenderer comes across a Feature object which does
	 * not have this attribute defined, then the FeatureRenderer will
	 * use the default specified by the user here.
	 * 
	 * @param key   Style key to set a default for.
	 * @param value Default value associated with the style key.
	 * @return      Previous value associated with the style key or null
	 *              if no such value existed.
	 */
	Object setDefaultAttribute(String key, Object value);
	
	/**
	 * Get the value of the reasonable default for an attribute that
	 * the FeatureRenderer pays attention to.
	 * 
	 * @param key Style key to get the default for.
	 * @return    Value associated with the style key.
	 */
	Object getDefaultAttribute(String key);
}
