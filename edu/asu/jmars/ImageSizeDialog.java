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


package edu.asu.jmars;

import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import javax.swing.JOptionPane;
import javax.swing.JDialog;
import javax.swing.JTextField;
import java.beans.*; //Property change stuff
import java.awt.*;
import java.awt.event.*;

public class ImageSizeDialog extends JDialog
 {
    public ImageSizeDialog(Frame parent, final Dimension d)
	 {
        super(parent, true);
        setTitle("Set the bitmap size");

		final JTextField txtWidth = new PasteField(8);
		final JTextField txtHeight = new PasteField(8);
        final Object[] inputs = { "Pixel Width", txtWidth,
								  "Pixel Height", txtHeight };
        final Object[] buttons = { "Generate", "Cancel" };

        final JOptionPane optionPane =
			new JOptionPane(inputs,
							JOptionPane.QUESTION_MESSAGE,
							JOptionPane.DEFAULT_OPTION,
							null,
							buttons,
							buttons[0]);

		optionPane.addPropertyChangeListener(
			new PropertyChangeListener()
			 {
				public void propertyChange(PropertyChangeEvent e)
				 {
					String prop = e.getPropertyName();

					if(isVisible()
					   && (e.getSource() == optionPane)
					   && (prop.equals(JOptionPane.VALUE_PROPERTY) ||
						   prop.equals(JOptionPane.INPUT_VALUE_PROPERTY)))
					 {
						if(optionPane.getValue().equals(buttons[0]))
						 {
							d.width = getInt(txtWidth);
							d.height = getInt(txtHeight);
						 }
						else
							d.width = d.height = 0;

						setVisible(false);
					 }
				 }
             }
			);

		setContentPane(optionPane);
        pack();
		setLocationRelativeTo(parent);

                txtWidth.setText(String.valueOf(d.width));
                txtHeight.setText(String.valueOf(d.height));


	 }

	private static int getInt(JTextField f)
	 {
		try
		 {
			return  Integer.parseInt(f.getText());
		 }
		catch(NumberFormatException e)
		 {
			return  0;
		 }
	 }
 }
