export LD_LIBRARY_PATH=.

# -O3 optimise!
# -g3 -ggdb3 debug!
gcc -framework CoreFoundation -framework Carbon -o libjvsthost.jnilib -fPIC -dynamiclib -O3  \
-I./ \
-I./vst2.x \
-I/Developer/SDKs/MacOSX10.5.sdk/System/Library/Frameworks/JavaVM.framework/Versions/1.5.0/Headers \
-I/Developer/SDKs/MacOSX10.5.sdk/Developer/Headers/FlatCarbon \
-I/Developer/SDKs/MacOSX10.5.sdk/Developer/Headers/CFMCarbon/CoreFoundation \
-I/Developer/SDKs/MacOSX10.5.sdk/Developer/Headers/CFMCarbon/Carbon \
-L/Developer/SDKs/MacOSX10.5.sdk/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Libraries \
./vst2.x/audioeffect.cpp \
JVstHost.cpp \
-lc -lstdc++ -lm

cp libjvsthost.jnilib /Library/Java/Extensions/libjvsthost.jnilib
ls -l ./libjvsthost.jnilib
