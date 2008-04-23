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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.event.AxisChangeListener;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj;
import edu.asu.jmars.layer.DataReceiver;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;
import edu.asu.jmars.util.stable.ColorCellEditor;
import edu.asu.jmars.util.stable.ColorCellRenderer;

public class ChartView extends JPanel implements DataReceiver, MapChannelReceiver, PipelineEventListener {
	private static final long serialVersionUID = 1L;

	private static DebugLog log = DebugLog.instance();
	
	/**
	 * Maximum power of 2 that the zoom can take.
	 */
	private static int maxZoomPwr = 14;
	
	// Attached mapLView
	MapLView mapLView;
	
	int ppd;
	ProjObj proj;
	
	// Channels this view is listening to.
	MapChannel ch;
	
	// Data samples
	Samples samples = null;
	
	// Profile line, as it exists in the MapLView
	Line2D profileLine;
	double profileLineLengthKm;
	
	// Range of linear parameter t (within [0,1]) for which profile line is effectively visible.
	Range effRange = new Range(0.0, 1.0);
	
	// JFreeChart related stuff
	//XYSeriesCollection dataset;
	JFreeChart chart;
	ChartPanel chartPanel;
	boolean sepRanges = true;
	
	JMenuItem saveAsTextMenuItem;
	JFileChooser fileChooser;
	JComboBox ppdComboBox;
	DefaultComboBoxModel ppdComboBoxModel;
	
	// Readout table stuff
	JTable readoutTable;
	ReadoutTableModel roTableModel;
	
	public ChartView(MapLView mapLView){
		this.mapLView = mapLView;
		
		Pipeline[] pipeline = mapLView.getLayer().mapSettingsDialog.buildChartPipeline();
		ppd = 1;
		
		setLayout(new BorderLayout());
		JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		sp.setDividerLocation(0.8);
		sp.setResizeWeight(0.8);
		sp.setLeftComponent(createChartPanel(pipeline));
		sp.setRightComponent(createReadoutPanel(pipeline, chart));
		add(sp, BorderLayout.CENTER);
		
		ch = new MapChannel(null, ppd, Main.PO, new Pipeline[0]);
		ch.addReceiver(this);
		// TODO: Figure out a better way than the following.
		// Setting pipeline this way makes sure that we'll get a pipelineChangedEvent
		// which we need to set the chart properly.
		ch.setPipeline(pipeline);
	}
	
	private JPanel createReadoutPanel(Pipeline[] pipeline, JFreeChart chart){
		readoutTable = new ReadoutTable(roTableModel = new ReadoutTableModel(pipeline, chart));
		JPanel roPanel = new JPanel(new BorderLayout());
		JScrollPane sp = new JScrollPane(readoutTable);
		sp.setPreferredSize(new Dimension(300,150));
		roPanel.add(sp, BorderLayout.CENTER);
		return roPanel;
	}
	
	private JPanel createChartPanel(Pipeline[] pipeline){
		chart = ChartFactory.createXYLineChart(
				"",
				"Distance (Km)",
				"Value",
				new XYSeriesCollection(),
				PlotOrientation.VERTICAL,
				true,
				false,
				false);
		
		chart.getXYPlot().setDomainCrosshairVisible(true);
		//chart.getXYPlot().setDomainCrosshairLockedOnData(true);
		chart.getXYPlot().setDomainCrosshairPaint(Color.blue);
		chart.getXYPlot().setDomainCrosshairStroke(new BasicStroke(1.0f));
		
		chartPanel = new ChartPanel(chart, true);
		configurePlot(pipeline);
		chartPanel.addChartMouseListener(new ChartMouseListener(){
			public void chartMouseClicked(ChartMouseEvent arg0) {}
			public void chartMouseMoved(ChartMouseEvent e) {
				chartMouseMovedEventOccurred(e);
			}
		});
		
		chartPanel.addComponentListener(new ComponentAdapter(){
			public void componentResized(ComponentEvent e) {
				chartResizedEventOccurred(e);
			}
		});
		
		fileChooser = createSaveAsTextFileChooser();
		saveAsTextMenuItem = createSaveAsTextMenuItem();
		
		JPopupMenu popupMenu = chartPanel.getPopupMenu();
		popupMenu.add(new JPopupMenu.Separator());
		popupMenu.add(saveAsTextMenuItem);
		chartPanel.setPopupMenu(popupMenu);
		
		chart.getXYPlot().getDomainAxis().addChangeListener(new AxisChangeListener(){
			public void axisChanged(AxisChangeEvent event) {
				domainAxisChangeEvent(event);
			}
		});
		
		//chartPanel.setHorizontalAxisTrace(true);
		
		JPanel ppdPanel = createPPDPanel();
		
		JPanel p = new JPanel(new BorderLayout());
		p.add(ppdPanel, BorderLayout.NORTH);
		p.add(chartPanel, BorderLayout.CENTER);
		
		return p;
	}
	
