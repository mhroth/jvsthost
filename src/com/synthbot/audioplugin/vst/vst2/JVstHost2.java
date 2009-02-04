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

import java.io.File;
import java.io.FileNotFoundException;

import javax.sound.midi.ShortMessage;

import com.synthbot.audioplugin.vst.JVstLoadException;
import com.synthbot.audioplugin.vst.VstVersion;
import com.synthbot.audioplugin.vst.view.JVstViewListener;

/**
 * JVstHost2 is an abstract class which generally defines the interface of a VST 2.x plugin. New instances can only be instantiated
 * by using <code>newInstance</code>. A subclass is returned which implements all of the methods of the corresponding VST version.
 * These subclasses are JVstHost20, JVstHost21, JVstHost22, JVstHost23, and JVstHost24. If you need a method which is
 * specific to one of the VST versions, such as <code>processDoubleReplacing</code> in JVstHost24, then you must cast the resulting
 * JVstHost2 to that class. You can check which VST version the plugin implements with <code>getVstVersion</code>.
 * Note that some basic methods, such as <code>process</code>, are deprecated in later VST versions (e.g., 2.4), and will
 * throw an exception if used.
 * 
 * I have tried to keep the method names as similar to the original C/C++ specification as possible.
 * Unfortunately the original functions are not always consistently or sensibly named. Complain to Steinberg.
 * Most methods in this and subclasses will check to make sure that the native library is loaded
 * before executing any native code. If a method is called and the native library is not loaded,
 * then an <code>IllegalStateException</code> will be thrown.
 * 
 * All public methods of JVstHost2 and its subclasses are synchronized. They are thread-safe.
 *
 */
public abstract class JVstHost2 implements JVstViewListener {
  
  protected final File pluginFile;
  protected final long vstPluginPtr;
  protected boolean isNativeComponentLoaded;
  
  protected JVstHost2(File pluginFile, long pluginPtr) {
    this.pluginFile = pluginFile;
    this.vstPluginPtr = pluginPtr;
    isNativeComponentLoaded = true;
  }
  
  /**
   * Instantiates a subclass of JVstHost, depending on the VST version. The returned plugin
   * is fully started and ready to process audio and events.
   * @param file  The location of the native plugin library.
   * @param sampleRate  The sample rate at which the plugin should operate.
   * @param blockSize  The maximum size of an audio block
   * @return A new instance of a <code>JVstHost2</code> subclass corresponding to the plugin's vst version.
   * @throws FileNotFoundException  Thrown if the given VST File does not exist.
   * @throws IllegalArgumentException  Thrown if the supplied sample rate or block size exceed their allowed values.
   * See <code>setSampleRate</code> and <code>setBlockSize</code>.
   * @throws JVstLoadException  Thrown if there are any errors while loading the native VST.
   * @throws NullPointerException  Thrown if the given VST File is null.
   * @throws For more information on exceptions, see <a href="http://github.com/mhroth/jvsthost/wikis/micro-blog/#fn3">http://github.com/mhroth/jvsthost/wikis/micro-blog/</a>
   */
  public static JVstHost2 newInstance(File file, float sampleRate, int blockSize) throws FileNotFoundException, JVstLoadException {
    JVstHost2 vst = newInstance(file);
    vst.setSampleRate(sampleRate);
    vst.setBlockSize(blockSize);
    vst.turnOn();
    return vst;
  }
  
