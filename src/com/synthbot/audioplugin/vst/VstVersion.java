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

package com.synthbot.audioplugin.vst;

public enum VstVersion {
  VST20(2), 
  VST21(2100), 
  VST22(2200), 
  VST23(2300), 
  VST24(2400),
  UNKNOWN(0);
  
  private final int vstVersionNumber;
  
  private VstVersion(int version) {
    this.vstVersionNumber = version;
  }
  
  /**
   * Returns the numerical vst version id.
   */
  public int getVstVersionNumber() {
    return vstVersionNumber;
  }
  
  public static VstVersion getVersion(int version) {
    for (VstVersion vstVersion : VstVersion.values()) {
      if (vstVersion.getVstVersionNumber() == version) {
        return vstVersion;
      }
    }
    return UNKNOWN;
  }
}