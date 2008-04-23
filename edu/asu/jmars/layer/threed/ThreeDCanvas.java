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


/**
 * builds the 3D scene and allows for parameters of that scene to be modified either directly
 * (via attribute writing methods) or indirectly (via rebuilding the scene).
 *
 * @author James Winburn MSFF-ASU  11/04
 */
package edu.asu.jmars.layer.threed;

import edu.asu.jmars.*;
import edu.asu.jmars.layer.*;
import edu.asu.jmars.util.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.image.Raster;
import java.util.*;
import javax.swing.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.universe.*;

public class ThreeDCanvas extends JPanel {
	private static DebugLog log = DebugLog.instance();
	
	// final attributes.
	private final int FIELD_OF_VIEW = 45;
	private final BoundingSphere infiniteBounds = new BoundingSphere(new Point3d(), Double.MAX_VALUE);
	private final float scaleOffset = -0.002f;
	private final Dimension initialSize = new Dimension(400, 400);
	
	// instance attributes
	private ThreeDLayer myLayer = null;
	private Layer.LView parent = null;
	private Canvas3D canvas = null;
	private ElevationModel model = null;
	private PlatformBehavior platform = null;
	private JPanel holdingPanel = null;
	private Color3f backgroundColor = new Color3f(0.0f, 1.0f, 0.0f);
	private Background bg = new Background(backgroundColor);
	private boolean directionalLightEnabled = false;
	private Color3f directionalLightColor = new Color3f(1.0f, 1.0f, 1.0f);
	private Vector3f directionalLightDirection = new Vector3f(0.0f, 0.0f, 1.0f);
	private DirectionalLight directionalLight = new DirectionalLight();
	private String altitudeSource = "";
	private boolean backPlaneEnabled = false;
	private Elevation elevation = null;
	private float scale = scaleOffset;

	// Orientation properties of the platform: this is held separately because 
	// some of the scene modification methods require that the entire scene be
	// rebuilt and we want the orientation to remain consistant from one rebuild
	// to the next.  This also makes sure that the viewers of duplicate 3D layers 
	// aren't linked.
	private PlatformProperties props;

