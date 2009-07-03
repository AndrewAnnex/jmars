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


/**
 **  A class for managing and maintaining rulers for all ruler-enabled layers in 
 **  JMARS.  This starts up when JMARS is started. In fact, the viewing window
 **  becomes the top ruler with a zero-height dragbar.  Rulers are added and
 **  removed from the list of rulers but the Ruler Manager is never shut down
 **  while JMARS is running.
 **  
 **
 **  @author James Winburn    10/03    MSFF_ASU
 **
 **/
package edu.asu.jmars.ruler;

// generic java imports
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.geom.*;

// JMARS specific imports
import edu.asu.jmars.util.*;
import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;


public class RulerManager 
    extends MultiSplitPane
{
    private static final DebugLog log = DebugLog.instance();

    // The list of rulers to be displayed.
    public ArrayList rulerList;

    // When JMARS first starts, the main window becomes the top ruler
    // in the RulerManager.  If this is not done, then no ruler may be 
    // added.  This flag stores whether it was done.
    static boolean isContentSet = false;

    // Properties dialog stuff for all rulers. 
    //static private JPanel         propertiesPanel;
    static public  JDialog        propertiesDialog;
    static public  JTabbedPane    propertiesTabbedPane;
    static public  JPanel         hidingPanel;


		// set up a location listener so that the rulers will be updated even if
		// the layer is turned off.
		public void setLViewManager( LViewManager lvman){
				lvman.getLocationManager().addLocationListener( new LocationListener() {
						public void setLocationAndZoom( Point2D loc, int zoom){
								notifyRulerOfViewChange();
						}
				});
		}

    // fields common to all rulers.
    public RulerProperty backgroundColor      = new RulerProperty( 
				"backgroundColor",     
				new RulerColorButton( "Background Color",  new Color(  0,0,0)));
	static public RulerProperty relFontSize = new RulerProperty(
			"relFontSize", new RulerStepSlider(-2,8,1,1));

    // The list of settings.  All rulers may have their own rulerSettings field.
    // This is public because Main.userProps.saveUserObject() looks for it.
    public Hashtable                 rulerSettings;

    // the singleton instance.  All methods needing the RulerManager should call
    // RulerManager.Instance  (eg.  RulerManager.Instance.foo() ).
    public final static RulerManager Instance  = new RulerManager();


    // the constructor.  Because there can be only one, the constructor is private.
    private RulerManager() 
    {
	super();
	setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
		
	rulerList     = new ArrayList();
	rulerSettings = new Hashtable();

	setUpPropertiesDialog();
    }
	

    // Makes the main view the top ruler.  This is done in Layer.java. If it
    // is NOT done, no ruler may be added to the layer.
    public Component setContent( JComponent component, JFrame frame)
    {
	Component comp = super.setContent( component, frame);
	if (comp != null){
	    isContentSet = true;
	}
	return comp;
    }
	

    // Adds a ruler to the Manager.  If a properties panel exists for the 
    // ruler, it is added as a tabbed pane to the properties dialog.
    public void addRuler( BaseRuler rp)
    {
	if (isContentSet==false){
	    return;
	}

	super.addComponent( rp);
	rulerList.add( rp);

	if (rp.isHidden()){
	    hideComponent( rp);
	}
		
	// if the title dragbar is hidden, unhide it.
	DragBar dbar = (DragBar)super.getComponent(1);
	if (dbar.isHidden()){
	    dbar.setHidden(false);
	}

	updatePropertiesTabbedPane();
	updateHidingPanel();
	packFrame();
    }


    // Removes a ruler from the Manager. If the ruler had a properties panel,
    // it too is removed.  
    public void removeRuler( BaseRuler rp)
    {
	super.removeComponent( rp);
	rulerList.remove( rp);

	// If there are no more rulers, hide the title dragbar.
	if (rulerList.isEmpty() ){
	    DragBar dbar = (DragBar)super.getComponent(1);
	    dbar.setHidden(true);
	}

	updatePropertiesTabbedPane();
	updateHidingPanel();
	packFrame();
    }

    // Get the ruler from ruler list based on the name of the ruler.
    // This is used in TestDriverLayered during the load ruler settings 
    // phase.
    public BaseRuler getRuler( String rulerName){
	for (int i = 0; i < rulerList.size(); i++){
	    BaseRuler ruler = (BaseRuler)rulerList.get(i);
	    if (ruler.getClass().getName().equals( rulerName)){
		return ruler;
	    }
	}
	return null;
    }
	
			
    // Accessed by other classes to inform the RulerManager that the rulers 
    // need to be updated.
    //
    // TODO: This has the side effect of also redrawing TestDriverLayered, which is 
    //       unnecessary work in most cases - unfortunately MRO relies on it, so care must
    //       be taken in fixing this.
    public void notifyRulerOfViewChange() {
	repaint();
    }


    // Loads up general ruler properties from the rulerSettings hashtable, which is assumed
    // to have been set up with the settings before calling this method.
    public void loadSettings(Hashtable rulerSettings)
    {
	if (rulerSettings == null){
	    return;
	}
	backgroundColor.loadSettings( rulerSettings);
	relFontSize.loadSettings(rulerSettings);
    }

    // Saves general ruler properties to the rulerSettings hashtable.  Methods that
    // call this will presumably access the settings afterwards.
    public Hashtable saveSettings(){
	Hashtable rulerSettings = new Hashtable();
	backgroundColor.saveSettings( rulerSettings);
	relFontSize.saveSettings(rulerSettings);
	return rulerSettings;
    }

	


    /*-----------------------------------------------------------
     * The following methods manage the properties pane.
     *---------------------------------------------------------*/

    // recreates the properties dialog with the properties panel 
    // of all rulers plus a panel that corresponds to general
    // ruler properties.
    public void updatePropertiesTabbedPane()
    {
	propertiesTabbedPane.removeAll();

	propertiesTabbedPane.add( "All Rulers", getPropertiesPanel());
	for (int i=0; i < rulerList.size(); i++) {
	    BaseRuler r = (BaseRuler) rulerList.get(i);
	    propertiesTabbedPane.add( r.getDescription(), 
				      r.getPropertiesPanel());
	}
	propertiesDialog.pack();
    }


    /**
     * update the hidden status of all rulers in the hidingPanel of the properties dialog.
     */
    public void updateHidingPanel(){
	hidingPanel.removeAll();
	hidingPanel.setLayout( new GridBagLayout());
	GridBagConstraints gbc = new GridBagConstraints();

	JPanel rulerPanel = new JPanel();
	rulerPanel.setLayout( new BoxLayout( rulerPanel, BoxLayout.Y_AXIS));
	rulerPanel.setBorder( new TitledBorder("Hide/Unhide Rulers"));
	for (int i=0; i < rulerList.size(); i++) {
	    final int       index = i;
	    final BaseRuler r = (BaseRuler) rulerList.get( index);
	    final JCheckBox cb = new JCheckBox( r.getDescription());
	    cb.addActionListener( new AbstractAction(){
		    public void actionPerformed( ActionEvent e){
			if (cb.isSelected()){
			    RulerManager.Instance.showComponent( r);
			} else {
			    RulerManager.Instance.hideComponent( r);
			}
		    }});
	    cb.setSelected( !r.isHidden());
	    rulerPanel.add( cb);
	}
	gbc.gridy++;
	hidingPanel.add( rulerPanel, gbc);
	propertiesDialog.pack();
    }



    // initializes the properties dialog.
    public void setUpPropertiesDialog(){
	propertiesDialog = new JDialog( super.frame, "Properties", false);

	Container propPane = propertiesDialog.getContentPane();
	propPane.setLayout( new BorderLayout());

	// Set up the hiding/unhiding panel
	hidingPanel = new JPanel();
	updateHidingPanel();
	propPane.add( hidingPanel, BorderLayout.NORTH);

	// Set up the tabbed pane in the middle of the dialog
	propertiesTabbedPane = new JTabbedPane();
	propertiesTabbedPane.setTabPlacement(JTabbedPane.TOP);
	updatePropertiesTabbedPane();
	propPane.add( propertiesTabbedPane, BorderLayout.CENTER);


	// Set up the button panel
	JPanel buttonPanel = new JPanel();
	buttonPanel.setLayout( new FlowLayout(FlowLayout.CENTER));
	JButton okButton = new JButton( 
				       new AbstractAction("OK") {
					   public void actionPerformed(ActionEvent e) {
					       propertiesDialog.setVisible(false);
					   }
				       });
	okButton.setFocusPainted(false);
	buttonPanel.add( okButton);
	propPane.add( buttonPanel, BorderLayout.SOUTH);

	propertiesDialog.pack();


	// Display the properties dialog in the middle of the screen.
	Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
	Dimension d = propertiesDialog.getSize();
	int x = (screen.width - d.width) / 2;
	int y = (screen.height - d.height) / 2;
	propertiesDialog.setLocation(x, y);
    }


    // sets up the general ruler properties panel.  This panel consists
    // of ruler background color and any properties that ruler types (Themis
    // and MRO) might have.
    private JPanel getPropertiesPanel(){
	
	JPanel propertiesPanel = new JPanel();
	
	propertiesPanel.setLayout( new GridBagLayout());
	GridBagConstraints gbc = new GridBagConstraints();
	
	gbc.gridy = 1;
	gbc.gridx = 1;
	gbc.gridwidth = 1;
	propertiesPanel.add( backgroundColor.getProp(), gbc);
	
	gbc.gridy++;
	gbc.gridx = 0;
	gbc.gridwidth = 1;
	gbc.anchor = GridBagConstraints.WEST;
	propertiesPanel.add(new JLabel("Font Size:"), gbc);
	
	gbc.gridx++;
	gbc.gridwidth = 1;
	gbc.anchor = GridBagConstraints.EAST;
	propertiesPanel.add( relFontSize.getProp(), gbc);
	
	
	if (rulerList.size()>0) {
	    BaseRuler r = (BaseRuler) rulerList.get(0);
	    gbc.gridy++;
	    gbc.gridx = 0;
		gbc.gridwidth = 2;
	    propertiesPanel.add(r.getBaseRulerPropertiesPanel(), gbc);
	}
	return propertiesPanel;
    }


} // end: class RulerManager.java

	
