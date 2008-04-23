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

import java.awt.event.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.util.DebugLog;


/**
 * Control the Z-order of the selected feature.
 * 
 * @author James Winburn originally but vastly improved by Saadat Anwar.  MSFF-ASU 6/2006
 */
public class ZOrderMenu extends JMenu implements ActionListener {

    private static DebugLog log = DebugLog.instance();

    private FeatureCollection fc;

	JMenuItem bottomMenuItem = new JMenuItem("Send to Bottom");
	JMenuItem lowerMenuItem = new JMenuItem("Lower");
	JMenuItem raiseMenuItem = new JMenuItem("Raise");
	JMenuItem topMenuItem = new JMenuItem("Bring to Top");

	public ZOrderMenu(String title, FeatureCollection fc) {
		super(title);
		this.fc = fc;

		add(bottomMenuItem);
		add(lowerMenuItem);
		add(raiseMenuItem);
		add(topMenuItem);

		bindActionsToMenuItems();
	}

	private void bindActionsToMenuItems() {
		bottomMenuItem.addActionListener(this);
		lowerMenuItem.addActionListener(this);
		raiseMenuItem.addActionListener(this);
		topMenuItem.addActionListener(this);
    }

	public void actionPerformed(ActionEvent e) {
		List selected = FeatureUtil.getSelectedFeatures(fc);
		if (selected.size() == 0)
			return;
		Map feat2idx = FeatureUtil.getFeatureIndices(fc.getFeatures(), selected);
		int rows[] = new int[selected.size()];
		Iterator selIt = selected.iterator();
		for (int i = 0; i < rows.length; i++)
			rows[i] = ((Integer)feat2idx.get(selIt.next())).intValue();
		Arrays.sort(rows);

		if (e.getSource() == bottomMenuItem || e.getSource() == topMenuItem) {
			if (fc.move(rows, e.getSource() == topMenuItem)) {
				int[] newIdx = new int[rows.length];
				for (int i = 0; i < newIdx.length; i++)
					newIdx[i] = (e.getSource() == topMenuItem) ? i : fc.getFeatureCount() - 1 - i;
			}
		} else if (e.getSource() == lowerMenuItem
				|| e.getSource() == raiseMenuItem) {
			if (fc.move(rows, e.getSource() == raiseMenuItem ? -1 : 1)) {
				for (int i = 0; i < rows.length; i++)
					rows[i] += e.getSource() == raiseMenuItem ? -1 : 1;
			}
		} else {
			log.aprintln("Unknown source encountered in ZOrderMenu ActionListener.");
			return;
		}
	}
}
