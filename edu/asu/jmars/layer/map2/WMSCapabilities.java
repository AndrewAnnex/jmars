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


package edu.asu.jmars.layer.map2;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import edu.asu.jmars.LocationManager;
import edu.asu.jmars.Main;
import edu.asu.jmars.util.DebugLog;

// TODO: at some point, this and the WMSLayer class need to handle more of the WMS standard.
// Rules for e.g. property inheritance will go here.

/** Provides an interface to the results of a WMS GetCapabilities request */
public class WMSCapabilities {
	private static DebugLog log = DebugLog.instance();
	
	/** If true, the WMS keywords prefixed by jmarsCategory will provide WMS structure */
	private final boolean jmarsCategories;
	/** The getmap service URI */
	private final URI mapURI;
	/** The service title */
	private final String serverTitle;
	/** The list of map sources */
	private final List<WMSLayer> layers;
	/** The document */
	final Document doc;
	
	/** Returns a WMSCapabilities by parsing WMS GetCapabilities XML from the given reader. */
	public WMSCapabilities(Reader xmlSource) throws DocumentException, IOException, URISyntaxException {
		// Read DOM representation of XML
		SAXReader reader = new SAXReader();
		reader.setEntityResolver(resolver);
		long startTime = System.currentTimeMillis();
		doc = reader.read(xmlSource);
		log.println("Read document (took " + (System.currentTimeMillis() - startTime) + " ms)");
		
		// Parse the DOM to create the properties
		log.println("Parsing document");
		startTime = System.currentTimeMillis();
		jmarsCategories = parseJmarsCategories(doc);
		serverTitle = parseServerTitle(doc);
		mapURI = parseMapURI(doc);
		layers = parseMapSources(doc, jmarsCategories, serverTitle);
		log.println("Fully parsed document (took " + (System.currentTimeMillis() - startTime) + " ms)");
	}
	
	/**
	 * if any Layer element has a keyword prefixed by 'jmarsCategory:', we split
	 * the part after the prefix on '//' to get part of the category after the
	 * server, instead of using the WMS spec's suggested way of getting
	 * hierarchy. If there is is no jmarsCategory-prefixed keyword, we do it the
	 * WMS way.
	 */
	public boolean isJmarsCategories() {
		return jmarsCategories;
	}
	
	private static boolean parseJmarsCategories(Document doc) {
		return doc.asXML().contains("<Keyword>jmarsCategory:");
	}
	
	/** Returns the WMS server's displayable title */
	public String getServerTitle() {
		return serverTitle;
	}
	
	private static String parseServerTitle(Document doc) {
		return doc.selectSingleNode("/WMT_MS_Capabilities/Service/Title/text()").getText();
	}
	
	/** Returns the URI of the WMS GetMap service implementation */
	public URI getMapURI() {
		return mapURI;
	}
	
	private static URI parseMapURI(Document doc) throws URISyntaxException {
		String xpath = "/WMT_MS_Capabilities/Capability/Request/GetMap/DCPType/HTTP/Get/OnlineResource";
		String mapUrlString = doc.selectSingleNode(xpath).valueOf("@xlink:href");
		return new URI(mapUrlString);
	}
	
	public List<WMSLayer> getLayers() {
		return layers;
	}
	
	private static List<WMSLayer> parseMapSources(Document doc, boolean jmarsCategories, String serverTitle) {
		List<WMSLayer> layers = new LinkedList<WMSLayer>();
		List<String> category = new ArrayList<String>(Collections.singletonList(serverTitle));
		List<?> nodes = doc.selectNodes("/WMT_MS_Capabilities/Capability/Layer");
		parseLayers(layers, category, jmarsCategories, nodes);
		return layers;
	}
	
