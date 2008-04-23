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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;

/**
 * Receives and processes MapData requests by filling in the Image property.
 * If an error occurs, the MapData image will be null.
 * If an intermediate update occurs, image will be non-null and the finished flag will be clear.
 * If a final update occurs, image will be non-null and the finished flag will be set.
 */
public class MapRetriever implements Runnable {

	private static final int NUM_RETRIEVER_THREADS = Config.get("map.retriever.threadCount", 5);	
	
	private static ExecutorService pool;
	private static DebugLog log = DebugLog.instance();
	
	public static MapRetriever getMapData(MapRequest request) {
		if (request.getExtent()==null) {
			log.println("NULL extent passed to MapRetriever.  MapRetriever isn't going to do anything useful for this case.  Correct the code that did this.");
			return null;
		}
		if (pool == null) {
			int procs = NUM_RETRIEVER_THREADS;
			log.println("Creating MapRetriever pool with " + procs + " processors");
			pool = Executors.newFixedThreadPool(procs, new MapThreadFactory());
		}
		
		MapRetriever newRetriever = new MapRetriever(request);
		
		return newRetriever;
	}
	
	MapRequest dataToFetch = null;
	MapData fetchedData = null;
	
	private MapRetriever(MapRequest request) {
		dataToFetch = request;
		fetchedData = new MapData(request);
	}
	
	/*
	 * Step 1: Ask CacheManager what tiles are cached
	 * Step 2: In parallel: Ask CacheManager for cached tiles
	 *                      Ask CacheManager for fuzzy non-cached tiles
	 *                      Ask DownloadManager for non-cached tiles
	 * Step 3: Track status of CacheManager and DownloadManager
	 *    As tiles are returned, notify MapProcessor
	 * Step 4: When CacheManager done, if not all cached tiles were retrieved, request them from DLManager
	 * Step 5: When DownloadManager done, send any successfully downloaded tiles to CacheManager so they
	 *    can be written to disk
	 * Step 6: Profit
	 * 
	 */
	
	private Collection<MapTile> unFinishedMapTiles;
	private Collection<MapTile> cachedMapTiles;
	private Collection<MapTile> nonCachedMapTiles;
	
	public static double getLongitudeTileSize(int ppd) {
		return 256.0/ppd;
		// 256x256 on a side
	}
	
	public static double getLatitudeTileSize(int ppd) {		
		return 256.0/ppd;
	}

	private MapTile requestTiles[];
	
	private void initTiles(MapRequest data) {
		double xstep = getLongitudeTileSize(data.getPPD());
		double ystep = getLatitudeTileSize(data.getPPD());
		
		double pixelWidth = 1.0 / data.getPPD();
		
		// tile set will not contain two tiles with the same xtile/ytile
		Set<MapTile> tiles = new LinkedHashSet<MapTile>();
		
		// Apply the offset (defaults to 0.0) to handle nudged maps
		Point2D offset = data.getSource().getOffset();
		
		Rectangle2D extent = data.getExtent();
		
		Rectangle2D offsetExtent = new Rectangle2D.Double(extent.getMinX() + offset.getX(),
				extent.getMinY() + offset.getY(), extent.getWidth(), extent.getHeight());
		
		// for each wrapped world rectangle
		Rectangle2D[] wrappedRects = Util.toWrappedWorld(offsetExtent);
		for (int i = 0; i < wrappedRects.length; i++) {
			Rectangle2D wrappedWorld = wrappedRects[i];
			
			double x = wrappedWorld.getX();
			double y = wrappedWorld.getY();
			double width = wrappedWorld.getWidth();
			double height = wrappedWorld.getHeight();
			
			// get tile range of this wrapped piece
			int xtileStart = (int) Math.floor(x / xstep); 
			int ytileStart = (int) Math.floor((y + 90.0) / ystep);
			
			// Subract the width of 1 pixel from the calculation to avoid
			// getting an extra column of tiles when the width is the same as the xstep
			int xtileEnd = (int) ((x + width - pixelWidth) / xstep);			
			int ytileEnd = (int) ((y + 90.0 + height-pixelWidth) / ystep);
			
			// create each tile
			for (int xtile = xtileStart; xtile <= xtileEnd; xtile ++) {
				for (int ytile = ytileStart; ytile <= ytileEnd; ytile ++) {
					tiles.add(new MapTile(fetchedData.getRequest(), xtile, ytile));
				}
			}
		}
		
		requestTiles = (MapTile[])tiles.toArray(new MapTile[0]);
	}

	
	public MapTile[] getIncompleteTiles() {
		Vector<MapTile> incompleteTiles = new Vector<MapTile>();
		
		for (int i = 0; i<requestTiles.length; i++) {
			if (requestTiles[i].isMissing() || requestTiles[i].isFuzzy()) {
				incompleteTiles.add(requestTiles[i]);
			}
		}
		
		if (incompleteTiles.size()==0)	return null;

		return (MapTile[])incompleteTiles.toArray(new MapTile[incompleteTiles.size()]);
	}
	
