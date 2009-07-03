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


package edu.asu.jmars.layer.shape2;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;

import com.thoughtworks.xstream.XStream;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.util.features.Feature;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.FeatureMouseHandler;
import edu.asu.jmars.layer.util.features.FeatureProvider;
import edu.asu.jmars.layer.util.features.FeatureProviderNomenclature;
import edu.asu.jmars.layer.util.features.FeatureSQL;
import edu.asu.jmars.layer.util.features.FeatureTableAdapter;
import edu.asu.jmars.layer.util.features.FeatureTableModel;
import edu.asu.jmars.layer.util.features.FeatureUtil;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.ScriptFileChooser;
import edu.asu.jmars.layer.util.features.SingleFeatureCollection;
import edu.asu.jmars.layer.util.features.Style;
import edu.asu.jmars.layer.util.features.Styles;
import edu.asu.jmars.layer.util.features.ZOrderMenu;
import edu.asu.jmars.layer.util.filetable.FileTable;
import edu.asu.jmars.layer.util.filetable.FileTableModel;
import edu.asu.jmars.swing.STable;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;
import edu.asu.jmars.util.stable.ColorCellEditor;
import edu.asu.jmars.util.stable.FilteringColumnModel;

public class ShapeFocusPanel extends FocusPanel {
	final private static DebugLog log = DebugLog.instance(); 
	private static final String defaultProviderKey = "shape.filefactory.default";
	
	public static final String saveSelectedFilesAsActionName = "Save Selected Files As";
	public static final String saveSelectedFeaturesAsActionName = "Save Selected Features As";
	public static final String saveAllFeaturesAsActionName = "Save All Features As";
	
    // File and feature maintainance Menu Items, where one may load, save, yes and even
    // delete files and features.
    JMenuItem deleteSelectedFilesMenuItem            = new JMenuItem("Delete Selected Files");
    JMenuItem saveSelectedFilesMenuItem              = new JMenuItem("Save Selected Files");
    JMenuItem saveSelectedFilesToFileAsMenuItem      = new JMenuItem(saveSelectedFilesAsActionName);
    JMenuItem saveSelectedFeaturesToFileMenuItem     = new JMenuItem(saveSelectedFeaturesAsActionName);
    JMenuItem saveAllFeaturesToFileAsMenuItem        = new JMenuItem(saveAllFeaturesAsActionName);
    
    JMenuItem   featureCommandMenuItem         = new JMenuItem("Edit Script");
    JMenuItem   featureLoadScriptsMenuItem     = new JMenuItem("Load & Run Script");
    
    JPanel         featurePanel; // FeatureTable container
    JScrollPane    featureTableScrollPane;
    JPanel         filePanel; // FileTable container
    JScrollPane     fileTableScrollPane;
    JSplitPane     splitPane; // SplitPane containing featurePanel and filePanel
    
    List           openDialogList = new ArrayList(); // List of popup dialogs currently active

    
    FileChooser loadSaveFileChooser;
    ScriptFileChooser shapeScriptFileChooser;
    
    ShapeLView     shapeLView;
    ShapeLayer     shapeLayer;
    
    private STable featureTable;
	private final StylesStore stylesStore = new StylesStore();
	
	public ShapeFocusPanel(ShapeLView parent) {
		super(parent);
		setLayout(new BorderLayout());
		
		shapeLView = parent;
		shapeLayer = (ShapeLayer)shapeLView.getLayer();
		loadSaveFileChooser = new FileChooser();
		
		// use jmars.config key to decide if we set up the chooser in the home
		// or working directories
		String chooserPathType = Config.get("shape.chooser.path", "home");
		if (chooserPathType.trim().toLowerCase().equals("working")) {
			loadSaveFileChooser.setStartingDir(new File("."));
		} else if (chooserPathType.trim().toLowerCase().equals("home")) {
			loadSaveFileChooser.setStartingDir(new File(Main.getUserHome()));
		}
		
		List<FeatureProvider> fileProviders = (List<FeatureProvider>)shapeLayer.getProviderFactory().getFileProviders();
		FileFilter selected = null;
		String defaultProviderClass = Config.get(defaultProviderKey);
		for (FeatureProvider fp: fileProviders) {
			FileFilter f = loadSaveFileChooser.addFilter(fp);
			if (fp.getClass().getName().equals(defaultProviderClass)) {
				selected = f;
			}
		}
		if (selected != null) {
			loadSaveFileChooser.setFileFilter(selected);
		}
		
		shapeScriptFileChooser = new ScriptFileChooser();

	    // build menu bar.
	    add(getMenuBar(),BorderLayout.NORTH);

	    // tie menus to corresponding actions
	    initMenuActions();

		// TODO: the mfc should be produced by the layer, not the file table!
		FeatureTableAdapter ft = new FeatureTableAdapter(shapeLayer.getFeatureCollection(), shapeLayer.selections, shapeLayer.getHistory());
		featureTable = ft.getTable();

		// add the column dialog to the list of open dialogs so its disposed
		// when the layer is removed.
		openDialogList.add (((FilteringColumnModel)featureTable.getColumnModel()).getColumnDialog());

	    // add FileTable and the merged FeatureTable.
	    filePanel = buildFilePanel();
	    featurePanel = buildFeaturePanel();
	    splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filePanel, featurePanel);
	    splitPane.setPreferredSize(new Dimension(400,500));
	    splitPane.setResizeWeight(.5);
	    splitPane.setBorder(new CompoundBorder(BorderFactory.createEmptyBorder(10,0,10,0),
	    					   BorderFactory.createBevelBorder(BevelBorder.LOWERED)) );
	    add(splitPane, BorderLayout.CENTER);

