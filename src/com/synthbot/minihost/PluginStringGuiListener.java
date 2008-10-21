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
/**
 *  <code>PluginStringGuiListener</code> - the host implements this
 *  and it allows the generic plugin gui to talk to the host in a
 *  controlled way.
 *
 * @author Matthew Yee-King, Martin Roth
 * @version 1.0
 */
public interface PluginStringGuiListener{
  public String setParameter(int index, float value);
  public float getParameter(int index);
  public int getNumParameters();
  public String getParameterDisplay(int index);
  public String getParameterName(int index);
  public String getParameterLabel(int index);
  public void setProgram(int index);
  public String getProgramName();    
  public void playNote(int noteNumber, int velocity);
}