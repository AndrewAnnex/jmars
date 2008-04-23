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


package edu.asu.jmars.layer.util.features;

import java.util.*;

/** 
 * Layers load class names as strings from jmars.config or some other source,
 * and they're initialized in the constructor. The base instance of each provider
 * is only used to access properties. If any specific information needs to be
 * retained for provider-specific reasons, the FeatureCollection produced by the
 * FeatureProvider.load() method should contain a reference to the custom object.
 */
public class FeatureProviderFactory {
	/** All FeatureProvider instances created for attribute getting. */
	List providers = new LinkedList();

	/**
	 * Constructs a new factory from the given class names. Individual layers will
	 * normally define a jmars.config array of class names to seed the factory with.
	 */
	public FeatureProviderFactory(String[] providerClassNames) {
		List failures = new LinkedList();
		for (int i = 0; i < providerClassNames.length; i++) {
			FeatureProvider provider = getProviderInstance(providerClassNames[i]);
			if (provider == null) {
				failures.add(providerClassNames[i]);
			} else {
				providers.add(provider);
			}
		}
	}

	private FeatureProvider getProviderInstance(String provider) {
		FeatureProvider pInstance = null;
		try {
			pInstance = (FeatureProvider)Class.forName(provider).newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (ClassCastException e) {
			e.printStackTrace();
		}
		return pInstance;
	}

	/**
	 * Returns the file providers that need a file chooser.
	 */
	public List getFileProviders () {
		List fileProviders = new LinkedList();
		for (Iterator it=providers.iterator(); it.hasNext(); ) {
			FeatureProvider provider = (FeatureProvider)it.next();
			if (provider.isFileBased())
				fileProviders.add(provider);
		}
		return fileProviders;
	}

	/**
	 * Returns the file providers that are automatically docked in the File
	 * menu.
	 */
	public List getNotFileProviders () {
		List notFileProviders = new LinkedList();
		for (Iterator it=providers.iterator(); it.hasNext(); ) {
			FeatureProvider provider = (FeatureProvider)it.next();
			if (! provider.isFileBased())
				notFileProviders.add(provider);
		}
		return notFileProviders;
	}
}

