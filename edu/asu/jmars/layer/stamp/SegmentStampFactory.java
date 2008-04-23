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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;
import java.util.StringTokenizer;
import javax.swing.*;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import edu.asu.jmars.Main;
import edu.asu.jmars.swing.PasteField;

/**
 * Factory for creating stamp layers to view THEMIS mission planning
 * orbit track segment and existing intersected/nearby observation data.  
 * Intended to serve solely as a diagnostic tool for automated mission 
 * planning.
 *  
 * @author hoffj MSFF-ASU
 */
public class SegmentStampFactory
extends StampFactory
{
    private static final DebugLog log = DebugLog.instance();
    
    private static final String[] SEGMENT_COLUMNS =
        new String[] { "Start ET", "End ET",
                       "Stamp Coverage", "Center Coverage", "ROI Coverage", "Solar Incidence",
                       "UL Lon", "UL Lat", "UR Lon", "UR Lat", 
                       "LL Lon", "LL Lat", "LR Lon", "LR Lat"
                     };
    
    private static final String[] OBS_COLUMNS =
        new String[] { "Name", "Type", "Start ET", "End ET",
                       "Stamp Coverage", "Center Coverage", "ROI Coverage", "Solar Incidence", 
                       "Heuristic Score", "Heuristic Summary", "Heuristic Report",
                       "UL Lon", "UL Lat", "UR Lon", "UR Lat", 
                       "LL Lon", "LL Lat", "LR Lon", "LR Lat"
                     };
    
    private static final String[] DB_INTERNAL_COLUMNS =
        new String[] { "filename", "start", "owner", "description",
                       "ctr_lat", "bandcfg", "status"};
    
    private static final String[] DB_PUBLIC_COLUMNS =
        new String[] { "filename", "start", "description",
                       "ctr_lat", "bandcfg" };
    
    
    public SegmentStampFactory()
    {
        super("Segment", null, null, null);
    }
    
    /**
     * Returns string describing database to which to connect to
     * retrieve data for this stamp layer.
     */
    protected String getDbUrl()
    {
        return dbThemisUrl;
    }
    
    public Layer.LView recreateLView(SerializedParameters parmBlock)
    {
        Layer.LView view = null;
        
        log.println("recreateLView called");
        
        if (parmBlock != null &&
            parmBlock instanceof StampLView.StampParameters)
        {
            StampLView.StampParameters parms = (StampLView.StampParameters) parmBlock;
            
            // We ignore the parameter block's initialColumns and instead select
            // these based on the layer type.
            Layer layer = getLayer(parms.sqlParms, parms.layerDesc);
            if (layer == null) {
                log.aprintln("null layer");
                return null;
            }
            String[] columns = getColumns(((SegmentStampLayer)layer).getLayerType());
            
            view = createLView(parms.name,
                               layer,
                               columns,
                               parms.unsColor,
                               parms.filColor);
            view.setVisible(true);
            
            log.println("successfully recreated StampLView");
        }
        
        return view;
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
        // We ignore the specified initialColumns and instead select
        // these based on the layer type.
        String[] columns = getColumns(((SegmentStampLayer)parent).getLayerType());
        
        Layer.LView view = 
            new StampLView(name,
                           parent,
                           columns,
                           unsColor,
                           filColor,
                           SegmentStampFactory.this)
                {
                    protected void storeParms(Layer parent)
                    {
                        super.storeParms(parent);
                        if (parent != null)
                            initialParms.sqlParms.sql = 
                                            ((SegmentStampLayer)parent).getLayerType()
                                            + " " + initialParms.sqlParms.importCacheFile;
                                
                    }
            
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
                        return true;
                    }
                    
                    protected MyFilledStampFocus createFilledStampFocus()
                    {
                        return new MyFilledStampFocus(this, null)
                                    {
                                        {
                                            if (btnRotateImage != null)
                                                btnRotateImage.setVisible(false);
                                            if (btnHFlip != null)
                                                btnHFlip.setVisible(false);
                                            if (btnVFlip != null)
                                                btnVFlip.setVisible(false);
                                            if (dropBand != null)
                                                dropBand.setVisible(false);
                                            if (btnViewPds != null)
                                                btnViewPds.setVisible(false);
                                        }
                                        
                                        protected StampImage load(Stamp s)
                                        {
                                            return null;
                                        }
                                        
                                        public void addStamp(Stamp s)
                                        {
                                            // Do nothing
                                        }
                                    };
                    }
                };
                           
                return view;
    }

    public String[] getColumns(Object layerType)
    {
        if (layerType == SegmentStampLayer.SEGMENT_TYPE)
            return SEGMENT_COLUMNS;
        else if (layerType == SegmentStampLayer.OBS_TYPE)
            return OBS_COLUMNS;
        else if (Main.isInternal())
            return DB_INTERNAL_COLUMNS;
        else
            return DB_PUBLIC_COLUMNS;
    }
    
    /**
     * Creates and returns instance of {@link SegmentStampLayer} according
     * to parameters.
     * 
     * @param dbUrl URL text descriptor for establishing connection to database
     * server, including any necessary access parameters such as username/password.
     * Example:  jdbc:mysql://host.domain/server?user=DBADMIN&password=GURU 
     * @param factory Factory instance with stamp data descriptors. 
     * @param sqlParms SQL query, table, and cache parameters.
     * 
     * @return Stamp layer instance corresponding to parameters.
     */
    protected StampLayer createLayer(String dbUrl,
                                     StampFactory factory,
                                     SqlStampLayer.SqlParameters sqlParms)
    {
        // We use a hack to obtain the information for creating the 
        // correct type of Segment layer.
        String filename = sqlParms.importCacheFile;
        String layerType = null;
        if (sqlParms.sql != null) {
            StringTokenizer tokenizer = new StringTokenizer(sqlParms.sql);
            layerType = tokenizer.nextToken();
        }
        
        // It's critical to get that string literal back...
        if (layerType != null)
            layerType = layerType.intern();

        // Create a clone of the SQL parameters.  Replace the SQL query in
        // this clone with a real SQL query (according to the layer type),
        // and pass the clone in as the SQL parameters when creating
        // the layer.  Note that SEGMENT layers don't actually use SQL
        // queries.
        //
        // This hack is used to work around a paradigm incompatibility between
        // normal stamp layers, how they are cached by the factory, and how 
        // SegmentStampLayer instances are actually distinguished.
        //
        // Normal stamp layers differ by SQL query.  Instances of this class, 
        // however, differ by segment layer type and the imported segment data 
        // filename.  However, for non-SEGMENT layers, we need a real SQL query
        // inside the layer instance, and it's not worth altering the
        // various methods in the superclass to support this specialized use
        // case.
        SqlStampLayer.SqlParameters realSqlParms = (SqlStampLayer.SqlParameters)sqlParms.clone();
        realSqlParms.sql = realGetSql(layerType);
        
        SegmentStampLayer layer = new SegmentStampLayer(dbUrl, factory, realSqlParms,
                                                        filename,
                                                        layerType);
        
        return layer;
    }

    
    public String realGetSql(Object type)
    {
        // Only support in internal version of JMARS.
        if (!Main.isInternal())
            return null;
            
        // No SQL query for SEGMENT or OBS stamps.
        if (type == SegmentStampLayer.SEGMENT_TYPE ||
            type == SegmentStampLayer.OBS_TYPE)
            return null;
        
        // Only use SQL query with INTERSECTED and NEAR stamps from segment data.
        String qube_tbl = Config.get("stamps.qube_tbl");
        if(qube_tbl == null)
            qube_tbl = "MISSING_CONFIG_SETTING";
        
        String fields = "themis2.geometry.*, " + 
                        "obs.owner, obs.description, obs.orbit, obs.start, obs.sclk, " +
                        "obs.status, obs.summing, obs.duration, obs.gain, obs.offset, "+ 
                        "obs.bandcfg, obs.image_type, " +
                        "s1.location, s1.stage, qubgeom.incidence_angle";
        
        String sql =
            "select " + fields +
            " from themis2.geometry inner join themis2.obs" +
            "   on themis2.geometry.filename = themis2.obs.filename" +
            " left outer join stage s1" +
            "   on themis2.geometry.filename = s1.file_id" +
            "   and s1.stage='EDR'" +
            " left outer join stage s2" +
            "   on themis2.geometry.filename = s2.file_id" +
            "   and s2.stage='RDR'" +
            " left outer join qubgeom" +
            "   on themis2.geometry.filename = qubgeom.file_id" +
            "   and qubgeom.point_id = 'CT'" +
            "   and qubgeom.band_idx = 1" +
            " where (obs.filename like 'V%' or obs.filename like 'I%')";
        
        return sql;
    }
    
    /**
     * Only used to create stamps for non-Segment stamps,
     * i.e., INTERSECTED or NEAR stamps from database.
     **/
    public Stamp createStamp(ResultSet rs)
    throws SQLException
    {
        int colCount = rs.getMetaData().getColumnCount();
        Object[] fields = new Object[colCount];
        for(int i=0; i<colCount; i++)
            fields[i] = rs.getObject(i+1);
        
        return stampPool.getStamp(
                                  rs.getString("filename"),
                                  (360 - rs.getDouble("nw_lon")) % 360,
                                  rs.getDouble("nw_lat"),
                                  (360 - rs.getDouble("ne_lon")) % 360,
                                  rs.getDouble("ne_lat"),
                                  (360 - rs.getDouble("sw_lon")) % 360,
                                  rs.getDouble("sw_lat"),
                                  (360 - rs.getDouble("se_lon")) % 360,
                                  rs.getDouble("se_lat"),
                                  fields
                                 );
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
        return new SegmentAddLayerWrapper(container, layerDesc, includeExtras);
    }
    
    private class SegmentAddLayerWrapper extends StampFactory.AddLayerWrapper
    {
        // Layer description field name keys
        public static final String SEGMENT_TYPE = "segment_type";
        public static final String FILENAME = "filename";
        
        private Object[] Fields;
        private JTextField txtFilename = new PasteField();
        private JComboBox comboStampType = new JComboBox(
                new String[] { SegmentStampLayer.OBS_TYPE,
                               SegmentStampLayer.SEGMENT_TYPE,
                               SegmentStampLayer.INTERSECTED_TYPE,
                               SegmentStampLayer.NEAR_TYPE}
        );

        
        public SegmentAddLayerWrapper(Container container, LayerDescription layerDesc,
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
            return new Object[] {"Filename:", txtFilename,
                                 "Stamp Type:", comboStampType
                                };
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
            
            layerDesc.dlgFieldNames = new String[] {SEGMENT_TYPE, 
                                                    FILENAME
                                                    }; 
         
            layerDesc.dlgFieldValues = new Object[] {comboStampType.getSelectedItem(),  
                                                     txtFilename.getText().trim()
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
                comboStampType.setSelectedItem(SegmentStampLayer.OBS_TYPE);
                txtFilename.setText("");
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
                else if (SEGMENT_TYPE.equals(field))
                {
                    if (value == null)
                        comboStampType.setSelectedItem(SegmentStampLayer.OBS_TYPE);
                    else if (value instanceof String)
                        comboStampType.setSelectedItem( ((String)value).intern() );
                    else
                        log.aprintln("Non-string value for field '" + field + "' :" + value);
                }
                else if (FILENAME.equals(field)) {
                    if (value == null)
                        txtFilename.setText("");
                    else
                        txtFilename.setText(value.toString());
                }
            }
        }
        
        public String getDefaultName()
        {
            Object type = comboStampType.getSelectedItem();
            if     (type == SegmentStampLayer.SEGMENT_TYPE) return  "Segment stamps";
            else if(type == SegmentStampLayer.OBS_TYPE) return  "Obs stamps";
            else if(type == SegmentStampLayer.INTERSECTED_TYPE) return  "Segment Intersected stamps";
            else if(type == SegmentStampLayer.NEAR_TYPE) return  "Segment Near stamps";
            
            // Should never happen
            log.aprintln("INVALID comboStampType value: " + type);
            return  "Stamps [INVALID NAME]";
        }

        /**
         * Hacked encoding used to communicated which type of layer
         * needs to be created; factory turns this into SQL query
         * if needed.  The segment data file is appended (with whitespace
         * delimiter) to ensure that a unique value is returned
         * for each distinct layer instance for caching purposes
         * in {@link getLayer}.  YES, THIS IS A HACK....
         */
        public String getSql()
        {
            return (String)comboStampType.getSelectedItem() + 
                   " " + getImportCacheFileName();
        }
        
        /**
         ** Returns name of the primary key field for stamps in 
         ** the SQL database (for whichever table contains this).
         **/
        public String getPrimaryKeyName()
        {
            return "filename";
        }
        
        /**
         ** Returns name of the orbit field for stamps in the SQL
         ** database (for whichever table contains this).
         **/
        public String getOrbitFieldName()
        {
            return "orbit";
        }
        
        /**
         * Not used.
         **/
        public String getServerLastModFieldName()
        {
            return null;
        }
        
        /**
         * Not used.
         **/
        public String getCacheLastModFieldName()
        {
            return null;
        }
        
        /**
         * Returns name of file containing segment data.
         */
        public String getImportCacheFileName()
        {
            return txtFilename.getText().trim();
        }
        
        /**
         * Overridden to set initial table columns based on
         * which segment data subset (layer type) is selected. 
         */
        public void actionPerformed(ActionEvent e)
        {
            // Replace initialColumns based on the layer type.
            initialColumns = getColumns(comboStampType.getSelectedItem());
            super.actionPerformed(e);
        }
    }
}
