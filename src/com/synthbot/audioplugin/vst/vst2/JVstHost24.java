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

package com.synthbot.audioplugin.vst.vst2;

import com.synthbot.audioplugin.vst.VstVersion;
import java.io.File;
import java.util.logging.Logger;
import javax.sound.midi.MidiMessage;

public class JVstHost24 extends JVstHost23 {
  
  protected final boolean canDoubleReplacing; // cached to do quick exception checks in processDoubleReplacing()

  protected JVstHost24(File pluginFile, long pluginPtr) {
    super(pluginFile, pluginPtr);

    canDoubleReplacing = (canDoubleReplacing(pluginPtr) != 0x0);
  }
  
  @Override
  public VstVersion getVstVersion() {
    return VstVersion.VST24;
  }
  
  /**
   * Generate audio output from the plugin, replacing the contents of the output array. Queued MIDI messages, via <code>queueMidiMessage</code>, will be passed to the plugin.
   * @param inputs  The audio input to the plugin is read from this array.
   * @param outputs  The output of the plugin will be placed into this array.
   * @param blockSize  Number of samples to read from the input and output buffers. May not be larger than the length of the arrays.
   * @throws IllegalArgumentException  Thrown if any of the arguments do not lie within their natural bounds.
   * @throws IllegalStateException  Thrown if the plugin does not implement <code>processReplacing</code>. Check <code>canReplacing</code>.
   * @throws OutOfMemoryError  Thrown in the rare case that native input and output arrays cannot be allocated by the JVM.
   * @throws NullPointerException  Thrown if the input or output arrays are null.
   */
  public synchronized void processDoubleReplacing(double[][] inputs, double[][] outputs, int blockSize) {
    assertNativeComponentIsLoaded();
    assertIsTurnedOn();
    if (!canDoubleReplacing) {
      throw new IllegalStateException("This plugin cannot do processDoubleReplacing().");
    }
    if (inputs == null) {
      throw new NullPointerException("The inputs array is null.");
    } else if (inputs.length < numInputs) {
      throw new IllegalArgumentException("Input array length must equal the number of inputs: " + inputs.length + " < " + numInputs);
    } else {
      for (double[] input : inputs) {
        if (input.length < blockSize) {
          throw new IllegalArgumentException("Input array length must be at least as large as the blockSize: " + input.length + " != " + blockSize);
        }
      }
    }
    if (outputs == null) {
      throw new NullPointerException("The outputs array is null.");
    } else if (outputs.length < numOutputs) {
      throw new IllegalArgumentException("Output array length must equal the number of outputs: " + outputs.length + " < " + numOutputs);
    } else {
      for (double[] output : outputs) {
        if (output.length < blockSize) {
          throw new IllegalArgumentException("Output array length must be at least as large as the blockSize: " + output.length + " != " + blockSize);
        }
      }
    }
    if (blockSize < 0x0) {
      throw new IllegalArgumentException("Block size must be non-negative: " + blockSize + " < 0");
    }
    
    MidiMessage[] messages = queuedMidiMessages.toArray(new MidiMessage[0x0]);
    queuedMidiMessages.clear();
    
    processDoubleReplacing(messages, inputs, outputs, blockSize, vstPluginPtr);
  }
  protected static native void processDoubleReplacing(MidiMessage[] messages, double[][] inputs, double[][] outputs, int blockSize, long pluginPtr);
  
  /**
   * Determines if this plugin supports <code>processDoubleReplacing</code>.
   * @return  True if this plugin supports <code>processDoubleReplacing</code>. False otherwise.
   */
  public synchronized boolean canDoubleReplacing() {
    return canDoubleReplacing;
  }
  protected static native int canDoubleReplacing(long pluginPtr);
  
  
  /**
   * This method is deprecated as of VST version 2.4. Use <code>processReplacing</code>, or <code>processDoubleReplacing</code> if it exists.
   * @throws IllegalStateException  Thrown if this method is called. <code>process</code> is not supported in VST version 2.4.
   */
  @Override
  @Deprecated
  public synchronized void process(float[][] inputs, float[][] outputs, int blockSize) {
    throw new IllegalStateException("process() is deprecated and unsupported as of VST version 2.4. Use processReplacing or processDoubleReplacing if it exists.");
  }
    private static final Logger LOG = Logger.getLogger(JVstHost24.class.getName());
}