	private  void chartResizedEventOccurred(ComponentEvent e){
		if (profileLine != null)
			if (updateMaxPpd())
				ppdComboBox.setSelectedIndex(ppdComboBox.getItemCount()-1);
	}
	
	private boolean updateMaxPpd(){
		return updateMaxPpd(-1);
	}
	
	private boolean updateMaxPpd(int minPpdIndex){
		// Get new angular span in degrees, determine max-ppd and update ppd combo-box.
		Line2D effLine = getEffProfileLineSpan();
		double aSpan = Util.angularAndLinearDistanceW(effLine.getP1(), effLine.getP2(), mapLView.getProj())[0];
		double pixSpan = chartPanel.getScreenDataArea().getWidth();
		int maxPpdIndex = aSpan > 0.0 && pixSpan > 0? (int)Math.ceil(Math.log(pixSpan / aSpan)/Math.log(2.0)): 0;
		if (minPpdIndex >= 0)
			maxPpdIndex = Math.max(maxPpdIndex, minPpdIndex);
		log.println("aSpan:"+aSpan+"  pixSpan:"+pixSpan+"  maxPpdIndex:"+maxPpdIndex);
		return updatePpdComboBox(maxPpdIndex);
	}
	
	private  void domainAxisChangeEvent(AxisChangeEvent event){
		
		if (profileLine != null){
			/*
			 * Outline:
			 * 1. Determine the Km range displayed by the chart.
			 * 2. Determine the span of profile-line in terms of the linear parameter t. 
			 * 3. Calculate the angular distance covered by the span.
			 * 4. Determine the pixel width of the plot area.
			 * 5. Compute the maximum PPD value based on 3 & 4.
			 * 6. Update the PPD combo-box based on maximum PPD computed in 5.
			 *    6.1. If PPD is smaller than max combo-box PPD, reset view to smaller PPD.
			 *    6.2. Add/remove combo-box as required.
			 * 7. If zooming out, check to see if the newly exposed area will be outside
			 *    currently cached range. If so, issue a fetch request.
			 */
			
			// Get the exposed Km range on the x-axis.
			Range kmRange = chart.getXYPlot().getDomainAxis().getRange();
			effRange = new Range(Math.max(0.0, kmRange.getLowerBound() / profileLineLengthKm),
					Math.min(1.0, kmRange.getUpperBound() / profileLineLengthKm));
			
			log.println("axisChanged: "+kmRange+"  t:"+effRange);
			
			// Get new angular span in degrees, determine max-ppd and update ppd combo-box.
			boolean fetch = updateMaxPpd();
			
			Samples samples = getSamples();
			if (samples == null || samples != null && (effRange.getLowerBound() < samples.t0 || effRange.getUpperBound() > samples.t1))
				fetch = true;
			
			if (fetch)
				setViewExtent(getEffProfileLineSpan(), ((Integer)ppdComboBox.getSelectedItem()).intValue());
		}
	}
	
	private  Line2D getProfileLineSpan(Range span){
		if (profileLine == null)
			return null;
		
		Line2D reqLine = new Line2D.Double(
				Util.interpolate(profileLine, span.getLowerBound()),
				Util.interpolate(profileLine, span.getUpperBound()));
		return reqLine;
	}
	
