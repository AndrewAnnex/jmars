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

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.sql.*;
import java.util.StringTokenizer;
import java.net.*;
import javax.swing.*;

/**
 * 
 * @author hoffj MSFF-ASU
 */
public class VikingStampFactory
extends StampFactory
{
    private static final DebugLog log = DebugLog.instance();
    private static final String CACHE_FILENAME = Config.get("viking.list_cache");
    private static final String PUBLIC_IMAGE_SERVER = Config.get("viking.gallery.data");
    private static final String URL_DIR_MARKER = "xx";
    
    private static int badUrlExceptions = 0;
    
    private VikingStampPool vikingStampPool = new VikingStampPool(); 
    private VikingImageFactory imageFactory = new VikingImageFactory();
    
    public VikingStampFactory()
    {
        super("Viking",
			  new String[] { "image_id", "image_time", "spacecraft_name",  
							 "ct_lat", "filter_name", "instrument_name", "note"},
			  new String[] { "up_lt_lat", "dn_lt_lat", "up_rt_lat",
							 "dn_rt_lat", "ct_lat" },
			  new String[] { "up_lt_lon", "dn_lt_lon", "up_rt_lon",
				 "dn_rt_lon", "ct_lon" }

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
                           VikingStampFactory.this)
            {
                protected void addSelectedStamps(final int[] rows)
                {
                    addSelectedStamps(rows, 0, 0, 0);
                }
                
                // Any non-null stamp name is treated as valid.
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
                    VikingStampFactory factory = (VikingStampFactory)originatingFactory;
                    
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
        
        Stamp s = vikingStampPool.getStamp(
                                           rs.getString("image_id"),
                                           (360 - rs.getDouble("up_lt_lon")) % 360,
                                           rs.getDouble("up_lt_lat"),
                                           (360 - rs.getDouble("up_rt_lon")) % 360,
                                           rs.getDouble("up_rt_lat"),
                                           (360 - rs.getDouble("dn_lt_lon")) % 360,
                                           rs.getDouble("dn_lt_lat"),
                                           (360 - rs.getDouble("dn_rt_lon")) % 360,
                                           rs.getDouble("dn_rt_lat"),
                                           fields);

        String url = rs.getString("gif_filepath");
        if (url != null)
            try
            {
                // Use URL string as-is for internal JMARS version; otherwise, 
                // create URL for use with public database.
                if (Main.isInternal())
                    s.pdsImageUrl = new URL(url);
                else {
                    // Find end of name of last directory in base URL
                    // (since it has a reliable pattern).
                    int markerPos = url.indexOf(URL_DIR_MARKER);
                    if (markerPos < 0)
                        throw new MalformedURLException("Cannot parse URL for stamp correctly");
                    
                    // Find beginning of name for last directory.
                    int begPos = url.lastIndexOf('/', markerPos);
                    if (begPos < 0)
                        throw new MalformedURLException("Cannot parse URL for stamp correctly");
                    
                    s.pdsImageUrl = new URL(PUBLIC_IMAGE_SERVER + url.substring(begPos + 1));
                }
            }
            catch(MalformedURLException e)
            {
                if (badUrlExceptions < 4)
                {
                    ++badUrlExceptions;
                    log.aprintln("------------------------------------");
                    log.aprintln("INVALID URL ENCOUNTERED IN DATABASE:");
                    log.aprintln(url);
                    log.aprintln(e);
                }
            }
        
        return s;
    }
    
    /**
     * Clears out projection-related information from stamps
     * in stamp pool.
     */
    synchronized void reprojectStampPool()
    {
        vikingStampPool.reprojectStamps();
    }

    private class VikingStamp extends Stamp
    {
        VikingStamp(String id,
                    double nwx, double nwy,
                    double nex, double ney,
                    double swx, double swy,
                    double sex, double sey,
                    Object[] data)
        {
            super(id, 
                 nwx, nwy,
                 nex, ney,
                 swx, swy,
                 sex, sey,
                 data);
        }
        
        public String getBrowseUrl()
        {
        	return Config.get("viking.gallery.browse")+id;
        }
    }

    private class VikingStampPool extends StampPool
    {
        /**
         * Creates Viking stamp instance using parameters.
         */
        protected Stamp createStamp(String id,
                                    double nwx, double nwy,
                                    double nex, double ney,
                                    double swx, double swy,
                                    double sex, double sey,
                                    Object[] data)
        {
            return new VikingStamp(id,
                                   nwx, nwy,
                                   nex, ney,
                                   swx, swy,
                                   sex, sey,
                                   data);
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
        return new VikingAddLayerWrapper(container, layerDesc, includeExtras);
    }
    
    private class VikingAddLayerWrapper extends StampFactory.AddLayerWrapper
    {
        // Layer description field name keys
        public static final String ORBITER     = "orbiter";
        public static final String INSTRUMENT  = "instrument";
        public static final String MIN_ORBIT   = "min_orbit";
        public static final String MAX_ORBIT   = "max_orbit";
        public static final String MIN_LAT     = "min_latitude";
        public static final String MAX_LAT     = "max_latitude";
        public static final String MIN_LON     = "min_longitude";
        public static final String MAX_LON     = "max_longitude";
        public static final String FILTERS     = "filters";
        public static final String EXTRA_SQL   = "extra_SQL";
        
        public static final String ALL_FILTERS = "ALL";
        public static final String BLUE = "BLUE";
        public static final String MINUS_BLUE = "MINUS_BLUE";
        public static final String VIOLET = "VIOLET";
        public static final String CLEAR = "CLEAR";
        public static final String GREEN = "GREEN";
        public static final String RED = "RED";
        
        // Field display components
        protected String orbiter1 = "Viking 1";
        protected String orbiter2 = "Viking 2";
        protected String orbiterBoth = "Both Orbiters";
        private JComboBox dropOrbiter = new JComboBox(new String[] { orbiter1,
                                                                     orbiter2,
                                                                     orbiterBoth} );
        protected String instA = "Camera A";
        protected String instB = "Camera B";
        protected String instBoth = "Both Cameras";
        private JComboBox dropInst = new JComboBox(new String[] { instA,
                                                                  instB,
                                                                  instBoth} );
        private JTextField txtMinOrbit = new PasteField();
        private JTextField txtMaxOrbit = new PasteField();
        private JTextField txtMinLat = new PasteField();
        private JTextField txtMaxLat = new PasteField();
        private JTextField txtMinLon = new PasteField();
        private JTextField txtMaxLon = new PasteField();
        private JTextField txtExtraSql = new PasteField();
        
        private JCheckBox chkFilterAll = new JCheckBox("ALL", true);
        private JCheckBox chkFilterBlue = new JCheckBox("BLUE", true); 
        private JCheckBox chkFilterMinusBlue = new JCheckBox("MINUS_BLUE", true);
        private JCheckBox chkFilterViolet = new JCheckBox("VIOLET", true); 
        private JCheckBox chkFilterClear = new JCheckBox("CLEAR", true);
        private JCheckBox chkFilterGreen = new JCheckBox("GREEN", true); 
        private JCheckBox chkFilterRed = new JCheckBox("RED", true);
        
        private Object[] Fields;
        
        public VikingAddLayerWrapper(Container container, LayerDescription layerDesc,
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
            
            JPanel filterPanel = new JPanel();
            JPanel subPanel1 = new JPanel();
            JPanel subPanel2 = new JPanel();
            JPanel subPanel3 = new JPanel();
            JPanel subPanel4 = new JPanel();

            subPanel1.add(chkFilterAll);
            subPanel1.add(chkFilterBlue);
            
            subPanel2.add(chkFilterMinusBlue);
            
            subPanel3.add(chkFilterViolet);
            subPanel3.add(chkFilterClear);
            
            subPanel4.add(chkFilterGreen);
            subPanel4.add(chkFilterRed);
            
            filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
            filterPanel.add(subPanel1);
            filterPanel.add(subPanel2);
            filterPanel.add(subPanel3);
            filterPanel.add(subPanel4);
            filterPanel.add(Box.createHorizontalStrut(120));
            filterPanel.add(Box.createVerticalStrut(10));
            
            // Set up a label that will be properly vertically spaced with
            // the whole above mess after TOP alignment (see implementation
            // for getLayout() method below...).
            JPanel filterLabelPanel = new JPanel();
            filterLabelPanel.setLayout(new BoxLayout(filterLabelPanel, BoxLayout.Y_AXIS));
            filterLabelPanel.add(Box.createVerticalStrut(9));
            filterLabelPanel.add(new JLabel("Filters:", SwingConstants.RIGHT));
            
            fields = new Object[] {"Orbiter:", dropOrbiter,
                                   "Instrument:", dropInst,
                                   "Min orbit:", txtMinOrbit,
                                   "Max orbit:", txtMaxOrbit,
                                   "Min latitude:", txtMinLat,
                                   "Max latitude:", txtMaxLat,
                                   "Westernmost longitude:", txtMinLon,
                                   "Easternmost longitude:", txtMaxLon,
                                   filterLabelPanel, filterPanel, 
                                   Box.createHorizontalStrut(150), Box.createHorizontalStrut(150),
                                   "Extra SQL condition:", txtExtraSql };
            
            return fields;
        }
        
        /**
         * Returns a custom layout manager designed to better handle
         * arrangement of the field parameters for the Viking layer.
         */
        protected LayoutManager getLayout(int rows, int columns, int hgap, int vgap)
        {
            // To address some layout issues with this manager, we add
            // one other, empty row to the requested space.
            SGLayout layout = new SGLayout(rows, columns, SGLayout.FILL, SGLayout.CENTER, hgap, vgap);
            layout.setColumnScale(1, 1.4);
            
            // We must use a vertically larger row for the row which includes 
            // the filter options; see getFieldList() implementation above.
            layout.setRowScale(8, 6.0);
            layout.setAlignment(8, 0, SGLayout.RIGHT, SGLayout.TOP);
            
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
            
            StringBuffer filterBuf = new StringBuffer();
            if (chkFilterAll.isSelected())
                filterBuf.append(ALL_FILTERS);
            else
            {
                if (chkFilterBlue.isSelected()) filterBuf.append(BLUE + " ");
                if (chkFilterMinusBlue.isSelected()) filterBuf.append(MINUS_BLUE + " ");
                if (chkFilterViolet.isSelected()) filterBuf.append(VIOLET + " ");
                if (chkFilterClear.isSelected()) filterBuf.append(CLEAR + " ");
                if (chkFilterGreen.isSelected()) filterBuf.append(GREEN + " ");
                if (chkFilterRed.isSelected()) filterBuf.append(RED + " ");
            }
            
            layerDesc.dlgFieldNames = new String[] {ORBITER, INSTRUMENT, 
                                                    FILTERS,
                                                    MIN_ORBIT, MAX_ORBIT,
                                                    MIN_LAT, MAX_LAT,
                                                    MIN_LON, MAX_LON,
                                                    EXTRA_SQL
                                                    }; 
         
            layerDesc.dlgFieldValues = new Object[] {dropOrbiter.getSelectedItem(), dropInst.getSelectedItem(), 
                                                     filterBuf.toString(), 
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
                dropOrbiter.setSelectedItem(orbiterBoth);
                dropInst.setSelectedItem(instBoth);
                txtMinOrbit.setText("");
                txtMaxOrbit.setText("");
                txtMinLat.setText("");
                txtMaxLat.setText("");
                txtMinLon.setText("");
                txtMaxLon.setText("");
                txtExtraSql.setText("");
                
                setDefaultFilters();
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
                else if (ORBITER.equals(field))
                {
                    if (value == null)
                        dropOrbiter.setSelectedItem(orbiterBoth);
                    else if (value instanceof String)
                        dropOrbiter.setSelectedItem( ((String)value).intern() );
                    else
                        log.aprintln("Non-string value for field '" + field + "' :" + value);
                }
                else if (INSTRUMENT.equals(field))
                {
                    if (value == null)
                        dropInst.setSelectedItem(instBoth);
                    else if (value instanceof String)
                        dropInst.setSelectedItem( ((String)value).intern() );
                    else
                        log.aprintln("Non-string value for field '" + field + "' :" + value);
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
                else if (FILTERS.equals(field))
                {
                    // For internal version only: set Filter checkboxes
                    // according to settings.
                    if (value == null)
                        setAllFilters(true);
                    else if (!(value instanceof String))
                        log.aprintln("Non-string value for field '" + field + "' :" + value);
                    else
                    {
                        String downStr = ((String) value).toUpperCase();
                        
                        if (downStr.indexOf(ALL_FILTERS) >= 0)
                            setAllFilters(true);
                        else 
                        {
                            StringTokenizer tokenizer = new StringTokenizer(downStr);
                            setAllFilters(false);
                            
                            while (tokenizer.hasMoreTokens()) 
                            {
                                String token = tokenizer.nextToken().trim();

                                if (token.equals(BLUE))
                                    chkFilterBlue.setSelected(true);
                                else if (token.equals(MINUS_BLUE))
                                    chkFilterMinusBlue.setSelected(true);
                                else if (token.equals(VIOLET))
                                    chkFilterViolet.setSelected(true);
                                else if (token.equals(CLEAR))
                                    chkFilterClear.setSelected(true);
                                else if (token.equals(GREEN))
                                    chkFilterGreen.setSelected(true);
                                else if (token.equals(RED))
                                    chkFilterRed.setSelected(true);
                                else
                                    log.aprintln("bad filter value: " + token);
                            }
                        }
                    }
                }
            }
        }

        private void setDefaultFilters()
        {
            setAllFilters(false);
            chkFilterRed.setSelected(true);
        }
        
        private void setAllFilters(boolean selected)
        {
            chkFilterAll.setSelected(selected);
            chkFilterBlue.setSelected(selected);
            chkFilterMinusBlue.setSelected(selected);
            chkFilterViolet.setSelected(selected);
            chkFilterClear.setSelected(selected);
            chkFilterGreen.setSelected(selected);
            chkFilterRed.setSelected(selected);
        }
        
        public String getDefaultName()
        {
            Object orbiter = dropOrbiter.getSelectedItem();
            if (orbiter == orbiterBoth)      return "Viking Stamps";
            else if (orbiter == orbiter1) return "Viking 1 Stamps";
            else if (orbiter == orbiter2) return "Viking 2 Stamps";

            
            // Should never happen
            log.aprintln("INVALID dropOrbiter value: " + orbiter);
            return  "Viking Stamps [INVALID NAME]";
        }
        
        public String getSql()
        {
            String vikingIdxFields = "viking_idx.*"; 
             
            String vikingGeometryFields = "gif_filepath, ct_lon, ct_lat, " + 
                                          "up_lt_lon, up_lt_lat, up_rt_lon, up_rt_lat, " +
                                          "dn_lt_lon, dn_lt_lat, dn_rt_lon, dn_rt_lat"; 

            return getSql("select " +
                          vikingIdxFields + ", " +
                          vikingGeometryFields +
                          " from viking_idx inner join viking_geometry on " +
                          "viking_idx.image_id=viking_geometry.image_id " +
                          "inner join viking_status on " +
                          "viking_idx.image_id=viking_status.image_id and ifnull(useable, 0) " +
                          "where target_name = 'MARS'");
        }
        
        public String getCacheSql()
        {
            // Not available yet.
            return null;
            
            // Internal users don't use a cached Viking stamp database since
            // it is assumed to be unnecessary (good network performance and
            // fewer number of users than for other scenarios such as the public
            // JMARS version).
            //
            // Also, check whether free-form extra SQL conditions were applied;
            // if so, we can't use the cached stamp database data.
//            if (Main.isInternal() ||
//                !txtExtraSql.getText().equals(""))
//                return null;
//            else
//                // Note that because the stamp cache file already restricts the 
//                // Viking stamp list to those with target_name = 'MARS',  
//                // there is no need to apply this condition here.
//                return getSql("select * from " + getCacheTableName());
        }
        
        public String getCacheTableName()
        {
            return null;
//            return "cached_viking";
        }
        
        /**
         ** Cached Viking stamp table and data are based on the following query
         ** used to dump the data:
         **
         ** select viking_idx.*, gif_filepath, ct_lon, ct_lat, up_lt_lon, up_lt_lat, 
         ** up_rt_lon, up_rt_lat, dn_lt_lon, dn_lt_lat, dn_rt_lon, dn_rt_lat
         ** from viking_idx inner join viking_geometry on viking_idx.image_id=viking_geometry.image_id 
         ** inner join viking_status on viking_idx.image_id=viking_status.image_id 
         ** and ifnull(useable, 0) 
         ** where target_name = 'MARS';
         **
         ** The selected fields and their column order must be kept in sync with the
         ** "util/make_viking_cache.pl" script that is used to generate the import cache file. 
         **/
        public String getCacheTableFieldsSql()
        {
            // Note the omission of size limits on integer fields from original MySQL table
            // definitions below.  Also, 'float' types have been changed to 'double' declarations.
            // HSQLDB does not allow these since these restrictions are not enforced, and it 
            // also converts both float and double SQL types to the Java double type.
            
                         // From viking_idx
            String sql = "image_id varchar(8) default '' NOT NULL, " +
                         "image_number int default '0' NOT NULL, " +                  // Was: int(11) 
                         "spacecraft_name varchar(16) default '' NOT NULL, " +
                         "mission_phase_name varchar(32) default NULL, " +
                         "target_name varchar(8) default NULL, " +
                         "image_time varchar(20) default NULL, " +
                         "earth_received_time varchar(20) default NULL, " +
                         "orbit_number int default NULL, " +                          // Was: int(11) 
                         "instrument_name enum('VISUAL_IMAGING_SUBSYSTEM_CAMERA_A','VISUAL_IMAGING_SUBSYSTEM_CAMERA_B') default NULL, " +
                         "gain_mode_id enum('LOW','HIGH') default NULL, " +
                         "flood_mode_id enum('ON','OFF') default NULL, " +
                         "offset_mode_id enum('ON','OFF') default NULL, " +
                         "filter_name enum('BLUE','MINUS_BLUE','VIOLET','CLEAR','GREEN','RED') default NULL, " +
                         "exposure_duration double default NULL, " +                  // Was:  float
                         "note varchar(160) default NULL, " +
                         "image_volume_id varchar(8) default NULL, " +
                         "image_file_name varchar(28) default NULL, " +
                         "browse_volume_id varchar(8) default NULL, " +
                         "browse_file_name varchar(28) default NULL, " +
                         // From viking_geometry
                         "gif_filepath varchar(255) default NULL, " +
                         "up_lt_lon double default NULL, " +
                         "dn_lt_lon double default NULL, " +
                         "up_rt_lon double default NULL, " +
                         "dn_rt_lon double default NULL, " +
                         "ct_lon double default NULL, " +
                         "up_lt_lat double default NULL, " +
                         "dn_lt_lat double default NULL, " +
                         "up_rt_lat double default NULL, " +
                         "dn_rt_lat double default NULL, " +
                         "ct_lat` double default NULL, " +
                         "UNIQUE (image_id) ";
            
            // Internal users don't use a cached Viking stamp database
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
            return new String[] {"ct_lat",
                                 "orbit_number"
                                 };
        }
        
        /**
         ** Returns name of the primary key field for stamps in 
         ** the SQL database (for whichever table contains this).
         **/
        public String getPrimaryKeyName()
        {
            return "image_id";
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
            return null;
//            return "touched";
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
            return null;
//          return "touched";
        }

        public String getImportCacheFileName()
        {
            return CACHE_FILENAME;
        }
        
        private String getSql(String sql)
        {
            // Instrument name
            Object orbiter = dropOrbiter.getSelectedItem();
            if     (orbiter == orbiterBoth) ;
            else if(orbiter == orbiter1) sql += " and spacecraft_name='VIKING_ORBITER_1'";
            else if(orbiter == orbiter2) sql += " and spacecraft_name='VIKING_ORBITER_2'";
            else
                log.aprintln("INVALID dropOrbiter value: " + orbiter);
            
            // Instrument name
            Object inst = dropInst.getSelectedItem();
            if     (inst == instBoth) ;
            else if(inst == instA) sql += " and instrument_name='VISUAL_IMAGING_SUBSYSTEM_CAMERA_A'";
            else if(inst == instB) sql += " and instrument_name='VISUAL_IMAGING_SUBSYSTEM_CAMERA_B'";
            else
                log.aprintln("INVALID dropInst value: " + inst);
            
            // Filter restrictions
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
                
            if (!chkFilterAll.isSelected()) {
                String sqlAdd = "";
                
                if (chkFilterBlue.isSelected())
                    sqlAdd += triggeredOR + "filter_name = 'BLUE'";
                if (chkFilterMinusBlue.isSelected())
                    sqlAdd += triggeredOR + "filter_name = 'MINUS_BLUE'";
                if (chkFilterViolet.isSelected())
                    sqlAdd += triggeredOR + "filter_name = 'VIOLET'";
                if (chkFilterClear.isSelected())
                    sqlAdd += triggeredOR + "filter_name = 'CLEAR'";
                if (chkFilterGreen.isSelected())
                    sqlAdd += triggeredOR + "filter_name = 'GREEN'";
                if (chkFilterRed.isSelected())
                    sqlAdd += triggeredOR + "filter_name = 'RED'";
                
                if (sqlAdd.length() > 0)
                    sql += " and (" + sqlAdd + ")";
            }
            
            // min/max orbit
            String minOrbit = txtMinOrbit.getText();
            String maxOrbit = txtMaxOrbit.getText();
            if(!minOrbit.equals("")) sql += " and orbit_number >= " + minOrbit;
            if(!maxOrbit.equals("")) sql += " and orbit_number <= " + maxOrbit;
            
            // min lat
            String minLat = txtMinLat.getText();
            String maxLat = txtMaxLat.getText();
            if (!minLat.equals("")) sql += " and " + latCond(">= " + minLat);
            if (!maxLat.equals("")) sql += " and " + latCond("<= " + maxLat);
           
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
