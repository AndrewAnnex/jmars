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


package edu.asu.jmars.layer.shape2;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import org.jdesktop.swingx.combobox.ListComboBoxModel;

import edu.asu.jmars.layer.util.features.CalculatedField;
import edu.asu.jmars.layer.util.features.Feature;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.FieldArea;
import edu.asu.jmars.layer.util.features.FieldBearing;
import edu.asu.jmars.layer.util.features.FieldFactory;
import edu.asu.jmars.layer.util.features.FieldLat;
import edu.asu.jmars.layer.util.features.FieldLength;
import edu.asu.jmars.layer.util.features.FieldLon;
import edu.asu.jmars.layer.util.features.FieldMap;
import edu.asu.jmars.util.LineType;
import edu.asu.jmars.util.Util;

public class ColumnEditor {
	private final ShapeLayer shapeLayer;
	
	public ColumnEditor(ShapeLayer shapeLayer) {
		this.shapeLayer = shapeLayer;
	}
	
	/** shows a modal dialog for editing the columns in a selected shape layer */
	public void showColumnEditor(final Frame parent, final FeatureCollection fc) {
		// this set should contain only one field for each 'type', except for calculated fields
		final List<FieldFactory<?>> types = new ArrayList<FieldFactory<?>>();
		types.add(new BasicField.Factory("Integer Number", Integer.class));
		types.add(new BasicField.Factory("Real Number", Double.class));
		types.add(new BasicField.Factory("String", String.class));
		types.add(new BasicField.Factory("Color", Color.class));
		types.add(new BasicField.Factory("Line Type", LineType.class));
		types.add(new BasicField.Factory("Boolean", Boolean.class));
		types.add(new FieldLon.Factory("Center Longitude"));
		types.add(new FieldLat.Factory("Center Latitude"));
		types.add(new FieldArea.Factory("Enclosed Area"));
		types.add(new FieldLength.Factory("Perimeter"));
		types.add(new FieldBearing.Factory("Line Direction"));
		types.add(new FieldMap.Factory("Map Sampling", fc));
		
		final Map<Field,CalculatedField> calculatedFields = new HashMap<Field,CalculatedField>(shapeLayer.calcFieldMap.get(fc).getCalculatedFields());
		
		Set<Field> oldFields = new LinkedHashSet<Field>(fc.getSchema());
		oldFields.remove(Field.FIELD_PATH);
		
		final DefaultListModel model = new DefaultListModel();
		for (Field f: oldFields) {
			model.addElement(new FieldWrapper(f));
		}
		final JList fieldList = new JList(model);
		fieldList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		
		final JComboBox valueCB = new JComboBox();
		valueCB.setEnabled(false);
		
		final Set<Field> toUpdate = new LinkedHashSet<Field>();
		final JCheckBox replaceCB = new JCheckBox("Update All Rows");
		replaceCB.setToolTipText("Updates all rows with the value set above; this can take some time for calculated fields");
		replaceCB.setEnabled(false);
		replaceCB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Field field = ((FieldWrapper)fieldList.getSelectedValue()).f;
				if (replaceCB.isSelected()) {
					toUpdate.add(field);
				} else {
					toUpdate.remove(field);
				}
			}
		});
		
		final JButton delPB = new JButton("Delete Column");
		delPB.setEnabled(false);
		delPB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int[] rows = fieldList.getSelectedIndices();
				for (int i = rows.length-1; i >= 0; i --) {
					Field field = ((FieldWrapper)model.remove(rows[i])).f;
					calculatedFields.remove(field);
					toUpdate.remove(field);
				}
			}
		});
		
		final JScrollPane calcEditorPane = new JScrollPane();
		valueCB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Object item = valueCB.getSelectedItem();
				calcEditorPane.setViewportView(null);
				if (item instanceof FieldFactory) {
					FieldFactory<?> fac = (FieldFactory<?>)item;
					Field target = ((FieldWrapper)fieldList.getSelectedValue()).f;
					Field source = calculatedFields.get(target);
					if (source == null || fac.getFieldType() != source.getClass()) {
						source = fac.createField(fc, target);
						if (source instanceof CalculatedField) {
							calculatedFields.put(target, (CalculatedField)source);
						}
					}
					JPanel editor = fac.createEditor(source);
					if (editor != null) {
						Box v = Box.createVerticalBox();
						v.add(editor);
						v.add(Box.createGlue());
						Box h = Box.createHorizontalBox();
						h.add(v);
						h.add(Box.createGlue());
						calcEditorPane.setViewportView(h);
						calcEditorPane.invalidate();
						calcEditorPane.validate();
					}
				}
			}
		});
		
		fieldList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				int selCount = fieldList.getSelectedIndices().length;
				delPB.setEnabled(selCount > 0);
				valueCB.setEnabled(false);
				calcEditorPane.setEnabled(false);
				replaceCB.setEnabled(false);
				if (selCount == 1) {
					Field field = ((FieldWrapper)fieldList.getSelectedValue()).f;
					valueCB.setEnabled(field.editable);
					calcEditorPane.setEnabled(field.editable);
					replaceCB.setEnabled(field.editable);
					replaceCB.setSelected(toUpdate.contains(field));
					List<FieldFactory<?>> calcFields = new ArrayList<FieldFactory<?>>();
//					ReplaceField.Factory replaceAll = new ReplaceField.Factory(
//						featureTable.getDefaultEditorsByColumnClass(),
//						featureTable.getDefaultRenderersByColumnClass());
//					calcFields.add(replaceAll);
//					FieldFactory<?> selected = replaceAll;
					FieldFactory<?> selected = null;
					for (FieldFactory<?> fac: types) {
						if (field.type.isAssignableFrom(fac.getDataType())) {
							calcFields.add(fac);
							if (!(fac instanceof BasicField.Factory)) {
								if (fac.getFieldType().isInstance(calculatedFields.get(field))) {
									selected = fac;
								}
							} else if (selected == null) {
								selected = fac;
							}
						}
					}
					valueCB.setModel(new ListComboBoxModel<FieldFactory<?>>(calcFields));
					valueCB.setSelectedItem(selected);
				} else {
					valueCB.setModel(new ListComboBoxModel<Object>(Arrays.asList()));
					valueCB.setSelectedItem(-1);
					calcEditorPane.setViewportView(null);
				}
			}
		});
		
		final JTextField nameText = new JTextField();
		final JComboBox typeCombo = new JComboBox(new ListComboBoxModel<FieldFactory<?>>(types));
		typeCombo.setSelectedItem(null);
		
		final JButton addPB = new JButton("Add Column");
		addPB.setEnabled(false);
		
		class EnableAdd {
			public void check() {
				addPB.setEnabled(nameText.getText().length() > 0 &&
					typeCombo.getSelectedItem() != null);
			}
		}
		
		final EnableAdd addEnabler = new EnableAdd();
		
		nameText.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				addEnabler.check();
			}
			public void insertUpdate(DocumentEvent e) {
				addEnabler.check();
			}
			public void removeUpdate(DocumentEvent e) {
				addEnabler.check();
			}
		});
		typeCombo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				addEnabler.check();
			}
		});
		
		addPB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// warn if column is already present, otherwise schedule for addition
				try {
					String name = nameText.getText();
					FieldFactory<?> fac = (FieldFactory<?>)typeCombo.getSelectedItem();
					Field newField = new Field(name, fac.getDataType(), true);
					for (int i = 0; i < model.getSize(); i++) {
						if (newField.equals(((FieldWrapper)model.get(i)).f)) {
							JOptionPane.showMessageDialog(parent, "Column already exists", "Error adding column", JOptionPane.WARNING_MESSAGE);
						}
					}
					FieldWrapper wrapper = new FieldWrapper(newField);
					model.addElement(wrapper);
					Field field = fac.createField(fc, newField);
					if (field instanceof CalculatedField) {
						calculatedFields.put(newField, (CalculatedField)field);
					}
					fieldList.setSelectedValue(wrapper, true);
					nameText.setText("");
					typeCombo.setSelectedIndex(-1);
				} catch (Exception ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(
						parent,
						ex.getMessage(), "Error adding column",
						JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		
		final JDialog dlg = new JDialog(parent, "Edit Columns...", true);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		Util.addEscapeAction(dlg);
		
		final boolean[] ok = {false};
		JButton okPB = new JButton("Okay");
		okPB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ok[0] = true;
				dlg.setVisible(false);
			}
		});
		
		JButton cancelPB = new JButton("Cancel");
		cancelPB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dlg.setVisible(false);
			}
		});
		
		Container content = dlg.getContentPane();
		content.setLayout(new GridBagLayout());
		int p = 4;
		Insets in = new Insets(p,p,p,p);
		
		// top section, for adding new fields
		content.add(new JLabel("Create Field"), new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,p,p));
		content.add(new JLabel("Name"), new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,p,p));
		content.add(nameText, new GridBagConstraints(2,0,1,1,1,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,p,p));
		content.add(new JLabel("Type"), new GridBagConstraints(1,1,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,p,p));
		content.add(typeCombo, new GridBagConstraints(2,1,1,1,1,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,p,p));
		content.add(addPB, new GridBagConstraints(3,0,1,2,0,0,GridBagConstraints.NORTHEAST,GridBagConstraints.HORIZONTAL,in,p,p));
		content.add(new JSeparator(JSeparator.HORIZONTAL), new GridBagConstraints(0,2,4,1,1,0,GridBagConstraints.CENTER,GridBagConstraints.HORIZONTAL,in,p,p));
		
		// left and right section, for showing and removing fields
		content.add(new JScrollPane(fieldList), new GridBagConstraints(0,3,1,11,1,1,GridBagConstraints.NORTHWEST,GridBagConstraints.BOTH,in,p,p));
		content.add(delPB, new GridBagConstraints(3,3,1,3,0,0,GridBagConstraints.NORTHEAST,GridBagConstraints.HORIZONTAL,in,p,p));
		
		// middle section, for showing the selected field
		content.add(new JLabel("Value"), new GridBagConstraints(1,3,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,p,p));
		content.add(valueCB, new GridBagConstraints(2,3,1,1,1,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,p,p));
		content.add(calcEditorPane, new GridBagConstraints(1,4,2,9,1,1,GridBagConstraints.NORTHWEST,GridBagConstraints.BOTH,in,p,p));
		content.add(replaceCB, new GridBagConstraints(1,13,2,1,1,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,p,p));
		
		// exit buttons
		content.add(okPB, new GridBagConstraints(3,12,1,1,0,0,GridBagConstraints.SOUTHEAST,GridBagConstraints.HORIZONTAL,in,p,p));
		content.add(cancelPB, new GridBagConstraints(3,13,1,1,0,0,GridBagConstraints.SOUTHEAST,GridBagConstraints.HORIZONTAL,in,p,p));
		
		dlg.pack();
		dlg.setSize(500, 500);
		dlg.setVisible(true);
		
		if (ok[0]) {
			Set<Field> f1 = oldFields;
			Set<Field> f2 = new LinkedHashSet<Field>();
			for (int i = 0; i < model.getSize(); i++) {
				f2.add(((FieldWrapper)model.get(i)).f);
			}
			
			// add and remove fields to match new model
			Set<Field> toRemove = new LinkedHashSet<Field>(f1);
			toRemove.removeAll(f2);
			
			Set<Field> toAdd = new LinkedHashSet<Field>(f2);
			toAdd.removeAll(f1);
			
			// TODO: move merging of results back into ShapeLayer
			
			if (toAdd.size() > 0 || toRemove.size() > 0 || toUpdate.size() > 0 ||
					!shapeLayer.calcFieldMap.get(fc).getCalculatedFields().equals(calculatedFields)) {
				shapeLayer.getHistory().mark();
				
				// remove deleted fields
				for (Field f: toRemove) {
					fc.removeField(f);
				}
				
				// add newly-added fields
				for (Field f: toAdd) {
					fc.addField(f);
				}
				
				// update calculated fields
				if (!calculatedFields.equals(shapeLayer.calcFieldMap.get(fc).getCalculatedFields())) {
					shapeLayer.calcFieldMap.get(fc).setCalculatedFields(calculatedFields);
				}
				
				// apply user defaults for all fields
				Map<Field,CalculatedField> columns = new LinkedHashMap<Field,CalculatedField>();
				for (Field field: toUpdate) {
					columns.put(field, calculatedFields.get(field));
				}
				if (columns.size() > 0) {
					// TODO: call this off the AWT thread
					shapeLayer.calcFieldMap.get(fc).updateValues((List<Feature>)fc.getFeatures(), columns);
				}
			}
		}
	}
	
	static class FieldWrapper {
		public final Field f;
		public FieldWrapper(Field f) {
			this.f = f;
		}
		public String toString() {
			return f.name + " (" + f.type.getSimpleName() + ")";
		}
	}
	
	// TODO: the editors for this are just not that good yet
	// improve them and use on the multirow set handler as well
	private static class ReplaceField extends CalculatedField {
		private final Field targetField;
		private Object initialValue;
		public ReplaceField(Field targetField) {
			super(targetField.name, targetField.type);
			this.targetField = targetField;
		}
		public Set<Field> getFields() {
			return Collections.emptySet();
		}
		public Object getValue(Feature f) {
			return initialValue;
		}
		public static class Factory extends FieldFactory<ReplaceField> {
			private final Map<Class<?>, TableCellEditor> editors;
			private final Map<Class<?>, TableCellRenderer> renderers;
			public Factory(Map<Class<?>, TableCellEditor> editors,  Map<Class<?>,TableCellRenderer> renderers) {
				super("Constant", Object.class, Object.class);
				this.editors = editors;
				this.renderers = renderers;
			}
			public JPanel createEditor(Field field) {
				final ReplaceField f = (ReplaceField)field;
				JPanel inputPanel = new JPanel(new BorderLayout());
				inputPanel.setBorder(new EmptyBorder(4,4,4,4));
				if (f.type.isAssignableFrom(Color.class)) {
					JColorChooser colorChooser = new JColorChooser();
					inputPanel.add(colorChooser, BorderLayout.NORTH);
					if (f.initialValue instanceof Color) {
						colorChooser.setColor((Color)f.initialValue);
					}
				} else {
					Object[][] cells = {{f.initialValue}};
					Object[] header = {"Value"};
					final JTable table = new JTable(cells, header);
					for (Class<?> type: editors.keySet()) {
						table.setDefaultEditor(type, editors.get(type));
					}
					for (Class<?> type: renderers.keySet()) {
						table.setDefaultRenderer(type, renderers.get(type));
					}
					TableCellEditor editor = table.getDefaultEditor(f.targetField.type);
					inputPanel.add(editor.getTableCellEditorComponent(table, null, false, 0, 0), BorderLayout.NORTH);
				}
				return inputPanel;
			}
			public ReplaceField createField(FeatureCollection fc, Field f) {
				return new ReplaceField(f);
			}
		}
	}
	
	private static class BasicField extends CalculatedField {
		public BasicField(String name, Class<?> type) {
			super(name, type);
		}
		public Set<Field> getFields() {
			return Collections.emptySet();
		}
		public Object getValue(Feature f) {
			return f.getAttribute(this);
		}
		public static class Factory extends FieldFactory<Field> {
			public Factory(String name, Class<?> dataType) {
				super(name, Field.class, dataType);
			}
			public JPanel createEditor(Field f) {
				return null;
			}
			public Field createField(FeatureCollection fc, Field f) {
				return new Field(getName(), getDataType(), true);
			}
		}
	}
}
