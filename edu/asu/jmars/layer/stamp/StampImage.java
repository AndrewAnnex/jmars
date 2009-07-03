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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj.Projection_OC;
import edu.asu.jmars.layer.MultiProjection;
import edu.asu.jmars.layer.stamp.StampLayer.StampTask;
import edu.asu.jmars.layer.stamp.StampLayer.Status;
import edu.asu.jmars.layer.stamp.chart.ChartView;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.IgnoreComposite;
import edu.asu.jmars.util.Util;

public class StampImage 
{ 	
	// Set of constants for tracking or manipulating the current rotation/flip state 
    static final int IMAGE_ROTATED_180 = 0;
    static final int IMAGE_HFLIPPED = 1;
    static final int IMAGE_VFLIPPED = 2;
    static final int IMAGE_NORMAL = 3;

    /**
     ** Mapping from current image state and operation being applied
     ** to resulting image state.  Usage:
     **
     ** result_state = IMAGE_RESULT_MAP[state][operation]
     **/
    static final int[][] IMAGE_RESULT_MAP = new int[][] {
	/* Operation:                   *IMAGE_ROTATED_180* *IMAGE_HFLIPPED*   *IMAGE_VFLIPPED*   */
	/* Start: IMAGE_ROTATED_180 */ { IMAGE_NORMAL,       IMAGE_VFLIPPED,    IMAGE_HFLIPPED},
	/* Start: IMAGE_HFLIPPED    */ { IMAGE_VFLIPPED,     IMAGE_NORMAL,      IMAGE_ROTATED_180},
	/* Start: IMAGE_VFLIPPED    */ { IMAGE_HFLIPPED,     IMAGE_ROTATED_180, IMAGE_NORMAL},
	/* Start: IMAGE_NORMAL      */ { IMAGE_ROTATED_180,  IMAGE_HFLIPPED,    IMAGE_VFLIPPED}
    }; 

    public static final String STAMP_CACHE = Main.getJMarsPath() + "stamps" + System.getProperty("file.separator");
    
    private static final DebugLog log = DebugLog.instance();
    
    static {
    	File cache = new File(STAMP_CACHE);
    	if (!cache.exists()) {
    		if (!cache.mkdirs()) {
    			throw new IllegalStateException("Unable to create stamp cache directory, check permissions in " + Main.getJMarsPath());
    		}
    	} else if (!cache.isDirectory()) {
    		throw new IllegalStateException("Stamp cache cannot be created, found regular file at " + STAMP_CACHE);
    	}
    }
    
    protected String productID;
    protected String label;
    
    protected ImageFrame frames[];
    
    protected double framePct;
    protected double lastFramePct;
    
    protected int projHash;
    protected int renderPPD;
        
    protected int userRotateFlip = IMAGE_NORMAL;

    protected String imageType="";
     
    public String getImageType() {
    	return imageType;
    }
    
    // Needed to keep PdsImage happy
    public StampImage(StampShape shape, String productId, String instrument, String imageType) {
    	myStamp=shape;
    	this.instrument = Instrument.valueOf(instrument.toUpperCase());
    	this.imageType = imageType;
        this.productID = productId;
        label = null;                
    }
    
    BufferedImage image = null;
    
    StampShape myStamp = null;
    
    public StampImage(StampShape shape, BufferedImage image, String productId, String instrument, String imageType)
    {
    	this(shape, productId, instrument, imageType);
        this.image = image;    
    }
    
    protected Instrument instrument;
    
    public Instrument getInstrument() {
    	return instrument;
    }
        
    public String getLabel()
    {
        return  label;
    }
    
