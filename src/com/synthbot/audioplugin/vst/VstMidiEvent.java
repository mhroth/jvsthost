package com.synthbot.audioplugin.vst;

import javax.sound.midi.ShortMessage;

public class VstMidiEvent {

  public enum VstMidiEventFlags {
    VST_MIDI_EVENT_IS_REALTIME(1);
    
    private final int mask;
    
    private VstMidiEventFlags(int mask) {
      this.mask = mask;
    }
    
    public int getMask() {
      return mask;
    }
  }
  
  public final int type = 1; // kVstMidiType (aeffectx.h)
  public final int deltaFrames;
  public final int flags;
  public final int noteLength;
  public final int noteOffset;
  public final ShortMessage message;
  public final char detune;
  public final char noteOffVelocity;
  public final char reserved1;
  public final char reserved2;
  
  /**
   * A basic default constructor.
   */
  public VstMidiEvent(ShortMessage message) {
    deltaFrames = 0;
    flags = VstMidiEventFlags.VST_MIDI_EVENT_IS_REALTIME.getMask();
    noteLength = 0;
    noteOffset = 0;
    this.message = message;
    detune = 0;
    noteOffVelocity = 0;
    reserved1 = 0;
    reserved2 = 0;
  }
  
  /**
   * The full constructor.
   */
  public VstMidiEvent(
      int deltaFrames, 
      int flags, 
      int noteLength, 
      int noteOffset, 
      ShortMessage message, 
      char detune, 
      char noteOffVelocity, 
      char reserved1, 
      char reserved2) {
    this.deltaFrames = deltaFrames;
    this.flags = flags;
    this.noteLength = noteLength;
    this.noteOffset = noteOffset;
    this.message = message;
    this.detune = detune;
    this.noteOffVelocity = noteOffVelocity;
    this.reserved1 = reserved1;
    this.reserved2 = reserved2;
  }
}
