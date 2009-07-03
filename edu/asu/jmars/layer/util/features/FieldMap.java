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


package edu.asu.jmars.layer.util.features;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jdesktop.swingx.combobox.EnumComboBoxModel;
import org.jdesktop.swingx.combobox.ListComboBoxModel;

import edu.asu.jmars.Main;
import edu.asu.jmars.graphics.GraphicsWrapped;
import edu.asu.jmars.layer.map2.MapChannelReceiver;
import edu.asu.jmars.layer.map2.MapChannelTiled;
import edu.asu.jmars.layer.map2.MapData;
import edu.asu.jmars.layer.map2.MapServer;
import edu.asu.jmars.layer.map2.MapServerFactory;
import edu.asu.jmars.layer.map2.MapSource;
import edu.asu.jmars.layer.map2.Pipeline;
import edu.asu.jmars.layer.map2.Stage;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.Util;

/**
 * Computes the min/max/mean/stdev of map pixels under
 * each shape at the given scale and projection.
 * 
 * Each instance of this field has an unbounded queue
 * of field requests that feed into a map channel one
 * at a time. The requests are handled asynchronously,
 * so initially updating this column will show an empty
 * cell (which is also the user's feedback that data
 * is still being processed.)
 */
public class FieldMap extends CalculatedField implements MapChannelReceiver {
	private static final long serialVersionUID = 1L;
	private static final Set<Field> fields = Collections.singleton(Field.FIELD_PATH);
	public enum Type {
		MIN,MAX,AVG,SUM,STDEV
	}
	public Set<Field> getFields() {
		return fields;
	}
	
	private transient ExecutorService pool;
	private transient CyclicBarrier barrier;
	private transient MapChannelTiled ch;
	
	private final FeatureCollection fc;
	private final Field field;
	private final int band;
	
	private MapSource source;
	private int ppd;
	private Type type;
	
	private Feature feature;
	private Shape roi;
	int count;
	double sum;
	double m, s;
	double min;
	double max;
	
	public FieldMap(FeatureCollection fc, Field target, String name, Type type, int ppd, MapSource source, int band) {
		super(name, Double.class);
		this.field = target;
		this.type = type;
		this.source = source;
		this.band = band;
		this.ppd = ppd;
		this.fc = fc;
		initTransients();
	}
	
	private void readObject(java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		initTransients();
	}
	
	private void initTransients() {
		pool = Executors.newFixedThreadPool(1);
		barrier = new CyclicBarrier(2);
		ch = new MapChannelTiled(this);
	}
	
