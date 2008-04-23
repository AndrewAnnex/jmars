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

import edu.asu.jmars.Main;
import edu.asu.jmars.util.DebugLog;


/**
 * Import function class for stamp data cache files.  A constructed instance
 * encapsulates methods for importing a cache file into an SQL database and 
 * running SQL queries against the data.
 * <p>
 * Unlike its parent class, {@link SqlStampCache}, this class does not support
 * updates, modifications, or persisent storage.  It provides strictly temporary,
 * read-only access to the imported data.
 *   
 * @author hoffj MSFF-ASU
 */
public class SqlStampImporter extends SqlStampCache
{
    private static final DebugLog log = DebugLog.instance();
    private static final int BUFF_SIZE = 100000;
    private static final String TEMP_DB_PREFIX = "imported_";
    private static final String TEMP_CACHE_SUFFIX = ".dat";
    private static final String ID_SUFFIX = ".impt";

    private static final String NOT_SUPPORTED_MSG = "operation not supported for stamp data importer";
    
    private boolean newImportCache = false;
    private boolean checkedNew = false;
    private boolean dataImported = false;
    
    /**
     * Constructs a temporary SQL database that for importing a stamp
     * data cache file appropriate to the specified stamp factory.  Imported
     * data may be accessed via queries on a read-only basis.
     * <p>
     * Data is not imported until a call to the {@link #importData} method is made.
     * 
     * @param factory Stamp factory with imported stamp cache descriptors 
     * @param name Optional name for instance (only useful for parent class); may
     * be <code>null</code>.
     *  
     * @throws IOException Whenever an error occurs accessing a cache-related file.
     * @throws CacheLockException Whenever the lock to the stamp cache for the specified 
     * stamp factory type cannot be acquired.
     * 
     * @see #lock
     */
    protected SqlStampImporter(StampFactory factory, String name)
    throws IOException, CacheLockException
    {
        super(factory, name, null);
        
        // Establish new/old import state.  This needs to be done now so that
        // the import status is in a known state.  Also, since this test can
        // fail due to file corruption or the like, we may need to re-create
        // the database used to import the cache file (something normally done
        // by the superclass implementation).
        boolean corruption = false;
        try {
            isNew();
        }
        catch (IOException e) {
        	log.println(e);
            corruption = true;
        }
        
        // If the import ID information was corrupted, we recreate the import
        // cache database.  Any failures here must be thrown by constructor.
        if (corruption) {
            destroyAll();
            createDB();
        }
    }

    /**
     * Creates a name for the SQL database based on the specified stamp
     * layer parameters and other appropriate information.
     * 
     * @param sqlParms Stamp layer parameters including SQL-related descriptors
     * @return Full-path base filename for import database files.
     */
    protected String makeDbName(SqlStampLayer.SqlParameters sqlParms)
    {
        return Main.getJMarsPath() + TEMP_DB_PREFIX + sqlParms.cacheTableName + DB_SUFFIX;
    }
    
    /**
     * It is recommended that each subclass override this method
     * to create a unique cache lock filename for each different stamp
     * factory type that it instantiates a cache for..
     */
    protected String getLockFilename()
    {    
        String dbPath = new File(fullDbName).getParent();
        return dbPath + File.separator + TEMP_DB_PREFIX + 
               sqlParms.cacheTableName + CACHE_LOCK_SUFFIX;
    }
    
    /**
     * It is recommended that each subclass override this method
     * to create a unique cache version filename for each different stamp
     * factory type that it instantiates a cache for..
     */
    protected String getVersionFilename()
    {    
        String dbPath = new File(fullDbName).getParent();
        return dbPath + File.separator + TEMP_DB_PREFIX + 
               sqlParms.cacheTableName + VER_SUFFIX;
    }
    
    protected String getImportCacheFilename()
    {
        // HSQLDB by default requires that any source data file for 
        // "set table ... source ..." command be in the same
        // directory tree as the database file that is created with the
        // database instance.  There is a way to change this behavior,
        // but it is too tedious and cumbersome to bother with currently.
        String dbPath = new File(fullDbName).getParent();
        return dbPath + File.separator + sqlParms.importCacheFile;
    }
    
    /**
     * It is recommended that each subclass override this method
     * to create a unique cache import ID filename for each different stamp
     * factory type that it instantiates a cache for..
     */
    protected String getImportIDFilename()
    {    
        String dbPath = new File(fullDbName).getParent();
        return dbPath + File.separator + sqlParms.cacheTableName + ID_SUFFIX;
    }
    
