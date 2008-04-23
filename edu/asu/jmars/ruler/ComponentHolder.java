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


/* 
 *  This software was adapted from the MultiSplitPane code from the Sun JXTA project.
 *  It was modified for use in JMARS.
 * 
 *  The implementation differs from the original in that there is a single title 
 *  bar between the top splitpane.  This titlebar allows the hiding/showing of ALL the 
 *  rulers that are added to the pane.
 * 
 *  @author James Winburn MSFF-ASU  10/03
 */ 
package edu.asu.jmars.ruler;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.metal.*;
import java.util.*;


public class ComponentHolder extends JPanel {
	JComponent component;
	int height;
	int restoreHeight; 
	
	ComponentHolder(JComponent component) {
		super(new GridLayout(1,1));
		this.component = component;
		height = component.getPreferredSize().height;
		ComponentHolder.this.add(component);
	} 
	
	void setRestoreHeight(int restoreHeight) {
		this.restoreHeight = restoreHeight;
	} 
	
	int getRealMinimumHeight() {
		return component.getMinimumSize().height;
	}
	
	public Dimension getPreferredSize() {
		return new Dimension(component.getPreferredSize().width, height);
	}
	
	public void setPreferredSize( Dimension d) {
		height = d.height;
		component.setPreferredSize( d);
	}

	public Dimension getMinimumSize() {
		return new Dimension(component.getMinimumSize().width, height);
	}
	
	public Dimension getMaximumSize() {
		return new Dimension(component.getMaximumSize().width, height);
	}
} 

