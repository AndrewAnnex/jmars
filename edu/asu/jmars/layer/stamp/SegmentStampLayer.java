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


/*
 */
package edu.asu.jmars.layer.stamp;

import java.util.*;
import java.io.*;
import java.text.DecimalFormat;

import edu.asu.jmars.util.DebugLog;

/**
 * Stamp layer for importing and viewing THEMIS mission planning orbit track 
 * segment and existing intersected/nearby observation data.  Intended to serve 
 * solely as a diagnostic tool for automated mission planning.
 *  
 * @author hoffj MSFF-ASU
 */
public class SegmentStampLayer extends SqlStampLayer
{
    public static final String SEGMENT_TYPE = "Segments";
    public static final String OBS_TYPE = "Observations";
    public static final String INTERSECTED_TYPE = "Intersected";
    public static final String NEAR_TYPE = "Near";
    
    private static final DebugLog log = DebugLog.instance();
    private static final String HEADER_MARKER    = "#";
    private static final String VERSION          = "VERSION";
    private static final String NAME             = "NAME";
    private static final String TYPE             = "TYPE";
    private static final String ET               = "ET";
    private static final String UL               = "UL";
    private static final String UR               = "UR";
    private static final String LL               = "LL";
    private static final String LR               = "LR";
    private static final String COVERED          = "COVERED";
    private static final String CENTER_COVERAGE  = "CENTER_COVERAGE";
    private static final String ROI_COVERAGE     = "ROI_COVERAGE";
    private static final String SOLAR_INCIDENCE  = "SOLAR_ANGLE";
    private static final String INTERSECTED      = "INTERSECTED";
    private static final String NEAR             = "NEAR";
    private static final String ROI              = "ROI";
    private static final String HVAL             = "HVAL";
    private static final String HSUMMARY         = "HSUMMARY";
    private static final String HREPORT          = "HREPORT";
    
    private static final String[] segmentFieldOrder = new String[] {
                                                                    ET, UL, UR, LL, LR,
                                                                    COVERED, CENTER_COVERAGE, ROI_COVERAGE, SOLAR_INCIDENCE,
                                                                    INTERSECTED, NEAR, ROI
                                                                   }; 
    private static final String[] obsFieldOrder = new String[] {
                                                                NAME, TYPE,
                                                                ET, UL, UR, LL, LR,
                                                                COVERED, CENTER_COVERAGE, ROI_COVERAGE, SOLAR_INCIDENCE, 
                                                                HVAL, HSUMMARY, HREPORT,
                                                                INTERSECTED, NEAR, ROI
                                                               };
    private static final DecimalFormat threeDecFmt = new DecimalFormat("#.###");
    
    private Class[] columnClasses;
    private String[] columnNames;
    private Stamp[] stamps;
    private String filename;
    private Object layerType;
    
    
    /**
     * @param dbUrl
     * @param stampFactory
     * @param sqlParms
     * @param filename
     * @param layerType
     */
    public SegmentStampLayer(String dbUrl, StampFactory stampFactory,
                             SqlParameters sqlParms, 
                             String filename, Object layerType)
    {
        super(dbUrl, stampFactory, sqlParms);
        this.filename = filename;
        this.layerType = layerType;
        
        // Load stamps from file and/or database according to layer type.
        getStamps();
    }
    
    /**
     * @return Returns the layerType.
     */
    public Object getLayerType()
    {
        return layerType;
    }

    public synchronized Class[] getColumnClasses()
    {
        if (layerType != SEGMENT_TYPE &&
            layerType != OBS_TYPE)
            return super.getColumnClasses();
        
        // For SEGMENT and OBS layers only
        if (columnClasses == null) {
            String[] columns = getColumnNames();
            if (columns != null) {
                columnClasses = new Class[columns.length];
                for (int i=0; i < columnClasses.length; i++)
                    columnClasses[i] = String.class;
            }
        }
        
        return columnClasses;
    }
    
