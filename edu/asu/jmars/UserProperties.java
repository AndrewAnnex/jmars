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


package edu.asu.jmars;

import edu.asu.jmars.util.*;

/**
 * This file used to manage User Properties
 */
import java.awt.*;
import java.io.*;
import java.util.*;

public class UserProperties extends Object
{

  private static DebugLog log = DebugLog.instance();

  private static Properties props = new Properties();

   public UserProperties()
	{
	}

   public UserProperties(File f)
	throws IOException
	{
	   loadPropertiesFile(f.toString());
	}

  public String getProperty(String key) {
    return getProperty(key, null);
  }

  public String getProperty(String key, String def) {
    return props.getProperty(key, def);
  }

  public void setProperty(String key, String val) {
    props.setProperty(key, val);
  }

  public int getPropertyInt(String key) {
    return getPropertyInt(key, -1);
  }

  public int getPropertyInt(String key, int def) {
    String val = props.getProperty(key, String.valueOf(def));
    int ret = def;

    if ( val != null ) {
      ret = (new Integer(val)).intValue();
    }

    return ret;
  }

  public void setPropertyInt(String key, int val) {
    props.setProperty(key, String.valueOf(val));
  }

  public boolean getPropertyBool(String key) {
    return getPropertyBool(key, false);
  }

  public boolean getPropertyBool(String key, boolean def) {
    String val = props.getProperty(key, String.valueOf(def));
    boolean ret = def;

    if ( val != null ) {
      ret = (new Boolean(val)).booleanValue();
    }

    return ret;
  }

  public void setPropertyBool(String key, boolean val) {
    props.setProperty(key, String.valueOf(val));
  }

  public Object remove(String key) {
    return props.remove(key);
  }

  public void reset() {
    props.clear();
  }

   public void loadPropertiesFile(String fname)
	throws IOException
	{
	   props.load(new FileInputStream(fname));
	}

   public void savePropertiesFile(String fname)
	throws IOException
	{
	   props.store(
		   new FileOutputStream(fname),
		   "JMars Initialization Properties");
	}

  public boolean setWindowPosition(Window win) {

    boolean ret = false;

    if (  props.size() > 0 ) {
        //try and get the  location and size of window
        int x = getPropertyInt(win.getClass().getName() + ".location.x", 0);
        int y = getPropertyInt(win.getClass().getName() + ".location.y", 0);
        int w = getPropertyInt(win.getClass().getName() + ".size.width", 0);
        int h = getPropertyInt(win.getClass().getName() + ".size.height", 0);

        if ( w > 0 && h > 0 ) {

          win.setLocation(x,y);
          win.setSize(w,h);

          ret = true;
        }

      }

      return ret;
  }

  public boolean wasWindowShowing(Window win) {

    return (new Boolean(getProperty(win.getClass().getName() + ".visible", String.valueOf(true)))).booleanValue();

  }

  public void saveWindowPosition(Window win) {

        //try and store location and size of windows
        setPropertyInt(win.getClass().getName() + ".location.x", win.getLocation().x);
        setPropertyInt(win.getClass().getName() + ".location.y", win.getLocation().y);
        setPropertyInt(win.getClass().getName() + ".size.width", win.getSize().width);
        setPropertyInt(win.getClass().getName() + ".size.height", win.getSize().height);
        setProperty(win.getClass().getName() + ".visible", String.valueOf(win.isVisible()));
  }

    public Object loadUserObject(String key)
    {
        Object oData = null;

        try {

          String objStr = getProperty(key);
          ByteArrayInputStream bArray = new ByteArrayInputStream(objStr.getBytes("ISO-8859-1"));
          ObjectInputStream fin = new ObjectInputStream(bArray);
          oData =  fin.readObject();
          fin.close();
          bArray.close();

        }
        catch (Exception e) {
             log.println("Error: " + e.getMessage());
        }

        return  oData;
    }

    public void saveUserObject( String key,  Serializable  dataObject)
    {

        try {

          ByteArrayOutputStream bArray = new ByteArrayOutputStream();
          ObjectOutputStream fout = new ObjectOutputStream(bArray);
          fout.writeObject(dataObject);
          fout.flush();
          fout.close();
          bArray.close();

          String objStr = new String(bArray.toByteArray(), "ISO-8859-1");
          setProperty(key, objStr);

        }
        catch (Exception e) {
                System.out.println(e.toString());
             System.out.println("Error saving key: " + key + " " + e.getMessage());
        }
    }


}
