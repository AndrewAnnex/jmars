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


package edu.asu.jmars.layer.stamp.focus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.GrayFilter;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.MouseInputAdapter;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.MultiProjection;
import edu.asu.jmars.layer.stamp.FilledStamp;
import edu.asu.jmars.layer.stamp.StampImage;
import edu.asu.jmars.layer.stamp.StampImageFactory;
import edu.asu.jmars.layer.stamp.StampLView;
import edu.asu.jmars.layer.stamp.StampLayer;
import edu.asu.jmars.layer.stamp.StampShape;
import edu.asu.jmars.layer.stamp.FilledStamp.State;
import edu.asu.jmars.layer.stamp.StampLayer.StampSelectionListener;
import edu.asu.jmars.layer.stamp.chart.ChartView;
import edu.asu.jmars.swing.FancyColorMapper;
import edu.asu.jmars.swing.MultiLabel;
import edu.asu.jmars.swing.PasteField;
import edu.asu.jmars.swing.ValidClipboard;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.HVector;
import edu.asu.jmars.util.Util;


public class FilledStampFocus extends JPanel implements StampSelectionListener
{
    private static final DebugLog log = DebugLog.instance();
    
    final protected StampLView parent;
    final protected StampLayer stampLayer;
    
    protected JList listStamps;
    protected DefaultListModel listModel;
    
    protected JButton btnViewPds;
    protected JButton btnListCopy;
    protected JButton btnListImport;
    
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
    
    JButton resetPan;
    JLabel xoffset;
    JLabel yoffset;
    
    protected boolean stampListDragging = false;
    
    private final JCheckBox onlyFillSelected;
    
    private FancyColorMapper mapper;
        
    private int dragStartIndex = -1;
    private int dragCurIndex = -1;
    
    // Examine this very very carefully for race conditions
    boolean modelUpdating = false;
        
	public void selectionsChanged() {
        List<StampShape> selectedStamps = stampLayer.getSelectedStamps();
        
        // We want to know what was selected at the start of this process, since it
        // can and will change as we're processing
		Object[] obj=listStamps.getSelectedValues();

        Enumeration listElements=listModel.elements();
        
        modelUpdating=true;
        
		while(listElements.hasMoreElements()) {
			FilledStamp fs = (FilledStamp)listElements.nextElement();
			if (selectedStamps.contains(fs.stamp)) {
				boolean alreadySelected=false;
				
				for (int i=0; i<obj.length; i++) {
					FilledStamp fso = (FilledStamp)obj[i];
					if (fso.stamp==fs.stamp) {
						alreadySelected=true;
						break;
					}
				}
				if (!alreadySelected) {
					int idx =listModel.indexOf(fs);
					if (idx>-1) {
						listStamps.addSelectionInterval(idx, idx);
					}
				}
			} else {
				int idx =listModel.indexOf(fs);
				if (idx>-1) {
					listStamps.removeSelectionInterval(idx, idx);
				}
			}
		}
		
		modelUpdating=false;
		redrawTriggered();
	}
	
	public void selectionsAdded(java.util.List<StampShape> newStamps) {
        Enumeration listElements=listModel.elements();

        // We want to know what was selected at the start of this process, since it
        // can and will change as we're processing
		Object[] obj=listStamps.getSelectedValues();
        
		while(listElements.hasMoreElements()) {
			FilledStamp fs = (FilledStamp)listElements.nextElement();
			if (newStamps.contains(fs.stamp)) {
				boolean alreadySelected=false;
				
				for (int i=0; i<obj.length; i++) {
					FilledStamp fso = (FilledStamp)obj[i];
					if (fso.stamp==fs.stamp) {
						alreadySelected=true;
						break;
					}
				}
				if (!alreadySelected) {
					int idx =listModel.indexOf(fs);
					if (idx>-1) {
						listStamps.addSelectionInterval(idx, idx);
					}
				}
			}
		}
	}
    
	int startSelectionIndex=-1;
	
	protected class ListKeyListener extends KeyAdapter {

		public void keyReleased(KeyEvent e) {
			if (e.isShiftDown()==false) {
				startSelectionIndex=-1;
				lastRow=-1;
			}
		}

