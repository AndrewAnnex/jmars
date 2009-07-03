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


package edu.asu.jmars.layer.stamp;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import edu.asu.jmars.Main;
import edu.asu.jmars.swing.ColorCombo;
import edu.asu.jmars.util.Util;

public class AddLayerDialog extends JDialog {
	ColorCombo initialColor = new ColorCombo();
	JTextField initialName = new JTextField();
	private boolean userHitOK = false;

	/**
	 ** Constructs a modal dialog for adding stamp layer
	 **/
	public AddLayerDialog(AddLayerWrapper wrapper)
	{
		super(Main.testDriver.getLManager(), "Add " + wrapper.getInstrument() + " stamp layer", true);

		JPanel queryPanel = wrapper.getContainer();
		if (queryPanel!=null) {
			getContentPane().setLayout(new BorderLayout());
			getContentPane().add(queryPanel, BorderLayout.CENTER);
		}

		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BorderLayout());
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel otherSettings = new JPanel();
		otherSettings.setLayout(new GridLayout(0, 2));

		otherSettings.add(new JLabel("Use stamp color:"));
		otherSettings.add(initialColor);
		otherSettings.add(new JLabel("Custom layer name:"));
		otherSettings.add(initialName);

		// Construct the "buttons" section of the container.
		JPanel buttons = new JPanel();
		buttons.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		final JDialog dialog = this;

		JButton ok = new JButton("Okay");
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				userHitOK = true;
				dialog.setVisible(false);
			}
		});
		buttons.add(ok);

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				dialog.setVisible(false);
			}

		});

		buttons.add(cancel);  

		JButton help = new JButton("Help");
		help.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Util.launchBrowser("http://jmars.asu.edu/wiki/index.php/Instrument_Glossaries");
			}
		});

		buttons.add(help);

		bottomPanel.add(otherSettings, BorderLayout.CENTER);
		bottomPanel.add(buttons, BorderLayout.SOUTH);

		getContentPane().add(bottomPanel, BorderLayout.SOUTH);

		Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();

		setLocation(Main.getLManager().getLocation());
		pack();

		// Attempt to make sure the bottom of the window (with the submit/cancel buttons)
		// is visible on the screen.  Create an extra 60 pixel margin to deal for snazzy
		// toolbars that drop out of view but screw with the reported screen dimensions.
		int dif = screen.height - (getLocation().y + getSize().height + 60);

		if (dif<0) {
			setSize(getSize().width, getSize().height+dif);
		}
	}

	public boolean isCancelled()
	{
		return !userHitOK;
	}
}
