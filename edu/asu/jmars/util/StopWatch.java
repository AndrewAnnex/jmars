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


package edu.asu.jmars.util;

/**
 * Encapsulation of an always-running stopwatch.  Designed for helping
 * performance tuning and obsession.  
 * 
 * @author hoffj MSFF-ASU
 */
public class StopWatch
{
    private long baseTime = 0;
    private long lastLapTime = 0;
    
    /**
     * Creates instance and sets base time to current system time.
     */
    public StopWatch()
    {
        reset();
    }
    
    /**
     * Sets base time for watch to current system time.
     */
    public void reset()
    {
        baseTime = System.currentTimeMillis();
        lastLapTime = baseTime;    
    }

    /**
     * Absolute base time for watch; changed via {@link #reset}.        
     */
    public long baseTimeMillis()
    {
        return baseTime;
    }

    /**
     * Elapsed time since last {@link #reset} or instance creation.        
     */
    public long elapsedMillis()
    {
        return System.currentTimeMillis() - baseTime;
    }
    
    /**
     * Returns elapsed time since:
     * <ul>
     * <li> (1) last call to this method, or 
     * <li> (2) last call to {@link #reset}, or
     * <li> (3) instance was created.
     * </ul>
     */
    public long lapMillis()
    {
        long curTime = System.currentTimeMillis();
        long lap = curTime - lastLapTime;
        lastLapTime = curTime;
        
        return lap;
    }
}

