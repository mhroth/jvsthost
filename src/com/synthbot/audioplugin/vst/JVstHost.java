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

import com.synthbot.audioplugin.vst.view.JVstView;
import com.synthbot.audioplugin.vst.view.JVstViewListener;

import java.io.File;
import java.util.Vector;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

/**
 * 
 * All methods are synchronized in order to ensure that multiple threads accessing
 * the object (such as a gui thread and an automation thread) do not cause trouble.
 */
public class JVstHost implements JVstViewListener {

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
  
  protected JVstView javaEditor = null;
  
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
  protected synchronized void finalize() throws Throwable {
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
  private synchronized native void loadPlugin(String filename) throws JVstLoadException;

  private synchronized native void unloadPlugin(long inputsPtr, long outputsPtr, long pluginPtr, long libPtr);
  
  public synchronized void addJVstHostListener(JVstHostListener listener) {
    if (!hostListeners.contains(listener)) {
      hostListeners.add(listener);
    }
  }
  
  public synchronized void removeJVstHostListener(JVstHostListener listener) {
    hostListeners.remove(listener);
  }

  private synchronized native void processReplacing(float[][] inputs, float[][] outputs, int blockSize, int numInputs, int numOutputs, long inputsPtr, long outputsPtr, long pluginPtr);
  /**
   * Generate audio output from the plugin.
   * @param inputs  The audio input to the plugin is read from this array.
   * @param outputs  The output of the plugin will be placed into this array.
   * @param blockSize  Number of samples to read from the input and output buffers. May not be larger than the length of the arrays.
   */
  public synchronized void processReplacing(float[][] inputs, float[][] outputs, int blockSize) {
    processReplacing(inputs, outputs, blockSize, vstNumInputs, vstNumOutputs, vstInputsPtr, vstOutputsPtr, vstPluginPtr);
  }
  /**
   * Generate audio output from the plugin. Usually used with effect.
   * @param inputs The audio input to the plugin is read from this array.
   * @param outputs The output of the plugin will be placed into this array.
   */
  public synchronized void processReplacing(float[][] inputs, float[][] outputs) {
    processReplacing(inputs, outputs, outputs[0].length);
  }
  /**
   * Generate audio output from the plugin, without supplying any input. Usually used with synthesizers.
   * @param outputs  The output of the plugin will be placed into this array
   */
  public synchronized void processReplacing(float[][] outputs) {
    processReplacing(null, outputs);
  }

  private synchronized native void setParameter(int index, float value, long pluginPtr);
  /**
   * Set a parameter
   * @param index  Parameter index.
   * @param value  Parameter value.
   */
  public synchronized void setParameter(int index, float value) {
    setParameter(index, value, vstPluginPtr);
    if (shouldUpdateJavaEditor()) {
      javaEditor.updateParameter(index, value, getParameterDisplay(index));
    }
  }

  private synchronized native float getParameter(int index, long pluginPtr);
  public synchronized float getParameter(int index) {
    return getParameter(index, vstPluginPtr);
  }

  private synchronized native String getParameterName(int index, long pluginPtr);
  public synchronized String getParameterName(int index) {
    return getParameterName(index, vstPluginPtr);
  }

  private synchronized native String getParameterDisplay(int index, long pluginPtr);
  public synchronized String getParameterDisplay(int index) {
    return getParameterDisplay(index, vstPluginPtr);
  }

  private synchronized native String getParameterLabel(int index, long pluginPtr);
  public synchronized String getParameterLabel(int index) {
    return getParameterLabel(index, vstPluginPtr);
  }

  private synchronized native String getEffectName(long pluginPtr);
  public synchronized String getEffectName() {
    return getEffectName(vstPluginPtr);
  }
  
  private synchronized native String getVendorName(long pluginPtr);
  public synchronized String getVendorName() {
    return getVendorName(vstPluginPtr);
  }
  
  private synchronized native String getProductString(long pluginPtr);
  public synchronized String getProductString() {
    return getProductString(vstPluginPtr);
  }
  
  private synchronized native int numParameters(long pluginPtr);
  public synchronized int numParameters() {
    return numParameters(vstPluginPtr);
  }
  
  private synchronized native int numInputs(long pluginPtr);
  public synchronized int numInputs() {
    return numInputs(vstPluginPtr);
  }
  
  private synchronized native int numOutputs(long pluginPtr);
  public synchronized int numOutputs() {
    return numOutputs(vstPluginPtr);
  }
  
  private synchronized native void setSampleRate(float sampleRate, long pluginPtr);
  public synchronized void setSampleRate(float sampleRate) {
    this.sampleRate = sampleRate;
    setSampleRate(sampleRate, vstPluginPtr);
  }
  
  public synchronized float getSampleRate() {
    return sampleRate;
  }
  
  private synchronized native void setBlockSize(int blockSize, long pluginPtr);
  public synchronized void setBlockSize(int blockSize) {
    this.blockSize = blockSize;
    setBlockSize(blockSize, vstPluginPtr);
  }
  
  /**
   * @return The length of the audio output of one processing iteration.
   */
  public synchronized int getBlockSize() {
    return blockSize;
  }

  private synchronized native void suspend(long pluginPtr);
  public synchronized void suspend() {
    if (currentState == VstState.RESUMED) {
      suspend(vstPluginPtr);
      currentState = VstState.SUSPENDED;
    }
  }

  private synchronized native void resume(long pluginPtr);
  public synchronized void resume() {
    if (currentState == VstState.SUSPENDED) {
      resume(vstPluginPtr);
      currentState = VstState.RESUMED;
    }
  }

  private synchronized native void setMidiEvents(ShortMessage[] midiMessages, long pluginPtr);
  /**
   * Sends MIDI messages to the VST. Passing null or an array of length 0 will
   * execute dispatcher(effProcessEvents), passing a VstEvents struct with 0 events.
   */
  public synchronized void setMidiEvents(ShortMessage[] midiMessages) {
    setMidiEvents(midiMessages, vstPluginPtr);
  }

  private synchronized native String getProgramName(long pluginPtr);
  public synchronized String getProgramName() {
    return getProgramName(vstPluginPtr);
  }

  private synchronized native void setProgramName(String name, long pluginPtr);
  public synchronized void setProgramName(String name) {
    setProgramName(name, vstPluginPtr);
  }

  private synchronized native int getProgram(long pluginPtr);
  /**
   * Returns the current program index.
   */
  public synchronized int getProgram() {
    return getProgram(vstPluginPtr);
  }

  private synchronized native void setProgram(int index, long pluginPtr);
  public synchronized void setProgram(int index) {
    setProgram(index, vstPluginPtr);
    if (shouldUpdateJavaEditor()) {
      javaEditor.updateProgram(index);
    }
  }
  
  private synchronized native int hasEditor(long pluginPtr);
  public synchronized boolean hasNativeEditor() {
    return (hasEditor(vstPluginPtr) != 0);
  }
  
  private synchronized native int canReplacing(long pluginPtr);
  public synchronized boolean canReplacing() {
    return (canReplacing(vstPluginPtr) != 0);
  }
  
  private synchronized native int acceptsProgramsAsChunks(long pluginPtr);
  public synchronized boolean acceptsProgramsAsChunks() {
    return (acceptsProgramsAsChunks(vstPluginPtr) != 0);
  }
  
  private synchronized native byte[] getChunk(int bankOrProgram, long pluginPtr);
  public synchronized byte[] getBankChunk() {
    return getChunk(0, vstPluginPtr);
  }
  public synchronized byte[] getProgramChunk() {
    return getChunk(1, vstPluginPtr);
  }
  
  private synchronized native void setChunk(int bankOrProgram, byte[] chunkData, long pluginPtr);
  public synchronized void setBankChunk(byte[] chunkData) {
    setChunk(0, chunkData, vstPluginPtr);
  }
  public synchronized void setProgramChunk(byte[] chunkData) {
    setChunk(1, chunkData, vstPluginPtr);
  }
  
  private synchronized native int isSynth(long pluginPtr);
  public synchronized boolean isSynth() {
    return (isSynth(vstPluginPtr) != 0);
  }
  
  private synchronized native void setBypass(boolean bypass, long pluginPtr);
  public synchronized void setBypass(boolean bypass) {
    setBypass(bypass, vstPluginPtr);
  }
  
  private synchronized native int canDoBypass(long pluginPtr);
  public synchronized boolean canDoBypass() {
    return (canDoBypass(vstPluginPtr) == 1);
  }
  
  private synchronized native int producesSoundInStop(long pluginPtr);
  public synchronized boolean producesSoundInStop() {
    return (producesSoundInStop(vstPluginPtr) != 0);
  }
  
  private synchronized native void openEditor(long pluginPtr);
  /**
   * Opens the native plugin editor.
   */
  public synchronized void openNativeEditor() {
    openEditor(vstPluginPtr);
  }
  
  private synchronized native void closeEditor(long pluginPtr);
  /**
   * Closes the native plugin editor.
   */
  public synchronized void closeNativeEditor() {
    if (osxWindow != 0) {
      closeEditor(vstPluginPtr);
    }
  }
  
  public synchronized void setJavaEditor(JVstView javaEditor) {
    this.javaEditor = javaEditor;
  }
  
//  public synchronized void setJavaEditor(Class<JVstGui> clazz) {
//    try {
//      this.javaEditor = clazz.newInstance();
//      javaEditor.init(this);
//    } catch (java.lang.IllegalAccessException ia) {
//      ia.printStackTrace(System.err);
//    } catch (java.lang.InstantiationException ie) {
//      ie.printStackTrace(System.err);
//    } catch (java.lang.ExceptionInInitializerError eiie) {
//      eiie.printStackTrace(System.err);
//    } catch (java.lang.SecurityException se) {
//      se.printStackTrace(System.err);
//    }
//  }
  
  public synchronized boolean hasJavaEditor() {
    return (javaEditor != null);
  }
  
  public synchronized JVstView getJavaEditor() {
    return javaEditor;
  }
  
  public synchronized JVstView removeJavaEditor() {
    JVstView gui = javaEditor;
    javaEditor = null;
    return gui;
  }
  
  protected synchronized boolean shouldUpdateJavaEditor() {
    return (javaEditor != null && javaEditor.isVisible());
  }
  
  /**
   * Opens a simple Java rendered string-based editor.
   */
  public synchronized void openJavaEditor() {
    if (javaEditor != null && !javaEditor.isVisible()) {
      javaEditor.setVisible(true);
    }
  }
  
  /**
   * Closes the Java rendered string-based editor.
   */
  public synchronized void closeJavaEditor() {
    if (javaEditor != null && javaEditor.isVisible()) {
      javaEditor.setVisible(false);
    }
  }
  
  public synchronized boolean isJavaEditorOpen() {
    return (javaEditor != null);
  }

  private synchronized native void editIdle(long pluginPtr);
  /**
   * Tell the plugin that it can update its native editor.
   */
  public synchronized void editIdle() {
    editIdle(vstPluginPtr);
  }
  
  private synchronized native int getUniqueId(long pluginPtr);
  /**
   * Returns the unique id of the plugin as a four character string.
   */
  public synchronized String getUniqueId() {
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
  public synchronized int getUniqueIdAsInt() {
    return getUniqueId(vstPluginPtr);
  }
  
  private synchronized native int getVersion(long pluginPtr);
  /**
   * Get the plugin version.
   */
  public synchronized int getVersion() {
    return getVersion(vstPluginPtr);
  }
  
  private synchronized native int getVstVersion(long pluginPtr);
  public synchronized VstVersion getVstVersion() {
    return VstVersion.getVersion(getVstVersion(vstPluginPtr));
  }
  
  private synchronized native void startProcess(long pluginPtr);
  /**
   * Called one time before the start of process call. This indicates that the process call will be interrupted.
   */
  public synchronized void startProcess() {
    startProcess(vstPluginPtr);
  }
  
  private synchronized native void stopProcess(long pluginPtr);
  /**
   * Called after the stop of process call.
   */
  public synchronized void stopProcess() {
    stopProcess(vstPluginPtr);
  }
  
  /**
   * Returns the length of the tail when no notes are playing (e.g., reverb tail length)
   * @param pluginPtr
   * @return 0 [default]
   *         1 [no tail]
   *         tail length (samples) otherwise
   */
  private synchronized native int getTailSize(long pluginPtr);
  public synchronized int getTailSize() {
    return getTailSize(vstPluginPtr);
  }
  
  /**
   * Turns the plugin on. Calls resume() and startProcess(), if necessary.
   */
  public synchronized void turnOn() {
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
  public synchronized void turnOff() {
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
  public synchronized void reset() throws JVstLoadException {
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
  
  public synchronized static void println(String s) {
    System.out.println(s);
  }
  
  public synchronized String getPluginDirectory() {
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
  private synchronized void audioMasterProcessMidiEvents(int command, int channel, int data1, int data2) {
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
  private synchronized void audioMasterAutomate(int index, float value) {
    for (JVstHostListener listener : hostListeners) {
      listener.onAudioMasterAutomate(index, value);
    }
    if (shouldUpdateJavaEditor()) {
      javaEditor.updateParameter(index, value, getParameterDisplay(index));
    }
  }
  
}