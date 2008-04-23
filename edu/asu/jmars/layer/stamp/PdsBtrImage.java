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

import edu.asu.jmars.util.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;


/**
 ** Class for loading Brightness Temperature (BTR) and Apparent Brightness
 ** Temperature (ABR) versions of THEMIS IR/VIS images.  Provides
 ** methods for calculating certain properties.
 ** 
 ** @see edu.asu.jmars.layer.stamp.PdsBtrImageFactory
 ** @author hoffj MSFF-ASU
 **/
public class PdsBtrImage extends PdsImage
{
    private static final DebugLog log = DebugLog.instance();
    private static final int BUFF_SIZE = 40960;
    private static final String dbUrl = Config.get("db_themis");
    
    protected int bandNumber;
    protected int imageOffset;
    protected double dataScaleOffset;
    protected double dataScaleFactor;

    
    /**
     * Contains search parameters used by {@link PdsBtrImageFactory}.
     * Ideally this abstraction should be located in the latter class,
     * but for backward-compatibility with ".jmars" files created by
     * previous JMARS versions, we keep it located here. 
     */
    public static class SearchParameters implements Serializable
    {
        static final long serialVersionUID = 1348452506572948736L;
        
        boolean localDirEnabled;
        boolean localHttpEnabled; 
        boolean themisHttpEnabled;
        int firstSrc; 
        int secondSrc; 
        boolean autoCopyToLocalDir;
        String localDirPath;
        String themisHttpPath;
        String localHttpPath;
    }
    

    protected PdsBtrImage(InputStream fin)
    throws IOException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[BUFF_SIZE];
        
        IMAGE_TYPE = "btr";
        IMAGE_FRAME_TYPE = "btr";
        
        // Read the entire file into a memory buffer
        log.println("Reading file...");
        try
        {
            int count;
            while((count = fin.read(temp)) >= 0)
                buffer.write(temp, 0, count);
        }
        catch(IOException e)
        {
            log.aprintln("IO error while reading PDS BTR image.");
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
            filename = filename.substring(0, 9);
        
        // Determine where image data starts
        int imageRecords = intValue(label, "^IMAGE");
        imageOffset = (imageRecords-1) * recordBytes;
        
        // Determine the dimensions of the image data
        sampleCount = intValue(label, "LINE_SAMPLES");
        lineCount = intValue(label, "LINES");
        bandNumber = intValue(label, "BAND_NUMBER");
        
        bandCount = 1;
        bandList = new ArrayList();
        bandList.add("Band " + String.valueOf(bandNumber));
        bandNumbers = new int[1];
        bandNumbers[0] = bandNumber;
        
        // Determine data scaling factors for IR images only,
        // not for VIS.
        if (filename.startsWith("I"))
        {
            dataScaleOffset = doubleValue(label, "OFFSET");
            dataScaleFactor = doubleValue(label, "SCALING_FACTOR");
        }
        
        startTimeEt = doubleValue(label, "START_TIME_ET");
        if (filename.startsWith("V"))
            spatialSumming = intValue(label, "SPATIAL_SUMMING");
        else
            spatialSumming = 0;
        
        log.println("recordBytes = " + recordBytes);
        log.println("labelRecords = " + labelRecords);
        log.println("imageRecords = " + imageRecords);
        log.println("sampleCount = " + sampleCount);
        log.println("lineCount = " + lineCount);
        
        imageBytes = sampleCount * lineCount;
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
        return getImage(band, sampleCount, lineCount,
                        dataBuffer, imageOffset);
    }
    
    /** 
     ** This method is used for internal optimization
     ** in place of #getImage.
     **/
    protected UniformImage getUniformImage(int band)
    {
        return getUniformImage(band, sampleCount, lineCount, dataBuffer, 
                               imageOffset);
    }
    
    public boolean isIntersected(HVector ptVect)
    {
        Point2D pt = null;
        
        pt = getImagePt(ptVect);
        
        log.println("returned image pt is " + pt);
        if ( pt != null)
            return true;
        else
            return false;
    }
    