    /**
     ** Applies current image rotation/flipping state to specified
     ** set of image or image frame coordinates.
     **
     ** IMPORTANT NOTE!! - This method must be called from any subclass 
     ** implementation of {@link #getPoints} method that supports image rotation 
     ** or flipping.
     **
     ** @param corners Four-element array of corner points in following
     ** order: 
     ** <ul>          
     ** <li>   corners[0]   lower left corner coordinates (SW)
     ** <li>   corners[1]   lower right corner coordinates (SE)
     ** <li>   corners[2]   upper left corner coordinates (NW)
     ** <li>   corners[3]   upper right corners coordinates (NE)
     ** </ul>
     **
     ** @return Four-element array of corners points in same above
     ** order but with elements swapped as needed to apply the current
     ** image orientation.
     **
     ** @see #rotateFlip
     ** @see #getPoints
     **/
    public Point2D[] getOrientedPoints(Point2D[] corners)
    {
        if (corners == null || corners.length % 4 != 0)
            throw new IllegalArgumentException("null or improperly sized array of corner points");
        
        Point2D[] newCorners = new Point2D[corners.length];
        
        System.arraycopy(corners, 0, newCorners, 0, corners.length);
        
        int lastPtIdx = corners.length-1;
        
        switch (userRotateFlip) {
        case IMAGE_NORMAL:
            // No corner swaps
            newCorners[lastPtIdx-3] = corners[lastPtIdx-3];
            newCorners[lastPtIdx-2] = corners[lastPtIdx-2];
            newCorners[lastPtIdx-1] = corners[lastPtIdx-1];
            newCorners[lastPtIdx] = corners[lastPtIdx];
            break;
        case IMAGE_ROTATED_180:
            // Swapping SW and NE, SE and NW
            newCorners[lastPtIdx-3] = corners[lastPtIdx];
            newCorners[lastPtIdx-2] = corners[lastPtIdx-1];
            newCorners[lastPtIdx-1] = corners[lastPtIdx-2];
            newCorners[lastPtIdx] = corners[lastPtIdx-3];
            break;
        case IMAGE_HFLIPPED:
            // Swapping SW and SE, NW and NE
            newCorners[lastPtIdx-3] = corners[lastPtIdx-2];
            newCorners[lastPtIdx-2] = corners[lastPtIdx-3];
            newCorners[lastPtIdx-1] = corners[lastPtIdx];
            newCorners[lastPtIdx] = corners[lastPtIdx-1];
            break;
        case IMAGE_VFLIPPED:
            // Swapping SW and NW, SE and NE
            newCorners[lastPtIdx-3] = corners[lastPtIdx-1];
            newCorners[lastPtIdx-2] = corners[lastPtIdx];
            newCorners[lastPtIdx-1] = corners[lastPtIdx-3];
            newCorners[lastPtIdx] = corners[lastPtIdx-2];
            break;
        default:
            log.aprintln("bad internal image orientation state: " + userRotateFlip);
        break;
        }
        
        return newCorners;
    }

    
    /** 
     ** Rotates or flips image as specified relative to current 
     ** stamp image orientation.  Actual result of rotation/flipping
     ** is not realized until calls are made to #getImageFrame method.
     **
     ** @param operation legal operation values are {@link #IMAGE_ROTATED_180}, 
     ** {@link #IMAGE_HFLIPPED}, {@link #IMAGE_VFLIPPED}.
     **
     ** @see #getPoints
     ** @see #getOrientedPoints
     **/
    synchronized public void rotateFlip(int operation)
    {
        if (operation != IMAGE_ROTATED_180 &&
                operation != IMAGE_HFLIPPED &&
                operation != IMAGE_VFLIPPED) {
            log.aprintln("illegal rotate/flip operation code: " + operation);
            return;
        }
        
        // Determine new image state
        userRotateFlip = IMAGE_RESULT_MAP[userRotateFlip][operation];
        pts=null;
        recreateImageFrames(renderPPD);
    }
    
    private String realFilename = null;

    /**
     * Removes any non-legal filename characters that may appear in
     * stamp image id's and returns result as a filename.
     */
    public String getFilename()
    {
        if (realFilename != null)
            return realFilename;
        else
            realFilename = StampImageFactory.getStrippedFilename(productID);
        
        return realFilename;
    }

    
    /** 
     ** Returns rotation string for specified stamp image orientation
     **/
    protected String getImageFrameType(int imageState)
    {
        if (imageState != IMAGE_NORMAL &&
            imageState != IMAGE_ROTATED_180 &&
            imageState != IMAGE_HFLIPPED &&
            imageState != IMAGE_VFLIPPED) {
            log.aprintln("illegal rotate/flip image state: " + imageState);
            return "";
        }
        
        return imageTypeMap[imageState];
    }
    
    private static final String IMAGE_TYPE_NORMAL = "";
    private static final String IMAGE_TYPE_ROTATED = "_r180";
    private static final String IMAGE_TYPE_HFLIPPED = "_hflip";
    private static final String IMAGE_TYPE_VFLIPPED = "_vflip";
    
    // Mapping from image state to image type label.
    private String[] imageTypeMap = new String[] {
          IMAGE_TYPE_ROTATED,   // IMAGE_ROTATED_180
          IMAGE_TYPE_HFLIPPED,  // IMAGE_HFLIPPED
          IMAGE_TYPE_VFLIPPED,  // IMAGE_VLIPPED
          IMAGE_TYPE_NORMAL     // IMAGE_NORMAL
    };
    
    
    boolean framePointsFaked=false;
    
