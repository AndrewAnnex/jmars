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

import java.util.EventListener;


/**
 * Listener interface for any entity which needs to be notified of 
 * changes to the JMARS configuration parameters at run-time.  It 
 * is the responsibility of the listener to determine what changes,  
 * if any, are relevant and should be acted on.
 * <p>
 * The primary intent of this interface is to provide instances of 
 * {@link edu.asu.jmars.layer.LViewManager} a mechanism with which
 * to notify the {@link edu.asu.jmars.layer.Layer.LView} instances
 * under its management of configuration parameter changes, and/or
 * to facilitate such notification.
 * 
 * @author Joel Hoff MSFF-ASU
 * 
 * @see edu.asu.jmars.util.Config
 */
public interface ConfigListener extends EventListener
{
    /**
     * General notification that changes have been made to
     * the JMARS configuration parameters.
     * 
     * @see edu.asu.jmars.util.Config 
     */
    public void configChanged();
    
    /**
     * Notification that changes of specific relevance to any
     * instance of the specified class have been made to
     * the JMARS configuration parameters.
     * 
     * @param cls Any configuration change listener that is an
     * instance of the specified class should check for and respond
     * to changes in the JMARS configuration.
     * 
     * @see edu.asu.jmars.util.Config 
     */
    public void configChanged(Class cls);
}
