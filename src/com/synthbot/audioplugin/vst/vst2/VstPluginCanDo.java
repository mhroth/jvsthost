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

public enum VstPluginCanDo {
  
  /*
   * From the VST documentation:
   * const char* canDoSendVstEvents = "sendVstEvents"; ///< plug-in will send Vst events to Host
   * const char* canDoSendVstMidiEvent = "sendVstMidiEvent"; ///< plug-in will send MIDI events to Host
   * const char* canDoReceiveVstEvents = "receiveVstEvents"; ///< plug-in can receive MIDI events from Host
   * const char* canDoReceiveVstMidiEvent = "receiveVstMidiEvent"; ///< plug-in can receive MIDI events from Host 
   * const char* canDoReceiveVstTimeInfo = "receiveVstTimeInfo"; ///< plug-in can receive Time info from Host 
   * const char* canDoOffline = "offline"; ///< plug-in supports offline functions (#offlineNotify, #offlinePrepare, #offlineRun)
   * const char* canDoMidiProgramNames = "midiProgramNames"; ///< plug-in supports function #getMidiProgramName ()
   * const char* canDoBypass = "bypass"; ///< plug-in supports function #setBypass ()
   */
  SEND_VST_EVENTS("sendVstEvents"),
  SEND_VST_MIDI_EVENT("sendVstMidiEvent"),
  RECEIVE_VST_EVENTS("receiveVstEvents"),
  RECEIVE_VST_MIDI_EVENT("receiveVstMidiEvent"),
  RECEIVE_VST_TIME_INFO("receiveVstTimeInfo"),
  OFFLINE("offline"),
  MIDI_PROGRAM_NAMES("midiProgramNames"),
  BYPASS("bypass");
  
  private String canDo;
  
  private VstPluginCanDo(String canDo) {
    this.canDo = canDo;
  }
  
  public String canDoString() {
    return canDo;
  }
  
}
