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


package edu.asu.jmars.layer.threed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.vecmath.Color3f;
import javax.vecmath.Vector3f;

import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.util.Util;

public class ThreeDFocus extends FocusPanel {
	static final String MOLA_ALTITUDE_STRING = "MOLA altitude data";
	private final String originalExaggeration = "1.0";
	private ThreeDLView parent;
	private ThreeDLayer myLayer;
	private JDialog viewer;
	private ThreeDCanvas canvasPanel;
	private JPanel controlPanel;
	private JTextField exaggerationText;
	private JButton resampleButton;
	private JButton resetButton;
	private JButton jpegDumpButton;
	private JCheckBox directionalLightCheckBox;
	private ColorButton directionalLightColorButton;
	private DirectionalLightWidget lightDirectionWidget;
	private ColorButton backgroundColorButton;
	private JComboBox layersComboBox;
	private JCheckBox backplaneCheckBox;
	private ListListener listListener = null;
	
	// Attributes that can be stored and retrieved.
	private boolean directionalLightBoolean = false;
	private Vector3f directionalLightDirection = new Vector3f(0.0f, 0.0f, 1.0f);
	private Color directionalLightColor = new Color(128, 128, 128);
	private Color backgroundColor = new Color(0, 0, 0);
	private String zScaleString = "1.0";
	private boolean backplaneBoolean = false;
	
	public ThreeDFocus(ThreeDLayer layer, ThreeDLView parent, ThreeDSettings settings) {
		super(parent);
		
		this.parent = parent;
		this.myLayer = layer;
		
		if (settings != null) {
			initSettings(settings);
		}
		
		canvasPanel = null;
		canvasPanel = new ThreeDCanvas(myLayer, parent);
		
		setUpViews();
		setUpControllers();
	}
	
	/**
	 * called by the focus panel whenever the lview is "cleaned up".
	 */
	public void destroyViewer() {
		if (canvasPanel != null) {

			// remove the listener on the altitude combobox.
			if (parent != null && parent.viewman2 != null
					&& listListener != null) {
				parent.viewman2.listener.deleteObserver(listListener);
			}
			viewer.dispose();
			viewer = null;
		}
	}

