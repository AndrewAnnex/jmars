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

import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import edu.asu.jmars.util.Util;

// Renderer for Strings, Integers, and Doubles.
public class TextCellRenderer
	extends JLabel
	implements TableCellRenderer
{
	private static final int MAX_WIDTH = 80;
	public static final Color nullFieldBackground = Color.lightGray;

	public TextCellRenderer(){
		super();
		setOpaque(true);
	}

	public Component getTableCellRendererComponent(JTable table, Object val, boolean isSelected, boolean hasFocus, int row, int column) {
		String s = (val == null ? "" : (String)val);
		String tip = null;

		// Limit string length to 80 chars
		if (s.length() > MAX_WIDTH) {
			s = s.substring(0, MAX_WIDTH-1)+"...";
			tip = Util.foldText((String)val, MAX_WIDTH, "\n");
		}

		setBackground(val==null? nullFieldBackground: isSelected? table.getSelectionBackground(): table.getBackground());
		setForeground(isSelected? table.getSelectionForeground(): table.getForeground());
		setFont(table.getFont());
		setText(s);
		setToolTipText(tip);

		return this;
	}
}
