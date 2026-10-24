#!/bin/bash
# Downloads the Vosk Android AAR and JNA, then extracts the Java classes and the
# native libraries for arm64-v8a and armeabi-v7a. Run once before building.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
LIBS="$HERE/libs"
mkdir -p "$LIBS/so/arm64-v8a" "$LIBS/so/armeabi-v7a"
cd "$LIBS"

curl -sL -o vosk.aar "https://repo1.maven.org/maven2/com/alphacephei/vosk-android/0.3.75/vosk-android-0.3.75.aar"
curl -sL -o jna.aar  "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.18.1/jna-5.18.1.aar"

rm -rf vx jx && mkdir vx jx
( cd vx && unzip -q -o ../vosk.aar classes.jar jni/arm64-v8a/libvosk.so jni/armeabi-v7a/libvosk.so )
( cd jx && unzip -q -o ../jna.aar  classes.jar jni/arm64-v8a/libjnidispatch.so jni/armeabi-v7a/libjnidispatch.so )
cp vx/classes.jar vosk-classes.jar
cp jx/classes.jar jna-classes.jar

# Strip module-info / META-INF so d8 can parse the jars
for j in vosk jna; do
  rm -rf "c_$j" && mkdir "c_$j"
  ( cd "c_$j" && unzip -q -o "../${j}-classes.jar" )
  rm -rf "c_$j/META-INF"
  find "c_$j" -name module-info.class -delete
  ( cd "c_$j" && jar cf "../${j}-clean.jar" . )
done

cp vx/jni/arm64-v8a/libvosk.so        so/arm64-v8a/
cp vx/jni/armeabi-v7a/libvosk.so      so/armeabi-v7a/
cp jx/jni/arm64-v8a/libjnidispatch.so so/arm64-v8a/
cp jx/jni/armeabi-v7a/libjnidispatch.so so/armeabi-v7a/

echo "libs ready at $LIBS"
