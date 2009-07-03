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

import java.awt.Component;
import java.awt.Container;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.awt.FocusTraversalPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * <p>Provides a {@link FocusTraversalPolicy} implementation that lets the
 * programmer specify the exact ordering of the component focus cycle.
 * 
 * <p>The {@link ContainerOrderFocusTraversalPolicy} would do the same job, if it
 * honored {@link Component#isFocusable()}.
 * 
 * <p>The components will be cycled in the order given in the object passed to the
 * constructor.
 * 
 * <p>The default component is always the first component in the list.
 */
public class CustomTabOrder extends FocusTraversalPolicy {
	private final List<Component> order;
	public CustomTabOrder(Component[] ordered) {
		this(Arrays.asList(ordered));
	}
	public CustomTabOrder(List<Component> ordered) {
		super();
		this.order = new ArrayList<Component>(ordered);
		if (ordered.size() == 0) {
			throw new IllegalArgumentException("Must supply at least one component");
		}
	}
	public Component getComponentAfter(Container container, Component component) {
		int pos = order.indexOf(component);
		if (pos >= 0 && pos < order.size() - 1) {
			return order.get(pos+1);
		} else {
			return null;
		}
	}
	public Component getComponentBefore(Container container, Component component) {
		int pos = order.indexOf(component);
		if (pos > 0 && pos < order.size()) {
			return order.get(pos-1);
		} else {
			return null;
		}
	}
	public Component getDefaultComponent(Container container) {
		return getFirstComponent(container);
	}
	public Component getFirstComponent(Container container) {
		return order.get(0);
	}
	public Component getLastComponent(Container container) {
		return order.get(order.size()-1);
	}
}

