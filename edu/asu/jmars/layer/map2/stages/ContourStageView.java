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


package edu.asu.jmars.layer.map2.stages;

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
import edu.asu.jmars.swing.ColorCombo;

public class ContourStageView implements StageView, PropertyChangeListener {
	ContourStageSettings settings;
	JPanel stagePanel;
	JTextField baseValField, stepValField;
	ColorCombo colorField;
	DecimalFormat nf = new DecimalFormat("###0.########");
	
	public ContourStageView(ContourStageSettings settings){
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
		baseValField = new JTextField(5);
		updateBaseFieldFromSettings();
		baseValField.setFocusable(true);
		baseValField.addFocusListener(new FocusAdapter(){
			public void focusLost(FocusEvent e){
				updateSettingsFromBaseField();
			}
		});
		baseValField.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				updateSettingsFromBaseField();
			}
		});
		
		stepValField = new JTextField(5);
		updateStepFieldFromSettings();
		stepValField.setFocusable(true);
		stepValField.addFocusListener(new FocusAdapter(){
			public void focusLost(FocusEvent e){
				updateSettingsFromStepField();
			}
		});
		stepValField.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				updateSettingsFromStepField();
			}
		});
		
		colorField = new ColorCombo();
		colorField.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				updateSettingsFromColorField();
			}
		});
		updateColorFieldFromSettings();

		Box baseStepBox = Box.createHorizontalBox();
		baseStepBox.add(new JLabel("Base:", JLabel.RIGHT));
		baseStepBox.add(baseValField);
		baseStepBox.add(Box.createHorizontalStrut(5));
		baseStepBox.add(new JLabel("Step:", JLabel.RIGHT));
		baseStepBox.add(stepValField);
		baseStepBox.add(Box.createHorizontalStrut(5));
		baseStepBox.add(new JLabel("Color:", JLabel.RIGHT));
		baseStepBox.add(colorField);
		
		JPanel slim = new JPanel(new BorderLayout());
		slim.add(baseStepBox, BorderLayout.NORTH);
		
		return slim;
	}
	
	private void updateBaseFieldFromSettings(){
		setFieldValue(baseValField, settings.getBase());
	}
	
	private void updateStepFieldFromSettings(){
		setFieldValue(stepValField, settings.getStep());
	}
	
	private void updateColorFieldFromSettings(){
		colorField.setColor(settings.getColor());
	}
	
	private void updateSettingsFromBaseField(){
		try {
			settings.setBase(getFieldValue(baseValField));
		}
		catch(ParseException ex){
			baseValField.selectAll();
			baseValField.requestFocus();
		}
	}

	private void updateSettingsFromStepField(){
		try {
			settings.setStep(getFieldValue(stepValField));
		}
		catch(ParseException ex){
			stepValField.selectAll();
			stepValField.requestFocus();
		}
	}
	
	private void updateSettingsFromColorField(){
		if (!settings.getColor().equals(colorField.getColor()))
			settings.setColor(colorField.getColor());
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
		
		if (prop.equals(ContourStageSettings.propBase))
			updateBaseFieldFromSettings();
		else if (prop.equals(ContourStageSettings.propStep))
			updateStepFieldFromSettings();
		else if (prop.equals(ContourStageSettings.propColor))
			updateColorFieldFromSettings();
	}

}
