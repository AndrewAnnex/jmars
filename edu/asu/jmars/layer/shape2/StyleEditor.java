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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jdesktop.swingx.combobox.ListComboBoxModel;

import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.Style;
import edu.asu.jmars.layer.util.features.StyleFieldSource;
import edu.asu.jmars.layer.util.features.StyleGlobalSource;
import edu.asu.jmars.layer.util.features.StyleSource;
import edu.asu.jmars.layer.util.features.Styles;
import edu.asu.jmars.swing.ColorButton;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.LineType;
import edu.asu.jmars.util.Util;

// TODO: add support for Font and FPath types (geometry style could be path, centroid(path), buffer(path), etc.)

public class StyleEditor {
	private static DebugLog log = DebugLog.instance();
	
	/**
	 * Shows a modal dialog that the user can use to edit style field sources and defaults.
	 * 
	 * A special field source is reserved for 'no field source', aka a global or what used to be considered an override value.
	 * 
	 * A special field source is reserved for creating new style columns within the editor, a huge convenience.
	 * 
	 * @param styles The styles object to modify.
	 * @param schema The feature collection schema to modify.
	 * @return true if the styles were modified, false if nothing was changed.
	 */
	public Set<Style<?>> showStyleEditor(Styles styles, List<Field> schema) {
		// create header of table and separator between header and styles
		JPanel itemPanel = new JPanel(new GridBagLayout());
		itemPanel.setBorder(new EmptyBorder(8,8,8,8));
		int row = 0;
		Insets in = new Insets(4,4,4,4);
		GridBagConstraints gbc = new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,0,0);
		gbc.gridx = 0;
		itemPanel.add(new JLabel("Name"), gbc);
		gbc.gridx = 1;
		itemPanel.add(new JLabel("Use Column"), gbc);
		gbc.gridx = 2;
		itemPanel.add(new JLabel("Default Value"), gbc);
		row ++;
		itemPanel.add(new JSeparator(), new GridBagConstraints(0,row++,3,1,0,1,GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,in,0,0));
		
		// create dialog and button to close it, using 'usedOkay' to see if it accepted or not
		final JDialog d = new JDialog((Frame)null, "Style Settings...", true);
		Util.addEscapeAction(d);
		
		final boolean[] usedOkay = {false};
		
