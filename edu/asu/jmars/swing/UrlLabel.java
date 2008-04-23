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


package edu.asu.jmars.swing;

import edu.asu.jmars.util.*;
import edu.stanford.ejalbert.BrowserLauncher;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class UrlLabel extends JLabel
 {
	private static DebugLog log = DebugLog.instance();

	private String url;
	private String plain;
	private String under;

	public UrlLabel(URL url)
	 {
		this(url.toString());
	 }
	public UrlLabel(String s)
	 {
		this.url = s;
		plain = "<html><pre><font color=#0000CC>"+s+"</color></pre>";
		under = "<html><pre><font color=#0000CC><u>"+s+"</u></color></pre>";

		setText(plain);
		addMouseListener(
			new MouseAdapter()
			 {
				Font oldFont = getFont();
				public void mouseClicked(MouseEvent e)
				 {
					if(SwingUtilities.isLeftMouseButton(e))
						try
						 {
							BrowserLauncher.openURL(url);
						 }
						catch(Exception ex)
						 {
							log.aprintln(ex);
							log.aprintln(url);
							JOptionPane.showMessageDialog(
								UrlLabel.this,
								"Unable to open browser due to:\n" + ex,
								"JMARS",
								JOptionPane.ERROR_MESSAGE);
						 }
				 }
				public void mouseEntered(MouseEvent ev)
				 {
					setText(under);
				 }
				public void mouseExited(MouseEvent e)
				 {
					setText(plain);
				 }
			 }
			);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	 }
 }
