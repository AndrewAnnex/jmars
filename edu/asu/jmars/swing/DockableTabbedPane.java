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

import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.event.*;

public class DockableTabbedPane extends SwappableTabbedPane
 {
    private static final DebugLog log = DebugLog.instance();

    public DockableTabbedPane()
     {
	MouseInputListener myMouse = new MyMouse();
	addMouseListener(myMouse);
	addMouseMotionListener(myMouse);
     }

    private class MyMouse extends MouseInputAdapter
     {
	int index = -1;
	MyFrame dragged;

	public void mousePressed(MouseEvent e)
	 {
	    index = indexAtLocation(e.getX(), e.getY());
	 }

	public void mouseDragged(MouseEvent e)
	 {
	    if(index == -1)
		return;

	    if(dragged == null)
	     {
		dragged = new MyFrame(getTitleAt(index),
				      getComponentAt(index),
				      getIconAt(index),
				      index,
				      true);
		dragged.setSize(getSize());
		dragged.setUndecorated(true);
	     }

	    Point draggedTo = e.getPoint();
	    SwingUtilities.convertPointToScreen(draggedTo, e.getComponent());
	    draggedTo.x -= 10;
	    draggedTo.y -= 10;

	    dragged.setLocation(draggedTo);

	    if(!dragged.isVisible()) dragged.setVisible(true);
	 }

	public void mouseReleased(MouseEvent e)
	 {
	    if(dragged == null)
		return;

	    setSelectedIndex(0);

	    Point draggedTo = dragged.getLocation();
	    dragged.dispose();
	    dragged = new MyFrame(getTitleAt(index),
				  getComponentAt(index),
				  getIconAt(index),
				  index,
				  false);
	    dragged.setSize(getSize());
	    dragged.setLocation(draggedTo);
	    dragged.setVisible(true);

	    dragged = null;
	    index = -1;
	 }
     }

    private static class ProxyComponent extends Component
     {
	Component comp;
	ProxyComponent(Component comp)
	 {
	    this.comp = comp;
	 }

	public void paint(Graphics g)
	 {
	    comp.paint(g);
	 }

	public Dimension getPreferredSize()
	 {
	    return  comp.getPreferredSize();
	 }
     }

    static BufferedImage createImage(Component comp)
     {
	BufferedImage img = Util.newBufferedImage(comp.getWidth(),
						  comp.getHeight());
	Graphics2D g2 = img.createGraphics();
	g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 0.5f));
	comp.paint(g2);
	g2.dispose();

	return  img;
     }

    private class MyFrame extends JFrame
     {
	JTabbedPane singleton;

	MyFrame(String title, Component comp, Icon icon,
		final int originalIndex, boolean isFake)
	 {
	    super(title);

//	    removeTabAt(originalIndex);
	    if(isFake)
		comp = new JLabel(new ImageIcon(
				      DockableTabbedPane.createImage(comp)));

	    this.singleton = new JTabbedPane();
	    singleton.addTab(title, icon, comp);

	    Container cont = getContentPane();
	    cont.setLayout(new BorderLayout());
	    cont.add(singleton, BorderLayout.CENTER);

	    if(!isFake)
		addWindowListener(
		    new WindowAdapter()
		     {
			public void windowClosing(WindowEvent e)
			 {
			    setVisible(false);
			    insertTab(singleton.    getTitleAt(0),
				      singleton.     getIconAt(0),
				      singleton.getComponentAt(0),
				      null,
				      originalIndex);
			 }
		     }
		    );
	 }
     }
 }
