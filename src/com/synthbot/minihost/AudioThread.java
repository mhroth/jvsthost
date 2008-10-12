package com.synthbot.minihost;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.AudioFormat;
import javax.sound.midi.ShortMessage;

import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.util.Vector;

import com.synthbot.audioplugin.vst.JVstHost;

public class AudioThread implements Runnable {

  private JVstHost vst;
  private volatile boolean keepRunning;
  private float[][] fOutputs;
  private byte[] bOutput;
  private int blockSize;
  private int numOutputs;

  private AudioInputStream audioInputStream;
  private SourceDataLine sourceDataLine;

  private Vector<ShortMessage> pendingMidi;
  
  private static final float ShortMaxValueAsFloat = (float) (Short.MAX_VALUE);

  public AudioThread(JVstHost vst) {
    super();
    addJVstHost(vst);
    numOutputs = vst.numOutputs();
    blockSize = vst.getBlockSize();
    
    pendingMidi = new Vector<ShortMessage>();

    sourceDataLine = null;
    
    InputStream byteArrayInputStream = new ByteArrayInputStream(bOutput);
    AudioFormat audioFormat = new AudioFormat((int)vst.getSampleRate(), 16, vst.numOutputs(), true, false);
    
    audioInputStream = new AudioInputStream(byteArrayInputStream, audioFormat, 
					    bOutput.length / audioFormat.getFrameSize());
    DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
    
    System.out.println("AudioThread::Sound card data line info:"+dataLineInfo.toString());
    
    try {
      sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
      sourceDataLine.open(audioFormat, bOutput.length);
      sourceDataLine.start();
    } catch (LineUnavailableException lue) {
      lue.printStackTrace(System.err);
      System.exit(1);
    }
  }

  
  private byte[] floatsToBytes(float[][] fData, byte[] bData){
    // convert floats to bytes
    int index = 0;
    for (int i = 0; i < blockSize; i++) {
      for (int j = 0; j < numOutputs; j++) {
        short sval = (short) (fData[j][i] * ShortMaxValueAsFloat);
        bData[index++] = (byte) (sval & 0x00FF);
	bData[index++] = (byte) ((sval & 0xFF00) >> 8);
      }
    }
    return bData;
  }

  public void stopAudio() {
    keepRunning = false;
  }

  public void addJVstHost(JVstHost vst) {
    this.vst = vst;
    fOutputs = new float[vst.numOutputs()][vst.getBlockSize()];
    bOutput = new byte[vst.numOutputs() * vst.getBlockSize() * 2];
  }
  
  // midi events are collected and passed into the jvsthost every time processReplacing gets called 
  public synchronized void addMidiMessages(ShortMessage message){
    pendingMidi.add(message);
  }

  public synchronized ShortMessage[] getMidiMessages(){
    ShortMessage[] messages = pendingMidi.toArray(new ShortMessage[0]);
    pendingMidi.clear();
    return messages;
  }

  public void run() {
    keepRunning = true;
    while (keepRunning) {
      vst.setMidiEvents(getMidiMessages()); // pass in the midi event
      vst.processReplacing(fOutputs);
      sourceDataLine.write(floatsToBytes(fOutputs, bOutput), 0, bOutput.length);
    }
    sourceDataLine.drain();
    sourceDataLine.close();
  }

}