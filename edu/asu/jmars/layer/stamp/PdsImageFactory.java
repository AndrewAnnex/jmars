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

import java.awt.image.*;
import java.io.*;
import java.net.*;
import edu.asu.jmars.util.*;

/**
 * Factory for creating instances of {@link PdsImage} from
 * image data contained in files, URLs, and any {@link Stamp} 
 * which contains a valid URL. 
 */
public class PdsImageFactory extends StampImageFactory
{
    private static final DebugLog log = DebugLog.instance();
    
    
    public static void main(String[] av)
    {
        PdsImageFactory factory = new PdsImageFactory();
        StampImage pdsi = factory.load(av[0]);
        
        String[] bands = pdsi.getBands();
        for(int i=0; i<bands.length; i++) {
            BufferedImage img = pdsi.getImage(i);
        }
        System.exit(0);
    }
    
    /**
     * 
     */
    public PdsImageFactory()
    {
        super();
    }
    
    public StampImage load(URL url)
    throws Exception
    {
        log.println("PdsImage: loading " + url);
        
        if (url == null)
            return null;
        
        try {
            BufferedInputStream bfin =
                new BufferedInputStream(url.openStream());
            return  new PdsImage(bfin);
        }
        catch(Throwable e) {
            log.aprintln(e);
            log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + url);
            return  null;
        }
    }
    
    public StampImage load(String fname)
    {
        log.println("PdsImage: loading " + fname);
        
        if (fname == null)
            return null;
        
        try {
            FileInputStream fin = new FileInputStream(fname);
            BufferedInputStream bfin = new BufferedInputStream(fin);
            return  new PdsImage(bfin);
        }
        catch(Throwable e) {
            log.aprintln(e);
            log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + fname);
            return  null;
        }
    }
    
    public StampImage load(Stamp s)
    {
        if (s == null ||
            s.pdsImageUrl == null)
            return  null;
        
        try {
            return load(s.pdsImageUrl);
        }
        catch (Throwable e) {}
        
        return null;
    }
    
}
