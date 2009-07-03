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


package edu.asu.jmars.layer.stamp;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj.Projection_OC;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.Util;
import edu.asu.msff.Stamp;

public class ImageFrame {
	public StampImage srcImage;
	public Cell cell;
	public Rectangle srcRange;
	public int frameNum;
	public int startx;
	public long starty;
	public long height;
	public int width;
	public BufferedImage dstImage;
	
	private static final DebugLog log = DebugLog.instance();
    
    ImageFrame(StampImage stampImage, Cell cell, Rectangle srcRange, int frameNum)
    {        	
    	this.cell = cell;
        this.srcImage = stampImage;
        this.srcRange = srcRange;
        this.frameNum = frameNum;
        
    	long totalLines = srcImage.getNumLines();
    	
    	long linesPerFrame;
    	if (srcImage.frames.length==1) {
    		linesPerFrame = totalLines;
    	} else {
        	linesPerFrame = (long)(totalLines*srcImage.framePct);
    	}
        
    	width = srcImage.getWidth()/srcImage.horizontalSplitCnt; 
    	        	
   		startx=(frameNum%srcImage.horizontalSplitCnt)*width;  // odd frames are on the right
    	
    	starty = ((frameNum)/srcImage.horizontalSplitCnt) * linesPerFrame;

    	height = linesPerFrame;
    	
    	if (frameNum!=0 && frameNum>=srcImage.frames.length-srcImage.horizontalSplitCnt) {
    		height = (long)(totalLines * srcImage.lastFramePct);
    	}    	
    }
    
    synchronized boolean hasImageLocally() {
    	if (dstImage!=null) {
    		return true;
    	}
    	
        String cachedFrameName = srcImage.getCachedImageFrameName(frameNum);

        dstImage = StampImageFactory.loadImage(cachedFrameName);
        
        if (dstImage != null) {
     	   return true;
        }
        
    	String urlStr=getUrlStr();
    	
    	String cacheFileName = StampImage.STAMP_CACHE + "src/"+StampImageFactory.getStrippedFilename(urlStr);
    	
    	if (null!=StampImageFactory.loadImage(cacheFileName)) {
    		return true;
    	}
    	    	    	
    	return false;
    }
    
    private String urlStr=null;
    
    public String getUrlStr() {
    	if (urlStr==null) {    		
			int scale =Main.testDriver.mainWindow.getMagnification(); 
	
	    	StampShape s = srcImage.myStamp;
			String instrument = srcImage.getInstrument().toString();
			String type = srcImage.imageType;
		
	    	urlStr=StampLayer.stampURL+"ImageServer?instrument="+instrument+"&id="+s.getId()+StampLayer.versionStr;
	    	
	    	if (type!=null && type.length()>=0) {
	    		urlStr+="&imageType="+URLEncoder.encode(type);
	    	}
	    	
	    	urlStr+="&zoom="+scale;
	    	urlStr+="&startx="+startx;
	    	urlStr+="&starty="+starty;
	    	urlStr+="&height="+height;
	    	urlStr+="&width="+width;
    	}
    	return urlStr;
    }
    
	/**
	 * Loads image from URL reference contained in {@link Stamp} instance; 
	 * pops up progress monitor dialog as needed.  Supports
	 * specialized tile-based caching mechanism used in {@link MocImage}.
	 * 
	 * @param s stamp containing valid URL.
	 */
	public BufferedImage loadSrcImage()
	{
				
		BufferedImage image=null;
	    try {
        	String urlStr=getUrlStr();
        	        	
        	String cacheFileName = StampImage.STAMP_CACHE + "src/"+StampImageFactory.getStrippedFilename(urlStr);
        	
        	image = StampImageFactory.loadImage(cacheFileName);
        	
        	if (image==null) {                    		
        		image = StampImageFactory.loadImage(new URL(urlStr));
        		if (image!=null) {
        			FrameFetcher.saveSrcImage(urlStr, image);
        		}
        	} 
	    }
	    catch (Throwable e) {
	        log.aprintln(e);
	    }
	    
	    return image;
	}
	
	
    public double biggest(double x, double y, double z)
	 {
	   double m = Math.abs(x);
	   if(m < Math.abs(y))m=Math.abs(y);
	   if(m < Math.abs(z))m=Math.abs(z);
	   return(m);
	 }
    
