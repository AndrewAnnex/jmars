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

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;
import edu.asu.msff.Stamp;

/**
 * Factory for creating instances of StampImages from image data contained in
 * files, URLs, and any {@link Stamp} which contains a valid URL. 
 */
public class StampImageFactory
{
    private static final DebugLog log = DebugLog.instance();
    private static final Toolkit toolkit = Toolkit.getDefaultToolkit();
    
    static {
    	File cache = new File(StampImage.STAMP_CACHE);
    	if (!cache.exists()) {
    		if (!cache.mkdirs()) {
    			throw new IllegalStateException("Unable to create stamp cache directory, check permissions in " + Main.getJMarsPath());
    		}
    	} else if (!cache.isDirectory()) {
    		throw new IllegalStateException("Stamp cache cannot be created, found regular file at " + StampImage.STAMP_CACHE);
    	}
    	File srcCache = new File(StampImage.STAMP_CACHE+"/src");
    	if (!srcCache.exists()) {
    		if (!srcCache.mkdirs()) {
    			throw new IllegalStateException("Unable to create stamp cache directory, check permissions in " + Main.getJMarsPath());
    		}
    	} else if (!srcCache.isDirectory()) {
    		throw new IllegalStateException("Stamp cache cannot be created, found regular file at " + StampImage.STAMP_CACHE+"/src");
    	}
    }
    
    /**
     * Loads image from URL reference contained in {@link Stamp} instance; 
     * pops up progress monitor dialog as needed.  Supports
     * specialized tile-based caching mechanism used in {@link MocImage}.
     * 
     * @param s stamp containing valid URL.
     */
    public static StampImage load(StampShape s, String instrument, String type)
    {
        StampImage stampImage = null;

    	if (instrument.equalsIgnoreCase("CTX")||
    			instrument.equalsIgnoreCase("HIRISE")||
    			instrument.equalsIgnoreCase("MOC")||
    			instrument.equalsIgnoreCase("VIKING")||
    			instrument.equalsIgnoreCase("HRSC")||
    			instrument.equalsIgnoreCase("MOSAIC")||
    			instrument.equalsIgnoreCase("MAP")||
    			instrument.equalsIgnoreCase("CRISM")||
//    			instrument.equalsIgnoreCase("THEMIS")||
    			instrument.equalsIgnoreCase("ASTER") ||
    			instrument.equalsIgnoreCase("APOLLO")) {
    	    return new StampImage(s, null, s.getId(), instrument, type);                            
    	}
    	
        try {
            BufferedImage image = loadImage(getStrippedFilename(s.getId()));
            
            if (image == null) {
            	String urlStr=StampLayer.stampURL+"ImageServer?instrument="+instrument+"&id="+s.getId()+StampLayer.versionStr;
            	
            	if (type!=null && type.length()>=0) {
            		urlStr+="&imageType="+type;
            	}
            	
            	urlStr+="&zoom="+Main.testDriver.mainWindow.getMagnification();
            	
            	log.println("Image being loaded from : " + urlStr);

            	URL url = new URL(urlStr);
            	
            	String cacheFileName = StampImage.STAMP_CACHE + "src/"+getStrippedFilename(urlStr);

        		if ((s.getId().startsWith("I") && (type.contains("BTR") || type.contains("PBT"))) || (s.getId().startsWith("V") && type.contains("ABR"))) {
                	File cacheFile = new File(cacheFileName);
                	
                	if (cacheFile.exists()) {
                		return new PdsImage(s, cacheFile, s.getId(), type, Instrument.THEMIS);
                	} else {                	
	        			InputStream in = url.openConnection().getInputStream();
	        		
	        			FileOutputStream out = new FileOutputStream(cacheFileName);
	        			
	        	        byte[] temp = new byte[40960];
	        	        
	    	            int count;
	    	            while((count = in.read(temp)) >= 0) {
	    	            	out.write(temp, 0, count);
	    	            }
	
	        	        in.close();
	        	        out.close();
	        	        
	        			return new PdsImage(s, cacheFile, s.getId(), type, Instrument.THEMIS);
                	}
        		}

        		// This is currently reachable for THEMIS BWS and DCS images
            	image = loadImage(cacheFileName);
            	
            	if (image==null) {            	
            		image = loadImage(url);
            	}
            }
            
            if (image != null) {
           		stampImage = new StampImage(s, image, s.getId(), instrument, type);
            }
        }
        catch (Throwable e) {
            log.aprintln(e);
        }
        
        return stampImage;
    }    
    /**
     * Loads image from file; pops up progress monitor dialog as needed.
     * 
     * @param fname Any valid filename.
     */
    protected static BufferedImage loadImage(String fname)
    {
        BufferedImage image = null;
        
        log.println("trying to load " + fname);
        if (fname != null &&
            toolkit != null) {
            Image img;
            
            try {
                byte[] buf = loadImage(new FileInputStream(fname));
                if (buf != null)
                    img = toolkit.createImage(buf);
                else {
                    log.println("image load cancelled or failed");
                    return null;
                }
            }
            catch (FileNotFoundException e) {
                log.println("failed to load " + fname);
                return null;
            }
            
            image = Util.makeBufferedImage(img);
            if (image != null)
                log.println("loaded image " + fname);
            else
                log.println("failed to load " + fname);
        }
        
        return image;
    }
    
