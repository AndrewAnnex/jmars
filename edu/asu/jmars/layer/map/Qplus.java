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
import java.net.*;
import java.io.*;
import java.util.*;

public class Qplus
{
	private static DebugLog log = DebugLog.instance();

	public class AreaMap
	{
		private 		HashMap     map;

		public	AreaMap()
		{
        map=new HashMap();
		}

		/**
		 ** Resets the area map back to an empty state
		 **/
		public synchronized void clear()
		 {
			 map.clear();
		 }

		public  void	addArea(Rectangle2D area)
		{
        TileCollection.MyPoint2D    hashPoint=	new TileCollection.MyPoint2D();

		  log.println("Adding");
		  hashPoint.setLocation(area.getX(),area.getY());
		  map.put(hashPoint,area);
		}

		public  void	removeArea(Rectangle2D area)
		{
        TileCollection.MyPoint2D    hashPoint=	new TileCollection.MyPoint2D();

        hashPoint.setLocation(area.getX(),area.getY());
        map.remove(hashPoint);

		}

		public boolean areaExists(Rectangle2D area)
		{
        TileCollection.MyPoint2D    hashPoint=	new TileCollection.MyPoint2D();

        hashPoint.setLocation(area.getX(),area.getY());

        return(map.containsKey(hashPoint));

      }

	}

	protected LinkedList queue;
	protected AreaMap	aMap[];

	//The Requested Object MUST contain at the least, the Area Object


	public Qplus(){}
	public Qplus(int numRes)
	{
		int i;
		queue=new LinkedList();
      aMap=new AreaMap[numRes];
      for (i=0;i<numRes;i++){
        aMap[i]=new AreaMap();
      }
    }

	/**
	 ** Resets the qplus back to an empty state
	 **/
	public synchronized void clear()
	 {
		 queue.clear();
		 if(aMap != null)
			 for(int i=0; i<aMap.length; i++)
				 if(aMap[i] != null)
					 aMap[i].clear();
	 }

	public synchronized boolean isQueueEmpty()
	{
    return (queue.isEmpty());
	}

	public synchronized void addToQueue(Object request)
	{
		queue.addLast(request);
	}

	public synchronized Object removeFromQueue()
	{
		if (queue.isEmpty()) {
			log.aprintln("ERROR: Attempting to remove from an empty queue!");
			return (null);
		}

		return(queue.removeFirst());
	}

	public synchronized boolean isBinInMap(int ppd_index,Rectangle2D area)
	{
      return (aMap[ppd_index].areaExists(area));
	}

	public synchronized void addToMap(int ppd_index, Rectangle2D area)
	{
      aMap[ppd_index].addArea(area);
	}

	public synchronized void removeFromMap(BackLayer.WorldImage wi)
	{
		int ppd_index=wi.resolutionIndex;
		Rectangle2D area = wi.where;
      aMap[ppd_index].removeArea(area);
   }
}


