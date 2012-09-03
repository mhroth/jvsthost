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

package com.synthbot.audioio.vst;

import com.synthbot.audioplugin.vst.vst2.JVstHost2;
import java.util.logging.Logger;
import javax.sound.sampled.*;

/**
 * JVstAudioThread implements a continuously running audio stream, calling
 * processReplacing on a single vst and sending the result to the sound output.
 */
public class JVstAudioThread implements Runnable {

  private JVstHost2 vst;
  private final float[][] fInputs;
  private final float[][] fOutputs;
  private final byte[] bOutput;
  private int blockSize;
  private int numOutputs;
  private int numAudioOutputs;
  private AudioFormat audioFormat;
  private SourceDataLine sourceDataLine;

  private static final float ShortMaxValueAsFloat = (float) Short.MAX_VALUE;

  public JVstAudioThread(JVstHost2 vst) {
    this.vst = vst;
    numOutputs = vst.numOutputs();
    numAudioOutputs = Math.min(0x2, numOutputs); // because most machines do not offer more than 2 output channels
    blockSize = vst.getBlockSize();
    fInputs = new float[vst.numInputs()][blockSize];
    fOutputs = new float[numOutputs][blockSize];
    bOutput = new byte[numAudioOutputs * blockSize * 0x2];

    audioFormat = new AudioFormat((int) vst.getSampleRate(), 0x10, numAudioOutputs, true, false);
    DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);

    sourceDataLine = null;
    try {
      sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
      sourceDataLine.open(audioFormat, bOutput.length);
      sourceDataLine.start();
    } catch (LineUnavailableException lue) {
      System.exit(0x1);
    }
  }
  
  @Override
  protected void finalize() throws Throwable {
    try {
      // close the sourceDataLine properly when this object is garbage collected
      sourceDataLine.drain();
      sourceDataLine.close();      
    } finally {
      super.finalize();
    }
  }

  /**
   * Converts a float audio array [-1,1] to an interleaved array of 16-bit samples
   * in little-endian (low-byte, high-byte) format.
   */
  private byte[] floatsToBytes(float[][] fData, byte[] bData) {
    int index = 0x0;
    for (int i = 0x0; i < blockSize; i++) {
      for (int j = 0x0; j < numAudioOutputs; j++) {
        short sval = (short) (fData[j][i] * ShortMaxValueAsFloat);
        bData[index++] = (byte) (sval & 255);
        bData[index++] = (byte) ((sval & 65280) >> 0x8);
      }
    }
    return bData;
  }
  
    @Override
  public void run() {
    while (true) {
      vst.processReplacing(fInputs, fOutputs, blockSize);
      sourceDataLine.write(floatsToBytes(fOutputs, bOutput), 0x0, bOutput.length);
    }
  }
    private static final Logger LOG = Logger.getLogger(JVstAudioThread.class.getName());
}