    /**
     * Returns image point corresponding to point specified in
     * HVector coordinate space based on cell coordinate data
     * stored in frameCells array (generated by createImageFrame()
     * using getPoints() ).
     *
     * If point does not lie within the image, null is returned.
     */ 
    public Point2D getImagePt(HVector ptVect)
    {
        Point2D.Double pt = null;
        
        log.println("attempting to get image point for HVector on image named " + filename);
        
        if (ptVect != null)
        {
            log.println("vector is [" + ptVect.x + " " + ptVect.y + " " + ptVect.z + "]");
            log.println("checking for point in " + frameCount + " frames");
            
            for (int i = 0; i < frameCount; i++)
            {
                Point2D.Double unitPt = new Point2D.Double();
                
                if (frameCells[0][i] == null)
                    log.println("null pointer for frame cell #" + i);
                else
                    frameCells[0][i].uninterpolate(ptVect, unitPt);
                
                // Check whether point falls within cell.
                if (unitPt.x >= 0  &&  unitPt.x <= 1  &&
                    unitPt.y >= 0  &&  unitPt.y <= 1  )
                {
                    pt = new Point2D.Double();
                    pt.x = unitPt.x * frameWidth;
                    
                    if (i == frameCount-1)
                        pt.y = (1 - unitPt.y) * lastFrameHeight + i * frameHeight;
                    else
                        pt.y = ((1 - unitPt.y) + i) * frameHeight;
                    
                    log.println("found point, frame #" + i);
                    log.println("image coords: x=" + pt.x + " y=" + pt.y);
                    log.println("unit coords: x=" + unitPt.x + " y=" + unitPt.y);
                    break;
                }
            }
        }
        else
            log.println("ptVect parameter passed in as null");
        
        if (pt == null)
            log.println("returning null image point");
        else
            log.println("returning image point: x=" + pt.x + " y=" + pt.y);
        
        return pt;
    }
    
    /**
     * Returns temperature in degrees Kelvin for specified image
     * coordinates.
     *
     * Note: this method is only useful for BTR images, not for ABR images.
     */
    public double getTemp(int x, int y)
    {
        int dataIndex = x + sampleCount * y + imageOffset;
        byte pixelVal = dataBuffer[dataIndex];
        
        // Make sure in calculation below that 'pixelVal' is
        // effectively treated as an unsigned value, 0-255 in range.
        double temp = dataScaleFactor * ((int)(pixelVal & 0xFF)) + dataScaleOffset;
        
        log.println("data index = " + dataIndex + ", temp(K) = " + temp);
        
        return temp;
    }
    
    /**
     * Returns temperature in degrees Kelvin for specified image
     * point; double/float coordinates are converted to integer by
     * simply dropping non-integer portion.
     *
     * Note: this method is only useful for BTR images, not for ABR images.
     */
    public double getTemp(Point2D imagePt)
    {
        return getTemp( (int)imagePt.getX(), (int)imagePt.getY() );
    }
    
    
    public Point2D[] getPoints(int band)
    {
        // Database appears to have both missing and incorrect data for
        // queries based on the "band" field in the "geometry_detail" table,
        // so use the method below instead since all BTR/ABR data is single-band.
        return getPoints();
    }
    
    public Point2D[] getPoints()
    {
        return getPoints(			      
                         // DB east lon => JMARS west lon
                         "select (360-lon) as lon," +
                         "       lat from frmgeom " +
                         "where file_id = '" + filename +
                         "' and point_id != 'CT' and band_idx = 1" +
                         " order by framelet_id, concat(point_id)"
        );
        
    }
    
    /**
     * Returns true if copy is successful.
     */
    protected boolean copyToDir(String dirPath)
    {
        boolean status = false;
        
        if (dirPath != null)
            try {
                // Create directory if it does not exist
                File dir = new File(dirPath);
                if (dir != null) {
                    if (!dir.exists() &&
                        !dir.mkdir()) {
                        log.aprintln("Could not create directory named " + dirPath);
                        return false;
                    }
                    else if (!dir.isDirectory()) {
                        log.aprintln(dirPath + " is not a directory");
                        return false;
                    }
                    
                    StringBuffer buf = new StringBuffer(dirPath);
                    buf.append(filename);
                    if (filename.startsWith("V"))
                        buf.append(PdsBtrImageFactory.ABR_SUFFIX);
                    else
                        buf.append(PdsBtrImageFactory.BTR_SUFFIX);
                    
                    FileOutputStream fout = new FileOutputStream(buf.toString());
                    if (fout != null) {
                        fout.write(dataBuffer);
                        fout.close();
                        status = true;
                    }
                }
            }
            catch (Throwable e) {
                log.aprintln(e);
            }
        
        return status;
    }
    
}
