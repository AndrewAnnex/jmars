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
import java.awt.event.*;
import java.awt.geom.*;
import java.text.*;
import javax.swing.*;
import javax.swing.event.*;


/**
 * Specialization of {@link StampLView} to support BTR/ABR versions of 
 * THEMIS IR/VIS stamps.
 *  
 * @see edu.asu.jmars.layer.stamp.PdsBtrImageFactory
 * @author hoffj MSFF-ASU
 */
public class BtrStampLView extends StampLView
{
    private static final String BTR_VIEW_SETTINGS_KEY = "btr_stamp";
    private static final String BTR_LOCAL_COPY_OPTION_VISIBLE_KEY = "stamps.btr.copy_visible";
    private static final String BTR_LOCAL_COPY_OPTION_ENABLED_KEY = "stamps.btr.copy_enabled";
    private static final String BTR_LOCAL_HTTP_SEARCH_ENABLED_KEY = "stamps.btr.local_http_enabled";
    private static final String TRUE = "true";
    private static DebugLog log = DebugLog.instance();
    
    protected PdsBtrImageFactory imageFactory;   // NOTE: This must be initialized in createFilledStampFocus()
                                                 //       since latter is called from StampLView constructor.
    protected PasteField localDirPathField;
    protected PasteField localHttpPathField;
    
    protected BtrStampSettings settings = new BtrStampSettings();
    private boolean restoreSearchCalled = false;
    
    
    protected BtrStampLView(Layer parent, StampFactory factory)
    {
        super(parent, factory);
    }
    
    public BtrStampLView(String name,
                         Layer parent,
                         String[] initialColumns,
                         Color unsColor, 
                         StampFactory factory)
    {
        super(name, parent, initialColumns, unsColor, factory);
    }
    
    public BtrStampLView(String name,
                         Layer parent,
                         String[] initialColumns,
                         Color unsColor,
                         Color filColor, 
                         StampFactory factory)
    {
        super(name, parent, initialColumns, unsColor, filColor, factory);
    }
    
    // Override of parent's method so that the subclass
    // below is constructed within parent's constructor.
    protected MyFilledStampFocus createFilledStampFocus()
    {
        imageFactory = new PdsBtrImageFactory();
        return new MyFilledBtrStampFocus(imageFactory);
    }
    
    // Note: this method is not synchronized so as to prevent
    // certain deadlock conditions in the AWT event thread.  As
    // implemented, an instance of the LManager class will
    // always be the first to call this method.
    public FocusPanel getFocusPanel()
    {
        if (focusPanel == null)
        {
            focusPanel =
                new DelayedFocusPanel()
                {
                    private boolean calledOnceAlready = false;
                    
                    public JPanel createFocusPanel()
                    {
                        // SUMMARY:  The code below prevents a classic 
                        // two-resource deadlock problem, one in which
                        // one thread locks resources in the order A-B, 
                        // and another thread locks in the order B-A.  
                        // There is no way to alter this ordering without 
                        // re-architecting the entire stamp layer/view, 
                        // hence the kludgey solution below.
                        //
                        // The implementation of the synchronized block
                        // below prevents race conditions from having
                        // any negative effect.  This conditional is
                        // tested *before* the synchronization block so
                        // as to prevent deadlocks in the AWT event thread.
                        //
                        // Technically, there is still a potential deadlock
                        // scenario if this method had never been called
                        // before the *first* time the AWT event thread
                        // calls it.  In practice, this circumstance does
                        // not happen (not a happy answer, but the other
                        // resource involved in the deadlock we are
                        // preventing is the AWT event queue).
                        if (calledOnceAlready)
                            return myFocus;
                        synchronized(BtrStampLView.this)
                        {
                            if (myFocus == null)
                            {
                                myFocus = new MyBtrFocus(BtrStampLView.this,
                                                         (StampLView) getChild());
                                calledOnceAlready = true;
                                log.println("created myFocus");
                            }
                            
                            return  myFocus;
                        }
                    }
                    
                    public void run()
                    {
                        JPanel fp = createFocusPanel();
                        removeAll();
                        add(fp);
                        validate();
                        repaint();
                        
                        // After creation of the focus panel, restore any rendered stamps 
                        // if this is a view restoration and settings have been loaded.
                        if (settingsLoaded &&
                            settings != null)
                        {
                            log.println("calling restoreStamps from createFocusPanel");
                            restoreStamps(settings.stampStateList);
                        }
                    }
                }
            ;
        }
        
        return  focusPanel;
    }
    
