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
import java.nio.channels.*;
import java.sql.*;
import java.text.*;
import java.util.*;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.*;


/**
 * Class for storing stamp list data as a local cache using a unique 
 * SQL database instance.  Supports inserting, updating, and retrieving
 * stamp data via {@link ResultSet} instances.  
 * <p>
 * The stamp cache database is persistent between different execution 
 * runs of JMARS.  Additionally, upon creation each cache database
 * is automatically updated with any changes from both a cache
 * import file (if available) and an external database server (if URL is
 * supplied as parameter).  The information necessary to access
 * the import file is provided via the stamp factory parameter.
 * 
 * @see #getCache(String, StampFactory, String)
 * @see SqlStampImporter 
 * @see SqlStampServer 
 * @author Joel Hoff MSFF-ASU
 */
public class SqlStampCache extends SqlStampSource
{
    private static final DebugLog log = DebugLog.instance();
    private static final HashMap cacheMap = new HashMap();

    protected static final String RESOURCES = "resources/";
    protected static final String DB_SUFFIX = "_db";
    protected static final String VER_SUFFIX = ".ver";
    protected static final String CACHE_LOCK_SUFFIX = ".scl";
    protected static final String IMPORT_PREFIX = "temp_";
    protected static final DateFormat timestampFormatter = new SimpleDateFormat("yyyyMMddHHmmss");

    protected String name;
    protected String fullDbName;
    protected SqlStampLayer.SqlParameters sqlParms;
    protected String dbUrl;
    protected boolean mainDbImportOnly = false;
    protected boolean ignoreUseNetwork = false;
    
    protected boolean versionChanged = false;
    protected boolean checkedVersion = false;
    
    private Connection connection;
    private FileChannel cacheChannel;
    private FileLock cacheLock;
    private boolean loadIncomplete = false;
    private String loadIncompleteReason;
    
    
    static
    {
        try {
            Class.forName("org.hsqldb.jdbcDriver");
        }
        catch (ClassNotFoundException e) {
            log.aprintln("HSQLDB database driver not found");
        }
        
        Runtime.getRuntime().addShutdownHook(
                                             new Thread()
                                             {
                                                 public void run()
                                                 {
                                                     SqlStampCache.closeCaches();
                                                 }
                                             }
                                            );
    }

    /**
     * Constructs an empty stamp cache appropriate for specified stamp factory.
     * This includes creation of a persistent standalone database to store the
     * local cache data.  
     * 
     * @param factory Factory instance with stamp cache descriptors 
     * @param name Identification key for unique cache instance. 
     * @param dbUrl Optional: URL text descriptor for establishing connection to an external database
     * server, including any necessary access parameters such as username/password.  This
     * may be set to <code>null</code> if no update from an external database is required.
     * Example:  jdbc:mysql://host.domain/server?user=DBADMIN&password=GURU
     * 
     * @throws IOException Whenever an error occurs accessing a cache-related file.
     * @throws CacheLockException Whenever the lock to the stamp cache for the specified 
     * stamp factory type cannot be acquired.
     * 
     * @see SqlStampServer 
     * @see #lock
     */
    protected SqlStampCache(StampFactory factory, String name, String dbUrl)
    throws IOException, CacheLockException
    {
        this(factory, name, dbUrl, false);
    }

    /**
     * Constructs an empty stamp cache appropriate for specified stamp factory.
     * This includes creation of a persistent standalone database to store the
     * local cache data.  
     * 
     * @param factory Factory instance with stamp cache descriptors 
     * @param name Identification key for unique cache instance. 
     * @param dbUrl Optional: URL text descriptor for establishing connection to an external database
     * server, including any necessary access parameters such as username/password.  This
     * may be set to <code>null</code> if no update from an external database is required.
     * Example:  jdbc:mysql://host.domain/server?user=DBADMIN&password=GURU
     * @param mainDbImportOnly Flag controlling stamp data import behavior.  If 
     * <code>true</code>, then only the main database will be used to import stamp data;
     * otherwise, all available data import sources are enabled for use, including the
     * main database, jarfile-based cache database resource, and either jarfile-based cache 
     * import file resource or external cache import file.
     * 
     * @throws IOException Whenever an error occurs accessing a cache-related file.
     * @throws CacheLockException Whenever the lock to the stamp cache for the specified 
     * stamp factory type cannot be acquired.
     * 
     * @see SqlStampServer 
     * @see #lock
     */
    protected SqlStampCache(StampFactory factory, String name, String dbUrl, boolean mainDbImportOnly)
    throws IOException, CacheLockException
    {
        super(factory);
        this.name = name;
        this.dbUrl = dbUrl;
        this.mainDbImportOnly = mainDbImportOnly;

        sqlParms = factory.getBaseSqlParms();
        fullDbName = makeDbName(sqlParms);

        // Before attempting to create/open the database, we must lock the cache
        // against concurrent use by another JMARS program instance (if any).  This
        // prevents potential later complications.
        lock();
        createDB();
    }

    /**
     * Creates a name for the SQL database based on the specified stamp
     * layer parameters and other appropriate information.  
     * 
     * @param sqlParms Stamp layer parameters including SQL-related descriptors
     * @return Full-path base filename for database files.
     */
    protected String makeDbName(SqlStampLayer.SqlParameters sqlParms)
    {
        return Main.getJMarsPath() + sqlParms.cacheTableName + DB_SUFFIX;
    }
    
    /**
     * Retrieves {@link SqlStampCache} instance corresponding to
     * name key.  This cache is backed by a persistent standalone database.
     * 
     * @param name Identification key for unique cache instance. 
     * @return Returns <code>null</code> if instance matching key
     * is not found.
     */
    synchronized public static SqlStampCache getCache(String name)
    {
        return (SqlStampCache) cacheMap.get(name);
    }

