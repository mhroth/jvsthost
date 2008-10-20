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


import java.awt.Dimension;
import java.awt.Container;
import javax.swing.BoxLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JPanel;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;

public class PluginStringGui extends JFrame implements GuiMiniHostListener  {

  private final PluginStringGuiListener host;

  private int numParameters;
  private JCheckBox[] noEvolveCheckboxes;
  private JSlider[] sliders;
  private JButton setEvolutionMaskButton;

  //  private static final String EVOLUTION_MASK = "Set Evoution Mask";
  private static final String SYNTHBOT_STRING_GUI = "SynthBot String GUI";
  //private static final String NO_EVOLVE = "Don't Evolve";
  private static final String PARAMETER_VALUE = "Parameter Value";
  private static final String PARAMETER_DISPLAY = "Parameter Display";
  private static final String PARAMETER_NAME = "Parameter Name";

  public PluginStringGui(PluginStringGuiListener pluginHost){
    super(SYNTHBOT_STRING_GUI);
    this.host = pluginHost;
  }

  public void generateGui(){
    // configure the gui based on this host
    int colWidth = 240;
    int colHeight = 25;

    numParameters = host.getNumParameters();
    // 0-1 floats of the paramter settings
    float[] values = new float[numParameters];
    // parameter values to display (e.g. '40' or 'low'
    String[] displays = new String[numParameters];
    // parameter names e.g. 'LFO speed'
    String[] names = new String[numParameters];
    // parameter 'units', e.g. 'seconds', 'Hz'
    String[] labels = new String[numParameters];

    for (int i = 0; i < numParameters; i++) {
      values[i] = host.getParameter(i);
      displays[i] = host.getParameterDisplay(i);
      names[i] = host.getParameterName(i);
      labels[i] = host.getParameterLabel(i);
      System.out.println("GuiMiniHost: label is "+labels[i]);
    }
    


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

    noEvolveCheckboxes = new JCheckBox[numParameters];
    sliders = new JSlider[numParameters];

    for (int i = 0; i < numParameters; i++) {
      //System.out.println("PluginStringGUI... adding para  "+names[i]);
      JLabel nameLabel = new JLabel(i + ": " + names[i] + "  (" + labels[i] + ")  ", JLabel.RIGHT);

      final JSlider slider = new JSlider(0, 127, (int) (values[i] * 127f)); // set
                                                                            // initial
                                                                            // value?
      slider.setValue((int) (values[i] * 127f));
      slider.setFocusable(false);
      sliders[i] = slider;

      JCheckBox checkBox = new JCheckBox();
      checkBox.setFocusable(false);
      noEvolveCheckboxes[i] = checkBox;
      // this will contain the controls
      Container widgets = new Container();
      widgets.setLayout(new BoxLayout(widgets, BoxLayout.X_AXIS));
      widgets.add(slider);
      widgets.add(checkBox);

      final JLabel valueLabel = new JLabel(displays[i]);

      final int index = i;
      slider.addChangeListener(new ChangeListener() {
	  public void stateChanged(ChangeEvent event) {
	    String displayString = host.setParameter(index,
						     ((float) slider.getValue()) / 127f);
	    valueLabel.setText(displayString);
	  }
	});
      // tell it not to fuck about with the sizes
      widgets.setMinimumSize(new Dimension(colWidth, colHeight));
      widgets.setPreferredSize(new Dimension(colWidth, colHeight));
      widgets.setMaximumSize(new Dimension(colWidth, colHeight));
      nameLabel.setMinimumSize(new Dimension(colWidth, colHeight));
      nameLabel.setPreferredSize(new Dimension(colWidth, colHeight));
      nameLabel.setMaximumSize(new Dimension(colWidth, colHeight));
      valueLabel.setMinimumSize(new Dimension(colWidth, colHeight));
      valueLabel.setPreferredSize(new Dimension(colWidth, colHeight));
      valueLabel.setMaximumSize(new Dimension(colWidth, colHeight));

      nameCol.add(nameLabel);
      widgetCol.add(widgets);
      valueCol.add(valueLabel);
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
	    //host.playNote(48 + note, 96, synthbot.onGetCurrentParameters());
	    host.playNote(48 + note, 96);
	  }
	});
      keyboard.add(key);
    }

    keyboard.add(Box.createHorizontalGlue());

    String[] progNames = new String[127];
    // float[][] progParams = new float[127][numParameters];
    // now create the program selecting drop down list
    for (int i = 0; i < 127; i++) {
      // need to load this program in order to query its name and paramter
      // settings
      host.setProgram(i);
      progNames[i] = host.getProgramName() + ":" + i;
    }
    host.setProgram(0);

    JComboBox progList = new JComboBox(progNames);
    progList.setSelectedIndex(0);
    progList.addActionListener(new ActionListener() {
	public void actionPerformed(ActionEvent e) {
	  JComboBox cb = (JComboBox) e.getSource();
	  int index = cb.getSelectedIndex();
	  String progName = (String) cb.getSelectedItem();
	  float[] program;
	  // System.out.println("PluginStringGUI: you selected program
	  // "+progName+" index "+index);
	  host.setProgram(index);
	  // now update the sliders...
	  //program = host.getCurrentParameters();

	  for (int i = 0; i < numParameters; i++) {
	    // System.out.println("PluginStringGUI: updating param "+i+" to
	    // "+program[i]);
	    sliders[i].setValue((int) (host.getParameter(i) * 127f));
	  }
	}
      });

    globalContainer.add(scrollPane);
    globalContainer.add(keyboard);
    globalContainer.add(progList);
    this.add(globalContainer);


    // container.add(playSound);

    this.addKeyListener(new KeyListener() {
	public void keyPressed(KeyEvent event) {
	}

	public void keyReleased(KeyEvent event) {
	}

	public void keyTyped(KeyEvent event) {
	  switch (event.getKeyChar()) {
          case ' ':
            // TODO play a sound here
            break;
          case 'w':
            // TODO close the frame
            break;
          default:
            break;
	  }
	}
      });

    this.setFocusable(true);
    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    this.pack();
    this.setVisible(true);
  }

}
