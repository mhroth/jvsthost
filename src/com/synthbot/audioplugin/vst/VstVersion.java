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
  VST20(0x2), 
  VST21(0x834), 
  VST22(0x898), 
  VST23(0x8fc), 
  VST24(0x960),
  UNKNOWN(0x0);
  
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