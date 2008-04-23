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
import edu.asu.jmars.swing.FontUtil;
import edu.asu.jmars.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.border.*;
import javax.swing.text.*;
import java.text.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.font.*;
import java.util.*;
import java.io.*;


public class ThemisGrapher extends JPanel 
{


/***********************************Inner Classes*****************************/


/*
** This class defines our drawing area.  We needed to override it's paint compoent
** and add mouse support (we're going to mouse on the canvas)
*/

	public class myTextField extends JTextField implements 
													KeyListener
	{

		public myTextField()
		{
			Keymap km = this.getKeymap();
			KeyStroke tab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
																0);

			KeyStroke [] ks = km.getBoundKeyStrokes();
			Action a=null;
			for (int i = 0; i<ks.length;i++)
				if (ks[i].getKeyCode() == KeyEvent.VK_ENTER){
					a = km.getAction(ks[i]);
					break;
				}

			if (a!=null)
				km.addActionForKeyStroke(tab,a);

		}


		public void keyPressed(KeyEvent e) {}
		public void keyReleased(KeyEvent e) 
		{
			if (e.getKeyCode() == KeyEvent.VK_ENTER) 
				log.println("Enter!");
			else if (e.getKeyCode() == KeyEvent.VK_TAB)
				 log.println("Tab!");

		}
		public void keyTyped(KeyEvent e) {}
	}


	public class MyCanvasAxis extends JPanel
	{
		public MyCanvasAxis()
		{
		}


		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			Font tmpFont;

			
			g2.transform(new AffineTransform(canvasPanel.getWidth()
								,0.0,0.0,canvasPanel.getHeight(),
								0.0,0.0));

			g2.setColor(Color.white);
			g2.fill(new Rectangle2D.Double(0.0,0.0,1.0,1.0));

			tmpFont = labelFont.deriveFont(new AffineTransform(
								1.0/canvasPanel.getWidth(), 0.0,0.0,
								1.0/canvasPanel.getHeight(),0.0,0.0));

			if (tmpFont == null) 
				log.println("Yo DUDE, bad font!");
			else
				g2.setFont(tmpFont); 


			drawAxes(g2);

			canvas.repaint();

		}

	}



	public class MyCanvas extends JPanel implements 
													MouseInputListener
	{

		protected Line2D lv = new Line2D.Double();
		protected Line2D lh = new Line2D.Double();
		protected JFileChooser fc = new JFileChooser();	
		protected Point boxAnchor=null;
		protected Point boxCorner=null;
		protected Rectangle drawingRec;
		protected Point draggedFrom=null;
		protected Point draggedTo=null;
		protected double 	stepSize=10.;


		public MyCanvas()
		{
			addMouseListener(this);
			addMouseMotionListener(this);
			setDoubleBuffered(true);
			fc.setDialogTitle("Saving Jmars Graph");
			fc.setDialogType(JFileChooser.SAVE_DIALOG);
		}


		protected void paintComponent(Graphics g)
		{

			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;

//			log.println("Enter!");


			g2.transform(new AffineTransform(canvas.getWidth(),0.0,
									0.0,canvas.getHeight(),0.0,0.0));
			g2.setColor(Color.white);
			g2.fill(new Rectangle2D.Double(0.0,0.0,1.0,1.0));

//			log.println("Cleared!");

			lastCross = null; //since it's been cleared!

			graphData(g2);

		}

		public void drawBox(Point p)
		{
			if (boxAnchor == null || p == null) {
				log.println("You sent in a null component!");
				return;
			}

			Graphics2D g = (Graphics2D) this.getGraphics();
			int x,y;
			int w,h;

			x = (int)(p.getX() < boxAnchor.getX() ? p.getX():boxAnchor.getX());
			y = (int)(p.getY() < boxAnchor.getY() ? p.getY():boxAnchor.getY());


			drawingRec = new Rectangle(x,y, (int)Math.abs(p.getX()-boxAnchor.getX()),
												 (int)Math.abs(p.getY()-boxAnchor.getY()));
				
			g.setStroke(new BasicStroke(1f));
			g.setXORMode(Color.gray);


			g.draw(drawingRec);
			g.setPaintMode();
		}

		public void drawCross(Point2D p)
		{
			NumberFormat nf = NumberFormat.getInstance();
			nf.setMinimumFractionDigits(6);
			nf.setMaximumFractionDigits(6);
			
			Graphics2D g = (Graphics2D) this.getGraphics();
			if (p == null) {
				log.println("You sent a NULL point!");
				return;
			}

			double X = p.getX();
			double W = this.getWidth();
			double Y = p.getY();
			double H = this.getHeight();

//			log.println("Set Vertical line from: "+nf.format(X)+" , "+
//							 nf.format(yMin)+" --> "+nf.format(X)+" , "+nf.format(yMax));

//			log.println("Set Horizontal line from: "+nf.format(xMin)+" , "+
//							 nf.format(Y)+" --> "+nf.format(xMax)+" , "+nf.format(Y));

			lv.setLine(X,0.0,X,H);
			lh.setLine(0.0,Y,W,Y);
			g.setStroke(new BasicStroke(1f));
			g.setXORMode(Color.gray);

			g.draw(lh);
			g.draw(lv);

			g.setPaintMode();
		}


		public void mouseMoved(MouseEvent e) 
		{
			int i;

			if (stringData.size() < 1)
				return;
			
			int s = stringData.size(); // Number of datasets
			int l = ((String [])stringData.get(0)).length; //length of any given dataset (MUST all be =)
			int x = e.getX();
			int w = (int)this.getWidth();
			double t = ((((double)x/(double)w)*(xLabelMax-xLabelMin)+xLabelMin)-unscaledXMin)/(unscaledXMax-unscaledXMin) ;

			t = (t - XMIN)/(XMAX-XMIN);

			if (t < 0.0) t = 0.0;
			if (t > 1.0) t = 1.0;

			int idx = (int)Math.rint((t*((double)(l-1))));
			if (idx >= l) idx = (l-1);
			else if (idx < 0) idx = 0;

/*
			log.println("x: "+x);
			log.println("w: "+w);
			log.println("t: "+t);
			log.println("s: "+s);
			log.println("l: "+l);
			log.println("idx: "+idx);
*/

			String [][] v = new String[s][];

			for (i = 0; i < s; i++){
				v[i] = new String[1];
				v[i][0] = ((String[])stringData.get(i))[idx];
			}


			parent.updateValue(v);

			if(crossHairs) {

				currCross = e.getPoint();

				if (lastCross == null && currCross != null) { //first time in
					lastCross = currCross;
					drawCross(currCross); //draw-mode;

				}

				else {
					drawCross(lastCross); //erase-mode;
					drawCross(currCross); //draw-mode;
					lastCross = currCross;
				}
			}



			if (liveUpdate) 
//				parent.derivedParent.liveUpdate( ((double)e.getX()/getWidth()) * (xMax-xMin) + xMin,false);
				parent.derivedParent.liveUpdate(((((double)e.getX()/getWidth())*(xLabelMax-xLabelMin)+xLabelMin)-unscaledXMin)/(unscaledXMax-unscaledXMin),false);

		}

   	public void mouseDragged(MouseEvent e) 
		{
			int m = e.getModifiers();

			if (m == InputEvent.BUTTON3_MASK) {
				draggedTo = e.getPoint();
				boolean dragged = false;
				double minMax[] = new double[2];
				double delta;
				NumberFormat nf = NumberFormat.getInstance();
				nf.setMaximumFractionDigits(2);

				if (Math.abs(draggedFrom.getX()-draggedTo.getX()) > 3) {

					minMax=findAxesMinMax(false); //Find x-axis range
					delta = (minMax[1]-minMax[0])/stepSize;
					dragged = true;
	
					if (draggedFrom.getX() < draggedTo.getX()) { //mouse moved to the right, so scene moves to the left
																			//This is acomplished by increasing the min/max setting
						xMin=minMax[0]+delta;
						xMax=minMax[1]+delta;

					}

					else { //mouse moved to the left
						xMin=minMax[0]-delta;
						xMax=minMax[1]-delta;
					}
				
					autoX.setSelected(false);
					xHighT.setEnabled(true);
					xHighT.setBackground(Color.white);
					xLowT.setEnabled(true);
					xLowT.setBackground(Color.white);
					xHighT.setText(nf.format(xMax));
					xLowT.setText(nf.format(xMin));
					autoScaleX = false;
					findAxesMinMax(false); //Set new range;
				}



				if (Math.abs(draggedFrom.getY()-draggedTo.getY()) > 3) {
					minMax=findAxesMinMax(true); //Find y-axis range
					delta = (minMax[1]-minMax[0])/stepSize;
					dragged = true;
	
					if (draggedFrom.getY() < draggedTo.getY()) { //mouse moved up, so scene moves down
																			//This is acomplished by increasing the min/max setting
						yMin=minMax[0]-delta;
						yMax=minMax[1]-delta;

					}

					else { //mouse moved to the left
						yMin=minMax[0]+delta;
						yMax=minMax[1]+delta;
					}
				
					autoY.setSelected(false);
					yHighT.setEnabled(true);
					yHighT.setBackground(Color.white);
					yLowT.setEnabled(true);
					yLowT.setBackground(Color.white);
					yHighT.setText(nf.format(yMax));
					yLowT.setText(nf.format(yMin));
					autoScaleY = false;
					findAxesMinMax(true); //Set new range;
				}

				if (dragged){
					reDo();
					draggedFrom = draggedTo;
				}

			}

			else {

				//add some dimension checks here
				Point mm = e.getPoint();
				if (mm.getX() < 0. || mm.getX() > canvas.getWidth())
					return;

				if (mm.getY() < 0. || mm.getY() > canvas.getHeight())
					return;

				if (boxCorner != null) 
					drawBox(boxCorner); //Erase old box
				
				drawBox(mm); //Draw new box
				boxCorner=mm;
			}
		}

   	public void mousePressed(MouseEvent e) 
		{
			int m = e.getModifiers();

			if (m == InputEvent.BUTTON3_MASK) {
				draggedFrom = e.getPoint();
				draggedTo = null;

			}

			else {
				boxAnchor = e.getPoint();
				boxCorner = null;
			}



		}

   	public void mouseReleased(MouseEvent e) 
		{


			int m = e.getModifiers();


			if (m == InputEvent.BUTTON3_MASK) {

				if (draggedTo != null) {
						draggedTo = null;
						draggedFrom = null;
						return;
				}

				draggedFrom = null;
				int returnVal = fc.showOpenDialog(this);
				log.println("Your choice is: "+returnVal);

				if(returnVal == JFileChooser.APPROVE_OPTION) {

					try {

						dumpGraph(fc.getSelectedFile().getCanonicalPath());
					}

					catch(IOException ex){log.println("Exception("+ex+") occured. Sorry, no name, no save");}


				}	

				return;

			}


			if (boxCorner != null)
				drawBox(boxCorner); //Erase


//Calculate new mins/maxs based on box location/size

			double boxMinX = Math.min(boxCorner.getX(),boxAnchor.getX())/canvas.getWidth();
			double boxMaxX = Math.max(boxCorner.getX(),boxAnchor.getX())/canvas.getWidth();
			double boxMinY = 1.0-(Math.max(boxCorner.getY(),boxAnchor.getY())/canvas.getHeight());
			double boxMaxY = 1.0-(Math.min(boxCorner.getY(),boxAnchor.getY())/canvas.getHeight());

			double minMax[] = new double[2];

			double dx;
			double dy;

			log.println(" boxMinX = "+ boxMinX);
			log.println(" boxMaxX = "+ boxMaxX);
			log.println(" boxMinY = "+ boxMinY);
			log.println(" boxMaxY = "+ boxMaxY);


//set new mins/maxs


			NumberFormat nf = NumberFormat.getInstance();
			nf.setMaximumFractionDigits(2);

			minMax = findAxesMinMax(false);
 			dx = minMax[1]-minMax[0];

			autoX.setSelected(false);
			xHighT.setEnabled(true);
			xHighT.setBackground(Color.white);
			xLowT.setEnabled(true);
			xLowT.setBackground(Color.white);
			xMax = boxMaxX*dx+minMax[0];
			xMin = boxMinX*dx+minMax[0];
			xHighT.setText(nf.format(xMax));
			xLowT.setText(nf.format(xMin));
			autoScaleX = false;


			findAxesMinMax(false); //This will cause them to set the NEW definition

			minMax = findAxesMinMax(true);
 			dy = minMax[1]-minMax[0];

			log.println("Incomming Y-Max: "+minMax[1]);
			log.println("Incomming Y-Min: "+minMax[0]);
			log.println("\tDifference: "+dy);


			autoY.setSelected(false);
			yHighT.setEnabled(true);
			yHighT.setBackground(Color.white);
			yLowT.setEnabled(true);
			yLowT.setBackground(Color.white);
			yMax = boxMaxY*dy+minMax[0];
			yMin = boxMinY*dy+minMax[0];
			yHighT.setText(nf.format(yMax));
			yLowT.setText(nf.format(yMin));
			autoScaleY = false;
			findAxesMinMax(true);

//redraw

			reDo();


//clean-up
			boxAnchor = null;
			boxCorner = null;

		}

   	public void mouseEntered(MouseEvent e) 
		{
			lastCross = null;
		}
   	public void mouseExited(MouseEvent e) 
		{
			if (crossHairs && lastCross != null)
				drawCross(lastCross); //erase-mode
			if (liveUpdate)
				parent.derivedParent.liveUpdate(((((double)e.getX()/getWidth())*(xLabelMax-xLabelMin)+xLabelMin)-unscaledXMin)/
																(unscaledXMax-unscaledXMin),true);

			lastCross = null;
		}
   	public void mouseClicked(MouseEvent e) {}
	}





