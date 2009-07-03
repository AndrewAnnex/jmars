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

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import edu.asu.jmars.Main;

public class FrameFetcher {

	int xoffset;
	long yoffset;
	
	int frameWidth;
	long frameHeight;
	
	int numXFrames;
	int numYFrames;
	
	ImageFrame frames[][];
	
	StampShape myStamp;
	StampImage srcImage;
	
	public FrameFetcher(ImageFrame framesToFill[][]) {
		ImageFrame firstFrame = framesToFill[0][0];
		
		this.xoffset=firstFrame.startx;
		this.yoffset=firstFrame.starty;

		srcImage = firstFrame.srcImage;
		myStamp = srcImage.myStamp;
		
		frames=framesToFill;

    	long totalLines = myStamp.getNumLines();

    	long linesPerFrame;
    	if (srcImage.frames.length==1) {
    		linesPerFrame = totalLines;
    	} else {
        	linesPerFrame = (long)Math.ceil(totalLines*srcImage.framePct);
    	}
    	
		frameHeight=linesPerFrame;
		
		this.numXFrames=framesToFill.length;
		this.numYFrames=framesToFill[0].length;
		
		frameWidth=myStamp.getNumSamples()/srcImage.horizontalSplitCnt;

	}
	
	public void fetchFrames() {
		int scale =Main.testDriver.mainWindow.getMagnification(); 

		String instrument = srcImage.getInstrument().toString();
		String type = srcImage.imageType;

    	long totalLines = myStamp.getNumLines();

    	String urlStr=StampLayer.stampURL+"ImageServer?instrument="+instrument+"&id="+myStamp.getId()+StampLayer.versionStr;
    	
    	if (type!=null && type.length()>=0) {
    		urlStr+="&imageType="+type;
    	}
    	
    	urlStr+="&zoom="+scale;
    	urlStr+="&startx="+xoffset;
    	urlStr+="&starty="+yoffset;
    	
    	int lastFrameNum = frames[frames.length-1][frames[0].length-1].frameNum;
    	
    	boolean lastRowInvolved=false;
    	
    	// Only used to determine the height of the last row of Frames;
    	double lastFramePct = (totalLines * srcImage.lastFramePct)/frameHeight;
    	
       	if (lastFrameNum !=0 && lastFrameNum>=srcImage.frames.length-srcImage.horizontalSplitCnt) {
       		urlStr+="&height="+Math.round((frameHeight*(numYFrames-1))+ ((totalLines * srcImage.lastFramePct)));
       		lastRowInvolved=true;
       	} else {
        	urlStr+="&height="+frameHeight*numYFrames;
        	lastRowInvolved=false;   		
       	}
    	
    	urlStr+="&width="+frameWidth*numXFrames;
    	
    	try {
    		BufferedImage bigImage = StampImageFactory.loadImage(new URL(urlStr));
    		
    	    double subWidth = bigImage.getWidth() / numXFrames;
    		
    	    double subHeight;
    	    
    	    double lastHeight=0;
    	    
    	    if (!lastRowInvolved) {
    	    	subHeight = bigImage.getHeight() / numYFrames;
    	    } else {
    	    	subHeight = bigImage.getHeight() / (numYFrames-1 + lastFramePct);
    	    	lastHeight = bigImage.getHeight() - ((numYFrames-1)*subHeight);
    	    }
    	    
    		for (int y=0; y<numYFrames; y++) {
    			for (int x=0; x<numXFrames; x++) {
    				BufferedImage subImage;
    				if (lastRowInvolved && y==numYFrames-1) {
    					subImage = bigImage.getSubimage((int)(x*subWidth), (int)(bigImage.getHeight()-lastHeight), (int)subWidth, (int)lastHeight);
    				} else {
    					subImage = bigImage.getSubimage((int)(x*subWidth), (int)(y*subHeight), (int)subWidth, (int)subHeight);
    				}
    				
    				saveSrcImage(frames[x][y].getUrlStr(), subImage);
    			}
    		}
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
	}
	
	
    protected static void saveSrcImage(String fname, BufferedImage bi) {
        try {
        	FileOutputStream fos = new FileOutputStream(StampImage.STAMP_CACHE+"src/"+StampImageFactory.getStrippedFilename(fname));
        	ImageIO.write(bi, "jpg", fos);
        	fos.close();
        }
        catch (Exception e) {
        	e.printStackTrace();
        }
                
    }

    public static boolean frameNeedsLoading(ImageFrame f, Rectangle2D worldWin, boolean done) {
    	if (done) return false;
    	if (f.hasImageLocally()) return false;
    	return StampImage.doesFrameIntersect(f, worldWin);
    }
    
    public static ImageFrame[][][] segment(ImageFrame frames[], int xcnt, int ycnt, Rectangle2D worldWin) {
    	ImageFrame newSegments[][][]=null;
    	
    	List<ImageFrame[][]> segmentList = new ArrayList<ImageFrame[][]>();
    	
    	ImageFrame allFrames[][] = new ImageFrame[xcnt][ycnt];

    	int cnt=0;

    	// Create a 2 dimensional array to make it easier to work with
    	for (int y=0; y<ycnt; y++) {
    		for (int x=0; x<xcnt; x++) {
    			allFrames[x][y]=frames[cnt++];
    		}
    	}

    	boolean done[][]=new boolean[xcnt][ycnt];
    	
    	for (int y=0; y<ycnt; y++) {
    		for (int x=0; x<xcnt; x++) {
    			done[x][y]=false;
    		}
    	}
    	
    	for (int y=0; y<ycnt; y++) {
    		for (int x=0; x<xcnt; x++) {
    			ImageFrame f = allFrames[x][y];
    			
    			if (!frameNeedsLoading(f, worldWin, done[x][y])) {
    				done[x][y]=true; // make sure if it's local it's marked as done
    				continue;
    			}
    			
    			int xadditional=0;
    			int yadditional=0;
    			
    			for (int x2=x+1; x2<xcnt; x2++) {
    				ImageFrame f2 = allFrames[x2][y];
    				if (frameNeedsLoading(f2, worldWin, done[x][y])) {
    					xadditional++;
    				} else {
    					break;
    				}
    			}
    			
    			boolean stillMatching=true;
    			for (int y2=y+1; y2<ycnt; y2++) {
    				if (!stillMatching) {
    					break;
    				}
    				
        			for (int x2=x; x2<xcnt; x2++) {
        				ImageFrame f2 = allFrames[x2][y2];
        				if (!(frameNeedsLoading(f2, worldWin, done[x][y]))) {
        					stillMatching=false;
        					break;
        				} 
        			}
        			if (stillMatching) yadditional++;
    			}
    			
    			ImageFrame newSegment[][]=new ImageFrame[1+xadditional][1+yadditional];
    			
    			for (int y2=0; y2<(1+yadditional); y2++) {
    				for (int x2=0; x2<(1+xadditional); x2++) {
    					newSegment[x2][y2]=allFrames[x+x2][y+y2];
    					done[x+x2][y+y2]=true;
    				}
    			}
    		
    			segmentList.add(newSegment);    		
    		}
    	}
    	
//    	System.out.println("Found a total of " + segmentList.size() + " segments.");
    	newSegments = new ImageFrame[segmentList.size()][][];
    	
    	newSegments = segmentList.toArray(newSegments);    	
    	
    	return newSegments;
    }
    
    
}
