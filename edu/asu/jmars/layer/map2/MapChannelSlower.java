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

import java.awt.geom.Rectangle2D;

import javax.swing.SwingUtilities;

import edu.asu.jmars.ProjObj;
import edu.asu.jmars.util.DeltaExecutor;

/**
 * Uses {@link DeltaExecutor} to transparently shield a {@link MapChannel}
 * from excessive view or pipeline changes.
 * 
 * For example, if the delta given to the constructor is 3 seconds, and 40
 * updates arrive in that time, this channel will process the first and last
 * updates instead of attempting to handle each one in turn.
 */
public class MapChannelSlower implements Runnable {
	private final DeltaExecutor slowExecutor;
	private final MapChannelTiled channel;
	private ProjObj po;
	private int ppd;
	private Rectangle2D extent;
	private Pipeline[] pipeline;
	public MapChannelSlower(MapChannelTiled channel, int delta) {
		this.channel = channel;
		this.slowExecutor = new DeltaExecutor(delta, this);
	}
	public ProjObj getProjection() {
		return po;
	}
	public int getPPD() {
		return ppd;
	}
	public Rectangle2D getExtent() {
		return extent;
	}
	public Pipeline[] getPipeline() {
		return pipeline;
	}
	private void set(ProjObj po, int ppd, Rectangle2D extent, Pipeline[] pipelines) {
		this.po = po;
		this.ppd = ppd;
		this.extent = extent;
		this.pipeline = pipelines;
	}
	private void update(boolean now) {
		if (now) {
			slowExecutor.runImmediately();
		} else {
			slowExecutor.runDeferrable();
		}
	}
	public synchronized void setRequest(ProjObj po, Rectangle2D extent, int ppd, Pipeline[] pipelines) {
		set(po, ppd, extent, pipelines);
		update(false);
	}
	public synchronized void setRequest(ProjObj po, Rectangle2D extent, int ppd, Pipeline[] pipelines, boolean now) {
		set(po, ppd, extent, pipelines);
		update(now);
	}
	public synchronized void setPipelines(Pipeline[] pipelines) {
		this.pipeline = pipelines;
		update(false);
	}
	public synchronized void setPipelines(Pipeline[] pipelines, boolean now) {
		this.pipeline = pipelines;
		update(now);
	}
	public synchronized void setView(ProjObj po, Rectangle2D extent, int ppd) {
		set(po, ppd, extent, pipeline);
		update(false);
	}
	public synchronized void setView(ProjObj po, Rectangle2D extent, int ppd, boolean now) {
		set(po, ppd, extent, pipeline);
		update(now);
	}
	public synchronized boolean isFinished() {
		return channel.isFinished() && !slowExecutor.deferredWaiting();
	}
	public synchronized void cancel() {
		channel.cancel();
		slowExecutor.reset();
	}
	public void restart() {
		update(false);
	}
	public void restart(boolean now) {
		update(now);
	}
	public void run() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				synchronized(MapChannelSlower.this) {
					channel.setRequest(po, extent, ppd, pipeline);
				}
			}
		});
	}
}

