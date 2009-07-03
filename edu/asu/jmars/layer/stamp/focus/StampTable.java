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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.stamp.StampLView;
import edu.asu.jmars.layer.stamp.StampLayer;
import edu.asu.jmars.layer.stamp.StampShape;
import edu.asu.jmars.layer.stamp.StampLayer.StampSelectionListener;
import edu.asu.jmars.layer.stamp.functions.RenderFunction;
import edu.asu.jmars.swing.STable;
import edu.asu.jmars.swing.ValidClipboard;
import edu.asu.jmars.util.stable.FilteringColumnModel;
import edu.asu.jmars.util.stable.Sorter;

/**
 **  The unfilled stamp table.
 **
 **  This table allows for sorting (either increasing or decreasing) on any columns in
 **  the table.  There may be a primary sort or a primary/secondary sort.
 **   
 **  Right-clicking on a row in the table allows users to render the stamp corresponding
 **  to the table row or browse the stamp's webpage. Left-clicking selects a row and highlights
 **  the stamps outline in the viewing windows.  A double left-click lightlights the outline and 
 **  centers the viewing windows about the stamp.
 **/
public class StampTable extends STable implements StampSelectionListener
{
	public void selectionsChanged() {
        List<StampShape> selectedStamps = myLView.stampLayer.getSelectedStamps();
        
        clearSelection();
        
    	toggleSelectedStamps(selectedStamps);
	}
	
	public void selectionsAdded(java.util.List<StampShape> newStamps) {
		setAutoscrolls(false);

		for (StampShape newStamp : newStamps) {
			int row = getRow(newStamp);
			if (row<0) continue;  // it's possible that this stamp isn't currently displayed
			row = getSorter().sortRow(row);
			
			if(isRowSelected(row)) {
				continue;
			} else {
				// Toggle the row
				changeSelection(row, 0, true, false);
			}
		}
		
		setAutoscrolls(true);
	}

	StampLView myLView;

	StampTableModel tableModel;
    
    public StampTableModel getTableModel() {
    	if (tableModel==null) {
    		tableModel=new StampTableModel();
    	}
    	return tableModel;
    }

	
	public StampTable(StampLView newView)
	{
		super();
		
		myLView = newView;
		
		setUnsortedTableModel(getTableModel());
		
		ToolTipManager.sharedInstance().registerComponent( this );
		
		setAutoResizeMode(STable.AUTO_RESIZE_OFF);
		setColumnSelectionAllowed(false);
		setPreferredScrollableViewportSize(new Dimension(getPreferredSize().width,400));

		addMouseListener( new TableMouseAdapter());
		addKeyListener(new TableKeyListener());

		myLView.stampLayer.addSelectionListener(this);
	}
	
	
	/**
	 ** returns the stamp that corresponds to the specified row.
	 ** returns null if the stamp cannot be found.
	 **
	 ** @param row - the table row whose stamp is to be fetched.
	 **/
	public StampShape getStamp(int row){
		StampTableModel model = (StampTableModel)getUnsortedTableModel();

		return (StampShape)model.getValueAt(row);				
	}
		
	public int getRow( StampShape stamp){
		StampTableModel model = (StampTableModel)getUnsortedTableModel();
		
		return model.getRow(stamp);
	}
		
	
    public void updateData(Class[] newTypes, String[] newNames, String[] initialCols) {
    	StampTableModel model = getTableModel();
    	
    	boolean firstUpdate = model.isFirstUpdate();
    	
    	model.updateData(newTypes, newNames);
		
		if (firstUpdate) {
			// first time through, set visibility of columns
			model.fireTableStructureChanged();
			FilteringColumnModel colModel = (FilteringColumnModel) getColumnModel();
			Set<String> displayCols = new HashSet<String>(Arrays.asList(initialCols));
			for (TableColumn tc: new ArrayList<TableColumn>(colModel.getAllColumns())) {
				colModel.setVisible(tc, displayCols.contains(tc.getIdentifier()));
			}
		} else {
			// all other updates result in a table change only
			model.fireTableDataChanged();
		}        	
    }
	
