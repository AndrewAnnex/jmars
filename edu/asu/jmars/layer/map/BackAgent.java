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


package edu.asu.jmars.layer.map;

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.geom.*;
import javax.swing.*;
import java.net.*;
import java.io.*;
import java.util.*;


public class BackAgent extends Agent
{
	private static DebugLog log = DebugLog.instance();
//	static public String TILEPATH = "TILES/TileHash.";
	private static int ID=1;
	private int myID=0;
	private Object lock = new Integer(0);

	private static final boolean TILES_DEBUG = null!=Config.get("tiles.debug");


	protected int		tileChoice;
	protected boolean	grid;
	protected String	fileURL;
	protected String	filename;
	protected boolean	global;

	protected	int						fileHash;
	private		Thread                	agentThread;
	private		BackLayer.AgentRequest	ar;
	private		AgentManager.Semephore	as;
	private		boolean					totallyCustom = false;


	protected String server;
	protected String remoteFileName;


	public String getTileName(int i)
	{
		if (i >= (BackPropertiesDiag.tileSet.length - 1)) //db item
			return (fileURL);
		else
			return(BackPropertiesDiag.tileSet[i]);
	}


	private void serverString()
	{
		log.println("REALIZE: your map server is " + server);
	}

//	This constructor exitst so that the request to hashed filename function
//	can be used without the overhead of a seperate thread

	public BackAgent(BackDialogParameterBlock bdpb)
	{
		log.println("Dead Agent Created.  You CANNOT retreive tiles with THIS agent.");

		tileChoice=bdpb.tileChoice;
		global = bdpb.global;
		totallyCustom = bdpb.isCustom;

		if (bdpb.customChoice != null) {
			fileURL = bdpb.customChoice;
			remoteFileName = bdpb.getRemoteHashedName();
		} else {
			fileURL = null;
			remoteFileName = null;
		}

		if (Main.isStudentApplication())
			server = Config.get("tiles.bot");
		else 
			server = bdpb.serverAddress;

	}

	public BackAgent(BackDialogParameterBlock bdpb,
			Object agentRequest, AgentManager.Semephore as)
	{
		ar = (BackLayer.AgentRequest) agentRequest;

		tileChoice = bdpb.tileChoice;
		grid = bdpb.grid;
		global = bdpb.global;
		totallyCustom = bdpb.isCustom;


		log.println("Our data has totallyCustom set to: " + totallyCustom);

		if (bdpb.customChoice != null) {
			fileURL = bdpb.customChoice;
			remoteFileName = bdpb.getRemoteHashedName();
			log.println("RECEIVING REMOTE hashname = " + remoteFileName);
		} else {
			fileURL = null;
			remoteFileName = null;
		}

		server = bdpb.serverAddress;

//		log.println("CONSTRUCTOR: (int tileChoice, String fileURL) ");
//		log.println("tileChoice = "+tileChoice);
//		log.println("fileURL= "+fileURL);
		synchronized(lock) {
			myID = ID;
			ID++;
		}
//		log.println("Agent ID: "+myID);
		serverString();
//		log.println("WIDTHALIGN= "+WIDTHALIGN);
//		log.println("HEIGHTALIGN= "+HEIGHTALIGN);


		this.as = as;

		agentThread = new WatchedThread(this, "Agent");
		agentThread.start();
	}


	/*
	 ** Given a Rectangle containing: Start Time, View angle, width &
	 ** Time durration, Segment and Align request, then generate the
	 ** appropriate sever request string(s) for the desired tile(s)
	 */

