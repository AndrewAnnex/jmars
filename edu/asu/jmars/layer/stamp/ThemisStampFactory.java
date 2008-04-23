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
import java.net.*;
import java.sql.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import javax.swing.*;

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;

public class ThemisStampFactory
extends StampFactory
{
    private static final DebugLog log = DebugLog.instance();
    private static final String CACHE_FILENAME = Config.get("stamps.list_cache");
    private static final String OLD_SQL_START_PARSE_TAG1 = "where not ifnull(cal,0) and not ifnull(shutter,0) and";
    private static final String OLD_SQL_START_PARSE_TAG2 = "where not ifnull(cal,0) and not ifnull(shutter,0)";
    private static final String OLD_SQL_STOP_PARSE_TAG = "order by start";
    private static final String CONVERT_SPECIAL = "s";
    private static final String CONVERT_EXTRACT = "e";
    private static final DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
    
    private static int badUrlExceptions = 0;
    
    public ThemisStampFactory()
    {
        this("Themis RDR (deprecated!)");
    }
    
    protected ThemisStampFactory(String instrument, String[] initCols)
    {
        super(instrument, initCols,
	      new String[] { "ctr_lat", "nw_lat", "ne_lat", "se_lat", "sw_lat"},
	      new String[] { "ctr_lon", "nw_lon", "ne_lon", "se_lon", "sw_lon"}
	    );
    }
    
    public ThemisStampFactory(String instrument)
    {
        this(instrument, getInitialColumns());
    }
    
    public Layer.LView recreateLView(SerializedParameters parmBlock)
    {
        Layer.LView view = null;
        
        log.println("recreateLView called");
        
        if (parmBlock != null &&
            parmBlock instanceof StampLView.StampParameters)
        {
            StampLView.StampParameters parms = (StampLView.StampParameters) parmBlock;
            try {
                parms = convertOldParameters(parms);
            }
            catch (OldQueryException e) {
                String msg = e.getMessage();
                log.aprintln(msg);
                log.println(e);
                
                if (Main.isInternal() &&
                    parms.sqlParms != null &&
                    parms.sqlParms.sql != null)
                    showLoadError(parms.name, parms.sqlParms.sql, msg);
                else
                    showLoadError(parms.name, null, msg);
                
                return null;
            }
            
            parms.sqlParms = getCacheSafeSqlParms(parms.sqlParms);
            view = new StampLView(parms.name,
                                  getLayer(parms.sqlParms, parms.layerDesc),
                                  parms.initialColumns,
                                  parms.unsColor,
                                  parms.filColor,
                                  ThemisStampFactory.this);
            view.setVisible(true);
            
            log.println("successfully recreated StampLView");
        }
        
        return view;
    }
    
    protected void showLoadError(String viewName, String query, String msg)
    {
        StringBuffer errMsg = new StringBuffer();
        
        errMsg.append(Util.lineWrap(msg, 60));
        if (query != null) {
            errMsg.append("\n\nFull query:\n\n");
            errMsg.append(Util.lineWrap(query, 60));
        }
        
        JOptionPane.showMessageDialog(null, 
                                      errMsg.toString(),
                                      "View Load Failure: " + viewName, 
                                      JOptionPane.WARNING_MESSAGE
        );
    }
    
    protected static String[] getInitialColumns()
    {
        if (Main.isInternal())
            return new String[] { "filename", "start", "owner", "description",
                                  "ctr_lat", "bandcfg", "status", "summing" };
        else if (Main.isStudentApplication())
            return new String[] { "filename", "ctr_lat", "ctr_lon", "description",
                                  "coll_date" };
        else
            return new String[] { "filename", "start", "description",
                                  "ctr_lat", "bandcfg" };
    }
    
    
    public static class OldQueryException extends Exception
    {
        OldQueryException(String msg)
        {
            super(msg);
        }
    }
    
    
    /**
     * NOTE: The primary use for this method is to convert THEMIS2-based parameters
     * to THEMIS3 parameters.
     *
     * Converts old stamp parameters to new parameters, if necessary.  Uses
     * revision of stamp parameter definition to determine necessary conversions.
     * Replaces old SQL query with new one based on layer description.  For 
     * older stamp parameters that do not contain a layer description and
     * use a THEMIS2-based SQL query only, a layer description is created from
     * the SQL query; this description is then used to create a THEMIS3 query.
     *
     * @return Returns a stamp parameters containing updated
     * values as necessary.  Returns passed instance if no changes are made; otherwise,
     * a new instance is returned.  Returns <code>null</code> if a <code>null</code>
     * parameter instance is passed in.
     *
     * @throws OldQueryException Thrown if SQL query in parameters is simply too
     * old to be converted correctly, i.e., format is too obsolete to parse safely.
     * Also thrown if query contains an extra-SQL condition; these can't be
     * either converted or used as-is safely.
     */
    protected StampLView.StampParameters convertOldParameters(StampLView.StampParameters parms)
    throws OldQueryException
    {
        StampLView.StampParameters newParms = null;
        
        if (parms == null)
            return null;
        
        // Return parameters unchanged if they are defined with
        // the current revision.
        if (parms.revision > 2)
            return parms;
        
        // Create new parameters using old.
        newParms = new StampLView.StampParameters();
        newParms.name = parms.name;
        newParms.unsColor = parms.unsColor;
        newParms.filColor = parms.filColor;
        
        // For old parameter revision without layer description, create
        // a layer description by extracting information from THEMIS2-style
        // SQL query.
        if (parms.revision < 1) {
            if (parms.sqlParms == null) {
                log.aprintln("null SQL parameters");
                newParms = null;
            }
            else {
                newParms.layerDesc = convertOldSqlQuery(parms.sqlParms.sql);
                if (newParms.layerDesc == null) {
                    log.aprintln("null layer description from old SQL query conversion");
                    newParms = null;
                }
            }
        }
        else if (parms.layerDesc == null) {
            log.aprintln("null layer description in stamp parameters");
            newParms = null;
        }
        else
            // Copy layer description from revision 1 stamp parameters.
            newParms.layerDesc = (LayerDescription)parms.layerDesc.clone();
        
        if (newParms != null) {
            // Create THEMIS3-style SQL query/parameters using layer description
            // copied/extracted above from old parameters.
            AddLayerWrapper wrapper = createWrapper(null, newParms.layerDesc, true);
            if (wrapper == null) {
                log.aprintln("null wrapper");
                newParms = null;
            }
            else {
                // Before getting SQL parameters based on layer description,
                // force update with description again.  This is necessary (due 
                // to what may be an implementation mistake in the AddLayerWrapper 
                // construction scheme) to populate the UI components when 
                // the wrapper has been created with a null-container.
                wrapper.updateLayerDescription(newParms.layerDesc);
                newParms.sqlParms = wrapper.getSqlParms();
                newParms.initialColumns = getInitialColumns();
            }
        }
        
        return newParms;
    }
    
    /**
     * Converts an old THEMIS2-style SQL query into a layer description.
     * Only recommended for use on queries which pre-date the addition
     * of the LayerDescription abstraction to StampFactory/ThemisStampFactory
     * classes.
     *
     * @throws OldQueryException Thrown if SQL query in parameters is simply too
     * old to be converted correctly, i.e., format is too obsolete to parse safely.
     * Also thrown if query contains an extra-SQL condition; these can't be
     * either converted or used as-is safely.
     */
    protected LayerDescription convertOldSqlQuery(String oldSql)
    throws OldQueryException
    {
        if (oldSql == null) {
            log.aprintln("null SQL query");
            return null;
        }
        
        // Mapping to convert conditional clauses from THEMIS2 SQL query
        // as of revision 1.10 of ThemisStampFactory, 11-9-2004.
        // 
        // Note: This simply marks the change point for the query from
        // THEMIS2 to THEMIS3.  This map is really only needed to restore
        // stamp layers saved in ".jmars" files created somewhat farther
        // back in time prior to the creation of the LayerDescription
        // abstraction in revision 1.8 (11-6-2004).
        String[] oldSqlClauseLayerDescMapping = new String[] {
            "image_type",                ThemisAddLayerWrapper.IMAGE_TYPE,  CONVERT_SPECIAL,
            "status",                    ThemisAddLayerWrapper.STATUS,      CONVERT_SPECIAL,
            // NOTE:  The "qube_stage" clause below has been normalized for matching after
            // parentheses get stripped from query (during processing below).
            "not isnullq2.qube_stage",   ThemisAddLayerWrapper.STATUS,      CONVERT_SPECIAL, 
            "incidence_angle >=",        ThemisAddLayerWrapper.MIN_ANGLE,   CONVERT_EXTRACT,
            "incidence_angle <=",        ThemisAddLayerWrapper.MAX_ANGLE,   CONVERT_EXTRACT,
            "orbit >=",                  ThemisAddLayerWrapper.MIN_ORBIT,   CONVERT_EXTRACT,
            "orbit <=",                  ThemisAddLayerWrapper.MAX_ORBIT,   CONVERT_EXTRACT,
            "ctr_lat >=",                ThemisAddLayerWrapper.MIN_LAT,     CONVERT_EXTRACT,
            "ctr_lat <=",                ThemisAddLayerWrapper.MAX_LAT,     CONVERT_EXTRACT,
            "summing",                   ThemisAddLayerWrapper.VIS_SUMMING, CONVERT_SPECIAL
        };
        Map oldSqlClauseLayerDescMap = new HashMap();
        Map oldSqlClauseHandlingMap = new HashMap();
        
        if (oldSqlClauseLayerDescMapping.length % 3 > 0) {
            log.aprintln("bad sql query conversion mapping initialization");
            return null;
        }
        
        for (int i=0; i < oldSqlClauseLayerDescMapping.length; i += 3) {
            oldSqlClauseLayerDescMap.put(oldSqlClauseLayerDescMapping[i],
                                         oldSqlClauseLayerDescMapping[i + 1]);
            oldSqlClauseHandlingMap.put(oldSqlClauseLayerDescMapping[i],
                                        oldSqlClauseLayerDescMapping[i + 2]);
        }
        
        ThemisAddLayerWrapper wrapper = (ThemisAddLayerWrapper) createWrapper(null, null, false);
        LayerDescription layerDesc = new LayerDescription();
        java.util.List fieldList = new ArrayList();
        java.util.List fieldValuesList = new ArrayList();
        StringBuffer summingValues = new StringBuffer();
        
        // Extract just the conditionals in the "where" portion
        // of the query.
        String sql = oldSql.toLowerCase().trim();
        String startKey = OLD_SQL_START_PARSE_TAG1;
        int idx0 = sql.indexOf(startKey);
        if (idx0 < 0) {
            startKey = OLD_SQL_START_PARSE_TAG2;
            idx0 = sql.indexOf(startKey);
        }
        int idx1 = sql.indexOf(OLD_SQL_STOP_PARSE_TAG);
        if (idx0 < 0 ||
            idx1 < 0 ||
            idx0 > idx1)    
            throw new OldQueryException("Stored SQL query too old to convert and import");
        sql = sql.substring(idx0 + startKey.length(), idx1);
        
        // Prepare the query for parsing by normalizing relational
        // expressions like "=" to have whitespace around them.
        StringBuffer buf = new StringBuffer();
        Pattern relationals = Pattern.compile("<=|>=|!=|=");
        Matcher m = relationals.matcher(sql);
        
        while (m.find())
            m.appendReplacement(buf, " $0 ");
        m.appendTail(buf);
        sql = buf.toString();
        
        // Strip any "image_type" clause that appears in OR-block
        // with VIS summing restrictions.
        String key = "\\(image_type = 0";
        int imageTypeIdx = sql.indexOf(key);
        if (imageTypeIdx >= 0) {
            String rest = sql.substring(imageTypeIdx);
            int end = rest.indexOf("or");
            
            if (end >= 0)
                rest = rest.substring(end + 2);
            else
                rest = rest.replaceFirst(key, "");
            
            sql = sql.substring(0, imageTypeIdx) + rest;
        }
        
        // More pre-parsing normalization:
        // - replace all "or" clauses with "and".
        // - strip all "(" and ")" characters.
        //
        // (This is a kludged scheme to avoid doing real semantic
        // analysis; a YACC equivalent would be overkill anyway.)
        sql = sql.replaceAll(" or ", " and ");
        sql = sql.replaceAll("\\(", "");
        sql = sql.replaceAll("\\)", "");
        
        // Further normalize query so that each whitespace section
        // contains only a single whitespace character.
        //
        // NOTE:  This must be the last normalization step.
        Pattern whitespace = Pattern.compile("\\s{2,}");
        m = whitespace.matcher(sql);
        buf = new StringBuffer();
        
        while (m.find())
            m.appendReplacement(buf, " ");
        m.appendTail(buf);
        sql = buf.toString().trim();
        
        // Check for no conditionals to parse.
        if (sql.length() == 0)
            return layerDesc;
        
        // Divide where-conditionals into separate clauses.
        String[] clauses = sql.split(" and ");
        
        // Take each clause and convert/add it to layer description.
        for (int i=0; i < clauses.length; i++)
        {
            String curClause = clauses[i].trim();
            String matchKey = null;
            String fieldName = null;
            String handling = null;
            
            // Skip any empty clause; this should never happen.
            if (curClause.length() == 0) {
                log.aprintln("ignoring empty clause #" + i + 
                             " in: " + sql);
                continue;
            }
            
            // Check for whole-clause match.
            fieldName = (String) oldSqlClauseLayerDescMap.get(curClause);
            if (fieldName != null) {
                handling = (String) oldSqlClauseHandlingMap.get(curClause);
                matchKey = curClause;
            }
            // Check for partial match on front of clause.
            else {
                StringTokenizer tokenizer = new StringTokenizer(curClause);
                String firstTok = tokenizer.nextToken();
                
                fieldName = (String) oldSqlClauseLayerDescMap.get(firstTok);
                if (fieldName != null) {
                    handling = (String) oldSqlClauseHandlingMap.get(firstTok);
                    matchKey = firstTok;
                }
                else if (tokenizer.hasMoreTokens()) {
                    String twoToks = firstTok + " " + tokenizer.nextToken();
                    
                    fieldName = (String) oldSqlClauseLayerDescMap.get(twoToks);
                    if (fieldName != null) {
                        handling = (String) oldSqlClauseHandlingMap.get(twoToks);
                        matchKey = twoToks;
                    }
                }
            }
            
            if (fieldName == null) {
                // Found an undefined clause. These *should* always be
                // "extra SQL" clauses.  Regardless, any undefined clause
                // means that the entire SQL query cannot be converted
                // since it refers to some unhandled field in the THEMIS2 table
                // structure has no *verbatim* equivalent in THEMIS3.
                log.aprintln("Unknown or unhandled conditional in old SQL query: " + curClause);
                throw new OldQueryException("Old SQL query cannot be converted due to " +
                                            "unknown/unhandled clause: " + curClause);
            }
            
            if (handling == CONVERT_EXTRACT) {
                Matcher relation = relationals.matcher(curClause);
                if (relation.find()) {
                    String val = curClause.substring(relation.end());
                    fieldList.add(fieldName);
                    fieldValuesList.add(val.trim());
                }
                else {
                    String msg = "Could not extract restriction from old query clause: " + curClause;
                    log.aprintln(msg);
                    throw new OldQueryException(msg);
                }
            }
            else {
                // Reminder: query string was normalized to lower case
                // earlier....
                if (fieldName.equals(ThemisAddLayerWrapper.IMAGE_TYPE))
                {
                    if (curClause.equals("image_type = 0")) {
                        fieldList.add(fieldName);
                        fieldValuesList.add(ThemisAddLayerWrapper.TYPE_IR);
                    }
                    else if (curClause.equals("image_type = 1")) {
                        fieldList.add(fieldName);
                        fieldValuesList.add(ThemisAddLayerWrapper.TYPE_VIS);
                    }
                    else
                        log.aprintln("unhandled 'image_type' clause: " + matchKey);
                }
                else if (fieldName.equals(ThemisAddLayerWrapper.STATUS))
                {
                    if (curClause.equals("status != 'planned'")) {
                        fieldList.add(fieldName);
                        fieldValuesList.add(wrapper.statusAnything);
                    }
                    else if (curClause.equals("status = 'uplinked'")) {
                        fieldList.add(fieldName);
                        fieldValuesList.add(wrapper.statusUplinked);
                    }
                    else if (curClause.equals("status = 'downlinked'")) {
                        fieldList.add(fieldName);
                        fieldValuesList.add(wrapper.statusDownlinked);
                    }
                    else if (curClause.equals("not isnullq2.qube_stage")) {
                        fieldList.add(fieldName);
                        fieldValuesList.add(wrapper.statusHasRDR);
                    }
                    else 
                        log.aprintln("unhandled 'status' clause: " + curClause);
                }
                else if (fieldName.equals(ThemisAddLayerWrapper.VIS_SUMMING))
                {
                    boolean handled = false;
                    
                    // Find and log VIS summing restriction.
                    int idx = curClause.indexOf('=');
                    if (idx >= 0) {
                        try {
                            int val = Integer.parseInt(curClause.substring(idx + 1).trim());
                            summingValues.append(val);
                            summingValues.append(' ');
                        }
                        catch (NumberFormatException e) {
                            log.aprintln("bad VIS summing clause: " + curClause);
                        }
                    }
                    
                    if (!handled)
                        log.aprintln("unhandled VIS summing clause: " + curClause);
                }
            }
        }
        
        // Check whether we accumulated any VIS summing restrictions.
        // Otherwise, record that all variants are allowed.
        if (summingValues.length() > 0) {
            fieldList.add(ThemisAddLayerWrapper.VIS_SUMMING);
            fieldValuesList.add(summingValues);
        }
        else {
            fieldList.add(ThemisAddLayerWrapper.VIS_SUMMING);
            fieldValuesList.add(ThemisAddLayerWrapper.ALL_VIS_SUM);
        }
        
        layerDesc.dlgFieldNames = (String[]) fieldList.toArray(new String[0]);
        layerDesc.dlgFieldValues = fieldValuesList.toArray(new Object[0]);
        
        return layerDesc;
    }
    
    
    /**
     * Returns string describing database to which to connect to
     * retrieve data for this stamp layer.  Override of parent to
     * connect to THEMIS 3 database.
     */
    protected String getDbUrl()
    {
        return dbThemisUrl;
    }
    
    public Stamp createStamp(ResultSet rs)
    throws SQLException
    {
        int colCount = rs.getMetaData().getColumnCount();
        Object[] fields = new Object[colCount];
        for (int i=0; i<colCount; i++)
            fields[i] = rs.getObject(i+1);
        
        Stamp s = stampPool.getStamp(
                                     rs.getString("filename"),
                                     // DB east lon => JMARS west lon
                                     360-rs.getDouble("nw_lon"), rs.getDouble("nw_lat"),
                                     360-rs.getDouble("ne_lon"), rs.getDouble("ne_lat"),
                                     360-rs.getDouble("sw_lon"), rs.getDouble("sw_lat"),
                                     360-rs.getDouble("se_lon"), rs.getDouble("se_lat"),
                                     fields
        );
        
        String pdsImageUrl = rs.getString("qube_file_loc");
        if (pdsImageUrl != null)
            try
            {
                s.pdsImageUrl = new URL(pdsImageUrl);
            }
            catch(MalformedURLException e)
            {
                if (badUrlExceptions < 4)
                {
                    ++badUrlExceptions;
                    log.aprintln("------------------------------------");
                    log.aprintln("INVALID URL ENCOUNTERED IN DATABASE:");
                    log.aprintln(pdsImageUrl);
                    log.aprintln(e);
                }
            }
        
        return  s;
    }
    
    /**
     * A hack that allows the {@link SqlDbCacheCreator} class to override
     * the normal "Is-this-Student-JMARS" check process for the purpose of
     * of creating stamp cache ZIP files targeted at the student version,
     * i.e., for convoluted reasons, we need to use a non-student-version
     * of JMARS to do this, and must override the normal behavior.  
     */
    protected boolean isStudentApplication()
    {
        return Main.isStudentApplication();
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
        return new ThemisAddLayerWrapper(container, layerDesc, includeExtras);
    }
    
    protected class ThemisAddLayerWrapper
    extends StampFactory.AddLayerWrapper
    {
        // Layer description field name keys
        public static final String IMAGE_TYPE = "image_type";
        public static final String STATUS = "status";
        public static final String MIN_ANGLE = "min_incidence_angle";
        public static final String MAX_ANGLE = "max_incidence_angle";
        public static final String MIN_ORBIT = "min_orbit";
        public static final String MAX_ORBIT = "max_orbit";
        public static final String MIN_LAT = "min_latitude";
        public static final String MAX_LAT = "max_latitude";
        public static final String MIN_LON = "min_longitude";
        public static final String MAX_LON = "max_longitude";
        public static final String EXTRA_SQL = "extra_SQL";
        public static final String VIS_SUMMING = "vis_summing";
        
        public static final String ALL_VIS_SUM = "ALL";
        
        // Image type description
        public static final String TYPE_BOTH = "";
        public static final String TYPE_IR  = "IR";
        public static final String TYPE_VIS = "VIS";
        
        // Field display components
        protected JComboBox dropType = new JComboBox(
                                                     new String[] { TYPE_BOTH,
                                                                    TYPE_IR,
                                                                    TYPE_VIS }
        );
        
        protected String statusAnything = "";
        protected String statusUplinked = "Uplinked";
        protected String statusDownlinked = "Downlinked";
        protected String statusHasRDR = "Has an RDR";
        protected JComboBox dropStatus = new JComboBox(
                                                       new String[] { statusAnything,
                                                                      statusUplinked,
                                                                      statusDownlinked,
                                                                      statusHasRDR }
        );
        
        protected JCheckBox chkSummingAll = new JCheckBox("All", true);
        protected JCheckBox chkSumming1 = new JCheckBox("1", true); 
        protected JCheckBox chkSumming2 = new JCheckBox("2", true);
        protected JCheckBox chkSumming4 = new JCheckBox("4", true); 
        
        
        protected JTextField txtMinAngle = new PasteField();
        protected JTextField txtMaxAngle = new PasteField();
        protected JTextField txtMinOrbit = new PasteField();
        protected JTextField txtMaxOrbit = new PasteField();
        protected JTextField txtMinLat = new PasteField();
        protected JTextField txtMaxLat = new PasteField();
        protected JTextField txtMinLon = new PasteField();
        protected JTextField txtMaxLon = new PasteField();
        protected JTextField txtExtraSql = new PasteField();
        
        
        public ThemisAddLayerWrapper(Container container, LayerDescription layerDesc,
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
         * @see #buildContainer
         */
        protected Object[] getFieldList()
        {
            Object[] fields = null;
            
            // Check that this is the internal version of JMARS and 
            // this factory is not being used to create a stamp cache
            // ZIP file for the student version.
            if (Main.isInternal() && !isStudentApplication()) {
                JPanel sumPanel = new JPanel();
                sumPanel.add(chkSummingAll);
                sumPanel.add(chkSumming1);
                sumPanel.add(chkSumming2);
                sumPanel.add(chkSumming4);
                
                fields = new Object[] { "Image type:", dropType,
                                        "Image status:", dropStatus,
                                        "Min incidence angle:", txtMinAngle,
                                        "Max incidence angle:", txtMaxAngle,
                                        "Min orbit:", txtMinOrbit,
                                        "Max orbit:", txtMaxOrbit,
                                        "Min latitude:", txtMinLat,
                                        "Max latitude:", txtMaxLat,
                                        "Westernmost longitude:", txtMinLon,
                                        "Easternmost longitude:", txtMaxLon,
                                        "VIS Summing", sumPanel,
                                        "Extra SQL condition:", txtExtraSql };
            }
            else {
                fields = new Object[] { "Image type:", dropType,
                                        "Image status:", dropStatus,
                                        "Min incidence angle:", txtMinAngle,
                                        "Max incidence angle:", txtMaxAngle,
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
            
            StringBuffer summingBuf = new StringBuffer();
            if (chkSummingAll.isSelected())
                summingBuf.append(ALL_VIS_SUM);
            else
            {
                if (chkSumming1.isSelected()) summingBuf.append("1 ");
                if (chkSumming2.isSelected()) summingBuf.append("2 ");
                if (chkSumming4.isSelected()) summingBuf.append("4 ");
            }
            
            layerDesc.dlgFieldNames = new String[] {IMAGE_TYPE, STATUS,
                                                    MIN_ANGLE, MAX_ANGLE,
                                                    MIN_ORBIT, MAX_ORBIT,
                                                    MIN_LAT, MAX_LAT,
                                                    MIN_LON, MAX_LON,
                                                    VIS_SUMMING,
                                                    EXTRA_SQL
            }; 
            
            layerDesc.dlgFieldValues = new Object[] {dropType.getSelectedItem(), dropStatus.getSelectedItem(),
                                                     txtMinAngle.getText(), txtMaxAngle.getText(),
                                                     txtMinOrbit.getText(), txtMaxOrbit.getText(),
                                                     txtMinLat.getText(), txtMaxLat.getText(),
                                                     txtMinLon.getText(), txtMaxLon.getText(),
                                                     summingBuf.toString(),
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
                dropType.setSelectedItem(TYPE_BOTH);
                dropStatus.setSelectedItem(statusAnything);
                txtMinAngle.setText("");
                txtMaxAngle.setText("");
                txtMinOrbit.setText("");
                txtMaxOrbit.setText("");
                txtMinLat.setText("");
                txtMaxLat.setText("");
                txtMinLon.setText("");
                txtMaxLon.setText("");
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
                else if (IMAGE_TYPE.equals(field)) {
                    // Set type: ir/vis/both
                    if (value == null)
                        dropType.setSelectedItem(TYPE_BOTH);
                    else if (value instanceof String)
                        dropType.setSelectedItem( ((String)value).intern() );
                    else
                        log.aprintln("Non-string value for field '" + field + "' :" + value);
                }
                else if (STATUS.equals(field)) {
                    // Set status: uplinked/downlinked/both
                    if (value == null)
                        dropStatus.setSelectedItem(statusAnything);
                    else if (value instanceof String)
                        dropStatus.setSelectedItem( ((String)value).intern() );
                    else
                        log.aprintln("Non-string value for field '" + field + "' :" + value);
                }
                else if (MIN_ANGLE.equals(field)) {
                    
                    // Set min incidence angle
                    if (value == null)
                        txtMinAngle.setText("");
                    else
                        txtMinAngle.setText(value.toString());
                }
                else if (MAX_ANGLE.equals(field)) {
                    // Set max incidence angle
                    if (value == null)
                        txtMaxAngle.setText("");
                    else
                        txtMaxAngle.setText(value.toString());
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
                        VIS_SUMMING.equals(field))
                {
                    // For internal version only: set VIS Summing checkboxes
                    // according to settings.
                    if (value == null)
                        setAllSumming(true);
                    else if (!(value instanceof String))
                        log.aprintln("Non-string value for field '" + field + "' :" + value);
                    else
                    {
                        String summingStr = ((String) value).toUpperCase();
                        
                        if (summingStr.indexOf(ALL_VIS_SUM) >= 0)
                            setAllSumming(true);
                        else 
                        {
                            StringTokenizer tokenizer = new StringTokenizer(summingStr);
                            setAllSumming(false);
                            
                            while (tokenizer.hasMoreTokens()) 
                            {
                                String token = tokenizer.nextToken().trim();
                                
                                try {
                                    int summing = Integer.parseInt(token);
                                    
                                    switch (summing)
                                    {
                                    case 1:
                                        chkSumming1.setSelected(true);
                                        break;
                                    case 2:
                                        chkSumming2.setSelected(true);
                                        break;
                                    case 4:
                                        chkSumming4.setSelected(true);
                                        break;
                                    default:
                                        log.aprintln("bad VIS summing value: " + token);
                                    break;
                                    }
                                }
                                catch (NumberFormatException e) {
                                    log.aprintln("bad VIS summing value: " + token);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        protected void setAllSumming(boolean selected)
        {
            chkSummingAll.setSelected(selected);
            chkSumming1.setSelected(selected);
            chkSumming2.setSelected(selected);
            chkSumming4.setSelected(selected);
        }
        
        public String getDefaultName()
        {
            Object type = dropType.getSelectedItem();
            if     (type == TYPE_BOTH) return  "IR/VIS Themis RDR stamps";
            else if (type == TYPE_IR  ) return  "IR Themis RDR stamps";
            else if (type == TYPE_VIS ) return  "VIS Themis RDR stamps";
            
            // Should never happen
            log.aprintln("INVALID dropType value: " + type);
            return  "Stamps [INVALID NAME]";
        }
        
        public String getSql()
        {
            //////////////////////////////////////////////////////////////
            // Original THEMIS2 database code -- SAVE ME FOR A WHILE....
            //
            //            String qube_tbl = Config.get("stamps.qube_tbl");
            //            if(qube_tbl == null)
            //                qube_tbl = "MISSING_CONFIG_SETTING";
            //            
            //            String fields;
            //            if(Main.isInternal())
            //                fields = "geometry.*, obs.owner, obs.description, obs.orbit, obs.start, obs.sclk, obs.status, obs.summing, obs.duration, obs.gain, obs.offset, obs.bandcfg, obs.image_type, q1.qube_file_loc, q1.qube_stage, geometry_detail.incidence_angle";
            //            else
            //                fields = "geometry.*, obs.description, obs.orbit, obs.start, obs.sclk, obs.duration, obs.gain, obs.offset, obs.bandcfg, obs.image_type, q1.qube_file_loc, q1.qube_stage, geometry_detail.incidence_angle";
            //            
            //            String sql =
            //                "select " + fields +
            //                " from geometry inner join obs" +
            //                " on geometry.filename=obs.filename" +
            //                " left outer join "+qube_tbl+" q1" +
            //                "  on geometry.filename=q1.qube_id" +
            //                "  and q1.qube_stage='EDR'" +
            //                " left outer join "+qube_tbl+" q2" +
            //                "  on geometry.filename=q2.qube_id" +
            //                "  and q2.qube_stage='RDR'" +
            //                " left outer join geometry_detail" +
            //                "  on geometry.filename=geometry_detail.filename" +
            //                "  and frame_id=0" +
            //                "  and point_type='CT'" +
            //                "  and band_idx=1" +
            //                " where not ifnull(cal,0)" +
            //                " and not ifnull(shutter,0)";
            //
            //            String qube_tbl = Config.get("stamps.qube_tbl");
            //            if(qube_tbl == null)
            //                qube_tbl = "MISSING_CONFIG_SETTING";
            //            
            //          Original THEMIS2 database code -- SAVE ME FOR A WHILE....
            //////////////////////////////////////////////////////////////
            
            
            String fields;
            
            // Check that this is the internal version of JMARS and 
            // this factory is not being used to create a stamp cache
            // ZIP file for the student version.
            if (Main.isInternal() && !isStudentApplication())
                fields = "themis2.geometry.*, " + 
                         "obs.owner, obs.description, obs.orbit, obs.start, obs.sclk, " +
                         "obs.status, obs.summing, obs.duration, obs.gain, obs.offset, "+ 
                         "obs.bandcfg, obs.image_type, " +
                         "s1.location, s1.stage, qubgeom.incidence_angle";
            else
                fields = "themis2.geometry.*, " + 
                         "obs.description, obs.orbit, obs.start, obs.sclk, obs.duration, " +
                         "obs.gain, obs.offset, obs.bandcfg, obs.image_type, " +
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

            return getSql(sql, false);
        }
        
        public String getCacheSql()
        {
            // Internal users currently can't use a cached Themis stamp database
            // due to column differences and status updates on planned observations.
            // It is also assumed to be unnecessary (good network performance and
            // fewer number of users than for other scenarios such as the public
            // JMARS version).
            //
            // Also, Check whether free-form extra SQL conditions were applied;
            // if so, we can't use the cached stamp database data.
            //
            // NOTE:  Check that this is the internal version of JMARS and 
            // this factory is not being used to create a stamp cache
            // ZIP file for the student version.
            if ( (Main.isInternal() && !isStudentApplication()) ||
                !txtExtraSql.getText().equals(""))
                return null;
            else
                return getSql("select * from " + getCacheTableName(), true);
        }
        
        public String getCacheTableName()
        {
            // NOTE:  We must use a special method for checking for the 
            // student version of JMARS in order to support the
            // stamp-cache-ZIP creation process.  DO NOT CALL the
            // Main.isStudentApplication() method directly!!
            if (isStudentApplication())
                return "cached_vis";
            else
                return "cached_themis";
        }
        
        /**
         * Cached THEMIS stamp table and data are based on the following query
         * used to dump the data from the *public* database:
         *
         * select themis2.geometry.*, obs.description, obs.orbit, obs.start, obs.sclk, 
         * obs.duration, obs.gain, obs.offset, obs.bandcfg, obs.image_type, 
         * s1.location, s1.stage, qubgeom.incidence_angle 
         * from themis2.geometry inner join themis2.obs on 
         * themis2.geometry.filename = themis2.obs.filename 
         * left outer join stage s1 on themis2.geometry.filename = s1.file_id and s1.stage='EDR' 
         * left outer join stage s2 on themis2.geometry.filename = s2.file_id and s2.stage='RDR' 
         * left outer join qubgeom on themis2.geometry.filename = qubgeom.file_id and 
         * framelet_id = 0 and qubgeom.point_id = 'CT' and qubgeom.band_idx = 1 
         * where not ifnull(cal,0) and not ifnull(shutter,0); 
         *
         * NOTE: The output from the command above needs to be piped through sed -e 's/NULL//g',
         *       or it chokes the dbFileTable reader
         *
         * NOTE: Above query is only compatible for PUBLIC JMARS USERS!!
         */
        public String getCacheTableFieldsSql()
        {
            // Note the omission of size limits on integer fields from original MySQL table
            // definitions below:  HSQLDB does not allow these as of 1.7.2 version.
            String sql = "filename char(9) default '' NOT NULL, " + 
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
                         "touched char(14) NOT NULL, " +             // Note: is really "timestamp(14)", but HSQLDB choked on MYSQL outfile
                         "description char(80) default NULL, " + 
                         "orbit int default NULL, " +                // Was:  int(10)      
                         "start double default 0 NOT NULL, " + 
                         "sclk double default NULL, " + 
                         "duration double default NULL, " + 
                         "gain smallint default NULL, " +            // Was:  smallint(5) 
                         "offset smallint default NULL, " +          // Was:  smallint(6)
                         "bandcfg smallint default NULL, " +         // Was:  smallint(5) 
                         "image_type tinyint default NULL, " +       // Was:  tinyint(1)
                         "location char(255) default NULL, " + 
                         "stage char(3) default '', " +
                         "incidence_angle double default NULL, " + 
                         "UNIQUE (filename)";
            
            // Internal users currently can't use a cached Themis stamp database
            // due to column differences and status updates on planned observations
            //
            // Exception: We allow "use" of a cached database when an internal
            // version of JMARS is being use to create a stamp cache ZIP file
            // targeted for the student version.
            if (Main.isInternal() && !isStudentApplication())
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
                                 "incidence_angle",
                                 "orbit",
                                 "touched"
            };
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
            return "geometry.touched";
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
        
        private String getSql(String sql, boolean cacheQuery)
        {
            // status: uplinked/downlinked/both
            Object status = dropStatus.getSelectedItem();
            String sqlAppend = "";
            
            if (!cacheQuery) {
                sqlAppend += " and ";
                
                // These tests are only valid for non-cached THEMIS data queries.
                if (status == statusAnything  ) sqlAppend += "status!='PLANNED'";
                else if (status == statusUplinked  ) sqlAppend += "status='UPLINKED'";
                else if (status == statusDownlinked) sqlAppend += "status='DOWNLINKED'";
                else if (status == statusHasRDR)
                    sqlAppend += "not isnull(s2.stage)";
                else
                    log.aprintln("INVALID dropStatus value: " + status);
            }
            // Only this test is useful for cached THEMIS data queries.
            else if (status == statusHasRDR) {
                sqlAppend += " and stage is not null";
            }
            
            // type: ir/vis/both
            Object type = dropType.getSelectedItem();
            if     (type == TYPE_BOTH) ;
            else if (type == TYPE_IR  ) sqlAppend += " and image_type=0";
            else if (type == TYPE_VIS ) sqlAppend += " and image_type=1";
            else
                log.aprintln("INVALID dropType value: " + type);
            
            if (Main.isInternal() &&
                    (type == TYPE_BOTH || type == TYPE_VIS))
            {
                // VIS summing restrictions
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
                    
                    if (!chkSummingAll.isSelected()) {
                        String sqlAdd = "";
                        
                        // Don't exclude IR images if they are part of the
                        // type selected.
                        if (type == TYPE_BOTH)
                            sqlAdd += triggeredOR + "image_type = 0";
                        
                        // Handle VIS summing restrictions
                        if (chkSumming1.isSelected())
                            sqlAdd += triggeredOR + "summing = 1";
                        if (chkSumming2.isSelected())
                            sqlAdd += triggeredOR + "summing = 2";
                        if (chkSumming4.isSelected())
                            sqlAdd += triggeredOR + "summing = 4";
                        
                        if (sqlAdd.length() > 0)
                            sqlAppend += " and (" + sqlAdd + ")";
                    }
            }
            
            // min/max incidence angle
            String minAngle = txtMinAngle.getText();
            String maxAngle = txtMaxAngle.getText();
            if (!minAngle.equals("")) sqlAppend += " and incidence_angle >= " + minAngle;
            if (!maxAngle.equals("")) sqlAppend += " and incidence_angle <= " + maxAngle;
            
            // min/max orbit
            String minOrbit = txtMinOrbit.getText();
            String maxOrbit = txtMaxOrbit.getText();
            if (!minOrbit.equals("")) sqlAppend += " and orbit >= " + minOrbit;
            if (!maxOrbit.equals("")) sqlAppend += " and orbit <= " + maxOrbit;
            
            // min/max lat
            String minLat = txtMinLat.getText();
            String maxLat = txtMaxLat.getText();
            if(!minLat.equals("")) sqlAppend += " and " + latCond(">="+minLat);
            if(!maxLat.equals("")) sqlAppend += " and " + latCond("<="+maxLat);

            // min/max lon
            String minLon = txtMinLon.getText();
            String maxLon = txtMaxLon.getText();
            if(!minLon.equals("")||!maxLon.equals("")) {
            	String lonFilter = lonCond(minLon,maxLon);
            	if (!lonFilter.equals("")) sqlAppend += " and " + lonCond(minLon, maxLon);
            }
            
            // extra sql
            String extraSql = txtExtraSql.getText();
            if (!extraSql.equals("")) sqlAppend += " and " + extraSql;
            
            sqlAppend += " order by start";
            
            if (sql.toLowerCase().indexOf("where") >= 0 ||
                sqlAppend.toLowerCase().indexOf("and") < 0)
                sql += sqlAppend;
            else {
                StringBuffer buf = new StringBuffer(sqlAppend);
                String keyword = "and";
                
                int idx = sqlAppend.toLowerCase().indexOf(keyword);
                buf.replace(idx, idx + keyword.length(), "where");
                
                sql += buf.toString();
            }
            
            return  sql;
        }
    }
}
