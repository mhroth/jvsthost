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
import java.util.Vector;
import javax.sound.midi.ShortMessage;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class AudioThread implements Runnable {

  private JVstHost vst;
  private volatile boolean keepRunning;
  private float[][] fOutputs;
  private byte[] bOutput;
  private int blockSize;
  private int numOutputs;
  private AudioFormat audioFormat;
  private SourceDataLine sourceDataLine;

  private Vector<ShortMessage> pendingMidi;

  private static final float ShortMaxValueAsFloat = (float) Short.MAX_VALUE;

  public AudioThread(JVstHost vst) {
    addJVstHost(vst);
    numOutputs = vst.numOutputs();
    blockSize = vst.getBlockSize();
    pendingMidi = new Vector<ShortMessage>();

    audioFormat = new AudioFormat((int) vst.getSampleRate(), 16, vst.numOutputs(), true, false);
    DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
    System.out.println("AudioThread::Sound card data line info:" + dataLineInfo.toString());

    sourceDataLine = null;
    try {
      sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
      sourceDataLine.open(audioFormat, bOutput.length);
      sourceDataLine.start();
    } catch (LineUnavailableException lue) {
      lue.printStackTrace(System.err);
      System.exit(1);
    }
  }

  /**
   * Converts float audio array to an interleaved audio array of 16-bit samples
   */
  private byte[] floatsToBytes(float[][] fData, byte[] bData) {
    int index = 0;
    for (int i = 0; i < blockSize; i++) {
      for (int j = 0; j < numOutputs; j++) {
        short sval = (short) (fData[j][i] * ShortMaxValueAsFloat);
        bData[index++] = (byte) (sval & 0x00FF);
        bData[index++] = (byte) ((sval & 0xFF00) >> 8);
      }
    }
    return bData;
  }

  public void stopAudio() {
    keepRunning = false;
  }

  public void addJVstHost(JVstHost vst) {
    this.vst = vst;
    fOutputs = new float[vst.numOutputs()][vst.getBlockSize()];
    bOutput = new byte[vst.numOutputs() * vst.getBlockSize() * 2];
  }

  // midi events are collected and passed into the jvsthost every time
  // processReplacing gets called
  public synchronized void addMidiMessages(ShortMessage message) {
    pendingMidi.add(message);
  }

  public synchronized ShortMessage[] getMidiMessages() {
    ShortMessage[] messages = pendingMidi.toArray(new ShortMessage[0]);
    pendingMidi.clear();
    return messages;
  }

  public void run() {
    keepRunning = true;
    while (keepRunning) {
      vst.setMidiEvents(getMidiMessages()); // pass in the midi event
      vst.processReplacing(fOutputs);
      sourceDataLine.write(floatsToBytes(fOutputs, bOutput), 0, bOutput.length);
    }
    sourceDataLine.drain();
    sourceDataLine.close();
  }
  
  /**
   * Play the given array once, independent of the real-time audio thread.
   * TODO: implement!
   * @param fInputs
   */
  @Deprecated
  public void playOnce(float[][] fInputs) {
	DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
	final int localBlockSize = 1024; 
    sourceDataLine = null;
    try {
      sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
      sourceDataLine.open(audioFormat, localBlockSize);
      sourceDataLine.start();
    } catch (LineUnavailableException lue) {
      lue.printStackTrace(System.err);
      System.exit(1);
    }
    byte[] bInputs = new byte[fInputs.length * fInputs[0].length * 2];
    floatsToBytes(fInputs, bInputs);
    /*
    Thread thread = new Thread() {
      public void run() {
    	int bytesToWrite
    	while
        sourceDataLine.write(bInputs, 0, bOutput.length);
      }
    };
    thread.start();
    */
  }

}