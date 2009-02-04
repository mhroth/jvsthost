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

package com.synthbot.audioplugin.vst.view;

import javax.sound.midi.ShortMessage;


/**
 *  <code>JVstGuiListener</code> - the host implements this
 *  and it allows the generic plugin gui to talk to the host in a
 *  controlled way.
 */
public interface JVstViewListener {

  /**
   * Set the given parameter and return the associated display value.
   * @param index  Parameter to set.
   * @param value  Value to set.
   */
  public void setParameter(int index, float value);
  
  /**
   * Get the parameter value.
   * @param index  Parameter index.
   * @return  Value of index parameter.
   */
  public float getParameter(int index);
  
  /**
   * Get the number of parameters of the audio plugin.
   * @return  Number of parameters of audio plugin.
   */
  public int numParameters();
  
  public String getParameterDisplay(int index);
  
  public String getParameterName(int index);
  
  public String getParameterLabel(int index);
  
  /**
   * Return the number of programs that the plugin has.
   */
  public int numPrograms();
  
  /**
   * Change the current program to the given index.
   */
  public void setProgram(int index);
  
  public void queueMidiMessage(ShortMessage message);
  
  /**
   * Get the name of the current program/preset.
   */
  public String getProgramName(int index);
  
  public String getVendorName();
  
  public String getEffectName();
}