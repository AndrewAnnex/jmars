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


package edu.asu.jmars.layer.shape2;

import java.awt.Component;
import java.awt.Frame;

import javax.swing.*;
import edu.asu.jmars.util.DebugLog;

public class DrawingProgressDialog {
	private static DebugLog log = DebugLog.instance();
	private volatile static long count = 1;
	
	JProgressBar pm = new JProgressBar();
	long timeToPopup = 500L;
	boolean hidden = false;
	JDialog window = null;
	long myid = count++;
	Frame ownerFrame;
	Component centerOn;
	
	/**
	 * Constructs a ProgressDialog based on the specified parameters.
	 * 
	 * @param ownerFrame Onwer frame of the window containing the progress bar.
	 * @param centerOn Component to center the progress bar on.
	 * @param timeToPopup Milliseconds to wait before popping up the progress dialog.
	 */
	public DrawingProgressDialog(Frame ownerFrame, Component centerOn, long timeToPopup){
		this.ownerFrame = ownerFrame;
		this.centerOn = centerOn;
		this.timeToPopup = timeToPopup;
		
		pm.setMinimum(0);
		pm.setIndeterminate(true);
		pm.setStringPainted(true);
		pm.setString("");
	}
	
	public void setMinimum(int min){
		pm.setMinimum(min);
	}
	public void setMaximum(int max){
		pm.setMaximum(max);
		pm.setIndeterminate(false);
		pm.setString(null);
	}

	public int getMinimum(){
		return pm.getMinimum();
	}
	
    public int getMaximum(){
    	return pm.getMaximum();
    }

    public void setValue(int i){
    	pm.setValue(i);
    }

    public void incValue(){
	    pm.setValue(pm.getValue()+1);
    }

    public int getValue(){
    	return pm.getValue();
    }
    
    public void show(){
    	if (window == null) {
    		window = new JDialog(ownerFrame); // Main.mainFrame
    		window.setUndecorated(true);
    		window.setContentPane(pm);
    		window.pack();
    		window.setLocationRelativeTo(centerOn); // Main.testDriver.mainWindow
    	}
    	if (!window.isVisible()) {
    		window.setVisible(true);
    	}
    }
    public void hide(){
    	log.println(getClass().getName()+"["+myid+"] hide() called");
    	hidden = true;
    	if (window == null){
    		log.println(getClass().getName()+"["+myid+"] hide() called with null window");
    		return;
    	}
    	log.println(getClass().getName()+"["+myid+"] hide() actually hiding window");
    	window.setVisible(false);
    }
}
    


