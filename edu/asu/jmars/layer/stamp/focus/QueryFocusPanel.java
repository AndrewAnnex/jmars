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


package edu.asu.jmars.layer.stamp.focus;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

import edu.asu.jmars.layer.stamp.AddLayerWrapper;
import edu.asu.jmars.layer.stamp.StampLayer;
import edu.asu.jmars.util.Util;

public class QueryFocusPanel extends JPanel {

	public QueryFocusPanel(final AddLayerWrapper wrapper, final StampLayer stampLayer)
	{
	    setLayout(new BorderLayout());
	    add(wrapper.getContainer(), BorderLayout.CENTER);
	    
	    // Construct the "buttons" section of the container.
	    JPanel buttons = new JPanel();
	    buttons.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
	
	    JButton ok = new JButton("Update Search");
	    ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (stampLayer.queryThread==null) {
	                // Update layer/view with stamp data from new version of
	                // query using parameters from query panel.
	                String queryStr = wrapper.getQuery();
	                stampLayer.setQuery(queryStr);
				}
			}
		});
	    buttons.add(ok);
	    
	    JButton help = new JButton("Help");
	    help.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// TODO: make it a config variable!
	    		Util.launchBrowser("http://jmars.asu.edu/wiki/index.php/Instrument_Glossaries");
			}
	    });
	    
	    buttons.add(help);
	             
	    add(buttons, BorderLayout.SOUTH);	    
	}
}