    /**
     ** Generates a new set of geometry points to subdivide a single 
     ** frame of geometry points into the specified number of divisions.
     ** Frames are created along lower-to-upper axis.
     ** <p>
     ** NOTE: This method uses the approximate interpolation scheme
     **       present in the HVector class.  Should probably only be used
     **       for client-side image subtiling purposes when more accurate
     **       data is unavailable from an image geometry database, etc.
     **
     ** @param pts  an array of four points corresponding to the four
     **             corners of an image frame:
     ** 
     ** <ul>          
     ** <li>          points[0]   lower left corner coordinates
     ** <li>          points[1]   lower right corner coordinates
     ** <li>          points[2]   upper left corner coordinates
     ** <li>          points[3]   upper right corners coordinates
     ** </ul>
     **
     ** @param frameSizeFactor  scaling factor for size of each frame along
     **                         divided axis between 0 and 1; e.g., 0.5
     **                         will divide image into two frames.
     **
     **                         If factor does not divide 1 evenly, last
     **                         frame will be sized to the fractional residual.
     **
     ** @return array of points organized as described for #getPoints(),
     **         whole image frame + subframes
     **
     **/
    protected Point2D[] getFakeFramePoints(Point2D[] pts, double frameSizeFactor)
    {
        if (pts == null ||
                pts.length != 4 ||
                frameSizeFactor <= 0 ||
                frameSizeFactor > 1)
        {
        	log.aprintln("frameSizeFactor = " + frameSizeFactor);
            log.aprintln("bad parameters");
            return null;
        }
        
        int numFrames = (int)Math.ceil(1 / frameSizeFactor);
        Point2D[] newPoints = new Point2D[numFrames * 4];
                
        // Convert image frame geometry to vector form.
        HVector ll = new HVector(pts[0]);
        HVector lr = new HVector(pts[1]);
        HVector ul = new HVector(pts[2]);
        HVector ur = new HVector(pts[3]);
        
        // Prepare uppper part of first subframe.
        Point2D nextUL = pts[2];
        Point2D nextUR = pts[3];
        
        // Create image subframe geometry points through
        // interpolation from whole image.  Do this
        // for all but the last subframe.  Frames start
        // from upper part of image.
        for (int i=0; i < numFrames-1; i++) {
            // Find lower left/right corners for new subframe
        	double val = frameSizeFactor * (i+1);
//            HVector newLL = ul.interpolate(ll, frameSizeFactor * (i+1));
//            HVector newLR = ur.interpolate(lr, frameSizeFactor * (i+1));

            HVector newLL = ul.interpolate(ll, val);
            HVector newLR = ur.interpolate(lr, val);

            // Store geometry for new subframe
            newPoints[i*4]   = newLL.toLonLat(null);
            newPoints[i*4+1] = newLR.toLonLat(null);
            newPoints[i*4+2] = nextUL;
            newPoints[i*4+3] = nextUR;
            
            // Prepare upper part of next subframe
            nextUL = newPoints[i*4];
            nextUR = newPoints[i*4+1];
        }
        
        // Create last subframe using residual part of whole image frame
        newPoints[(numFrames-1) * 4]   = pts[0];
        newPoints[(numFrames-1) * 4+1] = pts[1];
        newPoints[(numFrames-1) * 4+2] = nextUL;
        newPoints[(numFrames-1) * 4+3] = nextUR;
                
        framePct = frameSizeFactor;
        lastFramePct = 1 - (frameSizeFactor*(numFrames-1));
        
        framePointsFaked=true;
        
        for(int i=0; i<newPoints.length-4; i+=4) {
            newPoints[i+0] = newPoints[i+6] = midpoint(newPoints[i+0], newPoints[i+6]);
            newPoints[i+1] = newPoints[i+7] = midpoint(newPoints[i+1], newPoints[i+7]);
        }    		
        
        return splitFrames(newPoints);
    }
          
    int horizontalSplitCnt=1;
    
    private static final int LINES_PER_FRAME=500;
    
    private Point2D[] splitFrames(Point2D pts[]) {
    	
    	//
    	double maxPPD = getMaxRenderPPD();
    	
    	if (instrument==Instrument.HRSC) {
    		maxPPD = Double.valueOf(myStamp.getProjectionParams().get("map_resolution"));
    	}
    	
    	double linesPerFrame;
    	if (renderPPD > maxPPD) {
        	linesPerFrame = LINES_PER_FRAME;
    	} else {
        	linesPerFrame = LINES_PER_FRAME * (maxPPD/renderPPD);        		
    	}

    	if (linesPerFrame > getNumSamples()) {
    		linesPerFrame=getNumSamples();
    	}    	
    	//
    	
    	horizontalSplitCnt=(int)Math.ceil(getNumSamples()/linesPerFrame);
    	
    	if (horizontalSplitCnt<1) {
    		horizontalSplitCnt=1;
    	}
    	
    	log.println("Horizontal Split Cnt = " + horizontalSplitCnt);
        int numFrames =pts.length/4;
        
        // First set of 4 points is the entire image area
        Point2D[] newPoints = new Point2D[horizontalSplitCnt*numFrames * 4];
        
        double framePercent = 1.0 / horizontalSplitCnt;
        
        int total_samples=getNumSamples();
        double total_lines=getNumLines();
        
        double linesPerNormFrame=(getFrameSize()*framePct);
        double linesPerLastFrame=(getFrameSize()*lastFramePct);
        
        // Create image subframe geometry points through
        // interpolation from whole image.  Frames start
        // from upper part of image.
        for (int i=0; i < numFrames; i++) {        	
            // Convert image frame geometry to vector form.
            HVector ll = new HVector(pts[4*i]);
            HVector lr = new HVector(pts[(4*i)+1]);
            HVector ul = new HVector(pts[(4*i)+2]);
            HVector ur = new HVector(pts[(4*i)+3]);

            Point2D[] topRow = new Point2D[horizontalSplitCnt+1];
            Point2D[] botRow = new Point2D[horizontalSplitCnt+1];
            
            for (int n=0; n<(horizontalSplitCnt+1); n++) {
            	if (instrument==Instrument.HRSC) {
            		if (i==numFrames-1) {
                		topRow[n]=getHRSCPoint(i*linesPerNormFrame, (total_samples*framePercent*n));
                		botRow[n]=getHRSCPoint(i*linesPerNormFrame+linesPerLastFrame, (total_samples*framePercent*n));
            		} else {
                		//rounding error on last point?
                		topRow[n]=getHRSCPoint(i*linesPerNormFrame, (total_samples*framePercent*n));
                		botRow[n]=getHRSCPoint((i+1)*linesPerNormFrame, (total_samples*framePercent*n));
            		}
        			
            	} else {
            		topRow[n]=ul.interpolate(ur,n*framePercent).toLonLat(null);
            		botRow[n]=ll.interpolate(lr,n*framePercent).toLonLat(null);
            	}
            }
            
            int newFrameCnt = topRow.length-1;
            
            for (int j=0; j<newFrameCnt; j++) {
            	int newFrameNum = horizontalSplitCnt*i+j;

            	newPoints[(4*newFrameNum)   ]	= botRow[j];
            	newPoints[(4*newFrameNum) +1]	= botRow[j+1];
            	newPoints[(4*newFrameNum) +2]	= topRow[j];
            	newPoints[(4*newFrameNum) +3]	= topRow[j+1];
            }            
    }

//        for (int i=0; i< newPoints.length; i++) {
//        	System.out.println("Point " + i + ") = " + newPoints[i]);
//          }

        return newPoints;    	
    }    
    
