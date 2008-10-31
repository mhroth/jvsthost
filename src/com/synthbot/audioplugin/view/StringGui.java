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

package com.synthbot.audioplugin.view;

import com.synthbot.audioplugin.vst.view.JVstView;
import com.synthbot.audioplugin.vst.view.JVstViewListener;

import java.awt.Dimension;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

public class StringGui extends JFrame implements JVstView {

  private final static long serialVersionUID = 5L;
  
  private final JVstViewListener vst;

  private final int numParameters;
  private final JSlider[] sliders;
  private final JLabel[] displayLabels;
  private final ChangeListener[] changeListeners;

  public StringGui(JVstViewListener jVstViewListener) {
    super(jVstViewListener.getEffectName() + " by " + jVstViewListener.getVendorName());
    vst = jVstViewListener;
    this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    
    // configure the gui based on this host
    int colWidth = 240;
    int colHeight = 25;
    
    numParameters = vst.numParameters();
    sliders = new JSlider[numParameters];
    displayLabels = new JLabel[numParameters];
    changeListeners = new ChangeListener[numParameters];
    
    // this is the top level container with the 3 cols plus the keyboard
    Container globalContainer = new Container();
    globalContainer.setLayout(new BoxLayout(globalContainer, BoxLayout.Y_AXIS));
    // sub container with 3 columns
    Container columnContainer = new Container();
    columnContainer.setLayout(new BoxLayout(columnContainer, BoxLayout.X_AXIS));
    // now add 3 panels to the container, one for each column
    // name right aligned, checkbox and slider, value left aligned
    // [ 1-vibrato (%) ] [ X ----|---- ] [ 57 ]
    JPanel nameCol = new JPanel();
    nameCol.setLayout(new BoxLayout(nameCol, BoxLayout.Y_AXIS));
    JPanel widgetCol = new JPanel();
    widgetCol.setLayout(new BoxLayout(widgetCol, BoxLayout.Y_AXIS));
    JPanel valueCol = new JPanel();
    valueCol.setLayout(new BoxLayout(valueCol, BoxLayout.Y_AXIS));
    
    columnContainer.add(nameCol);
    columnContainer.add(widgetCol);
    columnContainer.add(valueCol);
    
    JScrollPane scrollPane = new JScrollPane(columnContainer);
    JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
    verticalScrollBar.setUnitIncrement(colHeight);
        
    for (int i = 0; i < numParameters; i++) {
      // parameter names e.g. 'LFO speed'
      // parameter 'units', e.g. 'seconds', 'Hz'
      JLabel nameLabel = new JLabel(i + ": " + vst.getParameterName(i) + "  (" + vst.getParameterLabel(i) + ")  ", JLabel.RIGHT);
      
      final JSlider slider = new JSlider(0, 127, (int) (vst.getParameter(i) * 127f));
      slider.setFocusable(false);
      sliders[i] = slider;
      
      // this will contain the controls
      Container widgets = new Container();
      widgets.setLayout(new BoxLayout(widgets, BoxLayout.X_AXIS));
      widgets.add(slider);
      
      final JLabel displayLabel = new JLabel(vst.getParameterDisplay(i));
      displayLabels[i] = displayLabel;
      
      final int index = i;
      changeListeners[i] = new ChangeListener() {
        public void stateChanged(ChangeEvent event) {
          vst.setParameter(index, ((float) slider.getValue()) / 127f);
          displayLabel.setText(vst.getParameterDisplay(index));
        }
      };
      slider.addChangeListener(changeListeners[i]);
      
      // tell it not to fuck about with the sizes
      widgets.setMinimumSize(new Dimension(colWidth, colHeight));
      widgets.setPreferredSize(new Dimension(colWidth, colHeight));
      widgets.setMaximumSize(new Dimension(colWidth, colHeight));
      nameLabel.setMinimumSize(new Dimension(colWidth, colHeight));
      nameLabel.setPreferredSize(new Dimension(colWidth, colHeight));
      nameLabel.setMaximumSize(new Dimension(colWidth, colHeight));
      displayLabel.setMinimumSize(new Dimension(colWidth, colHeight));
      displayLabel.setPreferredSize(new Dimension(colWidth, colHeight));
      displayLabel.setMaximumSize(new Dimension(colWidth, colHeight));
      
      nameCol.add(nameLabel);
      widgetCol.add(widgets);
      valueCol.add(displayLabel);
    }
    
    // black magic - add glue to the bottom of the columns which will
    // invisibly use up all of the space below, forcing the columns
    // to stay at the top - oh yeah!
    nameCol.add(Box.createVerticalGlue());
    widgetCol.add(Box.createVerticalGlue());
    valueCol.add(Box.createVerticalGlue());
    
    // create the keyboard
    Container keyboard = new Container();
    keyboard.setLayout(new BoxLayout(keyboard, BoxLayout.X_AXIS));
    keyboard.add(Box.createRigidArea(new Dimension(colWidth, 1)));
    int keyWidth = 20;
    // load some icons up
    ImageIcon blackKeyIcon = new ImageIcon("res/black_key.png");
    ImageIcon whiteKeyIcon = new ImageIcon("res/white_key.png");
    for (int i = 0; i < 12; i++) {
      JButton key;
      final int note = i;
      if (i == 0 || i == 2 || i == 4 || i == 5 || i == 7 || i == 9 || i == 11) {
        key = new JButton(whiteKeyIcon);
        key.setMinimumSize(new Dimension(keyWidth, colHeight * 2));
        key.setPreferredSize(new Dimension(keyWidth, colHeight * 2));
        key.setMaximumSize(new Dimension(keyWidth, colHeight * 2));
        
      } else {
        key = new JButton(blackKeyIcon);
        key.setMinimumSize(new Dimension(keyWidth - 2, colHeight * 2));
        key.setPreferredSize(new Dimension(keyWidth - 2, colHeight * 2));
        key.setMaximumSize(new Dimension(keyWidth - 2, colHeight * 2));
      }
      
      key.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          try {
            ShortMessage smNoteOn = new ShortMessage();
            smNoteOn.setMessage(ShortMessage.NOTE_ON, 0, 48 + note, 96);
            vst.setMidiEvents(new ShortMessage[] {smNoteOn});
          } catch (InvalidMidiDataException imde) {
            imde.printStackTrace(System.err);
          }

          // spin off a thread to stop the note at a later time
          new Thread(new Runnable() {
            public void run() {
              try {
                Thread.sleep(2000);
                ShortMessage smNoteOff = new ShortMessage();
                smNoteOff.setMessage(ShortMessage.NOTE_OFF, 0, 48 + note, 96);
                vst.setMidiEvents(new ShortMessage[] {smNoteOff});
              } catch (InvalidMidiDataException imde) {
                imde.printStackTrace(System.err);
              } catch (InterruptedException ie) {
                ie.printStackTrace(System.err);
              }
            }
          }).start();
        }
      });
      keyboard.add(key);
    }
    
    keyboard.add(Box.createHorizontalGlue());
    
    // add program changer
    String[] progNames = new String[127];
    for (int i = 0; i < 127; i++) {
      vst.setProgram(i);
      progNames[i] = vst.getProgramName() + ":" + i;
    }
    vst.setProgram(0);
    
    JComboBox progList = new JComboBox(progNames);
    progList.setSelectedIndex(0);
    progList.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox) e.getSource();
        int index = cb.getSelectedIndex();
        String progName = (String) cb.getSelectedItem();
        float[] program;
        vst.setProgram(index);
        // now update the sliders...
        for (int i = 0; i < numParameters; i++) {
          sliders[i].setValue((int) (vst.getParameter(i) * 127f));
        }
      }
    });
    
    globalContainer.add(scrollPane);
    globalContainer.add(keyboard);
    globalContainer.add(progList);
    this.add(globalContainer);
    
    this.setFocusable(true);
    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    this.pack();
  }
  
  private void setSliderValueWithoutFiringChangeListener(int index, float value) {
    sliders[index].removeChangeListener(changeListeners[index]);
    sliders[index].setValue((int) (value * 127f));
    sliders[index].addChangeListener(changeListeners[index]);
  }
  
  public void setVisible(boolean visible) {
    // if changing visible state to visible
    if (!this.isVisible() && visible) {
      // Rereads and resets all variable parameters and displays them in the GUI.
      for (int i = 0; i < numParameters; i++) {
        setSliderValueWithoutFiringChangeListener(i, vst.getParameter(i));
        displayLabels[i].setText(vst.getParameterDisplay(i));
      }
    }
    super.setVisible(visible);
  }
  
  public void updateParameter(final int index, final float value, final String display) {
    // update the GUI only if the calling thread is not the GUI thread.
    // If it is, then the GUI has already been updated!
    if (!EventQueue.isDispatchThread()) {
      // allow the GUI thread to update the GUI when it is convenient
      EventQueue.invokeLater(new Runnable() {
        public void run() {
          setSliderValueWithoutFiringChangeListener(index, value);
          displayLabels[index].setText(vst.getParameterDisplay(index));
        }
      });
    }
  }
  
  public void updateProgram(final int index) {
    if (!EventQueue.isDispatchThread()) {
      EventQueue.invokeLater(new Runnable() {
        public void run() {
          // TODO(mhroth): imeplement
        }
      });
    }
  }
}