	public ThreeDCanvas(ThreeDLayer layer, Layer.LView parent) {
		myLayer = layer;
		this.parent = parent;
		
		holdingPanel = null;
		holdingPanel = new JPanel();
		holdingPanel.setLayout(new BorderLayout());
		holdingPanel.setBackground(Color.black);
		holdingPanel.setPreferredSize(initialSize);
		setLayout(new BorderLayout());
		add(holdingPanel, BorderLayout.CENTER);

		// Because we repack the frame when we refresh, we need to set the 
		// the preferred size so that refreshing does not set the frame to its
		// initial size as well.
		holdingPanel.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				Dimension d = holdingPanel.getSize();
				holdingPanel.setPreferredSize(d);
			}
		});

		props = new PlatformProperties();
	}

	/**
	 * return to the orientation of the scene when it was first created.
	 */
	public void goHome() {
		platform.goHome();
	}

	/**
	 * This is called by the focus panel whenever new elevation data becomes available.
	 */
	public void updateElevationMOLA() {
		Raster data = myLayer.getElevationData();
		elevation = null;
		elevation = new Elevation(data);
	}

	/**
	 * This is called by the focus panel when non-mola elevation is fetched.
	 */
	public void updateElevationLayer() {
		BufferedImage image = copyLayerWindow(altitudeSource);
		elevation = null;
		if (image != null) {
			elevation = new Elevation(image);
		}
	}

	/**
	 * Some attributes of the scene cannot be maintained dynamically.  Changing any of these
	 * attributes require the scene to be completely rebuilt.
	 */
	public void refresh() {
		if (elevation == null) {
			log.aprintln("elevation is null.  huh?");
			return;
		}

		// we have to set canvas to null to release resources that may 
		// have been allocated to it before. 
		canvas = null;
		canvas = buildCanvas(elevation, parent.viewman2.copyMainWindow(), scale);

		holdingPanel.removeAll();
		holdingPanel.add(canvas, BorderLayout.CENTER);
	}

	/**
	 * sets the source of altitude data.  The scene must be rebuilt.
	 */
	public void setAltitudeSource(String s) {
		altitudeSource = s;
	}

	public String getAltitudeSource() {
		return altitudeSource;
	}

	/**
	 * set the altitudinal "scale" of the scene.  Since scene must be rebuild, we must refresh.
	 */
	public void setScale(float f) {
		scale = f * scaleOffset;
	}

	/**
	 * Set whether or not to use the directional light.  Because of the problem of the material,
	 * we cannot add or not add the light dynamically.  The scene has to be rebuild.
	 */
	public void enableDirectionalLight(boolean b) {
		directionalLightEnabled = b;
	}

	/**
	 * sets the direction of the DirectionalLight.  This can be done dynamically.
	 */
	public void setDirectionalLightDirection(float x, float y, float z) {
		directionalLightDirection.set(x, y, z);
		directionalLight.setDirection(directionalLightDirection);
	}

	/**
	 * sets the color of the DirectionalLight. This can be done dynamically.
	 */
	public void setDirectionalLightColor(Color c) {
		directionalLightColor = null;
		directionalLightColor = new Color3f(c);
		directionalLight.setColor(directionalLightColor);
	}

	/**
	 * sets the color of the background.  This can be done dynamically.
	 */
	public void setBackgroundColor(Color c) {
		backgroundColor = null;
		backgroundColor = new Color3f(c);
		bg.setColor(backgroundColor);
	}

	public void enableBackplane(boolean b) {
		backPlaneEnabled = b;
	}

	/**
	 * dumps the contents of the image to an external and user specified JPEG file.
	 */
	String startingDir = null;

	public void dumpJPG() {
		String filename = null;
		FileDialog fdlg = new FileDialog(Main.mainFrame,
				"Capture to JPEG File", FileDialog.SAVE);
		if (startingDir != null) {
			fdlg.setDirectory(startingDir);
		}
		fdlg.setVisible(true);
		if (fdlg.getFile() != null) {
			startingDir = fdlg.getDirectory();
			filename = fdlg.getDirectory() + fdlg.getFile();
		}
		if (filename == null) {
			return;
		}

		int width = (int) canvas.getSize().getWidth();
		int height = (int) canvas.getSize().getHeight();

		GraphicsContext3D ctx = canvas.getGraphicsContext3D();
		javax.media.j3d.Raster ras = new javax.media.j3d.Raster(new Point3f(
				-1.0f, -1.0f, -1.0f), javax.media.j3d.Raster.RASTER_COLOR, 0,
				0, width, height, new ImageComponent2D(
						ImageComponent.FORMAT_RGB, new BufferedImage(width,
								height, BufferedImage.TYPE_INT_RGB)), null);
		ctx.readRaster(ras);

		// Now strip out the image info
		BufferedImage bi = ras.getImage().getImage();
		Util.saveAsJpeg(bi, filename);

	}

	// build the scene and display it.
	private Canvas3D buildCanvas(Elevation elevation, BufferedImage image,
			float scale) {
		Canvas3D can = new Canvas3D(SimpleUniverse.getPreferredConfiguration());
		SimpleUniverse universe = new SimpleUniverse(can);
		BranchGroup world = new BranchGroup();
		Appearance appearance = new Appearance();

		// lights!
		// the Java 3D model requires that a material must be defined for a directional light,
		// or it doesn't show up.  Unfortunately (and I think this is a bug), if there is NO 
		// light defined and a material is defined, the scene is black. So, each time we 
		// build a canvas, we have to create a new appearance with or without a material 
		// based on whether there is or is not a light defined. 
		// Note that we can still have light color and direction changed dynamically.
		if (directionalLightEnabled == true) {
			directionalLight = null;
			directionalLight = new DirectionalLight();
			directionalLight
					.setCapability(DirectionalLight.ALLOW_DIRECTION_WRITE);
			directionalLight.setCapability(DirectionalLight.ALLOW_COLOR_WRITE);
			directionalLight.setDirection(directionalLightDirection);
			directionalLight.setColor(directionalLightColor);
			directionalLight.setInfluencingBounds(infiniteBounds);
			world.addChild(directionalLight);

			Material mat = new Material();
			mat.setShininess(0.0f);
			mat.setAmbientColor(0.0f, 0.0f, 0.0f);
			mat.setSpecularColor(1.0f, 1.0f, 1.0f);
			appearance.setMaterial(mat);
		}

		// Camera!  (well, ok, background.)
		bg = null;
		bg = new Background(backgroundColor);
		bg.setCapability(Background.ALLOW_COLOR_WRITE);
		bg.setApplicationBounds(infiniteBounds);
		world.addChild(bg);

		// set up the backside
		if (backPlaneEnabled && elevation != null) {
			world
					.addChild(new BacksidePlane((float) elevation.getWidth(),
							(float) elevation.getHeight(), elevation.getMean()
									* scale));
		} else {
			PolygonAttributes pa = new PolygonAttributes();
			pa.setCullFace(PolygonAttributes.CULL_NONE);
			appearance.setPolygonAttributes(pa);
		}

		// Action!! (build model)
		model = null;
		model = new ElevationModel(elevation, image, scale, appearance);
		world.addChild(model);
		world.compile();

		// Set the world into the universe.
		universe.addBranchGraph(world);

		// Step back a bit.
		View view = universe.getViewer().getView();
		view.setFrontClipDistance(1);
		view.setBackClipDistance(model.getModelLength() * 2);
		view.setFieldOfView(Math.toRadians(FIELD_OF_VIEW));

		// Set the behavior of the universe
		// Note that the Orientation Properties of the platform (as they currently
		// stand) and stuffed into the "new" object.  This makes sure that the 
		// orientation does not get reset every time we have to rebuild.
		platform = null;
		platform = new PlatformBehavior(can, props);
		platform.setSchedulingBounds(infiniteBounds);
		universe.getViewingPlatform().setViewPlatformBehavior(platform);

		// display the newly built scene.
		platform.integrateTransforms();

		return can;
	}

	/**
	 * dumps the contents of the viewer to the specified filename as a jpeg.
	 */
	public BufferedImage copyLayerWindow(String layerName) {
		if (parent == null || parent.viewman2 == null) {
			return null;
		}

		Dimension pixSize = parent.viewman2.getProj().getScreenSize();
		BufferedImage image = Util.newBufferedImageOpaque((int) pixSize
				.getWidth(), (int) pixSize.getHeight());
		Graphics2D g2 = image.createGraphics();

		Iterator iter = parent.viewman2.viewList.iterator();
		while (iter.hasNext()) {
			Layer.LView view = (Layer.LView) iter.next();
			if (view.isVisible() && view.getName().equals(layerName)) {
				view.realPaintComponent(g2);
			}
		}

		g2.dispose();
		return image;
	}

}
