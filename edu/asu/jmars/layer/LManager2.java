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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.basic.BasicMenuUI;
import javax.swing.plaf.metal.MetalComboBoxIcon;

import org.apache.commons.collections.ReferenceMap;

import edu.asu.jmars.Main;
import edu.asu.jmars.layer.Layer.LView;
import edu.asu.jmars.swing.DockableTabs;
import edu.asu.jmars.util.Config;
import edu.asu.jmars.util.DebugLog;
import edu.asu.jmars.util.MovableList;
import edu.asu.jmars.util.Util;

/**
 * The LManager is a JFrame GUI that provides the user with an interface to
 * manipulate the position and visibility of existing layer views. The
 * functionality of this interface will increase to include: Addition and
 * deletion of layerviews Other??
 * 
 * The LManager is a JFrame which contains a set of LPanels. LPanels are derived
 * from JPanels and contain a set of other J-components (ie JButton, JLabels,
 * JRadioButton) which provide the "interactivity" for the user to manipulate
 * the the layerview. The components and their functions are subject to change
 * as funtional needs become defined
 */
public class LManager2 extends LManager implements LViewFactory.Callback {
	private static final DebugLog log = DebugLog.instance();

	private MovableList viewList;
	private DockableTabs dockTabs;
	private JMenu layersMenu;
	private JMenu tooltipsMenu;
	private MainPanel mainPanel;

