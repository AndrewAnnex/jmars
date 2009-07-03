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


package edu.asu.jmars.layer.map2;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import edu.asu.jmars.swing.CustomTabOrder;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

public class FileUploadDialog extends JDialog {
	private static final long serialVersionUID = 2L;
	private static DebugLog log = DebugLog.instance();
	
	private final CustomMapServer customMapServer;
	private final JFileChooser fileChooser;
	private JTextField nameField = new JTextField(20);
	private JTextField remoteName = new JTextField(20);
	private JTextField ignoreField = new JTextField(5);
	private JTextField north = new JTextField(2);
	private JTextField south = new JTextField(2);
	private JTextField west = new JTextField(2);
	private JTextField east = new JTextField(2);
	private JCheckBox email = new JCheckBox("Email when ready");
	private JButton uploadPB = new JButton("Upload");
	private JButton cancelPB = new JButton("Cancel");
	
	/** Creates a simple file chooser for the custom maps */
	public static JFileChooser createDefaultChooser() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		return chooser;
	}

	/**
	 * The task currently executing within this dialog; the task can compare
	 * itself to this field on the AWT event thread to see if it 'owns' the
	 * dialog.
	 */
	private UploadTask currentTask;
	
	/**
	 * Creates a a modal upload dialog.
	 * @param parent Frame to hang this dialog from
	 * @param chooser File chooser, or null to internally create a default chooser with {@link #createDefaultChooser()}.
	 * @param customMapServer The map server to furnish the map to.
	 */
	public FileUploadDialog(Frame parent, JFileChooser chooser, CustomMapServer customMapServer){
		super(parent, "Upload File", true);
		this.fileChooser = chooser == null ? createDefaultChooser() : chooser;
		this.customMapServer = customMapServer;
		setContentPane(createMain());
		pack();
		setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
	}
	
	private static boolean isURL(String path) {
		path = path.trim().toLowerCase();
		return path.startsWith("ftp://") || path.startsWith("http://");
	}
	
	private JComponent createMain() {
		final JButton browsePB = new JButton("Browse");
		
		browsePB.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				String file = browse();
				if (file != null) {
					nameField.setText(file);
				}
			}
		});
		
		ignoreField.setToolTipText("Specify the pixel value which is transparent in all bands, or leave blank if there isn't one");
		
		uploadPB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				String path = nameField.getText();
				if (!isURL(path) && !new File(path).exists()) {
					error(path, "File does not exist!");
					return;
				}
				String name = remoteName.getText();
				if (name.length() == 0) {
					error(name, "Must provide a remote name to identify this map");
					return;
				}
				boolean titleUsed = false;
				for (MapSource source: customMapServer.getMapSources()) {
					if (source.getTitle().equals(name)) {
						titleUsed = true;
						break;
					}
				}
				if (titleUsed && JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(
						FileUploadDialog.this,
						"A custom map with that name already exists, overwrite?",
						"Map name exists",
						JOptionPane.YES_NO_OPTION)) {
					return;
				}
				try {
					Rectangle2D bounds = null;
					if (west.getText().trim().length() > 0 ||
							east.getText().trim().length() > 0 ||
							north.getText().trim().length() > 0 ||
							south.getText().trim().length() > 0) {
						double minx = Double.parseDouble(west.getText().trim());
						double maxx = Double.parseDouble(east.getText().trim());
						double miny = Double.parseDouble(south.getText().trim());
						double maxy = Double.parseDouble(north.getText().trim());
						if (minx < 0) {
							minx += 360;
							maxx += 360;
						}
						if (minx > maxx) {
							maxx += 360;
						}
						bounds = new Rectangle2D.Double(minx,miny,maxx-minx,maxy-miny);
					}
					
					Double ignore = null;
					if (ignoreField.getText().trim().length() > 0) {
						ignore = Double.parseDouble(ignoreField.getText());
					}
					
					boolean mail = email.isSelected();
					
					log.println(MessageFormat.format(
						"Uploading {0} to {1}, ignore {2}, bounds {3}, email {4}",
						path,
						customMapServer.getURI(),
						ignore==null?"unspecified":ignore.toString(),
						bounds==null?"unspecified":bounds.toString(),
						mail ? "yes" : "no"));
					
					// creates a new task for this upload off the AWT thread so
					// the user can potentially do other things while waiting, and moves
					// the onUpload callback into the task so the dialog doesn't have
					// to hold it
					currentTask.setArgs(name, path, bounds, ignore, mail);
					Thread uploadThread = new Thread(currentTask);
					uploadThread.setName("Custom upload thread");
					uploadThread.setPriority(Thread.MIN_PRIORITY);
					uploadThread.start();
				} catch(Exception ex) {
					error(path, ex.toString());
				}
			}
		});
		
		cancelPB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				stopAndClose();
			}
		});
		
		nameField.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				update();
			}
			public void insertUpdate(DocumentEvent e) {
				update();
			}
			public void removeUpdate(DocumentEvent e) {
				update();
			}
			private void update() {
				String name = nameField.getText();
				if (isURL(name)) {
					try {
						name = new URL(name).getPath().replaceAll("^/", "");
					} catch (Exception e) {
						// silently ignore, we just won't auto-populate the name
						// from a URL we don't understand
						return;
					}
				} else {
					name = new File(name).getName();
				}
				remoteName.setText(name);
			}
		});
		
		JPanel main = new JPanel(new GridBagLayout());
		main.setBorder(new EmptyBorder(4,4,4,4));
		
		Insets insets = new Insets(4,4,4,4);
		int padx = 0;
		int pady=  0;
		
		Box buttons = Box.createVerticalBox();
		buttons.add(Box.createVerticalGlue());
		buttons.add(uploadPB);
		buttons.add(Box.createVerticalStrut(insets.top));
		buttons.add(cancelPB);
		
		main.add(new JLabel("File or URL"), new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(nameField, new GridBagConstraints(2,0,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(browsePB, new GridBagConstraints(4,0,1,1,0,0,GridBagConstraints.EAST,GridBagConstraints.NONE,insets,padx,pady));
		main.add(new JLabel("Map Name"), new GridBagConstraints(0,1,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,insets,padx,pady));
		main.add(remoteName, new GridBagConstraints(2,1,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(email, new GridBagConstraints(0,2,5,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(new JLabel(), new GridBagConstraints(0,3,5,1,1,1,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(new JLabel("Advanced Options"), new GridBagConstraints(0,4,5,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,insets,padx,pady));
		main.add(new JLabel("Ignore Value"), new GridBagConstraints(0,5,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,insets,padx,pady));
		main.add(ignoreField, new GridBagConstraints(2,5,1,1,0.5,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(new JLabel("Bounds"), new GridBagConstraints(0,6,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,insets,padx,pady));
		main.add(new JLabel("Northernmost Latitude (\u00b0N)"), new GridBagConstraints(0,7,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,insets,padx,pady));
		main.add(north, new GridBagConstraints(2,7,1,1,0.5,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(new JLabel("Southernmost Latitude (\u00b0N)"), new GridBagConstraints(0,8,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,insets,padx,pady));
		main.add(south, new GridBagConstraints(2,8,1,1,0.5,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(new JLabel("Westernmost Longitude (\u00b0E)"), new GridBagConstraints(0,9,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,insets,padx,pady));
		main.add(west, new GridBagConstraints(2,9,1,1,0.5,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(new JLabel("Eastermost Longitude (\u00b0E)"), new GridBagConstraints(0,10,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,insets,padx,pady));
		main.add(east, new GridBagConstraints(2,10,1,1,0.5,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,insets,padx,pady));
		main.add(buttons, new GridBagConstraints(4,7,1,4,0,1,GridBagConstraints.SOUTHEAST,GridBagConstraints.NONE,insets,padx,pady));
		
		// set up hot keys
		browsePB.setMnemonic('B');
		uploadPB.setMnemonic('U');
		cancelPB.setMnemonic('C');
		
		// customize the focus traversal
		main.setFocusTraversalPolicy(new CustomTabOrder(new Component[] {
			nameField,
			browsePB,
			remoteName,
			email,
			ignoreField,
			north,
			south,
			west,
			east,
			uploadPB,
			cancelPB
		}));
		main.setFocusTraversalPolicyProvider(true);
		revertValues();
		return main;
	}
	
	private void setDialogWaiting(boolean waiting) {
		if (waiting) {
			uploadPB.setEnabled(false);
			cancelPB.setText("Hide");
			cancelPB.setToolTipText("Runs in the background, and pops up a dialog when map is ready");
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		} else {
			uploadPB.setEnabled(true);
			cancelPB.setText("Cancel");
			cancelPB.setToolTipText("");
			setCursor(Cursor.getDefaultCursor());
		}
	}
	
	private void revertValues() {
		north.setText("90");
		south.setText("-90");
		west.setText("-180");
		east.setText("180");
		nameField.setText("");
		ignoreField.setText("");
	}
	
	/**
	 * Resets and closes the dialog, should be called on the AWT event thread
	 * when Cancel/Hide is pressed, or an upload finishes successfully.
	 */
	private void stopAndClose() {
		// done with this action so let the reaper do its thing
		currentTask = null;
		setDialogWaiting(false);
		setVisible(false);
	}
	
	/** Shows an error dialog with the given message, only call on the AWT event thread */
	private void error(String path, String msg) {
		JOptionPane.showMessageDialog(FileUploadDialog.this,
			Util.foldText(MessageFormat.format(
					"Upload of ''{0}'' failed with message ''{1}''", path, msg),
				60, "\n"),
			"The upload failed", JOptionPane.ERROR_MESSAGE);
	}
	
	private String browse() {
		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			return fileChooser.getSelectedFile().getPath();
		} else {
			return null;
		}
	}
	
	/**
	 * Show the dialog for a new upload; note that calling {@link #setVisible(boolean)}
	 * merely shows the dialog from any prior upload, but won't set it up properly for
	 * a new one.
	 * 
	 * Should be called on the AWT event thread.
	 * 
	 * @param onUpload The action to call on the AWT event thread when this
	 * dialog has uploaded a map.
	 */
	public void uploadFile(final Runnable onUpload) {
		currentTask = new UploadTask(onUpload);
		revertValues();
		setVisible(true);
	}
	
	/** Tracks an upload so multiple uploads can run simultaneously */
	private class UploadTask implements Runnable {
		private String name;
		private String path;
		private Rectangle2D bounds;
		private Double ignore;
		private boolean wantsMail;
		private final Runnable onUpload;
		public UploadTask(Runnable onUpload) {
			this.onUpload = onUpload;
		}
		public void setArgs(String name, String path, Rectangle2D bounds, Double ignore, boolean mail) {
			this.name = name;
			this.path = path;
			this.bounds = bounds;
			this.ignore = ignore;
			this.wantsMail = mail;
		}
		public void run() {
			try {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (currentTask == UploadTask.this) {
							setDialogWaiting(true);
						}
					}
				});
				if (isURL(path)) {
					// A URL, ask the server to download the file itself
					URL url = new URL(path);
					customMapServer.uploadCustomMap(name, url, bounds, ignore, wantsMail);
				} else {
					// A file, upload the file to the server
					File file = new File(path);
					customMapServer.uploadCustomMap(name, file, bounds, ignore, wantsMail);
				}
				// on success, either close the dialog, or popup a notice of success
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (currentTask == UploadTask.this) {
							stopAndClose();
						} else {
							JOptionPane.showMessageDialog(
								FileUploadDialog.this,
								Util.foldText("Custom map '" + path + "'' has finished", 60, "\n"),
								"Custom map uploaded",
								JOptionPane.INFORMATION_MESSAGE);
						}
						
						// finally, hit the callback
						onUpload.run();
					}
				});
			} catch (final Exception e) {
				e.printStackTrace();
				// on error, get out of waiting mode, and use a popup to notify of failure
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (currentTask == UploadTask.this) {
							setDialogWaiting(false);
						}
						
						error(path, e.getMessage());
					}
				});
			}
		}
	}
	
	public static void main(String[] args) {
		new FileUploadDialog(null, null, null).setVisible(true);
	}
}
