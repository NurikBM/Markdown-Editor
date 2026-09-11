# Project State & Working Memory

## Active State
- **Current Phase:** Phase 5 — High-Value Core Enhancements
- **Current Milestone:** Milestone 5.2 — Document Export Pipeline (HTML & PDF)
- **Active Subtask:** Subtask 5.2.1 — Implement HTML & PDF export use cases with typed domain error handling
- **Status:** READY

## Completed Milestones
- [x] **Milestone 1.1**: Bootstrap multi-module architecture (`:core`, `:domain`, `:data`, `:presentation`, `:app`) with verified Gradle sync and compilation across all layers. Created `AGENTS.md`, `STATE.md`, and version catalog `gradle/libs.versions.toml`.
- [x] **Milestone 1.2**: Implemented core concurrency abstractions (`DispatcherProvider`, `DefaultDispatcherProvider` with `Dispatchers.Default.limitedParallelism(2)`), unified logging interface (`Logger`, `AndroidLogger`) in `:core`, and foundational MVI primitives (`MviViewModel`, `UiState`, `UiIntent`, `UiEffect`) in `:presentation`. Configured JUnit 5 Platform test runner with `junit-platform-launcher` and verified unit tests in `:core` and `:presentation`.
- [x] **Milestone 1.3**: Configured baseline Hilt dependency injection root (`@HiltAndroidApp` on `MarkdownApplication`, manifest registration, `@AndroidEntryPoint` on `MainActivity`, and `CoreModule` providing `DispatcherProvider` and `Logger` singletons in `:app`). Full APK assembly verified.
- [x] **Milestone 2.1**: Modeled pure Kotlin domain entities (`MarkdownDocument`, `MarkdownBlock`, `BlockId`, `BlockType`, `InlineSpan`) and typed error sealed hierarchies (`DomainError` for Storage, Parsing, and PatchConflict) in `:domain`. Zero Android SDK dependencies maintained.
- [x] **Milestone 2.2**: Implemented `MarkdownBlockParser` and `CommonmarkBlockParser` utilizing `commonmark-java` with incremental single-block update capabilities. Created `ParseDocumentUseCase`, `UpdateBlockUseCase` with `limitedParallelism(2)` dispatcher binding, and typing debounce extension (`debounceTyping(350L)`). Full unit test suite verified on JUnit Platform.
- [x] **Milestone 3.1**: Implemented Room persistence schema (`DocumentMetadataEntity`, `BlockEntity`, `SnapshotEntity`), DAOs (`DocumentDao`, `BlockDao`, `SnapshotDao`), and `MarkdownDatabase` in `:data`.
- [x] **Milestone 3.2**: Implemented Myers `DiffEngine` using `java-diff-utils` for bidirectional patch generation and rollback. Verified with `DiffEngineTest`.
- [x] **Milestone 3.3**: Implemented SAF Document Tree Coordinator (`SafTreeCoordinator`) enforcing Android persistent grant LRU eviction (< 120 grants threshold) and `SecurityException` fault recovery. Verified with `SafTreeCoordinatorTest`.
- [x] **Milestone 3.4**: Implemented domain repositories (`RoomMarkdownRepository`, `RoomSnapshotRepository`) and debounced auto-save coordinator (`AutoSaveCoordinator`) with typed error translation and background worker offloading (`Dispatchers.IO` and `limitedParallelism(2)`). Verified with `RoomMarkdownRepositoryTest`, `RoomSnapshotRepositoryTest`, and `AutoSaveCoordinatorTest`.
- [x] **Milestone 3.5**: Configured Hilt dependency injection modules (`DataModule`, `DomainModule`) in `:app` providing persistence, parsers, and repositories. Full unit test suite (98/98 tasks) and debug APK assembly verified.
- [x] **Milestone 4.1**: Implemented pure Clean Architecture block manipulation use cases in `:domain` (`SplitBlockUseCase`, `MergeBlockUseCase`, `UndoBlockUseCase`, `RedoBlockUseCase`, `DiffCalculator`), `EditorViewModel` with MVI contracts (`EditorUiState`, `EditorIntent`, `EditorEffect`), and verified unit tests with Turbine and MockK across `:domain` and `:presentation`.
- [x] **Milestone 4.2**: Implemented Jetpack Compose block UI components (`MarkdownBlockItem`, `ParagraphBlockView`, `HeadingBlockView`, `CodeBlockView`, `ListItemBlockView`, `BlockQuoteView`, `ThematicBreakView`) keyed by `BlockId` in `LazyColumn`, `EditorTopBar` with Undo/Redo/Save, `EditorScreen`, and integrated with `MainActivity` via Hilt `EditorViewModelFactory`.
- [x] **Milestone 4.3**: Verified MVP block editing pipeline, UI recomposition isolation, focus traversal on split/merge, undo/redo deltas, and debug APK assembly.
- [x] **Milestone 5.1**: Implemented rich Markdown live preview renderer (`MarkdownPreviewBlockItem`, `MarkdownPreviewPane`), split-view container with adaptive responsive layouts (side-by-side on wide screens, stacked on compact screens), bidirectional synchronized scrolling coordinator (`rememberSynchronizedScroll`), and mode switcher in `EditorTopBar` and `EditorViewModel`. Verified unit tests and debug APK assembly.

