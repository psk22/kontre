# Privacy Policy for Kontre

**Last Updated:** November 15, 2026  
**Effective Date:** November 15, 2026  

Welcome to **Kontre** ("we," "our," or "the App"), developed and maintained by **Pranava Siva Karthik Yadam** ("Developer"). 

Your privacy is our absolute priority. Kontre is engineered with a **zero-telemetry, offline-first philosophy**. We believe that your digital wellbeing tools should never compromise your digital privacy. This Privacy Policy explains clearly and transparently what permissions Kontre uses, why they are required, and confirms that **we do not collect, transmit, sell, or monetize any of your personal data**.

---

## 1. Summary of Core Privacy Principles

* **100% Local & Offline:** Kontre has zero remote servers, zero external analytics endpoints, and zero cloud synchronization.
* **No Account Required:** You do not need to register, provide an email address, or create a profile to use Kontre.
* **No Third-Party SDKs:** Kontre does not contain Google Firebase, advertising networks, crash reporting trackers, or behavioral analytics libraries.
* **Zero Personal Data Collection:** Kontre never captures, stores, or transmits your personal identity, messages, search history, keystrokes, contacts, or financial details.
* **User-Controlled Data:** All quota limits, daily watch counts, and streak milestones remain strictly sandboxed on your physical device.

---

## 2. Prominent Disclosure: Accessibility Service API Usage

> [!IMPORTANT]
> **Google Play Prominent Disclosure for Accessibility API (`android.permission.BIND_ACCESSIBILITY_SERVICE`)**
> 
> Kontre uses Android's Accessibility Service API strictly as an assistive digital wellbeing engine to help users curb compulsive short-form video consumption.

### A. How and Why Kontre Uses the Accessibility Service
When you explicitly grant Accessibility permission, Kontre uses the service solely to:
1. **Detect Short-Form Video Interfaces:** Identify when target short-form video interfaces (specifically YouTube Shorts and Instagram Reels) enter the active foreground.
2. **Count Consumed Videos:** Detect swipe and scroll transitions between short-form video items within supported apps to calculate your real-time daily reel count.
3. **Enforce User-Configured Limits:** When your self-imposed daily limit is reached (or when a platform toggle is actively blocking that service), execute user-directed navigation actions (such as `GLOBAL_ACTION_BACK` or displaying a blocking overlay) to gently exit the infinite-scroll feed.

### B. What Kontre NEVER Does with the Accessibility Service
* **NO Keystroke Logging:** Kontre never reads, tracks, or records text input, passwords, credit card numbers, or keyboard activity.
* **NO Personal Message or Content Reading:** Kontre never inspects chats, comments, private messages, personal feeds, photos, or media content.
* **NO User Settings Alteration:** Kontre does not alter system settings or restrict your ability to disable or uninstall the app at any time.
* **NO Data Transmission:** No information observed by the Accessibility Service is ever transmitted off your device or saved beyond your local daily count integer.

---

## 3. Other Permissions and Their Purposes

To provide reliable background monitoring and mindful overlay prompts, Kontre requests the following Android permissions:

| Permission | Purpose | Data Handled |
| :--- | :--- | :--- |
| **`SYSTEM_ALERT_WINDOW`** *(Display Over Other Apps)* | Draws the live floating reel counter badge and the full-screen shield overlay when a daily limit is reached. | None. Visual UI rendering only. |
| **`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`** | Keeps the wellbeing monitor active and prevents unexpected termination by aggressive OS memory managers. | None. Used exclusively for persistent background execution. |
| **`PACKAGE_USAGE_STATS`** *(Usage Access)* | Serves as an auxiliary fallback to verify which target video application is currently in the foreground. | On-device package name matching only. Never stored or shared. |
| **`POST_NOTIFICATIONS`** | Delivers milestone notifications, daily limit warnings, and persistent service status indicators. | Displayed locally on your device. |
| **`RECEIVE_BOOT_COMPLETED`** | Restores your configured daily limits and background protection automatically when your device reboots. | None. |
| **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** | Prevents device manufacturers from abruptly freezing the tracking service during active video sessions. | None. |

---

## 4. Data Storage and Retention

* **Storage Location:** All app preferences, daily reel counters, platform block toggles, and streak records are stored locally within Android's private, sandboxed `SharedPreferences` on your physical device.
* **Data Retention:** Daily counters automatically reset at midnight (00:00 local time). Historical streak numbers remain on your device until you clear them.
* **Data Erasure:** Because we do not store your data on external servers, you have full control over data deletion. You can permanently erase all stored counts and settings at any time by:
  1. Navigating to **Android Settings > Apps > Kontre > Storage & Cache**.
  2. Tapping **Clear Storage / Clear Data**.
  3. Uninstalling the App.

---

## 5. Third-Party Services and Analytics

Kontre **does not integrate with any third-party software development kits (SDKs)**, cloud platforms, or advertising networks. 
* No Google Analytics for Firebase
* No Facebook SDK
* No AdMob or advertising brokers
* No Sentry, Crashlytics, or remote diagnostic loggers

---

## 6. Children's Privacy (COPPA & GDPR-K Compliance)

Kontre is designed for a general audience seeking mindful digital habits. Kontre does not knowingly collect, solicit, or maintain personal information from children under the age of 13 (or under the applicable age of digital consent in your jurisdiction). Because the App does not collect any personal data from any user, it is fully compliant with the Children's Online Privacy Protection Act (COPPA) and the General Data Protection Regulation (GDPR).

---

## 7. Security

Because all data processing occurs entirely on your device and no data is transmitted over the internet, your information is protected by Android's application sandbox security model. We recommend maintaining the latest operating system security patches on your mobile device.

---

## 8. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect modifications in our features or compliance with legal and app store guidelines. Any updates will be posted directly within the application and in the project repository with an updated "Last Updated" date.

---

## 9. Contact Us

If you have any questions, suggestions, or concerns regarding this Privacy Policy or Kontre's privacy practices, please contact:

* **Developer:** Pranava Siva Karthik Yadam  
* **Email:** [yadampranav@gmail.com](mailto:yadampranav@gmail.com)  
* **Project Repository:** [https://github.com/psk22/kontre](https://github.com/psk22/kontre)  