		public void keyPressed(KeyEvent e) {
			// Do not override the standard copy/paste functionality
			if (e.isControlDown()) {
				super.keyPressed(e);
				return;
			}

			// Override standard selection, so we can make it work the way we want it to
			e.consume();
			
			if (e.isShiftDown()) {
				handleShiftClick(e);
				return;
			}
			
			int lastClicked = listStamps.getSelectionModel().getLeadSelectionIndex();

			if (e.isShiftDown()) {
				if (startSelectionIndex==-1) {
					startSelectionIndex=lastClicked;
				}
			}
			
			int newRow;
			if (e.getKeyCode()==KeyEvent.VK_UP) {
				newRow=lastClicked-1;
			} else if (e.getKeyCode()==KeyEvent.VK_DOWN) {
				newRow=lastClicked+1;
			} else {
				return; 
			}
			
			if (newRow>=listStamps.getModel().getSize() || newRow<0) { 
				return;
			}
			
			FilledStamp fs = (FilledStamp)listStamps.getModel().getElementAt(newRow);

			if (listStamps.getSelectedValues().length==1) {
				FilledStamp oldSelection = (FilledStamp)listStamps.getSelectedValue();
				if (oldSelection.stamp == fs.stamp) {
					listStamps.setSelectedIndex(newRow);
					return;
				}
			}
			
			modelUpdating=true;
			stampLayer.clearSelectedStamps();
            listStamps.setSelectedIndex(newRow);
			stampLayer.toggleSelectedStamp(fs.stamp);
			modelUpdating=false;
			redrawTriggered();
		}
		
		private int lastRow = -1;
		
		private void handleShiftClick(KeyEvent e) {
			if (lastRow==-1) {
				lastRow = listStamps.getSelectionModel().getLeadSelectionIndex();
			}

			if (startSelectionIndex==-1) {
				startSelectionIndex=lastRow;
			}
			
			int newRow;
			if (e.getKeyCode()==KeyEvent.VK_UP) {
				newRow=lastRow-1;
			} else if (e.getKeyCode()==KeyEvent.VK_DOWN) {
				newRow=lastRow+1;
			} else {
				return; 
			}

			FilledStamp fs;
			
			// If we are reducing the number of rows selected
			if ((e.getKeyCode()==KeyEvent.VK_UP && newRow>=startSelectionIndex)
					|| (e.getKeyCode()==KeyEvent.VK_DOWN && newRow<=startSelectionIndex))
			{
				fs = (FilledStamp)listStamps.getModel().getElementAt(lastRow);
			} else {
				if (newRow>=listStamps.getModel().getSize() || newRow<0) { 
					return;
				}

				fs = (FilledStamp)listStamps.getModel().getElementAt(newRow);
			}				
					
			listStamps.setSelectionInterval(startSelectionIndex, newRow);

			if (!othersSelected(fs)) {
				stampLayer.toggleSelectedStamp(fs.stamp);									
			} 	
			lastRow=newRow;
		}
		
	}
	
	private boolean othersSelected(FilledStamp fs) {
		Object[] obj = listStamps.getSelectedValues();
		boolean othersSelected=false;
		for (int i=0; i<obj.length; i++) {
			FilledStamp fso = (FilledStamp)obj[i];
			if (fso==fs) continue;
			if (fso.stamp==fs.stamp) {
				othersSelected=true;
				break;
			}
		}
		
		return othersSelected;
	}
	
	private void clearClipAreas() {
		List<FilledStamp> filledStamps = getFilled();
		
		for (FilledStamp fs : filledStamps) {			
			fs.pdsi.clearCurrentClip();
		}		
		
		parent.clearLastFilled();
	}
	
