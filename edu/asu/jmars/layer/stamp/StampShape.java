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

import java.awt.Dimension;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import edu.asu.jmars.Main;
import edu.asu.jmars.util.HVector;
import edu.asu.msff.StampInterface;

public class StampShape {

	private StampInterface myStamp;
	StampLayer stampLayer;
	
	public StampShape(StampInterface stamp, StampLayer stampLayer) {
		myStamp=stamp;
		this.stampLayer = stampLayer;
	}
	
	public String getId() {
		return myStamp.getId();
	}
	
	public StampInterface getStamp() {
		return myStamp;
	}
	
	public String toString() {
		return myStamp.getId();
	}
	
	/**
	 * Calculates and returns the center point (in lon/lat) for this stamp by
	 * averaging the 4 corner points
	 */
    Point2D centerPoint=null;
    
    public Point2D getCenter() {
    	if (centerPoint==null) {
    		
    		HVector corner = new HVector();
    		
    		for (int i=0; i<myStamp.getPoints().length; i=i+2) {
    			corner=corner.add(new HVector(new Point2D.Double(myStamp.getPoints()[i], myStamp.getPoints()[i+1])));
    		}
    		corner.div(myStamp.getPoints().length/2);
    		
    		centerPoint = corner.toLonLat(null);    		
    	}
    	
    	return centerPoint;
    }

	/**
	 * Returns the NW stamp corner spatial coordinates as
	 * a point in degrees: x = longitude, y = latitude.
	 */
    public Point2D getNW()
    {
    	if (nw==null) {
    		double pts[] = myStamp.getPoints();
    		nw=new Point2D.Double(pts[0], pts[1]);
    	}
    	return nw;
    }
        
    /**
     * Returns the SW stamp corner spatial coordinates as
     * a point in degrees: x = longitude, y = latitude.
     */
    
    private transient Point2D nw;
    private transient Point2D sw;
    
    public Point2D getSW()
    {
    	if (sw==null) {
    		double pts[] = myStamp.getPoints();
    		sw=new Point2D.Double(pts[pts.length-2], pts[pts.length-1]);
    	}
    	return sw;
    }
    
    public String getPopupInfo(boolean showAsHTML) {
        
        String info = "";
        
        if ( showAsHTML ) {
            info += "<html>";
        }
        
        info += "ID: ";
        info += getId();
        
        if ( showAsHTML ) {
            info += "</html>";
        }
        
        return info;
    }

    private List<String> supportedTypes=null;
    
    public List<String> getSupportedTypes() {
		if (supportedTypes==null) {		
			try {
				String typeLookupStr = StampLayer.stampURL+"ImageTypeLookup?id="+getId()+"&instrument="+stampLayer.getInstrument()+"&format=JAVA"+stampLayer.getAuthString()+StampLayer.versionStr;
						
				ObjectInputStream ois = new ObjectInputStream(new URL(typeLookupStr).openStream());
				
				supportedTypes = (List<String>)ois.readObject();				
			} catch (Exception e) {
				supportedTypes=new ArrayList<String>();
				e.printStackTrace();
			}
		}
		return supportedTypes;
    }
    
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
			String sizeLookupStr = StampLayer.stampURL+"ImageSizeLookup?id="+getId()+"&instrument="+stampLayer.getInstrument()+"&format=JAVA"+stampLayer.getAuthString()+StampLayer.versionStr;
					
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
    
    private HashMap<String,String> projectionParams=null;
    