	private  Line2D getEffProfileLineSpan(){
		return getProfileLineSpan(effRange);
	}
	
	private boolean updatePpdComboBox(int maxPpdIndex){
		boolean changed = false;
		int currMaxPpdIndex = ppdComboBoxModel.getSize()-1;
		int currPpdIndex = ppdComboBoxModel.getIndexOf(ppdComboBoxModel.getSelectedItem());
		if (maxPpdIndex > currMaxPpdIndex){
			// Add new zoom levels to the ppd combo box
			log.println("Adding zoom indices ["+currMaxPpdIndex+".."+maxPpdIndex+"]");
			Integer[] newZooms = getZoomValues(new int[]{currMaxPpdIndex+1, maxPpdIndex});
			for(int i=0; i<newZooms.length; i++)
				ppdComboBoxModel.addElement(newZooms[i]);
		}
		else if (maxPpdIndex < currMaxPpdIndex){
			if (maxPpdIndex < currPpdIndex){
				// Set new ppd value from the current maxPpdIndex
				log.println("Setting current ppd due to change in max-ppd index to "+maxPpdIndex);
				ppdComboBoxModel.setSelectedItem(ppdComboBoxModel.getElementAt(maxPpdIndex));
				changed = true;
			}
			
			// Remove zoom levels from the ppd combo box
			log.println("Removing ppd indices ["+(maxPpdIndex+1)+".."+currMaxPpdIndex+"]");
			while((ppdComboBoxModel.getSize()-1) > maxPpdIndex)
				ppdComboBoxModel.removeElementAt(ppdComboBoxModel.getSize()-1);
		}
		
		return changed;
	}
	
	private Integer[] getZoomValues(int[] zoomPwrRange){
		if (zoomPwrRange[0] < 0 || zoomPwrRange[1] < 0 || zoomPwrRange[1] < zoomPwrRange[0])
			throw new IllegalArgumentException("Illegal zoom power range ["+zoomPwrRange[0]+".."+zoomPwrRange[1]+"]");
		
		Integer[] zooms = new Integer[zoomPwrRange[1]-zoomPwrRange[0]+1]; 
		for(int i=0; i<zooms.length; i++)
			zooms[i] = new Integer(1<<(zoomPwrRange[0]+i));
		
		return zooms;
	}
	
