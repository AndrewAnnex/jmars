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


package edu.asu.jmars.layer;

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.util.*;

public class AddDialog extends JDialog
{

	protected	JButton		ok;
	protected	JButton 		cancel;
	protected	JPanel		panel;
	protected	AddDialog	me;
	protected	JList	cb;
	protected	String		si;
	protected	int			numberItems;
	protected	int			index = -1;
	private static DebugLog log=DebugLog.instance();
	private String items[];

	protected void buildListofItem()
	{
		numberItems=LViewFactory.factoryList.size();
		items=new String[numberItems];
		Iterator iter = LViewFactory.factoryList.iterator();
		int count=0;
		while(iter.hasNext())
		{
			LViewFactory factory = (LViewFactory) iter.next();
			items[count]=factory.getName();
			count++;
		}
	}	

	public AddDialog(JFrame parent, boolean modal)
	{
		super(parent,"Add Layer", modal);
		setLocation(parent.getLocation());

		ok=new JButton("OK");
		cancel=new JButton("Cancel");
		panel=new JPanel();
		me=this;
		buildListofItem();
		cb = 
			new JList(items)
			 {
				public String getToolTipText(MouseEvent e)
				 {
					int idx = locationToIndex(e.getPoint());
					if(idx == -1)
						return  null;
					LViewFactory f =
						(LViewFactory) LViewFactory.factoryList.get(idx);
					return  f.getDesc();
				 }
			 }
			;

		cb.setSelectedIndex(0);
		cb.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		cb.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		cb.addMouseListener(
			new MouseAdapter()
			 {
				public void mouseClicked(MouseEvent e)
				 {
					if(e.getClickCount() == 2  &&
					   SwingUtilities.isLeftMouseButton(e)  &&
					   cb.locationToIndex(e.getPoint()) != -1)
					 {
						setVisible(false);
						ok.doClick();
					 }
				 }
			 }
			);

		panel.setLayout(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

		JPanel p = new JPanel();
		p.add(cb);
		p.setBorder(BorderFactory.createEmptyBorder(0,0,10,0));

		panel.add(p, BorderLayout.NORTH);
		panel.add(ok,BorderLayout.WEST);
		panel.add(cancel,BorderLayout.EAST);

		getContentPane().add(panel);

		cancel.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e) {
					me.setVisible(false);
				}
			}
		);	

		ok.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e) {
					index=cb.getSelectedIndex();
					log.println("You have selected item: "+index);
					me.setVisible(false);
				}
			}
		);


		pack();
		setVisible(true);
	}


	public String getSelection()
	{
		return(si);
	}

	public int	getSelectionIndex()
	{
		return(index);
	}
}
