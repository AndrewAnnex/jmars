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


package edu.asu.jmars.layer.mosaics;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.LViewFactory;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.layer.WrappedMouseEvent;
import edu.asu.jmars.layer.Layer.LView;
import edu.asu.jmars.layer.map2.MapLViewFactory;
import edu.asu.jmars.layer.map2.MapSource;
import edu.asu.jmars.layer.util.features.FPath;
import edu.asu.jmars.layer.util.features.Feature;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.FeatureEvent;
import edu.asu.jmars.layer.util.features.FeatureListener;
import edu.asu.jmars.layer.util.features.FeatureRenderer;
import edu.asu.jmars.layer.util.features.FeatureUtil;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.ShapeRenderer;
import edu.asu.jmars.util.Util;

public class MosaicsLView extends LView implements MouseListener {
	Color fillColor;
	Color borderColor;
	Color borderColorSelected;
	FeatureCollection fc;
	
	public MosaicsLView(MosaicsLayer layer){
		super(layer);
		setBufferCount(2);
		
		addMouseListener(this);
		
		fillColor = Util.alpha(Color.BLUE, 128);
		borderColor = Color.white;
		borderColorSelected = Color.yellow;
		
		fc = layer.getFeatures();
		fc.addListener(new FeatureListener(){
			public void receive(FeatureEvent e) {
				if (e.type == FeatureEvent.CHANGE_FEATURE && e.fields.contains(Field.FIELD_SELECTED)){
					drawSelected();
				}
			}
		});
	}
	
	protected LView _new() {
		return new MosaicsLView((MosaicsLayer)getLayer());
	}

	protected Object createRequest(Rectangle2D where) {
		return where;
	}
	
	public FocusPanel getFocusPanel() {
		if (focusPanel == null)
			focusPanel = new MosaicsFocusPanel(this);
		
		return focusPanel;
	}

	private FeatureRenderer getFeatureRenderer(boolean forSelected){
		ShapeRenderer sr = new ShapeRenderer(this);
		sr.setDefaultAttribute(ShapeRenderer.STYLE_KEY_DRAW_COLOR, forSelected? borderColorSelected: borderColor);
		sr.setDefaultAttribute(ShapeRenderer.STYLE_KEY_LINE_WIDTH, new Double(forSelected? 2: 1));
		sr.setDefaultAttribute(ShapeRenderer.STYLE_KEY_FILL_COLOR, fillColor);
		sr.setDefaultAttribute(ShapeRenderer.STYLE_KEY_FILL_POLYGONS, forSelected? Boolean.FALSE: Boolean.TRUE);
		sr.setDefaultAttribute(ShapeRenderer.STYLE_KEY_SHOW_VERTICES, Boolean.FALSE);
		sr.setDefaultAttribute(ShapeRenderer.STYLE_KEY_SHOW_LINE_DIRECTION, Boolean.FALSE);
		sr.setDefaultAttribute(ShapeRenderer.STYLE_KEY_SHOW_LABELS, Boolean.FALSE);
		
		return sr;
	}
	
	private void drawAll(){
		if (!isVisible())
			return;
		
		clearOffScreen(0);
		FeatureRenderer sr = getFeatureRenderer(false);
		// TODO: This does not seem the right way of avoiding NullPointerException
		if (getOffScreenG2(0) != null)
			sr.drawAll(getOffScreenG2(0), fc.getFeatures());
		repaint();
	}
	
	private void drawSelected(){
		if (!isVisible())
			return;
		
		clearOffScreen(1);
		FeatureRenderer srSel = getFeatureRenderer(true);
		// TODO: This does not seem the right way of avoiding NullPointerException
		if (getOffScreenG2(1) != null)
			srSel.drawAll(getOffScreenG2(1), FeatureUtil.getSelectedFeatures(fc));
		repaint();
	}
	
	public void receiveData(Object layerData) {
		SwingUtilities.invokeLater(new Runnable(){
			public void run(){
				drawAll();
				drawSelected();
			}
		});
	}

	public String getName() {
		return "THEMIS Mosaics";
	}

	private Set<Feature> getFeaturesIntersecting(Rectangle2D worldRect){
		Set<Feature> underPt = new LinkedHashSet<Feature>();

		for(int i=0; i<fc.getFeatureCount(); i++){
			Feature f = fc.getFeature(i);
			if (f.getPath().getWorld().intersects(worldRect))
				underPt.add(f);
		}
		
		return underPt;
	}
	
	private Set<Feature> getFeaturesUnder(Point2D worldPt){
		return getFeaturesIntersecting(getProj().getClickBox(worldPt, 1));
	}
	
