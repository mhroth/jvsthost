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

import javax.sound.midi.ShortMessage;
import javax.sound.midi.InvalidMidiDataException;
import java.io.File;

public class ConsoleMiniHost {
  
  public static void main(String[] args){
    
    int blockSize = 44100; // how many samples to render 
    float sampleRate = 44100f;
    float[][] outputs; // the plugin will write its output into this array
    JVstHost vst = null;
    ShortMessage smNoteOn;
    int channel = 0;
    int midiNoteNumber = 60; // midi note number for middle C
    int velocity = 127;
    AudioPlayer player;

    outputs = new float[2][blockSize];
  
    // create a midi note on message
    smNoteOn = new ShortMessage();
    try {
      smNoteOn.setMessage(ShortMessage.NOTE_ON, channel, midiNoteNumber, velocity);
    } catch (InvalidMidiDataException imde) {
      imde.printStackTrace(System.err);
      System.exit(1);
    }
    
    try {
      // read the name of the plugin library from the command line
      vst = new JVstHost(new File(args[0]), sampleRate, blockSize);
    } catch (JVstLoadException jvle) {
      jvle.printStackTrace(System.err);
      System.exit(1);
    }
    
    outputs = new float[vst.numOutputs()][blockSize];
    
    vst.processReplacing(null, outputs, 64); // some synths seem to require an initial procRep
    vst.setMidiEvents(new ShortMessage[] {smNoteOn}); // pass in the midi event
    vst.processReplacing(outputs); //
    
    // play the left channel
    player = new AudioPlayer();
    player.playAudio(outputs[0]);
  }
}