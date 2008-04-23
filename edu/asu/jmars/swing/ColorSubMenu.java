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


package edu.asu.jmars.swing;

import edu.asu.jmars.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

public abstract class ColorSubMenu extends JMenu
 {
	private static final Color colorList[] =
	{
		Color.blue,
		Color.cyan,
		Util.darkGreen,
		Color.green,
		Color.yellow,
		Color.orange,
		Color.red,
		Color.magenta,
		Color.pink,

		Color.white,
		Color.lightGray,
		Color.gray,
		Color.darkGray,
		Color.black
	};

	protected Component colorChooserParent = null;
	protected String colorChooserTitle = null;
	protected Color colorChooserDefault = null;

	public ColorSubMenu(String title)
	 {
		super(title);

		prependMenuItems();
		add(new CustomColorMenuItem("Custom color..."));
		add(new JSeparator());
		for(int i=0; i<colorList.length; i++)
			add(new ColorMenuItem(colorList[i]));
	 }

	protected void prependMenuItems()
	 {
	 }

	protected void colorChosen(Color newColor)
	 {
	 }

	private class CustomColorMenuItem extends AbstractAction
	 {
		CustomColorMenuItem(String title)
		 {
			super(title);
		 }

		public void actionPerformed(ActionEvent e)
		 {
			Color newColor =
				JColorChooser.showDialog(colorChooserParent,
										 colorChooserTitle,
										 colorChooserDefault);
			if(newColor != null)
				colorChosen(newColor);
		 }
	 };

	private class ColorMenuItem extends JMenuItem implements ActionListener
	 {
		Border hilight;
		Color col;
		ColorMenuItem(Color col)
		 {
			super(" ");
			this.col = col;
			if(Util.getB(col) < 0.5)
				hilight = BorderFactory.createLineBorder(Color.white, 2);
			else
				hilight = BorderFactory.createLineBorder(Color.black, 2);

			addActionListener(this);
		 }

		public void actionPerformed(ActionEvent e)
		 {
			colorChosen(col);
		 }

		protected void paintBorder(Graphics g)
		 {
			if(isArmed())
				hilight.paintBorder(this, g, 0, 0,
									getWidth(), getHeight());
		 }

		protected void paintComponent(Graphics g)
		 {
			g.setColor(col);
			g.fillRect(0, 0, getWidth(), getHeight());
		 }
	 }
 }
