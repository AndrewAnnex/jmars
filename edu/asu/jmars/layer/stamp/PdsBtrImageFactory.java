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
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;


/**
 * Factory for creating instances of {@link PdsBtrImage} from
 * image data contained in files, URLs, and any {@link Stamp} 
 * which contains a valid URL.  Also supports specialized
 * search parameters related to multiple possible image sources
 * and/or naming conventions and webserver authentication
 * handling.
 * <p>
 * Search parameters used by this class are defined in 
 * {@link PdsBtrImage.SearchParameters} instances.  Ideally this 
 * abstraction should be located in this class, but for 
 * backward-compatibility with ".jmars" files created by previous
 * JMARS versions, it is kept in its current location. 
 * 
 * @author hoffj MSFF-ASU
 */
public class PdsBtrImageFactory extends PdsImageFactory
{
    private static final DebugLog log = DebugLog.instance();

    public static final String BTR_SUFFIX = "BTR.IMG";
    public static final String ABR_SUFFIX = "ABR.IMG";
    
    // Image source search values
    public static final int LOCAL_DIR = 0;
    public static final int LOCAL_WEBSERVER = 1;
    public static final int THEMIS_WEBSERVER = 2;
    public static final int NO_SEARCH_SOURCE = 3;
    
    // Keys used to look up path values via Config and UserProperties.
    protected static final String LOCAL_DIR_PATH_KEY = "stamps.btr.local_dir";
    protected static final String LOCAL_HTTP_PATH_KEY = "stamps.btr.local_http";
    protected static final String THEMIS_HTTP_PATH_KEY = "stamps.btr.themis_http";
    protected static final String BTR_THEMIS_SUBDIR_KEY = "stamps.btr.btr_subdir";
    protected static final String ABR_THEMIS_SUBDIR_KEY = "stamps.btr.abr_subdir";
    
    private static final int THEMIS_SUBDIRECTORY_PREFIX_LENGTH = 4;
    private static final String BTR_THEMIS_SUBDIRECTORY_SUFFIX = "xxbtr";
    private static final String ABR_THEMIS_SUBDIRECTORY_SUFFIX = "xxabr";
    
    // Webserver user authentication 
    private final int WEB_AUTH_ATTEMPTS_LIMIT = 5;
    private final HashMap userMap = new HashMap();
    private final HashMap passMap = new HashMap();
    private final WebAuthenticator webAuth = new WebAuthenticator();
    

    public static void main(String[] av)
    {
        PdsBtrImageFactory factory = new PdsBtrImageFactory();
        StampImage pdsi = factory.load(av[0]);
        
        BufferedImage img = pdsi.getImage(0);
        
        log.println("filename = " + ((PdsBtrImage)pdsi).filename);
        log.println("Start time ET = " + ((PdsBtrImage)pdsi).getStartTimeEt());
        log.println("Image data:");
        log.println(img.toString());
        
        System.exit(0);
    }
    
    /**
     * 
     */
    public PdsBtrImageFactory()
    {
        super();
    }
    
    // Returns the default local directory path for retrieval of image files.
    public String getLocalDirPath()
    {
        String dirPath = Config.get(LOCAL_DIR_PATH_KEY);
        
        if (dirPath != null &&
            !dirPath.endsWith("/"))
            dirPath = dirPath + "/";
        
        return dirPath;
    }
    
    // Returns the default local webserver path for retrieval of image files.
    public String getLocalHttpPath()
    {
        String httpPath = Config.get(LOCAL_HTTP_PATH_KEY);
        
        if (httpPath != null &&
            !httpPath.endsWith("/"))
            httpPath = httpPath + "/";
        
        return httpPath;
    }
    
    // Returns the default base THEMIS webserver path for retrieval of image files.
    public String getThemisHttpPath()
    {
        String httpPath = Config.get(THEMIS_HTTP_PATH_KEY);
        
        if (httpPath != null &&
            !httpPath.endsWith("/"))
            httpPath = httpPath + "/";
        
        return httpPath;
    }
    
    public String getBtrHttpPath(String basePath)
    {
        String subdirPath = Config.get(BTR_THEMIS_SUBDIR_KEY);
        
        if (subdirPath != null &&
            !subdirPath.endsWith("/"))
            subdirPath = subdirPath + "/";
        
        return basePath + subdirPath;
    }
    
