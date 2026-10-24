#!/bin/bash
set -e
source /scratch/work/env.sh

APP=/scratch/work/app
B=/scratch/work/build
rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/dex" "$B/apk"

echo "[1/6] aapt2 compile"
aapt2 compile --dir "$APP/res" -o "$B/res.zip"

echo "[2/6] aapt2 link"
aapt2 link \
  -o "$B/apk/base.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  "$B/res.zip" \
  --java "$B/gen" \
  --min-sdk-version 28 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 1.0

echo "[3/6] javac"
find "$APP/src" -name '*.java' > "$B/sources.txt"
find "$B/gen" -name '*.java' >> "$B/sources.txt"
javac -source 8 -target 8 \
  -bootclasspath "$PLATFORM" \
  -classpath "$PLATFORM" \
  -d "$B/classes" \
  @"$B/sources.txt" 2> "$B/javac.log" || { echo "--- javac failed ---"; cat "$B/javac.log"; exit 1; }
echo "javac ok"

echo "[4/6] d8"
find "$B/classes" -name '*.class' > "$B/classes.txt"
d8 --min-api 28 --lib "$PLATFORM" --output "$B/dex" @"$B/classes.txt" 2> "$B/d8.log" || { echo "--- d8 failed ---"; cat "$B/d8.log"; exit 1; }
echo "d8 ok"

echo "[5/6] package"
cp "$B/apk/base.apk" "$B/apk/app.apk"
( cd "$B/dex" && zip -q -j "$B/apk/app.apk" classes*.dex )
zipalign -f -p 4 "$B/apk/app.apk" "$B/apk/app-aligned.apk"

echo "[6/6] sign"
if [ ! -f /scratch/work/ks.jks ]; then
  keytool -genkeypair -keystore /scratch/work/ks.jks -alias key \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass openscribe -keypass openscribe \
    -dname "CN=OpenScribe, O=Open Source, C=IN" >/dev/null 2>&1
fi
apksigner sign \
  --ks /scratch/work/ks.jks --ks-pass pass:openscribe --key-pass pass:openscribe \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$B/apk/OpenScribe-1.0.apk" "$B/apk/app-aligned.apk"

echo "DONE"
ls -la "$B/apk"
