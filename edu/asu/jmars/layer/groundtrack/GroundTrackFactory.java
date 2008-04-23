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


package edu.asu.jmars.layer.groundtrack;

import java.util.HashMap;
import java.util.Map;

import edu.asu.jmars.layer.LViewFactory;
import edu.asu.jmars.layer.Layer;
import edu.asu.jmars.layer.SerializedParameters;

public class GroundTrackFactory extends LViewFactory {
	static final LayerParams[] SPACECRAFT = {
			new LayerParams(-53, "ODY", "Mars Odyssey"),
			new LayerParams(-94, "MGS", "Mars Global Surveyor"),
			new LayerParams(-41, "MEX", "Mars Express") };

	public GroundTrackFactory() {
		super("Groundtracks", "Orbital tracks showing the sub-spacecraft path");
	}

	/**
	 ** Maps Strings (spacecraft names) to GroundTrackLayers.
	 **/
	private static Map layers;

	public Layer.LView createLView() {
		return null;
	}

	public void createLView(LViewFactory.Callback callback) {
		callback.receiveNewLView(recreateLView(SPACECRAFT[0]));
	}

	public Layer.LView recreateLView(SerializedParameters sp) {
		LayerParams lp = (LayerParams) sp;

		GroundTrackLView view = new GroundTrackLView(lp);
		view.originatingFactory = this;
		return view;
	}

	static class LayerParams implements SerializedParameters {
		int id;
		String craft;
		String desc;

		LayerParams(int id, String craft, String desc) {
			this.id = id;
			this.craft = craft;
			this.desc = desc;
		}

		GroundTrackLayer getLayer() {
			if (layers == null)
				layers = new HashMap();

			GroundTrackLayer layer = (GroundTrackLayer) layers.get(craft);
			if (layer == null)
				layers.put(craft, layer = new GroundTrackLayer(id, this));

			return layer;
		}

		public String toString() {
			return craft + " - " + desc;
		}
	}
}
