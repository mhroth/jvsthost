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
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

public class JVstHost20 extends JVstHost2 {
  
  protected int numInputs; // not final because can change (ioChange)
  protected int numOutputs; // locally cached for error checking
  protected int numParameters;
  protected final int numPrograms;
  protected float sampleRate; // the last sampleRate to which the plugin was set
  protected int blockSize; // the last maximum blockSize to which the plugin was set
  protected final boolean canProcessReplacing;
  protected final boolean hasNativeEditor;
  protected volatile Thread editorThread; // volatile because the variable can be get/set by either the vst thread or the editor thread
  protected boolean isTurnedOff;
  
  protected final List<MidiMessage> queuedMidiMessages;
  
  protected final List<JVstHostListener> hostListeners;
  
  protected JVstHost20(File pluginFile, long pluginPtr) {
    super(pluginFile, pluginPtr);
    setThis(vstPluginPtr);
    
    numInputs = numInputs();
    numOutputs = numOutputs();
    numParameters = numParameters();
    numPrograms = numPrograms(); // this value seems never to be changed
    canProcessReplacing = (canReplacing(pluginPtr) != 0);
    hasNativeEditor = (hasEditor(vstPluginPtr) != 0);
    isTurnedOff = true;
    
    queuedMidiMessages = new ArrayList<MidiMessage>();
    
    hostListeners = new ArrayList<JVstHostListener>();
  }
  
  /**
   * Creates a weak global reference to the corresponding java object for the native plugin.
   * This reference allows callbacks methods to by easily called to the correct object.
   */
  private native void setThis(long pluginPtr);

  protected void assertIsTurnedOff() {
    if (!isTurnedOff) {
      throw new IllegalStateException("The plugin must be turned off in order to perform this operation.");
    }
  }
  
  protected void assertIsTurnedOn() {
    if (isTurnedOff) {
      throw new IllegalStateException("The plugin must be turned on in order to perform this operation.");
    }
  }
  
  @Override
  public VstVersion getVstVersion() {
    return VstVersion.VST20;
  }
  
  @Override
  public synchronized void turnOffAndUnloadPlugin() {
    if (isEditorOpen()) {
      closeEditor();
    }
    turnOff();
    unloadPlugin(vstPluginPtr);
    isNativeComponentLoaded = false;
  }
  
  @Override
  public synchronized void queueMidiMessage(SysexMessage message) {
    if (message == null) {
      throw new NullPointerException("Queued midi message may not be null.");
    }
    queuedMidiMessages.add(message);
  }
  
  @Override
  public synchronized void queueMidiMessage(ShortMessage message) {
    if (message == null) {
      throw new NullPointerException("Queued midi message may not be null.");
    }
    queuedMidiMessages.add(message);
  }
  
  @Override
  public synchronized void processReplacing(float[][] inputs, float[][] outputs, int blockSize) {
    assertNativeComponentIsLoaded();
    assertIsTurnedOn();
    if (!canProcessReplacing) {
      throw new IllegalStateException("This plugin does not implement processReplacing().");
    }
    if (inputs == null) {
      throw new NullPointerException("The inputs array is null.");
    } else if (inputs.length < numInputs) {
      throw new IllegalArgumentException("Input array length must equal the number of inputs: " + inputs.length + " < " + numInputs);
    } else {
      for (float[] input : inputs) {
        if (input.length < blockSize) {
          throw new IllegalArgumentException("Input array length must be at least as large as the blockSize: " + input.length + " < " + blockSize);
        }
      }
    }
    if (outputs == null) {
      throw new NullPointerException("The outputs array is null.");
    } else if (outputs.length < numOutputs) {
      throw new IllegalArgumentException("Output array length must equal the number of outputs: " + outputs.length + " < " + numOutputs);
    } else {
      for (float[] output : outputs) {
        if (output.length < blockSize) {
          throw new IllegalArgumentException("Output array length must be at least as large as the blockSize: " + output.length + " < " + blockSize);
        }
      }
    }
    if (blockSize < 0) {
      throw new IllegalArgumentException("Block size must be non-negative: " + blockSize + " < 0");
    }

    MidiMessage[] messages = queuedMidiMessages.toArray(new MidiMessage[0]);
    queuedMidiMessages.clear();
    
    processReplacing(messages, inputs, outputs, blockSize, vstPluginPtr);
  }
  protected static native void processReplacing(MidiMessage[] messages, float[][] inputs, float[][] outputs, int blockSize, long pluginPtr);
  
