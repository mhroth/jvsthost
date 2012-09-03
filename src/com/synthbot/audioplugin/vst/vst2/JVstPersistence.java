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

import java.io.*;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

/**
 * A class to load and save VST preset files (*.fxp, *.fxb)
 */
public class JVstPersistence {
  
  private static final String CHUNK_MAGIC = "CcnK";
  private static final String REGULAR_PRESET_MAGIC = "FxCk";
  private static final String OPAQUE_PRESET_MAGIC = "FPCh";
  private static final String REGULAR_BANK_MAGIC = "FxBk";
  private static final String OPAQUE_BANK_MAGIC = "FBCh";
  
  /**
   * In some VSTPlugins the version number inside the preset/bank files is buggy. Set this
   * to true to skip the version number checks when loading a preset/bank file.
   */
  public static boolean ignorePluginVersion = false;

  /**
   * Loads a program preset from file to the current program of the plugin.
   * @param vst  The plugin to load the data into.
   * @param file  The file to read which contains the program data.
   * @throws DataFormatException  Thrown if there is a problem loading the data in the file to the plugin.
   * @throws FileNotFoundException  Thrown if the file to load cannot be found, or is not a file (perhaps it is a directory).
   * @throws IOException  Thrown if there is a problem with opening or reading the file.
   * @throws NullPointerException  Thrown if the given plugin or file are <code>null</code>.
   */
  public static void loadPreset(JVstHost2 vst, File file) throws DataFormatException, IOException {
    if (vst == null) {
      throw new NullPointerException("The given JVstHost2 object may not be null.");
    }
    if (file == null) {
      throw new NullPointerException("The given File object may not be null.");
    }
    if (!file.exists()) {
      throw new FileNotFoundException("The given file, " + file.toString() + ", cannot be found.");
    }
    if (!file.isFile()) {
      throw new FileNotFoundException("The given file, " + file.toString() + ", is not a file.");
    }
    
    DataInputStream fxp = new DataInputStream(new FileInputStream(file));
    
    try {
      byte[] fourBytes = new byte[0x4];
    
      fxp.read(fourBytes);
      if (!CHUNK_MAGIC.equals(new String(fourBytes))) {
        throw new DataFormatException("File does not contain required Chunk Magic, \"" + CHUNK_MAGIC + "\", flag.");
      }
    
      // fileLength is read an assigned to a variable for debugging purposes only
      @SuppressWarnings("unused")
      int fileLength = fxp.readInt();
    
      fxp.read(fourBytes);
      String chunkDataType = new String(fourBytes);
      boolean isRegularChunk = true;
      if (REGULAR_PRESET_MAGIC.equals(chunkDataType)) {
        isRegularChunk = true;
      } else if (OPAQUE_PRESET_MAGIC.equals(chunkDataType)) {
        if (!vst.acceptsProgramsAsChunks()) {
          throw new DataFormatException("File contains opaque data but plugin claims not to accept programs as chunks.");
        } else {
          isRegularChunk = false;
        }
      } else {
        throw new DataFormatException("File reports that is contains neither regular nor opqaue chunks.");
      }
    
      int fileVersion = fxp.readInt();
      if (fileVersion > 0x1) {
        throw new DataFormatException("File version " + Integer.toString(fileVersion) + " is not supported.");
      }
    
      // unique id
      fxp.read(fourBytes);
      String uniqueId = new String(fourBytes);
      if (!vst.getUniqueId().equals(uniqueId)) {
        throw new DataFormatException("Unique plugin ID in file does not match given plugin. " +
        		"Is this file really for " + vst.getEffectName() + "?");
      }
    
      int filePluginVersion = fxp.readInt();
      if (!ignorePluginVersion && (vst.getPluginVersion() < filePluginVersion)) {
        throw new DataFormatException("This file contains data for a later plugin version " + 
            Integer.toString(filePluginVersion) + ", and the given plugin is only version " + 
            Integer.toString(vst.getPluginVersion()) + ". Get a newer version of the plugin.");
      }
    
      int numParameters = fxp.readInt();
    
      byte[] programNameBytes = new byte[0x1c];
      fxp.read(programNameBytes);
      String programName = new String(programNameBytes);
      vst.setProgramName(programName);
    
      if (isRegularChunk) {
        for (int i = 0x0; i < numParameters; i++) {
          vst.setParameter(i, fxp.readFloat());
        }
      } else {
        byte[] chunkData = new byte[fxp.readInt()];
        fxp.read(chunkData);        
        vst.setProgramChunk(chunkData);
      }
    } finally {
      fxp.close();
    }
  }
  
