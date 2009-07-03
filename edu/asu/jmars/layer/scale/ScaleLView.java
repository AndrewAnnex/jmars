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


package edu.asu.jmars.layer.scale;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import edu.asu.jmars.Main;
import edu.asu.jmars.graphics.FontRenderer;
import edu.asu.jmars.graphics.JFontChooser;
import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.WrappedMouseEvent;
import edu.asu.jmars.layer.Layer.LView;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.Util;

public class ScaleLView extends Layer.LView implements MouseMotionListener, MouseListener {
	private static final long serialVersionUID = 4808626677999276798L;
	private final ScaleParameters parms;
	private Point mouseOffset = null;
	private Rectangle lastBox;
	
	public ScaleLView(boolean main, Layer layerParent, ScaleParameters params) {
		super(layerParent);
		setBufferCount(1);
		this.parms = params;
		addMouseListener(this);
		addMouseMotionListener(this);
	}
	
	private static Map<Double,String> siUnits = new LinkedHashMap<Double,String>();
	private static Map<Double,String> usUnits = new LinkedHashMap<Double,String>();
	static {
		siUnits.put(1d, "km");
		siUnits.put(1000d, "m");
		siUnits.put(1000*100d, "cm");
		usUnits.put(0.621371192, "mi");
		usUnits.put(3280.83989376, "ft");
	}
	
	private FontRenderer getFontBox(String label) {
		FontRenderer fr = new FontRenderer(parms.labelFont, parms.fontOutlineColor, parms.fontFillColor);
		fr.setLabel(label);
		fr.setAntiAlias(true);
		return fr;
	}
	
	public void paintComponent(Graphics g) {
		Graphics2D g2 = null;
		try {
			g2 = (Graphics2D) getOffScreenG2Direct(0);
			// Don't paint if we're on the panner, or not ready to paint
			if (getChild() == null || g2 == null) {
				return;
			}
			
			int rulerWidth = (getWidth()-2)*parms.width/100;
			int rulerHeight = Math.min(10, rulerWidth/10);
			int tickHeight = rulerHeight * 3 / 2;
			
			Map<Double,String> units = (parms.isMetric ? siUnits : usUnits);
			String scale = units.values().iterator().next();
			int value = 1234;
			
			for (int pass = 0; pass < 2; pass++) {
				// each pass through, we calculate a number of positional
				// arguments based on the rulerWidth, scale, and value to
				// display
				FontRenderer fr = getFontBox(MessageFormat.format("{0,number,#} {1}", value, scale));
				
				// box to draw in will be at least as wide as it takes to draw our test label,
				// and as tall as the font box, the ruler, and some space between them
				Dimension font = fr.getSize();
				Dimension ruler = new Dimension(rulerWidth, Math.max(rulerHeight, tickHeight));
				int separatorHeight = ruler.height/2;
				Dimension box = new Dimension(
					Math.max(font.width, ruler.width),
					ruler.height + separatorHeight + font.height);
				
				// confine the offset to keep the corner closest to the edge within the screen
				int constraintX = getWidth()-box.width;
				parms.offsetX = Math.max(-constraintX, Math.min(constraintX, parms.offsetX));
				int constraintY = getHeight()-box.height;
				parms.offsetY = Math.max(-constraintY, Math.min(constraintY, parms.offsetY));
				
				int left, right, bottom, top;
				if (parms.offsetX < 0) {
					right = getWidth() + parms.offsetX;
					left = right - box.width;
				} else {
					left = parms.offsetX;
					right = left + box.width;
				}
				if (parms.offsetY < 0) {
					bottom = getHeight() + parms.offsetY;
					top = bottom - box.height;
				} else {
					top = parms.offsetY;
					bottom = top + box.height;
				}
				lastBox = new Rectangle((int)left, (int)top, (int)(right-left), (int)(bottom-top+1));
				
				double km = 0;
				final int count = 10;
				HVector prior = null;
				for (int i = 0; i < count; i++) {
					int x = (int)Math.round(lastBox.getMinX() + i*lastBox.width/(count-1));
					HVector current = HVector.intersectMars(HVector.ORIGIN, getProj().screen.toHVector(x, getHeight()/2));
					if (prior != null) {
						km += current.sub(prior).norm();
					}
					prior = current;
				}
				
				if (pass == 0) {
					// the first pass through, we update scale, value, and rulerWidth
					for (double s: units.keySet()) {
						scale = units.get(s);
						double base = km * s;
						double trim = Math.pow(10, Math.floor(Math.log(base)/Math.log(10)) - 1);
						int smaller = (int)(Math.floor(base/trim) * trim);
						int larger = (int)(Math.ceil(base/trim) * trim);
						double largeWidth = larger / base * lastBox.width;
						if (parms.offsetX < 0) {
							if (lastBox.getMaxX() - largeWidth < 0) {
								value = smaller;
							} else {
								value = larger;
							}
						} else {
							if (lastBox.getMinX() + largeWidth >= getWidth()-1) {
								value = smaller;
							} else {
								value = larger;
							}
						}
						rulerWidth = (int)Math.ceil(value / base * lastBox.width);
						if (value >= 1) {
							break;
						}
					}
				} else {
					// the second pass through, the positions are updated with
					// new scale/value/rulerWidth values, so draw
					clearOffScreen();
					
					// draw ruler
					g2.setColor(parms.barColor);
					g2.fillRect((int)left, (int)top + tickHeight - rulerHeight, (int)(right-left), rulerHeight);
					
					// draw ticks
					g2.setColor(parms.tickColor);
					for (int i = 0; i < parms.numberOfTicks && parms.numberOfTicks >= 2; i++) {
						int x = lastBox.x + lastBox.width * i/(parms.numberOfTicks-1) - 1;
						g2.fillRect(x, (int)top, 2, tickHeight);
					}
					
					if (Util.between(-90, getProj().screen.toWorld(getWidth()/2, getHeight()/2).getY(), 90)) {
						// draw font
						g2.translate(left, bottom - font.height);
						fr.paintComponent(g2);
					} else {
						clearOffScreen();
						g2.setColor(parms.barColor);
						g2.draw(lastBox);
					}
				}
			}
		} finally {
			// ensure that no g2's are left around
			if (g2 != null) {
				g2.dispose();
			}
			
			// call the LView painter to stack the back buffers together
			super.paintComponent(g);
		}
	}
	
