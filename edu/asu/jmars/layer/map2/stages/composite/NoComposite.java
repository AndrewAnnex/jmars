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


package edu.asu.jmars.layer.map2.stages.composite;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import edu.asu.jmars.layer.map2.MapAttr;

public class NoComposite extends CompositeStage implements Cloneable, Serializable {
	private static final long serialVersionUID = 2L;
	
	public NoComposite(NoCompositeSettings settings){
		super(settings);
	}
	
	public BufferedImage makeBufferedImage(int width, int height) {
		return null;
	}

	public MapAttr[] consumes(int inputNumber){
		return new MapAttr[0];
	}
	
	public MapAttr produces() {
		return null;
	}
	
	public String getStageName(){
		return getSettings().getStageName();
	}
	
	public int getInputCount(){
		return 0;
	}
	
	public String getInputName(int inputNumber){
		throw new UnsupportedOperationException();
	}
	
	public String[] getInputNames(){
		return new String[0];
	}
	
	public Object clone() throws CloneNotSupportedException {
		NoComposite stage = (NoComposite)super.clone();
		return stage;
	}
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}
}

