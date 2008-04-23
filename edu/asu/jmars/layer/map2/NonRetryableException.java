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

/**
 * This exception is generated when it is NOT possible to retry the 
 * operation causing this exception at a later stage and get a good
 * response back.
 */
public class NonRetryableException extends Exception {
	public NonRetryableException(String message, Throwable cause) {
		super(message, cause);
	}

	public NonRetryableException(Throwable cause) {
		super(cause);
	}
	
	public NonRetryableException(String message){
		super(message);
	}
}
