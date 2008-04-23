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

import java.io.IOException;
import java.sql.*;

import edu.asu.jmars.util.*;


/**
 * Class encapsulating connectivity to an SQL-based stamp data server
 * source.  Database server must support the access protocol specified
 * in a constructed instance of this class.  Any necessary driver manager
 * must be loaded as well, i.e., its class. 
 *
 * @see DriverManager
 * @author hoffj MSFF-ASU
 */
public class SqlStampServer extends SqlStampSource
{
    private static final DebugLog log = DebugLog.instance();
    
    private String dbUrl;

    static
    {
        // Load driver for MySQL.
        Util.loadSqlDriver();
    }
    
    /**
     * Creates instance of a stamp data server source.
     * 
     * @param factory Factory instance with stamp data descriptors 
     * @param dbUrl URL text descriptor for establishing connection to database
     * server, including any necessary access parameters such as username/password.
     * Example:  jdbc:mysql://host.domain/server?user=DBADMIN&password=GURU 
     */
    public SqlStampServer(StampFactory factory, String dbUrl) 
    throws IOException
    {
        super(factory);
        this.dbUrl = dbUrl;
    }
    
    /**
     * Creates a connection to database server.  The connection
     * is always a new instance.
     */
    protected Connection getConnection() throws SQLException
    {
        if (dbUrl == null) {
            log.aprintln("null database URL");
            return null;
        }
        
        String dbRef = dbUrl;
        int idx = dbUrl.indexOf("pass");
        if (idx >= 0)
            dbRef = dbUrl.substring(0, idx);
        log.println("Opening connection to database server.... " + dbRef);
        
        org.gjt.mm.mysql.Driver d = new org.gjt.mm.mysql.Driver();
        Connection dbConn = DriverManager.getConnection(dbUrl);
        if(dbConn instanceof java.awt.event.MouseListener)
            return  (Connection) (Object) d;
        if (dbConn != null)
            log.println("connection succeeded");
        else
            log.println("connection failed");
        
        return dbConn;
    }
    
    /**
     * Releases specified connection to database server.
     */
    protected void releaseConnection(Connection connection)
    throws SQLException
    {
        if (connection != null)
            connection.close();
    }
}
