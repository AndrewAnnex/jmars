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

import java.io.File;

import edu.asu.jmars.layer.shape2.FileChooser;

public class ScriptFileChooser extends FileChooser {
	FeatureProvider fp = new FeatureProviderScript();
	public ScriptFileChooser() {
		this.addFilter(fp);
	}
	public FeatureProvider getProvider () {
		return fp;
	}
	public static class FeatureProviderScript implements FeatureProvider {
		public String getDescription() {
			return "Shape Script File (*" + getExtension() + ")";
		}

		public File[] getExistingSaveToFiles(FeatureCollection fc, String name) {
			return new File[]{};
		}

		public String getExtension() {
			return ".ssf";
		}

		public boolean isFileBased() {
			throw new UnsupportedOperationException("isFileBased() has no value, this is only a filter");
		}

		public FeatureCollection load(String name) {
			throw new UnsupportedOperationException("Can't load, this is only a filter");
		}

		public int save(FeatureCollection fc, String name) {
			throw new UnsupportedOperationException("Can't save, this is only a filter");
		}

		public boolean isRepresentable(FeatureCollection fc) {
			throw new UnsupportedOperationException("Can't validate, this is only a filter");
		}
	}
}