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


package edu.asu.jmars.util;

import java.util.LinkedList;

/**
 * Implementation of a generic thread which runs the
 * added runnables in a serialized fashion.
 * 
 * Usage:
 * 1) Create an instance of this thread and start it.
 * 2) Add new tasks to it using its add(request) method.
 *    The requests are Runnable objects.
 * 
 * Thread's run method catches and absorbs all the
 * Exceptions thrown in running a request.
 * 
 * @author saadat
 *
 */
public class SerializingThread extends Thread {
	private static final DebugLog log = DebugLog.instance();

	/**
	 * Add this request to the SerializingThread to make it quit.
	 */
	public static final Runnable quitRequest = new Runnable(){
		public void run(){}
	};
	
	public SerializingThread(String name){
		super(name);
		
		outstanding = new LinkedList();
		busy = false;
	}
	
    public synchronized void add(Runnable req){
        // Add the request to the outstanding requests queue.
        outstanding.add(req);

        // Signal the worker thread to pickup this request and run with it.
        notify();
    }

    private synchronized Runnable getNextRequest(){
    	busy = false;
        while(outstanding.size() == 0){
            try { wait(); }
            catch(InterruptedException ex){}
        }
        busy = true;
        Thread.interrupted();
        return (Runnable)outstanding.removeFirst();
    }
    
    public synchronized int getOutstandingRequestsCount(){
    	return outstanding.size();
    }
    
    public synchronized boolean isIdle(){
    	return (!busy && outstanding.size() == 0);
    }
    
    /**
	 * Interrupts any current activity, and if flushPending is true clears any
	 * future activity, and returns whether a flush occurred
	 */
    public synchronized boolean interruptIfBusy(boolean flushPending){
    	if (!isIdle()){
    		interrupt();
    		if (flushPending) {
    			outstanding.clear();
        		return true;
    		}
    	}
    	return false;
    }
    
	public void run() {
		while (true) {
			req = getNextRequest();
			if (req == quitRequest)
				return;
			try {
				req.run();
			} catch (Exception ex) {
				log.aprintln(ex);
			}
			req = null;
		}
	}
	
	Runnable req;
	LinkedList outstanding;
	boolean busy;
}
