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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import javax.swing.table.DefaultTableModel;

import edu.asu.jmars.layer.stamp.StampShape;

class StampTableModel extends DefaultTableModel {
    private Class[] columnClasses=new Class[0];
    private String[] columnNames=new String[0];
    
    private HashMap<StampShape, Integer> rowMap = new HashMap<StampShape, Integer>();
	
	public Class<?> getColumnClass(int columnIndex) {
		if (columnIndex==columnNames.length) {
			return StampShape.class;
		}
		return columnClasses[columnIndex];
	}

	public int getColumnCount() {
		return columnNames.length;
	}

	public String getColumnName(int columnIndex) {
		if (columnIndex==columnNames.length) {
			return "_stamp";
		}
		return columnNames[columnIndex];
	}

	public synchronized void removeAll() {
		int lastRow = dataVector.size();
		
		for (int i=0; i<lastRow; i++) {
			Vector v = (Vector)dataVector.elementAt(i);
			v.clear();
		}
		
		dataVector.removeAllElements();
		fireTableRowsDeleted(0,lastRow-1);
		rowMap.clear();
	}
	
	public synchronized void addRows(List<Vector> newRows) {
		int startingRow = dataVector.size();
		
		int rowCnt = startingRow;
		
		for(Vector v : newRows) {
			StampShape shape = (StampShape)v.elementAt(v.size()-1);
			rowMap.put(shape, new Integer(rowCnt++));
		}
		
		int endingRow = startingRow+newRows.size();
		dataVector.addAll(newRows);
        fireTableRowsInserted(startingRow, endingRow);			
	}
	
	public int getRow(StampShape s) {
		Integer row = rowMap.get(s);
		if (row!=null) {
			return row.intValue();
		} else {
			return -1;
		}
		
//		Enumeration e = dataVector.elements();
//		
//		int loopCnt=0;
//		while (e.hasMoreElements()) {
//			loopCnt++;
//			Vector v = (Vector)e.nextElement();
//			StampShape s2 = (StampShape)v.elementAt(v.size()-1);
//			
//			if (s==s2) {
//				System.out.println("loopCnt="+loopCnt);
//				return dataVector.indexOf(v);
//			}				
//		}
//		System.out.println("loopCnt="+loopCnt);
//
//		return -1;
	}
	
	public Object getValueAt(int rowIndex, int columnIndex) {
		if (columnIndex==columnNames.length) {
			Vector v = (Vector)dataVector.elementAt(rowIndex);
			StampShape s = (StampShape)v.elementAt(v.size()-1);
			return s;
		} else {
			return super.getValueAt(rowIndex, columnIndex);
		}
	}

	public Object getValueAt(int rowIndex) {
		Vector v = (Vector)dataVector.elementAt(rowIndex);
		StampShape s = (StampShape)v.elementAt(v.size()-1);
		return s;
	}
	
   public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
    }				
   
	public boolean isFirstUpdate() {
    	if (columnClasses==null || columnClasses.length==0) {
    		return true;
    	}
    	return false;
    }	

	public void updateData(Class[] newTypes, String[] newNames) {
		columnClasses=newTypes;
		columnNames=newNames;
	}	
}