  /**
   * Instantiates a subclass of JVstHost, depending on the VST version. The resulting plugin is
   * only initialised, and not started, nor supplied with necessary information, such as sample rate
   * or block size.
   * @param file  The location of the native plugin library.
   * @return A new instance of a <code>JVstHost2</code> subclass corresponding to the plugin's vst version.
   * @throws FileNotFoundException  Thrown if the given VST File does not exist.
   * @throws JVstLoadException  Thrown if there are any errors while loading the native VST.
   * @throws NullPointerException  Thrown if the given VST File is null.
   * @throws For more information on exceptions, see <a href="http://github.com/mhroth/jvsthost/wikis/micro-blog/#fn3">http://github.com/mhroth/jvsthost/wikis/micro-blog/</a>
   */
  public static JVstHost2 newInstance(File file) throws FileNotFoundException, JVstLoadException {
    if (file == null) {
      throw new NullPointerException("VST file cannot be null. Specify a non-null File object.");
    }
    if (!file.exists()) {
      throw new FileNotFoundException(file.toString());
    }
    long pluginPtr = loadPlugin(file.toString());
    int vstVersionInt = getVstVersion(pluginPtr);
    VstVersion vstVersion = VstVersion.getVersion(vstVersionInt);
    switch (vstVersion) {
      case VST20: {
        return new JVstHost20(file, pluginPtr);        
      }
      case VST21: {
        return new JVstHost21(file, pluginPtr);
      }
      case VST22: {
        return new JVstHost22(file, pluginPtr); 
      }
      case VST23: {
        return new JVstHost23(file, pluginPtr);
      }
      case VST24: {
        return new JVstHost24(file, pluginPtr);
      }
      default: {
        throw new JVstLoadException("Unsupported VST version: " + Integer.toString(vstVersionInt));
      }
    }
  }
  
  /**
   * Automatically unloads the native component if not already done.
   */
  @Override
  protected synchronized void finalize() throws Throwable {
    try {
      if (isNativeComponentLoaded()) {
        turnOffAndUnloadPlugin();
      }
    } finally {
      super.finalize();
    }
  }

  static {
    System.loadLibrary("jvsthost2");
  }
  
  @Override
  public String toString() {
    return getEffectName() + "@0x" + Long.toHexString(vstPluginPtr); 
  }
  
  /**
   * Indicates if the native component of the plugin is loaded. Any use of the 
   * JVstHost2 object while the plugin is not loaded will throw an <code>IllegalStateException</code>.
   * @return  True if the native component is successfully loaded. False otherwise.
   */
  public synchronized boolean isNativeComponentLoaded() {
    return isNativeComponentLoaded;
  }
  
  protected synchronized void assertNativeComponentIsLoaded() {
    if (!isNativeComponentLoaded()) {
      throw new IllegalStateException("The native component is not currently loaded.");
    }
  }

  /**
   * Loads the native vst plugin object.
   * @return  A pointer to the native vst.
   * @throws JVstLoadException  Thrown if there is a problem loading or instantiating the native object.
   */
  protected static native long loadPlugin(String pluginFile) throws JVstLoadException;
  
  /**
   * Stop and unload the native component of the plugin. Any further use of this object will cause an <code>IllegalStateException</code>
   * to be thrown. This method is publicly exposed in order to allow the potentially heavy native component to be
   * manually managed. Java garbage collection does not act appropriately under these circumstances, as the JVstHost
   * java object is lightweight, and may not be expunged in a timely manner. This leaves the large native component
   * in memory while the java object awaits garbage collection. This method is automatically invoked by the java finalizer
   * if the native object is still loaded; it is not necessary for this method to be manually invoked.
   */
  public abstract void turnOffAndUnloadPlugin();
  
  /**
   * Unload and dispose of the native vst.
   * @param pluginPtr  A pointer to the native component.
   */
  protected static native void unloadPlugin(long pluginPtr);
  
  /**
   * Returns the VST version of the plugin.
   * @return  The VST version of the plugin.
   */
  public abstract VstVersion getVstVersion();
  protected static native int getVstVersion(long pluginPtr);
  
  /**
   * Returns the version of the plugin.
   * @return  The plugin version (example 1100 for version 1.1.0.0)
   */
  public abstract int getPluginVersion();

  /**
   * Get the current value of a parameter.
   * @param index  Parameter index.
   * @throws IndexOutOfBoundsException  Thrown if the parameter index is < 0 or >= numParameters.
   */
  public abstract float getParameter(int index);
  
  /**
   * Set a parameter to a value.
   * @param index  Parameter index.
   * @param value  Parameter value.
   * @throws IllegalArgumentException  Thrown if the parameter value is not between 0f and 1f.
   * @throws IndexOutOfBoundsException  Thrown if the parameter index is < 0 or >= numParameters.
   */
  public abstract void setParameter(int index, float value);
  