    /**
     * Retrieves {@link SqlStampCache} instance corresponding to
     * name key.  If none is found, a stamp cache will be created
     * that is appropriate for storing stamp lists for the specified
     * {@link StampFactory}.  The cache is backed by a persistent
     * standlone database.
     * <p>
     * As part of the cache instance creation, the cache database
     * is automatically updated with any changes from both a cache
     * import file (if available) and an external database server (if URL is
     * supplied as parameter).  The information necessary to access
     * the import file is provided via the stamp factory parameter.
     * 
     * @param name Identification key for unique cache instance. 
     * @param factory Factory instance with stamp cache descriptors
     * @param dbUrl Optional: URL text descriptor for establishing connection to an external database
     * server, including any necessary access parameters such as username/password.  This
     * may be set to <code>null</code> if no update from an external database is required.
     * Example:  jdbc:mysql://host.domain/server?user=DBADMIN&password=GURU 
     *  
     * @return Returns <code>null</code> if instance matching key
     * is not found.
     * 
     * @throws IOException Whenever an error occurs accessing a cache-related file.
     * @throws CacheLockException Whenever the lock to the stamp cache for the specified 
     * stamp factory type cannot be acquired.
     * 
     * @see SqlStampImporter 
     * @see SqlStampServer 
     * @see #lock
     */
    synchronized public static SqlStampCache getCache(String name, StampFactory factory, String dbUrl)
    throws IOException, CacheLockException
    {
        SqlStampCache cache = getCache(name);
        if (cache == null) {
            cache = new SqlStampCache(factory, name, dbUrl);
            cache.updateDB();
            cacheMap.put(name, cache);
        }
        
        return cache;
    }
    
    /**
     * All instances of this class which have been retrieved via either the
     * {@link #getCache(String)} or {@link #getCache(String, StampFactory, String)}
     * method is shutdown normally via the {@link #close} method.
     * <p>
     * This method should be called during a normal program exit so that
     * the cache databases close down safely.
     */
    synchronized public static void closeCaches()
    {
        Collection theCaches = cacheMap.values();
        Iterator it = theCaches.iterator();
        
        // Close each active stamp cache in proper fashion.
        while (it.hasNext()) {
            SqlStampCache cache = (SqlStampCache) it.next();
            try {
                if (cache != null)
                    cache.close();
            }
            catch (SQLException e) {
            	log.aprintln(e);
                if (cache != null)
                	log.aprintln("Error while closing cache database for: " + cache.name);
            }
        }
    }
    
    /**
     * Used to indicate that the cache lock could not be acquired.
     * 
     * @see #lock
     */
    public class CacheLockException extends Exception
    {
        public CacheLockException(String msg)
        {
        	super(msg);
        }
    }
    
    /**
     * It is recommended that each subclass override this method
     * to create a unique cache lock filename for each different stamp
     * factory type that it instantiates a cache for..
     */
    protected String getLockFilename()
    {    
        String dbPath = new File(fullDbName).getParent();
        return dbPath + File.separator + sqlParms.cacheTableName + CACHE_LOCK_SUFFIX;
    }
    
    /**
     * Locks the cache associated with the stamp factory type for this
     * instance so that no other JMARS program instance may open or create
     * the cache while this program instance is running.
     * 
     * @throws CacheLockException Whenever the lock to the stamp cache for the specified 
     * stamp factory type cannot be acquired.  If not thrown, then the
     * lock was acquired.
     * 
     * @see #getLockFilename
     */
    synchronized protected void lock()
    throws CacheLockException
    {
        String cacheFilename = getLockFilename();
        
        // If all of the calls below succeed, we will have acquired the
        // lock.  Otherwise, an exception will be thrown somewhere or a
        // null returned.
        try {
            // Only acquire channel/lock for cache if we don't have them.
            // All of this is non-blocking.
            if (cacheChannel == null ||
                cacheLock == null)
            {
            	File cacheLockFile = new File(cacheFilename);
                FileOutputStream fout = new FileOutputStream(cacheLockFile);
                cacheChannel = fout.getChannel();
                cacheLock = cacheChannel.tryLock();
                
                if (cacheLock != null)
                    log.println("Acquired cache lock for: " + cacheFilename);
                else
                    throw new IOException("cache locked by another process");
            }
        }
        catch (IOException e) {
            log.println(e);
            String msg = "Could not lock file '" + cacheFilename + "' : " + e.getMessage();
        	log.println(msg);
            
            cacheLock = null;
            cacheChannel = null;
            throw new CacheLockException(msg);
        }
    }

    /**
     * Creates or opens database to store local stamp cache data.  If database 
     * does not exist, it is created; otherwise, it is opened.  Database is
     * automatically recreated if it has either been corrupted (if detectable),
     * or if the cache version has changed.
     * 
     * @throws IOException Thrown if database cannot be created.
     * @see #isVersionChanged
     */
    synchronized protected void createDB()
    throws IOException
    {
        log.println("Entering, for: " + this.getClass().getName());
        StopWatch watch = new StopWatch();
        
        try {
            boolean versionChanged = false;
            boolean dbExists = false;
            boolean corruption = false;
            
            // If the cache version (database driver and/or structure) has
            // changed, then destroy any current version and rebuild from scratch.
            //
            // Since testing for this condition can fail due to file corruption,
            // serialized class changes, or obfuscated-vs-non-obfuscated code
            // mismatches, we also rebuild the database if the above tests fail. 
            try {
            	versionChanged = isVersionChanged();
                dbExists = cacheDbExists();
            }
            catch (IOException e) {
            	log.println(e);
                corruption = true;
            }
            
            if (versionChanged && dbExists) {
                log.aprintln(factory.getName() + ": stamp cache database version has changed - will destroy and recreate");
                destroyAll();
            }
            else if (corruption) {
                log.aprintln(factory.getName() + ": stamp cache database version information corrupted - will destroy and recreate database");
                destroyAll();
            }
            
            Connection conn = null;
            try {
                // Remove the HSQLDB lock file for this cache database.  This may still exist
                // if JMARS crashed, and sometimes removing it is all we have to do to recover
                // the database.  Also, thanks to the stamp cache lock (see lock() method), we
                // can do this with impunity.
                removeCacheDbLockFile();
                
                // If the cache version has changed (or is new) or if there was
                // cache corruption, theyn try to unpack cache database from 
                // zip file in JAR; only tries if it is present.  Only done if
                // ZIP format is supported for importing data (which is not true
                // for a subclass like SqlStampImporter that only handles text files).
                //
                // NOTE:  The cache database ZIP is only unpacked if import
                // has not been restricted to just the main database.  The latter
                // is a rare restriction currently only to support the actual
                // creation of cache database ZIP files (see SqlDbCacheCreator
                // class).
                //
                // STUDENT Version:  Due to the deployment strategy used for the
                // student version of JMARS, the cache database ZIP must *always*
                // be unpacked each time the stamp layer is started, i.e., the
                // stamp data is always being updated, there is no access to the
                // main database, and we need to protected against loading stale
                // data.  (Maybe a more elaborate version-scheme can be done
                // eventually based on the JMARS jarfile....)
                if (!mainDbImportOnly &&
                    isZipFormatSupported() &&
                    ( versionChanged || corruption || Main.isStudentApplication()) &&
                    unpackCacheZipfiles())
                {
                    // Whenever we have successfully unpacked the cache zipfile,
                    // we must destroy any previous text-based cache import file
                    // in the database directory and any/all leftover files from
                    // the import database itself, including the import ID file.
                    //
                    // RATIONALE:
                    //
                    // This is the only really safe way to prevent unnecessary
                    // imports from old text-based import files that are lying
                    // around from an older public version of JMARS.  Although
                    // the update() method's use of the "touched" field
                    // protects against importing individual stale records, it
                    // does not prevent *unnecessary* imports.
                    //
                    // Until the data deployment strategy clarifies, we want
                    // to preserve the option of subsequently updating a 
                    // zipfile-created database with a more recent text-based
                    // import cache file.  The only safe way to do this without
                    // instituting an elaborate uniform versioning-scheme that handles
                    // *both* the cache database and the cache import file is to
                    // ensure that the text-based import files/database are cleared
                    // out first.  Once a zipfile-created cache database is in place,
                    // a *later* JMARS program activation can safely assume that
                    // any discovered text-based import file which is discovered
                    // is safe to import.  The import ID mechanism while prevent
                    // any subsequent unnecessary import.
                    log.println("deleting any old import cache files / old import database");
                    SqlStampImporter cacheImport = new SqlStampImporter(getFactory(), IMPORT_PREFIX + sqlParms.cacheTableName);
                    cacheImport.destroyAll();
                    
                    File importCacheFile = new File(cacheImport.getImportCacheFilename());
                    if (importCacheFile.exists())
                        importCacheFile.delete();

                    log.println("Opening database");
                }
                else
                    log.println("Creating/opening database");
                
                // Create standalone database by opening connection (only creates
                // if needed).
                watch.reset();
                conn = getConnection();
                log.println("Done creating/opening database: elapsed time = " + 
                            watch.elapsedMillis()/1000 + " seconds");
                
                if ((versionChanged && dbExists) ||
                    corruption)
                    log.println("Created new empty cache database for " + factory.getName());
                
                return;
            }
            catch (Exception e) {
                log.aprintln(e);
            }

            // If there is an error the first time we attempt to create the
            // database, we assume that it may exist but has been corrupted.
            // So we try to destroy the cache database and rebuild it from scratch.
            log.aprintln("Error encountered while creating/opening cache database for " + factory.getName());
            log.aprintln("Trying to rebuild cache database from scratch...");
            destroyAll();
            
            boolean empty = true;
            if (!mainDbImportOnly)
                empty = !unpackCacheZipfiles();
            conn = getConnection();
            log.aprintln("Created new " + (empty ? "empty " : "") +
                         "cache database for " + factory.getName());
        }
        catch (Exception ex) {
            log.println("Total elapsed time = " + 
                        watch.elapsedMillis()/1000 + " seconds");
            log.aprintln(ex);
            String msg = "Error while trying to rebuild cache database from scratch for " + factory.getName() +
                         ": " + ex.toString();
            log.aprintln(msg);
            throw new IOException(msg);
        }
    }
    