## Immediate Next Steps
1. **Milestone 5.2**: Document Export Pipeline (HTML & PDF generation via Android print adapter with typed domain errors).
2. **Milestone 5.3**: Syntax highlighting for fenced code blocks using regex-based tokenization.
3. **Milestone 5.4**: Table of Contents (ToC) generation directly from AST Heading blocks with click-to-scroll navigation.
4. **Milestone 5.5**: In-document Find & Replace across block boundaries.

## Key Architecture Decisions (ADR Log)
| Date       | Decision                                      | Context & Rationale                                                                                                                                                                                | Status   |
|:-----------|:----------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:---------|
| 2026-09-08 | Multi-module Clean Architecture Graph         | Separated `:domain` (pure Kotlin/JVM), `:core` (Android lib), `:data` (Android lib), `:presentation` (Android lib), and `:app` (Android application) to enforce compile-time layer isolation.      | ACCEPTED |
| 2026-09-08 | Version Catalog (`libs.versions.toml`)        | Pinned all dependency versions across Kotlin, Compose, Room, Hilt, commonmark, java-diff-utils to guarantee reproducible builds.                                                                  | ACCEPTED |
| 2026-09-08 | Pure Kotlin/JVM Domain Layer                  | Kept `:domain` completely free of `com.android.*` dependencies so business logic and AST parser can run in standard JVM unit tests with maximum execution speed.                                   | ACCEPTED |
| 2026-09-08 | Dedicated Dispatcher for AST & Diffs          | Bound AST block parsing and Myers diff computation to `Dispatchers.Default.limitedParallelism(2)` to safeguard UI thread responsiveness while bounding background CPU load.                        | ACCEPTED |
| 2026-09-08 | SAF LRU Grant Eviction Policy                 | Decided to track persistent document tree grants in `DocumentMetadataEntity` with an eviction threshold (< 120 grants) to prevent runtime crashes from the 128 OS grant ceiling.                   | ACCEPTED |
| 2026-09-08 | Block-Level Composable LazyColumn             | Mandated independent Composable items keyed by `BlockId` instead of monolithic text transformations to ensure 60/120 FPS scrolling and isolated recomposition.                                     | ACCEPTED |
| 2026-09-08 | Git Repository Reinitialization & GitHub Push | Reinitialized fresh Git repository with main branch, clean commit history, and synchronized to GitHub origin (`NurikBM/MyApplication.git`).                                                        | ACCEPTED |
| 2026-09-10 | Modern Toolchain & SDK 37 Baseline Alignment  | Synchronized baseline with AGP 9.4.0, Kotlin 2.4.20, Gradle 9.7.1, Compile/Target SDK 37, Compose BOM 2026.09.00, Room 2.8.5, commonmark 0.30.0, java-diff-utils 4.17, JUnit 6.1.3 BOM.         | ACCEPTED |
| 2026-09-10 | Native StateFlow MVI & Zero-Dep Core Module   | Implemented custom `MviViewModel` container in `:presentation` avoiding 3rd-party MVI frameworks; kept `:core` free of Dagger/Hilt annotations by delegating DI provision to `:app/CoreModule`.   | ACCEPTED |
| 2026-09-10 | Incremental Block AST Engine                  | Implemented `CommonmarkBlockParser` decomposing Markdown into discrete `MarkdownBlock` items with persistent `BlockId`. Single-block update avoids full-document re-parsing.                      | ACCEPTED |
| 2026-09-10 | Asynchronous AST Dispatcher & Debounce        | Wired `ParseDocumentUseCase` and `UpdateBlockUseCase` to `Dispatchers.Default.limitedParallelism(2)` with standard 350 ms typing debounce (`debounceTyping`).                                     | ACCEPTED |
| 2026-09-11 | Room Cascade & Normalized Block Persistence   | Stored document blocks in normalized `blocks` table with foreign key to `documents` with `CASCADE` delete to guarantee referential integrity and avoid orphaned rows.                             | ACCEPTED |
| 2026-09-11 | Myers Bidirectional Diff Undo/Redo Engine     | Unified diff patches calculated via `DiffUtils` and `UnifiedDiffUtils` in `DiffEngine` ensuring deterministic forward and reverse transitions across process death.                              | ACCEPTED |
| 2026-09-11 | Debounced Auto-Save & Offloaded Diffing       | Designed `AutoSaveCoordinator` using coroutine job cancellation debounce on `Dispatchers.IO` and Myers diff offloading to `diffAndParsing` dispatcher.                                            | ACCEPTED |
| 2026-09-11 | Pure Domain Block Mutation & Diff Contracts   | Implemented `SplitBlockUseCase`, `MergeBlockUseCase`, `UndoBlockUseCase`, `RedoBlockUseCase`, and `DiffCalculator` in `:domain`, completely decoupled from presentation and persistence.          | ACCEPTED |
| 2026-09-11 | Keyed Block Recomposition & IME Split Handler | Keyed `LazyColumn` items by `BlockId.value` for isolated recomposition; implemented universal newline splitting and cursor placement for Enter and Backspace key gestures.                        | ACCEPTED |
| 2026-09-11 | Live Preview & Bidirectional Scroll Sync      | Built isolated MarkdownPreviewBlockItem interpreting domain InlineSpan and BlockType; implemented loop-free bidirectional LazyColumn scroll synchronization via LazyListState.isScrollInProgress. | ACCEPTED |

## Technical Baseline & Chosen Versions
- **Kotlin:** `2.4.20`
- **Android Gradle Plugin (AGP):** `9.4.0`
- **Gradle:** `9.7.1`
- **Compose BOM:** `2026.09.00`
- **Room:** `2.8.5`
- **Hilt:** `2.60.1`
- **KSP:** `2.3.6`
- **commonmark-java:** `0.30.0`
- **java-diff-utils:** `4.17`
- **Coroutines:** `1.11.0`
- **JUnit 5 (Jupiter):** `6.1.3`
- **MockK:** `1.14.11`
- **Turbine:** `1.2.1`
- **SDK Constraints:** `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`

## Session Handoff Block
- **Last Verified State:** Milestone 5.1 complete and verified. Full unit test suite (`testDebugUnitTest`) verified with 96/96 tasks passing across all 5 modules with 0 warnings/errors. Full APK assembly (`assembleDebug`) verified. Rich preview, adaptive split-view, and synchronized scrolling fully operational.
- **Exact Resumption Command/Action:** Proceed to Milestone 5.2: Document Export Pipeline (HTML and PDF generation via Android print adapter with typed domain errors).
