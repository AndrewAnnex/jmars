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
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;

import java.awt.Color;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import javax.swing.*;


public class SqlStampLayer extends StampLayer
{
    private static final DebugLog log = DebugLog.instance();
    
    protected String dbUrl;
    protected SqlParameters sqlParms;
    protected StampFactory stampFactory;
    protected ProjObj originalPO;
    
    
    public SqlStampLayer(String dbUrl,
                         StampFactory stampFactory,
                         SqlParameters sqlParms
                        )
    {
        this.dbUrl = dbUrl;
        this.stampFactory = stampFactory;
        this.sqlParms = sqlParms;
    }
    
    
    public static class SqlParameters implements SerializedParameters, Cloneable
    {
       static final long serialVersionUID = -6569260449094295909L;
        String sql;
        String cacheQuerySql;
        String cacheTableName;
        String cacheTableFieldsSql;
        String[] cacheIndexFields;
        String primaryKeyName;
        String orbitFieldName;
        String serverLastModFieldName;
        String cacheLastModFieldName;
        String importCacheFile;
        
        public Object clone()
        {
            SqlParameters copy = null;
            try {
                copy = (SqlParameters)super.clone();
                if (cacheIndexFields != null)
                    copy.cacheIndexFields = (String [])cacheIndexFields.clone();
            }
            catch (CloneNotSupportedException e) {
                throw new InternalError(e.toString());
            }
            return copy; 
        }
    }
    
    
    public SqlParameters getSqlParms()
    {
        return sqlParms;
    }
    
    private Class[] columnClasses;

    /**
     * Returns classes for stamp data columns.
     * 
     * @return May in some cases return <code>null</code> if stamp
     * data is unavailable or layer is in a bad state.
     * Data could also be a zero-length array.
     *
     * @see #isBad
     */
    public synchronized Class[] getColumnClasses()
    {
    	if (!isBad()) {
    		if (columnClasses == null)
    			loadStampData();
    	}
    	
    	return  columnClasses;
    }
    
    private String[] columnNames;

    /**
     * Returns names for stamp data columns.
     * 
     * @return May in some cases return <code>null</code> if stamp
     * data is unavailable or layer is in a bad state.
     * Data could also be a zero-length array.
     *
     * @see #isBad
     */
    public synchronized String[] getColumnNames()
    {
    	if (!isBad()) {
    		if (columnNames == null)
    			loadStampData();
    	}
    	
    	return  columnNames;
    }
    
    private Stamp[] cachedStamps;
    private Map stampMap = new HashMap();

    /**
     * Returns stamp data for layer.
     * 
     * @return May in some cases return <code>null</code> if stamp
     * data is unavailable or layer is in a bad state.
     * Data may also be a zero-length array.
     *
     * @see #isBad
     */
    protected synchronized Stamp[] getStamps()
    {
    	if (!isBad()) {
    		if (cachedStamps == null)
    			loadStampData();
    		if (originalPO != Main.PO)
    			reprojectStampData();
    	}
    	
    	return  cachedStamps;
    }
    
    /**
     * Returns stamp from layer corresponding to specified stamp ID.
     * 
     * @return Returns <code>null</code> if stamp does not exist,
     * stamp data is unavailable, or layer is in a bad state.
     *
     * @see #isBad
     */
    protected synchronized Stamp getStamp(String stampID)
    {
        if (stampID == null)
            return null;
        else
            return (Stamp)stampMap.get(stampID.trim());
    }
    
    private void createStampMap(Stamp[] stamps)
    {
        stampMap.clear();
        if (stamps != null)
            for (int i=0; i < stamps.length; i++)
               if (stamps[i] != null &&
                   stamps[i].id != null)
                   stampMap.put(stamps[i].id.trim(), stamps[i]);
    }

    private class NoStampsException extends Exception
	{
    	NoStampsException(String msg)
		{
    		super(msg);
		}
	}
    
    private class NoStampSrcException extends Exception
	{
    	NoStampSrcException(String msg)
		{
    		super(msg);
		}
	}
    
