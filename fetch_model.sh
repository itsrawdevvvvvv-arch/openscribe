#!/bin/bash
# Downloads the Whisper model into modelstage/assets/whisper so build.sh can
# bundle it inside the APK. The model is kept out of git to keep the repo small.
#
# Default: Whisper "small", q8_0 quantised (~253 MB) — multilingual (Hindi + English).
# Swap MODEL for any whisper.cpp ggml file, e.g. ggml-small.bin (full precision),
# ggml-medium-q5_0.bin (more accurate, larger), ggml-base.bin (smaller, faster).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
MODEL=ggml-small-q8_0.bin
DEST="$HERE/modelstage/assets/whisper"
mkdir -p "$DEST"
cd "$DEST"
curl -L -o "$MODEL" "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$MODEL"
echo "model ready at $DEST/$MODEL"
