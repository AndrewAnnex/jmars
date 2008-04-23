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
import java.util.*;
import java.io.*;

public class TileCollection
{

	protected	HashMap		tileMap;
	protected	String		serialHashName;
	private	static	DebugLog		log=DebugLog.instance();	
	private		Agent			nameMaker;
	private		BDialogParameterBlock bdpb;
	private		int			index;
	private		BLayer		myLayer;


	static public class MyPoint2D implements Serializable
	{
		public double x,y;

		 public MyPoint2D()
		 {
			x=-8;
			y=-8;
		 }

		public MyPoint2D(double x,double y)
		{
			this.x =x;
			this.y =y;
		}

		public void setLocation(double x,double y)
      {
         this.x =x;
         this.y =y;
      }


		public int hashCode()
		{
			return((int)x+(int)y);	
		}

		public boolean equals(Object a)
		{
			if (a!=null && a instanceof MyPoint2D)
			{
				MyPoint2D b = (MyPoint2D) a;
				return(this.x==b.x && this.y==b.y);
			}
			else
				return(false);
		}
	}
	


	public		TileCollection(BLayer blayer,BDialogParameterBlock bdpb, int index, Agent agent)
	{
		this.bdpb = bdpb;
		nameMaker=agent;
		this.index=index;
		myLayer=blayer;

		checkForHashMap(this.index);
	}

	public void dumpCollection()
	{
		writeHashMap(buildHashName(index));
	}

   public void clear()
	{
	   tileMap.clear();
	}

	protected void checkForHashMap(int ppd_index)
	{
		serialHashName=buildHashName(ppd_index);
		if (!readHashMap(serialHashName))
			tileMap = new HashMap();

	}

	protected boolean readHashMap(String filename)
	{
		try {

			FileInputStream in = new FileInputStream(filename);

			ObjectInputStream s = new ObjectInputStream(in);

			tileMap = (HashMap)s.readObject();	

			s.close();
			in.close();

			return(true);
		}

		catch (IOException e)
		{
			log.println(e);
			return(false);
		}

		catch (ClassNotFoundException e)
		{
			log.println(e);
			return(false);
		}

	}

	protected boolean writeHashMap(String filename)
	{
		try {
			if (tileMap.size() < 1)
				return true;

			FileOutputStream out = new FileOutputStream(filename);
			ObjectOutputStream s = new ObjectOutputStream(out);

			s.writeObject(tileMap);

			s.flush();

			s.close();
			out.close();

			return(true);
		}

		catch (IOException e)
		{
			log.println(e);
			return(false);
		}
	}

	protected RenderedImage loadUpTile(String fn)
	{
		RenderedImage bi;

		try
		{
			bi=myLayer.loadBufferedImage(fn);
			if (bi != null)
				log.println(fn+" was a Disk HIT!");
			else
				log.println(fn+" was a Disk MISS!");
		}

		catch(NullPointerException e)
		{
			log.println("Disk MISS!");
			bi=null;
		}
		catch(IllegalArgumentException e)
		{
			log.println("Disk MISS!");
			bi=null;
		}

		return bi;
	}

