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


package edu.asu.jmars.layer.map2;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;

import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputListener;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj;
import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.PannerGlass;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.layer.WrappedMouseEvent;
import edu.asu.jmars.layer.Layer.LView;
import edu.asu.jmars.layer.map2.msd.PipelineModel;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.Util;

public class MapLView extends LView implements MapChannelReceiver, PipelineEventListener {
	private static final long serialVersionUID = 1L;
	private static DebugLog log = DebugLog.instance();
	private static BufferedImage errorTile = Util.loadImage("resources/checker.png");
	
	// Parent MapLayer of this LView
	private MapLayer mapLayer;
	
	// MapChannel associated with this LView.
	private MapChannel ch;
	// Numeric data MapChannel associated with this LView.
	private MapChannel numCh;
	
	// Line for which the profile is to be plotted.
	private Line2D profileLine;
	
	// Handler for mouse events and holder of the currently worked upon
	// profile line.
	private ProfileLineDrawingListener profileLineMouseListener = null;
	private ProfileLineCueingListener profileLineCueingListener = null;

	private MapData myNumData;
	private Color   myStatus = Util.darkGreen;
	private Color   myNumStatus = Util.darkGreen;
	
	private Area badArea=new Area();
	private long startTime;
	
	/** Constructs the main and panner views */
	public MapLView(MapLayer layer, final boolean mainView) {
		super(layer);
		
		mapLayer = layer;
		if (mainView) {
			layer.focusPanel = (MapFocusPanel)createFocusPanel();
			focusPanel = layer.focusPanel;
			
			profileLineCueingListener = new ProfileLineCueingListener();
			addMouseMotionListener(profileLineCueingListener);
		}
		layer.focusPanel.addPipelineEventListener(this);
		
		// Start with busy-status.
		myStatus = Util.darkRed;
		
		ch = new MapChannel();
		ch.addReceiver(this);
		Pipeline[] initialPipeline = layer.focusPanel.buildLViewPipeline();
		log.println((mainView?"Main":"Child")+": Setting initial pipeline: "+Arrays.asList(initialPipeline));
		ch.setPipeline(initialPipeline);
		if (ch.getPipeline().length == 0)
			myStatus = Util.darkGreen;
		
		if (mainView){
			// Start with busy-status.
			myNumStatus = Util.darkRed;
			
			mapLayer.mapSettingsDialog.addPipelineEventListener(new PipelineEventListener(){
				public void pipelineEventOccurred(PipelineEvent e) {
					myNumData = null;
					numCh.setPipeline(e.source.buildChartPipeline());
					if (numCh.getPipeline().length == 0){
						myNumStatus = Util.darkGreen;
						pushStatus();
					}
					if (Main.getLManager() != null)
						Main.getLManager().updateLabels();
				}

				public void userInitiatedStageChangedEventOccurred(StageChangedEvent e) {}

			});
			numCh = new MapChannel();
			numCh.addReceiver(new MapChannelReceiver(){
				public void mapChanged(MapData newData) {
					if (!isVisible())
						return;
					
					myNumData = newData;
					
					// update status
					myNumStatus = (newData==null || newData.isFinished())? Util.darkGreen: Util.darkRed;
					pushStatus();
				}
			});
			numCh.setPipeline(mapLayer.mapSettingsDialog.buildChartPipeline());
			
			if (numCh.getPipeline().length == 0)
				myNumStatus = Util.darkGreen;
		}
		
		// update status.
		pushStatus();
	}
	