	public String getToolTipText(MouseEvent event) {
		if (viewman2.getActiveLView() != this)
			return null;

		Point2D worldPt = getProj().screen.toWorld(event instanceof WrappedMouseEvent?
				((WrappedMouseEvent)event).getRealPoint(): event.getPoint());
		Set<Feature> features = getFeaturesUnder(worldPt);
		
		if (features.isEmpty())
			return null;
		
		Set<Feature> selected = new HashSet<Feature>(FeatureUtil.getSelectedFeatures(features));
		
		StringBuffer sbuf = new StringBuffer();
		sbuf.append("<html>");
		for(Feature f: features){
			sbuf.append("<b>"+(selected.contains(f)?"<i>":"")+f.getAttribute(Field.FIELD_LABEL)+(selected.contains(f)?"</i>":"")+"</b><br>");
			String abstractText = (String)f.getAttribute(FeatureProviderWMS.FIELD_ABSTRACT);
			if (abstractText != null && abstractText.trim().length() > 0){
				sbuf.append("<table cellspacing=0 cellpadding=0>");
				sbuf.append("<tr>");
				sbuf.append("<td></td>");
				sbuf.append("<td>"+abstractText+"</td>");
				sbuf.append("</tr>");
				sbuf.append("</table>");
				sbuf.append("<br>");
			}
		}
		sbuf.append("</html>");
		
		return sbuf.toString();
	}

	public Component[] getContextMenu(Point2D worldPt){
		Set<Feature> features = getFeaturesUnder(worldPt);
		Set<Feature> selected = new HashSet<Feature>(FeatureUtil.getSelectedFeatures(features));
		List<Component> centerAtMenuItems = new ArrayList<Component>();
		List<Component> loadMenuItems = new ArrayList<Component>();
		JMenuItem menuItem;
		for(Feature f: features){
			menuItem = new JMenuItem(new CenterAtAction(f));
			if (selected.contains(f))
				menuItem.setFont(menuItem.getFont().deriveFont(Font.ITALIC|Font.BOLD));
			centerAtMenuItems.add(menuItem);
			menuItem = new JMenuItem(new LoadMosaicAction(f));
			if (selected.contains(f))
				menuItem.setFont(menuItem.getFont().deriveFont(Font.ITALIC|Font.BOLD));
			loadMenuItems.add(menuItem);
		}
		
		List<Component> agg = new ArrayList<Component>();
		agg.addAll(centerAtMenuItems);
		agg.add(new JSeparator(JSeparator.HORIZONTAL));
		agg.addAll(loadMenuItems);
		
		return agg.toArray(new Component[0]);
	}
	
	private Feature findClosest(Collection<Feature> underMouse, Point2D worldPt){
		Feature closest = null;
		double  minDistance = Double.MAX_VALUE;
		
		for(Feature f: underMouse){
			FPath wfpath = f.getPath().getWorld();
			Point2D[] vertices = wfpath.getVertices();
			
			double dist = Double.MAX_VALUE;
			Line2D.Double line = new Line2D.Double();
			for(int i=1; i<vertices.length; i++){
				line.setLine(vertices[i-1], vertices[i]);
				Util.normalize360(line);
				dist = Math.min(dist, line.ptSegDist(worldPt));
			}
			
			if (dist < minDistance){
				closest = f;
				minDistance = dist;
			}
		}
		
		return closest;
	}
	
	public SerializedParameters getInitialLayerData(){
		return new InitialParams();
	}
	
	//
	// Implementation of MouseListener interface
	//
	
	public void mouseClicked(MouseEvent e) {
		if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1){
			Point2D worldPt = getProj().screen.toWorld(e.getPoint());
			Set<Feature> underMouse = getFeaturesUnder(worldPt);
			
			List selected = FeatureUtil.getSelectedFeatures(fc);
			if (!selected.isEmpty()){
				FeatureUtil.deselectAllFeatures(fc);
			}
			
			if (!underMouse.isEmpty()){
				fc.setAttributes(findClosest(underMouse, worldPt),
						Collections.singletonMap(Field.FIELD_SELECTED, Boolean.TRUE));
			}
		}
	}

	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}

	
	//
	// Helper classes
	//

	public class CenterAtAction extends AbstractAction {
		private final Feature f;
		
		public CenterAtAction(Feature f){
			super("Center at "+f.getAttribute(Field.FIELD_LABEL));
			this.f = f;
		}
		
		public Feature getFeature(){
			return f;
		}

		public void actionPerformed(ActionEvent e) {
			Point2D centerAt = f.getPath().getWorld().getCenter(); 
			centerAtPoint(centerAt);
		}
	}
	
	public class LoadMosaicAction extends AbstractAction {
		private final Feature f;
		
		public LoadMosaicAction(Feature f){
			super("Load "+f.getAttribute(Field.FIELD_LABEL));
			this.f = f;
		}
		
		public Feature getFeature(){
			return f;
		}

		public void actionPerformed(ActionEvent e) {
			MapLViewFactory mapLViewFactory = (MapLViewFactory)LViewFactory.getFactoryObject(MapLViewFactory.class.getName());
			mapLViewFactory.createLayer(Main.getLManager(), (MapSource)f.getAttribute(FeatureProviderWMS.FIELD_MAP_SOURCE), null);
		}
	}
	

	/**
	 * Initial layer creation parameters that get serialized onto the disk
	 * and layer recreated when JMARS is started using a ".jmars" file.
	 */
	static class InitialParams implements SerializedParameters, Serializable {
		private static final long serialVersionUID = -1L;
	}

}
