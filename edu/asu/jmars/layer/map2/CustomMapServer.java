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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.PartSource;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;

import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

/**
 * CustomMapServer is a MapServer with authentication to get maps for a
 * particular user. There is only one constructor that prevents creation
 * CustomMapServer objects except from serialized data in jmars.config.
 */
public class CustomMapServer extends WMSMapServer implements Serializable {
	public static String customMapServerName = "custom";
	private static DebugLog log = DebugLog.instance();
	private final String user;
	private transient final String passwd;
	
	/** Constructs a new custom map server with the given user's custom maps. */
	public CustomMapServer(String serverName, String user, String passwd) {
		super(serverName, "user=" + user);
		this.user = user;
		this.passwd = passwd;
	}
	
	/** Overrides the category of a custom map source */
	public void add(MapSource source) {
		String[][] empty = {{}};
		super.add(
			new WMSMapSource(
				source.getName(),
				source.getTitle(),
				source.getAbstract(),
				empty,
				this,
				source.hasNumericKeyword(),
				(source instanceof WMSMapSource)? ((WMSMapSource)source).getLatLonBoundingBox(): null,
				source.getIgnoreValue(),
				source.getMaxPPD()));
	}
	
	/**
	 * @param name The descriptive name of this map
	 * @return The canonic unique name of  this map
	 */
	private String getCanonicName(String name) {
		String canonicName = user + "." + String.valueOf(name.hashCode());
		return canonicName.replaceAll("[^0-9A-Za-z\\.-]", "_");
	}
	
	/**
	 * Send a local map file to the custom map server.
	 * @param name The descriptive name for the user to see.
	 * @param file The File to post to the server.
	 * @throws Exception If anything goes wrong. The message will contain the error or server response.
	 */
	public void uploadCustomMap(String remoteName, File file, Rectangle2D bounds, Double ignoreValue, boolean email) throws Exception {
		String remoteID = getCanonicName(remoteName);
		PostMethod post = getMethod("upload");
		addArgs(post, remoteName, bounds, ignoreValue, email);
		addFile(post, file, remoteID);
		log.println("Uploading custom map named " + remoteName + " from local file " + file.getAbsolutePath() + " to layer id " + remoteID);
		String response = read(post);
		finishUpload("local", response, remoteID);
	}
	
	/**
	 * Send a remote map file to the custom map server.
	 * @param name The descriptive name for the user to see.
	 * @param remoteUrl The URL the server should download the image from
	 * @throws Exception Thrown if anything goes wrong. The message will
	 * contain the server response.
	 */
	public void uploadCustomMap(String name, URL remoteUrl, Rectangle2D bounds, Double ignoreValue, boolean email) throws Exception {
		PostMethod post = getMethod("remote");
		addArgs(post, name, bounds, ignoreValue, email);
		String remoteID = getCanonicName(name);
		post.addParameter("rfile",remoteUrl.toString());
		post.addParameter("lfile",remoteID);
		log.println("Uploading custom map named " + name + " from remote URL " + remoteUrl);
		String response = read(post);
		finishUpload("remote", response, remoteID);
	}
	
	protected HttpMethod getCapsMethod() {
		PostMethod post = new PostMethod(getCapabilitiesURI().toString());
		post.addParameter("passwd", passwd);
		return post;
	}
	
	private PostMethod getMethod(String request) {
		PostMethod post = new PostMethod(getSuffixedURI(getURI(), "request="+request).toString());
		post.addParameter("passwd", passwd);
		return post;
	}
	
	/** Adds upload parameters to a post */
	private void addArgs(PostMethod post, String remoteName, Rectangle2D bounds, Double ignoreValue, boolean email) {
		post.addParameter("name", remoteName);
		if (bounds != null) {
			post.addParameter("bbox", MessageFormat.format(
				"{0,number,#.######},{1,number,#.######},{2,number,#.######},{3,number,#.######}",
				bounds.getMinX(),bounds.getMinY(),bounds.getMaxX(),bounds.getMaxY()));
		}
		if (ignoreValue != null) {
			post.addParameter("ignore", MessageFormat.format("{0,number,#.######}",ignoreValue));
		}
		if (email) {
			post.addParameter("sendemail","1");
		}
	}
	
	private void finishUpload(String type, String response, String mapName) throws Exception {
		if (response.toUpperCase().startsWith("ERROR:")) {
			log.println("Uploading " + type + " map failed with " + response);
			throw new Exception(response);
		}
		
		log.println("Remote upload succeeded");
		Thread.sleep(2000);
		loadCapabilities(false);
		MapSource source = getSourceByName(mapName);
		if (source == null) {
			throw new Exception(
					"Upload succeeded but custom map cannot be found with name " +
					mapName);
		}
		
		// clear cache in case this is an updated map
		CacheManager.removeMap(source);
	}
	
	private void addFile(final PostMethod post, final File localFile , final String remoteName) throws URIException {
		// construct a custom FilePart that will cause 'localName' to be named
		// 'remoteName' on the server
		List<Part> parts = new ArrayList<Part>();
		for (NameValuePair parm: post.getParameters()) {
			parts.add(new StringPart(parm.getName(), parm.getValue()));
		}
		parts.add(new FilePart(remoteName, new PartSource() {
			public InputStream createInputStream() throws IOException {
				return new FileInputStream(localFile);
			}
			public String getFileName() {
				return remoteName;
			}
			public long getLength() {
				return localFile.length();
			}
		}));
		post.setRequestEntity(new MultipartRequestEntity(parts.toArray(new Part[0]), post.getParams()));
		
		// construct a retry handler that will never retry
		post.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
			public boolean retryMethod(HttpMethod method, IOException exception, int executionCount) {
				return false; 
			}
		});
	}
	
	private String read(PostMethod post) {
		try {
			HttpClient client = new HttpClient();
			int code = Util.postWithRedirect(client, post, 3);
			client.getHttpConnectionManager().getParams().setConnectionTimeout(getTimeout());
			if (code == HttpStatus.SC_OK) {
				return Util.readResponse(post.getResponseBodyAsStream());
			} else {
				return "ERROR: Unexpected HTTP code " + code + " received";
			}
		} catch (Exception e) {
			log.aprintln(e);
			return "ERROR: " + e.getMessage();
		} finally {
			if (post != null) {
				post.releaseConnection();
			}
		}
	}
	
	/**
	 * Removes the given custom map. A message dialog will show any errors to the user.
	 * @param name The canonic name of the map to remove.
	 * @throws Exception 
	 */
	public void deleteCustomMap(String name) throws Exception {
		log.println("Deleting custom map named " + name);
		MapSource source = getSourceByName(name);
		if (source == null) {
			throw new Exception("No map source with the name " + name + " was found");
		}
		PostMethod post = getMethod("delete");
		post.addParameter("names", name);
		String response = read(post);
		if (!response.startsWith("OK:")) {
			log.println("Delete of map failed with " + response);
			throw new Exception(response);
		} else {
			log.println("Removal succeeded");
			CacheManager.removeMap(source);
			loadCapabilities(false);
			if (getSourceByName(source.getName()) != null) {
				throw new Exception("Custom map removal succeeded but it is still found!");
			}
		}
	}
}
