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

import java.io.Serializable;
import java.sql.Driver;


/**
 * Encapsulation of database driver version information and table structure 
 * for cache database.  Used to detect (possible) database incompatibilities
 * and format changes.
 * 
 * @author hoffj MSFF-ASU
 */
public class SqlCacheVersion implements Serializable
{
    static final long serialVersionUID = 8661045560002568328L;
    
    int majorVersion;
    int minorVersion;
    String tableName;
    String tableStructure;
    
    // The revision field is used to mark/force version changes whenever one
    // of the other fields either doesn't or can't be relied upon to mark
    // cache database version changes, e.g., whenever HSQLDB software version
    // changes but this is not reflected in the Driver interface's major/minor
    // numbers.
    int revision = 1;

    public SqlCacheVersion(int major, int minor, String tableName, String tableStructure)
    {
        majorVersion = major;
        minorVersion = minor;
        this.tableName = tableName;
        this.tableStructure = tableStructure;
    }
    
    public SqlCacheVersion(Driver driver, SqlStampLayer.SqlParameters parms)
    {
        this(driver.getMajorVersion(), driver.getMinorVersion(),
             parms.cacheTableName, parms.cacheTableFieldsSql);
    }
    
    /**
     * Determines whether instances of {@link SqlStampCache.SqlCacheVersion} are
     * equal.
     * 
     * @param obj Instance to be compared with.
     * @return <code>true</code>, if the instances represent the same
     * database driver version and table structure.  Otherwise, 
     * <code>false</code> is returned.
     */
    public boolean equals(Object obj)
    {
        if (obj != null &&
            obj.getClass() == getClass() &&
            ((SqlCacheVersion)obj).revision == revision &&
            ((SqlCacheVersion)obj).majorVersion == majorVersion &&
            ((SqlCacheVersion)obj).minorVersion == minorVersion &&
            ((SqlCacheVersion)obj).tableStructure.equals(tableStructure) &&
            ((SqlCacheVersion)obj).tableName.equals(tableName))
            return true;
        
        return false;
    }
}

