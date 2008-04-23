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
 ** Utility class for time conversions. Based on an arbitrary instance
 ** in time, linearly extrapolated.
 **
 ** <p>What's referred to as "unix" time is the number of seconds
 ** since midnight, Jan 1, 1970 UTC.
 **/
public class Time
 {
	private static final String epochUtc = "2002-091 // 16:19:50.000";
	private static final long epochEt = 70950054;
	private static final long epochUnix = 1017703190;

	/**
	 ** Returns the current time in ET.
	 **/
	public static long getNowEt()
	 {
		return  unixToEt(System.currentTimeMillis() / 1000);
	 }

	/**
	 ** Given a unix time, returns its ET.
	 **/
	public static long unixToEt(long unix)
	 {
		return  unix - epochUnix + epochEt;
	 }
 }
