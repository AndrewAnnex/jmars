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


package edu.asu.jmars.layer.util.features;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import javax.swing.*;

public class FeatureSQLCommandDialog extends JDialog {

	FeatureCollection fc;
	FeatureProvider provider = new ScriptFileChooser.FeatureProviderScript();

	// panel stuff.
	JTextArea     commandArea           = new JTextArea(15,60);
	JMenuItem     runCommandMenuItem    = new JMenuItem("Run");
	JMenuItem     loadMenuItem          = new JMenuItem("Load");
	JMenuItem     saveMenuItem          = new JMenuItem("Save");
	JPanel        statusPanel           = new JPanel();
	JLabel        statusBar             = new JLabel(" ");
	ScriptFileChooser scriptFileChooser;

	public FeatureSQLCommandDialog(FeatureCollection fc) {
		super( (Frame)null, "Script", false);

		this.fc = fc;
		scriptFileChooser = new ScriptFileChooser();

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
		statusPanel.add( statusBar);
		propPane.add( statusPanel, BorderLayout.SOUTH);

		pack();

	}




	// Actions for components.
	private void initPropertyBehavior(){

		// Run the command file.
		runCommandMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e){
				String text = commandArea.getText();
				String [] lines = text.split("\n");
				for (int i=0; i< lines.length; i++){
					new FeatureSQL( lines[i], fc, statusBar);
				}
			}
		});

		// Load in a command file.
		loadMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e){
				File  [] scriptFile = scriptFileChooser.chooseFile("Load");
				if (scriptFile==null)
					return;

				StringBuffer inputLine  = new StringBuffer();
				try {
					BufferedReader  inStream = new BufferedReader(  new FileReader( scriptFile[0].toString()));
					String line;
					do {
						line = inStream.readLine();
						if (line != null && line.length() > 0) {
							inputLine.append( line + "\n" );
						}
					} while (line != null);
					inStream.close();
				} catch (IOException ex) {
					JOptionPane.showMessageDialog(
						FeatureSQLCommandDialog.this,
						new String[]{
							"Error reading file " + scriptFile + ": ",
							ex.getMessage(),
						},
						"Error!",
						JOptionPane.ERROR_MESSAGE);
					return;
				}
				commandArea.setText( inputLine.toString());
			}
		});

		// Save a command file out.
		saveMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e){
				File [] scriptFile = scriptFileChooser.chooseFile("Save");
				if (scriptFile==null)
					return;

				try {
					File outFile = filterFile( scriptFile[0]);
					BufferedWriter  outStream = new BufferedWriter( new FileWriter( outFile));
					outStream.write( commandArea.getText());
					outStream.flush();
					outStream.close();
				} catch (IOException ioe) {
					JOptionPane.showMessageDialog(
							FeatureSQLCommandDialog.this,
							new String[]{
								"Error writing file " + scriptFile + ": ",
								ioe.getMessage(),
							},
							"Error!",
							JOptionPane.ERROR_MESSAGE);
					//ioe.printStackTrace();
				}  catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
	} // end: initPropertyBehavior

	// The file must end with the file type extention.
	private File filterFile( File f){
		String fileName = f.toString();
		if (!fileName.endsWith(scriptFileChooser.getProvider().getExtension())) {
			fileName += provider.getExtension();
		}
		return new File( fileName);
	}
}

