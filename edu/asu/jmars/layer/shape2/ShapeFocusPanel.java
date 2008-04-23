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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.Layer.LView;
import edu.asu.jmars.layer.shape2.FileChooser;
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
import edu.asu.jmars.layer.util.features.ShapeRenderer;
import edu.asu.jmars.layer.util.features.SingleFeatureCollection;
import edu.asu.jmars.layer.util.features.ZOrderMenu;
import edu.asu.jmars.layer.util.filetable.FileTable;
import edu.asu.jmars.layer.util.filetable.FileTableModel;
import edu.asu.jmars.swing.STable;
import edu.asu.jmars.util.ColorComponent;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.Util;
import edu.asu.jmars.util.stable.ColorCellEditor;
import edu.asu.jmars.util.stable.FilteringColumnModel;

public class ShapeFocusPanel extends FocusPanel {
	final private static DebugLog log = DebugLog.instance(); 
	
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
    
    JMenuItem   featureAddColumnMenuItem       = new JMenuItem("Add New Column");
    JMenuItem   featureDelColumnMenuItem       = new JMenuItem("Delete Column");
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

	public ShapeFocusPanel(LView parent) {
		super(parent);
		setLayout(new BorderLayout());
		
		shapeLView = (ShapeLView)parent;
		shapeLayer = (ShapeLayer)shapeLView.getLayer();
		loadSaveFileChooser = new FileChooser();
		Iterator fpit = shapeLayer.getProviderFactory().getFileProviders().iterator();
		while (fpit.hasNext())
			loadSaveFileChooser.addFilter(((FeatureProvider)fpit.next()));

		shapeScriptFileChooser = new ScriptFileChooser();

	    // build menu bar.
	    add(getMenuBar(),BorderLayout.NORTH);

	    // tie menus to corresponding actions
	    initMenuActions();

		// TODO: the mfc should be produced by the layer, not the file table!
		shapeLayer.getFileTable().setHistory(shapeLayer.getHistory());
		FeatureTableAdapter ft = new FeatureTableAdapter(shapeLayer.getFeatureCollection());
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
						popup.add(new ZOrderMenu("Z Order", fc));
					
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
						final FeatureTableAdapter fta = new FeatureTableAdapter(fc);
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
		
		featureAddColumnMenuItem.addActionListener(new AddColumnActionListener());
		featureDelColumnMenuItem.addActionListener(new DelColumnActionListener());

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
		public void actionPerformed(ActionEvent e){
			final File [] f = loadSaveFileChooser.chooseFile("Load");

		    // If no file was selected, quit.
		    if (f == null)
				return;

	    	FeatureProvider fp = loadSaveFileChooser.getFeatureProvider();
	    	String[] files = new String[f.length];
	    	for (int i = 0; i < f.length; i++)
	    		files[i] = f[i].getAbsolutePath();
	    	loadSources(fp, files);
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
			loadSources(fp, new String[] {null});
		}
	}