    /**
     * Returns whether or not ZIP format of cache database is supported
     * for importing stamp data.
     */
    protected boolean isZipFormatSupported()
    {
        return true;
    }
    
    /**
     * Unpacks cached database from zipfile (if present) in JAR.
     * Cache is unpacked into normal database location, overwriting
     * an existing files.
     * <p>
     * NOTE: The {@link #removeCacheDbLockFile()} method should be called
     * as a precaution before calling this method. 
     * 
     * @return Returns flag indicating a successful unpacking of cache;
     * <code>true</code>, if unpacked; <code>false</code> if unpacking
     * failed or if zipfile is not present in JAR.
     */
    protected boolean unpackCacheZipfiles()
    {
       boolean unpacked = false;
       
       String zipFilename = SqlDbCacheCreator.getZipFilename(factory);
       InputStream zipIn = Main.getResourceAsStream(RESOURCES + zipFilename);
       
       if (zipIn == null) {
           String msg = "no cache zipfile resource found";
           log.println(msg);
       }
       else {
           String dbPath = new File(fullDbName).getParent() + File.separator;
           File zipFile = new File(dbPath + zipFilename);
           
           // Make certain the JMARS directory exists and then copy the 
           // stamp cache zipfile resource to it.
           boolean zipReady = false;
           try {
               Main.initJMarsDir();
               if (versionChanged && zipFile.exists())
                   log.println("version change: replacing existing cache zipfile with new cache zipfile resource");
               log.println("copying cache zipfile resource to file location: " + zipFile);
               BufferedOutputStream outfile = new BufferedOutputStream(new FileOutputStream(zipFile));
               BufferedInputStream bin = new BufferedInputStream(zipIn);
               
               byte[] buf = new byte[64 * 1024];
               int count;
               while((count = zipIn.read(buf)) >= 0)
                   outfile.write(buf, 0, count);
               
               outfile.flush();
               outfile.close();
               bin.close();
               
               zipReady = true;
               log.println("successfully copied cache zipfile resource to filesystem");
           }
           catch(IOException e) {
               log.aprintln(e);
               log.aprintln("IO error while copying cache zipfile resource to filesystem");
           }
           
           // Unpack cache database from zipfile.
           if (zipReady) {
               try {
                   log.aprintln("Unpacking cache database for " + factory.getName());
                   log.println("Opening zipfile: " + zipFile);

                   SqlDbCacheCreator.unpackDbZip(zipFile.getPath(), dbPath);

                   // Update the cache version information after unpacking database
                   // successfully.
                   storeCacheVersion();
                   unpacked = true;
               }
               catch (IOException e) {
                   log.aprintln(e);
                   log.aprintln("Error while unpacking cache zipfile");
               }
           }
       }
       
       return unpacked;
    }
    
    /**
     * Returns name of HSQLDB cache database lock file, not to be
     * confused with the stamp cache lock.
     *
     * @see #removeCacheDbLockFile
     * @see #lock
     */
    protected final String getCacheDbLockFilename()
    {
        return fullDbName + ".lck";
    }
    
    private File[] dbFileList;
    protected final File[] getCacheDbFileList()
    {
        if (dbFileList == null) {
            dbFileList = new File[] {
                                     new File(fullDbName + ".properties"), 
                                     new File(fullDbName + ".script"), 
                                     new File(fullDbName + ".data"),
                                     new File(fullDbName + ".backup"),
                                     new File(fullDbName + ".log"),
                                     new File(getCacheDbLockFilename())
                                    };
        }
        
        return dbFileList;
    }
    