    synchronized BufferedImage getImage()
    {
       if (dstImage != null) {
           return  dstImage;
       }
        
       String cachedFrameName = srcImage.getCachedImageFrameName(frameNum);

       dstImage = StampImageFactory.loadImage(cachedFrameName);
       
       if (dstImage != null) {
    	   return dstImage;
       }
        
       BufferedImage frameSrcImage = null;
        
       Instrument instrument = srcImage.getInstrument();
       
       if (instrument==Instrument.CTX
    		   || instrument==Instrument.HIRISE
    		   || instrument==Instrument.MOC
    		   || instrument==Instrument.VIKING
    		   || instrument==Instrument.MOSAIC
    		   || instrument==Instrument.HRSC
    		   || instrument==Instrument.MAP
    		   || instrument==Instrument.CRISM
    		   || instrument==Instrument.ASTER
    		   || instrument==Instrument.APOLLO) {     	
        	frameSrcImage = loadSrcImage();
       }

       Rectangle2D where = cell.getWorldBounds();
        
       
       int renderPPD=srcImage.renderPPD;
              
       // Determine the size of the projected frame image
       int dstW = (int) Math.ceil(where.getWidth()  * renderPPD);
       int dstH = (int) Math.ceil(where.getHeight() * renderPPD);
        
       // 
       dstW--;
       dstH--;       
       
       // BufferedImage gets all cranky about images with 0 sized dimensions
       if (dstW<=0) {
    	   dstW=1;
       }
       
       if (dstH<=0) {
    	   dstH=1;
       }
       
       dstImage = Util.newBufferedImage(dstW, dstH);
        
       if (dstImage == null) {
           log.aprintln("out of memory");
           return null;
       }
                
       /////// VARIABLES FOR THE for() LOOP BELOW
       // srcPt:   Stores a point location in the source image data
       // unitPt: Stores a point location in the unit square
       Point srcPt = new Point();
       Point2D.Double unitPt = new Point2D.Double();
       HVector spatialPt = new HVector();
        
       /////// CONSTANTS FOR THE for() LOOP BELOW
       // baseX: Pixel-wise world coordinate origin of the destination
       // baseY: Pixel-wise world coordinate origin of the destination
       double baseX = where.getMinX();
       double baseY = where.getMaxY(); // image y coords run top-down
       double X_ZERO = -0.5 / dstW;
       double Y_ZERO = -0.5 / dstH;
       double X_ONE = 1 + 0.5 / dstW;
       double Y_ONE = 1 + 0.5 / dstH;
               
       HashMap<String, String> projParams = null;
       
       // HRSC projection values
       double line_proj_offset = 0;
       double map_resolution = 0;
       double sample_proj_offset = 0;
       double center_lon = 0;
       double map_scale = 0;
       String map_projection_type = "";
       
       // Change this test to look for SINUSOIDAL, STEREOGRAPHIC, or UNPROJECTED
       if (instrument==Instrument.HRSC) {
    	   projParams=srcImage.myStamp.getProjectionParams();

    	   line_proj_offset = Double.parseDouble(projParams.get("line_projection_offset"));
    	   map_resolution = Double.parseDouble(projParams.get("map_resolution"));
    	   sample_proj_offset = Double.parseDouble(projParams.get("sample_projection_offset"));
    	   center_lon = Double.parseDouble(projParams.get("center_longitude"));
    	   map_scale = Double.parseDouble(projParams.get("map_scale"));
    	   map_projection_type = projParams.get("map_projection_type");
       }
       
       BufferedImage prevImage=null;
       BufferedImage nextImage=null;
       
       final Projection_OC proj = (Projection_OC)Main.PO;
       
       final HVector center =proj.getCenter();
       final HVector up = proj.getUp();

       // Variables used and reused by the loop below:
       final double n2 = Math.sqrt(up.x*up.x+up.y*up.y+up.z*up.z);
   		
       final double wx = up.x / n2;
       final double wy = up.y / n2;
       final double wz = up.z / n2;
       final double bigw = biggest(wx, wy, wz);
   		
       final double rx = wx / bigw;
       final double ry = wy / bigw;
       final double rz = wz / bigw;

       final double dotu = rx*rx + ry*ry + rz*rz;
			
       double ptx;
       double pty;
       double ptz;

       long startTime = System.currentTimeMillis();
       
       long realPixels=0;
       
       double sinTheta[] = new double[dstW];
       double cosTheta[] = new double[dstW];
       
	   for(int i=0; i<dstW; i++) {
		   double x = Math.toRadians(baseX + (double) i / srcImage.renderPPD);
		   sinTheta[i]=Math.sin(x);
		   cosTheta[i]=Math.cos(x);
	   }

		final double prx[] = new double[dstH];
		final double pry[] = new double[dstH];
		final double prz[] = new double[dstH];
		
		final double v1x[] = new double[dstH];
		final double v1y[] = new double[dstH];
		final double v1z[] = new double[dstH];

		final double v2x[] = new double[dstH];
		final double v2y[] = new double[dstH];
		final double v2z[] = new double[dstH];

       for(int j=0; j<dstH; j++) {
           double y = Math.toRadians(baseY - (double) j / srcImage.renderPPD);
           double sin=Math.sin(y);
           double cos=Math.cos(y);
           
       		double nx = center.x * cos + up.x * sin;
       		double ny = center.y * cos + up.y * sin;
       		double nz = center.z * cos + up.z * sin;
       		double bign = biggest(nx, ny, nz);
       		
			double tx = nx / bign;
			double ty = ny / bign;
			double tz = nz / bign;

			double dotv = rx*tx + ry*ty + rz*tz;
			
			double scaley = dotv*bign/dotu;
			
			prx[j] = rx * scaley;
			pry[j] = ry * scaley;
			prz[j] = rz * scaley;

			v1x[j] = nx-prx[j];
			v1y[j] = ny-pry[j];
			v1z[j] = nz-prz[j];
			
			v2x[j] = wy * v1z[j] - wz * v1y[j];
			v2y[j] = wz * v1x[j] - wx * v1z[j];
			v2z[j] = wx * v1y[j] - wy * v1x[j]; 				 				
       }
       
       final int srcWidth;
       final int srcHeight;
       
       if (frameSrcImage!=null) {
    	   srcWidth = frameSrcImage.getWidth()-1;
    	   srcHeight = frameSrcImage.getHeight()-1;
       } else {
    	   srcWidth = srcRange.width-1;
    	   srcHeight = srcRange.height-1;
       }
       
       for(int j=0; j<dstH; j++) {
    	   for(int i=0; i<dstW; i++) {
    		   
     			ptx = v1x[j] * cosTheta[i] + v2x[j] * sinTheta[i] + prx[j];
     			pty = v1y[j] * cosTheta[i] + v2y[j] * sinTheta[i] + pry[j];
     			ptz = v1z[j] * cosTheta[i] + v2z[j] * sinTheta[i] + prz[j];
           		           		
           		final double ptUnitz = ptz / Math.sqrt(ptx*ptx + pty*pty + ptz*ptz);
           		
           		final double cosLon;
           		final double sinLon;
           		           		
           		// This is code to convert arctan2 to atan
    	   		int sign=1;
    	   		
    	   		if (pty<0) { 
    	   			sign = -1; 
    	   		};

    	   		
    	   		if (ptx == 0) {    	   			
    	   			cosLon = 0;
    	   			sinLon = sign * 1;
    	   		} else 

           		if(pty != 0) {
        	   		double x1 = Math.abs(pty/ptx);

        	   		if (ptx>0) {
               			cosLon = 1.0 / Math.sqrt(x1*x1 + 1);
               			sinLon = sign * x1 / Math.sqrt(x1*x1 + 1);
        	   		} else {
               			cosLon = -1.0 / Math.sqrt(x1*x1 + 1);
               			sinLon = sign * x1 / Math.sqrt(x1*x1 + 1);
        	   		}           			
           		} else if (ptx < 0) {
           			cosLon = -1;
           			sinLon = 0;
           		} else {
           			cosLon = 1;
           			sinLon = 0;
           		}
                		           		
           		double cosLat = Math.sqrt(1-ptUnitz*ptUnitz);
           		           		
           		spatialPt.x = cosLat * cosLon;
           		spatialPt.y = cosLat * sinLon;
           		spatialPt.z = ptUnitz; // since latOf = Math.asin(ptUnitz);
           		
                // HRSC rendering doesn't use the unitPt, and 
                // running this block occasionally results in missed pixels
                if (instrument!=Instrument.HRSC) {
//                 Uninterpolate from spatial coordinates to the unit
//                 square.
                	
                // Change to call uninterpolateFast, which saves about 
                // 400 ms per 320,000 pixel tile.  This call is still responsible
                // for about 130 of the 330 remaining ms per tile.
               	cell.uninterpolateFast(spatialPt, unitPt);
                
                if(unitPt.x < 0)
                    if(unitPt.x >= X_ZERO)
                        unitPt.x = 0;
                    else
                        continue;
                else if(unitPt.x > 1)
                    if(unitPt.x <= X_ONE)
                        unitPt.x = 1;
                    else
                        continue;
                
                if(unitPt.y < 0)
                    if(unitPt.y >= Y_ZERO)
                        unitPt.y = 0;
                    else
                        continue;
                else if(unitPt.y > 1)
                    if(unitPt.y <= Y_ONE)
                        unitPt.y = 1;
                    else
                        continue;
            }                
                // Finally, convert from unit square coordinates to
                // source image pixel coordinates.
                
                
                if (frameSrcImage==null) {
            		srcPt.setLocation(
                              (int)(   unitPt.x *(srcWidth) ) + srcRange.x,
                              (int)((1-unitPt.y)*(srcHeight)) + srcRange.y);                	
                } else {
                	if (instrument==Instrument.HRSC) {
                    	// These expand to surprisingly expensive operations
                        double lat=spatialPt.lat();
                        double lon=360-spatialPt.lon();
                                        		
                		double radius = 3376.2; // verify this!
                		double line;
                		double sample;
                		// Sinusoidal
                		//
                		if (map_projection_type.equalsIgnoreCase("SINUSOIDAL")) {  // sinusoidal
                			line = (line_proj_offset - (lat * map_resolution));
                			sample = (sample_proj_offset + (lon - center_lon)*map_resolution*Math.cos(Math.toRadians(lat)));

                			if (sample>200000) { // Pretty obviously wrong for HRSC
                				if (lon%360>350) {
                					sample = (sample_proj_offset + (lon - (360+center_lon))*map_resolution*Math.cos(Math.toRadians(lat)));
                				}
                			} else if (sample < -100) {
               					sample = (sample_proj_offset + (lon - (center_lon-360))*map_resolution*Math.cos(Math.toRadians(lat)));
                			}
                				
                		} else { // polar stereographic
                			if (true) { // north
                				Point2D xy = lonLat2xy(lon, lat);
                				double x = xy.getX();
                				sample = (x / map_scale) + sample_proj_offset + 1;
                				
                				double y = xy.getY();
                				line = (y / map_scale) + line_proj_offset - 1;
           
                				// Run this depending on spacecraft orientation!
                				double totLines = srcImage.getNumLines();
                				
                				line = totLines - line;
                			} else { // south
                				//System.out.println("SOUTH!");
//                				sample = sample_proj_offset + (2 * radius * Math.tan(Math.PI / 4 + lat / 2) * Math.sin(lon - center_lon));
//                				line =   line_proj_offset - (2 * radius * Math.tan(Math.PI / 4 + lat / 2) * Math.cos(lon - center_lon));
                				                				
                				Point2D xy = lonLat2xy(lon, lat);
                				double x = xy.getX();
                				sample = (x / map_scale) + sample_proj_offset + 1;
                				
                				double y = xy.getY();
                				line = (y / map_scale) + line_proj_offset - 1;
           
                				// Run this depending on spacecraft orientation!
                				double totLines = srcImage.getNumLines();
                				
                				line = totLines - line;
                			}
                		}
                		
                		if (sample<0) {
                			log.println("Sample of " + sample + " is less than 1, skipping");
                			continue;
                		}
                		
                		if (line<0) {
                			log.println("Line of " + line + " is less than 0, skipping");
                			continue;
                		}

                		if (line>srcImage.getNumLines()) {
                			log.println("Line is greater than srcImage height!");
                			continue;
                		}
                		
                		if (srcImage.framePointsFaked) {
                			sample-=startx;
                			line-=starty;
                		}
                	
                		double scale = renderPPD*100.0 / map_resolution;
                		scale/=100;
                		if (scale>1) scale = 1;
                		
                		line=(line*scale);
                		sample=(sample*scale);
                		
                		line = Math.round(line);
                		sample = Math.round(sample);
                		
                  		BufferedImage hrscSrcImage = frameSrcImage;
                  	  
                		if (sample<0 && frameNum>0) {
                			if (prevImage==null) {
                				prevImage=srcImage.frames[frameNum-1].loadSrcImage();
                			}
                			
                			hrscSrcImage=prevImage;
                			sample+=hrscSrcImage.getWidth();
                		}
                		
                		if (line<0) {
                			log.println("Line of: " + line + " is less than 0");
                			continue;
                		}
                		
                		
                		if (sample>=frameSrcImage.getWidth() && srcImage.frames.length > frameNum+1) {
                			if (nextImage==null) {
                				nextImage=srcImage.frames[frameNum+1].loadSrcImage();
                			}
                			hrscSrcImage=nextImage;
                			sample-=frameSrcImage.getWidth();
                		}
                		
                		if (sample>=hrscSrcImage.getWidth()) {
                			log.println("Sample of " + sample + " is greater than width");
                			continue;
                		}
                		
                		// TODO: Is this reasonable??
                		if (line==frameSrcImage.getHeight()) {
                			line--;
                		}
                		
                		if (line>=frameSrcImage.getHeight()) {
                			log.println("Line of " + line + " is greater than height");
                			continue;
                		}
                		
                		srcPt.setLocation((int)sample, (int)line);
                		
                		try {
                		dstImage.setRGB(i, j, hrscSrcImage.getRGB(srcPt.x,
                                srcPt.y));
                		} catch (ArrayIndexOutOfBoundsException e) {
                			log.println("Out of bounds: " + sample + ", " + line);
                		}
                		
                		continue;
                	} else {
                	// height = frame height                	
                	srcPt.setLocation(
                            (int)(   unitPt.x *(srcWidth) ) ,
                            (int)((1-unitPt.y)*(srcHeight)) );
                	}
                }
                
                // Draw the pixel in the destination buffer!
                try {
                	realPixels++;
                	if (frameSrcImage==null) {
                		dstImage.setRGB(i, j, srcImage.getRGB(srcPt.x,
                                                          srcPt.y));
                	} else {
                		dstImage.setRGB(i, j, frameSrcImage.getRGB(srcPt.x,
                                srcPt.y));                		
                	}
                }
                catch (Exception e) {
                    log.aprintln("exception in frame #" + frameNum +
                                 " while drawing pixel at i=" + i + " j=" + j);
                    log.aprintln("srcPt.x = " + srcPt.x + " and srcPt.y = " + srcPt.y);
                    log.aprintln(e);
                    return dstImage;
                }
            }

       }
       
//        long pixels = dstImage.getHeight() * dstImage.getWidth(); 
//        System.out.println("Time to project tile of " + pixels + " pixels: " + (System.currentTimeMillis() - startTime));
//        System.out.println("Time to project tile of " + realPixels + " real pixels: " + (System.currentTimeMillis() - startTime));

        // Save this on a different thread
        if (dstImage != null) {
        	new PNGWriter(cachedFrameName, dstImage).queueProcessing();
//            StampImage.savePngImage(cachedFrameName, dstImage);
        }

//        System.out.println("Time including writing file: " + (System.currentTimeMillis() - startTime));

        return dstImage;
    }                    

