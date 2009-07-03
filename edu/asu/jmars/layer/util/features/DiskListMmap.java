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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of List<E> that stores the data in a memory mapped file, where
 * each element is converted to and from a fixed-length array of bytes using the
 * {@link #BufferConverter}.
 * 
 * Fast enough to be considered black magic by many tribes of developers.
 */
final class DiskListMmap<E> extends AbstractList<E> {
	private static List<DiskListMmap<?>> maps = new ArrayList<DiskListMmap<?>>();
	private static List<File> files = new ArrayList<File>();
	private static Thread cleaner = new Thread(new Runnable() {
		public void run() {
			for (DiskListMmap<?> map: maps) {
				map.clear();
			}
			for (File f: files) {
				f.delete();
			}
		}
	});
	static {
		Runtime.getRuntime().addShutdownHook(cleaner);
	}
	private final BufferConverter<E> converter;
	private final int itemSize;
	private File file;
	private RandomAccessFile fileAccess;
	private MappedByteBuffer buffer;
	private int usedSize = 0;
	private long bufSize = 100000;
	public DiskListMmap(BufferConverter<E> converter) throws IOException {
		this.converter = converter;
		this.itemSize = converter.getItemSize();
	}
	private ByteBuffer getBuffer() {
		if (buffer == null) {
			try {
				if (file == null) {
					file = File.createTempFile("data", ".tmp");
					fileAccess = new RandomAccessFile(file, "rw");
					synchronized(maps) {
						if (!maps.contains(this)) {
							maps.add(this);
						}
					}
					synchronized(files) {
						files.add(file);
					}
				}
				buffer = fileAccess.getChannel().map(MapMode.READ_WRITE, 0, bufSize);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return buffer;
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
		try {
			if (fileAccess != null) {
				fileAccess.getChannel().close();
				fileAccess.close();
			}
			if (file != null) {
				file.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			fileAccess = null;
			file = null;
			buffer = null;
			usedSize = 0;
			bufSize = 100000;
		}
	}
	public boolean add(E element) {
		if (usedSize + itemSize > bufSize) {
			bufSize = (bufSize+itemSize)/2*3;
			buffer.force();
			buffer = null;
		}
		ByteBuffer b = getBuffer();
		b.position(usedSize);
		converter.save(b, element);
		usedSize += itemSize;
		return true;
	}
	public E get(int index) {
		ByteBuffer b = getBuffer();
		b.position(itemSize*index);
		return converter.load(b);
	}
	public E set(int index, E element) {
		ByteBuffer b = getBuffer();
		int pos = itemSize*index;
		b.position(pos);
		E old = get(index);
		b.position(pos);
		converter.save(b, element);
		return old;
	}
	public int size() {
		return usedSize / itemSize;
	}
	public static void main(String[] args) throws IOException {
		long size = 1000000;
		while (true) {
			DiskListMmap<String> thing = new DiskListMmap<String>(new BufferConverter.StringConverter(20, '\uffff'));
			for (int i = 0; i < size; i++) {
				thing.add("abcdefghijklm");
			}
			long t = System.currentTimeMillis();
			for (int i = 0; i < size; i++) {
				if (!thing.get(i).equals("abcdefghi")) {
					System.out.println("Mismatch at " + i);
				}
			}
			System.out.println(size + "\t" + (System.currentTimeMillis() - t));
			size *= 2;
		}
	}
}