    /**
     * Returns import ID (size and last modification time) for the current
     * import cache file associated with this instance.
     * 
     * @return Import ID for current import cache; returns <code>null</code>
     * if no import cache file exists.
     * @throws IOException If an error occurs while accessing import cache file.
     */
    synchronized private SqlImportID getImportID()
    throws IOException
    {
        return SqlImportID.computeImportID(getImportCacheFilename());
    }
    
    /**
     * Returns import ID (size and last modification time) for the import
     * cache file that was last succesfully imported for the type of
     * stamp data (or stamp factory) associated with this importer.
     * 
     * @return Last import ID; returns <code>null</code> if no import ID
     * file exists.
     * @throws IOException If an error occurs while reading import ID file.
     */
    synchronized private SqlImportID getLastImportID()
    throws IOException
    {
        return SqlImportID.readImportID(getImportIDFilename());
    }

    /**
     * Creates or overwrites import ID to store ID information for
     * current import cache file.  If the latter does not exist,
     * then any current ID file is deleted.
     */
    synchronized private void storeImportID()
    throws IOException
    {
        SqlImportID id = getImportID();

        File idFile = new File(getImportIDFilename());
        if (id != null)
            SqlImportID.writeImportID(idFile.getPath(), id);
        else if (idFile.exists())
            idFile.delete();
    }

    /**
     * Returns whether or not this is a new version of the import stamp
     * cache.  A cache is "new" if it needs to be retrieved from a
     * from a JMARS jar file, or if it is has a different timestamp or
     * size than the last imported stamp cache file.  It is also considered
     * new if the cache version (database structure or driver) has changed.
     * 
     * @see #isVersionChanged
     */
    synchronized public boolean isNew()
    throws IOException
    {
        if (!checkedNew) {
            if (isVersionChanged())
                newImportCache = true;
            else
            {
                File importCacheFile = new File(getImportCacheFilename());
                if (!importCacheFile.exists())
                    newImportCache = true;
                else {
                    SqlImportID lastID = getLastImportID();
                    SqlImportID curID = getImportID();
                    
                    if (lastID == null ||
                        !lastID.equals(curID))
                        newImportCache = true;
                }
            }
            
            checkedNew = true;
        }
        
        return newImportCache;
    }
    
    /**
     * Imports the stamp data cache file for this instance.  Data
     * is only imported once; successive method calls do nothing once
     * a call has successfully completed the import.
     * 
     * @return <code>true</code>, if import succeeded; <code>false</code>,
     * otherwise, i.e., means the import cache file does not exist
     * and cannot be extracted from a jarfile.
     */
    synchronized public boolean importData()
    throws IOException
    {
        // Only import once
        if (dataImported)
            return true;
    
        String tableName = sqlParms.cacheTableName;
        String lastSql = null;

        File importCacheFile = new File(getImportCacheFilename());
        
        // Check whether the stamp database cache file exists in the external
        // JMARS directory.  If it is not present, try to access it as a jarfile resource
        // and copy it to this location so that HSQLDB can read it.
        //
        // If not available either way, return without an error.
        //
        // NOTE: If the cache version has changed, then we must always try to
        // copy the jarfile-version (if any) of the import cache file into the
        // database directory, overwriting the presumably older import cache file that
        // might be there.  If there is no jarfile-version included but an import cache
        // file exists on disk, we fall through below to the actual SQL import code.
        //
        // Worst case, the existing on-disk version is incompatible and an exception
        // gets thrown during the import.  The caller's code already has to handle this 
        // situation anyway, so this implementation is fine and preserves the ability
        // for JMARS to import from text-based import cache files that are not included in
        // the jarfile.
        boolean versionChanged = isVersionChanged();
        if (versionChanged ||
            !importCacheFile.exists())
        {
            if (versionChanged)
                log.println("version change: existing stamp data file (if any) may be out-of-date");
            else
                log.println("no cached stamp data file found");
            
            InputStream fin = Main.getResourceAsStream(RESOURCES + sqlParms.importCacheFile);
            if (fin == null)
                log.println("no cached stamp data resource found");
            else {
                BufferedInputStream bin = new BufferedInputStream(fin);
                try {
                    // Make certain the JMARS directory exists and then copy the 
                    // stamp cache resource to it.
                    Main.initJMarsDir();
                    if (versionChanged && importCacheFile.exists())
                        log.aprintln("version change: replacing existing cache file with new cache resource");
                    log.println("copying cache file resource to file location: " + importCacheFile);
                    BufferedOutputStream outfile = new BufferedOutputStream(new FileOutputStream(importCacheFile));
                    
                    byte[] temp = new byte[BUFF_SIZE];
                    int count;
                    
                    while((count = fin.read(temp)) >= 0)
                        outfile.write(temp, 0, count);
                    
                    outfile.flush();
                    outfile = null;
                    
                    log.println("successfully copied cached stamp data resource to filesystem");
                }
                catch(IOException e) {
                    log.aprintln(e);
                    log.aprintln("IO error while copying cached stamp data resource to filesystem");
                }
            }
        }

        // One last check for import cache file since above extraction
        // from jarfile can fail.
        if (!importCacheFile.exists()) {
            log.println("No cached stamp data file available to import");
            return dataImported;
        }
            
        try {
            Connection conn = getConnection();
            
            if (conn != null) {
                log.println("Start of cached stamp data import");
                
                // Destroy existing import table, if any.  This just an extra precaution;
                // as a temporary table it shouldn't exist if this is a new connection
                // instance. 
                lastSql = "DROP TABLE " + tableName + " IF EXISTS";
                conn.createStatement().execute(lastSql);

                lastSql = "CREATE TEMP TEXT TABLE " + tableName + 
                " (" + sqlParms.cacheTableFieldsSql + ")";
                conn.createStatement().execute(lastSql);
                
                // Using tab-delimiting for fields.
                lastSql = "SET TABLE " + tableName + " SOURCE \"" + importCacheFile.getName() + ";fs=\\t\"";
                conn.createStatement().execute(lastSql);
                
                lastSql = "SET TABLE " + tableName + " READONLY TRUE";
                conn.createStatement().execute(lastSql);
                
                // Update last import ID and cache version files
                storeCacheVersion();
                storeImportID();
                dataImported = true;                    
                log.println("Completed cached stamp data import");
            }
        }
        catch(Exception e) {
            log.aprintln(e);
            String msg = "IO error while importing cached stamp data: " + e.getMessage();
            if (e instanceof SQLException)
            	while ((e = ((SQLException)e).getNextException()) != null)
                     msg += ":  " + e.getMessage();
            log.aprintln(msg);
            log.aprintln("Tried SQL: " + lastSql);
            throw new IOException(msg);
        }
        
        return dataImported;
    }

