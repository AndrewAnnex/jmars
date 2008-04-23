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


package edu.asu.jmars.layer.util.features;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import java.sql.*;

import edu.asu.jmars.*;
import edu.asu.jmars.util.*;

import java.awt.geom.*;
import java.io.File;


/**
 * A class for saving or loading features from the ROI database.
 */
public class FeatureProviderROI implements FeatureProvider {
	private static DebugLog log = DebugLog.instance();
	
	public String getExtension () {
		return null;
	}

	public String getDescription () {
		return "ROI database";
	}

	public boolean isFileBased () {
		return false;
	}

	public static Field  ID           = new Field( "id",       String.class, false);
	public static Field  OWNER        = new Field( "owner",    String.class, false);
	public static Field  TITLE        = new Field( "title",    String.class, false);
	public static Field  DESCR        = new Field( "description",    String.class, false);
	public static Field  LS_LOW       = new Field( "ls_low",   String.class, false);
	public static Field  LS_HIGH      = new Field( "ls_high",  String.class, false);
	public static Field  LT_LOW       = new Field( "lt_low",   String.class, false);
	public static Field  LT_HIGH      = new Field( "lt_high",  String.class, false);
	public static Field  VIS_BAND     = new Field( "vis_band", String.class, false);
	public static Field  IR_BAND      = new Field( "ir_band",  String.class, false);
	public static Field  HAS_SP_INSTR = new Field( "has_sp_instr", Boolean.class, false); 
	public static Field  SP_INSTR     = new Field( "sp_instr", String.class, false);
	public static Field  HIGH_PRI     = new Field( "high_pri", Boolean.class, false); 
	public static Field  REPEAT       = new Field( "repeat", Boolean.class, false);

	public static int    ID_INDEX           = 0;
	public static int    OWNER_INDEX        = 1;
	public static int    TITLE_INDEX        = 2;
	public static int    DESCR_INDEX        = 3;
	public static int    LS_LOW_INDEX       = 4;
	public static int    LS_HIGH_INDEX      = 5;
	public static int    LT_LOW_INDEX       = 6;
	public static int    LT_HIGH_INDEX      = 7;
	public static int    VIS_BAND_INDEX     = 8;
	public static int    IR_BAND_INDEX      = 9;
	public static int    HAS_SP_INSTR_INDEX = 10;
	public static int    SP_INSTR_INDEX     = 11;
	public static int    HIGH_PRI_INDEX     = 12;
	public static int    REPEAT_INDEX       = 13;

	public static Field [] fieldArray = {
		ID,           
		OWNER,        
		TITLE,        
		DESCR,        
		LS_LOW,       
		LS_HIGH,      
		LT_LOW,       
		LT_HIGH,      
		VIS_BAND,     
		IR_BAND,      
		HAS_SP_INSTR, 
		SP_INSTR,     
		HIGH_PRI,     
		REPEAT,       
	};

	// database stuff.
	private static String dbUser              = Main.DB_USER;
	private static String dbUserPassword      = Main.DB_PASS;
	private static String dbDriver            = Config.getDbURL("db").driver;
	private static String dbHost              = Config.getDbURL("db").host;
	private static String dbDb                = Config.getDbURL("db").dbname;
	private static String dbUrl               = dbDriver + "://" + dbHost + "/" + dbDb + 
		"?user=" + dbUser + "&password=" + dbUserPassword;
	private static String userInfoTable       = "userinfo";
	private static String userClassColumnName = "class";
	private static String usernameColumn      = "username";
	private static String firstnameColumn     = "firstname";
	private static String lastnameColumn      = "lastname";
	private static String roiTable            = "roi";

	// the ROI data-structure.
	private class ROI {
		public FPath       poly;
		public String      id;
		public String      owner;
		public String      title;
		public String      description;
		public boolean     has_sp_instr = false;
		public String      sp_instr;
		public boolean     high_pri = false;

		// NOTE: ls_low, ls_high, lt_low, lt_high, repeat, vis_band, ir_band
		//    all have special "blank"-value significance. That is, when
		//    blank they have application defined defaults.
		public boolean     repeat;
		public String      vis_band;
		public String      ir_band;
		public String      ls_low, ls_high;     // L sub S range (can be blank)
		public String      lt_low, lt_high;     // local-time    (can be blank)
	}

