# RAWVision Camera

A RAW camera and non-destructive photo editor for Android, built with **Kotlin** and
**Jetpack Compose**. Shoot DNG with full manual control, edit without ever touching the original,
and keep everything (files and metadata) in the app's own folder and SQLite database.

- Android 9 (API 28) or newer
- RAW needs a camera that exposes the Camera2 `RAW` capability (most phones since 2018); lenses
  that cannot produce DNG fall back to JPEG automatically
- No network permission, no accounts, no analytics

**Contents:** [Features](#features) · [Install](#install) · [Using the app](#using-the-app) ·
[Where the files are](#where-the-files-are) · [Build from source](#build-from-source) ·
[Releasing](#releasing) · [Device notes](#device-notes) · [Reporting problems](#reporting-problems) ·
[License](#license)

## Features

### Shooting
- **RAW (DNG) + JPEG**, RAW only, or JPEG only
- **ISO and shutter speed independently automatic or manual.** When only one of them is manual
  the app meters the scene itself through a small analysis stream, which the standard Camera2
  auto-exposure cannot do.
- Autofocus with tap-to-focus, manual focus slider (macro ↔ ∞), AE lock
- White balance presets and manual Kelvin + tint, exposure compensation, flash / torch, zoom,
  self-timer, lens switching on multi-camera phones
- Live histogram, spirit level, rule-of-thirds / golden / square grids, volume keys as shutter
- In auto mode every shot is preceded by an autofocus lock and an AE pre-capture, like a dedicated
  camera's half-press

### Metadata
Stored in the database, written into the DNG and JPEG (EXIF) and into an XMP sidecar next to
each capture:
- GPS position and altitude (optional), date and time, device, lens, aperture, focal length
- Every exposure parameter used for the shot (ISO, shutter, EV, white balance, flash, zoom, mode)
- Author name and copyright

### Non-destructive editing
- Light: exposure, contrast, highlights, shadows, whites, blacks
- Colour: temperature, tint, saturation, vibrance
- Detail: sharpness, clarity, dehaze
- Effects: vignette
- Geometry: rotate, flip, straighten with automatic crop, aspect-ratio crop presets
- Undo / redo, press-and-hold to compare with the original, version history with restore,
  one-tap reset to the untouched original
- The recipe is stored as data; the DNG/JPEG on disk is never modified
- Export to JPEG with EXIF carried over (GPS, author, copyright, camera data)

### Library
- Gallery grid with filters (all / favourites / edited / RAW), rating and notes per photo
- Photo details with every capture parameter and the list of files and sizes
- *Exported files* screen with share, save to device and delete
- Share any JPEG export or the original DNG through the Android share sheet
- *Save to device* copies a file into `Pictures/RAWVision` so the system gallery can see it
- Optional automatic copy of every DNG and JPEG into `Pictures/RAWVision`

### Settings
- Theme: system / light / dark, plus a pure-black option for OLED screens
- Accent colour (10 presets) or Material You dynamic colour (Android 12+)
- Text size, author / copyright, capture format, JPEG quality, grid, level, histogram,
  shutter sound, haptics, volume-key shutter, keep screen on
- Storage overview and a *Camera diagnostics* page that shows what each lens reports

### Design
Material 3, bundled Roboto font, [Lucide](https://lucide.dev) icons. UI in English and Bulgarian.

## Install

1. Download `RAWVision-Camera-vX.Y.Z.apk` from the [Releases](../../releases) page.
2. Open the file on the phone. The first time, Android asks to allow installs from the app you
   opened it with (file manager or browser).
3. Start RAWVision and grant the camera permission. Location is optional; it is only used to
   geotag your photos and can be turned off in Settings.

All releases are signed with the same key, so each new version updates the previous one in place.
Builds you make yourself from this repository use that key too.

## Using the app

**Viewfinder.** The strip under the preview shows ISO, shutter (S), exposure compensation (EV),
white balance (WB), focus (AF/MF) and zoom. An **A** next to a value means it is automatic. Tap a
value to open its control; use the **AUTO** chip in the control to hand it back to the camera.
The round button on the right shows the current mode (`AUTO`, `P`, `S`, `ISO`, `M`); tapping it
returns everything to automatic.

- Tap the preview to focus and meter on a point; tap again elsewhere to move it.
- Pinch to zoom, double-tap to reset zoom.
- The icons over the preview are flash, timer, grid, GPS status, lens picker (when the phone has
  several lenses), front/back switch and Settings.
- The thumbnail at the bottom left opens the gallery.

**Editing.** Open a photo and tap **Edit**. Drag sliders, tap a value to reset it, press and hold
the image to see the original. Edits save automatically; the history (clock-arrow icon) keeps
every saved version and the original. The download icon renders the edit and stores it in
`Pictures/RAWVision`; the share icon renders it and opens the share sheet.

## Where the files are

Everything lives in the app's private internal storage, which other apps and file managers
cannot read:

```
files/
  db/rawvision.db          SQLite: photos, edits, edit_history, exports, settings, camera_presets
  photos/RV_*.dng          RAW captures
  photos/RV_*.jpg          camera JPEG companion
  photos/RV_*.xmp          sidecar: author, copyright, GPS, EXIF summary, edit recipe
  thumbs/RV_*.jpg          gallery thumbnails
  exports/RV_*_edit_*.jpg  rendered edits
```

Files leave this folder only when you use *Share*, *Save to device* or enable *Copy to phone
gallery* in Settings. Uninstalling the app deletes the folder, so export or copy what you want to
keep first.

## Build from source

Requirements: Android Studio Narwhal (2025.1.1) or newer, or any JDK 17+ on the command line.
The Gradle wrapper downloads Gradle 8.13 by itself; Android Studio installs the SDK.

```bash
git clone https://github.com/vislupus/RAWVision-Camera.git
cd RAWVision-Camera
./gradlew installDebug        # build and install on a connected phone (Windows: gradlew.bat)
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Or open the folder in Android Studio and press **Run**. Compose *Live Edit*
(`Settings → Editor → Live Edit`) pushes UI changes to the phone without restarting; *Apply
Changes* (`Ctrl+F10`) covers changes outside Compose.

The project has no code generators (no KSP/kapt) and no third-party runtime libraries besides
AndroidX. Versions are pinned in `gradle/libs.versions.toml` (AGP 8.11, Kotlin 2.2,
Compose BOM 2025.06).

### Project layout

```
app/src/main/java/com/rawvision/camera/
  camera/    Camera2 controller, lens capabilities, shooting parameters, metering loop,
             DNG/JPEG/XMP writer, sensors (GPS, level, rotation), viewfinder UI
  editor/    CPU image pipeline, exporter, editor screen
  gallery/   gallery, photo details, exported files
  settings/  settings and camera diagnostics
  data/      SQLite schema and DAOs, repositories, models, sandbox storage layout
  ui/        theme (Roboto, colour schemes derived from a seed, Lucide icons), shared components,
             navigation
  util/      image decoding helpers, formatters, thumbnail cache, save-to-device
```

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` and add an entry to
   `CHANGELOG.md`.
2. Tag and push:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
   The *Release* workflow builds `RAWVision-Camera-v1.0.1.apk` and attaches it to a GitHub
   Release for that tag. The *Build* workflow compiles a debug APK on every push and pull request.

Releases are signed with `debug.keystore` from the repository (alias `androiddebugkey`, password
`android`), so local and CI builds carry the same signature. A private key can be used instead
by creating `keystore.properties` (see `keystore.properties.example`) or by adding the
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets.
Changing the key breaks in-place updates for existing installs.

## Device notes

- **MIUI / MediaTek phones** (for example Redmi Note 8 Pro) return incomplete camera metadata
  before the camera is opened. The app re-reads the metadata after opening; without that, DNG
  creation fails with "Missing metadata fields for tag CalibrationIlluminant1".
- Some camera HALs report a minimum focus distance of 0 for lenses that do autofocus. The app
  also checks the available AF modes before deciding a lens is fixed-focus.
- If a lens cannot produce a DNG at all, the shot is kept as JPEG and the reason is stored in the
  photo's notes.
- DNG orientation is stored as a TIFF tag rather than by rotating pixels, which is standard; a few
  viewers ignore it.

## Reporting problems

Open an issue and include:
- the phone model and Android version
- the text from *Settings → Storage → Camera diagnostics* (use its copy button)
- for a failed shot, the note shown in the photo's details under *Location & author*

## License

Source code: [MIT](LICENSE).
Third-party assets: [Lucide icons](https://lucide.dev) (ISC, `licenses/LUCIDE_ISC.txt`) and
[Roboto](https://fonts.google.com/specimen/Roboto) (SIL Open Font License 1.1,
`licenses/ROBOTO_OFL.txt`).