    /**
     * Removes the HSQLDB cache database lock file if it exits. 
     * This should not be confused with the stamp cache lock.
     *
     * @see #getCacheDbLockFilename
     * @see #lock
     */
    synchronized protected final void removeCacheDbLockFile()
    throws IOException
    {
        String filename = getCacheDbLockFilename();
        File lockFile = new File(filename);
        if (lockFile.exists()) {
            log.println("Trying to remove HSQLDB lock file: " + filename);
            lockFile.delete();
            log.println("Removed HSQLDB lock file: " + filename);
        }
    }
    
    /**
     * Determines whether any of the component files that constitute
     * the cache database exist.
     * 
     * @return <code>true</code>, if one or more of the cache database
     * component files exists; otherwise, <code>false</code>.
     */
    protected final boolean cacheDbExists()
    {
        boolean exists = false;
    
        File[] toCheck = getCacheDbFileList();
        for (int i=0; i < toCheck.length; i++)
            if (toCheck[i].exists()) {
                exists = true;
                break;
            }
        
        return exists;
    }

    /**
     * Closes the database connection (shutting it down as well) and destroys all
     * files directly associated with the database and any cache version file.  
     */
    synchronized public void destroy()
    throws IOException
    {
        destroy(true);
    }
    
    /**
     * Closes the database connection (shutting it down as well) and destroys all
     * files directly associated with the database.  Can optionally also delete
     * the cache version file.
     * 
     * @param destroyVersionInfo If <code>true</code>, then the cache version file
     * is deleted; otherwise, not;
     */
    synchronized protected void destroy(boolean destroyVersionInfo)
    throws IOException
    {
        log.println("Entering, for: " + this.getClass().getName());
        
        try {
            close();
       
            log.println("destroying database for: " + sqlParms.cacheTableName);
            File[] toDelete = getCacheDbFileList();            
            for (int i=0; i < toDelete.length; i++)
                if (toDelete[i].exists())
                    toDelete[i].delete();
                
            if (destroyVersionInfo) {
                File versionFile = new File(getVersionFilename());
                if (versionFile.exists())
                    versionFile.delete();
            }
        }
        catch (Exception e) {
            log.aprintln(e);
            String msg = "Error while destroying database: " + e.getMessage();
            log.aprintln(msg);
            throw new IOException(msg);
        }
    }

    /**
     * The default implementation is to call {@link #destroy()}.  Subclasses
     * should override if they want additional files deleted whenever the
     * database is recreated from scratch.
     */
    protected synchronized void destroyAll() 
    throws IOException {
        destroy();
    }
    
    /**
     * Closes the database connection, which shuts it down as well.  Any changes
     * made will persistent until next session, i.e., between JMARS executions.  
     */
    synchronized public void close()
    throws SQLException
    {
        log.println("Entering, for: " + this.getClass().getName());
        
        try {
            if (connection != null) {
                log.println("closing database for: " + sqlParms.cacheTableName);
                connection.createStatement().execute("SHUTDOWN");
                connection = null;
            }
        }
        catch (SQLException e) {
        	log.println(e);
            
            // Ignore any "Connection is closed" messages; these are irrelevant
            // and seem to crop up on slow(?) computer (happened on a slower
            // Windows machine...)
            String msg = e.getMessage();
            if ( msg == null ||
                 msg.toLowerCase().indexOf("closed") < 0 )
                throw e;
        }
    }

    /**
     * Makes certain that the database closes down properly.
     */
    protected void finalize()
    {
        // Note: Any uncaught exception in the finalize() method is discarded 
        // by the JVM after being thrown.  In this case, however, we don't really care 
        // what is thrown since we can't do anything about it anyway.  The only 
        // problem showing up has been some mysterious "database already closed" 
        // errors under Windows....
        try {
        	close();
        }
        catch (SQLException e) {
            // log and ignore...
            log.println(e);
        }
    }
        
    
    /**
     * Returns cache version for the database structure and database
     * driver defined/used by the current software implementation.
     * 
     * @return Version for current database framework; never returns <code>null</code>.
     * @throws SQLException If an error occurs while determining current cache 
     * version.
     */
    protected SqlCacheVersion getCacheVersion()
    throws SQLException
    {
        return new SqlCacheVersion(getDriver(), sqlParms);
    }
    
    /**
     * Returns cache version information for the cache database at the
     * last time it was (re)created.
     * 
     * @return Last cache version; returns <code>null</code> if no version
     * file exists.
     * @throws IOException If an error occurs while reading version file.
     */
    synchronized protected SqlCacheVersion getLastCacheVersion()
    throws IOException
    {
        SqlCacheVersion version = null;
        
        File versionFile = new File(getVersionFilename());
        if (versionFile.exists()) {
            FileInputStream fin = new FileInputStream(versionFile);
            ObjectInputStream in = new ObjectInputStream(fin);
            
            try
            {
                version = (SqlCacheVersion)in.readObject();
            }
            catch (ClassNotFoundException e)
            {
                throw new IOException(e.getMessage());
            }
        }
        
        return version;
    }

    /**
     * Creates or overwrites cache version file to store version information for
     * the current cache database.  If the current version information is not
     * available, then any current version file is deleted.
     */
    synchronized protected void storeCacheVersion()
    throws IOException
    {
        SqlCacheVersion version = null;
        
        try {
            version = getCacheVersion();
        }
        catch (SQLException e) {
            throw new IOException(e.getMessage());
        }

        File versionFile = new File(getVersionFilename());
        if (version != null) {
            FileOutputStream fout = new FileOutputStream(versionFile);
            ObjectOutputStream out = new ObjectOutputStream(fout);
            
            out.writeObject(version);
            out.flush();
            out.close();
        }
        else if (versionFile.exists())
                 versionFile.delete();
    }

    /**
     * Returns database driver instance corresponding to cache's database 
     * access protocol.
     */
    protected Driver getDriver()
    throws SQLException
    {
        return DriverManager.getDriver("jdbc:hsqldb:" + fullDbName);
    }
    
    /**
     * It is recommended that each subclass override this method
     * to create a unique cache version filename for each different stamp
     * factory type that it instantiates a cache for..
     */
    protected String getVersionFilename()
    {    
        String dbPath = new File(fullDbName).getParent();
        return dbPath + File.separator + sqlParms.cacheTableName + VER_SUFFIX;
    }
    
    /**
     * Determines whether the version of the cache database has changed 
     * since the last time the cache database was (re)created.  The
     * version is considered changed if either the database driver or
     * the database table structure has changed.
     * 
     * @return <code>true</code>, if the version number of the driver has
     * changed; <code>false</code>, otherwise.
     */
    synchronized protected final boolean isVersionChanged()
    throws IOException
    {
        if (checkedVersion)
            return versionChanged;
        
        try {
            SqlCacheVersion oldVersion = getLastCacheVersion();
            SqlCacheVersion curVersion = getCacheVersion();
            if (curVersion == null ||
                !curVersion.equals(oldVersion))
                versionChanged = true;
            
            checkedVersion = true;
        }
        catch (Exception e) {
            log.println(e);
            throw new IOException(e.getMessage());
        }
        
        return versionChanged;
    }

