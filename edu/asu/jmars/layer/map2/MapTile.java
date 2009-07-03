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

import java.awt.Point;
import java.awt.image.BufferedImage;

public final class MapTile {
	private final MapRequest request;
	private final int xtile;
	private final int ytile;
	private final MapRequest tileRequest;
	private BufferedImage image;
	private BufferedImage fuzzyImage;
	private Exception exception=null;
	
	public boolean equals(Object o) {
		if (o instanceof MapTile) {
			return tileRequest.equals(((MapTile)o).tileRequest);
		} else {
			return false;
		}
	}
	
	public int hashCode() {
		return tileRequest.hashCode();
	}
	
	public MapTile(MapRequest entireRequest, MapRequest tileRequest, Point tilePosition) {
		this.request = entireRequest;
		xtile = tilePosition.x;
		ytile = tilePosition.y;
		this.tileRequest = tileRequest;
	}
	
	public MapRequest getRequest() {
		return request;
	}
	
	public BufferedImage getImage() {
		return image;
	}
	
	public BufferedImage getFuzzyImage() {
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
		if (image ==null) {
			image = newImage;
		}
	}
	
	public void setException(Exception e) {
		exception = e;
	}
	
	public Exception getException() {
		return exception;
	}
	
	public boolean hasError() {
		return exception!=null;
	}
	
	public MapRequest getTileRequest() {
		return tileRequest;
	}
}