  @Override
  public synchronized boolean canReplacing() {
    return canProcessReplacing;
  }
  protected static native int canReplacing(long pluginPtr);
  
  @Override
  public synchronized void process(float[][] inputs, float[][] outputs, int blockSize) {
    assertNativeComponentIsLoaded();
    assertIsTurnedOn();
    if (inputs == null) {
      throw new NullPointerException("The inputs array is null.");
    } else if (inputs.length < numInputs) {
      throw new IllegalArgumentException("Input array length must equal the number of inputs: " + inputs.length + " < " + numInputs);
    } else {
      for (float[] input : inputs) {
        if (input.length < blockSize) {
          throw new IllegalArgumentException("Input array length must be at least as large as the blockSize: " + input.length + " < " + blockSize);
        }
      }
    }
    if (outputs == null) {
      throw new NullPointerException("The outputs array is null.");
    } else if (outputs.length < numOutputs) {
      throw new IllegalArgumentException("Output array length must equal the number of outputs: " + outputs.length + " < " + numOutputs);
    } else {
      for (float[] output : outputs) {
        if (output.length < blockSize) {
          throw new IllegalArgumentException("Output array length must be at least as large as the blockSize: " + output.length + " < " + blockSize);
        }
      }
    }
    if (blockSize < 0) {
      throw new IllegalArgumentException("Block size must be non-negative: " + blockSize + " < 0");
    }
    
    MidiMessage[] messages = queuedMidiMessages.toArray(new MidiMessage[0]);
    queuedMidiMessages.clear();
    
    process(messages, inputs, outputs, blockSize, vstPluginPtr);
  }
  protected static native void process(MidiMessage[] messages, float[][] inputs, float[][] outputs, int blockSize, long pluginPtr);
  
  @Override
  public synchronized boolean canDo(VstPluginCanDo canDo) {
    assertNativeComponentIsLoaded();
    return (canDo(canDo.canDoString(), vstPluginPtr) != 0);
  }
  protected static native int canDo(String canDo, long pluginPtr);
  
  @Override
  public synchronized void setParameter(int index, float value) {
    assertNativeComponentIsLoaded();
    if (index < 0 || index >= numParameters) {
      throw new IndexOutOfBoundsException("Parameter index, " + index + ", must be between 0 and " + numParameters);
    }
    if (value < 0f || value > 1f) {
      System.err.println("Parameter values should be constrained to within [0,1]: " + Float.toString(value));
    }
    setParameter(index, value, vstPluginPtr);
  }
  protected static native void setParameter(int index, float value, long pluginPtr);

  @Override
  public synchronized float getParameter(int index) {
    assertNativeComponentIsLoaded();
    if (index < 0 || index >= numParameters) {
      throw new IndexOutOfBoundsException("Parameter index, " + index + ", must be between 0 and " + numParameters);
    }
    return getParameter(index, vstPluginPtr);
  }
  protected static native float getParameter(int index, long pluginPtr);
  
  @Override
  public synchronized boolean isParameterAutomatable(int index) {
    assertNativeComponentIsLoaded();
    if (index < 0 || index >= numParameters) {
      throw new IndexOutOfBoundsException("Parameter index, " + index + ", must be between 0 and " + numParameters);
    }
    return (isParameterAutomatable(index, vstPluginPtr) != 0);
  }
  protected static native int isParameterAutomatable(int index, long vstPluginPtr);
  
  @Override
  public synchronized String getParameterName(int index) {
    assertNativeComponentIsLoaded();
    if (index < 0 || index >= numParameters) {
      throw new IndexOutOfBoundsException("Parameter index, " + index + ", must be between 0 and " + numParameters);
    }
    return getParameterName(index, vstPluginPtr);
  }
  protected static native String getParameterName(int index, long pluginPtr);
  
  @Override
  public synchronized String getParameterDisplay(int index) {
    assertNativeComponentIsLoaded();
    if (index < 0 || index >= numParameters) {
      throw new IndexOutOfBoundsException("Parameter index, " + index + ", must be between 0 and " + numParameters);
    }
    return getParameterDisplay(index, vstPluginPtr);
  }
  protected static native String getParameterDisplay(int index, long pluginPtr);
  