    private class CacheIncompleteException extends Exception
    {
        CacheIncompleteException(String msg)
        {
            super(msg);
        }
    }
    
    
    /**
     * Loads stamp data from either the main database, a local
     * stamp data cache, or both.  Does nothing if layer has
     * been marked bad.
     * <p>.
     * If neither the main database nor a local stamp cache is
     * available, or if no stamps were returned by either, then
     * the layer is marked as bad.
     */
    private synchronized void loadStampData()
    {
        log.println("Entered");
    	if (isBad())
    	    return;

        ArrayList stamps = new ArrayList();
        String lastSql = null;
        String extraMsg = "";
        int numRecords = 0;
        boolean stampSrcOK = false;
        boolean abortLoad = false;
        
        ResultSet serverRS = null;
        try
        {
            setStatus(Color.red);
            log.println("Start of stamp load query");

            // Try to retrieve stamp data from local cache.
            try
            {
	            stampSrcOK = loadCacheData(stamps);
            	numRecords = stamps.size();
            }
            catch (CacheIncompleteException e)
            { 
		        // If we were able to retrieve at least some records via the cache
		        // (but it's update process failed), let's ask the user whether to
		        // use this data or instead query the main database (ignoring the
		        // cache data).
                log.println(e);
            	numRecords = stamps.size();
		    	if (numRecords > 0) {
		    	    Object[] options = new Object[] {"Cache Only",
		    	                                     "Database Only",
		    	                                     "Cancel"
		    	                                    };
		    	    
		    	    String msg = stampFactory.getName() +
                                 ": Only " + numRecords + " stamp records retrieved from local cache: \n" +
                                 e.getMessage() +
                                 "\n\nDo you wish to use this local cache data OR instead try to access main " +
                                 "database server (may also fail), or cancel stamp list data loading entirely?";
		        	msg = Util.lineWrap(msg, 55);
			        
		            int choice = JOptionPane.showOptionDialog( Main.mainFrame,
		                                                       msg,
		                                                       "Cache Load Error: " + stampFactory.getName(),
		                                                       JOptionPane.YES_NO_CANCEL_OPTION,
		                                                       JOptionPane.ERROR_MESSAGE,
		                                                       null,
		                                                       options,
		                                                       options[0]
		            										 );
			        
			        if (choice == 0) {
			            // Let's finish the load process with the usable stamp data from the cache.
			            log.println("incomplete load: user selected incomplete local cache to finish");
				        stampSrcOK = true;
			        }
			        else if (choice == 1) {
			            // Let's clear out all the results from the incomplete cache load
			            // but still allow the main database query later on.
			            log.println("incomplete load: user selected main database to complete load");
			            stamps.clear();
			            numRecords = 0;
				        stampSrcOK = false;
			        }
			        else {
			            // Let's abort the load process entirely.  We'll skip the main database query and
			            // clear the partially loaded stamp records.  We don't simply return
			            // immediately from the method because there is some cleanup code in
			            // a "finally" block down below (way at the end of the method) that handles 
			            // this when it checks whether any records were loaded.
			            log.println("incomplete load: user cancelled stamp load");
			            stamps.clear();
			            numRecords = 0;
				        stampSrcOK = false;
			            abortLoad = true;
			        }
		    	}
		    	else
		    	    // No records were retrieved from stamp cache due to this exception.
		    	    // Log it and continue on to try to get data from main database below.
		    	    log.aprintln(e.getMessage());
            }
            catch (IOException e) {
                // Stamp cache failed to load in some unusual wy; we use the failure 
                // explanation later on if needed.
                extraMsg = e.getMessage();
            }
		    // End of local cache load
		    	    
            
            // If retrieval via the integrated stamp cache failed or is not
            // configured for this version of JMARS, retrieve stamp data via
            // the main database instead provided that network access is available.
            if (!abortLoad &&
                numRecords < 1 &&
                Main.useNetwork())
            {
                log.println("start of main database query");
                SqlStampSource stampSrc = new SqlStampServer(stampFactory, dbUrl);
                lastSql = sqlParms.sql;
                StopWatch watch = new StopWatch();
                serverRS = stampSrc.executeQuery(sqlParms.sql);
                log.println("end of main database query, elapsed time = " +
                            watch.lapMillis()/1000 + " seconds");
                
                if (serverRS != null) {
                    while(serverRS.next()) {
                        Stamp s = stampFactory.createStamp(serverRS);
                        if (s != null)
                        {
                            stamps.add(s);
                            numRecords++;
                        }
                    }
                    
                    // Store column info
                    setColumnInfo(serverRS);
                }

                stampSrcOK = true;
                log.println("main database: end of stamp creation, elapsed time = " +
                            watch.lapMillis()/1000 + " seconds");
                log.println("main database: read " + numRecords + " records");
            }
            else if (numRecords < 1 &&
                    !Main.useNetwork()) {
                log.aprintln("Main database access not configured or not available");
            }
            //////// End of main database load
            
            
            log.println("End of stamp load query");

            // If at least one of the stamp data sources was accessed OK (cache or main
            // database) but no records were retrieved, then in most cases the problem
            // lies with the user's query (too restrictive).  Mark the layer as bad
            // in these situations.
            if (numRecords < 1 &&
            	stampSrcOK)
            {
            	markBad(true);
                
                String msg = "Could not retrieve stamp data from either cache or main database";
                if (extraMsg != null &&
                    extraMsg.length() > 0)
                	msg += ": " + extraMsg;
            	throw new NoStampsException(msg);
            }
            else if (numRecords < 1)
            {
            	// Bad situation: In the event we get here without an exception already, no
            	// stamps, and neither the stamp cache or database available, we flag
            	// the problem as serious and mark this stamp layer as bad.
            	markBad(true);

                String msg = "No stamp database (server or local cache) is available";
                if (extraMsg != null &&
                    extraMsg.length() > 0)
                    msg += ": " + extraMsg;
                throw new NoStampSrcException(msg);
            }
            
        }
        catch (NoStampsException ex) {
        	log.println(ex);
        	String msg = "No records matched your search criteria.  " +
			             "Please try again with a different query to create the view.";
        	msg = Util.lineWrap(msg, 55);
        	JOptionPane.showMessageDialog(
        			                      Main.mainFrame,
										  msg,
										  "Query Result",
										  JOptionPane.INFORMATION_MESSAGE
        	                             );
        }
        catch (NoStampSrcException ex2) {
        	log.println(ex2);
        	String msg = "Cannot create the view: " + ex2.getMessage();
        	msg = Util.lineWrap(msg, 55);
        	JOptionPane.showMessageDialog(
        			                      Main.mainFrame,
										  msg,
										  "Database Error",
										  JOptionPane.ERROR_MESSAGE
        	                             );
        }
        catch (Exception e) {
        	log.aprintln(e);
        	String msg =
        		         stamps.size() > 0
						 ? "Only able to retrieve " + stamps.size()
						 : "Unable to retrieve any";
                         
			msg += " " + stampFactory.getName() + " from the database, due to:\n" + e;
			msg = Util.lineWrap(msg, 55);
			
			log.aprintln("Tried SQL: " + lastSql);
			JOptionPane.showMessageDialog(
					                      Main.mainFrame,
										  msg,
										  "Database Error",
										  JOptionPane.ERROR_MESSAGE
			                             );
        }
        finally {
            if (serverRS != null)
                try {
                    // Release result set; this should also release the database
                    // connection due to implementation of SqlStampServer class.
                    //
                    // This addresses a possible problem with THEMIS database
                    // server building up a backlog of stale connections.  Don't
                    // whether this code has contributed to the problem or not,
                    // but just to be safe....
                    log.println("Releasing/close result set from database server");
                    serverRS.close();
                }
                catch (SQLException e)
                {
                    log.aprintln(e);
                }
                finally {
                    serverRS = null;
                }
            
        	// Final check on number of records retrieved.  However we get 
        	// here, mark the layer as bad if no records were retrieved.
        	if (numRecords < 1)
        		markBad(true);
        	
        	setStatus(Util.darkGreen);
        	cachedStamps = (Stamp[]) stamps.toArray(new Stamp[0]);
            createStampMap(cachedStamps);
        	originalPO = Main.PO;
        	log.println("End of stamp data load");
        }
        
        log.println("Exited");
    }