/***********************************ThemisGrapher********************************/


/* GUI components */
	protected	JScrollPane 	canvasScrollpane;
	protected	MyCanvas			canvas;
	protected	MyCanvasAxis	canvasPanel;
	protected	JPanel			p2,p3,p4,p5;
	protected	JButton			zoomOut,zoomIn,reset;
	protected	JCheckBox		crossRB,liveRB;

	protected	JPanel			pz;
	protected	JLabel			high,low,auto;
	protected	JLabel			lx,ly;
	protected	myTextField		xHighT,xLowT,yHighT,yLowT;
	protected	JCheckBox		autoX,autoY;
	protected	String[] scaleChoices = {"Raw","Normalized","Selected Norm","Selected Raw"};
	protected	JComboBox		scaler;
	protected	JLabel			scalerLabel;
	


/* Graphing Components */

	protected	boolean			raw=true;
	protected	boolean			normalized=false;
	protected	boolean			selectedNormalized=false;
	protected	boolean			selectedRaw=false;
	protected	boolean			autoScaleX=true;
	protected	boolean			autoScaleY=true;
	protected	boolean			crossHairs = false;
	protected	boolean			liveUpdate = false;
	protected 	double 			xMax,yMax,xMin,yMin; // graphing constraints
	protected	double			yLabelMin,yLabelMax,xLabelMin,xLabelMax;
	protected	double			unscaledXMin,unscaledXMax;
	protected	double			[] xAxis = null;
	protected	double			[] unScaled_xAxis = null;
	protected	Point2D			lastCross,currCross;
	protected	int				currRow=-1;


