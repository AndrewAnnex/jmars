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
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import edu.asu.jmars.swing.ValidClipboard;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.datatransfer.*;


/**
 ** The focus panel for filled-in stamps. Performs only the
 ** creation/layout of the components, doesn't actually "plug anything
 ** in."
 **/
public abstract class FilledStampFocus extends JPanel implements ImageConstants
{
    private static final DebugLog log = DebugLog.instance();
    
    public final int BDR;
    
    private final Object cacheLock = new Object();
    private HashMap fCache = new HashMap();
    private ArrayList newFilledList = new ArrayList();
    
    protected StampLView parent;
    protected StampImageFactory imageFactory;
    
    protected JList listStamps;
    protected DefaultListModel listModel;
    protected JComboBox dropBand;
    protected JButton btnRotateImage;
    protected JButton btnHFlip;
    protected JButton btnVFlip;
    protected JButton btnViewPds;
    protected JButton btnListCopy;
    protected JButton btnListImport;
    
    private JComponent pnlListStamps;
    private JButton btnRaise;
    private JButton btnLower;
    private JButton btnTop;
    private JButton btnBottom;
    private JButton btnDelete;
    private JButton btnSort;
    
    private JButton btnPanNW, btnPanN, btnPanNE;
    private JButton btnPanW,           btnPanE;
    private JButton btnPanSW, btnPanS, btnPanSE;
    private JButton btnPanSize;
    private JPanel pnlPanning;
    
    protected boolean ignoreChangeEvents = false;
    protected boolean stampListDragging = false;
    
    private boolean imageReleaseEnabled = true;
    
    public JCheckBox chkHideOutlines;
    private JCheckBox chkSingleImage;
    private JCheckBox chkKeepImagesInMemory;
    
    private FancyColorMapper mapper;
    
    private JPanel pnlMain;
    private JPanel pnlPerStamp;
    private JPanel pnlMapper;
    
    private int dragStartIndex = -1;
    private int dragCurIndex = -1;
    
    
    public FilledStampFocus(StampLView parent, StampImageFactory imageFactory)
    {
        this(parent, imageFactory, 10);
    }
    
    public FilledStampFocus(StampLView parent, StampImageFactory imageFactory, final int BDR)
    {
        this.parent = parent;
        this.imageFactory = imageFactory;
        this.BDR = BDR;
        
        listModel = new DefaultListModel();
        listStamps = new JList(listModel);

        listStamps.setSelectionModel(
                                     new TrackingListSelectionModel()
                                     {
                                         protected Object getValue(int idx)
                                         {
                                             return  listModel.get(idx);
                                         }
                                         
                                         protected void selectOccurred(boolean clearFirst,
                                                                       Object[] addvals,
                                                                       Object[] delvals)
                                         {
                                             if (!ignoreChangeEvents &&
                                                 !stampListDragging)
                                             {
                                                 Stamp[] adds = new Stamp[addvals.length];
                                                 for (int i=0; i<addvals.length; i++)
                                                     adds[i] = (Stamp) addvals[i];
                                                 
                                                 Stamp[] dels = new Stamp[delvals.length];
                                                 for (int i=0; i<delvals.length; i++)
                                                     dels[i] = (Stamp) delvals[i];
                                                 
                                                 FilledStampFocus.this.selectOccurred(clearFirst,
                                                                                      adds,
                                                                                      dels);
                                             }
                                         }
                                     }
        );
        listStamps.addListSelectionListener(
                                            new ListSelectionListener()
                                            {
                                                public void valueChanged(ListSelectionEvent e)
                                                {
                                                    if (!ignoreChangeEvents && 
                                                        !e.getValueIsAdjusting() &&
                                                        !stampListDragging)
                                                    {
                                                        updateStampSelected();
                                                        if (chkSingleImage.isSelected())
                                                            redrawTriggered();
                                                    }
                                                }
                                            }
        );

        MouseInputAdapter mouseListener = new MouseInputAdapter()
        {
            public void mousePressed(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStartIndex = listStamps.locationToIndex( new Point(e.getX(), e.getY()) );
                }
            }
            
            public void mouseDragged(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int min = listStamps.getMinSelectionIndex();
                    int max = listStamps.getMaxSelectionIndex();
                    
                    if (min >= 0 &&
                        min == max)
                        dragCurIndex = min;
                    else 
                        dragCurIndex = -1;
                    
                    stampListDragging = true;
                }
            }
            