    /**
     * Opens (if necessary) a connection to the HSQLDB cache database.
     * This is a single, shared connection that is kept open once
     * it is first created.  
     */
    synchronized protected final Connection getConnection()
    throws SQLException
    {
        // If not already open, create a singleton connection to the database and
        // keep it it open.  Although, multiple connections to HSQLDB in in-process 
        // mode are allowed(?), repeated opening/closing of connections can result
        // connection failure under heavy load.
        if (connection == null)
            connection = DriverManager.getConnection("jdbc:hsqldb:" + fullDbName, "sa", "");
        
        return connection;
    }

    /**
     * Does nothing, since the singleton connection is kept open
     * once created.
     */
    protected void releaseConnection(Connection connection)
    throws SQLException
    {
        // Do nothing.
    }
    
    /**
     * Updates cache database using stamp data in result set.  Each stamp record
     * in the result set which is a new stamp or has updated information
     * ('touched' field) will be inserted/updated.
     *
     * @param rs Specifies result set containing data update; must
     * contain data associated with the same type of stamp factory
     * as this instance.
     * @param allInsert Flag indicating that all records in the specified result
     * set are known to be new records relative to the current cache database.
     * This is used for performance optimization.  Use with caution.
     * 
     * @throws SQLException If there is an error, or if the <code>allInsert</code>
     * is set and a record is encountered which already exists in the cache
     * database.
     * 
     * @see StampFactory
     */
    synchronized public void update(ResultSet rs, boolean allInsert)
    throws SQLException
    {
        if (rs == null) {
            log.println("null result set");
            return;
        }
        
        String tableName = sqlParms.cacheTableName;
        String keyName = sqlParms.primaryKeyName;
        String touchedField = sqlParms.cacheLastModFieldName;
        
        String lastSql = null;
        PreparedStatement lastCmd = null;
        int examinedCount = 0;
        int newCount = 0;
        int updatedCount = 0;
        StopWatch watch = new StopWatch();

        try {
            Connection conn = getConnection();
            
            // Improve performance of inserts/updates.            
            lastSql = "SET WRITE_DELAY TRUE";
            conn.createStatement().execute(lastSql);
            lastSql = null;

            // Get field column info for result set
            ResultSetMetaData rsMetaData = rs.getMetaData();
            int rsColCount = rsMetaData.getColumnCount();

            // Get field column info for stamp table in cache database.
            lastSql = "SELECT TOP 1 * FROM " + tableName;
            ResultSet cacheRS = conn.createStatement().executeQuery(lastSql);
            lastSql = null;
            ResultSetMetaData cacheMetaData = cacheRS.getMetaData();

            // Verify number of columns match.
            int cacheColCount = cacheMetaData.getColumnCount();
            if (cacheColCount != rsColCount) {
                String msg = "Mismatch between number of columns for '" + tableName +
                              "': cache=" + cacheColCount +
                              "  update=" + rsColCount;
                log.aprintln(msg);
                throw new SQLException(msg);
            }
            
            int[] columnSqlTypes = new int[cacheColCount];
            String[] cacheColumnNames = new String[cacheColCount];
            for (int i=0; i < cacheColCount; i++) {
                columnSqlTypes[i] = cacheMetaData.getColumnType(i+1);
                cacheColumnNames[i] = cacheMetaData.getColumnLabel(i+1).toLowerCase();
            }
           
            // Set up some prepared commands for queries, inserts, and updates.
            PreparedStatement existTest = conn.prepareStatement("SELECT " + keyName +
                                                                "," + touchedField +
                                                                " FROM " + tableName + 
                                                                " WHERE " + keyName +
                                                                " = ?"
                                                               );
            PreparedStatement insertCmd = conn.prepareStatement("INSERT INTO " + tableName + getInsertColumns(cacheColumnNames) + 
                                                                " VALUES" + getInsertExpressions(cacheColCount) 
                                                               );
            PreparedStatement updateCmd = conn.prepareStatement("UPDATE " + tableName + 
                                                                " SET " + getUpdateExpressions(cacheColumnNames) +
                                                                " WHERE " + keyName +
                                                                " = ?"
                                                               );
            
            // Process result set records
            log.println("starting database update");
            watch.reset();
            while (rs.next()) {
                examinedCount++;
                
                if (!allInsert) {
                    // Check for existing record with same key
                    lastCmd = existTest;
                    existTest.setString(1, rs.getString(keyName));
                    cacheRS = existTest.executeQuery();
                
                    // If record with key exists, check whether the update set contains a newer
                    // record than the cache database.  Otherwise, insert the new record into
                    // the cache.
                    if (cacheRS.next()) {
                        Timestamp rsTouched = getTimestamp(rs, touchedField);
                        Timestamp cacheTouched = getTimestamp(cacheRS, touchedField);
                        
                        // Check for newer record than in cache.
                        if (rsTouched == null ||
                            cacheTouched == null)
                            log.aprintln("retrieved null value for field: " + touchedField);
                        else if (rsTouched.compareTo(cacheTouched) > 0) {
                            lastCmd = updateCmd;
                            copyToCmd(updateCmd, rs, columnSqlTypes);
                            updateCmd.executeUpdate();
                            updatedCount++;
                        }
                        
                        // Skip to next row in result set.
                        continue;
                    }
                }
                
                // Since an existing record was either not found or is known to
                // not exist, insert new record
                lastCmd = insertCmd;
                copyToCmd(insertCmd, rs, columnSqlTypes);
                insertCmd.executeUpdate();
                newCount++;
            }
        }
        catch (SQLException e) {
            log.aprintln(e);
            if (lastSql != null)
                log.aprintln("Tried SQL: " + lastSql);
            if (lastCmd != null)
                log.aprintln("Tried SQL: " + lastCmd.toString());
            throw e;
        }
        finally {
            log.println("Stopped updating database: elapsed time = " +
                        watch.elapsedMillis()/1000 + " seconds");
            log.println("examined " + examinedCount + " records");
            log.println("added " + newCount + " records");
            log.println("updated " + updatedCount + " records");
        }
    }

