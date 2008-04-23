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

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.layer.map2.CustomMapServer;
import edu.asu.jmars.layer.map2.MapServerFactory;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.sql.*;

/**
 * Title:        CustomFileManagerDialog
 * Description:  Mission Planner
 * Copyright:    Copyright (c) 2001
 * Company:      ASU
 * @author ...
 * @version 1.0
 */


public class CustomFileManagerDialog
{
	private static DebugLog log = DebugLog.instance();
	private ArrayList customObjects = new ArrayList(); //List of BackPropertiesDiag.CustomRecord objects
	private ArrayList customPanels = new ArrayList(); // This 2nd list will make it easy to 
                                                     // locate and removed items deemed for deletion
	private ArrayList itemsToDelete = new ArrayList();

	private	JDialog cfmd;

/*** GUI Components ***/

	JPanel topLevel;
	JPanel filler;
	JPanel B,A1,B1,B2;
	JScrollPane A;

	JButton selectAll = new JButton("Select All");
	JButton clear    = new JButton("Clear");
	JButton invert   = new JButton("Invert");
	JButton deleteSelected = new JButton("Delete Selected");
	JButton exit = new JButton("Egress");




	public	CustomFileManagerDialog(ArrayList co) // list of custom records
	{

		cfmd = new JDialog((JFrame)null,"Custom File Manager Dialog",true);

		customObjects = co;

		cfmd.getContentPane().add(initCustomFileManagerDialog());
		cfmd.setSize(new Dimension(535,400));
		cfmd.pack();
		cfmd.setVisible(true);

	}


	JComponent initCustomFileManagerDialog()
	{

		A1 = new JPanel(); //Top panel, lives in the scrollPanel, will be populated with choices
		buildChoices(A1);

		B = new JPanel(); //Bottom, panel.  Contains two panel, each with several buttons
		B.setLayout(new BoxLayout(B, BoxLayout.Y_AXIS));
		B1 = new JPanel();
		B1.setLayout(new BoxLayout(B1, BoxLayout.X_AXIS));
		B2 = new JPanel();
		B2.setLayout(new BoxLayout(B2, BoxLayout.X_AXIS));


		B1.add(selectAll);B1.add(clear);
		B2.add(invert);B2.add(deleteSelected);B2.add(exit);
		B.add(B1);B.add(B2);

		A = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		A.setViewportView(A1);
		A.setViewportBorder(new EmptyBorder(0,0,0,0));
		A.setBorder(new EmptyBorder(0,0,0,0));

//		topLevel = new JSplitPane(JSplitPane.VERTICAL_SPLIT,true,A,B);

		topLevel = new JPanel();

		topLevel.setLayout(new BorderLayout());

		topLevel.add(A,BorderLayout.CENTER);
//		filler = new JPanel();
//		filler.setLayout(new BorderLayout());
//		filler.add(new JSeparator(),BorderLayout.NORTH);
//		filler.add(B,BorderLayout.SOUTH);
		topLevel.add(B,BorderLayout.SOUTH);

		

		initCallBacks();

		return(topLevel);
	}


/*
** This routine sets-up all the button functionalities
**
*/

	protected void  initCallBacks()
	{
		exit.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
					cfmd.setVisible(false);
				}
		});

		selectAll.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){

					int i;
					ChoicePanel cp;
					for(i=0;i< customPanels.size();i++){
						cp = (ChoicePanel)customPanels.get(i);
						cp.setSelected(true);
					}
					cfmd.repaint();
				}
		});

		clear.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){

					int i;
					ChoicePanel cp;
					for(i=0;i< customPanels.size();i++){
						cp = (ChoicePanel)customPanels.get(i);
						cp.setSelected(false);
					}
					cfmd.repaint();
				}
		});

		invert.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){

					int i;
					ChoicePanel cp;
					for(i=0;i< customPanels.size();i++){
						cp = (ChoicePanel)customPanels.get(i);
						cp.setSelected(!cp.isSelected());
					}
					cfmd.repaint();
				}
		});

		deleteSelected.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					
					int i;
					ChoicePanel cp;
					itemsToDelete.clear();
					for(i=customPanels.size()-1;i>= 0;i--){
						cp = (ChoicePanel)customPanels.get(i);
							if (cp.isSelected()) {
								log.println("Removing object with index: "+i);	
								itemsToDelete.add(customObjects.remove(i));
							}
					}
					buildChoices(A1);
					A.setViewportView(A1);
					if (itemsToDelete.size() > 0)
						dbTransactionDelete();
					cfmd.pack();
					cfmd.repaint();
				}
		});


						


	}