    Point2D[] pts = null;
    
    protected Point2D[] getPoints() {
    	if (pts==null) {
    		try {
    			String urlStr = StampLayer.stampURL+"PointFetcher?id="+productID+"&instrument="+getInstrument()+StampLayer.versionStr;
    			
    			if (imageType!=null && imageType.length()>0) {
    				urlStr+="&imageType="+URLEncoder.encode(imageType);
    			}
    			log.println("Points URL: " + urlStr);
    			URL url = new URL(urlStr);
            
    			ObjectInputStream ois = new ObjectInputStream(url.openStream());
        
    			double dpts[] = (double[])ois.readObject();
    		               		   
    			pts = new Point2D[dpts.length/2]; 
    	        
	    	    for (int i=0; i<pts.length; i++) {
	    	    	pts[i]=new Point2D.Double(dpts[2*i], dpts[2*i+1]);
	    	    }
	    	    
	    	    if (imageType!=null && imageType.length()>0 && pts.length>4) {
		            for(int i=0; i<pts.length-4; i+=4) {
		                pts[i+0] = pts[i+6] = midpoint(pts[i+0], pts[i+6]);
		                pts[i+1] = pts[i+7] = midpoint(pts[i+1], pts[i+7]);
		            }
	    	    }

    		} catch (Exception e) {
    			e.printStackTrace();
    		}    		
    	}
    	    	    	
//    	for (int i=0; i<pts.length; i++) {
//    		System.out.println("Point[]:" + pts[i]);
//    	}
    	
    	return getOrientedPoints(pts);
    }
    
    static Point2D midpoint(Point2D a, Point2D b)
    {
        return  new HVector(a).add(new HVector(b)).toLonLat(null);
    }
    
    protected double getMaxRenderPPD() {
//    	boolean isMocWA=false;
    	
//    	StampShape s = myStamp;
    	
//    	if (instrument==Instrument.MOC) {
//	    	for(int i=0; i<s.stampLayer.columnNames.length; i++) {
//	    		String label = s.stampLayer.columnNames[i];
//	    		if (label.equalsIgnoreCase("instrument_name")) {
//	    			try {
//						String value = (String)myStamp.getStamp().getData()[i];
//						if (value.equalsIgnoreCase("MOC-WA")) {
//							isMocWA=true;
//						}
//	    			} catch (ClassCastException cce) {
//	    				cce.printStackTrace();
//	    			}
//				}
//	    	}
//    	}
//    	    	
//    	double maxPPD=512;
//    	switch(instrument) {
//    	case THEMIS :
//                if (productID.startsWith("V")) {
//                	maxPPD=2048; // divide by spatialSumming;
//                } else {
//                	maxPPD=512;
//                }
//    		break;
//    	case MOC :
//    			if (isMocWA) {
//    				maxPPD=128;  // being generous
//    			} else {
//    				maxPPD=8192;  // divide by downtrack summing
//    			}
//    		break;
//    	case CTX :
//    		maxPPD=8192;
//    		break;
//    	case HIRISE :
//    		maxPPD=8192;
//    		break;
//    	case HRSC :
//    		maxPPD=Double.parseDouble(myStamp.getProjectionParams().get("map_resolution"));
//    		System.out.println("HRSC maxPPD = " + maxPPD);
//
////    		maxPPD=2048;
//    		break;
//    	case VIKING :
//    		maxPPD=256;
//    		break;
//    	case MAP :
//    		maxPPD=128;
//    		break;
//    	case APOLLO :
//    		maxPPD=512;
//    		break;
//    	case CRISM :
//    		maxPPD=2048;
//    		break;
//    	case ASTER :
//    		maxPPD=2048;
//    		break;
//    	}
    	
    	
		Point2D points[] = getPoints();
		
		double degrees = distance(points[0], 
				                      points[1]);
			
		int pixels = getNumSamples();

		double maxPPD = pixels / degrees;

		log.println("MaxPPD is: " + maxPPD);
    	return maxPPD;
    }
    
