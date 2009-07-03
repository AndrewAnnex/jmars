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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.DataReceiver;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.map2.MapThreadFactory;
import edu.asu.jmars.layer.util.features.Feature;
import edu.asu.jmars.layer.util.features.FeatureCollection;
import edu.asu.jmars.layer.util.features.FeatureEvent;
import edu.asu.jmars.layer.util.features.FeatureIndex;
import edu.asu.jmars.layer.util.features.FeatureListener;
import edu.asu.jmars.layer.util.features.FeatureProvider;
import edu.asu.jmars.layer.util.features.FeatureProviderFactory;
import edu.asu.jmars.layer.util.features.Field;
import edu.asu.jmars.layer.util.features.MemoryFeatureIndex;
import edu.asu.jmars.layer.util.features.MultiFeatureCollection;
import edu.asu.jmars.layer.util.features.SingleFeatureCollection;
import edu.asu.jmars.layer.util.features.Style;
import edu.asu.jmars.layer.util.features.StyleFieldSource;
import edu.asu.jmars.layer.util.features.StyleSource;
import edu.asu.jmars.layer.util.filetable.FileTable;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.History;
import edu.asu.jmars.util.ObservableSet;
import edu.asu.jmars.util.Util;

public class ShapeLayer extends Layer {
	/** History size is obtained from the specified key. */
	public static final String CONFIG_KEY_HISTORY_SIZE = "shape.history_size";

	/** selected features for this layer */
	ObservableSet<Feature> selections = new ObservableSet<Feature>(new HashSet<Feature>());
	
	private final MemoryFeatureIndex index;
	
	/** FileTable for this layer */
	// TODO: this should be owned by the focus panel, NOT the layer!
	FileTable fileTable;

	/** History for changes to 'mfc' */
	private final History history = new History(Config.get(CONFIG_KEY_HISTORY_SIZE, 10));

	/** Keep track of states. */
	private List<LEDState> statusLEDStack = Collections.synchronizedList(new ArrayList<LEDState>());

	/** Factory for producing FeatureProviders */
	private FeatureProviderFactory providerFactory;
	
	boolean showProgress = false;
	String name = "Shape Layer";
	
	// style settings for this layer
	private final ShapeLayerStyles styles = new ShapeLayerStyles();
	
	/*
	 * Status to color mapping; earlier entries have priority over later entries
	 */
	protected static Map<Class<? extends LEDState>,Color> statusLEDColor;
	static {
		statusLEDColor = new LinkedHashMap<Class<? extends LEDState>,Color>();
		statusLEDColor.put(LEDStateFileIO.class, Color.RED);
		statusLEDColor.put(LEDStateProcessing.class, Color.ORANGE);
		statusLEDColor.put(LEDStateDrawing.class, Color.YELLOW);
	}
	
	public static String[] getFeatureProviderClassNames(){
		String[] providers = Config.getAll("shape.featurefactory");
		String[] classes = new String[providers.length / 2];
		for (int i = 1; i < providers.length; i+=2) {
			classes[(i-1)/2] = providers[i];
		}
		return classes;
	}
	
	public final Map<FeatureCollection,CalcFieldListener> calcFieldMap = new HashMap<FeatureCollection,CalcFieldListener>();
	private final FeatureCollection stylesFC = new SingleFeatureCollection();
	
	public ShapeLayer() {
		super();
		
		String[] classes = getFeatureProviderClassNames();
		providerFactory = new FeatureProviderFactory(classes);
		
		// Tie FileTable, MultiFeatureCollection and FeatureTable together.
		fileTable = new FileTable(history);
		
		updateLoadedShapes();
		fileTable.getModel().addTableModelListener(new TableModelListener() {
			public void tableChanged(TableModelEvent e) {
				updateLoadedShapes();
			}
		});
		
		index  = new MemoryFeatureIndex(fileTable.getMultiFeatureCollection());
		
		StyleColumnPositioner stylePos = new StyleColumnPositioner(fileTable.getMultiFeatureCollection(), stylesFC);
		fileTable.getSelectionModel().addListSelectionListener(stylePos);
		fileTable.getMultiFeatureCollection().addListener(stylePos);
		
		SingleFeatureCollection empty = new SingleFeatureCollection();
		fileTable.getFileTableModel().add(empty);
		fileTable.getSelectionModel().addSelectionInterval(0,0);
	}
	
