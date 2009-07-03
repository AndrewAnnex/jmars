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


package edu.asu.jmars.layer.stamp.functions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.SwingUtilities;

import edu.asu.jmars.layer.stamp.FilledStamp;
import edu.asu.jmars.layer.stamp.StampLayer;
import edu.asu.jmars.layer.stamp.StampShape;
import edu.asu.jmars.layer.stamp.focus.FilledStampFocus;

public class RenderFunction implements ActionListener {
	
	private StampLayer stampLayer;
	private FilledStampFocus filledStampFocus;
	private String imageType;
	
	public RenderFunction(StampLayer newLayer, FilledStampFocus newFilledFocus, String newType) {
		stampLayer=newLayer;
		filledStampFocus=newFilledFocus;
		imageType=newType;		
	}
	
	public void actionPerformed(ActionEvent e) {
	    Runnable runme = new Runnable() {
	        public void run() {
				List<StampShape> selectedStamps = stampLayer.getSelectedStamps();
	        	
	        	StampShape stampsToAdd[] = new StampShape[selectedStamps.size()];
	        	String types[] = new String[selectedStamps.size()];
	        	FilledStamp.State states[] = new FilledStamp.State[selectedStamps.size()];
	        	
	        	int cnt=0;
	        	
	        	for (StampShape stamp : selectedStamps) {
	        		stampsToAdd[cnt]=stamp;
	        		states[cnt]=null;
	        		
	        		if (imageType.equalsIgnoreCase("ABR")&&!stamp.getId().startsWith("V")) {
	        			stampsToAdd[cnt]=null;
	        			types[cnt]=null;
	        			continue;
	        		}
	        		
	        		if (imageType.equalsIgnoreCase("BTR")&&!stamp.getId().startsWith("I")) {
	        			stampsToAdd[cnt]=null;
	        			types[cnt]=null;
	        			continue;
	        		}
	        		
	        		if (imageType.equalsIgnoreCase("ABR / BTR")) {
	        			if (stamp.getId().startsWith("I")) {
	        				types[cnt]="BTR";
	        			} else {
	        				types[cnt]="ABR";
	        			}
	        		} else {								        			
						types[cnt]=imageType;
	        		}
	        		cnt++;
	        	}
	        	
	        	filledStampFocus.addStamps(stampsToAdd, states, types);
	        	
	        }
	    };
	
	    SwingUtilities.invokeLater(runme);
	}
}
