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
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import edu.asu.jmars.Main;
import edu.asu.jmars.ProjObj;
import edu.asu.jmars.layer.DataReceiver;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;
import edu.asu.jmars.util.stable.FilteringColumnModel;
import edu.asu.msff.ResponseInfo;
import edu.asu.msff.StampInterface;

public class StampLayer extends Layer
{
    private static DebugLog log = DebugLog.instance();

    // TODO: These should be at the JMARS level, not the StampLayer level
	public static final String CFG_BROWSER_CMD_KEY = "stamp_browser";
	public static final String URL_TAG = "%URL%";
    
    private static final String version = "1.2";
    public static final String versionStr="&version="+version+"&jmars_timestamp="+Main.ABOUT().SECS;
    
    /** @GuardedBy this */
    private StampShape[] cachedStamps=new StampShape[0];
    /** @GuardedBy this */
    private Map<String,StampShape> stampMap = new HashMap<String,StampShape>();

    private ArrayList<StampShape> selectedStamps=new ArrayList<StampShape>();
    
    protected String queryStr;
    protected StampFactory stampFactory;
    
    /** @GuardedBy this */
    protected ProjObj originalPO;
    
    public static final String stampURL = Config.get("stamps.url");
    
    public void dispose() {
    	stampMap.clear();
    	selectedStamps.clear();
    	cachedStamps=null;    	
    }
    
    private StampLayerSettings settings;
    
    public StampLayerSettings getSettings() {
    	if (settings==null) {
    		settings=new StampLayerSettings();
    	}
    	return settings;
    }
    
    public void setSettings(StampLayerSettings newSettings) {
    	settings=newSettings;
    	setQuery(newSettings.queryStr);
    }
    
    public StampLView viewToUpdate;
    
    private ArrayList<StampSelectionListener> selectionListeners = new ArrayList<StampSelectionListener>();
    
    public StampLayer(StampLayerSettings newSettings) {    	
    	settings = newSettings;
    }
    
    public void addSelectedStamp(StampShape newlySelectedStamp) {
    	if (!selectedStamps.contains(newlySelectedStamp)) {
	    	selectedStamps.add(newlySelectedStamp);
	    	ArrayList<StampShape> newStamps = new ArrayList<StampShape>();
	    	newStamps.add(newlySelectedStamp);
	    	notifySelectionListeners(newStamps);
    	}
    }

    public void addSelectedStamps(List<StampShape> newlySelectedStamps) {
    	for (StampShape newStamp : newlySelectedStamps) {
    		if (!selectedStamps.contains(newStamp)) {
    			selectedStamps.add(newStamp);
    		}
    	}
    	notifySelectionListeners();
    }
    
    public void removeSelectedStamp(StampShape unselectedStamp) {
    	selectedStamps.remove(unselectedStamp);
    	notifySelectionListeners();
    }
    
    public void clearSelectedStamps() {
    	selectedStamps.clear();
    	notifySelectionListeners();
    }
    
    public void toggleSelectedStamp(StampShape toggledStamp) {
    	if (selectedStamps.contains(toggledStamp)) {
    		selectedStamps.remove(toggledStamp);
    		notifySelectionListeners();
    	} else {
    		addSelectedStamp(toggledStamp);
    	}
    }

    // Does it make sense to toggle large numbers of stamps?
    public void toggleSelectedStamps(List<StampShape> toggledStamps) {
    	for (StampShape toggledStamp : toggledStamps) {
	    	if (selectedStamps.contains(toggledStamp)) {
	    		selectedStamps.remove(toggledStamp);
	    	} else {
	    		selectedStamps.add(toggledStamp);
	    	}
    	}
    	notifySelectionListeners();
    }

    /** Returns a copy of the selected stamps */
    public List<StampShape> getSelectedStamps() {
    	return new ArrayList<StampShape>(selectedStamps);
    }
    
    public boolean isSelected(StampShape stamp) {
    	return selectedStamps.contains(stamp);
    }
    
    public void addSelectionListener(StampSelectionListener newListener) {
    	selectionListeners.add(newListener);
    }

    private void notifySelectionListeners(List<StampShape> newStamps) {
    	for (StampSelectionListener listener : selectionListeners) {
    		listener.selectionsAdded(newStamps);
    	}
    }

    private void notifySelectionListeners() {
    	int cnt=0;
    	for (StampSelectionListener listener : selectionListeners) {
    		listener.selectionsChanged();
    		cnt++;
    	}
    }
    
