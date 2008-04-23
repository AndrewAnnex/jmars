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

/**
 * Used sparingly to defer control of internal values when package scoping will
 * not suffice. Roughly analogous usage to the C++ 'friend' operator.
 * @param name The name of the argument to override.
 * @param val The new value to set the argument to. It must be of the expected
 * type.
 * @throws IllegalArgumentException If the name is unknown or the value is of
 * the wrong type.
 */
public interface SetProxy { void set(String name, Object val); }
