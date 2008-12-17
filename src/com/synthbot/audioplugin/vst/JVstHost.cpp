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

#include "com_synthbot_audioplugin_vst_JVstHost.h"
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
#define TEMPO_BPM 120.0

// GLOBAL VARIABLES
JavaVM *jvm;
jclass vpwClass;
jclass classJVstLoadException;
jmethodID println;
jmethodID getSampleRate;
jmethodID getBlockSize;
jmethodID vpwAudioMasterProcessMidiEvents;
jmethodID vpwAudioMasterAutomate;
jmethodID getPluginDirectory;
jfieldID fidVtiPtr;
typedef struct {
  JNIEnv *env;
  jobject jobj;
} envobjmap;
envobjmap **map;
int mapSize;
int maxMapSize;

jobject getJobj(JNIEnv *env) {
  if (env == NULL) return NULL;
  for (int i = 0; i < mapSize; i++) {
    if (map[i]->env == env) {
      return map[i]->jobj;
    }
  }
  return NULL;
}

/**
 * returns true if the given object was found in the map assumging the given environment,
 * and the environment was updated
 */
bool setJNIEnv(jobject jobj, JNIEnv *env) {
  for (int i = 0; i < mapSize; i++) {
    // IsSameObject is used as the map version is a GlobalReference, and the input version is likely to be a local reference
    if (env->IsSameObject(map[i]->jobj, jobj) == JNI_TRUE) {
      map[i]->env = env;
      return true;
    }
  }
  return false;
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
  jclass javaClass = env->FindClass("com/synthbot/audioplugin/vst/JVstHost");
  vpwClass = (jclass) env->NewWeakGlobalRef(javaClass);
  javaClass = env->FindClass("com/synthbot/audioplugin/vst/JVstLoadException");
  classJVstLoadException = (jclass) env->NewWeakGlobalRef(javaClass);
  
  println = env->GetStaticMethodID(vpwClass, "println", "(Ljava/lang/String;)V");
  getSampleRate = env->GetMethodID(vpwClass, "getSampleRate", "()F");
  getBlockSize = env->GetMethodID(vpwClass, "getBlockSize", "()I");
  vpwAudioMasterProcessMidiEvents = env->GetMethodID(vpwClass, "audioMasterProcessMidiEvents", "(IIII)V");
  vpwAudioMasterAutomate = env->GetMethodID(vpwClass, "audioMasterAutomate", "(IF)V");
  getPluginDirectory = env->GetMethodID(vpwClass, "getPluginDirectory", "()Ljava/lang/String;");
  fidVtiPtr = env->GetFieldID(vpwClass, "vstTimeInfoPtr", "J");
  
  // initialise map
  mapSize = 0;
  maxMapSize = 128;
  map = (envobjmap **) malloc(sizeof(envobjmap *) * maxMapSize);
  
  return JNI_VERSION;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *jvm, void *reserved) {
  JNIEnv *env;
  jvm->GetEnv((void **)&env, JNI_VERSION);
  env->DeleteWeakGlobalRef(vpwClass);
  
  // free the map
  for (int i = 0; i < mapSize; i++) {
    free(map[i]);
  }
  free(map);
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
      //printf("audioMasterVersion: %i\n", kVstVersion);
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
    case 6: { // audioMasterWantMidi. This opcode is deprecated in vst 2.4 and thus not directly referenceable in the source code
      message = env->NewStringUTF("audioMasterWantMidi: DEPRECATED in VST 2.4");
      break;
    }
    case 14: { // audioMasterNeedIdle
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
  env->CallStaticObjectMethod(vpwClass, println, message);
  
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
      jobject jobj = getJobj(env);
      if (jobj != NULL) {
        env->CallVoidMethod(
            jobj, 
            vpwAudioMasterAutomate,
            (jint) index,
            (jfloat) opt);
      }
      return 0;
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
      jobject jobj = getJobj(env);
      if (jobj == NULL)
          return NULL;
      VstTimeInfo *oldVti = (VstTimeInfo *) env->GetLongField(jobj, fidVtiPtr);
      if (oldVti != NULL) {
        free(oldVti);
      }

      VstTimeInfo *vti = (VstTimeInfo *) malloc(sizeof(VstTimeInfo)); // init all fields to zero
      vti->samplePos = 0.0;
      vti->sampleRate = (double) HostCallback(effect, audioMasterGetSampleRate, 0, NULL, NULL, 0.0);
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
        vti->tempo = TEMPO_BPM;
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
    
      env->SetLongField(jobj, fidVtiPtr, (jlong) vti);
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
      jobject jobj = getJobj(env);
      if (jobj == NULL)
          return 0; // oops!
      
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
                ((jint) vstme->midiData[1]) & 0x000000FF,
                ((jint) vstme->midiData[2]) & 0x000000FF);
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
    
    // [index]: new width [value]: new height [return value]: 1 if supported
    case audioMasterSizeWindow: {
      return 0; // not supported
    }
    
    // [return value]: current sample rate
    case audioMasterGetSampleRate: {
      jobject jobj = getJobj(env);
      if (jobj == NULL) {
        return (VstIntPtr) 44100.0; // default
      } else {
        return (VstIntPtr) env->CallFloatMethod(jobj, getSampleRate);
      }
    }
    
    // Returns block size from Host
    case audioMasterGetBlockSize: {
      jobject jobj = getJobj(env);
      if (jobj == NULL) {
        return (VstIntPtr) 44100; // default
      } else {
        // this cast seems like a very strange thing to do!
        return (VstIntPtr) env->CallIntMethod(jobj, getBlockSize);
      }
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
      return kVstProcessLevelRealtime;
    }
    
    // [return value]: current automation state
    case audioMasterGetAutomationState: {
      return kVstAutomationUnsupported;
    }
    
    case audioMasterGetVendorString: {
      strcpy((char *)ptr, "Roth/Yee-King");
      // in general should prolly call the java code for this string
      return 1; // return success?
    }
    
    case audioMasterGetProductString: {
      strcpy((char *)ptr, "SynthBot");
      return 1; // return success?
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
      else if(strcmp(canDo, canDoAcceptIOChanges) == 0)                return 0;
      else if(strcmp(canDo, canDoSizeWindow) == 0)                     return 1;
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
    
    default: return 0;
  }
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_loadPlugin
  (JNIEnv *env, jobject jobj, jstring pluginPath) {

  void *libptr = NULL;
  AEffect *ae = NULL;

  // minihost.cpp
  #if _WIN32
    const char *path = (char *)(env->GetStringUTFChars(pluginPath, NULL));
    if (path == NULL) {
      env->ThrowNew(classJVstLoadException, "jstring conversion failed.");
      return;
    }
    libptr = LoadLibrary (path);
    if (libptr == NULL) {
      env->ThrowNew(classJVstLoadException, "the library could not be loaded.");
      return;
    }
    env->ReleaseStringUTFChars(pluginPath, path);
    AEffect* (*mainProc) (audioMasterCallback);
    mainProc = (AEffect* (*)(audioMasterCallback)) GetProcAddress((HMODULE) libptr, "VSTPluginMain");
    if (!mainProc) {
      mainProc = (AEffect* (*)(audioMasterCallback)) GetProcAddress((HMODULE) libptr, "main");
      if (!mainProc) {
        env->ThrowNew(classJVstLoadException, "The plugin entry function could not be found.");
        return;
      }
    }
    ae = (AEffect *) mainProc(HostCallback);
    if(ae == NULL || ae->magic != kEffectMagic) {
      FreeLibrary((HMODULE) libptr); // unload the library
      env->ThrowNew(classJVstLoadException, "The plugin could not be instantiated.");
      return;
    }

  #elif TARGET_API_MAC_CARBON
    // http://developer.apple.com/documentation/CoreFoundation/Reference/CFBundleRef/Reference/reference.html
    const char *path = (char *) (env->GetStringUTFChars(pluginPath, NULL)); // convert the java string pathname into a c char array
    if (path == NULL) {
      env->ThrowNew(classJVstLoadException, "jstring conversion failed.");
      return;
    }
    CFStringRef fileNameString = CFStringCreateWithCString(NULL, path, kCFStringEncodingUTF8);
    env->ReleaseStringUTFChars(pluginPath, path);
    if (fileNameString == NULL) {
	    env->ThrowNew(classJVstLoadException, "CFString creation failed.");
	    return;
    }
    CFURLRef url = CFURLCreateWithFileSystemPath(NULL, fileNameString, kCFURLPOSIXPathStyle, false);
    CFRelease(fileNameString);
    if (url == NULL) {
      env->ThrowNew(classJVstLoadException, "CFURLRef creation failed.");
      return;
    }
    libptr = CFBundleCreate(NULL, url);
    CFRelease (url);
    if (libptr == NULL) {
      env->ThrowNew(classJVstLoadException, "The plugin bundle does not exist.");
      return;
    }
    AEffect* (*mainProc) (audioMasterCallback);
    mainProc = (AEffect* (*)(audioMasterCallback)) CFBundleGetFunctionPointerForName((CFBundleRef) libptr, CFSTR("VSTPluginMain"));
    if (mainProc == NULL) {
      mainProc = (AEffect* (*)(audioMasterCallback)) CFBundleGetFunctionPointerForName((CFBundleRef) libptr, CFSTR("main_macho"));
      if (mainProc == NULL) {
        CFRelease((CFBundleRef)libptr);
	      env->ThrowNew(classJVstLoadException, "The plugin entry function could not be found.");
	      return;
      }
    }
    ae = (AEffect *) mainProc(HostCallback);
    if(ae == NULL || ae->magic != kEffectMagic) {
      CFRelease((CFBundleRef)libptr);
      env->ThrowNew(classJVstLoadException, "The plugin could not be instantiated.");
      return;
    }


  #else // for unix
    const char *path = (char *) env->GetStringUTFChars(pluginPath, NULL); // convert the java string pathname into a c char array
	  if (path == NULL) {
      env->ThrowNew(classJVstLoadException, "jstring conversion failed.");
      return;
    }
    libptr = dlopen(path, RTLD_LAZY); // load the library
	  if (libptr == NULL) {
		  env->ThrowNew(classJVstLoadException, "The VST library could not be loaded.");
		  return;
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
        env->ThrowNew(classJVstLoadException, "The plugin entry function could not be found.");
        return;
      } else {
        dlerror(); // clear the error field
      }
    }
    ae = (AEffect *) vstPluginFactory(HostCallback); // create the audioeffect!
    if(ae == NULL || ae->magic != kEffectMagic) {
      dlclose(libptr);
      env->ThrowNew(classJVstLoadException, "The plugin could not be instantiated.");
      return;
    }
  #endif

  ae->dispatcher (ae, effOpen, 0, 0, 0, 0); // open the plugin. Should be called, but many VSTs may not do anything with it

  // set C pointers for various constructs  
  jfieldID fid = env->GetFieldID(vpwClass, "vstLibPtr", "J");
  env->SetLongField(jobj, fid, (jlong) libptr);
  
  fid = env->GetFieldID(vpwClass, "vstPluginPtr", "J");
  env->SetLongField(jobj, fid, (jlong) ae);
  
  float **inputs = (float **)malloc(sizeof(float *) * ae->numInputs);
  fid = env->GetFieldID(vpwClass, "vstInputsPtr", "J");
  env->SetLongField(jobj, fid, (jlong) inputs);

  float **outputs = (float **)malloc(sizeof(float *) * ae->numOutputs);
  fid = env->GetFieldID(vpwClass, "vstOutputsPtr", "J");
  env->SetLongField(jobj, fid, (jlong) outputs);
  
  if (mapSize < maxMapSize) {
    envobjmap *eom = (envobjmap *) malloc(sizeof(envobjmap));
    eom->jobj = (jobject) env->NewWeakGlobalRef(jobj); // store a weak global reference to the object
    map[mapSize++] = eom;
  }
  setJNIEnv(jobj, env); // just in case some callback needs this at this time
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_unloadPlugin
  (JNIEnv *env, jobject jobj, jlong inputsPtr, jlong outputsPtr, jlong ae, jlong libPtr) {
  
  free((float **)inputsPtr);
  free((float **)outputsPtr);
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effClose, 0, 0, 0, 0);
  
  // close the reference to the library
  #if _WIN32
    FreeLibrary((HMODULE) libPtr);
  #elif TARGET_API_MAC_CARBON
    CFRelease ((CFBundleRef)libPtr);  // plugin unloads automatically when all bundle references to it are gone
  #else // unix
    dlclose((void *)libPtr);
  #endif
}