    /**
     * Override to update view specific settings
     */
    protected synchronized void updateSettings(boolean saving)
    {
        MyBtrFocus panel = (MyBtrFocus)myFocus;
        
        if (saving){
            log.println("saving settings");
            
            //
            // MyBtrFocus settings
            //
            
            // Update copy of image path settings as they are stored elsewhere
            // for actual editing and use.
            if (panel != null) {
                settings.searchParms = panel.getSearchParameters();
                settings.searchOrder = panel.getSearchOrder();
            }
            
            //
            // MyFilledBtrStampFocus settings
            //
            settings.stampOutlinesHiddenSelected = focusFilled.stampOutlinesHiddenSelected();
            settings.stampSingleImageSelected = focusFilled.stampSingleImageSelected();
            settings.stampStateList = focusFilled.getStampStateList();
            
            log.println("stamp ID list = ");
            for (int i=0; i < settings.stampStateList.length; i++)
                log.println(settings.stampStateList[i].id);
            log.println("stored " + settings.stampStateList.length + " stamps");
            
            viewSettings.put(BTR_VIEW_SETTINGS_KEY, settings);
        }
        else
        {
            log.println("loading settings");
            
            if ( viewSettings.containsKey(BTR_VIEW_SETTINGS_KEY) )
            {
                settings = (BtrStampSettings) viewSettings.get(BTR_VIEW_SETTINGS_KEY);
                if (settings != null)
                {
                    log.println("lookup of settings via key succeeded");
                    
                    // If the MyBtrFocus panel exists yet, push settings to it.
                    if (panel != null)
                        restoreSearchSettings(panel);
                    
                    //
                    // MyFilledBtrStampFocus settings
                    //
                    if (focusFilled != null)
                    {
                        focusFilled.setStampOutlinesHiddenSelected(settings.stampOutlinesHiddenSelected);
                        focusFilled.setStampSingleImageSelected(settings.stampSingleImageSelected);
                        
                        // Reload and rerender filled stamps; requires that both MyBtrFocus
                        // and MyFilledBtrStampFocus panels exist.
                        if (settings.stampStateList != null &&
                            panel != null)
                        {
                            log.println("calling restoreStamps from updateSettings");
                            restoreStamps(settings.stampStateList);
                        }
                    }
                    
                    settingsLoaded = true;
                }
                else
                {
                    log.println("lookup of settings via key failed");
                    settings = new BtrStampSettings();
                }
            }
        }
    }
    
    
    // Called to restore rendered image search settings during view restoration
    // after a program restart.  Only one successful call is allowed.
    private synchronized void restoreSearchSettings(MyBtrFocus panel)
    {
        if (panel != null &&
            !restoreSearchCalled)
        {
            restoreSearchCalled = true;
            panel.setSearchParameters(settings.searchParms);
            panel.setSearchOrder(settings.searchOrder);
        }
    }
    
    /** Adds specified stamps from stamp table list.
     **
     ** @param rows selected stamp indices in using *unsorted* row numbers
     **/
    protected void addSelectedStamps(final int[] rows)
    {
        addSelectedStamps(rows, 0);
    }
    
    
    public String getToolTipText(MouseEvent event)
    {
        String str = "";
        String temperatureStr = null;
        
        // Display the stamp and point temperature information if available
        // for the topmost filled stamp.
        try {
            Point2D mousePt = event.getPoint();
            temperatureStr = getTemperatureStringForPoint(mousePt, true);
            
            if (temperatureStr != null)
                str = temperatureStr;
        } 
        catch ( Exception ex) {
            log.println("error: " + ex.getMessage());
        }
        
        // Only display the stamp information for unfilled stamps if
        // no filled stamp information is available and if stamp
        // outlines are being displayed.
        if (temperatureStr == null &&
            !focusFilled.stampOutlinesHiddenSelected())
        {
            try {
                Point2D mouseWorld = getProj().screen.toWorld(event.getPoint());
                str = getStringForPoint(mouseWorld, true);
            } 
            catch ( Exception ex) {
                log.println("error: " + ex.getMessage());
            }
        }
        
        return str;
    }
    
