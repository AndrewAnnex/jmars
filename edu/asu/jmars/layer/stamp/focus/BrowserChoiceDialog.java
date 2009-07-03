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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.StringTokenizer;

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
import edu.asu.jmars.layer.stamp.StampLayer;
import edu.asu.jmars.swing.MultiLabel;
import edu.asu.jmars.swing.PasteField;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.Util;

public class BrowserChoiceDialog implements ActionListener
{
    private JDialog dialog;
    private JTextField txtCommand;
    private JButton btnBrowse = new JButton("Browse...");
    private JButton btnOK = new JButton("OK");
    private JButton btnClear = new JButton("Clear");
    private JButton btnCancel = new JButton("Cancel");
    
    BrowserChoiceDialog()
    {
        // Retrieve and existing custom browser command setting.
        String browserCmd = Config.get(StampLayer.CFG_BROWSER_CMD_KEY, "");
        
        // Construct dialog contents
        JPanel top = new JPanel();
        String msg = "Please provide command to start preferred webbrowser program.  " +
                     "Include program name with valid directory path (if needed) and " +
                     "any necessary command line options." +
                     "\n  \nFor the command argument " + 
                     "which specifies the webpage to open, use " + StampLayer.URL_TAG + 
                     " as the placeholder." +
                     "\n  \nExample:  mywebbrowser " + StampLayer.URL_TAG;
        msg = Util.lineWrap(msg, 80);
        MultiLabel txtBox = new MultiLabel(msg);
        top.add(txtBox);
        
        JPanel middle = new JPanel();
        middle.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        middle.add(new JLabel("Command:"));
        txtCommand = new PasteField(browserCmd, 35);
        middle.add(txtCommand);
        
        // Browser program chooser dialog launch button.
        btnBrowse.addActionListener(
                                    new ActionListener()
                                    {
                                        public void actionPerformed(ActionEvent e)
                                        {
                                            // Show the file chooser
                                            JFileChooser fc = getFileChooser();
                                            if (fc.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION)
                                                return;
                                            
                                            txtCommand.setText(fc.getSelectedFile().getPath() + 
                                                               " " + StampLayer.URL_TAG);
                                        }
                                    }
                                   );
        middle.add(btnBrowse);
        
        JPanel bottom = new JPanel();
        btnOK.addActionListener(this);
        bottom.add(btnOK);
        btnClear.addActionListener(this);
        bottom.add(btnClear);
        btnCancel.addActionListener(this);
        bottom.add(btnCancel);
        
        // Construct the dialog itself
        dialog = new JDialog(Main.getLManager(),
                             "Webbrowser Preference",
                             true);
        dialog.getContentPane().setLayout(new BoxLayout(dialog.getContentPane(), BoxLayout.Y_AXIS));
        dialog.getContentPane().add(top);
        dialog.getContentPane().add(middle);
        dialog.getContentPane().add(bottom);
        dialog.pack();
        dialog.setLocation(Main.getLManager().getLocation());
    }
    
    private JFileChooser fileChooser;
    protected final JFileChooser getFileChooser()
    {
        if (fileChooser == null)
            fileChooser = new JFileChooser();
        
        return fileChooser;
    }
    	    
    // Does not return until dialog is hidden or disposed of.
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
        else if(e.getSource() == btnClear) {
            txtCommand.setText("");
            return;
        }
        else if (e.getSource() == btnOK) {
            String cmd = txtCommand.getText().trim();
            
            if (cmd == null ||
                cmd.equals("") ||
                cmd.toLowerCase().equals(StampLayer.URL_TAG.toLowerCase())) 
            {
                // Clear custom browser command; will use default.
                Config.set(StampLayer.CFG_BROWSER_CMD_KEY, "");
                dialog.dispose();
                return;
            }

            // Verify basic command syntax requirements: The command must have
            // a program/command name as the first argument, and the URL_TAG placeholder
            // must appear somewhere in the command string after it.
            int urlIndex = cmd.toLowerCase().indexOf(StampLayer.URL_TAG.toLowerCase());
            StringTokenizer tokenizer = new StringTokenizer(cmd);
            
            if (tokenizer.countTokens() >= 2 &&
                !tokenizer.nextToken().equalsIgnoreCase(StampLayer.URL_TAG) &&
                urlIndex >= 0)
            {
                // Replace just the url placeholder with the proper case form.
                if (urlIndex >= 0) {
                    cmd = cmd.substring(0, urlIndex) + StampLayer.URL_TAG + cmd.substring(urlIndex + StampLayer.URL_TAG.length());
                    Config.set(StampLayer.CFG_BROWSER_CMD_KEY, cmd);
                }
            }
            else {
                String msg = "Command should have syntax similar to the following: \"mywebbrowser " + 
                             StampLayer.URL_TAG + "\", where " + StampLayer.URL_TAG + " is the placeholder for a webpage.  " +
                             "Other command arguments and any command/argument order is permitted, so " + 
                             "long as the webpage placeholder appears.";
                msg = Util.lineWrap(msg, 55);
                
                JOptionPane.showMessageDialog(
                                              Main.mainFrame,
                                              msg,
                                              "Browser Command Problem",
                                              JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            dialog.dispose();
            return;
        }
    }
}