  @Override
  public synchronized String getParameterLabel(int index) throws IndexOutOfBoundsException {
    assertNativeComponentIsLoaded();
    if (index < 0 || index >= numParameters) {
      throw new IndexOutOfBoundsException("Parameter index, " + index + ", must be between 0 and " + numParameters);
    }
    return getParameterLabel(index, vstPluginPtr);
  }
  protected static native String getParameterLabel(int index, long pluginPtr);
  
  @Override
  public synchronized String getEffectName() {
    assertNativeComponentIsLoaded();
    return getEffectName(vstPluginPtr);
  }
  protected static native String getEffectName(long pluginPtr);
  
  @Override
  public synchronized String getVendorName() {
    assertNativeComponentIsLoaded();
    return getVendorName(vstPluginPtr);
  }
  protected static native String getVendorName(long pluginPtr);
  
  @Override
  public synchronized String getProductString() {
    assertNativeComponentIsLoaded();
    return getProductString(vstPluginPtr);
  }
  protected static native String getProductString(long pluginPtr);
  
  @Override
  public synchronized int numParameters() {
    assertNativeComponentIsLoaded();
    return numParameters(vstPluginPtr);
  }
  protected static native int numParameters(long pluginPtr);
  
  @Override
  public synchronized int numInputs() {
    assertNativeComponentIsLoaded();
    return numInputs(vstPluginPtr);
  }
  protected static native int numInputs(long pluginPtr);
  
  @Override
  public synchronized int numOutputs() {
    assertNativeComponentIsLoaded();
    return numOutputs(vstPluginPtr);
  }
  protected static native int numOutputs(long pluginPtr);
  
  @Override
  public synchronized int numPrograms() {
    assertNativeComponentIsLoaded();
    return numPrograms(vstPluginPtr);
  }
  protected static native int numPrograms(long pluginPtr);
  
  @Override
  public synchronized void setSampleRate(float sampleRate) {
    assertIsTurnedOff();
    assertNativeComponentIsLoaded();
    if (sampleRate <= 0f) {
      throw new IllegalArgumentException("Sample rate must be positive: " + sampleRate);
    }
    this.sampleRate = sampleRate;
    setSampleRate(sampleRate, vstPluginPtr);
  }
  protected static native void setSampleRate(float sampleRate, long pluginPtr);
  
  @Override
  public synchronized float getSampleRate() {
    return sampleRate;
  }
  
  @Override
  public synchronized void setTempo(double tempo) {
    setTempo(tempo, vstPluginPtr);
  }
  protected static native void setTempo(double tempo, long pluginPtr);
  
  
  @Override
  public synchronized void setBlockSize(int blockSize) throws IllegalArgumentException {
    assertIsTurnedOff();
    assertNativeComponentIsLoaded();
    if (blockSize <= 0) {
      throw new IllegalArgumentException("Blocks size must be positive: " + blockSize);
    }
    this.blockSize = blockSize;
    setBlockSize(blockSize, vstPluginPtr);
  }
  protected static native void setBlockSize(int blockSize, long pluginPtr);
  
  @Override
  public synchronized int getBlockSize() {
    return blockSize;
  }
  
  @Override
  public synchronized String getUniqueId() {
    int uniqueId = getUniqueIdAsInt();
    byte[] uidArray = {
        (byte) (0x000000FF & (uniqueId >> 24)), 
        (byte) (0x000000FF & (uniqueId >> 16)), 
        (byte) (0x000000FF & (uniqueId >> 8)), 
        (byte) uniqueId};
    return new String(uidArray);
  }
  
  @Override
  public synchronized int getUniqueIdAsInt() {
    assertNativeComponentIsLoaded();
    return getUniqueId(vstPluginPtr);
  }
  protected static native int getUniqueId(long pluginPtr);
  
  @Override
  public synchronized boolean isSynth() {
    assertNativeComponentIsLoaded();
    return (isSynth(vstPluginPtr) != 0);
  }
  protected static native int isSynth(long pluginPtr);
  
  @Override
  public synchronized boolean acceptsProgramsAsChunks() {
    assertNativeComponentIsLoaded();
    return (acceptsProgramsAsChunks(vstPluginPtr) != 0);
  }
  protected static native int acceptsProgramsAsChunks(long pluginPtr);
  
