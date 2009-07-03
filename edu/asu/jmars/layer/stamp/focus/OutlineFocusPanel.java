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
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import edu.asu.jmars.layer.stamp.StampLayer;
import edu.asu.jmars.layer.stamp.StampShape;

public class OutlineFocusPanel extends JPanel {

	JLabel totalRecordCnt;
	StampLayer myLayer;
	
	// creates and returns a default unfilled stamp panel.
	public OutlineFocusPanel(final StampLayer stampLayer, final StampTable table)
	{
		myLayer = stampLayer;
	    JPanel top = new JPanel();
	    JButton findStamp = new JButton("Find stamp...");
	    findStamp.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) 
            {
                String id = JOptionPane.showInputDialog(OutlineFocusPanel.this,
                    "Enter a stamp id:", "Find stamp...", JOptionPane.QUESTION_MESSAGE);

                if (id == null) {
                    return;
                }
                
                StampShape stamp = stampLayer.getStamp(id.trim());
                if (stamp!=null) {
                	stampLayer.clearSelectedStamps();
                    stampLayer.viewToUpdate.panToStamp(stamp);
                    return;
                }
                    
                JOptionPane.showMessageDialog(OutlineFocusPanel.this,
                                              "Can't find the stamp \"" + id
                                              + "\", are you sure\n"
                                              + "it meets your layer's selection criteria?",
                                              "Find stamp...",
                                              JOptionPane.ERROR_MESSAGE);
            }
		});

		top.add(findStamp);
		
	    final JFileChooser fc = new JFileChooser();
	    top.add(new JButton( new AbstractAction("Dump table to file...") {
	        public void actionPerformed(ActionEvent e){
	            File f;
	            do {
	                if (fc.showSaveDialog(OutlineFocusPanel.this)
	                        != JFileChooser.APPROVE_OPTION)
	                    return;
	                f = fc.getSelectedFile();
	            }
	            while( f.exists() && 
	                    JOptionPane.NO_OPTION == JOptionPane.showConfirmDialog(
	                    		OutlineFocusPanel.this,
                               "File already exists, overwrite?\n" + f,
                               "FILE EXISTS",
                               JOptionPane.YES_NO_OPTION,
                               JOptionPane.WARNING_MESSAGE
	                    )
	            );
	            try {
	                PrintStream fout =
	                    new PrintStream(new FileOutputStream(f));
	                
	                synchronized(table) {
	                    int rows = table.getRowCount();
	                    int cols = table.getColumnCount();
	                    
	                    // Output the header line
	                    for (int j=0; j<cols; j++)
	                        fout.print(table.getColumnName(j)
	                                   + (j!=cols-1 ? "\t" : "\n"));
	                    
	                    // Output the data
	                    for (int i=0; i<rows; i++)
	                        for (int j=0; j<cols; j++)
	                            fout.print(table.getValueAt(i, j)
	                                       + (j!=cols-1 ? "\t" : "\n"));
	                }
	            } 
	            catch(FileNotFoundException ex){
	                JOptionPane.showMessageDialog(
	                		OutlineFocusPanel.this,	                                              
	                		"Unable to open file!\n" + f,
                          "FILE OPEN ERROR",
                          JOptionPane.ERROR_MESSAGE
	                );
	            }
	        }
	    }));
	    
	    
	    JPanel bot = new JPanel();
	    bot.setLayout(new FlowLayout());
	    
	    bot.add(new JLabel("Total records in current view: "));
	    totalRecordCnt = new JLabel("0");
	    bot.add(totalRecordCnt);
	    
	    setLayout(new BorderLayout());
	    add(top,    BorderLayout.NORTH);
	    add(new JScrollPane(table), BorderLayout.CENTER);
	    add(bot, BorderLayout.SOUTH);	    
	}
	
	public void dataRefreshed() {
		updateRecordCount(myLayer.viewToUpdate.stamps.length);
	}

	public void updateRecordCount(int newCnt) {
		totalRecordCnt.setText(""+newCnt);
	}
	
}