	// Sets up the panels and components in the focus panel.
	private void setUpViews() {
		controlPanel = null;
		controlPanel = new JPanel();
		controlPanel.setLayout(new GridBagLayout());
		GridBagConstraints gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.BOTH;
		gc.gridy++;

		JPanel scenePanel = new JPanel();
		scenePanel.setBorder(BorderFactory
				.createTitledBorder("Scene Properties"));
		scenePanel.setLayout(new GridBagLayout());
		GridBagConstraints sceneConstraints = new GridBagConstraints();
		{
			sceneConstraints.gridy++;
			sceneConstraints.gridx = 1;
			sceneConstraints.anchor = GridBagConstraints.WEST;
			backplaneCheckBox = null;
			backplaneCheckBox = new JCheckBox("Bottom", backplaneBoolean);
			backplaneCheckBox.setFocusPainted(false);
			canvasPanel.enableBackplane(backplaneBoolean);
			scenePanel.add(backplaneCheckBox, sceneConstraints);

			sceneConstraints.gridy++;
			sceneConstraints.anchor = GridBagConstraints.WEST;
			sceneConstraints.gridx = 1;
			resampleButton = null;
			resampleButton = new JButton("Update Scene");
			scenePanel.add(resampleButton, sceneConstraints);

			sceneConstraints.gridy++;
			sceneConstraints.anchor = GridBagConstraints.WEST;
			sceneConstraints.gridx = 1;
			backgroundColorButton = null;
			backgroundColorButton = new ColorButton("Background Color",
					backgroundColor);
			canvasPanel.setBackgroundColor(backgroundColor);
			scenePanel.add(backgroundColorButton, sceneConstraints);

			sceneConstraints.gridy++;
			sceneConstraints.anchor = GridBagConstraints.EAST;
			sceneConstraints.gridx = 0;
			scenePanel.add(new JLabel("Z Scale:"), sceneConstraints);
			exaggerationText = null;
			exaggerationText = new JTextField();
			exaggerationText.setDocument(new FloatDocument());
			exaggerationText.setText(zScaleString);
			canvasPanel.setScale(new Float(zScaleString).floatValue());
			Dimension exagDim = new Dimension(80, 20);
			exaggerationText.setPreferredSize(exagDim);
			sceneConstraints.anchor = GridBagConstraints.WEST;
			sceneConstraints.gridx = 1;
			scenePanel.add(exaggerationText, sceneConstraints);

			sceneConstraints.gridy++;
			sceneConstraints.anchor = GridBagConstraints.EAST;
			sceneConstraints.gridx = 0;
			scenePanel.add(new JLabel("Altitude:"), sceneConstraints);
			layersComboBox = null;
			layersComboBox = new JComboBox();
			layersComboBox.addItem(MOLA_ALTITUDE_STRING);
			// Because the viewman does not exist when JMARS is brought up, we can't 
			// access any layers and so cannot populate this combobox. If the layer is
			// added AFTER startup, there is no problem.
			String[] s = getLayerNames();
			if (s.length > 0) {
				for (int i = 0; i < s.length; i++) {
					if (!s[i].equals("3D viewer"))
						layersComboBox.addItem(s[i]);
				}
			}
			canvasPanel.setAltitudeSource(MOLA_ALTITUDE_STRING);
			sceneConstraints.anchor = GridBagConstraints.WEST;
			sceneConstraints.gridx = 1;
			scenePanel.add(layersComboBox, sceneConstraints);
		}
		
		gc.gridx = 0;
		controlPanel.add(scenePanel, gc);

		JPanel orientationPanel = new JPanel();
		orientationPanel.setBorder(BorderFactory
				.createTitledBorder("Orientation"));
		orientationPanel.setLayout(new GridBagLayout());
		GridBagConstraints gc3 = new GridBagConstraints();
		{
			resetButton = null;
			resetButton = new JButton("Reset Camera");
			gc3.gridy++;
			orientationPanel.add(resetButton, gc3);
			JPanel buttonPanel = new JPanel();
			buttonPanel.setLayout(new GridBagLayout());
			GridBagConstraints gc4 = new GridBagConstraints();
			{
				gc4.anchor = GridBagConstraints.EAST;
				gc4.gridx = 0;
				gc4.gridy = 0;
				buttonPanel.add(new JLabel("Drag Left Button:"), gc4);
				gc4.gridy++;
				buttonPanel.add(new JLabel("Shift Drag Left Button:"), gc4);
				gc4.gridy++;
				buttonPanel.add(new JLabel("Drag Right Button:"), gc4);
				gc4.gridy++;
				buttonPanel.add(new JLabel("Shift Drag Right Button:"), gc4);
				gc4.gridy++;
				buttonPanel.add(new JLabel("Middle Button:"), gc4);

				gc4.anchor = GridBagConstraints.WEST;
				gc4.gridx = 1;
				gc4.gridy = 0;
				buttonPanel.add(new JLabel(" rotate x/y"), gc4);
				gc4.gridy++;
				buttonPanel.add(new JLabel(" translate x/y"), gc4);
				gc4.gridy++;
				buttonPanel.add(new JLabel(" rotate z"), gc4);
				gc4.gridy++;
				buttonPanel.add(new JLabel(" zoom"), gc4);
				gc4.gridy++;
				buttonPanel.add(new JLabel(" zoom"), gc4);
			}
			gc3.gridy++;
			orientationPanel.add(buttonPanel, gc3);
		}
		gc.gridx = 0;
		gc.gridy++;
		controlPanel.add(orientationPanel, gc);

		JPanel dirLightPanel = new JPanel();
		dirLightPanel.setBorder(BorderFactory
				.createTitledBorder("Directional Light"));
		dirLightPanel.setLayout(new GridBagLayout());
		GridBagConstraints gc2 = new GridBagConstraints();
		{
			gc2.gridy++;
			directionalLightCheckBox = null;
			directionalLightCheckBox = new JCheckBox("Light on",
					directionalLightBoolean);
			directionalLightCheckBox.setFocusPainted(false);
			canvasPanel.enableDirectionalLight(directionalLightBoolean);
			dirLightPanel.add(directionalLightCheckBox, gc2);
			directionalLightColorButton = null;
			directionalLightColorButton = new ColorButton("Light Color",
					directionalLightColor);
			directionalLightColorButton.setEnabled(directionalLightBoolean);
			canvasPanel.setDirectionalLightColor(directionalLightColor);
			gc2.gridy++;
			dirLightPanel.add(directionalLightColorButton, gc2);
			gc2.gridy++;
			lightDirectionWidget = null;
			lightDirectionWidget = new DirectionalLightWidget();
			lightDirectionWidget.setEnabled(directionalLightBoolean);
			lightDirectionWidget.setColor(new Color3f(directionalLightColor));
			lightDirectionWidget.setPosition(directionalLightDirection.x,
					directionalLightDirection.y);
			canvasPanel.setDirectionalLightDirection(
					directionalLightDirection.x, directionalLightDirection.y,
					directionalLightDirection.z);
			dirLightPanel.add(lightDirectionWidget, gc2);
		}
		gc.gridx = 1;
		gc.gridy = 0;
		gc.gridheight = 2;
		controlPanel.add(dirLightPanel, gc);

		JPanel miscPanel = new JPanel();
		{
			jpegDumpButton = null;
			jpegDumpButton = new JButton("Save");
			miscPanel.add(jpegDumpButton);
		}
		gc.gridy = 2;
		gc.gridx = 0;
		gc.gridheight = 1;
		gc.gridwidth = 2;
		controlPanel.add(miscPanel, gc);

		// set up external viewer.
		viewer = null;
		viewer = new JDialog((Frame) null, "3D View", false);
		Container viewerContentPane = viewer.getContentPane();
		viewerContentPane.add(canvasPanel, BorderLayout.CENTER);
		viewer.pack();
		viewer.setVisible(true);

		// Set up the main pane.
		setLayout(new BorderLayout());
		add(new JScrollPane(controlPanel), BorderLayout.CENTER);
	}

