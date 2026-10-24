# OpenScribe

**A fully on-device, open-source dictation app for Android** — a local take on the
Wispr Flow experience, powered by **OpenAI Whisper**. Speak and watch text appear, or
pick an audio file and get a transcript plus a ready-to-use SRT subtitle file.

**No internet. No account. No API key. The Whisper model ships inside the app.**

> Not affiliated with Wispr Flow or OpenAI.

---

## Features

- **Live dictation** — tap record and speak; the audio is transcribed in short chunks on
  the device and the text appears as each phrase is processed, with a reactive waveform
  and a running timer.
- **Audio → transcript + SRT** — pick any audio or video file; it is decoded and
  transcribed locally, producing a transcript and timestamped `.srt` cues you can copy,
  save, or share.
- **Hindi / English / auto** — a language selector on the home screen. Whisper is
  multilingual; "auto" detects the language.
- **100% offline** — the only permission requested is `RECORD_AUDIO`. There is no
  `INTERNET` permission at all.
- **Minimal animated UI** — light canvas, outline cards, big rounded corners, monospace
  numerals, staggered entrances, and custom waveform / pulse views.

## Engine

Speech recognition runs **Whisper** locally via [whisper.cpp](https://github.com/ggerganov/whisper.cpp)
(MIT), compiled for Android with the NDK and called through a small JNI bridge
(`jni/whisper_jni.cpp`). The default model is **`ggml-small-q8_0`** — Whisper "small",
q8_0-quantised (~253 MB), multilingual. It is bundled in the APK under
`assets/whisper/` and unpacked to private storage on first launch.

Swap the model by editing `MODEL` in `fetch_model.sh` (e.g. `ggml-base.bin` for a
smaller/faster build, `ggml-medium-q5_0.bin` for higher accuracy).

## Install

Grab the latest `OpenScribe-*.apk` from **Releases** and open it on your phone (allow
installs from unknown sources if prompted).

- Requires Android 9.0 (API 28) or newer.
- ABIs: `arm64-v8a`, `armeabi-v7a`.
- First launch unpacks the model (~250 MB, a few seconds); after that it is instant.
- Whisper "small" is accurate but needs a reasonably capable phone. On low-end devices
  expect transcription to take a few seconds per phrase.

## Build from source

No Gradle. Two stages.

```bash
# 1. native: build libwhisper + ggml + the JNI bridge
export ANDROID_NDK=/path/to/android-ndk-r26d
./build_native.sh          # -> libs/so/<abi>/*.so

# 2. fetch the model
./fetch_model.sh           # -> modelstage/assets/whisper/ggml-small-q8_0.bin

# 3. build the APK
export JAVA_HOME=/path/to/jdk17
export BT=/path/to/build-tools/34.0.0
export PLATFORM=/path/to/platforms/android-34/android.jar
./build.sh                 # -> build/apk/OpenScribe-2.0.apk
```

> The `d8` and `apksigner` wrappers in build-tools default to a large `-Xmx`; on a
> memory-constrained machine, invoke their main classes directly with a smaller heap.

## Project layout

```
AndroidManifest.xml
res/                     layouts, drawables, vectors, theme
src/com/openscribe/app/
  MainActivity.java      home + language selector
  LiveActivity.java      live dictation (AudioRecord + Whisper chunking)
  FileActivity.java      audio file -> transcript + SRT
  WhisperEngine.java     JNI load, model unpack, decode + resample, transcribe
  SrtBuilder.java        SRT from Whisper segments
  Prefs.java             spoken-language preference
  WaveformView.java      animated waveform
  PulseRingView.java     record-button pulse rings
jni/whisper_jni.cpp      JNI bridge to whisper.cpp
build_native.sh          builds the native libs (NDK)
fetch_model.sh           downloads the Whisper model
build.sh                 builds the APK
```

## Privacy

Everything happens on the device. No network calls, no telemetry, no accounts.

## License

MIT. See `LICENSE`. whisper.cpp is MIT; Whisper weights are released by OpenAI under
the MIT licence.
