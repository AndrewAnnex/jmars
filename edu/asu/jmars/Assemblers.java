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


package edu.asu.jmars;

public class Assemblers
 {
	private static int current = 0;
	public static String getOne()
	 {
		return  list[Math.min(current++, list.length-1)];
	 }

	private static String[] list =
	{
		"tiny little martian hands",
		"highly evolved primates",
		"domesticated amoebas",
		"genetically engineered fruit flies",
		"a flock of pigeons",
		"skilled Jedi warriors",
		"a pair of circus ninjas",
		"three blind mice",
		"four mutant turtles",
		"five rabid fish",
		"Bill Gates",
		"Tori Amos",
		"Fatboy Slim",
		"Vogons",
		"a swarm of albino fleas",
		"rabid vampires",
		"a cadre of elephants",
		"a fun makefile",
		"Michael Weiss-Malik",
		"Ben Steinberg",
		"Saadat Anwar",
		"Steve Henrie",
		"Noel Gorelick",
        "James Winburn",
        "Dennis Skudney",
        "Joel Hoff",
		"sleep-deprived programmers"
	};
 }
