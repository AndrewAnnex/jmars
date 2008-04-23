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
import edu.asu.jmars.graphics.*;
import edu.asu.jmars.util.*;

import java.awt.geom.*;
import java.awt.image.*;
import java.sql.*;
import java.util.*;


/**
 * Class for loading Viking orbiter data from industry standard image
 * formats. 
 * 
 * @see edu.asu.jmars.layer.stamp.GenericImage
 * @see edu.asu.jmars.layer.stamp.VikingImageFactory
 * @author hoffj MSFF-ASU
 */
public class VikingImage extends GenericImage
{
    private static final DebugLog log = DebugLog.instance();
    private static final String dbUrl = Config.get("db");
    private static final String IMAGE_TYPE_NORMAL = "vik";
    private static final String IMAGE_TYPE_ROTATED = "vik_r180";
    private static final String IMAGE_TYPE_HFLIPPED = "vik_hflip";
    private static final String IMAGE_TYPE_VFLIPPED = "vik_vflip";
    
    private static final int MAX_FRAME_SIZE = 256;
    private static final int MAX_RENDER_PPD = 2048;
    private static final int MINIMUM_MAX_RENDER_PPD = 32;
    
    
    // The render PPD scale factor is computed as:
    //
    //   target_PPD * avg_image_side_length_in_world_coordinates
    //
    private static final double PPD_SCALE_FACTOR = (32 * 20);
    
    // Mapping from image state to image type label.
    private String[] imageTypeMap = new String[] {
                                                  IMAGE_TYPE_ROTATED,   // IMAGE_ROTATED_180
                                                  IMAGE_TYPE_HFLIPPED,  // IMAGE_HFLIPPED
                                                  IMAGE_TYPE_VFLIPPED,  // IMAGE_VLIPPED
                                                  IMAGE_TYPE_NORMAL     // IMAGE_NORMAL
    };
    
    
    static
    {
        Util.loadSqlDriver();
    }
    
    // Used only for internal purposes
    protected VikingImage()
    {
        IMAGE_TYPE = IMAGE_TYPE_NORMAL;
        IMAGE_FRAME_TYPE = IMAGE_TYPE_NORMAL;
    }
    
    public VikingImage(BufferedImage image, String filename)
    {
        try {
            IMAGE_TYPE = IMAGE_TYPE_NORMAL;
            IMAGE_FRAME_TYPE = IMAGE_TYPE_NORMAL;
            
            this.filename = filename;
            label = null;
            
            bandCount = 1;
            bandList = new ArrayList();
            bandList.add("Band 1");
            bandNumbers = new int[1];
            bandNumbers[0] = 1;
            
            images = new BufferedImage[bandCount];
            images[0] = image;
            storeCachedImage(0);
            
            log.println("Viking image created for: " + filename);
        }
        catch (Exception e) {
            log.aprintln("During Viking image creation...");
            log.aprintln(e);
        }
    }

    private HashMap pointsCache = new HashMap();
    
    synchronized public Point2D[] getPoints()
    {
        return getPoints(true);
    }
    
