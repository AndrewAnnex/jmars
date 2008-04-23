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
import edu.asu.jmars.layer.map2.CustomMapServer;
import edu.asu.jmars.layer.map2.MapServerFactory;
import edu.asu.jmars.layer.map2.MapSource;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;
import java.util.*;
import java.util.List;
import java.net.*;
import java.io.*;
import java.sql.*;


/**
 * Title:        JMARS
 * Description:  Mission Planner
 * Copyright:    Copyright (c) 2001
 * Company:      ASU
 * @author ...
 * @version 1.0
 */

public class BackPropertiesDiag extends JDialog
{
	private static DebugLog log=DebugLog.instance();
	static protected String items[];
	static protected String item_locations[];
	static protected int resolutionIndecies[];
	static protected boolean isGlobal[];
	static public String tileSet[];

	static boolean PRESELECT_CUSTOM = false;


	/*Build our list of availible "built-in" back maps */

	static protected void parseInput(String input,
			LinkedList l1,
			LinkedList l2,
			LinkedList l3,
			LinkedList l4,
			LinkedList l5)
	{
		String p;

		try
		{
			StringTokenizer st = new StringTokenizer(input,"\t");

			l1.add( ((p=st.nextToken())==null ? "NULL-ITEM":p));//log.println((String)l1.getLast());
			l2.add( ((p=st.nextToken())==null ? "NULL-ITEM":p));//log.println((String)l2.getLast());
			l3.add( ((p=st.nextToken())==null ? "NULL-ITEM":p));//log.println((String)l3.getLast());
			l4.add( ((p=st.nextToken())==null ? "NULL-ITEM":p));//log.println((String)l4.getLast());
			l5.add( ((p=st.nextToken())==null ? "NULL-ITEM":p));//log.println((String)l5.getLast());
		}
		catch(RuntimeException e)
		{
			log.aprintln("PARSE FAILURE ON: [" + input + "]");
			throw  e;
		}
	}

	static void buildArrays(LinkedList l1,
			LinkedList l2,
			LinkedList l3,
			LinkedList l4,
			LinkedList l5)
	{
		int i;
		String []p;
		Object []o;

//		First three lists are strings, so it's easy


		items			=		(String [])l1.toArray(new String[0]);
		tileSet			=		(String [])l2.toArray(new String[0]);
		item_locations	=		(String [])l3.toArray(new String[0]);

//		Fouth list is a list of integers...gotta convert from string form
		p = (String [])l4.toArray(new String[0]);	
		resolutionIndecies   = new int[p.length];
		for(i=0;i<p.length;i++)
			resolutionIndecies[i] = Integer.valueOf(p[i]).intValue();

//		Fifth list is a list of booleans...also gotta convert
		p = (String [])l5.toArray(new String[0]);	
		isGlobal = new boolean[p.length];
		for(i = 0; i < p.length; i++)
			isGlobal[i] = (p[i].compareToIgnoreCase("true") == 0 ? true : false);	

		/*DEBUG ONLY*** REMOVE/COMMENT-out for normal running */

//		for (i=0;i<p.length;i++){
//		log.println("index: "+i);
//		log.println("\t"+items[i]+" , "+tileSet[i]+" , "+item_locations[i]+" , "+resolutionIndecies[i]+" , "+isGlobal[i]);
//		}

	}


	static {

		log.println("Initializing Static portion of this CLASS");

		LinkedList theList_1 = new LinkedList(); //Empty list used for building Descriptions
		LinkedList theList_2 = new LinkedList(); //Empty list used for building Agent parameter names
		LinkedList theList_3 = new LinkedList(); //Empty list used for building locations
		LinkedList theList_4 = new LinkedList(); //Empty list used for building resolution indecies
		LinkedList theList_5 = new LinkedList(); //Empty list used for building isGlobal booleans


		String[] toBeParsed = Config.getArray("Gtiles");
		for(int i = 0; i < toBeParsed.length; i++){
			parseInput(toBeParsed[i], theList_1, theList_2, theList_3, theList_4, theList_5);
		}

		buildArrays(theList_1, theList_2, theList_3, theList_4, theList_5);

	}

	protected	BackDialogParameterBlock	bdpb;

	protected JFrame parent;
	protected boolean partial;
	JDialog me = this;
	JComboBox cb;
	JTextField tf = new PasteField(25);
	JTextField serverAddress = new PasteField(35);
	JTextField maxAgents = new PasteField(5);
	JCheckBox doGrid=new JCheckBox("Server Side Grid");

