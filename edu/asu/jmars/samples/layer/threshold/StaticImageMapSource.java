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


package edu.asu.jmars.samples.layer.threshold;


import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;

import javax.swing.SwingUtilities;

import edu.asu.jmars.layer.map2.MapAttr;
import edu.asu.jmars.layer.map2.MapAttrReceiver;
import edu.asu.jmars.layer.map2.MapRequest;
import edu.asu.jmars.layer.map2.MapServer;
import edu.asu.jmars.layer.map2.MapSource;
import edu.asu.jmars.layer.map2.NonRetryableException;
import edu.asu.jmars.layer.map2.RetryableException;

import edu.asu.jmars.ProjObj;

class StaticImageMapSource implements MapSource {
	BufferedImage image;
	MapAttr mapAttr;
	Point2D offset;
	NullMapServer mapServer;
	String name;
	double[] ignoreValues;
	
	public StaticImageMapSource(BufferedImage image, String name){
		this.image = image;
		this.name = name;
		this.mapAttr = new MapAttr(image);
		Arrays.fill(ignoreValues = new double[image.getRaster().getNumBands()], 0);
		this.offset = new Point2D.Double();
		this.mapServer = new NullMapServer();
		this.mapServer.add(this);
	}
	
	public BufferedImage fetchTile(MapRequest mapTileRequest) throws RetryableException, NonRetryableException {
		Rectangle2D r = mapTileRequest.getExtent();
		int reqPpd = mapTileRequest.getPPD();
		ProjObj projObj = mapTileRequest.getProjection();

		if (r.getWidth() < 0 || r.getHeight() < 0)
			return null;
		
		int ow = (int)(reqPpd * r.getWidth());
		int oh = (int)(reqPpd * r.getHeight());
		
		WritableRaster outRaster = image.getRaster().createCompatibleWritableRaster(ow, oh);
		BufferedImage outImage = new BufferedImage(image.getColorModel(), outRaster, image.isAlphaPremultiplied(), null);
		
		double imagePpdX = image.getWidth()/360.0;
		double imagePpdY = image.getHeight()/180.0;
		
		Raster inRaster = image.getRaster();
		double[] dArray = null;
		
		for(int i=0; i<ow; i++){
			for(int j=0; j<oh; j++){
				Point2D sp = projObj.convWorldToSpatial(
						r.getMinX() + offset.getX() + ((double)i)/(double)reqPpd,
						r.getMaxY() - offset.getY() - ((double)j)/(double)reqPpd);
				double lon = (360-sp.getX())%360;
				double lat = sp.getY();
				
				if (lon >= 0 && lon < 360 && lat > -90 && lat <= 90){
					int x = (int)(lon * imagePpdX);
					int y = (int)((90-lat) * imagePpdY);
					
					try {
						dArray = inRaster.getPixel(x, y, dArray);
					}
					catch(Exception ex){
						ex.printStackTrace();
					}
					outRaster.setPixel(i, j, dArray);
				}
			}
		}
		
		return outImage;
	}

	public String getAbstract() {
		return name;
	}

	public String[][] getCategories() {
		return new String[0][0];
	}

	public MapAttr getMapAttr() {
		return mapAttr;
	}

	public void getMapAttr(final MapAttrReceiver receiver) {
		SwingUtilities.invokeLater(new Runnable(){
			public void run(){
				receiver.receive(mapAttr);
			}
		});
	}

	public String getMimeType() {
		return hasNumericKeyword()? "image/vicar": "image/png";
	}

	public String getName() {
		return name;
	}

	public MapServer getServer() {
		return mapServer;
	}

	public String getTitle() {
		return name;
	}

	public boolean hasNumericKeyword() {
		return image.getType() == BufferedImage.TYPE_CUSTOM;
	}

	public boolean isMovable() {
		return true;
	}

	public double[] getIgnoreValue() {
		return ignoreValues;
	}

	public Point2D getOffset() {
		return (Point2D)offset.clone();
	}
	
	public void setOffset(Point2D offset) {
		offset.setLocation(offset);
	}
	
	public Rectangle2D getLatLonBoundingBox() {
		return new Rectangle2D.Double(0,-90,360,180);
	}
	
	public double getMaxPPD() {
		return Math.max(image.getWidth() / 360d, image.getHeight() / 180d);
	}
}

