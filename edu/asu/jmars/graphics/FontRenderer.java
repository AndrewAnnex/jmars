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


package edu.asu.jmars.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import javax.swing.JLabel;

/**
 * @author npiace
 */
public class FontRenderer extends JLabel {

	private static final long serialVersionUID = 4951467629049657482L;
	private Font selectedFont = new Font("Tahoma", Font.PLAIN, 14);
	private String text = " ";
	private Color outlineColor;
	private Color fontColor;
	private int fontSize;
	private boolean antialias;

	// private AffineTransform atrans = new AffineTransform();;
	public FontRenderer() {
		// super();
		setDoubleBuffered(false);
		selectedFont = new Font("Tahoma", Font.PLAIN, 14);
		outlineColor = Color.black;
		fontColor = Color.white;
		fontSize = selectedFont.getSize();

		antialias = true;
		if (selectedFont == null) {
			System.err.println("ERROR font is null in empty constructor!!!");
		}
		updateSize();
	}

	/**
	 * @param font
	 * @param outline
	 * @param textColor
	 * 
	 */
	public FontRenderer(Font font, Color outline, Color textColor) {
		// super();
		if (font == null) {
			System.err.println("ERROR font is null in constructor!!!");
		}
		setDoubleBuffered(false);
		selectedFont = font;
		outlineColor = outline;
		fontColor = textColor;
		fontSize = selectedFont.getSize();
		antialias = false;

		updateSize();
	}

	private void updateSize() {
		if (selectedFont == null) {
			System.err.println("ERROR font is null in updateSize!!!");
		}
		FontMetrics theseFontMetrics = getFontMetrics(selectedFont);
		int width = theseFontMetrics.stringWidth(text) + 4;
		int height = theseFontMetrics.getHeight() + 4;
		Dimension textDimensions = new Dimension(width, height);
		setPreferredSize(textDimensions);
		setMinimumSize(textDimensions);
		setSize(textDimensions);
		revalidate();
	}

	/**
	 * @param color
	 */
	public void setFontColor(Color color) {
		fontColor = color;
		repaint();
	}

	public void setTransform(AffineTransform transorm) {

	}

	/**
	 * @param aa
	 */
	public void setAntiAlias(boolean aa) {
		antialias = aa;
		repaint();
	}

	/**
	 * @param color
	 */
	public void setOutlineColor(Color color) {
		outlineColor = color;
		repaint();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.swing.JComponent#setFont(java.awt.Font)
	 */
	public void setLabelFont(Font font) {
		if (font != null) {
			selectedFont = font;
			updateSize();
		}
	}

	public void setLabel(String string) {
		text = string;
		updateSize();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
	 */
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D g2 = (Graphics2D) g.create();
		try {
			FontRenderContext frc = g2.getFontRenderContext();
			TextLayout tl = new TextLayout(text, selectedFont, frc);
			Shape outline = tl.getOutline(null);
			Stroke defaultStroke = g2.getStroke();
			if (antialias) {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
			}

			if (outlineColor != null && fontColor != null) {// draw the
				// wider stroke if we are drawing outline and filling
				if (fontSize >= 20) {
					g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND,
							BasicStroke.JOIN_ROUND));
				} else if (fontSize < 12) {
					g2.setStroke(new BasicStroke((float) 1.65,
							BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				} else {
					g2.setStroke(new BasicStroke((float) 2.25,
							BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				}

			}
			
			Rectangle2D bounds = outline.getBounds2D();
			g2.translate(-bounds.getMinX()+2, -bounds.getMinY()+2);
			
			if (outlineColor != null) {// draw the outline
				g2.setColor(outlineColor);
				g2.draw(outline);
			}
			
			if (fontColor != null) {// fill the outline
				g2.setStroke(defaultStroke);
				g2.setColor(fontColor);
				g2.fill(outline);
			}
		} finally {
			g2.dispose();// dispose
		}
	}
}
