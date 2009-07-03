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


package edu.asu.jmars.layer;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.Layer.LView;

/**
 * Manages the load/save layer dialogs. The outer class is there to hold the
 * data they must share. Each dialog's inner class constructor will
 * automatically show the dialog or not as required.
 */
public class LoadSaveLayerDialogs {
	/** Pixels of space to leave around components */
	private final int gap = 4;
	/** Top level frame to anchor everything to */
	private final Frame frame = Main.mainFrame;
	/** The file chooser, shared between load/save dialogs */
    private JFileChooser savedLayerChooser;
    /** Returns the file chooser, creating it the first time */
    private JFileChooser getSavedLayerChooser() {
    	if (savedLayerChooser == null) {
        	savedLayerChooser = new JFileChooser();
        	savedLayerChooser.setFileFilter(new JlfFilter());
    	}
    	return savedLayerChooser;
    }
    
    /** Filters file chooser selections down to readable files that have the .jlf extension */
	private final class JlfFilter extends FileFilter {
		public static final String EXT = ".jlf";
		public boolean accept(File f) {
			return f.isDirectory() || (f.isFile() && f.canRead() && f.getName().toLowerCase().endsWith(EXT));
		}
		public String getDescription() {
			return "JMARS Layer File (*" + EXT + ")";
		}
	}
    
	/** Returns the selected file on 'fc', with the file format suffix added if necessary */
	private static final File getSelectedFile(JFileChooser fc) {
		File f = fc.getSelectedFile();
		String name = f.getAbsolutePath();
		if (fc.getFileFilter() instanceof JlfFilter && !name.toLowerCase().trim().endsWith(JlfFilter.EXT)) {
			name = name + JlfFilter.EXT;
		}
		return new File(name);
	}
	
