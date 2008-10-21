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
  
  private final int type = 1; // kVstMidiType (aeffectx.h)
  private int deltaFrames;
  private int flags;
  private int noteLength;
  private int noteOffset;
  private final ShortMessage message;
  private char detune;
  private char noteOffVelocity;
  
  /**
   * A basic default constructor with the default settings
   */
  public VstMidiEvent(ShortMessage message) {
    deltaFrames = 0;
    flags = VstMidiEventFlags.VST_MIDI_EVENT_IS_REALTIME.getMask();
    noteLength = 0;
    noteOffset = 0;
    this.message = message;
    detune = 0;
    noteOffVelocity = 0;
  }

  /**
   * sample frames related to the current block start sample position
   */
  public int getDeltaFrames() {
    return deltaFrames;
  }

  /**
   * sample frames related to the current block start sample position
   */
  public void setDeltaFrames(int deltaFrames) {
    this.deltaFrames = deltaFrames;
  }

  public int getFlags() {
    return flags;
  }

  public void setFlags(int flags) {
    this.flags = flags;
  }

  /**
   * (in sample frames) of entire note, if available, else 0
   */
  public int getNoteLength() {
    return noteLength;
  }

  /**
   * (in sample frames) of entire note, if available, else 0
   */
  public void setNoteLength(int noteLength) {
    this.noteLength = noteLength;
  }

  /**
   * offset (in sample frames) into note from note start if available, else 0
   */
  public int getNoteOffset() {
    return noteOffset;
  }

  /**
   * offset (in sample frames) into note from note start if available, else 0
   */
  public void setNoteOffset(int noteOffset) {
    this.noteOffset = noteOffset;
  }

  /**
   * -64 to +63 cents; for scales other than 'well-tempered' ('microtuning')
   */
  public char getDetune() {
    return detune;
  }

  /**
   * -64 to +63 cents; for scales other than 'well-tempered' ('microtuning')
   */
  public void setDetune(char detune) {
    this.detune = detune;
  }

  public char getNoteOffVelocity() {
    return noteOffVelocity;
  }

  public void setNoteOffVelocity(char noteOffVelocity) {
    this.noteOffVelocity = noteOffVelocity;
  }

  public int getType() {
    return type;
  }

  public ShortMessage getMessage() {
    return message;
  }
  
  
}
