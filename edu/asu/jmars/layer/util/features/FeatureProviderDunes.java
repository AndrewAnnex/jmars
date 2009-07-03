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
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.Util;

public class FeatureProviderDunes implements FeatureProvider {
	public String getDescription() {
		return "USGS Mars Dune Database"; 
	}

	public File[] getExistingSaveToFiles(FeatureCollection fc, String baseName) {
		return null;
	}

	public String getExtension() {
		return null;
	}

	public boolean isFileBased() {
		return false;
	}

	public boolean isRepresentable(FeatureCollection fc) {
		return false;
	}
	
	public FeatureCollection load(String fileName) {
		String base = Main.getJMarsPath() + "shapes" + File.separator;
		try {
			ZipInputStream zis = new ZipInputStream(Main.getResourceAsStream("resources/Dune_Field.zip"));
			for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
				File out = new File(base + entry.getName());
				out.getParentFile().mkdirs();
				FileOutputStream fos = new FileOutputStream(out);
				Util.copy(zis, fos);
				fos.flush();
				fos.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return new FeatureProviderESRI().load(base + "Dune_Field" + File.separator + "Dune_Field.shp");
	}
	
	public int save(FeatureCollection fc, String fileName) {
		throw new UnsupportedOperationException("This is not supported");
	}
}
