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
import edu.asu.jmars.util.*;

import java.awt.Color;
import java.awt.Container;
import java.sql.*;


public class ThemisBtrStampFactory
extends ThemisStampFactory
{
    private static final String BROWSE_PATH_KEY = "stamps.btr.browse_http";
    private static final String BROWSE_PAGE_SUFFIX = ".html";
    
    private static DebugLog log = DebugLog.instance();
    
    private BtrStampPool btrStampPool = new BtrStampPool(); 
    
    
    public ThemisBtrStampFactory()
    {
        super("Themis");
    }
    
    public Layer.LView createLView()
    {
        if (!Main.isStudentApplication())
            return  null;
        else {
            // For Student JMARS, we automatically create a VIS stamp
            // layer.
            Layer.LView view = null;

            AddLayerWrapper wrapper = createWrapper(null, null, true);
            if (wrapper != null) {
                view = createLView(wrapper.getName(),
                                   wrapper.getSqlParms(),
                                   wrapper.getLayerDescription(),
                                   initialColumns,
                                   wrapper.getColor());
            }
            
            return view;
        }
    }
    
    protected Layer.LView createLView(String name,
                                      SqlStampLayer.SqlParameters parms,
                                      LayerDescription layerDesc,
                                      String[] initialColumns,
                                      Color unsColor
    )
    {
        return 
        new BtrStampLView(name,
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
            parmBlock instanceof BtrStampLView.StampParameters)
        {
            BtrStampLView.StampParameters parms = (BtrStampLView.StampParameters) parmBlock;
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
            view = new BtrStampLView(parms.name,
                                     getLayer(parms.sqlParms, parms.layerDesc),
                                     parms.initialColumns,
                                     parms.unsColor,
                                     parms.filColor,
                                     ThemisBtrStampFactory.this);
            view.setVisible(true);
            
            log.println("successfully recreated BtrStampLView");
        }
        
        return view;
    }
    
    public Stamp createStamp(ResultSet rs)
    throws SQLException
    {
        int colCount = rs.getMetaData().getColumnCount();
        Object[] fields = new Object[colCount];
        for(int i=0; i<colCount; i++)
            fields[i] = rs.getObject(i+1);
        
        Stamp s = btrStampPool.getStamp(
                                        rs.getString("filename"),
                                        // DB east lon => JMARS west lon
                                        360-rs.getDouble("nw_lon"), rs.getDouble("nw_lat"),
                                        360-rs.getDouble("ne_lon"), rs.getDouble("ne_lat"),
                                        360-rs.getDouble("sw_lon"), rs.getDouble("sw_lat"),
                                        360-rs.getDouble("se_lon"), rs.getDouble("se_lat"),
                                        fields
        );
        
        s.pdsImageUrl = null;
        
        return  s;
    }
    
    /**
     * Clears out projection-related information from stamps
     * in stamp pool.
     */
    synchronized void reprojectStampPool()
    {
        btrStampPool.reprojectStamps();
    }
    
    class BtrStamp extends Stamp
    {
        BtrStamp(String id,
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
            String httpPath = Config.get(BROWSE_PATH_KEY);
            if (httpPath != null) {
                if (!httpPath.endsWith("/"))
                    httpPath = httpPath + "/";
                
                return httpPath + this.id + BROWSE_PAGE_SUFFIX;
            }
            else
                return null;
        }
    }
    
    private class BtrStampPool extends StampPool
    {
        /**
         * Creates BTR THEMIS stamp instance using parameters.
         */
        protected Stamp createStamp(String id,
                                    double nwx, double nwy,
                                    double nex, double ney,
                                    double swx, double swy,
                                    double sex, double sey,
                                    Object[] data)
        {
            return new BtrStamp(id,
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
        AddLayerWrapper wrapper = new ThemisBtrAddLayerWrapper(container, layerDesc, includeExtras);
        
        // If this is either the Student version of JMARS or
        // another version acting as one (for creating stamp cache
        // ZIP files), and if no layer description was passed in,
        // then create a VIS-only wrapper as the default.
        if (layerDesc == null &&
            isStudentApplication())
        {
            // Update wrapper with a layer description for a
            // VIS stamp layer.
            LayerDescription newLayerDesc = new LayerDescription();
            newLayerDesc.dlgFieldNames = new String[] {ThemisBtrAddLayerWrapper.IMAGE_TYPE};
            newLayerDesc.dlgFieldValues = new Object[] {ThemisBtrAddLayerWrapper.TYPE_VIS};
            wrapper.updateLayerDescription(newLayerDesc);
        }

        return wrapper;
    }
    
    protected class ThemisBtrAddLayerWrapper
    extends ThemisStampFactory.ThemisAddLayerWrapper
    {
        public ThemisBtrAddLayerWrapper(Container container, LayerDescription layerDesc,
                                        boolean includeExtras)
        {
            super(container, layerDesc, includeExtras);
        }
        
        public String getDefaultName()
        {
            Object type = dropType.getSelectedItem();
            if     (type == TYPE_BOTH) return  "IR/VIS Themis Stamps";
            else if(type == TYPE_IR  ) return  "IR Themis Stamps";
            else if(type == TYPE_VIS ) return  "VIS Themis Stamps";
            
            // Should never happen
            log.aprintln("INVALID dropType value: " + type);
            return  "Stamps [INVALID NAME]";
        }
    }
}