	/*
	 * This method generates the standard Java Tooltip, populated with data from the numeric data contained
	 * within this LView
	 */
	public String getToolTipText(MouseEvent event) {
		
		if (viewman2.getActiveLView()!=this) return null;
		
		if (numCh==null || myNumData==null || myNumData.getImage()==null) {
			return null;
		}
		
		// Only show tooltips for the MainView
		if (event.getSource() instanceof PannerGlass) {
			return null;
		}
		
		Point2D worldPoint = getProj().screen.toWorld(event.getPoint());
		Rectangle2D extent = myNumData.getRequest().getExtent();
		
		if (!extent.contains(worldPoint)) {
			return null;
		}
		
		int ppd = myNumData.getRequest().getPPD();
		Rectangle2D sampleExtent = new Rectangle2D.Double(worldPoint.getX(), worldPoint.getY(), 1d/ppd, 1d/ppd);
		double[] samples = myNumData.getRasterForWorld(sampleExtent).getPixels(0, 0, 1, 1, (double[])null);
		
		NumberFormat nf = NumberFormat.getNumberInstance();
		nf.setMaximumFractionDigits(5);
		StringBuffer readouts = new StringBuffer(100);
		
		readouts.append("<html>");
		readouts.append("<table cellspacing=0 cellpadding=1>");
		
		Pipeline numPipes[] = numCh.getPipeline();
		
/*  We want to be able to determine the status of each MapRequest we are plotting, but by the
 *  time we can look at the data, it has all been compiled into one MapData object.
 *  
 *  The data will be initialized to NaN, so we can tell if it has been reset by real data, but we can't
 *  currently tell if it hasn't loaded yet or if we've given up on it.  We should solve this problem
 *  more thoroughly when we have time.
 */
				
		for (int i=0; i<samples.length; i++) {
			String title=numPipes[i].getSource().getTitle();
			readouts.append("<tr><td align=right nowrap><b>");
			readouts.append(title +":");
			readouts.append("</b></td>");
			readouts.append("<td>");
			if (Double.isNaN(samples[i])) {
				readouts.append("Value Unavailable");				
			} else {
				readouts.append(nf.format(samples[i]));
			}
			readouts.append("</td></tr>");
		}
		
		readouts.append("</table>");
		readouts.append("</html>");
		
		return readouts.toString();
	}
	
	/**
	 * Returns the channel attached to this view.
	 */
	public MapChannel getChannel(){
		return ch;
	}
	
	public FocusPanel getFocusPanel() {
		return mapLayer.focusPanel;
	}
	
	public MapLayer getLayer() {
		return mapLayer;
	}
	
	public MapFocusPanel createFocusPanel() {
		MapFocusPanel focusPanel = new MapFocusPanel(this);
		
		// Add mouse listener for profile line updates
		profileLineMouseListener = new ProfileLineDrawingListener();
		addMouseListener(profileLineMouseListener);
		addMouseMotionListener(profileLineMouseListener);
		
		return focusPanel;
	}
	
	/**
	 * Called by this.dup(), which is called by the LViewManager to get a panner
	 * instance
	 */
	protected LView _new() {
		return new MapLView(mapLayer, false);
	}
	
	/**
	 * The original intention of this method was to create a request object that
	 * is handed off to the Layer. The layer responds back by passing responses
	 * to the {@link #receiveData(Object)}. However, we are bypassing that mechanism
	 * updating the channel interface with the new parameters. Note that this method
	 * is called whenever there is a viewport change (i.e. pan/zoom/reproject) and
	 * the view is visible.
	 */
	protected Object createRequest(Rectangle2D where) {
		startTime = System.currentTimeMillis();
		updateChannelDetails();
		
		if (!(ch.getPipeline()==null || ch.getPipeline().length==0))
			myStatus = Util.darkRed;
		
		if (!(numCh == null || numCh.getPipeline() == null || numCh.getPipeline().length == 0))
			myNumStatus = Util.darkRed;
		
		pushStatus();
		
		return null;
	}
	
	/** Does nothing here - not using the Layer.LView requestData()/receiveData() mechanism */
	public void receiveData(Object layerData) {}
	
	/**
	 * Paints the component using the super's paintComponent(Graphics),
	 * followed by the profile-line drawing onto the on-screen graphics
	 * context.
	 */
	public synchronized void paintComponent(Graphics g) {
		// Don't try to draw unless the view is visible
		if (!isVisible() || viewman2 == null)
			return;
		
		// super.paintComponent draws the back buffers onto the layer panel
		super.paintComponent(g);
		
		// then we draw the profile line on top of the layer panel
		Graphics2D g2 = (Graphics2D) g.create();
		g2 = viewman2.wrapWorldGraphics(g2);
		g2.transform(getProj().getWorldToScreen());
		g2.setStroke(new BasicStroke(0));
		
		if (profileLineMouseListener != null)
			profileLineMouseListener.paintProfileLine(g2);

		if (profileLineCueingListener != null)
			profileLineCueingListener.paintCueLine(g2);
		
		if (profileLine != null){
			g2.setColor(Color.red);
			g2.draw(profileLine);
		}
	}
	
	/**
	 * Returns default name of this JMARS-Layer as it is displayed in the
	 * Layer Manager window.
	 */
	public String getName() {
		return mapLayer.getName(ch, numCh);
	}
	
