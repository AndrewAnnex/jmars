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
import edu.asu.jmars.graphics.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import java.awt.geom.*;
import java.io.*;
import java.text.*;
import java.util.*;

/** @deprecated Generates tiles for time mode only */
public class Bot
{
	private static DebugLog log = DebugLog.instance();
	public Bot(String []av) throws Throwable
	{
		/**
		 ** av[0] = "Generate"
		 ** av[1] = "student"
		 ** av[2] = Starting ET
		 ** av[3] = ET duration
		 **/
		if(av.length == 4  &&  av[1].equalsIgnoreCase("student"))
		 {
			av = new String[] {
				av[0],
				av[2],
				av[3],
				"5", // height in degrees
				"1", // tile choice
				"2", // max number of agents (REALLY this is max+1, I think!)
				"6", // max ppd, log2 (2^6 = 64ppd)
				"2"  // ppd skip stepsize
			};
		 }

         /*
         ** av[0]= "Generate"
         ** av[1]= Starting ET
         ** av[2]= ET width
         ** av[3]= Height of the map coverage in degrees
         ** av[4]= Tile Choice Index
         ** av[5]= Max Number of simultaneous agent threads
         ** av[6]= Max Ppd as a power of 2
         ** av[7]= ppd skip step, ie every value, or every other value, etc
         */

         if (av.length != 8) {
			Main.printResource("resources/usage.generate", System.out);

            System.out.println("\n\n\tTileSet Index:");
            for(int j =0 ; j <BackPropertiesDiag.items.length-1;j++)
               System.out.println("\t\t"+j+":\t"+BackPropertiesDiag.items[j]);

            System.exit(0);
         }

         if (av[1].equalsIgnoreCase("global"))
         {
            Main._doingTileBot=true;
            int i;
            int count=0;
            int maxTries=35;
            int ppdMax;
            int ppdStep;

            double cLat,cLon;
            BackDialogParameterBlock bdpb = new BackDialogParameterBlock();

            cLat =Double.parseDouble(av[2]);
            cLon =(360-Double.parseDouble(av[3])%360.)%360.;
            Main.PO = new ProjObj.Projection_OC(cLon,cLat);
            log.aprintln("You're using the CYLINDRICAL projection centered at: ("+cLon+"/"+cLat+").");

            bdpb.tileChoice=Integer.parseInt(av[4]);
            bdpb.maxResolutionIndex=BackPropertiesDiag.resolutionIndecies[bdpb.tileChoice];
            log.println("Using: "+bdpb.maxResolutionIndex+" for maxResolutionIndex");
            bdpb.serverAddress = Config.get("tiles.bot");
            ppdMax = Integer.parseInt(av[5]);
            ppdStep = Integer.parseInt(av[6]);
				bdpb.maxAgents=Integer.parseInt(av[7]);

            double ppd;
           BackLayer bl = new BackLayer(bdpb);
            BackLayer.WorldImage wi;
            double width = 405;
            double height = 180.;
            double left = -22.5;
            double bottom = -90.;
            TileBot tb = new TileBot();

            while(!(bl.ready()))
               ;;

            for(i=0;i<=ppdMax;i+=ppdStep){
               ppd=Math.pow(2,i);
               wi = bl.new WorldImage();
               wi.where=new Rectangle2D.Double(left,bottom,width,height);
               wi.pixelSize= new Dimension2D_Double(1.0/ppd,1.0/ppd);

               bl.receiveRequest((Object)wi,tb); //Submit ALL four requests
            }
            System.out.println("Pausing for a moment");
            Thread.sleep(2000);
            System.out.println("Ok, let's Roll!");

            while (count < maxTries) {
               while (bl.agentManagerBusy()){
                  log.println("WAITING for AgentManager");
                  Thread.sleep(5000);
                  count=0;
               }
               log.println("AgentManager Not BUSY for count: "+count);
               count++;
            }

            count=0;
            while (count < maxTries) {
               while(bl.isBackLayerBusy()) {
                  log.println("WAITING for BackLayer");
                  count=0;
               }
               log.println("BackLayer Not BUSY for count: "+count);
               count++;
            }

            bl.dumpTileCollections();
            System.exit(0);
         }

		 Util.recursiveRemoveDir(new File(Main.TILE_CACHE));

            Main._doingTileBot=true;
            int i;
            int count=0;
            int maxTries=35;
            int ppdMax;
            int ppdStep;

            double et = TimeField.parseTimeToEt(av[1]);
            Main.PO = new ProjObj.Projection_SOM(et);
            Main.timeOnCommandLine = true;

            BackDialogParameterBlock bdpb = new BackDialogParameterBlock();

            bdpb.tileChoice=Integer.parseInt(av[4]);
            bdpb.maxResolutionIndex=BackPropertiesDiag.resolutionIndecies[bdpb.tileChoice];
            log.println("Using: "+bdpb.maxResolutionIndex+" for maxResolutionIndex");
            bdpb.serverAddress = Config.get("tiles.bot");
            bdpb.maxAgents=Integer.parseInt(av[5]);
            ppdMax = Integer.parseInt(av[6]);
            ppdStep = Integer.parseInt(av[7]);

            double ppd;
            TileBot tb = new TileBot();
            BackLayer bl = new BackLayer(bdpb);
            BackLayer.WorldImage wi;
            double left = Double.parseDouble(av[1]);
            double width = Double.parseDouble(av[2]);
            double height = Double.parseDouble(av[3]);
            double bottom = -(height/2.0);
            // Note: world coordinates include the server offset
            Rectangle2D worldRange = new Rectangle2D.Double(0, -90, width, 180);

            System.out.println("You are generating CACHED data:");
            System.out.println("\tStarting Time:\t\t"+(int)left);
            System.out.println("\tDuration:\t\t"+(int)width+" seconds.");
            System.out.println("\tSwath width:\t\t"+height+" degrees.");
            System.out.println("\tTile set:\t\t"+ BackPropertiesDiag.items[bdpb.tileChoice]);
            System.out.println("\tMaximum number of Agents to spawn: "+bdpb.maxAgents);


            while(!(bl.ready()))
               ;;

            for(i=0;i<=ppdMax;i+=ppdStep){
               ppd=Math.pow(2,i);
               wi = bl.new WorldImage();
               wi.where=new Rectangle2D.Double(left,bottom,width,height);
               wi.pixelSize= new Dimension2D_Double(20.0/ppd,1.0/ppd);

               bl.receiveRequest((Object)wi,tb); //Submit ALL four requests
            }
            System.out.println("Pausing for a moment");
            Thread.sleep(2000);
            System.out.println("Ok, let's Roll!");

            GridDataStore.generateLocalFiles(worldRange); //While things start cranking we'll block here

/*
            while (!(bl.agentManagerBusy())){
               log.println("WAITING for BackLayer ");
            }
*/


            while (count < maxTries) {
               while (bl.agentManagerBusy()){
                  log.println("WAITING for AgentManager");
                  Thread.sleep(5000);
                  count=0;
               }
               log.println("AgentManager Not BUSY for count: "+count);
               count++;
            }

            count=0;
            while (count < maxTries) {
               while(bl.isBackLayerBusy()) {
                  log.println("WAITING for BackLayer");
                  count=0;
               }
               log.println("BackLayer Not BUSY for count: "+count);
               count++;
            }

            bl.dumpTileCollections();

			createStageDir(TimeField.parseTimeToEt(av[1]),
						   Integer.parseInt(av[4]));

            System.exit(0);
	}
   private class TileBot implements DataReceiver
	{
	   public void receiveData(Object data)
		{
		}
	}

