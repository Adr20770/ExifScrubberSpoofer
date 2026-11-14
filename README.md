# 🛡️ Exif Scrubber & Spoofer

**Exif Scrubber & Spoofer** is a privacy-first, 100% open-source Android application designed to inspect, scrub (remove), and spoof EXIF metadata from your photos locally on your device.

[![Android Version](https://img.shields.io/badge/Android-10.0%2B%20%28API%2029--35%29-brightgreen.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-v1.0.1%20Hotfix-blue.svg)](https://github.com/Adr20770/ExifScrubberSpoofer)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## ✨ Features

- **🔍 EXIF Inspector & Detailed Tag View**: Inspect hidden metadata tags (GPS, camera make/model, timestamps, software, aperture, ISO, exposure time, focal length) before sharing.
- **🧹 Scrub Metadata**: Strip GPS location, camera details, and creation dates with one click.
- **🎭 Metadata Spoofing**:
  - **Camera Make & Model**: Fake camera brand (e.g. *Leica M11*, *Hasselblad X1D*, *Analog Film Camera*).
  - **GPS Spoofing**: Enter custom Latitude & Longitude coordinates.
  - **Date & Time Spoofing**: Override photo timestamps with custom dates.
- **📦 Batch Processing**: Select and sanitize multiple photos simultaneously.
- **📄 Image ⇄ PDF Converter**:
  - Convert images to PDF documents locally.
  - Extract PDF pages as high-resolution JPEG images ready for metadata scrubbing.
- **💾 Image Compression**: Optional JPEG compression (85% quality) to save storage space.
- **🖤 OLED True Black & DayNight Themes**:
  - System Default
  - Light Mode
  - Dark Mode (Standard)
  - **Dark Mode (OLED True Black `#000000`)** for maximum battery saving on OLED displays.
- **🛡️ 100% Privacy & Local Processing**: No images or metadata ever leave your device. Zero cloud dependencies.
- **🌍 Multi-Language Support**: English, German, Spanish, French.

---

## 🚀 What's New in v1.0.1 Hotfix

- **Samsung Galaxy & Android 14 HEIC Support**: Automatic detection and conversion of HEIC/HEIF images to JPEG before applying EXIF attributes (resolves AndroidX HEIC EXIF write limitation).
- **MediaStore `IS_PENDING` Lifecycle**: Writes EXIF tags directly via `openFileDescriptor("rw")` on newly created MediaStore files to prevent Android MediaScanner from stripping custom metadata.
- **Original Photo Deletion Flow**: Resolves Photo Picker URIs to MediaStore IDs and triggers official `MediaStore.createDeleteRequest()` system prompt.
- **Spoofing Precedence**: Spoofed make, model, date, and GPS coordinates take priority over removal checkboxes.

---

## 📲 How to Install & Download APK

1. Go to the [Releases Page](https://github.com/Adr20770/ExifScrubberSpoofer/releases).
2. Download the `app-debug.apk` file.
3. Open the APK on your Android device and install it (allow installation from unknown sources if prompted).

---

## 🛠️ Build & Development

To build the APK locally from source:

```bash
git clone https://github.com/Adr20770/ExifScrubberSpoofer.git
cd ExifScrubberSpoofer
./gradlew app:assembleDebug
```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for more information.

© 2025 ADR20770 Apps.