    /**
     * Retrieves timestamp from result set.  Handles messy type
     * conversion issues.
     * 
     * @param rs Result set must already be located at the correct row.
     * @param fieldName Name of field in result set containing timestamp info. 
     * @return Timestamp or <code>null</code>
     * @throws SQLException Whenever the whole friggin' thing fails.
     */
    private Timestamp getTimestamp(ResultSet rs, String fieldName)
    throws SQLException
    {
        Timestamp timestamp = null;
        
        if (rs != null)
        	timestamp = getTimestamp(rs, rs.findColumn(fieldName));
        
        return timestamp;
    }
    
    /**
     * Retrieves timestamp from result set.  Handles messy type
     * conversion issues.
     * 
     * @param rs Result set must already be located at the correct row.
     * @param column index for result set column containing timestamp field 
     * @return Timestamp or <code>null</code>
     * @throws SQLException Whenever the whole friggin' thing fails.
     */
    private Timestamp getTimestamp(ResultSet rs, int column)
    throws SQLException
    {
        Timestamp timestamp = null;
        
        if (rs != null)
        {
            // Handling of the last-modification time in the both the
            // cache and import cache records (if this is the update
            // result set) is a bit tricky.   HSQLDB has demonstrated
            // some bizarre behavior involving the Timestamp java
            // type, the TIMESTAMP datatype in SQL, and the CHAR
            // datatype in SQL.
            //
            // So, we do it the hard way.
            Object rsObj = rs.getObject(column);
            if (rsObj != null) {
            	if (rsObj.getClass() == Timestamp.class)
                    timestamp = (Timestamp)rsObj;
                else {
                    try {
                        String timeStr = rsObj.toString();
                        java.util.Date date = timestampFormatter.parse(timeStr);
                        timestamp = new Timestamp(date.getTime());
                    }
                    catch (ParseException e) {
                        String msg = "Timestamp data conversion failed for: " + rsObj;
                        log.aprintln(msg);
                        throw new SQLException(msg);
                    }
                }
            }
        }
        
        return timestamp;
    }

    
    /**
     * Copies the result set's field values to the specified 
     * {@link PreparedStatement} instance's SQL command expression 
     * parameters.  Arguments are copied in column order from
     * the result set into the command in the same order, using
     * the specified SQL data types to as parameter types.
     * 
     * @param updateCmd Destination SQL command to be filled with arguments.
     * @param rs Source Result set containing argument data.
     * @param sqlTypes SQL data types for arguments in result set.
     *
     * @see java.sql.Types
     */
    private void copyToCmd(PreparedStatement cmd, ResultSet rs, int[] sqlTypes)
    throws SQLException
    {
        int i = 0;
        
        try {
            if (cmd != null &&
                rs != null &&
                sqlTypes != null)
                for (i=0; i < sqlTypes.length; i++)
                {
                    Object obj = rs.getObject(i+1);
                    int targetType = sqlTypes[i];
                    
                    // Check whether the field being copied is a Timestamp
                    // instance and the target is one of the SQL string
                    // types.  If so, we do the value conversion directly
                    // here to be consistent with how timestamps are
                    // perceived and handled by the stamp layer and in
                    // the two different data sources (main database and
                    // import cache file).
                    if (obj != null &&
                        obj.getClass() == Timestamp.class &&
                        ( targetType == Types.CHAR || 
                          targetType == Types.LONGVARCHAR || 
                          targetType == Types.VARCHAR))
                    {
                    	String timeStr = timestampFormatter.format((Timestamp)obj);
                        cmd.setObject(i+1, timeStr, sqlTypes[i]);
                    }
                    else
                    	cmd.setObject(i+1, obj, sqlTypes[i]);
                }
        }
        catch (SQLException e) {
            log.aprintln(e);
            log.aprintln("SQL error while copying field column #" + (i+1));
            throw e;
        }
    }

    /**
     * Returns column name string appropriate for an SQL "INSERT"
     * command containing the specified column names the listed order
     * correct SQL syntax.  
     */
    private String getInsertColumns(String colNames[])
    {
        StringBuffer buf = new StringBuffer();
        
        buf.append('(');
        if (colNames != null) {
            for (int i=0; i < colNames.length - 1; i++) {
                buf.append(colNames[i]);
                buf.append(',');
            }
        
            buf.append(colNames[colNames.length - 1]);
        }
        buf.append(')');
            
        return buf.toString();
    }

    /**
     * Returns expression string appropriate for an SQL "INSERT"
     * command corresponding to the specified number of columns.  
     * Returned string lists the expression arguments in with 
     * correct SQL syntax.  Each column argument is a question mark ("?") 
     * for use with {@link PreparedStatement} instances.
     */
    private String getInsertExpressions(int colCount)
    {
        StringBuffer buf = new StringBuffer();
        
        buf.append('(');
        if (colCount > 0) {
            for (int i=0; i < colCount - 1; i++)
                buf.append("?,");
        
            buf.append("?");
        }
        buf.append(')');
        
        return buf.toString();
    }

    /**
     * Returns combined column-expression string appropriate for an SQL 
     * "UPDATE" command corresponding to the specified column names.  
     * Returned text lists the fields in order with correct SQL syntax.  
     * A question mark ("?") is put in place of the expression arguments 
     * for use with {@link PreparedStatement} instances.
     */
    private String getUpdateExpressions(String colNames[])
    {
        StringBuffer buf = new StringBuffer();
        
        if (colNames != null) {
            for (int i=0; i < colNames.length - 1; i++) {
                buf.append(colNames[i]);
                buf.append(" = ?, ");
            }
        
            buf.append(colNames[colNames.length - 1]);
            buf.append(" = ?");
        }
            
        return buf.toString();
    }

    /**
     * Returns load completion status for cache.  This status is
     * set by the {@link #updateDB} method.  The return value of
     * this method is only useful if the update process finished
     * either normally or incompletely; otherwise, an exception
     * is thrown by the update.
     * 
     * @return Returns <code>true</code>, if cache load completed normally
     * or failed completely; <code>false</code>, if the cache load was 
     * incomplete but the cache is still usable.
     * 
     * @see #setLoadIncomplete
     * @see #getLoadIncompleteReason
     * @see #updateDB
     */
    public synchronized final boolean isLoadIncomplete()
    {
        return loadIncomplete;
    }

    /**
     * Sets status of whether or not the cache was loaded completely.
     * Reason for status may optionally be set as well.
     * 
     * @param loadIncomplete Load incomplete tatus (true/false).
     * @param reason Cause of status, if any; may be <code>null</code>.
     * 
     * @see #isLoadIncomplete
     * @see #getLoadIncompleteReason
     */
    protected synchronized final void setLoadIncomplete(boolean loadIncomplete, String reason)
    {
        this.loadIncomplete = loadIncomplete;
        loadIncompleteReason = reason;
    }
    
