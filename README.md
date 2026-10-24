# OpenScribe

**An open-source, Wispr Flow–style dictation app for Android.** Speak and watch text
appear live, or drop in an audio file and get a transcript plus a ready-to-use SRT
subtitle file. Minimal monochrome UI, smooth animations, Android 9 (API 28) and up.

> Not affiliated with Wispr Flow. Built from scratch as a free, auditable alternative.

---

## Features

- **Live dictation** — a big tap-to-record button, a reactive waveform, a live timer
  and a transcript that updates word-by-word as you speak. Uses the phone's own
  speech recogniser, so audio stays on-device when you enable "prefer offline".
- **Audio → transcript + SRT** — pick any audio file and get the full transcript and
  a timestamped `.srt` file you can save or share. Works with any OpenAI-compatible
  `/audio/transcriptions` endpoint (OpenAI, Groq, a self-hosted Whisper server, …).
- **Minimal, animated UI** — light card-based layout with outline cards, rounded
  corners, staggered entrance animations and a hand-drawn waveform/pulse.
- **Tiny and dependency-free** — no AndroidX, no third-party SDKs. ~50 KB APK.

## Install

Download the latest `OpenScribe-1.0.apk` from the **Releases** page and open it on your
phone (you may need to allow installs from unknown sources).

- Requires Android 9.0 (API 28) or newer.
- Live dictation needs the microphone permission and a speech recognition service
  (present on most devices; if the Google app is disabled you may need to enable it).

## Using file transcription

Open **Settings** and fill in:

| Field | Example |
|-------|---------|
| Base URL | `https://api.openai.com/v1` |
| API key | your key (`sk-…`) |
| Model | `whisper-1` |

Your key is stored only in the app's private preferences on the device. Requests go
straight from the phone to the endpoint you configure — there is no middleman server.

## Build from source

No Gradle required. The project builds with the Android SDK build-tools directly
(`aapt2` → `javac` → `d8` → `zipalign` → `apksigner`).

```bash
# Requires: JDK 17, Android SDK build-tools 34, android.jar for API 34
export JAVA_HOME=/path/to/jdk17
export BT=/path/to/build-tools/34.0.0
export PLATFORM=/path/to/platforms/android-34/android.jar
export PATH=$JAVA_HOME/bin:$BT:$PATH

bash build.sh          # produces build/apk/OpenScribe-1.0.apk
```

## Project layout

```
AndroidManifest.xml
res/                     layouts, drawables, vectors, theme
src/com/openscribe/app/
  MainActivity.java      home screen
  LiveActivity.java      live dictation (SpeechRecognizer)
  FileActivity.java      audio file -> transcript + SRT
  SettingsActivity.java  engine + API configuration
  WaveformView.java      animated waveform
  PulseRingView.java     expanding record-button rings
  ApiClient.java         multipart upload to /audio/transcriptions
  SrtBuilder.java        SRT generation from segments
  Prefs.java             settings storage
build.sh                 manual APK build script
```

## Privacy

- Live dictation uses the device recogniser; enable *Prefer on-device recognition* to
  keep audio local.
- File transcription sends the chosen audio to the endpoint **you** configure.
- No analytics, no telemetry, no accounts.

## License

MIT. See `LICENSE`.