	/**
	 * Gets a MapSource from each Layer element.
	 * Foreach XML element that is a map, get title, name, category, server, and numeric bit.
	 * 
	 * Each Layer with a name is used to create a MapSource.
	 * 
	 * Layers with titles but no names contribute to the category, so that each
	 * MapSource has a category of all non-MapSource Layers from the root to
	 * that Layer.
	 * 
	 * If the 'jmarsCategory' bit is true, then the categories are constructed
	 * as described there.
	 * @param sources
	 * @param category
	 * @param jmarsCategories
	 * @param nodes
	 */
	private static void parseLayers(List<WMSLayer> layers, List<String> category, boolean usingJmarsCategories, List<?> nodes) {
		for (Iterator<?> layerIt = nodes.iterator(); layerIt.hasNext(); ) {
			Node node = (Node)layerIt.next();
			Node title = node.selectSingleNode("Title");
			Node name = node.selectSingleNode("Name");
			Node abstractNode = node.selectSingleNode("Abstract");
			List<?> jmarsCategories = node.selectNodes("KeywordList/Keyword[contains(text(),\"jmarsCategory:\")]");
			Node latLonBox = node.selectSingleNode("LatLonBoundingBox");
			if (name != null) {
				// this XML element is a map
				String[][] nodeCategory;
				if (usingJmarsCategories && jmarsCategories.size() == 0) {
					// if using jmarsCategory keywords, Layer elements without the keyword are skipped
					continue;
				} else if (usingJmarsCategories && jmarsCategories.size() > 0) {
					// if using jmarsCategory keywords, split on '//' to get multiple levels of nesting
					nodeCategory = new String[jmarsCategories.size()][];
					int catpos = 0;
					for (Object o: jmarsCategories) {
						String cat = ((Node)o).getText();
						nodeCategory[catpos++] = cat.substring(cat.indexOf(":") + 1).split("//");
					}
				} else {
					// otherwise copy the current state of the category list
					nodeCategory = new String[][]{category.toArray(new String[0])};
				}
				
				// the numeric keyword is another magic keyword to tell us what
				// format we should request images in; if the keyword is found we
				// will use image/vicar, otherwise image/png
				Node numericKeyword = node.selectSingleNode("KeywordList/Keyword[text()=\"numeric\"]");
				boolean hasNumericKeyword = numericKeyword != null;
				
				String abstractText = abstractNode == null? null: abstractNode.getText();
				
				Rectangle2D latLonRect = null;
				if (latLonBox != null){
					double xmin = Double.parseDouble(latLonBox.selectSingleNode("@minx").getText());
					double xmax = Double.parseDouble(latLonBox.selectSingleNode("@maxx").getText());
					double ymin = Double.parseDouble(latLonBox.selectSingleNode("@miny").getText());
					double ymax = Double.parseDouble(latLonBox.selectSingleNode("@maxy").getText());
					latLonRect = new Rectangle2D.Double();
					latLonRect.setFrameFromDiagonal(xmin, ymin, xmax, ymax);
				}
				
				double[] ignoreValue = null;
				Node ignoreNode = node.selectSingleNode("KeywordList/Keyword[contains(text(),\"NullValue:\")]");
				if (ignoreNode != null) {
					String ignoreText = ignoreNode.getText();
					ignoreText = ignoreText.substring(ignoreText.indexOf(":") + 1);
					String[] parts = ignoreText.split(",");
					ignoreValue = new double[parts.length];
					for (int i = 0; i < parts.length; i++) {
						ignoreValue[i] = Double.parseDouble(parts[i]);
					}
				}
				
				double maxPPD = Collections.max(Arrays.asList(LocationManager.zoomFactors));
				Node maxPPDNode = node.selectSingleNode("KeywordList/Keyword[contains(text(),\"maxPPD:\")]");
				if (maxPPDNode != null) {
					String maxText = maxPPDNode.getText();
					maxPPD = Double.parseDouble(maxText.substring(maxText.indexOf(":") + 1));
				}
				
				// add a new MapSource
				layers.add(new WMSLayer(name.getText(), title.getText(), abstractText,
						nodeCategory, hasNumericKeyword, latLonRect, ignoreValue, maxPPD));
			} else if (title != null) {
				// this XML element is a category node
				
				// process sublayers, giving this title to the category of all
				// sublayers, unless we're using jmarsCategories in which case
				// we stick with the original category only
				category.add(title.getText());
				parseLayers(layers, category, usingJmarsCategories, node.selectNodes("Layer"));
				category.remove(category.size()-1);
			}
		}
	}
	
