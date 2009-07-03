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


package edu.asu.jmars.places;

import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.Util;

/**
 * Creates a dialog for editing places. Can be reused by using
 * {@link #setPlace(Place)} to update the place to edit.
 * 
 * The save button will use the {@link PlaceStore} given to the constructor to
 * save the place. This will create a new place or replace an existing one.
 * 
 * The default close operation is not replaced so code using this class should
 * take care to set it to an expected value.
 */
public class PlaceEditDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	/** The place being edited */
	private final PlaceStore store;
	private Place place;
	private JTextField nameText = new JTextField(20);
	private JTextField labelText = new JTextField(20);
	private JLabel placeText = new JLabel();
	private JButton save = new JButton("Save");
	
	/** Constructs a new place panel */
	public PlaceEditDialog(Frame owner, PlaceStore store) {
		super(owner, "Edit Place...", true);
		this.store = store;
		
		save.setMnemonic('S');
		save.setEnabled(false);
		save.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					save();
				} catch (Exception ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(
						Main.testDriver,
						"Error saving place:\n\n" + Util.join("\n", getMessages(ex)),
						"Error saving place",
						JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		
		JPanel panel = new JPanel(new GridBagLayout());
		nameText.setToolTipText("Name to appear in the menu, must be unique");
		labelText.setToolTipText("Comma-separated list of menu labels the place should appear in");
		
		Insets i = new Insets(4,4,4,4);
		int padx = 0;
		int pady = 0;
		panel.add(new JLabel("Name"), new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,i,padx,pady));
		panel.add(nameText, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,i,padx,pady));
		panel.add(new JLabel("Labels"), new GridBagConstraints(0,1,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,i,padx,pady));
		panel.add(labelText, new GridBagConstraints(1,1,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,i,padx,pady));
		panel.add(new JLabel("Place"), new GridBagConstraints(0,2,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,i,padx,pady));
		panel.add(placeText, new GridBagConstraints(1,2,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,i,padx,pady));
		panel.add(save, new GridBagConstraints(0,3,2,1,1,0,GridBagConstraints.EAST,GridBagConstraints.NONE,i,padx,pady));
		add(panel);
		pack();
	}
	
	private static List<String> getMessages(Throwable t) {
		List<String> out = new ArrayList<String>();
		for (Throwable s = t; s != null; s = s.getCause()) {
			out.add(s.getMessage());
		}
		return out;
	}
	
	/** Change the place being edited */
	public void setPlace(Place place) {
		this.place = place;
		save.setEnabled(place != null);
		nameText.setText(place == null ? "" : place.getName());
		labelText.setText(place == null ? "" : Util.join(", ", place.getLabels()));
		placeText.setText(place == null ? "" : MessageFormat.format(
			"<html><body>Location: {0,number,#.###}E {1,number,#.###}N<br>" +
			"Projection: {2,number,#.###}E {3,number,#.###}N<br>" +
			"PPD: {4}</body></html>",
			place.getLonLat().getX(), place.getLonLat().getY(),
			place.getProjCenterLonLat().getX(), place.getProjCenterLonLat().getY(),
			place.getPpd()));
	}
	
	public void setVisible(boolean vis) {
		nameText.selectAll();
		nameText.requestFocus();
		super.setVisible(vis);
	}
	
	/** Returns the place being edited */
	public Place getPlace() {
		return place;
	}
	
	/** Removes the old place and adds the new one */
	private void save() {
		store.remove(place);
		place.setName(nameText.getText());
		place.getLabels().clear();
		for (String label: labelText.getText().split(", *")) {
			label = label.trim();
			if (label.length() > 0) {
				place.getLabels().add(label);
			}
		}
		store.add(place);
		setVisible(false);
	}
}
