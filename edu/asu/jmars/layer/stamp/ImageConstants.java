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


package edu.asu.jmars.layer.stamp;

/**
 ** Set of constants for tracking or manipulating the current rotation/flip state 
 ** of an image.  It is possible to track the state of an image with
 ** a single variable set to one of these contants ({@link #IMAGE_NORMAL},
 ** {@link #IMAGE_ROTATED_180}, {@link #IMAGE_HFLIPPED}, {@link #IMAGE_VFLIPPED}) 
 ** because of the following commutative operational truths:
 **
 ** - rotation of 180 degrees + horizontal flip  =  vertical flip
 ** - rotation of 180 degrees + vertical flip    =  horizontal flip
 ** - horizontal flip + vertical flip            =  rotation of 180 degrees
 **/
public interface ImageConstants
{
    /**
     ** Image is rotated 180 degrees.
     **/
    static final int IMAGE_ROTATED_180 = 0;

    /**
     ** Image is horizontally flipped.
     **/
    static final int IMAGE_HFLIPPED = 1;

    /**
     ** Image is vertically flipped.
     **/
    static final int IMAGE_VFLIPPED = 2;

    /**
     ** Image is neither rotated nor flipped.
     **/
    static final int IMAGE_NORMAL = 3;

    /**
     ** Mapping from current image state and operation being applied
     ** to resulting image state.  Usage:
     **
     ** result_state = IMAGE_RESULT_MAP[state][operation]
     **/
    static final int[][] IMAGE_RESULT_MAP = new int[][] {
	/* Operation:                   *IMAGE_ROTATED_180* *IMAGE_HFLIPPED*   *IMAGE_VFLIPPED*   */
	/* Start: IMAGE_ROTATED_180 */ { IMAGE_NORMAL,       IMAGE_VFLIPPED,    IMAGE_HFLIPPED},
	/* Start: IMAGE_HFLIPPED    */ { IMAGE_VFLIPPED,     IMAGE_NORMAL,      IMAGE_ROTATED_180},
	/* Start: IMAGE_VFLIPPED    */ { IMAGE_HFLIPPED,     IMAGE_ROTATED_180, IMAGE_NORMAL},
	/* Start: IMAGE_NORMAL      */ { IMAGE_ROTATED_180,  IMAGE_HFLIPPED,    IMAGE_VFLIPPED}
    }; 
}