	public String getName() {
		return "Map Scalebar";
	}

	public FocusPanel getFocusPanel() {
		if (focusPanel == null)
			focusPanel = new DelayedFocusPanel() {
			public JPanel createFocusPanel() {
				JPanel spacing = new JPanel(new BorderLayout());
				spacing.add(new JScrollPane(new ScalePanel()), BorderLayout.CENTER);
				return spacing;
			}
		};
		return focusPanel;
	}
	
	private enum UnitStrings {
		Metric("SI (km/m)", true),
		Imperial("Imperial (mi/ft)", false);
		
		private final String text;
		private final boolean metric;
		public String toString() {
			return text;
		}
		public boolean isMetric() {
			return metric;
		}
		UnitStrings(String text, boolean metric) {
			this.text = text;
			this.metric = metric;
		}
	}
	
	private class ScalePanel extends JPanel implements ActionListener, ChangeListener {
		private static final long serialVersionUID = 3142320762007483904L;
		JButton modFont;
		JButton barColorButton;
		JButton tickColorButton;
		JComboBox units;
		JSlider tickNumberSlider;
		JSlider widthSlider;
		FontRenderer frSample;
		
		private GridBagConstraints gbc(int row, boolean isData) {
			int px = 4, py = 4;
			Insets in = new Insets(py,px,py,px);
			return new GridBagConstraints(isData?1:0, row, 1,1, isData?1:0, 0, GridBagConstraints.WEST, isData?GridBagConstraints.HORIZONTAL:GridBagConstraints.NONE,in,px,py);
		}
		
		public ScalePanel() {
			setLayout(new GridBagLayout());
			setBorder(new EmptyBorder(10, 5, 5, 5));
			
			units = new JComboBox(UnitStrings.values());
			units.addActionListener(this);
			add(new JLabel("Units"), gbc(0, false));
			add(units, gbc(0, true));
			
			modFont = new JButton("Choose Font...");
			modFont.addActionListener(this);
			add(modFont, gbc(1, false));
			frSample = new FontRenderer(parms.labelFont, parms.fontOutlineColor, parms.fontFillColor);
			frSample.setLabel("1 2 3 4 km mi");
			frSample.setAntiAlias(true);
			frSample.setFont(getFont().deriveFont(100f));
			add(frSample, gbc(1, true));
			
			add(new JLabel("Tick Count"), gbc(2, false));
			tickNumberSlider = new JSlider(0, 10, parms.numberOfTicks);
			tickNumberSlider.setSnapToTicks(true);
			tickNumberSlider.setPaintLabels(true);
			tickNumberSlider.setMajorTickSpacing(1);
			tickNumberSlider.setPaintTicks(true);
			tickNumberSlider.addChangeListener(this);
			add(tickNumberSlider, gbc(2, true));
			
			add(new JLabel("Width %"), gbc(3, false));
			widthSlider = new JSlider(10, 100, 30);
			widthSlider.setPaintTicks(true);
			widthSlider.setPaintLabels(true);
			widthSlider.setMajorTickSpacing(10);
			widthSlider.setPaintTicks(true);
			widthSlider.addChangeListener(this);
			add(widthSlider, gbc(3, true));
			
			add(new JLabel("Tick Color"), gbc(4, false));
			tickColorButton = new JButton(" ");
			tickColorButton.setBackground(parms.tickColor);
			tickColorButton.addActionListener(this);
			add(tickColorButton, gbc(4,true));
			
			add(new JLabel("Bar Color"), gbc(5, false));
			barColorButton = new JButton(" ");
			barColorButton.setBackground(parms.barColor);
			barColorButton.addActionListener(this);
			add(barColorButton, gbc(5, true));
			
			GridBagConstraints endCap = gbc(6, true);
			endCap.weighty = 1;
			add(new JLabel(""), endCap);
		}