	JButton browseButton = new JButton();
	private static String startingDir = null;


	JButton okButton = new JButton();
	JButton cancelButton = new JButton();
	JButton customFiles = new JButton("Manage Custom Files");
	boolean canceled = true;

	protected boolean global;
	protected double min_lat = 0.0, min_lon = 0.0, max_lat = 0.0, max_lon = 0.0;
	protected String serverName;

	protected ArrayList customRecords = new ArrayList();
	protected String[] customItems;
	protected boolean[] customIsGlobal;
	protected String[] masterItems;
	protected boolean[] masterIsGlobal;


//	Initialize this object's access to the DB
	static
	{
		Util.loadSqlDriver();
	}

	public static class CustomRecord
	{
		public String user;
		public String filename;
		public String descriptionName;
		public String extFlag;
		public String type;
		public double min_lat;
		public double min_lon;
		public double max_lat;
		public double max_lon;

		public CustomRecord()
		{
			user = null;
			filename = null;
			descriptionName = null;
			extFlag = null;
			type = null;
			min_lat =- 999999999.999;
			min_lon =- 999999999.999;
			max_lat =- 999999999.999;
			max_lon =- 999999999.999;
		}

	}

	protected boolean initDBConnection()
	{
		String user = Main.DB_USER;
		String passwd = Main.DB_PASS;

		if (user != null && user.equalsIgnoreCase("x")  && passwd.equals(""))
			user = null;

		if (!Main.useNetwork())
			user = null;

		int version = Config.get("tiles.version", 0);
		if (user == null || user.length() < 1 || (version != 2 && !Main.isInternal())) {
			masterItems = items;
			masterIsGlobal = isGlobal;
			return(false);
		}


		try 
		{
			loadCustomObjects(user, passwd);

			if (customRecords.size() > 0)  //Okay, we have records!
				buildMasterLists();
			else 
			{
				masterItems = items;
				masterIsGlobal = isGlobal;
			}

			return(true);
		}

		catch(SQLException ex)
		{
			JOptionPane.showMessageDialog(null,ex.getMessage(), "Custom Gfx List",
					JOptionPane.WARNING_MESSAGE);
			return(false);
		}


	}
	protected void buildComboBox(String []masterItems)
	{
		if (cb == null) {
			log.println("I can't build into a null combo box!");
			return;
		}

		else if (masterItems == null) {
			log.println("I can't build from a null list strings!");
			return;
		}

		for(int i = cb.getItemCount() - 1; i >= 0; i--) {
//			log.println("Removing item: "+i);
			cb.removeItemAt(i);
		}

		for(int i = 0; i < masterItems.length; i++)
			cb.addItem(new String(masterItems[i]));

//		log.println("Finished building Combo Box with: "+cb.getItemCount()+" item"+(cb.getItemCount() == 1 ? "":"s"));
	}

	protected void buildMasterLists()
	{
		int idx;
		int i;
		CustomRecord cr;

		masterItems = new String[items.length+customRecords.size()];
		masterIsGlobal = new boolean[isGlobal.length+customRecords.size()];

		for (i = 0; i < items.length - 1; i++) {
			masterItems[i] = items[i];
			masterIsGlobal[i] = isGlobal[i];
		}
		idx = i;
		for (i = 0; i < customRecords.size(); i++) {
			cr = (CustomRecord)customRecords.get(i);
			masterItems[idx] = cr.descriptionName;
			masterIsGlobal[idx] = cr.extFlag.equalsIgnoreCase("global");
			idx++;
		}
		masterItems[idx] = items[items.length - 1]; //Custom
		masterIsGlobal[idx] = isGlobal[isGlobal.length - 1]; //Custom global setting..ie false
	}


	protected void loadCustomObjects(String user, String passwd) throws SQLException
	{
		int version = Config.get("tiles.version", 0);
		if (version == 2) {
			CustomMapServer cs = MapServerFactory.getCustomMapServer();
			List sources = cs.getMapSources();
			for (Iterator it = sources.iterator(); it.hasNext(); ) {
				MapSource s = (MapSource)it.next();
				if (s.getTitle().toLowerCase().indexOf("numeric") == -1)
					customRecords.add(castMapSourceToCustomRecord(s));
			}
		} else {
			String dbUrl = Config.get("db") + "user=" + user + "&password=" + passwd;
			Connection con = DriverManager.getConnection(dbUrl);

			int count = 0;
			Statement stmt = con.createStatement();
			/**
			 * We'll check for a tiles.version, if we find one then we'll add the
			 * parameter to the query, if not then we will assume this is not an
			 * upgraded table the user is connecting to.
			 */
			String query = "select * from custom_map where user = \"" + Main.USER +
			"\" and type = \"VIS\"";
			if (version > 0)
				query += " and version=" + version;

			ResultSet rs = stmt.executeQuery(query);

			while (rs.next()) {
				count++;
				CustomRecord cr = castDBRecToCustomRecord(rs);
				if (cr == null) {
					log.aprintln("ERROR: failed to extract DB record #" + count + " for call:");
					log.aprintln("\t"+query);
				}

//				Here's where we need to store the records!
				else 
				{
					customRecords.add(cr);
				}
			}
		}
	}