/* These set the final drawing boundaries.  We work with 0.0 --> 1.0, but we want
** a little space between the axes and the data (just the tiniest gap) for better
** viewing.
*/
	public	static double	XMIN = 0.002;
	public	static double	YMIN = 0.002;
	public	static double	XMAX = 0.998;
	public	static double	YMAX = 0.998;




	private static  DebugLog			log=DebugLog.instance();
	protected	ArrayList		data = new ArrayList();
	protected	ArrayList		stringData = new ArrayList();
	protected	NumBackFocus	parent;
	protected	Font				font = null;
	protected	FontRenderContext frc = null;

	protected	final int		MAJOR_TICK = 8;
	protected	final int		MINOR_TICK = 4;
	protected	Font 				labelFont;


	public ThemisGrapher(NumBackFocus p)
	{
		parent = p;

		yMax = 0.9998;
		yMin = 0.0002;
		xMax = 0.9998;
		xMin = 0.0002;

		initUI();
		initEvents(); 
		labelFont = new Font("Serif", Font.PLAIN, 10 );
		if (labelFont == null)
			log.println("ERROR: Unable to initialize font!!");
		setRawScaling();

		addKeyListener(xHighT);
		addKeyListener(yHighT);
		addKeyListener(xLowT);
		addKeyListener(yLowT);
		xHighT.setRequestFocusEnabled(true);
		yHighT.setRequestFocusEnabled(true);
		xLowT.setRequestFocusEnabled(true);
		yLowT.setRequestFocusEnabled(true);
	}


	public void setRawScaling()
	{
		raw = true;
		normalized = false;
		selectedNormalized = false;
		selectedRaw = false;
	}

	public void setNormalizedScaling()
	{
		raw = false;
		normalized = true;
		selectedNormalized = false;
		selectedRaw = false;
	}

	public void setSelectedNormalizedScaling()
	{
		raw = false;
		normalized = false;
		selectedNormalized = true;
		selectedRaw = false;
	}

	 public void setSelectedRawScaling()
	{
		raw = false;
		normalized = false;
		selectedNormalized = false;
		selectedRaw = true;
	}


	public void reDo()
	{
		if (stringData.size() < 1)
			return;
		setAxes();
		data.clear(); //Clear the data block
		double [] tmp;
		int i;
		for (i=0;i<stringData.size();i++){
			tmp = convert((String [])stringData.get(i));
			scaleData(tmp);
		}	
		repaint();

	}

	protected void setAxes()
	{
		double [] minMax;


		minMax = findAxesMinMax(true);
		yLabelMax = minMax[1];
		yLabelMin = minMax[0];
		log.println("Y-Axis Labels:");
		log.println("\tY Min: "+yLabelMin);
		log.println("\tY Max: "+yLabelMax);


		minMax = findAxesMinMax(false);
		xLabelMax = minMax[1];
		xLabelMin = minMax[0];
//		log.println("X-Axis Labels:");
//		log.println("\tX Min: "+xLabelMin);
//		log.println("\tX Max: "+xLabelMax);

		xAxis = scale(XMIN,XMAX,unScaled_xAxis,xLabelMin,xLabelMax);
	}

	protected void scaleData(double []tmp)
	{

		if (raw || selectedRaw) 
		{
			data.add(scale(YMIN,YMAX,tmp,yLabelMin,yLabelMax));
		}

		else if (normalized || selectedNormalized) 
		{
			data.add(scale(YMIN,YMAX,tmp));
		}

		else  
		{
			log.println("ERROR: Scaling flags are befowled!");

		}
	}


	public void addData(String [][]values, double [] x)
	{
		int i,j;
//First flush the buffers, we ALWAYS start from scratch here.
		stringData.clear();
		data.clear(); 

		double [] tmp;
		double [] minMax;


		unScaled_xAxis = x;	//Need to collect min and max info about the unscaled X-axis 
		minMax = findMinMax(unScaled_xAxis);
		unscaledXMin = minMax[0];
		unscaledXMax = minMax[1];

		for (i=0;i<values.length;i++)
			stringData.add(values[i]); // Store the strings...we'll need them for label calculations

		setAxes();

		for (i=0;i<values.length;i++){
			tmp = convert(values[i]); //now we have an array of doubles;
			scaleData(tmp);
		}


		this.repaint();

	}