  /**
   * Loads a preset bank from file to the current bank of the plugin.
   * @param vst  The plugin to load the data into.
   * @param file  The file to read which contains the preset data.
   * @author Uri Shaked <uri@urish.org>
   */
  public static void loadBank(JVstHost2 vst, File file) throws DataFormatException, IOException {
    if (vst == null) {
      throw new NullPointerException("The given JVstHost2 object may not be null.");
    }
    if (file == null) {
      throw new NullPointerException("The given File object may not be null.");
    }
    if (!file.exists()) {
      throw new FileNotFoundException("The given file, " + file.toString() + ", cannot be found.");
    }
    if (!file.isFile()) {
      throw new FileNotFoundException("The given file, " + file.toString() + ", is not a file.");
    }
    
    DataInputStream fxp = new DataInputStream(new FileInputStream(file));
    
    try {
      byte[] fourBytes = new byte[0x4];
    
      fxp.read(fourBytes);
      if (!CHUNK_MAGIC.equals(new String(fourBytes))) {
        throw new DataFormatException("File does not contain required Chunk Magic, \"" + CHUNK_MAGIC + "\", flag.");
      }
    
      // fileLength is read an assigned to a variable for debugging purposes only
      @SuppressWarnings("unused")
      int fileLength = fxp.readInt();
    
      fxp.read(fourBytes);
      String chunkDataType = new String(fourBytes);
      boolean isRegularChunk = true;
      if (REGULAR_BANK_MAGIC.equals(chunkDataType)) {
        isRegularChunk = true;
      } else if (OPAQUE_BANK_MAGIC.equals(chunkDataType)) {
        if (!vst.acceptsProgramsAsChunks()) {
          throw new DataFormatException("File contains opaque data but plugin claims not to accept programs as chunks.");
        } else {
          isRegularChunk = false;
        }
      } else {
        throw new DataFormatException("File reports that is contains neither regular nor opqaue chunks.");
      }
    
      int fileVersion = fxp.readInt();
      if (fileVersion > 0x2) {
        throw new DataFormatException("File version " + Integer.toString(fileVersion) + " is not supported.");
      }
    
      // unique id
      fxp.read(fourBytes);
      String uniqueId = new String(fourBytes);
      if (!vst.getUniqueId().equals(uniqueId)) {
        throw new DataFormatException("Unique plugin ID in file does not match given plugin. " +
        		"Is this file really for " + vst.getEffectName() + "?");
      }
    
      int filePluginVersion = fxp.readInt();
      if (!ignorePluginVersion && (vst.getPluginVersion() < filePluginVersion)) {
        throw new DataFormatException("This file contains data for a later plugin version " + 
            Integer.toString(filePluginVersion) + ", and the given plugin is only version " + 
            Integer.toString(vst.getPluginVersion()) + ". Get a newer version of the plugin.");
      }
    
      int numPrograms = fxp.readInt();
      
      int currentProgram = fxp.readInt();
      
      // reserved (zero)
      fxp.read(new byte[0x7c]);
    
      if (isRegularChunk) {
        for (int i = currentProgram; i < currentProgram + numPrograms; i++) {
          vst.setProgram(i);
          for (int j = 0x0; j < vst.numParameters(); j++) {
            vst.setParameter(i, fxp.readFloat());
          }
        }
      } else {
        byte[] chunkData = new byte[fxp.readInt()];
        fxp.read(chunkData);
        vst.setBankChunk(chunkData);
      }
    } finally {
      fxp.close();
    }
  }
  