    public String getAbrHttpPath(String basePath)
    {
        String subdirPath = Config.get(ABR_THEMIS_SUBDIR_KEY);
        
        if (subdirPath != null &&
            !subdirPath.endsWith("/"))
            subdirPath = subdirPath + "/";
        
        return basePath + subdirPath;
    }
    
    // Used to pass back user cancellation of
    // user authorization for webservers requiring this.
    private static class CancelException extends Exception
    {
    }
    
    // Used to indicate webserver timeouts.
    private static class TimeoutException extends Exception
    {
    }
    
    public StampImage load(URL url)
    throws CancelException, TimeoutException
    {
        if (url == null)
            return null;
        
        try {
            return load(url, true, false);
        }
        catch (Throwable e) {
            if (e instanceof CancelException)
                throw (CancelException)e;
            else if (e instanceof TimeoutException)
                throw (TimeoutException)e;
        }
        
        return null;
    }
    
    
    // @param throwExceptions controls throwing of all exceptions
    //        except for the CancelException.
    public StampImage load(URL url, boolean checkAuthenticationError, 
                           boolean throwExceptions) 
    throws Exception
    {
        StampImage image = null;
        
        if (url == null)
            return null;
        
        log.println("loading " + url);
        
        // Repeat attempts to load image from URL so long as 401 user
        // authorization errors are received and the user does not
        // cancel authorization attempts via the dialog box put up
        // by the WebAuthenticator instance.
        boolean repeat = true;
        int count = 0;
        
        // First test whether webserver specified by URL is alive
        if (!isAlive(url)) {
            String host = url.getHost();
            
            if (!timeoutHosts.contains(host)) {
                JOptionPane.showMessageDialog(null,
                                              "Timeout accessing webserver " + host + "\n",
                                              "WEBSERVER ACCESS ERROR",
                                              JOptionPane.ERROR_MESSAGE);
                
                timeoutHosts.add(host);
            }
            
            throw new TimeoutException();
        }
        
        while (repeat) {
            try {
                // Set authenticator object for use with any authorization
                // errors that occur when opening a stream to the URL.
                // 
                // The WebAuthenticator object stores any previously
                // username/password combination for the
                // given host for later use.
                Authenticator.setDefault(webAuth);
                webAuth.setURL(url);
                webAuth.setUserMap(userMap);
                webAuth.setPasswordMap(passMap);
                webAuth.setNewRequest();
                
                BufferedInputStream bfin =
                    new BufferedInputStream(url.openStream());
                image = new PdsBtrImage(bfin);
                repeat = false;
            }
            catch(Throwable e) {
                // By default, do not repeat attempts to access same URL.
                repeat = false;
                
                if (e instanceof IOException) {
                    // IO exceptions are a normal part of PdsBtrImage
                    // usage and should only be logged for debugging purposes.
                    log.println(e);
                    log.println("ABOVE CAUSED FAILED IMAGE LOAD: " + url);
                }
                else {
                    log.aprintln(e);
                    log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + url);
                }
                
                // Check for user authorization error
                if (checkAuthenticationError) {
                    String msg = e.getMessage();
                    
                    if (isHttp401Error(msg)) {
                        if (webAuth.lastAuthorizationCancelled())
                            throw new CancelException();
                        else {
                            // Any supplied username/password for the current
                            // host is invalid, so clear them from the stored
                            // lookup tables.
                            webAuth.clearLastPassword();
                            
                            JOptionPane.showMessageDialog(
                                                          null,
                                                          "The server refuses to accept your\n" +
                                                          "username/password combination.\n" +
                                                          "Please try again.",
                                                          "WEBSERVER AUTHENTICATION",
                                                          JOptionPane.ERROR_MESSAGE);
                            
                            // Allow additional attempts to access this URL.
                            repeat = true;
                        }
                    }
                }
                
                if (throwExceptions &&
                    e instanceof IOException)
                    throw (IOException)e;
                
                // Limit the number of repeated attempts to access the same
                // URL; this may be caused by improper handling of user authorization 
                // or simply too many user errors.
                count++;
                if (repeat &&
                    count >= WEB_AUTH_ATTEMPTS_LIMIT)
                {
                    repeat = false;
                    
                    JOptionPane.showMessageDialog(
                                                  null,
                                                  "Too many failures trying to access\n" +
                                                  "the webserver.\n",
                                                  "WEBSERVER ACCESS ERROR",
                                                  JOptionPane.ERROR_MESSAGE);
                    
                    // Prevent any further attempts to load this image
                    // without additional user initiation.
                    throw new CancelException();
                }
            }
        }
        
