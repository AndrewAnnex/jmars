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


package edu.asu.jmars.samples.layer.test;

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

public class FakeLView
 extends Layer.LView
 {
	private static DebugLog log = DebugLog.instance();

	public FakeLView()
	 {
		super(null);

		addMouseListener(
			new MouseAdapter()
			 {
				public void mouseClicked(MouseEvent e)
				 {
					log.println("Received click");
					if(SwingUtilities.isLeftMouseButton(e))
						draw(e);
					else
						clearOffScreen();
					repaint();
				 }

				public void draw(MouseEvent e)
				 {
					Graphics2D g2 =
						getProj().createSpatialGraphics(getOffScreenG2());
					Point2D sp = Main.PO.convWorldToSpatialFromProj(
						getProj(),
						getProj().screen.toWorld(e.getPoint()));
					g2.drawString("ABC xyz",
								  (float) sp.getX(),
								  (float) sp.getY());
					g2.setPaint(Color.red);
					g2.draw(new Rectangle2D.Double(sp.getX(),
												   sp.getY(),
												   5,
												   1));
				 }
			 }
			);
	 }

	public void receiveData(Object layerData)
	 {
	 }
	
	protected Object createRequest(Rectangle2D where)
	 {
		return  null;
	 }

	protected Layer.LView _new()
	 {
		return  new FakeLView();
	 }
 }

