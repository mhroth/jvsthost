gcc -mno-cygwin -D__int64="long long" -o jvsthost2.dll -shared -O3 -w -Wl,--add-stdcall-alias \
-I. \
-I/cygdrive/c/Java/jdk1.6.0_10/include \
-I/cygdrive/c/Java/jdk1.6.0_10/include/win32 \
-I./vst2.x \
-I/usr/include \
JVstHost.cpp \
./vst2.x/audioeffect.cpp \
-lstdc++

ls -l jvsthost2.dll
echo copying jvsthost2.dll to C:/WINDOWS/system32
cp jvsthost2.dll /cygdrive/c/WINDOWS/system32
mv jvsthost2.dll ../../../../../../
