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


package edu.asu.jmars;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.awt.geom.*;
import java.io.*;

class Slideshow implements Runnable
 {
    private static final DebugLog log = DebugLog.instance();

    private BufferedReader bin;

    Slideshow(InputStream in)
     {
	this.bin = new BufferedReader(new InputStreamReader(in));
     }

    public void run()
     {
	String line = null;
	try
	 {
	    while((line = bin.readLine()) != null)
		processInput(line);

	    log.aprintln("SLIDESHOW ENDED DUE TO EOF!");
	 }
	catch(Throwable e)
	 {
	    log.aprintln(e);
	    log.aprintln("SLIDESHOW TERMINATED DUE TO ABOVE!");
	    log.aprintln("Last line read: " + line);
	 }
     }

    private void processInput(String line)
     {
	String[] args = line.split("\\s+");
	if(args.length == 0)
	    return;

	if(args[0].equals("center")  &&  args.length == 4)
	 {
	    double tol = Double.parseDouble(args[1]);
	    double lon = Double.parseDouble(args[2]);
	    double lat = Double.parseDouble(args[3]);

	    // USER east lon => JMARS west lon
	    lon = 360 - lon;

	    LViewManager mainVMan = Main.testDriver.mainWindow;
	    LocationManager locMgr =
		Main.testDriver.mainWindow.getLocationManager();

	    Point2D worldPt = mainVMan.getProj().spatial.toWorld(lon, lat);
	    if(Math.abs(worldPt.getY()) > tol)
	     {
		Main.setProjection(new ProjObj.Projection_OC(lon, lat));
		worldPt = Main.PO.convSpatialToWorld(lon, lat);
	     }
	    locMgr.setLocation(worldPt, true);
	 }
	else
	    log.aprintln("Skipping unrecognized input: " + line);
     }
 }