    public void setQuery(String newQuery) {
    	this.queryStr = newQuery;
    	loadStampData();
    }
    
    public void setViewToUpdate(StampLView newView) {
    	viewToUpdate=newView;
    }
    
    
    public synchronized StampShape[] getStamps()
    {
   		if (originalPO != Main.PO)
   			reprojectStampData();
    	
    	return  cachedStamps;
    }
    
    public synchronized StampShape getStamp(String stampID)
    {
        if (stampID == null)
            return null;
        else
            return stampMap.get(stampID.trim());
    }
            
    public void receiveRequest(Object layerRequest,
                               DataReceiver requester)
    {
//        if (layerRequest == null)
//        {
//            log.aprintln("RECEIVED NULL REQUEST");
//            return;
//        }
//        
//        if (layerRequest instanceof Rectangle2D) {
//            receiveAreaRequest((Rectangle2D) layerRequest, requester);
//        } else {
//            log.aprintln("BAD REQUEST CLASS: " +
//                         layerRequest.getClass().getName());
//		}
    	updateVisibleStamps();
    }
        
    private void receiveAreaRequest(Rectangle2D where,
                                    DataReceiver requester)
    {
        StampShape[] stamps = getStamps();
        
        StampTask task = startTask();
        task.updateStatus(Status.YELLOW);
        
        log.println("where = " + where);
        
        double x = where.getX();
        double y = where.getY();
        double w = where.getWidth();
        double h = where.getHeight();
        if (w >= 360)
        {
            x = 0;
            w = 360;
        }
        else
            x -= Math.floor(x/360.0) * 360.0;
        
        Rectangle2D where1 = new Rectangle2D.Double(x, y, w, h);
        Rectangle2D where2 = null;
        
        // Handle the two cases involving x-coordinate going past
        // 360 degrees:
        // Area rectangle extends past 360...
        if (where1.getMaxX() >= 360) {
            where2 = new Rectangle2D.Double(x-360, y, w, h);
            log.println("where2 = " + where2);
        }
        // Normalized stamp extends past 360 but
        // where rectangle does not...
        else if (where1.getMaxX() <= 180) {
            where2 = new Rectangle2D.Double(x+360, y, w, h);
            log.println("where2 = " + where2);
        }
        
        log.println("where1 = " + where1);
        log.println("where2 = " + where2);
                
        ArrayList<StampShape> data = new ArrayList<StampShape>(200);
        
        Shape path;
        for (int i=0; i<stamps.length; i++)
        {
        	// In rare circumstances we can get here with null stamps
        	if (stamps[i]==null) {
        		log.println("Null stamp detected!");
        		continue;
        	}
            path = stamps[i].getNormalPath();
            if (path != null &&
                ( path.intersects(where1) ||
                  (where2 != null && path.intersects(where2))))
                data.add(stamps[i]);
        }
        
        requester.receiveData(data.toArray(new StampShape[data.size()]));
        task.updateStatus(Status.DONE);
    }
    