            public void mouseReleased(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e) &&
                    dragStartIndex >= 0 &&
                    stampListDragging)
                {
                    int min = listStamps.getMinSelectionIndex();
                    int max = listStamps.getMaxSelectionIndex();
                    boolean redraw = false;
                    
                    if (min < 0) {
                        dragStartIndex = -1;
                        return;
                    }
                    
                    if (!chkSingleImage.isSelected()  &&
                        getFilled(dragStartIndex).band != -1)
                    {
                        for (int i=min; i<=max; i++)
                            if (getFilled(i).band != -1)
                            {
                                redraw = true;
                                break;
                            }
                    }
                    
                    // Check that this is truly a case of dragging
                    // a single stamp to a new list location; the
                    // current list selection should be the new location
                    // and contain only one selected element.
                    if (min == max &&
                        min != dragStartIndex)
                    {
                        // Move the stamp selected at the start of the drag
                        // motion to the new selected location.
                        ignoreChangeEvents = true;
                        listModel.insertElementAt(listModel.remove(dragStartIndex),
                                                  min);
                        listStamps.setSelectedIndex(min);
                        ignoreChangeEvents = false;
                    
                        // Need to handle stamp selection change here since; the
                        // normal selection change code has been disabled during 
                        // dragging due to conflicts.
                        FilledStampFocus.this.selectOccurred(true,
                                                             new Stamp[] {(Stamp)listModel.get(min)},
                                                             new Stamp[0]);
                        updateStampSelected();
                        if (redraw ||
                            chkSingleImage.isSelected())
                            redrawTriggered();
                    }
                    else if (min == max) {
                        // Need to handle possible selection change here.  The
                        // normal selection change code has been disabled above
                        // whenever dragging has started (to prevent conflicts), 
                        // and it is possible to immediately drag a new selection 
                        // without actually dragging to a new location.
                        ignoreChangeEvents = true;
                        listStamps.setSelectedIndex(min);
                        ignoreChangeEvents = false;
                        
                        FilledStampFocus.this.selectOccurred(true,
                                                             new Stamp[] {(Stamp)listModel.get(min)},
                                                             new Stamp[0]);
                        updateStampSelected();
                        if (chkSingleImage.isSelected())
                            redrawTriggered();
                    }
                    
                    dragStartIndex = -1;
                    dragCurIndex = -1;
                    stampListDragging = false;
                }
                else if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStartIndex = -1;
                    dragCurIndex = -1;
                }
            }
        };
        listStamps.addMouseListener(mouseListener);
        listStamps.addMouseMotionListener(mouseListener);
        
        listStamps.setCellRenderer(
                                   new DefaultListCellRenderer()
                                   {
                                       public Component getListCellRendererComponent(JList list,
                                                                                     Object value,
                                                                                     int index,
                                                                                     boolean isSelected,
                                                                                     boolean cellHasFocus)
                                       {
                                           String s = value.toString();
                                           setText(s);
                                           
                                           boolean dragTo = false;
                                           
                                           // Check whether cell is the start location for an
                                           // in-progress stamp drag operation.  If so, mark
                                           // it as "selected" for coloring purposes.
                                           if (index == dragStartIndex)
                                               isSelected = true;
                                           // Check whether cell is the current target for a stamp
                                           // drag-to location.
                                           else if (dragCurIndex == index &&
                                                    dragStartIndex >= 0 &&
                                                    dragStartIndex != dragCurIndex)
                                               dragTo = true;
                                           
                                           if (isSelected && !dragTo) {
                                               setBackground(list.getSelectionBackground());
                                               setForeground(list.getSelectionForeground());
                                           }
                                           else if (dragTo) {
                                               setBackground( new Color(~list.getSelectionBackground().getRGB()) );
                                               setForeground( new Color(~list.getSelectionForeground().getRGB()) );
                                           }
                                           else {
                                               setBackground(list.getBackground());
                                               setForeground(list.getForeground());
                                           }
                                           
                                           setEnabled(list.isEnabled());
                                           setFont(list.getFont());
                                           
                                           return this;
                                       }
                                   }
        );
        
        
        pnlListStamps =
            new JScrollPane(listStamps,
                            JScrollPane.  VERTICAL_SCROLLBAR_ALWAYS,
                            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
                            {{
                                setBorder(
                                          BorderFactory.createCompoundBorder(
                                                                             BorderFactory.createLoweredBevelBorder(),
                                                                             BorderFactory.createMatteBorder(0, 0, 0, 1,
                                                                                                             Color.darkGray)
                                          )
                                );
                            }}
        ;
        
        btnRaise = new JButton(
                               new AbstractAction("Raise")
                               {
                                   public void actionPerformed(ActionEvent e)
                                   {
                                       int min = listStamps.getMinSelectionIndex();
                                       int max = listStamps.getMaxSelectionIndex();
                                       if (min == -1  ||  min == 0)
                                           return;
                                       boolean redraw = false;
                                       if (!chkSingleImage.isSelected()  &&
                                           getFilled(min-1).band != -1)
                                       {
                                           for (int i=min; i<=max; i++)
                                               if (getFilled(i).band != -1)
                                               {
                                                   redraw = true;
                                                   break;
                                               }
                                       }
                                       
                                       // Swap the selection range and the item before it
                                       ignoreChangeEvents = true;
                                       listModel.insertElementAt(listModel.remove(min-1),
                                                                 max);
                                       listStamps.setSelectionInterval(min-1, max-1);
                                       ignoreChangeEvents = false;
                                       
                                       if (redraw)
                                           redrawTriggered();
                                   }
                               }
        );
        btnRaise.setToolTipText("Move the currently selected stamp(s) UP" +
                                " in the filled-stamps list.");
        
        btnLower = new JButton(
                               new AbstractAction("Lower")
                               {
                                   public void actionPerformed(ActionEvent e)
                                   {
                                       int min = listStamps.getMinSelectionIndex();
                                       int max = listStamps.getMaxSelectionIndex();
                                       if (max == -1  ||  max == listModel.getSize()-1)
                                           return;
                                       boolean redraw = false;
                                       if (!chkSingleImage.isSelected()  &&
                                           getFilled(max+1).band != -1)
                                       {
                                           for (int i=min; i<=max; i++)
                                               if (getFilled(i).band != -1)
                                               {
                                                   redraw = true;
                                                   break;
                                               }
                                       }
                                       
                                       // Swap the selection range and the item after it
                                       ignoreChangeEvents = true;
                                       listModel.insertElementAt(listModel.remove(max+1),
                                                                 min);
                                       listStamps.setSelectionInterval(min+1, max+1);
                                       ignoreChangeEvents = false;
                                       
                                       if (redraw)
                                           redrawTriggered();
                                   }
                               }
        );
        btnLower.setToolTipText("Move the currently selected stamp(s) DOWN" +
                                " in the filled-stamps list.");
        
        btnTop = new JButton(
                             new AbstractAction("Top")
                             {
                                 public void actionPerformed(ActionEvent e)
                                 {
                                     int min = listStamps.getMinSelectionIndex();
                                     int max = listStamps.getMaxSelectionIndex();
                                     if (min == -1  ||  min == 0)
                                         return;
                                     boolean redraw = false;
                                     if (!chkSingleImage.isSelected()  &&
                                         getFilled(min-1).band != -1)
                                     {
                                         for (int i=min; i<=max; i++)
                                             if (getFilled(i).band != -1)
                                             {
                                                 redraw = true;
                                                 break;
                                             }
                                     }
                                     
                                     // Move selection range to top of list.
                                     ignoreChangeEvents = true;
                                     for (int i=min; i <= max; i++) {
                                         Stamp s = (Stamp) listModel.remove(max);
                                         listModel.insertElementAt(s, 0);
                                         putTopStamp(s);
                                     }
                                     listStamps.setSelectionInterval(0, max-min);
                                     ignoreChangeEvents = false;
                                     
                                     if (redraw)
                                         redrawTriggered();
                                 }
                             }
        );
        btnTop.setToolTipText("Move the currently selected stamp(s) to TOP" +
                              " of the filled-stamps list.");
        
        btnBottom = new JButton(
                                new AbstractAction("Bottom")
                                {
                                    public void actionPerformed(ActionEvent e)
                                    {
                                        int min = listStamps.getMinSelectionIndex();
                                        int max = listStamps.getMaxSelectionIndex();
                                        if (max == -1  ||  max == listModel.getSize()-1)
                                            return;
                                        boolean redraw = false;
                                        if (!chkSingleImage.isSelected()  &&
                                            getFilled(max+1).band != -1)
                                        {
                                            for (int i=min; i<=max; i++)
                                                if (getFilled(i).band != -1)
                                                {
                                                    redraw = true;
                                                    break;
                                                }
                                        }
                                        
                                        // Move selection range to bottom of list.
                                        ignoreChangeEvents = true;
                                        for (int i=min; i <= max; i++)
                                            listModel.insertElementAt(listModel.remove(min), listModel.getSize());
                                        listStamps.setSelectionInterval(listModel.getSize() - (max-min) - 1, 
                                                                        listModel.getSize()-1);
                                        ignoreChangeEvents = false;
                                        
                                        if (redraw)
                                            redrawTriggered();
                                    }
                                }
        );
        btnBottom.setToolTipText("Move the currently selected stamp(s) to BOTTOM" +
                                 " of the filled-stamps list.");
        
        btnDelete = new JButton(
                                new AbstractAction("Delete")
                                {
                                    public void actionPerformed(ActionEvent e)
                                    {
                                        boolean redraw = false;
                                        Object[] selected = listStamps.getSelectedValues();
                                        for (int i=0; i<selected.length; i++)
                                        {
                                            listModel.removeElement(selected[i]);
                                            if ( getFilled((Stamp) selected[i]).band != -1 )
                                                redraw = true;
                                            
                                            removeCache((Stamp) selected[i]);
                                        }
                                        
                                        updateStampSelected();
                                        if (redraw)
                                            redrawTriggered();
                                    }
                                }
        );
        btnDelete.setToolTipText("Remove the currently selected(s) stamp" +
                                 " from the filled-stamps list.");
        
        btnSort = new JButton(
                                new AbstractAction("Left Sort")
                                {
                                    public void actionPerformed(ActionEvent e)
                                    {
                                        // Sort the filled stamps according to the 
                                        // West-to-East equator-intercept order, i.e.,
                                        // by longitude of stamp's corresponding
                                        // orbit track's intercept with the equator.
                                        Stamp[] filled = new Stamp[listModel.size()];
                                        for (int i=0; i < filled.length; i++)
                                            filled[i] = (Stamp)listModel.get(i);
                                        Stamp[] sorted = orbitTrackSort(filled);

                                        // Check whether the stamp order has changed
                                        // as a result of the sort.
                                        if (!Arrays.equals(filled, sorted))
                                        {
                                            // Move stamps in list to match sorted order.
                                            ignoreChangeEvents = true;
                                            for (int i = 0; i < sorted.length; i++)
                                                listModel.set(i, sorted[i]);
                                            listStamps.clearSelection();
                                            ignoreChangeEvents = false;
                                            
                                            updateStampSelected();
                                            redrawTriggered();
                                        }
                                    }
                                }
        );
        btnSort.setToolTipText("Sorts all filled-stamps in order of " +
                               "leftmost orbit track.");

        
        btnPanNW = new PanButton(-1, +1);
        btnPanN =  new PanButton( 0, +1);
        btnPanNE = new PanButton(+1, +1);
        btnPanE =  new PanButton(+1,  0);
        btnPanSE = new PanButton(+1, -1);
        btnPanS =  new PanButton( 0, -1);
        btnPanSW = new PanButton(-1, -1);
        btnPanW =  new PanButton(-1,  0);
        btnPanSize = new PanButton();
        
        pnlPanning = new JPanel(new GridLayout(3, 3));
        pnlPanning.add(btnPanNW);
        pnlPanning.add(btnPanN);
        pnlPanning.add(btnPanNE);
        pnlPanning.add(btnPanW);
        pnlPanning.add(btnPanSize);
        pnlPanning.add(btnPanE);
        pnlPanning.add(btnPanSW);
        pnlPanning.add(btnPanS);
        pnlPanning.add(btnPanSE);
        
        dropBand = new JComboBox();
        dropBand.setRenderer(new BandRenderer(dropBand.getRenderer()));
        dropBand.addActionListener(
                                   new ActionListener()
                                   {
                                       public void actionPerformed(ActionEvent e)
                                       {
                                           if (ignoreChangeEvents)
                                               return;
                                           mapper.setEnabled(dropBand.getSelectedIndex() > 0);
                                           FilledStamp fs = getFilledSingle();
                                           if (fs != null)
                                           {
                                               fs.band = dropBand.getSelectedIndex()-1;
                                               performRedrawSingle(fs);
                                           }
                                       }
                                   }
        );
        
        // This class and most derived classes will not use
        // the "Rotate Image 180" button, but this is the best
        // place to put this code.
        btnRotateImage = new JButton(
                                     new AbstractAction("Rotate Image 180")
                                     {
                                         public void actionPerformed(ActionEvent e)
                                         {
                                             FilledStamp fs = getFilledSingle();
                                             if (fs != null)
                                                 rotateFlipImage(fs, IMAGE_ROTATED_180);
                                         }
                                     }
        );
        btnRotateImage.setVisible(false);
        
        // This class and most derived classes will not use
        // the "Horizontal Flip" button, but this is the best
        // place to put this code.
        btnHFlip = new JButton(
                               new AbstractAction("Horizontal Flip")
                               {
                                   public void actionPerformed(ActionEvent e)
                                   {
                                       FilledStamp fs = getFilledSingle();
                                       if (fs != null)
                                           rotateFlipImage(fs, IMAGE_HFLIPPED);
                                   }
                               }
        );
        btnHFlip.setVisible(false);
        
        // This class and most derived classes will not use
        // the "Vertical Flip" button, but this is the best
        // place to put this code.
        btnVFlip = new JButton(
                               new AbstractAction("Vertical Flip")
                               {
                                   public void actionPerformed(ActionEvent e)
                                   {
                                       FilledStamp fs = getFilledSingle();
                                       if (fs != null)
                                           rotateFlipImage(fs, IMAGE_VFLIPPED);
                                   }
                               }
        );
        btnVFlip.setVisible(false);
        
        btnViewPds = new JButton(
                                 new AbstractAction("View PDS Label")
                                 {
                                     public void actionPerformed(ActionEvent e)
                                     {
                                         FilledStamp fs = getFilledSingle();
                                         JFrame frame = new JFrame(fs.stamp.id);
                                         JTextArea txt = new JTextArea(fs.pdsi.getLabel(), 24, 0);
                                         frame.getContentPane().add(new JScrollPane(txt));
                                         frame.pack();
                                         frame.setVisible(true);
                                     }
                                 }
        );
        
        btnListCopy = new JButton(
                                  new AbstractAction("Copy Stamp List to Clipboard")
                                  {
                                      public void actionPerformed(ActionEvent e)
                                      {
                                          StringBuffer buf = new StringBuffer();
                                          for (int i = 0; i < listModel.getSize(); i++) {
                                              buf.append( ((Stamp)listModel.get(i)).id);
                                              buf.append(' ');
                                          }
                                          
                                          StringSelection sel = new StringSelection(buf.toString());
                                          Clipboard clipboard = ValidClipboard.getValidClipboard();
                                          if (clipboard == null)
                                              log.aprintln("no clipboard available");
                                          else {
                                              clipboard.setContents(sel, sel);
                                              Main.setStatus("Stamp list copied to clipboard");
                                              
                                              log.println("stamp list copied: " + buf.toString());
                                          }
                                      }
                                  }
        );

        btnListImport = new JButton(
                                  new AbstractAction("Import Stamp List and Render")
                                  {
                                      public void actionPerformed(ActionEvent e)
                                      {
                                          new ImportStampsDialog().show();
                                      }
                                  }
        );
        
        chkHideOutlines = new JCheckBox("Hide stamp outlines");
        chkHideOutlines.addActionListener(
                                          new ActionListener()
                                          {
                                              public void actionPerformed(ActionEvent e)
                                              {
                                                  redrawOutlines(!chkHideOutlines.isSelected());
                                              }
                                          }
        );
        
        chkSingleImage = new JCheckBox("Fill only selected stamps");
        chkSingleImage.addActionListener(
                                         new ActionListener()
                                         {
                                             public void actionPerformed(ActionEvent e)
                                             {
                                                 ListSelectionModel selModel =
                                                     listStamps.getSelectionModel();
                                                 
                                                 for (int i=0; i<listModel.size(); i++)
                                                     if (!selModel.isSelectedIndex(i))
                                                         if (getFilled(i).band != -1)
                                                         {
                                                             redrawTriggered();
                                                             break;
                                                         }
                                             }
                                         }
        );
        
        chkKeepImagesInMemory = new JCheckBox("Keep images in memory");
        chkKeepImagesInMemory.addActionListener(
                                          new ActionListener()
                                          {
                                              public void actionPerformed(ActionEvent e)
                                              {
                                                  imageReleaseEnabled = !chkKeepImagesInMemory.isSelected();
                                              }
                                          }
        );
        
        mapper = new FancyColorMapper();
        mapper.addChangeListener(
                                 new ChangeListener()
                                 {
                                     public void stateChanged(ChangeEvent e)
                                     {
                                         if (ignoreChangeEvents  ||  mapper.isAdjusting())
                                             return;
                                         getFilledSingle().colors = mapper.getState();
                                         if (getFilledSingle().band != -1)
                                             performRedrawSingle(getFilledSingle());
                                     }
                                 }
        );
        mapper.btnAuto.setEnabled(true);
        mapper.btnAuto.addActionListener(
                                         new ActionListener()
                                         {
                                             public void actionPerformed(ActionEvent e)
                                             {
                                                 FilledStamp fs = getFilledSingle();
                                                 if (fs == null)
                                                     return;
                                                 
                                                 // guessMinMax
                                                 if (fs.band == -1)
                                                     return;
                                                 
                                                 int[] hist = fs.pdsi.getHistogram(fs.band);
                                                 
                                                 // Find the peak
                                                 int top = 0;
                                                 for (int i=0; i<256; i++)
                                                     if (hist[i] > hist[top])
                                                         top = i;
                                                     
                                                     // Find the hi boundary: the next time we hit 5% peak
                                                 int hi = top;
                                                 while(hi < 255  &&  hist[hi]*20 > hist[top])
                                                     ++hi;
                                                 
                                                 // Find the lo boundary: the prior time we hit 5% peak
                                                 int lo = top;
                                                 while(lo > 0  &&  hist[lo]*20 > hist[top])
                                                     --lo;
                                                 
                                                 mapper.rescaleTo(lo, hi);
                                             }
                                         }
        );
        
        // Set proper state
        enableEverything();
        
        // Assemble the per-stamp panel
        pnlPerStamp = new JPanel();
        pnlPerStamp.setLayout(new BoxLayout(pnlPerStamp, BoxLayout.Y_AXIS));
        pnlPerStamp.add(
                        new JPanel()
                        {{
                            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                            add(pnlPanning);
                            add(Box.createHorizontalStrut(BDR));
                            add(
                                new JPanel(new GridLayout(0, 1))
                                {{
                                    add(btnTop);
                                    add(btnRaise);
                                    add(btnLower);
                                    add(btnBottom);
                                    add(btnDelete);
                                    add(btnSort);
                                }}
                            );
                            verticalSquish(this);
                        }}
        );
        pnlPerStamp.add(
                        new JPanel()
                        {{
                            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                            
                            btnRotateImage.setAlignmentX(dropBand.getAlignmentX());
                            add(btnRotateImage);
                            btnHFlip.setAlignmentX(dropBand.getAlignmentX());
                            add(btnHFlip);
                            btnVFlip.setAlignmentX(dropBand.getAlignmentX());
                            add(btnVFlip);
                            
                            btnViewPds.setAlignmentX(dropBand.getAlignmentX());
                            add(btnViewPds);
                            
                            add(Box.createVerticalStrut(BDR));
                            btnListCopy.setAlignmentX(dropBand.getAlignmentX());
                            add(btnListCopy);
                            btnListImport.setAlignmentX(dropBand.getAlignmentX());
                            add(btnListImport);
                            
                            add(Box.createVerticalStrut(BDR));
                            add(Box.createGlue());
                            add(dropBand);
                            setBorder(BorderFactory.createEmptyBorder(BDR, 0, 0, 0));
                        }}
        );
        pnlPerStamp.setBorder(niceBevel());
        
        // Assemble the panel containing almost everything
        pnlMain = new JPanel();
        pnlMain.setLayout(new BoxLayout(pnlMain, BoxLayout.Y_AXIS));
        pnlMain.add(
                    new JPanel(new GridLayout(0, 1, 0, 0))
                    {{
                        setBorder(BorderFactory.createCompoundBorder(
                                                                     BorderFactory.createLoweredBevelBorder(),
                                                                     BorderFactory.createEmptyBorder(0, BDR, 0, BDR)));
                        add(chkHideOutlines);
                        add(chkSingleImage);
                        add(chkKeepImagesInMemory);
                        verticalSquish(this);
                    }}
        );
        pnlMain.add(pnlPerStamp);
        
        // Assemble the color-mapper panel
        JPanel pnlMapper =
            new JPanel(new BorderLayout())
            {{
                add(mapper, BorderLayout.CENTER);
                setBorder(niceBevel());
            }};
            
            // Assemble everything together into 'this'
            this.setLayout(new BorderLayout());
            this.add(pnlMain, BorderLayout.WEST);
            this.add(pnlListStamps, BorderLayout.CENTER);
            this.add(pnlMapper, BorderLayout.SOUTH);
    }
    
    // Subclasses should override this method to replace
    // base class's image-loading behavior with that of
    // a different behavior or image type.
    protected StampImage load(Stamp s)
    {
        return imageFactory.load(s);
    }
    
    /**
     * Creates a sorted list from the specified stamps.  The
     * sort order is according to each stamp's equator intercept
     * location, i.e., the point at which the stamp's corresponding
     * orbit track would intercept the equator.  Stamps are ordered
     * from west-to-east intercept longitude.
     * 
     * @param unsorted  List of unsorted stamps
     */
    protected Stamp[] orbitTrackSort(Stamp[] unsorted)
    {
        if (unsorted == null)
            return null;
        
        // Compute equator intercept longitude for each stamp.
        final Map equatorLon = new HashMap();
        for (int i = 0; i < unsorted.length; i++) {
            HVector nw = new HVector(unsorted[i].getNW());
            HVector sw = new HVector(unsorted[i].getSW());
            HVector npole = new HVector(0, 0, 1);
            HVector orbitPlane = nw.cross(sw); 
            HVector npoleCross = orbitPlane.cross(npole);
            
            equatorLon.put(unsorted[i], new Double(npoleCross.lonE()));
        }
        
        Stamp[] sorted = (Stamp[])unsorted.clone();
        Arrays.sort(sorted, new Comparator()
                                {
                                    public int compare(Object a, Object b)
                                    {
                                        Double lonA = (Double)equatorLon.get(a);
                                        Double lonB = (Double)equatorLon.get(b);
                                        if (lonA == null ||
                                            lonB == null)
                                        {
                                            log.aprintln("could not find longitude for either/both of stamp: " + a +
                                                         " or stamp: " + b);
                                            return 0;
                                        }
                                            
                                        double diff = lonA.doubleValue() - lonB.doubleValue();
                                        if (diff < 0)
                                            return -1;
                                        else if (diff > 0)
                                            return 1;
                                        else
                                            return 0;
                                    }
                                });
        
        return sorted;
    }
    
    /** 
     ** NOTE: Subclasses must set visible the  'Rotate Image 180', 
     ** 'Horizontal Flip', and 'Vertical Flip' buttons if they
     ** need to support image rotation/flipping.  By default,
     ** these are not visible.
     **
     ** Rotates or flips image as specified relative to current 
     ** stamp image orientation
     **
     ** @param operation legal operation values are {@link #IMAGE_ROTATED_180}, 
     ** {@link #IMAGE_HFLIPPED}, {@link #IMAGE_VFLIPPED}.
     **/
    protected void rotateFlipImage(FilledStamp fs, int operation)
    {
        if (fs != null &&
            fs.pdsi != null)
        {
            fs.pdsi.rotateFlip(fs.band, operation);
            performRedrawSingle(fs);
        }
    }
    
    protected int getNumStamps()
    {
        return listModel.getSize();
    }
    
    private Stamp[] getSelectedStamps()
    {
        Object[] objs = listStamps.getSelectedValues();
        Stamp[] stamps = new Stamp[objs.length];
        for (int i=0; i<stamps.length; i++)
            stamps[i] = (Stamp) objs[i];
        return  stamps;
    }
    
    // Returns list of IDs for stamps.
    public String[] getStampIDList()
    {
        String[] list = new String[listModel.size()];
        
        for (int i=0; i < listModel.size(); i++)
        {
            Stamp stamp = (Stamp)listModel.get(i);
            list[i] = stamp.id;
        }
        
        return list;
    }
    
    /**
     ** Returns list of filled stamp states
     **/
    public FilledStamp.State[] getStampStateList()
    {
        FilledStamp.State[] stateList = null;
        String[] idList = getStampIDList();
        
        if (idList != null) {
            stateList = new FilledStamp.State[idList.length];
            
            // Stamp ID list should already be in the same
            // order as used by listModel in getFilled().
            for (int i = 0; i < idList.length; i++) {
                FilledStamp fs = getFilled(i);
                
                if (fs == null ||
                    fs.stamp == null ||
                    !fs.stamp.id.equals(idList[i]))
                {
                    log.aprintln("filled stamp information different than expected");
                    return null;
                }
                else
                    stateList[i] = fs.getState();
            }
        }
        
        return stateList;
    }
    
    public boolean stampOutlinesHiddenSelected()
    {
        return chkHideOutlines.isSelected();
    }
    
    public boolean stampSingleImageSelected()
    {
        return chkSingleImage.isSelected();
    }
    
    public boolean imageReleaseEnabled()
    {
        return imageReleaseEnabled;
    }
    
    public void setStampOutlinesHiddenSelected(boolean enable)
    {
        chkHideOutlines.setSelected(enable);
    }
    
    public void setStampSingleImageSelected(boolean enable)
    {
        chkSingleImage.setSelected(enable);
    }
    
    private class BandRenderer implements ListCellRenderer
    {
        ListCellRenderer orig;
        JLabel redLabel;
        
        BandRenderer(ListCellRenderer orig)
        {
            this.orig = orig;
            redLabel =
                new JLabel(" ")
                {
                {
                    setOpaque(true);
                    super.setBackground(Color.red);
                }
                public void setBackground(Color col) { }
                public void setForeground(Color col) { }
                }
            ;
        }
        
        public Component getListCellRendererComponent(
                                                      JList list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus)
        {
            if (index < 0)
            {
                String strval = (String) value;
                if (strval != null  &&  strval.startsWith("-"))
                {
                    redLabel.setText(strval);
                    return  redLabel;
                }
            }
            
            return  orig.getListCellRendererComponent(list,
                                                      value,
                                                      index,
                                                      isSelected,
                                                      cellHasFocus);
        }
    }
    
    protected void enableEverything()
    {
        int[] selected = listStamps.getSelectedIndices();
        boolean anySelected = !listStamps.isSelectionEmpty();
        boolean singleSelected = selected.length == 1;
        boolean rangeSelected = isContiguous(selected);
        
        btnRaise.setEnabled(rangeSelected);
        btnLower.setEnabled(rangeSelected);
        btnTop.setEnabled(rangeSelected);
        btnBottom.setEnabled(rangeSelected);
        btnDelete.setEnabled(anySelected);
        btnSort.setEnabled(listModel.size() > 0);
        
        btnPanNW.setEnabled(anySelected);
        btnPanN .setEnabled(anySelected);
        btnPanNE.setEnabled(anySelected);
        btnPanW .setEnabled(anySelected);
        btnPanE .setEnabled(anySelected);
        btnPanSW.setEnabled(anySelected);
        btnPanS .setEnabled(anySelected);
        btnPanSE.setEnabled(anySelected);
        btnPanSize.setEnabled(anySelected);
        
        dropBand.setEnabled(singleSelected);
        btnRotateImage.setEnabled(singleSelected);
        btnHFlip.setEnabled(singleSelected);
        btnVFlip.setEnabled(singleSelected);
        btnViewPds.setEnabled(singleSelected);
        
        if (listModel.getSize() > 0)
            btnListCopy.setEnabled(true);
        else
            btnListCopy.setEnabled(false);
        
        mapper.setEnabled(singleSelected  &&
                          getFilledSingle() != null  &&  
                          getFilledSingle().band != -1);
    }
    
    private boolean isContiguous(int[] values)
    {
        return  values.length != 0
                &&  values[values.length-1] == values[0] + values.length-1;
    }
    
    protected void updateStampSelected()
    {
        enableEverything();
        
        // Set new band and colors
        FilledStamp fs = getFilledSingle();
        if (fs == null)
        {
            log.println("Null selection, removing all items");
            dropBand.removeAllItems();
        }
        else
        {
            log.println("Implementing newly-selected " + fs.stamp);
            String[] bands = new String[fs.pdsi.getBandCount()+1];
            bands[0] = "-No band selected-";
            System.arraycopy(fs.pdsi.getBands(), 0,
                             bands, 1,
                             fs.pdsi.getBandCount());
            log.println("Setting band to " + fs.band + " + 1");
            ignoreChangeEvents = true;
            dropBand.setModel(new DefaultComboBoxModel(bands));
            dropBand.setSelectedIndex(fs.band + 1);
            mapper.setState(fs.colors);
            ignoreChangeEvents = false;
        }
    }
    
    private Border cachedBevel;
    private Border niceBevel()
    {
        return  BorderFactory.createCompoundBorder(
                                                   BorderFactory.createLoweredBevelBorder(),
                                                   BorderFactory.createEmptyBorder(BDR, BDR, BDR, BDR)
        );
    }
    
    private static void verticalSquish(JComponent c)
    {
        Dimension d = c.getMinimumSize();
        d.width = c.getMaximumSize().width;
        c.setMaximumSize(d);
    }
    
    private static final int[] panSizeList = { 1, 2, 5, 10 };
    private static final ImageIcon[] panSizeIcons;
    private static final ImageIcon[] panSizeIconsD; // disabled icons
    static
    {
        panSizeIcons  = new ImageIcon[panSizeList.length];
        panSizeIconsD = new ImageIcon[panSizeList.length];
        for (int i=0; i<panSizeList.length; i++)
            try
            {
                URL url = Main.getResource("resources/pan_" +
                                           panSizeList[i] + ".gif");
                panSizeIcons[i]  = new ImageIcon(url);
                panSizeIconsD[i] = new ImageIcon(
                                                 GrayFilter.createDisabledImage(
                                                                                panSizeIcons[i].getImage())
                );
            }
        catch(Throwable e)
        {
            log.println("Failed to load icon for pansize " +
                        panSizeList[i]);
        }
    }
    private int panIdx = 0;
    private int panSize = panSizeList[panIdx];
    private class PanButton extends JButton
    {
        // Button for toggling pan step-size
        PanButton()
        {
            setAction(
                      new AbstractAction(null, panSizeIcons[0])
                      {
                          public void actionPerformed(ActionEvent e)
                          {
                              panIdx = (panIdx + 1) % panSizeList.length;
                              panSize = panSizeList[panIdx];
                              setIcon        (panSizeIcons [panIdx]);
                              setDisabledIcon(panSizeIconsD[panIdx]);
                          }
                      }
            );
            setToolTipText("Toggle the number of pixels" +
                           " that the arrow buttons shift by.");
            squish();
        }
        // Movement button
        PanButton(final int x, final int y)
        {
            // Determine an icon for the given x/y direction
            String dir = "";
            switch(y)
            {
            case -1:  dir += "s";  break;
            case +1:  dir += "n";  break;
            }
            switch(x)
            {
            case -1:  dir += "w";  break;
            case +1:  dir += "e";  break;
            }
            Icon dirIcon = null;
            try
            {
                dirIcon =
                    new ImageIcon(Main.getResource(
                                                   "resources/pan_" + dir + ".gif"));
            }
            catch(Throwable e)
            {
                System.out.println("Unable to load dir " + dir);
            }
            
            setAction(
            		new AbstractAction(null, dirIcon)
            		{
            			public void actionPerformed(ActionEvent e)
            			{
            				Point2D worldPan = getWorldPan(x * panSize,
            						y * panSize);
            				boolean redraw = false;
            				FilledStamp[] fss = getFilledAll();
            				for (int i=0; i<fss.length; i++)
            				{
            					Point2D oldOffset = fss[i].getOffset();
            					fss[i].setOffset(new Point2D.Double(oldOffset.getX() + worldPan.getX(),
            														oldOffset.getY() + worldPan.getY()));
            					
            					fss[i].saveOffset();
            					
            					if (fss[i].band != -1)
            						redraw = true;
            				}

            				if (redraw)
            					redrawTriggered();
            			}
            		}
            );
            setToolTipText("Shift the filled stamp(s) on-screen.");
            squish();
        }
        void squish()
        {
            setFocusPainted(false);
            
            Dimension d = this.getMinimumSize();
            d.width = d.height;
            setMaximumSize(d);
            setPreferredSize(d);
        }
    }
    
    protected FilledStamp[] getFilledAll()
    {
        Object[] objs = listStamps.getSelectedValues();
        FilledStamp[] filled = new FilledStamp[objs.length];
        for (int i=0; i<filled.length; i++)
            filled[i] = getCache((Stamp) objs[i]);
        return  filled;
    }
    
    private FilledStamp getFilledSingle()
    {
        Object[] selected = listStamps.getSelectedValues();
        if (selected.length != 1)
            return  null;
        return getCache((Stamp) selected[0]);
    }
    
    protected FilledStamp getFilled(int n)
    {
        return  getFilled( (Stamp) listModel.get(n) );
    }
    
    protected FilledStamp getFilled(Stamp s)
    {
        return getFilled(s, null);
    }
    
    /**
     ** @param state optional position offset and color map state settings
     ** used to restore FilledStamp state; may be null.
     **/
    protected FilledStamp getFilled(Stamp s, FilledStamp.State state)
    {
        synchronized(getCacheLock())
        {
            FilledStamp fs = getCache(s);
            
            if (fs == null) {
                StampImage pdsi = load(s);
                if (pdsi == null)
                    return  null;
                
                if (state != null)
                    addCache(fs = new FilledStamp(s, pdsi, state));
                else
                    addCache(fs = new FilledStamp(s, pdsi));
            }
            
            return  fs;
        }
    }
    
    /**
     * Returns lock object which should be used in synchronization
     * blocks to lock all access to the filled stamp cache.
     * 
     * @return Cache lock object; never <code>null</code>
     */
    protected final Object getCacheLock()
    {
        return cacheLock;
    }
    
    /**
     * Returns filled stamp for specified stamp from cache if available.
     * 
     * @return Returns <code>null</code> if filled stamp is not found
     * in cache.
     */
    protected final FilledStamp getCache(Stamp s)
    {
        synchronized(getCacheLock())
        {
            return (FilledStamp) fCache.get(s);
        }
    }
    
    /**
     * Removes filled stamp for specified stamp from cache if present.
     * Also removes the specified stamp from the list of new stamps.
     * 
     * @see #getNewStamps
     */
    protected final void removeCache(Stamp s)
    {
        synchronized(getCacheLock())
        {
            fCache.remove(s);
            newFilledList.remove(s);
        }
    }
    
    /**
     * Adds specified stamp to cache of filled stamps.  Notes the
     * change for purposes of tracking sequences of recent filled
     * stamp additions.
     * 
     * @see #getNewStamps
     * @see #clearNewStamps
     */
    protected final void addCache(FilledStamp fs)
    {
        if (fs != null) {
            synchronized(getCacheLock())
            {
                Stamp s = fs.stamp;
                if (getCache(s) == null) {
                    fCache.put(s, fs);
                    newFilledList.add(s);
                }
            }
        }
    }
    
    /**
     * Puts specified stamp onto top of new stamps list.  This
     * causes the contained base stamp to be returned as
     * a "new" stamp for redraw purposes.
     * 
     * @see #getNewStamps
     * @see #clearNewStamps
     */
    protected final void putTopStamp(Stamp s)
    {
        if (s != null) {
            synchronized(getCacheLock())
            {
                if (!newFilledList.contains(s))
                    newFilledList.add(s);
            }
        }
    }
    
    /**
     * Returns array of "new" stamps that have either been added as
     * filled stamps since the last call to {@link #clearNewStamps},
     * or that have been put onto the top of the filled stamps for
     * redraw purposes.
     * <p>
     * If in the interim a stamp is added and then removed as a 
     * filled stamp, then it is omitted from the new stamp list.
     *  
     * @return Array of {@link Stamp} instances; never <code>null</code>.
     * 
     * @see #removeCache 
     * @see #putTopStamp 
     */
    public final Stamp[] getNewStamps()
    {
        // Since the list of new stamps changes with additions to the
        // filled stamp cache, we must lock access to the latter.
        synchronized(getCacheLock())
        {
            return (Stamp[]) newFilledList.toArray(new Stamp[0]);
        }
    }
    
    /**
     * Clears list stamps that are new/top filled stamp additions.  Does
     * not alter either the filled stamp cache or the list of filled
     * stamps in this focus panel.
     * 
     * @see #getNewStamps 
     * @see #putTopStamp 
     */
    public final void clearNewStamps()
    {
        // Since the list of new stamps changes with additions to the
        // filled stamp cache, we must lock access to the latter.
        synchronized(getCacheLock())
        {
            newFilledList.clear();
        }
    }
    
    
    public void addStamp(Stamp s)
    {
        addStamp(s, false, false, false, false, -1);
    }
    
    /**
     *  @param state optional position offset and color map state settings
     *  used to restore FilledStamp state; may be null.
     *
     *  @param redraw flag controlling whether or not the added stamp is
     *  selected and redrawn; setting to false is useful for adding multiple
     *  stamps before a full screen/UI update.
     */
    public void addStamp(Stamp s, FilledStamp.State state, boolean redraw,
                         boolean ignoreAlreadyFilled, boolean ignoreGeometry, boolean ignoreNotLoaded)
    {
        addStamp(s, state, redraw, ignoreAlreadyFilled, ignoreGeometry, ignoreNotLoaded, -1);
    }
    
    /**
     *  @param bandToRender index of band in combox list to select after adding;
     *                     -1 means no selection
     *  @param redraw       render/draw any image band that has been selected.
     */
    protected void addStamp(Stamp s, boolean redraw, 
                            boolean ignoreAlreadyFilled, boolean ignoreGeometry, boolean ignoreNotLoaded,
                            int bandToRender)
    {
        addStamp(s, null, redraw, ignoreAlreadyFilled, ignoreGeometry, ignoreNotLoaded, bandToRender);
    }
    
    /**
     *  @param state optional position offset and color map state settings
     *  used to restore FilledStamp state; may be null.
     *
     *  @param bandToRender index of band in combox list to select after adding;
     *                     -1 means no selection
     *  @param redraw       render/draw any image band that has been selected.
     *
     *  Note: If the 'state' and 'bandToRender' parameters are both given meaningful
     *  specifications, then 'state' is used and 'bandToRender' is ignored.
     */
    protected void addStamp(final Stamp s, FilledStamp.State state, boolean redraw, 
                            boolean ignoreAlreadyFilled, boolean ignoreGeometry, boolean ignoreNotLoaded,
                            int bandToRender)
    {
        FilledStamp fs;
        
        if (listModel.contains(s))
        {
            if (!ignoreAlreadyFilled)
                JOptionPane.showMessageDialog(this,
                                              "Already in filled-stamp list: " + s,
                                              "OPERATION IGNORED",
                                              JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (state != null)
            fs = getFilled(s, state);
        else
            fs = getFilled(s);
        
        if (fs == null ||
                fs.pdsi == null)
        {
            if (!ignoreNotLoaded)
                JOptionPane.showMessageDialog(this,
                                              "Unable to load " + s,
                                              "PDS LOAD ERROR",
                                              JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        Point2D[] pts = fs.pdsi.getPoints();
        if (pts == null ||
                pts.length == 0)
        {
            if (!ignoreGeometry)
                JOptionPane.showMessageDialog(
                                              this,
                                              "Detailed geometry not available for " + s,
                                              "MYSQL ERROR",
                                              JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if ( fs.pdsi.validGeometryInfo(pts) == false )
        {
            if (!ignoreGeometry)
                JOptionPane.showMessageDialog(this,
                                              "Invalid geometry information for " + s,
                                              "MYSQL ERROR",
                                              JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (state != null)
            fs.band = state.band;
        else
            fs.band = bandToRender;
        
        listModel.insertElementAt(s, 0);
        
        if (fs.band >= 0 && redraw) {
            // Ignore change events while altering list stamp selection;
            // excessive repaints happen otherwise when adding stamps
            // with "fill only selected stamps" option turned on.
            ignoreChangeEvents = true;
            listStamps.setSelectedIndex(0);
            ignoreChangeEvents = false;
            
            // Update stuff that depends on how many stamps are selected;
            // also set the band in the band selection combo.
            //
            // Note:  There appears to be some duplication for the band selection
            // in the following two lines, but they are both needed
            // since there are slight differences that seem to matter
            // depending on which UI mechanism was used to add the
            // stamp.  Having both lines seems to handle all the cases.
            //
            // "Don't ask me why; I just work here...." -- Joel
            updateStampSelected();
            dropBand.setSelectedIndex(fs.band + 1);
            
            // Handle the stamp selection propagation via the following
            // Runnable, since we disabled the normal mechanism above.  
            // Done this way there are fewer repaints, i.e., fewer mechanisms 
            // triggered seemingly.
            Runnable delayed = 
                new Runnable()
                {
                    public void run()
                    {
                        FilledStampFocus.this.selectOccurred(true,
                                                             new Stamp[] {s},
                                                             new Stamp[0]);
                    }
                };
            SwingUtilities.invokeLater(delayed);
        }
        else if (fs.band == -1) {
            listStamps.setSelectedIndex(0);
            updateStampSelected();
        }
    }
    
    /**
     * Adds any stamps from file that are found in the associated
     * layer for this panel's stamp view.  File must contain
     * list of stamp IDs delimited by whitespace (includes newlines).
     */
    private void addStamps(File file)
    {
        try {
            if (file != null &&
                parent != null)
            {
               BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
               StampLayer layer = (StampLayer)parent.getLayer();
               
               if (layer == null) {
                   log.aprintln("null layer reference");
                   return;
               }

               // Build list of stamps from file that are present in this
               // panel's stamp view's layer.
               ArrayList stampList = new ArrayList();
               String line = reader.readLine();
               while (line != null) {
                   StringTokenizer tokenizer = new StringTokenizer(line);
                   while (tokenizer.hasMoreTokens()) {
                       String stampID = tokenizer.nextToken();
                       Stamp stamp = layer.getStamp(stampID);
                       
                       if (stamp != null) {
                           log.println("adding stamp " + stampID);
                           stampList.add(stamp);
                       }
                   }
                   
                   line = reader.readLine();
               }
               
               // Add stamps to rendered list as a group (improves user
               // experience during the loading, projecting, ... phases).
               Stamp[] stamps = (Stamp[]) stampList.toArray(new Stamp[0]);
               parent.addSelectedStamps(stamps, 0, null, 0, 0, true);
            }
        }
        catch (Exception e) {
            log.aprintln(e);
        }
    }
    
    public void addStamps(Stamp[] ss)
    {
        String problems = "";
        int oldSize = listModel.getSize();
        for (int i=0; i<ss.length; i++)
        {
            FilledStamp fs = getFilled(ss[i]);
            if (fs == null  ||  fs.pdsi.getPoints().length == 0)
                problems += "\n    " + ss[i];
            else if (!listModel.contains(ss[i]))
            {
                fs.band = -1;
                listModel.insertElementAt(ss[i], 0);
            }
        }
        
        if (problems.length() != 0)
            JOptionPane.showMessageDialog(this,
                                          "Unable to load:" + problems,
                                          "STAMP IMAGE/GEOMETRY LOAD ERROR",
                                          JOptionPane.ERROR_MESSAGE);
        
        if (oldSize != listModel.getSize())
        {
            listStamps.setSelectedIndex(0);
            updateStampSelected();
        }
    }
    
    /**
     ** Given a user-requested pan in pixels, should return the actual
     ** pan in world coordinates.
     **/
    protected abstract  Point2D getWorldPan(int px, int py);
    
    protected abstract void selectOccurred(boolean clearFirst,
                                           Stamp[] adds,
                                           Stamp[] dels);
    
    /**
     ** Indicates some state change in the dialog that would require
     ** redrawing of the images.
     **/
    protected abstract void performRedrawAll(FilledStamp[] displayed);
    
    /**
     * Indicates that the stamp lines (only) need to be redrawn according
     * to the specified state.  This state must be stored for use in
     * any subsequent {@link #performRedrawAll} method calls.
     * 
     * @param showOutlines If <code>true</code>, then the stamp outlines
     * are displayed; otherwise, outlines are hidden.
     */
    protected abstract void redrawOutlines(boolean showOutlines);
    
    /**
     ** Indicates that the user changed the drawing parameters of a
     ** single image. Default implementation simply invokes a complete
     ** redraw.
     **/
    protected void performRedrawSingle(FilledStamp changed)
    {
        redrawTriggered();
    }
    
    protected void redrawTriggered()
    {
        if (chkSingleImage.isSelected())
            if (listStamps.getSelectedIndex() == -1)
                performRedrawAll(new FilledStamp[0]);
            else
                performRedrawAll(getFilledAll());
        else
        {
            FilledStamp[] displayed = new FilledStamp[listModel.getSize()];
            for (int i=0; i<displayed.length; i++)
                displayed[i] = getFilled(i);
            performRedrawAll(displayed);
        }
    }
    
    /**
     ** Trivial implementation for testing purposes.
     **/
    static class Demo extends FilledStampFocus
    {
        Demo(StampLView parent)
        {
            super(parent, new PdsBtrImageFactory());
        }
        
        protected void performRedrawAll(FilledStamp[] displayed)
        {
            log.aprintStack(3);
            log.aprintln("------------ Redraw:");
            for (int i=0; i<displayed.length; i++)
                log.aprintln("\t" + displayed[i].stamp);
        }
        protected void performRedrawSingle(FilledStamp single)
        {
            log.aprintln("Single redraw: " + single.stamp);
        }
        protected void redrawOutlines(boolean showOutlines)
        {
        }
        protected Point2D getWorldPan(int px, int py)
        {
            return  new Point(px, py);
        }
        protected void selectOccurred(boolean clearFirst,
                                      Stamp[] adds,
                                      Stamp[] dels)
        {
            log.aprintln(clearFirst);
            for (int i=0; i<adds.length; i++)
                log.aprintln("+ " + adds[i]);
            for (int i=0; i<dels.length; i++)
                log.aprintln("- " + dels[i]);
        }
    }
    
    public Dimension getMinimumSize()
    {
        return  getPreferredSize();
    }
    
    /**
     ** Simple test driver.
     **/
    public static void main(String[] av)
    {
        DebugLog.readFile(".debugrc");
        JFrame frame = new JFrame("TestFocus");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        FilledStampFocus focus = new FilledStampFocus.Demo(null);
        focus.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        focus.addStamps(new Stamp[] { new Stamp("I01111002"),
                                      new Stamp("I01111006"),
                                      new Stamp("I01113006") }
        );
        
        frame.setContentPane(focus);
        frame.pack();
        frame.setVisible(true);
    }
    
    private File lastDirectory;
    
    protected class ImportStampsDialog implements ActionListener
    {
        private JDialog dialog;
        private JTextField txtFilename;
        private JButton btnBrowse = new JButton("Browse...");
        private JButton btnOK = new JButton("OK");
        private JButton btnCancel = new JButton("Cancel");
        
        ImportStampsDialog()
        {
            // Top panel
            JPanel top = new JPanel();
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            
            String msg1 = "Specify text file containing list of stamps to import.  The file " +
                          "must contain stamp IDs delimited by whitespace (includes newlines).";
            String msg2 = "Each stamp will be loaded and rendered if it is included in the " +
                          "list of stamps for this layer; otherwise, the stamp is ignored.";
            msg1 = Util.lineWrap(msg1, 60);
            msg2 = Util.lineWrap(msg2, 60);
            JPanel textPanel1 = new JPanel();
            JPanel textPanel2 = new JPanel();
            textPanel1.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
            textPanel2.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));
            textPanel1.add(new MultiLabel(msg1));
            textPanel2.add(new MultiLabel(msg2));
            
            top.add(textPanel1);
            top.add(textPanel2);
            
            // Middle panel
            JPanel middle = new JPanel();
            middle.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            middle.add(new JLabel("Filename:"));
            txtFilename = new PasteField(20);
            middle.add(txtFilename);
            
            // File chooser dialog launch button.
            btnBrowse.addActionListener(
                                        new ActionListener()
                                        {
                                            private JFileChooser fc = new JFileChooser(lastDirectory);
                                                
                                            public void actionPerformed(ActionEvent e)
                                            {
                                                // Show the file chooser
                                                if(fc.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION)
                                                    return;
                                                
                                                txtFilename.setText(fc.getSelectedFile().getPath());
                                                lastDirectory = fc.getCurrentDirectory();
                                            }
                                        }
            );
            middle.add(btnBrowse);
            
            // Bottom panel
            JPanel bottom = new JPanel();
            btnOK.addActionListener(this);
            bottom.add(btnOK);
            btnCancel.addActionListener(this);
            bottom.add(btnCancel);
            
            // Construct the dialog itself
            dialog = new JDialog(Main.getLManager(),
                                 "Import Stamps",
                                 true);
            dialog.getContentPane().add(top, BorderLayout.NORTH);
            dialog.getContentPane().add(middle, BorderLayout.CENTER);
            dialog.getContentPane().add(bottom, BorderLayout.SOUTH);
            dialog.pack();
            dialog.setLocation(Main.getLManager().getLocation());
        }
        
        // Does not return until dialog is hidden or
        // disposed of.
        public void show()
        {
            dialog.setVisible(true);
        }
        
        public void actionPerformed(ActionEvent e)
        {
            if(e.getSource() == btnCancel) {
                dialog.dispose();
                return;
            }
            else if (e.getSource() == btnOK) {
                String filename = txtFilename.getText().trim();
                if (filename == null ||
                        filename.equals("")) {
                    JOptionPane.showMessageDialog(null,
                                                  "Please provide name of file.",
                                                  null,
                                                  JOptionPane.PLAIN_MESSAGE);
                    return;
                }
                
                File file = new File(filename);
                if (!file.exists()) {
                    JOptionPane.showMessageDialog(null,
                                                  "File named " + filename + " does not exist.",
                                                  null,
                                                  JOptionPane.PLAIN_MESSAGE);
                    return;
                }
                
                addStamps(file);
                
                dialog.dispose();
                return;
            }
        }
    }
}
