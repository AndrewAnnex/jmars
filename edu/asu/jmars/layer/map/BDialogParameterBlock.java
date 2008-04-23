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
import edu.asu.jmars.layer.map2.CustomMapServer;
import edu.asu.jmars.layer.map2.MapServerFactory;
import edu.asu.jmars.util.*;
import java.awt.geom.*;
import java.net.*;

public abstract class BDialogParameterBlock extends DialogParameterBlock
 {
	public	int	     tileChoice;
	public	String	  customChoice=null;
	public   int        maxAgents;
	public   String     serverAddress;
	public   int        maxResolutionIndex;
	public   boolean	  global = true;
	public   Rectangle2D boundry=null;
	public	boolean		isCustom = false;

	private static DebugLog log = DebugLog.instance();


	public final static int NOFILESPECIFIED = 0;
	public final static int LOCALFILE = 1;
	public final static int REMOTEFILE = 2;

	// Required for proper behavior when using these things as keys
	// in a Map.
	public abstract boolean equals(Object obj);

	public int getCustomFileType() {

	   if ( customChoice == null || customChoice.length() == 0 )
		   return NOFILESPECIFIED;

	   if ( customChoice.startsWith("http://") || customChoice.startsWith("ftp://") )
		   return REMOTEFILE;

	   return LOCALFILE;

	}

	public String getBaseFileName()
	 {
		String first;
		String next;
		int start;
		int middle;

		if (customChoice==null) {
		   return("NULL");
		}

		if (getCustomFileType() == REMOTEFILE ) {
		   start = customChoice.indexOf("://");
		   if (start < 0) {
			  log.println("Poorly formed remote request!");
			  return("FOO");
		   }
		   start +=3;
		   first = customChoice.substring(3);

		}

		else if (getCustomFileType() == LOCALFILE ) {
		   first = customChoice;
		}

		else {
		   log.aprintln("You REALLY shouldn't call this method for NON-custom selections!");
		   return("FOOBAR");
		}

		middle = first.lastIndexOf('/');
		if (middle < 0) {
		   middle = first.lastIndexOf('\\'); //Maybe it's WINDOWS?
		   if (middle < 0) { //No Path???  We're in the root directory?
			  return(first);
		   }
		}
		middle++;

		next = first.substring(middle);

		return(next);
	 }
					

	public String getRemoteHashedName() 
	 {
		if (hashedName == null)
			setRemoteHashedName();

		return(hashedName);
	 }


			
	public void setRemoteHashedName(String hn)
	 {
		hashedName = hn;
		log.println("SETTING hashed name to: "+hn);
	 }

	public void setRemoteHashedName() {
		hashedName = customChoice;

		int version = Config.get("tiles.version", 0);
		
		try {
			if (Main.USER == null) {
				hashedName = InetAddress.getLocalHost().getHostName()
						+ "." + String.valueOf(customChoice.hashCode());
			} else if (isCustom && version == 2) {
				CustomMapServer cs = MapServerFactory.getCustomMapServer();
				hashedName = cs.getCanonicName(customChoice);
			} else {
				hashedName = Main.USER
						+ "." + String.valueOf(customChoice.hashCode());
			}
		} catch (Exception e) {
			// ignore error
			hashedName = String.valueOf(customChoice.hashCode());
		}
	}



	// Required for proper behavior when using these things as keys
	// in a Map.
	public int hashCode()
	 {
		// The hash codes included here should correspond to all or
		// some of the comparisons in the equals() method
		// above... but there should NEVER be hash codes here that
		// aren't involved in the equals() method. See the
		// documentation of the Object class in the javadocs.
		return  tileChoice
			^   (customChoice==null ? 0 : customChoice.hashCode());
	 }
 }



