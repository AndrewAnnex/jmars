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
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import edu.asu.jmars.layer.map2.StageSettings;
import edu.asu.jmars.layer.map2.StageView;
import edu.asu.jmars.util.DebugLog;

public class GrayscaleStageView implements StageView, PropertyChangeListener {
	private static final DebugLog log = DebugLog.instance();
	
	private static final String VAL_UNKNOWN = "unknown";
	
	GrayscaleStageSettings settings;
	JTextField minValField;
	JTextField maxValField;
	JCheckBox autoMinMaxCheckBox;
	JPanel stagePanel;
	DecimalFormat nf = new DecimalFormat("###0.########");
	
	public GrayscaleStageView(GrayscaleStageSettings settings){
		this.settings = settings;
		stagePanel = buildUI();
		settings.addPropertyChangeListener(this);
	}
	
	private void updateMinValFromField(){
		try {
			settings.setMinValue(getFieldValue(minValField));
		}
		catch(ParseException ex){
			log.println(ex.toString());
			minValField.selectAll();
			minValField.requestFocus();
		}
	}
	
	private void updateMaxValFromField(){
		try {
			settings.setMaxValue(getFieldValue(maxValField));
		}
		catch(ParseException ex){
			log.println(ex.toString());
			maxValField.selectAll();
			maxValField.requestFocus();
		}
	}
	
	private void updateAutoMinMaxFromCheckBox(){
		boolean enabled = autoMinMaxCheckBox.isSelected();
		settings.setAutoMinMax(enabled);
	}
	
	private JPanel buildUI(){
		JPanel minMaxPanel = new JPanel();
		minMaxPanel.setLayout(new BoxLayout(minMaxPanel, BoxLayout.X_AXIS));
		
		minValField = new JTextField(6);
		minValField.setFocusable(true);
		updateMinFieldFromSettings();
		minValField.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				updateMinValFromField();
			}
		});
		minValField.addFocusListener(new FocusAdapter(){
			public void focusLost(FocusEvent e) {
				updateMinValFromField();
			}
		});
		
		maxValField = new JTextField(6);
		maxValField.setFocusable(true);
		updateMaxFieldFromSettings();
		maxValField.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				updateMaxValFromField();
			}
		});
		maxValField.addFocusListener(new FocusAdapter(){
			public void focusLost(FocusEvent e) {
				updateMaxValFromField();
			}
		});
		
		autoMinMaxCheckBox = new JCheckBox("Auto");
		autoMinMaxCheckBox.setSelected(settings.getAutoMinMax());
		autoMinMaxCheckBox.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				updateAutoMinMaxFromCheckBox();
			}
		});
		
		minMaxPanel.add(new JLabel("Min:"));
		minMaxPanel.add(minValField);
		minMaxPanel.add(Box.createHorizontalStrut(10));
		minMaxPanel.add(new JLabel("Max:"));
		minMaxPanel.add(maxValField);
		minMaxPanel.add(Box.createHorizontalStrut(10));
		minMaxPanel.add(autoMinMaxCheckBox);
		
		JPanel slim = new JPanel(new BorderLayout());
		slim.add(minMaxPanel, BorderLayout.NORTH);
		return slim;
	}

	public StageSettings getSettings() {
		return settings;
	}

	public JPanel getStagePanel() {
		return stagePanel;
	}

	private void setFieldValue(JTextField textField, double val){
		if (Double.isInfinite(val))
			textField.setText(textField.isEnabled()? "": VAL_UNKNOWN);
		else
			textField.setText(nf.format(val));
	}
	
	private void updateMaxFieldFromSettings(){
		maxValField.setEnabled(!settings.getAutoMinMax());
		setFieldValue(maxValField, settings.getMaxValue());
	}
	
	private void updateMinFieldFromSettings(){
		minValField.setEnabled(!settings.getAutoMinMax());
		setFieldValue(minValField, settings.getMinValue());
	}
	
	private double getFieldValue(JTextField textField) throws ParseException {
		String text = textField.getText();
		if (VAL_UNKNOWN.equals(text))
			return textField == minValField? Double.POSITIVE_INFINITY: Double.NEGATIVE_INFINITY;
		return nf.parse(text).doubleValue();
	}
	
	public void propertyChange(final PropertyChangeEvent e) {
		final String prop = e.getPropertyName();
		
		if (prop.equals(GrayscaleStageSettings.propMin))
			updateMinFieldFromSettings();
		else if (prop.equals(GrayscaleStageSettings.propMax))
			updateMaxFieldFromSettings();
		else if (prop.equals(GrayscaleStageSettings.propAutoMinMax)){
			autoMinMaxCheckBox.setSelected(((Boolean)e.getNewValue()).booleanValue());
			updateMinFieldFromSettings();
			updateMaxFieldFromSettings();
		}
	}
}
