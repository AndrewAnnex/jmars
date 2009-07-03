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


package edu.asu.jmars.layer.stamp.focus;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import edu.asu.jmars.Main;
import edu.asu.jmars.swing.MultiLabel;
import edu.asu.jmars.swing.PasteField;
import edu.asu.jmars.util.Util;

public class ImportStampsDialog implements ActionListener
{
    private JDialog dialog;
    private JTextField txtFilename;
    private JButton btnBrowse = new JButton("Browse...");
    private JButton btnOK = new JButton("OK");
    private JButton btnCancel = new JButton("Cancel");
    
    private File lastDirectory;
    
    private FilledStampFocus filledFocus;
    
    ImportStampsDialog(FilledStampFocus newFocus)
    {
    	filledFocus=newFocus;
    	
        // Top panel
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        
        String msg1 = "Specify text file containing list of stamps to import.  The file " +
                      "must contain stamp IDs delimited by whitespace (includes newlines).";
        String msg2 = "Each stamp will be loaded and rendered if it is included in the " +
                      "list of stamps for this layer; otherwise, the stamp is ignored.";
        msg1 = Util.lineWrap(msg1, 60);
        msg2 = Util.lineWrap(msg2, 60);
        JPanel textPanel1 = new JPanel();
        JPanel textPanel2 = new JPanel();
        textPanel1.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
        textPanel2.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
        textPanel1.add(new MultiLabel(msg1));
        textPanel2.add(new MultiLabel(msg2));
        
        top.add(textPanel1);
        top.add(textPanel2);
        
        // Middle panel
        JPanel middle = new JPanel();
        middle.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        middle.add(new JLabel("Filename:"));
        txtFilename = new PasteField(20);
        middle.add(txtFilename);
        
        // File chooser dialog launch button.
        btnBrowse.addActionListener(
            new ActionListener()
            {
                private JFileChooser fc = new JFileChooser(lastDirectory);
                    
                public void actionPerformed(ActionEvent e)
                {
                    // Show the file chooser
                    if(fc.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION)
                        return;
                    
                    txtFilename.setText(fc.getSelectedFile().getPath());
                    lastDirectory = fc.getCurrentDirectory();
                }
            }
        );
        middle.add(btnBrowse);
        
        // Bottom panel
        JPanel bottom = new JPanel();
        btnOK.addActionListener(this);
        bottom.add(btnOK);
        btnCancel.addActionListener(this);
        bottom.add(btnCancel);
        
        // Construct the dialog itself
        dialog = new JDialog(Main.getLManager(),
                             "Import Stamps",
                             true);
        dialog.getContentPane().add(top, BorderLayout.NORTH);
        dialog.getContentPane().add(middle, BorderLayout.CENTER);
        dialog.getContentPane().add(bottom, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocation(Main.getLManager().getLocation());
    }
    
    // Does not return until dialog is hidden or
    // disposed of.
    public void show()
    {
        dialog.setVisible(true);
    }
    
    public void actionPerformed(ActionEvent e)
    {
        if(e.getSource() == btnCancel) {
            dialog.dispose();
            return;
        }
        else if (e.getSource() == btnOK) {
            String filename = txtFilename.getText().trim();
            if (filename == null ||
                    filename.equals("")) {
                JOptionPane.showMessageDialog(null,
                      "Please provide name of file.",
                      null,
                      JOptionPane.PLAIN_MESSAGE);
                return;
            }
            
            File file = new File(filename);
            if (!file.exists()) {
                JOptionPane.showMessageDialog(null,
                      "File named " + filename + " does not exist.",
                      null,
                      JOptionPane.PLAIN_MESSAGE);
                return;
            }
            
            filledFocus.addStamps(file);
            
            dialog.dispose();
            return;
        }
    }
}
