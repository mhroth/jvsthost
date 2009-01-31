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

import javax.sound.midi.ShortMessage;

/**
 * A class implementing this interface can receive callbacks from a VST plugin.
 * These callbacks are the AudioMaster set. Note that no time intensive operations
 * should be undertaken by these methods, as they will be run on the plugin's native UI thread.
 */
public interface JVstHostListener {
  
  /**
   * Called when the plugin has changed a parameter, usually through the use of the native editor.
   * @param vst The JVstHost2 which is generating this callback
   * @param index Index of the automated parameter
   * @param value New value of the automated parameter
   */
  public void onAudioMasterAutomate(JVstHost2 vst, int index, float value);
  
  /**
   * Called when the plugin sends a midi message to the host.
   * @param message
   */
  public void onAudioMasterProcessMidiEvents(JVstHost2 vst, ShortMessage message);
  
  /**
   * Called when the plugin changes it input/output configuration.
   * @param vst The JVstHost2 which is generating this callback
   * @param numInputs
   * @param numOutputs
   * @param initialDelay
   * @param numParameters
   */
  public void onAudioMasterIoChanged(JVstHost2 vst, int numInputs, int numOutputs, int initalDelay, int numParameters);
  
  /**
   * Notifies the listener that a parameter is being edited, such as when the user adjusts a knob 
   * by holding down a mouse button and dragging.
   * @param vst  The vst from which this callback is originating.
   * @param index  The index of the parameter being edited.
   */
  public void onAudioMasterBeginEdit(JVstHost2 vst, int index);
  
  /**
   * Notifies the listener that the parameter is no longer being actively edited by the user.
   * @param vst  The vst from which this callback is originating.
   * @param index  The index of the parameter no longer being edited.
   */
  public void onAudioMasterEndEdit(JVstHost2 vst, int index);

}
