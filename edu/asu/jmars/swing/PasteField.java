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

import java.awt.datatransfer.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.text.Document.*;

/**
 ** A system-clipboard enabled version of {@link JTextField}.
 ** Implements X-style clipboard operations:
 **
 ** <ul>
 ** <li>Clicking the middle mouse button pastes from the clipboard.</li>
 ** <li>Selecting text copies to the clipboard.</li>
 ** </ul>
 **
 ** <p>(All of the above operations use the system clipboard, not some
 ** private java clipboard).
 **
 ** <p>Instead of using <code>PasteField</code> objects directly, you
 ** can instead call {@link #enableClipboard} on pre-existing {@link
 ** JTextComponent} objects.
 **/

public class PasteField
 extends JTextField
 {
	public PasteField()
	 {
		super();
		enableClipboard(this);
	 }

	public PasteField(Document doc, String text, int columns)
	 {
		super(doc, text, columns);
		enableClipboard(this);
	 }

	public PasteField(int columns)
	 {
		super(columns);
		enableClipboard(this);
	 }

	public PasteField(String text)
	 {
		super(text);
		enableClipboard(this);
	 }

	public PasteField(String text, int columns)
	 {
		super(text, columns);
		enableClipboard(this);
	 }

	/**
	 ** Enables X-style copy/paste to/from the system clipboard via
	 ** selection/middle-mouse-button.
	 **/
	public static void enableClipboard(final JTextComponent txtBox)
	 {
		// Click-pasting recycles the same generic listener.
		txtBox.addMouseListener(pasteListener);

		// Selection-copying needs a listener for each box.
/*
DISABLED FOR NOW, FUNNY BEHAVIOR
		txtBox.addCaretListener(
			new CaretListener()
			 {
				public void caretUpdate(CaretEvent e)
				 {
					if(e.getDot() != e.getMark())
						txtBox.copy(); //copy(txtBox);
				 }
			 }
			);
*/
	 }
	private static final MouseListener pasteListener =
		new MouseAdapter()
		 {
			public void mouseClicked(MouseEvent e)
			 {
				if(SwingUtilities.isMiddleMouseButton(e))
					paste( (JTextComponent) e.getComponent() );
			 }
		 };

    /**
	 ** Performs almost identically to {@link JTextComponent#copy}.
	 ** In addition to the standard functionality, also provides for
	 ** losing ownership of the pasted text, which causes the
	 ** selection to cancel. NOT USED RIGHT NOW.
     **/
    private static void copy(final JTextComponent txtBox)
	 {
        try
		 {
			Caret caret = txtBox.getCaret();
            Clipboard clipboard = txtBox.getToolkit().getSystemClipboard();
            int p0 = Math.min(caret.getDot(), caret.getMark());
            int p1 = Math.max(caret.getDot(), caret.getMark());
            if (p0 != p1)
			 {
                String srcData = txtBox.getDocument().getText(p0, p1 - p0);
                StringSelection contents = new StringSelection(srcData);
                clipboard.setContents(
					contents,
					new ClipboardOwner()
					 {
						public void lostOwnership(Clipboard clipboard,
												  Transferable contents)
						 {
							txtBox.setCaretPosition(
								txtBox.getCaretPosition());
						 }
					 }
					);
			 }
		 }
		catch(BadLocationException e)
		 {
		 }
	 }

    /**
	 ** Performs almost identically to {@link JTextComponent#paste}.
	 ** In addition to the standard functionality, also provides for
	 ** losing ownership of the pasted text, which causes the
	 ** selection to cancel.
     **/
	private static void paste(JTextComponent txtBox)
	 {
		Clipboard clipboard = txtBox.getToolkit().getSystemClipboard();
		Transferable content = clipboard.getContents(txtBox);
		if(content != null)
			try
			 {
				txtBox.replaceSelection(nice(
					(String) content.getTransferData(DataFlavor.stringFlavor)
					));
			 }
			catch(Exception e)
			 {
				txtBox.getToolkit().beep();
			 }
	 }

	/**
	 ** Tidies up strings, to help prevent bad pasting accidents.
	 **/
	private static String nice(String s)
	 {
		// max len
		if(s.length() > 256)
			s = s.substring(0, 256);

		// single line
		int newline = s.indexOf('\n');
		if(newline != -1)
			s = s.substring(0, newline);

		// cut out control characters
		for(int i=0; i<256; i++)
			if(Character.isISOControl((char) i))
				s = s.replace((char) i, ' ');

		return  s;
	 }
 }
