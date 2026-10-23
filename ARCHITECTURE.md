# Kontre: Short-Form Video Blocker & Focus Guard
> **Comprehensive Architecture, Implementation, Bug Fixes & Verification Guide**

**Kontre** is a production-grade native Android digital wellbeing application built with **Kotlin** and **Jetpack Compose** (target SDK 35, Android 15 ready). It is engineered to break mindless doom-scrolling habits across **Instagram Reels** (Standard & Lite) and **YouTube Shorts** without resorting to blunt app-level blockers that break communication or feed browsing.

Unlike aggressive app blockers that shut down Instagram or YouTube entirely, Kontre preserves **Direct Messages (DMs)**, **Chats**, **General/Primary tabs**, **Stories**, and **Home Feed post browsing**, while selectively detecting, capping, and ejecting users from immersive short-form video loops.

---

## 📑 Table of Contents
1. [Core Features & Design Philosophy](#1-core-features--design-philosophy)
2. [Architecture & Component Map](#2-architecture--component-map)
3. [Detection & Protection Engine (`VideoBlockerAccessibilityService`)](#3-detection--protection-engine)
4. [Dashboard & Testing Simulation Lab in `MainActivity`](#4-dashboard--testing-simulation-lab)
5. [Complete Engineering & Debugging Log (Root Cause Analyses)](#5-complete-engineering--debugging-log)
   - [5.1 Home Screen Feed Scrolling vs Standalone Reel Ejection](#51-home-screen-feed-scrolling-vs-standalone-reel-ejection)
   - [5.2 Direct Messages (DMs) & "General" Tab Ejection Fix](#52-direct-messages-dms--general-tab-ejection-fix)
   - [5.3 Instagram Lite & FB-Lite Architecture Support](#53-instagram-lite--fb-lite-architecture-support)
   - [5.4 Tap-from-Feed & Tap-from-DMs Universal Interception](#54-tap-from-feed--tap-from-dms-universal-interception)
   - [5.5 False Chat Collision via "Direct Share" & "Direct Tab"](#55-false-chat-collision-via-direct-share--direct-tab)
   - [5.6 Active Typing & IME Software Keyboard Immunity](#56-active-typing--ime-software-keyboard-immunity)
   - [5.7 Samsung One UI & System Launcher Immunity](#57-samsung-one-ui--system-launcher-immunity)
   - [5.8 Instagram Stories & Shared Reels in Stories Immunity](#58-instagram-stories--shared-reels-in-stories-immunity)
6. [QA & Verification Test Matrix](#6-qa--verification-test-matrix)
7. [Build, Deployment & Shell Commands Guide](#7-build-deployment--shell-commands-guide)

---

## 1. Core Features & Design Philosophy

- **Zero Intrusive Full-Screen Overlays**:
  No aggressive blocking modal dialogs or lock screens over your phone. ReelShield uses a clean, non-intrusive floating status HUD pill at the top of the display (`[ 🛡️ 18 / 50 ]` or `[ 🔒 50 / 50 LOCKED ]`) while watching short-form videos. It automatically hides when you navigate away to chats, stories, or feeds.
- **50 Daily Video Allowance**:
  Users receive a daily quota of 50 short-form videos. Counters automatically reset every night at midnight (`00:00`).
- **Permanent Strict Mode (Instant Zero-Delay Ejection)**:
  Short-form videos (Instagram Reels & YouTube Shorts) are now permanently set to Strict Mode. As soon as an immersive Reel or Short is detected, ReelShield immediately triggers a backward navigation (`GLOBAL_ACTION_BACK`) with zero dwell delay. The toggle button has been removed from the UI as it is now the standard behavior.
- **Home Feed & Post Scrolling Immune**:
  Users can freely browse, scroll, read captions, and inspect regular posts (photos, carousels, and inline videos) on their Home feed without being kicked out.
- **Full Direct Messaging (DM) Shield**:
  Complete immunity inside message inboxes, chat threads, media galleries, and filter tabs (including "Primary" and "General").

---

## 2. Architecture & Component Map

```
┌─────────────────────────────────────────────────────────────┐
│                       Target Apps                           │
│  com.instagram.android | com.instagram.lite | YouTube       │
└──────────────────────────────┬──────────────────────────────┘
                               │ Accessibility Events
                               ▼
┌─────────────────────────────────────────────────────────────┐
│            VideoBlockerAccessibilityService                 │
│  - Event Filter (Package, Config, Toggle check)             │
│  - IME / Keyboard State Detector (TYPE_INPUT_METHOD)        │
│  - DM & Chat Guard (isInstagramTextingOrChatting)           │
│  - Home Feed Guard (isInstagramHomeFeed)                    │
│  - Fullscreen Reel Detector (isDedicatedFullscreenReel)     │
│  - 1.8s Dwell Engine (serviceScope Coroutine)               │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
       (Quota Reached)                 (Reels Active)
               ▼                              ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│   triggerIntervention()      │ │    Floating Counter Pill   │
│  - Backpress Action          │ │  - Top Window Overlay      │
│  - GLOBAL_ACTION_BACK        │ │  - TYPE_ACCESSIBILITY_...  │
│  - Abort if Home or DM       │ │  - FLAG_NOT_TOUCHABLE      │
└──────────────────────────────┘ └────────────────────────────┘
```

### Key Source Files

| File | Purpose |
| :--- | :--- |
| [`VideoBlockerAccessibilityService.kt`](file:///c:/Avv/p.proj/ReelShield/app/src/main/java/com/reelshield/app/service/VideoBlockerAccessibilityService.kt) | Core accessibility inspection engine, dimension validator, dwell timer, and auto-ejection controller. |
| [`QuotaManager.kt`](file:///c:/Avv/p.proj/ReelShield/app/src/main/java/com/reelshield/app/data/QuotaManager.kt) | Manages daily quota (50), reels/shorts counts, midnight reset timers, and blocking toggles. |
| [`MainActivity.kt`](file:///c:/Avv/p.proj/ReelShield/app/src/main/java/com/reelshield/app/MainActivity.kt) | Jetpack Compose dark-mode dashboard with live ticker, permission checklist, toggles, and simulation lab. |
| [`ReelShieldForegroundService.kt`](file:///c:/Avv/p.proj/ReelShield/app/src/main/java/com/reelshield/app/service/ReelShieldForegroundService.kt) | Persistent sticky service (`START_STICKY`) maintaining process priority against Android OOM killer. |
| [`accessibility_service_config.xml`](file:///c:/Avv/p.proj/ReelShield/app/src/main/res/xml/accessibility_service_config.xml) | Declares package targets (`com.instagram.android`, `com.instagram.lite`, `com.google.android.youtube`) and event flags. |

---

## 3. Detection & Protection Engine

The detection system inside [`VideoBlockerAccessibilityService.kt`](file:///c:/Avv/p.proj/ReelShield/app/src/main/java/com/reelshield/app/service/VideoBlockerAccessibilityService.kt) executes a prioritized decision tree on every accessibility event:

```
[Accessibility Event Received]
  │
  ├─ 1. Package check: Is it our own app? ──► IGNORE
  ├─ 2. Package check: Supported package? ──► IGNORE if not
  ├─ 3. Toggle check: Is blocking enabled? ──► IGNORE if toggled off
  ├─ 4. Keyboard check: Is IME open? ──► CANCEL DWELL & HIDE PILL (User is typing)
  │
  ▼ [detectShortFormVideo()]
  │
  ├─ 1. Typing / Focus Check: isUserActivelyTyping() ──► RETURN FALSE
  ├─ 2. Stories Check: story_progress present? ──► RETURN FALSE
  ├─ 3. DMs & Chat Check: isInstagramTextingOrChatting()
  │     (Inbox, General tab, Primary tab, Composer) ──► RETURN FALSE
  ├─ 4. Fullscreen Reel Signature: hasDedicatedFullscreenReel()
  │     (clips_viewer_view_pager occupying >= 75% height) ──► RETURN TRUE (BLOCK!)
  ├─ 5. Home Feed Check: isInstagramHomeFeed()
  │     (feed_recycler_view or Home tab selected) ──► RETURN FALSE (ALLOW FEED SCROLLING!)
  └─ 6. Generic/Lite Fullscreen Player Check ──► RETURN TRUE if >= 75% height with Reel metadata
```

### Heuristic Methods Reference

1. **`isDedicatedFullscreenReel(node)`**:
   Measures node screen bounds. Requires `height >= screenHeight * 0.75f` (75%) and `width >= screenWidth * 0.85f` (85%). In-feed posts never exceed 55–60% height because they share the display with the top header, post author info, like/comment buttons, caption, and bottom tab bar.
2. **`isInstagramHomeFeed(root)`**:
   Returns `true` if `feed_recycler_view`, `sticky_header_list`, or `main_feed_container` is present, or if the bottom Home/Feed tab is selected (`isInstagramHomeTabSelected == true`), provided no dedicated fullscreen viewer is open on top.
3. **`isInstagramTextingOrChatting(root)`**:
   Protects Direct Messages. Matches known DM view IDs (`inbox_recycler_view`, `direct_message_list`, `direct_inbox_filter_bar`, etc.) and performs tree-walk keyword inspection for `"General"`, `"Primary"`, `"Requests"`, `"Messages"`, `"Chats"`, and composer placeholders (`"Message..."`, `"Send message"`). Returns `false` only if a dedicated fullscreen Reel viewer is actively open on top.
4. **`hasDedicatedFullscreenReel(root)`**:
   Detects `com.instagram.android:id/clips_viewer_view_pager`, `root_clips_layout`, or `clips_viewer_container` with fullscreen bounds ($\ge 75\%$), or detects that the bottom Reels tab is selected.
5. **`isUserActivelyTyping(root)`**:
   Checks system input method window (`TYPE_INPUT_METHOD`), system input focus (`FOCUS_INPUT`), and focused editable nodes. Merely having an unfocused `EditText` in the background does not grant immunity.

---

## 4. Dashboard & Testing Simulation Lab

[`MainActivity.kt`](file:///c:/Avv/p.proj/ReelShield/app/src/main/java/com/reelshield/app/MainActivity.kt) exposes a Jetpack Compose dashboard equipped with a testing suite for on-device QA:

| Control | Action / Scenario Verified |
| :--- | :--- |
| **`+1 Reel`** | Manually increments Instagram Reel counter by 1. |
| **`+1 Short`** | Manually increments YouTube Short counter by 1. |
| **`Set to 25/50`** | Instantly sets progress to 50% (halfway milestone). |
| **`Set to 40/50 (Warn)`** | Puts counter in warning state (yellow progress bar & ⚠️ pill icon). |
| **`Set to 49/50`** | Sets counter to 1 video before daily lock (tests threshold transition). |
| **`Lock 50/50 Now`** | Immediately activates locked state (tests instant ejection). |
| **`Pill Normal`** | Previews normal floating HUD pill `[ 🛡️ 12 / 50 ]` for 4 seconds. |
| **`Pill Warn ⚠️`** | Previews warning floating HUD pill `[ ⚠️ 45 / 50 ]` for 4 seconds. |
| **`Pill Lock 🔒`** | Previews locked red floating HUD pill `[ 🔒 50 / 50 LOCKED ]` for 4 seconds. |
| **`Test Eject (Back)`** | Directly invokes `GLOBAL_ACTION_BACK` via the accessibility service. |
| **`Simulate Watch`** | Simulates a 1.8-second dwell session with live HUD display. |
| **`Reset All Counters`** | Clears counters and returns state back to 0/50. |

---

## 5. Complete Engineering & Debugging Log

### 5.1 Home Screen Feed Scrolling vs Standalone Reel Ejection
- **Problem**: When staying on the Instagram Home screen for a few seconds, the user was kicked out of the app to the phone's launcher. The user was unable to scroll feed posts, but requested to scroll the Home feed freely while ensuring that tapping any Reel triggers a block.
- **Root Cause**:
  In-feed vertical video posts on Instagram occupy ~55% of the screen height. The previous dimension check `isFullScreenNode` had a minimum height threshold of `45%`. As a result, every video post in the Home feed matched `clips_video_container`, `clips_media_component`, and BFS descriptions (`"Reel by ... double tap to play or pause"`). After the 1.8-second dwell timer (or immediately upon lockout), the service fired `GLOBAL_ACTION_BACK`. On the Home screen, BACK exits the app to the device launcher.
- **Solution**:
  1. Implemented `isDedicatedFullscreenReel` requiring **$\ge 75\%$ screen height** and **$\ge 85\%$ screen width**. In-feed posts never exceed 55–60% height.
  2. Implemented `isInstagramHomeFeed` to detect the Home feed list and Home tab. If on the Home feed and no dedicated fullscreen viewer is open, detection returns `false`.
  3. When a user taps a Reel on the Home feed, Instagram opens `ClipsViewerFragment`. The fullscreen ViewPager (`clips_viewer_view_pager`) occupies $\ge 75\%$ height, which `hasDedicatedFullscreenReel` detects immediately, releasing Home immunity and firing `GLOBAL_ACTION_BACK` to block the Reel and return the user to the feed.

### 5.2 Direct Messages (DMs) & "General" Tab Ejection Fix
- **Problem**: Opening Direct Messages (text) kicked the user back to the Home screen. Switching to the "General" filter tab inside DMs also kicked the user back to Home.
- **Root Cause**:
  1. `isInstagramTextingOrChatting` was inadvertently removed from `detectInstagramReels` in a previous build, leaving DMs with no immunity when the keyboard was closed.
  2. Shared Reel preview thumbnails and video attachments inside message lists matched BFS descriptions.
  3. The DM filter tabs ("General", "Primary", "Requests") lacked explicit text/description matchers for Instagram Lite.
  4. Firing `GLOBAL_ACTION_BACK` inside DMs navigated the user back to the Home screen.
- **Solution**:
  1. Re-introduced and prioritized `isInstagramTextingOrChatting` at the very top of `detectInstagramReels`.
  2. Added explicit text and content description matching for `"General"`, `"Primary"`, `"Requests"`, `"Channels"`, `"Messages"`, and `"Chats"`.
  3. Added double-guard in `triggerIntervention()`: if `isInstagramTextingOrChatting()` is true, intervention immediately aborts.

### 5.3 Instagram Lite & FB-Lite Architecture Support
- **Problem**: Instagram Lite (`com.instagram.lite`) uses a lightweight UI engine (derived from FB Lite) with minimal standard Android resource IDs (`com.instagram.android:id/...` IDs do not exist).
- **Solution**:
  1. Added `com.instagram.lite` to `accessibility_service_config.xml` and package listeners.
  2. Implemented BFS tree-walk inspecting `node.text`, `node.contentDescription`, `node.isSelected`, and bounds.
  3. Added `isInstagramHomeTabSelected` and `isInstagramReelsTabSelected` fallback matching across all tree nodes for `"Home"`, `"Feed"`, `"Reel"`, `"Clip"`, and `"Video"`.

### 5.4 Tap-from-Feed & Tap-from-DMs Universal Interception
- **Problem**: When a user tapped a Reel from the Home feed or a shared Reel link in DMs, the Reel was not blocked because the underlying fragment (Home tab or chat thread) remained in the backstack.
- **Solution**:
  1. Dedicated fullscreen viewer detection takes precedence over tab selection: `hasDedicatedFullscreenReel` checks for visible `clips_viewer_view_pager`, `root_clips_layout`, or `clips_viewer_container` with $\ge 75\%$ height.
  2. If a fullscreen Reel is detected, `isInstagramTextingOrChatting` and `isInstagramHomeFeed` return `false`, allowing the Reel to be blocked.
  3. `GLOBAL_ACTION_BACK` pops the Reel viewer off the backstack and returns the user directly to the Home feed or DM chat.

### 5.5 False Chat Collision via "Direct Share" & "Direct Tab"
- **Problem**: After adding DM safeguards, Reels were completely immune and not getting blocked even with `Total Block` enabled.
- **Root Cause**:
  Every Reel screen has a Direct Share button (`com.instagram.android:id/direct_share_button`), and the bottom bar has a Direct tab (`com.instagram.android:id/direct_tab`). A broad substring check `if (resId.contains("direct_"))` falsely classified every Reel screen as a DM chat.
- **Solution**:
  Replaced blanket `direct_` checks with exact view IDs (`direct_message_list`, `direct_thread_composer`, `message_composer`, `inbox_recycler_view`) and explicitly excluded buttons containing `"share"` or `"tab"`.

### 5.6 Active Typing & IME Software Keyboard Immunity
- **Problem**: Typing in chats or comments triggered dwell timers and blocked the screen.
- **Solution**:
  1. Inspected window types for `AccessibilityWindowInfo.TYPE_INPUT_METHOD` (software keyboard).
  2. Checked for input focus via `root.findFocus(FOCUS_INPUT)` and `node.isFocused` on editable nodes.
  3. Merely having an unfocused reply bar (like Instagram's `"Reply to [friend]..."` at the bottom of a Reel) does NOT count as typing.

### 5.7 Samsung One UI & System Launcher Immunity
- **Problem**: Device home screen navigation and launchers could be disrupted by accessibility back actions.
- **Solution**:
  Explicitly ignored launcher packages (`com.sec.android.app.launcher`, `com.android.launcher3`, `com.google.android.apps.nexuslauncher`, etc.) in the package filter and event dispatcher.

### 5.8 Instagram Stories & Shared Reels in Stories Immunity
- **Problem**: When users watched an Instagram Story containing a shared Reel sticker or embedded short video, Kontre falsely detected short-form video signatures and ejected the user out of the Story viewer.
- **Root Cause**:
  Instagram's Story viewer (`ReelViewerFragment`) shares legacy naming conventions (`reel_viewer_container`), and embedded Reel stickers in Stories can surface video layouts or text containing "Reel". Furthermore, previous checks only verified `isInstagramTextingOrChatting` and `isInstagramHomeFeed` in dwell/intervention handlers, leaving Stories unprotected when video layouts were encountered.
- **Solution**:
  1. Implemented dedicated `isInstagramStory(root: AccessibilityNodeInfo): Boolean` inspecting Story progress bars (`story_progress`, `stories_progress_bar`, `segment_progress_bar`, `reel_viewer_progress_bar`), story headers (`reel_viewer_header`, `reel_viewer_title`), and story reply composers.
  2. Guarded `hasDedicatedFullscreenReel` so that if `isInstagramStory(root)` is true, it immediately returns `false`.
  3. Added `isInstagramStory` safeguard to `detectInstagramReels`, dwell abortion, and `triggerIntervention`.

---

## 6. QA & Verification Test Matrix

| Scenario | Expected Behavior | Verification Status |
| :--- | :--- | :--- |
| **Browsing Home Feed** | User can scroll feed, read captions, view photos, carousels, and in-feed videos without dwell timer or ejections. | ✅ Passed |
| **Resting on Home Feed** | Pausing on the Home screen for several seconds/minutes never kicks the user out to the phone launcher. | ✅ Passed |
| **Watching Stories (Images & Videos)** | Story viewer progress bar detected; user can watch regular stories without any ejection or HUD pill. | ✅ Passed |
| **Watching Stories with Shared Reels** | Story containing an embedded/shared Reel is recognized via `isInstagramStory`; user is NOT ejected. | ✅ Passed |
| **Tap on Reel from Home Feed** | Fullscreen `ClipsViewerFragment` opens ($\ge 75\%$ height). Intercepted and ejected via `GLOBAL_ACTION_BACK`, returning user to feed. | ✅ Passed |
| **Opening DMs (Text)** | Navigating to Direct Messages inbox never kicks the user to Home. | ✅ Passed |
| **Switching to "General" Tab in DMs** | Tapping "General" filter tab keeps user inside DMs; never kicks to Home. | ✅ Passed |
| **Switching to "Primary" Tab in DMs** | Tapping "Primary" filter tab keeps user inside DMs; never kicks to Home. | ✅ Passed |
| **Reading Chat Thread** | Scrolling conversation messages with keyboard closed never ejects. | ✅ Passed |
| **Typing with Software Keyboard** | Keyboard visible (`TYPE_INPUT_METHOD`); all dwell timers aborted, pill hidden. | ✅ Passed |
| **Tap Shared Reel inside Chat** | Opens fullscreen Reel viewer; immediately blocked back to chat thread. | ✅ Passed |
| **Tap Dedicated Reels Bottom Tab** | Bottom tab selection detected; immediately ejected to previous tab. | ✅ Passed |
| **YouTube Shorts** | Dedicated Shorts player (`reel_recycler` / `reel_player_page_content`) detected and blocked; normal YouTube videos unaffected. | ✅ Passed |

---

## 7. Build, Deployment & Shell Commands Guide

### Prerequisites
- JDK 17: `c:\Avv\p.proj\jdk17\jdk-17.0.20.1+1`
- Android SDK: `c:\Avv\p.proj\android-sdk`

### Build Debug APK
```powershell
$env:JAVA_HOME = "c:\Avv\p.proj\jdk17\jdk-17.0.20.1+1"
cd c:\Avv\p.proj\ReelShield
.\gradlew.bat assembleRelease
```
*Production Signed Artifact:* `c:\Avv\p.proj\ReelShield-release.apk`  
*Debug Artifact:* `c:\Avv\p.proj\ReelShield\app\build\outputs\apk\debug\app-debug.apk`

### Install Release APK via ADB
```powershell
adb push c:\Avv\p.proj\ReelShield-release.apk /data/local/tmp/app-release.apk
adb shell pm install /data/local/tmp/app-release.apk
```

### Grant Permissions via ADB
```powershell
# Enable Accessibility Service
adb shell settings put secure enabled_accessibility_services com.reelshield.app/com.reelshield.app.service.VideoBlockerAccessibilityService
adb shell settings put secure accessibility_enabled 1

# Grant Draw Over Other Apps (Overlay)
adb shell appops set com.reelshield.app SYSTEM_ALERT_WINDOW allow
```

### Launch ReelShield Dashboard
```powershell
adb shell am start -n com.reelshield.app/com.reelshield.app.MainActivity
```

### Inspect Logcat Live Stream
```powershell
adb logcat -s VideoBlockerService:V
```

---

## 8. Samsung "Auto Blocker" & Knox Security Guide

### Why Samsung Auto Blocker Blocks Sideloading
Starting in **Samsung One UI 6.0 (Android 14)** and **One UI 6.1 / 6.1.1**, Samsung introduced a system feature called **Auto Blocker** (*Settings $\rightarrow$ Security and privacy $\rightarrow$ Auto Blocker*).
By default on many carrier builds or when enabled:
1. **"Block apps from unauthorized sources"**:
   Samsung explicitly blocks the installation of **any** APK downloaded outside the Google Play Store or Samsung Galaxy Store (even legitimate apps like Epic Games, Fortnite, Termux, or WhatsApp beta).
2. **Debug / Unsigned Detection**:
   Samsung Knox and McAfee App Protection flag debug APKs signed with generic `testkey` or containing `android:testOnly="true"`.

### Technical Solution Implemented
1. **Production Keystore Signing**:
   Generated `reelshield-release.jks` (RSA 2048-bit with SHA-256 certificate) and configured full APK Signature Scheme v2 & v3 in Gradle.
2. **Stripped Debug & Test Only Flags**:
   The release build (`ReelShield-release.apk`) has `android:debuggable="false"` and `android:testOnly="false"`, ensuring Android package manager accepts it as a production package.

### How to Install on Samsung Devices (10-Second Step)
1. On the Samsung phone, open **Settings**.
2. Tap **Security and privacy** $\rightarrow$ Tap **Auto Blocker**.
3. Toggle Auto Blocker **OFF** (confirm with your PIN/fingerprint).
4. Tap and install **`ReelShield-release.apk`**.
5. *(Optional)* After the app is installed, you can toggle Auto Blocker back **ON**! ReelShield will continue running with full protection.
6. Enable the two permissions inside ReelShield (Accessibility Service & Overlay). If Samsung shows a *"Restricted setting"* prompt for Accessibility, go to **Settings $\rightarrow$ Apps $\rightarrow$ ReelShield**, tap the **3 dots (⋮)** in the top right, tap **"Allow restricted settings"**, and toggle the service ON.

---

## 9. GitHub Integration & Automated CI/CD Releases

### Repository Details
- **Remote**: [`https://github.com/psk22/ReelShield`](https://github.com/psk22/ReelShield)
- **Primary Branch**: `main`

### Automated Build Workflow (`.github/workflows/build-apk.yml`)
- **Triggers**:
  - Push / Pull Request to `main`.
  - Pushing any version tag (e.g., `v1.0.0`).
  - Manual execution via `workflow_dispatch` in the Actions tab.
- **Pipeline Operations**:
  1. Sets up JDK 17 (Temurin) and Gradle caching.
  2. Runs `./gradlew assembleRelease` signed with `reelshield-release.jks`.
  3. Renames output to `ReelShield-release.apk` and archives it as a downloadable 30-day build artifact.
  4. On tag pushes (`v*`), automatically publishes a **GitHub Release** and attaches `ReelShield-release.apk` for direct download on any Android device.