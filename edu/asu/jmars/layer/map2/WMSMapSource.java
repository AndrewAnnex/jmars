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

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;

import edu.asu.jmars.ProjObj;
import edu.asu.jmars.ProjObj.Projection_OC;
import edu.asu.jmars.swing.SerializableRectangle2D;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;
import edu.asu.jmars.util.VicarException;

/**
 * A WMSMapSource is a MapSource which is available via a WMS Server.
 */
public class WMSMapSource implements MapSource, Serializable {
	private static final long serialVersionUID = -9016302326239498727L;
	
	private static DebugLog log = DebugLog.instance();
	
	public static final String CONTENT_TYPE_VICAR = "image/vicar";
	public static final String CONTENT_TYPE_PNG = "image/png";
	public static final String CONTENT_TYPE_WMS_ERROR = "application/vnd.ogc.se_xml";
	public static final String RESP_HDR_CONTENT_TYPE = "Content-Type";
	
	
	// accessible properties
	private String name;          // This is the map name to use when requesting a map from the server
	private MapServer server;
	private String title;	      // This is the map title to display to a user
	private String abstractText;
	private String[][] categories;  // Position of this map source in map server's hierarchy
	private SerializableRectangle2D latLonBBox; // (lat,lon) bounding box of the MapSource's extent (optional)
	private transient volatile MapAttr attr = null;
	
	// x and y offset, in degrees, representing how much the user has nudged the map
	// to deal with localized map variances.
	private boolean hasNumericKeyword = false;
	
	private final double[] ignoreValue;
	
	// internal-only fields for resolving the MapAttr object
	private transient MapChannel channel = null;
	
	private static transient Collection<MapSource> allSources = new ArrayList<MapSource>();
	private double xOffset = 0;
	private double yOffset = 0;
	
	public Point2D getOffset() {
		return new Point2D.Double(xOffset,yOffset);
	}
	
	public void setOffset(Point2D offset) {
		this.xOffset = offset.getX();
		this.yOffset = offset.getY();
	}
	
	public boolean isMovable() {
		// This should check a new keyword from the MapServer.
		// MOLA maps should NOT be moveable
		return !name.startsWith("MOLA");
	}
	
	/**
	 * Constructs a new MapSource from the given arguments.
	 * 
	 * If <code>dataType</code>
	 * is null, a new MapAttr will be created that will poll this new MapSource for
	 * a sample image from which to query the MapAttr values. Code interested in
	 * being notified when the state of this MapAttr is fully set should add a
	 * listener to {@link MapAttr#getChannel() MapAttr's MapChannel}.
	 */
	public WMSMapSource(String newName, String newTitle, String newAbstract, String[][] categories,
			MapServer newServer, boolean hasNumericKeyword, Rectangle2D latLonBBox, double[] ignoreValue) {
		this.name=newName;
		this.title=newTitle;
		this.abstractText=newAbstract;
		this.server=newServer;
		this.hasNumericKeyword = hasNumericKeyword;
		this.categories = categories;
		this.latLonBBox = safeCopy(latLonBBox);
		this.ignoreValue = ignoreValue;
		
		allSources.add(this);
	}
	
	public String[][] getCategories() {
		return categories;
	}
	
	/**
	 * Returns a canonic name for this MapSource within the scope of its MapServer.
	 * 
	 * This value must NOT be null.
	 */
	public String getName() {
		return name;
	}
	
	public String getTitle() {
		return title;
	}
	
	public String getAbstract(){
		return abstractText;
	}
	
	public MapServer getServer() {
		return server;
	}
	
	private SerializableRectangle2D safeCopy(Rectangle2D r){
		if (r == null)
			return null;
		return new SerializableRectangle2D(r);
	}
	
	public Rectangle2D getLatLonBoundingBox(){
		return safeCopy(latLonBBox);
	}
	
	/**
	 * Returns the MapAttr, or null if it has not been resolved yet. In that
	 * case, callers should call {@link getMapAttr(MapAttrReceiver)}
	 */
	public MapAttr getMapAttr() {
		return attr;
	}
	
