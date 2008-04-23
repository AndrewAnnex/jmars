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

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EventListener;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.event.EventListenerList;

import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.Layer.LView;
import edu.asu.jmars.layer.map2.msd.PipelineLegModelEvent;
import edu.asu.jmars.layer.map2.msd.PipelineModel;
import edu.asu.jmars.layer.map2.msd.PipelineModelEvent;
import edu.asu.jmars.layer.map2.msd.PipelineModelListener;
import edu.asu.jmars.layer.map2.stages.composite.CompositeStage;
import edu.asu.jmars.util.DebugLog;

public class MapFocusPanel extends FocusPanel implements PipelineEventListener, PipelineProducer {
	private static final long serialVersionUID = 1L;

	private static DebugLog log = DebugLog.instance();
	
	final MapLView mapLView;
	
	JButton configButton;
	// Chart view attached to the main view.
	ChartView chartView;
	// Tabbed pane enclosing the chart view and individual configuration panels
	JTabbedPane tabbedPane;
	// PipelineEvent listeners list
	EventListenerList eventListenersList = new EventListenerList();
	// Current LView piplineModel
	private PipelineModel pipelineModel = new PipelineModel(new Pipeline[0]);
	
	
	public MapFocusPanel(LView parent) {
		super(parent);
		mapLView = (MapLView)parent;
		initialize();
		
		pipelineModel.addPipelineModelListener(new PipelineModelListener(){
			public void childrenAdded(PipelineModelEvent e) {}
			public void childrenChanged(PipelineModelEvent e) {}
			public void childrenRemoved(PipelineModelEvent e) {}
			public void compChanged(PipelineModelEvent e) {}

			public void forwardedEventOccurred(PipelineModelEvent e) {
				PipelineLegModelEvent le = e.getWrappedEvent();
				switch(le.getEventType()){
					case PipelineLegModelEvent.STAGES_ADDED:
					case PipelineLegModelEvent.STAGES_REMOVED:
						firePipelineEvent();
						break;
					case PipelineLegModelEvent.STAGE_PARAMS_CHANGED:
						fireStageChangedEvent(new StageChangedEvent((Stage)le.getWrappedEvent().getSource()));
						break;
				}
			}
		});
		
		// Add ourselves to Source Configuration Panel's events so that we know of the
		// pipeline configuration changes sent down from the MapSettingsDialog.
		mapLView.getLayer().mapSettingsDialog.addPipelineEventListener(this);
		mapLView.getLayer().mapSettingsDialog.addPipelineEventListener(chartView);
		// TODO: This is not a good way of doing things.
		//mapLView.getLayer().mapSettingsDialog.firePipelineEvent();
	}
	
