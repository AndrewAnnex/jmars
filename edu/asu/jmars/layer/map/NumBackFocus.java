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


package edu.asu.jmars.layer.map;

import edu.asu.jmars.layer.*;
import edu.asu.jmars.Main;
import edu.asu.jmars.swing.*;
import edu.asu.jmars.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class NumBackFocus extends FocusPanel implements TableModelListener, ListSelectionListener
{

	public class ColorCell extends ColorCombo implements TableCellRenderer 
	{
		public Component getTableCellRendererComponent(
								JTable table, Object color,
								boolean isSelected, boolean hasFocus,
								int row, int column)
		{
			setColor((Color)color);
			return this;
		}
	}

	private NumTableModel tableModel;
	private JTable table;
	private static NumberFormat nf = NumberFormat.getInstance();
	private static DebugLog log = DebugLog.instance();
	private JScrollPane scrollpane;

	public static String[] colorString={"red","black","blue","cyan","darkGray","gray","green","lightGray","magenta","orange","pink","yellow"};

	protected LinkedList colorList = new LinkedList();
	protected LinkedList nameList = new LinkedList();
	protected LinkedList valueList = new LinkedList();
	protected int lastSelection;

	protected JButton add,delete,clear; /* Top Row */
	protected ThemisGrapher grapher;
	public 		NumBackLView derivedParent;

	public static Color stringToColor(String c)
	{

		if (c.equalsIgnoreCase("black"))
			return(Color.black);
		else if (c.equalsIgnoreCase("blue"))
			return(Color.blue);
		else if (c.equalsIgnoreCase("cyan"))
			return(Color.cyan);
		else if (c.equalsIgnoreCase("darkGray"))
			return(Color.darkGray);
		else if (c.equalsIgnoreCase("gray"))
			return(Color.gray);
		else if (c.equalsIgnoreCase("green"))
			return(Color.green);
		else if (c.equalsIgnoreCase("lightGray"))
			return(Color.lightGray);
		else if (c.equalsIgnoreCase("magenta"))
			return(Color.magenta);
		else if (c.equalsIgnoreCase("orange"))
			return(Color.orange);
		else if (c.equalsIgnoreCase("pink"))
			return(Color.pink);
		else if (c.equalsIgnoreCase("red"))
			return(Color.red);
		else if (c.equalsIgnoreCase("white"))
			return(Color.white);
		else if (c.equalsIgnoreCase("yellow"))
			return(Color.yellow);

		return(Color.black);
	}


	public NumBackFocus()
	{
		this((Layer.LView)null);
	}

	
	public NumBackFocus(Layer.LView parent)
	{
		super(parent); 
		tableModel = new NumTableModel();
//		tableModel.addTableModelListener(this);
		table = new JTable(tableModel);
		table.setDefaultRenderer(Color.class, new ColorCell());
		table.setColumnSelectionAllowed(false);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(this);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		table.setShowGrid(true);
		table.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new ColorCell()));

		derivedParent = (NumBackLView) parent;

		initUI();
		initEvents();

      nf.setMaximumIntegerDigits(12);
      nf.setMaximumFractionDigits(6);
      nf.setMinimumFractionDigits(0);
      nf.setGroupingUsed(false);


	}

	public int getSelectedRow()
	{
		return(table.getSelectedRow());
	}

	public Color getColor(int idx)
	{

		Color c;

		if (idx >= colorList.size())
			return(Color.black);

		c=(Color)colorList.get(idx);

/*

		else 
			c = stringToColor((String)colorList.get(idx));

		if (c == null) {
			log.println("C was null!");
			return(Color.black);
		}

*/
		return(c);
	}

