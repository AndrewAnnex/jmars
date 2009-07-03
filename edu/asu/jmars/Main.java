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


package edu.asu.jmars;

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.EventListenerList;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

import net.sf.ehcache.Ehcache;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.PostMethod;

import edu.asu.jmars.layer.LManager2;
import edu.asu.jmars.layer.LoadSaveLayerDialogs;
import edu.asu.jmars.layer.ProjectionEvent;
import edu.asu.jmars.layer.ProjectionListener;
import edu.asu.jmars.layer.map2.CacheManager;
import edu.asu.jmars.layer.stamp.StampImage;
import edu.asu.jmars.layer.util.FileLogger;
import edu.asu.jmars.places.PlacesMenu;
import edu.asu.jmars.places.XmlPlaceStore;
import edu.asu.jmars.ruler.RulerManager;
import edu.asu.jmars.swing.TimeField;
import edu.asu.jmars.swing.UrlLabel;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.JmarsTrustManager;
import edu.asu.jmars.util.Time;
import edu.asu.jmars.util.Util;
import edu.stanford.ejalbert.BrowserLauncher;


public class Main extends JFrame {
	static {
		// Make sure J2SE 5 drag and drop does the right thing.
		// Note that this must be done before the look and feel is set, since
		// the class instantiation loads in SwingUtilities2, which inspects this
		// property value!
		System.setProperty("sun.swing.enableImprovedDragGesture", "true");
		
		// configure the look and feel
		// Metal isn't the most novel look and feel, but it's everywhere now
		String plaf = "javax.swing.plaf.metal.MetalLookAndFeel";
		try {
			UIManager.setLookAndFeel(plaf);
			MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
		} catch (Exception e) {
			System.err.println("UNABLE TO SET LOOK AND FEEL TO: " + plaf);
			e.printStackTrace(System.err);
		}
	}
	
	private static DebugLog log = DebugLog.instance();
	private static FileLogger fileLogger;
	static {
		try {
			initJMarsDir();
			fileLogger = new FileLogger();
		} catch (FileNotFoundException e) {
			log.aprintln("Unable to open file logger");
		}
	}
	
	private static final String TITLE = Config.get("edition");
	private static String TITLE_FILE = "";
	private static String TITLE_LOC = "";
	
	public static UserProperties userProps;
	public static long NOW = Time.getNowEt();
	
	public static final String ABOUT_URL = Config.get("homepage");
	public static final String ABOUT_EMAIL = Config.get("email");
	
	private static final URL VERSION_URL = getResource("resources/about.txt");
	public static boolean IN_JAR = VERSION_URL == null ? false : VERSION_URL.getProtocol().equals("jar");
	private static About CACHED_ABOUT;
	
	// parameters from the ServerAbout response
	public static String KEY;
	public static String DB_USER;
	public static String DB_PASS;
	
	/** Returns the local about object */
	public static About ABOUT() {
		if (CACHED_ABOUT == null) {
			try {
				CACHED_ABOUT = new About(Util.readLines(VERSION_URL.openStream()));
			} catch (Exception e) {
				// create an empty about object (so we don't just keep retrying)
				IN_JAR = false;
				CACHED_ABOUT = new About();
			}
		}
		return CACHED_ABOUT;
	}
	
	/**
	 * Parses the basic About file format, which is generated by the build
	 * process and includes information on the build time and distribution
	 * contents.
	 * 
	 * The build time is used to ensure the application is up to date, and
	 * the other info is shown to the user in the 'about' Help menu item.
	 */
	public static class About {
		protected int line = 0;
		public final String DATE;
		public final long SECS;
		public final int LINES;
		public final int PAGES;
		public final int FILES;
		public final int CLASSES;
		public About() {
			DATE = "";
			SECS = 0;
			LINES = PAGES = FILES = CLASSES = 0;
		}
		public About(String[] lines) throws IOException {
			DATE = lines[line++];
			SECS = Long.parseLong(lines[line++].trim());
			LINES = Integer.parseInt(lines[line++].trim());
			PAGES = LINES / 60;
			FILES = Integer.parseInt(lines[line++].trim());
			CLASSES = Integer.parseInt(lines[line++].trim());
			if (DATE.length() < 10  || DATE.length() > 50  ||
					DATE.indexOf("200") == -1  ||
					DATE.indexOf(":") == DATE.lastIndexOf(":")) {
				throw new IllegalStateException("Date does not look right: " + DATE);
			}
			if (SECS < 30*365*24*60*60) {
				throw new IllegalStateException("Secds does not look right: " + SECS);
			}
		}
	}
	
	/**
	 * As the normal About class, but the server produces a response with the
	 * following additional lines (in this order):
	 * <ul>
	 * <li>accesskey
	 * <li>dbuser
	 * <li>dbpass
	 * </ul>
	 */
	public static final class ServerAbout extends About {
		public final String KEY;
		public final String DBUSER;
		public final String DBPASS;
		public ServerAbout(String[] lines) throws IOException {
			super(lines);
			KEY = lines[line++];
			DBUSER = lines[line++];
			DBPASS = lines[line++];
		}
	}
	
	/**
	 ** Sets the filename and/or location string used for the titlebar
	 ** of the application.
	 **
	 ** @param fname if null, no change is made
	 ** @param loc if null, no change is made
	 **/
	public static void setTitle(String fname, String loc)
	 {
		if(fname != null)
			TITLE_FILE = fname;
		if(loc != null)
			TITLE_LOC = loc;

		mainFrame.setTitle(createTitle());
	 }

	/**
	 ** Returns what the TITLE, TITLE_LOC, and TITLE_FILE variables
	 ** indicate the current title should be.
	 **/
	private static String createTitle()
	 {
		String t = TITLE;
		if(!TITLE_FILE.equals(""))
			t += " (" + TITLE_FILE + ")";
		t += " " + TITLE_LOC;
		return  t;
	 }

	/**
	 ** Replaces the whole getClass().getResource() chain of calls,
	 ** which is difficult to do statically without obfuscating our
	 ** obfuscation settings.
	 **/
	public static URL getResource(String name)
	 {
		return  Main.class.getResource("/" + name);
	 }

