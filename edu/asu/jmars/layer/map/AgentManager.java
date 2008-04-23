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
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.geom.*;
import javax.swing.*;
import java.util.*;
import java.util.Arrays.*;
import java.io.*;


/**
 * Title:        JMARS
 * Description:  Mission Planner
 * Copyright:    Copyright (c) 2001
 * Company:      ASU
 * @author ...
 * @version 1.0
 */

public class AgentManager implements Runnable, Serializable
{



  public static class Semephore
  {
    private int count;
    private int top;
	 private Layer myLayer;

    public Semephore(int count, Layer myLayer)
    {
      this.count=count;
      top=count;
		this.myLayer=myLayer;
    }

    public synchronized void decrement()
    {
      count--;
		if (count == 0)
			myLayer.setStatus(Color.red);
		else
			myLayer.setStatus(Color.pink);
		

    }

    public synchronized void increment()
    {
      count++;
      if (count > top)
        count=top;

		if (count < top)
			myLayer.setStatus(Color.pink);
		else
			myLayer.setStatus(Util.darkGreen);

    }

    public synchronized void setMaxCount(int mc, boolean reset)
    {
      top=mc;
      if (reset)
        resetSemephore();

    }

    public synchronized void resetSemephore()
    {
      count=top;
		myLayer.setStatus(Util.darkGreen);

    }

    public synchronized int getCount()
    {
      return(count);
    }

  }




  private   Qplus     qplus;
  private   int       tileChoice; //Agent parameter data
  private   String    fileURL=null;    //Agent parameter data
  protected Semephore agentCount;
  protected int       MAXAGENTCOUNT; //default max agent value
  protected BDialogParameterBlock bdpb;
  

  private   Thread    	agentManagerThread;
  private static DebugLog log=DebugLog.instance();
  private   Agent 		agent;
  private	BLayer 		myLayer;



  public AgentManager(Qplus qplus, 
							 BDialogParameterBlock bdpb,
							 BLayer bl)
  {
    this.qplus=qplus; // Hooked up the Qplus datastructure between the backlayer
                      // and this Agent Manager

	 this.bdpb=bdpb;
	 myLayer=bl; //This is purely for signalling buzy waits and so forth

	 MAXAGENTCOUNT= bdpb.maxAgents;

    agentCount=new Semephore(MAXAGENTCOUNT, myLayer);

    agentManagerThread=new WatchedThread(this,"AgentManager");
    agentManagerThread.start();

  }

  public void setMAXAGENTCOUNT(int mac)
  {
    MAXAGENTCOUNT=mac;
    agentCount.setMaxCount(mac,true);
  }

  public int getMAXAGENTCOUNT()
  {
    return (MAXAGENTCOUNT);
  }

  public boolean agentsDelegated()
  {
	synchronized(agentCount)
	{
		if (agentCount.getCount() < MAXAGENTCOUNT)
			return(true);
		else
			return(false);
	}
  }


  protected boolean noValidView(BackLayer.WorldImage wi)
  {
	
	 if (wi.validViewExists())
		return(false);

	 synchronized (qplus)
	 {
		qplus.removeFromMap(wi);
	 }

	 return(true);

  }
	
  public boolean isBusy()
  {
		synchronized(agentCount)
		{
			if (agentsDelegated())
				return(true);
			else
				return(false);

		}
  }


  public void run()
  {

    BackLayer.WorldImage    wi;
    DataReceiver            dr;
    BackLayer.AgentRequest  ar;

    log.println("I've started!");

	 myLayer.setStatus(Util.darkGreen);
    while (true) { //run forever -- change this to some more pleasant way to shut down

      synchronized (qplus)
      {

        while (qplus.isQueueEmpty()) //if queue is empty...sleep my darling
		 {
			myLayer.notifyForIdle();
			try
			 {
				qplus.wait();
			 }
			catch (InterruptedException e)
			 {
				log.println("Interrupt Exception thrown from qPlus...yeow!!");
			 }
		 }
		  myLayer.setStatus(Color.yellow);
        ar=(BLayer.AgentRequest) qplus.removeFromQueue();
		  log.println("Received requested from Que");

        wi= ar.wi;
        dr= ar.dr;

      }//End Syncronized


      //Check if there is still some view that contains this request
      if (noValidView(wi)) {
		  log.println("This request is NO LONGER valid: "+wi.where);
        continue;
		}

       //Okay, do a semephore count, if we have room, call an agent, other-wise
      //snooze until we have a free agent
      synchronized(agentCount)
      {
		  boolean weHaveWaited = false;
        agentCount.decrement();
        while (agentCount.getCount() == 0)
          try { 
				log.println("Reached Max Agent count: "+ MAXAGENTCOUNT+"...Sleeping");
				myLayer.notifyForIdle();
				weHaveWaited = true;
				agentCount.wait();
				log.println("I'm AWAKE now!");
          }
          catch (InterruptedException e)
          {
              log.println("Interrupt Exception thrown from a wait...ouch!!");
          }

		  // If we wait()ed, then there's a significant chance that the
		  // user panned or re-projected, so we'll check one more time
		  // if the request is relevant.
		  if(weHaveWaited  &&  noValidView(wi))
				synchronized(agentCount)
				 {
					 log.println("This request is NO LONGER valid (last minute): " +
									  wi.where);
					 agentCount.increment();
					 agentCount.notify();
					 continue;
				 }

		  log.println("CREATING Agent for request:");
	     log.println("\tX:" + (int) ar.wi.where.getX() +
               "\tY:" +       ar.wi.where.getY() );
        log.println("\tW:" + (int) ar.wi.where.getWidth() +
               "\tH:" +       ar.wi.where.getHeight() );

      	log.println("\tRes index: " + ar.wi.resolutionIndex);
  
			
        agent=myLayer.new_Agent(ar, agentCount);

      } // End Synchronized

    }//End While


  }// End run

}//End AgentManager
