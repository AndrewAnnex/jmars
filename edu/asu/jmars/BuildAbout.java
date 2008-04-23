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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.tools.ant.BuildException;

/**
 * Constructs the about.txt file, which is a simple text file with five lines in
 * the following format:
 * 
 * <pre>
 * human-readable date for when the build was made
 * seconds since start of 1970 to when the build was made
 * total lines of .java files
 * total count of .java files
 * total count of .class files
 * </pre>
 */
public class BuildAbout extends org.apache.tools.ant.Task {
	private String aboutPath;
	public void setAboutPath(String path) {
		aboutPath = path;
	}
	private String buildPath;
	public void setBuildPath(String path) {
		buildPath = path;
	}
	public void execute() throws BuildException {
		try {
			build(aboutPath,buildPath);
		} catch (Exception e) {
			throw new BuildException(e);
		}
	}
	public static void main(String[] args) throws IOException {
		new BuildAbout().build("resources/about_test.txt", ".eclipse_out");
	}
	public void build(String filename, String buildPath) throws IOException {
		PrintWriter writer = new PrintWriter(new FileWriter(new File(filename)));
		Date now = new Date();
		writer.println(new SimpleDateFormat("E MMM d k:m:s z yyyy").format(now));
		writer.println(now.getTime()/1000);
		writer.println(countLines(new File("edu")));
		writer.println(countFiles(new File("edu"), ".java"));
		writer.println(countFiles(new File(buildPath), ".class"));
		writer.flush();
		writer.close();
	}
	private int countFiles(File parent, String suffix) throws FileNotFoundException, IOException {
		int out = 0;
		if (parent.isDirectory()) {
			for (File child: parent.listFiles()) {
				if (child.isDirectory() && !child.getName().startsWith(".")) {
					out += countFiles(child, suffix);
				} else if (child.getName().toLowerCase().endsWith(suffix)) {
					out ++;
				}
			}
		} else {
			out ++;
		}
		return out;
	}
	private int countLines(File file) throws FileNotFoundException, IOException {
		int out = 0;
		if (file.isDirectory()) {
			for (File child: file.listFiles()) {
				if (child.isDirectory() && !child.getName().startsWith(".")) {
					out += countLines(child);
				} else if (child.getName().toLowerCase().endsWith(".java")) {
					out += countLines(new BufferedReader(new FileReader(child)));
				}
			}
		} else {
			out += countLines(new BufferedReader(new FileReader(file)));
		}
		return out;
	}
	private int countLines(BufferedReader reader) throws IOException {
		int out = 0;
		while (reader.readLine() != null) {
			out ++;
		}
		return out;
	}
}
// The original shell script this is based on was, uh, shorter:
//#!/bin/bash
//date > resources/about.txt
//date '+%s' >> resources/about.txt
//find edu -name '*.java' | xargs cat | wc -l >> resources/about.txt
//find edu -name '*.java' | wc -l >> resources/about.txt
//find $(CLSDIR) -name '*.class' | wc -l >> resources/about.txt
