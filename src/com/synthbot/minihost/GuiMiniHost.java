/*
 *  Copyright 2007, 2008 Martin Roth (mhroth@gmail.com)
 *                       Matthew Yee-King
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

package com.synthbot.minihost;

import com.synthbot.audioplugin.vst.JVstHost;
import com.synthbot.audioplugin.vst.JVstLoadException;
import java.io.File;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.InvalidMidiDataException;

public class GuiMiniHost implements PluginStringGuiListener  {

  private static final float sampleRate = 44100f;
  private static final int blockSize = 4096;
  private JVstHost vst;
  private AudioThread audioThread;

  private GuiMiniHostListener stringGui;
  
  private int channel = 0;

  public GuiMiniHost(File vstFile) {
    vst = null;

    stringGui = new PluginStringGui(this);
    try {
      vst = new JVstHost(vstFile, sampleRate, blockSize);
    } catch (JVstLoadException jvle) {
      jvle.printStackTrace(System.err);
      System.exit(1);
    }
    stringGui.generateGui();

    // start the audio thread
    audioThread = new AudioThread(vst);
    Thread thread = new Thread(audioThread);
    thread.start();
  }


  // implement the PluginStringGUIListener interface 
  
  public String setParameter(int index, float value){
    vst.setParameter(index, value);
    return vst.getParameterLabel(index);
  }

  public float getParameter(int index){
    return vst.getParameter(index);
  }

  
  public void setProgram(int index){
    vst.setProgram(index);
  }
  
  public String getProgramName(){
    return vst.getProgramName();
  }
  
  public synchronized void playNote(int note, int velocity){
    try {
      ShortMessage midiMessage = new ShortMessage();
      midiMessage.setMessage(ShortMessage.NOTE_ON, channel, note, velocity);
      audioThread.addMidiMessages(midiMessage);
    } catch (InvalidMidiDataException imde) {
      imde.printStackTrace(System.err);
      //System.exit(1);
    }
  }

  public int getNumParameters(){
    return vst.numParameters();
  }

  public String getParameterDisplay(int index){
    return vst.getParameterDisplay(index);
  }
  
  public String getParameterName(int index){
    return vst.getParameterName(index);
  }
  
  public String getParameterLabel(int index){
    return vst.getParameterLabel(index);
  
  }



  public static void main(String[] args) {
    if (args == null || args.length < 1) {
      System.err.println("Usage: java -jar JVstHost.jar <path to vst plugin>");
      System.exit(0);
    }

    GuiMiniHost host = new GuiMiniHost(new File(args[0]));
  }

}
