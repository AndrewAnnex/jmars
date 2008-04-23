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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.map2.msd.PipelineLegModel;
import edu.asu.jmars.layer.map2.msd.PipelineLegModelEvent;
import edu.asu.jmars.layer.map2.msd.PipelineLegModelListener;
import edu.asu.jmars.util.DebugLog;

public class PipelinePanel extends JPanel implements PipelineLegModelListener {
	private static final long serialVersionUID = 1L;
	private DebugLog log = DebugLog.instance();
	
	public static final String insertButtonLabel = "+";
	public static final String removeButtonLabel = "-";
	
	JPanel innerPanel;
	PipelineLegModel legModel;
	
	public PipelinePanel(PipelineLegModel legModel){
		super();
		this.legModel = legModel;
		this.legModel.addPipelineLegModelListener(this);
		
		initialize();
	}
	
	public PipelineLegModel getModel(){
		return legModel;
	}
	
	/**
	 * Filters the input list of Stages to returns a list of all those Stages 
	 * that can be inserted at the specified index. Filtering is done by looking at
	 * whether input from one stage can be patched through to the next stage or not.
	 * 
	 * @param possibleStages Potential list of stages to be filtered.
	 * @param index Index at which the Stage is destined for.
	 * @return Filtered, possibly empty list of StageViews that can go at the 
	 *         specified index.
	 */
	private List filterForInsertByStagePosition(List possibleStages, int index){
		if (index < 0)
			return Collections.EMPTY_LIST;
		
		// get input MapAttr from MapSource or previous stage
		MapAttr prevType;
		if (index == 0) {
			prevType = getMapSource().getMapAttr();
		} else {
			prevType = legModel.getStage(index - 1).produces();
		}
		
		// get next stage
		Stage nextStage = legModel.getStage(index);
		int   nextStageInputIndex = index == legModel.getInnerStageCount()? legModel.getAggStageInputNumber(): 0;
		
		List stages = new ArrayList();
		for (Iterator it=possibleStages.iterator(); it.hasNext(); ) {
			Stage stage = (Stage)it.next();
			if (stage.canTake(0, prevType) && nextStage.canTake(nextStageInputIndex, stage.produces())) {
				stages.add(stage);
			}
		}
		return stages;
	}
	
	/**
	 * Determines whether deleting the stage at the specified position would render
	 * the pipeline unusable. Where broken pipeline is a pipeline in which output
	 * from previous stages cannot be processed by the next stage.
	 * @param index Position of the stage being deleted.
	 * @return <code>true</code> if the deletion will not result in a dysfunctional
	 *        pipeline, <code>false</code> otherwise.
	 */
	private boolean deletePossibleOfPipelineStageAt(int index){
		int nInnerStages = legModel.getInnerStageCount();
		
		if (index < 0 || index >= nInnerStages)
			return false;
		
		if (index == 0){
			if ((index+1) < nInnerStages){
				// There is a Stage following the stage to be deleted
				return legModel.getStage(index+1).canTake(0, getMapSource().getMapAttr());
			}
			else {
				// The input is directly connected to the aggregation stage
				return getAggStage().canTake(legModel.getAggStageInputNumber(), getMapSource().getMapAttr());
			}
		}
		else if (index > 0){
			if ((index+1) < nInnerStages){
				// There is a stage following the stage to be deleted
				Stage prev = legModel.getStage(index-1);
				Stage next = legModel.getStage(index+1);
				return next.canTake(0, prev.produces());
			}
			else {
				// The stage feeding into this stage is connected to the aggregation stage
				Stage prev = legModel.getStage(index-1);
				return getAggStage().canTake(legModel.getAggStageInputNumber(), prev.produces());
			}
		}
		
		return false;
	}
	
	public MapSource getMapSource(){
		return legModel.getMapSource();
	}
	
	public Stage getAggStage(){
		return legModel.getStage(legModel.getInnerStageCount());
	}
	
	private void initialize(){
		// Inner panel holds the source panel and stage panels
		innerPanel = new JPanel();
		innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
		innerPanel.add(createSourcePanel());
		
		// Insert the stages that we inherited from the pipeline
		int n = legModel.getInnerStageCount();
		for(int i=0; i<n; i++){
			StagePipelinePanel spp = new StagePipelinePanel(legModel.getStage(i));
			innerPanel.add(spp, i+1); // panel is an additional source panel at the top, hence +1
		}
		
		// Outer panel keeps the items in the inner panel to their minimum height.
		setLayout(new BorderLayout()); //BoxLayout(this, BoxLayout.Y_AXIS));
		add(innerPanel, BorderLayout.NORTH);
		
	}
	