        return image;
    }
    
    private boolean isHttp401Error(String msg)
    {
        if (msg !=null &&
            msg.indexOf("401") >= 0 &&
            msg.toUpperCase().indexOf("RESPONSE") >= 0)
            return true;
        else
            return false;
    }
    
    public StampImage load(String fname)
    {
        log.println("loading " + fname);
        
        if (fname == null)
            return null;
        
        try
        {
            FileInputStream fin = new FileInputStream(fname);
            BufferedInputStream bfin = new BufferedInputStream(fin);
            return  new PdsBtrImage(bfin);
        }
        catch(Throwable e)
        {
            if (e instanceof IOException)
            {
                // IO exceptions are a normal part of PdsBtrImage
                // usage and should only be logged for debugging purposes.
                log.println(e);
                log.println("ABOVE CAUSED FAILED IMAGE LOAD: " + fname);
            }
            else
            {
                log.aprintln(e);
                log.aprintln("ABOVE CAUSED FAILED IMAGE LOAD: " + fname);
            }
            return  null;
        }
    }
    
    
    // Trys to load an image via three different naming schemes
    // in succession until one succeeds or all fail:
    //
    // 1. Simple flat directory path
    // 2. Themis-style subdirectory path (lowercase)
    // 3. Themis-style subdirectory path (uppercase)
    private StampImage loadFile(String dirPath, Stamp s)
    {
        StampImage pdsi = null;
        
        if (dirPath != null &&
            s != null)
        {
            String path = stampToPath(dirPath, s);
            pdsi = load(path);
            
            if (pdsi == null) {
                path = stampToThemisPath(dirPath, s, false);
                pdsi = load(path);
                
                if (pdsi == null) {
                    path = stampToThemisPath(dirPath, s, true);
                    pdsi = load(path);
                }
            }
        }
        
        return pdsi;
    }
    
    public StampImage load(Stamp s)
    {
        PdsBtrImage.SearchParameters parms = new PdsBtrImage.SearchParameters();
        parms.localDirEnabled = true;
        parms.localHttpEnabled = true; 
        parms.themisHttpEnabled = true; 
        parms.firstSrc = LOCAL_DIR; 
        parms.secondSrc = THEMIS_WEBSERVER; 
        parms.autoCopyToLocalDir = false;
        parms.localDirPath = getLocalDirPath();
        parms.localHttpPath = getLocalHttpPath();
        parms.themisHttpPath = getThemisHttpPath();
        
        return load(s, parms);
    }
    
    
    /** Loads image indicated by stamp according to specified 
     ** search parameters.
     **
     ** @param s stamp to be loaded.
     ** @param parms  Search source options:
     ** <UL>
     ** <LI> .firstSrc   primary search source from options of 
     **                  {@link #LOCAL_DIR}, {@link #LOCAL_WEBSERVER},
     **                  {@link #THEMIS_WEBSERVER}, and {@link #NO_SEARCH_SOURCE}.
     ** <LI> .secondSrc  secondary search source from options of 
     **                  {@link #LOCAL_DIR}, {@link #LOCAL_WEBSERVER},
     **                  {@link #THEMIS_WEBSERVER}, and {@link #NO_SEARCH_SOURCE}.
     ** </UL>
     **
     ** Whatever search source has not been specified by above
     ** parameters will be used as a tertiary source if it is enabled.
     **
     ** If NO_SEARCH_SOURCE has been specified for one of these parameters,
     ** that source preference will be skipped and no tertiary source will be used
     ** (only useful for the secondSrc parameter and to avoid various errors).
     ** 
     ** Method returns null if firstSrc and secondSrc are the same value.
     **/
    public StampImage load(Stamp s, PdsBtrImage.SearchParameters parms)
    {
        StampImage pdsi = null;
        int searchControl;
        
        if (s == null ||
            s.id == null ||
            parms.firstSrc == parms.secondSrc ||
            parms.firstSrc < LOCAL_DIR ||
            parms.firstSrc > THEMIS_WEBSERVER ||
            parms.secondSrc < LOCAL_DIR ||
            parms.secondSrc > THEMIS_WEBSERVER)
            return  null;
        
        log.println("firstSrc = " + parms.firstSrc + " secondSrc = " + parms.secondSrc);
        
        // Setup search control; a bit of a hack.
        searchControl = (parms.firstSrc << 4) + (parms.secondSrc << 2);
        searchControl += 3 - (parms.firstSrc | parms.secondSrc);
        
        log.println("searchControl = " + searchControl);
        
        for (int i = 4; i >= 0; i-=2)
        {
            String filename;
            int curSrc = (searchControl >> i) & 3;
            
            try
            {
                log.println("i = " + i + " curSrc = " + curSrc);
                
                switch(curSrc)
                {
                case LOCAL_DIR:
                    if (parms.localDirEnabled)
                    {
                        pdsi = loadFile(parms.localDirPath, s);
                        
                        // If load fails, try using the separate paths that a THEMIS webserver
                        // uses for BTR and ABR images; user may be using local directory
                        // in a read-only fashion that references a THEMIS webserver style structure.
                        if (pdsi == null) {
                            if (s.isIR())
                                pdsi = loadFile( getBtrHttpPath(parms.localDirPath), s );
                            else if (s.isVisible())
                                pdsi = loadFile( getAbrHttpPath(parms.localDirPath), s );
                        }
                    }
                    break;
                    
                case LOCAL_WEBSERVER:
                    if (parms.localHttpEnabled) {
                        pdsi = loadUrl(parms.localHttpPath, s);
                        
                        // If load fails, try using the separate paths that a THEMIS webserver
                        // uses for BTR and ABR images; user may be redirecting search
                        // to an alternate THEMIS webserver via the local webserver path.
                        if (pdsi == null) {
                            if (s.isIR())
                                pdsi = loadUrl( getBtrHttpPath(parms.localHttpPath), s );
                            else if (s.isVisible())
                                pdsi = loadUrl( getAbrHttpPath(parms.localHttpPath), s );
                        }
                    }
                    
                    if (parms.autoCopyToLocalDir &&
                        pdsi != null)
                        ((PdsBtrImage)pdsi).copyToDir(parms.localDirPath);
                    break;
                    
                case THEMIS_WEBSERVER:
                    if (parms.themisHttpEnabled)
                    {
                        if (s.isIR())
                            pdsi = loadUrl( getBtrHttpPath(parms.themisHttpPath), s );
                        else if (s.isVisible())
                            pdsi = loadUrl( getAbrHttpPath(parms.themisHttpPath), s );
                        
                        if (parms.autoCopyToLocalDir &&
                            pdsi != null)
                            ((PdsBtrImage)pdsi).copyToDir(parms.localDirPath);
                    }
                    break;
                }
            }
            catch (Throwable e)
            {
                if (!(e instanceof IOException) &&
                    !(e instanceof CancelException) &&
                    !(e instanceof TimeoutException))
                    log.aprintln("Exception:" + e.getMessage());
            }
            
            if (pdsi != null)
                break;
        }
        
        return pdsi;
    }
    
    protected class WebAuthenticator extends Authenticator
    {
        private HashMap userMap = null;
        private HashMap passMap = null;
        private URL url = null;
        private boolean lastAuthCancelled = false;
        private boolean newRequest = false;
        private String  lastHost = null;
        
        private String USER = null;
        private String PASS = null;
        
        // Supplies username/password from user via dialog; stores any 
        // previously-given username/password combination given host for later use.
        //
        // The 'clearLastUserPass()' and 'clearUserPass(hostname)' methods 
        // may be used invalidate stored usernames and passwords.
        //
        // The 'setUrl()' method should be called before any new attempt
        // to open a URL; this provides the authenticator object with hostname
        // information.
        ///
        // The 'newRequest()' method should be called before any
        // new attempt to open the same URL.  This flags the authenticator
        // object to not check for repetion errors on the first call to
        // the "getPasswordAuthentication" method.  Without this, an error
        // check is made to prevent repeatedly returning the same stored
        // username/password combination on successive calls for a
        // given hostname.
        
        // If the above condition exists, the method returns null.  This
        // should cause the Authenticator framework to stop calling this
        // method for the current URL connection attempt and report an
        // exception to the original calling method.
        protected PasswordAuthentication getPasswordAuthentication()
        {
            PasswordAuthentication auth = null;
            String username = null;
            String password = null;
            String hostname = null;
            
            if (url != null) {
                hostname = url.getHost();
                
                if (lastHost == hostname &&
                    !newRequest)
                    return null;
                
                if (hostname != null) {
                    if (userMap != null)
                        username = (String)userMap.get(hostname);
                    
                    if (passMap != null)
                        password = (String)passMap.get(hostname);
                    
                    if (username == null &&
                        password == null &&
                        newRequest)
                    {
                        // Store user's main keyserver username/password as a default
                        // authentication for any webserver for which there is no
                        // stored username/password.
                        //
                        // If this is not a valid authorization, the next call to
                        // this method will appropriately prompt the user for the
                        // correct info if calling code makes proper use of
                        // methods below for clearing usernames/passwords.
                        if (userMap != null && 
                            passMap != null)
                        {
                            userMap.put(hostname, Main.DB_USER);
                            passMap.put(hostname, Main.DB_PASS);
                            username = Main.DB_USER;
                            password = Main.DB_PASS;
                        }
                    }
                    
                }
            }
            
            newRequest = false;
            lastAuthCancelled = false;
            if (username == null ||
                password == null)
            {
                if (promptUserPass(username, hostname)) {
                    username = USER;
                    password = PASS;
                    
                    if (hostname != null) {
                        if (userMap != null)
                            userMap.put(hostname, username);
                        
                        if (passMap != null)
                            passMap.put(hostname, password);
                    }
                    
                }
                else
                    // User hit "Cancel" button on dialog.
                    lastAuthCancelled = true;
            }
            
            if (!lastAuthCancelled) {
                char [] passChars = new char[password.length()];
                if (passChars != null)
                    password.getChars(0, password.length(), passChars, 0);
                
                auth = new PasswordAuthentication (username, passChars);
            }
            
            lastHost = hostname;
            
            return auth;
        }
        
        public void setNewRequest()
        {
            newRequest = true;
        }
        
        // Returns 'true' if user clicked on Cancel button the
        // last time s/he was prompted for authorization info.
        public boolean lastAuthorizationCancelled()
        {
            return lastAuthCancelled;
        }
        
        // Used to set the current URL for use in storing a 
        // username and password via the hostname.
        public void setURL(URL url)
        {
            this.url = url;
        }
        
        // Sets lookup table for hostname-to-username storage.
        public void setUserMap(HashMap userMap)
        {
            this.userMap = userMap;
        }
        
        // Sets lookup table for hostname-to-password storage.
        public void setPasswordMap(HashMap passMap)
        {
            this.passMap = passMap;
        }
        
        public void clearLastPassword()
        {
            String hostname = null;
            
            if (url != null &&
                (hostname = url.getHost()) != null)
                clearPassword(hostname);
        }
        
        public void clearLastUserPassword()
        {
            String hostname = null;
            
            if (url != null &&
                (hostname = url.getHost()) != null)
                clearUserPassword(hostname);
        }
        
        public void clearPassword(String hostname)
        {
            if (hostname != null) {
                if (passMap != null)
                    passMap.remove(hostname);
            }
        }
        
        public void clearUserPassword(String hostname)
        {
            if (hostname != null) {
                if (userMap != null)
                    userMap.remove(hostname);
                
                if (passMap != null)
                    passMap.remove(hostname);
            }
        }
    
        // Returns "false" if user hit cancel button.
        private boolean promptUserPass(String username, String hostname)
        {
            class MyLabel extends JLabel
            {
                MyLabel(String s)
                {
                    super(s);
                    setAlignmentX(1);
                    setAlignmentY(0.5f);
                }
            }
            
            class MyBox extends Box
            {
                MyBox(JComponent a, JComponent b)
                {
                    super(BoxLayout.Y_AXIS);
                    add(a);
                    add(b);
                }
            }
            
            final JTextField txtUser = new JTextField(username);
            final JPasswordField txtPass = new JPasswordField();
            
            Box fields = new Box(BoxLayout.X_AXIS);
            fields.add(new MyBox(new MyLabel("Username: "),
                                 new MyLabel("Password: ")),
                                 BorderLayout.WEST);
            fields.add(new MyBox(txtUser,
                                 txtPass),
                                 BorderLayout.CENTER);
            
            JOptionPane op =
                new JOptionPane(
                                new Object[] {
                                              "Access to the webserver \"" + hostname + "\"\n" +
                                              "requires a username and password... please enter\n" +
                                              "them now.\n" +
                                              "\n",
                                              fields,
                                              "\n"
                                },
                                JOptionPane.WARNING_MESSAGE,
                                JOptionPane.OK_CANCEL_OPTION
                );
            
            JDialog dialog = op.createDialog(null, "WEBSERVER AUTHENTICATION");
            dialog.addWindowListener(
                                     new WindowAdapter()
                                     {
                                         public void windowActivated(WindowEvent we)
                                         {
                                             if (txtUser.getText().equals(""))
                                                 txtUser.grabFocus();
                                             else
                                                 txtPass.grabFocus();
                                         }
                                     }
            );
            dialog.setResizable(false);
            dialog.setVisible(true);
            if (!new Integer(JOptionPane.OK_OPTION).equals(op.getValue()))
            {
                log.println("User exited.");
                return false;
            }
            USER = txtUser.getText();
            PASS = new String(txtPass.getPassword());
            
            return true;
        }
    }
    
    // Trys to load an image via three different naming schemes
    // in succession until one succeeds or all fail:
    //
    // 1. Simple flat webserver directory path
    // 2. Themis webserver subdirectory path (lowercase)
    // 3. Themis webserver subdirectory path (uppercase)
    private StampImage loadUrl(String httpPath, Stamp s)
    throws CancelException, TimeoutException
    {
        StampImage pdsi = null;
        
        try {
            if (httpPath != null &&
                s != null)
            {
                URL url = stampToWebserverURL(httpPath, s);
                pdsi = load(url);
                
                if (pdsi == null) {
                    url = stampToThemisWebserverURL(httpPath, s, false);
                    pdsi = load(url);
                    
                    if (pdsi == null) {
                        url = stampToThemisWebserverURL(httpPath, s, true);
                        pdsi = load(url);
                    }
                }
            }
        }
        catch (Throwable e) {
            if (e instanceof CancelException)
                throw (CancelException)e;
            else if (e instanceof TimeoutException)
                throw (TimeoutException)e;
        }
        
        return pdsi;
    }
    
    /**
     * Returns URL path for a webserver based on a path and a
     * stamp.
     * 
     **/
    private URL stampToWebserverURL(String httpPath, Stamp s)
    {
        URL serverURL = null;
        
        String path = stampToPath(httpPath, s);
        
        if (path != null)
            try {
                serverURL = new URL(path);
            }
            catch (Throwable e) {
                log.aprintln("Exception:" + e.getMessage());
            }
        
        return serverURL;
    }
    
    private String stampToPath(String basePath, Stamp s)
    {
        String path = null;
        
        if (s != null &&
            basePath != null)
        {
            String basename = stampToBasename(s);
            
            if (!basePath.endsWith("/"))
                basePath = basePath + "/";
            
            path = basePath + basename;
        }
        
        return path;
    }
    
    /**
     * Returns URL path for a THEMIS webserver that automatically
     * includes the correct subdirectory with the specified lettercase.
     * 
     * The basename for the the document is obtained from the passed
     * stamp.
     **/
    private URL stampToThemisWebserverURL(String httpPath, Stamp s, boolean uppercase)
    {
        URL serverURL = null;
        
        String path = stampToThemisPath(httpPath, s, uppercase);
        
        if (path != null)
            try {
                serverURL = new URL(path);
            }
        catch (Throwable e) {
            log.aprintln("Exception:" + e.getMessage());
        }
        
        return serverURL;
    }
    
    private String stampToThemisPath(String basePath, Stamp s, boolean uppercase)
    {
        String path = null;
        
        if (s != null &&
            basePath != null)
        {
            if (!basePath.endsWith("/"))
                basePath = basePath + "/";
            
            // Determine image subdirectory on THEMIS data webserver
            String basename = stampToBasename(s);
            StringBuffer buf = new StringBuffer(basename);
            
            buf.delete(THEMIS_SUBDIRECTORY_PREFIX_LENGTH, buf.length());
            if (s.isIR())
                buf.append(BTR_THEMIS_SUBDIRECTORY_SUFFIX);
            else
                buf.append(ABR_THEMIS_SUBDIRECTORY_SUFFIX);
            buf.append("/");
            
            if (uppercase)
                buf = new StringBuffer(buf.toString().toUpperCase());
            else
                buf = new StringBuffer(buf.toString().toLowerCase());
            
            // Add Themis webserver base path.
            buf.insert(0, basePath);
            buf.append(basename);
            
            path = buf.toString();
        }
        
        return path;
    }
    
    
    private String stampToBasename(Stamp s)
    {
        String basename = null;
        String suffix;
        
        if (s != null &&
            s.id != null)
        {
            if (s.isIR())
                suffix = BTR_SUFFIX;
            else if (s.isVisible())
                suffix = ABR_SUFFIX;
            else
                return null;
            
            basename = s.id + suffix;
        }
        
        return basename;
    }
    
}