	static ExecutorService pool;
    static int writeCnt=0;
	class PNGWriter implements Runnable {
		BufferedImage image=null;
		String filename=null;
	
		PNGWriter(String name, BufferedImage data) {
			image=data;
			filename=name;
		}
		
		public void queueProcessing() {
			synchronized (this) {
				if (pool == null) {
					int procs = Math.max(2, Runtime.getRuntime().availableProcessors());
					pool = Executors.newFixedThreadPool(procs, new StampThreadFactory("Stamp PNG Writer"));
				}
				
				pool.execute(this);
			}
		}
			
		public void run() {
//			System.out.println("Image save started");
			StampImage.savePngImage(filename, image);
//			System.out.println("Image saved.");
//			System.out.println("Written " + ++writeCnt);
		}		
	}
    
	public static Point2D lonLat2xy(double lon, double lat) {
		double radius = 3375.8; // verify this!
		double center_lon = 0;
		double center_lat = -90; // for south pole
		
		double phi, lam, rho;
		double x=999,y=999;
		
		double sin_phi1 = 1; // why?
		
		phi = Math.toRadians(lat);
		lam = Math.toRadians(lon - center_lon);
		
		if (center_lat == 90) { // North
			rho = radius * Math.cos(phi) * (1 + sin_phi1) / (1 + Math.sin(phi));
			
		    x =  rho * Math.sin(lam);
		    y = -rho * Math.cos(lam);
		} else if (center_lat == -90) { // South			
			rho = radius * Math.cos(phi) * (1 + sin_phi1) / (1 - Math.sin(phi));
		    x = rho * Math.sin(lam);
		    y = rho * Math.cos(lam);
		}
		
		
//		double tmpIn = Math.PI / 4 - Math.toRadians(lat/2);
//				
//		double tmp = Math.tan(tmpIn);
//				
//		double x = (2 * radius * tmp * Math.sin(Math.toRadians(lon - 0)));
//		
//		double y = (-2 * radius * tmp * Math.cos(Math.toRadians(lon - 0)));
		
		return new Point2D.Double(x, y);		
	}
	