	public double distance(Point2D pointA, Point2D pointB)
	 {
		double lonA = Math.toRadians(pointA.getX());
		double latA = Math.toRadians(pointA.getY());

		double lonB = Math.toRadians(pointB.getX());
		double latB = Math.toRadians(pointB.getY());

		
		HVector a = new HVector(Math.cos(latA)*Math.cos(-lonA),
				Math.cos(latA)*Math.sin(-lonA),
				Math.sin(latA));
				
		HVector b = new HVector(Math.cos(latB)*Math.cos(-lonB),
				Math.cos(latB)*Math.sin(-lonB),
				Math.sin(latB));
		
		return  Math.toDegrees(a.separation(b));
	 }
    
    /**
     ** Renders the image onto the given (world-coordinate) graphics context.
     **/
    public synchronized void renderImage(final Graphics2D wg2, final BufferedImageOp op,
                            final MultiProjection proj, int renderPPD, StampTask task)
    {
        task.updateStatus(Status.RED);
        final Rectangle2D worldWin = proj.getWorldWindow();
        
        double maxRenderPPD=getMaxRenderPPD();
        
        if (renderPPD>maxRenderPPD) {
        	renderPPD=(int)maxRenderPPD;
        }
        
        if(frames == null || renderPPD != this.renderPPD || 
                projHash != Main.PO.getProjectionSpecialParameters().hashCode()) {
        	recreateImageFrames(renderPPD);
        }

//    	Runnable runme = new Runnable() {    		
//			public void run() {
		        List<ImageFrame> framesInView = new ArrayList<ImageFrame>();
		        for(int i=0; i<frames.length; i++) {                              
		            if (doesFrameIntersect(frames[i], worldWin)) {
		            	framesInView.add(frames[i]);
		            }
		        }
		        
		        if (framePointsFaked) {	        	
		        	ImageFrame frameSegmentsToFetch[][][] = FrameFetcher.segment(frames, horizontalSplitCnt, frames.length/horizontalSplitCnt, worldWin);
		        	
		        	for (int i=0; i<frameSegmentsToFetch.length; i++) {        	
		        		FrameFetcher ff = new FrameFetcher(frameSegmentsToFetch[i]);
		        		ff.fetchFrames();
		        	}
		        }
		        		        
		 //       System.out.println(imageType + " Frames in View = " + framesInView.size());
		
		        task.updateStatus(Status.YELLOW);
		        
		        for(ImageFrame f : framesInView) {     
		        	if (proj.getWorldWindow().equals(worldWin)) {
		        		drawFrame(f, worldWin, wg2, op);
		        	} else {
		        		log.println("Parameters changed, aborting frame draw");
		        	}
		        }
//			}
//    	};
    	
		        task.updateStatus(Status.DONE);
    }
    
    // Draw this frame onto the specified g2.  Draw it multiple times if
    // necessary due to worldwrap.  (How often are we really going to be
    // zoomed out enough to actually worry about this for stamps?)
    private void drawFrame(ImageFrame frame, Rectangle2D worldWin, Graphics2D wg2, BufferedImageOp op) {
        final double base = Math.floor(worldWin.getMinX() / 360.0) * 360.0;
        
        final int numWorldSegments =
            (int) Math.ceil (worldWin.getMaxX() / 360.0) -
            (int) Math.floor(worldWin.getMinX() / 360.0);
        
        Rectangle2D.Double where = new Rectangle2D.Double();
        
        where.setRect(frame.cell.getWorldBounds());
        double origX = where.x;
        
        int start = where.getMaxX() < 360.0 ? 0 : -1;
        
        for(int m=start; m<numWorldSegments; m++) {
            where.x = origX + base + 360.0*m;
            if(worldWin.intersects(where)) {
            	Graphics2D g2 = getFrameG2(wg2);
            	BufferedImage image = frame.getImage();
                g2.transform(Util.image2world(image.getWidth(), image.getHeight(), where));
                g2.drawImage(image, op, 0, 0);
            }
        }                    	
    }
    
    public static boolean doesFrameIntersect(ImageFrame frame, Rectangle2D worldWin) {
        final double base = Math.floor(worldWin.getMinX() / 360.0) * 360.0;
        
        final int numWorldSegments =
            (int) Math.ceil (worldWin.getMaxX() / 360.0) -
            (int) Math.floor(worldWin.getMinX() / 360.0);
        
        Rectangle2D.Double where = new Rectangle2D.Double();
        
        where.setRect(frame.cell.getWorldBounds());
        double origX = where.x;
        
        int start = where.getMaxX() < 360.0 ? 0 : -1;
        
        for(int m=start; m<numWorldSegments; m++) {
            where.x = origX + base + 360.0*m;
            if(worldWin.intersects(where)) {
            	return true;
            }
        }                   
        
        return false;
    }
    
    // currently unused
    Area clipArea=null;
    
    // The area of this stamp generated using the render points
    Area realClipArea = null;
    