	/**
	 * Supply the WMS 1.1.1 DTD from inside the jar file so we can parse XML in
	 * offline mode, or when a server references a DTD that does not exist, which
	 * oddly enough, several do...
	 */
	static private EntityResolver resolver = new EntityResolver() {
		public InputSource resolveEntity(String publicId, String systemId) {
			log.println("Resolving entity public: " + publicId + ", system: " + systemId);
			if (isDTDEntity(systemId)) {
				log.println("Using local DTD");
				InputStream in = Main.getResourceAsStream("resources/wms_1_1_1.dtd");
				if (in == null) {
					log.aprintln("Unable to load WMS 1.1.1 DTD, trying remote source");
					try {
						in = new URL(publicId).openStream();
					} catch (Exception e1) {
						log.aprintln("Total failure");
						return null;
					}
				}
				return new InputSource(in);
			}
			return null;
		}
		private boolean isDTDEntity(String suppliedValue) {
			return suppliedValue.endsWith("capabilities_1_1_1.dtd");
		}
	};
	
	public void save(File filename, long modTime) throws IOException {
		FileWriter out = new FileWriter(filename);
		OutputFormat format = OutputFormat.createPrettyPrint();
		XMLWriter writer = new XMLWriter(out, format);
		writer.write(doc);
		writer.flush();
		writer.close();
		if (modTime != 0) {
			filename.setLastModified(modTime);
		}
	}
}

class WMSLayer {
	private String name;
	private String title;
	private String abstractText;
	private String[][] categories;
	private boolean isNumeric; // optional
	private Rectangle2D latLonBBox; // optional
	private double[] ignoreValue; // optional
	private double maxPPD;
	public WMSLayer(String name, String title, String abstractText, String[][] categories,
			boolean isNumeric, Rectangle2D latLonBBox, double[] ignoreValue, double maxPPD) {
		super();
		this.name = name;
		this.title = title;
		this.abstractText = abstractText;
		this.categories = categories;
		this.isNumeric = isNumeric;
		this.latLonBBox = latLonBBox;
		this.ignoreValue = ignoreValue;
		this.maxPPD = maxPPD;
	}
	public String[][] getCategories() {
		return categories;
	}
	public boolean isNumeric() {
		return isNumeric;
	}
	public String getName() {
		return name;
	}
	public String getTitle() {
		return title;
	}
	public String getAbstract(){
		return abstractText;
	}
	public Rectangle2D getLatLonBoundingBox(){
		return latLonBBox;
	}
	public double[] getIgnoreValue() {
		return ignoreValue;
	}
	public double getMaxPPD() {
		return maxPPD;
	}
	/** The layer identity, using the layer 'name' element */
	public boolean equals(Object o) {
		return o instanceof WMSLayer && ((WMSLayer)o).name.equals(name);
	}
	public int hashCode() {
		return name.hashCode();
	}
	/**
	 * The real layer equality test, used to find differences between two
	 * instances of the same layer
	 */
	public boolean reallyEquals(WMSLayer layer) {
		return name.equals(layer.name) &&
			title.equals(layer.title) &&
			((abstractText == null && layer.abstractText == null) ||
			(abstractText != null && abstractText.equals(layer.abstractText))) &&
			Arrays.deepEquals(categories, layer.categories) &&
			isNumeric == layer.isNumeric &&
			((latLonBBox == null && layer.latLonBBox == null) || 
			 (latLonBBox != null && latLonBBox.equals(layer.latLonBBox)));
	}
	public String toString() {
		return "name: " + name + ", title: " + title;
	}
}