	/*
	 * Creates a separate thread to load files, since it can take awhile and if
	 * done on the AWT thread, would prevent updating the display. The
	 * FeatureProvider array needs to have the name to load from (if necessary)
	 * set already.
	 */
	public void loadSources (final FeatureProvider fp, final String[] files) {
		Runnable loader = new Runnable() {
			public void run() {
				List errors = new LinkedList();
				final List loaded = new LinkedList();
				ShapeLayer.LEDState led = null;
				try {
					// Set the LED state appropriately
					shapeLayer.begin(led = new ShapeLayer.LEDStateFileIO());
					// load each instantiated feature provider
					for (int i = 0; i < files.length; i++) {
						final FeatureCollection fc;
						try {
							fc = fp.load(files[i]);
							fc.setProvider(fp);
							fc.setFilename(files[i] == null ? fp.getDescription() : files[i]);
							loaded.add(fc);
						} catch (Exception e) {
							errors.add(e.getMessage() + " while loading " + files[i]);
						}
					}
					// inject the loaded data on the AWT thread since it
					// cascades into a display update
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							shapeLayer.getHistory().mark();
							for (Iterator it = loaded.iterator(); it.hasNext(); ) {
								SingleFeatureCollection fc = (SingleFeatureCollection)it.next();
								fc.setHistory(shapeLayer.getHistory());
								shapeLayer.getFileTable().add(fc);
							}
						}
					});
					// display errors
					if (errors.size() > 0) {
				    	JOptionPane.showMessageDialog(Main.getLManager(),
			    			Util.join("\n", (String[])errors.toArray(new String[0])),
			    			"Unable to load files...", JOptionPane.ERROR_MESSAGE);
					}
				}
				finally {
					// Unset the LED state
					shapeLayer.end(led);
				}
			}
		};

		new Thread(loader, "Feature Loader").start();
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
		
		public SaveAsAction(SingleFeatureCollection fc){
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
				fc.addFeatures(FeatureUtil.getSelectedFeatures(_fc));
			}
			else if (saveSelectedFilesAsActionName.equals(e.getActionCommand())){
				List fcl = shapeLayer.getFileTable().getSelectedFeatureCollections();
				for(Iterator i=fcl.iterator(); i.hasNext(); )
					fc.addFeatures(((FeatureCollection)i.next()).getFeatures());
			}
			else if (saveAllFeaturesAsActionName.equals(e.getActionCommand())){
				List fcl = shapeLayer.getFileTable().getFeatureCollections();
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
		private SingleFeatureCollection currentFc = null;
		
		/**
		 * Returns the FeatureCollection currently under process.
		 * @return FeatureCollection currently in the process of saving.
		 */
		public SingleFeatureCollection getFeatureCollection(){
			return currentFc;
		}
		
		public void actionPerformed(ActionEvent e){
			final FileTable ft = shapeLayer.getFileTable();
			final List unsavable = new ArrayList();
			final List saveable = new ArrayList();
			List fcl = ft.getSelectedFeatureCollections();
			for(Iterator i=fcl.iterator(); i.hasNext(); ){
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

				final ShapeLayer.LEDState led;
				// TODO The LED state is not getting updated, make it work.
				shapeLayer.begin(led = new ShapeLayer.LEDStateFileIO());
				Runnable saver = new Runnable(){
					public void run(){
						for(Iterator i=saveable.iterator(); i.hasNext(); ){
							currentFc = (SingleFeatureCollection)i.next();
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
									ft.setTouched(currentFc,false);
								}
							}
							catch(Exception ex){
								log.println("While processing "+getFcName(currentFc)+" caught exception: "+ex.getMessage());
								unsavable.add(getFcName(currentFc));
							}
						}
					}
				};
				// TODO There is a chance that user does something between
				//      the time that the saver thread is submitted and when it
				//      is scheduled.
				SwingUtilities.invokeLater(saver);
				SwingUtilities.invokeLater(new Runnable(){
					public void run(){
						shapeLayer.end(led);
					}
				});
			}

			SwingUtilities.invokeLater(new Runnable(){
				public void run(){
					if (!unsavable.isEmpty())
						JOptionPane.showMessageDialog(ShapeFocusPanel.this,
								unsavable.toArray(),
								"Unable to save the following:",
								JOptionPane.WARNING_MESSAGE);
				}
			});
		}
	}
	
	private String getFcName(FeatureCollection fc){
		String name = fc.getFilename();
		return name == null ? FileTableModel.NULL_PROVIDER_NAME : name;
	}

	/**
	 * Implements the "Delete Selected Files" action.
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
				if (!shapeLayer.getFileTable().removeSelected()){
					JOptionPane.showMessageDialog(Main.getLManager(),
							new String[] {
						"Unable to delete some of the selected FeatureCollections.",
						"Note that the "+FileTableModel.NULL_PROVIDER_NAME+" feature collection may not be deleted."
					},
					"Warning!",
					JOptionPane.WARNING_MESSAGE
					);
				}
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
			
			List fl = FeatureUtil.getSelectedFeatures(fc);
			fc.removeFeatures(fl);
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
			List selected = FeatureUtil.getSelectedFeatures(fc);
			if (selected.isEmpty())
				return;

			Point2D center = FeatureUtil.getCenterPoint(selected);
			if (center != null) {
				shapeLView.viewman2.getLocationManager().setLocation(center, true);
			}
		}
	}
	
	/**
	 * Implements "Add Column" action.
	 * 
	 * @author saadat
	 */
	private class AddColumnActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Map editors = new HashMap(featureTable.getDefaultEditorsByColumnClass());
			editors.remove(Object.class); // Don't show Object type in column creation dialog.
			
			ClassAdapter[] supportedTypes = new ClassAdapter[editors.size()];
			Iterator editorIt = editors.keySet().iterator();
			ClassAdapter initial = null;
			for(int i=0; i<supportedTypes.length; i++) {
				Class type = (Class) editorIt.next();
				supportedTypes[i] = new ClassAdapter(type);

				if (type == String.class)
					initial = supportedTypes[i];
			}

			JComboBox typeSelector = new JComboBox(supportedTypes);
			// set default selection to String, or first element if String is not supported
			typeSelector.setSelectedItem (initial == null ? supportedTypes[0] : initial);

			String inputValue = JOptionPane.showInputDialog(
					ShapeFocusPanel.this,
					new Object[] {
							"Select Column Type:",
							typeSelector,
							"Type Column Name"
					},
					"Create a new column",
					JOptionPane.QUESTION_MESSAGE);
			if(inputValue != null  &&  inputValue.trim().length()>0 ){
				if (typeSelector.getSelectedIndex() < 0)
					return;

				Class selectedType = ((ClassAdapter)typeSelector.getSelectedItem()).type;

				// Make a new history frame.
				shapeLayer.getHistory().mark();
				shapeLayer.getFeatureCollection().addField(new Field (inputValue, selectedType, true));
			}
		}
		class ClassAdapter {
			public final Class type;
			public ClassAdapter (Class type) {
				this.type = type;
			}
			public String toString () {
				return type.getName().replaceAll("^.*\\.", "");
			}
		}
	}
	
	/**
	 * Implements "Delete Column" action.
	 * 
	 * @author saadat
	 */
	private class DelColumnActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e){
			// Get schema.
			Vector schema = new Vector(shapeLayer.getFeatureCollection().getSchema());
			
			// Get rid of the schema elements the user shouldn't have control over.
			schema.remove(Field.FIELD_FEATURE_TYPE);
			schema.remove(Field.FIELD_PATH);
			schema.remove(Field.FIELD_SELECTED);
			
			final JList fieldList = new JList(schema);
			fieldList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			fieldList.setCellRenderer(new FieldCellRenderer());
			
			final JScrollPane fieldListSp = new JScrollPane(fieldList);
			fieldListSp.setPreferredSize(new Dimension(300,200));

			Container owner = ShapeFocusPanel.this.getTopLevelAncestor();
			if (!(owner instanceof Frame))
				owner = null;
			
			final JDialog d = new JDialog((Frame)owner,"Select columns to remove", true);
			d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			d.setLocationRelativeTo(ShapeFocusPanel.this);
			
			JButton bOk = new JButton("Delete");
			bOk.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					Object[] toRemove = fieldList.getSelectedValues();
					if (toRemove.length > 0) {
						// Make a new history frame.
						shapeLayer.getHistory().mark();
						for(int i=0; i<toRemove.length; i++)
							shapeLayer.getFeatureCollection().removeField((Field)toRemove[i]);
					}
					d.setVisible(false);
				}
			});
			JButton bCancel = new JButton("Cancel");
			bCancel.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent e){
					d.setVisible(false);
				}
			});

			JPanel buttonPanel = new JPanel(new FlowLayout());
			buttonPanel.add(bOk);
			buttonPanel.add(bCancel);
			
			JPanel mainPanel = new JPanel(new BorderLayout());
			mainPanel.add(fieldListSp, BorderLayout.CENTER);
			mainPanel.add(buttonPanel, BorderLayout.SOUTH);
			
			d.setContentPane(mainPanel);
			d.pack();
			registerDialogForAutoCleanup(d);
			d.setVisible(true);
		}
		
		class FieldCellRenderer extends JLabel implements ListCellRenderer {
			public FieldCellRenderer(){
				super();
				setOpaque(true);
				setHorizontalAlignment(LEFT);
				setVerticalAlignment(CENTER);
			}
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				if (isSelected) {
					setBackground(list.getSelectionBackground());
					setForeground(list.getSelectionForeground());
				} else {
					setBackground(list.getBackground());
					setForeground(list.getForeground());
				}

				Field f = (Field)value;
				setFont(list.getFont());
				setText(f.name+"  --  "+f.type.getName().replaceAll("^.*\\.", ""));
				
				return this;
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
		    shapeLayer.begin(ledState);
		    SwingUtilities.invokeLater(new Runnable(){
		    	public void run(){
				    try {
				    	BufferedReader  inStream = new BufferedReader( new FileReader( scriptFile[0].toString() ));
				    	String line;
				    	do {
				    		line = inStream.readLine();
				    		if (line!=null){
				    			new FeatureSQL( line, shapeLayer.getFeatureCollection(), null);
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
	
	
	/**
	 * ActionListener that handles processing of ColorComponents that result in a 
	 * Color preset in the ShapeLayer. This handler is linked to one or more
	 * of the menu options in the ShapeFocusPanel.
	 * 
	 * @author saadat
	 *
	 */
	private class ColorComponentActionListener implements ActionListener {
		private String dialogText;
		private String presetKey;
		
		public ColorComponentActionListener(String dialogText, String presetKey){
			super();
			this.dialogText = dialogText;
			this.presetKey = presetKey;
		}
		public void actionPerformed(ActionEvent e){
			ColorComponent colorButton = (ColorComponent)e.getSource();
			Color newColor = JColorChooser.showDialog(null, dialogText, colorButton.getColor());
			if (newColor != null) {
				colorButton.setColor(newColor);
				shapeLayer.setPreset(presetKey, newColor);
			}
		}
	}
	
	/**
	 * ActionListener that handles processing of JCheckbox that result in a 
	 * Boolean preset in the ShapeLayer. This handler is linked to one or more
	 * of the menu options in the ShapeFocusPanel.
	 * 
	 * @author saadat
	 *
	 */
	private class ToggleActionListener implements ActionListener {
		private String presetKey;
		
		public ToggleActionListener(String presetKey){
			super();
			this.presetKey = presetKey;
		}
		public void actionPerformed(ActionEvent e) {
			Boolean b = (Boolean)shapeLayer.getPreset(presetKey);
			shapeLayer.setPreset(presetKey, b.equals(Boolean.TRUE)? Boolean.FALSE: Boolean.TRUE);
		}
	}
	
	/**
	 * ActionListener that handles processing of JTextFields that result in a 
	 * Float preset in the ShapeLayer. This handler is linked to one or more
	 * of the menu options in the ShapeFocusPanel.
	 * 
	 * @author saadat
	 *
	 */
	private class FloatValueActionListener implements ActionListener, FocusListener {
		private String presetKey;
		private Float defaultValue;
		private JTextField f;

		public FloatValueActionListener(JTextField f, String presetKey, Float defaultValue){
			super();
			this.f = f;
			this.presetKey = presetKey;
			this.defaultValue = defaultValue;
			f.addActionListener(this);
			f.addFocusListener(this);
		}
		
		public void actionPerformed(ActionEvent e) {
			try {
				shapeLayer.setPreset(presetKey, Float.valueOf(f.getText()));
			} catch (NumberFormatException nfe) {
				f.setText(defaultValue.toString());
				shapeLayer.setPreset(presetKey, defaultValue);
			}
		}

		public void focusGained(FocusEvent e) {}
		public void focusLost(FocusEvent e) {
			actionPerformed(null);
		}
	}

	/**
	 * ActionListener that handles processing of JTextFields that result in a 
	 * Float preset in the ShapeLayer. This handler is linked to one or more
	 * of the menu options in the ShapeFocusPanel.
	 */
	private class IntegerValueActionListener implements ActionListener, FocusListener {
		private String presetKey;
		private Integer defaultValue;
		private JTextField f;
		
		public IntegerValueActionListener(JTextField f, String presetKey, Integer defaultValue){
			super();
			this.f = f;
			this.presetKey = presetKey;
			this.defaultValue = defaultValue;
			f.addActionListener(this);
			f.addFocusListener(this);
		}

		public void actionPerformed(ActionEvent e) {
			try {
				shapeLayer.setPreset(presetKey, Integer.valueOf(f.getText()));
			} catch (NumberFormatException nfe) {
				f.setText(defaultValue.toString());
				shapeLayer.setPreset(presetKey, defaultValue);
			}
		}

		public void focusGained(FocusEvent e) {}
		public void focusLost(FocusEvent e) {
			actionPerformed(null);
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

			int screenColumn = dataTable.getColumnModel()
					.getColumnIndexAtX(tableMouseEvent.getX());
			FeatureTableModel model = (FeatureTableModel)dataTable.getUnsortedTableModel();
			FeatureCollection fc = model.getFeatureCollection();
			String columnName = dataTable.getColumnName(screenColumn);
			TableColumn tableColumn = dataTable.getColumnModel().getColumn(screenColumn);
			int columnIndex = tableColumn.getModelIndex();
			if (!((Field)fc.getSchema().get(columnIndex)).editable) {
				JOptionPane.showMessageDialog(Main.getLManager(),
						"\""+columnName+"\" is not an editable column", "Error!",
						JOptionPane.ERROR_MESSAGE);
			}
			else {
				Class columnClass = dataTable.getModel().getColumnClass(columnIndex);
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
				
				if (input != null)
					for (int i = 0; i < selectedRows.length; i++)
						model.setValueAt(input, selectedRows[i], columnIndex);
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

	private JMenuItem getPropMenu() {
		return createMenu("Settings", null, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showPropDialog();
			}
		});
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

	private Component createPresetComponent(String key) {
		Object value = shapeLayer.getPreset(key);
		if (value instanceof Boolean) {
			JCheckBox cb = new JCheckBox("           ");
			cb.addActionListener(new ToggleActionListener(key));
			cb.setSelected(((Boolean)value).booleanValue());
			return cb;
		} else if (value instanceof Integer) {
			JTextField intField = new JTextField(3);
			intField.setText(((Integer)value).toString());
			new IntegerValueActionListener(intField, key, new Integer(0));
			return intField;
		} else if (value instanceof Float) {
			JTextField floatField = new JTextField(3);
			floatField.setText(((Float)value).toString());
			new FloatValueActionListener(floatField, key, new Float(0));
			return floatField;
		} else if (value instanceof Color) {
		    ColorComponent colorField = new ColorComponent(" ", Color.black);
		    colorField.setColor((Color)value);
		    colorField.addActionListener(new ColorComponentActionListener(key,key));
			return colorField;
		}
		throw new IllegalArgumentException("Unable to create item for preset " + key);
	}

	private void showPropDialog() {
		Frame top = (Frame)this.getTopLevelAncestor();
		final JDialog prop = new JDialog(top,"Settings",false);
		Container pane = prop.getContentPane();
		pane.setLayout(new BorderLayout());
		// header
		Box header = Box.createVerticalBox();
		header.add(new JSeparator(SwingConstants.HORIZONTAL));
		pane.add(header, BorderLayout.NORTH);
		// footer
		Box footer = Box.createVerticalBox();
		footer.add(new JSeparator(SwingConstants.HORIZONTAL));
		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				prop.dispose();
			}
		});
		JPanel buttons = new JPanel(new BorderLayout());
		buttons.add(close,BorderLayout.EAST);
		footer.add(buttons);
		pane.add(footer, BorderLayout.SOUTH);
		// create settings components
		JTextField nameField = getLayerNameField();
		JPanel settings = new JPanel(new GridBagLayout());
		String[] presets = new String[] {
			ShapeLayer.PRESET_KEY_SHOW_LABELS,
			ShapeLayer.PRESET_KEY_FILL_POLYGONS,
			ShapeLayer.PRESET_KEY_SHOW_VERTICES,
			ShapeLayer.PRESET_KEY_SHOW_PROGRESS,
			ShapeLayer.PRESET_KEY_ANTIALIASING,
			ShapeLayer.PRESET_KEY_LINE_WIDTH,
			ShapeLayer.PRESET_KEY_POINT_SIZE,
			ShapeLayer.PRESET_KEY_SELECTED_LINE_WIDTH,
			ShapeLayer.PRESET_KEY_SELECTED_LINE_COLOR
		};
		settings.add(new JLabel("Shape Layer Name"), getGBC(0, 0));
		settings.add(nameField, getGBC(0, 1));
		for (int i = 0; i < presets.length; i++) {
			settings.add(new JLabel(presets[i]), getGBC(i+1,0));
			settings.add(createPresetComponent(presets[i]), getGBC(i+1,1));
		}
		pane.add(settings, BorderLayout.CENTER);
		prop.pack();
		prop.setResizable(false);
		prop.setVisible(true);
	}

	private JTextField getLayerNameField() {
		final JTextField nameField = new JTextField(12);
		nameField.setText(shapeLView.getName());
		nameField.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				shapeLView.setName(nameField.getText());
			}
		});
		nameField.addFocusListener(new FocusListener() {
			public void focusGained(FocusEvent e) {}
			public void focusLost(FocusEvent e) {
				shapeLView.setName(nameField.getText());
			}
		});
		return nameField;
	}

	/**
	 * Returns constraints for a gbc cell at row,col. If col < 0,
	 * the gbc will be for a full row component.
	 */
	private GridBagConstraints getGBC(int row, int col) {
		return new GridBagConstraints(
			col >= 0 ? col : 0, row,
			col >= 0 ? 1 : 2, 1,
			col == 0 ? 0 : 1, 0,
			col == 0 ? GridBagConstraints.LINE_END : GridBagConstraints.LINE_START,
			col >= 0 ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL,
			new Insets(2,2,2,2), 2, 2);
	}

// TODO: make a defaults menu out of the remaining presets:
// new String[] {
//	ShapeLayer.PRESET_KEY_DEFAULT_LINE_COLOR),
//	ShapeLayer.PRESET_KEY_DEFAULT_FILL_COLOR),
//	ShapeLayer.PRESET_KEY_DEFAULT_LABEL_COLOR),
//	ShapeLayer.PRESET_KEY_DEFAULT_LINE_DIRECTED),
//	ShapeLayer.PRESET_KEY_DEFAULT_LINE_TYPE),
//	ShapeLayer.PRESET_KEY_DEFAULT_LINE_WIDTH),
// }

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
		selectMenu.add(featureAddColumnMenuItem);

		Set fieldSet = new HashSet(ShapeRenderer.getFieldToStyleKeysMap().keySet());
		fieldSet.remove(Field.FIELD_FONT);
	    Field[] fields = (Field[])fieldSet.toArray(new Field[0]);
	    JMenuItem[] styles = new JMenuItem[fields.length];
	    for (int i = 0; i < fields.length; i++)
	    	styles[i] = createMenu(fields[i].name, null, new AddStyleHandler(fields[i]));
		selectMenu.add(createMenu("Add Style Column", styles));

		selectMenu.add(featureDelColumnMenuItem);
		return selectMenu;
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

	/**
	 * Adds a Field to the main shape layer's feature collection
	 * when some action is performed.
	 */
	class AddStyleHandler implements ActionListener {
		Field f;
		public AddStyleHandler (Field f) {
			this.f = f;
		}
		public void actionPerformed(ActionEvent e) {
			shapeLayer.getFeatureCollection().addField(f);
		}
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