    /**
     * Returns description of cause for load completion status, if any.
     * 
     * @see #isLoadIncomplete
     * @see #setLoadIncomplete
     */
    public synchronized final String getLoadIncompleteReason()
    {
        return loadIncompleteReason;
    }

    /**
     * Updates the cache database using the import stamp data cache file
     * (if available, changed, and configured) and the main database server
     * (if network access is allowed).
     * <p>
     * With either update data source, only new and changed records are either
     * inserted or updated in the cache database.
     * <p>
     * If the stamp data table does not yet exist in the cache database, it
     * is created.
     * <p> 
     * In addition to updating the cache, this method sets the load completion status.  
     * This status can be retrieved via the {@link #isLoadIncomplete} method.  This
     * status is only useful if the update process finished either normally or incompletely.
     * If this method throws an exception, then the load is not considered incomplete.
     * 
     * @throws IOException Thrown whenever a fatal except occurs.
     * 
     * @see #isLoadIncomplete
     * @see #getLoadIncompleteReason
     */
    synchronized public void updateDB()
    throws IOException
    {
        boolean newTable = false;
        
        log.println("Entering, for table: " + sqlParms.cacheTableName);
        setLoadIncomplete(false, null);

        try {
            newTable = createTable();
        }
        catch (SQLException e) {
            log.aprintln(e);
            String msg = "Could not create stamp data cache table: " + e.getMessage();
            log.aprintln(msg);
            throw new IOException(msg);
        }
        catch (IOException e) {
            // This is only thrown for a failure to store the cache version info.
            // Although undesirable, this is not a fatal condition, so let's press
            // on.
        }
        
        String lastSql = "";
        StopWatch watch = new StopWatch();
        try
        {
            // Only import stamp data from import cache file if import
            // has not been restricted to just the main database.
            if (!mainDbImportOnly)
            {
                // First: update stamp data from raw cached data file if it
                // is available and is a new/different file than the last successful
                // import.  The update only uses new/changed rows.
                log.println("starting import of cache file");
                SqlStampImporter cacheImport = new SqlStampImporter(getFactory(), IMPORT_PREFIX + sqlParms.cacheTableName);
                log.println("ending import of cache file: elapsed time = " +
                            watch.lapMillis()/1000 + " seconds");
               
                if (cacheImport.isNew())
                {
                    if (cacheImport.importData()) {
                        log.println("Querying imported stamp cache");
                        lastSql = sqlParms.cacheQuerySql;
                        ResultSet importSet = cacheImport.executeQuery(lastSql);
                        lastSql = null;
                        log.println("End of cached stamp import query: elapsed time = " + 
                                    watch.lapMillis()/1000 + " seconds");
                        
                        log.println("Applying changes from imported stamp cache");
                        update(importSet, newTable);
                        log.println("End of update from cached stamp import");
                        
                        // Clear "new table" status if we successfully imported.
                        newTable = false;
                    }
                    else
                        log.println("No cached stamp data file could be imported");
                }
                else
                    log.println("Cache import skipped since file is not new");
            }
            else
                log.println("Only main database import is enabled; cache file import skipped.");
        }
        catch (Exception e)
        {
            // Importing from a cache file is allowed to fail since it may not exist, or
            // is corrupted, etc., and we can usually just update from the main database.
            log.println(e);
            log.println("Import update from cached stamp data file failed");
            log.println("Tried SQL: " + lastSql);
        }
        finally {
            log.println("End of cached stamp import phase: total elapsed time = " + 
                        watch.elapsedMillis()/1000 + " seconds");
        }

        watch.reset();
        ResultSet updateSet = null;
        try {            
            // Second: update from main database server if network access is available
            // and a main database was specified.
            if ((Main.useNetwork() || ignoreUseNetwork) &&
                dbUrl != null &&
                dbUrl.trim().length() > 0)
            {
                Timestamp minDateTime = new Timestamp(0);
                int minOrbit = Integer.MIN_VALUE;
                SqlStampSource mainServer = new SqlStampServer(getFactory(), dbUrl);

                // Set up query for update from main database.  Just retrieve new
                // or changed records unless this is a newly-created table.
                String mainDbSql = sqlParms.sql;
                boolean runOnce = true;
                while (!newTable &&
                       runOnce)
                {
                    // Check whether cache table actually contains records.  The MAX
                    // orbit number query below returns an answer regardless
                    // of whether there are rows in the table, and we want to
                    // establish a valid minimum orbit constraint for the query
                    // to the main database later on below.
                    log.println("Determining number of cache records");
                    lastSql = "SELECT COUNT(*) FROM " + sqlParms.cacheTableName;
                    ResultSet cacheRS = getConnection().createStatement().executeQuery(lastSql);
                    int numCacheRecs = 0;
                    if (cacheRS.next()) {
                        // Don't get info for main DB query constraints below if there
                        // are no cache records.
                        if ((numCacheRecs = cacheRS.getInt(1)) < 1)
                            break;
                    }
                    else
                        break;
                    log.println("Number of cache records before update from main database: " + numCacheRecs);
                                        
                    // Determine highest orbit number in cache records.
                    log.println("Determining last orbit number of cache records");
                    lastSql = "SELECT MAX(" + sqlParms.orbitFieldName + 
                              ") FROM " + sqlParms.cacheTableName;
                    cacheRS = getConnection().createStatement().executeQuery(lastSql);
                    if (cacheRS.next())
                        minOrbit = cacheRS.getInt(1);
                    log.println("minOrbit = " + minOrbit);
                        
                    // Determine most recent last-modification-time in cache records.
                    log.println("Determining last modification time of cache records");
                    lastSql = "SELECT MAX(" + sqlParms.cacheLastModFieldName + 
                              ") FROM " + sqlParms.cacheTableName;
                    cacheRS = getConnection().createStatement().executeQuery(lastSql);
                    if (cacheRS.next())
                        minDateTime = getTimestamp(cacheRS, 1);
                    if (minDateTime == null)
                        log.println("minDateTime = null");
                    else
                        log.println("minDateTime = " + timestampFormatter.format(minDateTime));
                    
                    // Append orbit number and last-modification-time restrictions from above
                    // to base query
                    log.println("Querying main database for new/updated records");
                    mainDbSql = appendRestrictions(mainDbSql, minOrbit, minDateTime,
                                                   sqlParms.serverLastModFieldName);
                    
                    runOnce = false;
                }
                
                // Send modified query to main database.
                watch.lapMillis();
                lastSql = mainDbSql;
                updateSet = mainServer.executeQuery(mainDbSql);
                log.println("End of main database query: elapsed time = " +
                            watch.lapMillis()/1000 + " seconds");
                
                // Apply updates, always assuming that records may exist already, even
                // with a new table being constructed: the main database is known to
                // (sometimes) contain duplicate records within itself.
                log.println("Applying changes from main database");
                update(updateSet, false);
                lastSql = null;
                log.println("End of update from main database");
            }
            else
                log.println("Skipping main database update phase:  no main database specified");
        }
        catch (Exception e)
        {
            // A failed attempt to update from the main database server is a 
        	// moderately significant event, so log this error and mark the
        	// cache as being load-incomplete.
        	//
        	// NOTE: The cache is still *usable*, whether or not it contains
        	// any records.
            log.println(e);
            String msg = "Update from main database server failed: ";
            log.println(msg);
            log.println("Tried SQL: " + lastSql);
            setLoadIncomplete(true, "Cache load/update incomplete: " + msg + e.getMessage());
        }
        finally {
            if (updateSet != null)
                try {
                    // Release result set from main database server; this should 
                    // also release the database connection due to implementation 
                    // of SqlStampServer class.
                    //
                    // This addresses a possible problem with THEMIS database
                    // server building up a backlog of stale connections.  Don't
                    // whether this code has contributed to the problem or not,
                    // but just to be safe....
                    log.println("Releasing/close result set from database server");
                    updateSet.close();
                }
                catch (SQLException e)
                {
                    log.aprintln(e);
                }
                finally {
                    updateSet = null;
                }
                
            log.println("End of main database update phase: total elapsed time = " + 
                        watch.elapsedMillis()/1000 + " seconds");
        }
    }
    