   private static void createStageDir(double startET, int tileIndex)
	{
	   DateFormat df = new SimpleDateFormat("yyMMdd_HHmmss");
	   String stageDir = "cd_stage_" + df.format(new Date());

	   log.aprintln("Creating stage directory '" + stageDir + "'...");
	   new File(stageDir).mkdir();

	   ///// GENERATE CONFIG FILE

	   Properties props = new Properties();
	   props.setProperty("init.default.tileIndex", "" + tileIndex);
	   props.setProperty("init.default.startET", "" + startET);

	   try
		{
		   OutputStream fout = new BufferedOutputStream(
			   new FileOutputStream(stageDir + "/jmars.config"));
		   props.store(fout, "Auto-generated by studentjmars bot");
		   fout.close();
		}
	   catch(IOException e)
		{
		   log.aprintln(e);
		   log.aprintln("Unable to create " +
						stageDir + "/jmars.config file, due to the above!");
		   System.exit(-1);
		}

	   ///// MOVE THE CACHE DATA DIRECTORIES

	   move("GRIDS"        , stageDir + File.separator + "GRIDS"   );
	   move(Main.TILE_CACHE, stageDir + File.separator + "PreCache");
	}

   private static final boolean IS_WINDOWS
   = System.getProperty("os.name").toUpperCase().indexOf("WINDOWS") != -1;

   private static void move(String src, String dst)
	{
	   log.println("Moving " + src + " to " + dst);

	   // Try the simplest method first
	   if(new File(src).renameTo(new File(dst)))
		   return;

	   log.println("Unable to File.renameTo " + src + " -> " + dst);

	   // Now get dirty... if we're crossing filesystems, or if the
	   // Java implementation simply doesn't support move via rename,
	   // then we run an actual shell command.

	   String cmd = (IS_WINDOWS ? "move" : "mv") + " " + src + " " + dst;
	   int exitCode = -1;
	   try
		{
		   Process proc = Runtime.getRuntime().exec(cmd);
		   exitCode = proc.waitFor();
		   proc.destroy();
		}
	   catch(Exception e)
		{
		   log.aprintln(e);
		   exitCode = -1;
		}

	   if(exitCode != 0)
		{
		   log.aprintln("ERROR: Failure on command `" + cmd + "` (exit=" +
						exitCode + ")");
		   log.aprintln("You must re-run, or move the directory yourself!");
		}
	}
}