#if TARGET_API_MAC_CARBON
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
JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_openEditor
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;

  // if this plugin does not have an editor, return.
  // There is nothing to be done here.
  if((effect->flags & effFlagsHasEditor) == 0) return;

  #if _WIN32
    /*
    MyDLGTEMPLATE t;	
    t.style = WS_POPUPWINDOW | WS_DLGFRAME | DS_MODALFRAME | DS_CENTER;
    t.cx = 100;
    t.cy = 100;
    DialogBoxIndirectParam (GetModuleHandle (0), &t, 0, (DLGPROC)EditorProc, (LPARAM)effect);
    */
    
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
    env->SetLongField(jobj, fid, (jlong) window);
  #else // unix
  
  #endif
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_closeEditor
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effEditClose, 0, 0, 0, 0);

  #if _WIN32
  
  #elif TARGET_API_MAC_CARBON
    jfieldID fid = env->GetFieldID(vpwClass, "osxWindow", "J");
    WindowRef window = (WindowRef) env->GetLongField(jobj, fid);
    if(window != NULL) {
      CFRelease(window); // ReleaseWindow (window); the latter is deprecated in OS10.5
      env->SetLongField(jobj, fid, NULL);
    } else {
      // error condition. window should be non-NULL
    }
  
  #else // unix
  
  #endif
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_editIdle
  (JNIEnv *env, jobject jobj, jlong ae) {
  
  AEffect *effect = (AEffect *) ae;
  effect->dispatcher(effect, effEditIdle, 0, 0, 0, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_processReplacing
  (JNIEnv *env, jobject jobj, jobjectArray jinputs, jobjectArray joutputs, jint sampleFrames, jint numInputs, jint numOutputs, jlong inputsPtr, jlong outputsPtr, jlong ae) {

  // set the environment, such that this object can be later recovered for use by a callback
  // which often happens as a result of calling processReplacing
  setJNIEnv(jobj, env);
  
  AEffect *effect = (AEffect *)ae;
  float **cinputs = (float **)inputsPtr;
  float **coutputs = (float **)outputsPtr;
  jfloatArray *input = (jfloatArray *) malloc(sizeof(jfloatArray) * numInputs);
  jfloatArray *output = (jfloatArray *) malloc(sizeof(jfloatArray) * numOutputs);
  for(int i = 0; i < numInputs; i++) {
    input[i] = (jfloatArray) env->GetObjectArrayElement(jinputs, i);
    cinputs[i] = (float *) env->GetPrimitiveArrayCritical(input[i], NULL);
  }
  for(int i = 0; i < numOutputs; i++) {
    output[i] = (jfloatArray) env->GetObjectArrayElement(joutputs, i);
    coutputs[i] = (float *) env->GetPrimitiveArrayCritical(output[i], NULL);
  }

  effect->processReplacing(effect, cinputs, coutputs, (int)sampleFrames);

  for(int i = 0; i < numInputs; i++) {
    env->ReleasePrimitiveArrayCritical(input[i], cinputs[i], JNI_ABORT);
  }
  for(int i = 0; i < numOutputs; i++) {
    env->ReleasePrimitiveArrayCritical(output[i], coutputs[i], 0);
  }
  free(input);
  free(output);
}

/*
JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_processReplacing
  (JNIEnv *env, jobject jobj, jobjectArray jinputs, jobjectArray joutputs, jint sampleFrames, jint numInputs, jint numOutputs, jlong inputsPtr, jlong outputsPtr, jlong ae) {

  setJNIEnv(jobj, env); // set the environment, such that this object can be later recovered for use by a callback
  
  AEffect *effect = (AEffect *)ae;
  float **cinputs = (float **)inputsPtr;
  float **coutputs = (float **)outputsPtr;
  jfloatArray *input = (jfloatArray *)malloc(sizeof(jfloatArray) * numInputs);
  jfloatArray *output = (jfloatArray *)malloc(sizeof(jfloatArray) * numOutputs);
  for(int i = 0; i < numInputs; i++) {
    input[i] = (jfloatArray)(env->GetObjectArrayElement(jinputs, i));
    // consider using env->GetPrimitiveArrayCritical
    cinputs[i] = env->GetFloatArrayElements(input[i], NULL);
  }
  for(int i = 0; i < numOutputs; i++) {
    output[i] = (jfloatArray)(env->GetObjectArrayElement(joutputs, i));
    coutputs[i] = env->GetFloatArrayElements(output[i], NULL);
  }

  effect->processReplacing(effect, cinputs, coutputs, (int)sampleFrames);

  for(int i = 0; i < numInputs; i++) {
    env->ReleaseFloatArrayElements(input[i], cinputs[i], JNI_ABORT);
  }
  for(int i = 0; i < numOutputs; i++) {
    env->ReleaseFloatArrayElements(output[i], coutputs[i], 0);
  }
  free(input);
  free(output);
}
*/

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_setParameter
  (JNIEnv *env, jobject jobj, jint index, jfloat value, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  effect->setParameter(effect, index, value);
}

JNIEXPORT jfloat JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getParameter
  (JNIEnv *env, jobject jobj, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  return effect->getParameter(effect, index);
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getEffectName
  (JNIEnv *env, jobject jobj, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxEffectNameLen);
  effect->dispatcher (effect, effGetEffectName, 0, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getParameterName
  (JNIEnv *env, jobject jobj, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxParamStrLen);
  effect->dispatcher (effect, effGetParamName, index, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getVendorName
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxVendorStrLen);
  effect->dispatcher (effect, effGetVendorString, 0, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getProductString
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxProductStrLen);
  effect->dispatcher (effect, effGetProductString, 0, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_numParameters
  (JNIEnv *env, jobject jobj, jlong ae) {
  
  return ((AEffect *)ae)->numParams;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_numInputs
  (JNIEnv *env, jobject jobj, jlong ae) {
  
  return ((AEffect *)ae)->numInputs;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_numOutputs
  (JNIEnv *env, jobject jobj, jlong ae) {
  
  return ((AEffect *)ae)->numOutputs;
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_setSampleRate
  (JNIEnv *env, jobject jobj, jfloat sampleRate, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effSetSampleRate, 0, 0, 0, sampleRate);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_setBlockSize
  (JNIEnv *env, jobject jobj, jint blockSize, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effSetBlockSize, 0, blockSize, 0, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_suspend
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effMainsChanged, 0, 0, 0, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_resume
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;  
  effect->dispatcher (effect, effMainsChanged, 0, 1, 0, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_setMidiEvents
  (JNIEnv *env, jobject jobj, jobjectArray midiMessages, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  
  // set up the vst events data structures
  int numMessages = 0;
  if (midiMessages != NULL) {
    numMessages = env->GetArrayLength(midiMessages);
  }
  jobject shortMessage;
  jclass shortMessageClass = env->FindClass("javax/sound/midi/ShortMessage");
  jmethodID getCommand = env->GetMethodID(shortMessageClass, "getCommand", "()I");
  jmethodID getChannel = env->GetMethodID(shortMessageClass, "getChannel", "()I");
  jmethodID getData1 = env->GetMethodID(shortMessageClass, "getData1", "()I");
  jmethodID getData2 = env->GetMethodID(shortMessageClass, "getData2", "()I");
  VstEvents *vstes;
  if(numMessages <= 2) {
    vstes = (VstEvents *) malloc(sizeof(VstEvent));
  } else {
    vstes = (VstEvents *) malloc(sizeof(VstEvents) + (numMessages-2)*sizeof(VstEvent *));
  }
  vstes->numEvents = numMessages;
  vstes->reserved = NULL;
  VstMidiEvent *vstme;
  for(int i = 0; i < numMessages; i++) {
    shortMessage = env->GetObjectArrayElement(midiMessages, i);
    
    vstme = (VstMidiEvent *) malloc(sizeof(VstMidiEvent));
    vstme->type = kVstMidiType;             //< #kVstMidiType
    vstme->byteSize = sizeof(VstMidiEvent); //< sizeof (VstMidiEvent)
    vstme->deltaFrames = 0;                 //< sample frames related to the current block start sample position
    vstme->flags = 0;                       //< @see VstMidiEventFlags
    vstme->noteLength = 0;                  //< (in sample frames) of entire note, if available, else 0
    vstme->noteOffset = 0;                  //< offset (in sample frames) into note from note start if available, else 0
    vstme->midiData[0]  = (char) (env->CallIntMethod(shortMessage, getCommand)) & 0xF0;
    vstme->midiData[0] |= (char) (env->CallIntMethod(shortMessage, getChannel)) & 0x0F;
    vstme->midiData[1]  = (char) (env->CallIntMethod(shortMessage, getData1)) & 0x7F; // the note
    vstme->midiData[2]  = (char) (env->CallIntMethod(shortMessage, getData2)) & 0x7F; // velocity
    vstme->midiData[3]  = (char) 0;
    vstme->detune = 0;                      //< -64 to +63 cents; for scales other than 'well-tempered' ('microtuning')
    vstme->noteOffVelocity = 0;             //< Note Off Velocity [0, 127]
    vstme->reserved1 = 0;                   //< zero (Reserved for future use)
    vstme->reserved2 = 0;                   //< zero (Reserved for future use)
    
    vstes->events[i] = (VstEvent *)vstme;
  }
  
  // send the events to the vst
  effect->dispatcher (effect, effProcessEvents, 0, 0, vstes, 0);
  
  // free the data structures
  for(int i = 0; i < numMessages; i++) {
    free(vstes->events[i]);
  }
  free(vstes);
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getProgramName
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxProgNameLen);
  effect->dispatcher (effect, effGetProgramName, 0, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

/*
 * There is a problem with this function in that the plugin will copy kVstMaxProgNameLen chars
 * from the given pointer. But the java function will not necessarily return a pointer to an array
 * that long. This would cause the plugin to copy too much, which is dangerous
 */
JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_setProgramName
  (JNIEnv *env, jobject jobj, jstring jname, jlong ae) {

  return;
/*
  AEffect *effect = (AEffect *)ae;
  const char *name = (char *)(env->GetStringUTFChars(jname, NULL));
  effect->dispatcher (effect, effSetProgramName, 0, 0, name, 0);
  env->ReleaseStringUTFChars(jname, name);
*/
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getProgram
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  return effect->dispatcher (effect, effGetProgram, 0, 0, 0, 0);
}


JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_setProgram
  (JNIEnv *env, jobject jobj, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effSetProgram, 0, index, 0, 0);
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getParameterDisplay
  (JNIEnv *env, jobject jobj, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxParamStrLen);
  effect->dispatcher (effect, effGetParamDisplay, index, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT jstring JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getParameterLabel
  (JNIEnv *env, jobject jobj, jint index, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  char *name = (char *)malloc(sizeof(char) * kVstMaxParamStrLen);
  effect->dispatcher (effect, effGetParamLabel, index, 0, name, 0);
  jstring jname = env->NewStringUTF(name);
  free(name);
  return jname;
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_editOpen
  (JNIEnv *env, jobject jobj, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effEditOpen, 0, 0, 0, 0); // may need a pointer here?
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_editClose
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  effect->dispatcher (effect, effEditClose, 0, 0, 0, 0);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_hasEditor
  (JNIEnv *env, jobject jobj, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsHasEditor);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_canReplacing
  (JNIEnv *env, jobject jobj, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsCanReplacing);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_acceptsProgramsAsChunks
  (JNIEnv *env, jobject jobj, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsProgramChunks);
}

JNIEXPORT jbyteArray JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getChunk
  (JNIEnv *env, jobject jobj, jint jBankOrProgram, jlong ae) {
 
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


JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_setChunk
  (JNIEnv *env, jobject jobj, jint jBankOrProgram, jbyteArray jChunkData, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  char *chunkData = (char *) env->GetByteArrayElements(jChunkData, NULL);
  int lenJChunkData = (int) env->GetArrayLength(jChunkData);
  effect->dispatcher(effect, effSetChunk, (int) jBankOrProgram, lenJChunkData, chunkData, 0);
  env->ReleaseByteArrayElements(jChunkData, (jbyte *) chunkData, JNI_ABORT);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_isSynth
  (JNIEnv *env, jobject jobj, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsIsSynth);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_canDoBypass
  (JNIEnv *env, jobject jobj, jlong ae) {
  
  using namespace PlugCanDos;
  AEffect *effect = (AEffect *)ae;
  return (jint) effect->dispatcher(effect, effCanDo, 0, 0, (void *) canDoBypass, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_setBypass
  (JNIEnv *env, jobject jobj, jboolean bypass, jlong ae) {
  
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher(effect, effSetBypass, 0, bypass == JNI_TRUE, 0, 0);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_producesSoundInStop
  (JNIEnv *env, jobject jobj, jlong ae) {

  return (((AEffect *)ae)->flags & effFlagsNoSoundInStop);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getUniqueId
  (JNIEnv *env, jobject jobj, jlong ae) {

  return (jint) ((AEffect *)ae)->uniqueID;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getVersion
  (JNIEnv *env, jobject jobj, jlong ae) {
 
  return  (jint) ((AEffect *)ae)->version;
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getVstVersion
  (JNIEnv *env, jobject jobj, jlong ae) {

  AEffect *effect = (AEffect *)ae;
  return effect->dispatcher(effect, effGetVstVersion, 0, 0, 0, 0);
}

JNIEXPORT jint JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_getTailSize
  (JNIEnv *env, jobject jobj, jlong ae) {
 
  AEffect *effect = (AEffect *)ae;
  return effect->dispatcher(effect, effGetTailSize, 0, 0, 0, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_startProcess
  (JNIEnv *env, jobject jobj, jlong ae) {
 
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher(effect, effStartProcess, 0, 0, 0, 0);
}

JNIEXPORT void JNICALL Java_com_synthbot_audioplugin_vst_JVstHost_stopProcess
  (JNIEnv *env, jobject jobj, jlong ae) {
 
  AEffect *effect = (AEffect *)ae;
  effect->dispatcher(effect, effStopProcess, 0, 0, 0, 0);
}
