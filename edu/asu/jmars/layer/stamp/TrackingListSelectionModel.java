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


package edu.asu.jmars.layer.stamp;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.util.*;
import javax.swing.*;

public abstract class TrackingListSelectionModel
 extends DefaultListSelectionModel
 {
	private static final Object[] NOTHING = new Object[0];

	private boolean clearFirst = false;
	private Set adds = new HashSet();
	private Set dels = new HashSet();

	public void setValueIsAdjusting(boolean isAdjusting)
	 {
		super.setValueIsAdjusting(isAdjusting);

		if(!isAdjusting)
		 {
			if(clearFirst  ||  !adds.isEmpty()  ||  !dels.isEmpty())
			 {
				selectOccurred(clearFirst,
							   adds.toArray(),
							   dels.toArray());
				clearFirst = false;
				adds.clear();
				dels.clear();
			 }
		 }
	 }

	/**
	 ** Supplied by implementation, requires access to data model.
	 **/
	protected abstract Object getValue(int idx);

	/**
	 ** Supplied by implementation, responds to selections.
	 **/
	protected abstract void selectOccurred(boolean clearFirst,
										   Object[] addvals,
										   Object[] delvals);

	/**
	 ** Supplied by implementation, invokes selection.
	public abstract void performSelect(boolean clearFirst,
									   Object[] adds,
									   Object[] dels);
	 **/

/*
	public void insertIndexInterval(int index, int length, boolean before)
	 {
		super.insertIndexInterval(index, length, before);
	 }
	public void removeIndexInterval(int a, int b)
	 {
		super.removeIndexInterval(a, b);
	 }
*/

	public void addSelectionInterval(int a, int b)
	 {
		addSelectDelayed(Math.min(a, b),
						 Math.max(a, b));

		super.addSelectionInterval(a, b);
	 }

	public void removeSelectionInterval(int a, int b)
	 {
		delSelectDelayed(Math.min(a, b),
						 Math.max(a, b));

		super.removeSelectionInterval(a, b);
	 }

	public void setSelectionInterval(int a, int b)
	 {
		setSelectDelayed(Math.min(a, b),
						 Math.max(a, b));

		super.setSelectionInterval(a, b);
	 }

	public void setLeadSelectionIndex(int n)
	 {
		int a = getAnchorSelectionIndex();
		int o = getLeadSelectionIndex();

		boolean sel = isSelectedIndex(a);
		int del = sel ? o : n;
		int add = sel ? n : o;

		delSelectDelayed(Math.min(a, del),
						 Math.max(a, del));
		addSelectDelayed(Math.min(a, add),
						 Math.max(a, add));

		super.setLeadSelectionIndex(n);
	 }

	// Delayed events

	private boolean clearSelectionHack = false;
	public void clearSelection()
	 {
		if(getValueIsAdjusting())
		 {
			dels.clear();
			adds.clear();
			clearFirst = true;
		 }
		else
			selectOccurred(true,
						   NOTHING,
						   NOTHING);

		clearSelectionHack = true;
		super.clearSelection();
		clearSelectionHack = false;
	 }

	private void setSelectDelayed(int min, int max)
	 {
		if(getValueIsAdjusting())
		 {
			dels.clear();

			clearFirst = true;
			adds.clear();
			if(min != -1  &&  max != -1)
				for(int i=min; i<=max; i++)
					adds.add(getValue(i));
		 }
		else
		 {
			if(min != -1  &&  max != -1)
				for(int i=min; i<=max; i++)
					adds.add(getValue(i));
			selectOccurred(true,
						   adds.toArray(),
						   NOTHING);
			adds.clear();
		 }
	 }

	private void delSelectDelayed(int min, int max)
	 {
		if(min == -1  ||  max == -1  ||  clearSelectionHack)
			return;
		if(getValueIsAdjusting())
			for(int i=min; i<=max; i++)
			 {
				Object val = getValue(i);
				dels.add(val);
				adds.remove(val);
			 }
		else
		 {
			for(int i=min; i<=max; i++)
				dels.add(getValue(i));
			selectOccurred(false,
						   NOTHING,
						   dels.toArray());
			dels.clear();
		 }
	 }

	private void addSelectDelayed(int min, int max)
	 {
		if(min == -1  ||  max == -1)
			return;
		if(getValueIsAdjusting())
			for(int i=min; i<=max; i++)
			 {
				Object val = getValue(i);
				adds.add(val);
				dels.remove(val);
			 }
		else
		 {
			for(int i=min; i<=max; i++)
				adds.add(getValue(i));
			selectOccurred(false,
						   adds.toArray(),
						   NOTHING);
			adds.clear();
		 }
	 }
 }