    // Converts a screen position (e.g., mouse position) to an HVector
    // coordinate that includes correction for the offset shift of a
    // filled stamp at the current pixel resolution.
    protected HVector screenPointToHVector(Point2D screenPt, FilledStamp fs)
    {
        HVector vec = null;
        
        if (screenPt != null &&
            fs != null)
        {
            Point2D worldPt = getProj().screen.toWorld(screenPt);
            Point2D offset = fs.getOffset();
            
            worldPt.setLocation( worldPt.getX() - offset.getX(),
                                 worldPt.getY() - offset.getY());
            
            vec = getProj().world.toHVector(worldPt);
        }
        
        return vec;
    }
    
    protected String getTemperatureStringForPoint(Point2D screenPt, boolean showAsHTML)
    {
        String str = null;
        
        FilledStamp fs = ((MyFilledBtrStampFocus)focusFilled).getIntersectedFilledStamp(screenPt);
        if (fs != null &&
            fs.pdsi != null)
        {
            log.println("Got filled stamp for point");
            
            // Only return temperature information for an IR stamp
            if (fs.stamp.isIR())
            {
                HVector screenVec = screenPointToHVector(screenPt, fs);
                Point2D imagePt = ((PdsBtrImage)fs.pdsi).getImagePt(screenVec);
                
                if (imagePt != null)
                {
                    StringBuffer buf = new StringBuffer();
                    double temp = ((PdsBtrImage)fs.pdsi).getTemp(imagePt);
                    
                    if (showAsHTML)
                        buf.append("<html>");
                    
                    buf.append("ID: ");
                    buf.append(fs.stamp.id);
                    
                    if (showAsHTML)
                        buf.append("<br>");
                    else
                        buf.append("\n");
                    
                    buf.append("Temp(K): ");
                    
                    DecimalFormat f = new DecimalFormat("0.000");
                    buf.append(f.format(temp));
                    
                    if (showAsHTML)
                        buf.append("</html>");
                    
                    str = buf.toString();
                    
                    log.println("Got temp(K) for point: " + str);
                }
            }
            else
            {
                // Return just a stamp ID label for non-IR stamps
                StringBuffer buf = new StringBuffer();
                
                if (showAsHTML)
                    buf.append("<html>");
                
                buf.append("ID: ");
                buf.append(fs.stamp.id);
                
                if (showAsHTML)
                    buf.append("</html>");
                
                str = buf.toString();
                
                log.println("Create label for non-IR stamp point: " + str);
            }
            
        }
        
        return str;
    }
    
    // Storage of user-changeable settings
    protected static class BtrStampSettings extends LViewSettings
    {
        static final long serialVersionUID = -831975293452247751L;
        
        // MyBtrFocus settings
        PdsBtrImage.SearchParameters searchParms;
        String[] searchOrder;
        
        // MyFilledBtrStampFocus settings
        boolean stampOutlinesHiddenSelected;
        boolean stampSingleImageSelected;
        FilledStamp.State[] stampStateList;
    }
    
    protected class MyFilledBtrStampFocus extends StampLView.MyFilledStampFocus
    {
        protected MyFilledBtrStampFocus(PdsBtrImageFactory imageFactory)
        {
            super(BtrStampLView.this, imageFactory);
            if (dropBand != null)
                dropBand.setVisible(false);
        }
        
        protected FilledStamp getFilled(Stamp s)
        {
            createFocusPanel();
            return getFilled(s, (MyBtrFocus)myFocus);
        }
        
        private FilledStamp getFilled(Stamp s, MyBtrFocus panel)
        {
            return getFilled(s, panel, null);
        }
        
        /**
         ** @param state optional position offset and color map state settings
         ** used to restore FilledStamp state; may be null.
         **/
        protected FilledStamp getFilled(Stamp s, FilledStamp.State state)
        {
            createFocusPanel();
            return getFilled(s, (MyBtrFocus)myFocus, state);
        }
        
        /**
         ** @param state optional position offset and color map state settings
         ** used to restore FilledStamp state; may be null.
         **/
        private FilledStamp getFilled(Stamp s, MyBtrFocus panel, FilledStamp.State state)
        {
            synchronized(getCacheLock())
            {
                FilledStamp fs = getCache(s);
                if (fs == null)
                {
                    if (panel != null)
                    {
                        PdsBtrImageFactory factory = (PdsBtrImageFactory) imageFactory;
                        StampImage pdsi = factory.load(s, panel.getSearchParameters());
                        if (pdsi == null)
                        {
                            log.println("image load operation failed");
                            return  null;
                        }
                        
                        addCache(fs = new FilledStamp(s, pdsi, state));
                    }
                    else
                        log.aprintln("could not load image due to missing MyBtrFocus panel");
                }
                
                return  fs;
            }
        }
        
