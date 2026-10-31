<div align="center">

# 🗣️ Parley

An open-source face-to-face translation app for Android.

[![Latest release](https://img.shields.io/github/v/release/EvATive7/Parley.svg?label=latest)](https://github.com/EvATive7/Parley/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?logo=android&logoColor=white)

**English** | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

[**Download APK**](https://github.com/EvATive7/Parley/releases/latest)

</div>

---

Hold to speak and release to translate. The other person does the same, and each side sees the translation. Parley is designed for situations that need immediate communication, such as asking for directions abroad or joining multilingual meetings.

<div align="center">
<table>
<tr>
<td><img src="docs/screenshots/translate-zh-ja.jpg" width="240" alt="Chinese–Japanese translation"></td>
<td><img src="docs/screenshots/recording.jpg" width="240" alt="Recording"></td>
<td><img src="docs/screenshots/translate-zh-en.jpg" width="240" alt="Chinese–English translation"></td>
</tr>
<tr>
<td align="center">Translation result</td>
<td align="center">Hold to record</td>
<td align="center">Bilingual prompts</td>
</tr>
</table>

<img src="docs/screenshots/face-to-face.jpg" width="600" alt="Face-to-face mode">

*Face-to-face mode: the screen is split in two, with each side facing one person.*

</div>

## ✨ Features

- **Automatic translation direction**: choose two languages once; speaking either language automatically translates into the other.
- **Streaming output**: translations appear word by word without waiting for the whole sentence.
- **Face-to-face mode**: place the phone flat on a table. The screen splits in two, with each side facing one person and showing the other person’s language.
- **Hold to talk**: hold to record, release to send, or slide up to cancel.
- **Bilingual prompts**: button instructions appear in both languages so each person can understand them.
- **Card actions**: tap a card to view the full text, copy it, or delete it; failed translations can be retried.
- **Direct end-to-end connections**: there is no intermediary server. Audio and text go directly to the providers you configure, and API keys are encrypted with Android Keystore.

## 📦 Getting started

1. Download and install the APK from [Releases](https://github.com/EvATive7/Parley/releases/latest) (Android 8.0 or later is required).
2. Open Settings using the gear icon in the upper-right corner, then configure a speech recognition service and an OpenAI-compatible translation service.
3. Choose the two languages, hold the button, and speak.

> Parley itself is free and does not provide accounts or usage credits. Your chosen providers charge for speech recognition and translation.

## 🎙️ Speech recognition services

| Provider | Service | Required configuration |
| --- | --- | --- |
| Alibaba Cloud DashScope | Fun-ASR-RealTime (streaming / non-streaming) | API key and region (Beijing or Singapore) |
| OpenAI | GPT Realtime Whisper, GPT-4o Transcribe, GPT-4o Mini Transcribe | API key |
| Volcengine | Doubao Seed ASR 2.0 (streaming / non-streaming / non-streaming turbo) | AppID and Access Token; the corresponding resource must be enabled in the console |

Streaming services return results while you speak. Non-streaming services return the result after you finish and are usually more accurate. Different services from the same provider share one set of credentials.

## 🌏 Supported languages

**Translation and interface**: English, Simplified Chinese, Traditional Chinese, Japanese, Korean, French, German, Spanish, Italian, Portuguese, Russian, Arabic, Thai, Vietnamese, and Indonesian.

The languages that can actually be recognized depend on the selected speech recognition service. For example, Volcengine’s streaming service supports only Chinese and English.

## 🔒 Privacy

- Audio and text are sent only to the providers you configure and never pass through an intermediary server.
- Recordings are processed only in memory. Conversations are not persisted and are lost when the app closes.
- API keys are encrypted with Android Keystore, excluded from logs, and not included in backups.

## ❓ FAQ

<details>
<summary><b>Why is the translation direction sometimes wrong?</b></summary>

When the speech recognition service does not return language information, Parley identifies the language locally. Very short sentences or mixed Chinese and English can be misidentified. If the language cannot be determined, Parley translates into the second selected language.

</details>

<details>
<summary><b>What happens to audio already sent when I slide up to cancel?</b></summary>

With a streaming service, audio is sent in real time while the button is held. Cancelling immediately stops further transmission and discards the result, but audio that has already been sent cannot be recalled.

</details>

<details>
<summary><b>Why do I need to enter my keys again after reinstalling?</b></summary>

Keys are tied to encrypted storage on the device and are not backed up. You must configure them again after reinstalling the app or after the system resets its encryption keys.

</details>

<details>
<summary><b>How can I troubleshoot a problem?</b></summary>

Enable **Debug mode** in Settings to show a floating in-app log window. Copy the log and report the problem in [Issues](https://github.com/EvATive7/Parley/issues).

</details>

## 🛠️ Development

<details>
<summary>Build, release, and project structure</summary>

### Build

JDK 17+ and Android SDK Platform 37 are required.

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Outputs are written to `app/build/outputs/apk/{debug,release}/`. The debug package is `io.github.evative7.parley.debug`, uses the name “Parley Debug” and an amber icon, and can be installed alongside the release build. Local builds use the fixed version `0.1.0`. If `app/release.jks` is absent, the release APK is signed with the debug key.

### Release

The Git tag is the sole source of the version number. Pushing an annotated tag triggers `.github/workflows/release.yml`:

```sh
git tag -a v0.5.0 -m "Release v0.5.0"
git push origin v0.5.0
```

CI extracts `versionName` from the tag, calculates `versionCode` as `major*10000 + minor*100 + patch`, runs unit tests, signs the APK, and uploads it to a GitHub Release. The repository must define these secrets: `KEYSTORE_BASE64` (base64 of `release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

Volcengine resource IDs: `volc.seedasr.sauc.duration`, `volc.seedasr.auc`, and `volc.bigasr.auc_turbo`.

### Project structure

```
app/src/main/java/io/github/evative7/parley/
├─ ui/        UI, talk button, cards, face-to-face layout, service settings
├─ state/     TranslateViewModel, SettingsViewModel, talk-button state machine
├─ audio/     Recording, volume, WAV, and resampling
├─ asr/       Speech presets and routing; DashScope, OpenAI, and Volcengine recognizers
├─ translate/ OpenAI-compatible streaming translation
├─ network/   SSE parsing, connection warm-up, sanitized service errors
├─ model/     Languages and on-device language identification
├─ data/      Settings, translation services, Keystore-backed secret storage
├─ debug/     In-app debug log
├─ tts/       Text-to-speech interface only
└─ di/        Hilt bindings and network configuration
```

### Documentation

- [Speech service API reference](docs/APIs/transcriber/README.md)

</details>
