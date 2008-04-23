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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import edu.asu.jmars.util.LineType;

//  The cell renderer for the line type column.
public class LineTypeTableCellRenderer extends JPanel implements TableCellRenderer
{
	private BasicStroke lineStroke = null;
	private static final Color nullFieldBackground = Color.lightGray;
	
	public LineTypeTableCellRenderer(){
		super();
		setOpaque(true);
	}
	
	public Component getTableCellRendererComponent(JTable table, Object val, boolean isSelected, boolean hasFocus, int row, int column) {
		setBackground(val == null? nullFieldBackground: isSelected? table.getSelectionBackground(): table.getBackground());
		setForeground(isSelected? table.getSelectionForeground(): table.getForeground());
		setFont(table.getFont());
		
		if (val==null){
			lineStroke = null;
			repaint();
			return this;
		}
		
		LineType lineType = (LineType)val;
		lineStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
				lineType.getDashPattern(), 0.0f);
		
		repaint();
		return this;
	}
	
	public void paintComponent( Graphics g){
		Graphics2D g2 = (Graphics2D)g;
		Dimension d = getSize();
		
		g2.setBackground(getBackground());
		g2.clearRect(0, 0, d.width, d.height);
		if (lineStroke != null){
			g2.setStroke(lineStroke);
			g2.setColor(getForeground());
			g2.draw(new Line2D.Double(0,d.height/2, d.width, d.height/2));
		}
	}
	
}