    public HashMap<String, String> getProjectionParams() {
    	if (projectionParams!=null) return projectionParams;
    	
		try {
			String paramLookupStr = StampLayer.stampURL+"ProjectionParamLookup?id="+getId()+"&instrument="+stampLayer.getInstrument()+"&format=JAVA"+stampLayer.getAuthString()+StampLayer.versionStr;
					
			ObjectInputStream ois = new ObjectInputStream(new URL(paramLookupStr).openStream());
			
			projectionParams = (HashMap<String,String>)ois.readObject();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return projectionParams;
    }
    
    public final synchronized void clearProjectedData()
    {
        path = null;
        normalPath = null;
        bounds = null;
    }
    
    private Rectangle2D bounds;
    
    public synchronized Rectangle2D getBounds2D()
    {
        if(bounds != null)
            return  bounds;
        
        bounds = getPath().getBounds2D();
        double w = bounds.getWidth();
        if(w <= 180)
            return  bounds;
        
        double x = bounds.getX();
        double y = bounds.getY();
        double h = bounds.getHeight();
        
        x += w;
        w = 360 - w;
        
        bounds.setFrame(x, y, w, h);
        return  bounds;
    }

    GeneralPath path;

    /**
     ** Returns a (cached) normalized version of the stamp's path.
     ** @see Util#normalize360
     **/
    private Shape normalPath;
    
    public synchronized Shape getNormalPath()
    {
        if(normalPath == null)
            normalPath = normalize360(getPath());
        return  normalPath;
    }
    
    public synchronized GeneralPath getPath()
    {
        if(path == null)
        {
            path = new GeneralPath();
            Point2D pt;
                        
            double pts[] = myStamp.getPoints();
            
            for (int i=0; i<pts.length; i=i+2) {
                pt = Main.PO.convSpatialToWorld(pts[i],
                        pts[i+1]);

                if (i==0) {
                	path.moveTo((float)pt.getX(),
                        (float)pt.getY());
                } else {
                	path.lineTo((float)pt.getX(),
                            (float)pt.getY());                	
                }
            }
            
            path.closePath();
        } 
        return  path;
    }


/**
 ** Given a shape, iterates over it and performs the given
 ** coordinate modification to every point in the shape.
 **/
private static Shape modify(Shape s, CoordModifier cm)
 {
GeneralPath gp = new GeneralPath();
PathIterator iter = s.getPathIterator(null);
float[] coords = new float[6];

// NOTE: No loss of precision in coords. All of the
// GeneralPath.foobarTo() methods take FLOATS and not doubles.

while(!iter.isDone())
 {
    switch(iter.currentSegment(coords))
     {

     case PathIterator.SEG_CLOSE:
	gp.closePath();
	break;

     case PathIterator.SEG_LINETO:
	cm.modify(coords, 2);
	gp.lineTo(coords[0], coords[1]);
	break;

     case PathIterator.SEG_MOVETO:
	cm.modify(coords, 2);
	gp.moveTo(coords[0], coords[1]);
	break;

     case PathIterator.SEG_QUADTO:
	cm.modify(coords, 4);
	gp.quadTo(coords[0], coords[1],
		  coords[2], coords[3]);
	break;

     case PathIterator.SEG_CUBICTO:
	cm.modify(coords, 6);
	gp.curveTo(coords[0], coords[1],
		   coords[2], coords[3],
		   coords[4], coords[5]);
	break;

     default:
	//log.aprintln("INVALID GENERALPATH SEGMENT TYPE!");

     }
    iter.next();
 }
return	gp;
 }

/**
 ** ONLY FOR CYLINDRICAL: Given a shape in world coordinates,
 ** "normalizes" it. This ensures that its left-most x coordinate
 ** is within the x-range [0:360], and that there is no
 ** wrap-around (that is, the shape simply pushes past 360).
 **/
public static Shape normalize360(Shape s)
 {
double x = s.getBounds2D().getMinX();
if(x < 0  ||  x >= mod)
    s = modify(s, cmModulo);

if(s.getBounds2D().getWidth() >= mod/2)
    s = modify(s, cmWrapping);

return	s;
 }


// Quick hack to allow verbatim code-reuse from GraphicsWrapped.java
private static final double mod = 360;

/**
 ** Performs the modulo operation on a shape's coordinates.
 **/
private static final CoordModifier cmModulo =
new CoordModifier()
 {
    public void modify(float[] coords, int count)
     {
	for(int i=0; i<count; i+=2)
	    coords[i] -= Math.floor(coords[i]/mod)*mod;
     }
 };

    /**
     ** Takes care of wrap-around on a shape's coordinates.
     **/
    private static final CoordModifier cmWrapping =
	new CoordModifier()
	 {
	    public void modify(float[] coords, int count)
	     {
		for(int i=0; i<count; i+=2)
		    if(coords[i] < mod/2)
			coords[i] += mod;
	     }
	 };
	 
	    /**
	     ** Interface for modifying a single coordinate of a {@link
	     ** PathIterator}.
	     **/
	    private static interface CoordModifier
	     {
		/**
		 ** @param coords The coordinate array returned by a shape's
		 ** {@link PathIterator}.
		 ** @param count The number of coordinates in the array (as
		 ** determined by the point type of the {@link PathIterator}.
		 **/
		public void modify(float[] coords, int count);
	     }


}