/*
** This is the entry function for making the panel of choices.
** If the user deletes some, but not ALL their choices, then 
** the panel will neeed to be rebuilt reflecting the deletions.
** By calling this function again, a new panel with ONLY the remaining
** choices will be built.
*/
	protected void buildChoices(JPanel jp)
	{
		int i;
		BackPropertiesDiag.CustomRecord tmp;
		ChoicePanel cp;
/*
		if (customObjects.size() < 1)
			return;
*/

		customPanels.clear();	
		jp.removeAll();

		jp.setLayout(new GridLayout(customObjects.size(),1));

//		log.println("Building Panel object with the following number of panel entries: "+customObjects.size());
		for(i=0;i<customObjects.size();i++){
			tmp = (BackPropertiesDiag.CustomRecord) customObjects.get(i);
			cp = new ChoicePanel(tmp.descriptionName);
			customPanels.add(cp);
			jp.add(cp);
		}
	}


/*
** Once DB connection is established, this function is called.
** This function builds the query string which will delete ALL the
** selected files from the DB
**
**/
	protected void deleteItems(Connection con) throws SQLException
	{
		int i;

		Statement stmt = con.createStatement();

		String query = "delete from custom_map where user = \""+Main.USER+"\"";

		for(i=0;i<itemsToDelete.size();i++){
			BackPropertiesDiag.CustomRecord cr = (BackPropertiesDiag.CustomRecord)itemsToDelete.get(i);
			if (i == 0)
				query += " and filename = \""+cr.filename+"\"";
			else
				query += "  or filename = \""+cr.filename+"\"";
		}

		log.println("PERFORMING query: "+query);
		stmt.execute(query);
		if (stmt.getUpdateCount() != itemsToDelete.size())
			log.println("Not all elements were deleted.");
		itemsToDelete.clear();

	}

/*
** Starting point to setup transaction to delete
** This function establishes a connection the database
** and then calls the deletion function
*/
	protected void dbTransactionDelete() {
		String user = Main.DB_USER;
		String passwd = Main.DB_PASS;

		if (user.equalsIgnoreCase("x") && passwd.equals(""))
			user = null;

		int version = Config.get("tiles.version", 0);
		
		if (user == null || user.length() < 1 || (version < 2 && !Main.isInternal()))
			return;

		if (version == 2) {
			CustomMapServer cs = MapServerFactory.getCustomMapServer();
			for(int i=0;i<itemsToDelete.size();i++){
				BackPropertiesDiag.CustomRecord cr = (BackPropertiesDiag.CustomRecord)itemsToDelete.get(i);
				try {
					cs.deleteCustomMap(cr.filename);
				} catch (Exception e) {
					log.aprintln("Not all items were deleted");
					log.aprintln(e);
					JOptionPane.showMessageDialog(null, e.getMessage(),
						"Delete Custom Files", JOptionPane.WARNING_MESSAGE);
				}
			}
			itemsToDelete.clear();
		} else {
			try {
				String dbUrl = Config.get("db") + "user=" + user + "&password="
						+ passwd;
				Connection con;
				con = DriverManager.getConnection(dbUrl);
				deleteItems(con);
			} catch (SQLException ex) {
				JOptionPane.showMessageDialog(null, ex.getMessage(),
						"Delete Custom Files", JOptionPane.WARNING_MESSAGE);
			}
		}
	}


		

/*******************************************************************************
 * ************* INNER CLASSES ****************************************
 ******************************************************************************/

	public class ChoicePanel extends JPanel
	{
		JLabel itemName;
		JCheckBox choice;

		public ChoicePanel()
		{
			itemName = new JLabel("NULL");
			choice = new JCheckBox();
			choice.setSelected(false);
			choice.setEnabled(false);
			init();
		}

		public ChoicePanel(String item)
		{
			itemName = new JLabel(item);
			choice = new JCheckBox();
			choice.setSelected(false);
			choice.setEnabled(true);
			init();

		}

		public boolean isSelected()
		{
			return (choice.isSelected());
		}

		public void setSelected(boolean s)
		{
			choice.setSelected(s);
		}

		public void init()
		{
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			add(choice);
			add(Box.createHorizontalStrut(20));
			add(itemName);
			itemName.setBackground(Color.white);
			itemName.setForeground(Color.black);
//			setBorder( BorderFactory.createBevelBorder(BevelBorder.LOWERED));
//			setBorder( BorderFactory.createLineBorder(Color.black));
			setBorder( BorderFactory.createEtchedBorder());
			
		}
	}
}