	/**
	 ** Replaces the whole getClass().getResource() chain of calls,
	 ** which is difficult to do statically without obfuscating our
	 ** obfuscation settings.
	 **/
	public static InputStream getResourceAsStream(String name)
	 {
		return  Main.class.getResourceAsStream("/" + name);
	 }

	/**
	 ** Utility method for retrieving the contents of a resource file
	 ** as a complete String. Returns null if the resource can't be
	 ** found.
	 **
	 ** @throws RuntimeException If some type of IO error occurs,
	 ** which should be rare. The underlying exception that caused the
	 ** problem will be chained correctly.
	 **/
	public static String getResourceAsString(String name)
	 {
		URL url = Main.class.getResource("/" + name);
		InputStream is = Main.class.getResourceAsStream("/" + name);
		if(is == null)
			return  null;

		try
		 {
			StringBuffer sb = new StringBuffer(1024);
			BufferedReader br = new BufferedReader(new InputStreamReader(is));

			char[] chars = new char[1024];
			while(br.read(chars) > -1)
				sb.append(String.valueOf(chars));

			return sb.toString();
		 }
		catch(IOException e)
		 {
			throw  new RuntimeException(
				"I/O error while reading resource file " + url, e);
		 }
		finally
		 {
			try { is.close(); } catch(Throwable e) { }
		 }
	 }

	/**
	 ** Utility method for printing the contents of a resource file
	 ** to an OutputStream.
	 **/
	public static void printResource(String name, OutputStream os)
	 throws IOException
	 {
		URL url = Main.class.getResource("/" + name);
		InputStream is = Main.class.getResourceAsStream("/" + name);
		if(is == null)
			throw new FileNotFoundException("Unable to open resource " + name);

		try
		 {
			BufferedInputStream bis = new BufferedInputStream(is);
			byte[] buff = new byte[1024];
			int numRead;
			while( (numRead = bis.read(buff)) > -1 )
				os.write(buff, 0, numRead);
		 }
		catch(Throwable e)
		 {
			throw  (IOException)
				new IOException("Error while reading " + url).initCause(e);
		 }
		finally
		 {
			try { is.close(); } catch(Throwable e) { }
		 }
	 }

	/**
	 ** If there's a newer version of JMARS available (according to
	 ** the server), a dialog box pops up warning the user. If the
	 ** server can't be contacted or its response is suspicious, a
	 ** different dialog pops up and informs the user.
	 **
	 ** <p>Otherwise, nothing visible occurs.
	 **/
	public static void checkAppVersion() {
		if (IN_JAR && !Config.get("skipabout", false) && USER != null && USER.length() > 0) {
			log.printStack(0);
			try {
				ServerAbout update = getServerAbout();
				if(update.SECS > ABOUT().SECS) {
					String msg =
						"Your version of JMARS is out of date!\n" +
						"\n" +
						"Yours was built:\n" +
						"    " + ABOUT().DATE + "\n" +
						"The server says the most recent build is:\n" +
						"    " + update.DATE + "\n" +
						"\n" +
						"It is strongly recommended that you upgrade.\n" +
						"\n";
					JOptionPane.showMessageDialog(null, msg, "JMARS", JOptionPane.WARNING_MESSAGE);
				}
			} catch(Exception e) {
				String msg =
					"JMARS is unable to determine whether or not it's\n" +
					"out of date!\n" +
					"\n" +
					"This may be because the server is down, or you've\n" +
					"lost network connectivity, or even because your\n" +
					"JMARS is so old that it doesn't know how to check\n" +
					"correctly (or there is a problem with your account.)\n" +
					"\n" +
					"Your JMARS was built:\n" +
					"    " + ABOUT().DATE + "\n" +
					"\n" +
					"If you receive this message repeatedly, it might be\n" +
					"time for you to upgrade.\n" +
					"\n";
				JOptionPane.showMessageDialog(null, msg, "JMARS", JOptionPane.ERROR_MESSAGE);
			}
		}
	}


	public static ProjObj PO;
	public static Main mainFrame;

	/**
	 ** Returns an indication of whether or not the ruler package is
	 ** around. Should be used to firewall any ruler code, so that the
	 ** public version (which doesn't include rulers) won't throw any
	 ** errors. Based on config file info.
	 **/
	public static boolean haveRulers()
	 {
		return  "yes".equals(Config.get("rulers.enabled"));
	 }

	public static boolean isInternal()
	 {
		return  Config.get("int") != null;
	 }


	/**
	 ** If non-null, indicates an initial location (in world
	 ** coordinates) for cylindrical mode. Used only for the public
	 ** "rounded" cylindrical mode, where the initial location is NOT
	 ** the center of the projeciton.
	 **/
	public static Point2D initialWorldLocation = null;

	public static TestDriverLayered testDriver;

	/**
	 ** The username used for server authentication.
	 **/
	public static String USER = Config.get("username");

	/**
	 ** The password used for server authentication.
	 **/
	public static String PASS = "";
	
	/**
	 * Retrieves the {@link ServerAbout} object from the server. Tries the
	 * stored username/password (in .jmarsrc) if it's present. Re-prompts the
	 * user as necessary, when/if the login attempt fails.
	 */
	private static void authenticateUser() {
		log.printStack(0);

		// Note: these values are SAVED down in the Main.saveState() routine.
		if (USER == null) {
			USER = userProps.getProperty("jmars.user");
		}

		try {
			if ("".equals(USER) && "".equals(PASS))
				throw new LocalAuthentication();

			if (USER == null || PASS == null || PASS == "")
				promptUserPass();

			do {
				try {
					ServerAbout auth = getServerAbout();
					KEY = auth.KEY;
					DB_USER = auth.DBUSER;
					DB_PASS = auth.DBPASS;
				} catch (Exception e) {
					JOptionPane.showMessageDialog(
						null,
						"There was a problem validating your username\n"
						+ "and password:\n"
						+ "\n"
						+ e.getMessage()
						+ "\n"
						+ "\n"
						+ "More information may be available in your\n"
						+ "command-line window.\n",
						"JMARS AUTHENTICATION",
						JOptionPane.ERROR_MESSAGE);
				}
				if (KEY == null)
					promptUserPass();
			} while (KEY == null);
		} catch (LocalAuthentication e) {
			log.aprintln("SKIPPING AUTHENTICATION KEY, hope you're a local user!");
		}
	}

