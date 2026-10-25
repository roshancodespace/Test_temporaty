# CodeFlow Studio

CodeFlow Studio is a native Android application built with Kotlin, Jetpack Compose, and Material 3 designed for deterministic coding session playback during screen recordings, technical tutorials, and product demonstrations.

## Features (Phase 1)
- **Deterministic Playback Engine**: Real-time character-by-character typing with deterministic random jitter, newline pauses, line/column tracking, pause/resume without character duplication, restart, and seek.
- **VS Code Dark Plus Aesthetic**: Flat, developer-first IDE aesthetic with deep slate background, subtle 1dp borders, tab strip with active indicators, and monospace line gutter.
- **Compose Syntax Highlighter**: High-performance tokenization for Kotlin, Java, Python, JSON, XML, HTML, and Markdown.
- **Safe ZIP Archive Import**: System File Picker (Storage Access Framework) integration with zip-slip and path traversal protection (`SafeZipExtractor`).
- **Collapsible File Explorer**: Hierarchical tree with file inclusion/exclusion selection.
- **Distraction-Free Mode**: Fullscreen editor mode hiding IDE chrome for clean screen capture recordings.
- **Bundled Sample Project**: Multi-file Android Kotlin Compose demo project (`GreetingViewModel.kt` and `GreetingScreen.kt`) ready to play immediately.

## Build and Run
- Build debug APK: `gradle assembleDebug`
- Run local unit and Robolectric tests: `gradle :app:testDebugUnitTest`
