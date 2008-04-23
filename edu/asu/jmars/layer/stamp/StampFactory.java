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
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.io.Serializable;
import java.sql.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.EventListenerList;

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;

/**
 ** Base functionality for factories of {@link StampLView}s.
 ** Subclasses need only define the two methods {@link
 ** #createLView(LViewFactory.Callback)} and {@link
 ** #createStamp}. Most will implement createLView by instantiating a
 ** subclass of {@link StampFactory.AddLayerWrapper}.
 **/
public abstract class StampFactory
extends LViewFactory
{
    private static DebugLog log = DebugLog.instance();
    private static final int MAX_UNUSED_LAYERS = 5;
    
    private HashMap layers = new HashMap();
    private HashMap layerSQL = new HashMap();
    private HashMap layerDescriptions = new HashMap();
    private HashMap layerAccessTimes = new HashMap();
    
    protected static final String dbUrl = Config.get("db");
    protected static final String dbThemisUrl = Config.get("db_themis");
    
    private String instrument;
    protected String[] initialColumns;
    protected StampPool stampPool = new StampPool();

    /**
     ** Should contain a list of the latitude fields to be used in
     ** {@link #latCond} for doing inclusive latitude range queries.
     **/
    protected final String[] latitudeFields;
    protected final String[] longitudeFields;

   /**
    ** Given a "simple" latitude condition (resembling "<= 12"),
    ** returns an appropriate "compound" latitude condition:
    ** "(lat_field1 <= 12 or lat_field2 <= 12 ...)".
    **
    ** @throws UnsupportedOperationException if called for a factory
    ** that hasn't initialized {@link #latitudeFields}.
    **/
   protected String latCond(String simpleCond)
    {
       if(latitudeFields == null  ||  latitudeFields.length == 0)
	   throw  new UnsupportedOperationException(
	       "Latitude querying not implemented (programmer must " +
	       "initialize latitudeFields array in constructor)");

       String compoundCond = latitudeFields[0] + simpleCond;
       for(int i=1; i<latitudeFields.length; i++)
	   compoundCond += " or " + latitudeFields[i] + simpleCond;

       return  "(" + compoundCond + ")";
    }

	private double getLonFromText(String lonStr)
	 {
		String dirStr;
		
		int dirStart = Math.max(lonStr.indexOf('W'),
								lonStr.indexOf('E'));
		
		// If omitted, default user input to east longitude
		if(dirStart == -1)
			dirStr = "E";
		else
		 {
			dirStr = lonStr.substring(dirStart);
			lonStr = lonStr.substring(0, dirStart);
		 }

		double lon = Double.parseDouble(lonStr);
		
		if(dirStr.equalsIgnoreCase("W")) {
			lon = -lon;
		}
		 
		if (lon<0) { 
			lon+=360;
	 	} else if (lon>360) {
	 		lon-=360;
	 	}
		
		return  lon;
	 }
   
   
   
   
   /*
    * minStr == Westernmost longitude
    * maxStr == Easternmost longitude
    */
   protected String lonCond(String westStr, String eastStr)
   {
      if(longitudeFields == null  ||  longitudeFields.length == 0)
	   throw  new UnsupportedOperationException(
	       "Longitude querying not implemented (programmer must " +
	       "initialize longitudeFields array in constructor)");

      double west = 0.0;
      double east = 360.0;
      
      try {
	      if (westStr.trim().length()>0) {
	    	  west = getLonFromText(westStr);
	      } 
	      
	      if (eastStr.trim().length()>0) {
	    	  east = getLonFromText(eastStr);
	      }
      } catch (Exception e) {
    	  return "";
      }

      // In theory this test should never be true unless the user puts in values considerably out of normal range.
      // If we do encounter strange input, we won't do any longitude filtering
      if (west<0 || east>360) {
    	  return "";
      }
      
      if (east-west>=360) {
    	  return "";
      }
      
      // Special case for crossing the meridian
      if (west>east) {
    	  double min1 = west;
    	  double max1 = 360;
    	  double min2 = 0;
    	  double max2 = east;
    	  
	      String compoundCond1 = "("+ longitudeFields[0] + " >= " + min1 + ") and (" + longitudeFields[0] + " <= " + max1 + ")";
	      
	      for(int i=1; i<longitudeFields.length; i++)
		   compoundCond1 += " or " + "("+ longitudeFields[i] + " >= " + min1 + ") and (" + longitudeFields[i] + " <= " + max1 + ")";

	      String compoundCond2 = "("+ longitudeFields[0] + " >= " + min2 + ") and (" + longitudeFields[0] + " <= " + max2 + ")";
	      
	      for(int i=1; i<longitudeFields.length; i++)
		   compoundCond2 += " or " + "("+ longitudeFields[i] + " >= " + min2 + ") and (" + longitudeFields[i] + " <= " + max2 + ")";

	      
	      return  "(" + compoundCond1 + " or " + compoundCond2 + ")";    	  
      } else {
	      String compoundCond = "("+ longitudeFields[0] + " >= " + west + ") and (" + longitudeFields[0] + " <= " + east + ")";
	      
	      for(int i=1; i<longitudeFields.length; i++)
		   compoundCond += " or " + "("+ longitudeFields[i] + " >= " + west + ") and (" + longitudeFields[i] + " <= " + east + ")";
	
	      return  "(" + compoundCond + ")";
      }
   }
   
    /**
     ** @param instrument The name of the instrument that the stamps
     ** are from. This is used for the user-displayed name/desc of the
     ** factory, as well as for the title of the add layer dialog.
     **/
    public StampFactory(String instrument, String[] initialColumns,
			String[] latitudeFields, String[] longitudeFields)
    {
        this.instrument = instrument;
        this.initialColumns = initialColumns;
        this.latitudeFields = latitudeFields;
        this.longitudeFields = longitudeFields;
    }
    
    
    public static class LayerDescription implements Serializable, Cloneable
    {
        static final long serialVersionUID = 1L;
        
        // Parameters in revision #1 (which is also the very first
        // implementation; no legacy code).
        int revision = 1;
        String[] dlgFieldNames;
        Object[] dlgFieldValues;  // Note: All stored values must be Cloneable

        public Object clone()
        {
            LayerDescription copy = null;
            try {
                copy = (LayerDescription)super.clone();
                if (dlgFieldNames != null)
                    copy.dlgFieldNames = (String [])dlgFieldNames.clone();
                if (dlgFieldValues != null)
                    copy.dlgFieldValues = (Object[])dlgFieldValues.clone();
            }
            catch (CloneNotSupportedException e) {
                throw new InternalError(e.toString());
            }
            return copy; 
        }
    }
    
    /**
     ** Should return a stamp (of the appropriate type for a
     ** particular StampFactory subclass) filled with data from the
     ** current row of the given ResultSet. Should NOT move the row
     ** pointer of the ResultSet, or in any way affect its state.
     **/
    public abstract Stamp createStamp(ResultSet rs) throws SQLException;
    
    /**
     * Creates stamp view and layer and then calls specified callback.
     * The default implementation calls {@link #createWrapper} and
     * {@link #createLView(String, SqlStampLayer.SqlParameters, StampFactory.LayerDescription, String[], Color)} 
     * to create a dialog for user input and then to create the stamp view using that input.  
     * A subclass should override if it needs different behavior.
     * 
     * @param callback Generally a callback object once supplied
     * to {@link #createLView(LViewFactory.Callback)}.
     */
    public void createLView(Callback callback)
    {
        Layer.LView view = null;
        AddLayerDialog dialog = new AddLayerDialog();
        AddLayerWrapper wrapper = createWrapper(dialog.getContentPane(), null, true);
        
        if (wrapper != null) {
            dialog.postBuild(wrapper);
            dialog.show();
            
            if (!dialog.isCancelled()) {
                view = createLView(wrapper.getName(),
                                   wrapper.getSqlParms(),
                                   wrapper.getLayerDescription(),
                                   initialColumns,
                                   wrapper.getColor());
		callback.receiveNewLView(view);
	    }
        }
    }
    
    public Layer.LView createLView()
    {
        return  null;
    }
    
    /**
     * Creates stamp view using specified parameters.  The default
     * implementation creates a {@link StampLView} instance.  A
     * subclass should override if it needs different behavior.
     * <p>
     * NOTE: Overriding this version of createLView is the preferred
     * means for subclass customization, as opposed to overriding
     * {@link #createLView(LViewFactory.Callback)}.
     * 
     * @param name
     * @param parms
     * @param layerDesc
     * @param initialColumns
     * @param unsColor
     */
    protected Layer.LView createLView(String name,
                                      SqlStampLayer.SqlParameters parms,
                                      LayerDescription layerDesc,
                                      String[] initialColumns,
                                      Color unsColor
                                      )
    {
        return 
            new StampLView(name,
                           getLayer(parms, layerDesc),
                           initialColumns,
                           unsColor,
                           this);
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
            
            view = new StampLView(parms.name,
                                  getLayer(parms.sqlParms, parms.layerDesc),
                                  parms.initialColumns,
                                  parms.unsColor,
                                  parms.filColor,
                                  StampFactory.this);
            view.setVisible(true);
            
            log.println("successfully recreated StampLView");
        }
        
        return view;
    }
    
    /**
     ** Returns the instrument name (as supplied in the constructor).
     **/
    public String getName()
    {
        return  instrument;
    }
    
    /**
     ** Returns the string "Outlines of [instrument] observation
     ** polygons.", with [instrument] as supplied in the constructor.
     **/
    public String getDesc()
    {
        return "Outlines of " + instrument + " observation polygons.";
    }
    
    /**
     * Returns string describing database to which to connect to
     * retrieve data for this stamp layer.
     */
    protected String getDbUrl()
    {
        return dbUrl;
    }
    
    /**
     * Returns layer appropriate to specified SQL query parameters.
     * Layers are normally cached for re-use.  If a cached layer
     * for the query parameters has gone into a bad state, it will
     * will be recreated.
     * 
     * @param sqlParms SQL-related description of layer including
     * query and cache-database-related parameters.
     * @param layerDesc Description of layer for use with {@link StampFactory.AddLayerWrapper}.
     *
     * @see StampLayer#isBad
     */
    protected synchronized final Layer getLayer(SqlStampLayer.SqlParameters sqlParms,
                                                LayerDescription layerDesc)
    {
        // NOTE: This method must be kept synchronized: bad things could conceivably
        // happen otherwise during ".jmars" saved state restoration.
        log.println("Using SQL: " + sqlParms.sql);
        
        StampLayer layer = (StampLayer) layers.get(sqlParms.sql);
        if (layer == null ||
            layer.isBad()
           )
        {
            layers.put(sqlParms.sql, 
                       layer = createLayer(getDbUrl() +
                                           "user=" + Main.DB_USER +
                                           "&password=" + Main.DB_PASS,
                                           this, sqlParms));
            if (layer != null) {
                layerSQL.put(layer, sqlParms.sql);
                layerDescriptions.put(layer, layerDesc);
            }
        }
        
        // Update most recent access time for layer.
        if (layer != null)
            layerAccessTimes.put(layer, new Long(System.currentTimeMillis()));
        
        // Purge cached layers of too many excess unused layers (no current
        // stamp view using them).
        purgeExcessLayers();
        
        return  layer;
    }
    
    // Removes cached layers of that are unused (no current
    // stamp view using them), are the least recently used, and that
    // are in excess of the maximum allowed number of unused layers to
    // be stored.
    private synchronized final void purgeExcessLayers()
    {
        // Identify unused layers, those that are not presently associated
        // with a stamp view.
        Set unusedSet = new HashSet(layers.values());
        Iterator iterator = Main.testDriver.mainWindow.viewList.iterator();
        while (iterator.hasNext()) {
            Layer.LView view = (Layer.LView) iterator.next();
            if (view != null)
                unusedSet.remove(view.getLayer());
        }
        
        // If number of unused layers is above allowed excess limit,
        // sort them in order of most recent use, and remove those that
        // are least recently used.
        if (unusedSet.size() > MAX_UNUSED_LAYERS) {
            java.util.List unusedList = new ArrayList(unusedSet);
            long[] times = new long[unusedList.size()];
            
            for (int i=0; i < unusedList.size(); i++) {
                Layer layer = (Layer) unusedList.get(i);
                Long timeLong = (Long) layerAccessTimes.get(layer);
                if (timeLong != null)
                    times[i] = timeLong.longValue();
            }
                
            int removeCount = unusedList.size() - MAX_UNUSED_LAYERS;
            log.println("purging " + removeCount + " layers");
            
            while (removeCount > 0) {
                // Find least recently used of the unused layers.
                int lru = 0;
                for (int i=1; i < times.length; i++)
                    if (times[i] < times[lru])
                        lru = i;
                    
                // Remove the LRU layer from all mappings, etc.
                Layer layer = (Layer) unusedList.get(lru);
                if (layer != null) {
                    String sql = (String)layerSQL.get(layer);
                    
                    layers.remove(sql);
                    layerSQL.remove(layer);
                    layerDescriptions.remove(layer);
                    layerAccessTimes.remove(layer);
                }
                
                times[lru] = Long.MAX_VALUE;
                removeCount--;
            }
        }
    }

    /**
     * Returns copy of layer description corresponding to layer instance.  This is  
     * tracked at the factory level since the description is used in layer 
     * creation/recreation/alteration via the {@link StampFactory.AddLayerWrapper}.
     */
    protected synchronized final LayerDescription getLayerDescription(Layer layer)
    {
        LayerDescription desc = null;
        
        if (layer != null) {
            desc = (LayerDescription) layerDescriptions.get(layer);
            if (desc != null)
                desc = (LayerDescription) desc.clone();
        }
        
        return desc;
    }
    
    /**
     * Subclasses should override if they need a specialized layer.  The
     * default implementation returns an instance of {@link SqlStampLayer}.
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
        return new SqlStampLayer(dbUrl, factory, sqlParms);
    }
    
    /**
     * Clears out projection-related information from stamps
     * in stamp pool.  Subclasses must override if they subclass
     * the {@link StampPool} class.
     * 
     * <p>Stamps are only reprojected if out-of-sync with current
     * Main window projection.
     */
    synchronized void reprojectStampPool()
    {
        stampPool.reprojectStamps();
    }


    /**
     * Returns base (invariant) SQL-related parameters for layers created
     * via this factory or a subclass' factory.
     * 
     * @return SQL-related stamp data parameters
     */
    public final SqlStampLayer.SqlParameters getBaseSqlParms()
    {
        AddLayerWrapper wrapper = createWrapper(null, null, true);
        if (wrapper != null)
            return wrapper.getSqlParms();
        else
            return null;
    }
    
    /**
     * Returns copy of SQL parameters that is up-to-date for all
     * cache-related parameters.  Does not alter main database SQL
     * query, but may alter any and all cache-related parameters,
     * including the cache database SQL query.  Later is only changed
     * to reflect correct table name, not table structure changes (if any).
     * <p>
     * Intended use: updating of serialized parameters.
     * 
     * @return New cache-safe (in most cases) parameters; returns
     * <code>null</code> if passed parameters are <code>null</code>.
     */
    protected SqlStampLayer.SqlParameters getCacheSafeSqlParms(SqlStampLayer.SqlParameters parms)
    {
        SqlStampLayer.SqlParameters newParms = null;
        
        if (parms != null) {
            newParms = getBaseSqlParms();
            newParms.sql = parms.sql;
            
            // If cache queries are allowed in current JMARS version,
            // copy the passed cache SQL query as a default for the
            // new parameters.
            if (newParms.cacheQuerySql != null)
                newParms.cacheQuerySql = parms.cacheQuerySql;
            
            if (newParms.cacheTableName == null)
                newParms.cacheQuerySql = null;
            else if (parms.cacheQuerySql != null &&
                     parms.cacheTableName != null)
            {
                String query = parms.cacheQuerySql;
                int idx = query.indexOf(" from ");
                
                if (idx < 0) {
                    log.aprintln("Could not parse table name in cache SQL query");
                    newParms.cacheQuerySql = query;
                }
                else {
                    String baseStr = query.substring(0, idx);
                    String rest = query.substring(idx);
                    
                    StringTokenizer tokenizer = new StringTokenizer(rest);
                    if (tokenizer.countTokens() < 2) {
                        log.aprintln("Could not parse table name in cache SQL query");
                        newParms.cacheQuerySql = query;
                    }
                    else {
                        // Find table name; replace with current table name (may be the same). 
                        tokenizer.nextToken();
                        String oldTable = tokenizer.nextToken();
                        
                        rest = rest.replaceFirst(oldTable, newParms.cacheTableName);
                        newParms.cacheQuerySql = baseStr + rest; 
                    }
                }
            }
        }
        
        return newParms;
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
     * fields.  Interpretation of both field names and field values is 
     * left to subclass' definition for method.
     * 
     * @param includeExtras If <code>true</code>, then a stamp-color
     * {@link edu.asu.jmars.swing.ColorCombo} and a custom layer name text box are added
     * to the display components in the container in addition to
     * those in this instance's field list; if <code>false</code>, 
     * then these components are excluded.
     * 
     * @return Dialog which may be displayed and from which filled-in
     * layer description and corresponding SQL layer parameters can be
     * retrieved.
     */
    public abstract AddLayerWrapper createWrapper(Container container, LayerDescription layerDesc, boolean includeExtras);
    
    /**
     ** Useful display dialog class for subclasses of {@link StampFactory}
     ** implementing {@link #createLView(LViewFactory.Callback)}.
     ** Subclasses should extend {@link StampFactory.AddLayerWrapper}, create an instance
     ** of latter to wrap this dialog's content pane, and then 
     ** call {@link #postBuild} method.
     **/
    protected class AddLayerDialog implements ActionListener
    {
        protected JDialog dialog;
        protected boolean userHitOK = false;
        
        /**
         ** Constructs a modal dialog for adding stamp layer (of
         ** appropriate instrument type) but with an empty content pane.
         ** Use in conjunction with {@link StampFactory.AddLayerWrapper}.
         **/
        public AddLayerDialog()
        {
            dialog = new JDialog(Main.testDriver.getLManager(),
                                 "Add " + instrument + " stamp layer",
                                 true);
        }
        
        public Container getContentPane()
        {
            return dialog.getContentPane();
        }
        
        /**
         ** Must be called after constuction and after being added
         ** to an instance of {@link StampFactory.AddLayerWrapper}.  Packs the
         ** dialog content pane, sets the dialog location, and adds
         ** the dialog as a listener for OK/Cancel button events. 
         **/
        protected void postBuild(AddLayerWrapper wrapper)
        {
            dialog.pack();
            dialog.setLocation(Main.getLManager().getLocation());
            wrapper.addActionListener(this);
        }
        
        public void show()
        {
            dialog.setVisible(true);
        }

        /**
         ** Made public as an implementation side effect, do not use.
         **/
        public void actionPerformed(ActionEvent e)
        {
            if (e == null)
                return;
            
            if (e.getActionCommand() == AddLayerWrapper.OK)
                userHitOK = true;
            else if (e.getActionCommand() == AddLayerWrapper.CANCEL)
                userHitOK = false;
            
            dialog.setVisible(false);
        }
        
        public boolean isCancelled()
        {
            return !userHitOK;
        }
        
    }
    
    /**
     ** Useful utility class for subclasses of {@link StampFactory}
     ** implementing {@link StampFactory#createLView(LViewFactory.Callback)}.  
     ** Subclasses need only call the base constructor with the right useful
     ** arguments, and implement the two functions {@link #getDefaultName} 
     ** and {@link #getSql}.
     **/
    protected abstract class AddLayerWrapper implements ActionListener
    {
        public static final String OK = "OK";
        public static final String CANCEL = "CANCEL";
    
        protected Container container;
        protected LayerDescription layerDesc;
        protected EventListenerList listenerList = new EventListenerList();
        
        protected ColorCombo dropColor = new ColorCombo();
        protected JTextField txtName = new PasteField();
        
        protected JButton btnOK = new JButton("OK");
        protected JButton btnCancel = new JButton("Cancel");
        
        /**
         ** Constructs wrapper around specified container and
         ** adds display components to latter, including OK/Cancel
         ** buttons.  Feedback from latter can be custom-channeled
         ** via {@link #addActionListener}.
         **
         ** @param container Container to be wrapped.
         **
         ** @param layerDesc Description of layer as query field
         ** settings; will be cloned by constructor.
         **
         ** <p>IMPORTANT: Subclass constructors must invoke {@link #buildContainer} 
         **  unless a {@link Container} has not been specified.  It
         **  is not invoked here due to member initialization order
         **  constraints. 
         **/
        public AddLayerWrapper(Container container, LayerDescription layerDesc)
        {
            this.container = container;
            setLayerDescription(layerDesc);
        }
        
        /**
         * Returns list of field description pairs for adding into
         * a {@link Container} when called via {@link #buildContainer} 
         * method.  Each pair must consist of either a Component
         * instance or a String instance in any combination; the
         * pair elements will be displayed in left-to-right order.
         * 
         * Subclasses must customize this to get correct behavior
         * in {@link #buildContainer} method.
         * 
         * @return List containing pairs of label strings (usually)
         * and components to add as description/field
         * pairs to the container.  However, any combination of strings
         * and components are allowed.
         * 
         * @see #buildContainer
         */
        protected abstract Object[] getFieldList();
        
        /**
         * Returns a layout manager which accommodates the specified
         * number of rows and columns for field layout.  The default
         * implementation returns <code>null</code>.  The {@link
         * #buildContainer} method calls this method to obtain a 
         * custom layout manager, so if one is needed, a subclass
         * should override this method.
         * 
         * @see #buildContainer
         */
        protected LayoutManager getLayout(int rows, int columns, int hgap, int vgap)
        {
            return null;
        }
        
        /**
         ** Automatically called by this class' constructor to
         ** initiate the proper construction of the container's
         ** elements.
         **
         ** @param container Container to which to add display components
         ** used to get layer description from user.  Container should
         ** be empty in advance in most cases.
         **
         ** @param includeExtras If <code>true</code>, then a stamp-color
         ** {@link edu.asu.jmars.swing.ColorCombo} and a custom layer name text box are added
         ** to the display components in the container in addition to
         ** those in this instance's field list; if <code>false</code>, 
         ** then these components are excluded.
         **
         ** <p>IMPORTANT: This invokes {@link #getFieldList}.  Subclasses
         ** must customize latter to return list of description/field
         ** pairs to be added to container.  All strings are converted
         ** into JLabel components.  Components (converted or otherwise)
         ** are organized in order into a 2-column grid.
         **
         ** <p> Additionally, if requested, this method appends a stamp-color
         ** {@link edu.asu.jmars.swing.ColorCombo} and a custom layer name text box to displayed
         ** components (at the very end).  Also, it sets the layout to
         ** {@link BorderLayout}, and at the bottom of the container places
         ** OK and Cancel buttons.
         **
         ** <p> By default, fields are put into a subpanel using a {@link GridLayout}.
         ** However, if the {@link #getLayout} method is overridden
         ** to return a non-null value, whatever {@link LayoutManager} instance 
         ** it returns will be used instead to organize the fields.
         **
         ** @see #getFieldList
         ** @see #getLayout
         **/
        protected void buildContainer(Container container, boolean includeExtras)
        {
            // Get default field list, add a few of our own and grab an iterator.
            Object[] fieldList = getFieldList();
            Object[] fieldList2 =
            {
                    "Use stamp color:", dropColor,
                    "Custom layer name:", txtName
            };
            ArrayList tmp = new ArrayList();
            tmp.addAll(Arrays.asList(fieldList));

            // Only include stamp color and custom layer name controls if
            // requested.
            if (includeExtras)
                tmp.addAll(Arrays.asList(fieldList2));
            
            Iterator fieldIter = tmp.iterator();
            
            // Construct the "fields" section of the container.  Use custom a 
            // layout manager if available, otherwise use a grid as the default.
            JPanel fields = new JPanel();
            int numRows = tmp.size() / 2 + tmp.size() % 2;
            LayoutManager layout = getLayout(numRows, 2, 5, 1);
            if (layout == null)
                layout = new GridLayout(0, 2, 5, 1);
            fields.setLayout(layout);
            
            int count = 0;
            while(fieldIter.hasNext())
            {
                Object next = fieldIter.next();
                count++;
                if (next instanceof String)
                    next = new JLabel((String)next, 
                            (count % 2 > 0) ? SwingConstants.RIGHT : SwingConstants.LEFT);
                else if (next instanceof JTextField) // hitting enter same as clicking "OK" or "Cancel" button.
                    ( (JTextField) next ).addActionListener(this);
                if (next != null)
                    fields.add( (Component) next );
                else
                    log.aprintln("null field label/component reference at list position: " + count);
            }
            
            // Add "glue" at bottom of fields panel within an outer 
            // enclosing panel to reduce vertical expansion of field 
            // components in some types of containers.
            JPanel wrappedFields = new JPanel();
            wrappedFields.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            wrappedFields.setLayout(new BoxLayout(wrappedFields, BoxLayout.Y_AXIS));
            wrappedFields.add(fields);
            wrappedFields.add(Box.createVerticalGlue());
            
            // Construct the "buttons" section of the container.
            JPanel buttons = new JPanel();
            buttons.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            btnOK.addActionListener(this);
            buttons.add(btnOK);
            
            btnCancel.addActionListener(this);
            buttons.add(btnCancel);
            
            // Put it all together.
            container.setLayout(new BorderLayout());
            container.add(wrappedFields, BorderLayout.CENTER);
            container.add(buttons, BorderLayout.SOUTH);
        }
        
        /**
         * The specified listener will receive action events
         * whenever the OK or Cancel button is clicked in the
         * wrapped container.  These {@link ActionEvent} instances
         * will specify this wrapper instance as the source and
         * will use either {@link #OK} or {@link #CANCEL} as
         * the only action command strings.
         */
        public void addActionListener(ActionListener listener)
        {
            if (listener != null)
                listenerList.add(ActionListener.class, listener);
        }
        
        /**
         ** Made public as an implementation side effect, do not use.
         **/
        public void actionPerformed(ActionEvent e)
        {
            ActionEvent buttonEvent = null;
            
            if (e.getSource() == btnOK)
                buttonEvent = new ActionEvent(this, Event.ACTION_EVENT, OK);
            else if (e.getSource() == btnCancel)
                buttonEvent = new ActionEvent(this, Event.ACTION_EVENT, CANCEL);
            
            if (buttonEvent != null) {
                EventListener[] listeners = listenerList.getListeners(ActionListener.class);
                for (int i=0; i < listeners.length; i++)
                    if (listeners[i] != null)
                        ((ActionListener)listeners[i]).actionPerformed(buttonEvent);
            }
        }
        
        public String getName()
        {
            String name = txtName.getText();
            if(name.equals(""))
                name = getDefaultName();
            
            return name;
        }
        
        public Color getColor()
        {
            return dropColor.getColor();
        }

        /**
         ** Should return the default name of a view created from the
         ** container, which might depend on the contents of some
         ** fields. The default name isn't used if the user enters a
         ** custom name into the container. The base class
         ** implementation returns the enclosing {@link StampFactory}
         ** instance's {@link StampFactory#getName} return value.
         **/
        public String getDefaultName()
        {
            return StampFactory.this.getName();
        }
        
        
        public final SqlStampLayer.SqlParameters getSqlParms()
        {
            SqlStampLayer.SqlParameters sqlParms = new SqlStampLayer.SqlParameters();
            
            sqlParms.sql = getSql();
            sqlParms.cacheQuerySql = getCacheSql();
            sqlParms.cacheTableName = getCacheTableName();
            sqlParms.cacheTableFieldsSql = getCacheTableFieldsSql();
            sqlParms.cacheIndexFields = getCacheIndexFields();
            sqlParms.primaryKeyName = getPrimaryKeyName();
            sqlParms.orbitFieldName = getOrbitFieldName();
            sqlParms.serverLastModFieldName = getServerLastModFieldName();
            sqlParms.cacheLastModFieldName = getCacheLastModFieldName();
            sqlParms.importCacheFile = getImportCacheFileName();
            
            return sqlParms;
        }
        
        /**
         * Returns filled-in field values and corresponding field
         * names after container has been displayed and interacted with
         * by user. 
         * 
         * @return Returns layer description.  Field values may be
         * empty if user did not complete necessary entries to create 
         * layer; however, all field names are always supplied.
         */
        public abstract LayerDescription getLayerDescription();
        
        /**
         * Sets and the layer description (by *CLONING* it) and updates 
         * displayed field values in wrapped container.  The field names 
         * should probably not change, i.e., behavior is undefined in this case.
         * 
         * <p>NOTE 1: Subclasses *MUST* OVERRIDE this method and call the
         * superclass implementation within their implementation.
         * 
         * <p>NOTE 2: Must only be called after {@link #buildContainer}
         * method has been called to complete initialization of wrapped
         * container.
         */
        public void updateLayerDescription(LayerDescription layerDesc)
        {
            setLayerDescription(layerDesc);
        }

        /**
         * Sets layer description for wrapper by cloning parameter.  
         * Validates field name and value compatbility for length, 
         * null lists, etc.  The layer description or 
         * BOTH names and values may be <code>null</code>.
         */
        private final void setLayerDescription(LayerDescription layerDesc)
        {
            if (layerDesc != null) {
                if (layerDesc.dlgFieldNames != null &&
                    layerDesc.dlgFieldValues != null)
                {
                    if (layerDesc.dlgFieldNames.length != layerDesc.dlgFieldValues.length)
                    {
                        String msg = "Field name and value list length mismatch";
                        log.println(msg);
                        throw new IllegalArgumentException(msg);
                    }
                }
                else if (layerDesc.dlgFieldNames != null ||
                         layerDesc.dlgFieldValues != null)
                {
                    String msg = "Field name and value list null/not-null mismatch";
                    log.println(msg);
                    throw new IllegalArgumentException(msg);
                }
                
                this.layerDesc = (LayerDescription)layerDesc.clone();
            }
            else
                layerDesc = null;
        }
        
        /**
         ** Should return the sql query appropriate to satisfy the
         ** data the user has entered into the container.
         **/
        public abstract String getSql();
        
        /**
         ** Returns name of the primary key field for stamps in 
         ** the SQL database (for whichever table contains this).
         **/
        public abstract String getPrimaryKeyName();
        
        /**
         ** Returns name of the orbit field for stamps in the SQL
         ** database (for whichever table contains this).
         **/
        public abstract String getOrbitFieldName();
        
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
        public abstract String getServerLastModFieldName();

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
        public abstract String getCacheLastModFieldName();

        /**
         ** Should return the sql query appropriate to satisfy the
         ** data the user has entered into the container for use with
         ** a JMARS-distributed HSQLDB-based database file for
         ** precached stamp data.
         **
         ** Default implementation returns NULL.
         **/
        public String getCacheSql()
        {
            return null;
        }
        
        /**
         ** Should return the sql table name used in conjunction with
         ** the JMARS-distributed HSQLDB-based database file for precached stamp data.
         **
         ** Default implementation returns NULL.
         **
         ** @see #getCacheTableFieldsSql
         **/
        public String getCacheTableName()
        {
            return null;
        }
        
        /**
         ** Should return the sql table fields description appropriate
         ** to match the content of the JMARS-distributed HSQLDB-based 
         ** database file for precached stamp data.  Any primary key
         ** declaration must be included.  The SQL "()" syntax surrounding
         ** the column declarations should *NOT* be included.
         **
         ** Default implementation returns NULL.
         **
         ** @see #getCacheTableName
         **/
        public String getCacheTableFieldsSql()
        {
            return null;
        }
        
        /**
         ** Should return the names of sql table fields needing indexes
         ** to speed up search.  These must match the table created to
         ** cache the SQL-based stamp data.  Only used for secondary (non-primary) 
         ** keys on single columns.
         **
         ** Default implementation returns NULL.
         **
         ** @see #getCacheTableName
         **/
        public String[] getCacheIndexFields()
        {
            return null;
        }
        
        /**
         ** Should return the HSQLDB-based database filename for
         ** precached stamp data.  Must be compatible with JMARS
         ** distribution scheme, i.e., JAR files.
         **
         ** Default implementation returns NULL.
         **/
        public String getImportCacheFileName()
        {
            return null;
        }
    }
    
    
    /**
     * Pool for storing {@link Stamp} instances.  Used to reduce
     * storage needs across multiple stamp layers, especially those
     * created by requerying with different parameters within
     * {@link StampLView}.
     * <p>
     * NOTE: Subclasses must override the {@link #createStamp} method
     * in this class if they need to create subclass instances of
     * {@link Stamp}.
     */
    protected class StampPool
    {
        protected final HashMap stampMap = new HashMap();
        protected ProjObj originalPO = Main.PO;
    
        /**
         * Returns {@link Stamp} instance from pool corresponding to specified
         * stamp ID.  Stamp may not exist.
         * 
         * @param id Stamp ID for requested stamp; used to retrieve
         * stamp from/to pool.  Must not be <code>null</code>.
         * @return Returns specified stamp; creates if necessary.  
         * @return Returns specified stamp; <code>null</code> if stamp
         * does not exist. 
         * @throws IllegalArgumentException Thrown if stamp ID is <code>null</code>. 
         * 
         * @see #getStamp(String, double, double, double, double, double, double, double, double, Object[])
         */
        synchronized protected final Stamp getStamp(String id)
        {
            if (id == null)
                throw new IllegalArgumentException("null stamp ID");
            
            return (Stamp)stampMap.get(id);
        }
        
        /**
         * Returns {@link Stamp} instance from pool corresponding to specified
         * stamp ID.  If the stamp does not exist, it is created with the
         * specified parameters.  Note that if a stamp already exists,
         * its field values are as-is, i.e., they may not correspond
         * to the parameters being passed to create an instance.
         * 
         * <p>Stamp instances are created via {@link #createStamp}. 
         * 
         * @param id Stamp ID for requested/created stamp; used to retrieve/
         * insert stamp from/to pool.  Must not be <code>null</code>.
         * @return Returns specified stamp; creates if necessary.  
         * Never returns <code>null</code>.
         * @throws IllegalArgumentException Thrown if stamp ID is <code>null</code>. 
         * 
         * @see #getStamp(String)
         * @see #createStamp
         */
        synchronized protected Stamp getStamp(String id,
                                              double nwx, double nwy,
                                              double nex, double ney,
                                              double swx, double swy,
                                              double sex, double sey,
                                              Object[] data)
        {
            Stamp s = getStamp(id);
            
            if (s == null) {
                s = createStamp(id,
                                nwx, nwy,
                                nex, ney,
                                swx, swy,
                                sex, sey,
                                data);
                stampMap.put(id, s);
            }
            
            return s;
        }
        
        /**
         * Creates {@link Stamp} instance using parameters.  Subclasses must override
         * this method to support correct behavior in 
         * {@link #getStamp(String, double, double, double, double, double, double, double, double, Object[])
         * getStamp}
         * if they need subclass instances of {@link Stamp}.
         * 
         * <p>NOTE: It is recommended that this method not be called directly.
         * The {@link #getStamp(String, double, double, double, double, double, double, double, double, Object[])
         * getStamp} 
         * method is the preferred way to retrieve and create stamps.
         */
        protected Stamp createStamp(String id,
                                    double nwx, double nwy,
                                    double nex, double ney,
                                    double swx, double swy,
                                    double sex, double sey,
                                    Object[] data)
        {
            return new Stamp(id,
                             nwx, nwy,
                             nex, ney,
                             swx, swy,
                             sex, sey,
                             data);
        }

        /**
         * Clears out projection-related information from stamps
         * in stamp pool.  Stamps are only reprojected if out-of-sync 
         * with current Main window projection.
         */
        synchronized void reprojectStamps()
        {
            if (originalPO != Main.PO) {
                Iterator iterator = stampMap.values().iterator();
                while (iterator.hasNext()) {
                    Stamp s = (Stamp) iterator.next();
                    if (s != null)
                        s.clearProjectedData();
                }
                
                originalPO = Main.PO;
            }
        }
    }
    
}