    public FilledStampFocus(final StampLView parent)
    {
        this.parent = parent;
        stampLayer = parent.stampLayer;
        
		parent.stampLayer.addSelectionListener(this);

        listModel = new DefaultListModel();
        listStamps = new JList(listModel);

        listStamps.addListSelectionListener(
            new ListSelectionListener()
            {
                public void valueChanged(ListSelectionEvent e)
                {
                    if (!e.getValueIsAdjusting() &&
                        !stampListDragging)
                    {
                        enableEverything();
                        if (onlyFillSelected.isSelected())
                            redrawTriggered();
                    }
                }
            }
        );

        listStamps.addKeyListener(new ListKeyListener());
        
        MouseInputAdapter mouseListener = new MouseInputAdapter()
        {
            public void mousePressed(MouseEvent e)
            {
                stampListDragging = false;

                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStartIndex = listStamps.locationToIndex( new Point(e.getX(), e.getY()) );
                }
                
				int clickedRow = listStamps.locationToIndex(e.getPoint());
												
				if (SwingUtilities.isLeftMouseButton(e)) {
					// if this was a double click, pan to the selected stamp.
					if (e.getClickCount() > 1) {
						FilledStamp fs = getFilled(clickedRow);
						if (fs != null) {
							parent.panToStamp(fs.stamp);
						}
					} else {
						FilledStamp fs = getFilled(clickedRow);
						if (fs != null) {					
							StampShape s = fs.stamp;
							
							if (e.isShiftDown()) {
								int lastClicked = startSelectionIndex;
								if (lastClicked<0) {
									lastClicked=startSelectionIndex=clickedRow;
								}
								int start = lastClicked < clickedRow ? lastClicked : clickedRow;
								int end = lastClicked > clickedRow ? lastClicked : clickedRow;
								parent.stampLayer.clearSelectedStamps();

								listStamps.addSelectionInterval(start, end);
								
								for (int i=start; i<=end; i++) {
									parent.stampLayer.addSelectedStamp(getFilled(i).stamp);
								}
							} else if (e.isControlDown()) {
								Object[] obj = listStamps.getSelectedValues();
								boolean othersSelected=false;
								for (int i=0; i<obj.length; i++) {
									FilledStamp fso = (FilledStamp)obj[i];
									if (fso==fs) continue;
									if (fso.stamp==fs.stamp) {
										othersSelected=true;
										break;
									}
								}
								if (!othersSelected) {
									parent.stampLayer.toggleSelectedStamp(s);									
								}
							} else {
								parent.stampLayer.clearSelectedStamps();
								listStamps.setSelectedIndex(clickedRow);
								parent.stampLayer.addSelectedStamp(s);
							}
						}																				
					}
					
					return;
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
                    
                    if (!onlyFillSelected.isSelected())
                    {
                        redraw = true;
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
                        listModel.insertElementAt(listModel.remove(dragStartIndex),
                                                  min);
                        listStamps.setSelectedIndex(min);
                    
                        // Need to handle stamp selection change here since; the
                        // normal selection change code has been disabled during 
                        // dragging due to conflicts.
                        parent.stampLayer.clearSelectedStamps();
						listStamps.setSelectedIndex(min);
                        parent.stampLayer.addSelectedStamp(((FilledStamp)listModel.get(min)).stamp);

                        enableEverything();
                        if (redraw ||
                            onlyFillSelected.isSelected())
                            redrawTriggered();
                    }
                    else if (min == max) {
                        // Need to handle possible selection change here.  The
                        // normal selection change code has been disabled above
                        // whenever dragging has started (to prevent conflicts), 
                        // and it is possible to immediately drag a new selection 
                        // without actually dragging to a new location.
                        
                        parent.stampLayer.clearSelectedStamps();
                        listStamps.setSelectedIndex(min);
                        parent.stampLayer.addSelectedStamp(((FilledStamp)listModel.elementAt(listStamps.getSelectedIndex())).stamp);

                        enableEverything();
                        if (onlyFillSelected.isSelected())
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
        
        listStamps.setCellRenderer(new StampCellRenderer());
        
        
        JScrollPane pnlListStamps = new JScrollPane(listStamps, JScrollPane.  VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
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
                   if (!onlyFillSelected.isSelected())
                   {
                       redraw = true;
                   }
                   
                   // Swap the selection range and the item before it
                   listModel.insertElementAt(listModel.remove(min-1),
                                             max);
                   listStamps.setSelectionInterval(min-1, max-1);
                   
                   clearClipAreas();
                   
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
                   if (!onlyFillSelected.isSelected())
                   {
                       redraw = true;
                   }
                   
                   // Swap the selection range and the item after it
                   listModel.insertElementAt(listModel.remove(max+1),
                                             min);
                   listStamps.setSelectionInterval(min+1, max+1);
                   
                   clearClipAreas();
                   
                   if (redraw)
                       redrawTriggered();
               }
           }
        );
        btnLower.setToolTipText("Move the currently selected stamp(s) DOWN" +
                                " in the filled-stamps list.");
        
        btnTop = new JButton("Top");
        
        btnTop.addActionListener(new ActionListener() {		
            public void actionPerformed(ActionEvent e)
            {
                int min = listStamps.getMinSelectionIndex();
                int max = listStamps.getMaxSelectionIndex();
                if (min == -1  ||  min == 0)
                    return;
                
                modelUpdating=true;
                boolean redraw = false;
                if (!onlyFillSelected.isSelected())
                {
                    redraw = true;
                }
                
                // Move selection range to top of list.
                for (int i=min; i <= max; i++) {
                    FilledStamp fs = (FilledStamp) listModel.remove(max);
                    listModel.insertElementAt(fs, 0);
                }
                listStamps.setSelectionInterval(0, max-min);
                modelUpdating=false;
                
                clearClipAreas();
                
                if (redraw)
                    redrawTriggered();
            }
		});
        
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
                    if (!onlyFillSelected.isSelected())
                    {
                       redraw = true;
                    }
                    
                    // Move selection range to bottom of list.
                    for (int i=min; i <= max; i++)
                        listModel.insertElementAt(listModel.remove(min), listModel.getSize());
                    listStamps.setSelectionInterval(listModel.getSize() - (max-min) - 1, 
                                                    listModel.getSize()-1);
                    
                    clearClipAreas();
                    
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
                    Object[] selected = listStamps.getSelectedValues();
                    for (int i=0; i<selected.length; i++)
                    {
                        listModel.removeElement(selected[i]);
                    }
                    
                    clearClipAreas();
                    
                    enableEverything();
                    
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
                    FilledStamp[] filled = new FilledStamp[listModel.size()];
                    for (int i=0; i < filled.length; i++)
                        filled[i] = (FilledStamp)listModel.get(i);
                    FilledStamp[] sorted = orbitTrackSort(filled);

                    // Check whether the stamp order has changed
                    // as a result of the sort.
                    if (!Arrays.equals(filled, sorted))
                    {
                        // Move stamps in list to match sorted order.
                        for (int i = 0; i < sorted.length; i++)
                            listModel.set(i, sorted[i]);
                        listStamps.clearSelection();    
                        
                        clearClipAreas();
						enableEverything();
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
        
        JPanel pnlPanning = new JPanel(new GridLayout(3, 3));
        pnlPanning.add(btnPanNW);
        pnlPanning.add(btnPanN);
        pnlPanning.add(btnPanNE);
        pnlPanning.add(btnPanW);
        pnlPanning.add(btnPanSize);
        pnlPanning.add(btnPanE);
        pnlPanning.add(btnPanSW);
        pnlPanning.add(btnPanS);
        pnlPanning.add(btnPanSE);
                                
        btnViewPds = new JButton(
             new AbstractAction("View PDS Label")
             {
                 public void actionPerformed(ActionEvent e)
                 {
                     FilledStamp fs = getFilledSingle();
                     JFrame frame = new JFrame(fs.stamp.getId());
                     JTextArea txt = new JTextArea(fs.pdsi.getLabel(), 24, 0);
                     frame.getContentPane().add(new JScrollPane(txt));
                     frame.pack();
                     frame.setVisible(true);
                 }
             }
        );
        
        if (parent.stampLayer.getInstrument().equalsIgnoreCase("themis")) {
        	btnViewPds.setVisible(true);
        } else {
        	btnViewPds.setVisible(false);
        }
        
        btnListCopy = new JButton(
    		new AbstractAction("Copy") {
    			public void actionPerformed(ActionEvent e) {
    				StringBuffer buf = new StringBuffer();
    				for (int i = 0; i < listModel.getSize(); i++) {
    					buf.append( ((FilledStamp)listModel.get(i)).stamp.getId());
    					buf.append(' ');
    				}
    				StringSelection sel = new StringSelection(buf.toString());
    				Clipboard clipboard = ValidClipboard.getValidClipboard();
    				if (clipboard == null) {
    					log.aprintln("no clipboard available");
    				} else {
    					clipboard.setContents(sel, sel);
    					Main.setStatus("Stamp list copied to clipboard");
    					log.println("stamp list copied: " + buf.toString());
    				}
    			}
    		}
        );
		btnListCopy.setToolTipText("Copy stamp IDs to the clipboard");
		
		btnListImport = new JButton(
			new AbstractAction("Import...")
			{
				public void actionPerformed(ActionEvent e)
				{
					new ImportStampsDialog(FilledStampFocus.this).show();
				}
			}
		);
		btnListImport.setToolTipText("Import list of stamps to render from a file, one ID per line");
        
        onlyFillSelected = new JCheckBox("Render selections only");
        onlyFillSelected.addActionListener(
             new ActionListener()
             {
                 public void actionPerformed(ActionEvent e)
                 {
     				stampLayer.getSettings().setRenderSelectedOnly(onlyFillSelected.isSelected());
                	 clearClipAreas();
                     redrawTriggered();
                 }
             }
        );
        
        onlyFillSelected.setSelected(stampLayer.getSettings().renderSelectedOnly());

        
        mapper = new FancyColorMapper();
        mapper.addChangeListener(
             new ChangeListener()
             {
                 public void stateChanged(ChangeEvent e)
                 {
                     if (mapper.isAdjusting())
                         return;
                     getFilledSingle().colors = mapper.getState();
                     parent.clearLastFilled();
                     redrawTriggered();
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
	                                                 
	                 int[] hist=null;;
	                 
	                 try {
	                	 hist = fs.pdsi.getHistogram();
	                 } catch (IOException ioe) {
	                	 ioe.printStackTrace();
	                	 return;
	                 }
	                 
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
        
        JCheckBox hideOutlines = new JCheckBox() {
        	{
        		setText("Hide stamp outlines");
        		addActionListener(new ActionListener() {
        			public void actionPerformed(ActionEvent e) {
        				stampLayer.getSettings().setHideOutlines(isSelected());
        				
        		        parent.drawOutlines();
        		        
        		        StampLView childView = (StampLView)parent.getChild();
        		        if (childView != null) {
            		        childView.drawOutlines();
        		        }
        			}
        		});
        	}
        };
        hideOutlines.setSelected(stampLayer.getSettings().hideOutlines());
        
        resetPan = new JButton("Reset Offset");
        xoffset = new JLabel("X offset: ");
        yoffset = new JLabel("Y offset: ");
        
        resetPan.addActionListener(new ActionListener(){		
			public void actionPerformed(ActionEvent e) {
				List<FilledStamp> filledStamps = getFilledSelections();
				for (FilledStamp fs : filledStamps)
				{
					fs.setOffset(new Point2D.Double(0,0));        					
					fs.saveOffset();
					refreshPanInfo(fs);
				}

                clearClipAreas();
                
				redrawTriggered();
			}
        });
        
        resetPan.setToolTipText("Reset this stamp to the original, unnudged position");
        
        int pad = 4;
        Insets in = new Insets(pad,pad,pad,pad);
        
        Box row1 = Box.createHorizontalBox();
        row1.add(btnListImport);
        row1.add(Box.createHorizontalStrut(pad));
        row1.add(btnListCopy);
        row1.add(Box.createHorizontalStrut(pad));
        row1.add(btnSort);
        
        Box row2 = Box.createHorizontalBox();
        row2.add(hideOutlines);
        row2.add(Box.createHorizontalStrut(pad));
        row2.add(onlyFillSelected);
        
        JPanel listPanel = new JPanel(new GridBagLayout());
        listPanel.setBorder(new TitledBorder("Rendered Stamps"));
        int row = 0;
        listPanel.add(row1, new GridBagConstraints(0,row,2,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
        row++;
        listPanel.add(row2, new GridBagConstraints(0,row,2,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
        row++;
        listPanel.add(pnlListStamps, new GridBagConstraints(0,row,1,5,1,1,GridBagConstraints.NORTHWEST,GridBagConstraints.BOTH,in,pad,pad));
        listPanel.add(btnDelete, new GridBagConstraints(1,row,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
        row++;
        listPanel.add(btnTop, new GridBagConstraints(1,row,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
        row++;
        listPanel.add(btnRaise, new GridBagConstraints(1,row,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
        row++;
        listPanel.add(btnLower, new GridBagConstraints(1,row,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
        row++;
        listPanel.add(btnBottom, new GridBagConstraints(1,row,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
        row++;
        
        JPanel col1 = new JPanel(new GridBagLayout());
        col1.add(pnlPanning, new GridBagConstraints(0,0,1,1,1,1,GridBagConstraints.NORTHWEST,GridBagConstraints.BOTH,in,pad,pad));
        
        JPanel col2 = new JPanel(new GridBagLayout());
        col2.add(xoffset, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,pad,pad));
        col2.add(yoffset, new GridBagConstraints(0,1,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,pad,pad));
        col2.add(resetPan, new GridBagConstraints(0,2,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.NONE,in,pad,pad));
                
        Box h = Box.createHorizontalBox();
        h.add(col1);
        h.add(Box.createHorizontalStrut(pad));
        h.add(col2);
        h.add(Box.createHorizontalGlue());
        
        JPanel selPanel = new JPanel(new GridBagLayout());
        selPanel.setBorder(new TitledBorder("Selected Stamps"));
        selPanel.add(h, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
        selPanel.add(mapper, new GridBagConstraints(0,1,1,1,1,0,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,in,pad,pad));
        
        // Assemble everything together
        setLayout(new GridBagLayout());
        add(listPanel, new GridBagConstraints(0,0,1,1,1,1,GridBagConstraints.NORTHWEST,GridBagConstraints.BOTH,in,pad,pad));
        add(selPanel, new GridBagConstraints(0,1,1,1,1,0,GridBagConstraints.NORTHWEST,GridBagConstraints.BOTH,in,pad,pad));
        
        // Set proper state
        enableEverything();
    }

	/**
	 * Creates a sorted list from the specified stamps. The sort order is
	 * according to each stamp's equator intercept location, i.e., the point at
	 * which the stamp's corresponding orbit track would intercept the equator.
	 * Stamps are ordered from west-to-east intercept longitude.
	 * 
	 * @param unsorted
	 *            List of unsorted stamps
	 */
    protected FilledStamp[] orbitTrackSort(FilledStamp[] unsorted) {
    	if (unsorted == null)
    		return null;

    	// Compute equator intercept longitude for each stamp.
    	final Map<FilledStamp, Double> equatorLon = new HashMap<FilledStamp, Double>();
    	for (FilledStamp s : unsorted) {
    		HVector nw = new HVector(s.stamp.getNW());
    		HVector sw = new HVector(s.stamp.getSW());
    		HVector npole = new HVector(0, 0, 1);
    		HVector orbitPlane = nw.cross(sw);
    		HVector npoleCross = orbitPlane.cross(npole);
    		equatorLon.put(s, new Double(npoleCross.lonE()));
    	}

    	FilledStamp[] sorted = (FilledStamp[]) unsorted.clone();
    	Arrays.sort(sorted, new Comparator<FilledStamp>() {
    		public int compare(FilledStamp a, FilledStamp b) {
    			double diff = equatorLon.get(a).doubleValue() - equatorLon.get(b).doubleValue();
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
     ** Returns list of filled stamp states for saving session settings
     **/
    public FilledStamp.State[] getStampStateList()
    {
    	List<FilledStamp> filledStamps = getFilled();
    	
        FilledStamp.State[] stateList = new FilledStamp.State[filledStamps.size()];
        
        int cnt=0;
        for (FilledStamp fs : filledStamps) {
           stateList[cnt++] = fs.getState();
        }
        
        return stateList;
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
        
        btnViewPds.setEnabled(singleSelected);
        
        if (listModel.getSize() > 0)
            btnListCopy.setEnabled(true);
        else
            btnListCopy.setEnabled(false);
        
        FilledStamp fs = getFilledSingle();
        
        mapper.setEnabled(singleSelected  &&
                          fs != null);

        resetPan.setEnabled(anySelected);
        
        if (fs != null) {
            log.println("Implementing newly-selected " + fs.stamp);
            
            refreshPanInfo(fs);

            mapper.setState(fs.colors);
        } else {
        	xoffset.setText("X offset: 0");
        	yoffset.setText("Y offset: 0");
        }

    }
    
    private boolean isContiguous(int[] values)
    {
        return  values.length != 0
                &&  values[values.length-1] == values[0] + values.length-1;
    }
    
    private void refreshPanInfo(FilledStamp fs) {
        String fmt = "{0} offset: {1,number,#.####}";
        xoffset.setText(MessageFormat.format(fmt, "X", fs.getOffset().getX()));
        yoffset.setText(MessageFormat.format(fmt, "Y", fs.getOffset().getY()));    	
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
                log.aprintln("Unable to load dir " + dir);
            }
            
            setAction(
            		new AbstractAction(null, dirIcon)
            		{
            			public void actionPerformed(ActionEvent e)
            			{
            				Point2D worldPan = getWorldPan(x * panSize,
            						y * panSize);
            				List<FilledStamp> filledStamps = getFilledSelections();
            				for (FilledStamp fs : filledStamps)
            				{
            					Point2D oldOffset = fs.getOffset();
            					fs.setOffset(new Point2D.Double(oldOffset.getX() + worldPan.getX(),
            														oldOffset.getY() + worldPan.getY()));
            					
            					fs.saveOffset();
            					
            					refreshPanInfo(fs);
            				}

            				clearClipAreas();
            				
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
    
    public List<FilledStamp> getFilledSelections()
    {
        ArrayList<FilledStamp> filledSelected = new ArrayList<FilledStamp>();

        for (Object obj : listStamps.getSelectedValues()) {
        	filledSelected.add((FilledStamp)obj);
        }
        
        return  filledSelected;
    }
    
    private FilledStamp getFilledSingle()
    {
        Object[] selected = listStamps.getSelectedValues();
        if (selected.length != 1)
            return  null;
        return (FilledStamp)selected[0];
    }
    
    protected FilledStamp getFilled(int n)
    {
        return  (FilledStamp) listModel.get(n);
    }
    
    public List<FilledStamp> getFilled() {
    	ArrayList<FilledStamp> filledStamps = new ArrayList<FilledStamp>();
    	    	
    	Enumeration stamps = listModel.elements();

    	while (stamps.hasMoreElements()) {
    		FilledStamp fs = (FilledStamp) stamps.nextElement();
    		filledStamps.add(fs);
    	}
  
    	return filledStamps;
    }
    
    /**
     ** @param state optional position offset and color map state settings
     ** used to restore FilledStamp state; may be null.
     **/
    protected FilledStamp getFilled(StampShape s, FilledStamp.State state, String type)
    {
    	if (type==null && state!=null) {
    		type = state.getImagetype();
    	}
    	
        StampImage pdsi = StampImageFactory.load(s, stampLayer.getSettings().getInstrument(), type);
            
        if (pdsi == null)
           return  null;
            
        return new FilledStamp(s, pdsi, state);
    }
    
    public void addStamp(StampShape s, String type)
    {
        addStamp(s, null, true, false, false, type);
    }
        
    /**
     *  @param state optional position offset and color map state settings
     *  used to restore FilledStamp state; may be null.
     *
     *  @param redraw       render/draw any image that has been selected.
     *
     */
    protected void addStamp(final StampShape s, FilledStamp.State state, boolean redraw, 
                            boolean ignoreAlreadyFilled, boolean ignoreNotLoaded, String type)
    {
    	Enumeration stamps = listModel.elements();
    
    	while (stamps.hasMoreElements()) {
    		FilledStamp stamp = (FilledStamp)stamps.nextElement();
    		if (stamp.stamp==s && stamp.pdsi.getImageType().equalsIgnoreCase(type)) {
                if (!ignoreAlreadyFilled)
                    JOptionPane.showMessageDialog(this,
                                                  "Already in filled-stamp list: " + s,
                                                  "OPERATION IGNORED",
                                                  JOptionPane.WARNING_MESSAGE);
                return;        			
    		}
    	}

    	if (type==null && stampLayer.getInstrument().equalsIgnoreCase("THEMIS")) {
    		if (s.getId().startsWith("I")) type="BTR"; else type="ABR";
    	}
    	
        FilledStamp fs = getFilled(s, state, type);
        
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
                       
        listModel.insertElementAt(fs, 0);

        listStamps.addSelectionInterval(0, 0);
        
        if (!stampLayer.isSelected(s)) {
        	stampLayer.addSelectedStamp(s);
        }
        
        ChartView myChart = parent.myFocus.chartView;
        if (myChart!=null) {
        	myChart.mapChanged();
        }
        
        enableEverything();
        
        if (redraw) {
        	redrawTriggered();
        }
    }
    
    /**
     * Adds any stamps from file that are found in the associated
     * layer for this panel's stamp view.  File must contain
     * list of stamp IDs delimited by whitespace (includes newlines).
     */
    protected void addStamps(File file)
    {
        try {
            if (file != null)
            {
               BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
               
               // Build list of stamps from file that are present in this
               // panel's stamp view's layer.
               ArrayList<StampShape> stampList = new ArrayList<StampShape>();
               String line = reader.readLine();
               while (line != null) {
                   StringTokenizer tokenizer = new StringTokenizer(line);
                   while (tokenizer.hasMoreTokens()) {
                       String stampID = tokenizer.nextToken();
                       StampShape stamp = stampLayer.getStamp(stampID);
                       
                       if (stamp != null) {
                           log.println("adding stamp " + stampID);
                           stampList.add(stamp);
                       }
                   }
                   
                   line = reader.readLine();
               }
               
               // Add stamps to rendered list as a group (improves user
               // experience during the loading, projecting, ... phases).
               StampShape[] stampsToAdd = (StampShape[]) stampList.toArray(new StampShape[0]);
               addStamps(stampsToAdd, null, null);
               // do we need to trigger StampLView.drawStampsTogether?
            }
        }
        catch (Exception e) {
            log.aprintln(e);
        }
    }
       
    /**
     ** Given a user-requested pan in pixels, should return the actual
     ** pan in world coordinates.
     **/
	protected Point2D getWorldPan(int px, int py) {
		MultiProjection proj = parent.viewman.getProj();
		if (proj == null) {
			log.aprintln("null projection");
			return null;
		}
		
		Dimension2D pixelSize = proj.getPixelSize();
		if (pixelSize == null) {
			log.aprintln("no pixel size");
			return null;
		}
		
		return  new Point2D.Double(px * pixelSize.getWidth(),
		                           py * pixelSize.getHeight());
	}
        
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
    	if (modelUpdating) {
    		return;
    	} 
    	
        parent.redrawEverything(true);
    }
        
    public Dimension getMinimumSize()
    {
        return  getPreferredSize();
    }
        
    public void addStamps(final StampShape stampsToAdd[], FilledStamp.State states[], String types[])
		{
    		// Do some data sanitizing on the input parameters first, to keep us out of trouble later
    		// We perform this logic here to hopefully save ourselves having to do this everywhere else.
    	
    		if (states==null) {
    			states = new FilledStamp.State[stampsToAdd.length];
    		}
    		
    		if (types==null) {
    			types = new String[stampsToAdd.length];
    		}
    	
    	
    	
    	
			Enumeration stamps = listModel.elements();
						
			while (stamps.hasMoreElements()) {
				FilledStamp stamp = (FilledStamp)stamps.nextElement();
				
				for (int i=0; i<stampsToAdd.length; i++) {
					if (stamp.stamp==stampsToAdd[i] && stamp.pdsi.getImageType().equalsIgnoreCase(types[i])) {
						// Already in the stamp list, skip to the next one
						stampsToAdd[i]=null; // Don't add this one again;
						continue;        			
					}				
				}
			}
	
			
			int cnt=0;
			ArrayList<StampShape> addedStamps = new ArrayList<StampShape>();
	
			modelUpdating=true;
			for (int i=0; i<stampsToAdd.length; i++) {	
				if (stampsToAdd[i]==null) { 
					// This can happen when a user selects a group of THEMIS IR+VIS images and then 
					// chooses 'Render as BTR' or 'Render as ABR'
					continue;  
				}
				
			
				if (types[i]==null) {
					if (stampLayer.getInstrument().equalsIgnoreCase("THEMIS")) {
						if (stampsToAdd[i].getId().startsWith("I"))  {
							types[i]="BTR"; }
						else {
							types[i]="ABR";
						}
					} else {
						types[i]=stampLayer.getInstrument().toUpperCase();
					}
				}
			
				FilledStamp fs = getFilled(stampsToAdd[i], states[i], types[i]);
				
				if (fs == null || fs.pdsi == null)
				{
	//				JOptionPane.showMessageDialog(this,
	//				                              "Unable to load " + stampsToAdd[i],
	//				                              "PDS LOAD ERROR",
	//				                              JOptionPane.ERROR_MESSAGE);
					continue;
				}
						       
				listModel.insertElementAt(fs, 0);
				cnt++;
			}			
			
			if (cnt>0) {
				listStamps.addSelectionInterval(0, cnt-1);
			}
				
			stampLayer.addSelectedStamps(addedStamps);
					
			modelUpdating=false;
			
	        ChartView myChart = parent.myFocus.chartView;
	        if (myChart!=null) {
	        	myChart.mapChanged();
	        }
			
			enableEverything();		
		}


	class StampCellRenderer extends JPanel implements ListCellRenderer
    {
    	JLabel label;
    	
    	public StampCellRenderer() {
    		setLayout(new BorderLayout());
    		label = new JLabel("");
    		add(label, "Center");
    	}
    	
        public Component getListCellRendererComponent(JList list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus)
        {
            String s = value.toString();
            label.setText(s);
            
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
	
	public void dispose() {
        listModel.clear();
	}
}


