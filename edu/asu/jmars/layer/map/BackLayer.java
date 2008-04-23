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
import java.net.*;

/* Class for background layer */
//public class BackLayer extends Layer implements DataReceiver, Runnable, Serializable
public class BackLayer extends BLayer
 {


	public class BusyTracker
	{
		protected boolean busy=false;

		synchronized public void setBusy()
		{
			busy=true;
		}

		synchronized public void setNotBusy()
		{
			busy=false;
		}

		synchronized public boolean areWeBusy()
		{
			return(busy);
		}
	}

	protected class WorldImage implements Cloneable, Serializable
	{
		public RenderedImage image;
		public Rectangle2D   where;
		public Dimension2D   pixelSize;
		public int           resolutionIndex;
		public String			localDiskName;
		public URL				localURLName=null;
		public int				ID=-1;
		public ProjObj       originalPO = Main.PO;
        public String serverURLName;

		public Object clone()
		{
			try {
				return(super.clone());
			}
			catch (CloneNotSupportedException e){}
			return(null);
		}

		private void writeObject(ObjectOutputStream s) throws IOException {

				s.writeObject(new Double(where.getX()));
				s.writeObject(new Double(where.getY()));
				s.writeObject(new Double(where.getWidth()));
				s.writeObject(new Double(where.getHeight()));

            s.writeObject(pixelSize);
            s.writeObject(new Integer(resolutionIndex));
            s.writeObject(localDiskName);
            s.writeObject(localURLName);
            s.writeObject(new Integer(ID));

		}

		private void readObject(ObjectInputStream s) throws IOException  {
			try{
					Integer tmp;
					Double x,y,w,h;


					x = (Double) s.readObject();
					y = (Double) s.readObject();
					w = (Double) s.readObject();
					h = (Double) s.readObject();

					where=new Rectangle2D.Double(x.doubleValue(),
															y.doubleValue(),
															w.doubleValue(),
															h.doubleValue());

					pixelSize = (Dimension2D) s.readObject();

					tmp = (Integer) s.readObject();
					resolutionIndex=tmp.intValue();

					localDiskName = (String) s.readObject();
					localURLName = (URL) s.readObject();

					tmp = (Integer)s.readObject();
					ID=tmp.intValue();

					image=null;	
			}

			catch(ClassNotFoundException e)
			{
				log.println(e);
			}
		}

		public boolean	validViewExists()
		{
		   log.println("Trying " + this.where);
			if (Main.isBot())
				return(true);

			// Just in case this image is from a different projection
			if(originalPO != Main.PO)
			 {
				 // This is "normal" behavior, but is important for debugging
				 log.println("IGNORING WORLDIMAGE... OUTDATED PROJECTION!");
				 return  false;
			 }

			Iterator		itr = getReceivers();
//We're interacting with the View...subtract offset

			double x = this.where.getX()-Main.PO.getServerOffsetX();
			double y = this.where.getY();
			double w = this.where.getWidth();
			double h = this.where.getHeight();
			if(!Main.inTimeProjection())
				x -= Math.floor(x / 360) * 360;

			MultiProjection proj;
			Dimension2D size;

			int pixsize;
			int reqRez=(int)Math.pow(2.0,(double)(resolutionIndex));
			while (itr.hasNext()) {
					LView lview;
					Object nextThing = itr.next();
					log.println("Object class = "+(nextThing.getClass()).getName());

					lview = (LView)nextThing;
					if (lview == null) {
						log.aprintln("NULL LVIEW in the INTERATOR!");
						continue;
					}

					// Views that were deleted in another thread
					if(!lview.isAlive())
						continue;

					proj=lview.getProj();

					if (proj == null) {
						log.aprintln("NULL PROJECTION in the LVIEW");
						continue;
					}

					size=proj.getPixelSize();

					if (size == null) {
						log.aprintln("NULL PIXELSIZE in the PROJECTION");
						continue;
					}

					pixsize=(int)(1.0/size.getHeight());
					Shape view = proj.getWorldWindowMod();

					if (view == null) {
						log.aprintln("NULL WORLDWINDOW in the LVIEW!");
						continue;
					}

					if (reqRez <= pixsize) //The resolution of this view qualifies
						if(view.intersects(x, y, w, h)  ||
						   (!Main.inTimeProjection()  &&
							view.intersects(x-360, y, w, h)))
						 {
							log.println("\tFOUND!");
							return  true;
						 }
			}

			log.println("\tnot found");
			return(false);
		}

		public String toString()
		 {
			return  "WorldImage:\n" +
				"\tID = " + ID + "\n" +
				"\timage = " + image + "\n" +
				"\tlocalDiskName = " + localDiskName + "\n" +
				"\tlocalURLName = " + localURLName + "\n" +
				"\tserverURLName = " + serverURLName + "\n" +
				"\toriginalPO = " + originalPO + "\n" +
				"\tpixelSize = " + pixelSize + "\n" +
				"\tresolutionIndex = " + resolutionIndex + "\n" +
				"\twhere = " + where;
		 }
	}

   public boolean isBackLayerBusy()
	{
		if (messageQueue == null)
			return true;

		log.println("123:Locking Message Queue");
	   synchronized(messageQueue)
		{
		   if(!messageQueue.isEmpty()) {
				log.println("123(if):UNLocking Message Queue");
			   return  true;
			}
		}
		log.println("123:UNLocking Message Queue");

		log.println("221:Entering qPlus locked area");
	   synchronized(qPlus)
		{
			if (!qPlus.isQueueEmpty()) {
				log.println("Leaving(if) qPlus locked area");
				return(true);
			}
		}
		log.println("Leaving qPlus locked area");

//Add AgentManager CheckHere
		if (am.isBusy())
			return(true);
	
		synchronized (buzy)
		{
			if (buzy.areWeBusy())
				return(true);
		}

		return(false);

	}


/* MOVED THIS INTO BASE CLASS
   protected static class AgentRequest implements Serializable
   {
      public WorldImage   wi;
      public DataReceiver dr;
		public Layer	  	  layer=null;
   }

*/


	private static DebugLog log = DebugLog.instance();

	protected int       numTiles;
//NEW DATASTRUCTURE tileCollection
	protected TileCollection[]  worldImages;

//	protected BackAgent agent;
	protected int       rcount;
	protected int			tileChoice=0;
	protected String		fileURL=null;
	protected int			numResIndices;
   protected Qplus      qPlus;
   protected AgentManager am;
   protected LinkedList messageQueue;
	protected BackDialogParameterBlock bdpb;
	public 	 BusyTracker	buzy=new BusyTracker();

	protected	boolean		global=true;		//Used for partial data sets
	protected	Rectangle2D boundry = null; 	//If outside a partial's boundary, skip the request


   protected   Thread     theThread;

	public boolean ready()
	{
		boolean result=false;

		if (qPlus == null)
			return (false);

		if (am == null)
			return (false);

		if (messageQueue == null)
			return (false);

		return(true);
	}

	public boolean agentManagerBusy()
	{	
		boolean result;
		result=am.agentsDelegated();
		return(result);
		
	}

	public BackLayer()
	{
		/*This is only needed for derived classes*/
	}


	public BackLayer(DialogParameterBlock bdpb) {
		/* Save these parameters for restarting session data */

		initialLayerData = bdpb;

		this.bdpb = (BackDialogParameterBlock) bdpb;
		global = this.bdpb.global;
		if (!global)
			boundry = new Rectangle2D.Double(this.bdpb.boundry.getX(),
					this.bdpb.boundry.getY(), this.bdpb.boundry.getWidth(),
					this.bdpb.boundry.getHeight());

		if (Main.useNetwork())
			log.println("REALIZE: your map server is "
					+ this.bdpb.serverAddress);

		numResIndices = (this.bdpb.maxResolutionIndex / LocationManager.ZOOM_MULTIPLIER) + 1;

		log.println("I've started!");

		init();

		// theThread=new WatchedThread(this,"BackLayer");

		String threadName;
		int i = this.bdpb.tileChoice;
		String custom = this.bdpb.customChoice;
		if (this.bdpb.getCustomFileType() == BDialogParameterBlock.REMOTEFILE) {
			String r;
			try {
				r = (new URL(custom)).getFile();
				r = Util.getBaseFileName(r);
			} catch (MalformedURLException e) {
				r = "BackLayer";
			}
			threadName = r;
		} else if (this.bdpb.getCustomFileType() == BDialogParameterBlock.LOCALFILE)
			threadName = Util.getBaseFileName(custom);
		else
			threadName = BackPropertiesDiag.items[i];

		threadName = threadName.substring(0,
				((threadName.length() < 12) ? threadName.length() : 12));

		theThread = new WatchedThread(this, threadName);

		theThread.start();
	}

	public void dumpTileCollections()
	{
		int i;
		for(i=0;i<numResIndices;i++){
			worldImages[i].dumpCollection();
		}
	}

   protected void init()
    {

		synchronized(buzy)
		{
			buzy.setBusy();
		}
	
		log.printStack(5);

		log.println("Initializing Collections");
		log.println("Using "+numResIndices+ " for the tileResolution Index");
    	worldImages=new TileCollection[numResIndices];

    	for(int i=0;i<numResIndices;i++)
    		worldImages[i]=new TileCollection(this,bdpb,i,new_Agent(bdpb));

    	numTiles=0;
    	rcount=1;
		log.println("Initializing Qplus");
      qPlus=new Qplus(numResIndices);
		log.println("Initializing Agent Manager");
      am=new AgentManager(qPlus,bdpb, this);
		log.println("Initializing MessageQueue");
      messageQueue=new LinkedList();

		synchronized(buzy)
		{
			buzy.setNotBusy();
		}
	
    }



/* This function is the core of BackLayer
    From run, the BackLayer thread blocks on its messageque and only
    "runs" when there is either data or a request for data...other-wise
    it is asleep.  When there is a message, the Backlayer extracts a message
    from the queue and then determins which kind of message (a value of 0
    means a request, while a value of 1 means data).  Then the BackLayer
    removes the next message (message are ALWAYS added in pairs: message type
    followed by message) and acts on it acording to its type.
*/

    public void run()
     {
        log.printStack(7);
        log.println("Using messageQueue: "+messageQueue);

        while (true)
         {
            Object message;

            // Get the next message (or wait() if there isn't one)
				
				log.println("652:Locking Message Queue");
            synchronized (messageQueue)
             {
                while (messageQueue.isEmpty())
                    try
                     {
								 log.println("No Messages...I'm going to sleep");
                        notifyForIdle();
                        messageQueue.wait();
								log.println("I'm Awake! And the queue contiains this many entires: "+messageQueue.size());
                     }
                    catch (InterruptedException e)
                     {
                        log.aprintln(e);
                     }
                message = messageQueue.removeFirst();
             }
				log.println("652:UNLocking Message Queue");
            log.println("RECEIVED message: type = " + message.getClass());

            if(message instanceof AgentRequest)
                processRequest( (AgentRequest) message );

            else if(message instanceof WorldImage)
                processData( (WorldImage) message );

            else
                log.aprintln("INVALID MESSAGE TYPE: " + message.getClass());

         }// End While(true)

     }//End run

    protected void iterator(Area where)
     {
        PathIterator pi = where.getPathIterator(null);
        double[]     coord = new double[6];

        int count = 1;
        while(!pi.isDone())
         {
            pi.currentSegment(coord);
            log.println("Point " + count++
                        + " is   X: "+(int)coord[0]+"  Y: "+coord[1]);
            pi.next();
        }
        if (count==1)
            log.println("EMPTY!!");
     }


    public void receiveData(Object agentData)
     {
		  WorldImage wi = (WorldImage) agentData;
		  if(wi.originalPO != Main.PO)
				return;

			log.println("345:Locking Message Queue");
        synchronized(messageQueue)
        {
			  messageQueue.addLast(wi);
			  messageQueue.notify();
        }
			log.println("345:UNLocking Message Queue");
     }

	 /**
	  ** On a projection change, we must flush all cached requests and
	  ** data, from both the layer and the agent manager.
	  **/
	 public void projectionChanged(ProjectionEvent e)
	  {
			log.println("937:Lokcing Message Queue");
		  synchronized(messageQueue)
			{
				log.println("467:Entering qPlus locked area");
				synchronized(qPlus)
				 {
					if(worldImages != null)
						synchronized(worldImages)
						 {
							messageQueue.clear();
							qPlus.clear();
							for(int i=0; i<worldImages.length; i++)
								worldImages[i].clear();
						 }
					else
					 {
						messageQueue.clear();
						qPlus.clear();
					 }
				 }
				log.println("Leaving qPlus locked area");
			}
			log.println("937:UNLokcing Message Queue");
	  }

     public void processData(Object agentData)
     {
		 synchronized(buzy){
			buzy.setBusy();
		 }
	

        WorldImage wi = (WorldImage) agentData;

        //Permenantly Add the new elemens to our collection

		  log.println("Received tile with localDiskName: "+ (wi.localDiskName == null ? wi.localURLName.toString() : wi.localDiskName));
		  log.println("Received tile with ID: "+wi.ID);

		  synchronized(worldImages)
			{
				worldImages[wi.resolutionIndex].setTile(agentData);
			}

        log.println("Resolution index is " + wi.resolutionIndex);
			
			log.println("509:Entering qPlus locked area");
        synchronized(qPlus)
        {
          qPlus.removeFromMap(wi);
        }
			log.println("Leaving qPlus locked area");

        //Call out to everyone who can use 'em
        log.println("Sending to LViews:");
        log.println("\tX: " + wi.where.getX() +
                    "\tY: " +      wi.where.getY() );
        log.println("\tW: " + wi.where.getWidth() +
                    "\tH: " +      wi.where.getHeight());


		  log.println("Attempting to load tile: "+(wi.localDiskName == null ? wi.localURLName.toString() : wi.localDiskName));

		  if (wi.localDiskName == null)
				wi.image=loadBufferedImage(wi.localURLName);
		  else
		  		wi.image=loadBufferedImage(wi.localDiskName);

		  if(wi.originalPO == Main.PO)
			  try
			   {
				  broadcast(wi);
			   }
			  catch(Exception e)
			   {
				  log.aprintln(e);
				  log.aprintln(wi);
			   }
		  else
				log.println("OUTDATED tile found!");

        //Update tile count for debug tracking purposes
        ++numTiles;

		 synchronized(buzy){
			buzy.setNotBusy();
		 }
	
     }

	static protected int zoomIndexFromPix(Dimension2D pix)
	 {
		int ppd = (int) Math.ceil(1 / pix.getHeight());
		return  zoomIndexFromPPD(ppd);
	 }

    static protected int zoomIndexFromPPD(int ppd)
     {
        for(int i=0; i<LocationManager.INITIAL_MAX_ZOOM_POWER; i++)
         {
            int num = ( 1<<(i*LocationManager.ZOOM_MULTIPLIER));
            if((ppd & num) == num)
                return  i;
         }
        return  -1;
     }

    /**
     ** Class to send data to another proxy, LIFO-style. Basically you
     ** queue up any data you want to send, using sendData. It doesn't
     ** actually get sent until you call sendPendingData(), at which
     ** time all of your queued data gets sent in REVERSE of the order
     ** in which you originally sent it.
     **/
  protected class DataProxyReversed implements DataProxy
     {
        private DataProxy realProxy;
        private Stack     dataStack = new Stack();

        public DataProxyReversed(DataProxy _realProxy)
         {
            realProxy = _realProxy;
         }

        public void sendData(Object data)
         {
            dataStack.push(data);
         }

        public void sendPendingData()
         {
            while(!dataStack.empty())
                realProxy.sendData(dataStack.pop());
         }
     }

  protected int binRequestRegion(Area where, Vector stuffItHere, int ppd)
   {
	  double BUCKETWIDTH;
	  double BUCKETHEIGHT;
		Rectangle2D     grid = where.getBounds2D();
		BackLayer.WorldImage    wi;
		int            row, col;
		double         top,bottom;
		double			left,right;
		double			startX,startY;
		double				x1,x2,y1,y2;
		double         tol = 1e-6;
		int            nt;
		int				i,j;

		 synchronized(buzy)
		 {
			buzy.setBusy();
		 }

        log.println("*********************ENTER****************");


			BUCKETWIDTH  = Main.PO.getBucketWidth((double)ppd);
			BUCKETHEIGHT = Main.PO.getBucketHeight((double)ppd);

			log.println("Bin Width: "+BUCKETWIDTH);
			log.println("Bin Height: "+BUCKETHEIGHT);

			log.println("Incomming Area:");
			log.println("	X: "+grid.getX());
			log.println("	Y: "+grid.getY());
			log.println("	W: "+grid.getWidth());
			log.println("	H: "+grid.getHeight());


        //Align the Time values

        	left = grid.getX();//+Main.PO.getServerOffsetX();
//      	left = Main.PO.getXMin(grid.getX());//+Main.PO.getServerOffsetX();
			right= left+grid.getWidth();
        	bottom = grid.getY();
			top= grid.getY()+grid.getHeight();

			x1=Math.floor(left/BUCKETWIDTH); //Left side is rounded down
			x2=Math.ceil(right/BUCKETWIDTH); //but right side needs to be rounded up if it's NOT on a boundry
			col=(int)(x2-x1);
			startX=(x1*BUCKETWIDTH);
			log.println("delta X: "+col);

			y1=Math.ceil(top/BUCKETHEIGHT); //but right side needs to be rounded up if it's NOT on a boundry
			log.println("y1: "+y1);
			y2=Math.floor(bottom/BUCKETHEIGHT); //Left side is rounded down
			log.println("Here's bottom/BUCKETHEIGHT: "+(bottom/BUCKETHEIGHT));
			log.println("Here's bottom/BUCKETHEIGHT floored: " +Math.floor(bottom/BUCKETHEIGHT));
			log.println("y2: "+y2);


			row=(int)(y1-y2);
			startY=(y2*BUCKETHEIGHT);
			log.println("delta Y: "+row);

			nt=row*col;
        	for(i=0; i<row; i++)
           for(j=0;j<col;j++)
            {
               wi = new BackLayer.WorldImage();
               wi.where = new Rectangle2D.Double();
			   double moduloX = startX + (double)j*BUCKETWIDTH;
			   if(!Main.inTimeProjection())
				   moduloX -= Math.floor(moduloX / 360) * 360;
               wi.where.setRect(moduloX,
                                startY  + (double)i*BUCKETHEIGHT,
                                BUCKETWIDTH,
                                BUCKETHEIGHT);
              stuffItHere.add(wi);
              log.println("Tile: " + (i*col+j) +
                          "  X: " + wi.where.getX() +
                          "  Y: " + wi.where.getY() +
                          "  X+W: " + (wi.where.getX()+wi.where.getWidth())+
                          "  Y+H: " + (wi.where.getY()+wi.where.getHeight()));
            }

		 synchronized(buzy)
		 {
			buzy.setNotBusy();
		 }

        return  nt;
     }

    /* Responds to requests from BackLView. */
    public void receiveRequest(Object layerRequest,
                                  DataReceiver requester)
    {
		if (layerRequest == null)
			return;

		log.printStack(10);
      AgentRequest  request=new AgentRequest();
      request.wi=(WorldImage) layerRequest;
      request.dr=requester;

		log.println("746:Locking Message Queue");
      synchronized (messageQueue)
      {
        messageQueue.addLast(request);
        messageQueue.notify();
			log.println("I haved added another request to the queue: "+messageQueue.size());
      }
		log.println("746:UNLocking Message Queue");

    }

    public void processRequest(Object req)
    {
		 synchronized(buzy)
		 {
			buzy.setBusy();
		 }

      DataReceiver requester =((AgentRequest)req).dr; //This is the LView's receiver
		//Extract the request and basic info about it
		WorldImage  wi        = ((AgentRequest)req).wi;
		int         ppd       = (int) Math.ceil(1 /
												wi.pixelSize.getHeight());

		int         index     = zoomIndexFromPPD(ppd); //if index > numResIndices
                                               //just have the view extrapolate
	


      boolean     corrected = false;
		int         i;
		Iterator    stuff;
		int         empty     = 0;
		boolean     needAgent = false;
		Area        target;
      WorldImage  littleTile;
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


		log.println("ATTENTION: ppd="+ppd);
		log.println("ATTENTION: index="+index);
		log.println("ATTENTION: corrected="+corrected);
		log.println("ATTENTION: numResIndices="+numResIndices);



		log.println("Current Request for data is as follows:");

		log.println("\tX:" + (int) wi.where.getX() +
					"\tY:" +       wi.where.getY() );
		log.println("\tW:" + (int) wi.where.getWidth() +
					"\tH:" +       wi.where.getHeight() );

		log.println("\tResolution: " + ppd);
		log.print("\tRes index: " + index+" ");
      if (corrected)
        log.println("CORRECTED from "+zoomIndexFromPPD(ppd));
      else
        log.println("");

//		log.println(numTiles + " tiles");

		if (corrected)  //need to modify ppd if we're past our max zoom point for this data
			ppd = (int)Math.pow(2.0,(double)(index* LocationManager.ZOOM_MULTIPLIER));



/* Here we:
   Chop up the requested Area into our PPD based bins
   Place each bin into it's own WorldImage data structure
   Check for exiting tiles of this resolution are area

   If they exist, use them

   else,
    Put each "binified" WorldImage object into the qPlus Que
    Put each WorldImage.where object into the qPlus Map
    Check for these tiles of a lesser resolution
*/

      //Bin it up
      numLittleTiles=binRequestRegion(new Area(wi.where),littleTiles,ppd);

      //Loop through binned tiles :
        //check for exiting tiles at given resolution and show 'em if we've got 'em
        //if not, submit qplus request, and then check for tiles in other rezes

      for (i=0;i<numLittleTiles;i++){

			//Now if there IS an intersection, we want to test on a tile-by-tile basis
			//in case it's either a tiny intersection or we're really zoomed out

//The WorldImages returning from the littleTiles Vector have thier where set, but that's it
        littleTile=(WorldImage)(littleTiles.get(i));

//So we need to set some of the other values


//Need to doctor this if we're working in correction MODE
		  if (corrected)	
        		littleTile.pixelSize=new Dimension2D_Double(Main.PO.getUnitWidth()/(double)ppd,
																		  Main.PO.getUnitHeight()/(double)ppd);
		  else
        		littleTile.pixelSize=wi.pixelSize;




           double bounds1=littleTile.where.getY();
           double bounds2=littleTile.where.getY()+littleTile.where.getHeight();

           // If we're outside of the proper coordinate boundaries
           // for the projection, we want to paint black. So we
           // replace the original request url with the url to the
           // blank.gif image in the images directory.
           if (bounds1 > Main.PO.upperLimit() ||
               bounds1 < Main.PO.lowerLimit() ||
               bounds2 > Main.PO.upperLimit() ||
               bounds2 < Main.PO.lowerLimit() )
			{
			   log.println("Found tile outside range, skipping.");
			   continue;
			}



        littleTile.resolutionIndex=index;

        target=new Area(littleTile.where);
        needAgent=false;
			log.println("index=" + index);

			 log.println("Looking for Tile ("+i+")  [" +
						 littleTile.where.getX()      + "," +
						 littleTile.where.getY()      + "," +
						 littleTile.where.getWidth()  + "," +
						 littleTile.where.getHeight() + "]..." );


        //Search existing tile set in this Layer
		  if(findThisResTiles(littleTile, index, requester))  // Not Empty
		  {
			 //Send in copy of target

          // if ( !Main.useNetwork() ) {
          //   log.print("We should go to the disk, but we are not going to for a student app");
          //}
          // else {
			 //  needAgent = true;
          // }
			 needAgent = true;
			 log.println("Not Found!....");
		  }

		  else
			 log.println("Found!");

		  if(needAgent){
          boolean pending=false;
			log.println("870: Entering qPlus locked area");
          synchronized (qPlus) //check to see if the request is pending
          {
            boolean res=qPlus.isBinInMap(index,littleTile.where);
            if (res)
            {
                pending=true;
            }
          }
			log.println("Leaving qPlus locked area");

          if (pending){
				log.println("But's already PENDING");
            continue;
			 }

			 else {
			 	ar=new AgentRequest();
				log.println("NOT Pending");
			 }


          ar.dr=this; //We want to set the receiver function to the LAYER now
          ar.wi=littleTile;
			 ar.layer=this;

          //This call will put our need on the Qplus queue
			 findRemainingData(ar);

		  }
          //Check other resolutions for this
		  if(!Main.isStudentApplication())
			  findOtherResTiles(littleTile, index, requester); //Here we use the LView's receiver
      }

		synchronized(buzy)
		{
			buzy.setNotBusy();
		}

    }

/*
** This is the BackAgent request function.  This function is called if
** after subtracting off the exitsing tiles at the current resolution
** there is a remaining area.  This area is "bounded" and an agent is
** called to retreive a tile (at this ppd) and bring it back to the
** Layer
*/
    protected void findRemainingData(AgentRequest request)
    {
		synchronized(buzy)
      {
         buzy.setBusy();
      }

        Rectangle2D     intermediary = request.wi.where;

        log.println("Submitting an Agent Requet");
        log.println("Area:");
        log.println("\tX:" + (int) intermediary.getX() +
                    "\tY:" +       intermediary.getY() );
        log.println("\tW:" + (int) intermediary.getWidth() +
                    "\tH:" +       intermediary.getHeight() );

        /*
        ** Send the Requested area to the BackAgent (NOTE: This will
        ** become a call to the Agent manager) Return will be 1 to N
        ** "WorldImage" objects which the agent gathered.  These will
        ** now be inserted into BackLayer's data-structure.  NOTE:
        ** Current BackLayer data-structure is only a simple
        ** Vector...need to move a more intellegent spacial
        ** datastructure like a quad-tree Get the new Collection of
        ** Tiles.
        */

 //       log.println("TileChoice ="+tileChoice+"\nfileURL="+(fileURL==null ? "":fileURL));

			log.println("947:Entering qPlus locked area");
        synchronized(qPlus)
        {
          qPlus.addToQueue((Object)request);
          qPlus.addToMap(request.wi.resolutionIndex ,request.wi.where);
          qPlus.notify();
        }
			log.println("Leaving qPlus locked area");

			synchronized(buzy)
      	{
      	   buzy.setNotBusy();
      	}

    }




/*
** This function takes an index value and searches through the Layer's
** set of exiting tiles (at said resolution) subtracting off intersecing
** tiles from the target area.  If the target area gets completely
** covered, the function returns a 0 otherwise, a 1
*/
    protected boolean findThisResTiles(WorldImage wi,
                                     int index,
                                     DataReceiver layerDataProxy)
    {
		synchronized(buzy)
      {
         buzy.setBusy();
      }
      WorldImage     		chunk; //Temporary Placeholder
		Vector					tiles;
      Area           		source;
		int						num;
		double					xBin,yBin;
		int						ppd=(int)Math.pow(2.0,(double)(index* LocationManager.ZOOM_MULTIPLIER));
		int						i;
        //First we iterate through the desired resolution's tiles
        //stuff is an Iterator which steps through the worldImage collection one object
        //at a time.  getNext() returns the object which is then "acted" upon
        log.println("index=" + index);
        log.println("ppd=" + ppd);
		xBin=Main.PO.getBucketWidth(ppd);
		yBin=Main.PO.getBucketHeight(ppd);

//		if ((worldImages[index].size()) == 0)
//			return(true);

		synchronized(worldImages)
		 {
			 tiles=worldImages[index].getTiles(wi,xBin,yBin);
		 }
		num=tiles.size();
		Area target = new Area(wi.where);

		log.println("Number of Tiles found: "+num);

      for(i=0;i<num;i++){
            chunk=(WorldImage)tiles.get(i);

			if(chunk.image == null)
			 {
				log.aprintln("===========================================");
				log.aprintln("WE'VE RETRIEVED AN IMAGE, BUT IT'S NULL!!!!");
				log.aprintln("===========================================");
				log.aprintln(chunk.image);
				continue;
			 }
				log.println("Found Tiles #"+i+" has:");
				log.println("\t chunk.where = "+chunk.where);
				
            source = new Area(chunk.where);
            target.subtract(source);
				log.println("Sending CACHED Image out to: "+layerDataProxy);


//				chunk.where.setRect(chunk.where.getX()-Main.PO.getServerOffsetX(),
//											chunk.where.getY(),chunk.where.getWidth(),chunk.where.getHeight());


            layerDataProxy.receiveData(chunk);

            //If the target has been covered, then that's it
            if(target.isEmpty())
            	return(false);
				else {
					PathIterator foo = target.getPathIterator(null);
					int result;
					while (!foo.isDone()) {
						double [] coords = new double[6];
						result = foo.currentSegment(coords);
						foo.next();
						log.println("Current coords [type ="+result+"]: "+coords[0]+","+coords[1]);
					}
				}
					
				
					
       }
		synchronized(buzy)
      {
         buzy.setNotBusy();
      }
       return(true); //There's still an unclaimed area
    }


/*
** This function iterates through the rest of the resolutions and finds
** intersections with the target.  It is used to give the user "filler"
** images while the current resolution data is fetched.  Calling this
** function implies there is a remaining Area left after subtracting off
** existing tiles of the current resolution.  While those tiles are being
** fetched by the Agent, we want to user to see SOMETHING!
*/

    protected void findOtherResTiles(WorldImage target,
                                   int index,
                                   DataReceiver layerDataProxy)
    {
		if (Main.isBot())
			return; //If we're a bot, don't bother searching other resolutions
//High to low resolution...low will be drawn first, high next to last and current last
        for(int i=index-1; i>=0; i--)
        	if(!findThisResTiles(target, i, layerDataProxy))
         	break;
    }


	public Agent new_Agent(BDialogParameterBlock bdpb)
	{
		return (new BackAgent((BackDialogParameterBlock)bdpb));
	}

	public Agent new_Agent( Object agentRequest, AgentManager.Semephore agentCount)

	{
		return (new BackAgent(this.bdpb, agentRequest,agentCount));
	}


	public RenderedImage loadBufferedImage(String path)
	{
		return(Util.loadBufferedImage(path));
	}
	public RenderedImage loadBufferedImage(URL path)
	{
		return(Util.loadBufferedImage(path));
	}

 }