	private synchronized void pushStatus(){
		Color status = Color.gray;
		
		if (myStatus == Util.darkRed || myNumStatus == Util.darkRed)
			status = Util.darkRed;
		else if (myStatus == Util.darkGreen && myNumStatus == Util.darkGreen)
			status = Util.darkGreen;
		
		mapLayer.monitoredSetStatus(this, status);
	}
	
	/**
	 * Handles response received from a MapChannel.
	 * @param newData Data returned by the MapChannel
	 */
	public synchronized void mapChanged(MapData newData) {
		if (! isVisible()) {
			log.println((getChild()!=null?"Main":"Child")+": Received mapData, but layer invisible.");
			// update status
			myStatus = (newData==null ||newData.isFinished())? Util.darkGreen: Util.darkRed;
			pushStatus();
			
			// we have nothing to do when the LView is not selected for viewing
			return;
		}
		
		// clear the screen and get out if we don't have good data
		if (newData == null || newData.getImage() == null){
			log.println("Received null mapData, clearing display.");
			clearOffScreen();
			
			// update status
			myStatus = (newData==null ||newData.isFinished())? Util.darkGreen: Util.darkRed;
			pushStatus();
			
			repaint();
			return;
		}
		
		/*
		// If we received a blank area, don't replace the current on-screen view just as yet.
		if (newData.getValidArea().isEmpty() && !(newData.isFinished() || newData.isCancelled())) {
			return;
		}
		*/
		
		BufferedImage img = newData.getImage();
		
		badArea.subtract(newData.getValidArea());		
		
		try {
			// At this point, we have good data to draw, so let's do it
			clearOffScreen();
			Graphics2D g2 = getOffScreenG2Raw();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
			
			if (newData.isFinished()) {
				badArea.add(new Area(newData.getRequest().getExtent()));
				badArea.subtract(newData.getValidArea());
			}
			
			if (!badArea.isEmpty()) {
				double length = 50d / newData.getRequest().getPPD();
				Paint p = new TexturePaint(errorTile, new Rectangle2D.Double(0,0,length,length));
				g2.setPaint(p);
				g2.fill(badArea);
			}
			
			g2.drawImage(img, Util.image2world(img, newData.getRequest().getExtent()), null);
		} catch (Exception ex) {
			log.println("Error drawing image: " + ex);
		}
		
		// update status
		myStatus = newData.isFinished()? Util.darkGreen: Util.darkRed;
		pushStatus();
		
		if (newData.isFinished()) {
			log.println("Request time (" + (getChild()==null ? "panner" : "main") + "): " + (System.currentTimeMillis() - startTime));
		}
		
		repaint();
	}
	
	/**
	 * Updates channel detail parameters of resolution/ppd, view-extent, and
	 * projection.
	 * 
	 * Also manages back buffers so we get consistent paints between when
	 * something changes, and when updated data begins arriving.
	 */
	private void updateChannelDetails(){
		// get portion of view over the OC-projected world
		Rectangle2D viewExtent = getProj().getWorldWindow();
		double y1 = Math.max(-90, viewExtent.getMinY());
		double y2 = Math.min(90, viewExtent.getMaxY());
		viewExtent.setRect(viewExtent.getMinX(), y1, viewExtent.getWidth(), y2-y1);
		// get projection and magnification level
		ProjObj proj = getPO();
		int ppd = viewman2.getMagnification();
		
		// reset the projection-specific values when the projection changes
		if (ch.getProjection() != proj) {
			badArea.reset();
			if (getChild() != null) {
				setProfileLine(null);
			}
			
			// Reset all MapSources' nudging offsets
			if (MapServerFactory.getMapServers() != null) {
				for(MapServer mapServer: MapServerFactory.getMapServers()) {
					for(MapSource mapSource: mapServer.getMapSources()){
						mapSource.setOffset(new Point2D.Double(0,0));
					}
				}
			}
		}
		
		if (viewExtent.isEmpty()) {
			// If the current view window doesn't intersect the world, then don't do anything
			log.println("channel not updated: view not touching world");
		} else {
			// otherwise update the channel
			log.println("channel details updated: extent:"+MapData.rect(viewExtent)+" ppd:"+ppd+" proj:"+proj.getProjectionCenter());
			
			ch.setMapWindow(viewExtent, ppd, proj);
			if (numCh != null)
				numCh.setMapWindow(viewExtent, ppd, proj);
		}
	}
	
