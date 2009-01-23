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
import com.synthbot.audioplugin.vst.JVstLoadException;
import com.synthbot.audioplugin.vst.vst2.JVstHost2;

import java.io.File;
import java.io.FileNotFoundException;

import javax.sound.midi.ShortMessage;
import javax.sound.midi.InvalidMidiDataException;

/**
 * This class is meant as a simple demonstration of how to host a JVstHost.
 * It does not take advantage of any graphical elements. It simply sends random
 * MIDI notes to the selected VST at regular intervals. RandomMiniHost may be used
 * as a simple way to test plugin loading capabilities and audio system functionality
 * from the command line.
 */
public class RandomMiniHost   {

  private static final float sampleRate = 44100f;
  private static final int blockSize = 8912;
  private JVstHost2 vst;
  private JVstAudioThread audioThread;

  private int channel = 0;
  private int velocity = 127;

  public RandomMiniHost(File vstFile) {
    vst = null;
    try {
      vst = JVstHost2.newInstance(vstFile, SAMPLE_RATE, BLOCK_SIZE);
    } catch (FileNotFoundException fnfe) {
      fnfe.printStackTrace(System.err);
      System.exit(1);
    } catch (JVstLoadException jvle) {
      jvle.printStackTrace(System.err);
      System.exit(1);
    }

    // start the audio thread
    audioThread = new JVstAudioThread(vst);
    Thread thread = new Thread(audioThread);
    thread.start();

    // create a midi note on message
    ShortMessage midiMessage = new ShortMessage();
    
    // play a random note every 1000ms for 1000ms
    try {
      while (true) {
        int note = (int) (Math.random() * 24) + 48;

        Thread.sleep(1000);
        midiMessage.setMessage(ShortMessage.NOTE_ON, channel, note, velocity);
        vst.queueMidiMessage(midiMessage);

        Thread.sleep(1000);
        midiMessage.setMessage(ShortMessage.NOTE_OFF, channel, note, 0);
        vst.queueMidiMessage(midiMessage);
      }
    } catch (InvalidMidiDataException imde) {
      imde.printStackTrace(System.err);
    } catch (InterruptedException ie) {
      ie.printStackTrace(System.err);
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
