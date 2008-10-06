package com.synthbot.test;

//import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.*;
import java.io.*;

public class AudioPlayer {

  private Thread playThread;
  private byte[] audioData;

  private AudioFormat audioFormat;
  private TargetDataLine targetDataLine;
  private AudioInputStream audioInputStream;
  private SourceDataLine sourceDataLine;

  // thanks to ...Richard Baldwin
  // http://www.developer.com/java/other/article.php/1565671
  // for de-obfuscating the javax.sound API long enough
  // for me to hack this togehter...

  public void playAudio(float[] audio) {
    try {
      if (playThread != null) {
        // shut down the source
        sourceDataLine.close();
        // wait for it to notice...
        playThread.join();
        // playThread.interrupt();
        // sourceDataLine.drain();

      }
      // Get everything set up for
      // playback.
      // Get the previously-saved data
      // into a byte array object.
      // byte audioData[] = byteArrayOutputStream.toByteArray();
      int frames = audio.length;
      // 2 bytes per frame
      audioData = new byte[frames * 2];

      // convert floats to bytes
      for (int i = 0; i < frames; i++) {
        short sval = new Float(audio[i] * (float) Short.MAX_VALUE).shortValue();
        audioData[i * 2] = (byte) (sval & 0x000000ff);
        audioData[i * 2 + 1] = (byte) ((sval & 0x0000ff00) >> 8);
      }

      // Get an input stream on the
      // byte array containing the data
      InputStream byteArrayInputStream = new ByteArrayInputStream(audioData);
      AudioFormat audioFormat = new AudioFormat(44100, 16, 1, true, false);

      audioInputStream = new AudioInputStream(byteArrayInputStream, audioFormat, audioData.length
          / audioFormat.getFrameSize());
      DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
      // System.out.println("data line info?"+dataLineInfo.toString());
      sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
      sourceDataLine.open(audioFormat);
      sourceDataLine.start();

      // Create a thread to play back
      // the data and start it
      // running. It will run until
      // all the data has been played
      // back.
      playThread = new Thread(new PlayThread());
      playThread.start();
    } catch (Exception oops) {
      System.out.println(oops);
      oops.printStackTrace();
      // System.exit(0);
      System.out.println("Problem playing audio: " + oops);
    }// end catch

  }

  // Inner class to play back the data
  // that was saved.
  class PlayThread extends Thread {
    byte tempBuffer[] = new byte[10000];

    public void run() {
      try {
        int cnt;
        // Keep looping until the input
        // read method returns -1 for
        // empty stream.
        while ((cnt = audioInputStream.read(tempBuffer, 0, tempBuffer.length)) != -1) {
          if (cnt > 0) {
            // Write data to the internal
            // buffer of the data line
            // where it will be delivered
            // to the speaker.
            sourceDataLine.write(tempBuffer, 0, cnt);
          }// end if
        }// end while
        // Block and wait for internal
        // buffer of the data line to
        // empty.
        sourceDataLine.drain();
        sourceDataLine.close();
      } catch (Exception e) {
        System.out.println(e);
        sourceDataLine.drain();
        sourceDataLine.close();
        //System.exit(0);
      }

    }//end run
  }//end inner class PlayThread

}