        public void addStamp(Stamp s)
        {
            // Always render stamp after adding by setting band
            // selection to zero.
            addStamp(s, true, false, false, false, 0);
        }
        
        protected void selectAllStamps()
        {
            if (Main.isStudentApplication())
                return;
            
            int[] indices = new int[listModel.getSize()];
            
            if (indices != null)
                for (int i = 0; i < indices.length; i++)
                    indices[i] = i;
            
            listStamps.setSelectedIndices(indices);
        }
        
        // Returns topmost filled stamp (if any) in the filled stamp list 
        // that is intersected at the specified point in HVector space.
        protected FilledStamp getIntersectedFilledStamp(Point2D screenPt)
        {
            PdsBtrImage image;
            
            log.println("list model size = " + listModel.getSize());
            for (int i=0; i < listModel.getSize(); i++)
            {
                FilledStamp fs = getFilled(i);
                
                if (fs != null)
                {
                    log.println("checking for intersection on stamp #" + i + ", id " + fs.stamp.id);
                    
                    HVector ptVect = screenPointToHVector(screenPt, fs);
                    
                    if (ptVect != null)
                    {
                        image = (PdsBtrImage) fs.pdsi;
                        if (image !=null &&
                            image.isIntersected(ptVect))
                            return fs;
                    }
                }
                else
                    log.println("got null for filled stamp on stamp " + i);
            }
            
            return null;
        }
        
    }
    
    protected class MyBtrFocus extends StampLView.MyFocus
    {
        public static final String LOCAL_DIR = "Local Directory";
        public static final String LOCAL_HTTP = "Local Webserver";
        public static final String THEMIS_HTTP = "THEMIS Webserver";
        
        private static final int SOURCES_LIST_SIZE = 3;
        
        public JCheckBox chkSearchLocalDirectory;
        public JCheckBox chkSearchThemisHttp;
        public JCheckBox chkSearchLocalHttp;
        public JCheckBox chkCopyToLocalDir;
        
        protected JList listSources;
        protected DefaultListModel listModel;
        
        private JButton btnRaisePriority;
        private JButton btnLowerPriority;
        private PdsBtrImage.SearchParameters searchParms = new PdsBtrImage.SearchParameters();
        
        public final int BORDER_WIDTH;
        
        
        protected MyBtrFocus(StampLView parent, StampLView panner)
        {
            this(parent, panner, true, 10);
        }
        
        protected MyBtrFocus(StampLView parent, StampLView panner, boolean createPanel, int border)
        {
            this(parent, panner, createPanel, border, null);
        }
        
        /**
         *  creates the focus panel for the Themis Stamp layer.  This differs from the creation of the
         *  focus panel in the super class in the extra fields that are added to the settings panel.
         *
         * @param parms settings to use for image load search; if 'null',
         *                    default settings are used.
         */
        protected MyBtrFocus(StampLView parent, StampLView panner, boolean createPanel, int border,
                             PdsBtrImage.SearchParameters parms)
        {
            // Call parent with no panel creation; panel can't be created until initialization
            // of member variables is complete, so we handle it below.
            super(parent, panner, false);
            
            this.BORDER_WIDTH = border;
            if (parms != null)
                searchParms = parms;
            else
                initSearchParameters(searchParms);
            
            if (createPanel) {
                setLayout(new GridLayout(1,1));
                JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
                
                tabs.add("Outlines", getFocusUnfilledPanel() );
                if (!Main.isStudentApplication())
                    tabs.add("Rendered", focusFilled);
                
                tabs.add("Settings", getFocusSettingsPanel());
                if (!Main.isStudentApplication())
                    tabs.add("Query", getQueryPanel());
                
                add(tabs, BorderLayout.CENTER);

                if (!Main.isStudentApplication())
                {
                    // Initialize search options based on whether settings have
                    // been loaded or not via updateSettings() method.
                    if (settingsLoaded &&
                        settings != null)
                        restoreSearchSettings(this);
                    else
                    {
                        chkSearchLocalDirectory.setSelected(searchParms.localDirEnabled);
                        chkSearchThemisHttp.setSelected(searchParms.themisHttpEnabled);
                        chkSearchLocalHttp.setSelected(searchParms.localHttpEnabled);
                        chkCopyToLocalDir.setSelected(searchParms.autoCopyToLocalDir);
                        
                        listModel.add(0, LOCAL_DIR);
                        listModel.add(1, THEMIS_HTTP);
                        listModel.add(2, LOCAL_HTTP);
                    }
                    
                    if (TRUE.equalsIgnoreCase(Config.get(BTR_LOCAL_COPY_OPTION_VISIBLE_KEY, "").trim()) == false)
                        chkCopyToLocalDir.setVisible(false);
                }
            }
        }
    