	private JPanel createPPDPanel(){
		JPanel ppdPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
		
		ppdPanel.add(new JLabel("Chart PPD:"));
		
		ppdComboBoxModel = new DefaultComboBoxModel(getZoomValues(new int[]{0, maxZoomPwr}));
		ppdComboBox = new JComboBox(ppdComboBoxModel);
		ppdComboBox.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				ppdComboBoxActionPerformed(e);
			}
		});
		ppdPanel.add(ppdComboBox);
		
		return ppdPanel;
	}
	
	private  void ppdComboBoxActionPerformed(ActionEvent e){
		try {
			int newppd = ((Integer)ppdComboBox.getSelectedItem()).intValue();
			
			/*
			 * If the user has selected a new ppd value, submit a fetch request
			 * for the visible span of profile line.
			 */
			if (newppd != ppd)
				setViewExtent(getEffProfileLineSpan(), newppd);
		}
		catch(NumberFormatException ex){
			log.aprintln("Exception while getting new zoom level: "+ex);
		}
	}
	
	private JFileChooser createSaveAsTextFileChooser(){
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setFileFilter(new FileFilter(){
			public boolean accept(File f) {
				String fileName = f.getName();
				int indexOfDot = fileName.lastIndexOf('.');
				if (indexOfDot > -1 && fileName.substring(indexOfDot).equalsIgnoreCase(".txt"))
					return true;
				return false;
			}

			public String getDescription() {
				return "Text Files";
			}
		});
		
		return fileChooser;
	}
	
	private JMenuItem createSaveAsTextMenuItem(){
		JMenuItem saveAsTextMenuItem = new JMenuItem("Save as CSV");
		saveAsTextMenuItem.setEnabled(false);
		saveAsTextMenuItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				saveAsTextActionPerformed(e);
			}
		});
		return saveAsTextMenuItem;
	}

	private void saveAsTextActionPerformed(ActionEvent e){
		int rc = fileChooser.showSaveDialog(this);
		if (rc != JFileChooser.APPROVE_OPTION || fileChooser.getSelectedFile() == null)
			return;

		try {
			saveAsText(fileChooser.getSelectedFile());
		}
		catch(Exception ex){
			JOptionPane.showMessageDialog(this, "Unable to save file: "+ex.getMessage(), "Error!", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	private  Samples getSamples(){
		return samples;
	}
	
	private void chartMouseMovedEventOccurred(ChartMouseEvent e){
		Point2D pt = chartPanel.translateScreenToJava2D(e.getTrigger().getPoint());
		double km = chart.getXYPlot().getDomainAxis().java2DToValue(
				pt.getX(),
				chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea(),
				chart.getXYPlot().getDomainAxisEdge());

		Point2D cuePoint = null;
		double[] sampleData = null;
		Samples samples = getSamples();
		if (samples != null){
			cuePoint = samples.getPointAtDist(km);
			sampleData = samples.getSampleData(km);
		}
		
		setCrosshair(km);
		updateReadouts(sampleData);
		
		// Forward the event to mapView so that it can show a cueing line as well.
		mapLView.cueChanged(cuePoint);
	}
	
	/**
	 * Receive cueChanged events from mapLView.
	 * @param worldCuePoint The new point within the profileLine segment boundaries
	 *        where the cue is to be generated.
	 */
	public void cueChanged(Point2D worldCuePoint){
		Samples samples = getSamples();
		double km = Double.NaN;
		double[] sampleData = null;
		if (samples != null && worldCuePoint != null){
			//km = samples.getDistance(worldCuePoint);
			sampleData = samples.getSampleData(worldCuePoint);
		}
		setCrosshair(km);
		updateReadouts(sampleData);
	}
		
	public void setCrosshair(double km){
		chart.getXYPlot().setDomainCrosshairValue(km);
	}
	
	public void updateReadouts(double[] sampleData){
		roTableModel.setSampleData(sampleData);
	}
	
	private void configurePlot(Pipeline[] pipeline){
		XYPlot plot = chart.getXYPlot();
		
		plot.setDataset(new XYSeriesCollection());
		plot.setRangeAxis(0, new NumberAxis("Value"));

		int i;
		for(i=0; pipeline != null && i<pipeline.length; i++){
			
			final String label = pipeline[i].getSource().getTitle();
			
			XYSeries s = new XYSeries(label);
			if (sepRanges){
				XYDataset newDataset = new XYSeriesCollection(s);
				NumberAxis newAxis = new NumberAxis(label);
				
				plot.setDataset(i, newDataset);
				plot.setRangeAxis(i, newAxis);
				plot.mapDatasetToRangeAxis(i, i);
				
				if (plot.getRenderer(i) == null){
					XYItemRenderer r = new DefaultXYItemRenderer();
					r.setShape(new GeneralPath());
					plot.setRenderer(i, r);
				}
			}
			else {
				((XYSeriesCollection)plot.getDataset()).addSeries(s);
			}
		}
		for(int j=plot.getDatasetCount()-1; j>=Math.max(i,1); j--){
			plot.setDataset(j, null);
			plot.setRangeAxis(j, null);
		}
	}
	
	private void resetPlot(Pipeline[] pipeline, Line2D profileLine){
		configurePlot(pipeline);
		chart.getXYPlot().getDomainAxis().setAutoRange(true);
		chart.getXYPlot().configureDomainAxes();
		chart.getXYPlot().configureRangeAxes();
		
		// The effRange has to be set here, since configureDomainAxes() generates an AxisChangedEvent
		// which modifies the effRange. Doing this outside, i.e. in setProfileLine does not work.
		effRange = new Range(0.0, 1.0);
		
		//chart.getXYPlot().getDomainAxis().setDefaultAutoRange(
		//	new Range(0, Util.angularAndLinearDistanceW(profileLine.getP1(), profileLine.getP2(), mapLView.getProj())[1]));
	}
	
	private void populatePlot(Pipeline[] pipeline, Samples samples){
		
		// Build XYSeries for data from each of the mapData channels.
		double[][] data = samples.getSampleData();
		double[] distances = samples.getDistances();
		for(int k=0; k<pipeline.length; k++){
			final String label = pipeline[k].getSource().getTitle();
			
			/*
			 * Create a new series of XY-data, with distances as the X-values and
			 * samples as Y-values.
			 * Add the start and end value of distance (i.e. domain) with null range
			 * value to the series. This enables the chart to zoom in and out properly.
			 * We need this because every time a zoom happens, we re-fetch and re-populate
			 * the data in the zoomed range at the new chart PPD.
			 */
			final XYSeries s = new XYSeries(label);
			s.add(new Double(0), null);
			for(int i=0; i<distances.length; i++)
				s.add(new Double(distances[i]), data[i] == null || data[i].length <= k? null: new Double(data[i][k]));
			s.add(new Double(samples.lsegLengthKm), null);

			final int axisNumber = k;
			if (sepRanges){
				XYDataset newDataset = new XYSeriesCollection(s);
				chart.getXYPlot().setDataset(axisNumber, newDataset);
			}
			else {
				if (axisNumber == 0)
					chart.getXYPlot().setDataset(new XYSeriesCollection());

				((XYSeriesCollection)chart.getXYPlot().getDataset()).addSeries(s);
			}
		}
	}
	
	/**
	 * Set the new profile line, clear the current plot and issue a
	 * request to get data corresponding to the new profile line.
	 * @param newProfileLine Profile line in world coordinates.
	 *        It can be null.
	 */
	public  void setProfileLine(Line2D newProfileLine, int newppd){
		// Set new profile line
		profileLine = newProfileLine;
		if (profileLine != null)
			profileLineLengthKm = Util.angularAndLinearDistanceW(profileLine.getP1(), profileLine.getP2(), mapLView.getProj())[1];
		else
			profileLineLengthKm = 0.0;

		samples = null;
		
		// Reset chart ranges and data.
		resetPlot(ch.getPipeline(), profileLine);

		if (profileLine != null){
			int idx = (int)(Math.log(newppd)/Math.log(2.0));
			updateMaxPpd(idx);
			ppdComboBoxModel.setSelectedItem(ppdComboBoxModel.getElementAt(idx));
		}
		
		setViewExtent(profileLine, newppd);
	}
	
	/**
	 * Sets the view extent to the specified extent. The specified extent is
	 * converted into effective extent by narrowing it to the profile line's extent.
	 * Appropriate diagonal from this extent is the output of the profile line.
	 */
	private  void setViewExtent(Line2D newViewExtent, int newppd){
		// disable data save menu options in the chart panel
		saveAsTextMenuItem.setEnabled(false);
		
		roTableModel.fireTableDataChanged();
		
		ppd = newppd;
		proj = Main.PO;
		if (newViewExtent != null && ch.getPipeline().length > 0) {
			mapLView.getLayer().monitoredSetStatus(this, Util.darkRed);
			
			Rectangle2D requestedExtent = expandByHalfAPixelEachSide(newViewExtent.getBounds2D(), ppd);
			log.println("Requesting viewExtent:"+requestedExtent+" at "+ppd+" ppd.");
			ch.setMapWindow(requestedExtent, ppd, proj);
		}
	}
	
	private Rectangle2D expandByHalfAPixelEachSide(Rectangle2D in, int ppd){
		Rectangle2D.Double out = new Rectangle2D.Double();
		out.setFrame(in);
		
		double dpp = 1.0/ppd;
		double hdpp = 0.5 * dpp;
		out.setFrame(out.getX() - hdpp, out.getY() - hdpp, out.getWidth() + dpp, out.getHeight() + dpp);
		
		return out;
	}
	
	/**
	 * Receives data from the MapChannels subscribed to by this chart.
	 * @param mapData Data from one of the channels.
	 */
	public  void mapChanged(MapData mapData){
		if (mapData == null || profileLine == null){
			log.println("Either mapData was null or the profileLine was null.");
			return;
		}
		
		if (mapData.getImage() == null){
			// Set the status LED appropriately and return.
			log.println("mapData.getImage() was null, setting status only.");
			updateFinishedStatus(mapData);
			return;
		}
		
		if (!mapData.isFinished())
			return;
		
		// Sample profile line data from the returned raster
		samples = new Samples(mapData, profileLine, effRange.getLowerBound(), effRange.getUpperBound(), mapData.getRequest().getPPD());
		
		Pipeline[] pipeline = ch.getPipeline();
		if (pipeline != null && samples.getNumBands() != pipeline.length){
			log.println("Pipeline and data bands mismatch ("+pipeline.length+" vs "+samples.getNumBands()+").");
			samples = null;
			updateFinishedStatus(mapData);
			return;
		}
		
		populatePlot(pipeline, samples);
		
		// Set the status LED appropriately.
		updateFinishedStatus(mapData);
		
		// Enable chart panel's save buttons
		saveAsTextMenuItem.setEnabled(true);
	}
	
	/**
	 * Updates the status-LED based on the finished bit in mapData.
	 * @param mapData Non-null mapData object.
	 */
	private void updateFinishedStatus(MapData mapData){
		Color status = mapData.isFinished()? Util.darkGreen: Util.darkRed;
		((MapLayer)mapLView.getLayer()).monitoredSetStatus(this, status);
	}

	/** Does nothing */
	public void receiveData(Object data) {}
	
	/** Updates chart's pipeline */
	public  void pipelineEventOccurred(PipelineEvent e) {
		ch.setPipeline(e.source.buildChartPipeline());
		configurePlot(ch.getPipeline());
		roTableModel.setPipeline(ch.getPipeline());
		setViewExtent(getEffProfileLineSpan(), ppd);
	}
	
	/** Does nothing */
	public void userInitiatedStageChangedEventOccurred(StageChangedEvent e){
	}
	
	public  void saveAsText(File outputFile) throws FileNotFoundException {
		log.println("Save as text requested to file "+outputFile.getName());
		String delim = ",";

		Pipeline[] pipeline = ch.getPipeline();
		NumberFormat nf = NumberFormat.getInstance();
		nf.setGroupingUsed(false);
		nf.setMinimumFractionDigits(0);
		nf.setMinimumIntegerDigits(1);
		
		// Output header
		PrintStream ps = new PrintStream(new FileOutputStream(outputFile));
		ps.print("Distance (Km)"+delim+"Longitude"+delim+"Latitude");
		for(int i=0; i<pipeline.length; i++)
			ps.print(delim+pipeline[i].getSource().getTitle());
		ps.println();
		
		// Output data
		double[] distances = samples.getDistances();
		Point2D[] samplePtsW = samples.getSamplePoints();
		double[][] sampleData = samples.getSampleData();
		for(int k=0; k<samples.getNumSamples(); k++){
			ps.print(nf.format(distances[k]));
			Point2D ptS = mapLView.getProj().world.toSpatial(samplePtsW[k]);
			ps.print(delim); ps.print(nf.format(ptS.getX()));
			ps.print(delim); ps.print(nf.format(ptS.getY()));
			for(int i=0; i<pipeline.length; i++)
				ps.print(delim+nf.format(sampleData[k][i]));
			ps.println();
		}
	}
	
	
	/**
	 * A table containing the readout values from the chart.
	 * There is one row per data item in the plot. The table allows
	 * editing of the plot color.
	 */
	class ReadoutTable extends JTable {
		private static final long serialVersionUID = 1L;

		public ReadoutTable(ReadoutTableModel model){
			super(model);
			setDefaultRenderer(Color.class, new ColorCellRenderer());
			setDefaultEditor(Color.class, new ColorCellEditor());
			setDefaultRenderer(Number.class, new NumberRenderer());
		}
	}
	
	class NumberRenderer extends DefaultTableCellRenderer {
		NumberFormat nf = NumberFormat.getNumberInstance();
		
	    public NumberRenderer() { 
	    	super();
	    	nf.setMaximumFractionDigits(8);
	    }

	    public void setValue(Object value) {
	    	/*
	    	 * The data from ReadoutTableModel for which NumberRenderer is used in conjunction with
	    	 * contains NaNs for the MapSources for which we haven't received any data as yet.
	    	 */
	        setText((value == null) ? "" : (Double.isNaN(((Number)value).doubleValue())? "Value Unavailable": nf.format(value)));
	    }  
	}
	
	/**
	 * Table model supporting the readout table.
	 */
	class ReadoutTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		
		public final String[] columns = new String[] {
			"Title",
			"Color",
			"Value"
		};
		
		private Pipeline[] pipeline = null;
		//private Point2D samplePt = null;
		private double[] sampleData = null;
		private JFreeChart chart = null;
		
		public ReadoutTableModel(Pipeline[] pipeline, JFreeChart chart){
			this.pipeline = pipeline;
			this.chart = chart;
		}
		
		public String getColumnName(int columnIndex){
			return columns[columnIndex];
		}
		
		public Class getColumnClass(int columnIndex){
			switch(columnIndex){
				case 0: return String.class;
				case 1: return Color.class;
				case 2: return Number.class;
			}
			
			return Object.class;
		}
		
		public void setPipeline(Pipeline[] newPipeline){
			pipeline = newPipeline;
			fireTableDataChanged();
		}
		
		public int getColumnCount() {
			return columns.length;
		}

		public int getRowCount() {
			return pipeline == null? 0: pipeline.length;
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			if (pipeline == null || pipeline.length == 0)
				return null;

			if (columnIndex == 0)
				return pipeline[rowIndex].getSource().getTitle();

			int datasetIndex = sepRanges? rowIndex: 0;
			int seriesIndex = sepRanges? 0: rowIndex;

			// This should be thread-safe since all updates to the chart occur on the AWT thread.
			XYPlot plot = chart.getXYPlot();
			if (datasetIndex < plot.getDatasetCount() && seriesIndex < plot.getDataset(datasetIndex).getSeriesCount()){
				if (columnIndex == 1){
					Paint p = ((XYLineAndShapeRenderer)plot.getRenderer(datasetIndex)).getSeriesPaint(seriesIndex);
					if (p instanceof Color)
						return (Color)p;
					return null;
				}
				else if (columnIndex == 2){
					if (sampleData != null && sampleData.length > rowIndex)
						return new Double(sampleData[rowIndex]);
				}
			}
			return null;
		}
		
		public boolean isCellEditable(int rowIndex, int colIndex){
			return (colIndex == 1);
		}
		
		public void setValueAt(Object value, int rowIndex, int columnIndex){
			if (columnIndex != 1)
				throw new IllegalArgumentException("Columns other than the color column are uneditable.");
			
			int datasetIndex = sepRanges? rowIndex: 0;
			int seriesIndex = sepRanges? 0: rowIndex;
			
			XYPlot plot = chart.getXYPlot();
			if (datasetIndex < plot.getDatasetCount() && seriesIndex < plot.getDataset(datasetIndex).getSeriesCount()){
				((XYLineAndShapeRenderer)chart.getXYPlot().getRenderer(datasetIndex)).setSeriesPaint(seriesIndex, (Color)value);
			}
		}
		
		public void setSampleData(double[] newSampleData){
			sampleData = newSampleData;
			//fireTableChanged(new TableModelEvent(this, 0, getRowCount(), 2, TableModelEvent.UPDATE));
			fireTableDataChanged();
		}
	}
	
	/**
	 * Storage for sample data as per a profile line segment.
	 * 
	 * @author saadat
	 */
	class Samples {
		final Line2D lseg;             // Line-segment in world coordinates
		final double t0, t1;           // [0,1] means the entire lseg, [0.5,1] means mid of lseg to end
		final double lsegLength;       // Length of line segment in degrees (of world coordinates)
		final double lsegLengthKm;     // Length of line segment in Km
		final int nSamples;            // Number of samples (or pixels)
		final int ppd;                 // Requested pixel-per-degree of data
		final int nBands;              // Number of bands per sample (or per pixel)
		final MapData mapData;         // MapData object used as source of all sample data
		final AffineTransform ext2Pix; // Transform to convert from map extent (or world) coordinates to mapData raster coordinates.
		
		/**
		 * World coordinates of each of the sampled location along the lseg.
		 */
		Point2D[]  pts;
		
		/**
		 * Sampled data as array of doubles for each sampled location.
		 */
		double[][] data;
		
		/**
		 * Linear distance in Km from the start of lseg.
		 */
		double[]   dist;
		
		/**
		 * Constructs a Samples object which holds sample data for the specified
		 * line segment as extracted from the input mapData object. Consecutive
		 * samples are spaced at 1/ppd.
		 * 
		 * @param mapData Source to use for sampling the data.
		 * @param lseg Line segment along which sampling is to be done.
		 * @param ppd Spacing between consecutive samples.
		 */
		public Samples(MapData mapData, Line2D lseg, double t0, double t1, int ppd){
			this.lseg = lseg;
			this.t0 = t0;
			this.t1 = t1;
			this.ppd = ppd;
			this.mapData = mapData;
			lsegLength = lseg.getP1().distance(lseg.getP2());
			lsegLengthKm = Util.angularAndLinearDistanceW(lseg.getP1(), lseg.getP2(), mapLView.getProj())[1];
			nSamples = (int)(ppd * lsegLength * (t1-t0));
			nBands = mapData.getImage() != null? mapData.getImage().getData().getNumBands(): 0;
			ext2Pix = StageUtil.getExtentTransform(
					mapData.getImage().getWidth(),
					mapData.getImage().getHeight(),
					mapData.getRequest().getExtent());
			
			pts = new Point2D[nSamples];
			data = new double[nSamples][];
			dist = new double[nSamples];
			
			sampleData(mapData);
		}
		
		private void sampleData(MapData mapData){
			BufferedImage image = mapData.getImage();
			Raster raster = image.getData();
			Rectangle rasterBounds = raster.getBounds();
			
			Point2D pix = new Point2D.Double();
			
			for(int i=0; i<nSamples; i++){
				double t = t0+((double)i)/nSamples;
				pts[i] = Util.interpolate(lseg, t);
				dist[i] = lsegLengthKm * t;
				// dist[i] = Util.angularAndLinearDistanceW(lseg.getP1(), pts[i], mapLView.getProj())[1];
				if (rasterBounds.contains(ext2Pix.transform(pts[i], pix)))
					raster.getPixel((int)pix.getX(), (int)pix.getY(), data[i] = new double[nBands]);
				else
					data[i] = null;
			}
		}
		
		public Point2D getPointAtDist(double km){
			double t = km/lsegLengthKm;
			Point2D p = Util.interpolate(lseg, t);
			return p;
		}
		
		public int getNumBands(){
			return nBands;
		}
		
		public int getNumSamples(){
			return nSamples;
		}
		
		public double[][] getSampleData(){
			return (double[][])data.clone();
		}
		
		public double[] getSampleData(Point2D worldPt){
			//log.println("getSampleData("+worldPt+")");
			Raster raster = mapData.getImage().getData();
			Point2D pix = null;
			double[] data = null;
			
			if (raster.getBounds().contains(pix = ext2Pix.transform(worldPt, null)))
				raster.getPixel((int)pix.getX(), (int)pix.getY(), data = new double[nBands]);
			
			return data;
		}
		
		public double[] getSampleData(double km){
			//log.println("getSampleData("+km+")");
			int idx = getDistanceIndex(km);
			if (idx >=0 && idx < dist.length)
				return data[idx];
			return null;
		}
		
		public double getDistance(Point2D worldPt){
			if (lseg.relativeCCW(worldPt) == 0){
				double t = Util.uninterploate(lseg, worldPt);
				return t * lsegLengthKm;
			}
			return Double.NaN;
		}
		
		public double[] getDistances(){
			return (double[])dist.clone();
		}
		
		public Point2D[] getSamplePoints(){
			return (Point2D[])pts.clone();
		}
		
		public int getDistanceIndex(double km){
			double t = km/lsegLengthKm - t0;
			int idx = (int)Math.round(t * nSamples);
			return idx;
		}
	}
	
	
}
