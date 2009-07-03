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


package edu.asu.jmars.samples.layer.map2.stages.threshold;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.text.ParseException;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import edu.asu.jmars.layer.map2.StageSettings;
import edu.asu.jmars.layer.map2.StageView;

public class ThresholdStageView implements StageView, PropertyChangeListener {
	ThresholdSettings settings;
	JPanel stagePanel;
	JTextField thresholdValField;
	DecimalFormat nf = new DecimalFormat("###0.########");
	
	public ThresholdStageView(ThresholdSettings settings){
		this.settings = settings;
		stagePanel = buildUI();
		settings.addPropertyChangeListener(this);
	}
	
	public StageSettings getSettings() {
		return settings;
	}

	public JPanel getStagePanel() {
		return stagePanel;
	}

	private JPanel buildUI(){
		thresholdValField = new JTextField(5);
		updateThresholdFieldFromSettings();
		thresholdValField.setFocusable(true);
		thresholdValField.addFocusListener(new FocusAdapter(){
			public void focusLost(FocusEvent e){
				updateSettingsFromThresholdField();
			}
		});
		thresholdValField.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				updateSettingsFromThresholdField();
			}
		});
		
		Box baseStepBox = Box.createHorizontalBox();
		baseStepBox.add(new JLabel("Threshold:", JLabel.RIGHT));
		baseStepBox.add(thresholdValField);
		baseStepBox.add(Box.createHorizontalStrut(5));
		
		JPanel slim = new JPanel(new BorderLayout());
		slim.add(baseStepBox, BorderLayout.NORTH);
		
		return slim;
	}
	
	private void updateThresholdFieldFromSettings(){
		setFieldValue(thresholdValField, settings.getThreshold());
	}
	
	private void updateSettingsFromThresholdField(){
		try {
			settings.setThreshold(getFieldValue(thresholdValField));
		}
		catch(ParseException ex){
			thresholdValField.selectAll();
			thresholdValField.requestFocus();
		}
	}

	private void setFieldValue(JTextField textField, double val){
		textField.setText(nf.format(val));
	}
	
	private double getFieldValue(JTextField textField) throws ParseException {
		String text = textField.getText();
		return nf.parse(text).doubleValue();
	}

	public void propertyChange(final PropertyChangeEvent e) {
		final String prop = e.getPropertyName();
		
		if (prop.equals(ThresholdSettings.propThresholdValue))
			updateThresholdFieldFromSettings();
	}

}
