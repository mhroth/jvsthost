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

import com.synthbot.audioio.vst.JVstAudioThread;
import com.synthbot.audioplugin.view.StringGui;
import com.synthbot.audioplugin.vst.JVstHost;
import com.synthbot.audioplugin.vst.JVstLoadException;
import com.synthbot.minihost.view.JVstMiniHostGui;
import java.io.File;

public class GuiMiniHost {

  private static final float sampleRate = 44100f;
  private static final int blockSize = 4096;
  private JVstHost vst = null;
  private JVstAudioThread audioThread;

  private static final String AUDIO_THREAD = "Audio Thread";
  
  public GuiMiniHost(File vstFile) {

    // start the main gui
    // also serves to start the gui thread which is needed to allow later guis to open
    JVstMiniHostGui miniHostGui = new JVstMiniHostGui();
    
    // load the vst
    try {
      vst = new JVstHost(vstFile, sampleRate, blockSize);
    } catch (JVstLoadException jvle) {
      jvle.printStackTrace(System.err);
      System.exit(1);
    }
        
    // start the audio thread
    audioThread = new JVstAudioThread(vst);
    Thread thread = new Thread(audioThread);
    thread.setName(AUDIO_THREAD); // for easy debugging
    thread.setDaemon(true); // allows the JVM to exit normally
    thread.start();
    
    // set the Java editor
    vst.setJavaEditor(new StringGui(vst));
    
    // open the Java string gui of the vst
    vst.openJavaEditor();
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
