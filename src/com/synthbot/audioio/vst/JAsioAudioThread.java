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

package com.synthbot.audioio.vst;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.synthbot.audioplugin.vst.vst2.JVstHost2;
import com.synthbot.jasiohost.AsioChannelInfo;
import com.synthbot.jasiohost.AsioDriver;
import com.synthbot.jasiohost.AsioDriverListener;
import com.synthbot.jasiohost.AsioInitException;
import com.synthbot.jasiohost.JAsioHost;

/**
 * JVstAudioThread implements a continuously running audio stream, calling
 * processReplacing on a single vst and sending the result to the sound output.
 */
public class JAsioAudioThread implements AsioDriverListener {

  private JVstHost2 vst;
  private final float[][] fInputs;
  private final float[][] fOutputs;
  private int blockSize;
  private int numOutputs;
  private AsioDriver asioDriver;
  private Set<AsioChannelInfo> activeChannels;

  private static final float ShortMaxValueAsFloat = (float) Short.MAX_VALUE;
  private static final float IntMaxValueAsFloat = (float) Integer.MAX_VALUE;

  public JAsioAudioThread(JVstHost2 vst) {
    this.vst = vst;
    numOutputs = vst.numOutputs();
    
    List<String> driverNames = JAsioHost.getDriverNames();
    if (driverNames.size() == 0) {
    	throw new RuntimeException("There are no ASIO drivers to load.");
    }
    asioDriver = null;
    try {
    	asioDriver = JAsioHost.getAsioDriver(driverNames.get(0));
    } catch (AsioInitException aie) {
    	throw new RuntimeException(aie);
    }
    
    blockSize = asioDriver.getBufferPreferredSize();
    fInputs = new float[vst.numInputs()][blockSize];
    fOutputs = new float[numOutputs][blockSize];

    activeChannels = new HashSet<AsioChannelInfo>();
    activeChannels.add(asioDriver.getChannelInfoOutput(0));
    activeChannels.add(asioDriver.getChannelInfoOutput(1));
    asioDriver.createBuffers(activeChannels, asioDriver.getBufferPreferredSize());
    
    asioDriver.addAsioDriverListener(this);
    
    asioDriver.openControlPanel();
  }
  
  @Override
  protected void finalize() throws Throwable {
    try {
      JAsioHost.shutdownAndUnloadDriver();
    } finally {
      super.finalize();
    }
  }
  
  public void start() {
    asioDriver.start();
  }

	@Override
	public void bufferSwitch(
			byte[][] inputByte, byte[][] outputByte,
			short[][] inputShort, short[][] outputShort,
			int[][] inputInt, int[][] outputInt, 
			float[][] inputFloat, float[][] outputFloat,
			double[][] inputDouble, double[][] outputDouble) {
    
	  
	  // process the VST
	  vst.processReplacing(fInputs, fOutputs, blockSize);
	  
	  // return the outputs to the ASIO device
	  for (AsioChannelInfo channelInfo : activeChannels) {
	    int channelIndex = channelInfo.getChannelIndex();
	    switch (channelInfo.getSampleType().getJavaNativeType()) {
	      case SHORT: {
	        for (int i = 0; i < blockSize; i++) {
            outputShort[channelIndex][i] = (short) (fOutputs[channelIndex][i] * ShortMaxValueAsFloat);
          }
	      }
	      case INTEGER: {
	        for (int i = 0; i < blockSize; i++) {
	          outputInt[channelIndex][i] = (int) (fOutputs[channelIndex][i] * IntMaxValueAsFloat);
	        }
	      }
	      case FLOAT: {
	        System.arraycopy(fOutputs[channelIndex], 0, outputInt[channelIndex], 0, blockSize);
	      }
	      case DOUBLE: {
	        for (int i = 0; i < blockSize; i++) {
            outputDouble[channelIndex][i] = (double) fOutputs[channelIndex][i];
          }
	      }
	      default: {
	        // do nothing (silence)
	      }
	    }
	  }
	}
	
	@Override
	public void latenciesChanged(int inputLatency, int outputLatency) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void resetRequest() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void resyncRequest() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void sampleRateDidChange(double sampleRate) {
		// TODO Auto-generated method stub
		
	}
}