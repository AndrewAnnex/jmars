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
import java.util.*;
import java.io.*;
import java.awt.image.renderable.*;
//import jpl.mipl.io.vicar.*;

/* Class for background layerview */
public class NumBackLView extends Layer.LView implements MouseInputListener
{
		/*
		** Given a NumBackDialogParameterBlock, which each dataset within the layer has,
		** we need an BufferedImage to get our information from.  Since the requested
		** entity could be of different types and organizations, we won't know what KIND
		** of BufferedImage image we're going to need until it's too late (that is, we will
		** have already tried to find out information about it for the incomming data to be
		** processed.  The solution is to take the NumBackDialogParameterBlock and generate
		** a kind of default tile, that is, data from requested entity, but for some default
		** locaity and size.  This default tile will provide the underlying information
		** needed to be ready for the 'real' incomming data.  This little inner class
		** takes a NumBackDialogParameterBlock and a dataset index (we can make use of 
		** previous cache information!) and returns the default tile in the form of
		** a BufferedImage.  This function will be called when the NumBackLView is first
		** initialized (and therefore has one dataset already in it) and on subsequent
		** dataset additions.
		*/


		private static DebugLog log = DebugLog.instance();

		private Area[] resolutions;
		private int resolutionCount;
		private boolean interactive; //Is this a NumLView or the front for a ThreeDLView??

		protected ArrayList data=new ArrayList();
		protected ArrayList oSIs = new ArrayList(); /* List of offScreenImages */
		protected ArrayList areaRemaining = new ArrayList(); /* each entry is a copy of the requested area */

		protected boolean preInitFocus=true;
		protected Object	[] updateWrapper; /*This array will match the number of bufferedImages...kludge for now*/
		protected Area			masterCopy;

		protected boolean firstClick=true;
		protected Point2D p1 = new Point2D.Double(); // Corrected Screen location of first point
		protected Point2D p2 = new Point2D.Double(); // Corrected Screen location of second point
		protected Point2D oldwp2 = null;
		protected Point2D wp1  = new Point2D.Double(); // World location of first point
		protected Point2D wp2  = new Point2D.Double(); // World location of second point
		protected Point2D sp1  = new Point2D.Double(); // Spatial location of first point
		protected Point2D sp2  = new Point2D.Double(); // Spatial location of second point
		protected Point2D dp1; 								// Uncorrected Screen location of first point
		protected Point2D dp2; 								// Uncorrected Screen location of second point
		protected Point2D linePt = null;
		protected Point2D oldLinePt =null;
		protected Point2D Q = null;
		protected Point2D oldQ =null;
		protected Line2D  testLine = new Line2D.Double();
		protected final int	liveTOL	 = 20;

		protected final double LINELENGTH  = 10.0;

		protected int tilesToGo;


		protected boolean	initFirstSet = true;
		protected boolean resample = false;

// The state of these four flags determines if we need to set re-sample to true or not
		protected boolean line_drawn = false;
		protected boolean ppd_changed = false;
		protected boolean line_intersects = false;
		protected boolean line_focus_changed = false;
		protected boolean liveUpdate = false;


		protected static int DEBUG_counter = 0;
		protected int ppd;


		//
		// Our name is simply "Numeric," since a single numeric layer can
		// now contain multiple datasets.
		//
		public String getName()
		{
			 return  "Numeric";
		}

		public String getName(int idx)
		{
				int i =  (((NumBackLayer)getLayer()).getBDPB(idx)).tileChoice;
				String custom =(((NumBackLayer)getLayer()).getBDPB(idx)).customChoice;
				if ( (((NumBackLayer)getLayer()).getBDPB(idx)).getCustomFileType() == 
					 BDialogParameterBlock.REMOTEFILE ) {
						String r;
						try {
								r = (new URL(custom)).getFile();
								r = Util.getBaseFileName(r);
						} 
						catch(MalformedURLException e) {
								return "Numeric Back Layer";
						}
						return(r);
				} 
				else if ( (((NumBackLayer)getLayer()).getBDPB(idx)).getCustomFileType() == 
						  BDialogParameterBlock.LOCALFILE ) {
						return Util.getBaseFileName(custom);
				} 
				else {
						return  NumBackPropertiesDiag.tileSet[i];
				}
		}
  

		public NumBackLView(Layer parent, boolean interactive)
		{
				super(parent);

				log.println("*************************");
				this.interactive = interactive;

				resolutionCount = LocationManager.INITIAL_MAX_ZOOM_POWER;
				resolutions = new Area[resolutionCount];
				for(int i=0; i<resolutionCount; i++)
						resolutions[i] = new Area();

				this.setVisible(true);

				FocusPanel fp = getFocusPanel();
				if (fp != null && fp instanceof NumBackFocus) {
					((NumBackFocus)fp).addElement(
							new Color[]{ getColor(0) },
							new String[]{ getName(0) },
							new int[ ((NumBackLayer)getLayer()).bdpb.size() ]);
				}
				
				if (this.interactive) {
					addMouseListener(this);
					addMouseMotionListener(this);
			}
			
		}

