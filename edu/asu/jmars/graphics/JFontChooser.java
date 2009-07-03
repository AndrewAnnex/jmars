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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * This is a swing component that allows the user to pick a font type, size,
 * style, and color.
 */

public class JFontChooser extends JComponent {

    /**
     * FontBox is used to draw the preview of the text using the Graphics2D
     * renderer.
     */
    private static final long serialVersionUID = -3593967768352881673L;

    /**
     * Adds an action listener for when the dialog is closed, handles the actual
     * return of this super class.
     */
    class FontTracker implements ActionListener {

	JFontChooser chooser;
	Font font;

	public FontTracker(JFontChooser c) {

	    chooser = c;
	}

	public void actionPerformed(ActionEvent e) {

	    font = chooser.getSelectedFont();
	}

	/**
	 * @return returns the font that is selected, if dialog is canceled,
	 *         then returns the default font.
	 */
	public Font getFont() {

	    if (font == null) {
		return defaultFont;
	    }
	    return font;
	}
    }

    /**
     * selectedFont is the font that is selected in this instance of
     * JFontChooser fontColor is the font color that is chosen by the user in
     * this instance of JFontChooser outlineColor is the outline color that is
     * chosen by the user in this instance of JFontChooser
     */
    public Font selectedFont;
    public Color fontColor, outlineColor;
    private Color lastOutline, lastFont;
    private String[] sizes = new String[] { "8", "10", "12", "14", "16", "18",
	    "20", "22", "24", "30", "36", "48", "72" };
    private String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment()
	    .getAvailableFontFamilyNames();
    private String[] fontStyles = new String[] { "Bold", "Italic", "Plain" };
    private FontRenderer graphicsPane;
    private JButton okButton, cancelButton, fontColorButton,
	    outlineColorButton;
    private int fontSize, fontStyle;
    private String fontName;
    private JDialog dialog, fontDialog;
    private JComboBox fontComboBox, sizeComboBox, fontStyleComboBox;
    private JCheckBox fillOnlyCheckbox, outlineOnlyCheckbox;
    private boolean outlineEnabled;
    private JScrollPane jsp;
    private JLabel outlineLabel, fillLabel;
    private Font defaultFont = new Font("Serif", Font.BOLD, 12);
    private boolean wasCanceled = false;

    /**
     * Same as JFontChooser(true)
     */
    public JFontChooser() {

	this(true);
    }

    /**
     * @param initialFont
     * @param outline
     * @param fill
     */
    public JFontChooser(Font initialFont, Color outline, Color fill) {

	selectedFont = initialFont;
	defaultFont = initialFont;
	fontColor = fill;
	lastFont = fill;
	outlineColor = outline;
	lastOutline = outline;
	fontSize = initialFont.getSize();
	fontName = initialFont.getFamily();
	fontStyle = initialFont.getStyle();
	graphicsPane = new FontRenderer(selectedFont, outlineColor, fontColor);
	graphicsPane.setLabel("AaBbCcDd-1234");
	graphicsPane.setAntiAlias(true);
	outlineEnabled = true;

    }

    /**
     * @param initialFont
     * @param fill
     */
    public JFontChooser(Font initialFont, Color fill) {

	this(false);

    }

    /**
     * @param enableOutline
     *            Set if user should be able to select a font outline
     */
    public JFontChooser(boolean enableOutline) {

	// instantiate the local variables
	selectedFont = new Font("Serif", Font.BOLD, 12);
	fontColor = Color.white;
	lastFont = Color.white;
	outlineColor = Color.black;
	lastOutline = Color.black;
	fontSize = 12;
	fontName = "Serif";
	fontStyle = Font.BOLD;
	if (!enableOutline) {
	    outlineColor = null;
	    lastOutline = null;
	}
	graphicsPane = new FontRenderer(selectedFont, outlineColor, fontColor);
	graphicsPane.setLabel("AaBbCcDd-1234");
	graphicsPane.setAntiAlias(true);
	outlineEnabled = enableOutline;
    }

