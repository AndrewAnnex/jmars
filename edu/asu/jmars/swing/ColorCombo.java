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
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

public class ColorCombo
 extends JComboBox
 {
	private static final Color clear = new Color(0,0,0,0);
	private static final Color colorList[] =
	{
		Color.blue,
		Color.cyan,
		Util.darkGreen,
		Color.green,
		Color.yellow,
		Color.orange,
		Color.red,
		Util.darkRed,
		Color.magenta,
		Util.purple,
		Color.pink,

		Color.white,
		Color.lightGray,
		Color.gray,
		Color.darkGray,
		Color.black
	};
	private static final Border clearBorder =
	BorderFactory.createLineBorder(clear, 2);

	private int alpha = 255;
	int width;
	String widthSpaces;

	/**
	 ** Constructs a ColorCombo with a default width. Same as calling
	 ** {@link #ColorCombo(int) ColorCombo(3)}.
	 **/
	public ColorCombo()
	 {
		this(3);
	 }

	/**
	 ** Constructs a ColorCombo with a given starting color value.
	 **/
	public ColorCombo(Color initial)
	 {
		this();
		setColor(initial);
	 }

	/**
	 ** Constructs a ColorCombo with a specific width. Note that the
	 ** width refers to the width of the colored area of the combobox,
	 ** and that <code>width=0</code> still results in some visible
	 ** width.
	 **
	 ** @param width The minimum "width" of the combo box, which is
	 ** equivalent to a JLabel (in its default font) with
	 ** <code>width</count> many space characters.
	 **/
	public ColorCombo(int width)
	 {
		this.width = width;
		populate();
	 }

	/**
	 ** Constructs a ColorCombo that can mirror another ColorCombo.
	 ** The width is picked up from the given combo. The new combo
	 ** will contain a selectable item that will put the combo into a
	 ** mode such that <code>this.getColor() =
	 ** other.getColor()</code>.
	 **/
	public ColorCombo(ColorCombo other)
	 {
		this.width = other.width;
		addItem(other);
		populate();
	 }

	private void populate()
	 {
		for(int i=0; i<colorList.length; i++)
			addItem(colorList[i]);
		widthSpaces = spaces(width);
		setMaximumRowCount(getItemCount());
		setRenderer(new Renderer());
	 }

	public boolean isLinked()
	 {
		return  getSelectedItem() instanceof ColorCombo;
	 }

	public Color getColor()
	 {
		Object item = getSelectedItem();

		if(item instanceof ColorCombo)
			return  ( (ColorCombo) item ).getColor();

		if(alpha == 255)
			return  (Color) item;

		return  Util.alpha((Color) item, alpha);
	 }

	public void setColor(Color c)
	 {
		alpha = c.getAlpha();
		if(c.getAlpha() != 255)
			c = new Color(c.getRGB()); // takes alpha out
		setSelectedItem(c);
	 }

	public void setAlpha(int alpha)
	 {
		this.alpha = alpha;
	 }

	public int getAlpha()
	 {
		return  alpha;
	 }

	private static String spaces(int n)
	 {
		String x = "";
		for(int i=0; i<n; i++)
			x += ' ';
		return  x;
	 }

	private static Map bordersByColor = new HashMap();
	private static Border getBorderForColor(Color c)
	 {
		Border b = (Border) bordersByColor.get(c);
		if(b == null)
		 {
			Color bc = Util.getB(c)<0.5 ? Color.white : Color.black;
			bordersByColor.put(c, b = BorderFactory.createLineBorder(bc, 2));
		 }
		return  b;
	 }

	// Used internally to render each colored cell in the combo box.
    private final class Renderer
	 extends JLabel
	 implements ListCellRenderer
	 {
		Renderer()
		 {
			setOpaque(true);
		 }

		// Need to be disabled if we want to render these things right.
		public void setBackground(Color col) { }
		public void setForeground(Color col) { }

        public Component getListCellRendererComponent(
			JList list,
			Object value,
			int index,
			boolean isSelected,
			boolean cellHasFocus)
		 {
			// A list item that mirrors another ColorCombo's value
			if(value instanceof ColorCombo)
			 {
				value = ( (ColorCombo) value ).getColor();
				setText("Linked");
			 }
			else
				setText(widthSpaces);

			setBorder(isSelected
					  ? getBorderForColor((Color)value)
					  : clearBorder);

			// Need the 'super' reference, to invoke the non-disabled version.
			super.setBackground( (Color) value );
//			super.setForeground(opaque);

			return this;
		 }

		//////////////////////////////////////////////////////////////////////
		// The following are overridden for performance reasons,
		// copy+pasted from DefaultCellRenderer's source code.
		//////////////////////////////////////////////////////////////////////
		public void validate() {}
		public void revalidate() {}
		public void repaint(long tm, int x, int y, int width, int height) {}
		public void repaint(Rectangle r) {}
		public void firePropertyChange(String propertyName, byte oldValue, byte newValue) {}
		public void firePropertyChange(String propertyName, char oldValue, char newValue) {}
		public void firePropertyChange(String propertyName, short oldValue, short newValue) {}
		public void firePropertyChange(String propertyName, int oldValue, int newValue) {}
		public void firePropertyChange(String propertyName, long oldValue, long newValue) {}
		public void firePropertyChange(String propertyName, float oldValue, float newValue) {}
		public void firePropertyChange(String propertyName, double oldValue, double newValue) {}
		public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
		protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
		   // Strings get interned...
		   if (propertyName=="text")
			   super.firePropertyChange(propertyName, oldValue, newValue);
		}

	 }

	public static void main(String[] av)
	 {
		JFrame f = new JFrame("Color Combo");
		f.getContentPane().add(
			new ColorCombo(new Color(255, 255, 0, 122))
			 {{
				addActionListener(
					new ActionListener()
					 {
						public void actionPerformed(ActionEvent e)
						 {
							System.out.println(getColor());
							System.out.println(getColor().getAlpha());
							System.out.println(getAlpha());
						 }
					 }
					);
			 }}
			);
		f.pack();
		f.setVisible(true);
	 }
 }