        /**
         * Override of parent implementation to handle BTR/ABR-specific
         * view settings.
         */
        protected JPanel getFocusSettingsPanel()
        {
            // Since student version of JMARS does not support image
            // loading, there is no need for the BTR/ABR-specific version
            // of this panel that deals with image caching issues.
            if (Main.isStudentApplication())
                return super.getFocusSettingsPanel();
            
            // Settings Panel
            JPanel focusSettings = new JPanel(new BorderLayout());
            JPanel focusSettingsTop = super.getFocusSettingsPanel();
            JPanel focusSettingsMiddle = new JPanel();
            focusSettingsMiddle.setLayout(new BoxLayout(focusSettingsMiddle, BoxLayout.Y_AXIS));
            
            JPanel focusSettingsMiddle1 = new JPanel(new GridLayout(0, 2));
            JPanel focusSettingsMiddle2 = new JPanel(new GridLayout(0, 1, 0, 0));
            JPanel focusSettingsMiddle3 = new JPanel();
            focusSettingsMiddle3.setLayout(new BoxLayout(focusSettingsMiddle3, BoxLayout.X_AXIS));
            
            focusSettingsMiddle1.add(new JLabel("Local Directory Path:"));
            localDirPathField = new PasteField(searchParms.localDirPath, 15);
            localDirPathField.getDocument().addDocumentListener(
                                                                new DocumentListener()
                                                                {
                                                                    public void changedUpdate(DocumentEvent e)
                                                                    {
                                                                        update();
                                                                    }
                                                                    
                                                                    public void insertUpdate(DocumentEvent e)
                                                                    {
                                                                        update();
                                                                    }
                                                                    
                                                                    public void removeUpdate(DocumentEvent e)
                                                                    {
                                                                        update();
                                                                    }
                                                                    
                                                                    private void update()
                                                                    {
                                                                        PdsBtrImage.SearchParameters parms = getSearchParameters();
                                                                        parms.localDirPath = localDirPathField.getText();
                                                                    }
                                                                }
            );
            focusSettingsMiddle1.add(localDirPathField);
            
            
            focusSettingsMiddle1.add(new JLabel("Local Webserver Path:"));
            localHttpPathField = new PasteField(searchParms.localHttpPath, 15);
            localHttpPathField.getDocument().addDocumentListener(
                                                                 new DocumentListener()
                                                                 {
                                                                     public void changedUpdate(DocumentEvent e)
                                                                     {
                                                                         update();
                                                                     }
                                                                     
                                                                     public void insertUpdate(DocumentEvent e)
                                                                     {
                                                                         update();
                                                                     }
                                                                     
                                                                     public void removeUpdate(DocumentEvent e)
                                                                     {
                                                                         update();
                                                                     }
                                                                     
                                                                     private void update()
                                                                     {
                                                                         PdsBtrImage.SearchParameters parms = getSearchParameters();
                                                                         parms.localHttpPath = localHttpPathField.getText();
                                                                     }
                                                                 }
            );
            focusSettingsMiddle1.add(localHttpPathField);
            
            verticalSquish(focusSettingsMiddle1);
            
            
            chkSearchLocalDirectory = new JCheckBox("Search local directory");
            chkSearchThemisHttp = new JCheckBox("Search THEMIS webserver");
            chkSearchLocalHttp = new JCheckBox("Search local webserver");
            chkCopyToLocalDir = new JCheckBox("Copy images to local directory");
            
            
            focusSettingsMiddle2.add(new JLabel("Image Search Path Options:"));
            focusSettingsMiddle2.add(chkSearchLocalDirectory);
            focusSettingsMiddle2.add(chkSearchThemisHttp);
            focusSettingsMiddle2.add(chkSearchLocalHttp);
            focusSettingsMiddle2.add(new JLabel(" "));
            focusSettingsMiddle2.add(chkCopyToLocalDir);
            verticalSquish(focusSettingsMiddle2);
            
            // Image source components
            listModel = new DefaultListModel();
            listSources = new JList(listModel);
            listSources.setVisibleRowCount(SOURCES_LIST_SIZE);
            listSources.setBorder( BorderFactory.createLoweredBevelBorder() );
            
            btnRaisePriority = new JButton(
                                           new AbstractAction("Raise Priority")
                                           {
                                               public void actionPerformed(ActionEvent e)
                                               {
                                                   int min = listSources.getMinSelectionIndex();
                                                   int max = listSources.getMaxSelectionIndex();
                                                   if (min == -1  ||  min == 0)
                                                       return;
                                                   
                                                   // Swap the selection range and the item before it
                                                   listModel.insertElementAt(listModel.remove(min-1),
                                                                             max);
                                                   listSources.setSelectionInterval(min-1, max-1);
                                               }
                                           }
            );
            btnRaisePriority.setToolTipText("Move selected image search method to higher priority use");
            
            btnLowerPriority = new JButton(
                                           new AbstractAction("Lower Priority")
                                           {
                                               public void actionPerformed(ActionEvent e)
                                               {
                                                   int min = listSources.getMinSelectionIndex();
                                                   int max = listSources.getMaxSelectionIndex();
                                                   if (max == -1  ||  max == listModel.getSize()-1)
                                                       return;
                                                   
                                                   // Swap the selection range and the item after it
                                                   listModel.insertElementAt(listModel.remove(max+1),
                                                                             min);
                                                   listSources.setSelectionInterval(min+1, max+1);
                                               }
                                           }
            );
            btnLowerPriority.setToolTipText("Move selected image search method to lower priority use");
            
            focusSettingsMiddle3.add( new JPanel(new GridLayout(0, 1))
                                      {{
                                          add(btnRaisePriority);
                                          add(btnLowerPriority);
                                          verticalSquish(this);
                                      }}
            );
            focusSettingsMiddle3.add(Box.createHorizontalStrut(BORDER_WIDTH));
            focusSettingsMiddle3.add(listSources);
            verticalSquish(focusSettingsMiddle3);
            
            
            // Note: the first and last empty panel components are needed for 
            // empty spacing.
            focusSettingsMiddle.add(new JPanel());
            focusSettingsMiddle.add(focusSettingsMiddle1);
            focusSettingsMiddle.add(new JLabel(" "));
            focusSettingsMiddle.add(focusSettingsMiddle2);
            focusSettingsMiddle.add(new JLabel(" "));
            focusSettingsMiddle.add(new JLabel("Image Source Search Order:", JLabel.LEFT));
            focusSettingsMiddle.add(new JLabel(" "));
            focusSettingsMiddle.add(focusSettingsMiddle3);
            focusSettingsMiddle.add(new JPanel());
            verticalSquish(focusSettingsMiddle);
            
            
            focusSettings.add(focusSettingsTop, BorderLayout.NORTH);
            focusSettings.add(focusSettingsMiddle, BorderLayout.CENTER);
            
            return focusSettings;
        }
    