    /**
     * Handles the actual construction of the dialog
     * 
     * @param ok
     *            The listener that should be used on the user clicking the "OK"
     *            button (FontListener).
     * @return returns the dialog to the showdialog() method
     */
    private JDialog createDialog(ActionListener ok) {

	dialog = new JDialog();
	dialog.setModal(true);// VERY IMPORTANT, this prevents the calling
	// dialog from continuing until we're done
	dialog.setTitle("Font Picker");
	Toolkit toolkit = Toolkit.getDefaultToolkit();
	Dimension scrnsize = toolkit.getScreenSize();
	dialog.setLocation((int) scrnsize.getWidth() / 5, (int) scrnsize.getHeight() / 3); // move me to a useful position on the screen

	JPanel masterPanel = new JPanel(new BorderLayout());
	masterPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
	BorderLayout bl = new BorderLayout();

	dialog.setLayout(bl);
	sizeComboBox = new JComboBox(sizes);
	fontComboBox = new JComboBox(fonts);
	GridLayout fl = new GridLayout(2, 2);
	fl.setVgap(2);
	fl.setHgap(2);
	JPanel colorPanel = new JPanel();
	JPanel colorPickerPanel1 = new JPanel(new GridLayout(1, 2));
	JPanel colorPickerPanel2 = new JPanel(new GridLayout(1, 2));
	colorPanel.add(colorPickerPanel1);
	colorPanel.add(colorPickerPanel2);
	colorPanel.setLayout(fl);
	fillLabel = new JLabel("Font Color ");
	colorPickerPanel1.add(fillLabel);
	fontColorButton = new JButton(" ");
	if (outlineEnabled) {
	    outlineColorButton = new JButton(" ");
	    outlineColorButton.setBackground(outlineColor);
	}
	fontColorButton.setBackground(fontColor);
	colorPickerPanel1.add(fontColorButton);
	if (outlineEnabled) {
	    outlineLabel = new JLabel("Outline Color ");
	    colorPickerPanel2.add(outlineLabel);
	    colorPickerPanel2.add(outlineColorButton);
	}
	ActionListener fontColorListener = new ActionListener() {

	    public void actionPerformed(ActionEvent e) {

		// show color dialog
		Color newColor = JColorChooser.showDialog((Component) e.getSource(), "Pick A Color", fontColor);
		if (newColor != null) {
			fontColor = newColor;
			fontColorButton.setBackground(fontColor);
			graphicsPane.setFontColor(fontColor);
			lastFont = fontColor;
		}
	    }
	};
	if (outlineEnabled) {
	    ActionListener outlineColorListener = new ActionListener() {

		public void actionPerformed(ActionEvent e) {

		    // show color dialog
		    Color newColor = JColorChooser.showDialog((Component) e
			    .getSource(), "Pick A Color", outlineColor);
		    if (newColor != null) {
		    	outlineColor = newColor;
			    outlineColorButton.setBackground(outlineColor);
			    graphicsPane.setOutlineColor(outlineColor);
			    lastOutline = outlineColor;
		    }
		}
	    };
	    outlineColorButton.addActionListener(outlineColorListener);
	}
	fontColorButton.addActionListener(fontColorListener);
	// JPanel checkboxPanel = new JPanel(new FlowLayout());
	// colorPanel.add(checkboxPanel);
	fillOnlyCheckbox = new JCheckBox("Fill");
	fillOnlyCheckbox.setHorizontalAlignment(SwingConstants.RIGHT);
	if (outlineEnabled) {
	    outlineOnlyCheckbox = new JCheckBox("Outline");
	    fillOnlyCheckbox.setSelected(true);
	    outlineOnlyCheckbox.setSelected(true);
	    colorPanel.add(fillOnlyCheckbox);
	    colorPanel.add(outlineOnlyCheckbox);
	    ActionListener cbListener = new ActionListener() {

		public void actionPerformed(ActionEvent e) {

		    if (fillOnlyCheckbox.isSelected()
			    && outlineOnlyCheckbox.isSelected()) {
			if (fontColor == null) {
			    fontColor = lastFont;
			}
			if (outlineColor == null) {
			    outlineColor = lastOutline;
			}
			fontColorButton.setVisible(true);
			outlineColorButton.setVisible(true);
			fillLabel.setVisible(true);
			outlineLabel.setVisible(true);
		    } else if (fillOnlyCheckbox.isSelected()
			    && !outlineOnlyCheckbox.isSelected()) {
			if (fontColor == null) {
			    fontColor = lastFont;
			}
			fontColorButton.setVisible(true);
			outlineColorButton.setVisible(false);
			fillLabel.setVisible(true);
			outlineLabel.setVisible(false);
			outlineColor = null;
		    } else if (!fillOnlyCheckbox.isSelected()
			    && outlineOnlyCheckbox.isSelected()) {
			if (outlineColor == null) {
			    outlineColor = lastOutline;
			}
			fontColorButton.setVisible(false);
			outlineColorButton.setVisible(true);
			fillLabel.setVisible(false);
			outlineLabel.setVisible(true);
			fontColor = null;
		    } else if (!fillOnlyCheckbox.isSelected()
			    && !outlineOnlyCheckbox.isSelected()) {
			fillOnlyCheckbox.setSelected(true);
			outlineOnlyCheckbox.setSelected(true);
			fontColor = lastFont;
			outlineColor = lastOutline;
			fontColorButton.setVisible(true);
			outlineColorButton.setVisible(true);
			fillLabel.setVisible(true);
			outlineLabel.setVisible(true);
		    }
		    graphicsPane.setOutlineColor(outlineColor);
		    graphicsPane.setFontColor(fontColor);
		}
	    };
	    fillOnlyCheckbox.addActionListener(cbListener);
	    outlineOnlyCheckbox.addActionListener(cbListener);
	}
	GridBagLayout fontBorderLayout = new GridBagLayout();

	GridBagConstraints gbc = new GridBagConstraints();
	JPanel tempPanel = new JPanel(new BorderLayout());
	BorderLayout southBorderLayout = new BorderLayout();
	sizeComboBox.setSelectedIndex(2);// set the selected size to 12pt
	JPanel fontPanel = new JPanel(fontBorderLayout);
	tempPanel.add(colorPanel, BorderLayout.NORTH);
	tempPanel.add(fontPanel, BorderLayout.CENTER);
	gbc.insets = new Insets(2, 2, 4, 2);
	fontPanel.add(sizeComboBox, gbc);
	JPanel southPanel = new JPanel(southBorderLayout);
	fontStyleComboBox = new JComboBox(fontStyles);
	fontPanel.add(fontStyleComboBox, gbc);
	fontPanel.add(fontComboBox, gbc);
	int i = 0;
	int selectedIndex = 0;

	for (String value : fonts) {
	    if (value.equals(defaultFont.getFamily())) {
		selectedIndex = i;

	    }
	    i++;
	}
	int ii = 0;

	while (ii < sizeComboBox.getItemCount()) {
	    if (Integer.valueOf(sizeComboBox.getItemAt(ii).toString()) == fontSize) {
		sizeComboBox.setSelectedIndex(ii);
		break;
	    }
	    ii++;
	}

	if (fontStyle == Font.BOLD) {
	    fontStyleComboBox.setSelectedIndex(0);
	} else if (fontStyle == Font.PLAIN) {
	    fontStyleComboBox.setSelectedIndex(2);
	} else if (fontStyle == Font.ITALIC) {
	    fontStyleComboBox.setSelectedIndex(1);
	}

	fontComboBox.setSelectedIndex(selectedIndex);
	masterPanel.add(tempPanel, BorderLayout.NORTH);

	jsp = new JScrollPane(graphicsPane);

	masterPanel.add(jsp, BorderLayout.CENTER);
	masterPanel.add(southPanel, BorderLayout.SOUTH);

	okButton = new JButton("Ok");
	cancelButton = new JButton("Cancel");
	JPanel buttonPanel = new JPanel();// panel that contains the ok and
	// cancel buttons
	buttonPanel.add(okButton);
	buttonPanel.add(cancelButton);
	okButton.setPreferredSize(cancelButton.getPreferredSize());

	southPanel.add(buttonPanel, BorderLayout.SOUTH);
	ActionListener buttonListener = new ActionListener() {

	    public void actionPerformed(ActionEvent e) {

		if (e.getSource() == cancelButton) {

		    wasCanceled = true;
		    dialog.dispose();

		}

	    }
	};

	okButton.setActionCommand("OK");
	if (ok != null) {
	    okButton.addActionListener(ok);
	}
	okButton.addActionListener(new ActionListener() {

	    public void actionPerformed(ActionEvent e) {

		dialog.dispose();
	    }
	});
	cancelButton.addActionListener(buttonListener);

	ActionListener fontListener = new ActionListener() {// listen for a

	    // change in the
	    // font list/size
	    // list
	    public void actionPerformed(ActionEvent e) {

		if (e.getSource() == sizeComboBox) {
		    fontSize = Integer.valueOf(sizeComboBox.getSelectedItem()
			    .toString());
		} else if (e.getSource() == fontComboBox) {
		    String fontSelected = fontComboBox.getSelectedItem()
			    .toString();
		    // if (fontSelected.equals("Dingbats")
		    // || fontSelected.equals("Standard Symbols L")) {
		    // fontName = "Tahoma";
		    // } else {
		    fontName = fontSelected;
		    // }
		} else if (e.getSource() == fontStyleComboBox) {
		    if (fontStyleComboBox.getSelectedItem().toString() == "Plain") {
			fontStyle = Font.PLAIN;
		    } else if (fontStyleComboBox.getSelectedItem().toString() == "Italic") {
			fontStyle = Font.ITALIC;
		    } else if (fontStyleComboBox.getSelectedItem().toString() == "Bold") {
			fontStyle = Font.BOLD;

		    }
		}
		selectedFont = newFont();
		graphicsPane.setLabelFont(selectedFont);
	    }
	};
	fontStyleComboBox.addActionListener(fontListener);
	sizeComboBox.addActionListener(fontListener);
	fontComboBox.addActionListener(fontListener);
	dialog.add(masterPanel);
	dialog.pack();

	dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	return dialog;// return the dialog that is created to the showDialog()
	// method
    }

    /**
     * @return Color that is chosen
     */
    public Color getFontColor() {

	return fontColor; // returns the font color
    }

    /**
     * @return Outline Color that is chosen (null if outline is disabled)
     */
    public Color getOutlineColor() {

	return outlineColor; // returns the outline color
    }

    /**
     * @return gets the selected font, same as the return from "showDialog()"
     */
    public Font getSelectedFont() {

	return selectedFont;// returns the selected font
    }

    /**
     * @return font based on name/style/size
     */
    private Font newFont() {

	return new Font(fontName, fontStyle, fontSize);// creates a new font
	// from the selected
	// name/style/size

    }

    /**
     * Shows a FontChooser Dialog
     * 
     * @return Font that is selected in the dialog, null if dialog was canceled
     */
    public Font showDialog() {

	final JFontChooser pane = this;
	FontTracker ok = new FontTracker(pane);
	fontDialog = createDialog(ok);
	fontDialog.setVisible(true);
	if (!wasCanceled) {
	    return ok.getFont();
	} else {
	    return null;
	}
    }

    // ************DEBUG MAIN STATEMENT********************
    public static void main(String[] args) {

	JFontChooser jfc = new JFontChooser();
	jfc.showDialog();
    }
}
