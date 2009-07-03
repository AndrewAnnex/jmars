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

import edu.asu.jmars.Main;
import edu.asu.jmars.util.Base64Codec;

/**
 * This class will encode and decode the userid and password in order to
 * serialize and store the object to a file for session restoration. Please
 * do not make these variables public - only use the access methods.
 */
public class UserPasswordParameterBlock extends DialogParameterBlock
{
	public final String defaultUser = Main.USER;
	public final String defaultPassword = Main.PASS;

	private String		userId;
	private String		password;

	public void setUserId(String u) {
		userId = Base64Codec.getInstance().encode(u);
	}

	public void setPassword(String p) {

		password = Base64Codec.getInstance().encode(p);

	}

	public String getUserId() {

		return Base64Codec.getInstance().decode(userId);

	}

	public String getPassword() {

		return Base64Codec.getInstance().decode(password);

	}
}
