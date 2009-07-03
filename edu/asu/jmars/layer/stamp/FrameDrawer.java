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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.asu.jmars.graphics.GraphicsWrapped;
import edu.asu.jmars.layer.stamp.StampLayer.StampTask;
import edu.asu.jmars.layer.stamp.StampLayer.Status;
import edu.asu.jmars.util.Util;

public class FrameDrawer implements Runnable {

    /**
	 * Creates a copy of the given Graphics2D and prepares it for rendering
	 * frames of this image type.
	 * 
	 * TODO: Some images may not want all black to be made transparent.  Need a flag
	 * to indicate this somehow.
	 */    
    protected final java.awt.Graphics2D getFrameG2(Instrument instrument, Graphics2D g2) {
    	g2 = (Graphics2D) g2.create();
    	if (instrument!=Instrument.CTX) {
   // 		g2.setComposite(new IgnoreComposite(Color.black));
    	}
    	
    	return g2;
    }
    
    // Draw this frame onto the specified g2.  Draw it multiple times if
    // necessary due to worldwrap.  (How often are we really going to be
    // zoomed out enough to actually worry about this for stamps?)
    public void drawFrame() {
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
            	Graphics2D g2 = getFrameG2(frame.srcImage.instrument, wg2);
            	
            	// DETERMINE WHICH IMAGES / DATASETS SHOULD BE CLIPPED
 //           	g2.setClip(new Area(frame.srcImage.myStamp.getNormalPath()));
// Commented out to allow code to compile - this class isn't used currently
// due to the rollback.
//            	Area area = frame.srcImage.getClipArea();

//            	((GraphicsWrapped)(g2)).setClip(area);
             	BufferedImage image = frame.getImage();
                g2.transform(Util.image2world(image.getWidth(), image.getHeight(), where));
                g2.drawImage(image, op, 0, 0);
            }
        }                    	
        frame.srcImage.myStamp.stampLayer.viewToUpdate.repaint();
        //task.frameRendered(frame);
    }
    
    ImageFrame frame;
    Rectangle2D worldWin;
    
    Graphics2D wg2;
    BufferedImageOp op;
    
    StampLayer stampLayer;
    
    FrameDrawer(StampLayer layer, ImageFrame f, Rectangle2D ww, Graphics2D g, BufferedImageOp o) {
    	frame=f;
    	worldWin=ww;
    	wg2=g;
    	op=o;
    	stampLayer = layer;
    }
    
	static ExecutorService pool;

	public void queueProcessing() {
		synchronized (this) {
			if (pool == null) {
				int procs = Math.max(1, Runtime.getRuntime().availableProcessors());
				pool = Executors.newFixedThreadPool(procs, new StampThreadFactory("Stamp FrameDrawer"));
			}
			
			pool.execute(this);
		}
	}

	
	public void run() {
		StampTask task = stampLayer.startTask();
		task.updateStatus(Status.YELLOW);
		drawFrame();
		task.updateStatus(Status.DONE);
	}

}
