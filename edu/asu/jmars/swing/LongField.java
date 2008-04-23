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

import javax.swing.*;

public class LongField
 extends PasteField
 {
	public LongField()
	 {
	 }

	public LongField(long val)
	 {
		setText(String.valueOf(val));
	 }

	public Long getLong()
	 {
		try
		 {
			return  new Long(getText());
		 }
		catch(NumberFormatException e)
		 {
			if(getText().length() != 0)
				JOptionPane.showMessageDialog(
					null,
					"Invalid integer: '" + getText() + "'",
					"FORMAT ERROR",
					JOptionPane.ERROR_MESSAGE);
			return  null;
		 }
	 }
 }
