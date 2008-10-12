package com.synthbot.minihost;

import com.synthbot.audioplugin.vst.JVstHost;
import com.synthbot.audioplugin.vst.JVstLoadException;
import java.io.File;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.InvalidMidiDataException;


public class GuiMiniHost {
  
  private static final float sampleRate = 44100f;
  private static final int blockSize = 4096;
  private JVstHost vst;
  private AudioThread audioThread;

  private int channel = 0;
  private int velocity = 127;
  private int midiNoteNumber = 60; // C4 (middle-C)

  public GuiMiniHost(File vstFile){
    vst = null;
    try {
      vst = new JVstHost(vstFile, sampleRate, blockSize);
    } catch (JVstLoadException jvle) {
      jvle.printStackTrace(System.err);
      System.exit(1);
    }
    
    // start the audio thread
    audioThread = new AudioThread(vst);
    Thread thread = new Thread(audioThread);
    thread.start();
    
    // create a midi note on message
    ShortMessage smNoteOn = new ShortMessage();
    ShortMessage smNoteOff = new ShortMessage();

    int note;
    
    // play a random note every 1000 ms 
    try {
      while (true) {
	note = (int)(Math.random() * 32) + 64;
	
	Thread.sleep(500);
	smNoteOn.setMessage(ShortMessage.NOTE_ON, channel, note, velocity);
	audioThread.addMidiMessages(smNoteOn);

  	Thread.sleep(500);
  	smNoteOff.setMessage(ShortMessage.NOTE_OFF, channel, note, 0);
  	audioThread.addMidiMessages(smNoteOff);
		
      }
    } catch (InvalidMidiDataException imde) {
      imde.printStackTrace(System.err);
      System.exit(1);
    } catch (InterruptedException e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
  public static void main(String[] args){
    if (args == null || args.length < 1) {
      System.err.println("Usage: java -jar JVstHost.jar <path to vst plugin>");
      System.exit(0);
    }
    
    GuiMiniHost host = new GuiMiniHost(new File(args[0]));
  }
  
}