	public String makeRequest(BackLayer.WorldImage region, boolean withServer) {
		int version = Config.get("tiles.version", 0);
		Area where = new Area(region.where);
		Dimension2D pixelSize = region.pixelSize;
		int index = region.resolutionIndex;
		// log.println("Received a request for Resolution of index " + index);

		int row;
		int col;
		int count;
		double spread;
		int ppd = (int) Math.ceil(1.0 / pixelSize.getHeight());
		double delta = Main.PO.getDelta((double) ppd);
		double xmin;
		int numTiles = 0;
		double r1, r2;
		int i;

		if (Main.isStudentApplication()) {
			grid = false;
		}
		// log.println( "AGENT SUBMIT:\n X: " + region.where.getX() +
		// "\n Y: " + region.where.getY() +
		// "\n X+W: " + (region.where.getX()+region.where.getWidth())+
		// "\n Y+H: " + (region.where.getY()+region.where.getHeight())+
		// "\n PPD: "+ppd);

		xmin = Main.PO.getXMin(region.where.getX());
		count = (int) Math.ceil(region.where.getWidth() / delta) + 1;
		spread = region.where.getHeight();
		r1 = region.where.getY();
		r2 = r1 + spread;

		row = (int) (spread * ppd);
		if (row == 0) {
			row = 1;
		}
		col = (int) (region.where.getWidth() / Main.PO.getCircumfrence()
				* 360.0 * ppd); // FIX THIS!
		if (col == 0) {
			col = 1;
		}

		String source;

		if (tileChoice >= (BackPropertiesDiag.tileSet.length - 1)) { 
			// It's from the db list
			if(2 == version) {
				source = "&dataimage_token=" + Util.urlEncode(remoteFileName);
			} else {
				if (!totallyCustom) {
					source = "&dataimage_filename="
						+ Util.urlEncode(remoteFileName);
				} else { // it's custom
					String remoteLocation = Config.get("tiles.cust.temploc");
					remoteLocation += remoteFileName;
					source = "&dataimage_filename="
						+ Util.urlEncode(remoteLocation);
				}
			}
		} else {
			source = "&dataimage_token="
				+ BackPropertiesDiag.tileSet[tileChoice];
		}
		if (!global) {
			source += "&partial=1";
		}
		String submit = "&xmin="
			+ (xmin)
			+ // +Main.PO.getServerOffsetX()) +
			"&xcount=" + count + "&xdelta=" + delta + "&ymin=" + r2
			+ "&ycount=2" + "&ydelta=" + (-spread) + "&row=" + row
			+ "&col=" + col + "&grid=" + (grid ? "y" : "") + "&maptype="
			+ Main.PO.getProjectType()
			+ Main.PO.getProjectionSpecialParameters() + source + "&";

		// log.println("Image Row: "+row+" Col: "+col);
		// log.println("Using: "+(server + submit)+" for your requesting
		// string");
		if (withServer)
			return region.serverURLName = server + submit;
		else
			return region.serverURLName = submit;
	}

	public void run()
	{
		String request = null;
		String part1 = TILEPATH;
		BackLayer.WorldImage region = ar.wi;
		DataReceiver agentDataProxy = ar.dr;

//		first we make the request withOUT the server portion
//		so we can hash JUST the request portion
		request = makeRequest(ar.wi, false); 
		log.println("Your parameter request STRING:\n " + request + "\n\n");

		int index = region.resolutionIndex;

		region.ID = myID;


		fileHash = request.hashCode();
		filename = part1 + fileHash;

//		Now we make the request WITH the server portion
//		so that we may execute the request correctly
		request = makeRequest(ar.wi, true);

		request += ("key=" + Main.KEY + "&");

		log.println("Your HASHED request String: " + filename + "\n");

		log.println("Your FULL request STRING:\n " + request + "\n\n");

		/* For now, the agent is only making a single request
		 ** and receiving a SINGLE response (ie single block of
		 ** data).  THIS WILL CHANGE and an agent MAY receive up
		 ** to N pieces of data with POTENTIALLY DIFFERENT
		 ** areas.  For now, it is the same, but it is important
		 ** that the area be determined here and that the
		 ** BackLayer have nothing to do with it (it asks for X,
		 ** it may get X, it may get Y....whatever, it has to
		 ** deal with it.  For, now, it's a one-to-one
		 ** assignment.
		 */

		double bounds1 = region.where.getY();
		double bounds2 = region.where.getY() + region.where.getHeight();
		try {

			// If we're outside of the proper coordinate boundaries
			// for the projection, we want to paint black. So we
			// replace the original request url with the url to the
			// blank.gif image in the images directory.
			if (bounds1 > Main.PO.upperLimit() ||
					bounds1 < Main.PO.lowerLimit() ||
					bounds2 > Main.PO.upperLimit() ||
					bounds2 < Main.PO.lowerLimit() )
			{
				URL requestURL = Main.getResource("images/blank.gif");
				if(requestURL == null)
				{
					log.aprintln("FAILED TO LOAD blank.gif FILE!!");
					throw new FileNotFoundException( "Unable to load blank tile: images/blank.gif");
				}
				request = requestURL.toString();
			}

			log.println("Submitting URL2Disk for ID: " + region.ID + " name: " + filename);

			if(TILES_DEBUG)
				Util.urlToDiskImageDebug(request, filename);
			else
				// Automatically retries up to 3 times
				Util.urlToDisk(3, request, filename);

			/* It is possible we're STILL not getting our file due to network PROBLEMS.
			 ** If that's the case, store a special filename which we WON'T cache so that in the future
			 ** we can try again to call for it.
			 */
			File f = new File(filename);
			if (f.exists())
				region.localDiskName = filename;
			else {
				region.localURLName = Main.getResource("images/Network_Pixel.gif");
				region.localDiskName = null;
			}


		}
		catch(Throwable e)
		{
			log.aprintln("Failed To Load Tile From Mapserver!  Using Error Pixel");
			log.println(log.DARK + "(" + filename + " <- " + request + ")");
			log.println(e);

			region.localURLName = Main.getResource("images/Network_Pixel.gif");
			region.localDiskName = null;

		}

		region.image = null;
		region.resolutionIndex = index;

		log.println("Returning from URL2Disk for ID: " + region.ID + " name: " + filename);

		agentDataProxy.receiveData(region);

		synchronized (as)
		{
			as.increment();
			as.notify();
		}

	} //End Run
} //End BackAgent
