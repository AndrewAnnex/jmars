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

import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;

import edu.asu.jmars.util.LineType;

// cell editor for the Line Type column
public class LineTypeCellEditor
	extends DefaultCellEditor
{
	public LineTypeCellEditor(){
		super(getComboBox());
	}

	public boolean isCellEditable(EventObject e){
		if (e==null){
			return false;
		} 
		int clickCount = ((MouseEvent)e).getClickCount();
		return (clickCount>1);
	}

	public Object getCellEditorValue() {
		String result = (String)delegate.getCellEditorValue();
		if (result==null){
			return null;
		}
		if (result.equals("Dotted")){
			return new LineType(1);
		} else if (result.equals("Dashed")){
			return new LineType(2);
		} else if (result.equals("Dash-dot")){
			return new LineType(3);
		} else {
			return new LineType(0);
		}
	}

	private static JComboBox getComboBox() {
		JComboBox comboBox = new JComboBox();
		comboBox.addItem("Solid");
		comboBox.addItem("Dotted");
		comboBox.addItem("Dashed");
		comboBox.addItem("Dash-dot");
		return comboBox;
	}
}
