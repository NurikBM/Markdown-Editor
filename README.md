# MyApplication

A clean, modern Android application structured for deterministic builds, rapid onboarding, and token-efficient AI-assisted development.

---

## ⚡ Quick Start (< 3 Minutes)

### Prerequisites
- **JDK:** OpenJDK 21 (LTS) installed and `JAVA_HOME` configured.
- **Android SDK:** Platform API 37, Build-Tools installed (typically via Android Studio or command-line tools).
- **Git:** Installed on system path.

### 1. Clone & Navigate
```bash
git clone https://github.com/<username>/MyApplication.git
cd MyApplication
```

### 2. Configure Environment
Set Android SDK location in `local.properties` (do not commit this file):
- **Windows:**
  ```cmd
  echo sdk.dir=C:\\Users\\%USERNAME%\\AppData\\Local\\Android\\Sdk > local.properties
  ```
- **macOS / Linux:**
  ```bash
  echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
  ```

### 3. Build & Test
Run the deterministic build commands using the bundled Gradle wrapper:

- **Run Unit Tests:**
  - Windows: `.\gradlew.bat test`
  - macOS/Linux: `./gradlew test`

- **Build Debug APK:**
  - Windows: `.\gradlew.bat assembleDebug`
  - macOS/Linux: `./gradlew assembleDebug`
  - Output artifact: `app/build/outputs/apk/debug/app-debug.apk`

---

## 🏗️ Architecture & Directives for AI Agents
This repository uses strict state anchors to eliminate context drift and optimize token usage:
- See [`AGENTS.md`](./AGENTS.md) for immutable architectural constraints, tech stack specifications, and canonical commands.
- See [`STATE.md`](./STATE.md) for active milestones, atomic task checklist, decision logs, and session handoffs.

---

## 🧪 CI/CD
Continuous Integration is automated via GitHub Actions on every pull request and push to `main`:
- Lint checks and static analysis.
- Unit test suite validation (`testDebugUnitTest`).
- Debug APK compilation (`assembleDebug`).

