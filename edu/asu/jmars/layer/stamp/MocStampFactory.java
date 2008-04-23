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

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.sql.*;
import java.util.StringTokenizer;
import java.net.*;
import javax.swing.*;


public class MocStampFactory
extends StampFactory
{
    private static final DebugLog log = DebugLog.instance();
    private static final String CACHE_FILENAME = Config.get("moc.list_cache");
    private static final String MALIN_STR = "msss.com";
    
    protected static final String MOC_IMAGE_SUBDIR1 = "nonmaps";
    protected static final String MOC_IMAGE_SUBDIR2 = "full_gif_non_map";
    
    private MocStampPool mocStampPool = new MocStampPool(); 
    private MocImageFactory imageFactory = new MocImageFactory();
    
    public MocStampFactory()
    {
        super("Moc",
			  new String[] { "product_id", "spacecraft_clock_start_count",
							 "ctr_lat", "local_time", "downtrack_summing"},
			  new String[] { "ctr_lat", "nw_lat", "ne_lat", "se_lat", "sw_lat"},
			  new String[] { "ctr_lon", "nw_lon", "ne_lon", "se_lon", "sw_lon"}
        );
    }
    
    protected Layer.LView createLView(String name,
                                      SqlStampLayer.SqlParameters parms,
                                      LayerDescription layerDesc,
                                      String[] initialColumns,
                                      Color unsColor
                                      )
    {
        return createLView(name,
                           getLayer(parms, layerDesc),
                           initialColumns,
                           unsColor);
    }
    
    public Layer.LView recreateLView(SerializedParameters parmBlock)
    {
        Layer.LView view = null;
        
        log.println("recreateLView called");
        
        if (parmBlock != null &&
            parmBlock instanceof StampLView.StampParameters)
        {
            StampLView.StampParameters parms = (StampLView.StampParameters) parmBlock;
            parms.sqlParms = getCacheSafeSqlParms(parms.sqlParms);
            
            view = createLView(parms.name,
                               getLayer(parms.sqlParms, parms.layerDesc),
                               parms.initialColumns,
                               parms.unsColor,
                               parms.filColor);
            view.setVisible(true);
            
            log.println("successfully recreated StampLView");
        }
        
        return view;
    }
    
    private Layer.LView createLView(String name,
                                    Layer parent,
                                    String[] initialColumns,
                                    Color unsColor)
    {
        return createLView(name, parent, initialColumns, unsColor,
                           new Color(unsColor.getRGB() & 0xFFFFFF, true));
    }
    
    private Layer.LView createLView(String name,
                                    Layer parent,
                                    String[] initialColumns,
                                    Color unsColor,
                                    Color filColor)
    {
        Layer.LView view = 
            new StampLView(name,
                           parent,
                           initialColumns,
                           unsColor,
                           filColor,
                           MocStampFactory.this)
            {
                protected void addSelectedStamps(final int[] rows)
                {
                    addSelectedStamps(rows, 0, 0, 0);
                }
                
                // Any non-null stamp name is treated as valid.
                // We rely on the filtering performed in creating
                // the stamp list to produce valid MOC stamps since
                // Malin controls the naming scheme.
                protected boolean validStampName(String name)
                {
                    boolean valid = false;
                    
                    if (name != null)
                        valid = true;
                    
                    return valid;
                }
                
                protected MyFilledStampFocus createFilledStampFocus()
                {
                    final StampLView view = this;
                    
                    // Set up direct access to the stamp factory instance's
                    // image factory via StampLView's reference to the
                    // stamp factory.  We do this because Java's mechanism
                    // for anonymous class construction means that the
                    // outer-class scope is not available yet--this method
                    // is being called from within StampLView's constructor.
                    MocStampFactory factory = (MocStampFactory)originatingFactory;
                    
                    return new MyFilledStampFocus(this, factory.imageFactory)
                    {
                        {
                            if (btnRotateImage != null)
                                btnRotateImage.setVisible(true);
                            if (btnHFlip != null)
                                btnHFlip.setVisible(true);
                            if (btnVFlip != null)
                                btnVFlip.setVisible(true);
                            
                            if (dropBand != null)
                                dropBand.setVisible(false);
                            if (btnViewPds != null)
                                btnViewPds.setVisible(false);
                        }
                        
                        protected StampImage load(Stamp s)
                        {
                            // Note: code below references "imageFactory" member within
                            // the FilledStampFocus scope.
                            MocImageFactory factory = (MocImageFactory)imageFactory;
                            StampImage img = factory.load(s, view);
                            if (img != null) {
                                if (((MocStamp) s).isWideAngle())
                                    ((MocImage)img).setWideAngle(true);
                                if (((MocStamp) s).isMalin())
                                    ((MocImage)img).setMalin(true);
                            }
                            
                            return img;
                        }
                        
                        public void addStamp(Stamp s)
                        {
                            // Always render stamp after adding by setting band
                            // selection to zero.
                            addStamp(s, true, false, false, false, 0);
                        }
                    };
                }
            };
            
        return view;
    }
    
    public Stamp createStamp(ResultSet rs)
    throws SQLException
    {
        int colCount = rs.getMetaData().getColumnCount();
        Object[] fields = new Object[colCount];
        for(int i=0; i<colCount; i++)
            fields[i] = rs.getObject(i+1);
        
        boolean wideAngle = false;
        if ("MOC-WA".equalsIgnoreCase(rs.getString("instrument_name")))
            wideAngle = true;
        
        return mocStampPool.getStamp(
                                     rs.getString("product_id"),
                                     (360 - rs.getDouble("nw_lon")) % 360,
                                     rs.getDouble("nw_lat"),
                                     (360 - rs.getDouble("ne_lon")) % 360,
                                     rs.getDouble("ne_lat"),
                                     (360 - rs.getDouble("sw_lon")) % 360,
                                     rs.getDouble("sw_lat"),
                                     (360 - rs.getDouble("se_lon")) % 360,
                                     rs.getDouble("se_lat"),
                                     fields,
                                     wideAngle);
    }
    
    /**
     * Clears out projection-related information from stamps
     * in stamp pool.
     */
    synchronized void reprojectStampPool()
    {
        mocStampPool.reprojectStamps();
    }

    
    private class MocStampPool extends StampPool
    {
        /**
         * Preferred variant of normal {@link #getStamp} method
         * used to retrieve/create stamps.
         */
        synchronized protected Stamp getStamp(String id,
                                              double nwx, double nwy,
                                              double nex, double ney,
                                              double swx, double swy,
                                              double sex, double sey,
                                              Object[] data,
                                              boolean wideAngle)
        {
            Stamp s = getStamp(id);
            
            if (s == null) {
                s = createStamp(id,
                                nwx, nwy,
                                nex, ney,
                                swx, swy,
                                sex, sey,
                                data,
                                wideAngle);
                stampMap.put(id, s);
            }
            
            return s;
        }
        
        /**
         * Creates MOC stamp instance using parameters.
         */
        protected Stamp createStamp(String id,
                                    double nwx, double nwy,
                                    double nex, double ney,
                                    double swx, double swy,
                                    double sex, double sey,
                                    Object[] data)
        {
            return new MocStamp(id,
                                nwx, nwy,
                                nex, ney,
                                swx, swy,
                                sex, sey,
                                data);
        }
        
        /**
         * Creates MOC stamp instance using parameters.
         */
        protected Stamp createStamp(String id,
                                    double nwx, double nwy,
                                    double nex, double ney,
                                    double swx, double swy,
                                    double sex, double sey,
                                    Object[] data,
                                    boolean wideAngle)
        {
            return new MocStamp(id,
                                nwx, nwy,
                                nex, ney,
                                swx, swy,
                                sex, sey,
                                data,
                                wideAngle);
        }
    }
    
    
    private class MocStamp extends Stamp
    {
        private boolean wideAngle = false;
        private boolean malin = false;
        
        MocStamp(String id)
        {
            this(id,
                 0, 0,
                 0, 0,
                 0, 0,
                 0, 0,
                 null,
                 false);
        }
        
        MocStamp(String id,
                 double nwx, double nwy,
                 double nex, double ney,
                 double swx, double swy,
                 double sex, double sey,
                 Object[] data)
        {
            this(id, 
                 nwx, nwy,
                 nex, ney,
                 swx, swy,
                 sex, sey,
                 data,
                 false);
        }
        
        MocStamp(String id,
                 double nwx, double nwy,
                 double nex, double ney,
                 double swx, double swy,
                 double sex, double sey,
                 Object[] data,
                 boolean wideAngle)
        {
            super(id, 
                  nwx, nwy,
                  nex, ney,
                  swx, swy,
                  sex, sey,
                  data);
            
            this.wideAngle = wideAngle;
            
            String url = getUrl(Config.get("moc.gallery.data"), MOC_IMAGE_SUBDIR1) + ".gif";
            if(url != null) {
                try {
                    pdsImageUrl = new URL(url);
                }
                catch(MalformedURLException e) {
                    log.println(e);
                    log.aprintln("INVALID URL GENERATED: " + url);
                }
                
                if (url.indexOf(MALIN_STR) >= 0)
                    malin = true;
            }
        }
        
        // Returns true if stamp is a wide-angle MOC image; false, otherwise.
        public boolean isWideAngle()
        {
            return wideAngle;
        }
        
        // Returns true if stamp is from Malin's MOC image gallery; false, otherwise.
        public boolean isMalin()
        {
            return malin;
        }
        
        public String getBrowseUrl()
        {
            return Config.get("moc.gallery.browse") + id;
        }
        
        // Returns path to stamp image or browse webpage based on
        // stamp ID and specified image subdirectory (indicating 
        // image, webpage, etc.).  Works for Malin's website (msss.com)
        // and ASU's own images derived from the MOC PDS data.
        //
        public String getUrl(String host, String imageDir)
        {
            String relPath;
            String phase;
            String num;
            boolean needImage = false;
            
            try {
                if (imageDir == null ||
                    id == null)
                    return null;
                else if (imageDir.equals(MOC_IMAGE_SUBDIR1))
                    needImage = true;
                
                // Handle stamp IDs with phase delimited by "-", i.e.,
                // "SPO-1", "SPO-2", "AB-1", etc.
                int hyphen = id.indexOf("-");
                if(hyphen != -1)
                {
                    String phasePrefix = id.substring(0, hyphen);
                    String phaseNum = id.substring(hyphen + 1, hyphen + 2);
                    
                    if (phasePrefix.equalsIgnoreCase("SPO"))
                        phasePrefix = "SP";
                    
                    phase = phasePrefix + phaseNum;
                    num = stripToNumbers(id.substring(hyphen + 1 + phaseNum.length()));
                }
                else
                {
                    // Handle all other stamp IDs, with phase delimited by "/"
                    int slash = id.indexOf("/");
                    if(slash == -1) {
                        log.aprintln("BAD MOC STAMP ID: " + id);
                        return  null;
                    }
                    
                    phase = id.substring(0, slash);
                    num = id.substring(slash + 1);
                }
                
                if(phase.startsWith("E"))
                {
                    int phaseN = Integer.parseInt(phase.substring(1));
                    
                    if (needImage)
                        imageDir = MOC_IMAGE_SUBDIR2;
                    
                    if (phaseN >= 1 && phaseN <= 6) {
                        relPath = "e01_e06/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 7 && phaseN <= 12) {
                        relPath = "e07_e12/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 13 && phaseN <= 18) {
                        relPath = "e13_e18/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 19 && phaseN <= 23) {
                        relPath = "e19_r02/" + imageDir + "/" + phase + "/";
                    } else {
                        return null;
                    }
                }
                else if(phase.startsWith("R"))
                {
                    int phaseN = Integer.parseInt(phase.substring(1));
                    
                    if (needImage)
                        imageDir = MOC_IMAGE_SUBDIR2;
                    
                    if (phaseN >= 1 && phaseN <= 2) {
                        relPath = "e19_r02/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 3 && phaseN <= 9) {
                        relPath = "r03_r09/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 10 && phaseN <= 15) {
                        relPath = "r10_r15/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 16 && phaseN <= 21) {
                        relPath = "r16_r21/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 22 && phaseN <= 23) {
                        relPath = "r22_s04/" + imageDir + "/" + phase + "/";
                    } else {
                        return null;
                    }
                }
                else if(phase.compareTo("M07") < 0 || 
                        phase.startsWith("SP") || 
                        phase.startsWith("AB") || 
                        phase.startsWith("FHA") || 
                        phase.startsWith("CAL")) {
                    relPath = "ab1_m04/" + imageDir + "/";
                }
                else if(phase.startsWith("S"))
                {
                    int phaseN = Integer.parseInt(phase.substring(1));
                    
                    if (needImage)
                        imageDir = MOC_IMAGE_SUBDIR2;
                    
                    if (phaseN >= 0 && phaseN <= 4) {
                        relPath = "r22_s04/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 5 && phaseN <= 10) {
                        relPath = "s05_s10/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 11 && phaseN <= 16) {
                    	relPath = "s11_s16/" + imageDir + "/" + phase + "/";
                    } else if (phaseN >= 17 && phaseN <= 22) {
                    	relPath = "s17_s22/" + imageDir + "/" + phase + "/";
                    } else {
                        return null;
                    }
                }
                else if(phase.compareTo("M13") < 0)
                {
                    relPath = "m07_m12/" + imageDir + "/" + phase + "/";
                }
                else if(phase.compareTo("M19") < 0)
                {
                    if (needImage)
                        imageDir = MOC_IMAGE_SUBDIR2;
                    
                    relPath = "m13_m18/" + imageDir + "/" + phase + "/";
                }
                else if(phase.compareTo("M24") < 0)
                {
                    if (needImage)
                        imageDir = MOC_IMAGE_SUBDIR2;
                    
                    relPath = "m19_m23/" + imageDir + "/" + phase + "/";
                }
                else
                {
                    return  null;
                }
                
                return host + relPath + phase + num;
            }
            catch (Exception e) {
                log.aprintln("Exception for stamp ID " + id + " and imageDir = " + imageDir);
                log.aprintln(e);
            }
            
            return null;
        }
        
        private String stripToNumbers(String str)
        {
            if (str == null)
                return null;
            
            StringBuffer buf = new StringBuffer();
            if (buf != null) {
                for (int i=0; i < str.length(); i++) {
                    char c = str.charAt(i);
                    if (Character.isDigit(c))
                        buf.append(c);
                }
            }
            
            return buf.toString();
        }
    }

    /**
     * Returns wrapper which can be used to get user input specifying what
     * kind of stamp layer to create, e.g., SQL database field value
     * restrictions, etc.
     * 
     * @param container Container to be wrapped and have field components
     * added to it.
     * 
     * @param layerDesc Input description of layer to create; used to
     * initialize field values in wrapped container.  Contains a list of 
     * field names, and a corresponding list of field values.  If 
     * <code>null</code>, then default values are used for wrapped container 
     * fields.
     * 
     * @param includeExtras If <code>true</code>, then a stamp-color
     * {@link ColorCombo} and a custom layer name text box are added
     * to the display components in the container in addition to
     * those in this instance's field list; if <code>false</code>, 
     * then these components are excluded.
     * 
     * @return Dialog which may be displayed and from which filled-in
     * layer description and corresponding SQL layer parameters can be
     * retrieved.
     */
    public AddLayerWrapper createWrapper(Container container, LayerDescription layerDesc, boolean includeExtras)
    {
        return new MocAddLayerWrapper(container, layerDesc, includeExtras);
    }
    
    private class MocAddLayerWrapper extends StampFactory.AddLayerWrapper
    {
        // Layer description field name keys
        public static final String IMAGE_TYPE        = "image_type";
        public static final String MIN_ORBIT         = "min_orbit";
        public static final String MAX_ORBIT         = "max_orbit";
        public static final String MIN_LAT           = "min_latitude";
        public static final String MAX_LAT           = "max_latitude";
        public static final String MIN_LON           = "min_longitude";
        public static final String MAX_LON           = "max_longitude";
        public static final String EXTRA_SQL         = "extra_SQL";
        public static final String DOWNTRACK_SUMMING = "downtrack_summing";
        
        public static final String ALL_DOWNTRACK_SUM = "ALL";
        
        // Field display components
        protected String typeNarr = "Only narrow-angle";
        protected String typeWide = "Only wide-angle";
        protected String typeBoth = "Both types";
        private JComboBox dropType = new JComboBox(new String[] { typeNarr,
                                                                  typeWide,
                                                                  typeBoth } );
        private JTextField txtMinOrbit = new PasteField();
        private JTextField txtMaxOrbit = new PasteField();
        private JTextField txtMinLat = new PasteField();
        private JTextField txtMaxLat = new PasteField();
        private JTextField txtMinLon = new PasteField();
        private JTextField txtMaxLon = new PasteField();
        private JTextField txtExtraSql = new PasteField();
        
        private JCheckBox chkDowntrackAll = new JCheckBox("All", true);
        private JCheckBox chkDowntrack1 = new JCheckBox("1", true); 
        private JCheckBox chkDowntrack2 = new JCheckBox("2", true);
        private JCheckBox chkDowntrack3 = new JCheckBox("3", true); 
        private JCheckBox chkDowntrack4 = new JCheckBox("4", true);
        private JCheckBox chkDowntrack5 = new JCheckBox("5", true); 
        private JCheckBox chkDowntrack6 = new JCheckBox("6", true);
        private JCheckBox chkDowntrack8 = new JCheckBox("8", true); 
        private JCheckBox chkDowntrack13 = new JCheckBox("13", true);
        private JCheckBox chkDowntrack27 = new JCheckBox("27", true);
        
        private Object[] Fields;
        
        public MocAddLayerWrapper(Container container, LayerDescription layerDesc,
                                  boolean includeExtras)
        {
            super(container, layerDesc);
            if (container != null) {
                buildContainer(container, includeExtras);
            
                // Now that display components have been built in the container,
                // let's set them with whatever values are in the layer
                // description (if any).
                updateLayerDescription(layerDesc);
            }
        }
        
        /**
         * Returns list of field description pairs for adding into
         * a {@link Container} when called via {@link #buildContainer} 
         * method.  Each pair must consist of either a Component
         * instance or a String instance in any combination; the
         * pair elements will be displayed in left-to-right order.
         * 
         * @return List containing pairs of label strings (usually)
         * and components to add as description/field
         * pairs to the container.  However, any combination of strings
         * and components are allowed.
         * 
         * @see StampFactory#buildContainer
         */
        protected Object[] getFieldList()
        {
            Object[] fields = null;
            
            if (Main.isInternal()) {
                JPanel downtrackPanel = new JPanel();
                JPanel subPanel1 = new JPanel();
                JPanel subPanel2 = new JPanel();
                JPanel subPanel3 = new JPanel();

                subPanel1.add(chkDowntrackAll);
                subPanel1.add(chkDowntrack1);
                subPanel1.add(chkDowntrack2);
                subPanel1.add(chkDowntrack3);
                
                subPanel2.add(chkDowntrack4);
                subPanel2.add(chkDowntrack5);
                subPanel2.add(chkDowntrack6);
                subPanel2.add(chkDowntrack8);
                
                subPanel3.add(chkDowntrack13);
                subPanel3.add(chkDowntrack27);
                
                downtrackPanel.setLayout(new BoxLayout(downtrackPanel, BoxLayout.Y_AXIS));
                downtrackPanel.add(subPanel1);
                downtrackPanel.add(subPanel2);
                downtrackPanel.add(subPanel3);
                downtrackPanel.add(Box.createVerticalStrut(10));
                
                // Set up a label that will be properly vertically spaced with
                // the whole above mess after TOP alignment (see implementation
                // for getLayout() method below...).
                JPanel downtrackLabelPanel = new JPanel();
                downtrackLabelPanel.setLayout(new BoxLayout(downtrackLabelPanel, BoxLayout.Y_AXIS));
                downtrackLabelPanel.add(Box.createVerticalStrut(9));
                downtrackLabelPanel.add(new JLabel("Downtrack Summing", SwingConstants.RIGHT));
                
                fields = new Object[] {
                                       "Min orbit:", txtMinOrbit,
                                       "Max orbit:", txtMaxOrbit,
                                       "Min latitude:", txtMinLat,
                                       "Max latitude:", txtMaxLat,
                                       "Westernmost longitude:", txtMinLon,
                                       "Easternmost longitude:", txtMaxLon,
                                       downtrackLabelPanel, downtrackPanel, 
                                       "Extra SQL condition:", txtExtraSql };
            } else {
                fields = new Object[] {
                                       "Min orbit:", txtMinOrbit,
                                       "Max orbit:", txtMaxOrbit,
                                       "Min latitude:", txtMinLat,
                                       "Max latitude:", txtMaxLat,
                                       "Westernmost longitude:", txtMinLon,
                                       "Easternmost longitude:", txtMaxLon,
                                       "Extra SQL condition:", txtExtraSql };
            }
            
            return fields;
        }
        
        /**
         * Returns a custom layout manager designed to better handle
         * arrangement of the field parameters for the MOC layer.
         */
        protected LayoutManager getLayout(int rows, int columns, int hgap, int vgap)
        {
            // Since non-internal versions do not currently include the
            // downtrack summing parameters, don't bother with a custom
            // layout.  (The results with the custom layout were bad
            // anyway without the other fields....)
            if (!Main.isInternal())
                return null;
            
            // To address some layout issues with this manager, we add
            // one other, empty row to the requested space.
            SGLayout layout = new SGLayout(rows, columns, hgap, vgap);
            layout.setColumnScale(1, 1.25);
            
            // For the internal version, we must use a vertically larger row
            // for the row which includes the downtrack summing options;
            // see getFieldList() implementation above.
            if (Main.isInternal()) {
                layout.setRowScale(6, 5.0);
                layout.setAlignment(6, 0, SGLayout.RIGHT, SGLayout.TOP);
            }
            
            return layout;
        }
        
        /**
         * Returns filled-in field values and corresponding field
         * names after dialog has been displayed and interacted with
         * by user. 
         * 
         * @return Returns layer description.  Field values may be
         * empty if user did not complete necessary entries to create 
         * layer; however, all field names are always supplied.
         * 
         * @see edu.asu.jmars.layer.stamp.StampFactory.AddLayerWrapper#getLayerDescription()
         */
        public LayerDescription getLayerDescription()
        {
            LayerDescription layerDesc = new LayerDescription();
            
            StringBuffer downtrackBuf = new StringBuffer();
            if (chkDowntrackAll.isSelected())
                downtrackBuf.append(ALL_DOWNTRACK_SUM);
            else
            {
                if (chkDowntrack1.isSelected()) downtrackBuf.append("1 ");
                if (chkDowntrack2.isSelected()) downtrackBuf.append("2 ");
                if (chkDowntrack3.isSelected()) downtrackBuf.append("3 ");
                if (chkDowntrack4.isSelected()) downtrackBuf.append("4 ");
                if (chkDowntrack5.isSelected()) downtrackBuf.append("5 ");
                if (chkDowntrack6.isSelected()) downtrackBuf.append("6 ");
                if (chkDowntrack8.isSelected()) downtrackBuf.append("8 ");
                if (chkDowntrack13.isSelected()) downtrackBuf.append("13 ");
                if (chkDowntrack27.isSelected()) downtrackBuf.append("27 ");
            }
            
            layerDesc.dlgFieldNames = new String[] {IMAGE_TYPE, DOWNTRACK_SUMMING,
                                                    MIN_ORBIT, MAX_ORBIT,
                                                    MIN_LAT, MAX_LAT,
                                                    MIN_LON, MAX_LON,
                                                    EXTRA_SQL
                                                    }; 
         
            layerDesc.dlgFieldValues = new Object[] {dropType.getSelectedItem(), downtrackBuf.toString(), 
                                                     txtMinOrbit.getText(), txtMaxOrbit.getText(),
                                                     txtMinLat.getText(), txtMaxLat.getText(),
                                                     txtMinLon.getText(), txtMaxLon.getText(),
                                                     txtExtraSql.getText()
                                                    };
            return layerDesc;
        }
        
        /**
         * Sets the layer description and updates displayed field values 
         * in wrapped container.  The field names should probably not
         * change, i.e., behavior is undefined in this case.
         * 
         * <p>NOTE: Must only be called after {@link #buildContainer}
         * method has been called to complete initialization of wrapped
         * container.
         */
        public void updateLayerDescription(LayerDescription layerDesc)
        {
            // Set and validate layer description.
            super.updateLayerDescription(layerDesc);
            
            // For an empty layer description, reset field to defaults 
            // and return.
            if (layerDesc == null ||
                layerDesc.dlgFieldNames == null ||
                layerDesc.dlgFieldValues == null)
            {
                dropType.setSelectedItem(typeNarr);
                txtMinOrbit.setText("");
                txtMaxOrbit.setText("");
                txtMinLat.setText("");
                txtMaxLat.setText("");
                txtMinLon.setText("");
                txtExtraSql.setText("");
                
                setAllSumming(true);
                return;
            }
            
            // Set field values according to layer description.  For
            // maximum compatibility over time with older saved ".jmars" files
            // as software and fields change, fields may appear in any
            // order and may be freely omitted from layer description
            // (if latter, then default values are used).
            for (int i=0; i < layerDesc.dlgFieldNames.length; i++)
            {
                String field = layerDesc.dlgFieldNames[i];
                Object value = layerDesc.dlgFieldValues[i];
                
                if (field == null)
                    continue;
                else if (IMAGE_TYPE.equals(field))
                {
                    // The image type field is currently only used with internal JMARS version.
                    if (Main.isInternal())
                    {
                        if (value == null ||
                            !Main.isInternal())
                            dropType.setSelectedItem(typeNarr);
                        else if (value instanceof String)
                            dropType.setSelectedItem( ((String)value).intern() );
                        else
                            log.aprintln("Non-string value for field '" + field + "' :" + value);
                    }
                }
                else if (MIN_ORBIT.equals(field)) {
                    if (value == null)
                        txtMinOrbit.setText("");
                    else
                        txtMinOrbit.setText(value.toString());
                }
                else if (MAX_ORBIT.equals(field)) {
                    if (value == null)
                        txtMaxOrbit.setText("");
                    else
                        txtMaxOrbit.setText(value.toString());
                }
                else if (MIN_LAT.equals(field)) {
                    if (value == null)
                        txtMinLat.setText("");
                    else
                        txtMinLat.setText(value.toString());
                }
                else if (MAX_LAT.equals(field)) {
                    if (value == null)
                        txtMaxLat.setText("");
                    else
                        txtMaxLat.setText(value.toString());
                }
                else if (MIN_LON.equals(field)) {
                    if (value == null)
                        txtMinLon.setText("");
                    else
                        txtMinLon.setText(value.toString());
                }
                else if (MAX_LON.equals(field)) {
                    if (value == null)
                        txtMaxLon.setText("");
                    else
                        txtMaxLon.setText(value.toString());
                }
                else if (EXTRA_SQL.equals(field)) {
                    if (value == null)
                        txtExtraSql.setText("");
                    else
                        txtExtraSql.setText(value.toString());
                }
                else if (Main.isInternal() &&
                         DOWNTRACK_SUMMING.equals(field))
                {
                    // For internal version only: set Downtrack Summing checkboxes
                    // according to settings.
                    if (value == null)
                        setAllSumming(true);
                    else if (!(value instanceof String))
                        log.aprintln("Non-string value for field '" + field + "' :" + value);
                    else
                    {
                        String downStr = ((String) value).toUpperCase();
                        
                        if (downStr.indexOf(ALL_DOWNTRACK_SUM) >= 0)
                            setAllSumming(true);
                        else 
                        {
                            StringTokenizer tokenizer = new StringTokenizer(downStr);
                            setAllSumming(false);
                            
                            while (tokenizer.hasMoreTokens()) 
                            {
                                String token = tokenizer.nextToken().trim();

                                try {
                                    int summing = Integer.parseInt(token);
                                    
                                    switch (summing)
                                    {
                                    case 1:
                                        chkDowntrack1.setSelected(true);
                                        break;
                                    case 2:
                                        chkDowntrack2.setSelected(true);
                                        break;
                                    case 3:
                                        chkDowntrack3.setSelected(true);
                                        break;
                                    case 4:
                                        chkDowntrack4.setSelected(true);
                                        break;
                                    case 5:
                                        chkDowntrack5.setSelected(true);
                                        break;
                                    case 6:
                                        chkDowntrack6.setSelected(true);
                                        break;
                                    case 8:
                                        chkDowntrack8.setSelected(true);
                                        break;
                                    case 13:
                                        chkDowntrack13.setSelected(true);
                                        break;
                                    case 27:
                                        chkDowntrack27.setSelected(true);
                                        break;
                                    default:
                                        log.aprintln("bad downtrack summing value: " + token);
                                        break;
                                    }
                                }
                                catch (NumberFormatException e) {
                                    log.aprintln("bad downtrack summing value: " + token);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        private void setAllSumming(boolean selected)
        {
            chkDowntrackAll.setSelected(selected);
            chkDowntrack1.setSelected(selected);
            chkDowntrack2.setSelected(selected);
            chkDowntrack3.setSelected(selected);
            chkDowntrack4.setSelected(selected);
            chkDowntrack5.setSelected(selected);
            chkDowntrack6.setSelected(selected);
            chkDowntrack8.setSelected(selected);
            chkDowntrack13.setSelected(selected);
            chkDowntrack27.setSelected(selected);
        }
        
        public String getDefaultName()
        {
            Object type = dropType.getSelectedItem();
            if (type == typeBoth)      return "MOC Stamps";
            else if (type == typeNarr) return "MOC-NA Stamps";
            else if (type == typeWide) return "MOC-WA Stamps";

            // Should never happen
            log.aprintln("INVALID dropType value: " + type);
            return  "Stamps [INVALID NAME]";
        }
        
        public String getSql()
        {
            String mocIdxFields = 
            	"volume_id, " + 
				"file_specification_name, " + 
				"moc_idx.product_id, " + 
				"image_time, " + 
				"instrument_name, " + 
				"filter_name, " + 
				"line_samples, " + 
				"lines_, " +
				"crosstrack_summing, " +       
				"downtrack_summing, " +      
				"scaled_pixel_width, " +   
				"pixel_aspect_ratio, " +   
				"emission_angle, " +       
				"incidence_angle, " +     
				"phase_angle, " +         
				"mission_phase_name, " + 
				"spacecraft_clock_start_count, " + 
				"spacecraft_altitude, " + 
				"slant_distance, " +      
				"usage_note, " + 
				"north_azimuth, " +       
				"solar_distance, " +      
				"solar_longitude, " +     
				"local_time, " +          
				"image_skew_angle, " +    
				"rationale_desc, " + 
				"data_quality_desc, " + 
				"orbit_number";
             
             String mocGeometryFields = 
             	"ctr_lon, ctr_lat, " +
				"nw_lon, nw_lat, " +
				"ne_lon, ne_lat, " +
				"sw_lon, sw_lat, " +
				"se_lon, se_lat, " +
				"coll_date, " +
				"touched";

            return getSql("select " +
                          mocIdxFields + ", " +
                          mocGeometryFields +
                          " from moc_idx inner join moc_geometry on " +
                          "moc_idx.product_id=moc_geometry.product_id and " +
						  "not ifnull(ignore_entry, 0) " +
                          "where target_name = 'MARS'");
        }
        
        public String getCacheSql()
        {
            // Internal users don't use a cached MOC stamp database since
            // it is assumed to be unnecessary (good network performance and
            // fewer number of users than for other scenarios such as the public
            // JMARS version).
            //
            // Also, check whether free-form extra SQL conditions were applied;
            // if so, we can't use the cached stamp database data.
            if (Main.isInternal() ||
                !txtExtraSql.getText().equals(""))
                return null;
            else
                // Note that because the stamp cache file already restricts the 
                // MOC stamp list to those with target_name = 'MARS' and 
                // ignore_entry = 0, there is no need to apply those conditions
                // here (indeed, those fields don't even appear in the import
                // cache file anymore).
                //
                // The restriction to wide-angle MOC images is kept as a precaution
                // since the desire to support this is somewhat in flux and
                // may change more rapidly than the row content in the cache file.
                return getSql("select * from " + getCacheTableName() +
                              " where instrument_name != 'MOC-WA'");
        }
        
        public String getCacheTableName()
        {
            return "cached_moc";
        }
        
        /**
         ** Cached MOC stamp table and data are based on the following query
         ** used to dump the data:
         **
         ** select [list-of-fields-in-desired-column-order] from moc_idx inner join moc_geometry 
         ** on moc_idx.product_id=moc_geometry.product_id and not ifnull(ignore_entry, 0) 
         ** where instrument_name != 'MOC-WA' and target_name = 'MARS';
         **
         ** The selected fields and their column order must be kept in sync with the
         ** "util/make_moc_cache.pl" script that is used to generate the import cache file. 
         **/
        public String getCacheTableFieldsSql()
        {
            // Note the omission of size limits on integer fields from original MySQL table
            // definitions below.  Also, 'float(size,precision)' types have been changed to
            // 'double' declarations.  HSQLDB does not allow these as of 1.7.2 version since
            // these restrictions are not enforced, and it converts both float and double
            // SQL types to the Java double type.
            String sql = "volume_id char(9) default NULL, " + 
                         "file_specification_name char(19) default NULL, " + 
						 "product_id char(12) default '' NOT NULL, " + 
						 "image_time char(22) default NULL, " + 
						 "instrument_name char(6) default NULL, " + 
						 "filter_name char(4) default NULL, " + 
						 "line_samples int default NULL, " +              // Was:  int(4) 
						 "lines_ int default NULL, " +                    // Was:  int(6)
						 "crosstrack_summing int default NULL, " +        // Was:  int(2)
						 "downtrack_summing int default NULL, " +         // Was:  int(2)
						 "scaled_pixel_width double default NULL, " +     // Was:  float(8,2) 
						 "pixel_aspect_ratio double default NULL, " +     // Was:  float(6,2) 
						 "emission_angle double default NULL, " +         // Was:  float(6,2) 
						 "incidence_angle double default NULL, " +        // Was:  float(6,2)
						 "phase_angle double default NULL, " +            // Was:  float(6,2)
						 "mission_phase_name char(10) default NULL, " + 
						 "spacecraft_clock_start_count char(15) default NULL, " + 
						 "spacecraft_altitude double default NULL, " +       // Was:  float(7,2)
						 "slant_distance double default NULL, " +            // Was:  float(7,2)
						 "usage_note char(1) default NULL, " + 
						 "north_azimuth double default NULL, " +             // Was:  float(6,2)
						 "solar_distance double default NULL, " +            // Was:  float(11,1)
						 "solar_longitude double default NULL, " +           // Was:  float(6,2)
						 "local_time double default NULL, " +                // Was:  float(5,2)
						 "image_skew_angle double default NULL, " +          // Was:  float(5,1)
						 "rationale_desc char(80) default NULL, " + 
						 "data_quality_desc char(6) default NULL, " + 
						 "orbit_number int default NULL, " +                 // Was:  int(5)
						 "ctr_lon double default NULL, " +
						 "ctr_lat double default NULL, " +
						 "nw_lon double default NULL, " +
						 "nw_lat double default NULL, " +
						 "ne_lon double default NULL, " +
						 "ne_lat double default NULL, " +
						 "sw_lon double default NULL, " +
						 "sw_lat double default NULL, " +
						 "se_lon double default NULL, " +
						 "se_lat double default NULL, " +
						 "coll_date datetime default NULL, " +
						 "touched char(14) NOT NULL, " +          // Note: is really "timestamp(14)", but HSQLDB choked on MYSQL outfile
						 "UNIQUE (product_id) ";
            
            // Internal users don't use a cached MOC stamp database
            // since it is unnecessary (usually there are no significant 
            // performance issues).
            if(Main.isInternal())
                return null;
            else
                return sql;
        }
        
        /**
         ** Should return the names of sql table fields needing indexes
         ** to speed up search.  These must match the table created to
         ** cache the SQL-based stamp data.  Only used for secondary (non-primary) 
         ** keys on single columns.
         **
         ** @see #getCacheTableName
         **/
        public String[] getCacheIndexFields()
        {
            return new String[] {"ctr_lat",
                                 "orbit_number",
                                 "touched"
                                 };
        }
        
        /**
         ** Returns name of the primary key field for stamps in 
         ** the SQL database (for whichever table contains this).
         **/
        public String getPrimaryKeyName()
        {
            return "product_id";
        }
        
        /**
         ** Returns name of the orbit field for stamps in the SQL
         ** database (for whichever table contains this).
         **/
        public String getOrbitFieldName()
        {
            return "orbit_number";
        }
        
        /**
         ** Returns name of the last modification time field for stamps 
         ** in the server database (for whichever table contains this).
         ** This field contains a timestamp for the last time a given
         ** record was updated.
         ** <p>
         ** If more than one table in the primary SQL query for this
         ** factory contains such a field, the table name must be
         ** included as part of the field name.
         **
         ** @see #getSql
         **/
        public String getServerLastModFieldName()
        {
            return "touched";
        }

        /**
         ** Returns name of the last modification time field for stamps 
         ** in the cache database (for whichever table contains this).
         ** This field contains a timestamp for the last time a given
         ** record was updated.
         ** <p>
         ** If more than one table in the cache SQL query for this
         ** factory contains such a field, the table name must be
         ** included as part of the field name (not typical, since
         ** the cache database is modeled as a single integrated table).
         **
         ** @see #getCacheSql
         **/
        public String getCacheLastModFieldName()
        {
            return "touched";
        }

        public String getImportCacheFileName()
        {
            return CACHE_FILENAME;
        }
        
        private String getSql(String sql)
        {
            // type
            if (Main.isInternal()) {
                Object type = dropType.getSelectedItem();
                if     (type == typeBoth) ;
                else if(type == typeNarr) sql += " and instrument_name='MOC-NA'";
                else if(type == typeWide) sql += " and instrument_name='MOC-WA'";
                else
                    log.aprintln("INVALID dropType value: " + type);
                
                // Downtrack summing restrictions
                Object triggeredOR =
                    new Object()
                    {
                        private int count = 0;
                        
                        public String toString()
                        {
                            if (count++ < 1)
                                return "";
                            else
                                return " or ";
                        }
                    };
                    
                if (!chkDowntrackAll.isSelected()) {
                	String sqlAdd = "";
                	
                	if (chkDowntrack1.isSelected())
                		sqlAdd += triggeredOR + "downtrack_summing = 1";
                	if (chkDowntrack2.isSelected())
                		sqlAdd += triggeredOR + "downtrack_summing = 2";
                	if (chkDowntrack3.isSelected())
                		sqlAdd += triggeredOR + "downtrack_summing = 3";
                	if (chkDowntrack4.isSelected())
                		sqlAdd += triggeredOR + "downtrack_summing = 4";
                	if (chkDowntrack5.isSelected())
                		sqlAdd += triggeredOR + "downtrack_summing = 5";
                	if (chkDowntrack6.isSelected())
                		sqlAdd += triggeredOR + "downtrack_summing = 6";
                	if (chkDowntrack8.isSelected())
                		sqlAdd += triggeredOR + "downtrack_summing = 8";
                	if (chkDowntrack13.isSelected())
                		sqlAdd += triggeredOR + "downtrack_summing = 13";
                	if (chkDowntrack27.isSelected())
                		sqlAdd += triggeredOR + "downtrack_summing = 27";
                	
                	if (sqlAdd.length() > 0)
                		sql += " and (" + sqlAdd + ")";
                }
            } 
            else {
                // Just use MOC-NA for public version 
                sql += " and instrument_name='MOC-NA'";
            }
            
            // min/max orbit
            String minOrbit = txtMinOrbit.getText();
            String maxOrbit = txtMaxOrbit.getText();
            if(!minOrbit.equals("")) sql += " and orbit_number >= " + minOrbit;
            if(!maxOrbit.equals("")) sql += " and orbit_number <= " + maxOrbit;
            
            // min lat
            String minLat = txtMinLat.getText();
            String maxLat = txtMaxLat.getText();
            if (!minLat.equals("")) sql += " and " + latCond(">=" + minLat);
            if (!maxLat.equals("")) sql += " and " + latCond("<=" + maxLat);
            
            // min/max lon
            String minLon = txtMinLon.getText();
            String maxLon = txtMaxLon.getText();
            if(!minLon.equals("")||!maxLon.equals("")) {
            	String lonFilter = lonCond(minLon,maxLon);
            	if (!lonFilter.equals("")) sql += " and " + lonCond(minLon, maxLon);
            }        
            
            // extra sql
            String extraSql = txtExtraSql.getText();
            if(!extraSql.equals("")) sql += " and " + extraSql;
            
            return  sql;
        }
        
    }
}
