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

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLEncoder;

import edu.asu.jmars.util.DebugLog;


/**
 * Supports loading ABR, BTR, and PBT versions of THEMIS IR/VIS stamps
 * 
 * @see edu.asu.jmars.layer.stamp.PdsImageFactory
 */
public class PdsImage extends StampImage
{
    private static final DebugLog log = DebugLog.instance();
    
	RandomAccessFile raf;
    protected int imageBytes;
    protected int sampleCount;
    protected int lineCount;
    protected int qubeOffset;
    protected int spatialSumming;
    
    private static final int BUFF_SIZE = 40960;
    
    protected int imageOffset;
    protected double dataScaleOffset;
    protected double dataScaleFactor;
    
    protected PdsImage(StampShape s, File cacheFile, String newFilename, String imageType, Instrument newInstrument)
    throws IOException
    {
    	super(s, s.getId(), "themis", imageType);
    	
    	raf = new RandomAccessFile(cacheFile, "r");
    	
    	instrument=newInstrument;
    	productID=newFilename;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[BUFF_SIZE];
        
        FileInputStream fin = new FileInputStream(cacheFile);
        
        // Read the entire file into a memory buffer
        log.println("Reading file...");
        try
        {
            int count;
            while((count = fin.read(temp)) >= 0)
                buffer.write(temp, 0, count);
            
            fin.close();
        }
        catch(IOException e)
        {
            log.aprintln("IO error while reading PDS BTR image.");
            throw  e;
        }
        
        log.println("Constructing it as a byte array...");
        
        // The entire data buffer
        byte[] dataBuffer;
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
        
        // Determine where image data starts
        int imageRecords = intValue(label, "^IMAGE");
        imageOffset = (imageRecords-1) * recordBytes;
        
        // Determine the dimensions of the image data
        sampleCount = intValue(label, "LINE_SAMPLES");
        lineCount = intValue(label, "LINES");
//        bandNumber = intValue(label, "BAND_NUMBER");
                
        // Determine data scaling factors for IR images only,
        // not for VIS.
        if (productID.startsWith("I"))
        {
            dataScaleOffset = doubleValue(label, "   OFFSET");
            dataScaleFactor = doubleValue(label, "SCALING_FACTOR");
        }
        
        if (productID.startsWith("V"))
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

    
    
    protected static String strValue(String lines, String key)
    {
        int start = lines.indexOf(key);
//        if (start == -1) start = lines.indexOf("\n" + needle);
//        if (start == -1) start = lines.indexOf("\t" + needle);
//        if (start == -1) start = lines.indexOf(" " + needle);
        if (start == -1) throw  new Error("Can't find key " + key);
        
        start += key.length();
        
        int end = lines.indexOf("=", start+1);
        
        if (end == -1)
            throw  new Error("Can't find end of key " + key);
        
        start=end+1;
        
        end = lines.indexOf("\n", start+1);
        if (end == -1)
            throw  new Error("Can't find end of key " + key);
        
        try {
            String val = lines.substring(start, end);
            if (val.startsWith("\"")) {
            	val=val.substring(1);
            }
            return  val.trim();
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
            
    protected Point2D[] getPoints()
    {
    	if (pts==null) {
			try {
				String urlStr = StampLayer.stampURL+"PointFetcher?id="+productID+"&instrument="+getInstrument()+StampLayer.versionStr;
				
    			if (imageType!=null && imageType.length()>0) {
    				urlStr+="&imageType="+URLEncoder.encode(imageType);
    			}
    			
				URL url = new URL(urlStr);
				
				ObjectInputStream ois = new ObjectInputStream(url.openStream());
				
				double dpts[] = (double[])ois.readObject();
				
				pts = new Point2D[dpts.length/2]; 
		        
	    	    for (int i=0; i<pts.length; i++) {
	    	    	pts[i]=new Point2D.Double(dpts[2*i], dpts[2*i+1]);
	    	    }	    	    
			} catch (Exception e) {
				e.printStackTrace();
			}
	        
	        // Take the midpoint of the border pixels, to make sure that
	        // each frame butts up exactly against the next. NOT FOR VISIBLE!
	        if (!productID.startsWith("V"))
	            for(int i=0; i<pts.length-4; i+=4) {
	                pts[i+0] = pts[i+6] = midpoint(pts[i+0], pts[i+6]);
	                pts[i+1] = pts[i+7] = midpoint(pts[i+1], pts[i+7]);
	            }
    	}
    	    	
        return  pts;
    }
            
    public int getHeight()
    {
    	return lineCount;
    }
 
    public int getWidth()
    {
    	return sampleCount;
    }

    public long getNumLines() {
    	return lineCount;
    }
    
    public int getNumSamples() {
    	return sampleCount;
    }

    // Returns 24-bit RGB color value at specified pixel location; no alpha
    // component.
    public int getRGB(int x, int y)	throws Exception
    {
		if (x >= sampleCount || y >= lineCount || x < 0 || y < 0)
		    throw new Exception("Invalid location: x=" + x + " y=" + y);
	
		long pos = x + y * sampleCount + imageOffset;
		raf.seek(pos);
		int b = raf.readByte() & 0xFF;
		return new Color(b, b, b).getRGB();
    }
        
    protected int getFrameSize()
    {
        return productID.startsWith("V") ? (192/spatialSumming) : 256;
    }
    
    protected int getFrameSize(int frame)
    {
        if (!productID.startsWith("V") && frame == frames.length-1) {
             return getHeight() % 256;
        }
        else {
            return getFrameSize();
        }
    }
    
    protected double getMaxRenderPPD()
    {
        if (productID == null)
            return 512;
        else
            return productID.startsWith("V") ? 2048/spatialSumming : 512;
    }
        
    private int histograms[][];
    public int[] getHistogram() throws IOException
    {
        if (histograms == null)
            histograms = new int[1][];
        int[] hist = histograms[0];
        
        if (hist == null) {
            final int offset = 0;
            
            // Create the histogram
            hist = histograms[0] = new int[256];
            for(int i=0; i<imageBytes; i++) {
            	raf.seek(i + offset);
                ++hist[ 0xFF & raf.readByte() ];
            }
            
            // Write to a file
            String filename = "band" + 0 + ".h";
            try {
                PrintStream fout = new PrintStream(
                                                   new FileOutputStream(filename));
                fout.println("# Histogram");
                for(int i=0; i<256; i++)
                    fout.println(i + "\t" + histograms[0][i]);
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
    
    /**
     * Returns temperature in degrees Kelvin for specified image
     * coordinates.
     *
     * Note: this method is only useful for BTR or PBT images.
     */
    public double getTemp(int x, int y)
    {
        int dataIndex = x + sampleCount * y + imageOffset;
        
        byte pixelVal;
        
        try {
        	raf.seek(dataIndex);
        	pixelVal = raf.readByte();
        } catch (IOException ioe) {
        	ioe.printStackTrace();
        	return -1;
        }
        
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
     * Note: this method is only useful for BTR images and PBT images.
     */
    public double getTemp(Point2D imagePt)
    {
        return getTemp( (int)imagePt.getX(), (int)imagePt.getY() );
    }
 
    
    
    
    
}
