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

import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
/**
 ** a class for controlling multiple threads.  If a method grabs a lock, any other method which
 ** also tries to get the lock will be blocked.  It will stay blocked until the locked method 
 ** releases the lock.  This effectively makes all the code between the 
 ** call to lock() and unlock() a critical section, at least as far as other locking methods 
 ** are concerned.
 **
 ** Both methods defined in this class may be invoked with an string indicating the name of the 
 ** method that called it. The log lines are part of the JMARS debuglog system and will only 
 ** displayed on the screen, if there is a line for this file in the .debugrc file, 
 ** namely  "+Lock.*"  The name argument is optional.
 **
 ** @author: James Winburn MSFF-ASU  05/28/03
 */
public class Lock {
	
	private static DebugLog log = DebugLog.instance();

	// The lock itself.  Note that this is static. There can only be one!
	private static boolean locked = false;

	final static int TIME_OUT_PERIOD = 5 * 1000;

	// Attempts to get the lock.  If it is successful, any other method which similarly tries
	// to get the lock will block.
	//
	// As a profilactic against deadlock, a blocked method will wake up after a time even if 
	// the lock has not been released.
	//
	// It is very important for a method which acquires the lock to release it at all possible 
	// return points.
	synchronized public void lock( String s) {
		if (s != null) {
			log.println("Attempting to get lock for " + s);
		}

		while (locked){
			try {
				wait( TIME_OUT_PERIOD);
			} catch (InterruptedException e) {
				log.println("Error in trying to wait for lock.");
			}
		}
		locked = true;
		if (s != null ){
			log.println("Got lock for " + s);
		}
	}


	// Releases the lock which the calling method snatched up.  One of the methods that
	// may have been blocked in trying to get the lock will be roused up from its tupor.
	synchronized public void unlock( String s) {
		if (s != null){
			log.println("Releasing lock for " + s);
		}
		locked = false;
		notify();
	}

	public synchronized boolean isLocked(){
		return locked;
	}
}
