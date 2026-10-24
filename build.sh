#!/bin/bash
# OpenScribe build — no Gradle. Android SDK build-tools + NDK-built libwhisper.
#
# Requires in the environment:
#   JAVA_HOME   JDK 17
#   BT          Android build-tools dir (aapt2, d8, zipalign, apksigner)
#   PLATFORM    android.jar for API 34
# Also needs (built separately, see build_native.sh):
#   /scratch/work/libs/so/<abi>/*.so   (whisper + ggml + libc++_shared + jni)
#   /scratch/work/modelstage/assets/whisper/ggml-small-q8_0.bin
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
APP="$HERE"
LIBS=/scratch/work/libs
MODELDIR=/scratch/work/modelstage
B=/scratch/work/build
VNAME=2.0
VCODE=3
OUT="OpenScribe-$VNAME.apk"

rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/dex" "$B/apk" "$B/lib/arm64-v8a" "$B/lib/armeabi-v7a"

echo "[1/8] aapt2 compile"
aapt2 compile --dir "$APP/res" -o "$B/res.zip"

echo "[2/8] aapt2 link"
aapt2 link \
  -o "$B/apk/base.apk" -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" "$B/res.zip" \
  --java "$B/gen" \
  --min-sdk-version 28 --target-sdk-version 34 \
  --version-code $VCODE --version-name $VNAME

echo "[3/8] javac"
find "$APP/src" -name '*.java' > "$B/sources.txt"
find "$B/gen" -name '*.java' >> "$B/sources.txt"
javac -source 8 -target 8 -bootclasspath "$PLATFORM" -classpath "$PLATFORM" \
  -d "$B/classes" @"$B/sources.txt" 2> "$B/javac.log" || { echo "--- javac failed ---"; cat "$B/javac.log"; exit 1; }
echo "javac ok"

echo "[4/8] d8"
jar cf "$B/app-classes.jar" -C "$B/classes" .
# Call D8 directly: the d8 wrapper forces -Xmx2G, which exceeds this sandbox's
# 1.5 GB address-space cap.
java -XX:-UseCompressedClassPointers -XX:-UseCompressedOops \
     -Xmx160m -Xms16m -XX:MaxMetaspaceSize=80m -XX:ReservedCodeCacheSize=24m \
     -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -Xss1m \
     -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
     --min-api 28 --lib "$PLATFORM" --output "$B/dex" "$B/app-classes.jar" \
  2> "$B/d8.log" || { echo "--- d8 failed ---"; tail -30 "$B/d8.log"; exit 1; }
echo "d8 ok: $(ls "$B/dex")"

echo "[5/8] package dex + native libs"
cp "$B/apk/base.apk" "$B/apk/app.apk"
( cd "$B/dex" && zip -q -j "$B/apk/app.apk" classes*.dex )
cp "$LIBS"/so/arm64-v8a/*.so "$B/lib/arm64-v8a/"
cp "$LIBS"/so/armeabi-v7a/*.so "$B/lib/armeabi-v7a/"
( cd "$B" && zip -q -r "$B/apk/app.apk" lib )

echo "[6/8] bundle model asset"
( cd "$MODELDIR" && zip -q "$B/apk/app.apk" assets/whisper/ggml-small-q8_0.bin )

echo "[7/8] align + sign"
zipalign -f -p 4 "$B/apk/app.apk" "$B/apk/app-aligned.apk"
if [ ! -f "$HERE/ks.jks" ]; then
  keytool -genkeypair -keystore "$HERE/ks.jks" -alias key \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass openscribe -keypass openscribe \
    -dname "CN=OpenScribe, O=Open Source, C=IN" >/dev/null 2>&1
fi
apksigner sign \
  --ks "$HERE/ks.jks" --ks-pass pass:openscribe --key-pass pass:openscribe \
  --min-sdk-version 28 --v2-signing-enabled true --v3-signing-enabled true \
  --out "$B/apk/$OUT" "$B/apk/app-aligned.apk"

echo "[8/8] done"
ls -la "$B/apk/$OUT"
