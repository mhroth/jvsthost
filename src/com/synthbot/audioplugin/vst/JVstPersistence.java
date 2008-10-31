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

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.SecurityException;

/**
 * A class to load and save VST preset files (*.fxp, *.fxb)
 */
public class JVstPersistence {
  
  private static final String chunkMagic = "CcnK";
  private static final String regularPresetMagic = "FxCk";
  private static final String opaquePresetMagic = "FPCh";
  private static final String regularBankMagic = "FxBk";
  private static final String opaqueBankMagic = "FBCh";
  
  /**
   * Saves a preset of the given JVstHost to the given File.
   * @return True if the load was successful.
   */
  public static boolean loadPreset(JVstHost vst, File file) {
    try {
      DataInputStream fxp = new DataInputStream(new FileInputStream(file));
      
      byte[] fourBytes = new byte[4];
      
      fxp.read(fourBytes);
      if (!chunkMagic.equals(new String(fourBytes))) {
        fxp.close();
        return false;
      }
      
      int fileLength = fxp.readInt();
      
      fxp.read(fourBytes);
      String chunkDataType = new String(fourBytes);
      boolean isRegularChunk = true;
      if (regularPresetMagic.equals(chunkDataType)) {
        isRegularChunk = true;
      } else if (opaquePresetMagic.equals(chunkDataType)) {
        if (!vst.acceptsProgramsAsChunks()) {
          fxp.close();
          return false;
        } else {
          isRegularChunk = false;
        }
      }
      
      int fileVersion = fxp.readInt();
      if (fileVersion > 1) {
        // ???
        fxp.close();
        return false;
      }
      
      // unique id
      fxp.read(fourBytes);
      String uniqueId = new String(fourBytes);
      if (!vst.getUniqueId().equals(uniqueId)) {
        fxp.close();
        return false;
      }
      
      if (vst.getVersion() < fxp.readInt()) {
        fxp.close();
        return false;
      }
      
      int numParameters = fxp.readInt();
      
      byte[] programNameBytes = new byte[28];
      fxp.read(programNameBytes);
      String programName = new String(programNameBytes);
      vst.setProgramName(programName);
      
      if (isRegularChunk) {
        for (int i = 0; i < numParameters; i++) {
          vst.setParameter(i, fxp.readFloat());
        }
      } else {
        byte[] chunkData = new byte[fxp.readInt()];
        fxp.read(chunkData);        
        vst.setProgramChunk(chunkData);
      }
      
      fxp.close();
      return true;
    } catch (FileNotFoundException fnfe) {
      fnfe.printStackTrace(System.err);
      return false;
    } catch (SecurityException se) {
      se.printStackTrace(System.err);
      return false;
    } catch (IOException ioe) {
      ioe.printStackTrace(System.err);
      return false;
    }
  }
  
  /**
   * This method is not yet implemented.
   */
  @Deprecated
  public static boolean loadBank(JVstHost vst, File file) {
    return false;
  }
  
  public static boolean savePreset(JVstHost vst, File file) {
    try {
      DataOutputStream fxpOut = new DataOutputStream(new FileOutputStream(file));

      fxpOut.writeBytes(chunkMagic);
      
      int chunkDataLength = 0;
      byte[] chunkData = null;
      if (vst.acceptsProgramsAsChunks()) {
        chunkData = vst.getProgramChunk();
        chunkDataLength = chunkData.length;
      } else {
        chunkDataLength = 4 * vst.numParameters();
      }
      
      // length of file - 8
      if (vst.acceptsProgramsAsChunks()) {
        fxpOut.writeInt(60 + chunkDataLength - 8);        
      } else {
        fxpOut.writeInt(59 + chunkDataLength - 8);
      }
      
      if (vst.acceptsProgramsAsChunks()) {
        fxpOut.writeBytes(opaquePresetMagic);
      } else {
        fxpOut.writeBytes(regularPresetMagic);
      }
      
      // format version
      fxpOut.writeInt(1);
      
      // plugin unique id
      fxpOut.writeBytes(vst.getUniqueId());
      
      // plugin version
      fxpOut.writeInt(vst.getVersion());
      
      // numParams
      if (vst.acceptsProgramsAsChunks()) {
        fxpOut.writeInt(1);
      } else {
        fxpOut.writeInt(vst.numParameters());
      }
      
      String programName = vst.getProgramName();
      if (programName.length() > 28) {
        fxpOut.writeBytes(programName.substring(0, 28));        
      }  else {
        fxpOut.writeBytes(programName);
        fxpOut.write(new byte[28 - programName.length()]);
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
        for (int i = 0; i < numParameters; i++) {
          fxpOut.writeFloat(vst.getParameter(i));
        }
      }
      
      fxpOut.close();
      return true;
    } catch (FileNotFoundException fnfe) {
      fnfe.printStackTrace(System.err);
      return false;
    } catch (SecurityException se) {
      se.printStackTrace(System.err);
      return false;
    } catch (IOException ioe) {
      ioe.printStackTrace(System.err);
      return false;
    }
  }
  
  /**
   * This method is not yet implemented.
   */
  @Deprecated
  public static boolean saveBank(JVstHost vst, File file) {
    return false;
  }
  
  /**
   * The preset parameters are saved as human readable text. Useful for debugging.
   * @return True if the save was successful.
   */
  public static boolean savePresetAsText(JVstHost vst, File file) {
    try {
      BufferedWriter fxp = new BufferedWriter(new FileWriter(file));
      StringBuilder sb = new StringBuilder();
      sb.append(vst.getProductString()); sb.append(" by "); sb.append(vst.getVendorName());
      sb.append("\n");
      sb.append("===");
      sb.append("\n");
      for (int i = 0; i < vst.numParameters(); i++) {
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
      return true;
    } catch (IOException ioe) {
      ioe.printStackTrace(System.err);
      return false;
    }
  }
}