    // The realClipArea of this stamp minus the overlapping areas of any
    // other rendered stamps that are higher in the view stack
    // Needed for multithreaded rendering, unused at the moment
    Area currentClipArea = null;
    
    // Not sure this needs to be public
    public Area getRealClipArea() {
    	if (realClipArea==null) {
    		realClipArea=new Area(getNormalPath());
    	}
    	
    	return realClipArea;  	
    }
    
    public void clearCurrentClip() {
    	currentClipArea=null;
    }
    
    public void calculateCurrentClip(List<StampImage> higherImages) {
    	// Short circuited until multithreaded rendering is enabled.
    	if (true) return;
    	if (currentClipArea==null) {
	    	currentClipArea = getAdjustedClipArea(getRealClipArea());
	    	
	    	for (StampImage s : higherImages) { 		
	    		// Somehow we need to worry about +/- 360 issues here too
	    		if (s.getRealClipArea().intersects(realClipArea.getBounds2D())) {
	    			currentClipArea.subtract(s.getRealClipArea());
	    		}    		
	    	}    	
    	}
    }
    
    public Area getCurrentClipArea() {
    	return currentClipArea;
    }
    
    
    public Area getAdjustedClipArea(Area startingArea) {
		Area newArea = new Area(startingArea);
		
    	// Handle the cases where wrapped coordinates get us into +360 or -360
		// space.
        AffineTransform transformer2 = new AffineTransform();
        transformer2.translate(360, 0);
    	Area area2 = ((Area) newArea.clone());
    	area2.transform(transformer2);
    	
        AffineTransform transformer3 = new AffineTransform();
        transformer3.translate(-360, 0);
    	Area area3 = ((Area) newArea.clone());
    	area3.transform(transformer3);

        AffineTransform transformer4 = new AffineTransform();
        transformer4.translate(720, 0);
    	Area area4 = ((Area) newArea.clone());
    	area4.transform(transformer4);
    	
        AffineTransform transformer5 = new AffineTransform();
        transformer5.translate(-720, 0);
    	Area area5 = ((Area) newArea.clone());
    	area5.transform(transformer5);

        AffineTransform transformer6 = new AffineTransform();
        transformer6.translate(1080, 0);
    	Area area6 = ((Area) newArea.clone());
    	area6.transform(transformer6);
    	
        AffineTransform transformer7 = new AffineTransform();
        transformer7.translate(-1080, 0);
    	Area area7 = ((Area) newArea.clone());
    	area7.transform(transformer7);
	    	
    	Area returnArea = new Area(newArea);
    	returnArea.add(area2);
    	returnArea.add(area3);
    	returnArea.add(area4);
    	returnArea.add(area5);
    	returnArea.add(area6);
    	returnArea.add(area7);    		
    	
    	return returnArea;
    }
    
    /**
	 * Creates a copy of the given Graphics2D and prepares it for rendering
	 * frames of this image type.
	 * 
	 * TODO: Some images may not want all black to be made transparent.  Need a flag
	 * to indicate this somehow.
	 */    
    protected final java.awt.Graphics2D getFrameG2(Graphics2D g2) {
    	g2 = (Graphics2D) g2.create();
    	if (instrument==Instrument.THEMIS && imageType.startsWith("D")) {
    		g2.setComposite(new IgnoreComposite(Color.black));
    	} else if (instrument==Instrument.HRSC || instrument==Instrument.CRISM) {
    		g2.setClip(myStamp.getNormalPath());
    	}
    	
    	// Something like this is needed for multi-threaded rendering support.
    	// g2.setClip(getCurrentClipArea());
    	
    	return g2;
    }
    /**
     ** Not quite sure what this does: see implementation in
     ** PdsImage. -- Joel Hoff
     **/
    public int[] getHistogram() throws IOException
    {
        return null;
    }
                       
    public int getHeight()
    {
    	if (image!=null) {
            return image.getHeight();    		
    	} else {
    		return (int)getNumLines();
    	}
    }
    
    public int getWidth()
    {
    	if (image!=null) {
    		return image.getWidth();
    	} else {
    		return getNumSamples();
    	}
    }
    
    // This block moved over from StampShape - it's tied to imageType,
    // not generic by Shape...
    private long numLines=Long.MIN_VALUE;
    private int numSamplesPerLine=Integer.MIN_VALUE;
    
    public long getNumLines() {
    	if (numLines==Long.MIN_VALUE) {
    		getFullImageSize();
    	}
    	return numLines;
    }
    
    public int getNumSamples() {
    	if (numSamplesPerLine==Integer.MIN_VALUE) {
    		getFullImageSize();
    	}
    	return numSamplesPerLine;
    }
    
    private void getFullImageSize() {
		try {
			String sizeLookupStr = StampLayer.stampURL+"ImageSizeLookup?id="+productID+"&instrument="+getInstrument()+"&imageType="+URLEncoder.encode(imageType)+"&format=JAVA"+StampLayer.getAuthString();
					
			ObjectInputStream ois = new ObjectInputStream(new URL(sizeLookupStr).openStream());
			
			Integer samples = (Integer)ois.readObject();
			Long lines = (Long)ois.readObject();
			numLines = lines.longValue();
			numSamplesPerLine = samples.intValue();
		} catch (Exception e) {
			numLines=0;
			numSamplesPerLine=0;
			e.printStackTrace();
		}
    }
    
    
    // Returns 32-bit RGB color value at specified pixel location; may
    // include alpha component.
    synchronized public int getRGB(int x, int y) throws Exception 
    {
        return image.getRGB(x, y);
    }
    