	/**
	 * Used to signal our username="x"+password="" hack to skip the
	 * authentication key.
	 **/
	private static class LocalAuthentication extends Exception
	 {
	 }

	/**
	 ** Prompts the user for their username/password, storing the
	 ** acquired values into {@link #USER} and {@link #PASS}.
	 **/
	private static void promptUserPass() throws LocalAuthentication
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

		final JTextField txtUser = new JTextField(USER);
		final JPasswordField txtPass = new JPasswordField();

		txtUser.addFocusListener(new FocusListener() {
			public void focusLost(FocusEvent e) {
				// Do not let the Dialog OK button steal the default focus from us
	            if (e.getOppositeComponent() instanceof JButton) {
	            	txtUser.grabFocus();
	            	txtUser.removeFocusListener(this);
	            }
			}
		
			public void focusGained(FocusEvent e) {
				// nop
			}		
		});

		txtPass.addFocusListener(new FocusListener() {
			public void focusLost(FocusEvent e) {
				// Do not let the Dialog OK button steal the default focus from us
	            if (e.getOppositeComponent() instanceof JButton) {
	            	txtPass.grabFocus();
	            	txtPass.removeFocusListener(this);
	            }
			}
		
			public void focusGained(FocusEvent e) {
				// nop
			}		
		});
		
		Box fields = new Box(BoxLayout.X_AXIS);
		fields.add(new MyBox(new MyLabel(Config.get("login.lbl") + " "),
							 new MyLabel("Password: ")),
				   BorderLayout.WEST);
		fields.add(new MyBox(txtUser,
							 txtPass),
				   BorderLayout.CENTER);

		JOptionPane op =
			new JOptionPane(
				new Object[] {
					"To use the JMARS servers, you must register on the\n" +
					"web. Please enter your login information below.\n\n",
					fields,
					"\nTo register, please visit:\n",
					new UrlLabel(Config.get("homepage")),
					"\n"
					 },
				JOptionPane.WARNING_MESSAGE,
				JOptionPane.OK_CANCEL_OPTION
				);

		JDialog dialog = op.createDialog(
			null, Config.get("edition") + " AUTHENTICATION");
        dialog.addWindowListener(
			new WindowAdapter()
			 {
				public void windowActivated(WindowEvent we)
				 {
					if(txtUser.getText().equals(""))
						txtUser.grabFocus();
					else
						txtPass.grabFocus();
                }
			 }
			);
        
		dialog.setResizable(false);
		dialog.setVisible(true);
		if(!new Integer(JOptionPane.OK_OPTION).equals(op.getValue()))
		 {
			log.println("User exited.");
			System.exit(-1);
		 }
		USER = txtUser.getText();
		PASS = new String(txtPass.getPassword());
		
