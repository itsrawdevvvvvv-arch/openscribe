#!/bin/bash
# OpenScribe build — no Gradle. Uses the Android SDK build-tools directly:
#   aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner
#
# Requires in the environment:
#   JAVA_HOME   JDK 17
#   BT          Android build-tools dir (e.g. .../build-tools/34.0.0)
#   PLATFORM    android.jar for API 34 (e.g. .../platforms/android-34/android.jar)
#   D8          (optional) d8 from build-tools 35+; older d8 chokes on JNA classes
#
# Run ./fetch_libs.sh and ./fetch_model.sh once before the first build.
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
APP="$HERE"
LIBS="$HERE/libs"
B="$HERE/build"
D8="${D8:-$BT/d8}"
CP="$PLATFORM:$LIBS/vosk-clean.jar:$LIBS/jna-clean.jar"
VNAME=1.1
VCODE=2
OUT="OpenScribe-$VNAME.apk"

rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/dex" "$B/apk" "$B/lib/arm64-v8a" "$B/lib/armeabi-v7a"

echo "[1/7] aapt2 compile"
aapt2 compile --dir "$APP/res" -o "$B/res.zip"

echo "[2/7] aapt2 link (with assets)"
aapt2 link \
  -o "$B/apk/base.apk" \
  -I "$PLATFORM" \
  -A "$APP/assets" \
  --manifest "$APP/AndroidManifest.xml" \
  "$B/res.zip" \
  --java "$B/gen" \
  --min-sdk-version 28 \
  --target-sdk-version 34 \
  --version-code $VCODE \
  --version-name $VNAME

echo "[3/7] javac"
find "$APP/src" -name '*.java' > "$B/sources.txt"
find "$B/gen" -name '*.java' >> "$B/sources.txt"
javac -source 8 -target 8 \
  -bootclasspath "$PLATFORM" \
  -classpath "$CP" \
  -d "$B/classes" \
  @"$B/sources.txt" 2> "$B/javac.log" || { echo "--- javac failed ---"; cat "$B/javac.log"; exit 1; }
echo "javac ok"

echo "[4/7] d8"
jar cf "$B/app-classes.jar" -C "$B/classes" .
"$D8" --min-api 28 --lib "$PLATFORM" --output "$B/dex" \
  "$B/app-classes.jar" "$LIBS/vosk-clean.jar" "$LIBS/jna-clean.jar" \
  2> "$B/d8.log" || { echo "--- d8 failed ---"; tail -40 "$B/d8.log"; exit 1; }
echo "d8 ok: $(ls "$B/dex")"

echo "[5/7] package"
cp "$B/apk/base.apk" "$B/apk/app.apk"
( cd "$B/dex" && zip -q -j "$B/apk/app.apk" classes*.dex )
cp "$LIBS"/so/arm64-v8a/*.so "$B/lib/arm64-v8a/"
cp "$LIBS"/so/armeabi-v7a/*.so "$B/lib/armeabi-v7a/"
( cd "$B" && zip -q -r "$B/apk/app.apk" lib )
zipalign -f -p 4 "$B/apk/app.apk" "$B/app-aligned.apk"

echo "[6/7] sign"
if [ ! -f "$HERE/ks.jks" ]; then
  keytool -genkeypair -keystore "$HERE/ks.jks" -alias key \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass openscribe -keypass openscribe \
    -dname "CN=OpenScribe, O=Open Source, C=IN" >/dev/null 2>&1
fi
apksigner sign \
  --ks "$HERE/ks.jks" --ks-pass pass:openscribe --key-pass pass:openscribe \
  --min-sdk-version 28 --v2-signing-enabled true --v3-signing-enabled true \
  --out "$B/apk/$OUT" "$B/app-aligned.apk"

echo "[7/7] done"
ls -la "$B/apk/$OUT"