    protected String getCachedImageFrameName(int frame)
    {
        if (getFilename() != null &&
                StampImage.STAMP_CACHE != null)
            return StampImage.STAMP_CACHE + getFilename() + "_" + projHash + "_" + frame + 
            "_" + imageType + "_" + renderPPD + getImageFrameType(userRotateFlip) + ".png";
        else
            return null;
    }
    
    protected static void savePngImage(String fname, BufferedImage image)
    {
        if (fname != null &&
                image != null) {
            try {
                File outputFile = new File(fname);
                
                if (outputFile != null) {
                    if (outputFile.exists() &&
                            outputFile.isDirectory()) {
                        log.aprintln("Could not store image: " + fname + " is a directory");                        
                    }
                    
                    ImageIO.write(image, "PNG", outputFile);
                }
            }
            catch (Throwable e) {
                log.aprintln(e);
            }
        }
        
    } 
            
    /**
     * Returns the default height of the image frames.
     */ 
    protected int getFrameSize()
    {     
        if (instrument==Instrument.THEMIS && productID.startsWith("I")) {
        	return 256;
        }

    	return getHeight(); 
    }

    /**
     * Returns the exact height of the image frame for the specified
     * frame of the specified image.  Subclasses may need to override;
     * the default implementation calls {@link #getFrameSize()}.
     * 
     * @param frame frame number, starting from 0.
     * 
     * @return Returns the exact height of the specified image frame;
     * returns 0 if there is an error.
     */ 

    protected int getFrameSize(int frame)
    {
    	if (!framePointsFaked) {
	        int size = getFrameSize();
	        int realSize = size;
	        
	        if (frames.length > 0 &&
	            frame == frames.length - 1 &&
	            size > 0)
	        {
	            int remainder = getHeight() % size;
	            if (remainder > 0)
	                realSize = remainder;
	        }
	        
	        return realSize;
    	} else {    		
        	if (frame!=0 && frame>=frames.length-horizontalSplitCnt) {
    			return (int)(getFrameSize()*lastFramePct);
    		} else {
    			return (int)(getFrameSize()*framePct);
    		}
    	}
    }
                        
    protected synchronized void recreateImageFrames(int renderPPD)
    {
        projHash = Main.PO.getProjectionSpecialParameters().hashCode();
        this.renderPPD = renderPPD;

        Point2D[] pts = getPoints();
                
        double maxPPD = getMaxRenderPPD();

    	double linesPerFrame;
    	if (renderPPD > maxPPD) {
        	linesPerFrame = LINES_PER_FRAME;
    	} else {
        	linesPerFrame = LINES_PER_FRAME * (maxPPD/renderPPD);        		
    	}
    	
    	double linesAtThisPPD=((renderPPD/maxPPD)*numLines);
    	
    	if (instrument == Instrument.THEMIS ||
    			linesAtThisPPD < LINES_PER_FRAME) {
    		
    		framePointsFaked=false;
    		horizontalSplitCnt=1;
    		
    	} else {        	        	
        	if (linesPerFrame > getNumLines()) {
        		linesPerFrame=getNumLines();
        	}
        	        	
        	linesPerFrame = Math.ceil(linesPerFrame);
        	
    		pts = getFakeFramePoints(pts, (linesPerFrame/getNumLines()));
    	} 
        
        int frameCount = pts.length / 4;
        frames = new ImageFrame[frameCount];
                
    	for (int i=0; i<frameCount; i++) {	    		        
	        int thisFrameSize = getFrameSize(i);
	        
	        Rectangle srcRange;
	        
	        int frameWidth;
	        if (frameCount==1) {
	        	frameWidth = getWidth();
	        } else {
	        	frameWidth=getWidth()/horizontalSplitCnt;
	        }
	        int startx;
	        if (i%horizontalSplitCnt==0) {
	        	startx=0;
	        } else {
	        	startx=(i%horizontalSplitCnt)*getWidth()/horizontalSplitCnt;
	        }

	        if (i==frameCount-1) {
	        	srcRange = new Rectangle(startx, i/horizontalSplitCnt * getFrameSize(i-1),
                    frameWidth, thisFrameSize);	        	
	        } else {
	        	srcRange = new Rectangle(startx, i/horizontalSplitCnt * getFrameSize(i),
	                    frameWidth, thisFrameSize);
	        }
	        
//        	for (int x=0; x<pts.length; x++) {
//        		System.out.println("Cell points: " + pts[x]);
//        	}
        	
	        Cell frameCell = new Cell(
	                 new HVector(pts[i*4]),
	                 new HVector(pts[i*4+1]),
	                 new HVector(pts[i*4+3]),
	                 new HVector(pts[i*4+2]), (Projection_OC)Main.PO);
	                
	        frames[i] = new ImageFrame(this, frameCell, srcRange, i);	                             
    	}
    }
    