		final JButton okay = new JButton("Save");
		okay.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				d.setVisible(false);
				usedOkay[0] = true;
			}
		});
		
		final JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				d.setVisible(false);
			}
		});
		
		final String globalItem = "<use default>";
		final String createItem = "<create column...>";
		
		// used to determine what the user changed without going digging through JComponents
		final Map<Style<?>,Object> oldFields = new HashMap<Style<?>,Object>();
		final Map<Style<?>,Object> oldDefaults = new HashMap<Style<?>,Object>();
		final Map<Style<?>,Object> newFields = new HashMap<Style<?>,Object>();
		final Map<Style<?>,Object> newDefaults = new HashMap<Style<?>,Object>();
		
		// create a row for each style
		for (final Style<? extends Object> s: styles.getStyles()) {
			final StyleSource<?> source = s.getSource();
			final Object defaultValue = source.getValue(null);
			
			// get the name, field, and default value
			String styleName = s.getName();
			Object styleField;
			Object styleDefault;
			if (source instanceof StyleFieldSource<?>) {
				StyleFieldSource<?> fieldSource = (StyleFieldSource<?>)source;
				styleField = fieldSource.getField();
				styleDefault = fieldSource.getValue(null);
			} else if (source instanceof StyleGlobalSource<?>) {
				StyleGlobalSource<?> orideSource = (StyleGlobalSource<?>)source;
				styleField = globalItem;
				styleDefault = orideSource.getValue(null);
			} else {
				// unrecognized style handler, show it as such
				styleField = null;
				styleDefault = null;
			}
			
			// the field combo is only enabled for recognized StyleSources
			final List<Object> items = new ArrayList<Object>();
			items.add(globalItem);
			if (styleDefault != null) {
				// if we have a default value, we could create a column on the fly with its type
				items.add(createItem);
			}
			
			if (styleDefault != null) {
				Class<?> styleClass = styleDefault.getClass();
				Class<?>[] typeMatches = {Number.class, String.class, Color.class, LineType.class};
				for (Field f: schema) {
					boolean ok = styleDefault.getClass().isAssignableFrom(f.type);
					for (Class<?> match: typeMatches) {
						if (ok) {
							break;
						}
						if (match.isAssignableFrom(styleClass) && match.isAssignableFrom(f.type)) {
							ok = true;
						}
					}
					if (ok) {
						items.add(f);
					}
				}
			}
			
			final JComboBox cbSource = new JComboBox(new ListComboBoxModel<Object>(items));
			if (styleField == null) {
				cbSource.setSelectedIndex(0);
				cbSource.setEnabled(false);
			} else {
				cbSource.setSelectedItem(styleField);
			}
			
			final Class<?> type = styleDefault == null ? null : styleDefault.getClass();
			cbSource.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					Object sel = cbSource.getSelectedItem();
					if (sel == createItem) {
						SwingUtilities.invokeLater(new Runnable() {
							// invoke later to let action handlers finish their work
							public void run() {
								String name = JOptionPane.showInputDialog(cbSource, "Enter new column name:");
								if (name != null) {
									Field f = new Field(name, type);
									items.add(f);
									cbSource.setSelectedItem(f);
								} else {
									cbSource.setSelectedItem(newFields.get(s));
								}
							}
						});
					} else {
						newFields.put(s, cbSource.getSelectedItem());
					}
				}
			});
			
			// the 'default' value is parsed every time a change is made and okay
			// is disabled if the value is invalid
			Component comp;
			if (styleDefault == null) {
				log.println("Editor is ignoring uneditable source " + source.getClass().getName());
				continue;
			} else if (String.class.isInstance(defaultValue)) {
				final JTextField txt = new JTextField(styleDefault.toString());
				txt.getDocument().addDocumentListener(new DocumentListener() {
					public void changedUpdate(DocumentEvent e) {change();}
					public void insertUpdate(DocumentEvent e) {change();}
					public void removeUpdate(DocumentEvent e) {change();}
					private void change() {
						newDefaults.put(s, txt.getText().trim());
					}
				});
				comp = txt;
			} else if (Number.class.isInstance(defaultValue)) {
				final JTextField txt = new JTextField(styleDefault.toString());
				final Color orgBG = txt.getBackground();
				txt.getDocument().addDocumentListener(new DocumentListener() {
					public void changedUpdate(DocumentEvent e) {change();}
					public void insertUpdate(DocumentEvent e) {change();}
					public void removeUpdate(DocumentEvent e) {change();}
					private void change() {
						try {
							if (defaultValue.getClass().isAssignableFrom(Integer.class)) {
								newDefaults.put(s, Integer.parseInt(txt.getText()));
							} else if (defaultValue.getClass().isAssignableFrom(Float.class)) {
								newDefaults.put(s, Float.parseFloat(txt.getText()));
							} else if (defaultValue.getClass().isAssignableFrom(Double.class)) {
								newDefaults.put(s, Double.parseDouble(txt.getText()));
							} else {
								throw new IllegalStateException("Unsupported number type");
							}
							okay.setEnabled(true);
							txt.setBackground(orgBG);
						} catch (Exception ex) {
							okay.setEnabled(false);
							txt.setBackground(Color.pink);
						}
					}
				});
				comp = txt;
			} else if (Boolean.class.isInstance(defaultValue)) {
				final JCheckBox cb = new JCheckBox("Enable", ((Boolean)defaultValue).booleanValue());
				cb.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						newDefaults.put(s, cb.isSelected());
					}
				});
				comp = cb;
			} else if (LineType.class.isInstance(defaultValue)) {
				final Object[] options = {
					LineType.PATTERN_ID_NULL,
					LineType.PATTERN_ID_2_2,
					LineType.PATTERN_ID_6_3,
					LineType.PATTERN_ID_12_3_3_3
				};
				final JComboBox cb = new JComboBox(options);
				cb.setSelectedItem(((LineType)defaultValue).getType());
				cb.setRenderer(new DefaultListCellRenderer() {
					private LineType type;
					public void paintComponent(Graphics g) {
						if (type == null) {
							super.paintComponent(g);
						} else {
							Graphics2D g2 = (Graphics2D)g;
							Dimension d = getSize();
							g2.setBackground(getBackground());
							g2.clearRect(0, 0, d.width, d.height);
							BasicStroke st = new BasicStroke(
								2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
								type.getDashPattern(), 0.0f);
							g2.setStroke(st);
							g2.setColor(getForeground());
							g2.draw(new Line2D.Double(0,d.height/2, d.width, d.height/2));
						}
					}
					public Component getListCellRendererComponent(JList list, Object value, final int index, boolean isSelected, boolean cellHasFocus) {
						type = new LineType((Integer)value);
						return super.getListCellRendererComponent(list, defaultValue, index, isSelected, cellHasFocus);
					}
				});
				cb.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						LineType lt = new LineType((Integer)options[cb.getSelectedIndex()]);
						newDefaults.put(s, lt);
					}
				});
				comp = cb;
			} else if (Color.class.isInstance(defaultValue)) {
				final ColorButton cb = new ColorButton("Color...", (Color)styleDefault);
				cb.addPropertyChangeListener("background", new PropertyChangeListener() {
					public void propertyChange(PropertyChangeEvent evt) {
						newDefaults.put(s, cb.getBackground());
					}
				});
				comp = cb;
			} else {
				log.println("Editor is skipping unrecognized type " + defaultValue.getClass().getName());
				continue;
			}
			
			oldFields.put(s, styleField);
			oldDefaults.put(s, styleDefault);
			
			gbc.gridy = row++;
			gbc.gridx = 0;
			itemPanel.add(new JLabel(styleName), gbc);
			gbc.gridx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1;
			itemPanel.add(cbSource, gbc);
			gbc.weightx = 0;
			gbc.gridx = 2;
			itemPanel.add(comp, gbc);
			gbc.fill = GridBagConstraints.NONE;
		}
		
		// initialize new fields and defaults to old ones
		newFields.putAll(oldFields);
		newDefaults.putAll(oldDefaults);
		
		// create outer gui elements
		Box h = Box.createHorizontalBox();
		h.add(Box.createHorizontalStrut(4));
		JButton help = new JButton("Help");
		help.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showMessageDialog(null,
					new String[]{
						"Each style can either be a fixed global value, or can vary from feature to feature.",
						"\nFor example:",
						"\n",
						"To fill all polygons, next to the 'fill polygon' style, choose '" + globalItem + "'' and check the 'enable' box.",
						"\n",
						"To fill polygons for features where the 'fill' column is true, choose 'fill' from the fields list.",
						"The default value will be used when a feature does not have a value in the 'fill' column."
					},
					"Style Settings Help", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		h.add(help);
		h.add(Box.createHorizontalStrut(4));
		h.add(Box.createHorizontalGlue());
		h.add(Box.createHorizontalStrut(4));
		h.add(cancel);
		h.add(Box.createHorizontalStrut(4));
		h.add(okay);
		h.setBorder(new EmptyBorder(8,8,8,8));
		JPanel content = new JPanel(new BorderLayout());
		content.add(h, BorderLayout.SOUTH);
		content.add(new JScrollPane(itemPanel), BorderLayout.CENTER);
		d.getContentPane().add(content);
		d.pack();
		d.setVisible(true);
		
		// if dialog returned because okay was not hit, get out now
		if (!usedOkay[0]) {
			return Collections.emptySet();
		}
		
		// otherwise go through the styles that were deemed editable, update
		// what the user changed, and return a set of those changed styles
		Set<Style<?>> changed = new HashSet<Style<?>>();
		for (Style<?> s: oldFields.keySet()) {
			Object oldField = oldFields.get(s);
			Object newField = newFields.get(s);
			Object oldDefault = oldDefaults.get(s);
			Object newDefault = newDefaults.get(s);
			if (!oldField.equals(newField) || !oldDefault.equals(newDefault)) {
				changed.add(s);
				log.println("Settings change: " + oldField + ", " + newField + ", " + oldDefault + ", " + newDefault);
				if (newField == globalItem) {
					s.setConstant(newDefault);
				} else {
					s.setSource((Field)newField, newDefault);
				}
			}
		}
		
		return changed;
	}
}
