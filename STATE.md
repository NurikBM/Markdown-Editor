# Project State & Working Memory

## Active State
- **Current Phase:** Phase 4 — Block-Based Presentation Layer (MVP Core)
- **Current Milestone:** Milestone 4.1 — Editor MVI ViewModel & Block Split/Merge Reducers
- **Active Subtask:** Subtask 4.1.1 — EditorViewModel, EditorUiState, EditorIntent, and EditorEffect
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

## Immediate Next Steps
1. **Milestone 4.1**: Implement `EditorViewModel` with MVI contracts (`EditorUiState`, `EditorIntent`, `EditorEffect`) in `:presentation`.
2. **Milestone 4.2**: Implement block split handling (Enter keystroke splits block at cursor) and block merge handling (Backspace at block start merges with preceding block).
3. **Milestone 4.3**: Build block-based `LazyColumn` Compose UI with keyed items per `BlockId` and isolated recomposition.
4. **Milestone 4.4**: Wire Undo/Redo actions to `SnapshotRepository` and `DiffEngine`.

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
- **Last Verified State:** Phase 3 fully completed and verified. 100% test pass rate across all 5 modules (`.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat test`), full APK generation (`.\gradlew.bat assembleDebug`) verified with 0 warnings/errors. Room persistence, Myers diff engine, SAF LRU eviction coordinator, and debounced auto-save pipeline operational.
- **Exact Resumption Command/Action:** Proceed to Phase 4 — Block-Based Presentation Layer (MVP Core), starting with Milestone 4.1: EditorViewModel, EditorUiState, EditorIntent, EditorEffect, and block split/merge reducer logic.