  /**
   * Queues the given midi message until the next time that a <code>process</code> variant is called.
   * The queue is cleared upon the execution of <code>process</code>.
   * @param message  A MIDI message to be queued.
   * @throws NullPointerException  Thrown if the queued midi message is null.
   */
  public abstract void queueMidiMessage(ShortMessage message);
  
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
  public abstract void processReplacing(float[][] inputs, float[][] outputs, int blockSize);
  
  /**
   * Determines if the plugin implements processReplacing(). It is an error to call
   * processReplacing() if the method is not implemented.
   * @return  True if the plugin implements processReplacing(). False otherwise.
   */
  public abstract boolean canReplacing();
  
  /**
   * Generate audio output from the plugin, adding the output to the output array. Queued MIDI messages, via <code>queueMidiMessage</code>, will be passed to the plugin.
   * @param inputs  The audio input to the plugin is read from this array.
   * @param outputs  The output of the plugin will be placed into this array.
   * @param blockSize  Number of samples to read from the input and output buffers. May not be larger than the length of the arrays.
   * @throws IllegalArgumentException  Thrown if any of the arguments do not lie within their natural bounds.
   * @throws IllegalStateException  Thrown if the plugin does not implement processReplacing(). Check canReplacing().
   * @throws OutOfMemoryError  Thrown in the rare case that native input and output arrays cannot be allocated by the JVM.
   * @throws NullPointerException  Thrown if the input or output arrays are null.
   */
  public abstract void process(float[][] inputs, float[][] outputs, int blockSize);
  
  /**
   * Returns the unique id of the plugin as a four character string.
   */
  public abstract String getUniqueId();
  
  /**
   * Returns the unique id of the plugin as an integer.
   */
  public abstract int getUniqueIdAsInt();

  /**
   * Get the name of a parameter.
   * Frequency: 10 Hz (Name: Display Label)
   * @param index  Parameter index.
   * @throws IndexOutOfBoundsException  Thrown if the parameter index is < 0 or >= numParameters.
   */
  public abstract String getParameterName(int index);

  /**
   * Get the display string of a parameter.
   * Frequency: 10 Hz (Name: Display Label)
   * @param index  Parameter index.
   * @throws IndexOutOfBoundsException  Thrown if the parameter index is < 0 or >= numParameters.
   */
  public abstract String getParameterDisplay(int index);

  /**
   * Get the parameter label.
   * Frequency: 10 Hz (Name: Display Label)
   * @param index  Parameter index.
   * @throws IndexOutOfBoundsException  Thrown if the parameter index is < 0 or >= numParameters.
   */
  public abstract String getParameterLabel(int index);

  public abstract String getEffectName();
  
  public abstract String getVendorName();
  
  public abstract String getProductString();
  
  public abstract int numParameters();
  
  public abstract int numInputs();
  
  public abstract int numOutputs();
  
  public abstract int numPrograms();
  
  public abstract boolean isSynth();
  
  /**
   * Set the current program to the given index.
   * @param index  Program index.
   * @throws IndexOutOfBoundsException  Thrown if the program index is negative.
   */
  public abstract void setProgram(int index);

  /**
   * Returns the current program index.
   * @return  The current program index.
   */
  public abstract int getProgram();
  
  public abstract String getProgramName();

  public abstract void setProgramName(String name);
  
  /**
   * Set the sample rate at which the plugin should process the audio.
   * @param sampleRate  The new sample rate.
   * @throws IllegalArgumentException  Thrown if the new sample rate is non-positive.
   * @throws IllegalStateException  Thrown if the plugin is turned on.
   */
  public abstract void setSampleRate(float sampleRate);
  
  /**
   * Returns the last sample rate to which the plugin was set (via <code>setSampleRate</code>).
   * @return  The last sample rate to which the plugin was set.
   */
  public abstract float getSampleRate();
  
  /**
   * Set the maximum block size which the plugin should expect to process data.
   * The block size can be made smaller when calling processReplacing().
   * @param blockSize  The new block size.
   * @throws IllegalArgumentException  Thrown in the block size is negative.
   * @throws IllegalStateException  Thrown if the plugin is turned on.
   */
  public abstract void setBlockSize(int blockSize);
  
  /**
   * Returns the last block size to which the plugin was set (via <code>setBlockSize</code>).
   * @return  The last block size to which the plugin was set.
   */
  public abstract int getBlockSize();
  
