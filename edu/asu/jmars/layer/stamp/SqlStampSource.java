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
import java.sql.*;

/**
 * Abstract class encapsulating an SQL-based stamp data source.
 * 
 * @author hoffj MSFF-ASU
 */
public abstract class SqlStampSource
{
    protected StampFactory factory;
    
    /**
     * Constructs a stamp data source appropriate for specified stamp factory.
     * 
     * @param factory Factory instance with stamp data descriptors 
     */
    protected SqlStampSource(StampFactory factory)
    throws IOException
    {
        this.factory = factory;
    }

    /**
     * Gets factory instance describing stamp data associated with this
     * stamp data source.
     * 
     * @return Returns the factory.
     */
    protected StampFactory getFactory()
    {
        return factory;
    }
    
    /**
     * Returns connection to stamp data source.
     */
    abstract protected Connection getConnection()
    throws SQLException;
    
    /**
     * Releases specified connection to stamp data source.  The implementing
     * subclass has complete discretion over how or whether the connection
     * is actually released.  However, the caller guarantees that the connection
     * is never used after calling this method (setting caller's reference to
     * <code>null</code> is best.
     */
    abstract protected void releaseConnection(Connection connection)
    throws SQLException;
    
    /**
     * Returns the result of the specified SQL query of the
     * data contained in this stamp data source.  If this method
     * is used with a database server (@link SqlStampServer), then
     * the {@link ResultSet#close()} method should called on the
     * returned result set after use is complete.
     * 
     * @param sql  SQL query text
     */
    public ResultSet executeQuery(String sql)
    throws SQLException
    {
        Connection conn = getConnection();
        return conn.createStatement().executeQuery(sql);
    }
}
