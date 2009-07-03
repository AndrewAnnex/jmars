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


package edu.asu.jmars.layer.shape2;

import java.awt.Frame;
import java.io.*;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;

import edu.asu.jmars.Main;

import edu.asu.jmars.layer.util.features.FeatureProvider;

// for maintaining the files used in both SAVING and LOADING.
public class FileChooser extends JFileChooser {
	// Note: the parent directory is maintained so that each time the user
	// selects a file, the directory
	// that was current that last time the chooser was used is brought back up.
	private File startingDir = null;

	private Frame parent = null;

	// Gets file(s) to be used for saving or loading.  "action" should be either "Save" or "Load".
	// If getting a file for loading, more than one file can be returned.  If saving, only one file
	// will be returned.
	public File[] chooseFile(String action) {
		parent = Main.getLManager();
		return chooseFile(action, null);
	}

	public File[] chooseFile(String action, String fileName) {
		// move to the previously used directory if any
		setCurrentDirectory(startingDir);

		// select user specified file
		if (fileName != null) {
			setSelectedFile(new File(fileName));
		}

		// User can load multiple files, but can save only one
		boolean loadMode = action.equals("Load");
		boolean multiSelectionEnabled = loadMode;
		setMultiSelectionEnabled(multiSelectionEnabled);
		
		setApproveButtonText(action);
		setToolTipText(action + " Shape File");
		setDialogTitle(action + " Shape File");

		int chooserResult = loadMode? showOpenDialog(parent): showSaveDialog(parent);

		if (chooserResult != JFileChooser.APPROVE_OPTION) {
			return null;
		}
		if (this.getFeatureProvider() == null) {
			JOptionPane.showMessageDialog(parent, "Must specify a file format!");
			return null;
		}

		// get the file(s), set the new parent (for next time), and return the selected file(s).

		// save this directory for the next time this dialog is popped up
		startingDir = getCurrentDirectory();

		// get selected files and process them one by one
		File[] files = loadMode? getSelectedFiles(): new File[] { getSelectedFile() };

		if (files.length > 0) {
			setSelectedFile(files[0]);

			// Make sure each File has the extension for the selected provider
			String ext = this.getFeatureProvider().getExtension();
			for (int i = 0; i < files.length; i++)
				if (!files[i].getName().endsWith(ext))
					files[i] = new File( files[i].getPath() + ext);
		}

		return files;
	}

	public File getStartingDir() {
		return startingDir;
	}
	
	public void setStartingDir(File dir) {
		if (dir.exists() && dir.isDirectory() && dir.canRead()) {
			startingDir = dir;
		} else {
			throw new IllegalArgumentException("Starting directory must be a readable directory");
		}
	}
	
	/**
	 * Wraps the given feature provider in a Filter for the file chooser, adds
	 * it and returns it so clients can set the current filter if desired
	 */
	public FileFilter addFilter(FeatureProvider fp) {
		UniqueFilter f = new UniqueFilter(fp);
		addChoosableFileFilter(f);
		return f;
	}

	/**
	 * Returns the FeatureProvider selected from the file type combo box.
	 */
	public FeatureProvider getFeatureProvider(){
		FileFilter ff = getFileFilter();
		if (ff instanceof UniqueFilter){
			UniqueFilter uff = (UniqueFilter)ff;
			return uff.getFeatureProvider();
		}
		return null;
	}

	private static class UniqueFilter extends FileFilter {
		private FeatureProvider fp;

		public UniqueFilter(FeatureProvider fp) {
			this.fp = fp;
		}

		public FeatureProvider getFeatureProvider() {
			return fp;
		}

		public boolean accept(File f) {
			String fname = f.getName().toLowerCase();
			return f.isDirectory() || fname.indexOf(fp.getExtension()) != -1;
		}

		public String getDescription() {
			return fp.getDescription();
		}
	}

} // end: class FileChooser
