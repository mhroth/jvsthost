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

public abstract class AbstractJVstHostListener implements JVstHostListener {

    @Override
  public void onAudioMasterAutomate(JVstHost2 vst, int index, float value) {
    // do nothing
  }

    @Override
  public void onAudioMasterIoChanged(JVstHost2 vst, int numInputs, int numOutputs, int initalDelay, int numParameters) {
    // do nothing
  }

    @Override
  public void onAudioMasterProcessMidiEvents(JVstHost2 vst, ShortMessage message) {
    // do nothing
  }
  
    @Override
  public void onAudioMasterBeginEdit(JVstHost2 vst, int index) {
    // do nothing
  }
  
    @Override
  public void onAudioMasterEndEdit(JVstHost2 vst, int index) {
    // do nothing
  }

}