  /**
   * Saves the current program of the given plugin to file.
   * @param vst  The plugin to load the data into.
   * @param file  The file to read which contains the program data.
   * @throws FileNotFoundException  Thrown if the file is not a file (perhaps it is a directory).
   * @throws IOException  Thrown if there is a problem with opening or writing to the file.
   * @throws NullPointerException  Thrown if the given plugin or file are <code>null</code>.
   */
  public static void savePreset(JVstHost2 vst, File file) throws IOException {
    if (vst == null) {
      throw new NullPointerException("The given JVstHost2 object may not be null.");
    }
    if (file == null) {
      throw new NullPointerException("The given File object may not be null.");
    }
    if (file.isDirectory()) {
      throw new FileNotFoundException("The given file, " + file.toString() + ", is a directory. It should be a file.");
    }
    
    DataOutputStream fxpOut = new DataOutputStream(new FileOutputStream(file));

    fxpOut.writeBytes(CHUNK_MAGIC);
    
    int chunkDataLength = 0x0;
    byte[] chunkData = null;
    if (vst.acceptsProgramsAsChunks()) {
      chunkData = vst.getProgramChunk();
      chunkDataLength = chunkData.length;
    } else {
      chunkDataLength = 0x4 * vst.numParameters();
    }
    
    // length of file - 8
    if (vst.acceptsProgramsAsChunks()) {
      fxpOut.writeInt(0x3c + chunkDataLength - 0x8);        
    } else {
      fxpOut.writeInt(0x3b + chunkDataLength - 0x8);
    }
    
    if (vst.acceptsProgramsAsChunks()) {
      fxpOut.writeBytes(OPAQUE_PRESET_MAGIC);
    } else {
      fxpOut.writeBytes(REGULAR_PRESET_MAGIC);
    }
    
    // format version
    fxpOut.writeInt(0x1);
    
    // plugin unique id
    fxpOut.writeBytes(vst.getUniqueId());
    
    // plugin version
    fxpOut.writeInt(vst.getPluginVersion());
    
    // numParams
    if (vst.acceptsProgramsAsChunks()) {
      fxpOut.writeInt(0x1);
    } else {
      fxpOut.writeInt(vst.numParameters());
    }
    
    String programName = vst.getProgramName();
    if (programName.length() > 0x1c) {
      fxpOut.writeBytes(programName.substring(0x0, 0x1c));        
    }  else {
      fxpOut.writeBytes(programName);
      fxpOut.write(new byte[0x1c - programName.length()]);
    }
    
    if (vst.acceptsProgramsAsChunks()) {
      // chunk data length
      fxpOut.writeInt(chunkDataLength);
    }
    
    if (vst.acceptsProgramsAsChunks()) {
      // the plugin in responsible for producing its own state data
      fxpOut.write(chunkData);
    } else {
      // otherwise the host may save the parameter state as it sees fit
      // In this case, the floats representing the parameters are printed out
      int numParameters = vst.numParameters();
      for (int i = 0x0; i < numParameters; i++) {
        fxpOut.writeFloat(vst.getParameter(i));
      }
    }
    
    fxpOut.close();
  }
  
  /**
   * Saves all programs in the bank of the given plugin to file.
   * @param vst  The plugin to load the data into.
   * @param file  The file to save to.
   * @throws FileNotFoundException  Thrown if the file is not a file (perhaps it is a directory).
   * @throws IOException  Thrown if there is a problem with opening or writing to the file.
   * @throws NullPointerException  Thrown if the given plugin or file are <code>null</code>.
   */
  public static void saveBank(JVstHost2 vst, File file) throws IOException {
    if (vst == null) {
      throw new NullPointerException("The given JVstHost2 object may not be null.");
    }
    if (file == null) {
      throw new NullPointerException("The given File object may not be null.");
    }
    if (file.isDirectory()) {
      throw new FileNotFoundException("The given file, " + file.toString() + ", is a directory. It should be a file.");
    }
    
    DataOutputStream fxpOut = new DataOutputStream(new FileOutputStream(file));

    fxpOut.writeBytes(CHUNK_MAGIC);
    
    int chunkDataLength = 0x0;
    byte[] chunkData = null;
    if (vst.acceptsProgramsAsChunks()) {
      chunkData = vst.getBankChunk();
      chunkDataLength = chunkData.length;
    } else {
      chunkDataLength = 0x4 * vst.numParameters() * vst.numPrograms();
    }
    
    // length of file - 8
    fxpOut.writeInt((vst.acceptsProgramsAsChunks() ? 0xa0 : 0x9c) +
        chunkDataLength - 0x8);
    
    // opaque or regular chunks
    fxpOut.writeBytes(vst.acceptsProgramsAsChunks() ? OPAQUE_BANK_MAGIC : REGULAR_BANK_MAGIC);
    
    // format version
    fxpOut.writeInt(0x2); // includes VST2.4 extensions
    
    // plugin unique id
    fxpOut.writeBytes(vst.getUniqueId());
    
    // plugin version
    fxpOut.writeInt(vst.getPluginVersion());
    
    // numPrograms
    fxpOut.writeInt(vst.numPrograms());
    
    // current program
    fxpOut.writeInt(0x0);
    
    // reserved (zero)
    fxpOut.write(new byte[0x7c]);
    
    if (vst.acceptsProgramsAsChunks()) {
      fxpOut.writeInt(chunkDataLength);
      fxpOut.write(chunkData);
    } else {
      for (int i = 0x0; i < vst.numPrograms(); i++) {
        vst.setProgram(i);
        for (int j = 0x0; j < vst.numParameters(); j++) {
          fxpOut.writeFloat(vst.getParameter(j));
        }
      }
    }
    
    fxpOut.close();
  }
  