    public synchronized String[] getColumnNames()
    {
        if (layerType != SEGMENT_TYPE &&
            layerType != OBS_TYPE)
            return super.getColumnNames();
        
        // For SEGMENT and OBS layers only
        if (columnNames == null)
            columnNames = ((SegmentStampFactory)stampFactory).getColumns(layerType);
        return columnNames;
    }
    
    protected synchronized Stamp[] getStamps()
    {
        if (stamps == null)
            try {
                stamps = getStamps(filename);
            }
            catch (IOException e) {
                log.println(e);
                log.aprintln(e.getMessage());
            }
            
        return stamps;
    }
    
    private synchronized Stamp[] getStamps(String filename)
    throws IOException
    {
        if (filename != null &&
            filename.trim().length() > 0)
            return getStamps(new File(filename));
        else
            return null;
    }

    /**
     * Constructs exclusion zone set from file data in
     * exclusion zone file format.
     */
    private synchronized Stamp[] getStamps(File fin)
    throws IOException
    {
        return getStamps(new FileInputStream(fin));
    }

    /**
     */
    private synchronized Stamp[] getStamps(InputStream in)
    throws IOException
    {
        Stamp[] stamps = null;
        BufferedReader bin = new BufferedReader(new InputStreamReader(in));

        int count = 0;
        try
        {
            List stampList = new ArrayList();
            Set idSet = new HashSet();
            String begET = null;
            String endET = null;
            String name = null;
            String obsType = null;
            String hSummary = null;
            String hReport = null;
            double nwx = 0, nwy = 0,
                   nex = 0, ney = 0,
                   swx = 0, swy = 0,
                   sex = 0, sey = 0,
                   hVal = 0,
                   stampCoverage = 0,
                   centerCoverage = 0,
                   roiCoverage = 0,
                   solarIncidence = 0;
            int fieldIndex = 0;
            
            String[] fieldOrder;
            if (layerType == OBS_TYPE)
                fieldOrder = obsFieldOrder;
            else
                fieldOrder = segmentFieldOrder;
            
            boolean unparsedLine = false;
            String line = null;
            do
            {
                if (!unparsedLine) {
                    line = bin.readLine();
                    if (line != null) {
                        line = line.trim();
                        line = line.toUpperCase();
                        count++;
                    }
                }
                else
                    unparsedLine = false;
                
                // If the line is not empty, or if we still have pending
                // field data that hasn't been turned into a segment stamp
                // (something that can happen with older files that are
                // missing newer fields), then process line text and/or
                // pending field data.
                if (line != null ||
                    fieldIndex > 0)
                {
                    // Skip any blank or header lines; also skip field-parsing
                    // if we have reached the end-of-file with pending
                    // processed field data.
                    if (line != null &&
                        line.length() > 0 &&
                        !line.startsWith(HEADER_MARKER) &&
                        line.indexOf(VERSION.toUpperCase()) < 0)
                    {
                        StringTokenizer tokenizer = new StringTokenizer(line);
                        String field = tokenizer.nextToken();
                        int tokensRemaining = tokenizer.countTokens();
                        
                        // Skip any missing fields until finding one that corresponds
                        // to this line in the file.  This provides support for older
                        // file formats.
                        while (fieldIndex < fieldOrder.length &&
                               !field.equalsIgnoreCase(fieldOrder[fieldIndex]))
                            fieldIndex++;

                        if (fieldIndex >= fieldOrder.length) {
                            // Check for wrap-around to first record field; this can
                            // happen with an older file that is missing newer fields
                            // at the end of a record.
                            if (field.equalsIgnoreCase(fieldOrder[0])) {
                                fieldIndex = fieldOrder.length - 1;
                                unparsedLine = true;
                            }
                            else
                                throw new IOException("bad field or field order: " + field);
                        }
                        else if ((layerType == INTERSECTED_TYPE && field.equalsIgnoreCase(INTERSECTED)) ||
                                 (layerType == NEAR_TYPE && field.equalsIgnoreCase(NEAR)))
                        {
                            while(tokenizer.hasMoreTokens())
                                idSet.add(tokenizer.nextToken());
                        }
                        else if (field.equalsIgnoreCase(INTERSECTED) ||
                                 field.equalsIgnoreCase(NEAR) ||
                                 field.equalsIgnoreCase(ROI))
                            // Ignore if not used in this layer type
                            ;
                        else if (layerType == SEGMENT_TYPE ||
                                 layerType == OBS_TYPE)
                        {
                            if (tokensRemaining == 2)
                            {
                                // Extract either new ET range (new stamp) or
                                // stamp coordinates.
                                if (field.equalsIgnoreCase(ET)) {
                                    begET = tokenizer.nextToken();
                                    endET = tokenizer.nextToken();
                                }
                                else if (field.equalsIgnoreCase(UL)) {
                                    // Segments file east lon => JMARS west lon
                                    nwx = 360 - Double.parseDouble(tokenizer.nextToken());
                                    nwy = Double.parseDouble(tokenizer.nextToken());
                                }
                                else if (field.equalsIgnoreCase(UR)) {
                                    // Segments file east lon => JMARS west lon
                                    nex = 360 - Double.parseDouble(tokenizer.nextToken());
                                    ney = Double.parseDouble(tokenizer.nextToken());
                                }
                                else if (field.equalsIgnoreCase(LL)) {
                                    // Segments file east lon => JMARS west lon
                                    swx = 360 - Double.parseDouble(tokenizer.nextToken());
                                    swy = Double.parseDouble(tokenizer.nextToken());
                                }
                                else if (field.equalsIgnoreCase(LR)) {
                                    // Segments file east lon => JMARS west lon
                                    sex = 360 - Double.parseDouble(tokenizer.nextToken());
                                    sey = Double.parseDouble(tokenizer.nextToken());
                                }
                            }
                            else if (tokensRemaining == 1 &&
                                     field.equalsIgnoreCase(COVERED))
                                // Stamp coverage field
                                stampCoverage = Double.parseDouble(tokenizer.nextToken());
                            else if (tokensRemaining == 1 &&
                                    field.equalsIgnoreCase(CENTER_COVERAGE))
                               // Stamp center coverage field
                               centerCoverage = Double.parseDouble(tokenizer.nextToken());
                            else if (tokensRemaining == 1 &&
                                    field.equalsIgnoreCase(ROI_COVERAGE))
                               // ROI coverage field
                               roiCoverage = Double.parseDouble(tokenizer.nextToken());
                            else if (tokensRemaining == 1 &&
                                    field.equalsIgnoreCase(SOLAR_INCIDENCE))
                               // Solar incidence angle field
                               solarIncidence = Double.parseDouble(tokenizer.nextToken());
                            else if (tokensRemaining == 1 &&
                                    field.equalsIgnoreCase(HVAL))
                               // heuristic score field
                               hVal = Double.parseDouble(tokenizer.nextToken());
                            else if (field.equalsIgnoreCase(HSUMMARY)) {
                                int pos = line.indexOf(field);
                                hSummary = line.substring( pos + field.length() ).trim();
                             }
                            else if (field.equalsIgnoreCase(HREPORT)) {
                               int pos = line.indexOf(field);
                               hReport = line.substring( pos + field.length() ).trim();
                            }
                            else if (layerType == OBS_TYPE &&
                                     ( tokensRemaining == 0 ||
                                       tokensRemaining == 1) )
                            {
                                if (field.equalsIgnoreCase(NAME)) {
                                    // For NAME fields, the value is optional
                                    if (tokensRemaining == 1)
                                        name = tokenizer.nextToken();
                                }
                                else if (field.equalsIgnoreCase(TYPE)) {
                                    if (tokensRemaining == 1)
                                        obsType = tokenizer.nextToken();
                                    else
                                        throw new IOException("invalid number of tokens for field '" + field +
                                                              "' : " + tokensRemaining);
                                }
                            }
                            else
                                throw new IOException("invalid number of tokens for field '" + field +
                                                      "' : " + tokensRemaining);
                        }
                    
                        fieldIndex++;
                    }
                    
                    // Check whether we have a complete record or have reached
                    // the end-of-file with pending processed field data (can
                    // occur with older format files with missing fields).
                    if (fieldIndex == fieldOrder.length ||
                        ( line == null &&
                          fieldIndex > 0 ))
                    {
                        // For SEGMENT and OBS layers, add new stamp
                        if (layerType == SEGMENT_TYPE)
                            stampList.add(new Stamp(ET + begET,
                                                    nwx, nwy,
                                                    nex, ney,
                                                    swx, swy,
                                                    sex, sey,
                                                    new Object[] {begET, endET,
                                                                  threeDecFmt.format(stampCoverage), 
                                                                  threeDecFmt.format(centerCoverage), 
                                                                  threeDecFmt.format(roiCoverage), 
                                                                  threeDecFmt.format(solarIncidence),
                                                                  threeDecFmt.format(nwx), threeDecFmt.format(nwy),
                                                                  threeDecFmt.format(nex), threeDecFmt.format(ney),
                                                                  threeDecFmt.format(swx), threeDecFmt.format(swy),
                                                                  threeDecFmt.format(sex), threeDecFmt.format(sey)
                                                                 }
                                          ));
                        else if (layerType == OBS_TYPE) {
                            String label = ET + begET;
                            if (name != null)
                                label = name + " " + label;
                            else
                                // Assign fake name so that stamp indexing works;
                                // i.e., need a stamp "filename".
                                name = label;
                            
                            stampList.add(new Stamp(label,
                                                    nwx, nwy,
                                                    nex, ney,
                                                    swx, swy,
                                                    sex, sey,
                                                    new Object[] {name, obsType, begET, endET,
                                                                  threeDecFmt.format(stampCoverage),
                                                                  threeDecFmt.format(centerCoverage), 
                                                                  threeDecFmt.format(roiCoverage), 
                                                                  threeDecFmt.format(solarIncidence),
                                                                  threeDecFmt.format(hVal), hSummary, hReport, 
                                                                  threeDecFmt.format(nwx), threeDecFmt.format(nwy),
                                                                  threeDecFmt.format(nex), threeDecFmt.format(ney),
                                                                  threeDecFmt.format(swx), threeDecFmt.format(swy),
                                                                  threeDecFmt.format(sex), threeDecFmt.format(sey)
                                                                 }
                                          ));
                            
                        }
                        
                        // Reset field values; avoids problems with missing fields in 
                        // older file formats.
                        begET = null;
                        endET = null;
                        name = null;
                        obsType = null;
                        hSummary = null;
                        hReport = null;
                        
                        nwx = 0; 
                        nwy = 0;
                        nex = 0; 
                        ney = 0;
                        swx = 0;
                        swy = 0;
                        sex = 0; 
                        sey = 0;
                        
                        hVal = 0;
                        stampCoverage = 0;
                        centerCoverage = 0;
                        roiCoverage = 0;
                        solarIncidence = 0;
                        
                        fieldIndex = 0;
                    }
                }
            }
            while (line != null);

            if (layerType != SEGMENT_TYPE &&
                layerType != OBS_TYPE)
            {
                // For INTERSECTED and NEAR layer types, retrieve all THEMIS stamps
                // from database (no other good way sadly) and filter this by the list
                // of INTERESECTED or NEAR stamp IDs from segment data.
                stamps = super.getStamps();
                for (int i=0; i < stamps.length; i++)
                    if (stamps[i] != null &&
                        idSet.contains(stamps[i].id))
                        stampList.add(stamps[i]);
            }
            
            stamps = (Stamp[])stampList.toArray(new Stamp[0]);
            
            log.aprintln("Loaded " + stamps.length + " segments/stamps");
        } 
        catch (Exception e) {
            throw new IOException("parse error at line " + count + ": " + e.getMessage());
        }

        return stamps;
    }
}