    /** Lets a user pick which layers to save and assigns a .jlf file to them */
    public class SaveLayersDialog {
		private final JDialog dlg = new JDialog(frame, "Save Layers...", true);
		private final Map<JCheckBox,LView> checked = new LinkedHashMap<JCheckBox,LView>();
		/**
		 * Shows the layer chooser dialog right away, and then shows the file
		 * chooser so the user can pick the output file
		 */
		public SaveLayersDialog() {
			JButton ok = new JButton("Save");
			ok.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					saveFile();
				}
			});
			
			Box bottom = Box.createHorizontalBox();
			bottom.add(Box.createHorizontalStrut(gap));
			bottom.add(Box.createHorizontalGlue());
			bottom.add(ok);
			bottom.add(Box.createHorizontalStrut(gap));
			
			Box v = Box.createVerticalBox();
			v.add(Box.createVerticalStrut(gap));
			// we get the list of lviews for the main window
			List<LView> views = new ArrayList<LView>(Main.testDriver.mainWindow.viewList);
			Collections.reverse(views);
			for (LView view: views) {
				String name;
				if (Main.getLManager() instanceof LManager2) {
					name = ((LManager2)Main.getLManager()).getUniqueName(view);
				} else {
					name = view.getName();
				}
				JCheckBox cb = new JCheckBox(name);
				checked.put(cb, view);
				Box hbox = Box.createHorizontalBox();
				hbox.add(Box.createHorizontalStrut(gap));
				hbox.add(cb);
				hbox.add(Box.createHorizontalGlue());
				hbox.add(Box.createHorizontalStrut(gap));
				v.add(hbox);
				v.add(Box.createVerticalStrut(gap));
			}
			v.add(bottom);
			v.add(Box.createVerticalStrut(gap));
			
			dlg.add(v);
			dlg.pack();
			dlg.setVisible(true);
    	}
		/** Saves the file immediately */
		private void saveFile() {
			List<SavedLayer> layers = new ArrayList<SavedLayer>();
			for (JCheckBox cb: checked.keySet()) {
				if (cb.isSelected()) {
					LView view = checked.get(cb);
					// we determine visibility 
					layers.add(new SavedLayer(
						view.originatingFactory.getClass().getName(),
						view.getName(),
						view.getInitialLayerData(),
						view.getViewSettings(),
						view.getAlpha(),
						view.isVisible(),
						view.getChild() != null ? view.getChild().isVisible() : false));
				}
			}
			if (!layers.isEmpty()) {
				JFileChooser fc = getSavedLayerChooser();
				while (JFileChooser.APPROVE_OPTION == fc.showSaveDialog(frame)) {
					File f = getSelectedFile(fc);
					if (!f.exists() || JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
							frame, "File exists, overwrite?", "File exists", JOptionPane.YES_NO_OPTION)) {
						XStream xstream = new XStream() {
							protected boolean useXStream11XmlFriendlyMapper() {
								return true;
							}
						};
						try {
							xstream.toXML(layers, new FileOutputStream(f));
							dlg.dispose();
							break;
						} catch (FileNotFoundException e1) {
							JOptionPane.showMessageDialog(
								Main.getLManager(),
								"Error saving layers: " + e1,
								"Error saving layers",
								JOptionPane.ERROR_MESSAGE);
							e1.printStackTrace();
						}
					}
				}
			}
		}
    }
    
    /** Lets a user pick a .jlf file to load layers from, and which layers in the file to load */
    public class LoadLayersDialog {
    	private List<SavedLayer> layers;
    	private Map<JCheckBox,SavedLayer> checked = new LinkedHashMap<JCheckBox,SavedLayer>();
		private JDialog dialog = new JDialog(frame, "Load Layers...", true);
    	public LoadLayersDialog() {
			JFileChooser fc = getSavedLayerChooser();
			if (JFileChooser.APPROVE_OPTION == fc.showOpenDialog(dialog)) {
				File file = getSelectedFile(fc);
				if (file.exists() && file.canRead()) {
					XStream xstream = new XStream() {
						protected boolean useXStream11XmlFriendlyMapper() {
							return true;
						}
					};
					try {
						Object data = xstream.fromXML(new FileInputStream(file));
						if (data instanceof List && ((List)data).size() > 0 && ((List)data).get(0) instanceof SavedLayer) {
							layers = (List<SavedLayer>)data;
							showDialog();
						}
					} catch (Exception e) {
						e.printStackTrace();
						JOptionPane.showMessageDialog(
							frame,
							"Error '" + e.getMessage() + "' with file " + file.getAbsolutePath(),
							"Error loading file",
							JOptionPane.ERROR_MESSAGE);
					}
				}
			}
    	}
    	private void showDialog() {
    		JButton load = new JButton("Load Selected");
    		load.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					loadLayers();
				}
    		});
    		
    		Box v = Box.createVerticalBox();
    		v.add(Box.createVerticalStrut(gap));
    		for (SavedLayer layer: layers) {
    			JCheckBox cb = new JCheckBox(layer.layerName);
    			cb.setSelected(true);
    			checked.put(cb, layer);
    			Box cbbox = Box.createHorizontalBox();
    			cbbox.add(Box.createHorizontalStrut(gap));
    			cbbox.add(cb);
    			cbbox.add(Box.createHorizontalGlue());
    			cbbox.add(Box.createHorizontalStrut(gap));
    			v.add(cbbox);
        		v.add(Box.createVerticalStrut(gap));
    		}
    		
    		Box bottom = Box.createHorizontalBox();
    		bottom.add(Box.createHorizontalStrut(gap));
    		bottom.add(Box.createHorizontalGlue());
    		bottom.add(load);
    		bottom.add(Box.createHorizontalStrut(gap));
    		
    		v.add(bottom);
    		v.add(Box.createVerticalStrut(gap));
    		
    		dialog.add(v);
    		dialog.pack();
    		dialog.setVisible(true);
    	}
    	private void loadLayers() {
    		List<JCheckBox> layersOrdered = new ArrayList<JCheckBox>(checked.keySet());
    		Collections.reverse(layersOrdered);
    		for (JCheckBox cb: layersOrdered) {
    			if (cb.isSelected()) {
    				SavedLayer layer = checked.get(cb);
    				
					LViewFactory factory = LViewFactory.getFactoryObject(layer.factoryName);
					if (factory == null) {
						JOptionPane.showMessageDialog(
							frame, "Unknown factory " + layer.factoryName,
							"Error loading saved layer",
							JOptionPane.ERROR_MESSAGE);
						continue;
					}
					
					LView lview = factory.recreateLView(layer.createParms);
					if (lview == null) {
						JOptionPane.showMessageDialog(
							frame, "Unable to recreate layer " + layer.layerName,
							"Error loading saved layer",
							JOptionPane.ERROR_MESSAGE);
						continue;
					}
					
					lview.setName(layer.layerName);
					
					if (layer.viewParms != null) {
						lview.setViewSettings(layer.viewParms);
					}
					
					Main.getLManager().receiveNewLView(lview);
					
					lview.setAlpha(layer.alpha);
					lview.setVisible(layer.showMain);
					if (lview.getChild() != null) {
						lview.getChild().setVisible(layer.showPanner);
					}
					Main.getLManager().updateVis();
					
					cb.setSelected(false);
				}
			}
    		
    		// leave the dialog open if any of the layers were not saved
    		for (JCheckBox cb: checked.keySet()) {
    			if (cb.isSelected()) {
    				return;
    			}
    		}
    		
    		dialog.dispose();
    	}
    }
}
