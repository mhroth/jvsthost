# check envt 
echo checking environment
if [ -z "$JAVA_HOME" ]
then
  echo "Environment variable JAVA_HOME is not set. It should be set to the root directory of your Java SDK install."
  exit
fi

# compile
echo compiling the library
gcc -o libjvsthost.so -fPIC -shared -Wl,-soname,libjvsthost.so -03 -lc -lstdc++ \
-I.//vst2.x \
-I$JAVA_HOME/include \
-I$JAVA_HOME/include/linux \
-I/usr/include \
./vst2.x/audioeffect.cpp \
JVstHost.cpp

# copy somewhere useful
echo copying library to /usr/local/lib/
cp libjvsthost.so /usr/local/lib/
