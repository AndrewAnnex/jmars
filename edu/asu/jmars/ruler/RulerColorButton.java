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


/**
 * A class for a button that defines the color of another object.  
 * The button is displayed in an initial color.  Clicking on it 
 * brings up a color chooser dialog. The color of the
 * button changes to the color selected in this dialog.
 * The color of the button may be accessed by other objects.
 *
 *  @author  James Winburn MSSF-ASU  
 */
package edu.asu.jmars.ruler;

// generic java imports.
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import edu.asu.jmars.swing.ColorButton;

public class RulerColorButton extends ColorButton {
	
	public RulerColorButton( String l, Color c){
		super(l,c);
		addPropertyChangeListener("background", new PropertyChangeListener(){
			public void propertyChange(PropertyChangeEvent evt) {
				RulerManager.Instance.notifyRulerOfViewChange();
			}
		});
	}
}

