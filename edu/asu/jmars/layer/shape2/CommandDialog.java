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


package edu.asu.jmars.layer.shape2;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import edu.asu.jmars.util.*;

import edu.asu.jmars.layer.util.features.*;

/*
 * Herewith are the definitions for the Command Dialog while allows users 
 * to enter in commands to manipulate the DataTable.
 */
public class CommandDialog extends JDialog {
	static public String EXTENTION    = ".ssf";
	static public String DESCRIPTION  = "Shape Script File (*" + EXTENTION + ")";
	
	ShapeLayer shapeLayer;
	FeatureCollection fc;
	History history;
	

	// panel stuff.
	JTextArea     commandArea           = new JTextArea(15,60);
	JMenuItem     runCommandMenuItem    = new JMenuItem("Run");
	JMenuItem     loadMenuItem          = new JMenuItem("Load");
	JMenuItem     saveMenuItem          = new JMenuItem("Save");
	JPanel        statusPanel           = new JPanel();
	JLabel        statusBar             = new JLabel(" ");

	FileChooser fileChooser;

	public CommandDialog(FeatureCollection fc, History history, ShapeLayer shapeLayer, Frame owner){
		super(owner, "Script", false);
		
		this.shapeLayer = shapeLayer;
		this.fc = fc;
		this.history = history;

		fileChooser = new FileChooser();

		initPropertyBehavior();

		Container propPane = getContentPane();
		propPane.setLayout( new BorderLayout());
		
		JPanel featurePanel = new JPanel();
		featurePanel.setLayout( new BorderLayout());
		featurePanel.setBorder( BorderFactory.createTitledBorder("Feature Table"));
		
		// build the file menu
		JMenu fileMenu = new JMenu("File");
		fileMenu.add( loadMenuItem);
		fileMenu.add( saveMenuItem);
		
		// build the run menu
		JMenu runMenu = new JMenu("Run");
		runMenu.add( runCommandMenuItem);
		
		
		// build the menu bar
		JMenuBar menuBar = new JMenuBar();
		menuBar.add( fileMenu);
		menuBar.add( runMenu);
		propPane.add( menuBar, BorderLayout.NORTH);
		
		// build the command area.
		commandArea.setEditable(true);
		JScrollPane scrollPane = new JScrollPane( commandArea, 
							  JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
							  JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setBorder(BorderFactory.createEtchedBorder());
		propPane.add( scrollPane, BorderLayout.CENTER);
		
		
		// build the status bar.
		statusBar.setHorizontalAlignment(JLabel.LEFT);
		statusPanel.add( statusBar);
		propPane.add( statusPanel, BorderLayout.SOUTH);

		pack();

		fileChooser.addFilter(new ScriptFileChooser.FeatureProviderScript());
	}

	// Actions for components.
	private void initPropertyBehavior(){

		// Run the command file.
		runCommandMenuItem.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e){
					// Make a new history frame.
					if (history != null)
						history.mark();

					final ShapeLayer.LEDState ledState = new ShapeLayer.LEDStateFileIO();
					SwingUtilities.invokeLater(new Runnable(){
						public void run(){
							shapeLayer.begin(ledState);
							try {
								String text = commandArea.getText();
								String [] lines = text.split("\n");
								for (int i=0; i< lines.length; i++){
									new FeatureSQL( lines[i], fc, shapeLayer.selections, statusBar);
									// String perror = FeatureSQL.getResultString();
									//if (perror != null && statusBar != null){
									//	statusBar.setText( perror);
									//}
								}
							}
							finally {
								shapeLayer.end(ledState);
							}
						}
					});
				}
			});


		// Load in a command file.
		loadMenuItem.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e){
					File  [] scriptFile = fileChooser.chooseFile("Load");
					if (scriptFile==null){
						return;
					}
					
					ShapeLayer.LEDState ledState = new ShapeLayer.LEDStateFileIO();
					StringBuffer inputLine  = new StringBuffer();
					shapeLayer.begin(ledState);
					try {
						String [] lines = Util.readLines( new FileInputStream( scriptFile[0]));
						for (int i=0; i< lines.length; i++){
							inputLine.append( lines[i] + "\n");
						}
						commandArea.setText( inputLine.toString());
					} catch (Exception exception) {
					} finally {
						shapeLayer.end(ledState);
					}
				}
			});
		
		// Save a command file out.
		saveMenuItem.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e){
					File [] scriptFile = fileChooser.chooseFile("Save");
					if (scriptFile==null){
						return;
					}
					
					
					ShapeLayer.LEDState ledState = new ShapeLayer.LEDStateFileIO();
					shapeLayer.begin(ledState);
					try {
						File outFile = filterFile( scriptFile[0]);
						BufferedWriter  outStream = new BufferedWriter( new FileWriter( outFile));
						outStream.write( commandArea.getText());
						outStream.flush();
						outStream.close();
					} catch (IOException ioe) {
						System.out.println("Error writing file " + 
								   scriptFile + ": " + ioe.getMessage());
						ioe.printStackTrace();
					}  catch (Exception ex) {
						ex.printStackTrace();
					}
					finally {
						shapeLayer.end(ledState);
					}
				}
			});
		
		
	} // end: initPropertyBehavior
    

	// The file must end with the file type extention.
	private File filterFile( File f){
		String fileName = f.toString();
		if (!fileName.endsWith( EXTENTION)){
			fileName += EXTENTION;
		}
		return new File( fileName);
	}
	
} // end: CommandDialog.java

