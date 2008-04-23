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

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

public abstract class StampLayer extends Layer
{
    private static DebugLog log = DebugLog.instance();
    
    private boolean isBadLayer = false;
    
    public abstract Class[] getColumnClasses();
    public abstract String[] getColumnNames();
    
    /**
     * Returns stamp data for this layer instance.
     */
    protected abstract Stamp[] getStamps();
    
    /**
     * Returns stamp from layer corresponding to specified stamp ID.
     * 
     * @return Returns <code>null</code> if stamp does not exist,
     * stamp data is unavailable, or layer is in a bad state.
     *
     * @see #isBad
     */
    protected abstract Stamp getStamp(String stampID);
    
    /**
     * Returns whether or not layer is in a usable state.  If
     * not, it should not be used to create {@link Layer.LView} 
     * instances.
     *
     * @return <code>true</code>, if layer is in a bad state
     * (usually because of some irrecoverable data retrieval
     * error); <code>false</code>, if layer is in a usable
     * state.
     *
     * @see #markBad
     */
    public boolean isBad()
    {
        return isBadLayer;
    }  
    
    /**
     * Sets whether or not layer is in a usable state.  Marking
     * layer as bad means it should not be used to create {@link Layer.LView} 
     * instances.
     *
     * @param isBad Should be set as follows: <code>true</code>, if layer is 
     * in a bad state (usually because of some irrecoverable data retrieval
     * error); <code>false</code>, if layer is in a usable state.
     *
     * @see #isBad
     */
    protected void markBad(boolean isBad)
    {
        isBadLayer = isBad;
    }
    
    public void receiveRequest(Object layerRequest,
                               DataReceiver requester)
    {
        if (layerRequest == null)
        {
            log.aprintln("RECEIVED NULL REQUEST");
            return;
        }
        
        if (layerRequest instanceof Rectangle2D)
            receiveAreaRequest((Rectangle2D) layerRequest, requester);
        
        else if (layerRequest instanceof Stamp)
            receiveStampRequest((Stamp) layerRequest, requester);
        
        else
            log.aprintln("BAD REQUEST CLASS: " +
                         layerRequest.getClass().getName());
    }
    
    private void receiveStampRequest(Stamp stamp,
                                     DataReceiver requester)
    {
        /*
         log.println("stamp.id = " + stamp.id);
         String loc = getStampURL(stamp);
         log.println("loc url = " + loc);
         try
         {
         if (loc != null)
         stamp.image = Util.loadBufferedImage(new URL(loc));
         }
         catch(MalformedURLException e)
         {
         log.aprintln("BAD URL [" + loc + "]");
         }
         
         if (stamp.image == null)
         log.aprintln("Unable to fill " + stamp.id);
         else
         requester.receiveData(stamp);
         */
    }
    
    private void receiveAreaRequest(Rectangle2D where,
                                    DataReceiver requester)
    {
        Stamp[] stamps = getStamps();
        
        if (Main.inTimeProjection())
        {
            requester.receiveData(stamps);
            return;
        }
        
        setStatus(Color.yellow);
        
        log.println("where = " + where);
        
        double x = where.getX();
        double y = where.getY();
        double w = where.getWidth();
        double h = where.getHeight();
        if (w >= 360)
        {
            x = 0;
            w = 360;
        }
        else
            x -= Math.floor(x/360.0) * 360.0;
        
        Rectangle2D where1 = new Rectangle2D.Double(x, y, w, h);
        Rectangle2D where2 = null;
        
        // Handle the two cases involving x-coordinate going past
        // 360 degrees:
        // Area rectangle extends past 360...
        if (where1.getMaxX() >= 360) {
            where2 = new Rectangle2D.Double(x-360, y, w, h);
            log.println("where2 = " + where2);
        }
        // Normalized stamp extends past 360 but
        // where rectangle does not...
        else if (where1.getMaxX() <= 180) {
            where2 = new Rectangle2D.Double(x+360, y, w, h);
            log.println("where2 = " + where2);
        }
        
        log.println("where1 = " + where1);
        log.println("where2 = " + where2);
        
        ArrayList data =
            new ArrayList((int)Math.ceil(
                                         Math.max(stamps.length,
                                                  stamps.length
                                                  * where.getHeight() * where.getWidth()
                                                  / (180.0 * 360.0))
            ));
        
        Shape path;
        for (int i=0; i<stamps.length; i++)
        {
            path = stamps[i].getNormalPath();
            if (path != null &&
                ( path.intersects(where1) ||
                  (where2 != null && path.intersects(where2))))
                data.add(stamps[i]);
        }
        
        requester.receiveData(data.toArray(new Stamp[data.size()]));
    }
    
    /**
     ** @deprecated Don't use, but please save.
     **/ 
    static String getStampURL(Stamp stamp)
    {
        if (true) throw  new Error("DEPRECATED... but please save for now!");
        
        int slash = stamp.id.indexOf("/");
        if (slash == -1)
        {
            log.aprintln("BAD STAMP ID: " + stamp.id);
            return  null;
        }
        String phase = stamp.id.substring(0, slash);
        String id = stamp.id.substring(slash+1);
        
        if (phase.compareTo("M07") < 0)
            return
                Config.get("stamps.moc.loc") + "/ab1_m04/nonmaps/" +
                phase + id + ".gif";
        
        else if (phase.compareTo("M13") < 0)
            return
                Config.get("stamps.moc.loc") + "/m07_m12/" + phase + "/" +
                phase + id + ".gif";
        
        else if (phase.compareTo("M19") < 0)
            return
                Config.get("stamps.moc.rem") + "/m13_m18/full_gif_non_map/" +
                phase + "/" + phase + id + ".gif";
        
        else if (phase.compareTo("M24") < 0)
            return
                Config.get("stamps.moc.rem") + "/m19_m23/full_jpg_non_map/" +
                phase + "/" + phase + id + ".jpg";
        
        log.aprintln("BAD STAMP ID: " + stamp.id);
        return  null;
        /*
         http://www.msss.com/moc_gallery/m19_m23/full_jpg_non_map/M19/M1901071.jpg
         http://www.msss.com/moc_gallery/m19_m23/full_gif_non_map/M19/M1901071.gif
         http://www.msss.com/moc_gallery/m07_m12/nonmaps/M08/M0806190.gif
         http://www.msss.com/moc_gallery/m13_m18/full_gif_non_map/M15/M1500835.gif
         -04
         07-12
         13-18
         19-23
         */
    }
}