	public static CustomRecord castDBRecToCustomRecord(ResultSet rs)
	{
		CustomRecord cr = new CustomRecord();
		try
		{
			cr.user 			= rs.getString("user");
			cr.filename 		= rs.getString("filename");
			cr.descriptionName 	= rs.getString("name");
			cr.extFlag 			= rs.getString("ext_flag");
			cr.type 			= rs.getString("type");

			if (cr.type == null) {
				log.aprintln("ERROR: Failed to retreive record TYPE.");
				return(null);
			}

			if (cr.type.equalsIgnoreCase("global"))
				return(cr);

			cr.min_lat = rs.getDouble("min_lat");
			cr.min_lon = rs.getDouble("min_lon");
			cr.max_lat = rs.getDouble("max_lat");
			cr.max_lon = rs.getDouble("max_lon");

			return(cr);
		}

		catch(SQLException ex) {
			log.aprintln("Received SQL Exception: " + ex.getMessage());
			return(null);
		}
	}

	public static CustomRecord castMapSourceToCustomRecord(MapSource source) {
		CustomRecord cr = new CustomRecord();
		cr.user = Main.USER;
		cr.filename = source.getName();
		cr.descriptionName = source.getTitle();
		cr.extFlag = "GLOBAL";
		cr.type = "VIS";
		return cr;
	}

	//Copy everything in the BackDialogParameterBlock to the swing components
	protected void backDialogPropertiesBlockToSwing()
	{
		if (bdpb.tileChoice >= cb.getItemCount())
			bdpb.tileChoice = 0;

		cb.setSelectedIndex(PRESELECT_CUSTOM
				? masterItems.length - 1
						: bdpb.tileChoice);
		if(PRESELECT_CUSTOM)
		{
			tf.setEditable(true);
			browseButton.setEnabled(true);
		}
		tf.setText(bdpb.customChoice);
		maxAgents.setText(String.valueOf(bdpb.maxAgents));
		serverAddress.setText(bdpb.serverAddress);
		doGrid.setSelected(bdpb.grid);
	}

	//Copy everything in the swing components to the BackDialogParameterBlock
	protected boolean swingToBackDialogPropertiesBlock()
	{
		bdpb.tileChoice = cb.getSelectedIndex();

		if (bdpb.tileChoice >= (items.length - 1) )
			bdpb.maxResolutionIndex = resolutionIndecies[items.length - 1];
		else
			bdpb.maxResolutionIndex = resolutionIndecies[bdpb.tileChoice];

		bdpb.maxAgents = (Integer.valueOf(maxAgents.getText())).intValue();
		bdpb.serverAddress = serverAddress.getText();
		bdpb.grid = doGrid.isSelected();

		if(!(processTileChoice()))
			return false;

		bdpb.global = global;

		if (!global) 
			bdpb.boundry = new Rectangle2D.Double(min_lon, min_lat, 
					(max_lon - min_lon), (max_lat - min_lat));

		return (true);

	}