	public Object getValue(final Feature f) {
		pool.submit(new Runnable() {
			public void run() {
				try {
					feature = f;
					roi = f.getPath().getWorld().getGeneralPath();
					// for points, create a one pixel box
					Rectangle2D bounds = roi.getBounds2D();
					if (bounds.isEmpty()) {
						roi = new Rectangle2D.Double(bounds.getCenterX()-.5/ppd, bounds.getCenterY()-.5/ppd, 1d/ppd, 1d/ppd);
					}
					sum = 0;
					m = 0;
					s = 0;
					count = 0;
					min = Double.POSITIVE_INFINITY;
					max = Double.NEGATIVE_INFINITY;
					ch.setRequest(Main.PO, roi.getBounds2D(), ppd, new Pipeline[]{new Pipeline(source, new Stage[0])});
					// if ch is finished right after we set it up
					// then mapData won't be called; this is
					// just so we don't hang FieldMap if something
					// goes wrong.
					if (!ch.isFinished()) {
						barrier.await();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		return null;
	}
	public void mapChanged(MapData mapData) {
		if (mapData.isFinished()) {
			Rectangle2D roiBounds = roi.getBounds2D();
			Rectangle2D extent = mapData.getRequest().getExtent();
			Raster raster = mapData.getImage().getRaster();
			int width = raster.getWidth();
			int height = raster.getHeight();
			BufferedImage maskImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g2 = maskImage.createGraphics();
			g2.setTransform(Util.world2image(extent, width, height));
			g2 = new GraphicsWrapped(g2,360,ppd,extent,"maskWrapped");
			try {
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
				g2.setColor(Color.white);
				g2.setStroke(new BasicStroke(1f/ppd));
				g2.draw(roi);
			} finally {
				g2.dispose();
			}
			int[] mask = new int[width*height];
			maskImage.getRaster().getPixels(0, 0, width, height, mask);
			
			Rectangle region = mapData.getRasterBoundsForWorld(roiBounds);
			for (int j = region.y+region.height-1; j >= region.y; j--) {
				for (int i = region.x+region.width-1; i >= region.x; i--) {
					if (mask[j*width+i] != 0) {
						count ++;
						double value = raster.getSampleDouble(i, j, band);
						switch (type) {
						case AVG:
						case SUM:
							sum += value;
							break;
						case MIN:
							min = Math.min(min, value);
							break;
						case MAX:
							max = Math.max(max, value);
							break;
						case STDEV:
							if (count == 1) {
								m = value;
								s = 0;
							} else {
								double delta = value - m;
								m += delta/count;
								s += delta*(value-m);
							}
							break;
						}
					}
				}
			}
			
			if (ch.isFinished()) {
				Object stat = null;
				switch (type) {
				case AVG: stat = sum/count; break;
				case MIN: stat = min; break;
				case MAX: stat = max; break;
				case SUM: stat = sum; break;
				case STDEV: stat = count==1 ? 0 : Math.sqrt(s/(count-1)); break;
				}
				if (stat != null) {
					fc.setAttributes(feature, Collections.singletonMap(field, stat));
				}
				try {
					barrier.await();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	public static class Factory extends FieldFactory<FieldMap> {
		private static final Comparator<MapSource> byTitle = new Comparator<MapSource>() {
			public int compare(MapSource o1, MapSource o2) {
				return o1.getTitle().compareTo(o2.getTitle());
			}
		};
		
		private List<MapSource> sources = new ArrayList<MapSource>();
		
		public Factory(String name, FeatureCollection fc) {
			super(name, FieldMap.class, Double.class);
			// just in case the user gets to this point before
			// the map server responds
			if (MapServerFactory.getMapServers() != null) {
				for (MapServer server: MapServerFactory.getMapServers()) {
					for (MapSource source: server.getMapSources()) {
						if (source.hasNumericKeyword()) {
							sources.add(source);
						}
					}
				}
			}
			Collections.sort(sources, byTitle);
		}
		private static ListComboBoxModel<Integer> getPpdModel(MapSource source) {
			List<Integer> ppdlist = new ArrayList<Integer>();
			for (int ppd = 1; ppd <= source.getMaxPPD(); ppd *= 2) {
				ppdlist.add(ppd);
			}
			return new ListComboBoxModel<Integer>(ppdlist);
		}
		public JPanel createEditor(Field field) {
			final FieldMap f = (FieldMap)field;
			
			final JComboBox ppdCB = new JComboBox();
			final JComboBox sourceCB = new JComboBox(new ListComboBoxModel<MapSource>(sources));
			sourceCB.setSelectedItem(f.source);
			ppdCB.setModel(getPpdModel(f.source));
			ppdCB.setSelectedItem(f.ppd);
			
			ppdCB.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					f.ppd = (Integer)ppdCB.getSelectedItem();
				}
			});
			
			sourceCB.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					f.source = (MapSource)sourceCB.getSelectedItem();
					ppdCB.setModel(getPpdModel(f.source));
					ppdCB.setSelectedIndex(ppdCB.getModel().getSize()-1);
				}
			});
			
			final JComboBox typeCB = new JComboBox(new EnumComboBoxModel<Type>(Type.class));
			typeCB.setSelectedItem(f.type);
			typeCB.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					f.type = (Type)typeCB.getSelectedItem();
				}
			});
			
			JPanel editor = new JPanel(new GridBagLayout());
			int pad = 4;
			Insets in = new Insets(pad,pad,pad,pad);
			editor.add(new JLabel("Map"), new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,pad,pad));
			editor.add(sourceCB, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
			editor.add(new JLabel("PPD"), new GridBagConstraints(0,1,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,pad,pad));
			editor.add(ppdCB, new GridBagConstraints(1,1,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
			editor.add(new JLabel("Type"), new GridBagConstraints(0,2,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,pad,pad));
			editor.add(typeCB, new GridBagConstraints(1,2,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
			editor.add(new JLabel(), new GridBagConstraints(0,3,1,2,0,1,GridBagConstraints.NORTHWEST,GridBagConstraints.VERTICAL,in,pad,pad));
			return editor;
		}
		/** Returns a field to compute the values, using the collection and field if it is necessary to send asynchronous updates */
		public FieldMap createField(FeatureCollection fc, Field f) {
			Type opType = Type.AVG;
			MapSource source = sources.get(0);
			String defaultElevServer = Config.get("threed.default_elevation.server");
			String defaultElevSource = Config.get("threed.default_elevation.source");
			for (MapSource s: sources) {
				if (s.getServer().getName().equals(defaultElevServer) &&
						s.getName().equals(defaultElevSource)) {
					source = s;
					break;
				}
			}
			int ppd = (int)Math.round(Math.pow(2, Math.ceil(Math.log(source.getMaxPPD())/Math.log(2))));
			return new FieldMap(fc, f, getName(), opType, ppd, source, 0);
		}
	}
}
