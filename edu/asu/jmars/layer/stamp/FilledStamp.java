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


package edu.asu.jmars.layer.stamp;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.HVector;

import java.awt.geom.*;
import java.io.*;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


public class FilledStamp
{
	private static DebugLog log = DebugLog.instance();

    public Stamp stamp;
    public StampImage pdsi;
    /** Offset in east-lon ocentric-lat */
    private Point2D offset = null;
    public int band = -1;
    public ColorMapper.State colors = ColorMapper.State.DEFAULT;

    private static final String dbUrl = Config.get("stamps.offsetdb");
    
    public ColorMapOp getColorMapOp()
    {
	return  colors.getColorMapOp();
    }

    public FilledStamp(Stamp stamp, StampImage pdsi)
    {
		this.stamp = stamp;
		this.pdsi = pdsi;
		
		offset = loadOffset();
    }

    public FilledStamp(Stamp stamp, StampImage pdsi, State state)
    {
		this.stamp = stamp;
		this.pdsi = pdsi;

		offset = loadOffset();
		
		if (state != null) {
		    band = state.band;

		    // Slightly bizarre means for restoring colors since our
		    // internal representation cannot be serialized.
		    ColorMapper mapper = new ColorMapper();
		    ByteArrayInputStream buf = new ByteArrayInputStream(state.colorMap);
		    if (mapper != null &&
			buf != null) {
			try {
			    mapper.loadColors(buf);
			    colors = mapper.getState();
			}
			catch (Exception e) {
			    // ignore
			}
		    }
		}
    }
    
    /**
	 * Return the world coordinate offset that moves the stamp center to the
	 * image center
	 */
    public Point2D getOffset() {
    	Point2D spatialStamp = stamp.getCenter();
    	Point2D spatialImage = add(spatialStamp, offset);
    	Point2D worldStamp = Main.PO.convSpatialToWorld(spatialStamp);
    	Point2D worldImage = Main.PO.convSpatialToWorld(spatialImage);
    	return sub(worldImage, worldStamp);
    }
    
    /**
     * Set the world coordniate offset that moves the stamp center to the
     * image center.
     */
    public void setOffset(Point2D worldOffset) {
    	Point2D spatialStamp = stamp.getCenter();
    	Point2D worldStamp = Main.PO.convSpatialToWorld(spatialStamp);
    	Point2D worldImage = add(worldStamp, worldOffset);
    	Point2D spatialImage = Main.PO.convWorldToSpatial(worldImage);
    	offset = sub(spatialImage, spatialStamp);
    }
    
    private Point2D add(Point2D p1, Point2D p2) {
    	return new Point2D.Double(p1.getX() + p2.getX(), p1.getY() + p2.getY());
    }
    
    private Point2D sub(Point2D p1, Point2D p2) {
    	return new Point2D.Double(p1.getX() - p2.getX(), p1.getY() - p2.getY());
    }
    
    private Point2D.Double loadOffset() {
    	java.sql.Connection conn = null;

    	String dbUserName = Main.DB_USER;
    	String dbPassword = Main.DB_PASS;

    	// This is the id that this offset will be stored with
    	String userID = Main.USER;

    	double x = 0.0;
    	double y = 0.0;
    	
    	try
    	{
    		conn = DriverManager.getConnection(dbUrl, dbUserName, dbPassword);

    		Statement s = conn.createStatement();

    		String queryStr = "select xOffset, yOffset from themis2.stamps_offset where userid = '"+userID+"' and stampid='"+stamp.id+"'";

    		log.println(queryStr);
    		ResultSet rs = s.executeQuery(queryStr);

    		if (rs.next()) {
    			x = rs.getDouble("xOffset");
    			y = rs.getDouble("yOffset");
    			log.println("Previous values set");
    		} else {
    			log.println("No prestored values found");
    		}    		
    	}
    	catch(SQLException sqle)
    	{
    		log.aprintln(sqle.getMessage());
    	}
    	finally
    	{
    		// Should this be closed every time, or returned?
    		try { conn.close(); } catch(Throwable exception) { }
    	}
    	
    	return new Point2D.Double(x,y);
    }
    
    public void saveOffset() {
    	stampUpdates.add(this);
    	lastOffsetUpdateTime = System.currentTimeMillis();
    }
    
    private void addSaveStatement(Statement s) throws SQLException {    	
		// This is the id that this offset will be stored with
		String userID = Main.USER;
		String queryStr = "replace into themis2.stamps_offset (stampid, userid, xoffset, yoffset) " +
				"values ('" + stamp.id + "', '"+userID+"', "+offset.getX()+","+offset.getY()+")";
		log.println(queryStr);
		s.addBatch(queryStr);
    }
    
    static Timer saveTimer = new Timer("Stamp Save Timer");
    static TimerTask timerTask = null;
    static long lastOffsetUpdateTime = Long.MAX_VALUE;
    
    static Set<FilledStamp> stampUpdates = new HashSet<FilledStamp>(20);
    
    static {    	    	
    	timerTask = new TimerTask() {	
 			public void run() {
 				
 				// Wait until 10 seconds after the last update to commit offset
 				// values to the database.
 				if (System.currentTimeMillis()-lastOffsetUpdateTime < 10000) {
 					log.println("FilledStamp TimerTask not running yet...");
 					return;
 				}
 				
 				if (stampUpdates.size()==0) return;
 				
				log.println("FilledStamp TimerTask Running...");
				
				java.sql.Connection conn = null;
				
				String dbUserName = Main.DB_USER;
				String dbPassword = Main.DB_PASS;
				
				try
				{
					conn = DriverManager.getConnection(dbUrl, dbUserName, dbPassword);
					
					Statement s = conn.createStatement();
					
					for(FilledStamp stamp : stampUpdates) {				
						stamp.addSaveStatement(s);
					} 

					s.executeBatch();
		   			stampUpdates.clear();
		   			lastOffsetUpdateTime = System.currentTimeMillis();
				}
				catch(SQLException sqle)
				{
					log.aprintln(sqle.getMessage());
				}
				finally
				{
					try { conn.close(); } catch(Throwable exception) { }
				}				
			}			
		};
    	
    	saveTimer.schedule(timerTask, 10000, 10000);
    }
    
    public State getState()
    {
	State state = new State();
	
	if (stamp != null)
	    state.id = stamp.id;

	state.band = band;

	// Slightly bizarre means for storing colors since our
	// internal representation cannot be serialized.
	ColorMapper mapper = new ColorMapper();
	ByteArrayOutputStream buf = new ByteArrayOutputStream();
	if (mapper != null &&
	    buf != null) {
	    try {
		mapper.setState(colors);
		mapper.saveColors(buf);
		state.colorMap = buf.toByteArray();
	    }
	    catch (Exception e) {
		// ignore
	    }
	}

	return state;
    }

    /**
     ** Minimal description of state needed to recreate
     ** a FilledStamp.
     **/
    public static class State implements SerializedParameters
    {
	String id;
	int    band;
	byte[] colorMap;
    }
}
