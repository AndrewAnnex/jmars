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

public class NumBackPropertiesDiag extends JDialog
{
	private static DebugLog log=DebugLog.instance();
	static protected String items[];
	static protected String item_locations[];
	static protected int resolutionIndecies[];
	static protected boolean isGlobal[];
	static public String tileSet[];
	/*Build our list of availible "built-in" back maps */

	static boolean PRESELECT_CUSTOM = false;

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

//		First three lists are strings, so it's easy
		items                =     (String [])l1.toArray(new String[0]);
		tileSet              =     (String [])l2.toArray(new String[0]);
		item_locations       =     (String [])l3.toArray(new String[0]);

//		Fouth list is a list of integers...gotta convert from string form
		p = (String [])l4.toArray(new String[0]);
		resolutionIndecies   = new int[p.length];
		for(i=0;i<p.length;i++)
			resolutionIndecies[i] = Integer.valueOf(p[i]).intValue();

//		Fifth list is a list of booleans...also gotta convert
		p = (String [])l5.toArray(new String[0]);
		isGlobal   = new boolean[p.length];
		for(i=0;i<p.length;i++)
			isGlobal[i] = (p[i].compareToIgnoreCase("true")==0 ? true:false);


		/*DEBUG ONLY*** REMOVE/COMMENT-out for normal running */