	/** Called when the file table contents change; updates the history and calc field listener */
	private void updateLoadedShapes() {
		Set<FeatureCollection> inTable = new HashSet<FeatureCollection>(fileTable.getFileTableModel().getAll());
		Set<FeatureCollection> inCalcMap = new HashSet<FeatureCollection>(calcFieldMap.keySet());
		for (FeatureCollection f: inCalcMap) {
			if (!inTable.contains(f)) {
				f.removeListener(calcFieldMap.remove(f));
			}
		}
		for (FeatureCollection f: inTable) {
			// make sure this history object is set on the data
			if (f instanceof SingleFeatureCollection) {
				((SingleFeatureCollection)f).setHistory(getHistory());
			}
			
			if (!inCalcMap.contains(f)) {
				// create the calc field updater, reusing the same field map
				CalcFieldListener c = new CalcFieldListener(f, history);
				calcFieldMap.put(f, c);
				f.addListener(c);
			}
		}
	}
	
	public void cleanup() {
		index.disconnect();
	}
	
	public FileTable getFileTable() {
		return fileTable;
	}

	/** Not used */
	public void receiveRequest(Object layerRequest, DataReceiver requester) {
	}

	public FeatureCollection getFeatureCollection(){
		return fileTable.getMultiFeatureCollection();
	}

	public History getHistory(){
		return history;
	}
	
	public void begin(LEDState state){
		statusLEDStack.add(state);
		updateStatus();
	}
	
	public void end(final LEDState state){
		statusLEDStack.remove(state);
		updateStatus();
	}
	