  @Override
  public synchronized void openEditor(final String frameTitle) {
    if (frameTitle == null) {
      throw new NullPointerException("frameTitle may not be null.");
    }
    assertNativeComponentIsLoaded();
    assertHasNativeEditor();
        
    if (!isEditorOpen()) {
      final JVstHost2 thisJVstHost = this;
      editorThread = new Thread(new Runnable() {
        public void run() {
          openEditor(frameTitle, vstPluginPtr); // this method blocks while the native window is open
          editorThread = null;
          synchronized(thisJVstHost) {
            thisJVstHost.notify(); // notify all waiting threads (such as one waiting in closeEditor), that the native editor is now closed
          }
        }
      });
      editorThread.setPriority(Thread.MIN_PRIORITY);
      editorThread.setName(toString() + " native editor thread");
      editorThread.start();      
    }
  }
  protected static native void openEditor(String frameTitle, long pluginPtr);
  
  @Override
  public synchronized boolean isEditorOpen() {
    return (editorThread != null);
  }
  
  @Override
  public synchronized void topEditor() {
    if (isEditorOpen()) {
      topEditor(vstPluginPtr);
      System.out.println("done topping editor");
    }
  }
  protected static native void topEditor(long pluginPtr);
  
  
  @Override
  public synchronized void closeEditor() {
    assertNativeComponentIsLoaded();
    if (isEditorOpen()) {
      closeEditor(vstPluginPtr);
      while (isEditorOpen()) {
        try {
          wait(); // wait for the editorThread to notify this JVstHost2 object that it has completed
        } catch (InterruptedException ie) {
          // do nothing, just wait again
        }
      }
    }
  }
  protected static native void closeEditor(long pluginPtr);
  
  @Override
  public synchronized boolean hasEditor() {
    return hasNativeEditor;
  }
  protected static native int hasEditor(long pluginPtr);
  
  protected void assertHasNativeEditor() {
    if (!hasNativeEditor) {
      throw new IllegalStateException("This plugin has no native editor. Do not try to manipulate it.");
    }
  }
  
  @Override
  public synchronized int getProgram() {
    assertNativeComponentIsLoaded();
    return getProgram(vstPluginPtr);
  }
  protected static native int getProgram(long pluginPtr);
  
  @Override
  public synchronized void setProgram(int index) {
    assertNativeComponentIsLoaded();
    if (index < 0 || index >= numPrograms) {
      throw new IndexOutOfBoundsException("The program index must be in [0, " + numPrograms + "): " + Integer.toString(index));
    }
    setProgram(index, vstPluginPtr);
  }
  protected static native void setProgram(int index, long pluginPtr);
  
  @Override
  public synchronized String getProgramName() {
    assertNativeComponentIsLoaded();
    return getProgramName(vstPluginPtr);
  }
  protected static native String getProgramName(long pluginPtr);
  
  @Override
  public synchronized String getProgramName(int index) {
    assertNativeComponentIsLoaded();
    if (index < 0 || index >= numPrograms) {
      throw new IndexOutOfBoundsException("The program index must be in [0, " + numPrograms + "): " + Integer.toString(index)); 
    }
    return getProgramName(index, vstPluginPtr);
  }
  protected static native String getProgramName(int index, long pluginPtr);
  

  @Override
  public synchronized void setProgramName(String name) {
    assertNativeComponentIsLoaded();
    setProgramName(name, vstPluginPtr);
  }
  protected static native void setProgramName(String name, long pluginPtr);
  
  @Override
  public synchronized int getPluginVersion() {
    assertNativeComponentIsLoaded();
    return getPluginVersion(vstPluginPtr);
  }
  protected static native int getPluginVersion(long pluginPtr);
  
  @Override
  public synchronized int getInitialDelay() {
    assertNativeComponentIsLoaded();
    return getInitialDelay(vstPluginPtr);
  }
  protected static native int getInitialDelay(long pluginPtr);
  
  @Override
  public synchronized void turnOn() {
    if (isTurnedOff) {
      resume(vstPluginPtr);
      isTurnedOff = false;
    }
  }
  protected static native void resume(long pluginPtr);
  
  @Override
  public synchronized void turnOff() {
    if (!isTurnedOff) {
      suspend(vstPluginPtr);
      isTurnedOff = true;
    }
  }
  protected static native void suspend(long pluginPtr);
  
  @Override
  public synchronized void setBankChunk(byte[] chunkData) {
    assertNativeComponentIsLoaded();
    if (chunkData == null) {
      throw new NullPointerException("Chunk data cannot be null.");
    }
    setChunk(0, chunkData, vstPluginPtr);
  }
  
