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


package edu.asu.jmars.graphics;

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.ruler.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.text.*;
import java.util.*;
import javax.swing.*;

public class SpatialLocationRuler extends BaseRuler implements DataReceiver
{

	private Point2D _leftLocation;
	private Point2D _rightLocation;
	private Point2D _centerLocation;
	
	static final int DEFAULT_RULER_HEIGHT = 70;
	
	public SpatialLocationRuler( Layer layer, Layer.LView lview) {
		super.setDescription("Spatial Location Ruler");
	}
	
	
	public void receiveData(Object data) {
		Rectangle2D world = getLView().getProj().getWorldWindow();
	}
	
	protected Point2D _latlong = new Point2D.Double(0,0);
		
	public Point2D getLatLong(Point2D pt2d) 
	{
		return edu.asu.jmars.Main.PO.convWorldToSpatialFromProj(getLViewManager().getProj(),pt2d);
	}
	
	public void updateRuler() {
		Rectangle2D worldData =  getLViewManager().getProj().getWorldWindow();
		_latlong = getLatLong(new Point2D.Double(Math.round(worldData.getX() + 
								    worldData.getWidth()/2),0));
	}
	
	public void paintThis(Graphics g) {
		g.setColor(Color.red);
		g.drawLine(12, 4, 12, getSize().height - 4);
		g.drawLine(12, getSize().height/2, getSize().width-10, getSize().height/2);
		g.drawLine(getSize().width-10, 4, getSize().width-10, getSize().height - 4);
		
		DecimalFormat decF = new DecimalFormat("0.00");
		FontMetrics fm = g.getFontMetrics(g.getFont());
		g.drawString("Lat: " + decF.format(_latlong.getY()) + " deg N", 14 , fm.getHeight());
		g.drawString("Lon: " + decF.format(_latlong.getX()) + " deg W", 14 , fm.getHeight()*2 - 3);
		
	}
	
	
	
	public void mouseMoved(MouseEvent e)
	{
		
		Dimension screenData =  getLViewManager().getProj().getScreenSize();
		int newX = Math.max(e.getPoint().x-13, 0);
		newX = Math.min(newX, screenData.width);
		
		Point p1 = new Point(newX, screenData.height/2);
		Point2D world = getLViewManager().getProj().screen.toWorld(p1);
		
		_latlong = getLatLong(new Point2D.Double(Math.round(world.getX()),0));
		RulerManager.Instance.notifyRulerOfViewChange();
		
	}
	
	public void mouseEntered(MouseEvent e)
	{
		mouseMoved(e);
	}
	
	public void mouseExited(MouseEvent e)
	{
		updateRuler();
		RulerManager.Instance.notifyRulerOfViewChange();
	}
	
}
