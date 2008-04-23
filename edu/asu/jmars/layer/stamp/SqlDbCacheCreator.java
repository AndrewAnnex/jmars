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
import java.util.*;
import java.util.zip.*;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.stamp.SqlStampLayer.SqlParameters;
import edu.asu.jmars.util.DebugLog;

/**
 */
public class SqlDbCacheCreator
{
    private static final DebugLog log = DebugLog.instance();
    private static final String CACHE_ZIP_SUFFIX = ".zip";
    private static final int MIN_DBFILES = 4;
    
    private StampFactory factory;
    private String dbUrl;


    /**
     * 
     */
    public SqlDbCacheCreator(StampFactory factory, String dbUrl)
    {
        this.factory = factory;
        this.dbUrl = dbUrl;
    }

    public void createDbCacheZip(String filename)
    throws IOException
    {
        try {
            SqlStampLayer.SqlParameters sqlParms = factory.getBaseSqlParms();
            
            // Create cache database using just stamp data from main database.
            log.println("creating database: " + sqlParms.cacheTableName);
            TempStampCache cache = new TempStampCache(factory, sqlParms.cacheTableName, dbUrl, true);
            log.aprintln("updating database: " + sqlParms.cacheTableName);
            cache.updateDB();

            // Close database.
            log.println("closing database: " + sqlParms.cacheTableName);
            cache.close();
            
            // Create zip-file containing all files that constitute the
            // cache database.
            log.aprintln("compressing database " + sqlParms.cacheTableName + " to zipfile....please wait");
            ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));
            String lockFile = cache.getCacheDbLockFilename();
            File[] dbFiles = cache.getCacheDbFileList();
            
            if (dbFiles == null)
                log.aprintln("null cache database filelist");
            else
            {
                int entryCount = 0;
                
                zip.setLevel(9);
                for (int i=0; i < dbFiles.length; i++)
                {
                    if (dbFiles[i] == null)
                        log.aprintln("cache database filelist: null file #" + i);
                    // By Michael: the .backup file is skipped, since we create it at
                    // unpacking time (down in unpackDbZip).
                    else if(dbFiles[i].getName().endsWith(".backup"))
                     {
                        log.aprintln("SKIPPING file: " + dbFiles[i].getName());
                        entryCount++;
                     }
                    // Ignore the lockfile and any file which doesn't exist.
                    else if (!dbFiles[i].getPath().equals(lockFile) &&
                             dbFiles[i].exists())
                    { 
                        // Write next database file to zip-file.
                        log.aprintln("zipping file: " + dbFiles[i].getName());
                        ZipEntry entry = new ZipEntry(dbFiles[i].getName());
                        zip.putNextEntry(entry);

                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] temp = new byte[64 * 1024];
                        InputStream fin = new BufferedInputStream(new FileInputStream(dbFiles[i]));
                        
                        int count;
                        while((count = fin.read(temp)) >= 0)
                            zip.write(temp, 0, count);
                        
                        zip.closeEntry();
                        entryCount++;
                    }
                }
                
