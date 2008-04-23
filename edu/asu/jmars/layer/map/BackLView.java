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
import javax.swing.event.*;
import java.net.URL;
import java.net.MalformedURLException;

/* Class for background layerview */
public class BackLView extends Layer.LView implements MouseInputListener
 {

    private static DebugLog log = DebugLog.instance();

	private Area[] resolutions;
	private int resolutionCount;

	//
	// Our name should be the same as the tile set that was chosen.
	// the layer knows which tile set was chosen.
	// for custom, its the file part of the url.
	//
	public String getName()
        {
          int i =  ((BackLayer)getLayer()).bdpb.tileChoice;
          String custom =((BackLayer)getLayer()).bdpb.customChoice;
          if ( ((BackLayer)getLayer()).bdpb.getCustomFileType() == BDialogParameterBlock.REMOTEFILE ) {
              String r;
              try {
                r = (new URL(custom)).getFile();
                r = Util.getBaseFileName(r);
              } catch(MalformedURLException e) {
                 return "Back Layer";
              }
              return(r);
          } else if ( ((BackLayer)getLayer()).bdpb.getCustomFileType() == BDialogParameterBlock.LOCALFILE ) {
              return Util.getBaseFileName(custom);
          } else {
            return  BackPropertiesDiag.items[i];
          }
  }

       public BackLView(Layer parent)
       {
		super(parent);

/*
		addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				System.out.println("MousePressed:");
			}
		});
*/
//		addMouseListener(this);
//		addMouseMotionListener(this);
		


		log.println("*************************");
		log.printStack(-1);
		// Fill our resolution-tracker with empty areas initially
		resolutionCount = LocationManager.INITIAL_MAX_ZOOM_POWER;
		resolutions = new Area[resolutionCount];
		for(int i=0; i<resolutionCount; i++)
			resolutions[i] = new Area();

		this.setVisible(true);

     }

   /**
    * This is good - build the focus panel on demand instead of during the view's constructor
    * That way, the saved session variables get propogated properly.
    */
    public FocusPanel getFocusPanel()
    {
        if(focusPanel == null)
          focusPanel = new BackFocus(this);

        return  focusPanel;
    }

    private synchronized Area clipByResolution(Rectangle2D where,
                                               int newRes)
     {
        Area newArea = new Area(where);
        int idealRes = BackLayer.zoomIndexFromPix(
            viewman.getProj().getPixelSize());

        // If the new tile is at the ideal resolution, then subtract
        // the new tile's area from every res except the ideal res.
        if(newRes == idealRes)
         {
            for(int oldRes=0; oldRes<resolutionCount; oldRes++)
                if(oldRes != newRes)
                    resolutions[oldRes].subtract(newArea);
         }
		// The new tile is above the ideal resolution. Toss it.
		else if(newRes > idealRes)
		 {
			return  new Area();
		 }
        // The new tile is below the ideal resolution. First, take the
        // ideal resolution out of the tile, so that we don't
        // overwrite anything idea. For resolutions under the tile,
        // subtract the new tile's area. For resolutions above the
        // tile, subtract from the tile's area with whatever we
        // already have.
        else
         {
			newArea.subtract(resolutions[idealRes]);
            for(int oldRes=0; oldRes<resolutionCount; oldRes++)
			 {
				Area oldArea = resolutions[oldRes];
				if     (oldRes < newRes) oldArea.subtract(newArea);
				else if(oldRes > newRes) newArea.subtract(oldArea);
			 }
         }

        // Remove resolutions[newRes] from this area... painting
        // that would be redundant. Save this as a separate Area, for
        // arithmetical stability issues when we finally add this area
        // to resolutions[newRes].
        Area clipped = (Area) newArea.clone();
        clipped.subtract(resolutions[newRes]);

        // Finally, add the leftover area to resolutions[newRes]
        resolutions[newRes].add(newArea);

        return  clipped;
     }

    /* Event triggered whenever there is new data to (potentially) paint in */
    public synchronized void receiveData(Object layerData)
     {
	
		if(!isAlive())
			return;

		BackLayer myLayer = (BackLayer)getLayer();

      BackLayer.WorldImage tile = (BackLayer.WorldImage) layerData;

		log.println("This ="+ this);
		log.println("Incomming Tile.where: "+tile.where);

		if(tile.originalPO != null && tile.originalPO != Main.PO)
		 {
			log.println("This tile's projection is outdated, ignoring it!");
			return;
		 }

		if(viewman.getProj().getWorldWindow() == null)
			log.aprintln("-- viewman.getProj().getWorldWindow() is null! --");
		if(tile.where == null)
			log.aprintln("-- tile.where is null! --");

		Rectangle2D.Double tmpWhere = new Rectangle2D.Double(tile.where.getX()-Main.PO.getServerOffsetX(),
																				tile.where.getY(),tile.where.getWidth(),tile.where.getHeight());
		if(!Main.inTimeProjection())
			tmpWhere.x %= 360;

        if(viewman.getProj().getWorldWindowMod().intersects(tmpWhere))
         {
			Area clippedWhere = clipByResolution(tmpWhere, tile.resolutionIndex);
			if(!clippedWhere.isEmpty())
			 {
				if(!isAlive())
					return;
				Graphics2D visibleG2 = getOffScreenG2();
				visibleG2.clip(clippedWhere);


				Rectangle2D world =  viewman.getProj().getWorldWindow();
				Rectangle2D clipArea = clippedWhere.getBounds2D();
/*
				log.println("DEBUG:******************************************");

				PathIterator foo = clippedWhere.getPathIterator(null);
				int result;
				while (!foo.isDone()) {
					double [] coords = new double[6];
					result = foo.currentSegment(coords);
					foo.next();
					log.println("Current clippedWhere Coords [type ="+result+"]: "+coords[0]+","+coords[1]);
				}


      		log.println("DEBUG: clipArea.getMinX(): "+clipArea.getMinX());
      		log.println("DEBUG: clipArea.getMaxX(): "+clipArea.getMaxX());
	     		log.println("DEBUG: clipArea.getMinY(): "+clipArea.getMinY());
	    		log.println("DEBUG: clipArea.getMaxY(): "+clipArea.getMaxY());


      		log.println("DEBUG: world.getMinX() = "+ world.getMinX());
      		log.println("DEBUG: world.getMaxX() = "+ world.getMaxX());
      		log.println("DEBUG: world.getMinY() = "+ world.getMinY());
      		log.println("DEBUG: world.getMaxY() = "+ world.getMaxY());

				log.println("DEBUG: Tile.where.getMinX() = " + tile.where.getMinX());
				log.println("DEBUG: Tile.where.getMaxX() = " + tile.where.getMaxX());
				log.println("DEBUG: Tile.where.getMinY() = " + tile.where.getMinY());
				log.println("DEBUG: Tile.where.getMaxY() = " + tile.where.getMaxY());

				log.println("DEBUG: Tile resolution index: = "+tile.resolutionIndex+"\n");
				log.println("DEBUG:******************************************\n\n\n");

*/


				BufferedImage tmpImage = (BufferedImage)tile.image;
		
				// Always replace the destination
				Composite tmpComposite = visibleG2.getComposite();
				visibleG2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
				visibleG2.drawImage(tmpImage, Util.image2world(tmpImage, tmpWhere), null);
				visibleG2.setComposite(tmpComposite);
			 }
			log.println("Repaint");
			repaint();
			

         }
     }

    protected Object createRequest(Rectangle2D where)
     {
			log.println("where = " + where);
			log.println("ViewMan.AnchorPoint = "+viewman.getAnchorPoint());
			log.println("viewman.getProj().getWorldWindow() = " + viewman.getProj().getWorldWindow());
			BackLayer myLayer = (BackLayer)getLayer();

			if (!myLayer.global) { // we're not global
				if (!Main.inTimeProjection()){
//					Shape ww = getProj().getWorldWindowMod();
					Rectangle2D ww = getProj().getWorldWindow();
					double x,y,w,h;
					x = 360.-myLayer.boundry.getX(); //Convert to West
					y = myLayer.boundry.getY();
					w = myLayer.boundry.getWidth();
					h = myLayer.boundry.getHeight();

               PathIterator foo = ww.getPathIterator(null);
               int result;
					log.println("Screen shape as world coords:\n");
               while (!foo.isDone()) {
                  double [] coords = new double[6];
                  result = foo.currentSegment(coords);
                  foo.next();
                  log.println("\tCurrent coords [type ="+result+"]: "+coords[0]+","+coords[1]);
               }
					log.println("");


					Point2D sll = new Point2D.Double((x),(y));
					Point2D sul = new Point2D.Double((x),(y+h));
					Point2D slr = new Point2D.Double((x-w),(y));
					Point2D sur = new Point2D.Double((x-w),(y+h));

					log.println("sll = "+sll);
					log.println("sul = "+sul);
					log.println("slr = "+slr);
					log.println("sur = "+sur+"\n");

					//The -1. & +1. are FUDGE factors to increase the boundary by a degree and prevent false misses
					Point2D wll = Main.PO.convSpatialToWorld(sll);
					Point2D wul = Main.PO.convSpatialToWorld(sul);
					Point2D wlr = Main.PO.convSpatialToWorld(slr);
					Point2D wur = Main.PO.convSpatialToWorld(sur);


					wll.setLocation(wll.getX()-1,wll.getY());
					wul.setLocation(wul.getX()-1,wul.getY());

					log.println("wll = "+wll);
					log.println("wul = "+wul);
					log.println("wlr = "+wlr);
					log.println("wur = "+wur+"\n\n");

					Line2D l1 = new Line2D.Double(wul,wll);
					Line2D l2 = new Line2D.Double(wll,wlr);
					Line2D l3 = new Line2D.Double(wlr,wur);

					GeneralPath gp = new GeneralPath();
					gp.append(l1,true);
					gp.append(l2,true);
					gp.append(l3,true);
					gp.closePath();

					
               PathIterator foo2 = gp.getPathIterator(null);
					log.println("Dataset shape as General Path:\n");
               while (!foo2.isDone()) {
                  double [] coords = new double[6];
                  result = foo2.currentSegment(coords);
                  foo2.next();
                  log.println("\tCurrent coords [type ="+result+"]: "+coords[0]+","+coords[1]);
               }
					log.println("");

/*					
					Rectangle2D test1=myLayer.boundry;
					Rectangle2D test2=new Rectangle2D.Double(test1.getX()+360.0,test1.getY(),
																			(test1.getX()+360.0+test1.getWidth()),
																			test1.getHeight());

					log.println("TESTING rectangle: "+test1);
					log.println("TESTING rectangle: "+test2);


					if (!(ww.intersects(test1) || ww.intersects(test2))) {
						log.println("Hit test --> MISS!");
						return(null);
					}
					else 
						log.println("Hit test --> BINGO!");
*/

					if (!(ww.intersects(gp.getBounds()))) {
						log.println("Hit test --> MISS!");
						return(null);
					}
					else 
						log.println("Hit test --> BINGO!");

				}


			}

			BackLayer.WorldImage request = myLayer.new WorldImage();
			request.where = where;
			
			request.pixelSize = viewman.getProj().getPixelSize();

//EXPERIMENTAL
			if (where !=null){
				request.where.setRect(where.getX()+Main.PO.getServerOffsetX(),
											where.getY(),where.getWidth(),where.getHeight());
			}

        return  request;
     }

    protected synchronized void viewChangedPre()
     {
		// Purge the resolutions
		for(int i=0; i<resolutionCount; i++)
			resolutions[i].reset();
	 }

    protected synchronized void viewChangedPost()
     {
		  Graphics2D g2 = getOffScreenG2Raw();
		  g2.setColor(Color.black);

		  Rectangle2D worldWin = getProj().getWorldWindow();

//         log.aprintln("View Center (World): "+getProj().getWorldWindow().getCenterX()+", "+
//                                             +getProj().getWorldWindow().getCenterY());


		  double bottomH = Main.PO.lowerLimit() - worldWin.getMinY();
		  if(bottomH > 0)
				g2.fill(
					 new Rectangle2D.Double(
						  worldWin.getX(),
						  worldWin.getY(),
						  worldWin.getWidth(),
						  bottomH));

		  double topH = worldWin.getMaxY() - Main.PO.upperLimit();
		  if(topH > 0)
				g2.fill(
					 new Rectangle2D.Double(
						  worldWin.getX(),
						  Main.PO.upperLimit(),
						  worldWin.getWidth(),
						  topH));
	  }

	protected Layer.LView _new()
	 {
		return  new BackLView(getLayer());
	 }

   public void mouseDragged(MouseEvent e) {}
   public void mousePressed(MouseEvent e){}
   public void mouseReleased(MouseEvent e) {log.println("released");}
   public void mouseEntered(MouseEvent e) {}
   public void mouseExited(MouseEvent e) {}
   public void mouseClicked(MouseEvent e) {}

	public void mouseMoved(MouseEvent e)
	{
		int w = (int)(Math.floor(1.0/(viewman.getProj().getPixelSize().getWidth())));
		int sw = viewman.getProj().getScreenSize().width;
		w*=360;
		
		int x = e.getX();
		int y = e.getY();
		if ( x < 0)
			log.aprintln("Mouse At: "+((w % (int)(Math.abs(x)) ))+","+y);
		else
			log.aprintln("Mouse At: "+(x % w)+","+y);
	}


 }
