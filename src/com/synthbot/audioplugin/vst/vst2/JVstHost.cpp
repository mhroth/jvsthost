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

#include "com_synthbot_audioplugin_vst_vst2_JVstHost2.h"
#include "com_synthbot_audioplugin_vst_vst2_JVstHost20.h"
#include "com_synthbot_audioplugin_vst_vst2_JVstHost23.h"
#include "com_synthbot_audioplugin_vst_vst2_JVstHost24.h"
#include "audioeffectx.cpp" // needed for Host/PlugCanDos
#include <stdlib.h>

#if _WIN32
  #include <windows.h>
#elif TARGET_API_MAC_CARBON
  #include <CoreFoundation/CoreFoundation.h>
  #include <Carbon/Carbon.h>
  //#include "macpath.cpp"
#else // unix
  #include <dlfcn.h>
#endif

#define JNI_VERSION JNI_VERSION_1_4

#define PPQ 96.0
#define DEFAULT_TEMPO 120.0
#define WINDOWS_EDITOR_CLASSNAME "JVstHost Native Editor"

// GLOBAL VARIABLES
JavaVM *jvm;
jclass vpwClass;
jclass midiMessageClass;
jmethodID vpwAudioMasterProcessMidiEvents;
jmethodID vpwAudioMasterIoChanged;
jmethodID vpwAudioMasterAutomate;
jmethodID vpwAudioMasterBeginEdit;
jmethodID vpwAudioMasterEndEdit;
jmethodID getPluginDirectory;
jmethodID mmGetMessage;

/**
 * A struct to hold locally cached variables for the host
 */
typedef struct hostLocalVars {
  jobject jVstHost2;
  float **fInputs;
  float **fOutputs;
  double **dInputs;
  double **dOutputs;
  VstTimeInfo *vti;
  void *libPtr;
  double sampleRate; // cache the current sampleRate and blockSize, so that the java object doesn't have to be asked for it every time an audioMaster callback is made (such as for VstTimeInfo pointers).
  int blockSize;
  double tempo;
  void *nativeEditorWindow;
};

/**
 * Can be extended in the future if we find that resvd1 is being overwritten by some plugins.
 */
bool isHostLocalVarsValid(AEffect *effect) {
  if (effect != NULL) {
    return (effect->resvd1 != NULL);
  } else {
    return false;
  }
}

void initHostLocalArrays(AEffect *effect) {
  if (isHostLocalVarsValid(effect)) {
    hostLocalVars *hostVars = (hostLocalVars *) effect->resvd1;
    hostVars->fInputs = (float **) malloc(sizeof(float *) * effect->numInputs);
    hostVars->fOutputs = (float **) malloc(sizeof(float *) * effect->numOutputs);
    if (effect->flags & effFlagsCanDoubleReplacing) {
      hostVars->dInputs = (double **) malloc(sizeof(double *) * effect->numInputs);
      hostVars->dOutputs = (double **) malloc(sizeof(double *) * effect->numOutputs);
    } else {
      hostVars->dInputs = 0;
      hostVars->dOutputs = 0;
    }
  }
}

void freeHostLocalArrays(AEffect *effect) {
  if (isHostLocalVarsValid(effect)) {
    hostLocalVars *hostVars = (hostLocalVars *) effect->resvd1;
    free(hostVars->fInputs);
    free(hostVars->fOutputs);
    if (effect->flags & effFlagsCanDoubleReplacing) {
      free(hostVars->dInputs);
      free(hostVars->dOutputs);
    }
  }
}

jobject getCachedCallingObject(AEffect *effect) {
  if (isHostLocalVarsValid(effect)) {
    return ((hostLocalVars *) effect->resvd1)->jVstHost2;
  } else {
    return NULL;
  }
}

