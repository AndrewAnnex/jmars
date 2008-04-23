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

public class AutoFillException extends Exception {
	final MapAttr mapAttr;
	final Stage targetStage;
	
	public AutoFillException(String message, MapAttr mapAttr, Stage targetStage){
		super(message);
		this.mapAttr = mapAttr;
		this.targetStage = targetStage;
	}
	
	public AutoFillException(String message, Throwable cause, MapAttr mapAttr, Stage targetStage){
		super(message, cause);
		this.mapAttr = mapAttr;
		this.targetStage = targetStage;
	}
	
	public AutoFillException(String message, Throwable cause){
		super(message, cause);
		this.mapAttr = null;
		this.targetStage = null;
	}
	
	public MapAttr getMapAttr(){
		return mapAttr;
	}
	
	public Stage getTargetStage(){
		return targetStage;
	}
	
	public String toString(){
		return this.getClass().getName()+" occurred due to "+
			(getCause() == null? getMessage(): getCause().toString());
	}
}