    public void updateVisibleStamps()
    {
        StampShape[] stamps = getStamps();

        ArrayList<StampShape> data = new ArrayList<StampShape>(200);

        List<StampFilter> filters = viewToUpdate.getFilters();
        		
		mainLoop: 		for (int i=0; i<stamps.length; i++)
		{			
			for (StampFilter filter : filters) {
				if (!filter.filterActive || filter.dataIndex==-1) {
					continue;
				}

				int min = filter.getMinValueToMatch();
				int max = filter.getMaxValueToMatch();

				if (filter.dataIndex2==-1) {
					Object o = stamps[i].getStamp().getData()[filter.dataIndex];
					
					if (o instanceof Float) {
    					float val = ((Float)(stamps[i].getStamp().getData()[filter.dataIndex])).floatValue();

    					if (val<min || val>max) {
    						continue mainLoop;
    					} 
					} else if (o instanceof Double) {
    					double val = ((Double)(stamps[i].getStamp().getData()[filter.dataIndex])).doubleValue();

    					if (val<min || val>max) {
    						continue mainLoop;
    					} 
					} else if (o instanceof Integer) {
    					int val = ((Integer)(stamps[i].getStamp().getData()[filter.dataIndex])).intValue();

    					if (val<min || val>max) {
    						continue mainLoop;
    					} 						
					} else if (o instanceof Short) {
    					int val = ((Short)(stamps[i].getStamp().getData()[filter.dataIndex])).shortValue();

    					if (val<min || val>max) {
    						continue mainLoop;
    					} 						
					} else if (o instanceof Long) {
    					long val = ((Long)(stamps[i].getStamp().getData()[filter.dataIndex])).longValue();

    					if (val<min || val>max) {
    						continue mainLoop;
    					} 						
					} if (o instanceof BigDecimal) { // Why in the world does the moc database use BigDecimal?
    					float val = ((BigDecimal)(stamps[i].getStamp().getData()[filter.dataIndex])).floatValue();

    					if (val<min || val>max) {
    						continue mainLoop;
    					} 						
					} else {
//						if (o==null) {
//							System.out.println("o is null..."); 
//						} else {
//							System.out.println("It's a : " + o.getClass());
//						}
					}
				} else {
					Object o = stamps[i].getStamp().getData()[filter.dataIndex];
					Object o2 = stamps[i].getStamp().getData()[filter.dataIndex2];
					
					if (o instanceof Float && o2 instanceof Float) {
    					float val = ((Float)(stamps[i].getStamp().getData()[filter.dataIndex])).floatValue();
    					float val2 = ((Float)(stamps[i].getStamp().getData()[filter.dataIndex2])).floatValue();
    					
    					if ((val<min || val>max) && (val2<min || val2>max)) {
    						continue mainLoop;
    					} 
					} else if (o instanceof Integer && o2 instanceof Integer) {
    					int val = ((Integer)(stamps[i].getStamp().getData()[filter.dataIndex])).intValue();
    					int val2 = ((Integer)(stamps[i].getStamp().getData()[filter.dataIndex2])).intValue();
    					
    					if ((val<min || val>max) && (val2<min || val2>max)) {
    						continue mainLoop;
    					} 					
					} else {
						System.out.println("Not a float value: " + filter.columnName + " " + filter.dataIndex);
						System.out.println("It's a : " + o.getClass());
					}    						
				}
			}

			data.add(stamps[i]);    
		}
        
//		System.out.println("Returning " + data.size() + " stamps...");

        viewToUpdate.receiveData(data.toArray(new StampShape[data.size()]));

        if (viewToUpdate.getChild()!=null) {
        	viewToUpdate.getChild().receiveData(data.toArray(new StampShape[data.size()]));
        }
		
		//

        
        ////

        // Short circuit the rest of the code.
        if (true) return;
        
//        StampTask task = startTask();
//        task.updateStatus(Status.YELLOW);
//        
//        log.println("where = " + where);
//        
//        double x = where.getX();
//        double y = where.getY();
//        double w = where.getWidth();
//        double h = where.getHeight();
//        if (w >= 360)
//        {
//            x = 0;
//            w = 360;
//        }
//        else
//            x -= Math.floor(x/360.0) * 360.0;
//        
//        Rectangle2D where1 = new Rectangle2D.Double(x, y, w, h);
//        Rectangle2D where2 = null;
//        
//        // Handle the two cases involving x-coordinate going past
//        // 360 degrees:
//        // Area rectangle extends past 360...
//        if (where1.getMaxX() >= 360) {
//            where2 = new Rectangle2D.Double(x-360, y, w, h);
//            log.println("where2 = " + where2);
//        }
//        // Normalized stamp extends past 360 but
//        // where rectangle does not...
//        else if (where1.getMaxX() <= 180) {
//            where2 = new Rectangle2D.Double(x+360, y, w, h);
//            log.println("where2 = " + where2);
//        }
//        
//        log.println("where1 = " + where1);
//        log.println("where2 = " + where2);
//                
////        ArrayList<StampShape> data = new ArrayList<StampShape>(200);
//        
//        Shape path;
//        for (int i=0; i<stamps.length; i++)
//        {
//        	// In rare circumstances we can get here with null stamps
//        	if (stamps[i]==null) {
//        		log.println("Null stamp detected!");
//        		continue;
//        	}
//            path = stamps[i].getNormalPath();
//            if (path != null &&
//                ( path.intersects(where1) ||
//                  (where2 != null && path.intersects(where2))))
//                data.add(stamps[i]);
//        }
//        
//        requester.receiveData(data.toArray(new StampShape[data.size()]));
//        task.updateStatus(Status.DONE);
    }

    
    public String getInstrument() {
    	return settings.instrument;
    }
    
