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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import edu.asu.jmars.layer.stamp.AddLayerWrapper;
import edu.asu.jmars.layer.stamp.StampFilter;
import edu.asu.jmars.layer.stamp.StampLayer;

public class FilterFocusPanel extends JPanel {
	
	StampLayer stampLayer;
	final AddLayerWrapper wrapper;
	
	public FilterFocusPanel(StampLayer newLayer, AddLayerWrapper newWrapper) {
		stampLayer = newLayer;
		wrapper = newWrapper;
		
		final Box vert = Box.createVerticalBox();
	
		JLabel srcLbl = new JLabel(stampLayer.viewToUpdate.getName());
		JPanel srcLblPanel = new JPanel(new BorderLayout());
		srcLblPanel.add(srcLbl, BorderLayout.NORTH);
		
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.setBorder(BorderFactory.createTitledBorder("Source"));
		
		JButton insertButton = new JButton("+");
		insertButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				StampFilter array[]=wrapper.filters.toArray(new StampFilter[wrapper.filters.size()]);
				StampFilter selected = (StampFilter)JOptionPane.showInputDialog(FilterFocusPanel.this, "Select Filter", "Select Filter",
						JOptionPane.QUESTION_MESSAGE, null, array, array.length > 0? array[0]: null);
				
				if (selected != null){
					vert.add(selected.getUI(stampLayer));
					stampLayer.viewToUpdate.focusPanel.validate();
				}
			}
		});
		
		JPanel buttonInnerPanel = new JPanel(new GridLayout(1,1));
		buttonInnerPanel.add(insertButton);
		JPanel buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(buttonInnerPanel, BorderLayout.NORTH);
	
		
		topPanel.add(srcLblPanel, BorderLayout.CENTER);
		topPanel.add(buttonPanel, BorderLayout.EAST);
		
		vert.add(topPanel);
		
		setLayout(new BorderLayout());
		
		JPanel filterPanel = new JPanel();
		filterPanel.setLayout(new BorderLayout());
		filterPanel.add(vert, BorderLayout.NORTH);
		
		JScrollPane scrollPane = new JScrollPane(filterPanel);
		
		add(scrollPane, BorderLayout.CENTER);
	}
}