/*****
** returning var[0] = min;
** returning var[1] = max;
*****/

	protected double [] findMinMax(double [] values)
	{
/*****
***DEBUG****
****/
		if (values.length <= 0) {
			log.println("LENGTH error: values is an empty array!");
			return(null);
		}

		int i;
		double min;
		double max;
		double tmp;

		max=min=values[0];

		for(i=1;i<values.length;i++){
			tmp =values[i];
			if (min > tmp) min = tmp;
			if (max < tmp) max = tmp;
		}

		double [] foo = new double[2];

		foo[0] = (min);
		foo[1] = (max);

		return(foo);

	}

/*
** Scale incomming array to min : max
**/

	protected double [] scale(double min, double max, double [] values)
	{
/*****
***DEBUG****
****/
      if (values.length <= 0) {
         log.println("LENGTH error: values is an empty array!");
         return(null);
      }

      double []minMax;
      double tmp;
      double [] v = new double[values.length];
      double diff;
		double diff2;

      int i;

      minMax = findMinMax(values);
      diff = minMax[1] - minMax[0];
		diff2 = (max - min);


//		log.println("Scale(double) "+min+" --> "+max+" (delta "+diff2+ "):");
//		log.println("\tMax: "+minMax[1]);
//		log.println("\tMin: "+minMax[0]);
//		log.println("\tDiff: "+diff);

      for (i=0;i<v.length;i++){
         tmp = values[i];
			v[i] = ((tmp-minMax[0])/diff)*diff2+min;
		}


		return(v);

	}

/*
** Scale incomming values between min and max based on given min/max values gmin and gmax.
** Example min = -1, max = 1; gmin = -32768 and gmax = 32767; Incomming data min = -1000 and max
** = 1000.  This would scale to: -0.0305 : 0.0305 
*/

	protected double [] scale(double min, double max, double [] values, double gmin, double gmax)
	{
/*****
***DEBUG****
****/
      if (values.length <= 0) {
         log.println("LENGTH error: values is an empty array!");
         return(null);
      }

		double diff = gmax - gmin;
		double diff2 = max - min;
		int i;
		double [] v = new double[values.length];

//		log.println("Scale(double) "+min+" --> "+max+" (delta "+diff2+ "):");
//		log.println("\tMax: "+gmax);
//		log.println("\tMin: "+gmin);
//		log.println("\tDiff: "+diff);

		for (i=0;i<v.length;i++)
			v[i] = ((values[i] - gmin)/diff) * diff2 + min;

		return(v);
	}


/*
** Used to convert an array of strings (which are assumed to be numeric values
** in some form (ie byte, short, int, float, or double)
** to an array of doubles.
*/
	protected double [] convert (String [] values) 
	{

/*****
***DEBUG****
****/
      if (values.length <= 0) {
         log.println("LENGTH error: values is an empty array!");
         return(null);
      }


		double [] v = new double[values.length];

		int i;

		for (i=0;i<v.length;i++){
//			log.println("Converting: values["+i+"] = "+values[i]);
			try
			{
				v[i] = Double.valueOf(values[i]).doubleValue();
			}
			catch (NumberFormatException e) //Happens at inf and so forth
			{
				v[i] = 0.0;
			}
			catch (NullPointerException e)
			{
				v[i] = 0.0;
			}
		}

		return(v);
}


