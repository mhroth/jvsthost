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

public class RandomMiniHost   {

  private static final float sampleRate = 44100f;
  private static final int blockSize = 8912;
  private JVstHost vst;
  private AudioThread audioThread;

  private int channel = 0;
  private int velocity = 127;

  public RandomMiniHost(File vstFile) {
    vst = null;
    try {
      vst = new JVstHost(vstFile, sampleRate, blockSize);
    } catch (JVstLoadException jvle) {
      jvle.printStackTrace(System.err);
      System.exit(1);
    }

    // start the audio thread
    audioThread = new AudioThread(vst);
    Thread thread = new Thread(audioThread);
    thread.start();

    // create a midi note on message
    ShortMessage midiMessage = new ShortMessage();
    
    // play a random note every 1000 ms
    try {
      while (true) {
        int note = (int) (Math.random() * 24) + 48;

        Thread.sleep(5000);
        midiMessage.setMessage(ShortMessage.NOTE_ON, channel, note, velocity);
        audioThread.addMidiMessages(midiMessage);

        Thread.sleep(5000);
        midiMessage.setMessage(ShortMessage.NOTE_OFF, channel, note, 0);
        audioThread.addMidiMessages(midiMessage);

      }
    } catch (InvalidMidiDataException imde) {
      imde.printStackTrace(System.err);
      System.exit(1);
    } catch (InterruptedException e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  public static void main(String[] args) {
    if (args == null || args.length < 1) {
      System.err.println("Usage: java -jar JVstHost.jar <path to vst plugin>");
      System.exit(0);
    }

    RandomMiniHost host = new RandomMiniHost(new File(args[0]));
  }

}
