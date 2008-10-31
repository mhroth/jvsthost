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

package com.synthbot.audioplugin.vst.view;

/**
 * Any view controlling or reflecting the state of a JVstHost must implement this interface.
 * A view implementing this interface will be notified of any state changes to a
 * vst. Such state changes may be caused by the native editor or by automation.
 */
public interface JVstView {

  /**
   * The view is updated with a new value for a given parameter. The associated
   * display string is also supplied.
   */
  public void updateParameter(int index, float value, String display);
  
  /**
   * The view is updated with the new program number.
   */
  public void updateProgram(int index);
  
  /**
   * The GUI will not be updated by JVstHost if it is not visible. Thus, when
   * becoming visible, it may be necessary to get all of the current parameter
   * values and display labels from the JVstHost.
   */
  public void setVisible(boolean visible);

  /**
   * As the view is generally only updated when it is visible, this information
   * must be made available to the JVstViewListener.
   */
  public boolean isVisible();
}
