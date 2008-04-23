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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;

import edu.asu.jmars.Main;
import edu.asu.jmars.swing.AbstractCellEditor;

/**
 * TableCellEditor for Color objects.
 */
public class ColorCellEditor
	extends AbstractCellEditor
	implements TableCellEditor, ActionListener
{
	Color  color;
	JColorChooser colorChooser;
	JDialog colorChooserDialog;
	ColorCellRenderer renderer;
	
	public ColorCellEditor(){
		super();
		
		color = null;
		
		colorChooser = new JColorChooser();
		colorChooserDialog = JColorChooser.createDialog(renderer, "Color Chooser", true, colorChooser, this, null);
		
		renderer = new ColorCellRenderer();
		renderer.addMouseListener(new MouseAdapter(){
			/*
			 * (non-Javadoc)
			 * @see java.awt.event.MouseAdapter#mousePressed(java.awt.event.MouseEvent)
			 * 
			 * TODO: This has to be mousePressed NOT mouseClicked. It so happens
			 * that the mousePressed occurs before mouseClicked and that's why
			 * we are using mousePressed() here. Any change here must be reflected
			 * in isCellEditable() below.
			 */
			public void mousePressed(MouseEvent e) {
				/*
				 *  NOTE:
				 *  Events that were used in isCellEditable are dispatched a
				 *  second time ater the Editor is installed in the table cell.
				 *  These second-time dispatched events are being used here.
				 */
				if (!checkIsCellEditable(e))
					return;
			
				colorChooser.setColor(color); // show current color in chooser
				colorChooserDialog.setLocationRelativeTo(renderer);
				colorChooserDialog.setVisible(true); // get new color
			}
		});
	}
	
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
		color = (Color)value;
		return renderer.getTableCellRendererComponent(table, value, isSelected, renderer.isFocusOwner(), row, column);
	}

	public Object getCellEditorValue() {
		return color;
	}

	public void actionPerformed(ActionEvent e) {
		// We reach here when user presses OK in the color chooser.
		// If the user did select a color, make it the current color.
		if (colorChooser.getColor() != null)
			color = colorChooser.getColor();
		
		// Mark the end of editing operation.
		fireEditingStopped();
	}
	
	public boolean isCellEditable(EventObject evt){
		return checkIsCellEditable(evt);
	}
	
	private boolean checkIsCellEditable(EventObject evt){
		if (evt instanceof MouseEvent){
			MouseEvent e = (MouseEvent)evt;
			return(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2);
		}
		return false;
	}
	
	public boolean shouldSelectCell(EventObject evt){
		return true;
	}
}
