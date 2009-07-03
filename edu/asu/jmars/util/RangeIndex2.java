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


package edu.asu.jmars.util;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public final class RangeIndex2<T> {
	private final SortedSet<Range> min = new TreeSet<Range>(new RangeMinComparator());
	private final SortedSet<Range> max = new TreeSet<Range>(new RangeMaxComparator());
	
	public void add(Range range, T val){
		add(range.getMin(), range.getMax(), val);
	}
	
	public void add(double minVal, double maxVal, T val){
		Tuple tuple = new Tuple(minVal, maxVal, val);
		min.add(tuple);
		max.add(tuple);
	}
	
	public Set<T> query(double minVal, double maxVal){
		return query(new Range.Double(minVal,maxVal));
	}
	
	public Set<T> query(Range range){
		Set<Range> overlap = new HashSet<Range>();
		overlap.addAll(min.headSet(new Tuple(range.getMax()+Math.ulp(range.getMax()), range.getMax(), null)));
		overlap.retainAll(max.tailSet(new Tuple(range.getMin(), range.getMin(), null)));
		
		Set<T> s = new HashSet<T>();
		for(Range r: overlap)
			s.add(((Tuple)r).getValue());
		
		return s;
	}
	
	public int size(){
		return min.size();
	}
	
	public void clear(){
		min.clear();
		max.clear();
	}
	
	public List<Range> getCoveredRanges(){
		return Range.coalesce(min);
	}
	
	/**
	 * Simple container for the extent and value.
	 */
	class Tuple extends Range {
		final double min;
		final double max;
		final T value;
		
		public Tuple(double min, double max, T value) {
			this.min = min; this.max = max; this.value = value;
		}
		
		public double getMin(){ return min; }
		public double getMax(){ return max; }
		public T      getValue(){ return value; }
	}
	
	private final static class RangeMinComparator implements Comparator<Range> {
		public int compare(Range o1, Range o2) {
			return (int)Math.signum(o1.getMin()-o2.getMin());
		}
	}
	private final static class RangeMaxComparator implements Comparator<Range> {
		public int compare(Range o1, Range o2) {
			return (int)Math.signum(o1.getMax()-o2.getMax());
		}
	}
}