	/**
	 * Sets line for which profile is to be extracted.
	 * @param newProfileLine Set the new line to be profiled to this line.
	 *        A null value may be passed as this argument to clear the profile line.
	 */
	private void setProfileLine(Line2D newProfileLine){
		log.println("update profile line: "+newProfileLine);
		profileLine = newProfileLine;
		if (focusPanel != null && (((MapFocusPanel)focusPanel).getChartView()) != null){
			ChartView chartView = ((MapFocusPanel)focusPanel).getChartView();
			chartView.setProfileLine(profileLine, profileLine == null? 1: getProj().getPPD());
		}
	}
	
	/**
	 * Receive cueChanged events from chartView.
	 * @param worldCuePoint The new point within the profileLine segment boundaries
	 *        where the cue is to be generated.
	 */
	public void cueChanged(Point2D worldCuePoint){
		profileLineCueingListener.setCuePoint(worldCuePoint);
	}
	
	public synchronized void pipelineEventOccurred(PipelineEvent e) {
		clearOffScreen();
		if (getChild() != null)
			setProfileLine(profileLine);
		
		Pipeline p[] = e.source.buildLViewPipeline();
		log.println((getChild()!=null?"Main":"Child")+": Setting new pipeline: "+ Arrays.asList(p));
		ch.setPipeline(p);
		if (p==null || p.length==0) {
			myStatus = Util.darkGreen;
			pushStatus();
			
			// We've already cleared offScreen, so this has the effect of clearing the view
			repaint();
		}
		if (Main.getLManager() != null)
			Main.getLManager().updateLabels();
		
		badArea.reset();
	}	

	public void userInitiatedStageChangedEventOccurred(StageChangedEvent e){
		// Reprocess data through the pipeline to reflect new parameters.
		// Since the pipeline internal stages are shared, necessary changes 
		// are already set on the stage.
		getChannel().reprocess();
	}
	
	/**
	 * BaseGlass proxy wraps the screen coordinates, which we do NOT want, so we use the
	 * real point it remembers IF this event is a wrapped one.
	 */
	public Point2D clampedWorldPoint (Point2D anchor, MouseEvent e) {
		Point mousePoint = e instanceof WrappedMouseEvent ? ((WrappedMouseEvent)e).getRealPoint() : e.getPoint();
		Point2D worldPoint = getProj().screen.toWorld(mousePoint);
		double x = Util.normWorldX(worldPoint.getX());
		double a = Util.normWorldX(anchor.getX());
		if (x - a > 180.0) x -= 360.0;
		if (a - x > 180.0) x += 360.0;
		double y = worldPoint.getY();
		if (y > 90) y = 90;
		if (y < -90) y = -90;
		return new Point2D.Double(x, y);
	}
	
	public SerializedParameters getInitialLayerData(){
		return new InitialParams(mapLayer.focusPanel.getLViewPipelineModel(), mapLayer.mapSettingsDialog.getChartPipelineModel());
	}
	
	static class InitialParams implements SerializedParameters {
		private static final long serialVersionUID = -6327986391829454142L;
		
		PipelineModel lviewPPM;
		PipelineModel chartPPM;
		
		public InitialParams(PipelineModel lviewPPM, PipelineModel chartPPM){
			this.lviewPPM = lviewPPM;
			this.chartPPM = chartPPM;
		}
	}
	
	/**
	 * This listener listens to and generates cueing events. Its
	 * functionality is completely isolated from the ProfileLineMouseListener.
	 * It however, depends upon the currently set profile line on the MapLView.
	 */
	class ProfileLineCueingListener extends MouseMotionAdapter {
		private int cueLineLengthPixels = 4;
		GeneralPath baseCueShape;
		Shape cueShape = null;
		
		public ProfileLineCueingListener(){
			super();
			
			GeneralPath gp = new GeneralPath();
			gp.moveTo(0, -cueLineLengthPixels/2);
			gp.lineTo(0, cueLineLengthPixels/2);
			baseCueShape = gp;
		}
								
		public void setCuePoint(Point2D worldCuePoint){
			Shape oldCueShape = cueShape;
			
			if (worldCuePoint == null)
				cueShape = null;
			else
				cueShape = computeCueLine(worldCuePoint);
			
			if (oldCueShape != cueShape)
				repaint();			
		}
		
