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

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import edu.asu.jmars.layer.util.FileLogger;

public class LogViewer {
	private JDialog dlg;
	public JDialog getDialog() {
		return dlg;
	}
	public LogViewer(FileLogger logger) {
		String content = logger.getContent();

		JTextArea text = new JTextArea();
		text.setLineWrap(true);
		text.setText(content);
		text.setEditable(false);
		text.setFont(new Font("Monospaced", Font.PLAIN, 12));

		JScrollPane scroller = new JScrollPane(text);
		scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		int gap = 4;
		scroller.setBorder(new CompoundBorder(new EmptyBorder(gap,gap,gap,gap), scroller.getBorder()));

		String nowText = new SimpleDateFormat().format(new Date());
		dlg = new JDialog(Main.mainFrame, "Log content as of " + nowText, false);
		dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dlg.setSize(800, 600);

		JButton close = new JButton("Close");
		close.setMnemonic(KeyEvent.VK_C);
		close.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dlg.dispose();
			}
		});

		Box v = Box.createVerticalBox();
		v.add(Box.createVerticalStrut(gap));
		v.add(close);
		v.add(Box.createVerticalStrut(gap));

		Box h = Box.createHorizontalBox();
		h.add(Box.createGlue());
		h.add(v);
		h.add(Box.createHorizontalStrut(gap));

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(scroller, BorderLayout.CENTER);
		panel.add(h, BorderLayout.SOUTH);

		dlg.getContentPane().add(panel);
	}
}