    public boolean isIntersected(HVector ptVect)
    {
        Point2D pt = null;
        
        pt = getImagePt(ptVect);
        
        log.println("returned image pt is " + pt);
        if ( pt != null)
            return true;
        else
            return false;
    }
    
    /**
     * Returns image point corresponding to point specified in
     * HVector coordinate space based on cell coordinate data
     * stored in frameCells array (generated by createImageFrame()
     * using getPoints() ).
     *
     * If point does not lie within the image, null is returned.
     */ 
    public Point2D getImagePt(HVector ptVect)
    {
        Point2D.Double pt = null;
        
        if (frames==null) {
        	recreateImageFrames(myStamp.stampLayer.viewToUpdate.viewman2.getMagnification());
        }
        
        if (ptVect != null)
        {
            for (int i = 0; i < frames.length; i++)
            {
                Point2D.Double unitPt = new Point2D.Double();
                
                frames[i].cell.uninterpolate(ptVect, unitPt);
                
                // Check whether point falls within cell.
                if (unitPt.x >= 0  &&  unitPt.x <= 1  &&
                    unitPt.y >= 0  &&  unitPt.y <= 1  )
                {
                    pt = new Point2D.Double();
                    pt.x = unitPt.x * getWidth();
                    
                    if (i == frames.length-1)
                        pt.y = (1 - unitPt.y) * getFrameSize(i) + i * getFrameSize(0);
                    else
                        pt.y = ((1 - unitPt.y) + i) * getFrameSize(i);
                    
                    log.println("found point, frame #" + i);
                    log.println("image coords: x=" + pt.x + " y=" + pt.y);
                    log.println("unit coords: x=" + unitPt.x + " y=" + unitPt.y);
                    break;
                }
            }
        }
        else
            log.println("ptVect parameter passed in as null");
        
        if (pt == null)
            log.println("returning null image point");
        else
            log.println("returning image point: x=" + pt.x + " y=" + pt.y);
        
        return pt;
    }


    double line_proj_offset = 0;
    double map_resolution = 0;
    double sample_proj_offset = 0;
    double center_lon = 0;

    HashMap<String,String> projParams=null;
    
    public Point2D getHRSCPoint(double line, double sample) {
    	if (projParams==null) {
    		projParams=myStamp.getProjectionParams();

		   line_proj_offset = Double.parseDouble(projParams.get("line_projection_offset"));
		   map_resolution = Double.parseDouble(projParams.get("map_resolution"));
		   sample_proj_offset = Double.parseDouble(projParams.get("sample_projection_offset"));
		   center_lon = Double.parseDouble(projParams.get("center_longitude"));
    	}
    	
		double lat =  (line_proj_offset - line ) / map_resolution;
		double lon = 360 - ((sample - sample_proj_offset) / 
		             ( map_resolution *  Math.cos(Math.toRadians(lat))) + center_lon) ;
    		        	
    	return new Point2D.Double(lon,lat);	
    }

    /**
     ** Returns a (cached) normalized version of the stamp's path.
     ** @see Util#normalize360
     **/
    private Shape normalPath;
    
    public synchronized Shape getNormalPath()
    {
        if(normalPath == null)
            normalPath = StampShape.normalize360(getPath());
        return  normalPath;
    }
    
    GeneralPath path;

	// This is the path of the rendered shape - note that this may go outside the
	// bounds of the stamp polygon, and may include more area than we want to 
	// make visible to the user.
	public synchronized GeneralPath getPath()
	{
	    if(path == null)
	    {
	        path = new GeneralPath();
	        Point2D pt;
	                    
	        Point2D pts[] = getPoints();
	
	        // trace a line down the left edge
	        for (int i=2; i<pts.length; i=i+4) {
	            pt = Main.PO.convSpatialToWorld(pts[i].getX(),
	                    pts[i].getY());
	
	            if (i==2) {
	            	path.moveTo((float)pt.getX(),
	                    (float)pt.getY());
	            } else {
	            	path.lineTo((float)pt.getX(),
	                        (float)pt.getY());                	
	            }
	        }
	
	        pt = pts[pts.length-4];
	        pt = Main.PO.convSpatialToWorld(pt.getX(),
	                pt.getY());
	        
	    	path.lineTo((float)pt.getX(),
	                (float)pt.getY());                	
	
	        // and then back up the right edge
	        for (int i=pts.length-3; i>0; i=i-4) {
	            pt = Main.PO.convSpatialToWorld(pts[i].getX(),
	                    pts[i].getY());
	
	           	path.lineTo((float)pt.getX(), (float)pt.getY());                	
	        }            
	
	        pt = pts[3];            
	        pt = Main.PO.convSpatialToWorld(pt.getX(),
	                pt.getY());
	        path.lineTo((float)pt.getX(), (float)pt.getY());
	
	        path.closePath();
	    } 
	    return  path;
	}
    
    
}


enum Instrument {
	THEMIS,
	MOC,
	VIKING,
	CTX,
	HIRISE,
	HRSC,
	MOSAIC,
	MAP,
	CRISM,
	ASTER,
	APOLLO;
}

