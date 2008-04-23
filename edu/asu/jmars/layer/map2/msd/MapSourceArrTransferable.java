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


package edu.asu.jmars.layer.map2.msd;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import edu.asu.jmars.layer.map2.MapSource;

public class MapSourceArrTransferable implements Transferable {
	public static final String mapSrcArrMimeType = DataFlavor.javaJVMLocalObjectMimeType+";class="+MapSource.class.getName();
	public static final DataFlavor mapSrcArrDataFlavor;
	DataFlavor[] suppFlavors;
	List suppFlavorsList;
	
	static {
		DataFlavor f = null;
		try {
			f = new DataFlavor(mapSrcArrMimeType);
		}
		catch(ClassNotFoundException ex){
			ex.printStackTrace();
		}
		mapSrcArrDataFlavor = f;
	}
	
	MapSource[] srcArr;
	
	public MapSourceArrTransferable(MapSource[] srcArr){
		if (mapSrcArrDataFlavor == null)
			suppFlavors = new DataFlavor[0];
		else
			suppFlavors = new DataFlavor[]{ mapSrcArrDataFlavor };
		suppFlavorsList = Arrays.asList(suppFlavors);
		
		this.srcArr = srcArr;
	}
	
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
		if (!isDataFlavorSupported(flavor))
			throw new UnsupportedFlavorException(flavor);
		
		return srcArr;
	}

	public DataFlavor[] getTransferDataFlavors() {
		return suppFlavors;
	}

	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return suppFlavorsList.contains(flavor);
	}
}

