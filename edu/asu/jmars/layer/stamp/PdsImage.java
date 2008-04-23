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
import java.io.*;
import java.sql.*;
import java.util.*;


/**
 * Supports loading EDR versions of THEMIS IR/VIS stamps (this functionality is
 * still implemented but no longer actively used in any released JMARS 
 * software versions).
 * 
 * @see edu.asu.jmars.layer.stamp.PdsImageFactory
 */
public class PdsImage extends StampImage
{
    private static final DebugLog log = DebugLog.instance();
    private static final String dbUrl = Config.get("db_themis");
    
    protected byte[] dataBuffer;
    protected int imageBytes;
    protected int sampleCount;
    protected int lineCount;
    protected int qubeOffset;
    protected double startTimeEt;
    protected int spatialSumming;
    
    
    static
    {
        Util.loadSqlDriver();
    }
    
    protected PdsImage()
    {
    }  
    
    protected PdsImage(InputStream fin)
    throws IOException
    {
        final int BUFF_SIZE = 40960;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[BUFF_SIZE];
        
        IMAGE_TYPE = "edr";
        IMAGE_FRAME_TYPE = "edr";
        
        // Read the entire file into a memory buffer
        log.println("Reading file...");
        try {
            int count;
            while((count = fin.read(temp)) >= 0)
                buffer.write(temp, 0, count);
        }
        catch(IOException e) {
            log.aprintln("IO error while reading PDS image.");
            throw  e;
        }
        
        log.println("Constructing it as a byte array...");
        
        // The entire data buffer
        dataBuffer = buffer.toByteArray();
        
        log.println("Determining parameters...");
        
        // The sizes of things
        int recordBytes;
        int labelRecords;
        // Determine just the label, as a big string
        String labelStart = new String(dataBuffer, 0, 1000);
        recordBytes = intValue(labelStart, "RECORD_BYTES");
        labelRecords = intValue(labelStart, "LABEL_RECORDS");
        label = new String(dataBuffer, 0, recordBytes * labelRecords).trim();
        
        // The database "filename"
        filename = strValue(label, "PRODUCT_ID");
        if (filename.length() >= 9)
            filename = filename.substring(1, 10);
        
        // Determine where qube data starts
        int qubeRecords = intValue(label, "^SPECTRAL_QUBE");
        qubeOffset = (qubeRecords-1) * recordBytes;
        
        // Determine the dimensions of the qube data
        {
            StringTokenizer tok =
                new StringTokenizer(strValue(label, "CORE_ITEMS"), "(,) ");
            if (!tok.hasMoreTokens())
                throw  new Error("Unable to read CORE_ITEMS 1");
            sampleCount = Integer.parseInt(tok.nextToken());
            if (!tok.hasMoreTokens())
                throw  new Error("Unable to read CORE_ITEMS 2");
            lineCount = Integer.parseInt(tok.nextToken());
            if (!tok.hasMoreTokens())
                throw  new Error("Unable to read CORE_ITEMS 3");
            bandCount = Integer.parseInt(tok.nextToken());
        }
        
        // Determine the bands present in the data
        bandList = new ArrayList();
        bandNumbers = new int[bandCount];
        {
            StringTokenizer bandNums;
            try {
                // Try the "original" keyword
                bandNums = new StringTokenizer(strValue(label, "BAND_BIN_ORIGINAL_BAND"), "(,) ");
            }
            catch(Throwable e) {
                // Try the "new" keyword if the first one failed
                bandNums = new StringTokenizer(strValue(label, "BAND_BIN_BAND_NUMBER"), "(,) ");
            }
            StringTokenizer bandVals =
                new StringTokenizer(strValue(label, "BAND_BIN_CENTER"),
                "(,) ");
            
            int i = 0;
            while(bandNums.hasMoreTokens()) {
                String band = bandNums.nextToken();
                bandNumbers[i++] = Integer.parseInt(band);
                if (bandVals.hasMoreTokens())
                    bandList.add("Band " + band +
                                 " (" + bandVals.nextToken() + " um)");
                else
                    bandList.add("Band " + band);
            }
        }
        
        startTimeEt = doubleValue(label, "START_TIME_ET");
        if (filename.startsWith("V"))
            spatialSumming = intValue(label, "SPATIAL_SUMMING");
        else
            spatialSumming = 0;
        
        log.println("recordBytes = " + recordBytes);
        log.println("labelRecords = " + labelRecords);
        log.println("qubeRecords = " + qubeRecords);
        log.println("sampleCount = " + sampleCount);
        log.println("lineCount = " + lineCount);
        log.println("bandCount = " + bandCount);
        
        imageBytes = sampleCount * lineCount;
    }
    