/**
 * Called only once at start of library load
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *ur_jvm, void *reserved) {
  // cache the java vm pointer
  // used for getting a pointer to the java environment during callbacks
  jvm = ur_jvm;
  
  JNIEnv *env;
  jvm->GetEnv((void **)&env, JNI_VERSION);
  
  jclass javaClass = env->FindClass("com/synthbot/audioplugin/vst/vst2/JVstHost2");
  vpwClass = (jclass) env->NewWeakGlobalRef(javaClass);
  
  javaClass = env->FindClass("javax/sound/midi/MidiMessage");
  midiMessageClass = (jclass) env->NewWeakGlobalRef(javaClass);

  vpwAudioMasterProcessMidiEvents = env->GetMethodID(vpwClass, "audioMasterProcessMidiEvents", "(IIII)V");
  vpwAudioMasterIoChanged = env->GetMethodID(vpwClass, "audioMasterIoChanged", "(IIII)V");
  vpwAudioMasterAutomate = env->GetMethodID(vpwClass, "audioMasterAutomate", "(IF)V");
  vpwAudioMasterBeginEdit = env->GetMethodID(vpwClass, "audioMasterBeginEdit", "(I)V");
  vpwAudioMasterEndEdit = env->GetMethodID(vpwClass, "audioMasterEndEdit", "(I)V");
  //getPluginDirectory = env->GetMethodID(vpwClass, "getPluginDirectory", "()Ljava/lang/String;");
  mmGetMessage = env->GetMethodID(midiMessageClass, "getMessage", "()[B");
  
  #if _WIN32
    WNDCLASS wndclass;
    wndclass.style = CS_HREDRAW | CS_VREDRAW;
    wndclass.lpfnWndProc = (WNDPROC) EditorProc;
    wndclass.cbClsExtra = 0;
    wndclass.cbWndExtra = sizeof(LONG_PTR); // a pointer to the associated effect is stored with the window in order to allow for callbacks in the window messaging loop
    wndclass.hInstance = GetModuleHandle(NULL);
    wndclass.hIcon = LoadIcon(NULL, IDI_APPLICATION);
    wndclass.hCursor = LoadCursor(NULL, IDC_ARROW);
    wndclass.hbrBackground = 0;
    wndclass.lpszMenuName = NULL;
    wndclass.lpszClassName = WINDOWS_EDITOR_CLASSNAME;
    
    RegisterClass(&wndclass);
  #endif
  
  return JNI_VERSION;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *jvm, void *reserved) {
  JNIEnv *env;
  jvm->GetEnv((void **)&env, JNI_VERSION);
  env->DeleteWeakGlobalRef(vpwClass);
  env->DeleteWeakGlobalRef(midiMessageClass);
}

void opcode2string(VstInt32 opcode, VstIntPtr value, JNIEnv *env) {
  jstring message;

  switch(opcode) {
    case audioMasterAutomate: {
      message = env->NewStringUTF("audioMasterAutomate");
      break;
    }
    case audioMasterVersion: {
      message = env->NewStringUTF("audioMasterVersion");
      break;
    }
    case audioMasterCurrentId: {
      message = env->NewStringUTF("audioMasterCurrentId");
      break;
    }
    case audioMasterIdle: {
      message = env->NewStringUTF("audioMasterIdle");
      break;
    }
    case audioMasterGetTime: {      
      char *str = (char *) malloc(sizeof(char) * 100);
      sprintf(str, "audioMasterGetTime: 0x%X", value);
      message = env->NewStringUTF(str);
      free(str);
      break;
    }
    case audioMasterProcessEvents: {
      message = env->NewStringUTF("audioMasterProcessEvents");
      break;
    }
    case audioMasterIOChanged: {
      message = env->NewStringUTF("audioMasterIOChanged");
      break;
    }
    case audioMasterSizeWindow: {
      message = env->NewStringUTF("audioMasterSizeWindow");
      break;
    }
    case audioMasterGetSampleRate: {
      message = env->NewStringUTF("audioMasterGetSampleRate");
      break;
    }
    case audioMasterGetBlockSize: {
      message = env->NewStringUTF("audioMasterGetBlockSize");
      break;
    }
    case audioMasterGetInputLatency: {
      message = env->NewStringUTF("audioMasterGetInputLatency");
      break;
    }
    case audioMasterGetOutputLatency: {
      message = env->NewStringUTF("audioMasterGetOutputLatency");
      break;
    }
    case audioMasterGetCurrentProcessLevel: {
      message = env->NewStringUTF("audioMasterGetCurrentProcessLevel");
      break;
    }
    case audioMasterGetAutomationState: {
      message = env->NewStringUTF("audioMasterGetAutomationState");
      break;
    }
    case audioMasterOfflineStart: {
      message = env->NewStringUTF("audioMasterOfflineStart");
      break;
    }
    case audioMasterOfflineRead: {
      message = env->NewStringUTF("audioMasterOfflineRead");
      break;
    }
    case audioMasterOfflineWrite: {
      message = env->NewStringUTF("audioMasterOfflineWrite");
      break;
    }
    case audioMasterOfflineGetCurrentPass: {
      message = env->NewStringUTF("audioMasterOfflineGetCurrentPass");
      break;
    }
    case audioMasterOfflineGetCurrentMetaPass: {
      message = env->NewStringUTF("audioMasterOfflineGetCurrentMetaPass");
      break;
    }
    case audioMasterGetVendorString: {
      message = env->NewStringUTF("audioMasterGetVendorString");
      break;
    }
    case audioMasterGetProductString: {
      message = env->NewStringUTF("audioMasterGetProductString");
      break;
    }
    case audioMasterGetVendorVersion: {
      message = env->NewStringUTF("audioMasterGetVendorVersion");
      break;
    }
    case audioMasterVendorSpecific: {
      message = env->NewStringUTF("audioMasterVendorSpecific");
      break;
    }
    case audioMasterCanDo: {
      message = env->NewStringUTF("audioMasterCanDo");
      break;
    }
    case audioMasterGetLanguage: {
      message = env->NewStringUTF("audioMasterGetLanguage");
      break;
    }
    case audioMasterGetDirectory: {
      message = env->NewStringUTF("audioMasterGetDirectory");
      break;
    }
    case audioMasterUpdateDisplay: {
      message = env->NewStringUTF("audioMasterUpdateDisplay");
      break;
    }
    case audioMasterBeginEdit: {
      message = env->NewStringUTF("audioMasterBeginEdit");
      break;
    }
    case audioMasterEndEdit: {
      message = env->NewStringUTF("audioMasterEndEdit");
      break;
    }
    case audioMasterOpenFileSelector: {
      message = env->NewStringUTF("audioMasterOpenFileSelector");
      break;
    }
    case audioMasterCloseFileSelector: {
      message = env->NewStringUTF("audioMasterCloseFileSelector");
      break;
    }
    case audioMasterWantMidi: {
      message = env->NewStringUTF("audioMasterWantMidi: DEPRECATED in VST 2.4");
      break;
    }
    case audioMasterNeedIdle: {
      message = env->NewStringUTF("audioMasterNeedIdle: DEPRECATED in VST 2.4");
      break;
    }
    default: {
      char *str = (char *) malloc(sizeof(char) * 100);
      sprintf(str, "Opcode not recognized: %i", opcode);
      message = env->NewStringUTF(str);
      free(str);
      break;
    }
  }

  // calls System.out.println(message);
  env->CallObjectMethod(
      env->GetStaticObjectField(
          env->FindClass("java/lang/System"), 
          env->GetStaticFieldID(env->FindClass("java/lang/System"), "out", "Ljava/io/PrintStream;")), 
      env->GetMethodID(env->FindClass("java/io/PrintStream"), "println", "(Ljava/lang/String;)V"), 
      message);

}

// opcodes listed in aeffect.h and aeffectx.h
VstIntPtr VSTCALLBACK HostCallback (AEffect *effect, VstInt32 opcode, VstInt32 index, VstIntPtr value, void *ptr, float opt) {

  JNIEnv *env;
  jvm->GetEnv((void **)&env, JNI_VERSION);

  //opcode2string(opcode, value, env);
  
  switch(opcode) {
  
    // called when a parameter value has changed
    // such as by the plugin's own editor
    // called when the plugin calls setParameterAutomated
    case audioMasterAutomate: {
      jobject jobj = getCachedCallingObject(effect);
      if (jobj == NULL) {
        return 0;
      } else {
        env->CallVoidMethod(
            jobj,
            vpwAudioMasterAutomate,
            (jint) index,
            (jfloat) opt);
        return 1;
      }
    }
    
    // Host VST version
    case audioMasterVersion: {
      return kVstVersion; // can handle (supposedly ;-) VST2.4
    }
    
    case audioMasterCurrentId: {
      return 0;
    }
    
    case audioMasterIdle: {
      return 0;
    }
    
    // [return value]: #VstTimeInfo* or null if not supported [value]: request mask
    /*
      double samplePos;				///< current Position in audio samples (always valid)
      double sampleRate;				///< current Sample Rate in Herz (always valid)
      double nanoSeconds;				///< System Time in nanoseconds (10^-9 second)
      double ppqPos;					///< Musical Position, in Quarter Note (1.0 equals 1 Quarter Note)
      double tempo;					///< current Tempo in BPM (Beats Per Minute)
      double barStartPos;				///< last Bar Start Position, in Quarter Note
      double cycleStartPos;			///< Cycle Start (left locator), in Quarter Note
      double cycleEndPos;				///< Cycle End (right locator), in Quarter Note
      VstInt32 timeSigNumerator;		///< Time Signature Numerator (e.g. 3 for 3/4)
      VstInt32 timeSigDenominator;	///< Time Signature Denominator (e.g. 4 for 3/4)
      VstInt32 smpteOffset;			///< SMPTE offset (in SMPTE subframes (bits; 1/80 of a frame)). The current SMPTE position can be calculated using #samplePos, #sampleRate, and #smpteFrameRate.
      VstInt32 smpteFrameRate;		///< @see VstSmpteFrameRate
      VstInt32 samplesToNextClock;	///< MIDI Clock Resolution (24 Per Quarter Note), can be negative (nearest clock)
      VstInt32 flags;					///< @see VstTimeInfoFlags
    */
    // check aeffecx.h!
    // note that the vti struct must be freed by the host, not the plugin.
    case audioMasterGetTime: {
      VstTimeInfo *vti = ((hostLocalVars *) effect->resvd1)->vti;
      vti->samplePos = 0.0;
      vti->sampleRate = ((hostLocalVars *) effect->resvd1)->sampleRate;
      vti->flags = 0;
      if (value & kVstNanosValid != 0) { // bit 8
        // Live returns this...
        //vti->nanoSeconds = 0.0;
        //vti->flags |= kVstNanosValid;
      }
      if (value & kVstPpqPosValid != 0) { // bit 9
        //vti->ppqPos = (vti->samplePos/vti->sampleRate) * (TEMPO_BPM/60.0);
        vti->ppqPos = 0.0;
        vti->flags |= kVstPpqPosValid;
      }
      if (value & kVstTempoValid != 0) { // bit 10
        vti->tempo = ((hostLocalVars *) effect->resvd1)->tempo;
        vti->flags |= kVstTempoValid;
      }
      if (value & kVstBarsValid != 0) { // bit 11
        // Live returns this...
        vti->barStartPos = 0.0;
        vti->flags |= kVstBarsValid;
      }
      if (value & kVstCyclePosValid != 0) { // bit 12
        // Live returns this...
        vti->cycleStartPos = 0.0;
        vti->cycleEndPos = 0.0;
        vti->flags |= kVstCyclePosValid;
      }
      if (value & kVstTimeSigValid != 0) { // bit 13
        vti->timeSigNumerator = 4;
        vti->timeSigDenominator = 4;
        vti->flags |= kVstTimeSigValid;
      }
      if (value & kVstSmpteValid != 0) { // bit 14
        //vti->smpteFrameRate = kVstSmpte24fps; // return something!???
        //vti->flags |= kVstSmpteValid;
      }
      if (value & kVstClockValid != 0) { // bit 15
        // Live returns this...
        //vti->samplesToNextClock = 0;
        //vti->flags |= kVstClockValid;
      }
      
      return (VstIntPtr) vti;
    }
    
    // [ptr]: pointer to #VstEvents
    // plugin is sending vstevents (midi events, all other types have been deprecated in VST2.4) to the host
    /*
     * this method currently sends each midi event seperately to the java object,
     * instead of collecting them all and sending up an array.
     * This is done in order to avoid calling lots of java methods to construct the ShortMessages
     * in the C++ code. I am not sure if this is worth the tradeoff however.
     * NOTE: less information is being passed up than is available in the VstMidiEvent structure.
     */
    case audioMasterProcessEvents: {
      jobject jobj = getCachedCallingObject(effect);
      if (jobj == NULL) {
        return 0;
      } else {
        VstEvents *vstes = (VstEvents *)ptr;
        VstEvent *vste;
        VstMidiEvent *vstme;
        VstMidiSysexEvent *vstmse;
        
        for (int i = 0; i < vstes->numEvents; i++) {
          vste = vstes->events[i];
          switch (vste->type) {
            case kVstMidiType: {
              vstme = (VstMidiEvent *)vste;
              env->CallVoidMethod(
                  jobj,
                  vpwAudioMasterProcessMidiEvents, 
                  ((jint) vstme->midiData[0]) & 0x000000F0,
                  ((jint) vstme->midiData[0]) & 0x0000000F,
                  ((jint) vstme->midiData[1]) & 0x0000007F,
                  ((jint) vstme->midiData[2]) & 0x0000007F);
              break;
            }
            case kVstSysExType: {
              // not handling this case at the moment
              break;
            }
          }
        }
        return 1;
      }
    }
    
    case audioMasterIOChanged: {
      jobject jobj = getCachedCallingObject(effect);
      if (jobj == NULL) {
        return 0;
      } else {
        env->MonitorEnter(jobj);
  
        freeHostLocalArrays(effect);
        initHostLocalArrays(effect); // reinitialise the arrays with the new numInputs and numOutputs
               
        env->CallVoidMethod(
            jobj,
            vpwAudioMasterIoChanged,
            effect->numInputs,
            effect->numOutputs,
            effect->initialDelay,
            effect->numParams);
  
        env->MonitorExit(jobj);
        return 1;
      }
    }
    
    // [index]: new width [value]: new height [return value]: 1 if supported
    case audioMasterSizeWindow: {
      #if _WIN32
        if (isHostLocalVarsValid(effect)) {
          HWND hwnd = (HWND) ((hostLocalVars *) effect->resvd1)->nativeEditorWindow;
          if (hwnd != NULL) {
            setEditorWindowSizeAndPosition(hwnd, (int) index, (int) value);
            return 1;
          }
        }
      #endif
      return 0; // not supported
    }
    
    // [return value]: current sample rate
    case audioMasterGetSampleRate: {
      return (VstIntPtr) ((hostLocalVars *) effect->resvd1)->sampleRate;
    }
    
    // Returns block size from Host
    case audioMasterGetBlockSize: {
      return (VstIntPtr) ((hostLocalVars *) effect->resvd1)->blockSize;
    }
    
    // [return value]: input latency in audio samples
    case audioMasterGetInputLatency: {
      return 0;
    }
    
    // [return value]: output latency in audio samples
    case audioMasterGetOutputLatency: {
      return 0;
    }
    
    // [return value]: current process level
    // return VstProcessLevels which are enumed in aeffectx.h
    case audioMasterGetCurrentProcessLevel: {
      /*
      kVstProcessLevelUnknown = 0,	///< not supported by Host
	    kVstProcessLevelUser,			///< 1: currently in user thread (GUI)
	    kVstProcessLevelRealtime,		///< 2: currently in audio thread (where process is called)
	    kVstProcessLevelPrefetch,		///< 3: currently in 'sequencer' thread (MIDI, timer etc)
	    kVstProcessLevelOffline			///< 4: currently offline processing and thus in user thread
      */
      return kVstProcessLevelUnknown;
    }
    
    // [return value]: current automation state
    case audioMasterGetAutomationState: {
      return kVstAutomationUnsupported;
    }
    
    case audioMasterGetVendorString: {
      strcpy((char *)ptr, "Synthbot.com");
      // in general should prolly call the java code for this string
      return 1;
    }
    
    case audioMasterGetProductString: {
      strcpy((char *)ptr, "JVstHost2");
      return 1;
    }
    
    case audioMasterGetVendorVersion: {
      return 10; // 1.0
    }
    
    case audioMasterCanDo: {
      char *canDo = (char *)ptr;

      using namespace HostCanDos;
      if(strcmp(canDo, canDoSendVstEvents) == 0)                       return 1;
      else if(strcmp(canDo, canDoSendVstMidiEvent) == 0)               return 1; ///< Host supports send of MIDI events to plug-in
      else if(strcmp(canDo, canDoSendVstTimeInfo) == 0)                return 1; ///< Host supports send of VstTimeInfo to plug-in
      else if(strcmp(canDo, canDoReceiveVstEvents) == 0)               return 1;
      else if(strcmp(canDo, canDoReceiveVstMidiEvent) == 0)            return 1;
      else if(strcmp(canDo, canDoReportConnectionChanges) == 0)        return 0;
      else if(strcmp(canDo, canDoAcceptIOChanges) == 0)                return 1;
      else if(strcmp(canDo, canDoSizeWindow) == 0)                     return 0;
      else if(strcmp(canDo, canDoOffline) == 0)                        return 0;
      else if(strcmp(canDo, canDoOpenFileSelector) == 0)               return 0;
      else if(strcmp(canDo, canDoCloseFileSelector) == 0)              return 0;
      else if(strcmp(canDo, canDoStartStopProcess) == 0)               return 1;
      else if(strcmp(canDo, canDoShellCategory) == 0)                  return 0;
      else if(strcmp(canDo, canDoSendVstMidiEventFlagIsRealtime) == 0) return 0;
      else return 0;
    }
    
    case audioMasterGetLanguage: {
      return kVstLangEnglish; // language of this host is english
    }
    
    case audioMasterOpenFileSelector: {
      return 0; // not supported
    }
    
    case audioMasterCloseFileSelector: {
      return 0;
    }
    
    case audioMasterGetDirectory: {
      /*
      jobject jobj = getJobj(env);
      if (jobj == NULL) {
        return NULL;
      } else {
        #if TARGET_API_MAC_CARBON
          // is this really working???
          jstring pluginDirectory = (jstring) env->CallObjectMethod(jobj, getPluginDirectory, NULL);
          FSSpec fss;
          path2fss(&fss, (char *) pluginDirectory);
          return (VstIntPtr) (&fss);
        #elif
          return NULL;
        #endif 
      }
      */
      return NULL;
    }
    
    case audioMasterUpdateDisplay: {
      return 0;
    }
    
    case audioMasterBeginEdit: {
      jobject jobj = getCachedCallingObject(effect);
      if (jobj == NULL) {
        return 0;
      } else {
        env->CallVoidMethod(
            jobj,
            vpwAudioMasterBeginEdit,
            (jint) index);
        return 1;
      }
    }
    
    case audioMasterEndEdit: {
      jobject jobj = getCachedCallingObject(effect);
      if (jobj == NULL) {
        return 0;
      } else {
        env->CallVoidMethod(
            jobj,
            vpwAudioMasterEndEdit,
            (jint) index);
        return 1;
      }
    }
    
    default: {
      return 0;
    }
  }
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_setThis
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  if (isHostLocalVarsValid(effect)) {
    ((hostLocalVars *) effect->resvd1)->jVstHost2 = env->NewWeakGlobalRef(jobj);  
  }
  
}

