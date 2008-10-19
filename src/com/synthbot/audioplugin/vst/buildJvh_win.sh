gcc -mno-cygwin -D__int64="long long" -o jvsthost.dll -shared -O3 -w  -Wl,--add-stdcall-alias \
-I. \
-I/cygdrive/c/Java/jdk1.6.0_10/include \
-I/cygdrive/c/Java/jdk1.6.0_10/include/win32 \
-I./vst2.x \
-I/usr/include \
JVstHost.cpp \
./vst2.x/audioeffect.cpp \
-lstdc++ 

ls -l jvsthost.dll
echo copying jvsthost.dll to C:/WINDOWS/system32
cp jvsthost.dll /cygdrive/c/WINDOWS/system32