    protected static String strValue(String lines, String key)
    {
        String needle = key + " = ";
        
        int start = lines.indexOf(needle);
        if (start == -1) start = lines.indexOf("\n" + needle);
        if (start == -1) start = lines.indexOf("\t" + needle);
        if (start == -1) start = lines.indexOf(" " + needle);
        if (start == -1) throw  new Error("Can't find key " + key);
        start += needle.length();
        
        int end = lines.indexOf("\n", start+1);
        if (end == -1)
            throw  new Error("Can't find end of key " + key);
        
        try {
            String val = lines.substring(start, end);
            end = val.length();
            while(end > 0  &&  Character.isWhitespace(val.charAt(end-1)))
                --end;
            return  val.substring(0, end);
        }
        catch(RuntimeException e) {
            log.aprintln("Problem returning key " + key);
            log.aprintln("start = " + start);
            log.aprintln("end = " + end);
            log.aprintln("lines.length() = " + lines.length());
            throw  e;
        }
    }
    
    
    protected static int intValue(String lines, String key)
    {
        String val = strValue(lines, key);
        try {
            return  Integer.parseInt( val );
        }
        catch(NumberFormatException e) {
            log.println("Unable to decipher " + key +
                        " = '" + val + "'");
            throw  e;
        }
    }
    
    protected static double doubleValue(String lines, String key)
    {
        String val = strValue(lines, key);
        try {
            return  Double.parseDouble( val );
        }
        catch(NumberFormatException e) {
            log.println("Unable to decipher " + key +
                        " = '" + val + "'");
            throw  e;
        }
    }
    
    private static Point2D midpoint(Point2D a, Point2D b)
    {
        return  new HVector(a).add(new HVector(b)).toLonLat(null);
    }
    
    
    public double getStartTimeEt()
    {
        return  startTimeEt;
    }
    
    public Point2D[] getPoints()
    {
        return getPoints(			      
                         // DB east lon => JMARS west lon
                         "select (360-lon) as lon," +
                         "       lat from frmgeom " +
                         "where file_id = '" + filename +
                         "' and point_id != 'CT' order by framelet_id, concat(point_id)"
        );
    }
    
    // Only use this method with "VIS" images.
    public Point2D[] getPoints(int band)
    {
        // Band-specific queries only work for for single-band and 
        // multi-band "VIS" images; all IR images run into database 
        // problems, i.e., the data is incomplete or missing.
        if (filename.startsWith("V"))
        {
            if (bandNumbers != null)
                return getPoints(			      
                                 // DB east lon => JMARS west lon
                                 "select (360-lon) as lon," +
                                 "       lat from frmgeom " +
                                 "where file_id = '" + filename +
                                 "' and point_id != 'CT' and band = "+ bandNumbers[band] + 
                                 " order by framelet_id, concat(point_id)"
                );
            else
                return null;
        }
        else
            return getPoints();
    }
    
