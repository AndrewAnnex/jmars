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
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import java.lang.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.geom.*;
import javax.swing.*;
import java.util.*;
import java.util.Arrays.*;
import java.io.*;
import java.awt.image.renderable.*;


/* Class for background layer */
public class NumBackLayer extends BackLayer
 {
	private static DebugLog log = DebugLog.instance();
//	protected NumBackDialogParameterBlock bdpb;
	protected LinkedList  bdpb = new LinkedList();
	protected LinkedList  worldImages = new LinkedList();
//	protected Qplusplus	qPlus;
	protected static int DEBUG_counter = 0;
	public NumBackLView	myLView=null;

	public	NumBackLayer(BDialogParameterBlock bdpb)
	{

   	initialLayerData = bdpb;

		NumBackDialogParameterBlock nbdpb = (NumBackDialogParameterBlock) bdpb;


		this.bdpb.add(nbdpb);

		numResIndices=(nbdpb.maxResolutionIndex/LocationManager.ZOOM_MULTIPLIER)+1;

   	if ( Main.useNetwork() )
  			  log.println("REALIZE: your map server is " + bdpb.serverAddress);

      log.println("I've started!");
     	init();

	   theThread=new WatchedThread(this,"NumBackLayer");

      theThread.start();
	}


	class NumWorldImage extends WorldImage
	{
		NumBackDialogParameterBlock bdpb;
		int whichDataSet;
		int DEBUG_ID=-2;
/*
		NumWorldImage(BDialogParameterBlock bdpb)
		{
			this.bdpb =  bdpb;
		}
*/

	}
	
/*
** Several other classes referenced the previous PUBLIC incarnation of bdpb.
** Since it's a linked list, we've made it accesable through this function.
*/
	public NumBackDialogParameterBlock getBDPB(int idx)
	{
		if (idx >= bdpb.size() || idx < 0) {
			log.println("Illegal attempt to access BDPB: "+idx+" . Returning null.");
			return null;
		}

		else
			return ((NumBackDialogParameterBlock)bdpb.get(idx));
	}

   protected void init()
    {

		synchronized(buzy)
		{
			buzy.setBusy();
		}
	

/*
** Since this is the INIT call, we can assume a single data layer so far
** Our TileCollection used to be an array, one collection for each resolution.
** Now, we can have MULTIPLE dataset for this layer, so we need to keep a list of
** arrays of tilecollections.  There is a tilecollection array for each data layer present
** and this is dynamically expandable.  We start with one and move on from there.
** The kicker the the add function which the NumBackLView will call when the add button
** is engaged.
*/

		log.println("Initializing Collections");
		log.println("Using "+numResIndices+ " for the tileResolution Index");
		TileCollection[] wI=new TileCollection[numResIndices];

    	for(int i=0;i<numResIndices;i++) {
    		wI[i]=new TileCollection(this,(NumBackDialogParameterBlock)bdpb.get(0),
													 i,new_Agent((NumBackDialogParameterBlock)bdpb.get(0)));

		}

		worldImages.add(wI);

    	numTiles=0;
    	rcount=1;
		log.println("Initializing Qplus");
      qPlus=new Qplusplus(numResIndices);
		if (qPlus == null){
			log.println("QPlus FAILED to initialize!");
		}

		log.println("Initializing Agent Manager");
      am=new AgentManager(qPlus,((NumBackDialogParameterBlock)bdpb.get(0)), this);
		log.println("Initializing MessageQueue");
      messageQueue=new LinkedList();

		synchronized(buzy)
		{
			buzy.setNotBusy();
		}
	
    }

   public Agent new_Agent(BDialogParameterBlock bdpb)
	{
		return (new NumBackAgent((NumBackDialogParameterBlock)bdpb));
	}

   public Agent new_Agent(Object agentRequest, AgentManager.Semephore agentCount)

   {
		int idx=	((NumBackLayer.NumWorldImage)((BLayer.AgentRequest)agentRequest).wi).whichDataSet;

		NumBackDialogParameterBlock tmp_bdpb = (NumBackDialogParameterBlock)bdpb.get(idx);

		log.println("tmp_bdpb = "+tmp_bdpb);

      return (new NumBackAgent(tmp_bdpb, agentRequest,agentCount));
   }


	private int vicarErrorCount = 0;
	public RenderedImage loadBufferedImage(String path) 
	{
	   try
		{
		   return  VicarReader.createBufferedImage(path);
		}
	   catch(VicarException e)
		{
		   log.println(e);
		   return  null;
		}
	}



    /* Responds to requests from BackLView. */

	public void receiveRequest(Object layerRequest, DataReceiver requester)
	{

		if (layerRequest == null) //The panner will send null requests to be ignored!
			return;

		NumWorldImage [] wis = (NumWorldImage []) layerRequest;

		int i;
		AgentRequest   request;
		Integer messagetype = new Integer(0);  //Request message type



		for (i=0;i<wis.length;i++){
			request = new AgentRequest();
			request.wi = wis[i];
			request.dr =requester;

			log.println("Request ["+i+"]: = "+wis[i]);

			synchronized (messageQueue)
			{
//				messageQueue.addLast(messagetype);
				messageQueue.addLast((Object)request);
				messageQueue.notify();
			}
		}
	}

	public void projectionChanged(ProjectionEvent e)
	{
		synchronized(messageQueue)
		{
			synchronized(qPlus)
			{
				if(worldImages != null)
					synchronized(worldImages)
					{
						messageQueue.clear();
						qPlus.clear();
						for(int j =0;j<worldImages.size();j++){
							TileCollection [] wi = (TileCollection [])worldImages.get(j);
							for(int i =0;i<wi.length;i++){
								wi[i].clear();
							}
						}
					}
				else {
					messageQueue.clear();
					qPlus.clear();
				}
			}
		}
	}


	public void processData(Object agentData)
	{
		synchronized(buzy)
		{
			buzy.setBusy();
		}


		NumWorldImage wi = (NumWorldImage) agentData;

		//Permenantly Add the new elemens to our collection

		log.println("Received tile with localDiskName: "+wi.localDiskName);
		log.println("Received tile with DEBUG_ID: "+wi.DEBUG_ID);
		log.println("Received agent with data set id: "+wi.whichDataSet);

		TileCollection [] wI = (TileCollection []) worldImages.get(wi.whichDataSet);

		wI[wi.resolutionIndex].setTile(agentData);

		log.println("Resolution index is " + wi.resolutionIndex);

/*
** Gotta fix the qPlus
*/

		synchronized(qPlus)
		{
			qPlus.removeFromMap(wi);
		}

		//Call out to everyone who can use 'em
		log.println("Sending to LViews:");
		log.println("\tX: " + wi.where.getX() +
						"\tY: " +      wi.where.getY() );
		log.println("\tW: " + wi.where.getWidth() +
						"\tH: " +      wi.where.getHeight());


		log.println("Attempting to load tile: "+wi.localDiskName);

		wi.image=loadBufferedImage(wi.localDiskName);

		log.println("Loaded: "+wi.image);

		log.println("Attempting to BROADCAST data:");
		broadcast(wi);
		 log.println("Broadcast!\n");

		//Update tile count for debug tracking purposes
		++numTiles;

		log.println("NumTile Count: "+numTiles);

		synchronized(buzy){
			buzy.setNotBusy();
		}

	}



/*
** This function takes an index value and searches through the Layer's
** set of exiting tiles (at said resolution) subtracting off intersecing
** tiles from the target area.  If the target area gets completely
** covered, the function returns a 0 otherwise, a 1

** Basically this function is the same as in its parent, however,
** for this derived class, we are handling some internal data structure
** differently, so we needed to override the original function for proper
** code syntax.  In this case, TileCollections are now kept in a linkedlist.

*/
	protected boolean findThisResTiles(WorldImage wi, int index,
                                     DataReceiver layerDataProxy)
	{
		synchronized(buzy)
		{
			buzy.setBusy();
		}


		NumWorldImage			nwi = (NumWorldImage)wi;
		NumWorldImage        chunk; //Temporary Placeholder
		Vector               tiles;
		Area                 source;
		int                  num;
		double               xBin,yBin;
		int                  ppd=(int)Math.pow(2.0,(double)(index* LocationManager.ZOOM_MULTIPLIER));
		int                  i;

		TileCollection[]		wI = (TileCollection[])worldImages.get(nwi.whichDataSet);

//First we iterate through the desired resolution's tiles
//stuff is an Iterator which steps through the worldImage collection one object
//at a time.  getNext() returns the object which is then "acted" upon

		log.println("index=" + index);
		log.println("ppd=" + ppd);
		xBin=Main.PO.getBucketWidth(ppd);
		yBin=Main.PO.getBucketHeight(ppd);

		

		tiles=wI[index].getTiles(wi,xBin,yBin);
		num=tiles.size();
		Area target = new Area(wi.where);

		for(i=0;i<num;i++){
			chunk=(NumWorldImage)tiles.get(i);
			if(chunk.image == null)
			 {
				log.aprintln("===========================================");
				log.aprintln("WE'VE RETRIEVED AN IMAGE, BUT IT'S NULL!!!!");
				log.aprintln("===========================================");
				log.aprintln(chunk.image);
				continue;
			 }
			chunk.whichDataSet=nwi.whichDataSet; //This will have been lost
			source = new Area(chunk.where);
			target.subtract(source);
			log.println("Sending CACHED Image out to: "+layerDataProxy);
			layerDataProxy.receiveData(chunk);

			if(target.isEmpty())
				return(false);
		}
		synchronized(buzy)
		{
			buzy.setNotBusy();
		}
		return(true); //There's still an unclaimed area
	}


/*
** This metodth was overridden only because the Qplus is access and this
** object needs access modifications to track multi-datasets.  Functionally,
** this method is identically to its parent's with the exception of the parameters
** that are sent to the Qplus object.
*/

	public void processRequest(Object req)
	{
		synchronized(buzy)
		{
			buzy.setBusy();
		}

		DataReceiver requester =((AgentRequest)req).dr; //This is the LView's receiver

		NumWorldImage  wi        = (NumWorldImage)((AgentRequest)req).wi;

		if (wi == null) //the panner would send a null request
			return;

		int         ppd       = (int) Math.ceil(1.0 / wi.pixelSize.getHeight());

		int         index     = zoomIndexFromPPD(ppd); //if index > numResIndices

		boolean     corrected = false;

		int         i;
		Iterator    stuff;
		int         empty     = 0;
		boolean     needAgent = false;
		Area        target;

		NumWorldImage  littleTile;

		BackLayer.WorldImage  tmpTile;
		AgentRequest  ar;
		Vector      littleTiles = new Vector();
		int         numLittleTiles=0;

		if (index >= numResIndices) {
			index=numResIndices-1;
			corrected=true;
			if (index < 0) {
				log.printStack(10);
				log.println("HEY: You have a bogus numResIndices Setting!!!!");
			}
		}

		log.println("ATTENTION: whichDataSet="+wi.whichDataSet);
          log.println("ANALYSIS: WI tile [" +
                   wi.where.getX()      + "," +
                   wi.where.getY()      + "," +
                   wi.where.getWidth()  + "," +
                   wi.where.getHeight() + "]..." );

		if (corrected)  //need to modify ppd if we're past our max zoom point for this data
			 ppd = (int)Math.pow(2.0,(double)(index* LocationManager.ZOOM_MULTIPLIER));

		numLittleTiles=binRequestRegion(new Area(wi.where),littleTiles,ppd);

//Ohhh this is SOOOOOO bad
		((NumBackLView)requester).setTileCount(numLittleTiles);

		log.println("binRequestRegion returned: "+littleTiles.size());

		for (i=0;i<numLittleTiles;i++){
			littleTile = new NumWorldImage();
			tmpTile=(BackLayer.WorldImage)littleTiles.get(i);
         log.println("ANALYSIS: temp tile ("+i+")  [" +
                   tmpTile.where.getX()      + "," +
                   tmpTile.where.getY()      + "," +
                   tmpTile.where.getWidth()  + "," +
                   tmpTile.where.getHeight() + "]..." );
			littleTile.where = tmpTile.where;
			
			if (corrected)
				littleTile.pixelSize=new Dimension2D_Double(Main.PO.getUnitWidth()/(double)ppd,
																			Main.PO.getUnitHeight()/(double)ppd);

			else
				littleTile.pixelSize=wi.pixelSize;


			littleTile.resolutionIndex=index;
			littleTile.whichDataSet=wi.whichDataSet;	
			littleTile.DEBUG_ID = DEBUG_counter++;

			target=new Area(littleTile.where);
			needAgent=false;

          log.println("Looking for Tile ("+i+")  [" +
                   littleTile.where.getX()      + "," +
                   littleTile.where.getY()      + "," +
                   littleTile.where.getWidth()  + "," +
                   littleTile.where.getHeight() + "]..." );

			log.println("Tile "+i+" also has:");
			log.println("\tpixelSize = "+littleTile.pixelSize);
			log.println("\tresolutionIndex = "+littleTile.resolutionIndex);
			log.println("\twhichDataSet = "+littleTile.whichDataSet+"\n\n");
			log.println("\tDEBUG_ID = "+littleTile.DEBUG_ID+"\n\n");



			if(findThisResTiles(littleTile, index, requester))  // Not Empty
        	{
				if ( Main.useNetwork() ) 
					needAgent = true;
			}

			if(needAgent){
				boolean pending=false;
				synchronized (qPlus) //check to see if the request is pending
				{
					boolean res=((Qplusplus)qPlus).isBinInMap(index,littleTile.where,littleTile.whichDataSet);
					if (res)
					{
						pending=true;
					}
				}

				if (pending){
					continue;
				}

				else {
					log.println("Generating NEW request Object");
					ar=new AgentRequest();
				}

				ar.dr=this; //We want to set the receiver function to the LAYER now
				ar.wi=littleTile;
				ar.layer=this;

				findRemainingData(ar);

//				findOtherResTiles(littleTile, index, requester); //Don't want OTHER resolutions!
			}
		}

		synchronized(buzy)
		{
			buzy.setNotBusy();
		}

	}

/*
** This metodth was overridden only because the Qplus is access and this
** object needs access modifications to track multi-datasets.  Functionally,
** this method is identically to its parent's with the exception of the parameters
** that are sent to the Qplus object.
*/
	protected void findRemainingData(AgentRequest request)
	{
		synchronized(buzy)
		{
			buzy.setBusy();
		}

		Rectangle2D     intermediary = request.wi.where;

		synchronized(qPlus)
		{
			log.println("Submitting Agent Request to Queue");
			qPlus.addToQueue((Object)request);
			((Qplusplus)qPlus).addToMap(request.wi.resolutionIndex ,request.wi.where,
								((NumWorldImage)request.wi).whichDataSet);
			qPlus.notify();
		}

		synchronized(buzy)
		{
			buzy.setNotBusy();
		}

	}

	public void removeDataSet(int idx)
	{
		if (idx < bdpb.size())
			bdpb.remove(idx);
		else
			log.println("Hey! you tried to remove index: "+idx+" but we only go upto: "+( bdpb.size()-1));

		if (idx < worldImages.size())
			worldImages.remove(idx);
		else
			log.println("Hey! you tried to remove index: "+idx+" but we only go upto: "+(worldImages.size() -1 ));

		((Qplusplus)qPlus).removeQplusQue(idx);
	}

	public void addDataSet(NumBackDialogParameterBlock nbdpb)
	{
		log.println("This is where We'd make LAYER additions!");

		bdpb.add(nbdpb);
		
		int idx = bdpb.size()-1;
		int[] vals = new int[bdpb.size()];
		Arrays.fill(vals, 0);
		((NumBackFocus)myLView.focusPanel).addElement(
				new Color[]{ myLView.getColor(idx) },
				new String[] { myLView.getName(idx) },
				vals);
		
		((Qplusplus)qPlus).addQplusQue();
		TileCollection[] wI=new TileCollection[numResIndices];

    	for(int i=0;i<numResIndices;i++) {
    		wI[i]=new TileCollection(this,nbdpb,i,new_Agent(nbdpb));

		}
		
		worldImages.add(wI);
	}

}

