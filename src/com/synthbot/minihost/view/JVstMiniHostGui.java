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

package com.synthbot.minihost.view;

import javax.swing.JFrame;

/**
 * A basic control GUI for the MiniHost.
 */
public class JVstMiniHostGui extends JFrame {
  
  private static final String FRAME_TITLE = "JVstHost Mini Host";
  
  public JVstMiniHostGui() {
    super(FRAME_TITLE);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(320, 240);
    setVisible(true);
  }

}
