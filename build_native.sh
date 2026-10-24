#!/bin/bash
# Builds libwhisper.so + ggml + the JNI bridge (libwhisperjni.so) for
# arm64-v8a and armeabi-v7a, and stages them under libs/so/<abi>/.
#
# Requires: ANDROID_NDK (r26+), cmake, ninja, git.
set -e
: "${ANDROID_NDK:?set ANDROID_NDK to your NDK path}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WS="${WHISPER_SRC:-$HERE/.whisper.cpp}"
TC="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64"

if [ ! -d "$WS" ]; then
  git clone --depth 1 --branch v1.7.4 https://github.com/ggerganov/whisper.cpp "$WS"
fi

for ABI in arm64-v8a armeabi-v7a; do
  echo "=== $ABI ==="
  cmake -S "$WS" -B "$HERE/nb/$ABI" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-28 \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
    -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=OFF -DWHISPER_BUILD_SERVER=OFF \
    -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF
  cmake --build "$HERE/nb/$ABI" --target whisper -j

  OUT="$HERE/libs/so/$ABI"
  mkdir -p "$OUT"
  cp "$HERE/nb/$ABI/src/libwhisper.so" "$OUT/"
  cp "$HERE/nb/$ABI"/ggml/src/libggml.so "$OUT/"
  cp "$HERE/nb/$ABI"/ggml/src/libggml-base.so "$OUT/"
  cp "$HERE/nb/$ABI"/ggml/src/libggml-cpu.so "$OUT/"

  case "$ABI" in
    arm64-v8a)       PREFIX=aarch64-linux-android28;  CPP="$TC/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" ;;
    armeabi-v7a)     PREFIX=armv7a-linux-androideabi28; CPP="$TC/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so" ;;
  esac
  "$TC/bin/${PREFIX}-clang++" -shared -fPIC -O3 -std=c++17 \
    -I"$WS/include" -I"$WS/ggml/include" "$HERE/jni/whisper_jni.cpp" \
    -L"$HERE/nb/$ABI/src" -lwhisper -Wl,--allow-shlib-undefined \
    -o "$OUT/libwhisperjni.so"
  cp "$CPP" "$OUT/"
done
echo "native libs ready under libs/so/"
