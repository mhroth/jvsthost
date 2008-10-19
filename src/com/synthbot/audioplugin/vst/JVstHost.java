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

package com.synthbot.audioplugin.vst;

import java.io.File;
import java.util.Vector;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

public class JVstHost {

  public enum VstVersion {
    VST20(2), 
    VST21(2100), 
    VST22(2200), 
    VST23(2300), 
    VST24(2400),
    UNKNOWN(0);
    
    private final int id;
    
    private VstVersion(int id) {
      this.id = id;
    }
    
    /**
     * Returns the numerical version id.
     */
    public int getId() {
      return id;
    }
    
    public static VstVersion getVersion(int id) {
      for (VstVersion version : VstVersion.values()) {
        if (version.getId() == id) {
          return version;
        }
      }
      return UNKNOWN;
    }
  }
  
  private enum VstState {
    RESUMED,
    SUSPENDED;
  }
  
  // pointers
  private long vstPluginPtr = 0; // pointer to the C++ vst plugin object
  private long vstLibPtr = 0; // pointer to the library
  private long vstInputsPtr = 0;
  private long vstOutputsPtr = 0;
  private long osxWindow = 0; // pointer to editor window in osx
  private long vstTimeInfoPtr = 0; // used to store an outstanding VstTimeInfo pointer
  
  protected final File pluginFile;
  protected volatile int blockSize;
  protected volatile float sampleRate;
  protected int vstNumInputs; // cached locally for processReplacing
  protected int vstNumOutputs;  // cached locally for processReplacing
  
  protected VstState currentState = VstState.SUSPENDED;
  protected final VstVersion vstVersion;
  
  private final Vector<JVstHostListener> hostListeners;
  
  public JVstHost(File file, float sampleRate, int blockSize) throws JVstLoadException {
    if (!file.exists()) {
      throw new JVstLoadException("The plugin file does not exist: " + file.toString());
    }
    pluginFile = file;
    try {
      loadPlugin(pluginFile.toString());
    } catch (JVstLoadException jvle) {
      throw jvle; // pass on the exception
    }

    // configure the local variables
    this.sampleRate = sampleRate;
    setSampleRate(sampleRate);
    this.blockSize = blockSize;
    setBlockSize(blockSize);
    vstNumInputs = numInputs();
    vstNumOutputs = numOutputs();
    vstVersion = getVstVersion();
    
    hostListeners = new Vector<JVstHostListener>();
    
    turnOn();
  }

  @Override
  protected void finalize() throws Throwable {
    try {
      turnOff();
      unloadPlugin(vstInputsPtr, vstOutputsPtr, vstPluginPtr, vstLibPtr);
    } finally {
      super.finalize();
    }
  }

  static {
    System.loadLibrary("jvsthost");
  }

  // load the C++ vst plugin object
  private native void loadPlugin(String filename) throws JVstLoadException;

  private native void unloadPlugin(long inputsPtr, long outputsPtr, long pluginPtr, long libPtr);
  
  public void addJVstHostListener(JVstHostListener listener) {
    if (!hostListeners.contains(listener)) {
      hostListeners.add(listener);
    }
  }
  
  public void removeJVstHostListener(JVstHostListener listener) {
    hostListeners.remove(listener);
  }

  private native void processReplacing(float[][] inputs, float[][] outputs, int blockSize, int numInputs, int numOutputs, long inputsPtr, long outputsPtr, long pluginPtr);
  /**
   * Generate audio output from the plugin.
   * @param inputs  The audio input to the plugin is read from this array.
   * @param outputs  The output of the plugin will be placed into this array.
   * @param blockSize  Number of samples to read from the input and output buffers. May not be larger than the length of the arrays.
   */
  public void processReplacing(float[][] inputs, float[][] outputs, int blockSize) {
    processReplacing(inputs, outputs, blockSize, vstNumInputs, vstNumOutputs, vstInputsPtr, vstOutputsPtr, vstPluginPtr);
  }
  /**
   * Generate audio output from the plugin. Usually used with effect.
   * @param inputs The audio input to the plugin is read from this array.
   * @param outputs The output of the plugin will be placed into this array.
   */
  public void processReplacing(float[][] inputs, float[][] outputs) {
    processReplacing(inputs, outputs, outputs[0].length);
  }
  /**
   * Generate audio output from the plugin, without supplying any input. Usually used with synthesizers.
   * @param outputs  The output of the plugin will be placed into this array
   */
  public void processReplacing(float[][] outputs) {
    processReplacing(null, outputs);
  }

  private native void setParameter(int index, float value, long pluginPtr);
  /**
   * Set a parameter
   * @param index  Parameter index.
   * @param value  Parameter value.
   */
  public void setParameter(int index, float value) {
    setParameter(index, value, vstPluginPtr);
  }

