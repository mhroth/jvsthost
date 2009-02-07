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

package com.synthbot.minihost;

import com.synthbot.audioio.vst.JVstAudioThread;
import com.synthbot.audioplugin.view.StringGui;
import com.synthbot.audioplugin.vst.JVstLoadException;
import com.synthbot.audioplugin.vst.vst2.AbstractJVstHostListener;
import com.synthbot.audioplugin.vst.vst2.JVstHost2;

import java.io.File;
import java.io.FileNotFoundException;

public class GuiMiniHost extends AbstractJVstHostListener {

  private static final float SAMPLE_RATE = 44100f;
  private static final int BLOCK_SIZE = 4096;
  private static final String AUDIO_THREAD = "Audio Thread";
  
  private JVstHost2 vst;
  private JVstAudioThread audioThread;
  private StringGui gui;
  
  public GuiMiniHost(File vstFile) {

    // start the main gui
    // also serves to start the gui thread which is needed to allow later guis to open
    //JVstMiniHostGui miniHostGui = new JVstMiniHostGui();
    
    // load the vst
    try {
      vst = JVstHost2.newInstance(vstFile, SAMPLE_RATE, BLOCK_SIZE);
    } catch (FileNotFoundException fnfe) {
      fnfe.printStackTrace(System.err);
      System.exit(1);
    } catch (JVstLoadException jvle) {
      jvle.printStackTrace(System.err);
      System.exit(1);
    }
    
    // add the host as a listener to receive any callbacks
    vst.addJVstHostListener(this);

    // start the audio thread
    audioThread = new JVstAudioThread(vst);
    Thread thread = new Thread(audioThread);
    thread.setName(AUDIO_THREAD); // for easy debugging
    thread.setDaemon(true); // allows the JVM to exit normally
    thread.start();

    // create and display a StringGui for controlling the vst
    gui = new StringGui(vst);
    gui.setVisible(true);
    
    if (vst.hasEditor()) {
      vst.openEditor(vst.getEffectName());    	
    }
  }
  
  @Override
  public void onAudioMasterAutomate(JVstHost2 vst, int index, float value) {
    if (gui != null) {
      gui.updateParameter(index, value);      
    }
  }

  public static void main(String[] args) {
    if (args == null || args.length < 1) {
      System.err.println("Usage: java -jar JVstHost.jar <path to vst plugin>");
      System.exit(0);
    }

    // start the mini host
    GuiMiniHost host = new GuiMiniHost(new File(args[0]));
  }

}