JNIEXPORT jlong JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost2_loadPlugin
  (JNIEnv *env, jclass jclazz, jstring pluginPath) {
  
  void *libptr = NULL;
  AEffect *ae = NULL;

  #if _WIN32
    const char *path = (char *)(env->GetStringUTFChars(pluginPath, NULL));
    if (path == NULL) {
      env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "jstring conversion failed.");
      return 0;
    }
    libptr = LoadLibrary (path);
    if (libptr == NULL) {
      env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "The native VST library could not be loaded.");
      return 0;
    }
    env->ReleaseStringUTFChars(pluginPath, path);
    AEffect* (*mainProc) (audioMasterCallback);
    mainProc = (AEffect* (*)(audioMasterCallback)) GetProcAddress((HMODULE) libptr, "VSTPluginMain");
    if (!mainProc) {
      mainProc = (AEffect* (*)(audioMasterCallback)) GetProcAddress((HMODULE) libptr, "main");
      if (!mainProc) {
        FreeLibrary((HMODULE) libptr);
        env->ThrowNew(
            env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
            "The plugin entry function could not be found.");
        return 0;
      }
    }
    ae = (AEffect *) mainProc(HostCallback);
    if(ae == NULL || ae->magic != kEffectMagic) {
      FreeLibrary((HMODULE) libptr); // unload the library
      env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "The plugin could not be instantiated.");
      return 0;
    }

  #elif TARGET_API_MAC_CARBON
    const char *path = (char *) (env->GetStringUTFChars(pluginPath, NULL)); // convert the java string pathname into a c char array
    if (path == NULL) {
      env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "jstring conversion failed.");
      return 0;
    }
    CFStringRef fileNameString = CFStringCreateWithCString(NULL, path, kCFStringEncodingUTF8);
    env->ReleaseStringUTFChars(pluginPath, path);
    if (fileNameString == NULL) {
	    env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "CFString creation failed.");
	    return 0;
    }
    CFURLRef url = CFURLCreateWithFileSystemPath(NULL, fileNameString, kCFURLPOSIXPathStyle, false);
    CFRelease(fileNameString);
    if (url == NULL) {
      env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "CFURLRef creation failed.");
      return 0;
    }
    libptr = CFBundleCreate(NULL, url);
    CFRelease (url);
    if (libptr == NULL) {
      env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "The plugin bundle does not exist.");
      return 0;
    }
    AEffect* (*mainProc) (audioMasterCallback);
    mainProc = (AEffect* (*)(audioMasterCallback)) CFBundleGetFunctionPointerForName((CFBundleRef) libptr, CFSTR("VSTPluginMain"));
    if (mainProc == NULL) {
      mainProc = (AEffect* (*)(audioMasterCallback)) CFBundleGetFunctionPointerForName((CFBundleRef) libptr, CFSTR("main_macho"));
      if (mainProc == NULL) {
        CFRelease((CFBundleRef)libptr); // unload the library
	      env->ThrowNew(
            env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
            "The plugin entry function could not be found.");
	      return 0;
      }
    }
    ae = (AEffect *) mainProc(HostCallback);
    if(ae == NULL || ae->magic != kEffectMagic) {
      CFRelease((CFBundleRef)libptr); // close the library
      env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "The plugin could not be instantiated.");
      return 0;
    }


  #else // for unix
    const char *path = (char *) env->GetStringUTFChars(pluginPath, NULL); // convert the java string pathname into a c char array
	  if (path == NULL) {
      env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "jstring conversion failed.");
      return 0;
    }
    libptr = dlopen(path, RTLD_LAZY); // load the library
	  if (libptr == NULL) {
		  env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "The VST library could not be loaded.");
		  return 0;
	  } else {
      dlerror(); // clear the error field
    }
    env->ReleaseStringUTFChars(pluginPath, path); // Informs the VM that the native code no longer needs access to chars
    AEffect* (*vstPluginFactory) (audioMasterCallback); // define the vstPluginFactory
    vstPluginFactory = (AEffect* (*)(audioMasterCallback)) dlsym(libptr, "VSTPluginMain"); // get a pointer to the entry function
    if(vstPluginFactory == NULL) {
      vstPluginFactory = (AEffect* (*)(audioMasterCallback))dlsym(libptr, "main"); // try another entry function
      if(vstPluginFactory == NULL) { // the entry function could not be found
        dlclose(libptr); // close the reference to the library
        env->ThrowNew(
            env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
            "The plugin entry function could not be found.");
        return 0;
      } else {
        dlerror(); // clear the error field
      }
    } else {
      dlerror();
    }
    ae = (AEffect *) vstPluginFactory(HostCallback); // create the audioeffect!
    if(ae == NULL || ae->magic != kEffectMagic) {
      dlclose(libptr);
      env->ThrowNew(
          env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException"), 
          "The plugin could not be instantiated.");
      return 0;
    }
  #endif

  ae->dispatcher (ae, effOpen, 0, 0, 0, 0); // open the plugin. Should be called, but many VSTs may not do anything with it
  
  // initialise the local variables for the host
  ae->resvd1 = (VstIntPtr) malloc(sizeof(hostLocalVars));
  ((hostLocalVars *) ae->resvd1)->jVstHost2 = NULL;
  initHostLocalArrays(ae);
  ((hostLocalVars *) ae->resvd1)->vti = (VstTimeInfo *) malloc(sizeof(VstTimeInfo));
  ((hostLocalVars *) ae->resvd1)->libPtr = libptr;
  ((hostLocalVars *) ae->resvd1)->sampleRate = 0.0;
  ((hostLocalVars *) ae->resvd1)->blockSize = 0;
  ((hostLocalVars *) ae->resvd1)->tempo = DEFAULT_TEMPO;
  ((hostLocalVars *) ae->resvd1)->nativeEditorWindow = NULL;

  return (jlong) ae;
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost2_unloadPlugin
  (JNIEnv *env, jclass jclazz, jlong ae) {

  if (ae != 0) {
    AEffect *effect = (AEffect *)ae;
    
    if (isHostLocalVarsValid(effect)) {
      hostLocalVars *hostVars = (hostLocalVars *) effect->resvd1;
      void *libPtr = hostVars->libPtr;
      if (hostVars->jVstHost2 != 0) {
        env->DeleteWeakGlobalRef(hostVars->jVstHost2);
      }
      freeHostLocalArrays(effect);
      if (hostVars->vti != 0) {
        free(hostVars->vti);
      }
      free(hostVars);
      effect->resvd1 = NULL;
      
      // close the plugin
      effect->dispatcher (effect, effClose, 0, 0, 0, 0);
      
      // close the library from which the plugin was loaded
      if (libPtr != 0) {
        #if _WIN32
          FreeLibrary((HMODULE) libPtr);
        #elif TARGET_API_MAC_CARBON
          CFRelease((CFBundleRef)libPtr);
        #else
          dlclose(libPtr);
        #endif
      }
    } else {
      effect->dispatcher (effect, effClose, 0, 0, 0, 0);
    }
  }
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost2_getVstVersion
  (JNIEnv *env, jclass jclazz, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  return effect->dispatcher(effect, effGetVstVersion, 0, 0, 0, 0);
}

/*
 * System dependent windowing code
 */
#if _WIN32
/*
 * Returns true if the ERect info has been successfully retrieved and set. False otherwise.
 * The window is set to a default size of 100 x 100 pixels in case of failure.
 */
bool setERectInfo(AEffect *effect, HWND hwnd) {
  ERect* eRect = NULL;
  effect->dispatcher (effect, effEditGetRect, 0, 0, &eRect, 0);
  if (eRect != NULL) {
    bool result = true;
    int width = eRect->right - eRect->left;
    int height = eRect->bottom - eRect->top;
    if (width < 100) {
      width = 100;
      result = false;
    }
    if (height < 100) {
      height = 100;
      result = false;
    }

    setEditorWindowSizeAndPosition(effect, hwnd);
    return result;
  } else {
    return false;
  }
}

void setEditorWindowSizeAndPosition(HWND hwnd, int width, int height) {
  RECT wRect;
  SetRect (&wRect, 0, 0, width, height);
  AdjustWindowRectEx(&wRect, GetWindowLong(hwnd, GWL_STYLE), FALSE, GetWindowLong(hwnd, GWL_EXSTYLE));
  width = wRect.right - wRect.left;
  height = wRect.bottom - wRect.top;
  SetWindowPos(hwnd, HWND_TOP, 0, 0, width, height, SWP_NOMOVE);
}

INT_PTR CALLBACK EditorProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {

  switch(msg) {
    case WM_CREATE: {
      AEffect *effect = (AEffect *) (((CREATESTRUCT *) lParam)->lpCreateParams);
      if (effect != NULL) {
        SetTimer(hwnd, 1, 40, NULL); // 40ms == 25 frames per second
      
        JNIEnv *env;
        jvm->GetEnv((void **)&env, JNI_VERSION);
        env->MonitorEnter(getCachedCallingObject(effect));

        // apparently some plugins require effEditGetRect to be called before effEditOpen
        bool isERectInfoSet = setERectInfo(effect, hwnd);
        effect->dispatcher (effect, effEditOpen, 0, 0, hwnd, 0);
        if (!isERectInfoSet) {
          setERectInfo(effect, hwnd);
        }
        
        env->MonitorExit(getCachedCallingObject(effect));

        ShowWindow(hwnd, SW_SHOW);
        UpdateWindow(hwnd);

        return TRUE; // the message was processed
      } else {
        return FALSE;
      }
    }
    
    case WM_TIMER: {
      AEffect *effect = (AEffect *) GetWindowLongPtr(hwnd, 0);
      if (effect != NULL) {
        //JNIEnv *env;
        //jvm->GetEnv((void **)&env, JNI_VERSION);
        //env->MonitorEnter(getCachedCallingObject(effect));
        effect->dispatcher (effect, effEditIdle, 0, 0, 0, 0);
        //env->MonitorExit(getCachedCallingObject(effect));
        return TRUE;
      } else {
        return FALSE;
      }
    }

    case WM_DESTROY: {
      AEffect *effect = (AEffect *) GetWindowLongPtr(hwnd, 0);
      if (effect != NULL) {
        JNIEnv *env;
        jvm->GetEnv((void **)&env, JNI_VERSION);
        env->MonitorEnter(getCachedCallingObject(effect));
        effect->dispatcher(effect, effEditClose, 0, 0, 0, 0);
        ((hostLocalVars *) effect->resvd1)->nativeEditorWindow = NULL;
        env->MonitorExit(getCachedCallingObject(effect));
      }
      KillTimer(hwnd, 1);
      PostQuitMessage(0);
      return TRUE;
    }
    
    default: {
      return DefWindowProc(hwnd, msg, wParam, lParam);
    }
  }
}

#elif TARGET_API_MAC_CARBON
void idleTimerProc (EventLoopTimerRef inTimer, void *inUserData)
{
	AEffect* effect = (AEffect*)inUserData;
	effect->dispatcher (effect, effEditIdle, 0, 0, 0, 0);
}

OSStatus windowHandler (EventHandlerCallRef inHandlerCallRef, EventRef inEvent, void *inUserData)
{
	WindowRef window = (WindowRef) inUserData;
	UInt32 eventClass = GetEventClass (inEvent);
	UInt32 eventKind = GetEventKind (inEvent);

	switch (eventClass) {
		case kEventClassWindow: {
			switch (eventKind) {
				case kEventWindowClose: {
          // http://developer.apple.com/documentation/Carbon/Reference/Carbon_Event_Manager_Ref/Reference/reference.html#//apple_ref/c/func/QuitAppModalLoopForWindow
					QuitAppModalLoopForWindow(window);
					break;
				}
			}
			break;
		}
	}
	return eventNotHandledErr;
}
#endif

// http://developer.apple.com/documentation/Carbon/Reference/Window_Manager/Reference/reference.html
JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_openEditor
  (JNIEnv *env, jclass jclazz, jstring jFrameTitle, jlong ae) {

  AEffect *effect = (AEffect *)ae;

  #if _WIN32
    const char *frameTitle = (char *) env->GetStringUTFChars(jFrameTitle, NULL);
    
    HWND hwnd = CreateWindow(
        WINDOWS_EDITOR_CLASSNAME, 
        frameTitle, 
        WS_SYSMENU, //WS_POPUP | WS_CAPTION | WS_BORDER | WS_SYSMENU | WS_VISIBLE | WS_DLGFRAME | DS_CENTER, //WS_OVERLAPPEDWINDOW | WS_POPUP, 
        CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT,
        NULL, 
        NULL, 
        GetModuleHandle(NULL), 
        (LPVOID) effect);
        
    env->ReleaseStringUTFChars(jFrameTitle, frameTitle);

    // set the pointer to the effect in the window
    SetWindowLongPtr(hwnd, 0, (LONG_PTR) effect);
    
    // Set the pointer to the window in resvd1 so that a close message can be sent to it later
    ((hostLocalVars *) effect->resvd1)->nativeEditorWindow = hwnd;
    
    MSG msg;
    while(GetMessage(&msg, NULL, 0, 0) != NULL) {
      TranslateMessage(&msg);
      DispatchMessage(&msg);
    }
    
    
  #elif TARGET_API_MAC_CARBON
    WindowRef window;
    Rect mRect = {0, 0, 300, 300}; // window has default 300x300 size
    
    // http://developer.apple.com/documentation/Carbon/Reference/Window_Manager/Reference/reference.html#//apple_ref/c/func/CreateNewWindow
    OSStatus err = CreateNewWindow(
        kDocumentWindowClass,
        kWindowCloseBoxAttribute       | 
        kWindowCompositingAttribute    | 
        kWindowAsyncDragAttribute      |
        kWindowStandardHandlerAttribute,
        &mRect,
        &window);
    if (err != noErr) return; // no native window could be created
    // defines the type of native events that our define windowHandler handles
    /*
    static EventTypeSpec eventTypes[] = {
		  { kEventClassWindow, kEventWindowClose }
	  };
    // http://developer.apple.com/documentation/Carbon/Conceptual/Carbon_Event_Manager/Tasks/chapter_3_section_4.html
    InstallWindowEventHandler (window, windowHandler, GetEventTypeCount (eventTypes), eventTypes, window, NULL);
    */
    effect->dispatcher(effect, effEditOpen, 0, 0, window, 0); // pass the window to the plugin editor and hope for the best
    
    ERect* eRect = NULL;
    effect->dispatcher (effect, effEditGetRect, 0, 0, &eRect, 0); // get bounds of plugin editor window
    if(eRect != NULL) { // if eRect was properly set
      // ...and resize the native window
      Rect bounds;
      GetWindowBounds (window, kWindowContentRgn, &bounds);
      bounds.right = bounds.left + (eRect->right - eRect->left);
      bounds.bottom = bounds.top + (eRect->bottom - eRect->top);
      SetWindowBounds (window, kWindowContentRgn, &bounds); 
    }
    
    
    // http://developer.apple.com/documentation/Carbon/Reference/Window_Manager/Reference/reference.html#//apple_ref/c/func/RepositionWindow
    RepositionWindow (window, NULL, kWindowCenterOnMainScreen); // move window to center of screen
    
    // http://developer.apple.com/documentation/Carbon/Reference/Window_Manager/Reference/reference.html#//apple_ref/c/func/ShowWindow
    ShowWindow (window); // show the window!
    
    // http://developer.apple.com/documentation/Carbon/Conceptual/Carbon_Event_Manager/Tasks/chapter_3_section_11.html
    EventLoopTimerRef idleEventLoopTimer;
	  InstallEventLoopTimer(
        GetCurrentEventLoop(),
        kEventDurationSecond / 10., // allows vst gui to update 10 times ever second
        kEventDurationSecond / 10.,
        idleTimerProc,
        effect,
        &idleEventLoopTimer);
    
    RunAppModalLoopForWindow(window);
    //RemoveEventLoopTimer (idleEventLoopTimer);
    
    // keep track of window so that it can be properly deleted when
    // the editor window is closed
    jfieldID fid = env->GetFieldID(vpwClass, "osxWindow", "J");
    env->SetLongField((jobject) effect->resvd1, fid, (jlong) window);
  #else // unix
  
  #endif
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_closeEditor
  (JNIEnv *env, jclass jclazz, jlong ae) {

  AEffect *effect = (AEffect *)ae;

  #if _WIN32
    // post a WM_DESTROY message to the native window's message queue, telling the native window to close
    HWND hwnd = (HWND) ((hostLocalVars *) effect->resvd1)->nativeEditorWindow;
    if (hwnd != NULL) {
      PostMessage(
          hwnd, WM_DESTROY,
          NULL, NULL);
    }
  #elif TARGET_API_MAC_CARBON
    jfieldID fid = env->GetFieldID(vpwClass, "osxWindow", "J");
    WindowRef window = (WindowRef) env->GetLongField((jobject) effect->resvd1, fid);
    if(window != NULL) {
      CFRelease(window); // ReleaseWindow (window); the latter is deprecated in OS10.5
      env->SetLongField((jobject) effect->resvd1, fid, NULL);
    } else {
      // error condition. window should be non-NULL
    }
  
  #else // unix
  
  #endif
}

/**
 * Sends the midi messages to the vst via effProcessEvents, and returns
 * a pointer to the VstEvents struct. This should be freed /after/ the
 * corresponding call to processX.
 */
VstEvents *setMidiEvents(JNIEnv *env, jobjectArray midiMessages, AEffect* effect) {

  // set up the vst events data structures
  int numMessages = 0;
  numMessages = env->GetArrayLength(midiMessages);
  jobject midiMessage;
  VstEvents *vstes;
  if (numMessages <= 2) {
    vstes = (VstEvents *) malloc(sizeof(VstEvent));
  } else {
    vstes = (VstEvents *) malloc(sizeof(VstEvents) + (numMessages-2)*sizeof(VstEvent *));
  }
  vstes->numEvents = numMessages;
  vstes->reserved = NULL;
  VstEvent *vste;
  VstMidiEvent *vstme;
  VstMidiSysexEvent *vstmse;
  jbyteArray jmessageArray;
  unsigned char *messageArray;
  for(int i = 0; i < numMessages; i++) {
    midiMessage = env->GetObjectArrayElement(midiMessages, i);
    jmessageArray = (jbyteArray) env->CallObjectMethod(midiMessage, mmGetMessage);
    messageArray = (unsigned char *) env->GetPrimitiveArrayCritical(jmessageArray, NULL);
    
    vste = (VstEvent *) malloc(sizeof(VstEvent));

    if (messageArray[0] == 0xF0) { // status byte == the System Exclusive flag
      vstmse = (VstMidiSysexEvent *) vste;
      vstmse->type = kVstSysExType;
      vstmse->byteSize = sizeof(VstMidiSysexEvent);
      vstmse->deltaFrames = 0;
      vstmse->flags = 0;
      vstmse->dumpBytes = (VstInt32) (env->GetArrayLength(jmessageArray) - 1);
      vstmse->resvd1 = (VstIntPtr) jmessageArray;
      vstmse->sysexDump = (char *) (messageArray+1); // the first byte of messageArray is the status byte, which we already recorded
      vstmse->resvd2 = 0;
      
    } else {      
      vstme = (VstMidiEvent *) vste;
      vstme->type = kVstMidiType;             //< #kVstMidiType
      vstme->byteSize = sizeof(VstMidiEvent); //< sizeof (VstMidiEvent)
      vstme->deltaFrames = 0;                 //< sample frames related to the current block start sample position
      vstme->flags = 0;                       //< @see VstMidiEventFlags
      vstme->noteLength = 0;                  //< (in sample frames) of entire note, if available, else 0
      vstme->noteOffset = 0;                  //< offset (in sample frames) into note from note start if available, else 0
      memset(vstme->midiData, 0, 4);          // clear the midiData array (4 bytes)
      memcpy(vstme->midiData, messageArray, env->GetArrayLength(jmessageArray)); // set the midiData array
      vstme->detune = 0;                      //< -64 to +63 cents; for scales other than 'well-tempered' ('microtuning')
      vstme->noteOffVelocity = 0;             //< Note Off Velocity [0, 127]
      vstme->reserved1 = 0;                   //< zero (Reserved for future use)
      vstme->reserved2 = 0;                   //< zero (Reserved for future use)
      env->ReleasePrimitiveArrayCritical(jmessageArray, messageArray, JNI_ABORT);
    }
    
    vstes->events[i] = vste;
  }
  
  // send the events to the vst
  effect->dispatcher (effect, effProcessEvents, 0, 0, vstes, 0);
  
  return vstes;
}

/**
 * Frees a VstEvents struct.
 */
void freeMidiEvents(VstEvents *vstes, JNIEnv *env) {
  VstMidiSysexEvent *vstmse;
  for(int i = 0; i < vstes->numEvents; i++) {
    if (vstes->events[i]->type == kVstSysExType) {
      vstmse = (VstMidiSysexEvent *) vstes->events[i];
      /*
       * sysexDump-1 to account for the fact that the message array starts with the status byte,
       * which had been previously accounted for. sysexDump was assigned to the first "message"
       * part of the messageArray. 
       */
      env->ReleasePrimitiveArrayCritical((jbyteArray) vstmse->resvd1, vstmse->sysexDump-1, JNI_ABORT);
    }
    free(vstes->events[i]);
  }
  free(vstes);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_processReplacing
  (JNIEnv *env, jclass jclazz, jobjectArray messages, jobjectArray jinputs, jobjectArray joutputs, jint sampleFrames, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  
  VstEvents *vstes = setMidiEvents(env, messages, effect);
  
  float **cinputs = ((hostLocalVars *) effect->resvd1)->fInputs;
  float **coutputs = ((hostLocalVars *) effect->resvd1)->fOutputs;
  for(int i = 0; i < effect->numInputs; i++) {
    cinputs[i] = (float *) env->GetPrimitiveArrayCritical(
        (jfloatArray) env->GetObjectArrayElement(jinputs, i), 
        NULL);
    if (cinputs[i] == NULL) {
      env->ThrowNew(
          env->FindClass("java/lang/OutOfMemoryError"),
          "GetPrimitiveArrayCritical failed to return a valid pointer.");
      return;
    }
  }
  for(int i = 0; i < effect->numOutputs; i++) {
    coutputs[i] = (float *) env->GetPrimitiveArrayCritical(
        (jfloatArray) env->GetObjectArrayElement(joutputs, i),
        NULL);
    if (coutputs[i] == NULL) {
      env->ThrowNew(
          env->FindClass("java/lang/OutOfMemoryError"),
          "GetPrimitiveArrayCritical failed to return a valid pointer.");
      return;
    }
  }

  effect->processReplacing(effect, cinputs, coutputs, (int) sampleFrames);

  for(int i = 0; i < effect->numInputs; i++) {
    env->ReleasePrimitiveArrayCritical(
        (jfloatArray) env->GetObjectArrayElement(jinputs, i),
        cinputs[i],
        JNI_ABORT);
  }
  for(int i = 0; i < effect->numOutputs; i++) {
    env->ReleasePrimitiveArrayCritical(
        (jfloatArray) env->GetObjectArrayElement(joutputs, i),
        coutputs[i],
        0);
  }

  freeMidiEvents(vstes, env);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost24_processDoubleReplacing
  (JNIEnv *env, jclass jclazz, jobjectArray messages, jobjectArray jinputs, jobjectArray joutputs, jint sampleFrames, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  
  VstEvents *vstes = setMidiEvents(env, messages, effect);
  
  double **cinputs = ((hostLocalVars *) effect->resvd1)->dInputs;
  double **coutputs = ((hostLocalVars *) effect->resvd1)->dOutputs;
  for(int i = 0; i < effect->numInputs; i++) {
    cinputs[i] = (double *) env->GetPrimitiveArrayCritical(
        (jdoubleArray) env->GetObjectArrayElement(jinputs, i), 
        NULL);
    if (cinputs[i] == NULL) {
      env->ThrowNew(
          env->FindClass("java/lang/OutOfMemoryError"),
          "GetPrimitiveArrayCritical failed to return a valid pointer.");
      return;
    }
  }
  for(int i = 0; i < effect->numOutputs; i++) {
    coutputs[i] = (double *) env->GetPrimitiveArrayCritical(
        (jdoubleArray) env->GetObjectArrayElement(joutputs, i),
        NULL);
    if (coutputs[i] == NULL) {
      env->ThrowNew(
          env->FindClass("java/lang/OutOfMemoryError"),
          "GetPrimitiveArrayCritical failed to return a valid pointer.");
      return;
    }
  }

  effect->processDoubleReplacing(effect, cinputs, coutputs, (int) sampleFrames);

  for(int i = 0; i < effect->numInputs; i++) {
    env->ReleasePrimitiveArrayCritical(
        (jdoubleArray) env->GetObjectArrayElement(jinputs, i),
        cinputs[i],
        JNI_ABORT);
  }
  for(int i = 0; i < effect->numOutputs; i++) {
    env->ReleasePrimitiveArrayCritical(
        (jdoubleArray) env->GetObjectArrayElement(joutputs, i),
        coutputs[i],
        0);
  }

  free(cinputs);
  free(coutputs);
  freeMidiEvents(vstes, env);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost24_canDoubleReplacing
  (JNIEnv *env, jclass jclazz, jlong ae) {
 
  return (((AEffect *)ae)->flags & effFlagsCanDoubleReplacing);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_process
  (JNIEnv *env, jclass jclazz, jobjectArray messages, jobjectArray jinputs, jobjectArray joutputs, jint sampleFrames, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  
  VstEvents *vstes = setMidiEvents(env, messages, effect);

  float **cinputs = ((hostLocalVars *) effect->resvd1)->fInputs;
  float **coutputs = ((hostLocalVars *) effect->resvd1)->fOutputs;
  for(int i = 0; i < effect->numInputs; i++) {
    cinputs[i] = (float *) env->GetPrimitiveArrayCritical(
        (jfloatArray) env->GetObjectArrayElement(jinputs, i), 
        NULL);
    if (cinputs[i] == NULL) {
      env->ThrowNew(
          env->FindClass("java/lang/OutOfMemoryError"),
          "GetPrimitiveArrayCritical failed to return a valid pointer.");
      return;
    }
  }
  for(int i = 0; i < effect->numOutputs; i++) {
    coutputs[i] = (float *) env->GetPrimitiveArrayCritical(
        (jfloatArray) env->GetObjectArrayElement(joutputs, i),
        NULL);
    if (coutputs[i] == NULL) {
      env->ThrowNew(
          env->FindClass("java/lang/OutOfMemoryError"),
          "GetPrimitiveArrayCritical failed to return a valid pointer.");
      return;
    }
  }

  effect->process(effect, cinputs, coutputs, (int) sampleFrames);

  for(int i = 0; i < effect->numInputs; i++) {
    env->ReleasePrimitiveArrayCritical(
        (jfloatArray) env->GetObjectArrayElement(jinputs, i),
        cinputs[i],
        JNI_ABORT);
  }
  for(int i = 0; i < effect->numOutputs; i++) {
    env->ReleasePrimitiveArrayCritical(
        (jfloatArray) env->GetObjectArrayElement(joutputs, i),
        coutputs[i],
        0);
  }

  free(cinputs);
  free(coutputs);
  freeMidiEvents(vstes, env);
}


JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_setParameter
  (JNIEnv *env, jclass jclazz, jint index, jfloat value, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  effect->setParameter(effect, index, value);
}

JNIEXPORT jfloat JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getParameter
  (JNIEnv *env, jclass jclazz, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  return effect->getParameter(effect, index);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_isParameterAutomatable
  (JNIEnv *env, jclass clazz, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  return effect->dispatcher (effect, effCanBeAutomated, index, 0, 0, 0);
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getEffectName
  (JNIEnv *env, jclass jclazz, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxEffectNameLen);
  effect->dispatcher (effect, effGetEffectName, 0, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getParameterName
  (JNIEnv *env, jclass jclazz, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxParamStrLen);
  effect->dispatcher (effect, effGetParamName, index, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getVendorName
  (JNIEnv *env, jclass jclazz, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxVendorStrLen);
  effect->dispatcher (effect, effGetVendorString, 0, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getProductString
  (JNIEnv *env, jclass jclazz, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxProductStrLen);
  effect->dispatcher (effect, effGetProductString, 0, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_numParameters
  (JNIEnv *env, jclass jclazz, jlong ae) {
  
  return ((AEffect *)ae)->numParams;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_numInputs
  (JNIEnv *env, jclass jclazz, jlong ae) {
  
  return ((AEffect *)ae)->numInputs;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_numOutputs
  (JNIEnv *env, jclass jclazz, jlong ae) {
  
  return ((AEffect *)ae)->numOutputs;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_numPrograms
(JNIEnv *env, jclass jclazz, jlong ae) {
  
  return ((AEffect *)ae)->numPrograms;
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_setSampleRate
  (JNIEnv *env, jclass jclazz, jfloat sampleRate, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  ((hostLocalVars *) effect->resvd1)->sampleRate = (double) sampleRate;
  effect->dispatcher (effect, effSetSampleRate, 0, 0, 0, sampleRate);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_setTempo
  (JNIEnv *env, jclass jclazz, jdouble tempo, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  ((hostLocalVars *) effect->resvd1)->tempo = (double) tempo;
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_setBlockSize
  (JNIEnv *env, jclass jclazz, jint blockSize, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  ((hostLocalVars *) effect->resvd1)->blockSize = blockSize;
  effect->dispatcher (effect, effSetBlockSize, 0, blockSize, 0, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_suspend
  (JNIEnv *env, jclass jclazz, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effMainsChanged, 0, 0, 0, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_resume
  (JNIEnv *env, jclass jclazz, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effMainsChanged, 0, 1, 0, 0);
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getProgramName__J
  (JNIEnv *env, jclass jclazz, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxProgNameLen);
  effect->dispatcher (effect, effGetProgramName, 0, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getProgramName__IJ
  (JNIEnv *env, jclass jclazz, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxProgNameLen);
  effect->dispatcher (effect, effGetProgramNameIndexed, (VstInt32) index, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

/*
 * There is a problem with this function in that the plugin will copy kVstMaxProgNameLen chars
 * from the given pointer. But the java function will not necessarily return a pointer to an array
 * that long. This would cause the plugin to copy too much, which is dangerous
 */
JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_setProgramName
  (JNIEnv *env, jclass jclazz, jstring jname, jlong ae) {

  return;
/*
  AEffect *effect = (AEffect *)ae;
  const char *name = (char *)(env->GetStringUTFChars(jname, NULL));
  effect->dispatcher (effect, effSetProgramName, 0, 0, name, 0);
  env->ReleaseStringUTFChars(jname, name);
*/
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getProgram
  (JNIEnv *env, jclass jclazz, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  return effect->dispatcher (effect, effGetProgram, 0, 0, 0, 0);
}


JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_setProgram
  (JNIEnv *env, jclass jclazz, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effSetProgram, 0, index, 0, 0);
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getParameterDisplay
  (JNIEnv *env, jclass jclazz, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxParamStrLen);
  effect->dispatcher (effect, effGetParamDisplay, index, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getParameterLabel
  (JNIEnv *env, jclass jclazz, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxParamStrLen);
  effect->dispatcher (effect, effGetParamLabel, index, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_hasEditor
  (JNIEnv *env, jclass jclazz, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsHasEditor);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_canReplacing
  (JNIEnv *env, jclass jclazz, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsCanReplacing);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_acceptsProgramsAsChunks
  (JNIEnv *env, jclass jclazz, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsProgramChunks);
}

JNIEXPORT jbyteArray JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getChunk
  (JNIEnv *env, jclass jclazz, jint jBankOrProgram, jlong ae) {
 
  AEffect *effect = (AEffect *)ae;
  char *chunkData;
  int lenChunkData = effect->dispatcher(effect, effGetChunk, (int) jBankOrProgram, 0, &chunkData, 0);
  if (lenChunkData <= 0) {
    return NULL;
  }
  jbyteArray jChunkData = env->NewByteArray((jsize) lenChunkData);
  if (jChunkData == NULL) {
    // error condition
    return NULL;
  }
  env->SetByteArrayRegion(jChunkData, 0, (jsize) lenChunkData, (jbyte *) chunkData); // transfer chunk into jbyteArray
  // DO NOT free the original chunkData resources. They are owned by the plugin.
  return jChunkData;
}


JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_setChunk
  (JNIEnv *env, jclass jclazz, jint jBankOrProgram, jbyteArray jChunkData, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  char *chunkData = (char *) env->GetByteArrayElements(jChunkData, NULL);
  int lenJChunkData = (int) env->GetArrayLength(jChunkData);
  effect->dispatcher(effect, effSetChunk, (int) jBankOrProgram, lenJChunkData, chunkData, 0);
  env->ReleaseByteArrayElements(jChunkData, (jbyte *) chunkData, JNI_ABORT);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_isSynth
  (JNIEnv *env, jclass jclazz, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsIsSynth);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_canDo
  (JNIEnv *env, jclass jclazz, jstring canDoString, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  const char *canDo = env->GetStringUTFChars(canDoString, NULL);
  return (jint) effect->dispatcher(effect, effCanDo, 0, 0, (void *) canDo, 0);
  env->ReleaseStringUTFChars(canDoString, canDo);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_setBypass
  (JNIEnv *env, jclass jclazz, jboolean bypass, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher(effect, effSetBypass, 0, bypass == JNI_TRUE, 0, 0);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_producesSoundInStop
  (JNIEnv *env, jclass jclazz, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsNoSoundInStop);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getUniqueId
  (JNIEnv *env, jclass jclazz, jlong ae) {

  return (jint) ((AEffect *)ae)->uniqueID;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getPluginVersion
  (JNIEnv *env, jclass jclazz, jlong ae) {
 
  return (jint) ((AEffect *)ae)->version;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getInitialDelay
  (JNIEnv *env, jclass jclazz, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  return  (jint) ((AEffect *)ae)->initialDelay;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getTailSize
  (JNIEnv *env, jclass jclazz, jlong ae) {
 
  AEffect *effect = (AEffect *)ae;
  return effect->dispatcher(effect, effGetTailSize, 0, 0, 0, 0);
}

JNIEXPORT jobject JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost20_getOutputProperties
  (JNIEnv *env, jclass clazz, jint index, jlong ae) {
  
  AEffect *effect = (AEffect *) ae;
  VstPinProperties *vpp = (VstPinProperties *) malloc(sizeof(VstPinProperties));
  jclass classVstPinProperties = env->FindClass("com/synthbot/audioplugin/vst/vst2/VstPinProperties");
  jobject jvstPinProperties;
  int isSupported = effect->dispatcher(effect, effGetOutputProperties, index, 0, vpp, 0);
  if (isSupported == 1) {
    jvstPinProperties = env->NewObject(
        classVstPinProperties,
        env->GetMethodID(classVstPinProperties, "<init>", "(ILjava/lang/String;Ljava/lang/String;I)V"),
        index,
        env->NewStringUTF(vpp->label),
        env->NewStringUTF(vpp->shortLabel),
        vpp->flags);
  } else {
    jvstPinProperties = env->NewObject(
        classVstPinProperties,
        env->GetMethodID(classVstPinProperties, "<init>", "()V"));
  }
    
  free(vpp);
  return jvstPinProperties;
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost23_startProcess
  (JNIEnv *env, jclass jclazz, jlong ae) {
 
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher(effect, effStartProcess, 0, 0, 0, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_vst2_JVstHost23_stopProcess
  (JNIEnv *env, jclass jclazz, jlong ae) {
 
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher(effect, effStopProcess, 0, 0, 0, 0);
}