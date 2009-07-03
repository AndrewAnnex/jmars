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
import java.util.AbstractList;

/**
 * Implementation of List<E> that stores the data in a file, where each element
 * is converted to and from a fixed-length array of bytes using the
 * {@link #BufferConverter}.
 */
public final class DiskListBuf<E> extends AbstractList<E> {
	private final BufferConverter<E> converter;
	private final int itemSize;
	private final File file;
	private final RandomAccessFile fileAccess;
	private final byte[] bytes;
	private final ByteBuffer buffer;
	private int size;
	public DiskListBuf(BufferConverter<E> converter) throws IOException {
		this.converter = converter;
		itemSize = converter.getItemSize();
		file = File.createTempFile("data", ".tmp");
		fileAccess = new RandomAccessFile(file, "rw");
		bytes = new byte[itemSize];
		buffer = ByteBuffer.wrap(bytes);
	}
	public void finalize() {
		try {
			fileAccess.close();
			file.delete();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public E get(int index) {
		try {
			fileAccess.seek(index*itemSize);
			fileAccess.read(bytes);
			buffer.rewind();
			return converter.load(buffer);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	public E set(int index, E value) {
		try {
			E old = get(index);
			buffer.rewind();
			converter.save(buffer, value);
			fileAccess.seek(index*itemSize);
			fileAccess.write(bytes);
			return old;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	public boolean add(E item) {
		try {
			buffer.rewind();
			converter.save(buffer, item);
			fileAccess.seek(size*itemSize);
			fileAccess.write(bytes);
			size ++;
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	public int size() {
		return size;
	}
	public static void main(String[] args) throws IOException {
		long size = 1000000;
		while (true) {
			DiskListBuf<Integer> thing = new DiskListBuf<Integer>(new BufferConverter.IntConverter(-Integer.MAX_VALUE));
			long t = System.currentTimeMillis();
			for (int i = 0; i < size; i++) {
				Integer o = i % 100 == 0 ? null : i;
				thing.add(o);
			}
			for (int i = 0; i < size; i++) {
				Integer o1 = thing.get(i);
				Integer o2 = i % 100 == 0 ? null : i;
				if (o1 != o2 && !o1.equals(o2)) {
					System.out.println("Mismatch at " + i);
				}
			}
			System.out.println(size + "\t" + (System.currentTimeMillis() - t));
			size *= 2;
		}
	}
}
