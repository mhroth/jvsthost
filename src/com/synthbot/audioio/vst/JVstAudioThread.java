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

package com.synthbot.audioio.vst;

import com.synthbot.audioplugin.vst.JVstHost;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * JVstAudioThread implements a continuously running audio stream, calling
 * processReplacing on a single vst and sending the result to the sound output.
 */
public class JVstAudioThread implements Runnable {

  private JVstHost vst;
  private float[][] fOutputs;
  private byte[] bOutput;
  private int blockSize;
  private int numOutputs;
  private AudioFormat audioFormat;
  private SourceDataLine sourceDataLine;

  private static final float ShortMaxValueAsFloat = (float) Short.MAX_VALUE;

  public JVstAudioThread(JVstHost vst) {
    this.vst = vst;
    numOutputs = vst.numOutputs();
    blockSize = vst.getBlockSize();
    fOutputs = new float[numOutputs][blockSize];
    bOutput = new byte[numOutputs * blockSize * 2];

    audioFormat = new AudioFormat((int) vst.getSampleRate(), 16, vst.numOutputs(), true, false);
    DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);

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
  
  @Override
  protected void finalize() {
    // close the sourceDataLine properly when this object is garbage collected
    sourceDataLine.drain();
    sourceDataLine.close();
  }

  /**
   * Converts a float audio array [-1,1] to an interleaved array of 16-bit samples
   * in little-endian (low-byte, high-byte) format.
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
  
  public void run() {
    while (true) {
      vst.processReplacing(fOutputs);
      sourceDataLine.write(floatsToBytes(fOutputs, bOutput), 0, bOutput.length);
    }
  }
}