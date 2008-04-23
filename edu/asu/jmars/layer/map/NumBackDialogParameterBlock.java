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

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.geom.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;


   public class NumBackDialogParameterBlock extends BDialogParameterBlock
   {
		public Color color;

      public NumBackDialogParameterBlock()
      {
         parameterBlockName="NumBackDialog";
         tileChoice=0;
         customChoice=null;
			if (Main.isInternal())
         	maxAgents=5;	
			else
         	maxAgents=3;	
			maxResolutionIndex=12;
			color = Color.red;
			
			
         if (Main.isBot())
            serverAddress="";
         else
			 serverAddress=Config.get("tiles.numeric");
      }



      // Required for proper behavior when using these things as keys
      // in a Map.
      public boolean equals(Object obj)
      {
         if(obj != null  &&  obj.getClass() == getClass())
          {
            NumBackDialogParameterBlock obj2 = (NumBackDialogParameterBlock) obj;

            // This should ALWAYS include comparisons for any+all
            // members that affect whether or not two parameter
            // blocks are "identical" enough to require the
            // creation of two different layers.
            return  obj2.tileChoice == tileChoice
               &&  ( obj2.customChoice == null
                    ? customChoice == null
                    : obj2.customChoice.equals(customChoice) )
               &&  ( obj2.serverAddress == null
                    ? serverAddress == null
                    : obj2.serverAddress.equals(serverAddress) );
          }
         return  false;
      }
   }
