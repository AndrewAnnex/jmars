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


package edu.asu.jmars.layer;

import edu.asu.jmars.*;
import edu.asu.jmars.util.*;
import javax.swing.*;
import java.io.*;

public class WatchedThread extends Thread
 {
	private static final DebugLog log = DebugLog.instance();

	public WatchedThread()
	 {
		super();
	 }

	public WatchedThread(Runnable target)
	 {
		super(target);
	 }

	public WatchedThread(Runnable target, String name)
	 {
		super(target, name);
	 }

	public WatchedThread(String name)
	 {
		super(name);
	 }

	public WatchedThread(ThreadGroup group, Runnable target)
	 {
		super(group, target);
	 }

	public WatchedThread(ThreadGroup group, Runnable target, String name)
	 {
		super(group, target, name);
	 }

	public WatchedThread(ThreadGroup group, String name)
	 {
		super(group, name);
	 }

/*	public void start()
	 {
		log.println("Spawning new thread: " + getName());
		log.printStack(5);
		super.start();
	 }
*/
	public final void run()
	 {
		try
		 {
			super.run();
		 }
		catch(Throwable e)
		 {
			e.printStackTrace();
			JOptionPane.showMessageDialog(
				Main.mainFrame,
				"Uncaught exception (in thread " + getName() + "):\n" +
				"    " + e + "\n" +
				"\n" +
				"The application may or may not have been destabilized.",
				"UNCAUGHT EXCEPTION",
				JOptionPane.ERROR_MESSAGE
				);
		 }
	 }
 }