	protected String buildHashName(int ppd_index)
	{
		String fs = System.getProperty("file.separator");
		String path=nameMaker.TILEPATH.substring(0,nameMaker.TILEPATH.lastIndexOf(fs)+1);
		String prefix = nameMaker.getTileName(bdpb.tileChoice);
		String special;
		String resolution="_"+Integer.toString(ppd_index);

		if (bdpb.customChoice != null)
			special="_"+bdpb.customChoice+".hash";

		else
			special=".hash";

		log.println("Hashmap filename= "+(path+prefix+resolution+special));

		return((path+prefix+resolution+special));
	}
	public		Vector	getTiles(BackLayer.WorldImage wi,double xBin, double yBin)
	{
		Area					target				=  new Area(wi.where);
		Vector				aBunchOfTiles		=	new Vector();
		Rectangle2D			where					=	wi.where;
		MyPoint2D		hashPoint			=	new MyPoint2D();

		double 		i,j;
		double		xStart,xStop;
		double		yStart,yStop;

//		int			key;

		BackLayer.WorldImage tile;

		int			count=0;

      double 	left = where.getX();
      double 	right= where.getX()+where.getWidth();
      double 	bottom = where.getY();
      double 	top= where.getY()+where.getHeight();

      int		x1=(int)Math.floor(left/xBin); 
      int		x2=(int)Math.ceil(right/xBin); 
      int		y1=(int)Math.ceil(top/yBin); 
      int		y2=(int)Math.floor(bottom/yBin); 


		if (xBin < 0) //Flipsie
			xBin*=-1.0;
		if (yBin < 0) 
			yBin*=-1;

		if (x1 < x2) {
			xStart=x1;
			xStop=x2;
		}
		else {
			xStart=x2;
			xStop=x1;
		}
	
		if (y1 < y2) {
			yStart=y1;
			yStop=y2;
		}
		else {
			yStart=y2;
			yStop=y1;
		}



		log.println("X Start: "+(xStart*xBin)+"\tX Stop: "+(xStop*xBin)+"\tX Step:"+xBin);
		log.println("Y Start: "+yStart+"\tY Stop: "+yStop+"\tY Step:"+yBin);

		for (i=xStart;i<xStop;i++){
			for(j=yStart;j<yStop;j++){

				BackLayer.WorldImage tempTile;

				hashPoint.setLocation((i*xBin),(j*yBin));

//				key=hashPoint.hashCode();

				log.println("Corner Point(#"+count+"): ["+(i*xBin)+","+(j*yBin)+"] ");

//First, search the HashMap for the filename (which, if it's in the map, then it's on disk)
//If we don't find a reference in the HashMap, not to fear.

				if (tileMap.containsKey(hashPoint))
				 {
					log.println("HASH is a HIT");

					//We've just pulled out the copy, need to load the disk image
					tile = (BackLayer.WorldImage)tileMap.get(hashPoint);	

					log.println("loaded the disk image");

					//But first, we make a clone of tile to attach the image too, or else
					// the image will essentially be kept in the hashmap too!
					tempTile=(BackLayer.WorldImage)tile.clone();

					log.println("cloned...Now to read in: "+tile.localDiskName);

					tempTile.image=myLayer.loadBufferedImage(tile.localDiskName);

					log.println("loadBufferedImage completed");

					aBunchOfTiles.add(tempTile);

					log.println("add completed");
				 }


//We could still have the tile on disk from another session (waste not, want not ;)

				else { 
					log.println("HASH: is a MISS");
					log.println("Seaching disk for this object");
						RenderedImage image = null;
						String fs = System.getProperty("file.separator");
						boolean preCache = false;

						String request = nameMaker.makeRequest(wi,false);
						log.println("*****************");
						log.println("Request string is: "+request);
						

						int nameHash = request.hashCode();

						String filename=nameMaker.TILEPATH + nameHash;
						String alt_filename = Main.TILE_PRE_CACHE+fs+Main.TILE_FILE_NAME+nameHash;


	
						if (filename == null || alt_filename == null) 
							log.println("Ahhhhhh! IMPOSSIBLE for filename to be NULL!!");
//						else
//							log.println("\t Which becomes: "+filename);

						if ((image=loadUpTile(alt_filename))==null) {//A null means we failed...so try another name
							log.println("Pre-Cache was a miss: "+alt_filename);
							if ((image=loadUpTile(filename))==null){
								log.println("Normal storage was a miss!");
								continue;
							}
							else
								preCache=false;
						}
						else
							preCache=true;


						//Clone the original request and save the filename
						if (image != null) {
							tile=(BackLayer.WorldImage)wi.clone();
							tile.localDiskName=(preCache ? alt_filename:filename);
							log.println("DISK RETREVAL: Storing Tile [index="+index+"] with Hash name: "+tile.localDiskName);
							tileMap.put(hashPoint,tile);

							//Since we stashed tile in the hash map, we need to
							//	clone it, so we can attach the image	
							tempTile=(BackLayer.WorldImage)tile.clone();
							tempTile.image=image;
							aBunchOfTiles.add(tempTile);
						}
					}
				}
			}
	

			return(aBunchOfTiles);

	}

	public		void setTile(Object agentData)
	{
		BackLayer.WorldImage	wi 		= 	(BackLayer.WorldImage) agentData;
		MyPoint2D    hashPoint=	new MyPoint2D();
		Rectangle2D 		where		=	wi.where.getBounds2D();
//		int					key;


//Create a copy of the incomming WorldImage (everything but the data)

		if (wi.localDiskName == null) {
			if (wi.localURLName != null) 
				log.println("I'm not going to store the error pixel");
			else
				log.aprintln("Ahhhhhh! WE GOT AGENT DATA WITH NULL FILENAME!!!");
			
			return;
		}

		BackLayer.WorldImage	copy 		= 	 (BackLayer.WorldImage)wi.clone();
		copy.image=null;

		hashPoint.setLocation(where.getX(),where.getY());

//		key=hashPoint.hashCode();

		log.println("Storing Tile[index="+index+"] with Hash name: "+copy.localDiskName);
		log.println("HashMap["+index+"] has size of: "+tileMap.size());

		//Store the copy in our Map, that way we don't waste room with the image data
		tileMap.put(hashPoint,copy);
	}

	public		int size()
	{
		log.println("Here");
		return(tileMap.size());
	}
}
