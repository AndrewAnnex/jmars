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


package edu.asu.jmars.layer;

import edu.asu.jmars.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.util.*;


/**
** The LManager is a JFrame GUI that provides the user with an interface
** to manipulate the position and visibility of existing layer views.
** The functionality of this interface will increase to include:
** Addition and deletion of layerviews
** Other??
**
** The LManager is a JFrame which contains a set of LPanels.
** LPanels are derived from JPanels and contain a set of other
** J-components (ie JButton, JLabels, JRadioButton) which
** provide the "interactivity" for the user to manipulate the
** the layerview.  The components and their functions are subject
** to change as funtional needs become defined
*/


public class LManager	extends JFrame implements LViewFactory.Callback
 {
    /**
     ** TEMPORARY HACK (MCW 9/1/2005) to aid in refactoring efforts.
     **/
    protected LManager(String title, GraphicsConfiguration gc)
     {
	super(title, gc);
     }

  static    private int		    idGenerator;
	    private ArrayList	    panels; //Array if LPanels
	    private JPanel	    backBoard;
	    private LViewManager    allViews; //Contains the list of LViews
	    private int		    length;

	    private JScrollPane	    scroll;
	    private DockableTabs    tb;
	    private JPanel	    panel;
	    private JPanel	    mainPanel;
	    private JButton	    add;
	    private JButton	    del;
	    private AddDialog	    addDialog;
	    private int		    selectedItem=0;

	    private JMenu	    layersMenu;
	    private JMenu	    tooltipsMenu;

	    private	    static	    DebugLog log = DebugLog.instance();


    /**
     ** Removes a view programmatically, from an external action.
     **/
    public void removeView(Layer.LView oldView)
     {
	int len= allViews.viewList.size();
	if (len < 1) return;

	int idx = allViews.viewList.indexOf(oldView);
	if(idx == -1) return;
	int selectedItem = length - idx - 1;

	   panels.remove(selectedItem);
	   allViews.viewList.remove(length-selectedItem-1);
	   tb.remove(selectedItem+1);

	   len--;
	   if (len == 0)
	       selectedItem=-1;
	   else if (selectedItem > 0)
	       selectedItem--;

	   if (selectedItem >= 0) {
	      LPanel tmp=(LPanel)panels.get(selectedItem);
	      tmp.swapColors(tmp);
	   } else {
	      selectedItem = -1;
	   }

	   rebuildMainTab();
	   rebuildMenus();
	   allViews.repaint();
	   allViews.childVMan.repaint();
	   tb.activateTab(0);
     }

    /**
     ** Returns the main view at a given index in the list.
     **/
    private Layer.LView getView(int idx)
     {
	return  (Layer.LView) allViews.viewList.get(idx);
     }

    /**
     ** Returns a string representing the docking states of the
     ** LManager tabs, which can be fed to {@link #setDockingStates}.
     **/
    public String getDockingStates()
     {
	Rectangle[] states = tb.getDockingStates();

	Properties prop = new Properties();
	prop.setProperty("lmanagerVersion", "1");
	prop.setProperty("viewCount",
			 Integer.toString(allViews.viewList.size()));
	for(int i=0; i<allViews.viewList.size(); i++)
	 {
	    Layer.LView view = getView(i);
	    prop.setProperty("view" + i + ".title", view.getName());
	    prop.setProperty("view" + i + ".type", view.getClass().getName());
	    if(states[i+1] != null)
		prop.setProperty("view" + i + ".bounds",
				 states[i+1].x + "," +
				 states[i+1].y + "," +
				 states[i+1].width + "," +
				 states[i+1].height);
	 }

	ByteArrayOutputStream out = new ByteArrayOutputStream();
	try
	 {
	    prop.store(out, null);
	 }
	catch(IOException e)
	 {
	    log.aprintln(e);
	    throw  new RuntimeException(
		"IMPOSSIBLE: I/O error writing to a string!", e);
	 }

	String stateString = out.toString();

	log.println("--- Saving as:");
	log.println(stateString);

	return  stateString;
     }

    /**
     ** Restores the lmanager tabs' docking states to those stored in
     ** a string previously created from {@link #getDockingStates}.
     **/
    public void setDockingStates(String stateString)
     {
	log.println("--- Restoring from:");
	log.println(stateString);

	if(stateString == null)
	    return;

	Properties prop = new Properties();
	try
	 {
	    prop.load(new ByteArrayInputStream(stateString.getBytes()));
	 }
	catch(Exception e)
	 {
	    log.aprintln(e);
	    log.aprintln("Failed to set layer manager tab positions");
	    return;
	 }

	Rectangle[] states = new Rectangle[allViews.viewList.size() + 1];

	log.println("Opening version " + prop.getProperty("lmanagerVersion"));
	int viewCount = Integer.parseInt(prop.getProperty("viewCount"));
	for(int i=1, viewIdx=0;
	    i<states.length && viewIdx<allViews.viewList.size();
	    i++, viewIdx++)
	 {
	    if(viewCount != allViews.viewList.size())
		while(titleAndTypeMismatch(viewIdx, prop))
		    if(viewIdx < viewCount)
		     {
			log.println("Skipped saved tab " + viewIdx);
			++viewIdx;
		     }
		    else
		     {
			log.println("Skipped past the end!");
			return;
		     }

	    String boundStr = prop.getProperty("view" + viewIdx + ".bounds");
	    if(boundStr != null)
	     {
		String[] boundA = boundStr.split(",");
		states[i] = new Rectangle(Integer.parseInt(boundA[0]),
					  Integer.parseInt(boundA[1]),
					  Integer.parseInt(boundA[2]),
					  Integer.parseInt(boundA[3]));
	     }
	 }

	tb.setDockingStates(states);
     }

    private boolean titleAndTypeMismatch(int viewIdx, Properties prop)
     {
	String key = "view" + viewIdx + ".";
	Layer.LView v = getView(viewIdx);
	return  !v           .getName().equals(prop.getProperty(key + "title"))
	    ||  !v.getClass().getName().equals(prop.getProperty(key + "type"));
     }

    /**
     ** Whenever the list of layers changes in any way, the layers
     ** menu (and its associated accelerator keys) must be rebuilt, as
     ** well as the tooltips menu.
     **/
    private void rebuildMenus()
     {
	// Rebuild the base of the layers menu
	if(layersMenu == null)
	    layersMenu = new JMenu("Layer Manager Tabs");
	else
	    layersMenu.removeAll();

	layersMenu.add(
	    new JMenuItem(
		new AbstractAction("Dock all tabs")
		 {
		    public void actionPerformed(ActionEvent e)
		     {
			buildTabs();
		     }
		 }
		));

	layersMenu.add(new JSeparator());

	layersMenu.add(
	    new JMenuItem(
		new AbstractAction("Show Main Tab")
		 {
		    public void actionPerformed(ActionEvent e)
		     {
			tb.activateTab(0);
		     }
		 }
		)
	     {{
		setAccelerator(KeyStroke.getKeyStroke("F1"));
	     }}
	    );

	// Rebuild the base of the tooltips menu
	if(tooltipsMenu == null)
	    tooltipsMenu = new JMenu("Tool Tips");
	else
	    tooltipsMenu.removeAll();
	tooltipsMenu.add(
	    new JCheckBoxMenuItem("Disable ALL tooltips",
				  !ToolTipManager.sharedInstance().isEnabled())
	     {{
		addActionListener(
		    new ActionListener()
		     {
			public void actionPerformed(ActionEvent e)
			 {
			    ToolTipManager.sharedInstance().setEnabled(
				!isSelected());
			    for(int i=2; i<tooltipsMenu.getItemCount(); i++)
				tooltipsMenu.getItem(i).setEnabled(
				    !isSelected());
			 }
		     }
		    );
	     }}
	    );
	tooltipsMenu.add(new JSeparator());

	for(int i=0; i<Math.min(length,11); i++)
	 {
	    final int ii = i;
	    final Layer.LView view =
		(Layer.LView) allViews.viewList.get(length-i-1);

	    layersMenu.add(
		new JMenuItem(
		    new AbstractAction("Activate " + view.getName())
		     {
			public void actionPerformed(ActionEvent e)
			 {
			    tb.activateTab(ii + 1);
			    setActiveLView(ii);
			 }
		     }
		    )
		 {{
		    setAccelerator(KeyStroke.getKeyStroke("F" + (ii + 2)));
		 }}
		);
	    tooltipsMenu.add(
		new JCheckBoxMenuItem("Disable for " + view.getName(),
				      view.tooltipsDisabled())
		 {{
		    addActionListener(
			new ActionListener()
			 {
			    public void actionPerformed(ActionEvent e)
			     {
				view.tooltipsDisabled(isSelected());
			     }
			 }
			);
		 }}
		);

	 }
     }

    /**
     ** Every LManager has associated with it a menu that is
     ** dynamically updated to reflect the tabs it displays. The
     ** menu's items also have associated accelerator keys.
     **/
    public JMenu getLayersMenu()
     {
	return  layersMenu;
     }

    /**
     ** Every LManager has associated with it a menu that is
     ** dynamically updated to reflect the tooltip-display options of
     ** the views it manages.
     **/
    public JMenu getTooltipsMenu()
     {
	return  tooltipsMenu;
     }

    /**
     ** Sets the active view. Note: index references the views ordered
     ** top to bottom (as seen on screen in the lmanager).
     **/
    public void setActiveLView(int index)
     {
	// ROUGHLY copied from the mouse handlers in LPanel... hope this works!
	if(selectedItem != index)
	 {
	    LPanel tmp = (LPanel) panels.get(selectedItem);
	    tmp.swapColors(tmp);
	    tmp = (LPanel) panels.get(index);
	    tmp.swapColors(tmp);
	    selectedItem = index;

	    // when the active view changes, the cursor should be set back to the default.  (JRW 6/05)
	    //Layer.LView view = getActiveLView();
	    getActiveLView().setCursor(  new Cursor( Cursor.DEFAULT_CURSOR));

	 }
     }

    public Layer.LView getActiveLView()
     {
	try {
	   if (length-selectedItem-1 >= 0)
	       return  (Layer.LView) allViews.viewList.get(length-selectedItem-1);
	}
	catch (IndexOutOfBoundsException e) {}

	return null;
     }

/**
 ** <p>Constructor:
 ** gets the array length, and creates the panels.
 ** Gets the size (height) of panels which is necessary for
 ** doing the interative swapping of position
 **/
    public	LManager(final LViewManager allViews)
     {
	super("Layer Manager", Util.getAltDisplay());

	this.allViews=allViews;
	allViews.lmanager = this; // kludge
	length= allViews.viewList.size();
	panels = new ArrayList();
	tb=new DockableTabs(1) // 1 -> freeze the main tab
	 {
	    protected void prepareTabbedPane(JTabbedPane pane)
	     {
		// Cause double-clicks on a tab to set the active layer
		pane.addMouseListener(
		    new MouseAdapter()
		     {
			public void mouseClicked(MouseEvent e)
			 {
			    if(e.getClickCount() != 2)
				return;

			    int tab = tb.indexAtLocation(e);

			    if(tab == 0) // double-clicking main causes rebuild
				buildTabs();
			    else if(tab > 0)
				setActiveLView(tab - 1);
			 }
		     }
		    );
	     }
	 };
	mainPanel=new JPanel();
	mainPanel.setLayout(new BorderLayout());

	JPanel	grouper=new JPanel();
	add=new JButton("Add");
	del=new JButton("Del");

	grouper.add(add);
	grouper.add(del);
	mainPanel.add(grouper, BorderLayout.NORTH);
	log.println("Building the AddDialog");

	del.addActionListener(new ActionListener(){
		public void actionPerformed(ActionEvent e) {
			removeView((Layer.LView)
					allViews.viewList.get(length - selectedItem - 1));
		}
	});

	add.addActionListener(new ActionListener(){
		public void actionPerformed(ActionEvent e) {
			log.println("You have selected to ADD...creating the AddDialog");
			addDialog=new AddDialog(LManager.this,true);
			log.println("You have selected item "+addDialog.getSelectionIndex());

			if (addDialog.getSelectionIndex() != -1) {
				LViewFactory factory =
					(LViewFactory) LViewFactory.factoryList.get(
							addDialog.getSelectionIndex());

				log.println("This results in Factory: "+factory.getName());

				factory.createLView(LManager.this);
			}
		}
	});

	addWindowListener(new WindowAdapter(){
	   public void windowClosing(WindowEvent e){
	      setVisible(false);
	      Main.sayGoodbyeToLManager();
	   }
	 });

	log.println("You should only ever see this ONCE!!!");
	init();

	rebuildMenus();
	pack();
	setVisible(true);

     }

    // Implements LViewManager.Callback
    //THIS is where factory produced LView is inserted into LayerPane
    public void receiveNewLView(Layer.LView newLView)
     {
	if (newLView == null) //for whatever reason...no new view
	    return;
	    
	FocusPanel fp;
	LPanel tmp;
	if (selectedItem != -1 && panels.size() > 0 ) {
	   tmp=(LPanel)panels.get(selectedItem); //First, change the selected item to unselected (before we shift things!)
	   tmp.swapColors(tmp);
	}
        
	allViews.viewList.add(newLView);
	log.println("The size of the viewList is now: " + allViews.viewList.size());
	newLView.setVisible(newLView.mainStartEnabled(), 
			    newLView.pannerStartEnabled());
        
	log.println("Your new View (#"+idGenerator+") is: "+newLView);
	panels.add(0, 
		   new LPanel((idGenerator++), 
			      newLView, 
			      newLView.mainStartEnabled(), 
			      newLView.pannerStartEnabled()) );
	    
	selectedItem=0; //Newly added items are technically index 0,
	//so now it is the currently selected object
	tmp=(LPanel)panels.get(selectedItem); //Last, change the new item to have a selected boarder
	tmp.swapColors(tmp);

	tb.insertTab(newLView.getName(),
		     newLView.light2,
		     fp = newLView.getFocusPanel(),
		     null,
		     1); // always just after the main tab (0)
	pack();
	rebuildMainTab();
	rebuildMenus();
     }

    /**
     ** Does nothing... only used in LManager2.
     **/
    public void refreshAddMenu()
     {
     }

/**
 ** <p> Internal initialization routine.
 ** Each panels is created and added into the JFrame of this object
 */
    private void init()
     {
	int i;
	LPanel	p;
	FocusPanel fp;

	length= allViews.viewList.size();

	idGenerator=0;
	scroll=new JScrollPane();
	panel=new JPanel();
	scroll.setViewportView(panel);

	panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
	mainPanel.add(scroll, BorderLayout.CENTER);

	idGenerator=length-1;
	for(i=0; i<length; i++)
	 {
            Layer.LView view = (Layer.LView) allViews.viewList.get(length-i-1);
            if (view != null)
	     {
                view.setVisible(view.mainStartEnabled(), 
                                view.pannerStartEnabled());
		p = new LPanel(idGenerator, view, view.mainStartEnabled(), view.pannerStartEnabled());
		panels.add(p);
		idGenerator--;
	     }
            else
                log.aprintln("null view reference at position: " + i);
	 }

	rebuildMainTab();
	buildTabs();

	//Get the selected item and swaps it's border colors (thus "selecting" it)
	LPanel tmp=(LPanel)panels.get(selectedItem);
	tmp.swapColors(tmp);

	getContentPane().add(tb.getMainTabbedPane());
	idGenerator=length;
     }


/**
 ** <p> Current solution which may change.
 ** When two panels switch places (from user interaction...not randoallViews.viewListy ;)
 ** the entire set of panels needs to be removed and re-added in their
 ** new order.  This is due to the Layout object being used.  A custom
 ** Layout object could allow for a simple swap to happen in place
 ** with out the need to re-build everything. Until, we have this.
 */
    private void buildTabs()
     {
	length = allViews.viewList.size();
//		tb.activateTab(-1);
	tb.removeAll();

	tb.addTab("Main", null, mainPanel, null);
	for(int i=0; i<length; i++)
	 {
	    Layer.LView view = (Layer.LView) allViews.viewList.get(length-i-1);
	    try
	     {
		tb.addTab(view.getName(), view.light2, view.getFocusPanel(),
			  null);
	     }
	    catch(Exception e)
	     {
		log.aprintln(e);
		log.aprintln(e.getMessage()); // For emphasis
		view.getLayer().setStatus(Color.black);
		JOptionPane.showMessageDialog(
		    this,
		    "'" + view.getName() + "' failed to load correctly.\n" +
		    "\n" +
		    "You should delete the layer, it's probably broken. More\n"  +
		    "information may available be on your command-line.",
		    "ERROR DURING LAYER CREATION",
		    JOptionPane.ERROR_MESSAGE);
		tb.insertTab("[ERROR] " + view.getName(), null,
			     new FocusPanel(view), e.toString(), -1);
	     }
	 }
	tb.activateTab(0);
     }

    private void rebuildMainTab()
     {
	length = allViews.viewList.size();
	panel.removeAll();

	for(int i=0; i<length; i++)
	 {
	    LPanel lp = (LPanel) panels.get(i);
	    lp.requeryName();
	    panel.add(lp);
	    Layer.LView view = (Layer.LView) allViews.viewList.get(length-i-1);
	 }

	mainPanel.validate();
	mainPanel.setVisible(true);
     }

    /**
     ** Causes the LManager to notice any changes in the layer names
     ** (i.e. the value of LView.getName()), and to propagate those
     ** changes to the tabs, the view list, and the menus.
     **/
    public void updateLabels()
     {
	for(int i=0; i<panels.size(); i++)
	 {
	    LPanel lpan = (LPanel) panels.get(i);
	    lpan.st.setText(lpan.myLView.getName());
	    lpan.st.setToolTipText(lpan.myLView.getName());
	    tb.setTitleAt(i+1, lpan.myLView.getName());
	 }
	rebuildMenus();
     }

    public void updateStatusOf(Layer.LView view, Icon icon)
     {
	int viewIdx = allViews.viewList.indexOf(view);
	int tabIdx = allViews.viewList.size() - viewIdx;

	if(tabIdx == -1  ||  viewIdx == -1)
	    return;

	if(tb.getIconAt(tabIdx) != icon)
	    tb.setIconAt(tabIdx, icon);
	else
	    tb.repaintIconAt(tabIdx);
     }

/**
 ** <p> The LPanel class
 */

    private class LPanel extends JPanel
     {
	protected	JLabel			st;
	protected	JSlider			sl;
	protected   JToggleButton   mVisible;
	protected   JToggleButton   pVisible;
	protected	int				ID;
	protected	Layer.LView		myLView;
	private	final int			STEP=10;
	public		Border			border_1;
	public		Border			border_2;

	protected void swapPanels(int ti, int si)
	 {
	    length= allViews.viewList.size();

	    allViews.viewList.move((length-si-1),(length-ti-1));
	    panels.add(ti, panels.remove(si));

	    rebuildMainTab();
	    rebuildMenus();
	    tb.moveTab(si+1, ti+1);
	 }
/**
 ** <p> When a particular panel is selected (ie either receied the mousepressed
 ** or the user has dragged the mouse over this panel, its fore and back grounds
 ** are swapped to give the user some feedback...ie this is the panel of focus
 ** at the moment.  This particular "trick" will most likely change and we'll use
 ** a different method to indicate focus
 */

	public void swapColors(LPanel source)
	 {
	    log.println("Swap Color called for Panel #"+source.ID);
	    log.println("Swap Color called from:");
	    log.printStack(1);
	    Border tmp=source.border_1;
	    source.border_1=source.border_2;
	    source.border_2=tmp;
	    source.setBorder(source.border_1);
	 }

/**
 ** <p> Mouse Addapter which tracks 3 mouse functions:
 ** 1) Mouse Press
 ** 2) Mouse Drag
 ** 3) Mouse Release
 **
 ** <p> Mouse Press: calculates which panel the mouse is
 ** is currently over, the size of the panel (which will be the size
 ** of ALL the panels and swaps that panel's fore and background colors
 **
 ** <p> Mouse Drag: calculates which panel the mouse is over,
 ** if the mouse has chaged panels, swap the colors of the old
 ** panel back, swap the colors of the new panel.
 **
 ** <p> Mouse Release: calculates which panel, swap colors
 ** AND if the original panel (the one the mouse was over
 ** when the button was pressed) is different from the current
 ** panel then swap their positions in the necessary arrays
 */

	private class MyAdapter extends MouseInputAdapter {

	   protected	int		lastY;
	   protected	boolean	first=false;
	   protected	LPanel	currentObj;
	   protected	LPanel	originalObj;
	   protected	int		height;
	   protected	int		originalIdx;



	   public void mouseClicked(MouseEvent e)
	    {
	       height=e.getComponent().getHeight();
	       int idx =
		   (e.getY()+(int)e.getComponent().getLocation().getY()) / (height);
	       if (idx < 0)
		   idx = 0;
	       else if (idx >= panels.size())
		   idx=(length-1);

	       log.println("Found index to switch tab to: "+idx);

	       if(e.getClickCount() == 2)
		   tb.activateTab(idx+1);
	    }

	   public void mousePressed(MouseEvent e)
	    {
	       LPanel	source=(LPanel)e.getSource();

	       int length=panels.size();
	       height=source.getHeight();
	       originalObj=currentObj=source;
	       originalIdx=((e.getY()+(int)originalObj.getLocation().getY())/(height));
	       log.println("MousePressed " + e.getX() +
			   "," + e.getY() + " " + originalIdx);
	       if (originalIdx < 0)
		   originalIdx = 0;
	       else if (originalIdx >= length)
		   originalIdx=(length-1);

	       // This code is a duplication of setActiveLView and has been masked out. JRW 6/05
	       /*
		 if (selectedItem!=originalIdx){
		 LPanel tmp=(LPanel)panels.get(selectedItem);
		 tmp.swapColors(tmp);
		 tmp=(LPanel)panels.get(originalIdx);
		 tmp.swapColors(tmp);
		 selectedItem=originalIdx;
		 }
	       */
	       setActiveLView( originalIdx);

	    }

	   public void mouseReleased(MouseEvent e)
	    {
	       int 		index;
	       LPanel	source;
	       LPanel	tmp;
	       int 		locY;

	       int length=panels.size();

	       locY=e.getY()+(int)originalObj.getLocation().getY();
	       index=(int)(locY/(height));

	       if (index < 0)
		   index = 0;
	       else if (index >= length)
		   index=(length-1);


	       if (index==originalIdx)
		   return;

	       if (index!=selectedItem) {
		  tmp=(LPanel)panels.get(selectedItem);
		  tmp.swapColors(tmp);
		  log.println("ALERT: index="+index+" SelectedItem="+selectedItem);
	       }

	       else {

		  tmp=(LPanel)panels.get(index);
		  tmp.swapColors(tmp);
	       }

	       swapPanels(index,originalIdx);
	       selectedItem=index;
	       tmp=(LPanel)panels.get(selectedItem);
	       tmp.swapColors(tmp);

	    }

	   public void mouseDragged(MouseEvent e)
	    {
	       int 		index;
	       LPanel	source;
	       int 		locY;

	       locY=e.getY()+(int)originalObj.getLocation().getY();
	       index=(int)(locY/(height));

	       if (index < 0)
		   index = 0;
	       else if (index >= length)
		   index=(length-1);

	       source=(LPanel)panels.get(index);

	       if(source==currentObj) {
		  return;
	       }

	       swapColors(currentObj);
	       currentObj=source;
	       selectedItem=index;
	       swapColors(currentObj);
	    }
	 }
/**
 ** <p> This function puts all the necessary components into
 ** the LPanel object (ie the button, radio-button, and label).
 */
	protected void buildPanel()
	 {
	    border_1=new LineBorder(new Color(0.0f,0.0f,0.0f,0.0f),4);
	    border_2=new LineBorder(Color.blue,4);
	    setBorder(border_1);
	    add(Box.createVerticalStrut(5));
	    add(myLView.light);
	    add(Box.createVerticalStrut(5));

	    add(mVisible);
	    add(pVisible);
	    add(Box.createVerticalStrut(5));

	    // st.setOpaque(false);
	    add(st);
	    // add(Box.createVerticalStrut(5));
	    // setSize(20,10);
	    // sl.setOpaque(false);
	    add(sl);
	    add(Box.createVerticalStrut(5));

	    add(Box.createHorizontalGlue());

	    ActionListener visibilityListener =
		new ActionListener ()
		 {
		    public void actionPerformed(ActionEvent e)
		     {
			myLView.setVisible(mVisible.isSelected(),
					   pVisible.isSelected());
		     }
		 };
	    mVisible.addActionListener(visibilityListener);
	    pVisible.addActionListener(visibilityListener);

	    sl.addChangeListener(new ChangeListener()
	     {
		public void stateChanged(ChangeEvent e)  {
		   if (!(sl.getValueIsAdjusting())){
		      myLView.setAlpha((float)sl.getValue()/100.0f);
		   }
		}
	     }
		);

	 }


/**
 ** <p> LPanel constructor
 */

        public LPanel (int id, Layer.LView lv)
	 {
            this(id, lv, true, true);
	 }
        
	public LPanel (int id, Layer.LView lv, boolean mainEnabled, boolean pannerEnabled)
	 {
	    mVisible = new JToggleButton("M", mainEnabled);
	    mVisible.setMargin(new Insets(0,0,0,0));
		    
	    pVisible = new JToggleButton("P", pannerEnabled);
	    pVisible.setMargin(new Insets(0,0,0,0));
	    pVisible.setPreferredSize(mVisible.getPreferredSize());
		    
	    st=new JLabel(lv.getName());
		    
	    this.setToolTipText(lv.getName());
		    
	    st.setPreferredSize(new Dimension(100,10));
	    sl=new JSlider(0,100,(int) (lv.getAlpha()*100.0f));
	    ID=id;
		    
	    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
	    myLView = lv;
	    buildPanel();
	    MyAdapter	foo=new MyAdapter();
	    addMouseListener(foo);
	    addMouseMotionListener(foo);
	 }
        
	public void requeryName()
	 {
	    st.setText(myLView.getName());
	 }
     }
 }
