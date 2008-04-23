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


package edu.asu.jmars.util.stable;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

// The cell editor for Boolean columns.
public class BooleanCellRenderer
	extends JPanel
	implements TableCellRenderer
{
	private JCheckBox checkBox;
	private static final Color nullFieldBackground = Color.lightGray;

	public BooleanCellRenderer(){
		super();
		setOpaque(true);
		setLayout(new BorderLayout());
		checkBox = new JCheckBox("");
		checkBox.setHorizontalAlignment(JCheckBox.CENTER);
	}

	public Component getTableCellRendererComponent(JTable table, Object val, boolean isSelected, boolean hasFocus, int row, int column) {
		if (val==null){
			removeAll();
		} else {
			checkBox.setSelected(((Boolean)val).booleanValue());
			add(checkBox, BorderLayout.CENTER);
		}
		
		setBackground(val==null? nullFieldBackground: isSelected? table.getSelectionBackground(): table.getBackground());
		setForeground(isSelected? table.getSelectionForeground(): table.getForeground());
		
		checkBox.setBackground(getBackground());
		checkBox.setForeground(getForeground());
		
		return this;
	}
}