	private JPanel createSourcePanel(){
		final MapSource source = getMapSource();
		
		JLabel srcLbl = new JLabel(source.getTitle());
		JPanel srcLblPanel = new JPanel(new BorderLayout());
		srcLblPanel.add(srcLbl, BorderLayout.NORTH);
		
		JPanel sourcePipelinePanel = new JPanel(new BorderLayout());
		sourcePipelinePanel.setBorder(BorderFactory.createTitledBorder("Source"));
		
		JButton insertButton = new JButton(insertButtonLabel);
		insertButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				dialogInsertStageAt(0);
			}
		});
		
		JPanel buttonInnerPanel = new JPanel(new GridLayout(1,1));
		buttonInnerPanel.add(insertButton);
		JPanel buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(buttonInnerPanel, BorderLayout.NORTH);
		
		// If the source is movable, display a nudge panel, otherwise leave the space empty
		if (source.isMovable()) {
			JPanel nudgeCursorPanel = new JPanel();
			nudgeCursorPanel.setLayout(new GridLayout(3,3));
			
			JButton left = new JButton(new ImageIcon(Main.getResource("resources/pan_w.gif")));
			JButton right = new JButton(new ImageIcon(Main.getResource("resources/pan_e.gif")));
			JButton up = new JButton(new ImageIcon(Main.getResource("resources/pan_n.gif")));
			JButton down = new JButton(new ImageIcon(Main.getResource("resources/pan_s.gif")));			
			final JButton stepToggle = new JButton("1");
			
			left.setFocusable(false);
			right.setFocusable(false);
			up.setFocusable(false);
			down.setFocusable(false);
			stepToggle.setFocusable(false);
			
			// Allow for moving by various pixel increments.  Patterned after the behavior in the Stamp layer
			stepToggle.addActionListener(new ActionListener(){			
				public void actionPerformed(ActionEvent e) {
					int stepSize = 1;
					
					try {
						stepSize=Integer.parseInt(stepToggle.getText());
					} catch (NumberFormatException nfe) {
						log.print("NumberFormatException caught in stepToggle: " + nfe.getMessage());
					}
					
					if (stepSize==1) {
						stepSize=2;
					} else if (stepSize==2) {
						stepSize=5;
					} else if (stepSize==5) {
						stepSize=10;
					} else {
						stepSize=1;
					}
					
					stepToggle.setText(""+stepSize);
				}			
			});
			
			left.addActionListener(new ActionListener() {				
				public void actionPerformed(ActionEvent e) {
					double dx = Double.parseDouble(stepToggle.getText()) / Main.testDriver.mainWindow.getMagnify();
					Point2D p = source.getOffset();
					source.setOffset(new Point2D.Double(p.getX()+dx, p.getY()));
					Main.testDriver.mainWindow.viewChanged();
					Main.testDriver.mainWindow.getChild().viewChanged();
				}
			});
			
			right.addActionListener(new ActionListener() {				
				public void actionPerformed(ActionEvent e) {
					double dx = -Double.parseDouble(stepToggle.getText()) / Main.testDriver.mainWindow.getMagnify();
					Point2D p = source.getOffset();
					source.setOffset(new Point2D.Double(p.getX()+dx, p.getY()));
					Main.testDriver.mainWindow.viewChanged();
					Main.testDriver.mainWindow.getChild().viewChanged();
				}
			});
			
			up.addActionListener(new ActionListener() {				
				public void actionPerformed(ActionEvent e) {
					double dy = -Double.parseDouble(stepToggle.getText()) / Main.testDriver.mainWindow.getMagnify();
					Point2D p = source.getOffset();
					source.setOffset(new Point2D.Double(p.getX(), p.getY()+dy));
					Main.testDriver.mainWindow.viewChanged();
					Main.testDriver.mainWindow.getChild().viewChanged();
				}
			});
			
			down.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					double dy = Double.parseDouble(stepToggle.getText()) / Main.testDriver.mainWindow.getMagnify();
					Point2D p = source.getOffset();
					source.setOffset(new Point2D.Double(p.getX(), p.getY()+dy));
					Main.testDriver.mainWindow.viewChanged();
					Main.testDriver.mainWindow.getChild().viewChanged();
				}
			});
			
			nudgeCursorPanel.add(new JLabel());
			nudgeCursorPanel.add(up);
			nudgeCursorPanel.add(new JLabel());
			nudgeCursorPanel.add(left);
			nudgeCursorPanel.add(stepToggle);
			nudgeCursorPanel.add(right);
			nudgeCursorPanel.add(new JLabel());
			nudgeCursorPanel.add(down);
			nudgeCursorPanel.add(new JLabel());
			
			JButton reset = new JButton("Reset");
			reset.addActionListener(new ActionListener(){			
				public void actionPerformed(ActionEvent e) {
					source.setOffset(new Point2D.Double(0,0));
					Main.testDriver.mainWindow.viewChanged();
					Main.testDriver.mainWindow.getChild().viewChanged();
				}			
			});
			
			JPanel nudgePanel = new JPanel();
			nudgePanel.setLayout(new BoxLayout(nudgePanel, BoxLayout.X_AXIS));
			nudgePanel.setBorder(BorderFactory.createTitledBorder("Nudge Map"));
			nudgePanel.add(nudgeCursorPanel);
			nudgePanel.add(Box.createHorizontalStrut(2));
			nudgePanel.add(reset);
			
			sourcePipelinePanel.add(nudgePanel, BorderLayout.WEST);
		}

		sourcePipelinePanel.add(srcLblPanel, BorderLayout.CENTER);
		sourcePipelinePanel.add(buttonPanel, BorderLayout.EAST);

		return sourcePipelinePanel;
	}

	/**
	 * Inserts the given stage at the specified index in the current pipeline.
	 * @param stage Stage to insert.
	 * @param index Index in the pipeline. Note that first stage is at index 0.
	 *        Also note that there are more panels than pipeline stages because
	 *        the source name takes up one panel.
	 */
	public void insertStage(Stage stage, int index){
		StagePipelinePanel spp = new StagePipelinePanel(stage);
		innerPanel.add(spp, index+1); // panel is an additional source panel at the top, hence +1
	}
	
	public void removeStage(StagePipelinePanel spp){
		innerPanel.remove(spp);
	}
	
	/**
	 * Show dialog to the user to add a pipeline stage at the specified index.
	 * @param stageIndex Pipeline index of the stage at whose location an insert is to be done.
	 */
	public void dialogInsertStageAt(int index){
		List allStages = StageFactory.instance().getAllSingleIOStages();
		Stage[] filteredStages = (Stage[])filterForInsertByStagePosition(allStages, index).toArray(new Stage[0]);
		Stage selected = (Stage)JOptionPane.showInputDialog(this, "Select Stage", "Select Stage",
				JOptionPane.QUESTION_MESSAGE, null, filteredStages, filteredStages.length > 0? filteredStages[0]: null);
		
		if (selected != null){
			Stage stage = StageFactory.getStageInstance(selected.getStageName());
			legModel.insertStage(index, stage);
		}
	}
	
	public void dialogInsertStageAfter(StagePipelinePanel sp){
		int idx = Arrays.asList(innerPanel.getComponents()).indexOf(sp);
		dialogInsertStageAt(idx); // There is an additional Source panel component at the top of innerPanel, hence -1
	}
	
	class StagePipelinePanel extends JPanel {
		private static final long serialVersionUID = 1L;
		
		Stage stage;
		StageView stageView;
		JButton removeButton = new JButton(removeButtonLabel);
		JButton insertButton = new JButton(insertButtonLabel);
		
		public StagePipelinePanel(Stage stage){
			super();
			this.stage = stage;
			this.stageView = this.stage.getSettings().createStageView();
			
			insertButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					dialogInsertStageAfter(StagePipelinePanel.this);
				}
			});
			removeButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					int index = Arrays.asList(innerPanel.getComponents()).indexOf(StagePipelinePanel.this);
					if (deletePossibleOfPipelineStageAt(index-1))
						legModel.removeStage(StagePipelinePanel.this.stage);
					else
						JOptionPane.showMessageDialog(StagePipelinePanel.this,
								"Cannot remove stage \""+getStage().getStageName()+"\".",
								"Error!", JOptionPane.ERROR_MESSAGE);
				}
			});
			
			JPanel buttonInnerPanel = new JPanel(new GridLayout(2,1));
			buttonInnerPanel.add(removeButton);
			buttonInnerPanel.add(insertButton);
			JPanel buttonPanel = new JPanel(new BorderLayout());
			buttonPanel.add(buttonInnerPanel, BorderLayout.NORTH);
			
			setBorder(BorderFactory.createTitledBorder(getStage().getStageName()));
			
			setLayout(new BorderLayout());
			add(getStageView().getStagePanel(), BorderLayout.CENTER);
			add(buttonPanel, BorderLayout.EAST);
		}
		
		public StageView getStageView(){
			return stageView;
		}
		
		public Stage getStage(){
			return stage;
		}
	}
	
	
	//
	// PipelineLegModelListener implementation.
	//

	// Unused.
	public void stageParamsChanged(PipelineLegModelEvent e) {}

	public void stagesAdded(PipelineLegModelEvent e) {
		int[] indices = e.getStageIndices();
		Stage[] stages = e.getStages();
		
		for(int i=0; i<indices.length; i++){
			insertStage(stages[i], indices[i]);
		}
	}

	public void stagesRemoved(PipelineLegModelEvent e) {
		int[] indices = e.getStageIndices();
		for(int i=indices.length-1; i>=0; i--){
			removeStage((StagePipelinePanel)innerPanel.getComponent(indices[i]+1));
		}
	}

	public void stagesReplaced(PipelineLegModelEvent e) {
		while(innerPanel.getComponentCount() > 1){
			removeStage((StagePipelinePanel)innerPanel.getComponent(innerPanel.getComponentCount()-1));
		}
		
		Stage[] stages = e.getStages();
		for(int i=0; i<stages.length; i++){
			insertStage(stages[i], i);
		}
	}

}
