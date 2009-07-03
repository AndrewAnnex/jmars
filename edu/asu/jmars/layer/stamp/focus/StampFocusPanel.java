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


/**
 * 
 */
package edu.asu.jmars.layer.stamp.focus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.stamp.AddLayerWrapper;
import edu.asu.jmars.layer.stamp.FilledStamp;
import edu.asu.jmars.layer.stamp.StampLView;
import edu.asu.jmars.layer.stamp.StampLayer;
import edu.asu.jmars.layer.stamp.StampLayerSettings;
import edu.asu.jmars.layer.stamp.StampShape;
import edu.asu.jmars.layer.stamp.FilledStamp.State;
import edu.asu.jmars.layer.stamp.StampLayer.StampSelectionListener;
import edu.asu.jmars.layer.stamp.chart.ChartView;
import edu.asu.jmars.layer.stamp.functions.RenderFunction;
import edu.asu.jmars.swing.ColorCombo;
import edu.asu.jmars.swing.MultiLabel;
import edu.asu.jmars.swing.PasteField;
import edu.asu.jmars.swing.STable;
import edu.asu.jmars.swing.ValidClipboard;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.Util;
import edu.asu.jmars.util.stable.FilteringColumnModel;

public class StampFocusPanel extends FocusPanel	{
	public StampTable     table;
	
	// Chart view attached to the main view.
	public ChartView chartView;	
	OutlineFocusPanel outlinePanel;
	
	public StampFocusPanel(StampLView stampLView, AddLayerWrapper wrapper) {
		super(stampLView);

		StampLayer stampLayer = stampLView.stampLayer; 
		
		table = new StampTable(stampLView);
		
		setLayout(new GridLayout(1,1));
		JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
        
		outlinePanel = new OutlineFocusPanel(stampLayer, table);
		tabs.add("Outlines", outlinePanel);
		
		tabs.add("Filters", new FilterFocusPanel(stampLayer, wrapper));
								
		JScrollPane renderSP = new JScrollPane(stampLView.focusFilled);
		renderSP.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		renderSP.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		tabs.add("Rendered", renderSP);
        
		tabs.add("Settings", new SettingsFocusPanel(stampLayer.getSettings(), stampLView));
        tabs.add("Query", new QueryFocusPanel(wrapper, stampLayer));
        
        if (stampLayer.getInstrument().equalsIgnoreCase("THEMIS")) {
        	chartView = new ChartView(stampLView);  		
        	tabs.add("Chart", chartView);
        }
        
		add(tabs, BorderLayout.CENTER);

		// add initial rows to the table
		table.dataRefreshed();
	}

	public void dispose() {
		table.getTableModel().removeAll();		
	}
	
	public ChartView getChartView() {
		return chartView;
	}
	
	public int[] getSelectedRows() {
		return table.getSelectedRows();
	}
			
	public void dataRefreshed() {
		table.dataRefreshed();
		outlinePanel.dataRefreshed();
	}
		      
    public void updateData(Class[] newTypes, String[] newNames, String[] initialCols) {
    	table.updateData(newTypes, newNames, initialCols);
    }
}