/*
** Here we'll set up the listen/callback functionality for all the buttons, the canvas 
** and the table.
*/


	void initEvents()
	{ 

		add.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {

				NumBackPropertiesDiag nbpd=
							new NumBackPropertiesDiag(new JFrame(),true);

				if (nbpd.Canceled())
					return;

				NumBackDialogParameterBlock  pars = 
							(NumBackDialogParameterBlock)nbpd.getParameters();
/*
** Now we need to propagate the add event:
**		focus --> lview --> layer --> qplus				
*/
				derivedParent.addDataSet(pars);
				//addElement(new Color[] { pars.color }, pars.name, new Object[] { null });

				tableModel.tableUpdate();
			}
		});


		delete.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {

				int r = table.getSelectedRow();

				if (r < 0) { //Nothing selected!
					log.println("Hey! Just what exactly am I supposed to delete?");
					return;
				}

				deleteElement(r);
				derivedParent.deleteDataSet(r);
				grapher.delete(r);
				if(valueList.isEmpty())
				 {
					Main.testDriver.getLManager().removeView(derivedParent);
					NumBackFactory.deleteLayers();
				 }
			}
		});

				
		clear.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {

				derivedParent.clearLine();
				parent.clearOffScreen();
				grapher.clear();
				parent.repaint();

	
			}
		});

	


	}

	public void valueChanged(ListSelectionEvent e){
		int r = table.getSelectedRow();

		if (lastSelection == r)
			return;
		lastSelection = r;

		grapher.reDo();

		log.println("Ping!");

	};


	void initUI()
	{
		add = new JButton("Add");
		delete = new JButton("Delete");
		clear = new JButton("Clear");

		JPanel buttonPanel = new JPanel(new FlowLayout());
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		buttonPanel.add(add);
		buttonPanel.add(Box.createHorizontalStrut(40));
		buttonPanel.add(delete);
		buttonPanel.add(Box.createHorizontalStrut(40));
		buttonPanel.add(clear);

		
		scrollpane = new JScrollPane(table,
				  JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				  JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollpane.setPreferredSize(new Dimension(400,100));

		grapher = new ThemisGrapher(this);
		grapher.setPreferredSize(new Dimension(400,400));
		
		JPanel p1 = new JPanel(new BorderLayout());
		p1.add(scrollpane, BorderLayout.NORTH);
		p1.add(grapher, BorderLayout.CENTER);
		/*
		JSplitPane jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollpane, grapher);
		jsp.setDividerLocation(0.5);
		jsp.resetToPreferredSizes();
		*/

		setLayout(new BorderLayout());
		add(buttonPanel, BorderLayout.NORTH);
		add(p1, BorderLayout.CENTER);
	}

	public static String [] value2String(Object value)
	{
//		String	cls;
		Class		cls;
		int 		ii;

		byte 		[] b;
		char 		[] c;
		short		[] s;
		int		[] i;
		float		[] f;
		double	[] d;
		String	[] st;

		String [] v;

		if (value == null) {
			log.println("ERROR: Called value2String with NULL object!");
			v=new String[1];
			v[0]="NULL";
			return (v);
		}


		cls = value.getClass().getComponentType();

		if (cls == String.class) {
			st = (String [])value;
			v = new String[st.length];
			for (ii=0;ii<st.length;ii++)
				v[ii]=new String(st[ii]);

		}

		else if (cls == Byte.TYPE) {
			b=(byte [])value;
			v = new String[b.length];
			for (ii=0;ii<b.length;ii++)
				v[ii]=nf.format(b[ii]);

		}

		else if (cls == Character.TYPE) {
			c=(char [])value;
			v = new String[c.length];
			for (ii=0;ii<c.length;ii++)
				v[ii]=nf.format(c[ii]);
		}

		else if (cls == Short.TYPE) {
			s=(short [])value;
			v = new String[s.length];
			for (ii=0;ii<s.length;ii++)
				v[ii]=nf.format(s[ii]);
		}

		else if (cls == Integer.TYPE) {
			i=(int [])value;
			v = new String[i.length];
			for (ii=0;ii<i.length;ii++)
				v[ii]=nf.format(i[ii]);
		}

		else if (cls == Float.TYPE) {
			f=(float [])value;
			v = new String[f.length];
			for (ii=0;ii<f.length;ii++)
				v[ii]=nf.format(f[ii]);
		}

		else if (cls == Double.TYPE) {
			d=(double [])value;
			v = new String[d.length];
			for (ii=0;ii<d.length;ii++)
				v[ii]=nf.format(d[ii]);
		}

		else {
			log.println("Hey! I couldn't translate incomming value becuase I can't figure out it's type!");
			log.println("Incomming type: "+cls.getComponentType().getName());
//			log.println("Index of call: "+cls.indexOf("String"));
			v=null;
			return(v);
		}

/*********************************************
****************CRIPPLE MULTI-BAND INPUT******
**********************************************/

	String [] cripple = new String[1];
	cripple[0] = v[0]; //JUST THE FIRST BAND

	return (cripple);
//	return (v);


	
	}

	public void deleteElement(int idx)
	{
		if (idx < valueList.size()){
			valueList.remove(idx);
			colorList.remove(idx);
			nameList.remove(idx);
			tableModel.tableDelete(idx);
		}
		else
			log.println("Hey! you tried to remove index: "+idx+" but we only go upto: "+(valueList.size() -1));
	}
			


/*
** value is an array [], of primative types, ie byte,char,short,int,float or double.
** the array represents the number of bands in one of the NumBackLView's buffered images.
** For now, this should only be 1, but in the future we may support multi-band bufferedImages.
** As a precautionary, color and name are String arrays and MUST contain the the same
** number of elements as value.  Or, they must be NULL and names and colors will be
** provided.
*/

	public void addElement(final Color [] color,
						   final String [] name,
						   final Object value)
	{
	   /** BEGIN STRANGE CODE BY MICHAEL **/

	   /*
	    * Okay, here goes with trying to explain the insanity below.
	    * "Conceptually," you should think of this function as simply
	    * calling addElementImpl directly with no threading or
	    * synchronization manipulation involved anywhere. As far as
	    * externally-visible effects, that's truly all that it appears
	    * to do from the outside. But in practice the below is
	    * necessary to prevent a threading bug.
	    *
	    * If none of the below were in place, the following bug
	    * occurs: if the user adds a new numeric layer, and as it's
	    * just painting itself in the user drops down the combobox for
	    * the zoom factor, then the application hangs. What's
	    * occurring is a deadlock... in the code here, we have a lock
	    * on lview from up above in one of our caller's callers. In
	    * the course of addElementImpl, we wind up fiddling with some
	    * swing containers (to add a new data source line in the focus
	    * panel). Well, the fiddling requires swing to synchronize on
	    * some internal objects.
	    *
	    * But if the user has dropped the zoom box, those internal
	    * objects are already locked by the mouse handler that took
	    * the user's click. So the thread here blocks trying to
	    * synchronize on those internal AWT objects.
	    *
	    * HOWEVER, the AWT thread itself is busy trying to set up the
	    * combo box's drop-down, which (apparently) involves adding a
	    * mouse listener to our lview (it's part of how Swing
	    * implements pop-up menus). And since addMouseListener is
	    * synchronized, AWT blocks trying to synchronize on the lview.
	    *
	    * BAM, deadlock: #1) We've got the lview synchronized here and
	    * blocked trying to synchronize the AWT internal stuff.
	    * #2) Meanwhile the AWT thread has it the other way around,
	    * with the internal stuff synchronized, but blocking on trying
	    * to synchronize the lview.
	    *
	    * Now, the "obvious" solution would be to simply use the
	    * SwingUtilities.invokeAndWait utility to queue the
	    * addElementImpl call synchronously on the swing AWT thread.
	    * However, that winds up failing under the same conditions,
	    * since invokeAndWait waits on some AWT objects while still
	    * holding the lview lock (which still blocks the AWT thread
	    * trying to get that lock). And we really need an
	    * invokeAndWait... invokeLater won't do, I tried it and we
	    * wind up with duplicate data source entries in the focus
	    * panel. So we need to NOT leave this method until AFTER
	    * addElementImpl has executed, PERIOD.
	    *
	    * So instead we call a function in Util that effectively
	    * behaves just like the standard invokeAndWait, but which
	    * synchronizes on some other object instead of AWT's internal
	    * object. And we choose that "other" object to be the lview,
	    * which effectively relinquishes the lview's lock while we're
	    * waiting, allowing the AWT thread to finish and eventually
	    * call our code.
	    *
	    * Ugly and long-winded, yes. I know it looks like overkill,
	    * but it's actually as elegant a solution as could be devised,
	    * given the problem... nothing simpler worked, since it's
	    * impossible to guarantee the ordering of the locks (because
	    * it's our caller who's locking).
		*/

	   // This call doesn't return until the run() below is executed
	   // on the AWT event thread.
	   Util.invokeAndWaitSafely(
		   derivedParent, // the lview
		   new Runnable()
			{
			   public void run()
				{
				   addElementImpl(color, name, value);
				}
			}
		   );

	   /** END STRANGE CODE BY MICHAEL **/
	}
	
   /**
	** DO NOT CALL DIRECTLY. Implementation of {@link #addElement},
	** which internally must perform its work on the AWT thread to
	** avoid a deadlock problem.
	**/
	private void addElementImpl(Color[] color, String[] name, Object value)
	{
		String [] v;
		int i;
		
		v = value2String(value);

		if (v == null) {
			log.println("NULL??? Our String is NULL!!!");
			return;
		}


		if (color != null)
			if (color.length != v.length) {
				log.aprintln("Number of Colors in String don't match number of Value elements: "+
								  color.length+" vs "+ v.length+"   Add Aborted");
				return;
			}

		if (name != null)
			if (name.length != v.length) {
				log.aprintln("Number of Names in String don't match number of Value elements: "+
								  name.length+" vs "+ v.length+"   Add Aborted");
				return;
			}


		for (i=0;i<v.length;i++) {

			valueList.add(v[i]);
	
			log.println("Value List is "+valueList.size()+" in size\n");

			if (color == null) {
				log.println("Color List is "+colorList.size()+" in size");
				log.println("Added color: "+colorString[(colorList.size() % colorString.length)]);
				colorList.add(stringToColor(colorString[(colorList.size() % colorString.length)]));
				log.println("Color List is "+colorList.size()+" in size\n\n");
			}
			else 
				colorList.add(color[i]);


			if (name == null)
				nameList.add("Data Object "+String.valueOf(nameList.size()+1));
			else
				nameList.add(name[i]);

		}

		tableModel.tableInsert(v.length);
	}


/*
** update Value receives an array of Objects.  Each Object element is itself
** an array of primatives.  Each element in the Object array represents one
** of the bufferedImages in the NumBackLView.  There can be a minimum of 1 and
** a maximum of...N (no MAX specified).  Each bufferedImage can itself have
** multiple bands of information and therefore the array of primatives reflects
** a value for each band.  Currently only single band bufferedImages are supported
** but MULTIPLE bufferedImages ARE currently supported.
*/
	public void updateValue(Object [] l)
	{
		int i,j;
		String [] v;
		int idx = 0;

//		log.println("inncomming count: "+l.length);


		for(i=0;i<l.length;i++) {

			if (l[i] == null) {
				log.println("OBJECT list contains a null object at index: "+i);
				continue;
			}

			v = value2String(l[i]);

//			log.println("Item count for index "+i+" = "+v.length);

			if (idx >= table.getRowCount()){ //We;ve been sent MORE items then we have in our table...ADD!
//				log.println("We've been sent MORE items then we have. ADD! idx= "+idx+" table count = "+table.getRowCount());
				String [] foo = new String[v.length];
				Color [] cFoo = new Color[v.length];
				for(int k =0; k<v.length;k++) {
					if (v.length > 1)
						foo[k] = derivedParent.getName(i) + Integer.toString(k);
					else
						foo[k] = derivedParent.getName(i);

					cFoo[k] = derivedParent.getColor(i);
				}

				addElement(cFoo,foo,l[i]);
			}

			for (j=0;j<v.length;j++) {
//				log.println("Setting --> "+v[j]+", index["+idx+"]");
				valueList.set(idx,v[j]);
				idx++;
			}
		}

		tableModel.tableUpdate();
	}

	public void tableChanged(TableModelEvent e){

		log.print("Event\t");
		switch(e.getType()) {

		case TableModelEvent.INSERT:
			log.print("INSERT: ");
			break;

		case TableModelEvent.DELETE:
			log.print("DELETE: ");
			break;

		case TableModelEvent.UPDATE:
			log.print("UPDATE: ");
			break;

		default:
			log.println("HUH???");

		}


		log.print("["+e.getFirstRow()+","+e.getLastRow()+"] Col: ");
		if (e.getColumn()==TableModelEvent.ALL_COLUMNS)
			log.println("All");
		else
			log.println(e.getColumn());

	}



	public class NumTableModel extends AbstractTableModel {

		private String[] columnNames = { "Color","Name","Value"};


		public NumTableModel(){}

		public String getColumnName(int col) {return columnNames[col];}
		public int getRowCount(){return (valueList.size());}
		public int getColumnCount(){return (columnNames.length);}

		public boolean isCellEditable(int row, int col) 
		{
			if (col == 0)
				return(true);
			else
				return(false);
		}
			


		public void tableUpdate()
		{
			fireTableRowsUpdated(0,valueList.size());
		}

		public void tableInsert(int l)
		{
			fireTableRowsInserted(l,l);
		}

		public void tableDelete(int idx)
		{
			fireTableRowsDeleted(idx,idx);
		}
			

		public Object getValueAt(int row, int column)
		{

				switch(column) {

				case 0: return((Color)colorList.get(row));
				case 1: return((String)nameList.get(row));
				case 2: return((String)valueList.get(row));
				default: return(null);

				}

		}


/*
** The only collumn (at this point in time) which is 'editable' is the color column.
** This collumn contains a ColorCell object (which is really a ColorCombo object).
** It is here where we make the color changes happen.  We need to update the color combo box
** AND the color list.  We then tell the grapher to repaint so that the data takes on its new color.
*/
		public void setValueAt(Object value, int row, int col) 
		{
			log.println("CHANGE at ["+row+","+col+"]");

			Color c = (Color) value;
			colorList.set(row,c);

			grapher.repaint();

		}

		/*
		 * JTable uses this method to determine the default renderer/
		 * editor for each cell.  If we didn't implement this method,
		 * then the last column would contain text ("true"/"false"),
		 * rather than a check box.
		*/
		public Class getColumnClass(int c) {
			switch(c){
			case 0: return Color.class;
			case 1: return String.class;
			case 2: return String.class;
			default: return Object.class;
			}
		}
	}

	public void addData(String [][] values, double [] x)
	{
		grapher.addData(values,x);
	}

	public void liveUpdate(double t, boolean out)
   {
		grapher.liveUpdate(t,out);
   }

}
  
	
