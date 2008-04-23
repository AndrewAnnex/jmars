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

import java.io.File;
import java.io.Serializable;
import java.net.URL;

import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

// TODO: move Util.httpPost and Util.httpPostFileName from Util to here
/**
 * CustomMapServer is a MapServer with authentication to get maps for a
 * particular user. There is only one constructor that prevents creation
 * CustomMapServer objects except from serialized data in jmars.config.
 */
public class CustomMapServer extends WMSMapServer implements Serializable {
	public static String customMapServerName = "custom";
	private static DebugLog log = DebugLog.instance();
	private final String user;
	
	/** Constructs a new custom map server with the given user's custom maps. */
	public CustomMapServer(String serverName, String user, String passwd) {
		super(serverName, "user=" + Util.urlEncode(user) + "&passwd=" + Util.urlEncode(passwd) + "&");
		this.user = user;
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
				source.getIgnoreValue()));
	}
	
	/**
	 * @param name The descriptive name of this map
	 * @return The canonic unique name of  this map
	 */
	public String getCanonicName(String name) {
		String canonicName = user + "." + String.valueOf(name.hashCode());
		return canonicName.replaceAll("[^0-9A-Za-z\\.-]", "_");
	}
	
	/**
	 * Send a local map file to the custom map server.
	 * @param name The descriptive name for the user to see.
	 * @param file The File to post to the server.
	 * @throws Exception If anything goes wrong. The message will contain the error or server response.
	 */
	public void uploadCustomMap(String name, File file) throws Exception {
		log.println("Uploading custom map named " + name + " from local file " + file.getAbsolutePath());
		String url = getURI("request=upload","name=" + name).toString().replaceAll("\\?", "/?");
		String fname = file.getAbsolutePath();
		String response = Util.httpPostFileName(url, fname, getCanonicName(fname));
		if (response.toUpperCase().startsWith("ERROR:")) {
			log.println("Uploading local map failed with " + response);
			throw new Exception(response);
		} else {
			log.println("Local upload succeeded");
			loadCapabilities(false);
			if (getSourceByName(getCanonicName(fname)) == null) {
				throw new Exception(
					"Upload succeeded but custom map cannot be found with name " +
					getCanonicName(fname));
			}
		}
	}
	
	/**
	 * Send a remote map file to the custom map server.
	 * @param name The descriptive name for the user to see.
	 * @param remoteUrl The URL the server should download the image from
	 * @throws Exception Thrown if anything goes wrong. The message will
	 * contain the server response.
	 */
	public void uploadCustomMap(String name, URL remoteUrl) throws Exception {
		log.println("Uploading custom map named " + name + " from remote URL " + remoteUrl);
		String getString = getURI("request=remote","name=" + name).toString().replaceAll("\\?", "/?");
		String fileNameOnServer = user + "." + remoteUrl.hashCode();
		String postString = "rfile=" + remoteUrl + "&lfile=" + fileNameOnServer;
		String response = Util.httpPost(getString, postString);
		if (response.toUpperCase().startsWith("ERROR:")) {
			log.println("Uploading remote map failed with " + response);
			throw new Exception(response);
		} else {
			log.println("Remote upload succeeded");
			loadCapabilities(false);
			if (getSourceByName(fileNameOnServer) == null) {
				throw new Exception(
					"Upload succeeded but custom map cannot be found with name " +
					fileNameOnServer);
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
		String reqUrl = getURI("request=delete","names=" + name).toString();
		String response = Util.readResponse(new URL(reqUrl).openStream());
		if (!response.startsWith("OK:")) {
			log.println("Delete of map failed with " + response);
			throw new Exception(response);
		} else {
			log.println("Removal succeeded");
			loadCapabilities(false);
			if (getSourceByName(source.getName()) != null) {
				throw new Exception("Custom map removal succeeded but it is still found!");
			}
		}
	}
}

