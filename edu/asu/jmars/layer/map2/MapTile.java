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


package edu.asu.jmars.layer.map2;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public final class MapTile {
	private final MapRequest request;
	private final Rectangle2D tileExtent;
	private final int xtile;
	private final int ytile;
	private BufferedImage image;
	private BufferedImage fuzzyImage;
	
	public boolean equals(Object o) {
		return o instanceof MapTile &&
			xtile == ((MapTile)o).xtile && ytile == ((MapTile)o).ytile;
	}
	
	public int hashCode() {
		return xtile*31 + ytile;
	}
	
	public MapTile(MapRequest request, int i, int j) {
		this.request = request;
		xtile = i;
		ytile = j;
		
		double xstep = MapRetriever.getLatitudeTileSize(request.getPPD());
		double ystep = MapRetriever.getLongitudeTileSize(request.getPPD());
		double x = xtile * xstep;
		double y = ytile * ystep - 90;
		tileExtent = new Rectangle2D.Double(x, y, xstep, ystep);
	}
	
	public MapRequest getRequest() {
		return request;
	}
	
	public Rectangle2D getTileExtent() {
		return tileExtent;
	}
	
	public BufferedImage getImage() {
		if (image!=null) return image;
		return fuzzyImage;
	}
	
	public int getXtile() {
		return xtile;
	}
	
	public int getYtile() {
		return ytile;
	}
	
	public boolean isMissing() {
		if (image==null && fuzzyImage==null) return true;
		return false;
	}
	
	public boolean isFinal() {
		if (image!=null) return true;
		return false;
	}
	
	public boolean isFuzzy() {
		if (image==null && fuzzyImage!=null) return true;
		return false;
	}
	
	public synchronized void setFuzzyImage(BufferedImage newImage) {
		if (isFinal()) {
			return;  // don't do fuzzy updates after we've already received final data
		}
 		fuzzyImage = newImage;
	}
	
	public synchronized void setImage(BufferedImage newImage) {
		if (image !=null) {
			return;
		}
		image = newImage;
	}
		
	String errorMessage=null;
	
	public void setErrorMessage(String msg) {
		errorMessage = msg;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}
	
	public boolean hasError() {
		if (errorMessage!=null) return true;
		return false;
	}
}