	protected boolean processTileChoice()
	{

		//get server name from this server after the http://

		int pos = bdpb.serverAddress.indexOf("/", 7);

		if (pos < 0)
			serverName = bdpb.serverAddress.substring(7);
		else
			serverName = bdpb.serverAddress.substring(7, pos);

		if (!((String)cb.getSelectedItem()).equalsIgnoreCase("Custom")) {

			global = masterIsGlobal[cb.getSelectedIndex()];
			bdpb.isCustom = false;

			if (cb.getSelectedIndex() >= (items.length - 1)) { //choosen a tile from db list
				int offset = items.length - 1;
				CustomRecord cr = (CustomRecord)customRecords.get(cb.getSelectedIndex() - offset);
				bdpb.customChoice = (String)cb.getSelectedItem();
				log.println("Setting REMOTE hashname = " + cr.filename);
				bdpb.setRemoteHashedName(cr.filename);
				if (!cr.extFlag.equalsIgnoreCase("global"))
				{
					min_lon = cr.min_lon;
					min_lat = cr.min_lat;
					max_lon = cr.max_lon;
					max_lat = cr.max_lat;
				}
			} 

			else {


				if (!global) 
					parsePartial(item_locations[cb.getSelectedIndex()]);

			}

		}

		else {

			String t1;
			String response = null;
			int idx;
			String outcome;

			bdpb.customChoice = tf.getText();
			bdpb.isCustom = true;

			if ( bdpb.getCustomFileType() == BDialogParameterBlock.REMOTEFILE ) {
				response = getRemote();
			}

			else if ( bdpb.getCustomFileType() == BDialogParameterBlock.LOCALFILE ) 
			{
				response = sendLocal();
			}

			else {
				log.aprintln("How can this happen?");
				return false;
			}

			this.setCursor(Cursor.getDefaultCursor());
			parent.setCursor(Cursor.getDefaultCursor());
			Main.mainFrame.setStatus("");
			Main.mainFrame.repaint();

			/*
			 ** Check value of response (ERROR,GLOBAL,PARTIAL)
			 ** descide what to do from there
			 */
			if (response == null) {
				log.println("ERROR! There was no reponse!");
				failedDialog("The upload failed for some unknown reason...contact jmars@mars.asu.edu");
				return false;
			}

			idx = response.indexOf(':');
			if (idx < 0)
			{
				log.aprintln("ERROR! The return message is garbage!");
				failedDialog("The upload failed for some unknown reason...contact jmars@mars.asu.edu");
				return false;
			}

			outcome = response.substring(0,idx);
			log.println("Outcome = " + outcome);
			log.println("Response = " + response);

			if (outcome.equalsIgnoreCase("error")) {
				failedDialog(response.substring((idx + 1)));
				return false;
			}

			else if (outcome.equalsIgnoreCase("global")) {
				global = true;
				Main.mainFrame.setStatus("Upload successfull");
				return true;
			}

			else {
				global = false;
				Main.mainFrame.setStatus("Upload successfull");
				parsePartial(response.substring((idx + 1)));
				return (true);
			}
		}

		return(true);

	}



	/*
	 ** bb is a comma seperated list containing:
	 **	  min_lat,min_lon,max_lat,max_lon
	 */
	protected void parsePartial(String bb)
	{
		StringTokenizer st = new StringTokenizer (bb, ",");
		String token;
		double v[] = new double[4];
		int idx = 0;


		while (st.hasMoreTokens()) {
			if (idx >= 4) {
				log.println("Parsing error.  There are more than four entities....you're in deep do-do!");
				return;
			}
			token = st.nextToken();
			log.print("Token #" + idx + " = ("+token+") and after trimming =(");
			token = token.trim();
			log.println(token + ")");
			v[idx] = (Double.valueOf(token)).doubleValue();
			log.println("Which translates to: " + v[idx]);
			idx++;
		}

		min_lat = v[0];
		min_lon = v[1];
		max_lat = v[2];
		max_lon = v[3];

//		Need to fix-up meridian fuzzies
//		Now ALL meridian straddlers will be around 0 (ie min_lon should be < 0)
		if (max_lon > 360.0 || min_lon > 360.0) {
			max_lon -= 360.0;
			min_lon -= 360.0;
		}



	}

	protected void failedDialog(String message)
	{
		Main.mainFrame.setStatus("Upload Failed");
		JOptionPane oops = new JOptionPane();
		oops.showMessageDialog(new JFrame(),message);
	}

	protected String getSafeName(String name) {
		name = name.replaceAll("[^0-9a-zA-Z]", "_");
		return name;
	}

	protected String getRemote()
	{
		URL foo;
		try {
			foo = new URL(tf.getText());
		} catch (MalformedURLException exception) {
			return ("ERROR:Malformed URL: " + tf.getText());
		}
		
		int version = Config.get("tiles.version", 1);
		if (version == 2) {
			try {
				CustomMapServer cs = MapServerFactory.getCustomMapServer();
				cs.uploadCustomMap(getSafeName(foo.getFile()), foo);
				return "global:ok";
			} catch (Exception e1) {
				log.println("Error occured");
				log.println(e1);
				return e1.getMessage();
			}
		} else {
			log.println("Your PROCESSED custom string is: " + bdpb.customChoice);
			//Go get the file
			try {
				String res;
				String serverURL = "http://";
				serverURL += serverName;
				serverURL += Config.get("tiles.cust.remoteget");
				String postData = (Main.USER == null ? "":"user=" + Main.USER +
						"&passwd=" + Util.urlEncode(Main.PASS)) + "&name=" + bdpb.getBaseFileName()+
						"&rfile=" + bdpb.customChoice + "&lfile=" + bdpb.getRemoteHashedName();
				this.setCursor(new Cursor(Cursor.WAIT_CURSOR));
				log.println("serverURL= " + serverURL);
				log.println("postData= " + postData);
				res = Util.httpPost(serverURL, postData);
				this.setCursor(Cursor.getDefaultCursor());
				return (res);
			}  catch (Exception e) {
				log.println(e);
				return ("ERROR:" + e.getMessage());
			}
		}
	} 


