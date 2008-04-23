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

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

public class FileUploadDialog extends JDialog implements ActionListener {
	private static final long serialVersionUID = 1L;
	private static DebugLog log = DebugLog.instance();

	JTextField nameField;
	JButton browseButton;
	JFileChooser fileChooser;
	JButton okButton, cancelButton;
	CustomMapServer customMapServer;
	String fileUploaded = null;
	
	public FileUploadDialog(Dialog parent, CustomMapServer customMapServer){
		super(parent, "Upload File", true);
		this.customMapServer = customMapServer;
		initialize();
	}
	
	public FileUploadDialog(Frame parent, CustomMapServer customMapServer){
		super(parent, "Upload File", true);
		this.customMapServer = customMapServer;
		initialize();
	}
	
	private void initialize(){
		JPanel main = new JPanel(new BorderLayout());
		main.add(getNamePanel(), BorderLayout.CENTER);
		main.add(getButtonPanel(), BorderLayout.SOUTH);
		setContentPane(main);
		pack();
		setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
		addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				windowClosingEvent(e);
			}
		});
	}
	
	private JPanel getButtonPanel(){
		okButton = new JButton("Ok");
		okButton.addActionListener(this);
		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(this);
		
		JPanel buttonPanel = new JPanel(new FlowLayout());
		buttonPanel.add(okButton);
		buttonPanel.add(cancelButton);
		
		return buttonPanel;
	}
	
	private JPanel getNamePanel(){
		nameField = new JTextField(30);
		
		browseButton = new JButton("Browse");
		browseButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				browseButtonActionPerformed(e);
			}
		});
		
		JPanel namePanel = new JPanel(new BorderLayout());
		namePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		namePanel.add(new JLabel("File or URL:"), BorderLayout.WEST);
		namePanel.add(nameField, BorderLayout.CENTER);
		namePanel.add(browseButton, BorderLayout.EAST);
		
		return namePanel;
	}
	
	private void browseButtonActionPerformed(ActionEvent e){
		if (fileChooser == null){
			fileChooser = new JFileChooser();
			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		}
		
		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION){
			nameField.setText(fileChooser.getSelectedFile().getPath());
		}
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand().equals(okButton.getActionCommand())){
			log.println("Uploading "+nameField.getText()+" file to "+customMapServer.getURI());
			
			// Clear the name of the last file that was uploaded, on successful upload
			// it will be set with the new file name below.
			fileUploaded = null;
			
			String fileUrlString = nameField.getText();
			
			try {
				if (fileUrlString.startsWith("http://") || fileUrlString.startsWith("ftp://")) {
					// A URL, ask the server to download the file itself
					URL url = new URL(fileUrlString);
					customMapServer.uploadCustomMap(url.getPath(), url);
				} else {
					// A file, upload the file to the server
					File file = new File(fileUrlString);
					customMapServer.uploadCustomMap(file.getName(), file);
				}
				
				// Save the name of the uploaded file.
				fileUploaded = fileUrlString;
			}
			catch(Exception ex) {
				JOptionPane.showMessageDialog(this,
						Util.foldText(ex.toString(), 80, "\n"),
						"Error! Unable to upload \""+fileUrlString+"\".", JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
		else if (e.getActionCommand().equals(cancelButton.getActionCommand())){
			log.println("Upload cancelled.");
		}
		setVisible(false);
	}
	
	public void windowClosingEvent(WindowEvent e){
	}
	
	/**
	 * Show the dialog and don't return until a file is uploaded or the user
	 * cancels or closes the dialog. On return from this method, the name of
	 * the uploaded file is returned or a <code>null</code> is returned
	 * signifying the fact that the user cancelled.
	 */
	public String uploadFile(){
		fileUploaded = null;
		setVisible(true);
		return fileUploaded;
	}
}