/*
** This function comes from pw (the name was preserved...I have NO idea what it means).
** It was originally a fortran function.  I have no idea where Noel got it from or
** if it her wrote it himself.
**
** Given a min and max values (of ANY range and magnitude) and a tick 'count',
** that is, the MAX number of steps to get from min to max, this fuction returns:
** value[0] = starting value
** value[1] = step value
**
** Starting at value[0] and incrementsing by value[1] you will reach max in
** chunk or less steps.
*/

	protected double log10(double x)
	{
		return ((Math.log(x)/Math.log(10.0)));
	}

	protected double [] inice(double tMin,double tMax, double clicks)
	{
		int ia;
		double rng, amin, amax, a, b, d_bint;
		double values[] = new double[2];

		rng = (tMax - tMin);
		amin = tMin;
		amax = clicks;

		if (amax <= 0.0)
			amax = 1.0;

		a = log10(rng / amax);
		ia = (int)a;

		if (ia > a)
			ia = ia - 1;

		b = a - ia;

		if (b <= log10( 2.0))
			b = log10( 2.0);
		else if (b > log10( 5.0))
			b =  1.0;
		else
			b = log10( 5.0);

		d_bint = Math.pow( 10.0, (ia + b));
		a = Math.abs(amin) + d_bint;

		do {
			a = a - d_bint;
				if (a -  10.0 * d_bint >  0.0)
					a = a - ( 10.0 * d_bint);
				if (a - 10.0 * 10.0 * d_bint >  0.0)
					a = a - ( 10.0 *  10.0 * d_bint);
				if (a - d_bint <=  0.0 && a >=  0.0)
					break;
		} while (a >= 0.0);

		if (amin < 0)
			a = amin + a;
		else if (amin == 0.0)
			a = amin;
		else
			a = amin - a + d_bint;

		values[0] =  d_bint;
		values[1] =  a;

		return(values);
	}
	
	protected double[] findAxesMinMax(boolean axis)
	{
		double [] minMax = new double[2];

		if (axis) { // true = Y-Axis

			if (autoScaleY) {

				if (raw) {
					if (stringData.size() < 1) {// no data at the moment
						minMax[0]=0.0;
						minMax[1]=1.0;
					}
					else {

						double [] posible = new double [stringData.size() * 2];
						double [] mm;
						for (int i = 0 ; i< (stringData.size() * 2); i++){
							mm = findMinMax(convert((String [])stringData.get(i/2)));
							posible[i++]=mm[0];
							posible[i] = mm[1];
						}
						minMax=findMinMax(posible);
					}
	
				}

				else if (normalized)
				{
					minMax[0]=0.0;
					minMax[1]=1.0;

				}

				else
				{
					int r = parent.getSelectedRow();
					if (r < 0)  //no row selected
						currRow = 0;
					else
						currRow = r;

					minMax=findMinMax(convert((String [])stringData.get(currRow)));
				}
			}

			else 
			{
				minMax[0] = yMin;
				minMax[1] = yMax;

			}

		}

		else 
		{

			if (autoScaleX) {
				minMax[0]=0.0;
				if (unScaled_xAxis == null) 
					minMax[1]=1.0;
				else
					minMax=findMinMax(unScaled_xAxis);
	
			}

			else 
			{
				minMax[0] = xMin;
				minMax[1] = xMax;

			}

		}
	
		log.println((axis ? "Y-Axis: ":"X-Axis: ")+" Max: "+minMax[1]+"  Min: "+minMax[0]);

		return(minMax);
	}

	protected String encode(double value)
	{
		NumberFormat nf = NumberFormat.getInstance();
		
		if (value < 1.0) { //We want high persision
			//first check:
			if (value == 0.0) {
				nf.setMinimumFractionDigits(0);
				nf.setMaximumFractionDigits(0);
			}
	
			else {
				int min = (int)Math.rint(Math.abs(log10(value)));
				if (min > 6) min = 6;
				nf.setMinimumFractionDigits(min);	
				nf.setMaximumFractionDigits(min+1);
			}
		}

		else if ((int) value == value) {
			nf.setMinimumFractionDigits(0);
			nf.setMaximumFractionDigits(0);
		}
		else {
//			log.println("Value appears to have a decimel portion: "+value);
			int min = (int)Math.rint(log10(value));
			if (min >= 3) {//we're talking thousands
				nf.setMinimumFractionDigits(0);
				nf.setMaximumFractionDigits(0);
			}
			else {
				nf.setMaximumFractionDigits(2);
				nf.setMinimumFractionDigits(2);
			}
		}

		return(nf.format(value));
	}



