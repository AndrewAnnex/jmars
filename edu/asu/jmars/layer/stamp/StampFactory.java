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


package edu.asu.jmars.layer.stamp;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ObjectInputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import edu.asu.jmars.layer.LViewFactory;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.SerializedParameters;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;

/**
 * Factory for creating and reloading stamp layers in JMARS. In days of old,
 * there were subclasses of this class with instrument-specific names that
 * provided the constructor on this class with the properties it needed to build
 * a view settings object. Those subclasses have been done away with and left in
 * place for compatibility with old session files. This is now the only stamp
 * factory for creating new layers.
 */
public class StampFactory extends LViewFactory {
	private static DebugLog log = DebugLog.instance();
	
	private static Map<String,String[]> layerTypes;
	private static Map<String,String[]> getLayerTypes() {
		if (layerTypes == null) {
			String body=Config.get("bodyname", "Mars");
			String urlStr = StampLayer.stampURL+"InstrumentFetcher?planet="+body+"&format=JAVA"+StampLayer.versionStr;
			try {
				// Connect timeout and SO_TIMEOUT of 10 seconds
				URL url = new URL(urlStr);
				URLConnection conn = url.openConnection();
				conn.setConnectTimeout(10*1000);
				conn.setReadTimeout(10*1000);
				ObjectInputStream ois = new ObjectInputStream(url.openStream());
				layerTypes = (HashMap<String, String[]>) ois.readObject();
				ois.close();
			} catch (Exception e) {
				log.aprintln("Error retrieving list of stamps");
				log.aprintln(e);
				layerTypes = new LinkedHashMap<String,String[]>();
			}
		}
		return layerTypes;
	}
	
	/**
	 * Returns an instance of this StampFactory for just those classes that
	 * could have been used to identify a serialized layer that this class
	 * should be able to handle
	 */
	public static synchronized StampFactory createAdapterFactory(String oldFactoryClass) {
		if (oldFactoryClass.equals("edu.asu.jmars.layer.stamp.MocStampFactory"))
			return new StampFactory();
		if (oldFactoryClass.equals("edu.asu.jmars.layer.stamp.CTXStampFactory"))
			return new StampFactory();
		if (oldFactoryClass.equals("edu.asu.jmars.layer.stamp.HiRISEStampFactory"))
			return new StampFactory();
		if (oldFactoryClass.equals("edu.asu.jmars.layer.stamp.VikingStampFactory"))
			return new StampFactory();
		if (oldFactoryClass.equals("edu.asu.jmars.layer.stamp.HRSCStampFactory"))
			return new StampFactory();
		if (oldFactoryClass.equals("edu.asu.jmars.layer.stamp.ThemisBtrStampFactory"))
			return new StampFactory();
		return null;
	}
	
    public StampFactory() {
    	super("Stamps", "Access to individual images from a variety of instruments");
    }

	/**
	 * Returns the 'loading' menu immediately, gets on another thread to
	 * retrieve the instrument types, and gets back on the AWT thread to update
	 * the original menu.
	 */
	protected JMenuItem[] createMenuItems(final Callback callback) {
		final JMenu menu = new JMenu("Loading Stamps");
		menu.setEnabled(false);
		Thread thread = new Thread(new Runnable() {
			public void run() {
				final Map<String,String[]> types = getLayerTypes();
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						for (final String instrument: types.keySet()) {
							final String[] initialColumns = types.get(instrument);
							JMenuItem type = new JMenuItem(instrument + " Stamps");
							type.setToolTipText("Outlines of " + instrument + " observation polygons");
							type.addActionListener(new ActionListener() {
								public void actionPerformed(ActionEvent e) {
									addLView(callback, instrument, initialColumns);
								}
							});
							menu.add(type);
						}
						menu.setText("Stamps");
						menu.setEnabled(true);
					}
				});
			}
		});
		thread.setName("InstrumentFetcher");
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.setDaemon(true);
		thread.start();
		return new JMenuItem[]{menu};
	}
	
	/**
	 * Causes a dialog to be shown that the user fills out, and if they hit okay
	 * a new stamp layer with the user's settings and the initial columns given
	 * here is added
	 */
	private void addLView(Callback callback, String instrument, String[] initialColumns) {
		AddLayerWrapper wrapper = new AddLayerWrapper(instrument);
		AddLayerDialog dialog = new AddLayerDialog(wrapper);
		dialog.setVisible(true);
		if (!dialog.isCancelled()) {
			StampLayerSettings newSettings = new StampLayerSettings();
			if (dialog.initialName.getText().trim().length()>0) {
				newSettings.name = dialog.initialName.getText().trim();
			} else {
				newSettings.name = instrument + " Stamps";
			}
			newSettings.instrument = instrument;
			newSettings.queryStr = wrapper.getQuery();
			newSettings.initialColumns = initialColumns;
			newSettings.unsColor = dialog.initialColor.getColor();
			newSettings.filColor = new Color(dialog.initialColor.getColor().getRGB() & 0xFFFFFF, true);
			StampLayer layer = new StampLayer(newSettings);
			StampLView view = new StampLView(StampFactory.this, layer, wrapper);
			layer.setViewToUpdate(view);
			layer.setQuery(wrapper.getQuery());
			callback.receiveNewLView(view);
		}
	}
	
	/** Stamp layers are not added by default */
    public Layer.LView createLView() {
        return null;
    }
    
    public Layer.LView recreateLView(SerializedParameters parmBlock) {
        StampLView view = null;
        
        log.println("recreateLView called");
        
        if (parmBlock != null &&
            parmBlock instanceof StampLayerSettings)
        {
            StampLayerSettings settings = (StampLayerSettings) parmBlock;
			settings.queryStr = settings.queryStr.replaceAll(".*StampFetcher\\?",StampLayer.stampURL+"StampFetcher?");
            StampLayer stampLayer = new StampLayer(settings);
            view = new StampLView(this, stampLayer, null);
        	stampLayer.setViewToUpdate(view);
            
            log.println("successfully recreated StampLView");
        }
        
        return view;
    }
}
