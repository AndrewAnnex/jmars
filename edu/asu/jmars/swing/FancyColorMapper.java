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


package edu.asu.jmars.swing;

import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.event.*;

/**
 ** Same as {@link ColorMapper}, with the addition of buttons for
 ** copy, paste, swap, and auto.
 **/
public class FancyColorMapper extends ColorMapper
 {
	private static final DebugLog log = DebugLog.instance();

	protected JPanel pnlButtons;
	protected JButton btnCopy;
	protected JButton btnPaste;
	protected JButton btnSwap;
	protected ColorMapper.State colClipboard;

	public JButton btnAuto;

	public FancyColorMapper()
	 {
		this(new int[] { 0, 255 },
			 new Color[] { Color.black, Color.white }
			);
	 }

	public FancyColorMapper(int[] values, Color[] colors)
	 {
		super(values, colors);
	 }

	public final void setEnabled(boolean e)
	 {
		super.setEnabled(e);
		btnCopy.setEnabled(e);
		btnPaste.setEnabled(e);
		btnSwap.setEnabled(e);
		btnAuto.setEnabled(e);
	 }

	protected BufferedImage getImageForAuto()
	 {
		return  null;
	 }

	private int[] getHistogram(BufferedImage img)
	 {
		int[] hist = new int[256];
		for(int x=0; x<img.getWidth(); x++)
			for(int y=0; y<img.getHeight(); y++)
			 {
				int p = img.getRGB(x, y);
				int r = p       &  0xFF;
				int g = p >> 4  &  0xFF;
				int b = p >> 8  &  0xFF;
				if(p >> 12 != 0)
					++hist[ (r+g+b) / 3 & 0xFF ];
			 }

		return  hist;
	 }

	protected final void extraInit()
	 {
		colClipboard = getState();

		btnCopy = new JButton(
			new AbstractAction("Copy")
			 {
				public void actionPerformed(ActionEvent e)
				 {
					colClipboard = getState();
				 }
			 }
			);
		btnCopy.setToolTipText(
			"Copies the current colors into a private clipboard.");

		btnPaste = new JButton(
			new AbstractAction("Paste")
			 {
				public void actionPerformed(ActionEvent e)
				 {
					setState(colClipboard);
				 }
			 }
			);
		btnPaste.setToolTipText(
			"Pastes the private clipboard into the current colors.");

		btnSwap = new JButton(
			new AbstractAction("Swap")
			 {
				public void actionPerformed(ActionEvent e)
				 {
					ColorMapper.State temp = getState();
					setState(colClipboard);
					colClipboard = temp;
				 }
			 }
			);
		btnSwap.setToolTipText(
			"Swaps the private clipboard and the current colors.");

		btnAuto = new JButton(
			new AbstractAction("Auto")
			 {
				public void actionPerformed(ActionEvent e)
				 {
					BufferedImage img = getImageForAuto();
					if(img == null)
						return;

					int[] hist = getHistogram(img);

/*
 * THE NEW ALGORITHM: Scales the slider to the minimum and maximum DN,
 * throwing out the outer 1% of the pixels.
 */

					int pixels = img.getWidth() * img.getHeight();

					// Find the min DN (ignoring the first 1% of pixels)
					int ignored = 0;
					int lo = 0;
					do
						ignored += hist[lo++];
					while(ignored * 100 < pixels);

					// Find the max DN (ignoring the first 1% of pixels)
					ignored = 0;
					int hi = 255;
					do
						ignored += hist[hi--];
					while(ignored * 100 < pixels);

/* THE LEGACY ALGORITHM: Find the most-common DN value. Then find the
 * nearest DNs that are 5% as common, and use them as the min/max DN
 * for the scaler slider.

					// Find the peak
					int top = 0;
					for(int i=0; i<256; i++)
						if(hist[i] > hist[top])
							top = i;

					// Find the hi boundary: the next time we hit 5% peak
					int hi = top;
					while(hi < 255  &&  hist[hi]*20 > hist[top])
						++hi;

					// Find the lo boundary: the prior time we hit 5% peak
					int lo = top;
					while(lo > 0  &&  hist[lo]*20 > hist[top])
						--lo;
*/
					rescaleTo(lo, hi);
				 }
			 }
			);
		btnAuto.setToolTipText(
			"When possible, guesses proper contrast stretch settings.");

		pnlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER));
		pnlButtons.add(btnCopy);
		pnlButtons.add(btnPaste);
		pnlButtons.add(btnSwap);
		pnlButtons.add(btnAuto);
		pnlButtons.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

		add(pnlButtons, BorderLayout.SOUTH);
	 }
 }