    private HashMap pointsCache = new HashMap();
    protected Point2D[] getPoints(String query)
    {
        Point2D[] points = null;
        
        if (pointsCache == null ||
                query == null)
            return null;
        
        if (pointsCache.get(query) != null)
            return (Point2D[]) pointsCache.get(query);
        
        ResultSet rs = null;
        try {
            log.println("filename = " + filename);
            rs = DriverManager.getConnection(dbUrl +
                                             "user=" + Main.DB_USER +
                                             "&password=" + Main.DB_PASS)
                                             .createStatement()
                                             .executeQuery(query);
            ArrayList pts = new ArrayList();
            while(rs.next())
                pts.add(new Point2D.Double(rs.getDouble("lon") % 360,
                                           rs.getDouble("lat")));
            if (pts.size() % 4 != 0) {
                log.aprintln("Bad record count: " + pts.size());
                return  null;
            }
            log.println("pts.size = " + pts.size());
            points = (Point2D[]) pts.toArray(new Point2D[0]);
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
        
        // Take the midpoint of the border pixels, to make sure that
        // each frame butts up exactly against the next. NOT FOR VISIBLE!
        if (!filename.startsWith("V"))
            for(int i=4; i<points.length-4; i+=4) {
                points[i+0] = points[i+6] = midpoint(points[i+0], points[i+6]);
                points[i+1] = points[i+7] = midpoint(points[i+1], points[i+7]);
            }
        
        pointsCache.put(query, points);
        return  points;
    }
    
    /**
     ** The BufferedImage version of getImage() is being supported
     ** for compatibility for some non-Themis-Stamp views.  However,
     ** for storage and performance optimization, the newer
     ** @link #getUniformImage method is used for all internal
     ** execution.
     **/
    public BufferedImage getImage(int band)
    {
        return getImage(band, sampleCount, lineCount, dataBuffer,
                        qubeOffset + imageBytes * band);
    }
    
    protected BufferedImage getImage(int band, int dataW, int dataH,
                                     byte[] data, int offset)
    {
        if (band >= bandCount) {
            log.aprintln("In " + filename + ", band " +
                         band + "/" + bandCount + " doesn't exist!");
            return  null;
        }
        
        try {
            if (images == null)
                images = new BufferedImage[bandCount];
            
            if (images[band] == null) {
                images[band] = getCachedImage(band);
                
                if (images[band] == null) {
                    images[band] = Util.createGrayscaleImageRot(dataW, dataH,
                                                                data, offset, false);
                    
                    if (images[band] != null)
                        storeCachedImage(band);
                }
            }
        }
        catch(Throwable e) {
            log.aprintln("FAILED TO CREATE PDS IMAGE FROM PDS DATA DUE TO:");
            log.aprintln(e);
            return  null;
        }
        
        return  images[band];
    }
    
    
    /** 
     ** This method is used for internal optimization
     ** in place of #getImage.
     **/
    protected UniformImage getUniformImage(int band)
    {
        return getUniformImage(band, sampleCount, lineCount, dataBuffer, 
                               qubeOffset + imageBytes * band);
    }
    
    protected UniformImage getUniformImage(int band, int dataW, int dataH,
                                           byte[] data, int offset)
    {
        if (band >= bandCount) {
            log.aprintln("In " + filename + ", band " +
                         band + "/" + bandCount + " doesn't exist!");
            return  null;
        }
        
        if (uniformImages == null)
            uniformImages = new UniformImage[bandCount];
        
        try {
            if (uniformImages[band] == null)
                uniformImages[band] = new CompactImage(dataW, dataH,
                                                       data, offset);
        }
        catch(Throwable e) {
            log.aprintln("FAILED TO CREATE PDS IMAGE FROM PDS DATA DUE TO:");
            log.aprintln(e);
            return  null;
        }
        
        return  uniformImages[band];
    }
    
    protected int getFrameSize()
    {
        return filename.startsWith("V") ? (192/spatialSumming) : 256;
    }
    
    protected int getFrameSize(int band, int frame)
    {
        if (!filename.startsWith("V")  &&  
            frame == frameCount-1) {
            try {
                UniformImage srcImage = getUniformImage(band);
                
                if (srcImage != null)
                    return srcImage.getHeight() % 256;
                else {
                    log.aprintln("null image");
                    return getFrameSize();
                }
            }
            catch (Exception e) {
                log.aprintln(e);
                return getFrameSize();
            }
        }
        else
            return getFrameSize();
    }
    
    protected int getMaxRenderPPD()
    {
        return getMaxRenderPPD(filename, spatialSumming);
    }
    
    protected int getMaxRenderPPD(String filename, int spatialSumming)
    {
        if (filename == null)
            return 512;
        else
            return filename.startsWith("V") ? 2048/spatialSumming : 512;
    }
    
    // Verifies that the specified frame point geometry information
    // matches the image size data and calculated framesize.
    public boolean validGeometryInfo(Point2D[] pts)
    {
        int frameCount = pts.length / 4 - 1;
        int frameSize = filename.startsWith("V") ? (192/spatialSumming) : 256;
        
        if (frameCount * frameSize > lineCount + (frameSize - (lineCount % frameSize)))
        {
            log.println("framesize is incompatible: lineCount=" + lineCount +
                        " frameCount=" + frameCount + " frameSize=" + frameSize +
                        " frameCount*frameSize = " + frameCount * frameSize);
            return false;
        }
        else
            return true;
    }
    
    private int histograms[][];
    public int[] getHistogram(int band)
    {
        if (histograms == null)
            histograms = new int[bandCount][];
        int[] hist = histograms[band];
        
        if (hist == null) {
            final int offset = qubeOffset + imageBytes * band;
            
            // Create the histogram
            hist = histograms[band] = new int[256];
            for(int i=0; i<imageBytes; i++)
                ++hist[ 0xFF & dataBuffer[i + offset] ];
            
            // Write to a file
            String filename = "band" + band + ".h";
            try {
                PrintStream fout = new PrintStream(
                                                   new FileOutputStream(filename));
                fout.println("# Histogram of " + getBand(band));
                for(int i=0; i<256; i++)
                    fout.println(i + "\t" + histograms[band][i]);
                fout.close();
                log.println("Wrote histogram to file: " + filename);
            }
            catch(Throwable e) {
                log.aprintln("Unable to write histogram file " + filename);
                log.println(e);
            }
        }
        
        return  (int[]) hist.clone();
    }
    
}