	protected String sendLocal()
	{
		//see if local file exists...
		File f = new File(bdpb.customChoice);
		if (!f.exists())
			return ("ERROR:File doesn't exist");
		
		log.println("File Name = " + f.getName());

		Main.setStatus("Uploading file to server, please wait....");
		Main.mainFrame.repaint();
		setCursor(new Cursor(Cursor.WAIT_CURSOR));
		
		int version = Config.get("tiles.version", 1);
		if (version == 2) {
			try {
				CustomMapServer cs = MapServerFactory.getCustomMapServer();
				cs.uploadCustomMap(getSafeName(f.getName()), f);
				log.println("Uploaded file " + f + " with name " + getSafeName(f.getName()));
				return "global:ok";
			} catch (Exception e1) {
				log.println("Error occured");
				log.println(e1);
				return e1.getMessage();
			}
		} else {
			try {
				String serverURL = "http://";
				serverURL += serverName;
				serverURL += Config.get("tiles.cust.upload");

				log.println("NAME = " + bdpb.getBaseFileName());

				if (Main.USER != null) 
					serverURL += "?user=" + Main.USER + "&passwd=" + Util.urlEncode(Main.PASS) +
					"&name=" + bdpb.getBaseFileName();

				log.println("sending Req: " + serverURL);
				log.println("sending customChoice: " + bdpb.customChoice);
				log.println("sending RemoteHashedName: " + bdpb.getRemoteHashedName());

				String response = Util.httpPostFileName(serverURL, bdpb.customChoice,  bdpb.getRemoteHashedName());
				return (response);
			} catch (Exception e) {
				log.println(e);
				return ("ERROR:Some Exception has occured (this shouldn't happen!)");
			}
		}
	}

	public JFrame getThisParent() {
		return parent;
	}

	public BackPropertiesDiag(JFrame parent, boolean modal)
	{
		this(parent, modal, true);
	}

