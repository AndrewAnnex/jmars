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
 * A class that abstracts the behavior of a JCheckBox.
 * The checkbox is displayed in an initial state.  Clicking on it 
 * toggles the state and redisplays the rulers.
 * The state of the check box  may be accessed by other objects.
 *
 *  @author  James Winburn MSSF-ASU  
 */
package edu.asu.jmars.ruler;

// generic java imports.
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class RulerCheckBox 
extends JCheckBox
{
	boolean boolValue;

	public RulerCheckBox( String l, boolean b){
		super(l, b);
		setFocusPainted( false);
		boolValue = super.isSelected();
		addActionListener( new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				boolValue = !boolValue;
				setSelected( boolValue );
				RulerManager.Instance.notifyRulerOfViewChange();
			}
		});
	}


	public void setSelected( boolean  b){
		boolValue = b;
		super.setSelected( boolValue);
	}

	public boolean isSelected(){
		return boolValue;
	}

} // end: RulerCheckBox