	/**
	 ** specifies the tooltip to be displayed whenever the cursor
	 ** halts over a cell.  This is called because the table's panel
	 ** is registered with the tooltip manager in the constructor.
	 **/
	public String getToolTipText(MouseEvent e){
		Point p = e.getPoint();
		int col = columnAtPoint(p);
		int row = rowAtPoint(p);
		if (col == -1  ||  row == -1) {
			return  null;
		}
		String name = getColumnName(col);
		Object value = getValueAt(row, col);
		if (value == null)
			value = "-NULL-";
		return  name + " = " + value;
	}
	

	/**
	 ** The cell renderer for time columns. This is set up in the
	 ** constructor.
	 **/
	class TimeRenderer extends DefaultTableCellRenderer 
    {
		DecimalFormat formatter = new DecimalFormat("#");
        
		public TimeRenderer() { 
			super(); 
		}
        
		public void setValue(Object value) {
			setHorizontalAlignment(JLabel.RIGHT);
			if ( value == null || ((Double)value).isNaN() ) {
				setText( "NaN");
			} else {
				setText( formatter.format(value));
			}
		}
	}

	/**
	 ** updates the stamp table with contents of the stamp array. The 
	 ** length and order of the rows may not be the same after the update,
	 ** but this method maintains the selected rows.
	 **/
	void dataRefreshed() 
	{
		if (myLView.stamps==null){
			return;
		}

		// don't do any of this if we are dealing with the panner view.
		if (myLView.getChild()==null){
			return;
		}

		// rebuild the table.

		final StampTableModel model = (StampTableModel)getUnsortedTableModel();


		final List<Vector> newRows = new ArrayList<Vector>();

		int numCols = model.getColumnCount();
		for (StampShape stampShape : myLView.stamps) {
			Object data[] = stampShape.getStamp().getData();
			Vector v = new Vector(numCols + 1);			        
			for (int i=0; i < data.length; i++) {
				v.addElement(data[i]);
			}			        
			v.addElement(stampShape);										
			newRows.add(v);
		}

		Runnable updateTable = new Runnable() {
			public void run() {
				model.removeAll();
				model.addRows(newRows);
				
				List<StampShape> selectedStamps = myLView.stampLayer.getSelectedStamps();

				// reselect any stamps that were selected before.
				if (selectedStamps != null){
					// Improve re-selection performance by temporarily disabling
					// the auto-scrolling and list selection changes behavior.
					setAutoscrolls(false);

					for (StampShape stamp : selectedStamps) {
						int row = getRow( stamp );
						if (row!=-1) {  
							row=getSorter().sortRow(row);
							if (row != -1){
								getSelectionModel().addSelectionInterval(row, row);
							}
						}
					}

					setAutoscrolls(true);
				}
				
				newRows.clear();
			}
		};

		try {
			if (SwingUtilities.isEventDispatchThread()) {
				updateTable.run();
			} else {
				SwingUtilities.invokeAndWait(updateTable);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 ** Called when the view has a selection status toggled, updates
	 ** the selection list (which cascades to update the view through
	 ** {@link #setSelectedStamps}).
	 **/
	void toggleSelectedStamps(List<StampShape> toggledStamps) {
		if (toggledStamps.size()<1)
			return;
		
		setAutoscrolls(false);
		
		int row = -1;
		
		for (StampShape toggledStamp : toggledStamps) {					
			row = getRow(toggledStamp);
			if (row == -1) {
				continue;
			}
			row = getSorter().sortRow(row);
			
			// Toggle the row
			changeSelection(row, 0, true, false);
		}
		
		setAutoscrolls(true);
		
		// Scroll to make the toggled row visible for the last stamp in the list.
		if (row >= 0) {
			Rectangle r = getCellRect(row, 0, false).union(
								       getCellRect(row, getColumnCount()-1, false));
			int extra = Math.min(r.height * 3, getHeight() / 4);
			r.y -= extra;
			r.height += 2 * extra;
			scrollRectToVisible(r);
		}				
	}			
	
	// listens for mouse events in the unfilled stamp table.  If a single left-click,
	// the stamp outline in the viewer is hilighted.  If a double left-click, the stamp
	// is hilighted AND the viewers center about the stamp.  If a right-click, the 
	// render/browse popup is brought forth.
	protected class TableMouseAdapter extends MouseAdapter {
		public void mousePressed(MouseEvent e){
			synchronized (StampTable.this) {
				// get the indexes of the sorted rows. 
				final int[] rowSelections = getSelectedRows();
				if (rowSelections==null || rowSelections.length==0) {
					return;
				}
				
				// if this was a left click, pan to the selected stamp.
				if (SwingUtilities.isLeftMouseButton(e)){
					if (e.getClickCount() > 1) {
						int row = rowSelections[0];
						StampShape s = getStamp(getSorter().unsortRow(row));
						
						if (s != null){
							myLView.panToStamp( s);
						}
						e.consume();
						return;
					} else {
						Sorter sorter = getSorter();
						
						int row = rowAtPoint(e.getPoint());
						
						StampShape s = getStamp(sorter.unsortRow(row));
												
						if (e.isShiftDown()) {
							int lastClicked = startSelectionIndex;
							if (lastClicked<0) {
								// We need to use the sorted row (displayed to the user) index
								// so we get the range of rows the user sees as sequential
								lastClicked=startSelectionIndex=row;
							}
							int start = lastClicked < row ? lastClicked : row;
							int end = lastClicked > row ? lastClicked : row;
							myLView.stampLayer.clearSelectedStamps();
							ArrayList<StampShape> selectedStamps=new ArrayList<StampShape>();
							for (int i=start; i<=end; i++) {
								selectedStamps.add(getStamp(sorter.unsortRow(i)));
							}
							
							myLView.stampLayer.addSelectedStamps(selectedStamps);
						} else if (e.isControlDown()) {
							myLView.stampLayer.toggleSelectedStamp(s);
						} else {
							myLView.stampLayer.clearSelectedStamps();
							myLView.stampLayer.addSelectedStamp(s);
						}																			
					}
					return;
				} 

				// if this was a right click, bring up the render/browse popup
				JPopupMenu menu = new JPopupMenu();
				if (rowSelections.length == 1) 
                {
					final int row = getSorter().unsortRow(rowSelections[0]);
					if (row < 0) {
						return;
					}
					
                    //
					StampShape stamp = myLView.stamps[row];
					List<String> supportedTypes = stamp.getSupportedTypes();
					
					for (String supportedType : supportedTypes) {
						JMenuItem renderMenu = new JMenuItem("Render " + stamp.getId() + " as " + supportedType);
						final String type = supportedType;
						renderMenu.addActionListener(new ActionListener() {			
							public void actionPerformed(ActionEvent e) {
							    Runnable runme = new Runnable() {
							        public void run() {
							        	myLView.focusFilled.addStamp(myLView.stamps[row], type);
							        }
							    };
			
						        SwingUtilities.invokeLater(runme);
							}
						});
						menu.add(renderMenu);
					}
					
					JMenuItem webBrowse = new JMenuItem("Web browse " + myLView.stamps[row].getId());
					webBrowse.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							myLView.browse(myLView.stamps[row]);
						}
					});
					menu.add(webBrowse);
				} 
				else {
					JMenu loadSelected = new JMenu("Render Selected Stamps");
					
					//
					final List<StampShape> selectedStamps = myLView.stampLayer.getSelectedStamps();
					
					List<String> imageTypes = new ArrayList<String>();

					boolean btr=false;
					boolean abr=false;
					
					try {
						String idList="";
						for (StampShape stamp : selectedStamps) {
							idList+=stamp.getId()+",";
						}
						
						if (idList.endsWith(",")) {
							idList=idList.substring(0,idList.length()-1);
						}

						String typeLookupStr = StampLayer.stampURL+"ImageTypeLookup";
								
						String data = "id="+idList+"&instrument="+myLView.stampLayer.getInstrument()+"&format=JAVA"+myLView.stampLayer.getAuthString()+StampLayer.versionStr;
						
						URL url = new URL(typeLookupStr);
						URLConnection conn = url.openConnection();
						conn.setDoOutput(true);
						OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
						wr.write(data);
						wr.flush();
						wr.close();
						
						ObjectInputStream ois = new ObjectInputStream(conn.getInputStream());
																	
						List<String> supportedTypes = (List<String>)ois.readObject();
						for (String type : supportedTypes) {
							if (type.equalsIgnoreCase("BTR")) {
								btr=true;
							} else if (type.equalsIgnoreCase("ABR")) {
								abr=true;
							} 
							
							imageTypes.add(type);								
						}
						ois.close();
					} catch (Exception e2) {
						e2.printStackTrace();
					}

					
					if (abr && btr) {
						imageTypes.add(0, "ABR / BTR");
					}
					
					for (final String imageType : imageTypes) {
						JMenuItem renderMenu = new JMenuItem("Render Selected as " + imageType);
						renderMenu.addActionListener(new RenderFunction(myLView.stampLayer, myLView.focusFilled, imageType));
						loadSelected.add(renderMenu);
					}
											
					//
					
					menu.add(loadSelected);
				}

				// Add copy-selected-stamp-IDs menu item.
				if (rowSelections != null && rowSelections.length > 0) {
					JMenuItem copyToClipBoard = new JMenuItem("Copy Selected Stamp List to Clipboard");
					copyToClipBoard.addActionListener(new ActionListener() {						
						public void actionPerformed(ActionEvent e) {
							StringBuffer buf = new StringBuffer();
							for (int i = 0; i < rowSelections.length; i++) {
								buf.append( getStamp(getSorter().unsortRow(rowSelections[i])).getId() );
								buf.append(' ');
							}
				           
							StringSelection sel = new StringSelection(buf.toString());
							Clipboard clipboard = ValidClipboard.getValidClipboard();
							if (clipboard == null) {
								Main.setStatus("no clipboard available");
							}
							else {
								clipboard.setContents(sel, sel);
								Main.setStatus("Stamp list copied to clipboard");
							}
						}						
					});
					menu.add(copyToClipBoard);
				}

				menu.show(e.getComponent(), e.getX(), e.getY());
			} 
		}// end: mousePressed()				
	};// end: class TableMouseAdapter

	int startSelectionIndex=-1;
	
	protected class TableKeyListener extends KeyAdapter {

		public void keyReleased(KeyEvent e) {
			if (e.isShiftDown()==false) {
				startSelectionIndex=-1;
			}
		}

		public void keyPressed(KeyEvent e) {
			e.consume();
			
			int lastClicked = getSelectionModel().getLeadSelectionIndex();
			
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
			
			if (e.isShiftDown()) {
				if ((e.getKeyCode()==KeyEvent.VK_UP && newRow>=startSelectionIndex)
						|| (e.getKeyCode()==KeyEvent.VK_DOWN && newRow<=startSelectionIndex))
			{
					StampShape s = getStamp(getSorter().unsortRow(lastClicked));
					myLView.stampLayer.toggleSelectedStamp(s);
					return;
			}
			}
			
			if (newRow>=getRowCount() || newRow<0) { 
				return;
			}
			
			StampShape s = getStamp(getSorter().unsortRow(newRow));

			if (e.isShiftDown()) {
				myLView.stampLayer.toggleSelectedStamp(s);
			} else {
				myLView.stampLayer.clearSelectedStamps();
				myLView.stampLayer.toggleSelectedStamp(s);
			}
		}
	}

	
}

