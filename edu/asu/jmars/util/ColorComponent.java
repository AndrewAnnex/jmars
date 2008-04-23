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


package edu.asu.jmars.util;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.layer.map.*;
import edu.asu.jmars.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;
import java.awt.geom.*;
import java.awt.image.*;




// An class that displays a button and allows the user to change a color of some
// component or other.  It is used in this application to change the color of the directional
// light and the color of the background.
public class ColorComponent extends JButton {
    private Color  color;
	
    public ColorComponent( String l, Color c){
	super(l);
	setColor( c);
	setFocusPainted( false);
	requestFocus();
    }
    
    // sets the background as the color of the button.  If the color is lighter
    // than gray, then black is used for the color of the button's text instead
    // of white.
    public void setColor( Color c){
	color = c;
	setBackground( c);
	if ( (c.getRed() + c.getGreen() + c.getBlue()) > (128 + 128 + 128) ){
	    setForeground( Color.black);
	} else {
	    setForeground( Color.white);
	}
    }
    
    // returns the color that was previously defined.
    public Color getColor(){
	return color;
    }

}


			
