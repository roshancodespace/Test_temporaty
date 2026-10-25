# CodeFlow Studio — Deterministic Coding Session Playback

CodeFlow Studio is a native Android presentation and automation tool designed for screen recordings, tutorials, and realistic demonstrations by playing back deterministic, character-by-character coding sessions in a polished desktop-inspired Material 3 IDE.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were aligned during Phase 1 clarification:

- **Bundled Sample Project**: High-fidelity Android Kotlin project (`GreetingViewModel.kt` and `GreetingScreen.kt` using Jetpack Compose) for immediate zero-config testing and recording out of the box.
- **Visual Design & Palette**: VS Code Dark Plus palette featuring a deep slate editor background (`#1E1E1E`), panel border dividers (`#2D2D2D`), active tab indicator with electric cyan accent (`#007ACC` / `#4FC1FF`), and syntax token colors (keywords in blue `#569CD6`, types in turquoise `#4EC9B0`, strings in orange `#CE9178`, comments in green `#6A9955`).
- **Phased Implementation Roadmap**: Phase 1 focuses exclusively on the local visual playback engine, safe ZIP import, project explorer, tabbed editor with syntax highlighting, and deterministic character typing. Bluetooth HID keyboard output and GitHub import are scheduled for subsequent phases.

---

## 1. Overview & Core Concept

- **What It Does**: Simulates realistic developer typing activity on Android devices. Users can import ZIP archives (or load the bundled Kotlin project), pick files, and replay code entry character-by-character with realistic typing variations, line-end pauses, caret navigation, and tab switching.
- **Target Persona**: Content creators, developer educators, software presenters, and technical video producers recording coding screencasts without typing mistakes or live latency.
- **Key Value**: 100% deterministic, reproducible, offline playback with zero artificial AI bloat, no external accounts, and complete safety (isolated presentation mode leaves source code untouched).

---

## 2. User Experience & Visual Design

### Key User Flows

1. **First Launch & Sample Playback**:
   - The user opens CodeFlow Studio directly into a focused IDE workspace.
   - The bundled Kotlin demo project is loaded in Presentation Mode.
   - The user taps **Play** (or spacebar/control bar) to watch the code appear character-by-character with syntax coloring and animated caret.
   - Playback can be paused, resumed, or restarted at any time without duplicating or dropping characters.
2. **Project Import Flow**:
   - User navigates to **Projects** or taps **Open ZIP**.
   - System File Picker (SAF) opens; user selects any `.zip` project archive.
   - Safe extractor validates paths against directory traversal (`../`), unpacks into app-internal sandbox cache, and displays the directory tree.
   - User selects files to include/exclude and launches the workspace.
3. **Session Configuration & Speed Tuning**:
   - User configures typing speed (characters per second or ms/char), line break delay, and randomized typing jitter seed.
   - Fullscreen distraction-free mode hides navigation chrome for clean screen capture.

### Visual Identity & Theme Tokens

- **Aesthetic**: Flat, developer-first desktop IDE inspired by VS Code and Material 3 (4–8dp rounded corners, 1dp subtle panel borders, dark surface elevations).
- **Color Roles**:
  - `editor_bg`: `#1E1E1E` (deep editor surface)
  - `sidebar_bg`: `#252526` (project file tree and explorer)
  - `tab_active_bg`: `#1E1E1E`, `tab_inactive_bg`: `#2D2D2D`
  - `border_subtle`: `#333333`
  - `accent_primary`: `#007ACC` (active tab highlight, primary action)
  - `cursor_caret`: `#AEAFAD` (blinking solid bar)
  - `selection_bg`: `#264F78`
- **Typography**: Bundled JetBrains Mono or System Monospace (`FontFamily.Monospace`) for code, line gutter, and terminal text. Inter / Roboto for IDE toolbars and tree labels.

---

## 3. Key Product Decisions & Trade-Offs

- **Syntax-Highlighted Compose Code Editor**:
  - *Chosen Approach*: A custom high-performance Compose-native text display and editing canvas supporting regex-based Lexer/Tokenizer for Kotlin, Java, Python, JSON, XML, HTML, and Markdown. It renders line numbers, active line highlighting, custom caret positioning, and tokenized `AnnotatedString` spans.
  - *Why*: Eliminates fragile heavyweight third-party view wrappers while guaranteeing millisecond-level programmatic character insertion, stable layout during screen recording, and zero dropped frames.
