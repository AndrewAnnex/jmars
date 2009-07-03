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


package edu.asu.jmars.samples.layer.map2.stages.threshold;

import java.awt.BorderLayout;

import edu.asu.jmars.layer.FocusPanel;
import edu.asu.jmars.samples.layer.threshold.ThresholdLView;
import edu.asu.jmars.samples.layer.threshold.ThresholdLayer;

public class ThresholdFocusPanel extends FocusPanel {
	ThresholdLView lview;
	
	public ThresholdFocusPanel(ThresholdLView parent) {
		super(parent);
		this.lview = parent;

		buildUI();
	}

	private void buildUI(){
		setLayout(new BorderLayout());
		add(((ThresholdLayer)lview.getLayer()).getThresholdSettings().createStageView().getStagePanel(), BorderLayout.NORTH);
	}
}
