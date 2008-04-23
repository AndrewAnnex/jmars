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

import java.io.*;

/**
 * Encapsulation of ID information for import cache files;
 * used as a lightweight mechanism to detect changes.
 * 
 * @author hoffj MSFF-ASU
 */
public class SqlImportID implements Serializable
{
    static final long serialVersionUID = 3959142707605173823L;
    
    long lastModified;
    long length;

    public SqlImportID(long lastMod, long length)
    {
        lastModified = lastMod;
        this.length = length;
    }
    
    /**
     * Determines whether instances of {@link SqlImportID} are
     * equal.
     * 
     * @param obj Instance to be compared with.
     * @return <code>true</code>, if the instances have the same
     * modification time and length for the represented import
     * cache file.  Otherwise, <code>false</code> is returned.
     */
    public boolean equals(Object obj)
    {
        if (obj != null &&
            obj.getClass() == getClass() &&
            ((SqlImportID)obj).lastModified == lastModified &&
            ((SqlImportID)obj).length == length)
            return true;
        
        return false;
    }

    /**
     * Reads serialized import ID from specified file.
     */
    public static SqlImportID readImportID(String filename)
    throws IOException
    {
        SqlImportID id = null;
        
        File idFile = new File(filename);
        if (idFile.exists()) {
            FileInputStream fin = new FileInputStream(idFile);
            ObjectInputStream in = new ObjectInputStream(fin);
            
            try
            {
                id = (SqlImportID)in.readObject();
            }
            catch (ClassNotFoundException e)
            {
                throw new IOException(e.getMessage());
            }
        }
        
        return id;
    }
    
    /**
     * Writes serialized import ID to specified file.
     */
    public static void writeImportID(String filename, SqlImportID id)
    throws IOException
    {
        File idFile = new File(filename);
        if (id != null) {
            FileOutputStream fout = new FileOutputStream(idFile);
            ObjectOutputStream out = new ObjectOutputStream(fout);
            
            out.writeObject(id);
            out.flush();
            out.close();
        }
    }

    /**
     * Computes and returns the import ID for the specified import 
     * cache file.
     */
    public static SqlImportID computeImportID(String filename)
    {
        SqlImportID id = null;
        
        File importCacheFile = new File(filename);
        if (importCacheFile.exists())
            id = new SqlImportID(importCacheFile.lastModified(), 
                                 importCacheFile.length());
        
        return id;
    }
}