	/**
	 * gets file(s) to load then reads in the features contained in those file(s).  
	 * Note that this process is put into its own thread to keep the potentially expensive
	 * action off the event handler.
	 */
	public FeatureCollection load(String name) {
		// verify the user is in the database.
		if (!userIsValid()) {
			return null;
		}

		// Build a FeatureCollection.
		SingleFeatureCollection fc = new SingleFeatureCollection();

		// Attach this as a listener on this collection. Subsequently saving
		// this collection will poll FC listeners for an ROIDeleteListener,
		// and if one is found, save() will perform the deletes.
		new ROIDeleteListener(fc);

		// get the users whose records will be fetched.
		ArrayList userList = getUserList();
			
		// Get all the records of the user(s).
		List rows = loadRecords( userList, fc);

		fc.addFeatures( rows);
		return fc;
	}

	// gets the records associated with all the users in the user list.
	// the records are returned as an array of Features.
	private java.util.List loadRecords( ArrayList userList, FeatureCollection fc){
		java.util.List featureList    = new ArrayList();
		
		
		// build the sql line from the userList.
		String     queryStr     = 
			"select * from "+ roiTable
			+ " where " + "owner in ( ";
		for (int i=0; i< userList.size(); i++){
			queryStr += " '" + userList.get(i) + "'";
			if (i< userList.size()-1){
				queryStr += ",";
			}
		}
		queryStr += " )"; 
		
		
		// get a connection to the database and apply the query to the table.
		try {
			Connection con = DriverManager.getConnection(dbUrl, dbUser, dbUserPassword);
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery( queryStr);
			while (rs.next()){
				// build a feature from the record
				Feature f = new Feature();
				f.setAttribute( Field.FIELD_FEATURE_TYPE, FeatureUtil.TYPE_STRING_POLYGON);
				f.setAttribute( Field.FIELD_PATH,     stringToFPath(rs.getString("poly")).getSpatialWest());
				f.setAttribute( Field.FIELD_SELECTED, Boolean.FALSE); 
	 			f.setAttribute( ID,            rs.getString("id"));
				f.setAttribute( OWNER,         rs.getString("owner"));
				f.setAttribute( TITLE,         rs.getString("title"));
				f.setAttribute( DESCR,         rs.getString("description"));
				f.setAttribute( LS_LOW,        rs.getString("ls_low"));
				f.setAttribute( LS_HIGH,       rs.getString("ls_high"));
				f.setAttribute( LT_LOW,        rs.getString("lt_high"));
				f.setAttribute( LT_HIGH,       rs.getString("lt_high"));
				f.setAttribute( VIS_BAND,      rs.getString("vis_band"));
				f.setAttribute( IR_BAND,       rs.getString("ir_band"));
				f.setAttribute( HAS_SP_INSTR, (rs.getBoolean("has_sp_instr")==true) ? Boolean.TRUE : Boolean.FALSE);
				f.setAttribute( SP_INSTR,      rs.getString("sp_instr"));
				f.setAttribute( HIGH_PRI,     (rs.getBoolean("high_pri")==true) ? Boolean.TRUE : Boolean.FALSE);
				f.setAttribute( REPEAT,	      (rs.getBoolean("repeat")==true) ? Boolean.TRUE : Boolean.FALSE);
				
				// add the feature to the list
				featureList.add( f);
			}
			con.close();
		}
		catch(SQLException ex){
			log.aprintln("Unable to get records from the database due to:\n     " + ex + "\n");
		}
		
		return featureList;
	}
	







	// panel stuff.  (Defined here because they are accessed by an inner class.)
	private ArrayList     userList    = new ArrayList();
	private static String []    columnNames = {"User name", "First name", "Last name"};
      	private SortingTable  userTable   = new SortingTable( columnNames);
  	private JDialog       userDialog  = new JDialog( (Frame)null, "ROI users", true);