	// Sets up all the controllers for the components.
	private void setUpControllers() {
		// get a new exaggeration scale and apply it.
		exaggerationText.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				zScaleString = exaggerationText.getText();
				canvasPanel.setScale(new Float(exaggerationText.getText())
						.floatValue());
				canvasPanel.refresh();
				viewer.pack();
			}
		});

		// enable or disable the directional light.
		directionalLightCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				directionalLightBoolean = !directionalLightBoolean;
				if (directionalLightBoolean == true) {
					directionalLightColorButton.setEnabled(true);
					lightDirectionWidget.setEnabled(true);
				} else {
					directionalLightColorButton.setEnabled(false);
					lightDirectionWidget.setEnabled(false);
				}
				canvasPanel.enableDirectionalLight(directionalLightBoolean);
				canvasPanel.refresh();
				viewer.pack();
			}
		});

		// enable or disable the backplane.
		backplaneCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				backplaneBoolean = !backplaneBoolean;
				canvasPanel.enableBackplane(backplaneBoolean);
				canvasPanel.refresh();
				viewer.pack();
			}
		});

		// On a re-sample event, just set the scene to "dirty".  The engine
		// deals with actually re-projecting the scene.
		// Note that the NumBack thread controls THIS redrawing.
		resampleButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (canvasPanel.getAltitudeSource()
						.equals(MOLA_ALTITUDE_STRING)) {
					myLayer.setStatus(Color.yellow);
					resampleButton.setEnabled(false);
					parent.setVisible(true);
					parent.setDirty(true);
				} else {
					canvasPanel.updateElevationLayer();
					canvasPanel.refresh();
					viewer.pack();
				}
			}
		});

		// on a reset event, the scene and the widget are both reset.
		resetButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				exaggerationText.setText(originalExaggeration);
				canvasPanel.goHome();
				canvasPanel.refresh();
				viewer.pack();
			}
		});

		// dump the image to a jpeg file.
		jpegDumpButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				canvasPanel.dumpJPG();
			}
		});

		// get a new color for the background and apply it.
		backgroundColorButton.addActionListener(new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				final Color newColor = JColorChooser.showDialog(
						edu.asu.jmars.Main.getLManager(), backgroundColorButton
								.getText(), backgroundColorButton.getColor());
				if (newColor != null) {
					backgroundColor = newColor;
					backgroundColorButton.setColor(newColor);
					canvasPanel.setBackgroundColor(newColor);
				}
			}
		});

		// get a new direction for the DirectionalLight and apply it.
		directionalLightColorButton.addActionListener(new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				final Color newColor = JColorChooser.showDialog(
						edu.asu.jmars.Main.getLManager(),
						directionalLightColorButton.getText(),
						directionalLightColorButton.getColor());
				if (newColor != null) {
					directionalLightColor = newColor;
					directionalLightColorButton.setColor(newColor);
					canvasPanel.setDirectionalLightColor(newColor);
					lightDirectionWidget.setColor(new Color3f(newColor));
				}
			}
		});

		layersComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String altitudeSource = (String) layersComboBox
						.getSelectedItem();
				// altitudeSource is null if removeAllItems() is called on the 
				// comboBox, which it is when there is a change to the view list.
				// So, we gots to check for this.
				if (altitudeSource == null) {
					return;
				}
				canvasPanel.setAltitudeSource(altitudeSource);
				if (altitudeSource.equals(MOLA_ALTITUDE_STRING)) {
					update();
				} else {
					canvasPanel.updateElevationLayer();
					canvasPanel.refresh();
					viewer.pack();
				}
			}
		});

		if (parent != null && parent.viewman2 != null) {
			listListener = new ListListener();
			parent.viewman2.listener.addObserver(listListener);
		}
	}

	// Listens for changes in the LManager's view list.  When such changes occur,
	// the items of the layersComboBox is updated.
	private class ListListener implements Observer {
		public void update(Observable o, Object arg) {

			/**
			 ** Prevents the 3d window from re-appearing when we delete
			 ** the layer. (added by Michael as a quick fix)
			 **/
			if (((ThreeDLView) parent).isDead)
				return;

			// Get the altitude source to be (possibly) restored
			String altitudeSource = (String) layersComboBox.getSelectedItem();

			// update the items of the box with the current view list.
			layersComboBox.removeAllItems();
			layersComboBox.addItem(MOLA_ALTITUDE_STRING);
			String[] s = getLayerNames();
			if (s.length > 0) {
				for (int i = 0; i < s.length; i++) {
					if (!s[i].equals("3D viewer")) {
						layersComboBox.addItem(s[i]);
					}
				}
			}

			// restore the altitude source (if that's possible).
			if (altitudeSource != null) {
				layersComboBox.setSelectedItem(altitudeSource);
			}
		}
	}

	/** This updates the scene with new elevation data. */
	public void update() {
		myLayer.setStatus(Color.yellow);

		// Do whatever needs to be done to re-render the scene
		canvasPanel.updateElevationMOLA();
		canvasPanel.refresh();

		/**
		 ** Prevents the 3d window from re-appearing when we delete
		 ** the layer. (added by Michael as a quick fix)
		 **/
		if (parent.isDead)
			return;

		viewer.pack();
		viewer.setVisible(true);

		// clean up
		resampleButton.setEnabled(true);
		exaggerationText.setEnabled(true);
		parent.setDirty(false);

		myLayer.setStatus(Util.darkGreen);
	}

	// This is an implementation of the abstract LightDirectionWidget class.  When
	// the light is moved around in the widget, the setLightDirection method is called,
	// which then changes the direction of the light in the scene.
	private class DirectionalLightWidget extends LightDirectionWidget {
		void setLightDirection(float x, float y, float z) {
			directionalLightDirection.x = x;
			directionalLightDirection.y = y;
			directionalLightDirection.z = z;
			canvasPanel.setDirectionalLightDirection(x, y, z);
		}
	}

	// An inner class that displays a button and allows the user to change a color of some
	// component or other.  It is used in this application to change the color of the directional
	// light and the color of the background.
	private class ColorButton extends JButton {
		private Color color;

		public ColorButton(String l, Color c) {
			super(l);
			setColor(c);
			setFocusPainted(false);
		}

		// sets the background as the color of the button.  If the color is lighter
		// than gray, then black is used for the color of the button's text instead
		// of white.
		public void setColor(Color c) {
			color = c;
			setBackground(c);
			if ((c.getRed() + c.getGreen() + c.getBlue()) > (128 + 128 + 128)) {
				setForeground(Color.black);
			} else {
				setForeground(Color.white);
			}
		}

		// returns the color that was previously defined.
		public Color getColor() {
			return color;
		}
	}

	private void initSettings(ThreeDSettings s) {
		directionalLightBoolean = s.directionalLightBoolean;
		directionalLightDirection = s.directionalLightDirection;
		directionalLightColor = s.directionalLightColor;
		backgroundColor = s.backgroundColor;
		zScaleString = s.zScaleString;
		backplaneBoolean = s.backplaneBoolean;
	}

	public ThreeDSettings getSettings() {
		ThreeDSettings s = new ThreeDSettings();
		s.directionalLightBoolean = directionalLightBoolean;
		s.directionalLightDirection = directionalLightDirection;
		s.directionalLightColor = directionalLightColor;
		s.backgroundColor = backgroundColor;
		s.zScaleString = zScaleString;
		s.backplaneBoolean = backplaneBoolean;
		return s;
	}

	public String[] getLayerNames() {
		List<String> list = new ArrayList<String>();
		if (parent != null && parent.viewman2 != null) {
			Iterator iter = parent.viewman2.viewList.iterator();
			while (iter.hasNext()) {
				Layer.LView view = (Layer.LView) iter.next();
				list.add(view.getName());
			}
		}
		return list.toArray(new String[0]);
	}
}
