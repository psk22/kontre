# Kontre (ReelShield) — Short-Form Video Blocker & Focus Guard

> **A native Android digital wellbeing companion built with Kotlin & Jetpack Compose (Target SDK 35 / Android 15 ready).**  
> Engineered to break compulsive doom-scrolling on **Instagram Reels** and **YouTube Shorts** without breaking messaging, search, or normal feed browsing.

---

## 🎯 1. Overview & Philosophy

Most screen-time blockers take a blunt "all-or-nothing" approach: they shut down Instagram or YouTube completely when a timer expires. This creates friction because users still need:
- **Instagram Direct Messages (DMs)** to chat with family, friends, and clients.
- **Instagram Stories & Feed Posts** to stay updated without getting sucked into infinite scroll loops.
- **YouTube Long-Form Videos & Search** for studying, tutorials, and intentional watching.

**Kontre** takes a surgical approach. It uses real-time accessibility node inspection to identify **only** full-screen vertical short-form video players (Reels & Shorts), while leaving the rest of Instagram and YouTube 100% accessible and operational.

---

## ✨ 2. Key Features

### 🔢 Daily Video Quota (e.g. 50 Reels/Shorts)
- Sets a clear daily limit for short-form video consumption (default: **50 videos/day**).
- Shows real-time progress in the format: `[ 3 / 50 ]`.
- Automatic daily reset at midnight (`00:00`).

### 🏷️ Smooth Draggable Floating Counter Badge
- **Appears only inside short-form feeds**: The floating counter overlay mounts automatically when you open Instagram Reels or YouTube Shorts.
- **Fully adjustable**: Touch and drag the counter anywhere on your screen. Coordinates are clamped within screen boundaries so it never goes off-screen.
- **Permanent position memory**: Saves your preferred spot in persistent preferences and restores it on next launch.
- **Zero duplicates**: Built with a strict single-instance overlay lifecycle that guarantees you will never see multiple or ghost popups.
- **Auto-dismiss on exit**: Within ~250ms of leaving a Reel or Short (navigating to Home feed, DMs, profile, or exiting the app), the badge automatically hides.

### 🛡️ Graceful Home Redirection (No App Kicking)
- **Stays inside the app**: When your 50-video quota is reached, Kontre does **not** kick you out of Instagram or YouTube.
- **Automatic Home Navigation**: Instead of spamming the Android `BACK` button (which crashes or closes the entire app), the engine identifies and smoothly clicks the **Home** navigation tab.
  - **In Instagram**: You are redirected to the Home feed, where you can browse photos, check stories, and open DMs.
  - **In YouTube**: You are redirected to the Home tab, where you can watch standard 16:9 long-form videos or browse subscriptions.
- **Single-Back Fallback with 2s Rate Limiter**: If a Reel was opened in full-screen from a shared link or message (where no bottom navigation bar is visible), Kontre triggers a single `BACK` action protected by a 2000ms cooldown. It will never fire repeated back presses or close the app.

### 🔍 Multi-Factor Vertical Feed Detection Engine
- Inspects accessibility view trees to detect authentic vertical video players using multiple corroborating signals:
  - Full-screen container dimensions (`height >= 70%`, `width >= 75%`).
  - Vertically stacked right-edge action buttons (Like, Comment, Share, Remix, Audio).
  - Selected bottom navigation tabs (`Reels` tab in Instagram, `Shorts` tab in YouTube).
  - Unique author, caption, or audio signature transitions for accurate video counting.

### 🛡️ Comprehensive Feed & Feature Immunity
The heuristic engine explicitly verifies non-reel contexts and never interferes with:
- **Stories Tray & Story Viewer**: Viewing friends' stories (even with embedded reels) is 100% immune.
- **Direct Messages & Chats**: DMs, typing, requests, and inbox threads are never blocked.
- **Home Feed Posts & Carousels**: Scrolling regular image or video posts on the feed will never count against your quota or trigger an ejection.
- **Search, Explore & Profile**: Browsing user profiles, search results, or settings is fully permitted.
- **Standard YouTube Videos**: Standard 16:9 long-form videos (`watch_player`), searches, and subscriptions are completely untouched.

### 🔒 100% Privacy & Zero Database Bloat
- **Local Operation**: All detection runs directly on your device via the Android Accessibility Service.
- **No Database**: Room database dependencies have been removed. No event history or video identifiers are saved to disk.
- **Zero Telemetry**: No network requests, analytics trackers, or user profiling.

---

## 🏗️ 3. Architecture & Component Structure

```
com.reelshield.app
├── MainActivity.kt                      # Jetpack Compose UI (status cards, quota settings, switches)
├── data/
│   └── QuotaManager.kt                  # Lightweight SharedPreferences manager for daily counters & limits
└── service/
    ├── ContentTrackerService.kt         # Core Accessibility Service engine:
    │                                    #   - Real-time view tree scanner (YouTube Shorts & Instagram Reels)
    │                                    #   - Graceful Home tab redirection & single-back rate-limiter
    │                                    #   - 250ms liveness watchdog for instant overlay dismissal
    ├── FloatingCounterManager.kt        # Draggable floating window overlay manager:
    │                                    #   - Single-instance FrameLayout overlay attached to WindowManager
    │                                    #   - Real-time touch drag listener with boundary clamping
    │                                    #   - Position coordinate persistence in SharedPreferences
    ├── VideoBlockerAccessibilityService # Primary accessibility service declaration extending ContentTrackerService
    └── WatcherForegroundService.kt      # Foreground service maintaining process priority
```

---

## ⚙️ 4. Required Android Permissions

| Permission | Purpose |
| :--- | :--- |
| **Accessibility Service** | Allows Kontre to inspect the active view hierarchy to detect when a Reel or Short is loaded, count transitions, and navigate back to Home upon limit completion. |
| **Display over other apps** (`SYSTEM_ALERT_WINDOW`) | Allows the small `[ 3 / 50 ]` floating badge to render over short-form video players while you watch. |

---

## 🚀 5. How to Build & Install

### Prerequisites
- Android Studio / Android SDK (Platform SDK 35).
- An Android device with **USB Debugging** enabled (`Settings → Developer Options → USB Debugging`).

### Build & Run Commands
```powershell
# Navigate to the project directory
cd c:\Avv\p.proj\ReelShield

# Compile and install onto connected phone
.\gradlew installDebug

# Launch the app
adb shell monkey -p com.reelshield.app -c android.intent.category.LAUNCHER 1
```

### Initial Device Setup
1. Open **Kontre** on your phone.
2. Grant **Accessibility Service** permission (`Settings → Accessibility → Installed Apps → Kontre`).
3. Grant **Display over other apps** (for the floating counter).
4. Open **Instagram** or **YouTube**:
   - The floating counter appears showing `0/50`.
   - Drag the counter to your preferred position.
   - When 50 reels are reached, the app automatically redirects you to the Home feed without closing Instagram or YouTube.