        protected void initSearchParameters(PdsBtrImage.SearchParameters searchParms)
        {
            PdsBtrImageFactory factory = (PdsBtrImageFactory) imageFactory;

            searchParms.localDirEnabled = true;
            searchParms.localHttpEnabled = TRUE.equalsIgnoreCase(Config.get(BTR_LOCAL_HTTP_SEARCH_ENABLED_KEY, "").trim());
            searchParms.themisHttpEnabled = true; 
            searchParms.firstSrc = PdsBtrImageFactory.LOCAL_DIR; 
            searchParms.secondSrc = PdsBtrImageFactory.THEMIS_WEBSERVER; 
            searchParms.autoCopyToLocalDir = TRUE.equalsIgnoreCase(Config.get(BTR_LOCAL_COPY_OPTION_ENABLED_KEY, "").trim());
            searchParms.localDirPath = factory.getLocalDirPath();
            searchParms.localHttpPath = factory.getLocalHttpPath();
            searchParms.themisHttpPath = factory.getThemisHttpPath();
        }
        
        // Returns current state of image search parameters settings.
        public PdsBtrImage.SearchParameters getSearchParameters()
        {
            if (Main.isStudentApplication())
                return null;

            searchParms.localDirEnabled = localDirSearchEnabled();
            searchParms.localHttpEnabled = localHttpSearchEnabled();
            searchParms.themisHttpEnabled = themisHttpSearchEnabled();
            searchParms.autoCopyToLocalDir = copyToLocalDirEnabled();
            searchParms.firstSrc = getFirstSearchSourceIdentifier();
            searchParms.secondSrc = getSecondSearchSourceIdentifier();
            
            if (searchParms.localDirPath != null &&
                !searchParms.localDirPath.endsWith("/"))
                searchParms.localDirPath = searchParms.localDirPath + "/";
            
            if (searchParms.localHttpPath != null &&
                !searchParms.localHttpPath.endsWith("/"))
                searchParms.localHttpPath = searchParms.localHttpPath + "/";
            
            if (searchParms.themisHttpPath != null &&
                !searchParms.themisHttpPath.endsWith("/"))
                searchParms.themisHttpPath = searchParms.themisHttpPath + "/";
            
            return searchParms;
        }
        