	public BackPropertiesDiag(JFrame parent, boolean modal, boolean visible)
	{
		super(parent,"Background Properties Dialog", modal);
		if(parent != null)
			setLocation(parent.getLocation());
		bdpb = new BackDialogParameterBlock();
		this.parent = parent;

		if (!initDBConnection())
			log.println("DB access failed.  There will be NO custom lists added");

		cb = new JComboBox();
		buildComboBox(masterItems);

		try
		{
			jbInit(); //Caned initialization ala JBuilder
			backDialogPropertiesBlockToSwing(); //unCanned initialization ala Ben

			//CallBack Set-up Here:
			cb.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					log.println("You've selected index: " + cb.getSelectedIndex());
					if (e.getStateChange() != ItemEvent.SELECTED)
						return;

					log.println("You've selected " + cb.getSelectedItem() + " which is " +
							((String)cb.getSelectedItem()).length() + " characters");
					if (!((String)cb.getSelectedItem()).equalsIgnoreCase("Custom")) {
						tf.setEditable(false);
						browseButton.setEnabled(false);
					} else {
						tf.setEditable(true);
						browseButton.setEnabled(true);
					}
				}
			});



			okButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {

					log.println("You chose item " + cb.getSelectedIndex() + " = " +
							(String)cb.getSelectedItem());

					if(swingToBackDialogPropertiesBlock())
					{
						canceled = false;
						me.setVisible(false);
					}
				}
			});

			cancelButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					canceled = true;
					me.setVisible(false);
				}
			});

			browseButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {

					String path = tf.getText();
					String fs = System.getProperty("file.separator");
					boolean found = false;
					int idx;
					log.println("Path = " + path);
					if (path != null && path.length() > 0) {
						File f = new File(path);
						log.println("Your path (" + path + ") does " + (f.exists() ? "" : "NOT ") +
						"exist.");
						if (f.exists() == false) {
							idx = path.lastIndexOf(fs,path.length());
							while (idx >= 0) {
								path = path.substring(0, idx);
								log.println("Path = " + path);
								f = new File(path);
								if (f.exists()){
									found = true;
									break;
								}
								idx = path.lastIndexOf(fs, path.length());
							}
						}
						else
							found = true;
					}
					if (found)
						startingDir = path;
					else
						startingDir = null;

					FileDialog fdlg = new FileDialog(getThisParent(), "Select Custom Map File", 
							FileDialog.LOAD);

					if (startingDir != null)
						fdlg.setDirectory(startingDir);

					fdlg.setVisible(true);
					if (fdlg.getFile() != null) {
						startingDir = fdlg.getDirectory() ;
						tf.setText( fdlg.getDirectory() + fdlg.getFile());
					}
				}
			});

			if (customRecords.size() > 0) {
				customFiles.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						log.println("Master Items List contains the following number of items: " + 
								masterItems.length);
						CustomFileManagerDialog cmfd = new CustomFileManagerDialog(customRecords);

						if (customRecords.size() > 0) {//Okay, we have records!
							log.println("Returning from Custom Manager...Rebuilding File list");
							buildMasterLists();
						}
						else 
						{
							masterItems = items;
							masterIsGlobal = isGlobal;
						}

						buildComboBox(masterItems);
						backDialogPropertiesBlockToSwing();
						repaint();
						log.println("Master Items List contains the following number of items: " + 
								masterItems.length);
					}
				});
			}
			else
				customFiles.setEnabled(false);

			pack();
			if(visible)
				setVisible(true);
			else
				dispose();
		}

		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private void jbInit()
	{
		this.getContentPane().setLayout(new BorderLayout(10, 10));

		JLabel l1 = new JLabel();
		JLabel l2 = new JLabel();
		JLabel l3 = new JLabel();
		JLabel l4 = new JLabel();

		l1.setText("Map source:");
		l1.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
		l2.setText("Custom Map (URL or Local File):");
		l2.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));


		l3.setText("Map Server Address:");
		l3.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
		l4.setText("Max Agents:");
		l4.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));


		tf.setEditable(false);
		tf.setHorizontalAlignment(SwingConstants.LEFT);
		browseButton.setEnabled(false);

		Box p2 = Box.createVerticalBox();

		JPanel p = new JPanel(new BorderLayout(10, 10));
		p.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
		p.add(l1, BorderLayout.WEST);
		p.add(cb, BorderLayout.EAST);
		p2.add(p);

		p = new JPanel(new BorderLayout(10, 10));
		p.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
		p.add(l2, BorderLayout.WEST);
		p.add(tf, BorderLayout.CENTER);
		p.add(browseButton, BorderLayout.EAST);
		p2.add(p);

		if (Main.isInternal()){
			p = new JPanel(new BorderLayout(10, 10));
			p.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
			p.add(l3, BorderLayout.WEST);
			p.add(serverAddress,BorderLayout.CENTER);
			p2.add(p);

			p = new JPanel(new BorderLayout(10, 10));
			p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
			p.add(l4, BorderLayout.WEST);
			p.add(maxAgents, BorderLayout.CENTER);
			p2.add(p);

			p = new JPanel(new BorderLayout(10, 10));
			p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
			p.add(doGrid,BorderLayout.WEST);
			p2.add(p);
		}

		p = new JPanel(new BorderLayout(10, 10));
		p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		p.add(customFiles,BorderLayout.WEST);
		p2.add(p);

		p = new JPanel(new BorderLayout(10, 10));
		p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		p.add(okButton, BorderLayout.WEST);
		p.add(cancelButton, BorderLayout.EAST);
		p2.add(p);

		browseButton.setText("Browse");
		okButton.setText("OK");
		cancelButton.setText("Cancel");


		this.getContentPane().add(p2);
		this.getContentPane().setEnabled(true);
		this.setTitle("New Map Layer");

		this.pack();

		// this.getContentPane().setBackground(Color.lightGray);
		// this.getContentPane().setEnabled(true);
		// this.setSize(new Dimension(469, 359));
		// this.setResizable(false);
		// this.setBorder(border2);

	}


	public BDialogParameterBlock getParameters()
	{
		log.print("Your BDPB is "	);
		if (bdpb == null)
			log.println("NULL!!!");
		else
			log.println(bdpb);
		return(bdpb);
	}


	public boolean Canceled()
	{
		return(canceled);
	}
}
