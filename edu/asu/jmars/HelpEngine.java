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

import edu.asu.jmars.util.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import java.io.IOException;

/**
 **  a class for maintaining an associative array that describes the help engine.  
 **  Rather than putting the name of an html file directly in the code to access it,
 **  a help button looks up a general reference to the page from the jmars config file.
 **
 **  static methods:
 **      showPage( String key )
 **           - displays the web page corresponding to the inputting string. The page is displayed in 
 **               a separate frame.
 **
 **   @author James Winburn  MSFF-ASU 4/22/03
 **/
public class HelpEngine {
	
   private static DebugLog log = DebugLog.instance();
	private static JEditorPane editorPane = new JEditorPane();
	private static JFrame     f;
	// constructor
	public HelpEngine(){}

	// Display the page associated with the inputted string.
	static void showPage ( String page){
	   String helpDir = Config.get("help.directory");

	   String pageName = helpDir + Config.get(page);
	   String url;
	   try
	    {
	       url = Main.getResource(pageName).toString();
	    }
	   catch(NullPointerException e)
	    {
	       log.aprintln(pageName);
	       throw  e;
	    }
	   final boolean usingJar = url.startsWith("jar:");

		editorPane.setEditable(false);
		try {
			editorPane.setPage(url);
		} catch (Exception ex) { 
			ex.printStackTrace(); 
		}
		editorPane.addHyperlinkListener( new HyperlinkListener() {
			public void hyperlinkUpdate(HyperlinkEvent e){
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					try {
						String newPage = (usingJar ? "jar:" : "file:") +
							e.getURL().getFile();
						String ref = e.getURL().getRef();
						if (ref != null){
							newPage +=  "#" + ref;
						} 
						editorPane.setPage( newPage);
					} catch (IOException ex) { 
						ex.printStackTrace();
					}
				}
			}
		});

		// set up the scrollframe.
		JScrollPane scrollPane = new JScrollPane( editorPane );
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int frameWidth = (int)(screenSize.width * 0.6);
		int frameHeight = (int)(screenSize.height * 0.9);
		scrollPane.setPreferredSize( new Dimension(frameWidth, frameHeight));

		// If the help pane has not been defined, set it up.  If the help pane
		// HAS been defined, remove whatever is in it now and put the desired
		// page up.
		if (f != null){
			f.getContentPane().removeAll();
		} else {
			f =  new JFrame( (String)Config.get("help.title"));
			f.setLocation( 
				(int)((screenSize.width - frameWidth) / 2),
				(int)((screenSize.height - frameHeight) / 4)
				);
			f.addWindowListener( new WindowListener() {
				
				public void windowClosing(WindowEvent e) {	
					f = null;
				}
				public void windowClosed(WindowEvent e) {}
				public void windowOpened(WindowEvent e) {}
				public void windowIconified(WindowEvent e) {}
				public void windowDeiconified(WindowEvent e) {}
				public void windowActivated(WindowEvent e) {}
				public void windowDeactivated(WindowEvent e) {}
			});
		}		
		
		f.getContentPane().add( scrollPane);
		f.pack();
		f.setVisible(true);
	}
}