        // Sets current state of image search parameters to specified
        // settings.
        public void setSearchParameters(PdsBtrImage.SearchParameters parms)
        {
            searchParms = parms;
            
            setLocalDirSearchEnabled(searchParms.localDirEnabled);
            setLocalHttpSearchEnabled(searchParms.localHttpEnabled);
            setThemisHttpSearchEnabled(searchParms.themisHttpEnabled);
            setCopyToLocalDirEnabled(searchParms.autoCopyToLocalDir);
            localDirPathField.setText(searchParms.localDirPath);
            localHttpPathField.setText(searchParms.localHttpPath);
        }
        
        // Returns checkbox setting for local directory search enabling.
        private boolean localDirSearchEnabled()
        {
            return chkSearchLocalDirectory.isSelected();
        }
        
        // Returns checkbox setting for local webserver search enabling.
        private boolean localHttpSearchEnabled()
        {
            return chkSearchLocalHttp.isSelected();
        }
        
        // Returns checkbox setting for Themis webserver search enabling.
        private boolean themisHttpSearchEnabled()
        {
            return chkSearchThemisHttp.isSelected();
        }
        
        // Returns checkbox setting for copy-to-local-directory enabling.
        private boolean copyToLocalDirEnabled()
        {
            boolean status = false;
            
            if (TRUE.equalsIgnoreCase(Config.get(BTR_LOCAL_COPY_OPTION_VISIBLE_KEY, "").trim()))
                status = chkCopyToLocalDir.isSelected();
            else
                status = TRUE.equalsIgnoreCase(Config.get(BTR_LOCAL_COPY_OPTION_ENABLED_KEY, "").trim());
            
            return status;
        }
        
        // Sets checkbox for local directory search enabling.
        private void setLocalDirSearchEnabled(boolean enable)
        {
            chkSearchLocalDirectory.setSelected(enable);
        }
        
        // Sets checkbox for local webserver search enabling.
        private void setLocalHttpSearchEnabled(boolean enable)
        {
            chkSearchLocalHttp.setSelected(enable);
        }
        
        // Sets checkbox for Themis webserver search enabling.
        private void setThemisHttpSearchEnabled(boolean enable)
        {
            chkSearchThemisHttp.setSelected(enable);
        }
        
        // Sets checkbox for copy-to-local-directory enabling.
        private void setCopyToLocalDirEnabled(boolean enable)
        {
            chkCopyToLocalDir.setSelected(enable);
            searchParms.autoCopyToLocalDir = enable;
        }
        
        /**
         ** Returns search source identifier for specified image
         ** source.  The identifier corresponds to one of the
         ** following:
         **
         ** {@link PdsBtrImageFactory#LOCAL_DIR}
         ** {@link PdsBtrImageFactory#LOCAL_WEBSERVER},
         ** {@link PdsBtrImageFactory#THEMIS_WEBSERVER}
         ** {@link PdsBtrImageFactory#NO_SEARCH_SOURCE}.
         **
         ** NO_SEARCH_SOURCE will only be returned as an error.
         **/
        public int getSearchSourceIdentifier(String srcName)
        {
            int code = PdsBtrImageFactory.NO_SEARCH_SOURCE;
            
            if (LOCAL_DIR.equals(srcName))
                code = PdsBtrImageFactory.LOCAL_DIR;
            else if (LOCAL_HTTP.equals(srcName))
                code = PdsBtrImageFactory.LOCAL_WEBSERVER;
            else if (THEMIS_HTTP.equals(srcName))
                code = PdsBtrImageFactory.THEMIS_WEBSERVER;
            
            return code;
        }
        