- **Presentation Mode vs Workspace Mode**:
  - *Chosen Approach*: Phase 1 implements **Presentation Mode**, keeping the original extracted files immutable in storage and maintaining playback buffer state in memory.
  - *Why*: Ensures zero risk of corrupting user source files during test runs and allows instant restarts from character offset 0.
- **Safe ZIP Extraction Pipeline**:
  - *Chosen Approach*: Stream-based extraction with canonical path validation preventing zip-slip vulnerabilities, maximum size limits, and recursion safety.

---

## 4. Technical Architecture & Data Strategy

### System Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CodeFlow Studio UI                              │
├────────────────────────────────┬───────────────────────────────────────┤
│    Sidebar / File Explorer     │             Editor Workspace          │
│   ┌──────────────────────────┐ │ ┌───────────────────────────────────┐ │
│   │ Project Tree View        │ │ │ Tab Strip (Active / Pinned)       │ │
│   │ Included / Excluded Tag  │ │ ├───────────────────────────────────┤ │
│   │ SAF ZIP Import Trigger   │ │ │ Line Gutter │ Code Canvas Buffer  │ │
│   └──────────────────────────┘ │ │ (Numbers)   │ (AnnotatedString)   │ │
│                                │ ├───────────────────────────────────┤ │
│                                │ │ Minimal Status Bar & Progress     │ │
│                                │ └───────────────────────────────────┘ │
├────────────────────────────────┴───────────────────────────────────────┤
│                      Playback Control Floating HUD                     │
│    [ Restart ]   [ Rewind ]   [ Play / Pause ]   [ Speed 1x/2x ]       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ triggers
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Deterministic Playback Engine                     │
│  - Coroutine Flow State Machine (Idle | Playing | Paused | Completed)  │
│  - Step Sequencer (Char insertion, pause, newline delay, tab switch)   │
│  - Seeded Deterministic Jitter Generator                               │
│  - Presentation Document Buffer (Target Text vs Visible Text)          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ interacts with
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         Storage & Model Layer                          │
│  - SafeZipExtractor (Canonical Path Traversal & Sandbox Isolation)     │
│  - Bundled Sample Project Provider (Kotlin Compose Greeting App)       │
│  - ProjectRepository & SessionRepository (DataStore / Room schema)     │
└────────────────────────────────────────────────────────────────────────┘
```

### Key Data Entities

- `ProjectModel`: ID, name, root directory path, total files, included files filter rules.
- `FileNode`: Relative path, name, isDirectory, children, file extension.
- `PlaybackSession`: Actions list, base typing speed (cps), newline pause ms, current action index, character offset.
- `PlaybackAction`: Polymorphic sealed class:
  - `OpenFile(filePath: String)`
  - `InsertText(text: String, startOffset: Int)`
  - `Pause(durationMs: Long)`
  - `SwitchTab(tabId: String)`
- `PlaybackState`: `Idle`, `Playing(progress: Float, charsTyped: Int, totalChars: Int, currentFile: String)`, `Paused(...)`, `Completed`.

---

## 5. Phase 1 Implementation Plan

1. **App Identity & Core Setup**:
   - Update `metadata.json` and `res/values/strings.xml` to `CodeFlow Studio`.
   - Configure dark Material 3 IDE theme, colors, and VS Code Dark Plus typography.
2. **Deterministic Playback Engine**:
   - Implement `PlaybackEngine` with cancellation-safe Kotlin Coroutines.
   - Support precise character-by-character typing with deterministic jitter, newline pauses, pause/resume/restart/stop, and speed multiplier (0.5x, 1x, 2x, 4x).
3. **Syntax Highlighter & Lexer**:
   - Tokenizer for Kotlin, Java, XML, JSON, and Python producing styled `AnnotatedString` blocks with accurate keyword, string, comment, and type highlights.
4. **Editor Presentation Workspace**:
   - Multi-tab support, line gutter with line numbering, active line highlight, blinking caret, and smooth auto-scrolling to current typing position.
   - Status bar displaying language mode, line/col, chars typed, and playback status.
5. **Safe ZIP Import & File Tree**:
   - System File Picker SAF integration with `ActivityResultContracts.OpenDocument`.
   - Path-traversal proof `SafeZipExtractor`.
   - Collapsible hierarchical tree view with file inclusion/exclusion toggles.
6. **Bundled Sample Project**:
   - Built-in multi-file Android Kotlin project (`GreetingViewModel.kt` and `GreetingScreen.kt`) ready for immediate playback without requiring external files.
7. **Verification**:
   - JVM Robolectric / unit tests verifying character synchronization, non-duplication upon pause/resume, and ZIP path sanitization.
   - Compile and verify applet execution.
