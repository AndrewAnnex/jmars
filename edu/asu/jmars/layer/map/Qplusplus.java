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

public class Qplusplus extends Qplus
{
	protected LinkedList aMap; //Now this list a linkedlist
	private static DebugLog log = DebugLog.instance();
	int numRes;

	public Qplusplus(int numRes)
	{
		int i;
		AreaMap [] aM= new AreaMap[numRes];
		
		this.numRes=numRes;

		queue=new LinkedList();
		aMap = new LinkedList();

		for (i=0;i<numRes;i++){
			aM[i]=new AreaMap();
		}

		aMap.add(aM);
	}

	//This function is new, and is needed as datasets are removed
	public void removeQplusQue(int idx)
	{	
		if (idx < aMap.size())
			aMap.remove(idx);
		else
			log.println("Hey! you tried to remove index: "+idx+" but we only go upto: "+(aMap.size() -1));
	}


	//This function is new, and is needed as new datasets are added

	public void addQplusQue()
	{
		int i;
		AreaMap [] aM= new AreaMap[numRes];

		for (i=0;i<numRes;i++){
			aM[i]=new AreaMap();
		}

		aMap.add(aM);
	}	

	public synchronized boolean isBinInMap(int ppd_index,Rectangle2D area,int index)
	{
		AreaMap [] aM = (AreaMap [])aMap.get(index);
		return (aM[ppd_index].areaExists(area));
	}

	public synchronized void addToMap(int ppd_index, Rectangle2D area,int index)
	{
		AreaMap [] aM = (AreaMap [])aMap.get(index);
		aM[ppd_index].addArea(area);
	}

	public synchronized void removeFromMap(BackLayer.WorldImage wi)
	{
		NumBackLayer.NumWorldImage nwi = (NumBackLayer.NumWorldImage) wi;
		int ppd_index=wi.resolutionIndex;
		Rectangle2D area = wi.where;
		int index =nwi.whichDataSet;
		AreaMap [] aM = (AreaMap [])aMap.get(index);

		aM[ppd_index].removeArea(area);
	}
}