  /**
   * Set the current tempo in beats per minute (BPM). Some plugins have tempo-based
   * effects, such as synchronised LFOs, etc. This value will influence their function.
   * A default value of 120 BPM is used.
   * @param tempo  The new tempo in beats per minute.
   */
  public abstract void setTempo(double tempo);
  
  /**
   * Returns the initial plugin audio processing delay in samples.
   * @return  The initial plugin audio processing delay in samples.
   */
  public abstract int getInitialDelay();
  
  /**
   * Queries the plugin regarding which optional capabilities that it supports.
   * @param canDo  
   * @return  True if the capability is supported. False otherwise.
   */
  public abstract boolean canDo(VstPluginCanDo canDo);
  
  /**
   * Load a bank.
   * @param chunk  A byte array representing the bank data to load.
   * @throws NullPointerException  Thrown if the argument is null.
   */
  public abstract void setBankChunk(byte[] chunk);
  
  /**
   * Load a program.
   * @param chunk  A byte array representing the program data to load.
   * @throws NullPointerException  Thrown if the argument is null.
   */
  public abstract void setProgramChunk(byte[] chunk);

  /**
   * Get data representing the current bank. Used for persistent storage.
   * @return  A byte array representing the current bank.
   */
  public abstract byte[] getBankChunk();
  
  /**
   * Get data representing the current program. Used for persistent storage.
   * @return  A byte array representing the current program.
   */
  public abstract byte[] getProgramChunk();
  
  /**
   * 
   * @return
   */
  public abstract boolean acceptsProgramsAsChunks();
  
  /**
   * Open the native editor window. This method does nothing if the editor is already open.
   * NOTE: This method is currently only implemented on Windows.
   * @param frameTitle  The title of the native editor frame. This can be used to uniquely identify individual windows.
   * @throws IllegalStateException  Thrown if the plugin has no native editor. Check to see if the plugin has a native editor with <code>hasEditor</code>.
   * @throws NullPointerException  Thrown if <code>frameTitle</code> is null.
   */
  public abstract void openEditor(final String frameTitle);
  
  /**
   * Checks if the editor is currently open.
   * @return  True if the native editor of the plugin is currently open. False otherwise.
   */
  public abstract boolean isEditorOpen();
  
  /**
   * Close the native editor window.
   * NOTE: This method is not yet implemented.
   * @throws IllegalStateException  Thrown if the plugin has no native editor. Check to see if the plugin has a native editor with <code>hasEditor</code>.
   */
  public abstract void closeEditor();
  
  /**
   * Determines if the plugin implements a graphical editor from which it can be manipulated.
   * @return True if the plugin has an editor. False otherwise.
   */
  public abstract boolean hasEditor();
  
  /**
   * Returns the absolute path of the plugin file.
   * @return  The absolute path of the plugin file.
   */
  public String getPluginPath() {
    return pluginFile.getAbsolutePath();
  }
  
  /**
   * Ensure that the plugin is initialised and ready to process. Plugins must be
   * turned on in order to process audio or midi events. Successive invocations of
   * this method have no effect.
   */
  public abstract void turnOn();
  
  /**
   * Suspend the plugin's operation. Plugins must usually be turned off in order
   * to set critical parameters such as sample rate or block size. Successive invocations
   * of this method have no effect.
   */
  public abstract void turnOff();
  
  /**
   * Changes the bypass mode of the plugin, if it supports the option.
   * @param bypass  True if bypass should be turned on. False otherwise.
   */
  public abstract void setBypass(boolean bypass);
  
  /*
   * Callbacks from the native plugin.
   */
  protected abstract void audioMasterProcessMidiEvents(int command, int channel, int data1, int data2);
  
  protected abstract void audioMasterAutomate(int index, float value);
  
  protected abstract void audioMasterIoChanged(int numInputs, int numOutputs, int initialDelay, int numParameters);
  
  protected abstract void audioMasterBeginEdit(int index);
  
  protected abstract void audioMasterEndEdit(int index);
  
  /*
   * Listener manager methods.
   */
  public abstract void addJVstHostListener(JVstHostListener listener);
  
  public abstract void removeJVstHostListener(JVstHostListener listener);

}