	// Gets a list of users whose records will be retrieved.
	private ArrayList  getUserList() {


		// If the user is NOT a planner, they will just be allowed to 
		// fetch their own records.
		if (userIsPlanner()==false){
			userList.add( Main.DB_USER);
			return userList;
		}


		// User is a mission planner.  Get the list of users whose records will be fetched. 

		// Build a dialog that allows the user to select a list of users.
		JPanel    buttonPanel = new JPanel();
		JButton   okButton    = new JButton("OK");
		okButton.addActionListener(  new ActionListener() {
				public void actionPerformed(ActionEvent e){
					int [] rows = userTable.getSelectedRows();
					if (rows==null || rows.length<1){
						return;
					}
					userList.clear();
					for (int i=0; i< rows.length; i++){
						Object [] obj = userTable.getRow(rows[i]);
						userList.add( obj[0]);
					}
					userDialog.dispose();
				}
			});
		buttonPanel.add( okButton);
		JButton  cancelButton= new JButton("Cancel");
		cancelButton.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e){
					userList.add( Main.DB_USER);
					userDialog.dispose();
				}
			});
		buttonPanel.add( cancelButton);

		userDialog.getContentPane().setLayout( new BorderLayout());
		userDialog.getContentPane().add( userTable.getPanel(), BorderLayout.CENTER);
		userDialog.getContentPane().add( buttonPanel, BorderLayout.SOUTH);
		userDialog.pack();

		// Fill the table with names of the users contained in the userInfo table.
		try {
			Connection con = DriverManager.getConnection(dbUrl, dbUser, dbUserPassword);
			String     queryStr     = 
				"select " +  usernameColumn + "," + firstnameColumn + "," + lastnameColumn 
				+ " from " + userInfoTable;
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery( queryStr);
			while (rs.next()){
				Object [] row = { rs.getString(1), rs.getString(2), rs.getString(3)};
				userTable.addRow( row );
			}
			con.close();

			// show the dialog.  This blocks until the dialog is disposed.
			userDialog.setVisible(true);
		}
		catch(SQLException ex){
			log.aprintln("Unable to get column names due to:\n     " + ex + "\n");
		}

		return userList;
	}


    
	// returns whether the user is a mission planner.
	private boolean userIsPlanner(){
		Connection con          = null;
		String     queryStr     = 
			"select " +  userClassColumnName 
			+ " from " + userInfoTable 
			+ " where username like \'" + dbUser + "\'";
		String     userClassStr = null;
         
		try {
			con = DriverManager.getConnection(dbUrl, dbUser, dbUserPassword);
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery( queryStr);
			if (rs.next()){
				// If any data was returned, process it.
				userClassStr = rs.getString(userClassColumnName);

				if (rs.next()){
					// If more than one rows were returned, warn the user.
					String msg = "User "+ dbUser +" belongs to "+ 
						"more than one class."; 
					JOptionPane.showMessageDialog( 
								      null, msg, "Warning!", JOptionPane.WARNING_MESSAGE); 
				}
			}

			con.close();
		}
		catch(SQLException ex){
			log.aprintln(  "Unable determine user's class due to:\n     " + ex + "\n" +
				       "Defaulting to user-level access.");
			return false;
		}

		if (userClassStr.equals("MissionPlanner")){
			return true;
		} else {
			return false;
		}
	}



	// returns whether the user is in the database.
	private boolean userIsValid() {
		try {
			Connection con = DriverManager.getConnection(dbUrl);
			con.close();
			return true;
		}catch(SQLException ex){
			String msg = "Getting connection to database "+
				dbDb +
				" failed for user "+ dbUser + ".\n"+
				"Reason: SQLException: "+ ex.getMessage();
			JOptionPane.showMessageDialog( null, msg);
			return false;
		}
	}

	/*-----------------------------------*
	 *             saving                *
	 *-----------------------------------*/

	public boolean isRepresentable(FeatureCollection fc) {
		return true;
	}
	public File[] getExistingSaveToFiles(FeatureCollection fc, String name) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Finds the ROIDeleteListener for the given feature collection. Can only
	 * succeed if it's a SingleFeatureCollection.
	 */
	public ROIDeleteListener getDeleteListener(FeatureCollection fc) {
		if (fc instanceof SingleFeatureCollection) {
			Iterator delIt = ((SingleFeatureCollection)fc).getListeners().iterator();
			while (delIt.hasNext()) {
				FeatureListener l = (FeatureListener)delIt.next();
				if (l instanceof ROIDeleteListener) {
					return (ROIDeleteListener)l;
				}
			}
		}
		return null;
	}

	/**
	 * Writes polygon features out the specified file in ROI file format. 
	 */
	public int save(FeatureCollection fc, String name)
	{
		Connection con   = null;
		Statement  stmt  = null;
		String     roiId = "";

		try {
			// Open database connection
			con = DriverManager.getConnection(dbUrl);
			stmt = con.createStatement();

			// do any appropriate deletions.
			List deleteList = getDeleteListener(fc).getDeleteList();
			if (deleteList.size()>0) {
				String     deleteStr = "delete from "+ roiTable + " where " + "id in ( ";
				for (int j=0; j< deleteList.size(); j++){
					deleteStr += " '" + deleteList.get(j) + "'";
					if (j< deleteList.size()-1){
						deleteStr += ",";
					}
				}
				deleteStr += " )"; 
				
				// Do the deed.
				log.aprintln( deleteStr);
				stmt.executeUpdate( deleteStr);
				
				// If we got here, the delete succeeded, so flush the deletes
				// from the listener
				getDeleteListener(fc).clearDeleteList();
			}

			Iterator fi = fc.getFeatures().iterator();
			while (fi.hasNext()){
				Feature f = (Feature)fi.next();
				ROI roi  = new ROI();
				FPath swPath = (FPath)f.getAttribute(Field.FIELD_PATH);
				roi.poly = swPath.getSpatialEast();
				roi.id   =  (String)f.getAttribute( ID);
				roiId = roi.id;
				roi.owner = (String)f.getAttribute( OWNER);
				if (roi.owner ==null){
					roi.owner = dbUser;
				}
				roi.title =          (String)f.getAttribute( TITLE);
				roi.description =    (String)f.getAttribute( DESCR);
				roi.ls_low=          (String)f.getAttribute( LS_LOW);
				roi.ls_high =        (String)f.getAttribute( LS_HIGH);
				roi.lt_low=          (String)f.getAttribute( LT_LOW);
				roi.lt_high =        (String)f.getAttribute( LT_HIGH);
				roi.vis_band=        (String)f.getAttribute( VIS_BAND);
				roi.ir_band =        (String)f.getAttribute( IR_BAND);
				roi.has_sp_instr =   ((Boolean)f.getAttribute(HAS_SP_INSTR)).booleanValue();
				roi.sp_instr =       (String)f.getAttribute( SP_INSTR);
				roi.high_pri =       ((Boolean)f.getAttribute( HIGH_PRI)).booleanValue();
				roi.repeat =         ((Boolean)f.getAttribute( REPEAT)).booleanValue();

				// If any of the rows are new, insert them.
				if (roi.id == null){
					String addStmt    = createRoiStatement( "insert into ", roi);
					if (addStmt !=null){
						log.aprintln( addStmt);
						stmt.executeUpdate( addStmt);
					}
				}
				// A non-null ID means that these rows are loaded from the database. 
				// They should therefore be updated.
				else {
					String updateStmt = createRoiStatement( "update ", roi);
					if (updateStmt != null){
						updateStmt += " where id=\'" + roi.id + "\'";
						log.aprintln( updateStmt);
						stmt.executeUpdate( updateStmt);
					}
				}
			}
		} catch(SQLException ex){
			String msg = "Error occurred while commiting ROI "+
				roiId + ". See following message.\n" +
				ex.getClass().getName() + ": "+
				ex.getMessage();
			log.aprintln( msg);
		} 
		try {
			con.close();
		} catch ( SQLException sqlex) {}

		return fc.getFeatures().size();
	}

	private String createRoiStatement( String commandStr, ROI roi)
	{
		String stmt = commandStr + " " + roiTable + " set ";

		if (roi.poly == null){
			log.aprintln("no poly specified for id=" + roi.id );
			return null;
		} else {
			stmt += "poly=\'"          + fpathToString(roi.poly) + "\', ";
		}
		if (roi.title != null) {
			stmt += "title=\'"         + roi.title + "\',";
		}
		if (roi.description != null){
			stmt += "description=\'"   + roi.description + "\', ";
		}
		if(roi.ls_low!= null && !roi.ls_low.trim().equals("")){
			stmt += "ls_low=\'"  + roi.ls_low.trim() + "\', ";
		}
		if(roi.ls_high!=null && !roi.ls_high.trim().equals("")){
			stmt += "ls_high=\'" + roi.ls_high + "\', ";
		}
		if(roi.lt_low!=null && !roi.lt_low.trim().equals("")){
			stmt += "lt_low=\'"  + roi.lt_low + "\', ";
		}
		if(roi.lt_high!=null && !roi.lt_high.trim().equals("")){
			stmt += "lt_high=\'" + roi.lt_high + "\',";
		}
		stmt += "repeat="          + (roi.repeat? 1:0) + ", ";
		if (roi.vis_band != null){
			stmt += "vis_band=\'"        + roi.vis_band + "\', ";
		}
		if (roi.ir_band != null){
			stmt += "ir_band=\'"         + roi.ir_band + "\', ";
		}
		stmt += "has_sp_instr=" + (roi.has_sp_instr? 1:0) + ", ";
		if (roi.sp_instr != null){
			stmt += "sp_instr=\'"        + roi.sp_instr + "\', ";
		}
		stmt += "high_pri="        + (roi.high_pri? 1:0) + ", ";
		stmt += "last_modified=now(), ";
		stmt += "owner=\'"           + dbUser + "\' ";

		return stmt;
	}

	/*--------------------------------------------*
	 *           saving and loading               *
	 *--------------------------------------------*/

	/**
	 * Prints out the columns of the userInfoTable.
	 * This is for debugging purposes only.
	 */
	public void  debugTableToString() {
		try {
			Connection con = DriverManager.getConnection(dbUrl, dbUser, dbUserPassword);
			DatabaseMetaData dbmd = con.getMetaData();
			ResultSet rs = dbmd.getColumns( null, null, roiTable, null);

			while (rs.next()){
				log.aprintln( "col=" + rs.getString("COLUMN_NAME") + "\t" +  
						"type=" + rs.getString("DATA_TYPE"));
			}
			con.close();
		}
		catch(SQLException ex){
			log.aprintln("Unable to get column names due to:\n     " + ex + "\n");
		}
	}

	/**
	 * Returns an FPath from the given string of east-leading spatial
	 * coordinates.
	 */
	private FPath stringToFPath (String str)
	{
		StringTokenizer tokenizer = new StringTokenizer(str, "(),");
		List points = new ArrayList();
		while(tokenizer.hasMoreTokens()) {
			try {
				String tknX = tokenizer.nextToken();
				String tknY = tokenizer.nextToken();

				float x = Float.parseFloat(tknX);
				float y = Float.parseFloat(tknY);

				points.add (new Point2D.Float(x,y));
			} catch (NumberFormatException e) {
				log.println ("Unable to parse row: " + e.getMessage());
			}  catch (NoSuchElementException e) {
				log.println ("Missing data in row: " + e.getMessage());
			}
		}
		Point2D[] vertices = (Point2D[]) points.toArray(new Point2D[0]);
		return new FPath (vertices, FPath.SPATIAL_EAST, true);
	}

	// Create a string representation of a Polygon (encoded as a
	// General Path) parsable by string2GeneralPath() above.
	private String fpathToString(FPath path)
	{
		Point2D [] pts = path.getSpatialEast().getVertices();
		String[] strPoints = new String[pts.length];
		for (int i = 0; i < pts.length; i++)
			strPoints[i] = "(" + pts[i].getX() + "," + pts[i].getY() + ")";
		return Util.join(",", strPoints);
	}
}