	/**
	 * Returns whether or not the cache data file has been imported.
	 */
	public boolean isDataImported() {
		return dataImported;
	}
    
    /**
     * Same as {@link #destroy()}, but does not delete the cache version file.
     */
    public synchronized void destroy()
    throws IOException
    {
        destroy(false);
    }
    
    /**
     * Same as {@link #destroy()}, but also deletes the import ID file and
     * cache version files.
     */
    protected synchronized void destroyAll() 
    throws IOException
    {
        super.destroy();
        
        File idFile = new File(getImportIDFilename());
        if (idFile.exists())
            idFile.delete();
    }
    
    /**
     * Makes certain that the database closes down properly and then that
     * ALL OF ITS DIRECTLY-RELATED FILES ARE DESTROYED.  This does not impact
     * the stamp data cache files that are imported into instances, nor
     * the import ID file.
     */
    protected void finalize()
    {
        try {
            // Always destroy the temporary import cache database after
            // importing, but leave the import ID file entact.
            destroy();
        }
        catch (Exception e)
        {
            // Note: Any uncaught exception in the finalize() method is discarded 
            // by the JVM after being thrown.  In this case, however, we don't really care 
            // what is thrown since we can't do anything about it anyway.  The only 
            // problem showing up has been some mysterious "database already closed" 
            // errors under Windows....
            log.println("While closing and destroying temporary import database...");
            log.println(e);
        };
    }
    
    /**
     * This method is not supported.
     * 
     * @throws SQLException unsupported operation for all calls.
     */
    public void update(ResultSet rs, boolean allInsert)
    throws SQLException
    {
        throw new SQLException(NOT_SUPPORTED_MSG);
    }
    
    /**
     * This method is not supported.
     * 
     * @throws IOException unsupported operation for all calls.
     */
	public synchronized void updateDB() throws IOException
    {
        throw new IOException(NOT_SUPPORTED_MSG);
	}
    
    /**
     * Returns whether or not ZIP format of cache database is supported
     * for importing stamp data.
     * 
     * @return Always returns <code>false</code> since ZIP format is
     * not supported for this class.
     */
    protected boolean isZipFormatSupported()
    {
        return false;
    }
    
    /**
     * This method is not supported.
     * 
     * @throws UnsupportedOperationException unsupported operation for all calls.
     */
    protected boolean unpackCacheZipfiles()
    {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MSG);
    }
}
