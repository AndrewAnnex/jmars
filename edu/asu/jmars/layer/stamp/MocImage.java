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
import edu.asu.jmars.util.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.sql.*;
import java.util.*;


/**
 * Note: this subclass does not use the "images" member variable.
 * All methods in superclass which use this variable are overridden
 * to support a special tile-based image caching mechanism.
  * 
 * @author hoffj MSFF-ASU
*/
public class MocImage extends GenericImage
{
    private static final DebugLog log = DebugLog.instance();
    private static final String dbUrl = Config.get("db");
    
    private static final int NORTH_NORM_ORBIT = 6634;
    private static final String IMAGE_TYPE_NORMAL = "moc";
    private static final String IMAGE_TYPE_ROTATED = "moc_r180";
    private static final String IMAGE_TYPE_HFLIPPED = "moc_hflip";
    private static final String IMAGE_TYPE_VFLIPPED = "moc_vflip";
    private static final int MOC_NA_MIN_RENDER_PPD = 256;              // narrow-angle MOC stamps
    private static final int MOC_WA_MIN_RENDER_PPD = DEFAULT_MIN_RENDER_PPD;   // wide-angle MOC stamps
    
    private boolean srcImageNorthNormalized = false;
    private boolean wideAngle = false;
    private boolean malin = false;
    
    
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
    
    protected MocImage()
    {
        IMAGE_TYPE = IMAGE_TYPE_NORMAL;
        IMAGE_FRAME_TYPE = IMAGE_TYPE_NORMAL;
    }
    
    public MocImage(BufferedImage image, String filename)
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
            
            uniformImages = new UniformImage[1];
            uniformImages[0] = new TiledUniformImage(image);
            