  @Override
  public synchronized void setProgramChunk(byte[] chunkData) {
    assertNativeComponentIsLoaded();
    if (chunkData == null) {
      throw new NullPointerException("Chunk data cannot be null.");
    }
    setChunk(1, chunkData, vstPluginPtr);
  }
  protected static native void setChunk(int bankOrProgram, byte[] chunkData, long pluginPtr);
  
  @Override
  public synchronized byte[] getBankChunk() {
    assertNativeComponentIsLoaded();
    return getChunk(0, vstPluginPtr);
  }
  
  @Override
  public synchronized byte[] getProgramChunk() {
    assertNativeComponentIsLoaded();
    return getChunk(1, vstPluginPtr);
  }
  protected static native byte[] getChunk(int bankOrProgram, long pluginPtr);
  
  @Override
  public synchronized void setBypass(boolean bypass) {
    assertNativeComponentIsLoaded();
    setBypass(bypass, vstPluginPtr);
  }
  protected static native void setBypass(boolean bypass, long pluginPtr);
  
  @Override
  public synchronized VstPinProperties getInputProperties(int index) {
    if (index < 0 || index >= numInputs) {
      throw new IndexOutOfBoundsException("The input index must be in [0, " + numInputs + "): " + Integer.toString(index));
    }
    return getPinProperties(index, true, vstPluginPtr);
  }
  
  @Override
  public synchronized VstPinProperties getOutputProperties(int index) {
    if (index < 0 || index >= numOutputs) {
      throw new IndexOutOfBoundsException("The output index must be in [0, " + numOutputs + "): " + Integer.toString(index));
    }
    return getPinProperties(index, false, vstPluginPtr);
  }
  
  /**
   * @param isInput  <code>true</code> if the index refers to an input. <code>false</code> if it 
   * refers to an output.
   */
  protected static native VstPinProperties getPinProperties(int index, boolean isInput, long pluginPtr);
  
  public void setTimeSignature(int numerator, int denominator) {
    if (numerator <= 0) {
      throw new IllegalArgumentException("The number of note values per measure must be positive: " + Integer.toString(numerator));
    }
    if (denominator <= 0) {
      throw new IllegalArgumentException("The note value per beat must be positive: " + Integer.toString(denominator));
    }
    setTimeSignature(numerator, denominator, vstPluginPtr);
  }
  protected static native void setTimeSignature(int numerator, int denominator, long pluginPtr);
  
  /*
   * Native plugin callbacks.
   */
  protected synchronized void audioMasterProcessMidiEvents(int command, int channel, int data1, int data2) {
    try {
      ShortMessage message = new ShortMessage();
      message.setMessage(command, channel, data1, data2);
      for (JVstHostListener listener : hostListeners) {
        listener.onAudioMasterProcessMidiEvents(this, message);
      }
    } catch (InvalidMidiDataException imde) {
      /*
       * If there is a problem in constructing the ShortMessage, just print out an error message
       * and allow the program to continue. It isn't the end of the world.
       */
      imde.printStackTrace(System.err);
    }
  }
  
  protected synchronized void audioMasterAutomate(int index, float value) {
    for (JVstHostListener listener : hostListeners) {
      listener.onAudioMasterAutomate(this, index, value);
    }
  }
  
  protected synchronized void audioMasterIoChanged(int numInputs, int numOutputs, int initialDelay, int numParameters) {
    this.numInputs = numInputs; // update cached vars
    this.numOutputs = numOutputs;
    this.numParameters = numParameters;
    for (JVstHostListener listener : hostListeners) {
      listener.onAudioMasterIoChanged(this, numInputs, numOutputs, initialDelay, numParameters);
    }
  }
  
  protected synchronized void audioMasterBeginEdit(int index) {
    for (JVstHostListener listener : hostListeners) {
      listener.onAudioMasterBeginEdit(this, index);
    }
  }
  
  protected synchronized void audioMasterEndEdit(int index) {
    for (JVstHostListener listener : hostListeners) {
      listener.onAudioMasterEndEdit(this, index);
    }
  }
  
  /*
   * Listener manager methods.
   */
  @Override
  public synchronized void addJVstHostListener(JVstHostListener listener) {
    if (!hostListeners.contains(listener)) {
      hostListeners.add(listener);
    }
  }
  
  @Override
  public synchronized void removeJVstHostListener(JVstHostListener listener) {
    hostListeners.remove(listener);
  }
  
}