    protected static void saveSrcImage(String fname, byte b[]) {
        if (fname != null &&
                toolkit != null) {
                
                try {
                	
                	FileOutputStream fos = new FileOutputStream(StampImage.STAMP_CACHE+"src/"+getStrippedFilename(fname));
                	fos.write(b);
                	fos.close();
                }
                catch (Exception e) {
                	e.printStackTrace();
                }
                
    	}    	
    }
              
    /**
     * Loads image from URL; pops up progress monitor dialog as needed.
     * 
     * @param url Any valid URL supported by {@link URL} class.
     * @param parentComponent UI component to reference for dialog creation 
     * purposes; may be null.
     */
    protected static BufferedImage loadImage(URL url)
    {
        BufferedImage image = null;
        
        log.println("trying to load " + url);
        if (url != null &&
            toolkit != null)
        {
            Image img = null;
            
            if (!isAlive(url)) {
                log.println("timeout accessing " + url);
                return null;
            }
            
            try {
                URLConnection uc = url.openConnection();
                if (uc != null) {
                    int size = uc.getContentLength();
                    log.println("content length = " + size);
                    byte[] buf = loadImage(uc.getInputStream());
                    if (buf != null) {
                    	saveSrcImage(url.toString(), buf);
                        img = toolkit.createImage(buf);
                    } else {
                        log.println("image load cancelled or failed");
                        return null;
                    }
                }
                else
                    log.aprintln("could not open connection for " + url);
            }
            catch (IOException e) {
                log.println(e);
                log.println("failed to load " + url);
                return null;
            }
            
            image = Util.makeBufferedImage(img);
            if (image != null)
                log.println("loaded image " + url);
            else
                log.println("failed to load " + url);
        }
        
        return image;
    }
        
    /**
     ** Loads image via passed input stream into returned byte array
     **/
    protected static byte[] loadImage(InputStream inStream)
    {
        int BUFF_SIZE = 512000;
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        byte[] inBuf = new byte[BUFF_SIZE];
        
        BufferedInputStream bin = new BufferedInputStream(inStream);
        
        if (inStream == null ||
            outBuf == null ||
            inBuf == null) {
            log.println("failed to create buffers for loading image");
            return  null;
        }
        
        try {
            int count = 0;
            int total = 0;
            
            log.println("available stream bytes: " + inStream.available());
            
            while((count = bin.read(inBuf)) >= 0) {
                total += count;
                outBuf.write(inBuf, 0, count);                
            }
        }
        catch(IOException e) {
            log.println("error loading image: " + e);
        }
        
        return outBuf.toByteArray();
    }
    
    /**
     ** Tests specified URL to determine if webserver is alive
     ** and accepting connections.
     **
     ** @param url Valid URL for a webserver
     ** @return Returns <code>true</code> if webserver is alive;
     ** <code>false</code>, otherwise.  If the protocol is
     ** not either "http" or "https", then <code>true</code> is
     ** always returned.
     **/
    protected static boolean isAlive(URL url)
    {
        final int timeout = 20000; // milliseconds
        String host = null;
        int port;
        
        if (url != null)
            try {
                // Only test actual webservers.
                String protocol = url.getProtocol();
                if (protocol == null ||
                    !( protocol.equalsIgnoreCase("http") || 
                       protocol.equalsIgnoreCase("https")))
                    return true;
                
                host = url.getHost();
                port    = url.getPort();
                
                if (port == -1)
                    port = 80;
                
                Socket connection = TimedSocket.getSocket (host, port, timeout);
                
                if (connection != null) {
                    connection.close();
                    
                    log.println("webserver " + host + " is alive");
                    return true;
                }
            }
        catch (Throwable e) {}
        
        log.println("webserver " + host + " is not alive");
        return false;
    }
    /**
     * Returns a filename that has been stripped of any
     * '/' characters; useful for converting stamp IDs
     * that may contain such characters into useable
     * base filenames.
     */
    protected static String getStrippedFilename(String fname)
    {
//        String stripped = null;

    	fname=fname.replaceAll(StampLayer.stampURL, "");
        fname=fname.replaceAll(StampLayer.versionStr, "");
        fname=fname.replaceAll("/", "");
        fname=fname.replaceAll("&","");
        fname=fname.replaceAll("http:", "");
        fname=fname.replaceAll("\\?", "");
        fname=fname.replaceAll("=", "");
        fname=fname.replaceAll(":","");
        fname=fname.replaceAll("-","");
        
//        if (fname != null) {
//            StringBuffer buf = new StringBuffer();
//            if (buf != null) {
//                char c;
//                
//                for (int i=0; i < fname.length(); i++)
//                    if ( (c = fname.charAt(i)) != '/' )
//                        buf.append(c);
//                    
//                stripped = buf.toString();
//            }
//        }
//        
//        return stripped;
        return fname;
    }    
}
