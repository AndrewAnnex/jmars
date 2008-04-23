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


package edu.asu.jmars.layer;

import java.util.*;
import javax.swing.*;

/**
 ** Encapsulates a parent node in a tree of LView factories.
 **/
public abstract class MultiFactory {
	private String name;

	protected MultiFactory(String name) {
		this.name = name;
	}

	/**
	 ** Must return a List consisting of just MultiFactory and/or
	 ** LViewFactory instances.
	 **/
	protected abstract List getChildFactories();

	private JMenu createMenu(LViewFactory.Callback callback) {
		JMenu menu = new JMenu(name);
		addAllToMenu(callback, menu, getChildFactories());
		return menu;
	}

	static void addAllToMenu(LViewFactory.Callback callback, JMenu menu, List factories) {
		for (Iterator i = factories.iterator(); i.hasNext();) {
			Object child = i.next();
			if (child instanceof MultiFactory)
				menu.add(((MultiFactory) child).createMenu(callback));
			else {
				for (JMenuItem childMenu: ((LViewFactory) child).createMenuItems(callback))
					menu.add(childMenu);
			}
		}
	}

	void addDescendantsTo(List list) {
		for (Iterator i = getChildFactories().iterator(); i.hasNext();) {
			Object child = i.next();
			if (child instanceof MultiFactory)
				((MultiFactory) child).addDescendantsTo(list);
			else
				list.add(child);
		}
	}
}