    /**
     ** @param cacheResults If <code>true</code>, then the results
     ** of this call will be stored for any future calls, including
     ** this call if a result is already cached.  If <code>false</code>, any result
     ** which has not already been cached will not be cached.
     **/
    synchronized private Point2D[] getPoints(boolean cacheResults)
    {
        Point2D[] points = null;
        
        // DB east lon => JMARS west lon for the 'viking_geometry' table.
        String query = "select (360 - dn_lt_lon) as dn_lt_lon, dn_lt_lat, (360 - dn_rt_lon) as dn_rt_lon, dn_rt_lat, " +
                       "(360 - up_lt_lon) as up_lt_lon, up_lt_lat, (360 - up_rt_lon) as up_rt_lon, up_rt_lat, " +
                       "!isnull(up_lt_lon) as has_geometry, orbit_number " + 
                       "from viking_idx inner join viking_geometry on viking_idx.image_id = viking_geometry.image_id " +
                       "inner join viking_status on viking_idx.image_id = viking_status.image_id " +
                       "and ifnull(useable, 0) and ifnull(gif, 0) " +
                       "where viking_idx.image_id='" + filename + "'";
        
        if (pointsCache != null &&
            pointsCache.get(query) != null)
        {
            log.println("returning cached points");
            return (Point2D[]) pointsCache.get(query);
        }

        ResultSet rs = null;
        try {
            log.println("getting new points");
            rs= getPoints(query);
            
            if (rs != null) {
                // There should be only one result set....
                if(rs.next()) {
                    if (rs.getBoolean("has_geometry")) {
                        // Create an array of geometry points for whole image only.
                        Point2D[] imagePts = new Point2D[4];
                        if (imagePts != null)
                        {
                            imagePts[0] = new Point2D.Double(rs.getDouble("dn_lt_lon") % 360,
                                                             rs.getDouble("dn_lt_lat"));
                            imagePts[1] = new Point2D.Double(rs.getDouble("dn_rt_lon") % 360,
                                                             rs.getDouble("dn_rt_lat"));
                            imagePts[2] = new Point2D.Double(rs.getDouble("up_lt_lon") % 360,
                                                             rs.getDouble("up_lt_lat"));
                            imagePts[3] = new Point2D.Double(rs.getDouble("up_rt_lon") % 360,
                                                             rs.getDouble("up_rt_lat"));
                            
                            // Apply current image orientation to corner points
                            imagePts = getOrientedPoints(imagePts);
                            
                            // Create geometry points for an approximate set of image subframes
                            // based on the whole image frame.  We are using this process only
                            // for image frame caching performance and human factors reasons until
                            // or unless better geometry information becomes available in the Viking stamp
                            // database.
                            UniformImage uImg = getUniformImage(0);
                            if (uImg != null)
                            {
                                try {
                                    double frameSizeFactor = (double)getFrameSize() / uImg.getHeight();
                                    points = getFakeFramePoints(imagePts, frameSizeFactor);
                                }
                                catch (Exception e) {
                                    // This should never happen....
                                    log.aprintln("while getting image height: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
                
                if (points == null)
                    log.aprintln("no geometry points");
                else if (points.length % 4 != 0)
                    log.aprintln("Bad geometry points count: " + points.length);
                else if (cacheResults) {
                    pointsCache.put(query, points);
                    log.println("points length = " + points.length);
                }
            }
        }
        catch(SQLException e) {
            log.aprintln("Database error!");
            log.aprintln(e);
            return  null;
        }
        finally {
            if (rs != null)
                try {
                    // Release result set; this should also release the database
                    // connection.
                    //
                    // This addresses a possible problem with THEMIS database
                    // server building up a backlog of stale connections.  Don't
                    // whether this code has contributed to the problem or not,
                    // but just to be safe....
                    rs.close();
                }
            catch (SQLException e) {
                log.aprintln(e);
            }
            finally {
                rs = null;
            }
        }
        
        return points;
    }
    
    
    /**
     *  @param query must specify a select command that returns a single-record result set
     */
    protected ResultSet getPoints(String query)
    {
        ResultSet rs = null;
        
        try {
            log.println("filename = " + filename);
            rs = DriverManager
            .getConnection(dbUrl +
                           "user=" + Main.DB_USER +
                           "&password=" + Main.DB_PASS)
                           .createStatement()
                           .executeQuery(query);
        }
        catch(SQLException e) {
            log.aprintln("Database error!");
            log.aprintln(e);
            return  null;
        }
        
        return rs;
    }
    
    public void clearPointsCache()
    {
        pointsCache.clear();
    }
 
    
    /** 
     * Override of superclass implementation to provide a 
     * {@link UniformImage} instance which masks unnecessary
     * border columns from source image.  Masked region is
     * made transparent.
     *  
     * @see GenericStampImage#getUniformImage(int)
     */
    protected UniformImage getUniformImage(int band)
    {
        if (band >= bandCount)
            log.aprintln("invalid band number: " + band);
        
        if (uniformImages == null)
            uniformImages = new UniformImage[bandCount];
        
        try {
            // Create image with Viking columns 1-23 and 1179-1204 (counting from 1)
            // masked into transparent form.
            if (uniformImages[band] == null)
                uniformImages[band] = 
                    new CachedUniformImage(getImage(band), getCachedImageName(band))
                    {
                        public int getRGB(int x, int y)
                        throws Exception
                        {
                            // Check for image via getImage() to ensure it is loaded properly.
                            BufferedImage image = getImage();                           
                            if (image != null) {
                                // Adjusted to internal ZERO-based columns, blank
                                // region in columns 0-22 and 1178-1203
                                int rgb = super.getRGB(x, y);
                                if (x < 23 || x > 1177)
                                    return rgb & 0xFFFFFF; 
                                else
                                    return rgb;
                            }
                            else
                                throw new Exception("no contained image");
                        }
                    };
        }
        catch(Throwable e) {
            log.aprintln("Failed to create image due to:");
            log.aprintln(e);
            return  null;
        }
        
        return  uniformImages[band];
    }
    
    /**
     * Returns a capped default image frame size suitable for handling
     * larger images.
     * 
     * @see edu.asu.jmars.layer.stamp.StampImage#getFrameSize()
     */
    protected int getFrameSize()
    {
        return Math.min(MAX_FRAME_SIZE, super.getFrameSize());
    }
    
    private int maxRenderPPD = 0;
    
    /**
     * Returns maximum rendering resolution for image based on 
     * planetary image size as measured in world coordinates.
     * Image stamps with larger planetary projections have a 
     * smaller maximum resolution so as to reduce memory and
     * performance problems during rendering. 
     *  
     * @see edu.asu.jmars.layer.stamp.StampImage#getMaxRenderPPD()
     */
    protected int getMaxRenderPPD()
    {
        // If maximum render solution value has not been cached, compute 
        // and store it.
        if (maxRenderPPD <= 0) {
            int max = MAX_RENDER_PPD;
            
            final Point2D[] pts = getPoints();
            if (pts != null) {
                // Compute average side length in world coordinates.
                GridDataStore.Cell imageCell = new GridDataStore.Cell(
                                                                 new HVector(pts[0]),
                                                                 new HVector(pts[1]),
                                                                 new HVector(pts[3]),
                                                                 new HVector(pts[2]));
                Rectangle2D worldRect = imageCell.getWorldBounds();
                double avgSide = (worldRect.getWidth() + worldRect.getHeight()) / 2;
                
                // Scale maximum resolution according to average side length.
                if (avgSide > 0) {
                    double trialMax = Math.max(MINIMUM_MAX_RENDER_PPD,  
                                               Math.min(MAX_RENDER_PPD, PPD_SCALE_FACTOR / avgSide));
                    
                    // Set resolution to nearest power of 2.  The notion of
                    // "nearest" here is a little fuzzy since we perform rounding
                    // in log-domain (imprecision is OK for this purpose).
                    long nearestLog2 = Math.round(Math.log(trialMax) / Math.log(2));
                    
                    max = 1 << nearestLog2;
                    max = Math.max(MINIMUM_MAX_RENDER_PPD,  
                                   Math.min(MAX_RENDER_PPD, max));
                }
            }
            
            log.println("set max resolution to " + max + " for stamp image " + filename);
            maxRenderPPD = max;
        }
            
        return maxRenderPPD;
    }
    
    /** 
     ** Returns image frame label for specified stamp image orientation
     **
     ** @param imageState legal state values are {@link #IMAGE_NORMAL},
     ** {@link #IMAGE_ROTATED_180}, {@link #IMAGE_HFLIPPED}, and {@link #IMAGE_VFLIPPED}.
     **/
    protected String getImageFrameType(int imageState)
    {
        if (imageState != IMAGE_NORMAL &&
            imageState != IMAGE_ROTATED_180 &&
            imageState != IMAGE_HFLIPPED &&
            imageState != IMAGE_VFLIPPED) {
            log.aprintln("illegal rotate/flip image state: " + imageState);
            return "";
        }
        
        return imageTypeMap[imageState];
    }
    
}
