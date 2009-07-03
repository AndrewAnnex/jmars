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


package edu.asu.jmars.layer.map2;

/** Describes a change in a processing pipeline */
public class PipelineEvent {
	private static final long serialVersionUID = 1L;
	public final PipelineProducer source;
	/**
	 * If true, the user has deliberately changed the pipeline, otherwise an
	 * automated change has occurred.
	 */
	public final boolean userInitiated;
	/**
	 * If true, the change is limited to a stage settings change, otherwise
	 * there may have been structural modification and/or settings changes and
	 * any operations that rely on either should reprocess the entire pipeline.
	 */
	public final boolean settingsChange;
	/**
	 * @param source
	 *            The source of the pipeline event, capable of creating a
	 *            duplicate of the pipeline for the receiver's use
	 * @param userInitiated
	 *            If true, the user has deliberately changed the pipeline,
	 *            otherwise an automated change has occurred.
	 * @param settingsChange
	 *            If true, the change is limited to a stage settings change,
	 *            otherwise there may have been structural modification and/or
	 *            settings changes and any operations that rely on either should
	 *            reprocess the entire pipeline.
	 */
	public PipelineEvent(PipelineProducer source, boolean userInitiated, boolean settingsChange) {
		this.source = source;
		this.userInitiated = userInitiated;
		this.settingsChange = settingsChange;
	}
}
