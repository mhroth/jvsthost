/*
 *  Copyright 2007 - 2009 Martin Roth (mhroth@gmail.com)
 *                        Matthew Yee-King
 * 
 *  This file is part of JVstHost.
 *
 *  JVstHost is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  JVstHost is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with JVstHost.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.synthbot.audioplugin.vst.vst2;

import java.util.logging.Logger;

/**
 * A data class holding information regarding an input or output. The elements of this class are
 * not mutable. Check to see if the data is valid with <code>isValid</code>. If not, then any access
 * of methods other than <code>getIoIndex</code> will throw an <code>IllegalStateException</code>.
 */
public class VstPinProperties {

  private final boolean IS_VALID;
  private final int INDEX;
  private final String LABEL;
  private final String SHORT_LABEL;
  private final boolean IS_ACTIVE;
  private final boolean IS_FIRST_IN_STEREO_PAIR;

  /**
   * Construct an invalid <code>VstPinProperties</code> object for the given index.
   * @param index  
   */
  public VstPinProperties(int index) {
    IS_VALID = false;
    INDEX = index;
    LABEL = "";
    SHORT_LABEL = "";
    IS_ACTIVE = false;
    IS_FIRST_IN_STEREO_PAIR = false;
  }
  
  /**\
   * Construct a valid <code>VstPinProperties</code> object for the given index.
   * @param index
   * @param label
   * @param shortLabel
   * @param flags
   */
  public VstPinProperties(int index, String label, String shortLabel, int flags) {
    IS_VALID = true;
    INDEX = index;
    LABEL = label;
    SHORT_LABEL = shortLabel;
    IS_ACTIVE = (flags & 1) != 0x0;
    IS_FIRST_IN_STEREO_PAIR = (flags & 2) != 0x0;
  }
  
  /**
   * Returns the input or output index to which this data belongs.
   */
  public int getIoIndex() {
    return INDEX;
  }
  
  /**
   * Returns a textual description of this pin.
   * @return
   */
  public String getLabel() {
    if (IS_VALID) {
      return LABEL;      
    } else {
      throw new IllegalStateException("The data represented by this object is not valid. " +
          "The plugin does not support VstPinProperties.");
    }
  }
  
  public String getShortLabel() {
    if (IS_VALID) {
      return SHORT_LABEL;      
    } else {
      throw new IllegalStateException("The data represented by this object is not valid. " +
          "The plugin does not support VstPinProperties.");
    }
  }
  
  public boolean isActive() {
    if (IS_VALID) {
      return IS_ACTIVE;      
    } else {
      throw new IllegalStateException("The data represented by this object is not valid. " +
          "The plugin does not support VstPinProperties.");
    }
  }
  
  /**
   * Indicates if this pin is first in a stereo pair. If <code>true</code>, then the next pin is
   * the alternate channel. Note There is some confusion as to the correct interpretation of this 
   * information based on the original VST API. This flag indicates either that the pin is a part
   * of a stereo pair, or that it is the first in a stereo pair, as described. Due to this confusion,
   * different plugins may decide on differing interpretations.
   */
  public boolean isFirstInStereoPair() {
    if (IS_VALID) {
      return IS_FIRST_IN_STEREO_PAIR;      
    } else {
      throw new IllegalStateException("The data represented by this object is not valid. " +
          "The plugin does not support VstPinProperties.");
    }
  }
  
  /**
   * Indicates if the data represented by this object is valid. If <code>false</code>, then any
   * other method will throw an <code>IllegalStateException</code> if called.
   */
  public boolean isValid() {
    return IS_VALID;
  }
    private static final Logger LOG = Logger.getLogger(VstPinProperties.class.getName());
}