    private static String authStr = null;
    
    public static String getAuthString() {
    	if (authStr==null) {   		
	        String user = Main.USER;
	        String pass;
	        
	        if (Main.isInternal()) {
	        	pass = Util.mysqlPassHash(Main.PASS);
	        } else {
	        	pass = Util.apachePassHash(Main.USER, Main.PASS);
	        }
	
	        authStr = "&user="+user+"&password="+pass;
    	}
        return authStr;
    }
    
    enum Status {
		RED,
		YELLOW,
		PINK,
		GREEN,
		DONE
	}
    
    public class StampTask {
    	StampLayer myLayer;
    	private StampTask(StampLayer newLayer) {
    		myLayer=newLayer;
    	}
    	
    	Status currentStatus=Status.RED;
    	
    	public void updateStatus(Status newStatus) {
    		currentStatus=newStatus;
    		myLayer.updateStatus();
    	}
    }
    
    public synchronized StampTask startTask() {
    	StampTask newTask = new StampTask(this);
    	activeTasks.add(newTask);
    	return newTask;
    }
    
    List<StampTask> activeTasks = new ArrayList<StampTask>();
    
	public synchronized void updateStatus() {
		Color layerStatus = Util.darkGreen;
		List<StampTask> doneTasks = new ArrayList<StampTask>();
		for (StampTask task : activeTasks) {
			switch(task.currentStatus) {
			   case GREEN :
			   case DONE :
				   doneTasks.add(task);
				   continue;
			   case YELLOW :
				   if (layerStatus==Util.darkGreen) {
					   layerStatus=Color.yellow;
				   }
				   break;
			   case PINK :  // not sure how pink should be used
				   if (layerStatus==Util.darkGreen) {
					   layerStatus=Color.pink;
				   }
				   break;
			   case RED :
				   layerStatus = Color.red;
				   break;			
			}
		}
		
		activeTasks.removeAll(doneTasks);
		log.println("Status updated to : " + layerStatus);
		
		final Color newStatus = layerStatus;
		
		// get on the AWT thread to update the GUI
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				setStatus(newStatus);		
			}
		});
				
	}
	
    /**
     * Loads stamp data from the main database.
     */
	private synchronized void loadStampData()
	{
		Runnable runme = new Runnable() {

			public void run() {

				StampTask task = startTask();
				task.updateStatus(Status.RED);

				ArrayList<StampInterface> newStamps = new ArrayList<StampInterface>();
				Class[] newColumnClasses=new Class[0];
				String[] newColumnNames=new String[0];

				ProgressDialog dialog=null;
				ObjectInputStream ois=null;

				try
				{       
					String authStr = getAuthString();

					log.println("start of main database query: " + queryStr+authStr);

					dialog = new ProgressDialog(Main.mainFrame, StampLayer.this); 

					String urlStr = queryStr+authStr;

					int idx = urlStr.indexOf("?");

					String connStr = urlStr.substring(0,idx);
					
					String data = urlStr.substring(idx+1);
					
					URL url = new URL(connStr);
					URLConnection conn = url.openConnection();
					conn.setDoOutput(true);
					OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
					wr.write(data);
					wr.flush();
					//wr.close();
					
					dialog.updateStatus("Requesting stamps from server....");

					GZIPInputStream zipStream = new GZIPInputStream(conn.getInputStream());

					ois = new ObjectInputStream(zipStream);

					ResponseInfo info = (ResponseInfo)ois.readObject();
					if (info.getStatus()!=0) {
						JOptionPane.showMessageDialog(
								Main.mainFrame,
								info.getMessage(),
								"Query Result",
								JOptionPane.INFORMATION_MESSAGE
						);
					}

					dialog.updateStatus("Server completed query");

					newColumnClasses = (Class[])ois.readObject();
					newColumnNames = (String[])ois.readObject();

					dialog.updateStatus("Retrieving " + info.getRecordCnt() + " stamps...");

					int recordsToRead=info.getRecordCnt();            
					int recordsRead=0;

					newStamps = new ArrayList<StampInterface>(recordsToRead);

					dialog.startDownload(0, recordsToRead);

					while (recordsToRead>recordsRead && !dialog.isCanceled()) {
						StampInterface newStamp = (StampInterface)ois.readObject();
						newStamps.add(newStamp);
						recordsRead++;
						if (recordsRead%100==0) {
							dialog.downloadStatus(recordsRead);
						}

						dialog.setNote("Retrieving: " + recordsRead + " of " + recordsToRead);
					}

					dialog.downloadStatus(recordsRead);

					ois.close();
					zipStream.close();

					if (dialog.isCanceled()) {
						dialog.close();
						return;
					}

					int numRecords = newStamps.size();
					log.println("main database: read " + numRecords + " records");

					if (numRecords < 1)
					{
						String msg = "No stamps match the specified filter";
						throw new NoStampsException(msg);
					}
				}
				catch (NoStampsException ex) {
					log.println(ex);
					String msg = "No records matched your search criteria.  " +
					"Please try again with a different query to create the view.";
					msg = Util.lineWrap(msg, 55);
					JOptionPane.showMessageDialog(
							Main.mainFrame,
							msg,
							"Query Result",
							JOptionPane.INFORMATION_MESSAGE
					);
				}
				catch (Exception e) {
					log.aprintln("Error occurred while downloading " + settings.instrument + " stamps");
					log.aprintln(e);
					String msg =
						newStamps.size() > 0
						? "Only able to retrieve " + newStamps.size()
								: "Unable to retrieve any";

						msg += " " + settings.instrument + " stamps from the database, due to:\n" + e;
						msg = Util.lineWrap(msg, 55);
						
						JOptionPane.showMessageDialog(
								Main.mainFrame,
								msg,
								"Database Error",
								JOptionPane.ERROR_MESSAGE
						);
				}
				finally {                    	
					task.updateStatus(Status.DONE);

					if (!dialog.isCanceled()) {
						dialog.close();
						
						// lock the StampLayer before updating it
						synchronized(StampLayer.this) {
							// update the projection
							originalPO = Main.PO;
							
							stampMap.clear();
							cachedStamps = new StampShape[newStamps.size()];
							
							for (int i=0; i<newStamps.size(); i++) {
								StampInterface s = newStamps.get(i);

								StampShape shape = new StampShape(s, StampLayer.this);

								cachedStamps[i] = shape;
								stampMap.put(cachedStamps[i].getId().trim(), cachedStamps[i]);
							}
						}
						 
						final Class[] colTypes = newColumnClasses;
						final String[] colNames = newColumnNames;
												
				        List<StampFilter> filters = viewToUpdate.getFilters();

						for (int i=0; i<newColumnNames.length; i++) {
							for (StampFilter filter : filters) {
								if (newColumnNames[i].equalsIgnoreCase(filter.columnName+"_min")) {
									filter.dataIndex=i;
								} else if (newColumnNames[i].equalsIgnoreCase(filter.columnName+"_max")) {
									filter.dataIndex2=i;
								} else if (newColumnNames[i].equalsIgnoreCase(filter.columnName)) {
				    				filter.dataIndex=i;
								}
							}    				
						}
						
						// get on the AWT thread to update the GUI
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								// ensure table has been created
								viewToUpdate.createFocusPanel();
								
								// update the columns
								viewToUpdate.myFocus.updateData(colTypes, colNames, settings.initialColumns);								
							}
						});
						
						log.println("End of stamp data load");
					} else {
						log.println("Stamp data load cancelled");
					}
					if (ois!=null) {
						try {
							ois.close();
						} catch (Exception e) {

						}
					}
					queryThread=null;
					
					if (viewToUpdate!=null) {
						viewToUpdate.viewChanged();
						Layer.LView childLView = viewToUpdate.getChild();
						if (childLView != null)
							childLView.viewChanged();
					}

				}
				
			}
		};
		
		queryThread = new Thread(runme);
		queryThread.start();       
	}
	
    public Thread queryThread = null;
    
    private synchronized void reprojectStampData()
    {
        Iterator<StampShape> iterator = stampMap.values().iterator();
        while (iterator.hasNext()) {
            StampShape s = iterator.next();
            if (s != null)
                s.clearProjectedData();
        }
               
        originalPO = Main.PO;
    }

    public interface StampSelectionListener {
    	public void selectionsChanged();
    	public void selectionsAdded(List<StampShape> newStamps);
    }
    
    private class NoStampsException extends Exception
	{
    	NoStampsException(String msg)
		{
    		super(msg);
		}
	}
        
    public String getQuery() {
    	return queryStr;
    }    
}
