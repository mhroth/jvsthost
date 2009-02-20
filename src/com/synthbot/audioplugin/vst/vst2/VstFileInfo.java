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


public class VstFileInfo {

  private final String UNIQUE_ID;
  private final int PLUGIN_VERSION;
  private final int FILE_VERSION;
  private final boolean IS_OPAQUE;
  
  protected VstFileInfo(String uniqueId, int pluginVersion, int fileVersion, boolean isOpaque) {
    UNIQUE_ID = uniqueId;
    PLUGIN_VERSION = pluginVersion;
    FILE_VERSION = fileVersion;
    IS_OPAQUE = isOpaque;
  }
  
  /**
   * Returns the unique id of the plugin for which this file is intended.
   */
  public String getUniqueId() {
    return UNIQUE_ID;
  }
  
  /**
   * Returns the version of the plugin which wrote this file.
   */
  public int getPluginVersion() {
    return PLUGIN_VERSION;
  }

  /**
   * Returns the file format version.
   */
  public int getFileFormatVersion() {
    return FILE_VERSION;
  }
  
  /**
   * Indicates if the data in the file is opaque (data supplied by the plugin), or regular 
   * (data written by the host).
   */
  public boolean isDataOpaque() {
    return IS_OPAQUE;
  } 
}