	private void initialize(){
		// add configure button and call to show map sources dialog
		configButton = new JButton("Configure");
		configButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				try {
					/*
					 * Push the current pipeline back to the MapSettingsDialog before
					 * showing it to get new map parameters. Note that we are not
					 * pusing the ChartPipeline back. This is because we don't modify
					 * the ChartPipeline locally, so the configuration within the
					 * MapSettingsDialog is current.
					 */ 
					Pipeline[] pipeline = pipelineModel.buildPipeline();
					mapLView.getLayer().mapSettingsDialog.setLViewPipeline(pipeline);
					mapLView.getLayer().mapSettingsDialog.dialog.setVisible(true);
				}
				catch(Exception ex){
					ex.printStackTrace();
				}
			}
		});
		
		// initialize tabs for chart and processing panels
		tabbedPane = new JTabbedPane();
		tabbedPane.setTabPlacement(JTabbedPane.BOTTOM);
		
		// Add tab for the chart view
		chartView = new ChartView(mapLView);
		tabbedPane.add("Chart", chartView);
		
		// Add it all together
		setLayout(new BorderLayout());
		add(tabbedPane, BorderLayout.CENTER);
		add(configButton, BorderLayout.SOUTH);
	}
	
	public ChartView getChartView() {
		return chartView;
	}

	/*
	 * We receive these events MapSettingsDialog in response to an Ok button push.
	 * This means that we have a new pipeline, we should update our pipelineModel
	 * and the dialogs showing the stage panels.
	 * 
	 * This event is cascaded to both the main and panner views via their registered
	 * listeners.
	 * 
	 * (non-Javadoc)
	 * @see edu.asu.jmars.layer.map2.PipelineEventListener#pipelineEventOccurred(edu.asu.jmars.layer.map2.PipelineEvent)
	 */
	public void pipelineEventOccurred(PipelineEvent e) {
		Pipeline[] newPipeline;
		
		try {
			newPipeline = Pipeline.getDeepCopy(e.source.buildLViewPipeline());
		}
		catch(CloneNotSupportedException ex){
			ex.printStackTrace();
			newPipeline = new Pipeline[0];
		}
		
		pipelineModel.setFromPipeline(newPipeline, Pipeline.getCompStage(newPipeline));
		
		int selectedTab = tabbedPane.getSelectedIndex();
		while(tabbedPane.getComponentCount() > 1)
			tabbedPane.removeTabAt(tabbedPane.getComponentCount()-1);
		
		CompositeStage aggStage = pipelineModel.getCompStage();
		for(int i=0; i<pipelineModel.getSourceCount(); i++){
			PipelinePanel pp = new PipelinePanel(pipelineModel.getPipelineLeg(i));
			tabbedPane.addTab(aggStage.getInputName(i), new JScrollPane(pp));
		}
		tabbedPane.setSelectedIndex(Math.min(selectedTab, tabbedPane.getTabCount()-1));
		
		firePipelineEvent();
	}

	public void addPipelineEventListener(PipelineEventListener l){
		eventListenersList.add(PipelineEventListener.class, l);
	}
	public void removePipelineEventListener(PipelineEventListener l){
		eventListenersList.remove(PipelineEventListener.class, l);
	}
	
	public void firePipelineEvent() {
		PipelineEvent e = new PipelineEvent(this);
		EventListener[] listeners = eventListenersList.getListeners(PipelineEventListener.class);
		for(int i=0; i<listeners.length; i++){
			((PipelineEventListener)listeners[i]).pipelineEventOccurred(e);
		}
	}

	// Unused, this does not produce chart pipeline
	public Pipeline[] buildChartPipeline() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Produces a pipeline suitable for ONE view. Even if the views are using
	 * the same conceptual pipeline, this method must still be called once for
	 * each view.
	 */
	public Pipeline[] buildLViewPipeline() {
		Pipeline[] pipeline = pipelineModel.buildPipeline();
		return Pipeline.replaceAggStage(pipeline, 
				CompStageFactory.instance().getStageInstance(Pipeline.getCompStage(pipeline).getStageName()));
	}
	
	public PipelineModel getLViewPipelineModel(){
		return pipelineModel;
	}
	
	/**
	 * Listen to Stage parameter changes. These are fast changes that should not
	 * cause the whole pipeline to be rebuilt, but should ping the MapProcessor
	 * to reprocess the data.
	 */
	public void userInitiatedStageChangedEventOccurred(StageChangedEvent e) {
		// Forward fast changes from stages to listeners
		fireStageChangedEvent(e);
	}

	/**
	 * Forwards a Stage parameters change (or a fast change) to the ChangeListeners.
	 * These changes are supposed to be fast changes that don't require a pipeline rebuild.
	 */
	public void fireStageChangedEvent(StageChangedEvent e){
		log.println("Firing Stage change event.");
		
		Object[] listeners = eventListenersList.getListeners(PipelineEventListener.class);
		for(int i=0; i<listeners.length; i++){
			((PipelineEventListener)listeners[i]).userInitiatedStageChangedEventOccurred(e);
		}
	}

	

}