/****************************************************************
*************************** GUI code ****************************
****************************************************************/

	protected void drawXTick(Graphics2D g2, boolean major, 
									 double xBase, double yBase, 
									 double H)
	{
		double length;

		if (major)
			length = MAJOR_TICK;
		else
			length = MINOR_TICK;

		Line2D l = new Line2D.Double(xBase,yBase,xBase,yBase+(length/H));
		g2.setStroke(new BasicStroke(0.001f));
		g2.draw(l);
	}

	protected void drawXValue(Graphics2D g2, double xBase, double yBase,double H,double W,double value)
	{
		FontMetrics fontMetrics = g2.getFontMetrics(labelFont);
		String str = encode(value);

		double rw = fontMetrics.stringWidth(str);
		double rh = fontMetrics.getHeight();

		double w = rw / W;
		double h = rh / H;

//		log.println("Drawing: "+str+" at: "+(xBase-(w/2))+" , "+(yBase+((MAJOR_TICK+2.0)/H)+(h/2.0)));

		g2.setColor(Color.black);
		FontUtil.drawStringAsShape(g2,str,(float)(xBase-(w/2.0)),(float)(yBase+((MAJOR_TICK+2.0)/H)+(h/2.0)));
		
	}

	protected void drawYTick(Graphics2D g2, boolean major,
                            double xBase, double yBase,
                            double W)
	{
		double length;

		if (major)
			length = MAJOR_TICK;
		else
			length = MINOR_TICK;


		Line2D l = new Line2D.Double(xBase-(length/W),yBase,xBase,yBase);
		g2.setStroke(new BasicStroke(0.001f));
		g2.draw(l);

	}


	protected void drawYValue(Graphics2D g2, double yBase, double xBase,double W,double H, double value)
	{
		FontMetrics fontMetrics = g2.getFontMetrics(labelFont);
		String str = encode(value);

		double rw = fontMetrics.stringWidth(str);
		double rh = fontMetrics.getHeight();

		double w = (rw + 8) / W;
		double h = (rh/4.0) / H;

//		log.println("Drawing: "+str+" at: "+(xBase-(w))+" , "+(yBase+h));

		g2.setColor(Color.black);
		FontUtil.drawStringAsShape(g2,str,(float)(xBase-(w)),(float)(yBase+h));

	}


	protected void drawAxes(Graphics2D g2)
	{

		double x = canvas.getX() - 3;
		double h = canvas.getHeight() + 3;

		double W = canvasPanel.getWidth();
		double H = canvasPanel.getHeight();

		double []minMax; // [0]=min, [1]=max
		double []value;  //[0] = step, [1] = start;

		double f,loc;

		double xPad = (3.0/W);
		double yPad = (3.0/H);

		double normX,normY;
		double diff;


		normX = (x/W);
		normY = (h/H);

		Line2D yaxis = new Line2D.Double(normX,0.0+yPad,(normX),(normY));
		Line2D xaxis = new Line2D.Double((normX),(normY),1.0-xPad,(normY));

		g2.setStroke(new BasicStroke(0.001f));
		g2.setColor(Color.black);
		g2.draw(yaxis);
		g2.draw(xaxis);


/*
** First we determine our scaling type: raw, normalized, selected_normalized.
**	Then we extract the needed Y min/max based on the above (x is always the same!).
** Then we draw our ticks.
** Then we label our axes.
*/


// Draw Y-Major ticks
		value=inice(yLabelMin,yLabelMax,(H/50.0)); //This last paramter comes from pw (plot.c, line 1362)
		diff = yLabelMax - yLabelMin;
		for(f = value[1]; f < yLabelMax; f += value[0]){
			loc = ((1.0-((f-yLabelMin)/(diff))) * ((normY)-yPad) + yPad ); 
			drawYTick(g2,true,(normX),loc,W);
			drawYValue(g2,loc,(normX),W,H,f);
		}

// Draw Y-Minor ticks
		value=inice(yLabelMin,yLabelMax,(H/60.0*5.0)); //This last paramter comes from pw (plot.c, line 1397)
		for(f = value[1]; f < yLabelMax; f += value[0]){
			loc = ((1.0-((f - yLabelMin)/(diff))) * ((normY) -yPad) +yPad); //This is now normalized (0.0 - 1.0) for the Y-axis
			drawYTick(g2,false,(normX),loc,W);
		}


// Draw X-Major ticks
		value=inice(xLabelMin,xLabelMax,(W/50.0)); //This last paramter comes from pw (plot.c, line 1362)
		diff = xLabelMax - xLabelMin;
		for(f = value[1]; f < xLabelMax; f += value[0]){
			loc = ((((f-xLabelMin)/(diff))) * (1.0-(normX)-xPad) + (normX)); 
			drawXTick(g2,true,loc,(normY),H);
			drawXValue(g2,loc,(normY),H,W,f);
		}


// Draw X-Minor ticks
		value=inice(xLabelMin,xLabelMax,(W/60.0*5.0)); //This last paramter comes from pw (plot.c, line 1397)
		for(f = value[1]; f < xLabelMax; f += value[0]){
			loc = ((((f-xLabelMin)/(diff))) * (1.0-(normX)-xPad) + (normX)); //This is now normalized (0.0 - 1.0) for the Y-axis
			drawXTick(g2,false,loc,(normY),H);
		}


	}


	public void graphData(Graphics2D g2)
	{
		if (xAxis == null) //nothing here yet
			return;
		if (xAxis.length < 1)
			return;

//		log.println("Drawing");

		Line2D l2d = new Line2D.Double();
		g2.setStroke(new BasicStroke(0.001f));

		int i,j;
		double x1,y1,x2,y2;
		double [] d;
		double [] minMax;
		double [] scaledDataY;
		double [] scaledDataX;
		int length;
		x2=x1=0.0;
		y2=y1=0.0;
		

		scaledDataX = xAxis;

		for (i=0;i<data.size();i++){
			d= (double [])(data.get(i));

			if (d == null) {
				log.println("NULL dataset!!!!");
				continue;
			}

			g2.setColor(parent.getColor(i));
	
			scaledDataY = d;

			x2=x1=scaledDataX[0];
			y2=y1=YMAX-scaledDataY[0];

			for(j=1;j<scaledDataY.length;j++){	
//				log.println("Data Point #"+j+" at: "+x2+", "+y2);
				l2d.setLine(x1,y1,x2,y2);
				g2.draw(l2d);
				x1=x2;
				y1=y2;
				x2=scaledDataX[j];
				y2=YMAX-scaledDataY[j];
			}
		}
	}



	protected void initEvents()
	{



      reset.addActionListener(new ActionListener(){
         public void actionPerformed(ActionEvent e) {

            log.println("Reset");
				xHighT.setEnabled(false);
				xHighT.setBackground(Color.gray);
				xLowT.setEnabled(false);
				xLowT.setBackground(Color.gray);
				xHighT.setText(null);
				xLowT.setText(null);
				autoScaleX = true;
				yHighT.setEnabled(false);
				yHighT.setBackground(Color.gray);
				yLowT.setEnabled(false);
				yLowT.setBackground(Color.gray);
				yHighT.setText(null);
				yLowT.setText(null);
				autoScaleY = true;

				findAxesMinMax(false);
				findAxesMinMax(true);

				reDo();

         }
      });

      zoomOut.addActionListener(new ActionListener(){
         public void actionPerformed(ActionEvent e) {

				double dx,dy;
				double minMax[] = new double[2];
				NumberFormat nf = NumberFormat.getInstance();
				nf.setMaximumFractionDigits(2);

				minMax=findAxesMinMax(false); 
	 			dx = minMax[1]-minMax[0];

				autoX.setSelected(false);
				xHighT.setEnabled(true);
				xHighT.setBackground(Color.white);
				xLowT.setEnabled(true);
				xLowT.setBackground(Color.white);
				xMax = minMax[1] + (dx/4.0);
				xMin = minMax[0] - (dx/4.0);
				xHighT.setText(nf.format(xMax));
				xLowT.setText(nf.format(xMin));
				autoScaleX = false;


				findAxesMinMax(false); //This will cause them to set the NEW definition
	
				minMax = findAxesMinMax(true);
	 			dy = minMax[1]-minMax[0];

				autoY.setSelected(false);
				yHighT.setEnabled(true);
				yHighT.setBackground(Color.white);
				yLowT.setEnabled(true);
				yLowT.setBackground(Color.white);
				yMax = minMax[1] + (dy/4.0);
				yMin = minMax[0] - (dy/4.0);
				yHighT.setText(nf.format(yMax));
				yLowT.setText(nf.format(yMin));
				autoScaleY = false;
				findAxesMinMax(true);

//redraw

				reDo();


         }
      });

      zoomIn.addActionListener(new ActionListener(){
         public void actionPerformed(ActionEvent e) {

				double dx,dy;
				double minMax[] = new double[2];

				NumberFormat nf = NumberFormat.getInstance();
				nf.setMaximumFractionDigits(2);
				minMax=findAxesMinMax(false); 
	 			dx = minMax[1]-minMax[0];

				autoX.setSelected(false);
				xHighT.setEnabled(true);
				xHighT.setBackground(Color.white);
				xLowT.setEnabled(true);
				xLowT.setBackground(Color.white);
				xMax = minMax[1] - (dx/4.0);
				xMin = minMax[0] + (dx/4.0);
				xHighT.setText(nf.format(xMax));
				xLowT.setText(nf.format(xMin));
				autoScaleX = false;


				findAxesMinMax(false); //This will cause them to set the NEW definition
	
				minMax = findAxesMinMax(true);
	 			dy = minMax[1]-minMax[0];

				autoY.setSelected(false);
				yHighT.setEnabled(true);
				yHighT.setBackground(Color.white);
				yLowT.setEnabled(true);
				yLowT.setBackground(Color.white);
				yMax = minMax[1] - (dy/4.0);
				yMin = minMax[0] + (dy/4.0);
				yHighT.setText(nf.format(yMax));
				yLowT.setText(nf.format(yMin));
				autoScaleY = false;
				findAxesMinMax(true);

//redraw

				reDo();


         }
      });

      crossRB.addActionListener(new ActionListener(){
         public void actionPerformed(ActionEvent e) {

				crossHairs = crossRB.isSelected();
//				log.println("CrossHairs = "+crossHairs);

         }
      });

      liveRB.addActionListener(new ActionListener(){
         public void actionPerformed(ActionEvent e) {

            liveUpdate = liveRB.isSelected();
				parent.derivedParent.setLiveUpdateEnabled(liveUpdate); //Set flag true/false in the LView

         }
      });

      autoX.addActionListener(new ActionListener(){
         public void actionPerformed(ActionEvent e) {
			
				NumberFormat nf = NumberFormat.getInstance();
				nf.setMaximumFractionDigits(2);

				if (!autoX.isSelected()){
					xHighT.setEnabled(true);
					xHighT.setBackground(Color.white);
					xLowT.setEnabled(true);
					xLowT.setBackground(Color.white);

					xMax = xLabelMax;
					xMin = xLabelMin;
					xHighT.setText(nf.format(xMax));
					xLowT.setText(nf.format(xMin));
					autoScaleX = false;
				}
				else {
					xHighT.setEnabled(false);
					xHighT.setBackground(Color.gray);
					xLowT.setEnabled(false);
					xLowT.setBackground(Color.gray);
					xHighT.setText(null);
					xLowT.setText(null);
					autoScaleX = true;
				}

				reDo();

         }
      });

      autoY.addActionListener(new ActionListener(){
         public void actionPerformed(ActionEvent e) {

				if (!autoY.isSelected()){
					yHighT.setEnabled(true);
					yHighT.setBackground(Color.white);
					yLowT.setEnabled(true);
					yLowT.setBackground(Color.white);

					yMax = yLabelMax;
					yMin = yLabelMin;
					yHighT.setText(Double.toString(yMax));
					yLowT.setText(Double.toString(yMin));
					autoScaleY = false;
				}
				else {
					yHighT.setEnabled(false);
					yHighT.setBackground(Color.gray);
					yLowT.setEnabled(false);
					yLowT.setBackground(Color.gray);
					yHighT.setText(null);
					yLowT.setText(null);
					autoScaleY = true;
				}

				reDo();
         }
      });

		scaler.addActionListener(new ActionListener(){
         public void actionPerformed(ActionEvent e) {

				switch(scaler.getSelectedIndex())
				{
					case 0:
						setRawScaling();
						break;
					case 1:
						setNormalizedScaling();
						break;
					case 2:
						setSelectedNormalizedScaling();
						break;
					case 3:
						setSelectedRawScaling();
				}
				reDo();
			}
		});


		xHighT.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
					String foo = xHighT.getText();
					if (foo == null)
						return;

					foo = cleanText(foo); //This removes any 'junk' chatacters (ie non-legal numeric values)

					xMax = Double.valueOf(foo).doubleValue();	

					reDo();
	
				}
		});

		yHighT.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
					String foo = yHighT.getText();
					if (foo == null)
						return;

					foo = cleanText(foo); //This removes any 'junk' chatacters (ie non-legal numeric values)

					yMax = Double.valueOf(foo).doubleValue();	

					reDo();
				}
		});

		xLowT.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
					String foo = xLowT.getText();
					if (foo == null)
						return;

					foo = cleanText(foo); //This removes any 'junk' chatacters (ie non-legal numeric values)

					xMin = Double.valueOf(foo).doubleValue();	

					reDo();
				}
		});

		yLowT.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
					String foo = yLowT.getText();
					if (foo == null)
						return;

					foo = cleanText(foo); //This removes any 'junk' chatacters (ie non-legal numeric values)

					yMin = Double.valueOf(foo).doubleValue();	

					reDo();
				}
		});


	}


	protected void initUI()
	{

      canvas = new MyCanvas();
      canvas.setBackground(Color.yellow);
      canvas.setPreferredSize(new Dimension(400,200));

		canvasPanel = new MyCanvasAxis();
		canvasPanel.setBackground(Color.red);
      canvasPanel.setMinimumSize(new Dimension(535,235));
      canvasPanel.setPreferredSize(new Dimension(535,235));
		canvasPanel.setLayout(new BoxLayout(canvasPanel, BoxLayout.X_AXIS));
		canvasPanel.setBorder(BorderFactory.createEmptyBorder(0, 60, 35, 0));

		canvasPanel.add(canvas);

/* Zooming Control and Checkbox Panel */
		
		pz = new JPanel(new GridLayout(3,1));
//		pz.setLayout(new BoxLayout(pz,BoxLayout.Y_AXIS));
		zoomIn = new JButton("+");
		zoomOut = new JButton("-");
		reset = new JButton("Reset");
		pz.add(zoomIn);
		pz.add(zoomOut);
		pz.add(reset);
		pz.setBorder(BorderFactory.createTitledBorder("Zoom"));

		p2 = new JPanel();
		p2.setLayout(new BoxLayout(p2,BoxLayout.Y_AXIS));
		crossRB = new JCheckBox("Cross-Hairs");
		liveRB = new JCheckBox("Live Update");
		p2.add(crossRB);
		p2.add(liveRB);


/* Axis Range Control panel */
		high = new JLabel("High"); high.setForeground(Color.black);
		low = new JLabel("Low "); low.setForeground(Color.black);
		auto = new JLabel("Auto"); auto.setForeground(Color.black);
		lx = new JLabel("X: "); lx.setForeground(Color.black);
		ly = new JLabel("Y: "); ly.setForeground(Color.black);

		xHighT = new myTextField();
		xHighT.setMaximumSize(new Dimension(50,30));
		xHighT.setEnabled(false);
		xHighT.setBackground(Color.gray);

		yHighT = new myTextField();
		yHighT.setMaximumSize(new Dimension(50,30));
		yHighT.setEnabled(false);
		yHighT.setBackground(Color.gray);

		xLowT = new myTextField();
		xLowT.setMaximumSize(new Dimension(50,30));
		xLowT.setEnabled(false);
		xLowT.setBackground(Color.gray);

		yLowT = new myTextField();
		yLowT.setMaximumSize(new Dimension(50,30));
		yLowT.setEnabled(false);
		yLowT.setBackground(Color.gray);

		autoX = new JCheckBox();
		autoX.setSelected(true);

		autoY = new JCheckBox();
		autoY.setSelected(true);
		
		Box b;
		JPanel sp = new JPanel(new GridLayout(3,4));
		sp.add(new JLabel());
		sp.add(low);
		sp.add(high);
		sp.add(auto);
		b = Box.createHorizontalBox();
		b.add(Box.createHorizontalGlue());
		b.add(lx);
		sp.add(b);
		sp.add(xLowT);
		sp.add(xHighT);
		sp.add(autoX);
		b = Box.createHorizontalBox();
		b.add(Box.createHorizontalGlue());
		b.add(ly);
		sp.add(b);
		sp.add(yLowT);
		sp.add(yHighT);
		sp.add(autoY);


/*p5: update and scale */

		scaler = new JComboBox(scaleChoices);
		scaler.setMinimumSize(new Dimension(50,30));
		scalerLabel = new JLabel("X-form:");
		scalerLabel.setForeground(Color.black);
		//scalerLabel.setMinimumSize(new Dimension(50,30));
		
		p5 = new JPanel(new FlowLayout());
		p5.add(scalerLabel);
		p5.add(scaler);

		p3= new JPanel(new BorderLayout());
		p3.add(p5, BorderLayout.NORTH);
		p3.add(sp, BorderLayout.CENTER);
		p3.setBorder(BorderFactory.createTitledBorder("Scale"));


/* p4: Bottom Control Row */

      p4 = new JPanel();
      p4.setLayout(new BoxLayout(p4, BoxLayout.X_AXIS));
      p4.add(p2);
      p4.add(pz);
      p4.add(p3);

/* Put it all together */


      this.setLayout(new BorderLayout());
//      this.setBorder(new CompoundBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), BorderFactory.createBevelBorder(BevelBorder.RAISED)));

		JPanel fooPanel = new JPanel(new BorderLayout());
		fooPanel.setBorder( new CompoundBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3),
															BorderFactory.createBevelBorder(BevelBorder.RAISED)));
		fooPanel.add(canvasPanel, BorderLayout.CENTER);
      add(fooPanel, BorderLayout.CENTER);
      add(p4, BorderLayout.SOUTH);

	}


	protected String cleanText(String txt)
	{
		return(txt);
	}

	public void clear()
	{
		stringData.clear();
		data.clear();
//		yMin=xMin=0.02;
//		yMax=xMax=0.9998;
		repaint();
	}

	public void delete(int index)
	{
		if (index < stringData.size())
			stringData.remove(index);
		else
			log.println("Hey! you tried to remove index: "+index+" but we only go upto: "+(stringData.size() -1));

		if (index < data.size())
			data.remove(index);
		else
			log.println("Hey! you tried to remove index: "+index+" but we only go upto: "+(data.size()-1));

		reDo();
	}

	public void liveUpdate(double t, boolean out)
	{
		if (data.size() < 1)
			return;
		int len = ((double [])data.get(0)).length;

		int x = (int)Math.rint( t * (double)(len-1));

		double w = canvas.getWidth();
		double h = canvas.getHeight();

		int idx = parent.getSelectedRow();
		if (idx < 0) idx =0;
		if (idx >= data.size()){
			log.println("Hey! You've got an INDEX problem, received: "+idx+" max = "+(data.size()-1)); 
			idx = data.size()-1;
		}
		if (out && lastCross == null)
			return;

//		log.println("\n\nENTER: t= "+t);
//		log.println("\t out= "+out+"\n\n");

		if (out && lastCross != null) { //we've exited
			canvas.drawCross(lastCross);
			lastCross = null;
			return;
		}

		double y = ((double [])data.get(idx))[x];

		currCross = new Point2D.Double(xAxis[x]*w,(YMAX-y)*h);

		if (lastCross == null) { //first time
			canvas.drawCross(currCross);
			lastCross = currCross;
		}

		else {
			canvas.drawCross(lastCross);
			canvas.drawCross(currCross);
			lastCross = currCross;
		}
	}


	protected void dumpGraph(String fn)
	{
		//Dump Ascii graph here
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(4);
		nf.setMinimumFractionDigits(4);

		DataOutputStream oos;
		try {
			oos = new DataOutputStream(new FileOutputStream(fn));

		}
		catch (FileNotFoundException e) {
			log.println("ERROR: unable to open "+fn+" for some reason.  Aborting Save.");
			return;
		}
		catch (IOException e) {
			log.println("ERROR: cannot write to file: "+fn+" for some reason...aborting!");
			return;
		}


		int width = stringData.size();
		String [][] output = new String [width][];
		int i,j;

		for (i=0;i<stringData.size();i++){
			output[i] = (String [])stringData.get(i);
		}

		//Okay, we've loaded ALL the data into an array of 'width' by # of data Rows

		try {

			for (j = 0; j < output[0].length /*Each col has same # of rows */; j++){
				oos.writeBytes((nf.format(unScaled_xAxis[j]) + "\t"));
				System.out.print((nf.format(unScaled_xAxis[j]) + "\t"));
				for (i = 0 ;i <width - 1; i++) {
					oos.writeBytes((output[i][j]+"\t"));
					System.out.print((output[i][j]+"\t"));
				}
				oos.writeBytes((output[i][j]+"\n"));
				System.out.print((output[i][j]+"\n"));
			}

			oos.close();
		}

		catch (IOException e) {
			log.println("ERROR: cannot write to file: "+fn+" for some reason...aborting!");
			return;
		}
	}
				
}