    /**
     * Returns an SQL query with a WHERE conditional appended
     * (or an existing WHERE clause appended to) that restricts
     * the results to either having a minimum orbit number or
     * a minimum modification time (one or both conditions may
     * apply as part of an OR test, or only one condition may
     * be part of the query).
     * 
     * @param sql Base SELECT query text 
     * @param minOrbit Minimum acceptable orbit number; if 
     * {@link Integer#MIN_VALUE}, then is conditional is excluded.
     * @param minDataTime Minimum timestamp value; if equivalent to
     * January 1, 1970 00:00:00 GMT, then this conditional is excluded.
     * @param toucheFieldName Name of field in table containing the
     * last modification time.
     * @return Modified query text with added orbit constraint
     */
    private String appendRestrictions(String sql, int minOrbit, Timestamp minDateTime,
                                      String touchedFieldName)
    {
        Timestamp noTime = new Timestamp(0);
        String conditional = "";
        
        if (minDateTime == null)
            minDateTime = noTime;
        String timeStr = timestampFormatter.format(minDateTime);

        // Construct the conditional restrictions that apply.  For orbit
        // restrictions, the test is "greater than or equal" since we can't
        // be certain that previous orbit data was complete.  However, for
        // the last-modification time, a "greater than" test is both correct
        // and preferred; the stored time granularity is down to seconds.
        if (minOrbit != Integer.MIN_VALUE && 
            noTime.equals(minDateTime))
            conditional = sqlParms.orbitFieldName + " >= " + minOrbit;
        else if (minOrbit == Integer.MIN_VALUE && 
                !noTime.equals(minDateTime))
            conditional = touchedFieldName + " > " + timeStr;
        else if (minOrbit != Integer.MIN_VALUE && 
                !noTime.equals(minDateTime))
            conditional = "(" + sqlParms.orbitFieldName + " >= " + minOrbit + " or " +
                                touchedFieldName + " > " + timeStr + ")";
        else
            // Neither conditional is being applied
            return sql;
        
        // Insert conditional restriction into existing query based on
        // presence/location of any existing WHERE clause.
        final String keyword = "where";
        StringBuffer buf = new StringBuffer(sql);
        int idx0 = sql.toLowerCase().indexOf(keyword);
        if (idx0 < 0)
            buf.append(" where " + conditional);
        else
            buf.insert(idx0 + keyword.length(), 
                        " " + conditional + " and ");
        
        return buf.toString();
    }
    
    /**
     * Creates table and indexes in database for stamp data.
     * 
     * @return <code>true</code>, if table and indexes were created; <false>, if
     * the table already existed.
     * @throws SQLException Failed to open connection to cache database.
     * @throws IOException Failed to create/update cache version info file.
     */
    synchronized private boolean createTable()
    throws SQLException, IOException
    {
        boolean newTable = false;
        String lastSql = null;
        Connection conn = getConnection();
    
        try {
            lastSql = "CREATE CACHED TABLE " + sqlParms.cacheTableName + 
                      " (" + sqlParms.cacheTableFieldsSql + ")";
            conn.createStatement().execute(lastSql);
            
            // If we reached this line, then a new table was indeed created,
            // so log this.
            newTable = true;
            log.println("created table: " + sqlParms.cacheTableName);
        }
        catch (SQLException e) {
            // Ignore any error while creating table since in most cases this 
            // will simply mean that the table already exists.  (Unfortunately,
            // the HSQLDB dialect of SQL does not support conditional table
            // creation options.)
            log.println(e);
            while ((e = ((SQLException)e).getNextException()) != null)
                log.println(e.getMessage());
            log.println("Tried SQL: " + lastSql);
        }
        
        // Note: indexes are optional....
        if (sqlParms.cacheIndexFields != null)
            for (int i=0; i < sqlParms.cacheIndexFields.length; i++) {
                try {
                    lastSql = "CREATE INDEX idx"+ i+ " ON " + sqlParms.cacheTableName +
                    " (" + sqlParms.cacheIndexFields[i] + ")";
                    conn.createStatement().execute(lastSql);
                }
                catch (SQLException e) {
                    // Ignore any error while creating indices since in most cases this 
                    // will simply mean that they already exists.  (Unfortunately,
                    // the HSQLDB dialect of SQL does not support conditional table
                    // creation options.)
                    log.println(e);
                    while ((e = ((SQLException)e).getNextException()) != null)
                    	log.println(e.getMessage());
                    log.println("Tried SQL: " + lastSql);
                }
            }
        
        try {
            // If the table was actually created (it didn't exist), let's store 
            // the cache version info.
            if (newTable)
            	storeCacheVersion();
        }
        catch (IOException e) {
            // This is a true problem: the store operation for the cache
            // version info failed.
            log.println(e);
            String msg = "Failed to store stamp cache version information";
            log.aprintln(msg);
            throw new IOException(msg + e.getMessage());
        }
        
        return newTable;
    }

}
