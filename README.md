# RAWVision Camera

A RAW camera and non-destructive editor for Android, written in **Kotlin + Jetpack Compose**.
It shoots DNG with full manual control, keeps every file inside its own private folder, stores
all metadata in a local SQLite database, and never modifies an original.

> Status: 1.0.0. Tested on Xiaomi Redmi Note 8 Pro (Android 11). Needs a phone with Android 9+
> whose camera exposes the Camera2 `RAW` capability (most phones from 2018 onwards; the app
> falls back to JPEG on lenses that cannot produce DNG).

## Features

**Shooting**
- RAW (DNG) + JPEG, RAW only, or JPEG only
- ISO and shutter speed independently automatic or manual (the app meters the scene itself when
  only one of them is manual, something the stock camera stack cannot do)
- Autofocus with tap-to-focus, manual focus slider (macro ↔ ∞), AE lock
- White balance presets and manual Kelvin + tint, exposure compensation, flash / torch, zoom,
  self-timer, lens switching on multi-camera phones
- Live histogram, spirit level, rule-of-thirds / golden / square grids, volume-key shutter
- Autofocus lock and AE pre-capture before every shot in auto mode

**Metadata** (kept in the database, in the DNG/JPEG EXIF and in an XMP sidecar next to each shot)
- GPS position and altitude (optional), date, device, lens, aperture, focal length, every exposure
  parameter, author name and copyright

**Non-destructive editing**
- Exposure, contrast, highlights, shadows, whites, blacks
- Temperature, tint, saturation, vibrance
- Sharpness, clarity, dehaze, vignette
- Rotate, flip, straighten with auto-crop, aspect-ratio crop presets
- Undo / redo, press-and-hold to compare with the original, version history with restore,
  one-tap reset to the untouched original
- Export to JPEG with EXIF copied (GPS, author, copyright, camera data)

**Library**
- Grid gallery with filters (all / favourites / edited / RAW), rating, notes
- Photo details with every capture parameter and the list of files
- "Exported files" screen with share, save to device and delete
- Share any JPEG export or the original DNG through the Android share sheet
- Save to device: copies files into `Pictures/RAWVision` so other apps can see them
- Optional automatic copy of every capture into `Pictures/RAWVision`

**Settings**
- Theme: system / light / dark, pure-black OLED option
- Accent colour (10 presets) or Material You dynamic colour (Android 12+)
- Text size, author / copyright, capture format, JPEG quality, grid, level, histogram,
  shutter sound, haptics, volume-key shutter, keep screen on
- Storage overview and a camera diagnostics page (what each lens reports)

**Design**: Material 3, bundled Roboto font, [Lucide](https://lucide.dev) icons, English and
Bulgarian UI.

## Install

1. Download `app-release.apk` from the [Releases](../../releases) page.
2. Open it on the phone. Android will ask to allow installs from that app (file manager or
   browser) the first time.
3. Grant the camera permission when asked. Location is optional and is only used for geotagging.

Release APKs attached to GitHub releases are signed with the project's release key when the
repository owner configured one, otherwise with a debug key (see *Releasing* below). An APK signed
with a different key cannot update an existing install; uninstall the old one first.

## Where the files are

Everything lives in the app's private folder (`Android/data` is **not** used; this is the internal
`files/` directory that only the app can read):

```
files/
  db/rawvision.db          SQLite: photos, edits, edit_history, exports, settings, camera_presets
  photos/RV_*.dng          RAW captures
  photos/RV_*.jpg          camera JPEG companion
  photos/RV_*.xmp          sidecar: author, copyright, GPS, EXIF summary, edit recipe
  thumbs/RV_*.jpg          gallery thumbnails
  exports/RV_*_edit_*.jpg  rendered edits
```

Nothing is written outside this folder unless you use *Share*, *Save to device* or enable
*Copy to phone gallery* in Settings. Uninstalling the app deletes the folder.

## Build from source

Requirements: Android Studio Narwhal (2025.1.1) or newer with its bundled JDK, or any JDK 17+ on
the command line. The Gradle wrapper downloads Gradle 8.13 itself.

```bash
git clone https://github.com/vislupus/RAWVision-Camera.git
cd RAWVision-Camera
./gradlew installDebug          # build and install on a connected phone (Windows: gradlew.bat)
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

Or open the folder in Android Studio and press **Run**. Compose *Live Edit*
(`Settings → Editor → Live Edit`) applies UI changes to the phone without restarting; *Apply
Changes* (`Ctrl+F10`) covers the rest.

No Android SDK path is needed beyond what Android Studio writes to `local.properties`. The project
uses no code generators (no KSP/kapt) and no third-party runtime libraries besides AndroidX.

### Project layout

```
app/src/main/java/com/rawvision/camera/
  camera/    Camera2 controller, capabilities, parameters, metering, DNG/JPEG saver, sensors, viewfinder UI
  editor/    CPU image pipeline, exporter, editor screen
  gallery/   gallery, photo details, exported files
  settings/  settings and diagnostics
  data/      SQLite (schema + DAOs), repositories, models, sandbox storage
  ui/        theme (Roboto, colour schemes from a seed, Lucide icons), shared components, navigation
  util/      image helpers, formatters, thumbnail cache, save-to-device
```

Versions are pinned in `gradle/libs.versions.toml` (AGP 8.11, Kotlin 2.2, Compose BOM 2025.06).

## Releasing

1. Create a release keystore once and keep it safe:
   ```bash
   keytool -genkeypair -v -keystore rawvision.jks -alias rawvision -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Locally: copy `keystore.properties.example` to `keystore.properties`, fill it in, run
   `./gradlew assembleRelease`. Both files are git-ignored. Without them the release build is
   signed with the debug key, which is fine for your own phone but not for distribution.
3. On GitHub: add the repository secrets `KEYSTORE_BASE64` (`base64 -w0 rawvision.jks`),
   `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Then push a tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   The *Release* workflow builds the APK and attaches it to a GitHub Release for that tag.
   Every push and pull request also runs the *Build* workflow as a smoke test.

Bump `versionCode` and `versionName` in `app/build.gradle.kts` for each release.

## Device notes

- **MIUI / MediaTek phones** return incomplete camera metadata before the camera is opened; the
  app re-reads it after opening, otherwise DNG creation fails with
  "Missing metadata fields for tag CalibrationIlluminant1".
- Some HALs report a minimum focus distance of 0 for lenses that do autofocus; the app also
  looks at the available AF modes.
- When a lens really cannot produce a DNG, the shot is kept as JPEG and the reason is stored in
  the photo's notes. *Settings → Storage → Camera diagnostics* shows what each lens reports;
  please include it in bug reports.
- The DNG orientation is stored as a TIFF tag (not by rotating pixels); some viewers ignore it.

## Privacy

The app has no network permission. Location is only read when *Save location* is enabled and
is only written into your own files. Nothing leaves the phone unless you share it.

## Licence

The source code is released under the MIT licence (see `LICENSE`).
Third-party assets: [Lucide icons](https://lucide.dev) (ISC, `licenses/LUCIDE_ISC.txt`) and
[Roboto](https://fonts.google.com/specimen/Roboto) (SIL OFL 1.1, `licenses/ROBOTO_OFL.txt`).