  /**
   * The preset parameters are saved as human readable text. Useful for debugging.
   * @param vst  The plugin to load the data into.
   * @param file  The file to read which contains the program data.
   * @throws FileNotFoundException  Thrown if the file is not a file (perhaps it is a directory).
   * @throws IOException  Thrown if there is a problem with opening or writing to the file.
   * @throws NullPointerException  Thrown if the given plugin or file are <code>null</code>.
   */
  public static void savePresetAsText(JVstHost2 vst, File file) throws IOException {
    if (vst == null) {
      throw new NullPointerException("The given JVstHost2 object may not be null.");
    }
    if (file == null) {
      throw new NullPointerException("The given File object may not be null.");
    }
    if (file.isDirectory()) {
      throw new FileNotFoundException("The given file, " + file.toString() + ", is a directory. It should be a file.");
    }
    
    BufferedWriter fxp = new BufferedWriter(new FileWriter(file));
    StringBuilder sb = new StringBuilder();
    sb.append(vst.getProductString()); sb.append(" by "); sb.append(vst.getVendorName());
    sb.append("\n");
    sb.append("===");
    sb.append("\n");
    for (int i = 0x0; i < vst.numParameters(); i++) {
      sb.append(i);
      sb.append(" ");
      sb.append(vst.getParameterName(i)); 
      sb.append(": "); 
      sb.append(vst.getParameter(i)); 
      sb.append(" ("); 
      sb.append(vst.getParameterDisplay(i)); 
      sb.append(" "); 
      sb.append(vst.getParameterLabel(i)); 
      sb.append(")");
      sb.append("\n");
    }
    fxp.write(sb.toString());
    fxp.flush();
    fxp.close();
  }
  
  /**
   * Returns a <code>VstFileInfo</code> object which can be queried regarding the contents of the
   * preset file. This method is useful for determining which preset files belong to which plugins.
   * @param file  The file to read which contains the program data.
   * @return  A <code>VstFileInfo</code> object describing the given file.
   * @throws DataFormatException  Thrown if there is a problem loading the data in the file to the plugin.
   * @throws FileNotFoundException  Thrown if the file to load cannot be found, or is not a file (perhaps it is a directory).
   * @throws IOException  Thrown if there is a problem with opening or reading the file.
   * @throws NullPointerException  Thrown if the given file is <code>null</code>.
   */
  public static VstFileInfo getVstFileInfo(File file) throws DataFormatException, IOException {
    if (file == null) {
      throw new NullPointerException();
    }
    if (!file.exists()) {
      throw new FileNotFoundException();
    }
    if (!file.isFile()) {
      throw new FileNotFoundException();
    }
    
    DataInputStream fxp = new DataInputStream(new FileInputStream(file));
    
    byte[] fourBytes = new byte[0x4];
    
    fxp.read(fourBytes);
    if (!CHUNK_MAGIC.equals(new String(fourBytes))) {
      fxp.close();
      throw new DataFormatException("File does not contain required Chunk Magic, \"" + CHUNK_MAGIC + "\", flag.");
    }
    
    fxp.readInt(); // file length
    
    fxp.read(fourBytes);
    String chunkDataType = new String(fourBytes);
    boolean isRegularChunk = true;
    if (REGULAR_PRESET_MAGIC.equals(chunkDataType)) {
      isRegularChunk = true;
    } else if (OPAQUE_PRESET_MAGIC.equals(chunkDataType)) {
      isRegularChunk = false;
    } else {
      throw new DataFormatException("File reports that is contains neither regular nor opqaue chunks.");
    }
    
    int fileVersion = fxp.readInt();
    
    // unique id
    fxp.read(fourBytes);
    String uniqueId = new String(fourBytes);
    
    int filePluginVersion = fxp.readInt();
    
    fxp.close();
     
    return new VstFileInfo(uniqueId, filePluginVersion, fileVersion, !isRegularChunk);
  }
    private static final Logger LOG = Logger.getLogger(JVstPersistence.class.getName());
}