		/**
		 * This is good - build the focus panel on demand instead of during the view's constructor
		 * That way, the saved session variables get propogated properly.
		 */
		public FocusPanel getFocusPanel()
		{
				if(focusPanel == null) {
						focusPanel = new NumBackFocus(this);
						log.println("FOCUS PANEL Creation!!");
				}

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

/*
** This function augments the old  getOffScreenG2();
** Since we have a list of BufferedImages, we need to draw into the correct one.
** We use the same drawing techniques, but cycle through them based on the received
** data's ID.
*/
		protected WritableRenderedImage getListImage(int index)
		{
				WritableRenderedImage ti = (WritableRenderedImage)oSIs.get(index);
				return  ti;
		}


		// clips, scales, and translates an incomming image to the screen area.  
		// This is normailly done by the G2 for the offscreenImageBuffer, but
		// since we are dealing with non-traditional types, the G2 won't work, so we
		// have to do the transformations by hand, so to speak.
		// 
		// returns the raster of the transformed tile.
		protected synchronized Raster xformTile(NumBackLayer.NumWorldImage tile)
		{
				log.println("\n\nSTART: *************WORKING with tile ID: "+tile.DEBUG_ID+"*********************");

				// get the world coordinates.
				Rectangle2D world =  viewman.getProj().getWorldWindow();
				double wMaxX = world.getMaxX();
				double wMinX = world.getMinX();
				double wMaxY = world.getMaxY();
				double wMinY = world.getMinY();
				log.println("wMinX = "+ wMinX);
				log.println("wMaxX = "+ wMaxX);
				log.println("wMinY = "+ wMinY);
				log.println("wMaxY = "+ wMaxY);
				 
				// get the coords of the tile.
				double tileMaxX   = tile.where.getMaxX()-Main.PO.getServerOffsetX();
				double tileMinX   = tile.where.getMinX()-Main.PO.getServerOffsetX();
				double tileMaxY   = tile.where.getMaxY();
				double tileMinY   = tile.where.getMinY();
				double tileWidth  = tile.image.getWidth();
				double tileHeight = tile.image.getHeight();
				log.println("tileMinX = "   + tileMinX);
				log.println("tileMaxX = "   + tileMaxX);
				log.println("tileMinY = "   + tileMinY);
				log.println("tileMaxY = "   + tileMaxY);
				log.println("tileWidth = "  + tileHeight);
				log.println("tileHeight = " + tileHeight);
				 
				// get the screen coords.
				Dimension ss =	viewman.getProj().getScreenSize();
				double screenWidth = ss.width;
				double screenHeight = ss.height;
				 
				// Get the intersection of the input tile and the world
				Rectangle2D tmpWhere = new Rectangle2D.Double(tileMinX,tileMinY,(tileMaxX-tileMinX),(tileMaxY-tileMinY));
				Rectangle2D useful= new Rectangle2D.Double(); 
				Rectangle2D.intersect(world,tmpWhere,useful);
				log.println("Intersection.getMinX() = "+ useful.getMinX());
				log.println("Intersection.getMaxX() = "+ useful.getMaxX());
				log.println("Intersection.getMaxY() = "+ useful.getMaxY());
				log.println("Intersection.getMinY() = "+ useful.getMinY());

				// Test the intersection, returning a null if there was a problem.
				if (useful.getHeight() < 0 || useful.getWidth() < 0){
						log.println("Error with intersection, returning null.");
						return null;
				}
	
				// get the factor by which the output Raster will be scaled. 
				double screenPPD = screenWidth / (wMaxX - wMinX);
				double imagePPD  = tileWidth / (tileMaxX - tileMinX);
				 
				 
				// Get the new Raster.
				WritableRaster newRaster;
				double xOffset = (useful.getMinX() - tileMinX ) / (tileMaxX - tileMinX) * tileWidth;
				double yOffset = (tileMaxY - useful.getMaxY() ) / (tileMaxY - tileMinY) * tileHeight; 
				int newX = 0;
				int newY = 0;
				int newW = (int)(useful.getWidth()  * screenPPD);
				int newH = (int)(useful.getHeight() * screenPPD);
				log.println("xOffset=" + xOffset + " yOffset=" + yOffset);
				log.println("newW="    + newW    + " newH=" + newH);

				// If the tile PPD is the same as the tile PPD, then we can just clip the tile to the screen and be done with 
				// it, otherwise the new raster must be made from the clipped and scaled input tile.
				if (screenPPD == imagePPD){
						log.println("PPDs equal, just clipping the raster.");
						newRaster = ((WritableRaster)tile.image.getData()).createWritableChild((int)xOffset, (int)yOffset, 
																								newW, newH, newX, newY, null);
				} else {
						double scale = screenPPD / imagePPD;
						log.println("PPDs not equal. clipping and scaling the raster by " + scale);
						newRaster = tile.image.getData().createCompatibleWritableRaster( newX, newY, newW, newH);
						Raster oldRaster = tile.image.getData();
						double [] valueArray = new double[tile.image.getData().getNumBands()];
						for (int row=0; row<newH; row++){
								for (int col=0; col<newW; col++){
										oldRaster.getPixel( (int)((col/scale)+xOffset), (int)((row/scale)+yOffset), valueArray);
										newRaster.setPixel( col, row, valueArray);
								}
						}
				}

				// In either case, we need to translate the Raster.
				float tx = (float)Math.rint(((useful.getMinX() - wMinX) / (wMaxX - wMinX)) * screenWidth);
				float ty = (float)Math.rint((1.0 - ((useful.getMaxY() - wMinY) / (wMaxY - wMinY))) * screenHeight);
				log.println("Translation X: = "+ tx + "  Translation Y: = "+ ty);
				Raster xpi = newRaster.createTranslatedChild(Math.round(tx), Math.round(ty));

				log.println("END  : *************WORKING with tile ID: "+tile.DEBUG_ID+"*********************\n\n");
				return(xpi);
		}




		/* Event triggered whenever there is new data to (potentially) paint in.
		**	 
		** Since this layer is not **VISUALIZED**, there is no need to load other resolution
		**	data.  Therefore, if the incomming data is NOT the same resolution as our current
		**	setting, we ignore it, and return.
		*/

		public synchronized void setTileCount(int tc)
		{
				tilesToGo = tc;
				log.println("Setting tile count to: "+tilesToGo);
		}

		public synchronized void receiveData(Object layerData)
		{
	

				log.println("-- LView Receiving Data --");

				if(!isAlive())
						return;


				NumBackLayer.NumWorldImage tile = (NumBackLayer.NumWorldImage) layerData;

				log.println("**RECEIVED: tile - ID "+tile.DEBUG_ID);
			    log.println("\t** tile = "+tile);

				if(viewman.getProj().getWorldWindow() == null)
						log.aprintln("-- viewman.getProj().getWorldWindow() is null! --");
				if(tile.where == null)
						log.aprintln("-- tile.where is null! --");

				int curRes = BackLayer.zoomIndexFromPix(viewman.getProj().getPixelSize());
				int maxRes = ((NumBackLayer)getLayer()).getBDPB(tile.whichDataSet).maxResolutionIndex;	

				if (curRes != tile.resolutionIndex && curRes <= maxRes) {
						log.println("IGNORING incomming data (Current: "+curRes+" != Incomming: "+tile.resolutionIndex+")");
						return;
				}

				if (curRes > maxRes && tile.resolutionIndex < maxRes) {
						log.println("IGNORING incomming data (Current: "+curRes+" != Incomming: "+tile.resolutionIndex+")");
						return;
				}

				log.println("STATE of LineDrawn: "+line_drawn);
				log.println("COORDS of Incomming Tile:");
				log.println("\t minX: "+tile.where.getMinX());
				log.println("\t minY: "+tile.where.getMinY());
				log.println("\t maxX: "+(tile.where.getMinX()+tile.where.getWidth()));
				log.println("\t maxY: "+(tile.where.getMinY()+tile.where.getHeight()));
				log.println("\t Width: "+tile.where.getWidth());
				log.println("\t Height: "+tile.where.getHeight()+"\n\n");
				log.println("IMAGE: "+( (tile.image == null) ? "null":tile.image.toString()));

				if(Main.inTimeProjection())
				{
						if(drawTile(tile))
								tilesToGo--;
				}

				// In cylindrical, we wish to draw a modulo'd tile to a
				// non-modulo'd view, so we for() loop over all the
				// 360-degree periods that are visible, translating the tile
				// for each copy. Takes a bunch of setup, the real guts are
				// just the short for() loop.
				else
				{
						NumBackLayer.NumWorldImage fakeTile;
						Rectangle2D.Double fakeWhere;

						// Set up a fake copy of the tile with its own "where"
						// that we can translate arbitrarily.
						fakeWhere = new Rectangle2D.Double();
						fakeWhere.setRect(tile.where);
						fakeTile = (NumBackLayer.NumWorldImage) tile.clone();
						fakeTile.where = fakeWhere;

						// Stolen from GraphicsWrapped constructor, with values
						// as if invoked from LViewManager.wrapWorldGraphics()
						float mod = 360;
						double min = getProj().getWorldWindow().getMinX();
						double max = getProj().getWorldWindow().getMaxX();
						double base = Math.floor(min / mod) * mod;
						int count =
								(int) Math.ceil (max / mod) -
								(int) Math.floor(min / mod);

						// Ensures that we only decrement once for the real tile
						// (as opposed to once for each fake tile).
						boolean needDecrement = true;

						// Imitates GraphicsWrapped.drawImage()

						log.println("*** MATH for DUPLICATION ***");
						log.println("\tmin = "+min);
						log.println("\tmax = "+max);
						log.println("\tbase = "+base+"\n END: *** MATH for DUPLICATION ***\n\n");

						for(int i=0; i<count; i++)
						{
								log.println("Drawing " + (i+1) + " / " + count + " duplicate");
								// Translate the fake tile's "where" from the real tile
								fakeWhere.x = tile.where.getX() + base + mod*i;
								if(drawTile(fakeTile)  &&  needDecrement)
								{
										tilesToGo--;
										needDecrement = false;
								}
						}
				}
		}



		/**
		 ** Copy/pasted from source code to the function {@link
		 ** BufferedImage#setData(Raster)}.
		 **/
		private void setData(BufferedImage THIS, Raster r) {
				int width = r.getWidth();
				int height = r.getHeight();
				int startX = r.getMinX();
				int startY = r.getMinY();
 
				Object tdata = null;
				WritableRaster raster = THIS.getRaster();

				// Clip to the current Raster
				Rectangle rclip = new Rectangle(startX, startY, width, height);
				Rectangle bclip = new Rectangle(0, 0,
												raster.getWidth(), raster.getHeight());
				Rectangle intersect = rclip.intersection(bclip);
				if (intersect.isEmpty()) {
						return;
				}
				width = intersect.width;
				height = intersect.height;
				startX = intersect.x;
				startY = intersect.y;

				// remind use get/setDataElements for speed if Rasters are
				// compatible
				for (int i = startY; i < startY+height; i++)  {
						tdata = r.getDataElements(startX,i,width,1,tdata);
						raster.setDataElements(startX,i,width,1, tdata);
				}
		}

		/**
		 ** Performs the work to "draw" a numeric tile into the
		 ** appropriate off-screen buffer. Returns an indication of
		 ** whether or not the tile was actually drawn (the tile could be
		 ** off-screen).
		 **/
		private boolean drawTile(NumBackLayer.NumWorldImage tile)
		{
				if(!isAlive())
						return  false;

				if (tile.image == null) {
						log.aprintln("Recevied NULL image from: "+tile);
						log.printStack(5);
						return(false);
				}

				Rectangle2D.Double tmpWhere = new Rectangle2D.Double();
				tmpWhere.setRect(tile.where);
				if(Main.inTimeProjection())
						tmpWhere.x -= Main.PO.getServerOffsetX();
				else
						tmpWhere.x -= Math.floor(tmpWhere.x/360)*360;

				if(!viewman.getProj().getWorldWindowMod().intersects(tmpWhere))
				{
						Rectangle2D ww = viewman.getProj().getWorldWindow();
						if(log.isActive())
						 {
								log.println("NO INTERSECTION:");
								log.println("\tCOORDS of Incomming Tile:");
								log.println("\t  minX: "+tmpWhere.getMinX());
								log.println("\t  minY: "+tmpWhere.getMinY());
								log.println("\t  maxX: "+(tmpWhere.getMinX()+tmpWhere.getWidth()));
								log.println("\t  maxY: "+(tmpWhere.getMinY()+tmpWhere.getHeight()));
								log.println("\t  Width: "+tmpWhere.getWidth());
								log.println("\t  Height: "+tmpWhere.getHeight()+"\n\n");
								log.println("\tCOORDS of World Window:");
								log.println("\t  minX: "+ww.getMinX());
								log.println("\t  minY: "+ww.getMinY());
								log.println("\t  maxX: "+(ww.getMinX()+ww.getWidth()));
								log.println("\t  maxY: "+(ww.getMinY()+ww.getHeight()));
								log.println("\t  Width: "+ww.getWidth());
								log.println("\t  Height: "+ww.getHeight()+"\n\n");
						 }
						return  false;
				}


				log.println("Incomming TILE (ID = "+tile.DEBUG_ID+") INTERSECTS view");

				// Transform tile into correct space (scale, transform, & crop)
				Raster xti = xformTile(tile);

				// If their was some sort of problem with the returned Raster, we should not
				// be drawing it.
				if (xti==null){
					log.println("Problem occurred in xformTile.");
						return false;
				}

				log.println("COORDS of X-Formed image:");
				log.println("\t minX: "+xti.getMinX());
				log.println("\t minY: "+xti.getMinY());
				log.println("\t maxX: "+(xti.getMinX()+xti.getWidth()));
				log.println("\t maxY: "+(xti.getMinY()+xti.getHeight()));
				log.println("\t Width: "+xti.getWidth());
				log.println("\t Height: "+xti.getHeight());

			
				// We're going to keep our set of offscreenBuffers current on demand.  That is,
				// slots in the list are empty until requested.  We put null placemarks in so
				// that we can "jump ahead".  Example, list contains 2 entries indexed 0 and 1
				// respectively.  A Request comes in for dataset index 6.  We place a set of
				// dummy entries until we have an entry for index 6.  We then check an entry
				// when we go to use it to make sure it's valid (say our next request is for
				// index 4).  If it's null, we quickly make a valid entry else we continue on
				// and draw into it.
				if (tile.whichDataSet >= oSIs.size()) {
						log.println("Playing catch-up: Requested oSIs index: "+tile.whichDataSet+" oSIs.size()= "+oSIs.size());
						int idx;
						for(idx =0; idx < (tile.whichDataSet - oSIs.size() +1);idx++){
								Object filler = null;
								oSIs.add(filler);
								if (masterCopy == null) {
										log.println("ERROR: masterCopy is NULL!!!\n\tTrying to fake it!");
										areaRemaining.add(new Area(viewman.getProj().getWorldWindow()));
								}
								else
										areaRemaining.add(new Area(masterCopy));
						}
				}


				//Get appropriate offscreenBuffer
				WritableRenderedImage tmpTiled = getListImage(tile.whichDataSet);

				
				//Check to see if it's valid, if not, set it
				if (tmpTiled == null) {
						log.println("MAKING Appropraite Image for our Buffer");
						WritableRenderedImage fooImage = makeAppropraiteImage(tile.image);
						oSIs.set(tile.whichDataSet,fooImage);
						tmpTiled = getListImage(tile.whichDataSet);
				}



				//"draw" tile into Dataset
				log.println("DRAWING into our buffer: "+tmpTiled.hashCode());
				log.println("COORDS of BUFFER:");
				log.println("\t minX: "+tmpTiled.getMinX());
				log.println("\t minY: "+tmpTiled.getMinY());
				log.println("\t maxX: "+(tmpTiled.getMinX()+tmpTiled.getWidth()));
				log.println("\t maxY: "+(tmpTiled.getMinY()+tmpTiled.getHeight()));
				log.println("\t Width: "+tmpTiled.getWidth());
				log.println("\t Height: "+tmpTiled.getHeight());
				setData((BufferedImage) tmpTiled, xti);
			
				updateReamingArea(tile,tmpWhere); //gotta use the region which HAS the offset subtracted off
				updateRasterList(tile.whichDataSet);

				/************
				 ** Test for Data completeness (ie, we've received all the data we're gonna get.
				 ** And test for the re-sample flag.  When both are true, re-sample.
				 ************/
				log.println("TESTING for re-sample");
				if (resample && !(isThereRemainingArea())){
						sampleData();
				}
				else {
						if (resample) {
								log.println("There appears to be remaining AREA");
						} else {
								log.println("NO resample requested");
						}
				}
				


				if (preInitFocus && interactive) {
						if(focusPanel == null )
								return  true;

						log.println("I'm ALIVE and sampleing rasters!");
	
						Raster data_0 = getRaster(0);

						log.println("Getting Initial Raster if it's availible");

						log.println("RASTER: "+( (data_0 == null) ? "null":data_0.toString()));
		
						if (data_0 != null) {
								updateWrapper[0]=extract_pixel(0,0,data_0);

								log.println("INITIAL PIXEL: "+updateWrapper[0].getClass().getComponentType().getName());
								/*
								String [] foo = new String[1];
								foo[0] = getName(0);
								Color [] cFoo = new Color[1];
								cFoo[0]=((NumBackDialogParameterBlock)(((NumBackLayer)getLayer()).bdpb.get(0))).color;
								((NumBackFocus)focusPanel).addElement(cFoo,foo,updateWrapper[0]);
								*/
								preInitFocus=false;
						}
				}

				return  true;
		}
		
		protected boolean isThereRemainingArea()
		{
				int i;

				for (i=0;i<areaRemaining.size();i++){
						if (!(((Area)areaRemaining.get(i)).isEmpty()))
								return(true);
				}

				return(false);
		}

		protected void updateReamingArea(NumBackLayer.NumWorldImage tile, Rectangle2D tmpWhere)
		{
				if (tile.whichDataSet >= areaRemaining.size()){
						log.println("ERROR: requested dataset index: "+tile.whichDataSet+
									" is too big for areaRemaining.size()= "+areaRemaining.size());
						return;
				}

				Area tmp = (Area)areaRemaining.get(tile.whichDataSet);
				tmp.subtract(new Area(tmpWhere));
		}

		protected synchronized Object createRequest(Rectangle2D where)
		{
				if (getChild() == null) //I'm the panner, don't make a request
						return null;

				NumBackLayer myLayer = (NumBackLayer)getLayer();
				int len = oSIs.size();
				int i;

				tilesToGo = 0; //This is for tracking requests...EXPERIMENTAL

				if (len == 0) //first time request
						len = 1;

				NumBackLayer.NumWorldImage  [] request = new NumBackLayer.NumWorldImage[len];

				areaRemaining.clear(); //Clear out the requesting area copies


				log.println("where = " + where);
				log.println("viewman.getProj().getWorldWindow() = " + viewman.getProj().getWorldWindow());

				for (i=0;i<len;i++){
						request[i]= myLayer.new NumWorldImage();	
						request[i].where = new Rectangle2D.Double();
//				request[i].where.setRect(where);
						request[i].pixelSize = viewman.getProj().getPixelSize();
						request[i].whichDataSet=i;

						request[i].bdpb = myLayer.getBDPB(i); //Associated parameter block


//EXPERIMENTAL
						if (where !=null){
								request[i].where.setRect(where.getX()+Main.PO.getServerOffsetX(),
														 where.getY(),where.getWidth(),where.getHeight());
						}

						areaRemaining.add(new Area(where)); //One area copy for every dataset, of the "pre-added" where (ie before the offset is added back)
				}
				masterCopy = new Area(where);

				return  request;
		}

/*
** Given an image (which implements RenderedImage), take
** its properties and create a new image with the same properties,
** but whoes size is that of the SCREEN
*/

		protected WritableRenderedImage makeAppropraiteImage(RenderedImage ri)
		{
				MultiProjection proj = 	viewman.getProj();
				Dimension pixSize 	=	proj.getScreenSize();
				ColorModel cm 			= 	ri.getColorModel();

				log.println("Making image of size (row,col): "+pixSize.height+","+pixSize.width);
				log.printStack(7);

				WritableRaster oldRaster = (WritableRaster) ri.getData();
				WritableRaster newRaster =
						oldRaster.createCompatibleWritableRaster(0, 0,
																 pixSize.width,
																 pixSize.height);
				return  new BufferedImage(cm, newRaster, false, null);
		}


/*
** This function is called form the begining of viewChangedReal().  The latter function
** rebuilds or initializes the offsceenImage buffer.  Our LIST of buffers is very much
** like the offscreenImage buffer, so we need the same behavior for our list.  This
** is were we can:
** 
**		1) Check and see if this is the very first time we'd be accessing the list and
**			therefore we need to initialize the first buffer.
**
**		2) Check and see if the screen has changed size and therefore we need to RE-initialize
**			ALL the buffers in the list
**
**	This the same set of functions that viewChangedReal performs on offscreenImage.
**
*/


		protected synchronized void viewChangedPre()
		{
	
				int i;	
				int old_ppd;
				NumBackLayer myLayer = (NumBackLayer)getLayer();

				MultiProjection proj = viewman.getProj();

				old_ppd = ppd;

				ppd = (int)(Math.floor(1.0/(viewman.getProj().getPixelSize().getWidth()))) * 360;

				//Cleanout any old cross-hair junk before we change the view
				if (focusPanel != null) 
						((NumBackFocus)focusPanel).liveUpdate(0.0,true); //This will clear the last draw


// If there is a line drawn then, when the view changes, we MUST re-sample
				if (ppd!=old_ppd)
						ppd_changed = true; 
				else
						ppd_changed = false;

				int count;

				pingOffscreenImage();

				log.println("viewChangedPre()");

				//Check for FIRST time flag.  If true, initialize the first image in the array buffer

				log.println("RE-Initializing Image Buffer because of VIEW change");

				count = oSIs.size();

//Flush Raster List;
				data.clear();

				for(i=0;i<count;i++){
/*
**A comment should be made about the following line:
** We retreive the image from the list (at i) (which is the right type, but wrong size)
** and send it into the function which will return an image of the SAME type but
** CORRECT size.  We then replace our image at i with the correct sized one
*/
						oSIs.set(i,makeAppropraiteImage((RenderedImage)oSIs.get(i)));
				}


				// Purge the resolutions
				for(i=0; i<resolutionCount; i++)
						resolutions[i].reset();

/*
** Check and see:

1) Is there a line drawn?
2) Is it in view?
3) Did it's focus change (ie did it just come into/leave view)?
4) Has our magification changed?

Depending on a combination of the above, we'll want to re-sample

****/

				double x;



				if (!line_drawn) { //There is NO line, so we're done
						log.println("NO Line DRAWN...leaving now");
						return;
				}

				boolean intersects;

				Shape ww = getProj().getWorldWindowMod();
				Line2D line = new Line2D.Double(wp1,wp2);
				Rectangle2D bb = line.getBounds2D();
				Rectangle2D bb2;
				log.println("Bounding Box for your line is: "+bb);

				if ( (Math.abs(wp2.getX() - wp1.getX()) > 180.0) &&
					 (!Main.inTimeProjection())) { //wrapped...gotta fix

						if (wp1.getX() < wp2.getX() )
								line.setLine(wp1.getX()+360.0,wp1.getY(),wp2.getX(),wp2.getY());
						else
								line.setLine(wp1.getX(),wp1.getY(),wp2.getX()+360.0,wp2.getY());

						bb = line.getBounds2D();

						if (wp1.getX() < wp2.getX() )
								line.setLine(wp1.getX(),wp1.getY(),wp2.getX()-360.0,wp2.getY());
						else
								line.setLine(wp1.getX()-360.0,wp1.getY(),wp2.getX(),wp2.getY());

						bb2 = line.getBounds2D();

						intersects = (ww.intersects(bb) || ww.intersects(bb2));
			

				}

				else {
			
						intersects = ww.intersects(bb);
				}

				line_focus_changed = intersects ^ line_intersects; //XOR: the focus has changed if one is true but not the other

				line_intersects = intersects;

				if (!line_intersects) { //reguadless of focus change, if no intersections....
						log.println("NO Line INTERSECTION....leaving now\n");
						return;
				}

				else
						resample = true; //basically, if the view has changed and we're in it, resample



//		Since there IS an intersection in World-Coords, we need to re-cast p1,p2 in screen	
//		coords in case they've gotten a little wonky (magnification, coord-shifting in cylindrical, etc...)
	
				//Convert the WorldCoords of our line points into the NEW screen points


				dp1 = getProj().world.toScreen(wp1);
				dp2 = getProj().world.toScreen(wp2);


				log.println("Before Convertion: ");
				log.println("\t World-point1 (X,Y): "+wp1.getX()+" , "+wp1.getY()+" --> "+dp1.getX()+" , "+p1.getY()); 
				log.println("\t World-point2 (X,Y): "+wp2.getX()+" , "+wp2.getY()+" --> "+dp2.getX()+" , "+p2.getY()); 


				//Gotta fix up our screen-X values for cylindrical:
				if(!Main.inTimeProjection()) { 
						p1.setLocation(getProj().screen.toScreenLocal(dp1));
						p2.setLocation(getProj().screen.toScreenLocal(dp2));
		
						double dist = Math.abs(p1.getX() - p2.getX());
						log.println("Distance = "+dist);
						log.println("ppd = "+ppd);

						if (dist > (ppd/2.0) ) { //we're badly wrapped
								if (p1.getX() > p2.getX())
										p1.setLocation((p1.getX() - ppd),p1.getY());
								else
										p2.setLocation((p2.getX() - ppd),p2.getY());
						}

				}
				else {
						p1 = dp1;
						p2 = dp2;
				}


				log.println("Converted: ");
				log.println("\t World-point1 (X,Y): "+wp1.getX()+" , "+wp1.getY()+" --> "+p1.getX()+" , "+p1.getY()); 
				log.println("\t World-point2 (X,Y): "+wp2.getX()+" , "+wp2.getY()+" --> "+p2.getX()+" , "+p2.getY()); 


/*

if (line_focus_changed) { 
log.println("LINE FOCUS CHANGED...resample issued!");
resample = true;
}

else if (ppd_changed) { 
log.println("PPD CHANGED...resample issued");
resample = true;


}
*/

				repaint();

		}

		protected synchronized void viewChangedPost()
		{


				log.println("ViewChangedPost:");
				//Okay, so far we know: there IS a line, and it DOES intersect, so at the very least
				// DRAW it!!!
				if (line_intersects) { //reguadless of focus change, if no intersections....
						drawLine(wp1,wp2,getOffScreenG2(),true,Color.white,getProj().getPixelSize().getHeight());
						repaint();
				}

		}



		protected void loadRasterBuffers()
		{
				int i;
				log.println("Collecting "+oSIs.size()+" raster buffer"+ ( oSIs.size() > 1 ? "s":" ")  );

				if (oSIs.size() >= 1) {

						//Collect the rasters from ALL the BufferedImages and stash 'em
						data.clear();
						for (i=0;i<oSIs.size();i++)
								data.add(((WritableRenderedImage)oSIs.get(i)).getData());
						updateWrapper = new Object[data.size()]; //Set-up for mouse_moves;
				}
		}

		protected Layer.LView _new()
		{
				return(new NumBackLView(getLayer(),interactive));
		}


		protected BufferedImage newBufferedImage(int w, int h)
		{
				log.println("Making new buffered image");

				return (Util.newBufferedImage(w,h));
		}


		protected void updateRasterList(int idx)
		{

				if (oSIs.size() <= idx) {
						log.println("Requested RASTER idx: "+idx+" BUT offscreenBuffers only goto: "+oSIs.size());
				}

				if (data.size()  <= idx) { //asked for buffer we just don't have yet
						log.println("BUILDING data buffers");
						for(int i=0;i<(idx - data.size() + 1);i++){
								Object filler = null;
								data.add(filler);
						}

						log.println("SETTING up "+data.size()+" wrapper buffer"+(data.size() > 1 ? "s":""));
			
						updateWrapper = new Object[data.size()]; //Set-up for mouse_moves;
				}


				Raster fooRaster = ((WritableRenderedImage)oSIs.get(idx)).getData();
				
				if (idx == data.size())
					data.add(idx,fooRaster);
				else
					data.set(idx,fooRaster);
	

		}

		public Raster getRaster(int idx)
		{
				if (oSIs.size() <= idx) {
						log.println("Requested RASTER idx: "+idx+" BUT offscreenBuffers only goto: "+oSIs.size());
						return(null);
				}

				if (data.size()  <= idx) { //asked for buffer we just don't have yet
						log.println("BUILDING data buffers");
						for(int i=0;i<(idx - data.size() + 1);i++){
								Object filler = null;
								data.add(filler);
						}

						log.println("SETTING up "+data.size()+" wrapper buffer"+(data.size() > 1 ? "s":""));
						updateWrapper = new Object[data.size()]; //Set-up for mouse_moves;
				}


				Raster r = (Raster)data.get(idx);

				if (r == null) {
						log.println("NULL RASTER: Re-Building raster: "+idx);
						WritableRenderedImage fooImage =(WritableRenderedImage)oSIs.get(idx);
						if (fooImage == null) {
								log.println("NO data for this buffer has come in yet!\n");
								return(null);
						}
						Raster fooRaster = fooImage.getData();
						data.set(idx,fooRaster);
						r = (Raster)data.get(idx);
				}

//		log.println("GETTING Raster: "+idx+" from offScreenBuffer collection");
//		log.println("RASTER "+idx+" = "+r);

				return(r);
		}

		public void deleteDataSet(int idx)
		{
				if (idx < oSIs.size())
						oSIs.remove(idx);
				else
						log.println("Hey! you tried to remove index: "+idx+" but we only go upto: "+(oSIs.size()-1));

				if (idx < data.size())
						data.remove(idx);
				else
						log.println("Hey! you tried to remove index: "+idx+" but we only go upto: "+(data.size()-1));

				updateWrapper = new Object[data.size()];

				((NumBackLayer)getLayer()).removeDataSet(idx);
		}

		public void addDataSet(NumBackDialogParameterBlock bdpb)
		{
				NumBackLayer myLayer = (NumBackLayer)getLayer();

				Object filler = null;

				oSIs.add(filler); //This will put a placemark in for the new dataset

				myLayer.addDataSet(bdpb);

				MultiProjection proj = getProj();
				Rectangle2D req = proj.getWorldWindow(); 

//		log.println("ADDING dataset.  Creating request at: "+req.getMinX());

				Object layerRequest = createRequest(req);
				myLayer.receiveRequest(layerRequest, this);

				if (line_drawn && line_intersects)
						resample = true;

		}
		
		protected  Object extract_pixel(int x,int y,Raster r)
		{
				int type = (r.getDataBuffer()).getDataType(); //This should be determined once
				int depth = r.getNumDataElements();


				int minX = r.getMinX();
				int minY = r.getMinY();
				int maxX = r.getMinX()+r.getWidth();
				int maxY = r.getMinY()+r.getHeight();


				if (x < minX) x = minX;
				if (y < minX) y = minX;
				if (x >= maxX) x = maxX-1;
				if (y >= maxY) y = maxY-1;


				byte [] b;
				short [] s;
				int   [] i;
				float [] f;
				double [] d;

				switch (type) {

				case DataBuffer.TYPE_INT:
				case DataBuffer.TYPE_SHORT:
				case DataBuffer.TYPE_BYTE:
						i = new int[depth];
						r.getPixel(x,y,i);
						int ii[] = new int[1];
						ii[0]=i[0];
						return ii;

				case DataBuffer.TYPE_FLOAT:
						f = new float[depth];
						r.getPixel(x,y,f);
						float ff[]=new float[1];
						ff[0]=f[0];
						return ff;

				case DataBuffer.TYPE_DOUBLE:
						d = new double[depth];
						r.getPixel(x,y,d);
						double dd[] = new double[1];
						dd[0]=d[0];
						return dd;
				default:
						return(null);
				}
		}

		public void liveUpdate(double t, boolean off)
		{
				if (!line_drawn || !line_intersects)
						return;

				t = (t-ThemisGrapher.XMIN)/(ThemisGrapher.XMAX-ThemisGrapher.XMIN); //re-scale t

				if (t < 0.0 || t > 1.0)
						off = true;

				double m,x,y;

				x = p2.getY()-p1.getY(); //yes, getY for x
				y = -(p2.getX()-p1.getX()); //ditto

				m = Math.sqrt((x*x)+(y*y));

				x = (x/m) * LINELENGTH;
				y = (y/m) * LINELENGTH;

				if (off && oldQ!=null && oldLinePt!= null) {//we're turning off, so just erase
						oldQ.setLocation((x)+oldLinePt.getX(),(y)+oldLinePt.getY());
						drawLine(oldLinePt,oldQ,(Graphics2D)this.getGraphics(),false,Color.lightGray,3.0);
						oldQ.setLocation((-x)+oldLinePt.getX(),(-y)+oldLinePt.getY());
						drawLine(oldLinePt,oldQ,(Graphics2D)this.getGraphics(),false,Color.lightGray,3.0);
						oldQ=null;
						oldLinePt=null;
						return;
				}
	
//Erase old Line
				if (oldQ != null) {
						oldQ.setLocation((x)+oldLinePt.getX(),(y)+oldLinePt.getY());
						drawLine(oldLinePt,oldQ,(Graphics2D)this.getGraphics(),false,Color.lightGray,3.0);
						oldQ.setLocation((-x)+oldLinePt.getX(),(-y)+oldLinePt.getY());
						drawLine(oldLinePt,oldQ,(Graphics2D)this.getGraphics(),false,Color.lightGray,3.0);
				}

				linePt = new Point2D.Double((1.0-t)*p1.getX()+t*p2.getX(),
											(1.0-t)*p1.getY()+t*p2.getY());


				Q = new Point2D.Double(x+linePt.getX(),y+linePt.getY());
				drawLine(linePt,Q,(Graphics2D)this.getGraphics(),false,Color.lightGray,3.0);
				Q.setLocation((-x)+linePt.getX(),(-y)+linePt.getY());
				drawLine(linePt,Q,(Graphics2D)this.getGraphics(),false,Color.lightGray,3.0);

				oldQ=Q;
				oldLinePt=linePt;
		}

		public void mouseDragged(MouseEvent e) 
		{
				int i;
				Point2D m;

				if (!Main.inTimeProjection())
						m = getProj().screen.toScreenLocal(e.getPoint());
				else
						m = e.getPoint();

				int x = (int)m.getX();
				int y = (int)m.getY();
				if (!firstClick) {

						p2.setLocation((float)x,(float)y);
						wp2.setLocation(getProj().screen.toWorld(e.getPoint()));

						if (oldwp2 != null)
								drawLine(wp1,oldwp2,getOnScreenG2(),false,Color.gray,getProj().getPixelSize().getHeight());
						else
								oldwp2=new Point2D.Double();

						drawLine(wp1,wp2,getOnScreenG2(),false,Color.gray,getProj().getPixelSize().getHeight());

						oldwp2.setLocation(wp2);
				}
	
		}
		public synchronized void mouseReleased(MouseEvent e) 
		{
				int f = e.getModifiers();
				if (f != InputEvent.BUTTON1_MASK) 
						return;

				int i;
				Point2D m;

				if (!Main.inTimeProjection())
						m = getProj().screen.toScreenLocal(e.getPoint());
				else
						m = e.getPoint();


				int x = (int)m.getX();
				int y = (int)m.getY();
				p2.setLocation((float)x,(float)y);
				wp2.setLocation(getProj().screen.toWorld(e.getPoint()));
				drawLine(wp1,wp2,getOffScreenG2(),true,Color.white,getProj().getPixelSize().getHeight());
				repaint();
				firstClick=true;

				if(magnitude(p1,p2) < 2) { // ie, someone just clicked and released
						line_drawn = false;
						line_intersects = false;
						resample = false;
						return;
				}

				line_drawn = true;
				line_intersects = true;
				resample=true;

				sampleData();

		}
		public void mouseMoved(MouseEvent e)
		{
				int i;
				Point2D m;

				if (!Main.inTimeProjection())
						m = getProj().screen.toScreenLocal(e.getPoint());
				else
						m = e.getPoint();

				int x = (int)m.getX();
				int y = (int)m.getY();
		
				if (focusPanel == null) return;

				if (data.size() == 0) {
						log.print("No Rasters in our List!\n\tI Can't sample from this!!!");
						return;
				}

				Raster data_0=(Raster)data.get(0);

				if (data_0 == null)
						return;

				/*
				  If the screen has just been resized, it's possible to get X,Y values
				  for the NEW screen, but before the new offscreenImage has been assigned
				*/

				//First we check the Y-direction.  This is true for both projections

				if (y > data_0.getHeight() || y < 0) {
						log.println("Outta Bounds in the Y: "+y);
						return;
				}


				//Next we check projection types

				if(Main.inTimeProjection()) { //In TIME projection, we do a quick x validation
						if (x >= data_0.getWidth() || x < 0) {
								log.println("Outta Bounds in the X: "+x);
								return;
						}
				}



				/*
				  Now we cycle through our list of Rasters, pulling pixel arrays from each one.
				  We place each 'pixel' into our updateWrapper (an array of Objects).
				  After we've sampled ALL the layers, we call update 
				*/


				if (liveUpdate && line_intersects && !resample) { //gotta send back t [0.0, 1.0] which represents the 
						// first check if mouse is 'close' enough to the line
						testLine.setLine(p1,p2);
						double dis = testLine.ptSegDist(m);
						if (dis <= liveTOL) { //okay we're 'close' enough
								double ax,ay;
								double bx,by;
								double t;
								bx = x - p1.getX();
								by = y - p1.getY();
								ax = p2.getX() - p1.getX();
								ay = p2.getY() - p1.getY();


//				log.println("a("+ax+","+ay+") dot b("+bx+","+by+") = "+((bx*ax)+(by*ay)));
//				log.println("a("+ax+","+ay+") dot a("+ax+","+ay+") = "+((ax*ax)+(ay*ay)));
								t = ((bx*ax)+(by*ay))/((ax*ax)+(ay*ay)); 
//				log.println("t = "+t+"\n\n");

								if (t >= 0.0 && t <= 1.0)  //we're near the line
										((NumBackFocus)focusPanel).liveUpdate(t,false);
								else
										((NumBackFocus)focusPanel).liveUpdate(t,true);
						}
						else
								((NumBackFocus)focusPanel).liveUpdate(0.0,true);
				}
				
				String [] output;
				for(i=0;i<oSIs.size();i++){
						data_0=getRaster(i);
						if (data_0 != null) {
								updateWrapper[i]=extract_pixel(x,y,data_0);

								output = NumBackFocus.value2String(updateWrapper[i]);
/*

log.print("Value at --> ["+x+","+y+"]: ");
for(int j=0;j<output.length;j++){
log.print(output[j]+" ");
}
log.println("");
*/


						}
				}				


				((NumBackFocus)focusPanel).updateValue(updateWrapper);
		}

		protected void drawLine(Point2D pp1, Point2D pp2, Graphics2D g, boolean mode, Color color, double thickness)
		{

				Color c = (color == null ? Color.gray : color);

				Line2D l = new Line2D.Double(pp1.getX(),pp1.getY(),pp2.getX(),pp2.getY());
	
				if (mode){
						g.setPaintMode();
						g.setColor(color);
				}

				else
						g.setXORMode(color);

				g.setStroke(new BasicStroke((float)thickness));
				g.draw(l);
		}

		public void savePPM (String file,BufferedImage bi) throws IOException
		{
				int width = bi.getWidth();
				int height = bi.getHeight();
				int [] buffer = bi.getRGB(0,0,width,height,(int [])null,0,width);

				FileOutputStream fileOut = new FileOutputStream (file);
				BufferedOutputStream bufferedOut = new BufferedOutputStream (fileOut);
				DataOutputStream dataOut = new DataOutputStream (bufferedOut);
				dataOut.write ("P6\n".getBytes ());
				dataOut.write ((width + " " + height + "\n").getBytes ());
				dataOut.write ("255\n".getBytes ());


				for (int i = 0; i < width * height; ++ i) {
						int rgb = buffer[i];
						dataOut.write (rgb >> 16);
						dataOut.write (rgb >> 8);
						dataOut.write (rgb >> 0);
				}
				dataOut.close ();
		}


		public void mousePressed(MouseEvent e) 
		{
				int f = e.getModifiers();
				if (f == InputEvent.BUTTON2_MASK) {
						log.println("Saving "+oSIs.size()+" image"+(oSIs.size() > 1 ? "s.":"."));
						for (int i = 0;i < oSIs.size(); i++){
								String name = "image_layer_"+Integer.toString(i);
								Dump_oSIs(i,name);


						}
						return;
				}

				else if (f != InputEvent.BUTTON1_MASK)
						return;

				Point2D m;

				if (!Main.inTimeProjection())
						m = getProj().screen.toScreenLocal(e.getPoint());
				else
						m = e.getPoint();


				int x = (int)m.getX();
				int y = (int)m.getY();

				if (firstClick)	{
						clearOffScreen();
						repaint();
						p1.setLocation((float)x,(float)y);
						wp1.setLocation(getProj().screen.toWorld(e.getPoint()));
						oldwp2=null;
						firstClick=false;
						line_drawn=false;
						return;

				}

		}

		public void sampleData()
		{


				int i,j,k;
				int x,y;
				int idx=0;
				Raster data_0;
				float t, xf,yf;

				if (p1 == null || p2 == null) {
						log.println("One or more anchor points are invalid");
						return;
				}

				int samples = magnitude(p1,p2);

				if (samples < 1) {
						log.println("Samples less than 1");
						return;
				}

				if (oSIs == null) {
						log.println("Buffers List is NULL");
						return;
				}

				if (oSIs.size() < 1) {
						log.println("Buffers List is Empty");
						return;
				}

				if (focusPanel == null) {
						log.println("FocusPanel is NULL");
						return;
				}


				log.println("Collecting "+samples+" pixels");
		

				resample=false;


				String [][] values;
				String [] output;	
				double [] xValues=new double[samples];
				Object tmpObj;
				Point2D tmpPoint = new Point2D.Double();

				values = new String[oSIs.size()][]; //This is how deep it is;
				Point2D curr;
				Point2D down;
				Rectangle2D r = getProj().getScreenWindow();
				int w = (int)r.getWidth()-1;
				int h = (int)r.getHeight()-1;

				for (i=0;i<oSIs.size();i++)
						values[i] = new String[samples]; //need one for every pixel in our line

				for (i=0;i<samples;i++){
						down = getProj().screen.toWorld(p1);

						t=(float)i/(float)(samples-1);
						xf = (1.0f-t)*(float)p1.getX() + t*(float)p2.getX();
						yf = (1.0f-t)*(float)p1.getY() + t*(float)p2.getY();
						x=(int)Math.rint(xf);
						y=(int)Math.rint(yf);

						curr = getProj().screen.toWorld(new Point2D.Double(x,y));

						if(Main.inTimeProjection())
						{
								down = Main.PO.convWorldToSpatialFromProj(getProj(), down);
								curr = Main.PO.convWorldToSpatialFromProj(getProj(), curr);
						}
						else
						{
								down = Main.PO.convWorldToSpatial(down);
								curr = Main.PO.convWorldToSpatial(curr);
						}
						xValues[i]=((getProj().spatial.distance(down, curr) * 3390.0 * 2*Math.PI / 360.0));

						idx = 0;
//Need to clamp x and y
						if (x < 0) x=0;
						if (y < 0) y=0;
						if (x > w) x = w;
						if (y > h) y = h;


						for(j=0;j<oSIs.size();j++){
								data_0=getRaster(j);
								if (data_0 != null) {
										tmpObj = extract_pixel(x,y,data_0);
										output = NumBackFocus.value2String(tmpObj);
										for(k=0;k<output.length;k++){
												values[idx][i]=new String(output[k]);
												idx++;
										}
								}
								else {

										log.println("Tried to get raster at: "+j+"  This returned NULL.  OffScreenBuffer.size() = "+oSIs.size());

								}
						}
				}

/***DEBUG**
	for(i=0;i<idx;i++){
	for(j=0;j<samples;j++){
	log.println("Bank #"+i+" ("+x+","+y+")  --> "+values[i][j]);
	}
	}
*/
				((NumBackFocus)focusPanel).addData(values,xValues);

		}


		public void mouseEntered(MouseEvent e) {}
		public void mouseExited(MouseEvent e) 
		{
				//Cleanout any old cross-hair junk before we change the view
				if (focusPanel != null) 
						((NumBackFocus)focusPanel).liveUpdate(0.0,true); //This will clear the last draw
		}

		public void mouseClicked(MouseEvent e) {}


		public void Dump_oSIs(int idx, String name)
		{
				WritableRenderedImage fooImage = (WritableRenderedImage)oSIs.get(idx); //Just get the first one
				dumpImage(fooImage,name);
		}
        public void viewCleanup()
        {
				//If this function is being called, we need to NULL out the layer's reference to the view
				((NumBackLayer)getLayer()).myLView = null;
        }

		public void dumpImage(RenderedImage ti, String fn)
		{
				String filename;
				RenderedImage fooImage;
				DataOutputStream dos = null;

				if (ti == null) {
						log.println("You sent a NULL image to be dumped!");
						return;
				}

				else fooImage = (RenderedImage)ti;

				if (fn == null)
						filename = "debug_oSIs.dump";
				else
						filename = fn;

				try {
						dos = new DataOutputStream(new FileOutputStream(filename));
						Raster fooRaster = fooImage.getData();
						int []pix = new int[10];
						int row = fooRaster.getHeight();
						int col = fooRaster.getWidth();
						int startX = fooRaster.getMinX();
						int startY = fooRaster.getMinY();
						int i,j;

						for (i = startY ; i < (startY+row) ;i++){
								for (j = startX ; j < (startX+col) ;j++){
										fooRaster.getPixel(j,i,pix);
										dos.writeShort((short)pix[0]);
								}
						}

						log.println("Done!");
						log.aprintln("Wrote a "+col+" x "+row+" Raw SHORT image to: "+filename);

				}

				catch (IOException e){log.aprintln(e);}
				finally {
						try {
								if (dos != null) dos.close();
						}
						catch (IOException e){}
				}
		}

		protected int magnitude(Point2D p1, Point2D p2)
		{

				return((int)Math.round(p1.distance(p2)));
		}

		public void clearLine()
		{
				line_drawn = false;
				ppd_changed = false;
				line_intersects = false;
				line_focus_changed = false;
				p1.setLocation(0.0,0.0);
				p2.setLocation(0.0,0.0);
		}
/*
  public Point2D fuScreenToLocalScreen(Point2D fuP)
  {

  Point2D fuS = getProj().screen.toScreenLocal(fuP);


  log.println("fuP ="+fuP);
  log.println("fuS ="+fuS+"\n\n");

  return(fuS);
  }
*/


		public void setLiveUpdateEnabled(boolean b)
		{
				liveUpdate = b;
				repaint();
		}

		public Color getColor(int idx)
		{
				NumBackLayer myLayer = (NumBackLayer)getLayer();

				if (idx >= myLayer.bdpb.size()) {
						log.print("Requested a color from index: "+idx+" but max index is: "+(myLayer.bdpb.size()-1));
						return(Color.black);
				}

				NumBackDialogParameterBlock nbdpb = (NumBackDialogParameterBlock) myLayer.bdpb.get(idx);

				return(nbdpb.color);
		}	

}
