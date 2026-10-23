# Kontre 🛡️
> **A high-performance Android short-form video blocker targeting Instagram Reels & YouTube Shorts while preserving full access to DMs, chats, stories, and feed posts.**

---

## 🏗️ Core Architecture & Components

### 1. Accessibility Service (`VideoBlockerAccessibilityService`)
- Subclasses Android's `AccessibilityService`.
- Listens to `TYPE_WINDOW_STATE_CHANGED`, `TYPE_VIEW_SCROLLED`, and `TYPE_WINDOW_CONTENT_CHANGED`.
- Target Packages: `com.instagram.android` and `com.google.android.youtube`.
- **BFS & Recursive Tree-Walk**:
  - Traverses `AccessibilityNodeInfo` in the active window.
  - **Resource ID Matching**: Looks for keywords such as `reel`, `shorts_player`, `clips_video_container`, `reel_viewer`.
  - **Content Description Matching**: Detects view labels for terms like `"Reels"`, `"Shorts"`, `"Sound"`, `"Remix"`.
  - **View Hierarchy & Dimensions**: Detects full-screen vertical swipe containers (`ViewPager2` / vertical `RecyclerView` occupying $\ge 90\%$ screen height/width).
  - **Explicit Safeguards**:
    - Direct Messages / Chat: `direct_message_list`, `composer` $\rightarrow$ NEVER flagged as Reels.
    - Stories: `story_progress`, `story_item` $\rightarrow$ NEVER flagged as Reels.
    - Home Feed: `feed_recycler_view` $\rightarrow$ NEVER flagged as Reels.

### 2. Dwell & Debounce Engine
- Prevents false positives from feed scroll-bys.
- Runs a **1.8-second Coroutine dwell timer**:
  - Starts when a short-form signature is first detected.
  - Automatically cancels if the user leaves the node or scrolls past within 1.8 seconds.
  - Increments daily count and triggers the intervention handler only upon intentional completion.

### 3. Non-Intrusive Floating Top Counter & Zero-Overlay Ejection
1. **Live Floating Top Counter Pill**:
   - Pinned at the top center of the screen as an unobtrusive status pill (`[ 🛡️ 18 / 50 ]`).
   - Automatically appears **only** when watching Instagram Reels or YouTube Shorts.
   - Non-touchable (`FLAG_NOT_TOUCHABLE`) so it never blocks taps, buttons, or navigation underneath.
   - Automatically disappears when you navigate back to Chats, Stories, or Feed.
2. **Clean Navigation Ejection (Zero Overlay)**:
   - When the 50-video quota is reached, the pill displays `[ 🔒 50 / 50 LOCKED ]`.
   - Opening a Reel or Short executes `performGlobalAction(GLOBAL_ACTION_BACK)` to immediately step out.
   - **No intrusive fullscreen overlays, no popups, and no dialogs**.

### 4. Foreground Service & Lifecycle Stability (`KontreForegroundService`)
- Sticky `ForegroundService` (`START_STICKY`) with ongoing notification to prevent Android's OOM low-memory killer.
- Configured with `foregroundServiceType="specialUse"` and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` for Android 14+ compliance.
- Supports battery optimization exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).

---

## 📁 Source Code Map

```
ReelShield/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml                        # Permissions & Services configuration
│   │   ├── res/
│   │   │   ├── xml/accessibility_service_config.xml    # Event types & package filter
│   │   │   ├── layout/layout_reel_block_overlay.xml   # In-app habit friction overlay
│   │   │   ├── drawable/                              # Adaptive icons & button shapes
│   │   │   └── values/themes.xml                      # Dark themes & colors
│   │   └── java/com/reelshield/app/
│   │       ├── data/QuotaManager.kt                   # 50-video quota & midnight reset
│   │       ├── service/
│   │       │   ├── VideoBlockerAccessibilityService.kt # BFS heuristics & dwell engine
│   │       │   ├── BlockingOverlayService.kt          # TYPE_APPLICATION_OVERLAY
│   │       │   └── KontreForegroundService.kt         # Sticky FGS lifecycle watcher
│   │       ├── ui/
│   │       │   ├── BlockOverlayActivity.kt            # Fullscreen lockout activity
│   │       │   └── theme/                             # Jetpack Compose theme tokens
│   │       └── MainActivity.kt                        # Jetpack Compose dashboard
│   └── build.gradle.kts                               # AGP 8.7 & Kotlin 2.0 (minSdk 26, targetSdk 35)
├── gradle/libs.versions.toml
└── settings.gradle.kts
```

---

## 📲 Installation & Build

```powershell
# 1. Build Debug APK with JDK 17
$env:JAVA_HOME = "c:\Avv\p.proj\jdk17\jdk-17.0.20.1+1"
cd c:\Avv\p.proj\ReelShield
.\gradlew.bat assembleDebug

# 2. Push & Install via ADB
adb push app\build\outputs\apk\debug\app-debug.apk /data/local/tmp/app.apk
adb shell pm install -r -d -t /data/local/tmp/app.apk

# 3. Enable Accessibility & Overlay
adb shell settings put secure enabled_accessibility_services com.reelshield.app/com.reelshield.app.service.VideoBlockerAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell appops set com.reelshield.app SYSTEM_ALERT_WINDOW allow
```

For the comprehensive architecture breakdown, heuristics guide, and bug fix engineering log, see [`ARCHITECTURE.md`](ARCHITECTURE.md).

---

## 🚀 Automated Builds & GitHub Actions
- **Continuous Integration**: Every push to `main` automatically runs `./gradlew assembleRelease` via GitHub Actions and publishes the signed `Kontre-release.apk` as a build artifact.
- **Releases**: Creating and pushing a tag (e.g. `v1.0.0`) automatically publishes a GitHub Release with the attached `Kontre-release.apk` ready to download directly on any phone.