	/**
	 * Returns the MapAttr asynchronously by passing it to the given callback
	 * some time later on the AWT thread.
	 */
	public void getMapAttr(final MapAttrReceiver receiver) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (attr != null) {
					receiver.receive(attr);
				} else {
					if (channel == null) {
						log.println("Creating deferred MapAttr");
						int ppd = 32;
						Rectangle2D extent = new Rectangle2D.Double(0,0,1d/ppd,1d/ppd);
						ProjObj previewProj = new ProjObj.Projection_OC(0,0);
						Pipeline[] pipes = new Pipeline[]{new Pipeline(WMSMapSource.this, new Stage[0])};
						channel = new MapChannel(extent, ppd, previewProj, pipes);
					}
					channel.addReceiver(new MapChannelReceiver() {
						public void mapChanged(final MapData mapData) {
							if (mapData.isFinished()) {
								SwingUtilities.invokeLater(new Runnable() {
									public void run() {
										channel = null;
										attr = new MapAttr(mapData.getImage());
										log.println("Deferred MapAttr resolved succesfully");
										receiver.receive(attr);
									}
								});
							}
						}
					});
				}
			};
		});
	}
	
	/**
	 * Returns the tile size in pixels.
	 * @param mapTileRequest
	 */
	private Dimension getTileSizeInPixels(MapRequest mapTileRequest){
		int ppd = mapTileRequest.getPPD();
		double width = (MapRetriever.getLatitudeTileSize(ppd) * ppd);
		double height = (MapRetriever.getLongitudeTileSize(ppd) * ppd);
		return new Dimension((int)width, (int)height);
	}
	
	private String buildRequestUrlString(MapRequest mapTileRequest){
		String serverURL = getServer().getMapUrl();

		StringBuffer wmsURL = new StringBuffer();
		wmsURL.append(serverURL);
		wmsURL.append("SERVICE=WMS&REQUEST=GetMap&FORMAT="); wmsURL.append(getMimeType());
		
		// get up vector to request, and adjust longitude values based on requested projection
		Projection_OC poc = (Projection_OC)mapTileRequest.getProjection();
		Point2D up = Util.getJmars1Up(poc, null);
		
		wmsURL.append("&SRS=JMARS:1,"); wmsURL.append(up.getX()); wmsURL.append(","); wmsURL.append(up.getY());				
		wmsURL.append("&STYLES=&VERSION=1.1.1&LAYERS="); wmsURL.append(getName());

		// TODO: This can be simplified to just '256'.  Should it be, or is it better to let us have
		// some flexibility?
		Dimension tileSize = getTileSizeInPixels(mapTileRequest);
		wmsURL.append("&WIDTH="); wmsURL.append(tileSize.width);
		wmsURL.append("&HEIGHT="); wmsURL.append(tileSize.height);

		Rectangle2D tileExtent = mapTileRequest.getExtent();

		double startX = tileExtent.getMinX();
		double startY = tileExtent.getMinY();
		double endX = tileExtent.getMaxX();
		double endY = tileExtent.getMaxY();

		startX = Util.worldXToJmars1X(poc, startX);
		endX = Util.worldXToJmars1X(poc, endX);

		StringBuffer tileURL = new StringBuffer();
		tileURL.append("&BBOX="); tileURL.append(startX); tileURL.append(","); tileURL.append(startY);
		tileURL.append(","); tileURL.append(endX); tileURL.append(","); tileURL.append(endY);

		String urlString = wmsURL.toString()+tileURL.toString();
		
		return urlString;
	}
	
	public BufferedImage fetchTile(MapRequest mapTileRequest) throws RetryableException, NonRetryableException {
		String urlString = buildRequestUrlString(mapTileRequest);
		Dimension tileSize = getTileSizeInPixels(mapTileRequest);
		String mimeType = getMimeType();
		log.println("Downloading ["+mimeType+"] tile from URL " + urlString);
		
		HttpClient client = new HttpClient();
		HttpConnectionManager conMan = client.getHttpConnectionManager();
		
		conMan.getParams().setDefaultMaxConnectionsPerHost(10);
	    conMan.getParams().setConnectionTimeout(getServer().getTimeout());
	    conMan.getParams().setSoTimeout(60000);
	    
	    HttpMethod method = new GetMethod(urlString);
	    
		BufferedImage tileImage = null;
	    
		try {
			client.executeMethod(method);
			
			// check the resulting content type
			String contentType = checkForWMSException(method);
			
			if (CONTENT_TYPE_VICAR.equals(contentType)) {
				tileImage = MyVicarReaderWriter.read(method.getResponseBodyAsStream());
			}
			else if (CONTENT_TYPE_PNG.equals(contentType)) {
				tileImage = ImageIO.read(method.getResponseBodyAsStream());
				tileImage = Util.replaceWithLinearGrayCS(tileImage);
			} else {
				throw new NonRetryableException("Unrecognized MIME type " + contentType);
			}
			
			if (tileImage == null || tileImage.getWidth() != tileSize.width || tileImage.getHeight() != tileSize.height) {
				throw new RetryableException(tileImage==null?"Downloaded image was null": "Wrong size image downloaded");
			}
		}
		catch (MalformedURLException mue) {
			throw new NonRetryableException("Malformed URL " + urlString, mue);
		}
		catch (HttpException he) {
			throw new NonRetryableException("HttpException for URL: " + urlString, he);
		}
		catch (SocketTimeoutException ste) {
			// We do NOT want to retry timeouts
			throw new NonRetryableException("Socket Timedout: " + ste.getMessage() + " for " + urlString, ste);
		}
		catch (IOException ioe){
			throw new RetryableException("I/O exception connecting to URL: " + urlString, ioe);
		}
		catch (VicarException ve) {
			throw new RetryableException("A VicarException occured", ve);
		}
		catch (WMSException wmse) {
			throw new NonRetryableException(wmse);
		}
		catch (NonRetryableException nre){
			throw nre;
		}
		catch (RetryableException re){
			throw re;
		}
		catch (Exception e) {
			throw new NonRetryableException("An unknown error occured", e);
		} 
		finally {
			method.releaseConnection();
		}
	    
	    return tileImage;
	}
	
	/**
	 * Checks for WMS Exceptions throwing them as needed, returning the
	 * content type.
	 * @param method 
	 * @return Content type of the data.
	 */
	private String checkForWMSException(HttpMethod method) throws WMSException, IOException {
		String contentType = method.getResponseHeader(RESP_HDR_CONTENT_TYPE).getValue();
		
		if (contentType == null || contentType.length() == 0){
			throw new IOException("MapServer did not provide a content type");
		}
		else if (CONTENT_TYPE_WMS_ERROR.equalsIgnoreCase(contentType)) {
			// TODO: these errors have semantic meaning, handle retries appropriately
			InputStream responseStream = method.getResponseBodyAsStream();

			String errorText = Util.readResponse(responseStream);
			String pattern1 = "ServiceException code=\"";
			String pattern2 = "\">";
			int start = errorText.indexOf(pattern1) + pattern1.length();
			int end = errorText.indexOf(pattern2, start);

			if (start > -1 && end > -1)
				throw new WMSException("A WMS Error occurred: " + errorText.substring(start, end));
			else
				throw new WMSException("An unknown WMS error occurred.");
		}
		
		return contentType;
	}
	
	public String toString(){
		return getTitle();
	}
	
	/**
	 * The numeric keyword will cause a map to be requested in vicar format,
	 * otherwise in png
	 * 
	 * TODO: this usage of 'numeric' is GROSS, wrong, and may possibly be used
	 * by other servers to mean something else! Get rid of it and deduce map
	 * request types using the original design (using the strongest format
	 * supported both by the server and client.)
	 */
	public boolean hasNumericKeyword() {
		return hasNumericKeyword;
	}
	
	public String getMimeType() {
		if (hasNumericKeyword()) {
			return "image/vicar";
		} else {
			return "image/png";
		}
	}
	
	/** Equality comparison by <em>identity</em> */
	public boolean equals(Object o) {
		if (o instanceof WMSMapSource) {
			MapSource s = (MapSource)o;
			return s.getServer().equals(getServer()) && s.getName().equals(getName());
		}
		return false;
	}
	
	/** Hash-code is based on <em>identity</em> */
	public int hashCode(){
		return (getServer().hashCode() * (1<<31)) + getName().hashCode();
	}
	
	/**
	 * Returns the ignore value reported by the server, or a NaN value for each
	 * band if the MapAttr has resolved, or null otherwise
	 */
	public double[] getIgnoreValue() {
		if (ignoreValue != null) {
			return ignoreValue;
		} else if (attr != null) {
			double[] out = new double[attr.getNumColorComp()];
			Arrays.fill(out, Double.NaN);
			return out;
		} else {
			return null;
		}
	}
}
