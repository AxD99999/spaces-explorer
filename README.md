# Spaces Explorer for Hugging Face

A native Android app for browsing [Hugging Face Spaces](https://huggingface.co/spaces) and running Gradio Spaces directly from your phone.

> **Unofficial.** This is an independent open-source project. It isn't affiliated with or endorsed by Hugging Face. "Hugging Face" is a trademark of Hugging Face, Inc.

## Install

Download the APK from the [latest release](https://github.com/AxD99999/spaces-explorer/releases/latest) and open it on your phone. You'll need to allow installs from your browser or file manager. Android 8.0 or newer is required.

## Features

### Discover
- **Browse**: the trending Spaces feed with infinite scroll, pull to refresh, debounced search on the Hub, semantic categories, and filters. Sort by trending, likes, recently updated or newest, and filter by SDK, on the Hub's side. Status, hardware, pinned and featured filters apply on the device. Recent searches are remembered. The last trending page is cached, so the app opens offline.
- **Adult content hidden by default**: Spaces that Hugging Face tags `not-for-all-audiences` are left out of Browse and search until you turn on "Show adult Spaces" in Settings. Untagged Spaces can't be caught, and Spaces you favourited or own are always shown.
- **Space details**: status, hardware, the API's endpoints with their inputs and outputs, and ready-to-copy Python / JavaScript / cURL snippets (taken from the Space itself on Gradio 5+).
- **Compare**: run the same prompt on two Spaces side by side.

### Run
- **Runner**: a form generated from the Space's Gradio schema. Each component gets the right control: text, number, slider (with its real range), checkbox, radio/dropdown, multi-select, image/audio/video/file pickers, and gallery uploads. Files are uploaded through Gradio's upload API, never inlined as base64.
- **Queue aware**: runs go through Gradio's queue protocol, so you see your place in the queue, the ETA, step progress and streamed output. Stopping a run tells the Space to drop it. Spaces without the queue fall back to the `/call` API.
- **Wake sleeping Spaces**: a sleeping or paused Space can be woken from the runner, which polls until it's up.
- **Chat**: Spaces built on `gr.ChatInterface` open in a chat view with streamed replies.
- **Presets**: save a form's inputs under a name and reload them later.
- **Run again**: any run in the history reopens the runner with the same inputs.
- **Share to the app**: share text or images from another app, pick a Space, and the form is pre-filled.
- **Background runs**: runs keep going in a foreground service while you use other apps. A notification shows progress and has a Stop all action, and you get a notification with a thumbnail when a run finishes. History survives restarts.

### Keep
- **Saved outputs**: images, audio, video, files and text from each run are copied into app storage, because Gradio deletes its temporary files. A full-screen viewer has pinch to zoom and swipe, and images can be saved to the system gallery or shared.
- **Favourites**: refreshed from the Hub, sortable, grouped into your own collections. You can also import the Spaces you liked on Hugging Face.
- **Home screen**: launcher shortcuts for your most used Spaces, and a widget that shows your latest output.

### Your Spaces
- **My Spaces**: with a write token, list your own and your organisations' Spaces, then restart, factory rebuild, pause, change hardware, and follow build/run logs live.
- **Access tokens**: optional Hugging Face tokens for private, gated or ZeroGPU Spaces. They're encrypted with an Android Keystore key and only ever sent to `huggingface.co` / `*.hf.space`.

### App
- **Settings**: light/dark/system theme, Material You dynamic colour on Android 12+, finished-run notifications, and adult content.
- **Tablets and foldables**: on wide screens the Space details open in a second pane next to the list.

## Building

Requirements: JDK 17 to 21 to run Gradle, Android SDK 36.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # JVM + Robolectric tests
./gradlew lintDebug
./gradlew connectedDebugAndroidTest   # instrumented smoke tests (needs a device or emulator)
```

`testDebugUnitTest` also runs an end-to-end UI test against a fake Hub and writes phone screenshots to `app/build/outputs/screenshots/` for review. CI runs the instrumented tests on an API 34 emulator.

## Releasing

Releases are signed APKs published on GitHub. The signing key never goes in the repo: `keystore.properties`, `*.jks` and `*.keystore` are ignored by git.

1. **Create a key once**, and back it up somewhere safe. If you lose it, phones can't update to your next release without uninstalling first.

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias release -keyalg RSA -keysize 4096 -validity 10000
   ```

2. **Local signed builds**: put `release.jks` in the project root and create `keystore.properties` next to it:

   ```properties
   storeFile=release.jks
   storePassword=...
   keyAlias=release
   keyPassword=...
   ```

   `./gradlew assembleRelease` then writes a signed `app/build/outputs/apk/release/app-release.apk`. Without the file, the APK is unsigned.

3. **Releases from CI**: add four repository secrets under *Settings → Secrets and variables → Actions*:

   | Secret | Value |
   | --- | --- |
   | `SIGNING_KEYSTORE_BASE64` | the keystore, base64 encoded (`base64 -w0 release.jks`, or in PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))`) |
   | `SIGNING_STORE_PASSWORD` | the keystore password |
   | `SIGNING_KEY_ALIAS` | `release` |
   | `SIGNING_KEY_PASSWORD` | the key password |

   Then bump `versionCode` and `versionName` in `app/build.gradle.kts` and push a matching tag:

   ```bash
   git tag v2.2.0 && git push origin v2.2.0
   ```

   The Release workflow checks the tag matches `versionName`, builds the APK and publishes a GitHub Release with the APK and its SHA-256 checksum.

## Architecture

```
app/src/main/java/com/hfspaces/explorer/
├── AppContainer.kt           manual DI (one per process)
├── data/
│   ├── remote/               HfHubClient (listing, search, metadata, likes, owner actions, logs), GradioClient (info, upload, queue calls), SpaceIds (id/host validation)
│   ├── gradio/               schema models, InputKind mapping, payloads, queue protocol and diffs, chat shapes, SSE and output parsing (pure Kotlin, unit tested)
│   ├── local/                Room database (v3) with migrations
│   ├── security/             Keystore token cipher
│   └── repository/           Spaces, favourites, collections, presets, settings, tokens, media, TaskManager
├── system/                   run service and notifications, deep links and share target, shortcuts and widget
└── ui/                       Compose screens, one ViewModel per screen
```

Security notes:

- Space ids and hosts are validated before use. Only `https://<label>.hf.space` hosts are called, so a malicious listing can't redirect requests (or your token) elsewhere.
- Endpoint names are validated, and errors reported by a Space are shown as-is rather than hidden behind silent fallbacks.
- Backups are disabled, because Keystore-encrypted tokens can't be restored onto another device.

## Privacy

- The app has no analytics, ads or tracking, and no server of its own. It talks to Hugging Face and to the Spaces you open.
- Hugging Face access tokens are encrypted on the device and only sent to `huggingface.co` and `*.hf.space`.
- What you type or upload when you run a Space goes to that Space. Spaces are run by third parties on Hugging Face, so treat inputs as you would on the Space's web page.
- Favourites, run history and saved outputs stay on your device.

## License

Licensed under the [Apache License 2.0](LICENSE).
