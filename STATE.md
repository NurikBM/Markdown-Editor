# Project State & Working Memory

## Active State
- **Current Phase:** Phase 1 — Core Foundation & Build Setup
- **Current Milestone:** Milestone 1.3 — Base Hilt Setup & Core DI Graph
- **Active Subtask:** Phase 1 Gate Verification
- **Current Phase:** Phase 2 — Domain Modeling & Incremental AST Engine
- **Current Milestone:** Milestone 2.2 — Incremental AST Engine & Parser Use Cases
- **Active Subtask:** Phase 2 Gate Verification
- **Status:** VERIFIED

## Completed Milestones
- [x] **Milestone 1.1**: Bootstrap multi-module architecture (`:core`, `:domain`, `:data`, `:presentation`, `:app`) with verified Gradle sync and compilation across all layers. Created `AGENTS.md`, `STATE.md`, and version catalog `gradle/libs.versions.toml`.
- [x] **Milestone 1.2**: Implemented core concurrency abstractions (`DispatcherProvider`, `DefaultDispatcherProvider` with `Dispatchers.Default.limitedParallelism(2)`), unified logging interface (`Logger`, `AndroidLogger`) in `:core`, and foundational MVI primitives (`MviViewModel`, `UiState`, `UiIntent`, `UiEffect`) in `:presentation`. Configured JUnit 5 Platform test runner with `junit-platform-launcher` and verified unit tests in `:core` and `:presentation`.
- [x] **Milestone 1.3**: Configured baseline Hilt dependency injection root (`@HiltAndroidApp` on `MarkdownApplication`, manifest registration, `@AndroidEntryPoint` on `MainActivity`, and `CoreModule` providing `DispatcherProvider` and `Logger` singletons in `:app`). Full APK assembly verified.
- [x] **Milestone 2.1**: Modeled pure Kotlin domain entities (`MarkdownDocument`, `MarkdownBlock`, `BlockId`, `BlockType`, `InlineSpan`) and typed error sealed hierarchies (`DomainError` for Storage, Parsing, and PatchConflict) in `:domain`. Zero Android SDK dependencies maintained.
- [x] **Milestone 2.2**: Implemented `MarkdownBlockParser` and `CommonmarkBlockParser` utilizing `commonmark-java` with incremental single-block update capabilities. Created `ParseDocumentUseCase`, `UpdateBlockUseCase` with `limitedParallelism(2)` dispatcher binding, and typing debounce extension (`debounceTyping(350L)`). Full unit test suite verified on JUnit Platform.

## Immediate Next Steps
1. **Phase 2 Gate**: Prepare domain entities (`MarkdownDocument`, `MarkdownBlock`, `BlockId`, `BlockType`, `InlineSpan`) in `:domain`.
2. **Subtask 2.1**: Implement `MarkdownBlockParser` utilizing `commonmark-java` with incremental block boundary extraction and re-parsing.
3. **Subtask 2.2**: Wire parser coroutine execution with `limitedParallelism(2)` and typing debounce.
1. **Phase 3 Gate**: Data Layer & Persistence Pipeline.
2. **Subtask 3.1**: Design Room database schema (`SnapshotEntity`, `DocumentMetadataEntity`, `BlockEntity`) and DAOs in `:data`.
3. **Subtask 3.2**: Implement `DiffEngine` using `java-diff-utils` for bidirectional Myers patch generation and rollback.
4. **Subtask 3.3**: SAF document tree coordinator with LRU permission eviction (< 120 grants).

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
| 2026-09-10 | Modern Toolchain & SDK 37 Baseline Alignment  | Synchronized baseline with AGP 9.4.0, Kotlin 2.4.20, Gradle 9.7.1, Compile/Target SDK 37, Compose BOM 2026.08.00, Room 2.8.4, commonmark 0.30.0, java-diff-utils 4.17, JUnit 6.1.3 BOM.         | ACCEPTED |
| 2026-09-10 | Native StateFlow MVI & Zero-Dep Core Module   | Implemented custom `MviViewModel` container in `:presentation` avoiding 3rd-party MVI frameworks; kept `:core` free of Dagger/Hilt annotations by delegating DI provision to `:app/CoreModule`.   | ACCEPTED |
| 2026-09-10 | Incremental Block AST Engine                  | Implemented `CommonmarkBlockParser` decomposing Markdown into discrete `MarkdownBlock` items with persistent `BlockId`. Single-block update avoids full-document re-parsing.                      | ACCEPTED |
| 2026-09-10 | Asynchronous AST Dispatcher & Debounce        | Wired `ParseDocumentUseCase` and `UpdateBlockUseCase` to `Dispatchers.Default.limitedParallelism(2)` with standard 350 ms typing debounce (`debounceTyping`).                                     | ACCEPTED |

## Technical Baseline & Chosen Versions
- **Kotlin:** `2.4.20`
- **Android Gradle Plugin (AGP):** `9.4.0`
- **Gradle:** `9.7.1`
- **Compose BOM:** `2026.08.00`
- **Room:** `2.8.4`
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
- **Last Verified State:** Phase 1 complete. Full unit test suite (`.\gradlew.bat test`) and debug APK assembly (`.\gradlew.bat assembleDebug`) verified with 130/130 tasks passing and 0 errors. Concurrency dispatchers, MVI primitives, logging, Hilt injection graph, and JUnit Platform runner fully operational.
- **Exact Resumption Command/Action:** Proceed to Phase 2, Milestone 2.1: Domain Modeling & Incremental AST Engine (`MarkdownDocument`, `MarkdownBlock`, `BlockId`, `BlockType`, `InlineSpan`).
- **Last Verified State:** Phase 2 complete. Full unit test suite (`.\gradlew.bat test`) verified with 91/91 tasks passing across `:core`, `:domain`, `:presentation`, and `:app` with 0 errors. AST parser, single-block incremental update, domain error hierarchies, and debounce use cases fully verified.
- **Exact Resumption Command/Action:** Proceed to Phase 3, Milestone 3.1: Data Layer & Persistence Pipeline (Room database schema, DAOs, Myers diff engine).
