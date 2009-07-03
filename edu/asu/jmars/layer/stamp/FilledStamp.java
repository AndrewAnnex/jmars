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

import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.swing.ColorMapOp;
import edu.asu.jmars.swing.ColorMapper;
import edu.asu.jmars.util.DebugLog;


public class FilledStamp
{
	private static DebugLog log = DebugLog.instance();

    public StampShape stamp;
    public StampImage pdsi;
    
    /** Offset in east-lon ocentric-lat */
    private Point2D offset = null;
    public ColorMapper.State colors = ColorMapper.State.DEFAULT;

    public ColorMapOp getColorMapOp()
    {
	return  colors.getColorMapOp();
    }

    public String toString() {
    	return stamp.getId() + " : " + pdsi.imageType;
    }
    
    public FilledStamp(StampShape stamp, StampImage pdsi, State state)
    {
		this.stamp = stamp;
		this.pdsi = pdsi;

		offset = loadOffset();
		
		if (state != null) {
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
    	// This is the id that this offset will be stored with
    	String userID = Main.USER;

    	Point2D.Double offset = null;
    	
    	try
    	{
    		
            URL url = new URL(StampLayer.stampURL+"OffsetFetcher?user="+userID+"&stamp="+stamp.getId()+StampLayer.versionStr);
                            
            ObjectInputStream ois = new ObjectInputStream(url.openStream());

            double pt[] = (double[]) ois.readObject();
            offset = new Point2D.Double(pt[0], pt[1]);
            ois.close();
    	}
    	catch(Exception e)
    	{
    		log.aprintln(e.getMessage());
    	}
    	
    	return offset;
    }
    
    public void saveOffset() {
    	stampUpdates.add(this);
    	lastOffsetUpdateTime = System.currentTimeMillis();
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
								
				try
				{
					for(FilledStamp stamp : stampUpdates) {				
						String updateStr = StampLayer.stampURL+"OffsetUpdater?user="+Main.USER+"&stamp="+stamp.stamp.getId()+StampLayer.versionStr;
						
						updateStr += "&xoffset="+stamp.offset.getX()+"&yoffset="+stamp.offset.getY();
		 	            URL url = new URL(updateStr);

		 	            url.openStream();		 	            
					} 
					
		   			stampUpdates.clear();
		   			lastOffsetUpdateTime = System.currentTimeMillis();
				}
				catch(Exception e)
				{
					log.aprintln(e.getMessage());
				}
			}			
		};
    	
    	saveTimer.schedule(timerTask, 10000, 10000);
    }
    
    public State getState()
    {
		State state = new State();
		
		if (stamp != null)
		    state.id = stamp.getId();
	
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

		state.imageType = pdsi.imageType;
		return state;
    }

    /**
     * Minimal description of state needed to recreate
     * a FilledStamp.
     */
    public static class State implements SerializedParameters {
    	private static final long serialVersionUID = -2396089407110933527L;
    	String id;
    	byte[] colorMap;
    	String imageType;
    	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    		ois.defaultReadObject();
    	}
    	
    	public String getImagetype() {
    		return imageType;
    	}
    	
    	public void setImageType(String newType) {
    		imageType=newType;
    	}
    }
}
