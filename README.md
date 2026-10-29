# Hugging Face Spaces Explorer (Android)

A native Android app for browsing [Hugging Face Spaces](https://huggingface.co/spaces) and running Gradio Spaces directly from your phone.

## Features

- **Browse**: the trending Spaces feed with infinite scroll, pull to refresh, debounced search on the Hub, semantic categories, and local filters (status, SDK, hardware, pinned, featured) plus sorting. The last trending page is cached, so the app opens offline.
- **Space details**: status, hardware, the API's endpoints with their inputs and outputs, and ready-to-copy Python / JavaScript / cURL snippets (taken from the Space itself on Gradio 5).
- **Runner**: a form generated from the Space's Gradio schema. Each component gets the right control: text, number, slider (with its real range), checkbox, radio/dropdown, multi-select, image/audio/video/file pickers, and gallery uploads. Files are uploaded through Gradio's upload API, never inlined as base64.
- **Background runs**: runs continue while you browse, stream progress and partial output, can really be stopped (the HTTP call is cancelled), and are kept in a history that survives restarts.
- **Saved outputs**: images, audio, video, files and text from each run are copied into app storage, because Gradio deletes its temporary files. You can play, open and share them from the gallery.
- **Favourites**: refreshed from the Hub, so status and metadata stay current.
- **Access tokens**: optional Hugging Face tokens for private, gated or ZeroGPU Spaces. They're encrypted with an Android Keystore key and only ever sent to `huggingface.co` / `*.hf.space`.

## Building

Requirements: JDK 17+, Android SDK 35.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # JVM + Robolectric tests
./gradlew lintDebug
```

`testDebugUnitTest` also runs an end-to-end UI test against a fake Hub and writes phone screenshots to `app/build/outputs/screenshots/` for review.

## Architecture

```
app/src/main/java/com/hfspaces/explorer/
├── AppContainer.kt           manual DI (one per process)
├── data/
│   ├── remote/               HfHubClient (listing, search, metadata), GradioClient (info, upload, queue calls), SpaceIds (id/host validation)
│   ├── gradio/               schema models, InputKind mapping, payload building, SSE and output parsing (pure Kotlin, unit tested)
│   ├── local/                Room database (v2) with migrations
│   ├── security/             Keystore token cipher
│   └── repository/           Spaces, favourites, tokens, media, TaskManager
└── ui/                       Compose screens, one ViewModel per screen
```

Security notes:

- Space ids and hosts are validated before use. Only `https://<label>.hf.space` hosts are called, so a malicious listing can't redirect requests (or your token) elsewhere.
- Endpoint names are validated, and errors reported by a Space are shown as-is rather than hidden behind silent fallbacks.
- Backups are disabled, because Keystore-encrypted tokens can't be restored onto another device.