		for (i=0;i<p.length;i++){
			log.println("index: "+i);
			log.println("\t"+items[i]+" , "+tileSet[i]+" , "+item_locations[i]+" , "+resolutionIndecies[i]+" , "+isGlobal[i]);
		}

	}


	static {

		log.println("Initializing Static portion of this CLASS");

		LinkedList theList_1 = new LinkedList(); //Empty list used for building Descriptions
		LinkedList theList_2 = new LinkedList(); //Empty list used for building Agent parameter names
		LinkedList theList_3 = new LinkedList(); //Empty list used for building locations
		LinkedList theList_4 = new LinkedList(); //Empty list used for building resolution indecies
		LinkedList theList_5 = new LinkedList(); //Empty list used for building isGlobal booleans


		String[] toBeParsed = Config.getArray("Ntiles");
		for(int i=0; i<toBeParsed.length; i++){
			parseInput(toBeParsed[i],theList_1,theList_2,theList_3,theList_4,theList_5);
		}

		buildArrays(theList_1,theList_2,theList_3,theList_4,theList_5);

	}


	protected	NumBackDialogParameterBlock	nbdpb;

	protected JFrame parent;
	JDialog me = this;
	JComboBox cb;// = new JComboBox(items);
	JTextField tf = new PasteField(25);
	JTextField serverAddress = new PasteField(35);
	JTextField maxAgents = new PasteField(5);
	ColorCombo cc = new ColorCombo();

	JButton browseButton = new JButton();
	private static String startingDir = null;


	JButton okButton = new JButton();
	JButton cancelButton = new JButton();
	boolean canceled=false;
	protected boolean global;
	protected double min_lat=0.0,min_lon=0.0,max_lat=0.0,max_lon=0.0;
	protected String serverName;


	protected ArrayList customRecords = new ArrayList();
	protected String[] customItems;
	protected boolean[] customIsGlobal;
	protected String[] masterItems;
	protected boolean[] masterIsGlobal;

	static
	{
		Util.loadSqlDriver();
	}

	public class CustomRecord
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
			user=null;
			filename=null;
			descriptionName=null;
			extFlag=null;
			type=null;
			min_lat=-999999999.999;
			min_lon=-999999999.999;
			max_lat=-999999999.999;
			max_lon=-999999999.999;
		}
	}

	protected boolean initDBConnection()
	{
		String user = Main.DB_USER;
		String passwd = Main.DB_PASS;

		if (user != null  &&  user.equalsIgnoreCase("x")  && passwd.equals(""))
			user=null;

		if (!Main.useNetwork())
			user=null;

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
			JOptionPane.showMessageDialog(null,ex.getMessage(),"Custom Gfx List",JOptionPane.WARNING_MESSAGE);
			return(false);
		}

	}

	protected void buildMasterLists()
	{
		int idx;
		int i;
		CustomRecord cr;

		masterItems = new String[items.length+customRecords.size()];
		masterIsGlobal = new boolean[isGlobal.length+customRecords.size()];

		for (i=0;i<items.length-1;i++){
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
		masterItems[idx] = items[items.length-1]; //Custom
		masterIsGlobal[idx] = isGlobal[isGlobal.length-1]; //Custom global setting..ie false
	}
	
	protected void loadCustomObjects(String user, String passwd) throws SQLException
	{
		int version = Config.get("tiles.version", 0);
		if (version == 2) {
			CustomMapServer cs = MapServerFactory.getCustomMapServer();
			List sources = cs.getMapSources();
			for (Iterator it = sources.iterator(); it.hasNext(); ) {
				MapSource s = (MapSource)it.next();
				if (s.getTitle().toLowerCase().indexOf("numeric") >= 0)
					customRecords.add(castMapSourceToCustomRecord(s));
			}
		} else {
			String dbUrl = Config.get("db")+"user="+user+ "&password="+passwd;
			Connection con = DriverManager.getConnection(dbUrl);

			int count = 0;
			Statement stmt = con.createStatement();
			String query = "select * from custom_map where user = \""+Main.USER+"\" and type = \"NUM\"";
			if (version > 0)
				query += " and version=" + version;
			ResultSet rs = stmt.executeQuery(query);

			while (rs.next()){
				count++;
				CustomRecord cr = castDBRecToCustomRecord(rs);
				if (cr == null) {
					log.aprintln("ERROR: failed to extract DB record #"+count+" for call:");
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

	protected CustomRecord castDBRecToCustomRecord(ResultSet rs)
	{
		CustomRecord cr = new CustomRecord();
		try
		{
			cr.user              = rs.getString("user");
			cr.filename          = rs.getString("filename");
			cr.descriptionName   = rs.getString("name");
			cr.extFlag           = rs.getString("ext_flag");
			cr.type              = rs.getString("type");

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
		catch(SQLException ex){
			log.aprintln("Received SQL Exception: "+ex.getMessage());
			return(null);
		}
	}

	protected CustomRecord castMapSourceToCustomRecord(MapSource source) {
		CustomRecord cr = new CustomRecord();
		cr.user = Main.USER;
		cr.filename = source.getName();
		cr.descriptionName = source.getTitle();
		cr.extFlag = "GLOBAL";
		cr.type = "NUM";
		return cr;
	}

	//Copy everything in the BackDialogParameterBlock to the swing components
	protected void backDialogPropertiesBlockToSwing()
	{
		cb.setSelectedIndex(PRESELECT_CUSTOM
				? masterItems.length - 1
						: nbdpb.tileChoice);
		if(PRESELECT_CUSTOM)
		{
			tf.setEditable(true);
			browseButton.setEnabled(true);
		}

		tf.setText(nbdpb.customChoice);
		maxAgents.setText(String.valueOf(nbdpb.maxAgents));
		serverAddress.setText(nbdpb.serverAddress);
		cc.setColor(nbdpb.color);
	}
	//Copy everything in the swing components to the BackDialogParameterBlock
	protected boolean swingToBackDialogPropertiesBlock()
	{
		nbdpb.tileChoice=cb.getSelectedIndex();
		nbdpb.maxAgents=(Integer.valueOf(maxAgents.getText())).intValue();
		nbdpb.serverAddress=serverAddress.getText();

		if (nbdpb.tileChoice >= (items.length-1) )
			nbdpb.maxResolutionIndex=resolutionIndecies[items.length-1];
		else
			nbdpb.maxResolutionIndex=resolutionIndecies[nbdpb.tileChoice];

		nbdpb.color = cc.getColor();

		if(!(processTileChoice()))
			return false;

		nbdpb.global = global;

		if (!global)
			nbdpb.boundry = new Rectangle2D.Double(min_lon,min_lat,(max_lon-min_lon),(max_lat-min_lat));

		return(true);
	}

	protected boolean processTileChoice()
	{

		//get server name from this server after the http://
		int pos = nbdpb.serverAddress.indexOf("/",7);
		serverName = nbdpb.serverAddress.substring(7,pos);

		if (!((String)cb.getSelectedItem()).equalsIgnoreCase("Custom")) {
			global =  masterIsGlobal[cb.getSelectedIndex()];
			nbdpb.isCustom = false;

			if (cb.getSelectedIndex() >= (items.length-1)) { //choosen a tile from db list
				int offset = items.length-1;
				CustomRecord cr = (CustomRecord)customRecords.get(cb.getSelectedIndex() - offset);
				nbdpb.customChoice=(String)cb.getSelectedItem();
				log.println("Setting REMOTE hashname = "+cr.filename);
				nbdpb.setRemoteHashedName(cr.filename);
				if (!cr.extFlag.equalsIgnoreCase("global"))
				{
					min_lon=cr.min_lon;
					min_lat=cr.min_lat;
					max_lon=cr.max_lon;
					max_lat=cr.max_lat;
				}
			}

			else {

				if (!global) {
					String item = (String)cb.getSelectedItem();
					parsePartial(item.substring((item.indexOf('(')+1),(item.length()-1)));
				}
			}
		}

		else {

			String t1;
			String response=null;
			int idx;
			String outcome;

			nbdpb.customChoice=tf.getText();
			nbdpb.isCustom = true;

			if ( nbdpb.getCustomFileType() == BDialogParameterBlock.REMOTEFILE ) {
				response = getRemote();
			}

			else if ( nbdpb.getCustomFileType() == BDialogParameterBlock.LOCALFILE )
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
				failedDialog("The upload failed for some unknown reason...contact jmars@asu.edu");
				return false;
			}

			idx = response.indexOf(':');
			if (idx < 0)
			{
				log.aprintln("ERROR! The return message is garbage!");
				failedDialog("The upload failed for some unknown reason...contact jmars@asu.edu");
				return false;
			}

			outcome = response.substring(0,idx);
			log.println("Outcome = "+outcome);
			log.println("Response = "+response);

			if (outcome.equalsIgnoreCase("error")) {
				failedDialog(response.substring((idx+1)));
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
				parsePartial(response.substring((idx+1)));
				return (true);
			}
		}

		return(true);

	}


	/*
	 ** bb is a comma seperated list containing:
	 **   min_lat,min_lon,max_lat,max_lon
	 */
	protected void parsePartial(String bb)
	{
		StringTokenizer st = new StringTokenizer (bb,",");
		String token;
		double v[] = new double[4];
		int idx = 0;


		while (st.hasMoreTokens()) {
			if (idx >= 4) {
				log.println("Parsing error.  There are more than four entities....you're in deep do-do!");
				return;
			}
			token = st.nextToken();
			log.print("Token #"+idx+" = ("+token+") and after trimming =(");
			token=token.trim();
			log.println(token+")");
			v[idx] = (Double.valueOf(token)).doubleValue();
			log.println("Which translates to: "+v[idx]);
			idx++;
		}

		min_lat=v[0];
		min_lon=v[1];
		max_lat=v[2];
		max_lon=v[3];

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
		if (name.toLowerCase().indexOf("numeric") == -1)
			name += "_numeric";
		return name;
	}
	
	protected String getRemote()
	{
		URL foo;
		try {
			foo = new URL(tf.getText());
		} catch (MalformedURLException exception) {
			return ("ERROR:Malformed URL: "+tf.getText());
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
			log.println("Your PROCESSED custom string is: "+nbdpb.customChoice);

			//Go get the file
			try {
				String res;
				String serverURL = "http://";
				serverURL += serverName;
				serverURL += Config.get("tiles.cust.remoteget");
				String postData = (Main.USER == null ? "":"user="+Main.USER+
						"&passwd="+Util.urlEncode(Main.PASS))+"&name="+nbdpb.getBaseFileName()+
						"numeric=1&rfile=" + nbdpb.customChoice + "&lfile=" + nbdpb.getRemoteHashedName();

				this.setCursor(new Cursor(Cursor.WAIT_CURSOR));


				log.println("serverURL= "+serverURL);
				log.println("postData= "+postData);

				res = Util.httpPost(serverURL, postData);

				this.setCursor(Cursor.getDefaultCursor());

				return (res);
			}

			catch (Exception e)
			{
				return ("ERROR:Some Exception has occured (this shouldn't happen!)");
			}
		}
	}

	protected String sendLocal()
	{
		//see if local file exists...
		File f = new File(nbdpb.customChoice);
		if ( ! f.exists() )
			return ("ERROR:File doesn't exist");

		Main.setStatus("Uploading file to server, please wait....");
		Main.mainFrame.repaint();
		this.setCursor(new Cursor(Cursor.WAIT_CURSOR));

		int version = Config.get("tiles.version", 1);
		if (version == 2) {
			try {
				CustomMapServer cs = MapServerFactory.getCustomMapServer();
				cs.uploadCustomMap(getSafeName(f.getName()), f);
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
				serverURL += "/?numeric=1";

				if (Main.USER != null)
					serverURL += "&user=" + Main.USER + "&passwd=" + Util.urlEncode(Main.PASS) + "&name=" + nbdpb.getBaseFileName();

				log.println("Using: " + serverURL);

				String response = Util.httpPostFileName(serverURL, nbdpb.customChoice,  nbdpb.getRemoteHashedName());

				return (response);
			} catch (Exception e) {
				return ("ERROR:Some Exception has occured (this shouldn't happen!)");
			}
		}
	}

	public JFrame getThisParent() {
		return parent;
	}

	public NumBackPropertiesDiag(JFrame parent, boolean modal )
	{
		this(parent, modal, true);
	}

	public NumBackPropertiesDiag(JFrame parent, boolean modal, boolean visible)
	{
		super(parent,"Numerical Background Properties Dialog",modal);

		nbdpb=new NumBackDialogParameterBlock();

		this.parent=parent;

		if (!initDBConnection())
			log.println("DB access failed.  There will be NO custom lists added");

		cb = new JComboBox(masterItems);





		try
		{
			jbInit(); //Caned initialization ala JBuilder

			backDialogPropertiesBlockToSwing(); //unCanned initialization ala Ben

//			CallBack Set-up Here:
			cb.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					if (e.getStateChange() != ItemEvent.SELECTED)
						return;

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

					log.println("You chose item "+cb.getSelectedIndex()+
							" = "+(String)cb.getSelectedItem());

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

					FileDialog fdlg = new FileDialog(getThisParent(), "Select Custom Map File", FileDialog.LOAD);

					if ( startingDir != null )
						fdlg.setDirectory(startingDir);

					fdlg.setVisible(true);

					if ( fdlg.getFile() != null ) {
						startingDir = fdlg.getDirectory() ;
						tf.setText( fdlg.getDirectory() + fdlg.getFile());
					}
				}
			});

			if(visible)
			{
				setVisible(true);
				pack();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private void jbInit()
	{
//		nbdpb=new NumBackDialogParameterBlock();
		this.getContentPane().setLayout(new BorderLayout(10,10));

		JLabel l1 = new JLabel();
		JLabel l2 = new JLabel();
		JLabel l3 = new JLabel();
		JLabel l4 = new JLabel();
		JLabel l5 = new JLabel();

		l1.setText("Map source:");
		l1.setBorder(BorderFactory.createEmptyBorder(0,10,0,0));
		l2.setText("Custom Map (URL or Local File):");
		l2.setBorder(BorderFactory.createEmptyBorder(0,10,0,0));


		l3.setText("Map Server Address:");
		l3.setBorder(BorderFactory.createEmptyBorder(0,10,0,0));
		l4.setText("Max Agents:");
		l4.setBorder(BorderFactory.createEmptyBorder(0,10,0,0));
		l5.setText("Data Color:");
		l5.setBorder(BorderFactory.createEmptyBorder(0,10,0,0));

		tf.setEditable(false);
		tf.setHorizontalAlignment(SwingConstants.LEFT);
		browseButton.setEnabled(false);

		Box p2 = Box.createVerticalBox();

		JPanel p = new JPanel(new BorderLayout(10,10));
		p.setBorder(BorderFactory.createEmptyBorder(10,10,0,10));
		p.add(l1, BorderLayout.WEST);
		p.add(cb, BorderLayout.EAST);
		p2.add(p);

		p = new JPanel(new BorderLayout(10,10));
		p.setBorder(BorderFactory.createEmptyBorder(10,10,0,10));
		p.add(l2, BorderLayout.WEST);
		p.add(tf, BorderLayout.CENTER);
		p.add(browseButton, BorderLayout.EAST);
		p2.add(p);

		if (Main.isInternal()){
			p = new JPanel(new BorderLayout(10,10));
			p.setBorder(BorderFactory.createEmptyBorder(10,10,0,10));
			p.add(l3, BorderLayout.WEST);
			p.add(serverAddress,BorderLayout.CENTER);
			p2.add(p);

			p = new JPanel(new BorderLayout(10,10));
			p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
			p.add(l4, BorderLayout.WEST);
			p.add(maxAgents, BorderLayout.CENTER);
			p2.add(p);
		}
		
		p = new JPanel(new BorderLayout(10,10));
		p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		p.add(l5, BorderLayout.WEST);
		p.add(cc, BorderLayout.CENTER);
		p2.add(p);

		p = new JPanel(new BorderLayout(10,10));
		p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
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
		if (nbdpb==null)
			log.println("NULL!!!");
		else
			log.println(nbdpb);
		return(nbdpb);
	}


	public boolean Canceled()
	{
		return(canceled);
	}
}