	// Testing purposes only
	public static Point2D xy2lonLat(double x, double y) {
		double radius = 3376.2; // verify this!
				
		// South version only
		double center_lat = -90.0;
		
		y=-1*y;
		
		double lat = Math.PI/2 - 2 * Math.atan(
		   Math.sqrt(
				   ( Math.pow(x,2) + Math.pow(y,2) )
			     / (4 * radius * radius)	   
		   
		   )				
		);
								
		double lon;
		
		if (center_lat == 90.0) {
			lon = Math.atan(x / (-1 * y));
		} else {  // better be -90.0
			if (y!=0) {
				lon = Math.atan(x / y);
			} else if(x >= 0) {
			    lon = Math.PI/2;
			} else {
			    lon = 1.5 * Math.PI;
			}
		}
		
//		if (x < 0) { 
//			if (y > 0) {
//				lon += Math.PI;
//	        } else {
//	            lon += 2*Math.PI;
//            }
//	    } else if (y > 0) {
//	    	lon += Math.PI;
//	    } else {
//	    	// do nothing;
//	    }
				
		lon = Math.toDegrees(lon);

		lat = Math.toDegrees(lat);
		
		return new Point2D.Double(lon, lat);		
	}

	public static void main(String args[]) {
		
		double lon = 300;
		double lat = -80;
		
		double map_scale = 0.072;
		double line_proj_offset = 14505;
		double sample_proj_offset = 14910;
		
		double sample = 0;
		double line = 0;
		
		Point2D xy = lonLat2xy(lon, lat);
		double x = xy.getX();
		sample = (x / map_scale) + sample_proj_offset + 1;
		
		double y = xy.getY();
		line = (y / map_scale) + line_proj_offset - 1;

		System.out.println("x = " + x);
		System.out.println("y = " + y);
		
		System.out.println(xy2lonLat(x,y));
		
		System.out.println("line = " + line);
		System.out.println("sample = " + sample);
		
		double radius = 3376.2; // verify this!
		double center_lon = 0;
		
		sample = sample_proj_offset + (2 * radius * Math.tan(Math.PI / 4 + lat / 2) * Math.sin(lon - center_lon));
		line =   line_proj_offset - (2 * radius * Math.tan(Math.PI / 4 + lat / 2) * Math.cos(lon - center_lon));

		System.out.println("line = " + line);
		System.out.println("sample = " + sample);

		
	}
}