	private void updateStatus() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				Color c = Color.GREEN.darker();
				synchronized(statusLEDStack) {
					// check for each status in priority order
					for (Class<?> test: statusLEDColor.keySet()) {
						boolean found = false;
						for (LEDState state: statusLEDStack) {
							if (state.getClass() == test) {
								c = statusLEDColor.get(test);
								found = true;
								break;
							}
						}
						if (found) {
							break;
						}
					}
				}
				setStatus(c);
			}
		});
	}
	
	public abstract static class LEDState {}
	public static class LEDStateProcessing extends LEDState {}
	public static class LEDStateFileIO extends LEDState {}
	public static class LEDStateAllDone extends LEDState {}
	public static class LEDStateDrawing extends LEDState {}
	
	/**
	 * Returns the provider factory created from the FeatureProvider class
	 * names in jmars.config. See FeatureProviderFactory for more info.
	 */
	public FeatureProviderFactory getProviderFactory () {
		return providerFactory;
	}
	
	/** Returns a copy of the styles object */
	public ShapeLayerStyles getStyles() {
		return new ShapeLayerStyles(styles);
	}
	
	/** Returns the style instances that use any field in the given collection */
	public Set<Style<?>> getStylesFromFields(Collection<Field> fields) {
		Set<Style<?>> out = new HashSet<Style<?>>();
		for (Style<?> s: styles.getStyles()) {
			StyleSource<?> ss = s.getSource();
			if (ss instanceof StyleFieldSource<?>) {
				if (fields.contains(((StyleFieldSource<?>)ss).getField())) {
					out.add(s);
				}
			}
		}
		return out;
	}
	
	/** Sets style sources on this styles document from the sources in the given set of styles */
	public void applyStyleChanges(Set<Style<?>> changes) {
		// match style instances by name
		Set<Style<?>> current = styles.getStyles();
		for (Style<?> s: changes) {
			// update matching sources
			for (Style<?> c: current) {
				if (s.getName().equals(c.getName())) {
					c.setSource((StyleSource)s.getSource());
					break;
				}
			}
		}
		
		// get style fields before and after the changes were applied
		Set<Field> oldFields = new LinkedHashSet<Field>(stylesFC.getSchema());
		Set<Field> newFields = new LinkedHashSet<Field>();
		for (Style<?> s: current) {
			StyleSource<?> source = s.getSource();
			if (source instanceof StyleFieldSource) {
				newFields.add(((StyleFieldSource<?>)source).getField());
			}
		}
		
		// remove fields not still there
		for (Field f: oldFields) {
			if (!newFields.contains(f)) {
				stylesFC.removeField(f);
			}
		}
		
		// add fields that weren't there before
		for (Field f: newFields) {
			if (!oldFields.contains(f)) {
				stylesFC.addField(f);
			}
		}
		
		// notify the listeners that these styles were changed
		broadcast(new StylesChange(changes));
	}
	
	public void broadcast(Object o) {
		super.broadcast(o);
	}
	
	public static final class StylesChange {
		public final Set<Style<?>> changes;
		public StylesChange(Set<Style<?>> changes) {
			this.changes = changes;
		}
	}

	public ObservableSet<Feature> getSelections() {
		return selections;
	}
	
	public FeatureIndex getIndex() {
		return index;
	}
	
	/**
	 * Track style columns as a separate collection, and keep that
	 * collection's columns all the way to the right.
	 */
	private static class StyleColumnPositioner implements ListSelectionListener, FeatureListener {
		private boolean busy = false;
		private final MultiFeatureCollection mfc;
		private final FeatureCollection stylesFC;
		public StyleColumnPositioner(MultiFeatureCollection mfc, FeatureCollection stylesFC) {
			this.mfc = mfc;
			this.stylesFC = stylesFC;
		}
		public void valueChanged(ListSelectionEvent e) {
			if (!e.getValueIsAdjusting()) {
				reinsertStyles();
			}
		}
		public void receive(FeatureEvent e) {
			switch (e.type) {
			case FeatureEvent.REMOVE_FIELD:
			case FeatureEvent.ADD_FIELD:
				reinsertStyles();
				break;
			}
		}
		private void reinsertStyles() {
			// removeFeatureCollection triggers a REMOVE_FIELD event, which
			// we want to ignore, so prevent reentrant processing
			if (!busy) {
				busy = true;
				// inserts styles at a later time, so the current table changes should be finished
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						try {
							if (mfc.getSupportingFeatureCollections().contains(stylesFC)) {
								mfc.removeFeatureCollection(stylesFC);
							}
							mfc.addFeatureCollection(stylesFC);
						} finally {
							busy = false;
						}
					}
				});
			}
		}
	}
	
	public void loadSources(final List<LoadData> sources) {
		loadSources(sources, new SourceAdder(sources));
	}
	
	/**
	 * Processes FileLoad updates; as each change is reported, it gathers errors
	 * to show at the end, and adds successful loads right away. It will mark a
	 * history frame before making the first change.
	 */
	private class SourceAdder implements LoadListener {
		private boolean marked = false;
		private final List<String> msgs = new ArrayList<String>();
		private final List<LoadData> sources;
		public SourceAdder(List<LoadData> sources) {
			this.sources = new ArrayList<LoadData>(sources);
		}
		public void receive(LoadData data) {
			if (data.fc != null) {
				if (!marked) {
					marked = true;
					getHistory().mark();
				}
				getFileTable().getFileTableModel().add(data.fc);
			}
			if (data.error != null) {
				msgs.add(data.error.getMessage() + " while loading " + data.data);
			}
			sources.remove(data);
			if (sources.isEmpty() && !msgs.isEmpty()) {
				JOptionPane.showMessageDialog(Main.getLManager(),
						Util.join("\n", msgs),
						"Error occurred while loading...", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	/**
	 * Loads features in a thread pool, and puts the collection or error on the
	 * LoadData objects passed in.
	 * 
	 * The callback is called on the AWT thread each time the status of any
	 * LoadData changes, so the caller can do something with each file as it
	 * becomes ready.
	 * 
	 * As long as any files are still loading, the shape layer's status LED
	 * should show that a file IO operation is ongoing.
	 */
	public void loadSources(List<LoadData> sources, final LoadListener callback) {
		final ExecutorService pool = Executors.newFixedThreadPool(5, new MapThreadFactory("ShapeLoader"));
		final CyclicBarrier barrier = new CyclicBarrier(sources.size());
		for (final LoadData source: new ArrayList<LoadData>(sources)) {
			pool.execute(new Runnable() {
				public void run() {
					final ShapeLayer.LEDState led = new ShapeLayer.LEDStateFileIO();
					begin(led);
					try {
						source.fc = (SingleFeatureCollection) source.fp.load(source.data);
						source.fc.setProvider(source.fp);
						source.fc.setFilename(source.data == null ? source.fp.getDescription() : source.data);
					} catch (final Exception e) {
						e.printStackTrace();
						source.error = e;
					} finally {
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								callback.receive(source);
								end(led);
							}
						});
						
						try {
							barrier.await();
						} catch (Exception e) {
							e.printStackTrace();
						}
						
						synchronized(pool) {
							pool.shutdown();
						}
					}
				}
			});
		}
	}
	
	interface LoadListener {
		void receive(LoadData data);
	}
	
	public static class LoadData {
		public final FeatureProvider fp;
		public final String data;
		public SingleFeatureCollection fc;
		public Exception error;
		public LoadData(FeatureProvider fp, String data) {
			this.fp = fp;
			this.data = data;
		}
	}
}

