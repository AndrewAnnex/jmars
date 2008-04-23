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
 **  An abstraction of the methods and objects that deal with properties of JMARS rulers.
 **
 ** @author James Winburn MSFF-ASU 1/04
 **/
package edu.asu.jmars.ruler;

import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;
import javax.swing.*;


public  class RulerProperty {
	private Object   prop;
	private String   className;
	private String   name;
	private boolean   boolValue;

	/**
	 ** Constructor
	 **
	 ** @param name - A string that is the name of the property that will be saved to 
	 **       and loaded back from the config file.  Note that this does not have to 
	 **       be the same as the object name.
	 ** @param property  - A component that is to be drawn in the properties panel.
	 **/ 
	public RulerProperty( String name, Object property){
		this.prop = property;
		this.name = name;
		this.className = prop.getClass().getName();
	}
	
	
	/**
	 ** Returns the Component corresponding to the property.  This Component
	 ** may be cast to the actual class of the property in order to access 
	 ** fields of the property.
	 **
	 ** @param  N/A
	 ** @return The Component.
	 **/
	public Component getProp(){
		return (Component)prop;
	}			
	

	/**
	 ** Returns the boolean value of the component if the component is a JCheckBox.  False
	 ** is returned if it is not a JCheckBox.
	 **
	 ** @param N/A
	 ** @return the boolean value of a JCheckBox component.
	 **/
	public boolean getBool(){
		if (className.equals("edu.asu.jmars.ruler.RulerCheckBox") ){
			return ((RulerCheckBox)prop).isSelected();
		} else {
			return false;
		}
	}

	/**
	 ** Returns the color of the component if the component is a ColorButton.  Null
	 ** is returned if it is not a ColorButton.
	 **
	 ** @param N/A
	 ** @return the color of the ColorButton component.
	 **/
	public Color getColor(){
		if (className.equals("edu.asu.jmars.ruler.ColorButton") ){
			return ((ColorButton)prop).getColor();
		} else {
			return null;
		}
	}

	/**
	 * Returns the int value for the RulerStepSlider component or zero otherwise.
	 * @return Slider's current value.
	 */
	public int getIntValue(){
		if (className.equals("edu.asu.jmars.ruler.RulerStepSlider")){
			return ((RulerStepSlider)prop).getValue();
		}
		return 0;
	}

	/**
	 ** gets the name of the component which is generally used for storing and retrieving
	 ** the component in the config file.
	 **/
	public String getName(){
		return name;
	}

	
	/**  
	 **  Loads the property from the config file if the key for the Compoent exists.
	 **
	 **  @param a Hashtable that is all of the properties retrieved from the config file.
	 **
	 **  @return N/A
	 **/
	public void loadSettings( Hashtable rulerSettings){
		if (className.equals("edu.asu.jmars.ruler.ColorButton") &&  rulerSettings.containsKey( name) ) {
			((ColorButton)prop).setColor( (Color)rulerSettings.get( name));
		}
		if (className.equals("edu.asu.jmars.ruler.RulerCheckBox") &&  rulerSettings.containsKey( name) ) {
			boolean boolValue = ((Boolean)rulerSettings.get(name)).booleanValue();
			((RulerCheckBox)prop).setSelected( boolValue );
		} 
		if (className.equals("edu.asu.jmars.ruler.RulerStepSlider") && rulerSettings.containsKey(name)){
			RulerStepSlider.Settings s = (RulerStepSlider.Settings)rulerSettings.get(name);
			RulerStepSlider slider = (RulerStepSlider)prop;
			slider.restoreSettings(s);
		}
	}
	
	
	/** Saves the properties of the Component to the config file.  The properties are set
	 ** externally to this method.
	 ** 
	 ** @param A Hashtable that is all of the properties to be saved to the config file.
	 **
	 ** @return N/A
	 **/
	public void saveSettings( Hashtable rulerSettings){
		if (className.equals("edu.asu.jmars.ruler.ColorButton") ){
			rulerSettings.put(name,  (Object)( ((ColorButton)prop).getColor()));
		}
		if (className.equals("edu.asu.jmars.ruler.RulerCheckBox") ){
			rulerSettings.put(name,  new Boolean( ((RulerCheckBox)prop).isSelected()) );
		}
		if (className.equals("edu.asu.jmars.ruler.RulerStepSlider")){
			rulerSettings.put(name, ((RulerStepSlider)prop).getSettings());
		}
	}
}


