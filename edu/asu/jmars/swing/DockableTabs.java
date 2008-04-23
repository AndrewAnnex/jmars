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
import java.util.*;
import java.util.List;

public class DockableTabs
 {
    private static final DebugLog log = DebugLog.instance();

    private SwappableTabbedPane mainTabs = new SwappableTabbedPane();
    private List pages = new ArrayList();
    private int frozenTabs = 0;

    public Rectangle[] getDockingStates()
     {
	Rectangle[] locs = new Rectangle[pages.size()];
	for(int i=0; i<pages.size(); i++)
	 {
	    Page p = getPage(i);
	    if(p.undocked != null)
		locs[i] = p.undocked.getBounds();
	 }
	return  locs;
     }

    public void setDockingStates(Rectangle[] locs)
     {
	if(locs.length != pages.size())
	    throw  new IllegalArgumentException("There are " + pages.size() +
						" tabs, not " + locs.length);
	for(int i=0; i<pages.size(); i++)
	 {
	    Page p = getPage(i);
	    if(locs[i] != null)
		undock(i, locs[i]);
	    else
		redock(i);
	 }
     }

    private boolean mainTabsPrepared = false;
    public JTabbedPane getMainTabbedPane()
     {
	if(!mainTabsPrepared)
	 {
	    mainTabsPrepared = true;
	    prepareTabbedPane(mainTabs);
	 }
	return  mainTabs;
     }

    public void activateTab(int idx)
     {
	Page p = getPage(idx);
	if(p.undocked == null)
	    mainTabs.setSelectedComponent(p.comp);
	else
	    p.undocked.toFront();
     }

    public void addTab(String name, Icon icon, Component comp, String tip)
     {
	insertTab(name, icon, comp, tip, pages.size());
     }

    public void insertTab(String name, Icon icon, Component comp, String tip,
			  int idx)
     {
	Page newPage = new Page(comp);
	pages.add(idx, newPage);

	mainTabs.insertTab(name, icon, comp, tip, newMainIndex(idx));

	// Remove the entry if we already had one, since the component
	// is now re-parented.
	for(int i=0; i<pages.size(); i++)
	    if(i != idx  &&  getPage(i).comp == comp)
		pages.remove(i);
     }

    private int newMainIndex(int oldIdx)
     {
	// Determine the index on the main tab, by counting the docked tabs.
	int mainIdx = 0;
	for(int i=0; i<oldIdx; i++)
	    if(getPage(i).undocked == null)
		++mainIdx;
	return  mainIdx;
     }

    private int newMainIndex(Component comp)
     {
	// Determine the index on the main tab, by counting the docked tabs.
	int mainIdx = 0;
	for(int i=0; i<pages.size(); i++)
	 {
	    Page p = getPage(i);
	    if(p.comp == comp)
		return  mainIdx;
	    if(getPage(i).undocked == null)
		++mainIdx;
	 }
	return  mainIdx;
     }

    public void remove(int idx)
     {
	Page p = (Page) pages.remove(idx);
	if(p.undocked == null)
	    mainTabs.remove(p.comp);
	else
	    p.undocked.dispose();
     }

    public void removeAll()
     {
	for(int i=0; i<pages.size(); i++)
	 {
	    TabFrame d = getPage(i).undocked;
	    if(d != null)
	     {
		d.singleton.remove(0);
		d.dispose();
	     }
	 }
	pages.clear();
	mainTabs.removeAll();
     }

    public void setTitleAt(int idx, String title)
     {
	Page p = getPage(idx);
	if(p.undocked == null)
	    mainTabs.setTitleAt(mainTabs.indexOfComponent(p.comp), title);
	else
	 {
	    p.undocked.singleton.setTitleAt(0, title);
	    p.undocked.setTitle(title);
	 }
     }

    public Icon getIconAt(int idx)
     {
	Page p = getPage(idx);
	if(p.undocked == null)
	 {
	    idx = mainTabs.indexOfComponent(p.comp);
	    if(idx == -1)
	     {
		log.aprintln("ODD: icon idx is -1!");
		return  null;
	     }
	    return  mainTabs.getIconAt(idx);
	 }
	else
	    return  p.undocked.singleton.getIconAt(0);
     }

    public void setIconAt(int idx, Icon icon)
     {
	Page p = getPage(idx);
	if(p.undocked == null)
	 {
	    idx = mainTabs.indexOfComponent(p.comp);
	    if(idx == -1)
	     {
		log.aprintln("ODD: icon idx is -1!");
		return;
	     }
	    mainTabs.setIconAt(idx, icon);
	 }
	else
	    p.undocked.singleton.setIconAt(0, icon);
     }

    public void repaintIconAt(int idx)
     {
	Page p = getPage(idx);
	if(p.undocked == null)
	 {
	    idx = mainTabs.indexOfComponent(p.comp);
	    if(idx == -1)
	     {
		log.aprintln("ODD: icon idx is -1!");
		return;
	     }
	    mainTabs.repaint(mainTabs.getBoundsAt(idx));
	 }
	else
	    p.undocked.singleton.repaint(p.undocked.singleton.getBoundsAt(0));
     }

    public void swapTabsAt(int idxA, int idxB)
     {
	// Swap the pages
	Page pageA = getPage(idxA);
	Page pageB = getPage(idxB);
	pages.set(idxA, pageB);
	pages.set(idxB, pageA);

	// Get the main tab indices, in order
	int mainA = mainTabs.indexOfComponent(pageA.comp);
	int mainB = mainTabs.indexOfComponent(pageB.comp);

	// Swap both main tab indices
	if(mainA != -1  &&  mainB != -1)
	    mainTabs.swapTabsAt(mainA, mainB);
	// Swap the one main tab index into where the other would/should be
	else if(mainA != -1  ||  mainB != -1)
	 {
	    int oldMain = mainA!=-1 ? mainA : mainB;
	    int oldIdx  = mainA!=-1 ? mainB : mainA;

	    // This will automatically re-parent/remove from the old
	    // tab index location.
	    mainTabs.insertTab(mainTabs.      getTitleAt(oldMain),
			       mainTabs.       getIconAt(oldMain),
			       mainTabs.  getComponentAt(oldMain),
			       mainTabs.getToolTipTextAt(oldMain),
			       newMainIndex(oldIdx));
	 }
     }

    public void moveTab(int idxSrc, int idxDst)
     {
	// Move the page
	Page pageSrc = getPage(idxSrc);
	Page pageDst = getPage(idxDst);
	pages.remove(idxSrc);
	pages.add(idxDst, pageSrc);

	// If the tab is docked right now, then move it. If it's
	// undocked, then we don't have to do anything at all!
	if(pageSrc.undocked == null)
	 {
	    int mainSrc = mainTabs.indexOfComponent(pageSrc.comp);
	    int mainDst = newMainIndex(idxDst);

	    mainTabs.moveTab(mainSrc, mainDst);
	 }
     }

    public int indexAtLocation(MouseEvent e)
     {
	JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
	int tabIdx = tabbedPane.indexAtLocation(e.getX(),e.getY());
	if(tabIdx < 0)
	    return  -1;

	Component tabComp = tabbedPane.getComponentAt(tabIdx);
	return  indexOfComponent(tabComp);
     }

    public int indexOfComponent(Component c)
     {
	for(int i=0; i<pages.size(); i++)
	    if(getPage(i).comp == c)
		return  i;
	return  -1;
     }

    private Page getPage(int idx)
     {
	return  (Page) pages.get(idx);
     }

    private Page getPage(Component comp)
     {
	for(int i=0; i<pages.size(); i++)
	 {
	    Page p = (Page) pages.get(i);
	    if(p.comp == comp)
		return  p;
	 }
	return  null;
     }

    public DockableTabs()
     {
	MouseInputListener myMouse = new MyMouse();
	mainTabs.addMouseListener(myMouse);
	mainTabs.addMouseMotionListener(myMouse);
     }

    public DockableTabs(int frozenTabs)
     {
	this();
	if(frozenTabs < 0)
	    throw  new IllegalArgumentException(
		"You can't freeze a negative number of tabs!");
	this.frozenTabs = frozenTabs;
     }

    /**
     ** Client can override if (for example) a mouse listener must be
     ** attached to every tabbed pane we create. Default
     ** implementation does nothing.
     **/
    protected void prepareTabbedPane(JTabbedPane pane)
     {
     }

    public void undock(int idx, Rectangle where)
     {
	Page p = getPage(idx);
	if(where == null)
	 {
	    where = new Rectangle();
	    where.setSize(p.comp.getSize());
	    where.setLocation(p.comp.getLocationOnScreen());
	 }

	if(p.undocked == null)
	 {
	    int mainIdx = mainTabs.indexOfComponent(p.comp);
	    p.undocked = new TabFrame(mainIdx, false);
	    p.undocked.setBounds(where);
	    p.undocked.setVisible(true);
	 }
	else
	    p.undocked.setBounds(where);
     }

    public void redock(int idx)
     {
	Page p = getPage(idx);
	if(p.undocked != null)
	    p.undocked.redock();
     }

    private class MyMouse extends MouseInputAdapter
     {
	int index = -1;
	TabFrame dragged;
	Point dragOffset;

	public void mousePressed(MouseEvent e)
	 {
	    index = mainTabs.indexAtLocation(e.getX(), e.getY());
	    if(index < frozenTabs)
		index = -1;

	    // If we hit a tab AND it wasn't one of the frozen ones
	    if(index != -1)
	     {
		dragOffset = e.getPoint();
		Rectangle tabBounds = mainTabs.getBoundsAt(index);
		dragOffset.y -= tabBounds.y;
	     }
	 }

	public void mouseDragged(MouseEvent e)
	 {
	    if(index == -1)
		return;

	    if(dragged == null)
		dragged = new TabFrame(index, true);

	    setDraggedLocationFor(dragged, e);
	 }

	public void mouseReleased(MouseEvent e)
	 {
	    if(dragged == null)
		return;

	    mainTabs.setSelectedIndex(0);

	    TabFrame released = new TabFrame(index, false);
	    setDraggedLocationFor(released, e);

	    dragged.dispose();
	    dragged = null;
	    index = -1;
	 }

	void setDraggedLocationFor(TabFrame what, MouseEvent e)
	 {
	    Insets insets = what.getInsets();
	    Point draggedTo = e.getPoint();
	    SwingUtilities.convertPointToScreen(draggedTo, e.getComponent());
	    draggedTo.x -= dragOffset.x + insets.left;
	    draggedTo.y -= dragOffset.y + insets.top;
	    if(!what.isVisible()  &&  !what.isFake)
		what.setVisible(true);
	    what.setLocation(draggedTo);
	    if(!what.isVisible())
		what.setVisible(true);
	 }
     }

    private class TabFrame extends JFrame
     {
	JTabbedPane singleton = new JTabbedPane();
	boolean isFake;

	TabFrame(int mainIdx, boolean isFake)
	 {
	    super(mainTabs.getTitleAt(mainIdx) +
		  (isFake ? " " : ""));

	    this.isFake = isFake;

	    setUndecorated(isFake);
	    setFocusableWindowState(!isFake);

	    Component comp = mainTabs.  getComponentAt(mainIdx);
	    Dimension originalSize = comp.getSize();
	    if(isFake)
		comp = new JLabel(new ImageIcon(Util.createImage(comp, 0.3f)));

	    singleton.addTab(mainTabs.      getTitleAt(mainIdx),
			     mainTabs.       getIconAt(mainIdx),
			     comp,
			     mainTabs.getToolTipTextAt(mainIdx));

	    Container cont = getContentPane();
	    cont.setLayout(new BorderLayout());
	    cont.add(singleton, BorderLayout.CENTER);

	    if(isFake)
	     {
		singleton.setEnabledAt(0, false);
		singleton.setDisabledIconAt(0, singleton.getIconAt(0));
	     }
	    else
	     {
		getPage(comp).undocked = this;

		addWindowListener(
		    new WindowAdapter()
		     {
			public void windowClosing(WindowEvent e)
			 {
			    redock();
			 }
		     }
		    );
		prepareTabbedPane(singleton);
	     }

	    pack();
	    setSize(originalSize.width  +  getWidth() - comp. getWidth(),
		    originalSize.height + getHeight() - comp.getHeight());
	    comp.requestFocusInWindow();
	 }

	void redock()
	 {
	    setVisible(false);
	    String   title = singleton.      getTitleAt(0);
	    Icon      icon = singleton.       getIconAt(0);
	    Component comp = singleton.  getComponentAt(0);
	    String     tip = singleton.getToolTipTextAt(0);

	    singleton.remove(0);
	    int oldIdx = mainTabs.getSelectedIndex();
	    int newIdx = newMainIndex(comp);
	    if(oldIdx >= newIdx)
		++oldIdx;
	    mainTabs.insertTab(title, icon, comp, tip, newIdx);
	    mainTabs.setSelectedIndex(oldIdx);
	    getPage(comp).undocked = null;
	 }
     }

    private static class Page
     {
	Component comp;
	TabFrame undocked;

	Page(Component comp)
	 {
	    this.comp = comp;
	 }
     }
 }