		installContextMenuOnFeatureTable(featureTable, featureTableScrollPane);
		installDoubleClickHandlerOnFileTable(shapeLayer.getFileTable());
		installContextMenuOnFileTable(shapeLayer.getFileTable(), fileTableScrollPane);
	}
	
	private void installContextMenuOnFeatureTable(
			final STable featureTable,
			final JScrollPane featureTableScrollPane)
	{
		final FeatureCollection fc = ((FeatureTableModel)featureTable.getUnsortedTableModel()).getFeatureCollection();
		final JMenuItem deleteSelectedFeaturesMenuItem = new JMenuItem(new DelSelectedFeaturesAction(fc));
		final JMenuItem centerMenuItem = new JMenuItem(new CenterOnFeatureAction(fc));
	    final JMenuItem multiEditMenuItem = new JMenuItem(new MultiEditAction(featureTable));
	    final JMenuItem saveSelectedFeaturesToFileMenuItem = new JMenuItem(new SaveAsAction("Save Selected Features As", fc));


		featureTable.addMouseListener(new MouseAdapter(){
			public void mouseClicked(MouseEvent e){
				if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1){
					int[] selectedRows = featureTable.getSelectedRows();
					
					if (selectedRows.length == 0)
						return;
					
					final JPopupMenu popup = new JPopupMenu();
					popup.add(centerMenuItem);

					// Install Z-order menu for the main FeatureTable in the FocusPanel only.
					if (getFeatureTable() == featureTable)
						popup.add(new ZOrderMenu("Z Order", fc, shapeLayer.selections));
					
					if (selectedRows.length > 1)
						popup.add(multiEditMenuItem);
					
					popup.add(saveSelectedFeaturesToFileMenuItem);
					popup.add(deleteSelectedFeaturesMenuItem);

					// bring up the popup, but be sure it goes to the cursor position of 
					// the PANEL, not the position in the table.  If we don't do this, then
					// for a large table, the popup will try to draw itself beyond the 
					// screen.
					Point2D p = getScrollPaneRelativeCoordinates(e.getPoint(), featureTableScrollPane);
					popup.show( featureTableScrollPane, (int)p.getX(), (int)p.getY());
				}
			}
			public void mousePressed(MouseEvent e){
				if (SwingUtilities.isRightMouseButton(e))
					((MultiEditAction)multiEditMenuItem.getAction()).setTableMouseEvent(e);
			}
		});
	}
	
	/**
	 * Registers the specified dialog with the FocusPanel so that the FocusPanel is able
	 * to destroy these dialogs on a FocusPanel dispose.
	 * 
	 * @param dialog
	 */
	protected void registerDialogForAutoCleanup(final JDialog dialog){
		openDialogList.add(dialog);
		dialog.addWindowListener(new WindowAdapter(){
			public void windowClosed(WindowEvent e){
				openDialogList.remove(dialog);
			}
		});
	}
	
	/**
	 * Compute the specified table coordiantes to scroll-pane viewport 
	 * relative coordinates. This is useful for displaying popup menus on a 
	 * JTable which is enclosed in a JScrollPane where the mouse click
	 * point has been received via a MouseEvent.
	 * 
	 * @param p Point in the JTable coordinates.
	 * @param sp Scrollpane containing the JTable.
	 * @return Point in JScrollPane viewport relative coordinates.
	 */
	private Point2D getScrollPaneRelativeCoordinates(Point2D p, JScrollPane sp){
		JViewport vp = sp.getViewport();
		return new Point2D.Double(
				p.getX() - vp.getViewPosition().x,
				p.getY() - vp.getViewPosition().y);
	}
	
	private void installDoubleClickHandlerOnFileTable(final FileTable fileTable){
		fileTable.addMouseListener(new MouseAdapter(){
			public void mouseClicked(MouseEvent e){
				// if a row in the FileTable is left-double-clicked, a new
				// dialog containing the row's
				// FeatureTable is displayed.
				if (SwingUtilities.isLeftMouseButton(e)	&&
					e.getClickCount() == 2 && 
					fileTable.columnAtPoint(e.getPoint()) > 0){
					
					List fcl = fileTable.getSelectedFeatureCollections();
					for(Iterator li=fcl.iterator(); li.hasNext(); ){
						FeatureCollection fc = (FeatureCollection)li.next();
						final FeatureTableAdapter fta = new FeatureTableAdapter(fc, shapeLayer.selections, shapeLayer.getHistory());
						final STable ft = fta.getTable();
						final JDialog columnDialog = ((FilteringColumnModel)ft.getColumnModel()).getColumnDialog();
						// add the column dialog to the list of open dialogs so it's disposed
						// when the layer is removed.
						registerDialogForAutoCleanup (columnDialog);
						String title = (fc.getProvider() == null)?
								FileTableModel.NULL_PROVIDER_NAME:
									fc.getFilename();
						final JDialog ftDialog = new JDialog((JFrame)null, title, false);
						JPanel ftPanel = new JPanel(new BorderLayout());
						ftPanel.setSize(new Dimension(400,400));
						JScrollPane ftScrollPane = new JScrollPane(ft);
						installContextMenuOnFeatureTable(ft, ftScrollPane);
						ftPanel.add(ftScrollPane, BorderLayout.CENTER);
						ftDialog.setContentPane(ftPanel);
						ftDialog.pack();
						ftDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
						ftDialog.addWindowListener(new WindowAdapter(){
							public void windowClosed(WindowEvent e) {
								// Ask FeatureTable to clear its listeners.
								fta.disconnect();
								// Remove the popup table now, so don't do it
								// when removing the layer
								columnDialog.dispose();
							}
						});
						registerDialogForAutoCleanup(ftDialog);
						ftDialog.setVisible(true);
					}
				}
			}
		});
	}
	
	private void installContextMenuOnFileTable(
			final FileTable fileTable,
			final JScrollPane fileTableScrollPane)
	{
		fileTable.addMouseListener(new MouseAdapter(){
			public void mouseClicked(MouseEvent e){
				// if the fileTable is right-clicked, bring up a right-click menu that 
				// allows for saving of filetable featuretables.
				if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1){
				    // if we had NO selected rows, do nothing.
					int[] selectedRows = fileTable.getSelectedRows();
					if (selectedRows == null) 
						return;
					
					JPopupMenu popup = new JPopupMenu();
					popup.add(saveSelectedFilesMenuItem);
					popup.add(saveSelectedFilesToFileAsMenuItem);
					popup.add(deleteSelectedFilesMenuItem);
					
					Point2D p = getScrollPaneRelativeCoordinates(e.getPoint(), fileTableScrollPane);
					popup.show(fileTable, (int)p.getX(), (int)p.getY());			    
				}
			}
		});
	}

	private JPanel  buildFilePanel() {
	    JPanel filePanel = new JPanel();
	    filePanel.setLayout( new BorderLayout());
	    filePanel.setBorder( BorderFactory.createTitledBorder("Files"));
	    fileTableScrollPane = new JScrollPane(shapeLayer.getFileTable()) ;
	    filePanel.add(fileTableScrollPane, BorderLayout.CENTER);
	    return filePanel;
	}
	
	private JPanel  buildFeaturePanel(){
	    JPanel featurePanel = new JPanel();
	    featurePanel.setLayout( new BorderLayout());
	    featurePanel.setBorder( BorderFactory.createTitledBorder("Features"));
	    featureTableScrollPane = new JScrollPane(featureTable); 
	    featurePanel.add(featureTableScrollPane, BorderLayout.CENTER);
	    return featurePanel;
	}
	
	/**
	 * Bind actions to menu items in ShapeFocusPanel.
	 */
	private void initMenuActions(){
		deleteSelectedFilesMenuItem.addActionListener(new DelSelectedFilesActionListener());
		saveSelectedFeaturesToFileMenuItem.addActionListener(new SaveAsAction(saveSelectedFeaturesAsActionName, shapeLayer.getFeatureCollection()));
		saveSelectedFilesToFileAsMenuItem.addActionListener(new SaveAsAction(saveSelectedFilesAsActionName, null));
		saveAllFeaturesToFileAsMenuItem.addActionListener(new SaveAsAction(saveAllFeaturesAsActionName, null));
		
		saveSelectedFilesMenuItem.addActionListener(new SaveSelectedFilesActionListener());
		
		featureCommandMenuItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				shapeLView.getFeatureMouseHandler().setMode(FeatureMouseHandler.SELECT_FEATURE_MODE);

				// The dialog what lets users enter in SQL commands.
				JDialog commandDialog = new CommandDialog(
						shapeLayer.getFeatureCollection(), shapeLayer.getHistory(),
						shapeLayer, (Frame)ShapeFocusPanel.this.getTopLevelAncestor());
				commandDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
				registerDialogForAutoCleanup(commandDialog);
				commandDialog.setVisible(true);
			}
		});
		
		featureLoadScriptsMenuItem.addActionListener(new LoadScriptActionListener());
	}
	
	/**
	 * Implements "Load Files" action.
	 * 
	 * @author saadat
	 */
	private class LoadActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			final File [] f = loadSaveFileChooser.chooseFile("Load");

		    // If no file was selected, quit.
		    if (f == null)
				return;
		    
	    	FeatureProvider fp = loadSaveFileChooser.getFeatureProvider();
	    	final List<ShapeLayer.LoadData> sources = new ArrayList<ShapeLayer.LoadData>();
	    	for (int i = 0; i < f.length; i++) {
	    		sources.add(new ShapeLayer.LoadData(fp, f[i].getAbsolutePath()));
	    	}
	    	Config.set(defaultProviderKey, fp.getClass().getName());
			shapeLayer.loadSources(sources);
		}
	}
	
	/**
	 * Implements loading for an array of FeatureProviders. If the instance of a
	 * provider needs a name, it needs to be set prior to sending it to this
	 * class.
	 */
	private class CustomProviderHandler implements ActionListener {
		FeatureProvider fp;
		public CustomProviderHandler(FeatureProvider fp) {
			this.fp = fp;
		}
		public void actionPerformed(ActionEvent e) {
			shapeLayer.loadSources(Arrays.asList(new ShapeLayer.LoadData(fp, null)));
		}
	}
	
	/**
	 * Implements various "Save As" actions. This code is also invoked as a
	 * subordinate of the "Save" action. Such a situation occurs when the 
	 * "Save" action cannot save all Features.
	 * 
	 * @author saadat
	 */
	private class SaveAsAction extends AbstractAction {
		final FeatureCollection _fc;
		
		public SaveAsAction(String name, FeatureCollection _fc){
			super(name);
			this._fc = _fc;
		}
		
		public SaveAsAction(FeatureCollection fc){
			this("Save As", fc);
		}
		
		public void actionPerformed(ActionEvent e){
		    File [] f = loadSaveFileChooser.chooseFile(e.getActionCommand());

		    if (f == null || f.length == 0)
		    	return;

		    if (f.length > 1){
		    	JOptionPane.showMessageDialog(loadSaveFileChooser,
		    			"Cannot save to multiple files.",
		    			"Select one file only.",
		    			JOptionPane.ERROR_MESSAGE);
		    	return;
		    }

		    String fileName = f[0].getAbsolutePath();

			// The runningAsSubordinate flag is set to true if this SaveAs action 
			// resulted from a currently progressing Save action.
			boolean runningAsSubordinate = false;
			FeatureCollection fc = new SingleFeatureCollection();
			// fc.setFilename(_fc.getFilename());
			if (saveSelectedFeaturesAsActionName.equals(e.getActionCommand())){
				fc.addFeatures(shapeLayer.selections);
			}
			else if (saveSelectedFilesAsActionName.equals(e.getActionCommand())){
				List fcl = shapeLayer.getFileTable().getSelectedFeatureCollections();
				for(Iterator i=fcl.iterator(); i.hasNext(); )
					fc.addFeatures(((FeatureCollection)i.next()).getFeatures());
			}
			else if (saveAllFeaturesAsActionName.equals(e.getActionCommand())){
				List fcl = shapeLayer.getFileTable().getFileTableModel().getAll();
				for(Iterator i=fcl.iterator(); i.hasNext(); )
					fc.addFeatures(((FeatureCollection)i.next()).getFeatures());
			}
			else if (e.getSource() instanceof SaveSelectedFilesActionListener){
				fc = _fc;
				runningAsSubordinate = true;
			}
			else {
				log.aprintln("UNKNOWN actionCommand!");
				return;
			}

			FeatureProvider fp = (FeatureProvider)loadSaveFileChooser.getFeatureProvider();

			if (!fp.isRepresentable(fc)){
				int option = JOptionPane.showConfirmDialog(Main.getLManager(),
						"Type does not support saving all the Features. Continue?",
						"Continue?",
						JOptionPane.YES_NO_OPTION);
				if (option != JOptionPane.OK_OPTION)
					return;
			}

			File[] files = fp.getExistingSaveToFiles(fc, fileName);
		    if (files.length > 0) {
		    	String[] fileNames = new String[] {(String)files[0].getAbsolutePath()};
				int option = JOptionPane.showConfirmDialog(
				   Main.getLManager(),fileNames,
				   "File exists. Overwrite?", 
				   JOptionPane.OK_CANCEL_OPTION);
				if (option != JOptionPane.OK_OPTION)
					return;
		    }

			// Mark a new history frame
		    if (!runningAsSubordinate)
		    	shapeLayer.getHistory().mark();

		    ShapeLayer.LEDState led = null;
		    try {
		    	if (!runningAsSubordinate)
		    		shapeLayer.begin(led = new ShapeLayer.LEDStateFileIO());
		    	fp.save(fc,fileName);
		    }
		    catch(RuntimeException ex){
		    	log.aprintln("While saving "+fileName+" got: "+ex.getMessage());
		    	ex.printStackTrace();
		    	if (!runningAsSubordinate)
		    		JOptionPane.showMessageDialog(Main.getLManager(), "Unable to save "+fileName);
		    	else
		    		throw ex;
		    }
		    finally {
		    	if (!runningAsSubordinate)
		    		shapeLayer.end(led);
		    }
		}
	}
	
	/**
	 * Implements the "Save Selected Files" action.
	 * 
	 * @author saadat
	 */
	private class SaveSelectedFilesActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e){
			FileTable ft = shapeLayer.getFileTable();
			List<String> unsavable = new ArrayList<String>();
			List<FeatureCollection> saveable = new ArrayList<FeatureCollection>();
			List<?> fcl = ft.getSelectedFeatureCollections();
			for(Iterator<?> i=fcl.iterator(); i.hasNext(); ){
				FeatureCollection fc = (FeatureCollection)i.next();
				if (fc.getProvider() == null)
					unsavable.add(FileTableModel.NULL_PROVIDER_NAME);
				else if (fc.getProvider() instanceof FeatureProviderNomenclature)
					unsavable.add(fc.getProvider().getDescription());
				else
					saveable.add(fc);
			}

			if (!saveable.isEmpty()){
				// Don't mark a history frame as that frame only contains the touched flag
				//shapeLayer.getHistory().mark();

				ShapeLayer.LEDState led;
				shapeLayer.begin(led = new ShapeLayer.LEDStateFileIO());
				try {
					for(FeatureCollection currentFc: saveable) {
						boolean produceSaveAs = false;
						FeatureProvider fp = currentFc.getProvider();

						if (!fp.isRepresentable(currentFc)){
							int option = JOptionPane.showOptionDialog(
								ShapeFocusPanel.this,
								"The save operation on "+currentFc.getFilename() +
									" will not save all Features.",
								"Warning!",
								JOptionPane.YES_NO_CANCEL_OPTION,
								JOptionPane.QUESTION_MESSAGE,
								null,
								new String[]{ "Continue", "Save As", "Cancel"},
								"Continue");

							switch(option){
								case 0:	produceSaveAs = false; break;
								case 1:	produceSaveAs = true; break;
								default: continue;
							}
						}
						
						try {
							if (produceSaveAs){
								(new SaveAsAction(currentFc)).actionPerformed(
										new ActionEvent(SaveSelectedFilesActionListener.this,
												ActionEvent.ACTION_PERFORMED, "Save As"));
							}
							else {
								// Save the FeatureCollection
								currentFc.getProvider().save(currentFc, currentFc.getFilename());
								// If saved properly, reset the dirty marker
								ft.getFileTableModel().setTouched(currentFc,false);
							}
						}
						catch(Exception ex){
							log.println("While processing "+getFcName(currentFc)+" caught exception: "+ex.getMessage());
							unsavable.add(getFcName(currentFc));
						}
					}
					if (!unsavable.isEmpty()) {
						JOptionPane.showMessageDialog(ShapeFocusPanel.this,
								unsavable.toArray(),
								"Unable to save the following:",
								JOptionPane.WARNING_MESSAGE);
					}
				} finally {
					shapeLayer.end(led);
				}
			}
		}
	}
	
	private String getFcName(FeatureCollection fc){
		String name = fc.getFilename();
		return name == null ? FileTableModel.NULL_PROVIDER_NAME : name;
	}

	/**
	 * Implements the "Delete Selected Files" action, which lets the user remove
	 * any file except the untitled one.
	 * 
	 * @author saadat
	 */
	private class DelSelectedFilesActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e){
			// Mark a new history frame
			shapeLayer.getHistory().mark();
			ShapeLayer.LEDState led = null;
			try {
    			shapeLayer.begin(led = new ShapeLayer.LEDStateProcessing());
    			List<FeatureCollection> sel = shapeLayer.getFileTable().getSelectedFeatureCollections();
    			Iterator<FeatureCollection> it = sel.iterator();
    			while (it.hasNext()) {
    				FeatureCollection fc = it.next();
    				if (fc.getProvider() == null) {
    					JOptionPane.showMessageDialog(Main.getLManager(),
    							new String[] {
    						"Cannot remove the "+FileTableModel.NULL_PROVIDER_NAME+" file."
    					},
    					"Warning!",
    					JOptionPane.WARNING_MESSAGE
    					);
    					it.remove();
    				}
    			}
				shapeLayer.getFileTable().getFileTableModel().removeAll(sel);
			}
			finally {
    			shapeLayer.end(led);
			}
		}
	}
	
	/**
	 * Implements the "Delete Selected Features" action.
	 * 
	 * @author saadat
	 */
	private class DelSelectedFeaturesAction extends AbstractAction {
		final FeatureCollection fc;
		
		public DelSelectedFeaturesAction(FeatureCollection fc){
			super("Delete Selected Features");
			this.fc = fc;
		}
		
		public void actionPerformed(ActionEvent e){
			// Mark a new history frame
			shapeLayer.getHistory().mark();
			fc.removeFeatures(shapeLayer.selections);
		}
	}
	
	/**
	 * Implements the "Center on Feature" action.
	 * 
	 * @author saadat
	 */
	private class CenterOnFeatureAction extends AbstractAction {
		final FeatureCollection fc;
		
		public CenterOnFeatureAction(FeatureCollection fc){
			super("Center on Feature");
			this.fc = fc;
		}
		
		public void actionPerformed(ActionEvent e){
			if (!shapeLayer.selections.isEmpty()) {
				Point2D center = FeatureUtil.getCenterPoint(shapeLayer.selections);
				if (center != null) {
					shapeLView.viewman2.getLocationManager().setLocation(center, true);
				}
			}
		}
	}
	
	/**
	 * Implements "Load Script" action.
	 * 
	 * @author saadat
	 */
	private class LoadScriptActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e){
			final File[] scriptFile = shapeScriptFileChooser.chooseFile("Load");
			if (scriptFile == null)
				return;
			
	    	// Make a new history frame.
	    	shapeLayer.getHistory().mark();
	    	
		    // If we are going to do anything here, we need to be in edit mode.
		    shapeLView.getFeatureMouseHandler().setMode(FeatureMouseHandler.SELECT_FEATURE_MODE);
 
		    final ShapeLayer.LEDState ledState = new ShapeLayer.LEDStateFileIO();
		    SwingUtilities.invokeLater(new Runnable(){
		    	public void run(){
				    shapeLayer.begin(ledState);
				    try {
				    	BufferedReader  inStream = new BufferedReader( new FileReader( scriptFile[0].toString() ));
				    	String line;
				    	do {
				    		line = inStream.readLine();
				    		if (line!=null){
				    			new FeatureSQL( line, shapeLayer.getFeatureCollection(), shapeLayer.selections, null);
				    		}
				    	} while (line != null);
				    	inStream.close();
				    } catch (IOException ex) {
				    	JOptionPane.showMessageDialog(ShapeFocusPanel.this,
				    			new String[] {
				    				"Error reading file " + scriptFile[0].toString() + ": ",
				    				ex.getMessage(),
				    			},
				    			"Error!", JOptionPane.ERROR_MESSAGE);
				    }
				    finally {
				    	shapeLayer.end(ledState);
				    }
		    	}
		    });
		}
	}
	
	private class MultiEditAction extends AbstractAction {
		MouseEvent tableMouseEvent = null;
		STable dataTable;
		boolean booleanResult = true;
		
		public MultiEditAction(STable dataTable){
			super("Edit column of selected rows");
			this.dataTable = dataTable;
		}
		
		public void setTableMouseEvent(MouseEvent e){
			tableMouseEvent = e;
		}

		public void actionPerformed(ActionEvent e) {
			if (tableMouseEvent == null)
				return;

			int screenColumn = dataTable.getColumnModel().getColumnIndexAtX(tableMouseEvent.getX());
			String columnName = dataTable.getColumnName(screenColumn);
			TableColumn tableColumn = dataTable.getColumnModel().getColumn(screenColumn);
			int columnIndex = tableColumn.getModelIndex();
			Field field = (Field)tableColumn.getIdentifier();
			if (!field.editable) {
				JOptionPane.showMessageDialog(Main.getLManager(),
						"\""+columnName+"\" is not an editable column", "Error!",
						JOptionPane.ERROR_MESSAGE);
			}
			else {
				Class<?> columnClass = dataTable.getModel().getColumnClass(columnIndex);
				TableCellEditor editor = dataTable.getDefaultEditor(columnClass);
				int[] selectedRows = dataTable.getSelectedRows();
				if (selectedRows.length == 1)
					return;
				
				JPanel inputPanel = new JPanel(new BorderLayout());
				JColorChooser colorChooser = null;
				if (editor instanceof ColorCellEditor){
					inputPanel.add(colorChooser = new JColorChooser(), BorderLayout.CENTER);
				}
				else {
					inputPanel.add(editor.getTableCellEditorComponent(dataTable, null,
							false, selectedRows[0], columnIndex), BorderLayout.CENTER);
				}
				
				SimpleDialog dialog = SimpleDialog.getInstance(Main.getLManager(),
						"Enter value for \""+columnName+"\"", true, inputPanel);
				dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
				dialog.setVisible(true);
				
				Object input = null;
				if (SimpleDialog.OK_COMMAND.equals(dialog.getActionCommand()))
					input = (colorChooser == null? editor.getCellEditorValue(): colorChooser.getColor());
				
				if (input != null) {
					Map<Feature,Object> values = new LinkedHashMap<Feature,Object>();
					List<Feature> features = shapeLayer.getFeatureCollection().getFeatures();
					for (int sortedIdx: selectedRows) {
						int unsortedIdx = dataTable.getSorter().unsortRow(sortedIdx);
						values.put(features.get(unsortedIdx), input);
					}
					shapeLayer.getFeatureCollection().setAttributes(field, values);
				}
			}
		}
	}

	private static class SimpleDialog extends JDialog implements ActionListener {
		public static final String OK_COMMAND = "Ok";
		public static final String CANCEL_COMMAND = "Cancel";
		
		JButton okButton = new JButton(OK_COMMAND);
		JButton cancelButton = new JButton(CANCEL_COMMAND);
		JPanel inputPanel;
		JPanel buttonPanel;
		String actionCommand = null;
		
		protected SimpleDialog(Frame owner, String title, boolean modal){
			super(owner, title, modal);
			
			inputPanel = new JPanel(new BorderLayout());
			inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
			buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
			buttonPanel.add(okButton);
			buttonPanel.add(cancelButton);
			inputPanel.add(buttonPanel, BorderLayout.SOUTH);
			okButton.addActionListener(this);
			okButton.setDefaultCapable(true);
			cancelButton.addActionListener(this);
			setContentPane(inputPanel);
		}
		
		public static SimpleDialog getInstance(Frame owner, String title, boolean modal, JComponent inputComponent){
			SimpleDialog dialog = new SimpleDialog(owner, title, modal);
			dialog.inputPanel.add(inputComponent, BorderLayout.CENTER);
			dialog.pack();
			return dialog;
		}
		
		public void actionPerformed(ActionEvent e){
			actionCommand = e.getActionCommand();
			setVisible(false);
		}
		
		public String getActionCommand(){
			return actionCommand;
		}
	}
	
	private JMenuBar getMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		menuBar.add(getFileMenu());
		menuBar.add(getSelectMenu());
		menuBar.add(getScriptMenu());
		menuBar.add(getPropMenu());
		menuBar.setBorder(BorderFactory.createEtchedBorder());
		return menuBar;
	}
	
	/** Create a menu with children */
	private JMenuItem createMenu(String title, Component[] children) {
		JMenuItem item = new JMenu(title);
		for (int i = 0; i < children.length; i++)
			item.add(children[i]);
		return item;
	}

	/** Create a menu item with an optional action listener */
	private JMenuItem createMenu(String title, JMenuItem instance, ActionListener handler) {
		JMenuItem item = (instance != null ? instance : new JMenuItem());
		item.setText(title);
		if (handler != null)
			item.addActionListener(handler);
		return item;
	}

	private JMenu getPropMenu() {
		JMenuItem name = new JMenuItem("Name...");
		name.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String oldName = shapeLView.getName();
				String newName = JOptionPane.showInputDialog(ShapeFocusPanel.this, "Shape Layer Name", oldName);
				if (newName != null && !oldName.equals(newName)) {
					shapeLView.setName(newName);
				}
			}
		});
		
		final JMenuItem showProgress = new JCheckBoxMenuItem("Show Progress", shapeLayer.showProgress);
		showProgress.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				shapeLayer.showProgress = showProgress.isSelected();
			}
		});
		
		JMenu props = new JMenu("Settings");
		props.add(name);
		props.add(showProgress);
		return props;
	}

	private JMenu getScriptMenu() {
		JMenu scriptsMenu = new JMenu("Scripts");
		scriptsMenu.add(featureLoadScriptsMenuItem);
		scriptsMenu.add(featureCommandMenuItem);
		return scriptsMenu;
	}

	private JMenu getSelectMenu() {
		JMenu selectMenu = new JMenu("Feature");

		// build the FeatureTable menu
	    JMenuItem featureUndoMenuItem = new JMenuItem("Undo");
		selectMenu.add(featureUndoMenuItem);
		featureUndoMenuItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				ShapeLayer.LEDState led = null;
				try {
					shapeLayer.begin(led = new ShapeLayer.LEDStateProcessing());
					shapeLayer.getHistory().undo();
				}
				finally {
					shapeLayer.end(led);
				}
			}
		});
		
		selectMenu.add(saveAllFeaturesToFileAsMenuItem);
		final JMenuItem editColumnMenu = createMenu("Edit Columns...", null, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// take a snapshot of the columns before hand, and then show a column editor to the user
				FeatureCollection fc = shapeLayer.getFeatureCollection();
				Set<Field> oldFields = new HashSet<Field>(fc.getSchema());
				int[] rows = shapeLayer.fileTable.getSelectedRows();
				if (rows == null || rows.length != 1) {
					return;
				}
				
				Frame parent = (Frame)SwingUtilities.getAncestorOfClass(Frame.class, ShapeFocusPanel.this);
				new ColumnEditor(shapeLayer).showColumnEditor(parent, shapeLayer.fileTable.getFileTableModel().get(rows[0]));
				
				// get all fields affected by the editor
				Set<Field> unchangedFields = new HashSet<Field>(fc.getSchema());
				unchangedFields.retainAll(oldFields);
				Set<Field> changedFields = new HashSet<Field>(oldFields);
				changedFields.addAll(fc.getSchema());
				changedFields.removeAll(unchangedFields);
				
				// notify the views of the styles affected by the column edit
				Set<Style<?>> changed = shapeLayer.getStylesFromFields(changedFields);
				shapeLayer.broadcast(new ShapeLayer.StylesChange(changed));
			}
		});
		editColumnMenu.setEnabled(shapeLayer.fileTable.getSelectedRowCount() == 1);
		selectMenu.add(editColumnMenu);
		
		// make sure we can only edit columns for one file at a time
		final FileTable ft = shapeLayer.getFileTable();
		ft.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				editColumnMenu.setEnabled(ft.getSelectedRowCount() == 1);
				editColumnMenu.setToolTipText(editColumnMenu.isEnabled() ? null : "Select one file to edit columns");
			}
		});
		
		selectMenu.addSeparator();
		
		new StylesMenu(selectMenu);
		
		return selectMenu;
	}
	
	/** simple abstract way of getting the styles */
	interface StylesFactory {
		ShapeLayerStyles getStyles();
	}
	
	private JRadioButton emptySelection = new JRadioButton("hidden selection");
	public void clearStyleSelection() {
		emptySelection.setSelected(true);
	}
	
	private class StylesMenu {
		private static final String STYLES_DEFAULT_KEY = "shape.styles_default";
		private JMenu stylesMenu = new JMenu("Styles");
		private ButtonGroup group = new ButtonGroup();
		private final Map<String,JMenuItem> choices = new LinkedHashMap<String,JMenuItem>();
		private final Map<String,StylesFactory> factories = new LinkedHashMap<String,StylesFactory>();
		
		public StylesMenu() {
			this(new JMenu("Styles"));
		}
		
		public StylesMenu(JMenu stylesMenu) {
			this.stylesMenu = stylesMenu;
			
			group.add(emptySelection);
			
			stylesMenu.add(createMenu("Edit Styles...", null, new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					// popup dialog, which returns the changed styles
					ShapeLayerStyles styles = shapeLayer.getStyles();
					List<Field> fields = shapeLayer.getFeatureCollection().getSchema();
					Set<Style<?>> changed = new StyleEditor().showStyleEditor(styles, fields);
					if (!changed.isEmpty()) {
						shapeLayer.applyStyleChanges(changed);
						
						// empty the styles selection since the current styles is not known to be equal to any of them
						emptySelection.setSelected(true);
					}
				}
			}));
			
			stylesMenu.add(createMenu("Save Styles...", null, new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					while (true) {
						final String input = JOptionPane.showInputDialog(ShapeFocusPanel.this, "Name this styles configuration:");
						if (input == null) {
							break;
						}
						try {
							if (choices.keySet().contains(input)) {
								throw new IllegalArgumentException("That name already exists, must use another");
							}
							stylesStore.save(input, shapeLayer.getStyles());
							addFromStore(input);
							select(input);
							break;
						} catch (Exception ex) {
							JOptionPane.showMessageDialog(ShapeFocusPanel.this, "Error saving styles: " + ex.getMessage(),
								"Error Saving Styles", JOptionPane.ERROR_MESSAGE);
						}
					}
				}
			}));
			
			stylesMenu.add(createMenu("Remove Styles...", null, new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					final DefaultListModel model = new DefaultListModel();
					List<String> userNames = stylesStore.getNames();
					for (String name: userNames) {
						model.addElement(name);
					}
					
					final JButton del = new JButton("Remove");
					del.setEnabled(false);
					
					final JButton ok = new JButton("Okay");
					
					final JButton cancel = new JButton("Cancel");
					
					final JList list = new JList(model);
					
					int gap = 4;
					
					JPanel pad = new JPanel(new BorderLayout());
					pad.add(new JScrollPane(list));
					pad.setBorder(new EmptyBorder(gap,gap,gap,gap));
					
					Box buttons = Box.createVerticalBox();
					buttons.add(Box.createVerticalStrut(gap));
					buttons.add(del);
					buttons.add(Box.createVerticalStrut(gap));
					buttons.add(Box.createVerticalGlue());
					buttons.add(ok);
					buttons.add(Box.createVerticalStrut(gap));
					buttons.add(cancel);
					buttons.add(Box.createVerticalStrut(gap));
					
					final JDialog dlg = new JDialog((Frame)SwingUtilities.getAncestorOfClass(Frame.class, ShapeFocusPanel.this),
						"Remove Styles", true);
					dlg.getContentPane().setLayout(new BorderLayout());
					dlg.getContentPane().add(pad, BorderLayout.CENTER);
					dlg.getContentPane().add(buttons, BorderLayout.EAST);
					dlg.pack();
					
					final boolean[] okHit = {false};
					
					ok.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							okHit[0] = true;
							dlg.setVisible(false);
						}
					});
					
					cancel.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							dlg.setVisible(false);
						}
					});
					
					del.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							int[] indices = list.getSelectedIndices();
							for (int i = indices.length - 1; i>=0; i--) {
								model.remove(indices[i]);
							}
						}
					});
					
					list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
						public void valueChanged(ListSelectionEvent e) {
							del.setEnabled(list.getSelectedIndices().length > 0);
						}
					});
					
					Util.addEscapeAction(dlg);
					
					dlg.setSize(400,400);
					dlg.setVisible(true);
					
					if (okHit[0]) {
						List<String> names = Collections.list((Enumeration<String>)model.elements());
						List<String> errors = new ArrayList<String>();
						for (String name: userNames) {
							if (!names.contains(name)) {
								if (!removeFromStore(name)) {
									errors.add(name);
								}
							}
						}
						if (!errors.isEmpty()) {
							JOptionPane.showMessageDialog(ShapeFocusPanel.this,
								"Unable to remove the following styles:\n\n" + Util.join("\n", errors),
								"Error removing styles", JOptionPane.ERROR_MESSAGE);
						}
					}
				}
			}));
			
			stylesMenu.addSeparator();
			
			// add built-in styles
			String[] defaultStyles = Config.getAll("shape.styles");
			final XStream xs = new XStream();
			for (int i = 0; i < defaultStyles.length; i += 2) {
				final String name = defaultStyles[i+0];
				final String location = defaultStyles[i+1];
				addChoice(name, new StylesFactory() {
					public ShapeLayerStyles getStyles() {
						return (ShapeLayerStyles)xs.fromXML(Main.getResourceAsStream(location));
					}
				});
			}
			
			// add user-defined styles
			for (final String name: stylesStore.getNames()) {
				addFromStore(name);
			}
			
			// set the default
			select(Config.get(STYLES_DEFAULT_KEY));
		}
		
		public JMenu getMenu() {
			return stylesMenu;
		}
		
		private void addChoice(final String name, final StylesFactory factory) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(name);
			stylesMenu.add(item);
			group.add(item);
			choices.put(name, item);
			factories.put(name, factory);
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					select(name);
				}
			});
		}
		
		private boolean removeFromStore(final String name) {
			boolean result = stylesStore.remove(name);
			if (result) {
				JMenuItem item = choices.remove(name);
				factories.remove(name);
				if (item != null) {
					group.remove(item);
					stylesMenu.remove(item);
				}
			}
			return result;
		}
		
		private void addFromStore(final String name) {
			addChoice(name, new StylesFactory() {
				public ShapeLayerStyles getStyles() {
					return (ShapeLayerStyles) stylesStore.load(name);
				}
			});
		}
		
		private void select(String choice) {
			for (AbstractButton b: Collections.list(group.getElements())) {
				boolean val = b.getText().equals(choice);
				if (val) {
					try {
						ShapeLayerStyles styles = factories.get(choice).getStyles();
						if (styles != null) {
							// replace the shape layer's loaded styles
							shapeLayer.applyStyleChanges(styles.getStyles());
						}
					} catch (Exception ex) {
						ex.printStackTrace();
						JOptionPane.showMessageDialog(stylesMenu, Util.foldText(ex.getMessage(), 60, "\n"),
							"Error loading styles", JOptionPane.ERROR_MESSAGE);
					}
					if (!b.getText().equals(Config.get(STYLES_DEFAULT_KEY))) {
						Config.set(STYLES_DEFAULT_KEY, b.getText());
					}
				}
				group.setSelected(b.getModel(), val);
			}
		}
	}
	
	/** persists styles to and from disk */
	static class StylesStore {
		private XStream xs = new XStream();
		private static File base = new File(Main.getJMarsPath() + "styles");
		private static String ext = ".xml";
		/** Returns the names of all saved styles */
		public List<String> getNames() {
			List<String> names = new ArrayList<String>();
			if (base.exists() && base.isDirectory()) {
				for (File f: base.listFiles()) {
					if (f.getName().endsWith(ext)) {
						names.add(f.getName().replaceAll(ext + "$", ""));
					}
				}
			}
			return names;
		}
		private File getFile(String name) {
			return new File(base.getAbsolutePath() + File.separator + name + ext);
		}
		/** Removes the styles with the given name, returning true if the removal succeeded */
		public boolean remove(String name) {
			File f = getFile(name);
			return f.exists() ? f.delete() : true;
		}
		/** Returns the styles with this name, or throws an exception if an error occurred */
		public Styles load(String name) {
			try {
				return (Styles)xs.fromXML(new FileReader(getFile(name)));
			} catch (Exception e) {
				throw new IllegalArgumentException("Error while loading", e);
			}
		}
		/** Saves the styles with the given name, throwing IllegalArgumentException if the save failed */
		public void save(String name, Styles styles) {
			File file = getFile(name);
			if (file.exists()) {
				throw new IllegalArgumentException("File already exists");
			} else if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
				throw new IllegalStateException("Unable to create folder to save styles (" + file.getParentFile().getAbsolutePath() + ")");
			}
			try {
				xs.toXML(styles, new FileWriter(file));
			} catch (Exception e) {
				throw new IllegalArgumentException("XML conversion error: " + e.getMessage(), e);
			}
		}
	}
	
	private JMenu getFileMenu() {
		JMenu fileMenu = new JMenu("File");

	    // build the FileTable menu.
		if (shapeLayer.getProviderFactory().getFileProviders().size() > 0) {
			fileMenu.add(createMenu("Load File...", null, new LoadActionListener()));
		}
		Iterator fIt = shapeLayer.getProviderFactory().getNotFileProviders().iterator();
		while (fIt.hasNext()) {
			FeatureProvider provider = (FeatureProvider)fIt.next();
			ActionListener handler = new CustomProviderHandler(provider);
			fileMenu.add(createMenu("Load " + provider.getDescription(), null, handler));
		}
		
		return fileMenu;
	}
	
	public STable getFeatureTable(){
		return featureTable;
	}
	
	/**
	 * Disposes various resources currently in use by the FocusPanel rendering
	 * the FocusPanel unusable.
	 */
	protected void dispose(){
		for(Iterator i=openDialogList.iterator(); i.hasNext(); ){
			try {
				JDialog d = (JDialog)i.next();
				d.dispose();
			}
			catch(RuntimeException ex){
				log.print(ex);
			}
		}
	}
}