		/**
		 * Generate cueing line for the specified mouse coordinates
		 * specified in world coordinates. A profileLine must be set
		 * in the MapLView for this method to be successful.
		 * @param worldMouse Mouse position in world coordinates.
		 * @return A new cueing line or null depending upon whether the
		 *        mouse coordinate "falls within" the profileLine range or not.
		 */
		private Shape computeCueLine(Point2D worldMouse){
			if (profileLine == null)
				return null;
			
			HVector p = new HVector(worldMouse.getX(), worldMouse.getY(), 0);
			HVector p1 = new HVector(profileLine.getX1(), profileLine.getY1(), 0);
			HVector p2 = new HVector(profileLine.getX2(), profileLine.getY2(), 0);
			double t = HVector.uninterpolate(p1, p2, p);
			
			Shape newCueShape = null;
			if (t >= 0.0 && t <= 1.0){
				Point2D mid = Util.interpolate(profileLine.getP1(), profileLine.getP2(), t);
				// log.println("computeCueLine t:"+t+" pt:"+mid+" dist:"+Util.angularAndLinearDistanceW(profileLine.getP1(), mid, getProj())[1]);
				
				double angle = HVector.X_AXIS.separationPlanar(p2.sub(p1), HVector.Z_AXIS);//+Math.PI/2.0;
				double scale = cueLineLengthPixels * getProj().getPixelWidth();
				
				AffineTransform at = new AffineTransform();
				at.translate(mid.getX(), mid.getY());
				at.rotate(angle);
				//at.scale(scale, 1.0);
				at.scale(scale, scale);
				newCueShape = baseCueShape.createTransformedShape(at);
			}
			
			return newCueShape;
		}
		
		public void paintCueLine(Graphics2D g2){
			if (profileLine != null && cueShape != null){
				g2.setColor(Color.yellow);
				g2.draw(cueShape);
			}
		}		
	}
	
	/**
	 * Mouse listener for drawing profile line. It also holds the current
	 * in-progess profile line and is responsible for drawing it onto the
	 * on-screen buffer on a repaint. While drawing the profile line, the
	 * LViewManager's status bar is updated on every drag (via 
	 * {@link Main#setStatus(String)}) to show the new position, the 
	 * spherical distance and the linear distance traversed by the line.
	 * 
	 * Once the line is built, the LView is notified via its 
	 * {@link MapLView#setProfileLine(Line2D)} method. The profile
	 * line created is either null, if no drag occurred, or an actual line
	 * if a drag really occurred.
	 */
	class ProfileLineDrawingListener implements MouseInputListener {
		Point2D p1 = null, p2 = null;
		Line2D profileLine = null;
		
		public void mouseClicked(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {}
		public void mouseExited(MouseEvent e) {}
		public void mouseMoved(MouseEvent e) {}
		
		public void mousePressed(MouseEvent e) {
			if (SwingUtilities.isLeftMouseButton(e)){
				if (e.getClickCount() == 1)
					p1 = getProj().screen.toWorld(e.getPoint());
				profileLine = null;
				cueChanged(null);
			}
		}
		
		public void mouseReleased(MouseEvent e) {
			if (SwingUtilities.isLeftMouseButton(e)){
				if (profileLine != null) {
					// Set the profile line to this new line the user just created
					Point2D p2 = clampedWorldPoint(p1, e);
					profileLine = new Line2D.Double(p1, p2);
				}
				// If the drag did not occur, clear the previous line, if any
				setProfileLine(profileLine);
				profileLine = null;
				
				MapLView.this.repaint();
			}
		}
		
		public void mouseDragged(MouseEvent e) {
			if (SwingUtilities.isLeftMouseButton(e)){
				Point2D p2 = clampedWorldPoint(p1, e);
				profileLine = new Line2D.Double(p1, p2);
				
				// Update status bar angular and linear distance values
				// TODO This approach is a kludge, which needs fixing.
				double[] distances = Util.angularAndLinearDistanceW(p1, p2, getProj());
				DecimalFormat f = new DecimalFormat("0.00");
				Main.setStatus(Util.formatSpatial(getProj().world.toSpatial(p2)) +
						"  deg = " + f.format(distances[0]) + 
						"  dist = " + f.format(distances[1]) + "km");
				
				// Update the view so that it can display the in-progress profile line
				repaint();
			}
		}
		
		public void paintProfileLine(Graphics2D g2){
			if (profileLine == null)
				return;
			
			g2.setColor(Color.yellow);
			g2.draw(profileLine);
		}
	}
}
