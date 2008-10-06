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

package com.synthbot.test;

import com.synthbot.audioplugin.vst.JVstHost;
import com.synthbot.audioplugin.vst.JVstLoadException;

import javax.sound.midi.ShortMessage;
import javax.sound.midi.InvalidMidiDataException;
import java.io.File;

public class MiniHost {
  
  public static void main(String[] args){
    // how many samples to render 
    int blockSize = 44100;
    float sampleRate = 44100f;
    // the plugin will write its output into this array
    float[][] outputs;
    JVstHost host;
    ShortMessage smNoteOff, smNoteOn;
    int channel = 0;
    AudioPlayer player;

    outputs = new float[2][blockSize];
  
    // create a midi note on message
    smNoteOff = new ShortMessage();
    smNoteOn = new ShortMessage();
    try {
      smNoteOff.setMessage(ShortMessage.NOTE_OFF, channel, 60, 0);
      smNoteOn.setMessage(ShortMessage.NOTE_ON, channel, 60, 96);
    } catch (InvalidMidiDataException imde) {
      imde.printStackTrace(System.err);
      System.exit(0);
    }
    
    try {
      // read the name of the plugin library from the command line
      host = new JVstHost(new File(args[0]), sampleRate, blockSize);
      host.processReplacing(outputs); // some synths seem to require an initial procRep
      // pass in the midi messages for prcessing
      host.setMidiEvents(new ShortMessage[] {smNoteOff, smNoteOn});
      // render (blockSize) frames of output
      host.processReplacing(outputs); 
      
      // play the first channel
      player = new AudioPlayer();
      player.playAudio(outputs[0]);
    } catch (JVstLoadException e) {
      e.printStackTrace(System.err);
    }

  
    
  }

}