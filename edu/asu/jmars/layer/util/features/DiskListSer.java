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


package edu.asu.jmars.layer.util.features;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.asu.jmars.util.Util;

/**
 * Stores elements in order of addition using pages, stored in memory when there
 * is space and on disk otherwise - removal is not allowed.
 * 
 * General, but *slow*.
 */
public final class DiskListSer<E> {
	private final int maxPages;
	private final int pageSize;
	private final List<E[]> pages = new ArrayList<E[]>();
	private final List<Integer> loadOrder = new ArrayList<Integer>();
	private final Set<Integer> dirtyPages = new HashSet<Integer>();
	private final String cachePrefix;
	private int size;
	public DiskListSer(int pageSize, int maxPages) throws IOException {
		this.maxPages = maxPages;
		this.pageSize = pageSize;
		File f = File.createTempFile("data", ".tmp");
		f.delete();
		f.mkdirs();
		cachePrefix = f.getAbsolutePath() + File.separator;
	}
	public void finalize() {
		try {
			super.finalize();
			clear();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	public void clear() {
		Util.recursiveRemoveDir(new File(cachePrefix));
		pages.clear();
		loadOrder.clear();
		dirtyPages.clear();
	}
	public void add(E element) {
		getPage(size)[getPageOffset(size)] = element;
		size ++;
	}
	public E get(int index) {
		return getPage(index)[getPageOffset(index)];
	}
	public int getSize() {
		return size;
	}
	public E set(int index, E element) {
		E[] page = getPage(index);
		int pageOffset = getPageOffset(index);
		E old = page[pageOffset];
		page[pageOffset] = element;
		dirtyPages.add(index/pageSize);
		return old;
	}
	private int getPageOffset(int index) {
		return index - index/pageSize*pageSize;
	}
	private E[] getPage(int index) {
		int pageIndex = index / pageSize;
		int pageCount = pages.size();
		if (pageIndex < pageCount) {
			// use existing page
			E[] page = pages.get(pageIndex);
			if (page == null) {
				// will have to reload the page, but first make sure we have room
				trimLoadedPages();
				// now load the page
				page = load(pageIndex);
				pages.set(pageIndex, page);
				loadOrder.add(pageIndex);
			}
			return page;
		} else {
			// create a page, but first make sure we have room
			trimLoadedPages();
			// now create the page
			E[] page = (E[])new Object[pageSize];
			pages.add(page);
			loadOrder.add(pageIndex);
			dirtyPages.add(pageIndex);
			return page;
		}
	}
	// make sure at least one page is free
	private void trimLoadedPages() {
		if (loadOrder.size() >= maxPages) {
			int dropIndex = loadOrder.remove(0);
			E[] dropPage = pages.set(dropIndex, null);
			if (dirtyPages.contains(dropIndex)) {
				save(dropIndex, dropPage);
				dirtyPages.remove(dropIndex);
			}
		}
	}
	private void save(int pageIndex, E[] page) {
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(new FileOutputStream(cachePrefix + pageIndex));
			oos.writeObject(page);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (oos != null) {
				try {
					oos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	private E[] load(int pageIndex) {
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(new FileInputStream(cachePrefix + pageIndex));
			return (E[])ois.readObject();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (ois != null) {
				try {
					ois.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	public static void main(String[] args) throws IOException {
		for (int size: Arrays.asList(250, 500, 1000, 2000, 5000)) {
			long t = System.currentTimeMillis();
			DiskListSer<Integer> thing = new DiskListSer<Integer>(size, 100000/size);
			for (int i = 0; i < 1000000; i++) {
				thing.add(i);
			}
			for (int i = 0; i < 1000000; i++) {
				if (thing.get(i) != i) {
					System.out.println("Mismatch at " + i);
				}
			}
			System.out.println(size + "\t" + (System.currentTimeMillis() - t));
		}
	}
}