        /**
         ** Similar to {@link #getSearchSourceIdentifier}, this
         ** returns a code for the first source in the current
         ** source order list.
         **/
        public int getFirstSearchSourceIdentifier()
        {
            int code = PdsBtrImageFactory.NO_SEARCH_SOURCE;
            
            String[] sources = getSearchOrder();
            if (sources != null &&
                sources.length >= 1)
                code = getSearchSourceIdentifier(sources[0]);
            
            return code;
        }
        
        /**
         ** Similar to {@link #getSearchSourceIdentifier}, this
         ** returns a code for the second source in the current
         ** source order list.
         **/
        public int getSecondSearchSourceIdentifier()
        {
            int code = PdsBtrImageFactory.NO_SEARCH_SOURCE;
            
            String[] sources = getSearchOrder();
            if (sources != null &&
                sources.length >= 2)
                code = getSearchSourceIdentifier(sources[1]);
            
            return code;
        }
        
        /** Returns list of defined strings corresponding to image search
         ** options that are enabled and the order in which they should
         ** be used.  Defined strings are: 
         **
         ** {@link #LOCAL_DIR}
         ** {@link #LOCAL_HTTP}
         ** {@link #THEMIS_HTTP}
         **/
        public String[] getEnabledSearchOrder()
        {
            if (Main.isStudentApplication())
                return null;
            
            String[] tempList = new String[listModel.size()];
            String[] realList = null;
            int count = 0;
            
            for (int i = 0; i < listModel.size(); i++)
            {
                String item = (String)listModel.get(i);
                
                if (item != null)
                {
                    if (item.equals(LOCAL_DIR) &&
                        localDirSearchEnabled())
                    {
                        tempList[count] = LOCAL_DIR;
                        count++;
                    }
                    else if (item.equals(LOCAL_HTTP) &&
                             localHttpSearchEnabled())
                    {
                        tempList[count] = LOCAL_HTTP;
                        count++;
                    }
                    else if (item.equals(THEMIS_HTTP) &&
                             themisHttpSearchEnabled())
                    {
                        tempList[count] = THEMIS_HTTP;
                        count++;
                    }
                }
                else
                    log.println("item is null for listModel element #" + i);
            }
            
            realList = new String[count];
            for (int i = 0; i < count; i++)
                realList[i] = tempList[i];
            
            return realList;
        }
        
        /** Returns list of defined strings corresponding to image search
         ** sources in the order in which they should be used.  
         ** Does not filter list for enabled/disabled sources.
         ** Defined strings are: 
         **
         ** {@link #LOCAL_DIR}
         ** {@link #LOCAL_HTTP}
         ** {@link #THEMIS_HTTP}
         **/
        public String[] getSearchOrder()
        {
            if (Main.isStudentApplication())
                return null;
            
            String[] list = new String[listModel.size()];
            
            if (list != null)
                for (int i = 0; i < listModel.size(); i++)
                {
                    String item = (String)listModel.get(i);
                    
                    if (item != null) {
                        if (item.equals(LOCAL_DIR))
                            list[i] = LOCAL_DIR;
                        else if (item.equals(LOCAL_HTTP))
                            list[i] = LOCAL_HTTP;
                        else if (item.equals(THEMIS_HTTP))
                            list[i] = THEMIS_HTTP;
                        else
                            list[i] = null;
                    }
                    else
                        log.println("item is null for listModel element #" + i);
                }
            
            return list;
        }
        
        /** Sets list of defined strings corresponding to image search
         ** sources in the order in which they should be used.  
         ** 
         ** Defined strings are: 
         **
         ** {@link #LOCAL_DIR}
         ** {@link #LOCAL_HTTP}
         ** {@link #THEMIS_HTTP}
         **/
        public void setSearchOrder(String[] searchOrder)
        {
            if (Main.isStudentApplication())
                return;
            
            if (searchOrder != null)
            {
                listModel.clear();
                
                for (int i=0; i < searchOrder.length; i++)
                    if (searchOrder[i] != null &&
                        ( searchOrder[i].equals(LOCAL_DIR) ||
                          searchOrder[i].equals(LOCAL_HTTP) ||
                          searchOrder[i].equals(THEMIS_HTTP)))
                        listModel.add(i, searchOrder[i]);
            }
        }
    }
}
