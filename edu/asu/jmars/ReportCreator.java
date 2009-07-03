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

import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;

import edu.asu.jmars.layer.util.FileLogger;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.Util;

/**
 * Gathers information on the state of JMARS when a problem occurred and emails
 * it to <code>jmars.config:reportpage</code>.
 */
public class ReportCreator {
	private JDialog dlg;
	private Thread sendingThread;
	private String error;
	
	public JDialog getDialog() {
		return dlg;
	}
	
	public ReportCreator(JFrame parent, FileLogger logger) {
		dlg = new JDialog(parent, "Report a Problem", true);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		
		JTextPane intro = new JTextPane();
		intro.setBackground(dlg.getContentPane().getBackground());
		intro.setEditable(false);
		intro.setText("Got a problem?  We'd like to help.\n\n" +
			"Submit the form below and we'll get back to you as soon as possible.");
		
		JLabel emailLabel = new JLabel("Email Address");
		final JTextField email = new JTextField();
		if (!Main.isInternal()) {
			email.setText(Main.USER);
		}
		
		JLabel noteLabel = new JLabel("How to Recreate the Problem");
		final JTextArea noteText = new JTextArea();
		noteText.setLineWrap(true);
		noteText.setRows(8);
		JScrollPane noteSP = new JScrollPane(noteText);
		
		JLabel dataLabel = new JLabel("Debugging Information");
		final JTextArea data = new JTextArea();
		data.setRows(8);
		data.setEditable(false);
		StringBuffer text = new StringBuffer();
		
		text.append("========= Environment =========\n");
		text.append("JMARS build time: " +
			Main.ABOUT().DATE + "\n");
		text.append("Java version: " +
			System.getProperty("java.runtime.name") + ", " +
			System.getProperty("java.vm.vendor") + "\n");
		text.append("Java Library Path: " +
			System.getProperty("java.library.path") + "\n");
		text.append("Operating system: " +
			System.getProperty("os.name") + ", " +
			System.getProperty("os.arch") + ", " +
			System.getProperty("os.version") + "\n");
		text.append("\n");
		
		text.append("========= Log =========\n");
		text.append(logger == null ? "Log empty!" : logger.getContent());
		text.append("\n\n\n");
		data.setText(text.toString());
		data.setLineWrap(true);
		data.setBackground(dlg.getContentPane().getBackground());
		JScrollPane dataSP = new JScrollPane(data);
		
		final JButton send = new JButton("Send");
		send.setMnemonic(KeyEvent.VK_S);
		send.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				send.setEnabled(false);
				send(email.getText(), noteText.getText(), data.getText());
			}
		});
		
		int pad = 8;
		Container c = dlg.getContentPane();
		c.setLayout(new GridBagLayout());
		Insets base = new Insets(pad,pad,pad,pad);
		Insets indent = new Insets(pad,pad*3,pad,pad);
		c.add(intro, new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.BOTH, base, 0,0));
		c.add(emailLabel, new GridBagConstraints(0,1,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.BOTH, base, 0,0));
		c.add(email, new GridBagConstraints(0,2,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.BOTH, indent, 0,0));
		c.add(noteLabel, new GridBagConstraints(0,3,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.BOTH, base, 0,0));
		c.add(noteSP, new GridBagConstraints(0,4,1,1,1,1,GridBagConstraints.WEST,GridBagConstraints.BOTH, indent, 0,0));
		c.add(dataLabel, new GridBagConstraints(0,5,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.BOTH, base, 0,0));
		c.add(dataSP, new GridBagConstraints(0,6,1,1,1,1,GridBagConstraints.WEST,GridBagConstraints.BOTH, indent, 0,0));
		c.add(send, new GridBagConstraints(0,7,1,1,0,0,GridBagConstraints.EAST,GridBagConstraints.NONE, base, 0,0));
		c.setMaximumSize(new Dimension(800,600));
		
		dlg.pack();
	}
	
	private void send (final String email, final String notes, final String info) {
		final int timeout = 30*1000;
		final String base = dlg.getTitle();
		dlg.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
		dlg.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		error = null;
		
		final Timer timer = new Timer(1000, new ActionListener() {
			int time = timeout/1000;
			public void actionPerformed(ActionEvent e) {
				time--;
				dlg.setTitle(base + " - sending (" + time + " sec...)");
			}
		});
		timer.start();
		
		// get off the AWT thread so updates can be shown in the GUI
		sendingThread = new Thread(new Runnable() {
			public void run() {
				try {
					PostMethod post = new PostMethod(Config.get("reportpage"));
					post.addParameter("user", Main.USER);
					post.addParameter("email", email);
					post.addParameter("notes", notes);
					post.addParameter("info", info);
					
					HttpClient http = new HttpClient();
					http.getHttpConnectionManager().getParams().setConnectionTimeout(timeout);
					
					int code = Util.postWithRedirect(http, post, 3);
					if (code != HttpStatus.SC_OK) {
						error = "Server returned unexpected code " + code;
					} else {
						String response = new BufferedReader(
							new InputStreamReader(
								post.getResponseBodyAsStream())).readLine();
						if (response.equals("OKAY")) {
							// mail sent properly
						} else if (response.equals("FAILURE")) {
							error = "Server was unable to deliver the message: " + response;
						}
					}
				} catch (HttpException e) {
					error = "Error communicating with server";
				} catch (IOException e) {
					error = "Unable to establish a connection, are you connected?";
				} catch (Exception e) {
					error = "Unexpected exception occurred: " + e.getMessage();
				}
				
				// get back on the AWT thread to handle the result
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						timer.stop();
						dlg.dispose();
						if (error != null) {
							// failure sending report, suggest e-mail
							JOptionPane.showMessageDialog(
								Main.mainFrame,
								"Unable to deliver message: " + error + "\n\nEmail " + Config.get("email"),
								"Unable to deliver message", JOptionPane.ERROR_MESSAGE);
						}
					}
				});
				
				sendingThread = null;
			}
		});
		sendingThread.setPriority(Thread.MIN_PRIORITY);
		sendingThread.start();
	}
}