  private native float getParameter(int index, long pluginPtr);
  public float getParameter(int index) {
    return getParameter(index, vstPluginPtr);
  }

  private native String getParameterName(int index, long pluginPtr);
  public String getParameterName(int index) {
    return getParameterName(index, vstPluginPtr);
  }

  private native String getParameterDisplay(int index, long pluginPtr);
  public String getParameterDisplay(int index) {
    return getParameterDisplay(index, vstPluginPtr);
  }

  private native String getParameterLabel(int index, long pluginPtr);
  public String getParameterLabel(int index) {
    return getParameterLabel(index, vstPluginPtr);
  }

  private native String getEffectName(long pluginPtr);
  public String getEffectName() {
    return getEffectName(vstPluginPtr);
  }
  
  private native String getVendorName(long pluginPtr);
  public String getVendorName() {
    return getVendorName(vstPluginPtr);
  }
  
  private native String getProductString(long pluginPtr);
  public String getProductString() {
    return getProductString(vstPluginPtr);
  }
  
  private native int numParameters(long pluginPtr);
  public int numParameters() {
    return numParameters(vstPluginPtr);
  }
  
  private native int numInputs(long pluginPtr);
  public int numInputs() {
    return numInputs(vstPluginPtr);
  }
  
  private native int numOutputs(long pluginPtr);
  public int numOutputs() {
    return numOutputs(vstPluginPtr);
  }
  
  private native void setSampleRate(float sampleRate, long pluginPtr);
  public void setSampleRate(float sampleRate) {
    this.sampleRate = sampleRate;
    setSampleRate(sampleRate, vstPluginPtr);
  }
  
  public float getSampleRate() {
    return sampleRate;
  }
  
  private native void setBlockSize(int blockSize, long pluginPtr);
  public void setBlockSize(int blockSize) {
    this.blockSize = blockSize;
    setBlockSize(blockSize, vstPluginPtr);
  }
  
  /**
   * @return The length of the audio output of one processing iteration.
   */
  public int getBlockSize() {
    return blockSize;
  }

  private native void suspend(long pluginPtr);
  public void suspend() {
    if (currentState == VstState.RESUMED) {
      suspend(vstPluginPtr);
      currentState = VstState.SUSPENDED;
    }
  }

  private native void resume(long pluginPtr);
  public void resume() {
    if (currentState == VstState.SUSPENDED) {
      resume(vstPluginPtr);
      currentState = VstState.RESUMED;
    }
  }

  private native void setMidiEvents(ShortMessage[] midiMessages, long pluginPtr);
  /**
   * Sends MIDI messages to the VST. Passing null or an array of length 0 will
   * execute dispatcher(effProcessEvents), passing a VstEvents struct with 0 events.
   */
  public void setMidiEvents(ShortMessage[] midiMessages) {
    setMidiEvents(midiMessages, vstPluginPtr);
  }

  private native String getProgramName(long pluginPtr);
  public String getProgramName() {
    return getProgramName(vstPluginPtr);
  }

  private native void setProgramName(String name, long pluginPtr);
  public void setProgramName(String name) {
    setProgramName(name, vstPluginPtr);
  }

  private native int getProgram(long pluginPtr);
  /**
   * Returns the current program index.
   */
  public int getProgram() {
    return getProgram(vstPluginPtr);
  }

  private native void setProgram(int index, long pluginPtr);
  public void setProgram(int index) {
    setProgram(index, vstPluginPtr);
  }
  
  private native int hasEditor(long pluginPtr);
  public boolean hasEditor() {
    return (hasEditor(vstPluginPtr) != 0);
  }
  
  private native int canReplacing(long pluginPtr);
  public boolean canReplacing() {
    return (canReplacing(vstPluginPtr) != 0);
  }
  
  private native int acceptsProgramsAsChunks(long pluginPtr);
  public boolean acceptsProgramsAsChunks() {
    return (acceptsProgramsAsChunks(vstPluginPtr) != 0);
  }
  
  private native byte[] getChunk(int bankOrProgram, long pluginPtr);
  public byte[] getBankChunk() {
    return getChunk(0, vstPluginPtr);
  }
  public byte[] getProgramChunk() {
    return getChunk(1, vstPluginPtr);
  }
  
  private native void setChunk(int bankOrProgram, byte[] chunkData, long pluginPtr);
  public void setBankChunk(byte[] chunkData) {
    setChunk(0, chunkData, vstPluginPtr);
  }
  public void setProgramChunk(byte[] chunkData) {
    setChunk(1, chunkData, vstPluginPtr);
  }
  
  private native int isSynth(long pluginPtr);
  public boolean isSynth() {
    return (isSynth(vstPluginPtr) != 0);
  }
  
  private native void setBypass(boolean bypass, long pluginPtr);
  public void setBypass(boolean bypass) {
    setBypass(bypass, vstPluginPtr);
  }
  
