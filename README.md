# OpenScribe

**A fully on-device, open-source dictation app for Android** — a local take on the
Wispr Flow experience. Speak and watch text appear live, or pick an audio file and
get a transcript plus a ready-to-use SRT subtitle file.

**No internet. No account. No API key. The speech model ships inside the app.**

> Not affiliated with Wispr Flow.

---

## Features

- **Live dictation** — tap the record button and text streams in word-by-word, with a
  reactive waveform and a running timer. Captured from the mic and recognised locally.
- **Audio → transcript + SRT** — pick any audio file; it is decoded on the device and
  transcribed locally, producing a transcript and timestamped `.srt` cues you can copy,
  save, or share.
- **100% offline** — the only permission requested is `RECORD_AUDIO`. There is no
  `INTERNET` permission at all.
- **Minimal animated UI** — light canvas, outline cards, big rounded corners, monospace
  numerals, staggered entrances, and custom waveform / pulse views.

## How it runs locally

Speech recognition is done by [Vosk](https://alphacephei.com/vosk/) (Apache-2.0),
running on-device via `libvosk.so`. The model (`vosk-model-small-en-in-0.4`, Indian
English, ~40 MB) is bundled in the APK under `assets/model/` and unpacked to private
storage on first launch. Change the model by editing `fetch_model.sh`.

## Install

Grab the latest `OpenScribe-*.apk` from **Releases** and open it on your phone (allow
installs from unknown sources if prompted).

- Requires Android 9.0 (API 28) or newer.
- ABIs: `arm64-v8a`, `armeabi-v7a`.

## Build from source

No Gradle. The project builds with the Android SDK build-tools directly
(`aapt2 → javac → d8 → zipalign → apksigner`).

```bash
./fetch_libs.sh     # pulls the Vosk + JNA classes and native libs into libs/
./fetch_model.sh    # downloads the speech model into app/assets/model

export JAVA_HOME=/path/to/jdk17
export BT=/path/to/build-tools/34.0.0
export PLATFORM=/path/to/platforms/android-34/android.jar
export D8=/path/to/build-tools/35.0.0/d8   # d8 from build-tools 35+ (older d8 fails on JNA)

./build.sh          # -> build/apk/OpenScribe-1.1.apk
```

> `D8`: build-tools 34's d8 crashes on a JNA class; build-tools 35/36/37 work.

## Project layout

```
app/
  AndroidManifest.xml
  assets/model/            bundled speech model (fetched, not in git)
  res/                     layouts, drawables, vectors, theme
  src/com/openscribe/app/
    MainActivity.java      home screen
    LiveActivity.java      live dictation (AudioRecord + Vosk)
    FileActivity.java      audio file -> transcript + SRT
    VoskEngine.java        model unpack + decoding + recognition
    SrtBuilder.java        SRT generation from word timings
    WaveformView.java      animated waveform
    PulseRingView.java     record-button pulse rings
fetch_libs.sh              fetch Vosk/JNA classes + native libs
fetch_model.sh             fetch the speech model
build.sh                   manual APK build
```

## Privacy

Everything happens on the device. No network calls, no telemetry, no accounts.

## License

MIT. See `LICENSE`. Vosk is Apache-2.0; the model ships under its own terms.
