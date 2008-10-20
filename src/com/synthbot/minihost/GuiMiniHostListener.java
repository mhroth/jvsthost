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
 *  <code>GuiMiniHostListener</code> - the generic plugin gui,
 *  PluginStringGUI implements thie interface and this allows it to
 *  talk to the host in a controlled way.
 *
 * @author <a href="mailto:matthew@tosharus2">Matthew Yee-King</a>
 * @version 1.0
 */
public interface GuiMiniHostListener {
  public void generateGui();

}