  private native int canDoBypass(long pluginPtr);
  public boolean canDoBypass() {
    return (canDoBypass(vstPluginPtr) == 1);
  }
  
  private native int producesSoundInStop(long pluginPtr);
  public boolean producesSoundInStop() {
    return (producesSoundInStop(vstPluginPtr) != 0);
  }
  
  private native void openEditor(long pluginPtr);
  public void openEditor() {
    openEditor(vstPluginPtr);
  }
  
  private native void closeEditor(long pluginPtr);
  public void closeEditor() {
    if (osxWindow != 0) {
      closeEditor(vstPluginPtr);
    }
  }

  private native void editIdle(long pluginPtr);
  /**
   * Tell the plugin that it can update its native editor.
   */
  public void editIdle() {
    editIdle(vstPluginPtr);
  }
  
  private native int getUniqueId(long pluginPtr);
  /**
   * Returns the unique id of the plugin as a four character string.
   */
  public String getUniqueId() {
    int uniqueId = getUniqueId(vstPluginPtr);
    byte[] uidArray = {
        (byte) (0x000000FF & (uniqueId >> 24)), 
        (byte) (0x000000FF & (uniqueId >> 16)), 
        (byte) (0x000000FF & (uniqueId >> 8)), 
        (byte) uniqueId};
    return new String(uidArray);
  }
  /**
   * Returns the unique id of the plugin as an integer.
   */
  public int getUniqueIdAsInt() {
    return getUniqueId(vstPluginPtr);
  }
  
  private native int getVersion(long pluginPtr);
  /**
   * Get the plugin version.
   */
  public int getVersion() {
    return getVersion(vstPluginPtr);
  }
  
  private native int getVstVersion(long pluginPtr);
  public VstVersion getVstVersion() {
    return VstVersion.getVersion(getVstVersion(vstPluginPtr));
  }
  
  private native void startProcess(long pluginPtr);
  /**
   * Called one time before the start of process call. This indicates that the process call will be interrupted.
   */
  public void startProcess() {
    startProcess(vstPluginPtr);
  }
  
  private native void stopProcess(long pluginPtr);
  /**
   * Called after the stop of process call.
   */
  public void stopProcess() {
    stopProcess(vstPluginPtr);
  }
  
  /**
   * Returns the length of the tail when no notes are playing (e.g., reverb tail length)
   * @param pluginPtr
   * @return 0 [default]
   *         1 [no tail]
   *         tail length (samples) otherwise
   */
  private native int getTailSize(long pluginPtr);
  public int getTailSize() {
    return getTailSize(vstPluginPtr);
  }
  
  /**
   * Turns the plugin on. Calls resume() and startProcess(), if necessary.
   */
  public void turnOn() {
    resume();
    switch(vstVersion) {
      case VST23:
      case VST24:
        startProcess();
        break;
      default:
        break;
    }
  }
  
  /**
   * Turns the plugin off. Calls stopProcess(), if necessary, and suspend().
   */
  public void turnOff() {
    switch(vstVersion) {
      case VST23:
      case VST24:
        stopProcess();
        break;
      default:
        break;
    }
    suspend();
  }
  
  /**
   * Resets the plugin by unloading the native component and loading it again
   * from scratch.
   * @throws JVstLoadException
   */
  public void reset() throws JVstLoadException {
    turnOff();
    unloadPlugin(vstInputsPtr, vstOutputsPtr, vstPluginPtr, vstLibPtr);
    try {
      loadPlugin(pluginFile.toString());
    } catch (JVstLoadException jvle) {
      throw jvle;
    }
    setBlockSize(blockSize);
    setSampleRate(sampleRate);
    turnOn();
  }
    
  // === Callbacks from JNI library === //
  
  public static void println(String s) {
    System.out.println(s);
  }
  
  public String getPluginDirectory() {
    return pluginFile.getAbsolutePath();
  }
  
  /**
   * Processes a MIDI event sent from the plugin.
   * http://www.midi.org/about-midi/table3.shtml
   * @param channel
   * @param command
   * @param data1
   * @param data2
   */
  private void audioMasterProcessMidiEvents(int command, int channel, int data1, int data2) {
    try {
      ShortMessage message = new ShortMessage();
      message.setMessage(command, channel, data1, data2);
      for (JVstHostListener listener : hostListeners) {
        listener.onAudioMasterProcessMidiEvents(message);
      }
    } catch (InvalidMidiDataException imde) {
      imde.printStackTrace(System.err);
      return;
    }
  }
  
  /**
   * Called when a plugin changes its own parameter state, such as though the use of the
   * native editor.
   * @param index Index of the automated parameter
   * @param value New value of the automated parameter
   */
  private void audioMasterAutomate(int index, float value) {
    for (JVstHostListener listener : hostListeners) {
      listener.onAudioMasterAutomate(index, value);
    }
  }
  
}