	public LManager2(LViewManager viewman) {
		super("Layer Manager", Util.getAltDisplay());

		this.viewList = viewman.viewList;
		this.mainPanel = new MainPanel();

		viewman.lmanager = this;

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				setVisible(false);
				Main.sayGoodbyeToLManager();
			}
		});

		rebuildTabs();
		rebuildMenus();
		setSize(300, 400);
		setVisible(true);
	}

	public void removeView(Layer.LView view) {
		mainPanel.actDelete.actionPerformed(null);
	}

	public JMenu getLayersMenu() {
		return layersMenu;
	}

	public JMenu getTooltipsMenu() {
		return tooltipsMenu;
	}

	public void receiveNewLView(Layer.LView view) {
		log.println(view);
		dockTabs.addTab(getUniqueName(view), view.light2, view.getFocusPanel(),
				null);
		viewList.add(view);
		view.setVisible(view.mainStartEnabled(), view.pannerStartEnabled());
		mainPanel.addView(view);
		setActiveLView(viewList.size() - 1);
		updateLabels();
	}

	private final Map<String, LView> nameToLayer = new ReferenceMap(
			ReferenceMap.HARD, ReferenceMap.WEAK);
	private final Map<LView, String> layerToName = new ReferenceMap(
			ReferenceMap.WEAK, ReferenceMap.HARD);

	/**
	 * Returns a unique name for this view. Layers are disambiguated by by
	 * adding a suffix like ' (2)', ' (3)', etc. The numbers are added in the
	 * order receiveNewLView is called.
	 */
	public String getUniqueName(LView newView) {
		String newName = newView.getName();
		String oldName = layerToName.get(newView);
		if (oldName != null) {
			// there is an existing entry for this layer
			if (oldName.equals(newName)
					|| (oldName.startsWith(newName) && oldName.substring(
							newName.length()).matches(" ([0-9]+)$"))) {
				// the existing layer name has the same prefix so reuse it
				return oldName;
			} else {
				// the prefix changed so remove the layer here, and reinsert it
				// below
				nameToLayer.remove(layerToName.remove(newView));
			}
		}
		// at this point we must find a new name for this layer
		LView used = nameToLayer.get(newName);
		if (used == null) {
			// name is not in use
			nameToLayer.put(newName, newView);
			layerToName.put(newView, newName);
			return newName;
		} else {
			// name is in use by another layer. look for alternate endings that
			// are not used, but don't look past 50 so we can guarantee the
			// search ends quickly
			for (int suffix = 2; suffix <= 50; suffix++) {
				String suffixName = newName + " (" + suffix + ")";
				if (!nameToLayer.containsKey(suffixName)) {
					nameToLayer.put(suffixName, newView);
					layerToName.put(newView, suffixName);
					return suffixName;
				}
			}
			// if we got here there were MANY views with the same name
			return newName + " (...)";
		}
	}

	public void updateLabels() {
		for (int i = 0; i < mainPanel.rows.size(); i++) {
			Row r = (Row) mainPanel.rows.get(i);
			String name = getUniqueName(r.view);
			r.label.setText(" " + name);
			r.label.setToolTipText(" " + name);
			dockTabs.setTitleAt(mainPanel.rows.size() - i, name);
		}
		rebuildMenus();
	}

	public void updateVis() {
		for (Row r : (List<Row>) mainPanel.rows) {
			r.btnM.setSelected(r.view.isVisible());
			r.btnP.setSelected(r.view.getChild().isVisible());
		}
	}

	public void updateStatusOf(Layer.LView view, Icon icon) {
		int viewIdx = viewList.indexOf(view);
		int tabIdx = viewIdx + 1;

		if (tabIdx == -1 || viewIdx == -1)
			return;

		if (dockTabs.getIconAt(tabIdx) != icon)
			dockTabs.setIconAt(tabIdx, icon);
		else
			dockTabs.repaintIconAt(tabIdx);
	}

	private void rebuildMenus() {
		// Rebuild the base of the layers menu
		if (layersMenu == null)
			layersMenu = new JMenu("Layer Manager Tabs");
		else {
			layersMenu.setPopupMenuVisible(false);
			layersMenu.removeAll();
		}

		layersMenu.add(new JMenuItem(new AbstractAction("Dock all tabs") {
			public void actionPerformed(ActionEvent e) {
				rebuildTabs();
			}
		}));

		layersMenu.add(new JSeparator());

		layersMenu.add(new JMenuItem(new AbstractAction("Show Main Tab") {
			public void actionPerformed(ActionEvent e) {
				dockTabs.activateTab(0);
			}
		}) {
			{
				setAccelerator(KeyStroke.getKeyStroke("F1"));
			}
		});

		// Rebuild the base of the tooltips menu
		if (tooltipsMenu == null)
			tooltipsMenu = new JMenu("Tool Tips");
		else {
			tooltipsMenu.setPopupMenuVisible(false);
			tooltipsMenu.removeAll();
		}
		tooltipsMenu.add(new JCheckBoxMenuItem("Disable ALL tooltips",
				!ToolTipManager.sharedInstance().isEnabled()) {
			{
				addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						ToolTipManager.sharedInstance().setEnabled(
								!isSelected());
						for (int i = 2; i < tooltipsMenu.getItemCount(); i++)
							tooltipsMenu.getItem(i).setEnabled(!isSelected());
					}
				});
			}
		});
		tooltipsMenu.add(new JSeparator());

		for (int i = 0; i < Math.min(viewList.size(), 11); i++) {
			final int ii = i;
			final Layer.LView view = getView(ii);

			layersMenu.add(new JMenuItem(new AbstractAction("Activate "
					+ getUniqueName(view)) {
				public void actionPerformed(ActionEvent e) {
					dockTabs.activateTab(ii + 1);
					setActiveLView(ii);
				}
			}) {
				{
					setAccelerator(KeyStroke.getKeyStroke("F" + (ii + 2)));
				}
			});
			tooltipsMenu.add(new JCheckBoxMenuItem("Disable for "
					+ getUniqueName(view), view.tooltipsDisabled()) {
				{
					addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							view.tooltipsDisabled(isSelected());
						}
					});
				}
			});
		}
	}

	public void setActiveLView(int newIdx) {
		selectedIdx = newIdx;
		mainPanel.updateRows();
		// mainPanel.repaint();
		Layer.LView view = getActiveLView();
		if (view != null)
			view.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
	}

	private void setActiveLView(Layer.LView view) {
		int idx = viewList.indexOf(view);
		setActiveLView(idx);
	}

	public Layer.LView getActiveLView() {
		if (!Util.between(0, selectedIdx, viewList.size() - 1))
			return null;
		return getView(selectedIdx);
	}

	private void rebuildTabs() {
		if (dockTabs != null)
			dockTabs.removeAll();
		dockTabs = new MyDockableTabs();
		getContentPane().removeAll();
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(dockTabs.getMainTabbedPane(), BorderLayout.CENTER);
	}

	private Layer.LView getView(int idx) {
		return (Layer.LView) viewList.get(idx);
	}

	public void refreshAddMenu() {
		mainPanel.rebuildAddMenu();
	}

	private class MainPanel extends JLayeredPane {
		Row dragRow;
		int dragPos;
		int dragSrc;
		int dragDst;
		List rows = new ArrayList();
		JMenuBar btnAdd;
		JButton btnDel;

		JLayeredPane rowsPanel = new JLayeredPane() {
			public void layout() {
				if (rows.isEmpty())
					return;

				Insets insets = rowsPanel.getInsets();
				int h = ((Row) rows.get(0)).getPreferredSize().height;
				int w = rowsPanel.getWidth() - insets.left - insets.right;

				for (int i = 0; i < rows.size(); i++) {
					Row r = (Row) rows.get(i);
					int y;
					if (i == dragSrc)
						y = dragPos;
					else if (i > dragSrc && i <= dragDst)
						y = h * (i - 1);
					else if (i < dragSrc && i >= dragDst)
						y = h * (i + 1);
					else
						y = h * i;
					r.setSize(w, h);
					r.setLocation(insets.left, insets.top + y);
				}
			}
		};

		void rebuildAddMenu() {
			JMenu menu = new JMenu("Add new layer");
			MultiFactory.addAllToMenu(LManager2.this, menu,
					LViewFactory.factoryList2);

			menu.setUI(new BasicMenuUI() {
				protected void installDefaults() {
					super.installDefaults();
					selectionBackground = UIManager.getColor("Button.select");
				}
			});
			menu.setBorder(UIManager.getBorder("Button.border"));
			menu.setIcon(new MetalComboBoxIcon());

			if (Config.get("lmanager.flatten", false)) {
				menu.removeAll();
				Iterator i = LViewFactory.factoryList.iterator();
				while (i.hasNext())
					menu.add(new JMenuItem(i.next().toString()));
			}

			btnAdd.removeAll();
			btnAdd.add(menu);
		}

		MainPanel() {
			setDragOffset(0);

			rowsPanel.setLayout(null);
			for (int i = 0; i < viewList.size(); i++)
				addView(getView(i));

			btnAdd = new JMenuBar();
			btnAdd.setBorder(null);
			rebuildAddMenu();

			btnDel = new JButton(actDelete);
			btnDel.setFocusable(false);

			Dimension d1 = btnAdd.getPreferredSize();
			Dimension d2 = btnDel.getPreferredSize();
			Dimension d = new Dimension(Math.max(d1.width, d2.width), Math.max(
					d1.height, d2.height));
			setAll(d, btnAdd.getMenu(0));
			setAll(d, btnDel);

			setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();

			c.insets = new Insets(10, 5, 0, 5);
			c.weightx = 1;
			c.anchor = GridBagConstraints.EAST;
			add(btnAdd, c);

			c.anchor = GridBagConstraints.WEST;
			add(btnDel, c);

			c.gridy = 1;
			c.gridwidth = 2;
			c.insets = new Insets(10, 0, 8, 0);
			c.anchor = GridBagConstraints.CENTER;
			c.fill = GridBagConstraints.BOTH;
			add(new JSeparator(), c);

			c.insets = new Insets(0, 5, 5, 5);
			c.gridy = 2;
			c.weighty = 1;
			add(rowsPanel, c);
		}

		Action actDelete = new AbstractAction("Delete layer") {
			public void actionPerformed(ActionEvent e) {

				LView view = (LView) viewList.get(selectedIdx);

				if (view.deleted()) {
					// Remove the actual view
					viewList.remove(selectedIdx);
					nameToLayer.remove(layerToName.remove(view));
					Main.testDriver.repaint();

					// Remove the tab
					dockTabs.remove(selectedIdx + 1);

					// Remove the row... the row indices are reversed
					// relative to everything else.
					Row r = (Row) rows.remove(rows.size() - 1 - selectedIdx);
					rowsPanel.remove(r);
					rowsPanel.repaint();

					// Finally: update the selected index, if we deleted
					// the highest one.
					if (selectedIdx >= rows.size())
						setActiveLView(rows.size() - 1);
					else
						updateRows();
					rebuildMenus();
				}
			}
		};

		void addView(Layer.LView view) {
			Row r = new Row(view, rowMouseHandler);
			rowsPanel.add(r);
			rows.add(0, r);
		}

		void updateRows() {
			for (Iterator i = rows.iterator(); i.hasNext();)
				((Row) i.next()).updateRow();
		}

		void setDragOffset(int dragOffset) {
			if (dragOffset == 0 || rows.isEmpty()) {
				dragSrc = -1;
				dragDst = -1;
				dragPos = -1;
			} else {
				int h = ((Row) rows.get(0)).getPreferredSize().height;
				dragSrc = rows.indexOf(dragRow);
				dragPos = Util.bound(0, h * dragSrc + dragOffset, h
						* (rows.size() - 1));
				dragDst = (dragPos + h / 2 - 1) / h;
			}
		}

		MouseInputListener rowMouseHandler = new MouseInputAdapter() {
			int pressed;

			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() != 2)
					return;

				Component c = e.getComponent();
				Row r = (Row) (c instanceof Row ? c : c.getParent());
				int idx = rows.indexOf(r);
				dockTabs.activateTab(rows.size() - idx);
			}

			public void mousePressed(MouseEvent e) {
				Component c = e.getComponent();
				dragRow = (Row) (c instanceof Row ? c : c.getParent());
				if (DARK_DRAG) {
					dragRow.setBackground(darkColor);
					dragRow.slider.setBackground(darkColor);
				}
				setDragOffset(0);
				pressed = screenY(e);
				rowsPanel.moveToFront(dragRow);
				setActiveLView(dragRow.view);
			}

			public void mouseDragged(MouseEvent e) {
				setDragOffset(screenY(e) - pressed);
				rowsPanel.invalidate();
				rowsPanel.repaint();
			}

			public void mouseReleased(MouseEvent e) {
				if (dragSrc != dragDst) {
					int dragSrcRev = rows.size() - 1 - dragSrc;
					int dragDstRev = rows.size() - 1 - dragDst;

					// Move the user-visible row, the actual view list
					// order, and the focus tabs. The latter two are
					// in reverse order from the first one.
					rows.add(dragDst, rows.remove(dragSrc));
					viewList.move(dragSrcRev, dragDstRev);
					dockTabs.moveTab(dragSrcRev + 1, dragDstRev + 1);
					setActiveLView(dragDstRev);
					rebuildMenus();
				}
				if (DARK_DRAG) {
					dragRow.setBackground(lightColor);
					dragRow.slider.setBackground(lightColor);
				}
				dragRow = null;
				setDragOffset(0);
				rowsPanel.invalidate();
				rowsPanel.repaint();
			}
		};
	}

	private static final Color FOCUS_COLOR = UIManager
	.getColor("ToggleButton.focus");

	private static final boolean DARK_DRAG = Config.get("lmanager.darkdrag",
			false);

	private static final Color darkColor = UIManager
	.getColor("ToggleButton.select");
	private final Color lightColor = getBackground();

	private static Border selectedBorder = new SoftBevelBorder(Config.get(
			"lmanager.raised", true) ? BevelBorder.RAISED : BevelBorder.LOWERED);
	private static Border unselectedBorder = new EmptyBorder(selectedBorder
			.getBorderInsets(null));
	private static Border selectedBorder2 = new LineBorder(Color.blue, // FOCUS_COLOR
			// ,
			selectedBorder.getBorderInsets(null).top);
	private int selectedIdx = 0;

	private static int screenY(MouseEvent e) {
		Point pt = e.getPoint();
		SwingUtilities.convertPointToScreen(pt, e.getComponent());
		return pt.y;
	}

	private class Row extends JPanel {
		static final int PAD = 2;

		final Layer.LView view;
		JComponent light;
		HiddenToggleButton btnM;
		HiddenToggleButton btnP;
		JLabel label;
		JSlider slider;

		Row(final Layer.LView view, MouseInputListener rowMouseHandler) {
			super(new GridBagLayout());

			this.view = view;
			this.light = view.light;
			this.btnM = new HiddenToggleButton("M", view.isVisible());
			this.btnP = new HiddenToggleButton("P", view.getChild().isVisible());
			this.label = new JLabel(" " + getUniqueName(view));
			this.slider = new JSlider(0, 1000);

			slider.setFocusable(false);
			slider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					view.setAlpha((float) slider.getValue() / slider.getMaximum());
					slider.setToolTipText("Opacity: " + (int)Math.round(slider.getValue()*100d / slider.getMaximum()) + "%");
				}
			});
			slider.setValue((int) (view.getAlpha() * 1000));
			
			label.addMouseListener(rowMouseHandler);
			label.addMouseMotionListener(rowMouseHandler);

			addMouseListener(rowMouseHandler);
			addMouseMotionListener(rowMouseHandler);

			prepare(btnM);
			prepare(btnP);

			int btnHeight = Math.max(btnM.getPreferredSize().height, btnM
					.getPreferredSize().width);
			btnHeight = Math.max(btnHeight, slider.getPreferredSize().height);
			Dimension d = new Dimension(btnHeight, btnHeight);

			setAll(d, light);

			d.height += PAD * 2;
			d.width += PAD * 2;
			setAll(d, btnM);
			setAll(d, btnP);
			d.height -= PAD * 2;

			d.width = 100;
			setAll(d, slider);

			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			c.weighty = 1;

			Insets saved = c.insets;
			c.insets = new Insets(PAD, PAD * 2, PAD, PAD);
			add(view.light, c);
			c.insets = new Insets(0, 0, 0, 0);
			add(btnM, c);
			add(btnP, c);
			c.insets = saved;
			c.weightx = 1;
			add(label, c);
			c.weightx = 0;
			c.fill = GridBagConstraints.NONE;
			c.anchor = GridBagConstraints.SOUTH;
			c.insets = new Insets(0, 0, 0, PAD);
			add(slider, c);
			updateRow();
		}

		void updateRow() {
			setBorder(view == getActiveLView() ? selectedBorder2
					: unselectedBorder);
		}

		void prepare(JToggleButton btn) {
			btn.setMargin(new Insets(0, 0, 0, 0));
			// btn.setInsets(new Insets(2, 2, 2, 2));
			// log.aprintln(btn.getBorder());
			// btn.setBorder(null);
			btn.setFocusable(false);
			// btn.addMouseListener(hiliteHandler);
			btn.addActionListener(visibilityHandler);
		}
	}

	private static void setAll(Dimension d, JComponent c) {
		d = new Dimension(d);
		c.setMinimumSize(d);
		c.setMaximumSize(d);
		c.setPreferredSize(d);
	}

	private ActionListener visibilityHandler = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			JToggleButton btn = (JToggleButton) e.getSource();
			Row row = (Row) btn.getParent();
			Layer.LView view = row.view;
			if (btn.getText().equals("P"))
				view = view.getChild();
			view.setVisible(btn.isSelected());
		}
	};

	private class MyDockableTabs extends DockableTabs {
		MyDockableTabs() {
			super(1); // 1 -> freeze the main tab

			addTab("Main", null, mainPanel, null);
			for (int i = 0; i < viewList.size(); i++) {
				Layer.LView view = getView(i);
				addTab(getUniqueName(view), view.light2, view.getFocusPanel(),
						null);
			}
		}

		MouseListener tabDoubleClickHandler = new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() != 2)
					return;

				int tab = dockTabs.indexAtLocation(e);

				if (tab == 0) // double-clicking main causes rebuild
					rebuildTabs();
				else if (tab > 0)
					setActiveLView(tab - 1);
			}
		};

		protected void prepareTabbedPane(JTabbedPane pane) {
			// Cause double-clicks on a tab to set the active layer
			pane.addMouseListener(tabDoubleClickHandler);
		}
	};

	/**
	 * Provides a toggle button that uses borders to indicate mouse-over, and
	 * that stores the isSelected property on the LView's isVisible property.
	 */
	private static class HiddenToggleButton extends JToggleButton {
		public static final Color DEFAULT_BACK = UIManager.getColor("Button.background");
		public static final Border borderNone = BorderFactory.createLineBorder(DEFAULT_BACK, 2);
		public static final Border borderRaised = BorderFactory.createCompoundBorder(borderNone, new LineBorder(Color.black, 1));
		public static final Border borderLowered = BorderFactory.createCompoundBorder(borderNone,new LineBorder(Color.black, 1));

		private Border realBorder;
		private boolean borderActive;

		public HiddenToggleButton(String text, boolean selected) {
			super(text, selected);
			addActionListener(borderChanger);
			addMouseListener(borderActivator);
			// replace the background color (which is a subclass of Color
			// implementing the UIResource interface) with a Color instance that
			// does not implement it, so the JButton UI painter won't fill
			// gradients
			Color b = getBackground();
			setBackground(new Color(b.getRed(), b.getGreen(), b.getBlue(), b.getAlpha()));
			setBorder(borderNone);
			this.realBorder = selected ? borderLowered : borderRaised;
		}

		public void updateBorder() {
			realBorder = isSelected() ? borderLowered : borderRaised;
			setBorder(borderActive ? realBorder : borderNone);
		}

		private static final ActionListener borderChanger = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				((HiddenToggleButton) e.getSource()).updateBorder();
			}
		};

		private static final MouseListener borderActivator = new MouseAdapter() {
			public void mouseEntered(MouseEvent e) {
				HiddenToggleButton btn = (HiddenToggleButton) e.getSource();
				btn.borderActive = true;
				btn.updateBorder();
			}

			public void mouseExited(MouseEvent e) {
				HiddenToggleButton btn = (HiddenToggleButton) e.getSource();
				btn.borderActive = false;
				btn.updateBorder();
			}
		};
	}

	private MouseListener hiliteHandler = new MouseAdapter() {
		Border hilited = new SoftBevelBorder(BevelBorder.RAISED);
		Border pressed = new SoftBevelBorder(BevelBorder.LOWERED);
		Border unhilited = BorderFactory.createLineBorder(getBackground(),
				hilited.getBorderInsets(null).top);

		// Border unhilited = new EmptyBorder(hilited.getBorderInsets(null));

		public void mouseEntered(MouseEvent e) {
			setBorder(e, hilited);
		}

		public void mousePressed(MouseEvent e) {
			setBorder(e, pressed);
		}

		public void mouseReleased(MouseEvent e) {
			setBorder(e, hilited);
		}

		public void mouseExited(MouseEvent e) {
			setBorder(e, unhilited);
		}

		void setBorder(MouseEvent e, Border b) {
			((JComponent) e.getComponent()).setBorder(b);
		}
	};

	/**
	 ** Returns a string representing the docking states of the LManager tabs,
	 * which can be fed to {@link #setDockingStates}.
	 **/
	public String getDockingStates() {
		Rectangle[] states = dockTabs.getDockingStates();

		Properties prop = new Properties();
		prop.setProperty("lmanagerVersion", "1");
		prop.setProperty("viewCount", Integer.toString(viewList.size()));
		for (int i = 0; i < viewList.size(); i++) {
			Layer.LView view = getView(i);
			prop.setProperty("view" + i + ".title", getUniqueName(view));
			prop.setProperty("view" + i + ".type", view.getClass().getName());
			if (states[i + 1] != null)
				prop.setProperty("view" + i + ".bounds", states[i + 1].x + ","
						+ states[i + 1].y + "," + states[i + 1].width + ","
						+ states[i + 1].height);
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			prop.store(out, null);
		} catch (IOException e) {
			log.aprintln(e);
			throw new RuntimeException(
					"IMPOSSIBLE: I/O error writing to a string!", e);
		}

		String stateString = out.toString();

		log.println("--- Saving as:");
		log.println(stateString);

		return stateString;
	}

	/**
	 ** Restores the lmanager tabs' docking states to those stored in a string
	 * previously created from {@link #getDockingStates}.
	 **/
	public void setDockingStates(String stateString) {
		log.println("--- Restoring from:");
		log.println(stateString);

		if (stateString == null)
			return;

		Properties prop = new Properties();
		try {
			prop.load(new ByteArrayInputStream(stateString.getBytes()));
		} catch (Exception e) {
			log.aprintln(e);
			log.aprintln("Failed to set layer manager tab positions");
			return;
		}

		Rectangle[] states = new Rectangle[viewList.size() + 1];

		log.println("Opening version " + prop.getProperty("lmanagerVersion"));
		int viewCount = Integer.parseInt(prop.getProperty("viewCount"));
		for (int i = 1, viewIdx = 0; i < states.length
		&& viewIdx < viewList.size(); i++, viewIdx++) {
			if (viewCount != viewList.size())
				while (titleAndTypeMismatch(viewIdx, prop))
					if (viewIdx < viewCount) {
						log.println("Skipped saved tab " + viewIdx);
						++viewIdx;
					} else {
						log.println("Skipped past the end!");
						return;
					}

			String boundStr = prop.getProperty("view" + viewIdx + ".bounds");
			if (boundStr != null) {
				String[] boundA = boundStr.split(",");
				states[i] = new Rectangle(Integer.parseInt(boundA[0]), Integer
						.parseInt(boundA[1]), Integer.parseInt(boundA[2]),
						Integer.parseInt(boundA[3]));
			}
		}

		dockTabs.setDockingStates(states);
	}

	private boolean titleAndTypeMismatch(int viewIdx, Properties prop) {
		if (viewIdx >= viewList.size())
			return false;

		String key = "view" + viewIdx + ".";
		Layer.LView v = getView(viewIdx);
		return !v.getName().equals(prop.getProperty(key + "title"))
			|| !v.getClass().getName().equals(prop.getProperty(key + "type"));
	}
}