/**
 * Listens to a ROI-generated FeatureCollection and tracks deletions.
 * When the user saves, the deletes will be retrieved. When the deletes
 * are succesfully processed, they will be cleared.
 */
class ROIDeleteListener implements FeatureListener {
	private ArrayList  deleteList = new ArrayList();

	public ROIDeleteListener (FeatureCollection fc) {
		fc.addListener(this);
	}

	public List getDeleteList() {
		return Collections.unmodifiableList(deleteList);
	}

	public void clearDeleteList() {
		deleteList.clear();
	}

	/**
	 * Implements the method mandated by the FeatureListener.
	 * This is the only FeatureProvider class that needs this.  It
	 * is specified so that the module can get notifications of database
	 * rows that have been deleted or undeleted. 
	 */
	public void receive( FeatureEvent fce){
		if (fce.type == FeatureEvent.REMOVE_FEATURE){
			for (int i=0; i< fce.features.size(); i++){
				Feature f = (Feature)fce.features.get(i);
				String id = (String)f.getAttribute(FeatureProviderROI.ID);
				if (id != null){
					deleteList.add(id);
				}
			}
		}

		if (fce.type == FeatureEvent.ADD_FEATURE){
			for (int i=0; i< fce.features.size(); i++){
				Feature f = (Feature)fce.features.get(i);
				String id = (String)f.getAttribute(FeatureProviderROI.ID);
				if (id != null){
					deleteList.remove(id);
				}
			}
		}
	}
}

