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


package edu.asu.jmars.layer.map;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.lang.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.geom.*;
import javax.swing.*;
import java.util.*;
import java.util.Arrays.*;
import java.io.*;

/* Base Class for ALL background layers */

public abstract class BLayer extends Layer implements DataReceiver, Runnable, Serializable
{

	/*
	** Abstract Function.  Used for creating Living agent to fetch data over the network.
	** A final piece needed by the agent, DialogParameterBlock, will be provided by the layer itself
	*/

	protected abstract Agent new_Agent(Object ar, AgentManager.Semephore as);


	/* Abstract Function.  Used for creating Dead agents (which are only for filename generation).
	** Calling object will provided the necessary DialogParameterBlock
	*/
	protected abstract Agent new_Agent(BDialogParameterBlock bdpb);

   protected static class AgentRequest implements Serializable
   {
	  public AgentRequest() { }
      public BackLayer.WorldImage   wi;
      public DataReceiver dr;
      public Layer        layer=null;
   }

	public abstract RenderedImage loadBufferedImage(String path);

}