	private void fetchMapData(MapRequest mapRequest) {		
		initTiles(mapRequest);
		
		MapTile tiles[]=getIncompleteTiles();
		
		MapTile checkedTiles[][]=CacheManager.checkCache(tiles);
		MapTile cachedTiles[]=checkedTiles[0];
		MapTile nonCachedTiles[]=checkedTiles[1];
		
		cachedMapTiles = new ArrayList<MapTile>(Arrays.asList(cachedTiles));
		nonCachedMapTiles = new ArrayList<MapTile>(Arrays.asList(nonCachedTiles));
		unFinishedMapTiles = new ArrayList<MapTile>(checkedTiles.length);
		unFinishedMapTiles.addAll(Arrays.asList(cachedTiles));
		unFinishedMapTiles.addAll(Arrays.asList(nonCachedTiles));
		
		fuzzyTilesRequested = nonCachedTiles.length;
		cachedTilesRequested = cachedTiles.length;
		downloadedTilesRequested = nonCachedTiles.length;
		
		CacheManager.getTiles(this, cachedTiles);
		CacheManager.getFuzzyTiles(this, nonCachedTiles);
		new DownloadManager(this, nonCachedTiles);
		
	}

	private boolean finishedDataSent=false;

	private int fuzzyTilesRequested=0;
	private int cachedTilesRequested=0;
	private int downloadedTilesRequested=0;

	private int fuzzyTilesReceived=0;
	private int cachedTilesReceived=0;
	private int downloadedTilesReceived=0;

	public int getFuzzyTilesRequested() {
		return fuzzyTilesRequested;
	}
	
	public int getCachedTilesRequested() {
		return cachedTilesRequested;
	}
	
	public int getDownloadedTilesRequested() {
		return downloadedTilesRequested;
	}

	
	public int getFuzzyTileCnt() {
		return fuzzyTilesReceived;
	}
	
	public int getCachedTilesReceived() {
		return cachedTilesReceived;
	}
	
	public int getDownloadedTilesReceived() {
		return downloadedTilesReceived;
	}

	public boolean allTilesFetched() {
		return (cachedMapTiles.isEmpty() && nonCachedMapTiles.isEmpty());
	}
	
//	List modifiedExtents = new ArrayList();
	
	public synchronized void cacheResponse(MapTile tile, BufferedImage image) {
		if (tile.getRequest().isCancelled()) {
			return;
		}
		
		if (cachedMapTiles.contains(tile)) {
			cachedTilesReceived++;
			cachedMapTiles.remove(tile);
			
			// Uncomment this block to cause the cache to drop 1/5 of
			// the tiles.  This is useful for testing the code that draws
			// checkerboards over bad tiles.  Just don't forget to comment it 
			// back out before you check in changes!
			//if (tile.getXtile()*tile.getYtile()%5==0) {
			//	return;
			//}
			
			if (image==null) {
				nonCachedMapTiles.add(tile);
				MapTile tiles[] = new MapTile[1];
				tiles[0]=tile;
				
				// TODO: This is wild overkill.  Instead, should try to 
				// add to the existing DownloadManager for this request
				
				log.println("CacheManager returned a null image, trying to download image instead");
				
				new DownloadManager(this, tiles);
				return;
			}
			
//			modifiedExtents.add(tile.getExtent());
			
			tile.setImage(image);
			fetchedData.addTile(tile);
			
			if (cachedMapTiles.isEmpty()) {
				sendUpdate();
			}
		} else {
			// error
		}
	}
	
	public synchronized void fuzzyResponse(MapTile tile, BufferedImage image) {
		if (tile.getRequest().isCancelled()) {
			return;
		}
		
		if (nonCachedMapTiles.contains(tile)) {
			fuzzyTilesReceived++;
			tile.setFuzzyImage(image);
			fetchedData.addTile(tile);

//			modifiedExtents.add(tile.getExtent());
			
			sendUpdate();
		} else {
			// This could be fine - it just means we may have received the downloadResponse
			// first.  Should we separate these into two different outstanding queues
			// for ultimate clarity?
		}
	}
	
	public synchronized void downloadResponse(MapTile tile, BufferedImage image) {
		if (tile.getRequest().isCancelled()) {
			return;
		}
		
		if (nonCachedMapTiles.contains(tile)) {
			downloadedTilesReceived++;
			
			if (image==null && !tile.hasError()) {
				// error
				log.println("Null Tile in downloadResponse but not marked as an error");
				return;
			}

			nonCachedMapTiles.remove(tile);

			if (image!=null) {
				tile.setImage(image);				
				fetchedData.addTile(tile);
				
//				modifiedExtents.add(tile.getExtent());
				
				sendUpdate();
				
				CacheManager.storeMapData(tile);				
			}
		} else {
			// error
		}
	}

	public synchronized MapData getData(boolean convert) {
		return fetchedData.getDeepCopy(convert);
	}
	
	public MapRequest getRequest() {
		return dataToFetch;
	}
	
	private synchronized void sendUpdate() {		
		if (finishedDataSent==true) {
			log.println("MapRetriever received an update after sending finished data!.");
			return;
		}
		
		if (dataToFetch.isCancelled()) {
			return;
		}
		
		if (allTilesFetched()) {
			if (!fetchedData.isFinished()) {
				log.println("Setting data to finished");	
				fetchedData.setFinished(true);
			}
		}
		
		if (fetchedData.isFinished()) {
			finishedDataSent=true;
		}
		
		receiver.receiveUpdate();
	}
	
	public void run() {
		fetchMapData(dataToFetch);
	}
	
	private MapProcessor receiver;
	
	public void setReceiver(MapProcessor recv) {
		this.receiver = recv;
		pool.execute(this);
	}
}
