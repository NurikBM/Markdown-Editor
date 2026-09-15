# Changelog

All notable changes to the **Markdown Editor** project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-09-15

### Added
- **Biometric Document Security:**
  - Integrated `androidx.biometric:biometric-ktx` supporting `BIOMETRIC_STRONG` and system device credentials (PIN, pattern, password).
  - Added Room schema migration `MIGRATION_1_2` introducing persistent `isLocked` flag to document metadata.
  - Added `LockedDocumentView` with biometric prompt trigger for locked documents.
  - Added instant "Lock Note Now" session lock UI action in `EditorTopBar` and overflow menu.
  - Added automatic background session relock when application transitions to `Lifecycle.Event.ON_STOP`.
- **Multi-Format Document-to-Markdown Conversion Engine:**
  - Microsoft Word (`.docx`) converter parsing OpenXML structures into headings, lists, tables, and formatted text.
  - Microsoft Excel (`.xlsx`) converter reading shared strings and sheet data into Markdown tables.
  - Portable Document Format (`.pdf`) converter via `pdfbox-android` with heuristic heading detection and bold/italic extraction.
  - HTML (`.html`, `.htm`) converter via JSoup DOM tree transformation.
  - Tabular data (`.csv`, `.tsv`) converter with multiline quoted string support.
  - Structured code/data (`.json`, `.xml`, `.yaml`, `.yml`) conversion into fenced code blocks.
- **Android System Integrations:**
  - `Intent.ACTION_SEND` receiver for sharing text or document streams directly into Markdown Editor.
  - `Intent.ACTION_VIEW` receiver for opening `.md`, `.markdown`, and `.txt` files directly from file managers.
  - Home Screen Quick Note widget built with Jetpack Glance (`androidx.glance:glance-appwidget:1.1.1`).
  - Document Manager Navigation Drawer (`ModalNavigationDrawer`) for document browsing, creation, deletion, and SAF file importing.
- **Production Hardening:**
  - Configured ProGuard / R8 full-mode optimization with code minification and resource shrinking in `app/build.gradle.kts`.
  - Created comprehensive `proguard-rules.pro` keeping Room, Hilt, Compose, Glance, PDFBox, Jsoup, DiffUtils, and Commonmark.
  - Personalized release APK naming: `MarkdownEditor-v1.0-release.apk` (16.5 MB, >50% size reduction).

### Fixed
- **Biometric Persistence:** Resolved issue where background auto-save was resetting `isLocked` to false on unpopulated metadata flushes.
- **Startup Stutter / IME Collision:** Eliminated cold-start layout jank by defaulting `focusedBlockId` to null, preventing soft keyboard auto-opening during initial Compose composition.
- **Orientation State Retention:** Fixed document reset on device rotation by guarding intent extraction behind `savedInstanceState == null`.
- **Text Input Stability:** Resolved cursor jumping by decoupling local Compose `BasicTextField` state from asynchronous Myers diffing and Room persistence.

### Security
- Hardened `AndroidManifest.xml` by disabling `allowBackup` and enforcing HTTPS cleartext network restrictions.
- Implemented universal binary sniffing (`content.take(4096).contains('\u0000')`) and media extension blacklist to prevent corrupt file imports.

---

## [0.6.0] - 2026-09-12

### Added
- **Material 3 Dynamic Theming:**
  - Dynamic Color theming based on Android 12+ wallpaper palette with pure `#000000` AMOLED dark theme.
  - Adaptive App Icon with clean vector Markdown badge (`ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`).
- **Accessory Keyboard Toolbar:**
  - Docked Markdown formatting toolbar above soft keyboard (`imePadding()`) for instant syntax insertion.
  - Smart list continuation (`- `, `1. `) and quote continuation (`> `) on Enter keypress.
- **Visual Search Highlighting:**
  - Chained `SearchHighlightVisualTransformation` over code syntax highlighting.
  - Case-sensitive ("Aa") search toggle and cyclical match navigation.

---

## [0.5.0] - 2026-09-11

### Added
- **Live Preview & Split-View Mode:**
  - Split-view container with responsive dual-pane layout on tablets/foldables and stacked layout on compact screens.
  - Bidirectional loop-free scroll synchronization coordinator (`rememberSynchronizedScroll`).
- **Fenced Code Block Syntax Highlighting:**
  - Domain lexical tokenizer (`CodeSyntaxTokenizer`, `RegexCodeSyntaxTokenizer`) supporting Kotlin, Java, Python, JS/TS, JSON, SQL, XML, Markdown.
  - Dark and light theme-aware Compose `CodeSyntaxVisualTransformation`.
- **Document Export Pipeline:**
  - Semantic HTML export with responsive typography and print stylesheets (`ExportHtmlUseCase`).
  - Native PDF generation and direct printing via Android `PrintManager` and `WebView.createPrintDocumentAdapter`.
- **Table of Contents (ToC):**
  - AST-derived heading extraction and navigation sheet with animated scroll-to-block.
- **Multi-Block Find & Replace:**
  - Search across all document blocks with cyclical navigation and single/bulk replace backed by Myers diff undo snapshots.

---

## [0.4.0] - 2026-09-11

### Added
- **Block-Based Compose Editor UI:**
  - Jetpack Compose `LazyColumn` with keyed items per `BlockId` ensuring isolated recomposition.
  - Dedicated block composables: `ParagraphBlockView`, `HeadingBlockView`, `CodeBlockView`, `ListItemBlockView`, `BlockQuoteView`, `ThematicBreakView`.
  - Enter key block splitting and Backspace block merging with focus traversal.
  - Unidirectional Data Flow MVI architecture (`EditorViewModel`, `EditorUiState`, `EditorIntent`, `EditorEffect`).

---

## [0.3.0] - 2026-09-11

### Added
- **Room Persistence Layer:**
  - Room database schema (`DocumentMetadataEntity`, `BlockEntity`, `SnapshotEntity`).
  - DAOs with cascade deletion to guarantee referential integrity.
- **Myers Diff Undo/Redo Engine:**
  - Bidirectional delta calculation and patch rollback via `java-diff-utils`.
  - Process-death surviving snapshot history in Room database.
- **Storage Access Framework (SAF) Coordinator:**
  - Persistent document tree coordinator with LRU grant eviction (< 120 grants threshold).
  - Debounced auto-save coordinator offloading file I/O to `Dispatchers.IO`.

---

## [0.2.0] - 2026-09-10

### Added
- **Domain Modeling & AST Engine:**
  - Pure Kotlin domain entities: `MarkdownDocument`, `MarkdownBlock`, `BlockId`, `BlockType`, `InlineSpan`.
  - Incremental block parser utilizing `commonmark-java` with single-block re-parsing capability.
  - Dedicated background dispatcher (`Dispatchers.Default.limitedParallelism(2)`) and typing debounce.

---

## [0.1.0] - 2026-09-08

### Added
- **Project Bootstrap:**
  - Five-module Clean Architecture Gradle graph (`:core`, `:domain`, `:data`, `:presentation`, `:app`).
  - Centralized Gradle version catalog (`gradle/libs.versions.toml`).
  - Foundation MVI primitives (`MviViewModel`, `UiState`, `UiIntent`, `UiEffect`).
  - Dagger Hilt dependency injection setup and JUnit 5 test platform.