		public void actionPerformed(ActionEvent e) {
			if (e.getSource() == modFont) {
				JFontChooser jfc = new JFontChooser(parms.labelFont,
						parms.fontOutlineColor, parms.fontFillColor);
				if (null != jfc.showDialog()) {
					parms.labelFont = jfc.selectedFont;
					parms.fontFillColor = jfc.fontColor;
					parms.fontOutlineColor = jfc.outlineColor;
					frSample.setOutlineColor(parms.fontOutlineColor);
					frSample.setFontColor(parms.fontFillColor);
					frSample.setLabelFont(parms.labelFont);
					ScaleLView.this.repaint();
				}
			} else if (e.getSource() == tickColorButton) {
				Color newColor = JColorChooser.showDialog(Main.mainFrame,
						"Choose a Tick color", parms.tickColor);
				if (newColor != null) {
					parms.tickColor = newColor;
					tickColorButton.setBackground(parms.tickColor);
					ScaleLView.this.repaint();
				}
			} else if (e.getSource() == barColorButton) {
				Color newColor = JColorChooser.showDialog(Main.mainFrame,
						"Choose a bar color", parms.barColor);
				if (newColor != null) {
					parms.barColor = newColor;
					barColorButton.setBackground(parms.barColor);
					ScaleLView.this.repaint();
				}
			} else if (e.getSource() == units) {
				JComboBox object = (JComboBox) e.getSource();

				boolean last = parms.isMetric;
				parms.isMetric = UnitStrings.values()[object.getSelectedIndex()].isMetric();
				if (last != parms.isMetric) {
					ScaleLView.this.repaint();
				}
			}
		}
		
		public void stateChanged(ChangeEvent e) {
			if (e.getSource() == tickNumberSlider) {
				parms.numberOfTicks = tickNumberSlider.getValue();
			} else if (e.getSource() == widthSlider) {
				parms.width = widthSlider.getValue();
			}
			ScaleLView.this.repaint();
		}
	}
	
	protected LView _new() {
		return new ScaleLView(false, getLayer(), parms);
	}
	
	protected Object createRequest(Rectangle2D where) {
		repaint();
		return null;
	}
	
	public void receiveData(Object layerData) {

	}
	
	public void mouseDragged(MouseEvent e) {
		if (lastBox != null) {
			Point p = e instanceof WrappedMouseEvent ? ((WrappedMouseEvent)e).getRealPoint() : e.getPoint();
			
			Rectangle box = new Rectangle(lastBox);
			box.x = p.x - mouseOffset.x;
			box.y = p.y - mouseOffset.y;
			box = box.intersection(new Rectangle(0,0,getWidth()-1,getHeight()-1));
			
			if (box.x < getWidth() - box.getMaxX()) {
				parms.offsetX = box.x;
			} else {
				parms.offsetX = (int)(box.getMaxX() - getWidth());
			}
			if (box.y < getHeight() - box.getMaxY()) {
				parms.offsetY = box.y;
			} else {
				parms.offsetY = (int)(box.getMaxY() - getHeight());
			}
			
			repaint();
		}
	}
	
	public void mousePressed(MouseEvent e) {
		if (lastBox != null) {
			mouseOffset = e instanceof WrappedMouseEvent ? ((WrappedMouseEvent)e).getRealPoint() : e.getPoint();
			mouseOffset.x -= lastBox.x;
			mouseOffset.y -= lastBox.y;
		}
	}
	
	public void mouseReleased(MouseEvent e) {
		mouseOffset = null;
		repaint();
	}
	
	public void mouseMoved(MouseEvent e) {
		// not used
	}
	
	public void mouseClicked(MouseEvent e) {
		// not used
	}

	public void mouseEntered(MouseEvent e) {
		// not used
	}

	public void mouseExited(MouseEvent e) {
		// not used
	}
}
