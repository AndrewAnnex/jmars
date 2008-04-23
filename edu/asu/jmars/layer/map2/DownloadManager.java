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


package edu.asu.jmars.layer.map2;

import java.awt.image.BufferedImage;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;

public class DownloadManager {
	private static DebugLog log = DebugLog.instance();
	private static ExecutorService pool;
	
	private static final int NUM_DOWNLOAD_THREADS = Config.get("map.download.threadCount", 50);

	/*
	 * This block is executed the first time DownloadManager is invoked.  This pool of threads will
	 * continue to be available and be used anytime a tile needs to be downloaded thereafter.
	 */
	static {
		if (pool == null) {
			log.println("Creating DownloadManager pool with " + NUM_DOWNLOAD_THREADS + " processors");
			
			pool = new ThreadPoolExecutor(NUM_DOWNLOAD_THREADS, NUM_DOWNLOAD_THREADS,
                    0L, TimeUnit.MILLISECONDS,
                   new PriorityBlockingQueue());
		}		
	}
		
	MapRetriever myRetriever=null;
	MapTile mapTiles[]=null;
	
	// These are used to attempt to give instances of DownloadManager a unique id to make debugging 
	// output easier to follow
	private static int instanceId=1;	
	private int myId=instanceId++;
	
	public DownloadManager(MapRetriever retriever, MapTile tiles[]) {
		myRetriever=retriever;
		mapTiles=tiles;
		startingSize=tiles.length;
		getMapData();
	}
	
	Vector<MapTile> tilesToFetch = new Vector<MapTile>();
	int startingSize = 1;
	
	public int getPercentRemaining() {
		int remainingTiles = tilesToFetch.size();
		
		return remainingTiles/startingSize;
	}
	
	public void getMapData() {
		if (mapTiles==null || mapTiles.length==0) {
			return;
		}
		
		MapSource source = mapTiles[0].getRequest().getSource();
		TileDownloader td[] = new TileDownloader[mapTiles.length];
		
		for (int i=0; i<mapTiles.length; i++) {
			MapTile mapTile = mapTiles[i];
			td[i]= new TileDownloader(this, mapTile, source);
			tilesToFetch.add(mapTile);
		}
		
		for (int i=0; i<td.length; i++) {
			pool.execute(td[i]);
		}		
	}
	
	synchronized void tileFetched(MapTile tile, BufferedImage image) {
		tilesToFetch.remove(tile);

		log.println("DM("+myId+") has " + tilesToFetch.size() + " tiles left");
		
		if (tilesToFetch.size()==0) {
			log.println("DM("+myId+") is done");
		}
		
		myRetriever.downloadResponse(tile, image);
	}
}

class TileDownloader implements Runnable, Comparable {
	
	
	// This is used to determine the relative priority of tiles being fetched.
	// Unfortunately it is only guaranteed to be called when a tile is being added to
	// the prioritization queue.  Once the tile already exists in the queue, its compareTo
	// method may or may not ever be called again.
	//
	// This still has the desired behavior of giving priority to tasks that haven't started yet,
	// such as single pixel requests done by the source selection GUI.  This lets us very
	// quickly modify a request in progress, regardless of how many download requests are
	// already waiting in the queue.
	public int compareTo(Object o) {
		TileDownloader tdo = (TileDownloader) o;
				
		return tdo.dm.getPercentRemaining() - dm.getPercentRemaining();
	}

	private static DebugLog log = DebugLog.instance();

	DownloadManager dm;
	MapTile mapTile;
	MapSource source;
	BufferedImage tileImage;
	
	TileDownloader(DownloadManager inDM, MapTile inTile, MapSource inSource) {
		dm=inDM;
		mapTile=inTile;
		source=inSource;
	}
	
	public void run() {
		fetchTile();
		dm.tileFetched(mapTile, tileImage);
	}
	
	private void fetchTile() {
		if (mapTile.getRequest().isCancelled()) {
			log.println("mapTile is Cancelled!");
			return;
		}
		
	    String errorStr=null;
	    int maxRetries = source.getServer().getMaxRequests();
	    
	    for (int i=0; i<maxRetries; i++) {
	    	if (errorStr!=null) {
	    		log.println("Tile download failed: Retrying...");
	    	}
	    	
			try {
				tileImage = source.fetchTile(new MapRequest(
					mapTile.getRequest().getSource(),
					mapTile.getTileExtent(),
					mapTile.getRequest().getPPD(),
					mapTile.getRequest().getProjection()));
			    errorStr=null;
			    break;
			}
			catch (RetryableException re){
				errorStr = re.getMessage();
			}
			catch (Exception ex){
				errorStr = ex.getMessage();
				break; // don't retry
			}
			continue;
	    }// end for loop
	    
	    if (errorStr!=null) {
	    	log.println("Tile Download failed: " + errorStr);
	    	mapTile.setErrorMessage(errorStr);
	    }
	}
}
