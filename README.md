# ImageSquee

A lightweight Android utility that compresses, resizes, and strips metadata from images during sharing. Select photos in any app, share to ImageSquee, and forward the optimized result to its final destination.

## How It Works

1. Select one or more images in any app (gallery, file manager, etc.)
2. Tap **Share** and choose **ImageSquee**
3. Images are instantly resized, compressed, and stripped of EXIF metadata
4. A new share chooser appears to send the processed images wherever you want

No extra screens, no accounts, no internet required.

## Features

- Handles single and multi-image sharing via `ACTION_SEND` / `ACTION_SEND_MULTIPLE`
- Output to **WebP** or **JPEG**
- Configurable target resolution: 480p, 720p, 1080p, 1440p, 4K
- Compression quality slider (1%–100%)
- Full EXIF/metadata stripping (GPS, camera model, timestamps, and more)
- Processing runs on background coroutines with a minimal overlay
- Settings persisted via Jetpack DataStore

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3 with dynamic color)
- **Android Bitmap APIs** for resize and compress
- **AndroidX ExifInterface** for metadata stripping
- **Kotlin Coroutines** for background processing
- **Preferences DataStore** for settings persistence
- **FileProvider** for secure URI sharing between apps

## Requirements

- Android 12 (API 32) or later

## Building

```bash
./gradlew assembleDebug       # Debug APK
./gradlew bundleRelease        # Signed release AAB
```

Release signing requires a `keystore.properties` file in the project root:

```properties
storeFile=keystore/release.jks
storePassword=your_password
keyAlias=your_alias
keyPassword=your_password
```

## Privacy

ImageSquee does not request internet access. All processing happens on-device. No data is collected or transmitted. See the full [Privacy Policy](https://ximfinity.github.io/ImageSquee/).

## License

All rights reserved.