		if ((USER.equals("") || USER.equalsIgnoreCase("x"))  &&  PASS.equals(""))
		 {
			USER = "";
			throw  new LocalAuthentication();
		 }
	 }
	
	/**
	 * Using the current USER and PASS, retrieves and returns the
	 * server key.
	 * @return The about object if the USER/PASS combination is valid, null if not.
	 * @throws IOException If there is an error connecting or parsing the document.
	 */
	private static ServerAbout getServerAbout() throws IOException {
		if (isInternal() == USER.contains("@")) {
			throw new IllegalStateException("ERROR: Wrong account type");
		}
		
		PostMethod post = null;
		try {
			String url = Config.get("auth");
			log.println("Retrieving key from " + url);
			post = new PostMethod(url);
			post.setRequestBody(new NameValuePair[] {
				new NameValuePair("user",USER),
				new NameValuePair("pass",PASS)
			});
			post.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
			int code = Util.postWithRedirect(new HttpClient(), post, 3);
			if (code != HttpStatus.SC_OK) {
				throw new IllegalStateException("Server gave unexpected response code " + code);
			}
			String[] lines = Util.readLines(post.getResponseBodyAsStream());
			if (lines.length > 0 && lines[0].startsWith("ERROR!")) {
				throw new IllegalStateException(Util.join("\n", lines));
			}
			try {
				return new ServerAbout(lines);
			} catch (Exception e) {
				throw new IllegalStateException("Unable to parse server response", e);
			}
		} finally {
			if (post != null) {
				post.releaseConnection();
			}
		}
	}
	
	// Daemon timer thread for server authentication and versioning.
	static private Timer checkServer = new Timer(true);

     public Main()
     {
         this(true);
     }
     
     /**
      * @param createUI Controls whether or not a UI is created and
      * displayed; <code>null</code> means do not create UI, but
      * do initialize various state variables.
      */
	 private Main(boolean createUI)
	 {
		 super(createTitle());
		 setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		 // Test for and setup as necessary the JMARS and TILES directories
		 initJMarsDir();

		if (createUI)
		{
			// build the main view and load in any properties from the config file, if
			// there is one.
			testDriver = new TestDriverLayered();

			// Set up the RulerManager ( only one allowed ) before
			// building the layers, so that the layers can create their
			// own rulers as necessary. The original layered pane is now a
			// child of the RulerManager instead of the Main window panel.
			RulerManager.Instance.setContent( (JComponent)testDriver, this);
			setContentPane( RulerManager.Instance);
			RulerManager.Instance.hideAllDragbars( true);

			// If there are any views to be built, build them now.
			// Previously, this happened when the testDriver was created.
			// But now the RulerManager content must be set before any
			// views are created, otherwise the rulers will never be created.
			// The code to do this is therefore sandwiched in betweeen 
			// creating the testDriver and building the views.
			testDriver.buildViews();

			// Get the properties of the rulers, if such there be.
			// This must be done AFTER views are created because rulers
			// are created when views are and only the properties of 
			// created rulers are loaded.  
			testDriver.loadRulersState();

			// Once everything is loaded, we
			// need to update the tabbed pane so that any loaded properties
			// show up in the panel.
			// RulerManager.Instance.updatePropertiesTabbedPane();

			testDriver.lmanager = new LManager2(testDriver.mainWindow);

			// reset the parameters of the Layer Manager.
			testDriver.loadLManagerState();

			// Set up a location listener so that the rulers will be updated even
			// if the layer is turned off.
			RulerManager.Instance.setLViewManager( testDriver.mainWindow);

			// The menu bar adds 23 pixels to the height of the panel.
			initMenuBar();

			// Set the tool-tip delay if that property exists in the config file.
			ToolTipManager ttm = ToolTipManager.sharedInstance();
			int tooltipInitialDelay = Config.get("tooltip.initial", ttm.getInitialDelay());
			ttm.setInitialDelay(Math.abs(tooltipInitialDelay));
			int tooltipDismissDelay = Config.get("tooltip.dismiss", ttm.getDismissDelay());
			ttm.setDismissDelay(Math.abs(tooltipDismissDelay));

			// Resize everything and display.
			RulerManager.Instance.packFrame();
			Util.centerFrame(this);

			setVisible(true);
		}
	}

    static public void initJMarsDir()
    {
        File myDir = new File(getJMarsPath());
        if (!myDir.isDirectory()) { //It doesn't exist...hopefully!
            log.print("Attempting to create JMARS directory....");
            if(!(myDir.mkdirs())){
                log.aprintln();
                log.aprintln("\nMAJOR SYSTEM ERROR:  Unable to create JMARS directory...aborting");
                System.exit(0);
            }
            else
                log.println("Success!");
        }
    }

	public static void setStatus(String status)
	 {
	    if ( status == null )
	        return;

		JLabel text = Main.testDriver.statusBar;
		text.setText(status);
	 }
	
	/** Return the user home directory */
	public static String getUserHome() {
		String val = System.getProperty("user.home");

		if (val == null) {
			val = Config.get("windows_base_dir");
		}

		return val;
	}
	
    /**
     * Returns path to base JMARS directory, under which other files and directories
     * (such as the map tiles cache or stamp list data cache files) may be stored. 
     */
    public static String getJMarsPath() {
        return getUserHome() + File.separator + "jmars" + File.separator;
    }
    
	private static void initiateAuthentication()
	 {
		authenticateUser();

		final int delayMS = 1000 * 60 * 30; // 30 minutes
		final int delayForVersionMS = 1000 * 60 * 60 * 24; // one day

		TimerTask checkServerTask =
			new TimerTask()
			 {
				long lastTime = System.currentTimeMillis();
				long lastTimeVer = System.currentTimeMillis();
				public void run()
				 {
					long currTime = System.currentTimeMillis();
					// Prevent the normal timer behavior of
					// queuing a single extra overlapped timer
					// task.
					if(currTime - lastTime < delayMS/2)
						return;

					try
					 {
						authenticateUser();
					 }
					catch(Throwable e)
					 {
						log.aprintln("FAILED PERIODIC USER KEY CHECK:");
						log.aprintln(e);
					 }
					try
					 {
						if(currTime - lastTimeVer > delayForVersionMS)
						 {
							lastTimeVer = currTime;
							checkAppVersion();
						 }
					 }
					catch(Throwable e)
					 {
						log.aprintln("FAILED PERIODIC APP VERSION CHECK:");
						log.aprintln(e);
					 }

					lastTime = System.currentTimeMillis();
				 }
			 }
			;

		checkServer.schedule(checkServerTask, delayMS, delayMS);
	 }

	private static void processFlagMultiHead(String arg, ListIterator iter)
	 {
		if(arg.equals(":"))
		 {
			Util.printAltDisplays();
			System.exit(0);
		 }

		Util.setAltDisplay(arg);
		iter.remove();
	 }
	
	public static void cleanCache()
	 {
		log.aprintln("------ CLEANING! -------");

		// Delete the whole cache directory for edu.asu.jmars.layer.map2
		Util.recursiveRemoveDir(new File(CacheManager.getCacheDir()));
		
		FilenameFilter capFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().startsWith("wms_") && name.toLowerCase().endsWith(".xml");
			}
		};
		
		for (File capXml: new File(Main.getJMarsPath()).listFiles(capFilter)) {
			capXml.delete();
		}
		
		// Delete the stamp cache directory for edu.as.u.jmars.layer.stamp
		Util.recursiveRemoveDir(new File(StampImage.STAMP_CACHE));
	 }

	private static void processFlagSlideshow(String arg, ListIterator iter)
	 {
		log.aprintln("----- SLIDESHOW MODE! -----");

		try
		 {
			new Thread(new Slideshow(System.in)).start();
		 }
		catch(Throwable e)
		 {
			log.aprintln(e);
			log.aprintln("UNABLE TO START SLIDE SHOW DUE TO ABOVE!");
			System.exit(-1);
		 }

		iter.remove();
	 }

	public static void startupInCyl(String lon, String lat)
	 {
		log.aprintln("You're using the CYLINDRICAL projection.");

		TITLE_LOC = lon + "E " + lat + "N";
		// USER east lon => JMARS west lon
		double latnum = Double.parseDouble(lat);
		double lonnum = (360 - Double.parseDouble (lon) % 360.0) % 360.0;
		if (Math.abs (latnum) <= 90.0)
			PO = new ProjObj.Projection_OC(lonnum, latnum);
	 	else {
			log.aprintln ("Unable to process latitudes greater than 90 degrees!");
			System.exit (-1);
		}
	 }

	/**
	 ** Rounds to the nearest 90-degrees longitude, 45-degrees
	 ** latitude. Resolves identical projections into a projection
	 ** centered at one of the following lon/lat centers:
	 ** <p>
	 **   0 90<br>
	 **   0  0<br>
	 **   0 45<br>
	 **  90 45<br>
	 ** 180 45<br>
	 ** 270 45<br>
	 **/
	private static void startupInCylRounded(String dispLon, String dispLat)
	 {
		// View center
		double lon = Double.parseDouble(dispLon);
		double lat = Double.parseDouble(dispLat);

		if(lon < 0  ||  lon > 360)
		 {
			System.out.println("Bad longitude: must be between 0 and 360!");
			System.out.println("Start with -help for usage.");
			System.exit(-1);
		 }
		if(lat < -90  ||  lat > 90)
		 {
			System.out.println("Bad latitude: must be between -90 and +90!");
			System.out.println("Start with -help for usage.");
			System.exit(-1);
		 }

		// USER east lon => JMARS west lon
		lon = (360 - lon) % 360;

		// Determine projection center (?: and abs() force northern hemisphere)
		double plon = lat >= 0 ? lon : (lon+180)%360;
		double plat = Math.abs(lat);
		// Polar
		if(plat > (90+45)/2.)
		 {
			plat = 90;
			plon = 0;
		 }
		// Equatorial
		else if(plat < (0+45)/2.)
		 {
			plat = 0;
			plon = 0;
		 }
		// One of the 45-degree ones
		else
		 {
			plat = 45;
			plon = Math.round(plon / 90) * 90 % 360;
		 }

		// JMARS west lon => USER east lon
		TITLE_LOC = (int) (360-plon)%360 + "E " + (int) plat + "N";
		PO = new ProjObj.Projection_OC(plon, plat);

		initialWorldLocation = PO.convSpatialToWorld(lon, lat);

		log.aprintln("You're using the CYLINDRICAL projection.");
		log.println("Your display is centered at " + lon + "W " + lat);
		log.println("Your projection is centered at " + plon + "W " + plat);
		log.println("Your initial world pt is near " +
					(int) initialWorldLocation.getX() + "x " +
					(int) initialWorldLocation.getY() + "y");
	 }

	private static void startupFromFile(String fname)
	 {
		// Attempt to open local properties file to restore state
		File f = new File(fname);
		try
		 {
			log.aprintln("LOADING FROM SAVED FILE " + fname);

			userProps = new UserProperties(f);
			getRcFileChooser().setSelectedFile(f);
			TITLE_FILE = fname;

			// Proxy to the appropriate startup routines, according
			// whatever's in the save file.
			if(userProps.getPropertyBool("TimeProjection")) {
				JOptionPane.showMessageDialog(Main.mainFrame,
					"Time Mode Session Files cannot be used since Time Mode has been removed from JMARS!",
					"Session File Unusable",
					JOptionPane.ERROR_MESSAGE);
				throw new IllegalArgumentException("Invalid session file");
			} else
				startupInCyl(userProps.getProperty("Projection_lon"),
							 userProps.getProperty("Projection_lat"));
		 }
		catch(IOException e)
		 {
			log.aprintln(e);
			log.aprintln("UNABLE TO LOAD JMARS FILE!");
			log.aprintln(f);
			System.exit(-1);
		 }
	 }
	
	private static void processArgsInternal(String[] av) {
		switch (av.length) {
		case 0:
			startupInCyl("0", "0");
			break;
		case 1:
			startupFromFile(av[0]);
			break;
		case 2:
			startupInCyl(av[0], av[1]);
			break;
		default:
			showUsage();
		}
	}

	private static void processArgsPublic(String[] av) {
		switch (av.length) {
		case 0:
			startupInCyl("0", "0");
			break;
		case 1:
			startupFromFile(av[0]);
			break;
		case 2:
			startupInCylRounded(av[0], av[1]);
			break;
		default:
			showUsage();
		}
	}

	private static void processArgs(String[] av)
	 {
		try
		 {
			if(isInternal())
				processArgsInternal(av);
			else
				processArgsPublic  (av);
		 }
		catch(Exception e)
		 {
			log.aprintln("ERROR PROCESSING STARTUP ARGUMENTS:");
			log.aprintln(e);
			showUsage();
		 }
	 }

	private static void showVersion(boolean verbose)
	 {
		if(verbose)
		 {
			Map props = new TreeMap(System.getProperties());
			for(Iterator i=props.keySet().iterator(); i.hasNext(); )
			 {
				String key = (String) i.next();
				String pad = "                              ";
				pad = pad.substring(Math.min(key.length(), pad.length()));
				System.out.println(key + pad + " " +
								   log.DARK + props.get(key) + log.RESET);
			 }
			System.out.println("YOUR CACHE PATH IS: " + CacheManager.getCacheDir());
		 }
		else
			System.out.println(
				"\n * Invoke with -VERSION in caps for even more\n");

		System.out.println("================================================");
		System.out.println("Your JMARS was built:");
		System.out.println("\t" + ABOUT().DATE);
		System.out.println("");
		System.out.println("Your Java version is:");
		System.out.println("\t" + System.getProperty("java.runtime.name"));
		System.out.println("\t" + System.getProperty("java.vm.vendor") +
						   " / "+ System.getProperty("java.runtime.version"));
		System.out.println("");
		System.out.println("Your operating system version is:");
		System.out.println("\t" + System.getProperty("os.name") +
						   " / "+ System.getProperty("os.arch"));
		System.out.println("\t" + System.getProperty("os.version"));
		System.out.println("================================================");
		System.exit(-1);
	 }

	private static void showUsage()
	 {
		try
		 {
			BufferedReader fin = new BufferedReader(new InputStreamReader(
				getResourceAsStream("resources/usage")));
			String line;
			while((line = fin.readLine()) != null)
				System.out.println(line);
		 }
		catch(Exception e)
		 {
			log.aprintln("ERROR! UNABLE TO PRINT USAGE DUE TO:");
			log.aprintln(e);
		 }
		System.exit(-1);
	 }

	private static void processAllArgs(String[] av)
	 throws Throwable
	 {
		// Construct a modifiable list of the command-line arguments
		List args = new LinkedList(Arrays.asList(av));
		ListIterator iter = args.listIterator();

		// Consume the flags from the command-line argument list
		while(iter.hasNext())
		 {
			String arg = (String) iter.next();

			// processFlagXyz calls modify the list and/or call System.exit()
			if     (arg.equals("-help")   ) showUsage();
			else if(arg.equals("-version")) showVersion(false);
			else if(arg.equals("-VERSION")) showVersion(true);
			else if(arg.startsWith(":")   ) processFlagMultiHead (arg, iter);
			else if(arg.equals("clean")   ) {
				cleanCache();
				iter.remove();
			} else if(arg.equals("slideshow"))processFlagSlideshow (arg, iter);
			else if(arg.equals("-open")) iter.remove();
		 }

		// Process the "real" startup parameters
		processArgs((String[]) args.toArray(new String[0]));
	 }

	public static void main(String av[])
	 throws Throwable
	 {
		try
		 {
//			Map<String,String> env = System.getenv();
//			System.out.println("===> ENVIRONMENT <===");
//			for (String key: env.keySet()) {
//				System.out.println(key + "=" + env.get(key));
//			}
//			System.out.println("===> PROPERTIES  <===");
//			System.getProperties().list(System.out);
			
			// install JMARS-specific certificate trust policy
			JmarsTrustManager.install();

			// Churn through the command-line arguments
			processAllArgs(av);

			// Load database drivers
			Util.loadSqlDrivers();
			
			if(userProps == null) // Might've been populated in the processArgs
				userProps = new UserProperties();

			// Pop-up authentication dialog, set a timer to do so periodically
			initiateAuthentication();

			// Load any remote or chained config files
			Config.loadRemoteProps();

			// Upgrade nag screen
			if(ABOUT().DATE != null)
				checkAppVersion();

			// FINALLY: Create the application frame!
			SwingUtilities.invokeLater(new Runnable(){
				public void run(){
					mainFrame = new Main();
				}
			});
		 }
		catch(Exception e)
		 {
			log.aprintln(e);
			System.exit(-1);
		 }
	 }


	//Override to manage initial size and location
	public void setVisible( boolean b )
	 {
		if ( b )
			userProps.setWindowPosition(this);

		super.setVisible(b);
	 }

	/** These menu items are referenced elsewhere in the class */
	JCheckBoxMenuItem miLMgr;
	JMenu menuAction;
	JMenu menuFile;

	private static JRadioButtonMenuItem MRO_TIME_MENU_ITEM;	
	public static void ACTIVATE_MRO()
	 {
		if(MRO_TIME_MENU_ITEM != null)
		 {
			MRO_TIME_MENU_ITEM.setEnabled(true);
			MRO_TIME_MENU_ITEM.doClick(0);
		 }
	 }
    public static boolean IS_MRO_ACTIVATED()
	 {
		return  MRO_TIME_MENU_ITEM != null  &&  MRO_TIME_MENU_ITEM.isEnabled();
	 }

    protected void initMenuBar() {
		JMenuBar mainMenuBar = new JMenuBar();
		menuFile = new JMenu("File");
		menuFile.setMnemonic(KeyEvent.VK_F);
		mainMenuBar.add(menuFile);

		initFileMenu();

		JMenu menuView = new JMenu("View");
		menuView.setMnemonic(KeyEvent.VK_V);
		mainMenuBar.add(menuView);

		AbstractAction viewLMgrAction = new AbstractAction("Layer Manager") {
			public void actionPerformed(ActionEvent e) {
				if (testDriver.getLManager().isVisible()) {
					testDriver.getLManager().setVisible(false);
					miLMgr.setSelected(false);

				} else {
					testDriver.getLManager().setVisible(true);
					miLMgr.setSelected(true);
				}
			}
		};

		// define this one as class wide
		miLMgr = new JCheckBoxMenuItem(viewLMgrAction);
		miLMgr.setMnemonic(KeyEvent.VK_L);
		miLMgr.setSelected(true);
		menuView.add(miLMgr);
		
		final JCheckBoxMenuItem viewMeters = new JCheckBoxMenuItem("Memory Meter");
		viewMeters.setMnemonic('M');
		viewMeters.setSelected(Config.get("main.meters.enable", false));
		viewMeters.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Main.testDriver.setMetersVisible(viewMeters.isSelected());
			}
		});
		menuView.add(viewMeters);
		
		// Dynamically-updated list of layers and their shortcut keys
		menuView.add(getLManager().getLayersMenu());

		// Tooltips
		menuView.add(getLManager().getTooltipsMenu());

		// Keyboard navigation shortcuts
		menuView.add(testDriver.mainWindow.getNavMenu());
		
		JMenu menuTimes = new JMenu("Default time format");

		// Single time format menu selection
		final ButtonGroup timeFormatsG = new ButtonGroup();
		class TimeFormatItem extends JRadioButtonMenuItem {
			TimeFormatItem(boolean selected, final char format, String label) {
				super(new AbstractAction(label) {
					public void actionPerformed(ActionEvent e) {
						TimeField.setDefaultFormat(format);
					}
				});
				setSelected(selected);
				timeFormatsG.add(this);
			}
		}

		String format = Config.get("default.time.format", "E");
		if (format.length() != 1 || "EOSU".indexOf(format) < 0)
			format = "E";
		menuTimes.add(new TimeFormatItem(format.equals("E"), 'E', "ET"));
		menuTimes.add(new TimeFormatItem(format.equals("O"), 'O', "Orbit+offset"));
		menuTimes.add(new TimeFormatItem(format.equals("S"), 'S', "Sclk"));
		menuTimes.add(new TimeFormatItem(format.equals("U"), 'U', "UTC"));
		TimeField.setDefaultFormat(format.charAt(0));

		menuTimes.add(new JSeparator());

		// Single time craft menu selection
		final ButtonGroup timeCraftsG = new ButtonGroup();
		  class TimeCraftItem extends JRadioButtonMenuItem
		   {
			  TimeCraftItem(boolean selected, final String craft,
							String desc, boolean isEnabled)
			   {
				  super(
					  new AbstractAction(craft + " - " + desc)
					   {
						  public void actionPerformed(ActionEvent e)
						   {
						TimeField.setDefaultCraft(craft);
					}
					   }
					  );
				  if(!isEnabled) setEnabled(false);
				setSelected(selected);
				timeCraftsG.add(this);
			}
		}
		  menuTimes.add(
			  new TimeCraftItem(true,  "ODY", "Mars Odyssey", true));
		  menuTimes.add(
			  new TimeCraftItem(false, "MEX", "Mars Express", true));
		  menuTimes.add(
			  new TimeCraftItem(false, "MGS", "Mars Global Surveyor", true));
		  menuTimes.add(
			  MRO_TIME_MENU_ITEM =
			  new TimeCraftItem(false, "MRO", "Mars Reconnaissance Orbiter",
								false));

		menuView.add(menuTimes);

		menuView.add(new AbstractAction("Re-center projection") {
			{
				setEnabled("true".equals(Config.get("reproj")));
			}

			public void actionPerformed(ActionEvent e) {
				testDriver.locMgr.reprojectFromText();
				RulerManager.Instance.notifyRulerOfViewChange();
			}
		});

		if (Config.get("reproj.up", false))
			menuView.add(new AbstractAction("Set projection's up vector") {
				{
					setEnabled("true".equals(Config.get("reproj")));
				}

				public void actionPerformed(ActionEvent e) {
					testDriver.locMgr.reprojectUpVectorFromText();
					testDriver.locMgr.setLocation(new Point(), true);
					RulerManager.Instance.notifyRulerOfViewChange();
				}
			});

		JMenuItem placesMenu = new PlacesMenu(new XmlPlaceStore()).getMenu();
		placesMenu.setMnemonic('P');
		mainMenuBar.add(placesMenu);
		
		JMenu menuHelp = new JMenu("Help");
		menuHelp.setMnemonic(KeyEvent.VK_H);
		mainMenuBar.add(menuHelp);

		// Help file menu item stuff (JW - 5/20/03)
		JMenuItem miHelp = new JMenuItem(new AbstractAction("Help Website") {
			public void actionPerformed(ActionEvent e) {
				try {
					new BrowserLauncher().openURLinBrowser(Config.get("wikipage"));
				} catch (Exception e1) {
					log.aprintln(e1);
				}
			}
		});
		miHelp.setMnemonic(KeyEvent.VK_H);
		menuHelp.add(miHelp);
		
		menuHelp.addSeparator();
		
		// Cache manager menu item
		JMenuItem cacheHelp = new JMenuItem(new AbstractAction("Manage Cache") {
			public void actionPerformed(ActionEvent e) {
				new CacheDialog(Main.this).getDialog().setVisible(true);
			}
		});
		cacheHelp.setMnemonic(KeyEvent.VK_C);
		menuHelp.add(cacheHelp);
		
		// View logs menu item
		JMenuItem logHelp = new JMenuItem(new AbstractAction("View Log") {
			public void actionPerformed(ActionEvent e) {
				new LogViewer(fileLogger).getDialog().setVisible(true);
			}
		});
		logHelp.setMnemonic(KeyEvent.VK_G);
		menuHelp.add(logHelp);
		
		// disable the logger if the log object couldn't be created
		logHelp.setEnabled(fileLogger != null);
		
		// Report problem menu item
		JMenuItem reportHelp = new JMenuItem(new AbstractAction("Report a Problem") {
			public void actionPerformed(ActionEvent e) {
				new ReportCreator(Main.mainFrame, fileLogger).getDialog().setVisible(true);
			}
		});
		reportHelp.setMnemonic(KeyEvent.VK_R);
		menuHelp.add(reportHelp);
		
		AbstractAction aboutAction = new AbstractAction("About") {
			public void actionPerformed(ActionEvent e) {
				String msg =
					"<html><pre>" +
					"<b>J</b>ava<br>" +
					"<b>M</b>ission-planning and<br>" +
					"<b>A</b>nalysis for<br>" +
					"<b>R</b>emote<br>" +
					"<b>S</b>ensing<br>" +
					"\n" +
					"JMARS is a product of The ASU Mars\n" +
					"Scientific Software Team.\n" +
					"\n" +
					"Your copy was personally assembled by\n" +
					Assemblers.getOne() + " on:\n" +
					"    " + ABOUT().DATE + "\n" +
					"\n" +
					"The source code is currently " + ABOUT().LINES + " lines\n" +
					"(or " + ABOUT().PAGES + " printed pages), contained in "
					+ ABOUT().FILES + "\n" +
					"source files that define " + ABOUT().CLASSES
					+ " java classes.\n" +
					"\n" +
					ABOUT_EMAIL;

				try {
					ImageIcon img = new ImageIcon(Main.class.getClassLoader().getResource("images/alienwave.gif"));
					JOptionPane.showMessageDialog(
							Main.this,
							new Object[] { msg, new UrlLabel(ABOUT_URL), "\n" },
							"About " + Config.get("edition"),
							JOptionPane.PLAIN_MESSAGE,
							img);

				} catch (Exception ex) {
					JOptionPane.showMessageDialog(Main.this, msg, "About JMARS", JOptionPane.PLAIN_MESSAGE);
				}
			}
		};

		menuHelp.addSeparator();
		
		JMenuItem miAbout = new JMenuItem(aboutAction);
		miAbout.setMnemonic(KeyEvent.VK_A);
		menuHelp.add(miAbout);

		this.setJMenuBar(mainMenuBar);
	}

        public static void sayGoodbyeToLManager() {
		mainFrame.miLMgr.setSelected(false);
	}

	/**
	 * Returns null if the LManager doesn't exist yet.
	 */
	public static LManager2 getLManager()
	 {
		try
		 {
			return testDriver.getLManager();
		 }
		catch(NullPointerException e)
		 {
			log.println(e);
			return  null;
		 }
	 }

	private static JFileChooser rcFileChooser;
	private static JFileChooser getRcFileChooser()
	 {
		if(rcFileChooser == null)
		 {
			rcFileChooser = new JFileChooser();
			rcFileChooser.setFileFilter(
				new FileFilter()
				 {
					public boolean accept(File f)
					 {
						String name = f.getName().toLowerCase();
						int lastDot = name.lastIndexOf('.');
						return  lastDot != -1
							&&  name.substring(lastDot).equals(".jmars");
					 }
					public String getDescription()
					 {
						return  "JMARS State File (*.jmars)";
					 }
				 }
				);
//			rcFileChooser.setSelectedFile(
//				new File(Util.getHomePath("default.jmars")));
		 }
		return  rcFileChooser;
	 }

	protected void initFileMenu()
	 {
		final AbstractAction saveAction = new AbstractAction("Save") {
			{
				putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_S));
				setEnabled(rcFileChooser != null && rcFileChooser.getSelectedFile() != null);
			}

			public void actionPerformed(ActionEvent e) {
				saveState(getRcFileChooser().getSelectedFile().toString());
			}
		};
		menuFile.add(saveAction);
		
		AbstractAction saveAsAction = new AbstractAction("Save As...") {
			{
				putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_A));
			}

			public void actionPerformed(ActionEvent e) {
				JFileChooser fc = getRcFileChooser();
				if (Util.showSaveWithConfirm(fc, Main.this, ".jmars")) {
					File f = fc.getSelectedFile();
					saveState(f.toString());
					saveAction.setEnabled(true);
					setTitle(f.toString(), null);
				}
			}
		};
		menuFile.add(saveAsAction);
		
		final LoadSaveLayerDialogs layerDialogs = new LoadSaveLayerDialogs();
		
		AbstractAction saveLayersAction = new AbstractAction("Save Layers...") {
			{
				putValue(MNEMONIC_KEY, KeyEvent.VK_V);
			}
			public void actionPerformed(ActionEvent e) {
				layerDialogs.new SaveLayersDialog();
			}
		};
		menuFile.add(saveLayersAction);
		
		AbstractAction loadLayersAction = new AbstractAction("Load Layers...") {
			{
				putValue(MNEMONIC_KEY, KeyEvent.VK_L);
			}
			public void actionPerformed(ActionEvent e) {
				layerDialogs.new LoadLayersDialog();
			}
		};
		menuFile.add(loadLayersAction);
		
		menuFile.add(new JSeparator());

		AbstractAction action = new AbstractAction("Capture To JPEG") {
            public void actionPerformed(ActionEvent e) {
                String filename = null;
                JFileChooser fc = getFileChooser(".jpg", "JPEG Files (*.jpg)");
                if (fc == null)
                    return;
                fc.setDialogTitle("Capture to JPEG File");
                
                if (fc.showSaveDialog(Main.mainFrame) != JFileChooser.APPROVE_OPTION)
                    return;
                if ( fc.getSelectedFile() != null )
                    filename = fc.getSelectedFile().getPath();
                
                if(filename == null)
                        return;
                testDriver.dumpMainLViewManagerJpg(filename);

            }
          };

          JMenuItem miCapture = new JMenuItem(action);
          miCapture.setMnemonic(KeyEvent.VK_C);
          menuFile.add(miCapture);

          action = new AbstractAction("Capture To PNG") {
              public void actionPerformed(ActionEvent e) {
                  String filename = null;
                  JFileChooser fc = getFileChooser(".png", "PNG Files (*.png)");
                  if (fc == null)
                      return;
                  fc.setDialogTitle("Capture to PNG File");
                  
                  if (fc.showSaveDialog(Main.mainFrame) != JFileChooser.APPROVE_OPTION)
                      return;
                  if ( fc.getSelectedFile() != null )
                      filename = fc.getSelectedFile().getPath();
                  
                  if (filename == null)
                      return;
                  testDriver.dumpMainLViewManagerPNG(filename);
              }
          };
          
          JMenuItem miPngCapture = new JMenuItem(action);
          miCapture.setMnemonic(KeyEvent.VK_C);
          menuFile.add(miPngCapture);


         menuFile.add(new JSeparator());

         AbstractAction exitAction =
         	new AbstractAction("Exit")
			    {
				     public void actionPerformed(ActionEvent e)
				     {
				     	System.exit(0);
				     }
			    }
                ;

          JMenuItem miExit = new JMenuItem(exitAction);
          miExit.setMnemonic(KeyEvent.VK_X);
          menuFile.add(miExit);
        }
    
        private HashMap fileChoosersMap = new HashMap();
    	private JFileChooser getFileChooser(final String extension, final String description)
    	{
            JFileChooser fileChooser = (JFileChooser) fileChoosersMap.get(extension);
    	    if (fileChooser == null)
    	    {
    	        // Create the file chooser
    	        fileChooser = new JFileChooser(System.getProperty("user.dir"));
    	        fileChooser.addChoosableFileFilter(
    	                                            new javax.swing.filechooser.FileFilter()                                                {
    	                                                public boolean accept(File f)
    	                                                {
    	                                                    String fname = f.getName().toLowerCase();
    	                                                    return  f.isDirectory()  ||  fname.endsWith(extension);
    	                                                }
    	                                                public String getDescription()
    	                                                {
    	                                                    return description;
    	                                                }
    	                                            }
    	        );
                
                fileChoosersMap.put(extension, fileChooser);
    	    }
            
    	    return  fileChooser;
    	}
     
	 // saves the application properties to an external file (fname). 
	 protected void saveState(String fname)
	 {
		 // update the User Properties 
		 userProps.reset();
		 testDriver.saveState();

		 // Write the user properties to the config file.
		 try
		 {
			 userProps.savePropertiesFile(fname);
		 }
		 catch(IOException e)
		 {
			 log.aprintln(e);
			 JOptionPane.showMessageDialog(
				 this,
				 "Unable to save JMARS file:\n\n" + fname + "\n\n" + e + "\n\n",
				 "FILE SAVE ERROR",
				 JOptionPane.ERROR_MESSAGE);
		 }
	 }

	public static void setProjection(ProjObj po)
	 {
		if(po.getClass() != PO.getClass())
		 {
			log.aprintln("CANNOT ALTER PROJECTION TYPE AT RUNTIME!");
			log.aprintStack(-1);
			log.aprintln("CANNOT ALTER PROJECTION TYPE AT RUNTIME!");
			return;
		 }

		synchronized(listenerList)
		 {
			ProjObj old = PO;
			PO = po;
			fireProjectionEvent(old);
		 }
	 }

	/**
	 ** Used for ProjectionListener registrations.
	 **/
	private static EventListenerList listenerList = new EventListenerList();

	public static void addProjectionListener(ProjectionListener l)
	 {
		listenerList.add(ProjectionListener.class, l);
	 }

	public static void removeProjectionListener(ProjectionListener l)
	 {
		listenerList.remove(ProjectionListener.class, l);
	 }

	protected static void fireProjectionEvent(ProjObj old)
	 {
		// Guaranteed to return a non-null array
		Object[] listeners = listenerList.getListenerList();

		// Process the listeners last to first, notifying those that
		// are interested in this event
		ProjectionEvent e = null;
		for(int i=listeners.length-2; i>=0; i-=2)
			if(listeners[i] == ProjectionListener.class)
			 {
				// Lazily create the event
				if(e == null)
					e = new ProjectionEvent(old);
				ProjectionListener l = (ProjectionListener) listeners[i+1];
				l.projectionChanged(e);
			 }
	 }
}