            log.println("MOC image created for: " + filename);
        }
        catch (Exception e) {
            log.aprintln("During MOC image creation...");
            log.aprintln(e);
        }
    }

    /**
     ** Sets image as wide-angle MOC image or not.
     **/
    public void setWideAngle(boolean wide)
    {
        wideAngle = wide;
    }
    
    /**
     **  Returns true if image data is from Malin's MOC image gallery; false, otherwise.
     **/
    public boolean isMalin()
    {
        return malin;
    }
    
    /**
     ** Sets image source as Malin or not (for orientation normalization assumptions).
     **/
    public void setMalin(boolean malin)
    {
        this.malin = malin;
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
        
        // DB east lon => JMARS west lon for the 'moc_geometry' table only.
        String query = "select (360 - sw_lon) as sw_lon, sw_lat, (360 - se_lon) as se_lon, se_lat, " +
                       "(360 - nw_lon) as nw_lon, nw_lat, (360 - ne_lon) as ne_lon, ne_lat, " +
                       "!isnull(nw_lon) as has_geometry, orbit_number " + 
                       "from moc_idx inner join moc_geometry on moc_idx.product_id=moc_geometry.product_id " +
                       "and not ifnull(ignore_entry, 0)" +
                       "where moc_idx.product_id='" + filename + "'";
        
        
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
                if (rs.next()) {
                    if (rs.getBoolean("has_geometry")) {
                        // Create an array of geometry points for whole image only.
                        Point2D[] imagePts = new Point2D[4];
                        if (imagePts != null)
                        {
                            imagePts[0] = new Point2D.Double(rs.getDouble("sw_lon") % 360,
                                                             rs.getDouble("sw_lat"));
                            imagePts[1] = new Point2D.Double(rs.getDouble("se_lon") % 360,
                                                             rs.getDouble("se_lat"));
                            imagePts[2] = new Point2D.Double(rs.getDouble("nw_lon") % 360,
                                                             rs.getDouble("nw_lat"));
                            imagePts[3] = new Point2D.Double(rs.getDouble("ne_lon") % 360,
                                                             rs.getDouble("ne_lat"));
                            
                            // If the image is from Malin's image gallery, we need to test
                            // for whether image's normalization is different than our geometry data:
                            //
                            // If image source data was normalized to have top of image
                            // to be oriented in a generally-northward direction, then 
                            // rotate image coordinates 180-degrees for any image with
                            // a south/north latitudes reversed (moc_geometry coordinates
                            // are normalized for all image corners to be mapped to the correct
                            // planet location; "nw" is really upper right corner of image source
                            // data (first pixel), regardless of whether this is truly northwest on
                            // Mars.)
                            //
                            // Malin started north-normalizing its processed-but-unprojected GIF images 
                            // for MOC after a particular orbit, i.e., upper-right corner of image data 
                            // is *always* northwest from this orbit onward.
                            //
                            // ASU's image data has the exact same orientation as the PDS, i.e., the
                            // moc_geometry data scheme.
                            if (isMalin() &&
                                rs.getInt("orbit_number") >= NORTH_NORM_ORBIT &&
                                rs.getDouble("sw_lat") > rs.getDouble("nw_lat"))
                            {
                                Point2D temp;
                                
                                srcImageNorthNormalized = true;
                                
                                // Swap SW and NE
                                temp = imagePts[0];
                                imagePts[0] = imagePts[3];
                                imagePts[3] = temp;
                                
                                // Swap SE and NW
                                temp = imagePts[1];
                                imagePts[1] = imagePts[2];
                                imagePts[2] = temp;
                                
                                log.println("swapped points for Malin north normalization");
                            }
                            
                            // Apply current image orientation to corner points
                            imagePts = getOrientedPoints(imagePts);
                            
                            // Create geometry points for an approximate set of image subframes
                            // based on the whole image frame.  We are using this process only
                            // for image frame caching performance and human factors reasons until
                            // or unless better geometry information becomes available in the MOC stamp
                            // database.
                            TiledUniformImage uImg = (TiledUniformImage) getUniformImage(0);
                            if (uImg != null)
                            { 
                                double frameSizeFactor = (double)getFrameSize() / uImg.getHeight();
                                points = getFakeFramePoints(imagePts, frameSizeFactor);
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
    
    
    // @param query must specify a select command that returns a single-record result set
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
     * Unlike its superclass, this subclass requires that all 
     * storage-related operations on the wrapped BufferedImage 
     * pass through it.  This makes possible both caching and demand-based
     * loading of subtiles of the wrapped image as well as reliable 
     * references to instances of this class.
     */
    protected class TiledUniformImage extends UniformImage
    {
        private final String TILE_NUM_PREFIX = "_tile";
        private final int height;
        private final int width;
        private final int numTiles;
        private final int lastTileHeight;
        private final BufferedImage imageTiles[];
        
        // Wraps passed image but does not copy underlying instance.
        public TiledUniformImage(BufferedImage image)
        throws Exception
        {
            if (image == null)
                throw new Exception("null image");
            
            bImage = image;
            height = image.getHeight();
            width = image.getWidth();
            
            if (height % width > 0) {
                numTiles = height / width + 1;
                lastTileHeight = height % width;
            }
            else {
                numTiles = height / width;
                lastTileHeight = width;
            }
            
            imageTiles = new BufferedImage[numTiles];
        }
        
        synchronized public BufferedImage getImage()
        throws Exception
        {
            if (bImage == null) {
                bImage = realGetImage(0);
                
                if (bImage == null)
                    throw new Exception("could not retrieve image");
                else if (bImage.getHeight() != height ||
                        bImage.getWidth() != width)
                    throw new Exception("retrieved image has wrong dimensions");
            }
            
            return bImage;
        }
        
        synchronized public void releaseImage()
        {
            bImage = null;
            
            for (int i=0; i < numTiles; i++)
                imageTiles[i] = null;
        }
        
        synchronized public void storeCachedImage(boolean storeBaseImage)
        {
            try {
                // Save as PNG image for between-program-launch-execution caching.
                if (storeBaseImage) {
                    String fname = getCachedImageName(0);
                    savePngImage(fname, getImage());
                }
                
                // Save image tiles
                storeCachedImageTiles();
            }
            catch (Exception e) {
                log.aprintln(e);
            }
        }
        
        public int getHeight()
        {
            return height;
        }
        
        public int getWidth()
        {
            return width;
        }
        
        // Returns 32-bit RGB color value at specified pixel location; may
        // include alpha component.
        synchronized public int getRGB(int x, int y)
        throws Exception
        {
            // Try to get pixel from base image if it is loaded
            if (bImage != null)
                return bImage.getRGB(x, y);
            else {
                // Get pixel from image tile
                int tile = y / width;
                int tileY = y % width;
                
                BufferedImage tileImg = getTile(tile);
                if (tileImg == null)
                    throw new Exception("no image tile found for tile #" + tile);
                else
                    return tileImg.getRGB(x, tileY);
            }		
        }
        
        protected String getCachedImageTileName(int tile)
        {
            String fname = getFilename();
            
            if (fname != null &&
                StampImage.STAMP_CACHE != null)
                return StampImage.STAMP_CACHE + fname + "_" + 0 + TILE_NUM_PREFIX + tile +
                "_" + IMAGE_TYPE + CACHED_IMAGE_SUFFIX;
            else
                return null;
        }
        
        // Stores image as multiple vertical tiles.  Each tile is height/width
        // equal to the base image's width; last tile has height equal to
        // remainder.  (MOC images are known to be height > width).
        synchronized protected void storeCachedImageTiles()
        {
            try {
                for (int i=0; i < numTiles; i++) {
                    String fname = getCachedImageTileName(i);
                    savePngImage(fname, getTile(i));
                }
            }
            catch (Exception e) {
                log.println(e);
            }
        }
        
        synchronized protected BufferedImage getTile(int tile)
        throws Exception
        {
            if (tile >= numTiles) {
                log.aprintln("tile not found, #" + tile);
                return null;
            }
            
            if (imageTiles[tile] == null) {
                int tileHeight;
                if (tile == numTiles - 1)
                    tileHeight = lastTileHeight;
                else
                    tileHeight = width;
                
                // Create image tile from base image if it is loaded.  For
                // better performance, we don't try to force loading of base
                // image yet.
                if (bImage != null)
                    imageTiles[tile] = bImage.getSubimage(0, tile * width, width, tileHeight);
                else {
                    // Otherwise, try to load image tile from cache.
                    String fname = getCachedImageTileName(tile);
                    imageTiles[tile] = MocImageFactory.loadImage(fname);
                    
                    // If could not load cached tile, force retrieval of
                    // base image (from disk), and create tile.
                    if (imageTiles[tile] == null) {
                        BufferedImage img = getImage();
                        if (img != null)
                            imageTiles[tile] = bImage.getSubimage(0, tile * width, width, tileHeight);
                    }
                    else if (imageTiles[tile].getHeight() != tileHeight ||
                            imageTiles[tile].getWidth() != width)
                        throw new Exception("wrong image tile dimensions");
                }
            }
            
            return imageTiles[tile];
        }
        
    }
    
    protected UniformImage getUniformImage(int band)
    {
        if (band >= bandCount)
            log.aprintln("invalid band number: " + band);
        
        if (uniformImages == null)
            uniformImages = new UniformImage[bandCount];
        
        try {
            if (uniformImages[band] == null)
                uniformImages[band] = new TiledUniformImage(realGetImage(band));
        }
        catch(Throwable e) {
            log.aprintln("Failed to create image due to:");
            log.aprintln(e);
            return  null;
        }
        
        return  uniformImages[band];
    }
    
    // Note: this overrides the storage approach of the superclass
    // by rerouting all image storage/retrieval through instances
    // of #TiledUniformImage.
    public BufferedImage getImage(int band)
    {
        BufferedImage img = null;
        
        try {
            TiledUniformImage uImg = (TiledUniformImage) getUniformImage(band);
            
            if (uImg != null)
                img = uImg.getImage();
        }
        catch (Exception e) {
            log.aprintln(e);
        }
        
        return null;
    }
    
    protected BufferedImage realGetImage(int band)
    {
        if (band != 0) {
            log.aprintln("In " + filename + ", band " +
                         band + "/" + bandCount + " doesn't exist!");
            return null;
        }
        else
            return getCachedImage(0);
    }
    
    /**
     ** Releases resources (memory) associated with the image at the
     ** particular band.
     **
     ** @param eraseFrames if 'true', not only projected image frames
     ** but also all image frame data of any kind is erased.
     **/
    synchronized public void releaseImage(int band, boolean eraseFrames)
    {
        log.println("releasing image");
        if (band != 0) {
            log.aprintln("In " + filename + ", band " +
                         band + "/" + bandCount + " doesn't exist!");
            return;
        }
        
        if (uniformImages != null &&
            uniformImages[0] != null)
            ((TiledUniformImage)uniformImages[0]).releaseImage();
        
        // We need to use the getPoints() method to get the frame count, but
        // the results of this call must not be cached: prevents synchronization error
        // at load time involving wide-angle images and some north-normalized images 
        // retrieved from a Malin's website.  (This method is called during the load
        // process to avoid a memory crunch in some situtations.)
        Point2D[] pts = getPoints(false);
        if (pts != null) {
            int frameCount = pts.length / 4;
            if (frameCache != null)
                for (int i = 0; i < frameCount; i++)
                    if (frameCache[band][i] != null) {
                        frameCache[band][i].releaseImage();
                        
                        if (eraseFrames)
                            frameCache[band][i] = null;
                    }
                    
            if (eraseFrames)
                frameCache = null;
        }
    }
    
    /**
     * Overridden to support special image-caching mechanism.
     */
    protected BufferedImage getCachedImage(int band)
    {
        if (band >= bandCount) {
            log.aprintln("In " + filename + ", band " +
                         band + "/" + bandCount + " doesn't exist!");
            return  null;
        }
        
        BufferedImage img = MocImageFactory.loadImage(getCachedImageName(band));
        
        return img;
    }
    
    protected void storeCachedImage(int band)
    {
        storeCachedImage(band, true);
    }
    
    // @param storeBaseImage  store base image in addition to any
    //                        image tiles
    protected void storeCachedImage(int band, boolean storeBaseImage)
    {
        if (band >= bandCount) {
            log.aprintln("In " + filename + ", band " +
                         band + "/" + bandCount + " doesn't exist!");
            return;
        }
        
        TiledUniformImage img = (TiledUniformImage) getUniformImage(band);
        if (img != null)
            img.storeCachedImage(storeBaseImage);
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
    
    public boolean validGeometryInfo(Point2D[] pts)
    {
        return true;
    }
    
    protected int getFrameSize()
    {
        TiledUniformImage uImg = (TiledUniformImage) getUniformImage(0);
        if (uImg != null)
            return Math.min(uImg.getWidth(), uImg.getHeight());
        else
            return 0;
    }
    
    protected int getFrameSize(int band, int frame)
    {
        int size = getFrameSize();
        int realSize = size;
        
        if (frameCount > 0 &&
            frame == frameCount - 1 &&
            size > 0)
        {
            TiledUniformImage uImg = (TiledUniformImage) getUniformImage(0);
            if (uImg == null)
                log.aprintln("called with no image loaded");
            else
            {
                int remainder = uImg.getHeight() % size;
                if (remainder > 0)
                    realSize = remainder;
            }
        }
        
        return realSize;
    }
    
    protected int getMaxRenderPPD()
    {
        if (wideAngle)
            return 512;
        else
            return 8192;
    }
    
    protected int getMinRenderPPD()
    {
        if (wideAngle)
            return MOC_WA_MIN_RENDER_PPD;
        else
            return MOC_NA_MIN_RENDER_PPD;
    }
    
}
