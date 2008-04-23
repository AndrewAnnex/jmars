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
 *  A class for a menu item that when selected brings up the properties dialog.
 *
 *  @author: James Winburn MSFF-ASU 
 */
package edu.asu.jmars.ruler;

// generic java imports 
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.lang.*;
import java.util.*;
import javax.swing.*;
import java.awt.font.TextAttribute;
import java.text.*;

// JMARS specific imports.
import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import edu.asu.jmars.swing.*;
//import edu.asu.jmars.layer.groundtrack.*;



public 	class PropertiesMenuItem extends JMenuItem {
	JPanel         propPanel;
	AbstractAction action;
	public PropertiesMenuItem( JPanel p){
		super("Properties");
		propPanel = p;
		action = new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				// This doesn't return until the dialog is dismissed.
				if (RulerManager.propertiesDialog == null){
					System.out.println("no dialog setup.");
				} else {
					JTabbedPane tp = RulerManager.propertiesTabbedPane;
					tp.setSelectedComponent( propPanel);
					RulerManager.propertiesDialog.pack();
					RulerManager.propertiesDialog.setVisible(true);
				}
			}
		};
		addActionListener( action );
	}
}

