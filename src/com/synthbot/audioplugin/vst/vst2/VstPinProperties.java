package com.synthbot.audioplugin.vst.vst2;

/**
 * A data class holding information regarding an input or output. The elements of this class are
 * not mutable. Check to see if the data is valid with <code>isValid</code>. If not, then any access
 * of other methods will throw an <code>IllegalStateException</code>.
 */
public class VstPinProperties {

  private final boolean IS_VALID;
  private final int INDEX;
  private final String LABEL;
  private final String SHORT_LABEL;
  private final boolean IS_ACTIVE;
  private final boolean IS_FIRST_IN_STEREO_PAIR;

  public VstPinProperties() {
    IS_VALID = false;
    INDEX = 0;
    LABEL = "";
    SHORT_LABEL = "";
    IS_ACTIVE = false;
    IS_FIRST_IN_STEREO_PAIR = false;
  }
  
  public VstPinProperties(int index, String label, String shortLabel, int flags) {
    IS_VALID = true;
    INDEX = index;
    LABEL = label;
    SHORT_LABEL = shortLabel;
    IS_ACTIVE = (flags & 0x1) != 0;
    IS_FIRST_IN_STEREO_PAIR = (flags & 0x2) != 0;
  }
  
  /**
   * Returns the input or output index to which this data belongs.
   */
  public int getIoIndex() {
    if (IS_VALID) {
      return INDEX;      
    } else {
      throw new IllegalStateException("The data represented by this object is not valid. " +
      		"The plugin does not support VstPinProperties.");
    }
  }
  
  /**
   * Returns a textual description of this pin.
   * @return
   */
  public String getLabel() {
    if (IS_VALID) {
      return LABEL;      
    } else {
      throw new IllegalStateException("The data represented by this object is not valid. " +
          "The plugin does not support VstPinProperties.");
    }
  }
  
  public String getShortLabel() {
    if (IS_VALID) {
      return SHORT_LABEL;      
    } else {
      throw new IllegalStateException("The data represented by this object is not valid. " +
          "The plugin does not support VstPinProperties.");
    }
  }
  
  public boolean isActive() {
    if (IS_VALID) {
      return IS_ACTIVE;      
    } else {
      throw new IllegalStateException("The data represented by this object is not valid. " +
          "The plugin does not support VstPinProperties.");
    }
  }
  
  /**
   * Indicates if this pin is first in a stereo pair. If <code>true</code>, then the next pin is
   * the alternate channel.
   */
  public boolean isFirstInStereoPair() {
    if (IS_VALID) {
      return IS_FIRST_IN_STEREO_PAIR;      
    } else {
      throw new IllegalStateException("The data represented by this object is not valid. " +
          "The plugin does not support VstPinProperties.");
    }
  }
  
  /**
   * Indicates if the data represented by this object is valid. If <code>false</code>, then any
   * other method will throw an <code>IllegalStateException</code> if called.
   */
  public boolean isValid() {
    return IS_VALID;
  }
}
