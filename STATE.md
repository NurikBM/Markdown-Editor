# Project State & Working Memory

## Active State
- **Current Phase:** Phase 1 — Core Foundation & Build Setup
- **Current Milestone:** Milestone 1.1 — Modular Project Structure & Tooling Baseline
- **Active Subtask:** Subtask 1.1.1 — Multi-module Gradle initialization & governance setup
- **Status:** VERIFIED

## Completed Milestones
- [x] **Milestone 1.1**: Bootstrap multi-module architecture (`:core`, `:domain`, `:data`, `:presentation`, `:app`) with verified Gradle sync and compilation across all layers. Created `AGENTS.md`, `STATE.md`, and version catalog `gradle/libs.versions.toml`.

## Immediate Next Steps
1. **Subtask 1.2**: Implement core MVI primitives (`MviViewModel`, `UiState`, `UiIntent`, `UiEffect`) and dispatcher abstractions in `:core` / `:presentation`.
2. **Subtask 1.3**: Wire baseline Hilt injection (`@HiltAndroidApp`, application module) in `:app`.
3. **Phase 2 Gate**: Prepare domain entities (`MarkdownDocument`, `MarkdownBlock`, `BlockId`) and incremental `commonmark-java` parser.

## Key Architecture Decisions (ADR Log)
| Date       | Decision                                      | Context & Rationale                                                                                                                                                                                | Status   |
|:-----------|:----------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:---------|
| 2026-09-08 | Multi-module Clean Architecture Graph         | Separated `:domain` (pure Kotlin/JVM), `:core` (Android lib), `:data` (Android lib), `:presentation` (Android lib), and `:app` (Android application) to enforce compile-time layer isolation.      | ACCEPTED |
| 2026-09-08 | Version Catalog (`libs.versions.toml`)        | Pinned all dependency versions across Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.10.01, Room 2.6.1, Hilt 2.60.1, commonmark 0.24.0, java-diff-utils 4.15 to guarantee reproducible builds. | ACCEPTED |
| 2026-09-08 | Pure Kotlin/JVM Domain Layer                  | Kept `:domain` completely free of `com.android.*` dependencies so business logic and AST parser can run in standard JVM unit tests with maximum execution speed.                                   | ACCEPTED |
| 2026-09-08 | Dedicated Dispatcher for AST & Diffs          | Bound AST block parsing and Myers diff computation to `Dispatchers.Default.limitedParallelism(2)` to safeguard UI thread responsiveness while bounding background CPU load.                        | ACCEPTED |
| 2026-09-08 | SAF LRU Grant Eviction Policy                 | Decided to track persistent document tree grants in `DocumentMetadataEntity` with an eviction threshold (< 120 grants) to prevent runtime crashes from the 128 OS grant ceiling.                   | ACCEPTED |
| 2026-09-08 | Block-Level Composable LazyColumn             | Mandated independent Composable items keyed by `BlockId` instead of monolithic text transformations to ensure 60/120 FPS scrolling and isolated recomposition.                                     | ACCEPTED |
| 2026-09-08 | Git Repository Reinitialization & GitHub Push | Reinitialized fresh Git repository with main branch, clean commit history, and synchronized to GitHub origin (`NurikBM/MyApplication.git`).                                                        | ACCEPTED |

## Technical Baseline & Chosen Versions
- **Kotlin:** `2.0.21`
- **Android Gradle Plugin (AGP):** `9.3.2`
- **Gradle:** `9.5.0`
- **Compose BOM:** `2024.10.01`
- **Room:** `2.6.1`
- **Hilt:** `2.60.1`
- **KSP:** `2.0.21-1.0.28` (strictly synchronized with Kotlin `2.0.21`)
- **commonmark-java:** `0.24.0`
- **java-diff-utils:** `4.15`
- **Coroutines:** `1.9.0`
- **JUnit 5 (Jupiter):** `5.10.2`
- **MockK:** `1.13.13`
- **Turbine:** `1.2.0`
- **SDK Constraints:** `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`

## Session Handoff Block
- **Last Verified State:** Clean Git repository initialized and verified locally (commit `22cca44`). Remote unlinked from `MyApplication` to preserve user's other repository. Full debug APK build (`:app:assembleDebug`) verified with 124/124 tasks passing and 0 errors. Resource linking and XML theme resolved.
- **Exact Resumption Command/Action:** Proceed to Phase 1, Subtask 1.2: Implement MVI primitives (`MviViewModel`, `UiState`, `UiIntent`, `UiEffect`) in `:core` and `:presentation`.
