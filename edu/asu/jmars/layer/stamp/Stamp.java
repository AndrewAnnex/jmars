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

import java.awt.*;
import java.awt.geom.*;
import java.net.*;

import edu.asu.jmars.*;
import edu.asu.jmars.util.*;

public class Stamp
{
    private static DebugLog log = DebugLog.instance();
    
    private static final String IR_PREFIX  = "I";
    private static final String VIS_PREFIX = "V";
    
    public String id;
    public URL pdsImageUrl;  // name preserved for historical reasons at moment.
    public Object[] data;
    private double[] pts;
    private double[] ptw;
    private Point2D nw;
    private Point2D ne;
    private Point2D sw;
    private Point2D se;
    GeneralPath path;
    
    Stamp(String id)
    {
        this(id,
             0, 0,
             0, 0,
             0, 0,
             0, 0,
             null);
    }
    
    public Stamp(String id,
          double nwx, double nwy,
          double nex, double ney,
          double swx, double swy,
          double sex, double sey,
          Object[] data)
    {
        this.id = id;
        this.data = data;
        
        pts = new double[] { nwx, nwy, nex, ney, sex, sey, swx, swy };
        nw = new Point2D.Double(nwx, nwy);
        ne = new Point2D.Double(nex, ney);
        sw = new Point2D.Double(swx, swy);
        se = new Point2D.Double(sex, sey);
    }
    
    public final synchronized void clearProjectedData()
    {
        path = null;
        normalPath = null;
        bounds = null;
    }
    
    void dump()
    {
        log.println(id + " spatial ------");
        for(int i=0; i<pts.length; i+=2)
            log.println(pts[i] + "\t" + pts[i+1]);
        log.println(id + " world ------");
        for(int i=0; i<ptw.length; i+=2)
            log.println(ptw[i] + "\t" + ptw[i+1]);
    }
    
    public boolean isIR()
    {
        return id.toUpperCase().startsWith(IR_PREFIX.toUpperCase());
    }
    
    public boolean isVisible()
    {
        return id.toUpperCase().startsWith(VIS_PREFIX.toUpperCase());
    } 
    
    public String toString()
    {
        return  id;
    }
    
    public int hashCode()
    {
        return  id.hashCode();
    }
    
    public boolean equals(Object obj)
    {
        return  obj instanceof Stamp
                &&  ( (Stamp) obj ).id.equals(this.id);
    }
    
    
    
    /**
     * Returns the NE stamp corner spatial coordinates as
     * a point in degrees: x = longitude, y = latitude.
     */
    public Point2D getNE()
    {
        return ne;
    }
    
    /**
     * Returns the NW stamp corner spatial coordinates as
     * a point in degrees: x = longitude, y = latitude.
     */
    public Point2D getNW()
    {
        return nw;
    }
    
    /**
     * Returns the SE stamp corner spatial coordinates as
     * a point in degrees: x = longitude, y = latitude.
     */
    public Point2D getSE()
    {
        return se;
    }
    
    /**
     * Returns the SW stamp corner spatial coordinates as
     * a point in degrees: x = longitude, y = latitude.
     */
    public Point2D getSW()
    {
        return sw;
    }
    
    Point2D centerPoint=null;
    
    /**
	 * Calculates and returns the center point (in lon/lat) for this stamp by
	 * averaging the 4 corner points
	 */
    public Point2D getCenter() {
    	if (centerPoint==null) {
    		HVector corner = new HVector(nw).unit();
    		corner.add(new HVector(ne).unit());
    		corner.add(new HVector(sw).unit());
    		corner.add(new HVector(se).unit());
    		corner.div(4.0);
    		
    		centerPoint = corner.toLonLat(null);    		
    	}
    	
    	return centerPoint;
    }
    
    private Rectangle2D bounds;
    public synchronized Rectangle2D getBounds2D()
    {
        if(bounds != null)
            return  bounds;
        
        bounds = getPath().getBounds2D();
        double w = bounds.getWidth();
        if(w <= 180)
            return  bounds;
        
        double x = bounds.getX();
        double y = bounds.getY();
        double h = bounds.getHeight();
        
        x += w;
        w = 360 - w;
        
        bounds.setFrame(x, y, w, h);
        return  bounds;
    }
    
    /**
     ** Returns a (cached) normalized version of the stamp's path.
     ** @see Util#normalize360
     **/
    private Shape normalPath;
    public synchronized Shape getNormalPath()
    {
        if(normalPath == null)
            normalPath = Util.normalize360(getPath());
        return  normalPath;
    }
    
    public synchronized GeneralPath getPath()
    {
        if(path == null)
        {
            path = new GeneralPath();
            Point2D pt;
            
            ptw = new double[8];
            
            pt = Main.PO.convSpatialToWorld(pts[0],
                                            pts[1]);
            ptw[0] = pt.getX();
            ptw[1] = pt.getY();
            
            pt = Main.PO.convSpatialToWorld(pts[2],
                                            pts[3]);
            ptw[2] = pt.getX();
            ptw[3] = pt.getY();
            
            pt = Main.PO.convSpatialToWorld(pts[4],
                                            pts[5]);
            ptw[4] = pt.getX();
            ptw[5] = pt.getY();
            
            pt = Main.PO.convSpatialToWorld(pts[6],
                                            pts[7]);
            ptw[6] = pt.getX();
            ptw[7] = pt.getY();
            
            path.moveTo((float)ptw[0],
                        (float)ptw[1]);
            path.lineTo((float)ptw[2],
                        (float)ptw[3]);
            path.lineTo((float)ptw[4],
                        (float)ptw[5]);
            path.lineTo((float)ptw[6],
                        (float)ptw[7]);
            path.closePath();
        }
        return  path;
    }
    
    /**
     * Returns URL string for stamp browse information.
     * 
     * @return String text in URL format.
     */
    public String getBrowseUrl()
    {
        return  null;
    }
    
    public String getPopupInfo(boolean showAsHTML) {
        
        String info = "";
        
        if ( showAsHTML )
            info += "<html>";
        
        try {
            
            info += "ID: ";
            info += id;
            /*
             if ( showAsHTML )
             info += "<br>";
             else
             info += " ";
             
             
             info += "ET: ";
             info += data[16];
             
             if ( showAsHTML )
             info += "<br>";
             else
             info += " ";
             
             
             info += "Orbit: ";
             info += data[15];
             
             if ( showAsHTML )
             info += "<br>";
             else
             info += " ";
             
             info += "status: ";
             info += data[18];
             
             if ( showAsHTML )
             info += "<br>";
             else
             info += " ";
             
             info += data[14];
             
             */
        } 
        catch(Exception ex) {
            //ignore
        }
        
        if ( showAsHTML )
            info += "</html>";
        
        return info;
    }
}
