# 🛡️ Exif Scrubber & Spoofer

**Exif Scrubber & Spoofer** is a privacy-first, 100% open-source Android application designed to inspect, scrub (remove), and spoof EXIF metadata from your photos locally on your device.

[![Android Version](https://img.shields.io/badge/Android-10.0%2B%20%28API%2029--35%29-brightgreen.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-v1.0.2%20%28Build%203%29-blue.svg)](https://github.com/Adr20770/ExifScrubberSpoofer)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## ✨ Features (v1.0.2 - Phase B)

- **📍 GPS Map Picker (osmdroid)**: Pick location directly on an OpenStreetMap map or apply a random GPS offset.
- **💾 Custom Presets**: Save and load your own custom camera & spoofing presets locally via SharedPreferences & PresetManager.
- **📅 Advanced Date Spoofing**: Generate random dates (past 25 years) or shift dates by N days.
- **📄 PDF Privacy Report**: Export a formatted audit report of your privacy actions directly into `Documents/ExifScrubber`.
- **🔍 Before/After EXIF Comparison**: Review tag differences immediately after scrubbing.
- **📜 Scrub History**: Track, view, and open the last 10 scrub operations.
- **🎨 Material 3 UI & Bottom Navigation**: Clean 3-Tab layout (*Single, Batch, Tools*).
- **🛡️ 100% Privacy & Local Processing**: No images or metadata ever leave your device. Zero cloud dependencies.
- **🌍 Multi-Language Support**: English, German, Spanish, French.

---

## 📲 How to Install & Download APK

1. Go to the [Releases Page](https://github.com/Adr20770/ExifScrubberSpoofer/releases).
2. Download the `app-debug.apk` file.
3. Open the APK on your Android device and install it.

---

## 🛠️ Build & Development

To build the APK locally from source:

```bash
git clone https://github.com/Adr20770/ExifScrubberSpoofer.git
cd ExifScrubberSpoofer
./gradlew app:assembleDebug
```

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for more information.

© 2025 ADR20770 Apps.