    /**
     * Loads stamp data from the local database cache, if available.
     * 
     * @param stamps List to which instances of {@link Stamp} will be
     * added.
     * @return Flag indicating availability of stamp cache; <code>true</code>, 
     * if cache was available and data was loaded normally from it; <code>false</code>,
     * if the cache was not available and/or an error occurred that prevented
     * access.
     * 
     * @throws IOException Thrown for some errors that prevent stamp cache access;
     * used mainly to pass on information messages since the method's return value
     * is the primary indicator for cache load failures.
     * 
     * @throws SqlStampCache.LoadIncompleteException Thrown if cache was available
     * and some stamp data was retrieved, but an error occurred which may have
     * caused the load to be incomplete, e.g., failure to update cache with
     * data from main database.
     */
    private synchronized boolean loadCacheData(List stamps)
    throws IOException, CacheIncompleteException
    {
        boolean stampSrcOK = false;
        String lastSql = null;
        int numRecords = 0;
        
	    try {
			// If a stamp data cache is available with this version of JMARS and
			// if the query creating this layer can be satisfied via the cache, then
			// try to retrieve stamp list data via the integrated stamp cache
			// appropriate to the stamp factory for this layer.
			if (sqlParms.cacheQuerySql != null) {
	            log.println("Start of stamp cache load query");
			    
	            SqlStampCache stampSrc = SqlStampCache.getCache(sqlParms.cacheTableName,  
	                                                             stampFactory, dbUrl);
				
			    if (stampSrc != null) {
			        lastSql = sqlParms.cacheQuerySql;
			        ResultSet rs = stampSrc.executeQuery(sqlParms.cacheQuerySql);
			        if (rs != null) {
			            while(rs.next()) {
			                Stamp s = stampFactory.createStamp(rs);
			                if (s != null)
			                {
			                    stamps.add(s);
			                    numRecords++;
			                }
			            }
			            
	                    // Store column info
	                    setColumnInfo(rs);
			        }
	
			        stampSrcOK = true;
			    }
			    else
			        log.aprintln("Failed to get access to stamp data cache");
			    
			    log.println("integrated cache: read " + numRecords + " records");
			
			    // Now we check whether the cache load was complete; if not,
                // throw an exception with the reason indicated by the cache.
			    if (stampSrc != null &&
                    stampSrc.isLoadIncomplete())
			    	throw new CacheIncompleteException(stampSrc.getLoadIncompleteReason());
			}
		}
	    catch (IOException e1) {
	        // This is is not fatal if we have network access for a main 
	        // database query.
	        log.aprintln(e1.getMessage());
	        log.aprintln("Skipping query from stamp data cache");
		}
	    catch (SQLException e2) {
	        // This is is not fatal if we have network access for a main 
	        // database query.
	        log.aprintln(e2.getMessage());
	        log.aprintln("Skipping query from stamp data cache");
	    }
	    catch (SqlStampCache.CacheLockException e3) {
	        // This is is not fatal if we have network access for a main 
	        // database query.  However, we log the original message
	        // and throw our own exception instead. 
	        log.println(e3.getMessage());
	        String extraMsg = "Stamp data cache in use by another JMARS program instance";
	        log.aprintln(extraMsg);
	        log.aprintln("Skipping query from stamp data cache");
	        throw new IOException(extraMsg);
	    }
	    //////// End of stamp cache load
	    
        log.println("End of stamp cache load query");
        
	    return stampSrcOK;
    }


    /**
     * Sets the column information for the stamp layer
     * corresponding to the data that was retrieved in the
     * specified result set.
     */
    private synchronized void setColumnInfo(ResultSet rs)
    throws SQLException
    {
        if (rs != null) {
            ResultSetMetaData metaData = rs.getMetaData();
            int colCount = metaData.getColumnCount();
            
            columnClasses = new Class[colCount];
            columnNames = new String[colCount];
            for(int i=0; i<colCount; i++)
            {
                String x = metaData.getColumnTypeName(i+1);
                Class y = (Class) Util.jdbc2java(x);
                String z = metaData.getColumnLabel(i+1).toLowerCase();
                
                columnClasses[i] = y;
                columnNames[i] = z;
            }
        }

    }
    
    private synchronized void reprojectStampData()
    {
        setStatus(Color.pink);
        if (this.stampFactory != null)
            stampFactory.reprojectStampPool();
        originalPO = Main.PO;
        setStatus(Util.darkGreen);
    }
}
