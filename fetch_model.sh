#!/bin/bash
# Downloads the speech model into app/assets/model. The model is bundled inside
# the APK (so the app works fully offline) but is kept out of git to keep the
# repository small. Run once before building.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
MODEL=vosk-model-small-en-in-0.4   # Indian English; swap for another Vosk model if you like
mkdir -p "$HERE/app/assets"
cd "$HERE/app/assets"

curl -L -o model.zip "https://alphacephei.com/vosk/models/$MODEL.zip"
rm -rf model
unzip -q model.zip
mv "$MODEL" model
rm -f model.zip
echo "model ready at app/assets/model"
