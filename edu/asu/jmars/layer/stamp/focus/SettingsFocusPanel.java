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

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.StringTokenizer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.stamp.StampLView;
import edu.asu.jmars.layer.stamp.StampLayer;
import edu.asu.jmars.layer.stamp.StampLayerSettings;
import edu.asu.jmars.swing.ColorCombo;
import edu.asu.jmars.swing.PasteField;
import edu.asu.jmars.util.Config;


public class SettingsFocusPanel extends JPanel {

    JLabel         labelBrowseCmd;

	public SettingsFocusPanel(final StampLayerSettings settings, final StampLView stampLView)
	{
		JLabel borderColorLbl = new JLabel("Outline color:");
		final ColorCombo borderColor = new ColorCombo();
		borderColor.setColor(settings.getUnselectedStampColor());
		borderColor.addActionListener(new ActionListener() {
          	public void actionPerformed(ActionEvent e) {	            		
           		settings.setUnselectedStampColor(borderColor.getColor());

           		StampLView child = (StampLView) stampLView.getChild();

           		if (!settings.hideOutlines()) {
           			stampLView.drawOutlines();
           			child.drawOutlines();
           		}
          	}
        });
	        
			    
		JLabel fillColorLbl = new JLabel("Fill color:");
		final ColorCombo fillColor = new ColorCombo();
		fillColor.setColor(new Color(settings.getFilledStampColor().getRGB() & 0xFFFFFF, false));
		fillColor.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int alpha = settings.getFilledStampColor().getAlpha();
				settings.setFilledStampColor(
					new Color( (alpha<<24) | (fillColor.getColor().getRGB() & 0xFFFFFF), true));
				if (alpha != 0){
					stampLView.redrawEverything(true);
				}
			}
		});
					    
		JLabel alphaLbl = new JLabel("Fill alpha:");
		final JSlider alpha = new JSlider(0, 255, 0);
		alpha.setValue(settings.getFilledStampColor().getAlpha());
	    alpha.addChangeListener(new ChangeListener() {
	    	public void stateChanged(ChangeEvent e) {
	    		if (alpha.getValueIsAdjusting()) {
	    			return;
	    		}
	    		
	    		int alphaVal = alpha.getValue();
	    		int color = settings.getFilledStampColor().getRGB() & 0xFFFFFF;
	    		settings.setFilledStampColor(new Color((alphaVal<<24) | color, true));
	    		stampLView.redrawEverything(true);
	    	}
	    });
		
		JLabel nameLbl = new JLabel("Layer name:");
		final PasteField name = new PasteField(12);
		name.setText(settings.getName());
		name.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	settings.setName(name.getText());
                Main.getLManager().updateLabels();
            }
        });
		
		final JButton browser = new JButton("Customize Webbrowser...");
		browser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)
			{
				new BrowserChoiceDialog().show();

	            String browseCmd = Config.get(StampLayer.CFG_BROWSER_CMD_KEY, " ");
	            if (browseCmd.trim().length() > 0)
	                // Extract program name for browser
	                browseCmd = new StringTokenizer(browseCmd).nextToken();
	            labelBrowseCmd.setText(browseCmd);
			}
		});
		
	    String browseCmd = Config.get(StampLayer.CFG_BROWSER_CMD_KEY, " ");
	    if (browseCmd.trim().length() > 0)
	        // Extract program name for browser
	        browseCmd = new StringTokenizer(browseCmd).nextToken();
	    labelBrowseCmd = new JLabel(browseCmd);
	    
	    
	    setLayout(new GridBagLayout());
	    setBorder(new EmptyBorder(4,4,4,4));
	    int row = 0;
	    int pad = 4;
	    Insets in = new Insets(pad,pad,pad,pad);
	    add(borderColorLbl, new GridBagConstraints(0,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    add(borderColor, new GridBagConstraints(1,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    row++;
	    add(fillColorLbl, new GridBagConstraints(0,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    add(fillColor, new GridBagConstraints(1,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    row++;
	    add(alphaLbl, new GridBagConstraints(0,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    add(alpha, new GridBagConstraints(1,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    row++;
	                
	    add(nameLbl, new GridBagConstraints(0,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    add(name, new GridBagConstraints(1,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    row++;
	    add(labelBrowseCmd, new GridBagConstraints(0,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    add(browser, new GridBagConstraints(1,row,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
	    row++;
	    add(new JLabel(),new GridBagConstraints(0,row,2,1,1,1,GridBagConstraints.WEST,GridBagConstraints.BOTH,in,pad,pad));
		
	}
}
