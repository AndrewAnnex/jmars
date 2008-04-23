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


package edu.asu.jmars.layer.map;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.event.*;
import java.text.*;
import javax.swing.border.*;

public class BackFocus extends FocusPanel
 {
	FancyColorMapper cmap;

	JPanel p1;
	JPanel p2;
	JPanel p3;
	JLabel info;
	JLabel l1,l2,l3,l4;
	private static NumberFormat nf = NumberFormat.getInstance();
	BackLayer myLayer;

	public BackFocus(Layer.LView p)
	 {
		super(p);
		setLayout(new BorderLayout());

		cmap = parent.createFancyColorMapper();
		cmap.addChangeListener(
			new ChangeListener()
			 {
				public void stateChanged(ChangeEvent e)
				 {
					if(!cmap.isAdjusting())
						parent.setColorMapOp(cmap.getColorMapOp());
				 }
			 }
			);
		add(cmap, BorderLayout.NORTH);



		myLayer = (BackLayer)p.getLayer();
		if (!myLayer.global) { //Partial Map...Add BBox info
			nf.setMaximumFractionDigits(2);
			nf.setMinimumFractionDigits(2);
			nf.setMaximumIntegerDigits(3);

			info = new JLabel("Bounding Region: ");
			info.setForeground(Color.black);
			info.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

			l1 = new JLabel("Min Lat: "+nf.format(myLayer.boundry.getY()));
			l1.setBackground(Color.white);
			l1.setForeground(Color.black);
//			l1.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
			l2 = new JLabel("Min Lon: "+nf.format(myLayer.boundry.getX()));
			l2.setBackground(Color.white);
			l2.setForeground(Color.black);
//			l2.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
			l3 = new JLabel("Max Lat: "+nf.format((myLayer.boundry.getY()+myLayer.boundry.getHeight())));
			l3.setBackground(Color.white);
			l3.setForeground(Color.black);
//			l3.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
			l4 = new JLabel("Max Lon: "+nf.format((myLayer.boundry.getX()+myLayer.boundry.getWidth())));
			l4.setBackground(Color.white);
			l4.setForeground(Color.black);
//			l4.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

			p3 = new JPanel();
			p3.setLayout(new BoxLayout(p3, BoxLayout.Y_AXIS));
			p3.add(l1);
			p3.add(Box.createVerticalStrut(8));
			p3.add(l2);
			p3.add(Box.createVerticalStrut(8));
			p3.add(l3);
			p3.add(Box.createVerticalStrut(8));
			p3.add(l4);
			p3.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
			p3.setMaximumSize(new Dimension(150,300));

			p2 = new JPanel();
			p2.setLayout(new BoxLayout(p2, BoxLayout.Y_AXIS));
			p2.add(info);
			p2.add(Box.createVerticalStrut(10));
			p2.add(p3);
			p2.setMaximumSize(new Dimension(150,350));


			p1 = new JPanel();
			p1.setLayout(new BoxLayout(p1, BoxLayout.X_AXIS));
			p1.add(Box.createHorizontalGlue());
			p1.add(p2);
			p1.add(Box.createHorizontalGlue());
			p1.setMaximumSize(new Dimension(150,400));
//			p1.add(Box.createVerticalGlue());
//			p1.setBackground(Color.white);
			p1.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

			add(p1,BorderLayout.SOUTH);
		}
	 }
 }
