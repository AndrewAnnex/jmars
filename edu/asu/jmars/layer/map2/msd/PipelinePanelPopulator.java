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


package edu.asu.jmars.layer.map2.msd;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import edu.asu.jmars.layer.map2.PipelinePanel;

class PipelinePanelPopulator implements TreeSelectionListener, PipelineModelListener {
	JPanel procStepsPanel;
	ProcTree procTree;
	PipelinePanel[] procPanels;
	
	public PipelinePanelPopulator(JPanel procStepsPanel, ProcTree procTree){
		this.procStepsPanel = procStepsPanel;
		this.procTree = procTree;
		this.procTree.getSelectionModel().addTreeSelectionListener(this);
		((ProcTreeModel)this.procTree.getModel()).getVisNode().addPipelineModelListener(this);

		init();
	}
	
	private void init(){
		PipelineModel vis = ((ProcTreeModel)procTree.getModel()).getVisNode();
		int sourceCount = vis.getSourceCount();
		procPanels = new PipelinePanel[sourceCount];
		
		updateFromSelection();
	}
	
	private void updateFromSelection(){
		PipelineModel vis = ((ProcTreeModel)procTree.getModel()).getVisNode();
		
		clearProcStepsPanel();
		TreePath path = procTree.getSelectionPath();
		if (path != null && path.getLastPathComponent() instanceof WrappedMapSource && path.getParentPath().getLastPathComponent() == vis){
			WrappedMapSource source = (WrappedMapSource)path.getLastPathComponent();
			int selectedIndex = vis.getSourceIndex(source);
			if (procPanels[selectedIndex] == null && source.getWrappedSource() != null){
				if (vis.getPipelineLeg(selectedIndex) != null)
					procPanels[selectedIndex] = new PipelinePanel(vis.getPipelineLeg(selectedIndex));
			}
			if (procPanels[selectedIndex] != null){
				procStepsPanel.add(
						new JScrollPane(procPanels[selectedIndex]),
						BorderLayout.CENTER);
				validateHack();
			}
		}
	}
	
	private void clearProcStepsPanel(){
		procStepsPanel.removeAll();
		validateHack();
	}
	
	private void validateHack(){
		procStepsPanel.validate();
		procStepsPanel.repaint();
	}
	
	//
	// Implementation of TreeSelectionListener
	//
	public void valueChanged(TreeSelectionEvent e) {
		updateFromSelection();
	}

	//
	// Implementation of PipelineModelListener
	//
	
	public void childrenChanged(PipelineModelEvent e) {
		init();
	}

	public void compChanged(PipelineModelEvent e) {
		init();
	}

	// This event is never generated by the vis node.
	public void childrenAdded(PipelineModelEvent e) {
		throw new UnsupportedOperationException("childrenAdded");
	}

	// This event is never generated by the vis node.
	public void childrenRemoved(PipelineModelEvent e) {
		throw new UnsupportedOperationException("childrenRemoved");
	}

	public void forwardedEventOccurred(PipelineModelEvent e) {
		PipelineLegModelEvent le = e.getWrappedEvent();
		//PipelineLegModel lm = (PipelineLegModel)le.getSource();
		
		switch(le.getEventType()){
			case PipelineLegModelEvent.STAGES_ADDED:
			case PipelineLegModelEvent.STAGES_REMOVED:
			case PipelineLegModelEvent.STAGES_REPLACED:
				init();
				break;
		}
	}
}

