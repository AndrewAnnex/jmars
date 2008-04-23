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
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;


/**
 * Abstract factory defining framework for creating instances of 
 * {@link StampImage} subclasses from image data contained in 
 * files, URLs, and any {@link Stamp} which contains a valid URL. 
 * 
 * @author hoffj MSFF-ASU
 */
public abstract class StampImageFactory
{
    private static final DebugLog log = DebugLog.instance();
    private static final Toolkit toolkit = Toolkit.getDefaultToolkit();
    
    protected HashSet timeoutHosts = new HashSet();
    
    /**
     * 
     */
    public StampImageFactory()
    {
    }
    
    /**
     **  Loads image from URL.
     **/
    public StampImage load(URL url) throws Exception
    {
        return null;
    }
    
    /**
     ** Loads image from file.
     **/
    public StampImage load(String fname)
    {
        return null;
    }
    
    /**
     ** Loads image from stamp information.
     **/
    public StampImage load(Stamp s)
    {
        return null;
    }

    
    /**
     * Loads image from file.
     * 
     * @param fname Any valid filename.
     */
    protected static BufferedImage loadImage(String fname)
    {
        return loadImage(fname, null);
    }
    
    /**
     * Loads image from file; pops up progress monitor dialog as needed.
     * 
     * @param fname Any valid filename.
     * @param parentComponent UI component to reference for dialog creation 
     * purposes; may be null.
     */
    protected static BufferedImage loadImage(String fname, Component parentComponent)
    {
        BufferedImage image = null;
        
        log.println("trying to load " + fname);
        if (fname != null &&
            toolkit != null) {
            Image img;
            
            try {
                byte[] buf = loadImageWithProgress(parentComponent, new FileInputStream(fname));
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
    
    /**
     * Loads image from URL.
     * 
     * @param url Any valid URL supported by {@link URL} class.
     */
    protected static BufferedImage loadImage(URL url)
    {
        return loadImage(url,  null);
    }
    
    /**
     * Loads image from URL; pops up progress monitor dialog as needed.
     * 
     * @param url Any valid URL supported by {@link URL} class.
     * @param parentComponent UI component to reference for dialog creation 
     * purposes; may be null.
     */
    protected static BufferedImage loadImage(URL url, Component parentComponent)
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
                    byte[] buf = loadImageWithProgress(parentComponent, uc.getInputStream(), size);
                    if (buf != null)
                        img = toolkit.createImage(buf);
                    else {
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
     ** Loads image via passed input stream into returned byte array; displays
     ** progress dialog as needed.
     **
     ** @param parentComponent UI component to reference for dialog creation 
     ** purposes; may be null.
     **/
    protected static byte[] loadImageWithProgress(Component parentComponent, InputStream inStream)
    {
        return loadImageWithProgress(parentComponent, inStream, -1);
    }
    
    /**
     ** Loads image via passed input stream into returned byte array; displays
     ** progress dialog as needed.
     **
     ** @param parentComponent UI component to reference for dialog creation 
     ** purposes; may be null.
     **
     ** @param imageSize size of image to be loaded if known; used for progress
     ** monitor; value is ignored if it is non-positive.
     **/
    protected static byte[] loadImageWithProgress(Component parentComponent, InputStream inStream,
                                                  int imageSize)
    {
        int BUFF_SIZE = 512000;
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        byte[] inBuf = new byte[BUFF_SIZE];
        
        BufferedInputStream bin = new BufferedInputStream(inStream);
        
        if (inStream == null ||
            outBuf == null ||
            inBuf == null) {
            log.aprintln("failed to create buffers for loading image");
            return  null;
        }
        
        StampProgressMonitor pm = new StampProgressMonitor(parentComponent,
                                                           "Loading Image",
                                                           "", 0, imageSize, 0,
                                                           400, 700);
        try {
            int count = 0;
            int total = 0;
            
            log.println("available stream bytes: " + inStream.available());
            
            while((count = bin.read(inBuf)) >= 0) {
                total += count;
                outBuf.write(inBuf, 0, count);
                
                pm.setProgress(total);
                if (pm.isCanceled())
                    return null;
                
            }
        }
        catch(IOException e) {
            log.aprintln("error loading image: " + e);
        }
        
        pm.close();
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
    
}