                if (entryCount < MIN_DBFILES)
                    log.aprintln("WARNING: Created zipfile contains only " + entryCount +
                                 " files; should have been at least " + MIN_DBFILES +
                                 " files for a valid cache database.");
            }
            
            zip.flush();
            zip.close();
        }
        catch (Exception e) {
            log.println(e);
            throw new IOException(e.getMessage());
        }
    }
    
    protected class TempStampCache extends SqlStampCache
    {
        TempStampCache(StampFactory factory, String name, String dbUrl, boolean mainDbImportOnly)
        throws IOException, CacheLockException
        {
            super(factory, name, dbUrl, mainDbImportOnly);
            ignoreUseNetwork = true;
        }
        
        protected String makeDbName(SqlParameters sqlParms)
        {
            String dbName = null;
            
            try {
                File tempDir = File.createTempFile(sqlParms.cacheTableName, "");
                tempDir.delete();
                tempDir.mkdir();
                
                String tempPath = tempDir.getPath(); 
                String fs = System.getProperty("file.separator");
    
                if (tempPath == null ||
                    tempPath.equals(""))
                    dbName = sqlParms.cacheTableName + DB_SUFFIX;
                else
                    dbName = tempPath + fs + sqlParms.cacheTableName + DB_SUFFIX;
            }
            catch (IOException e) {
                log.aprintln(e);
            }
            
            return dbName;
        }
    }
    
    /**
     * Returns standard cache ZIP file name used for specified stamp
     * factory.
     */
    public static String getZipFilename(StampFactory factory)
    {
        String zipName = null;
        
        if (factory != null) {
            SqlStampLayer.SqlParameters sqlParms = factory.getBaseSqlParms();
            zipName = sqlParms.cacheTableName + CACHE_ZIP_SUFFIX;
        }
        
        return zipName;
    }
    
    /**
     * Creates cache databases for all stamp layers which have them (MOC
     * and THEMIS) from specified main SQL database and stores them in 
     * appropriately named ZIP-compressed files in the specified
     * directory.  Used to create files for either public or student 
     * versions of JMARS.
     *  
     * @param dirPath Path to directory in which zipfiles are to be created.
     * 
     * @param forStudent  Controls whether or not the stamp cache
     * ZIP files are for the student version of JMARS.
     * 
     * @return Returns list of ZIP files containing the cache database,
     * one database per file.  Returns <code>null</code> if one of the
     * passed arguments is <code>null</code>. 
     */
    public static File[] createDbZips(String dirPath, boolean forStudent)
    throws IOException
    {
        File[] zipFiles = null;
        
        if (dirPath == null)
            log.aprintln("null directory path for zipfiles");
        else
        {
            List fileList = new ArrayList();
            StampFactory[] factories;
            
            if (forStudent)
                factories = new StampFactory[] {
                                                new ThemisBtrStampFactory()
                                                {
                                                    // This is overridden in order to create a
                                                    // student version of the stamp cache ZIP from
                                                    // a non-student-version of JMARS.
                                                    //
                                                    // This allows us to work around a variety of issues
                                                    // involving the packaging of JMARS and still
                                                    // access the main THEMIS database to provide
                                                    // non-public VIS data for targeting purposes
                                                    // in Student JMARS.
                                                    protected boolean isStudentApplication()
                                                    {
                                                        return true;
                                                    }
                                                }
                                               };
            else
                factories = new StampFactory[] {
                                                new ThemisBtrStampFactory(),
                                                new MocStampFactory()
                                               };
            
            try {
                for (int i=0; i < factories.length; i++)
                    if (factories[i] != null)
                    {
                        String zip = dirPath + getZipFilename(factories[i]);
                        log.aprintln("creating " + factories[i].getName() + 
                                     " cache database zipfile: " + zip);
                        SqlDbCacheCreator zipCreator = new SqlDbCacheCreator(factories[i], 
                                                                             factories[i].getDbUrl() +
                                                                                 "user=" + Main.DB_USER +
                                                                                 "&password=" + Main.DB_PASS);
                
                        zipCreator.createDbCacheZip(zip);
                        fileList.add(new File(zip));
                    }                
            }
            catch (IOException e) {
                log.println(e);
                throw e;
            }
            
            zipFiles = (File[]) fileList.toArray(new File[0]);
        }
        
        return zipFiles;
    }

    /**
     * Unpacks cache database from zipfile into specified directory.
     * 
     * @param zipFilename Name of zipfile
     * @param dbPath      Directory path for unzipping cache database files.
     * 
     * @throws IOException Thrown if anything goes wrong during unpacking
     * process.
     */
    public static void unpackDbZip(String zipFilename, String dbPath)
    throws IOException
    {
        if (zipFilename == null ||
            zipFilename.trim().length() < 1)
        {
            log.aprintln("null or blank ZIP filename");
            return;
        }
        else if (dbPath == null) {
            log.aprintln("null database path");
            return;
        }
        
        if (!dbPath.endsWith(File.separator))
            dbPath = dbPath + File.separator;
        
        ZipFile zip = new ZipFile(zipFilename);
        Enumeration entries = zip.entries();
        
        while (entries.hasMoreElements()) {
            // Get next database component file from ZIP.
            ZipEntry zipEntry = (ZipEntry) entries.nextElement();
            InputStream zin = zip.getInputStream(zipEntry);
            
            if (zin == null)
                throw new IOException("Could not read entry '" + zipEntry.getName() +
                                      "' from zipfile: " + zipFilename);
            else {
                // Copy database component from ZIP and cache database directory.
                log.println("Copying component '" + zipEntry.getName() +
                "' from zipfile");
                String filename = dbPath + zipEntry.getName();
                BufferedOutputStream outfile = new BufferedOutputStream(new FileOutputStream(filename));
                BufferedInputStream bin = new BufferedInputStream(zin);
                
                // By Michael: If non-null, used to create the .backup file.
                DeflaterOutputStream backupOut = null;
                if(filename.endsWith(".data")) {
                    String backupFName = filename.replaceAll("\\.data$", ".backup");
                    log.println("Creating " + backupFName + " on-the-fly");

                    // This code comes from ZipUnzipFile.compressFile in the hsqldb
                    // source. We use the code directly, instead of calling
                    // compressFile, because that function performs the compression
                    // by filename... we want access to a raw stream.
                    backupOut = new DeflaterOutputStream(
                        new BufferedOutputStream(new FileOutputStream(backupFName), 64*1024),
                        new Deflater(Deflater.BEST_SPEED), 64*1024);
                }

                byte[] buf = new byte[64 * 1024];
                int count;
                while((count = bin.read(buf)) >= 0) {
                    outfile.write(buf, 0, count);
                    if(backupOut != null)               // By Michael: Generate the
                        backupOut.write(buf, 0, count); // .backup file in parallel.
                }
                
                outfile.flush();
                outfile.close();
                if(backupOut != null) // By Michael
                    backupOut.close();
                bin.close();
            }
        }
    
        zip.close();
    }
}
