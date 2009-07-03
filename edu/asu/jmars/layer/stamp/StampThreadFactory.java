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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Based on {@link java.util.concurrent.Executors.DefaultThreadFactory}, but
 * lowers the priority.
 */
public class StampThreadFactory implements ThreadFactory {
	static final AtomicInteger poolNumber = new AtomicInteger(1);
	final ThreadGroup group;
	final AtomicInteger threadNumber = new AtomicInteger(1);
	final String namePrefix;

	public StampThreadFactory(String name) {
		SecurityManager s = System.getSecurityManager();
		group = (s != null)? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
		namePrefix = name + "-" + poolNumber.getAndIncrement() + "-thread-";
	}

	public Thread newThread(Runnable r) {
		Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
		// do NOT want a hung Stage to keep the JVM open
		t.setDaemon(true);
		// do NOT want threads killing the AWT thread
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	}
}
