package com.kontre.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontre.app.R
import com.kontre.app.ui.theme.StitchBorder
import com.kontre.app.ui.theme.StitchDarkBg
import com.kontre.app.ui.theme.StitchEmerald
import com.kontre.app.ui.theme.StitchEmeraldMuted
import com.kontre.app.ui.theme.StitchSurface
import com.kontre.app.ui.theme.StitchTextMuted

/**
 * Dedicated Full-Screen Privacy Vault Screen.
 * Inspired by modern editorial web privacy manifestos (Signal, Proton, Apple Privacy).
 * Avoids cluttered bento cards in favor of a clean, linear, high-trust architectural narrative.
 */
@Composable
fun PrivacyVaultScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StitchDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Navigation Bar
        SubPageTopBar(
            title = "PRIVACY VAULT",
            subtitle = "ZERO-TELEMETRY MANIFESTO",
            onBack = onBack
        )

        // Main Linear Editorial Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Editorial Hero Section
            PrivacyHeroSection()

            // The Zero-Knowledge Guarantee (Linear Manifesto)
            LinearManifestoSection()

            // Google Play Prominent Disclosure: Accessibility API
            AccessibilityVaultDisclosureSection()

            // Device Permissions Linear Index
            PermissionsLinearIndexSection()

            // Data Retention & Total Erasure
            DataErasureSection()

            // Verifiable Open Source Audit Card
            VerifiableAuditSection(context)

            // Bottom Breathing Room
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrivacyHeroSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Vault Status Banner
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StitchEmerald)
            )
            Text(
                text = "AIR-GAPPED SYSTEM PROTOCOL",
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = StitchEmerald
            )
        }

        // Main Statement
        Text(
            text = "Your attention is private.\nYour data stays on your silicon.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 32.sp,
            letterSpacing = (-0.5).sp,
            color = Color.White
        )

        Text(
            text = "Kontre was founded on a simple rule: tools designed to protect your mental focus should never monitor your private life. We operate with zero cloud databases, zero telemetry SDKs, and zero interest in tracking you.",
            fontSize = 13.5.sp,
            lineHeight = 22.sp,
            color = Color(0xFFC0C0D0)
        )

        // Triple Key Pillars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VaultPill(label = "100% OFFLINE")
            VaultPill(label = "NO ACCOUNTS")
            VaultPill(label = "ZERO TRACKERS")
        }
    }
}

@Composable
private fun VaultPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF181822))
            .border(1.dp, Color(0x3307D98C), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = StitchEmerald
        )
    }
}

@Composable
private fun LinearManifestoSection() {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionDividerHeader(title = "ARCHITECTURAL PRINCIPLES")

        LinearStatement(
            title = "Zero Cloud Infrastructure",
            body = "Kontre does not possess remote web servers, cloud sync APIs, or user databases. There is no server to breach, no cloud identity to leak, and no account to hack."
        )

        LinearStatement(
            title = "Strict Volatile Memory Execution",
            body = "Short-form video swipe detection occurs purely in volatile RAM during active playback. No viewing history, search history, or personal session logs are ever saved to storage."
        )

        LinearStatement(
            title = "No Commercial Telemetry",
            body = "Our APK contains zero advertising networks, zero tracking brokers, and zero diagnostic spy tools. No Firebase Analytics, no Facebook SDK, no Sentry, and no AdMob."
        )
    }
}

@Composable
private fun LinearStatement(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(StitchEmerald)
            )
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Text(
            text = body,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = StitchTextMuted,
            modifier = Modifier.padding(start = 13.dp)
        )
    }
}

@Composable
private fun AccessibilityVaultDisclosureSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF151520))
            .border(1.dp, Color(0x3307D98C), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_kontre_dragon_vector),
                contentDescription = "Shield Guard",
                tint = StitchEmerald,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = "ACCESSIBILITY API DISCLOSURE",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = StitchEmerald
                )
                Text(
                    text = "Google Play Prominent Disclosure Compliance",
                    fontSize = 10.sp,
                    color = StitchTextMuted
                )
            }
        }

        Text(
            text = "Kontre uses the Android Accessibility Service API (android.permission.BIND_ACCESSIBILITY_SERVICE) solely as an assistive digital wellbeing intervention mechanism.",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.sp,
            color = Color.White
        )

        // Exact Positive Scope
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B1B28))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "WHAT KONTRE OBSERVES & DOES:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = StitchEmerald
            )
            Text(
                text = "• Identifies when YouTube Shorts or Instagram Reels player view hierarchies enter the foreground.\n" +
                        "• Increments your on-device reel counter upon detecting swipe transitions between short video containers.\n" +
                        "• When your daily limit is reached, executes user-directed navigation (such as GLOBAL_ACTION_BACK or display shield) to exit the feed.",
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = Color(0xFFDCDCE8)
            )
        }

        // Strict Negative Guarantees
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF22161E))
                .border(1.dp, Color(0x33FF4A36), RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "WHAT KONTRE NEVER TOUCHES (ZERO EXCEPTIONS):",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFFFF5252)
            )
            Text(
                text = "• Never inspects or reads keystrokes, passwords, usernames, or text input.\n" +
                        "• Never accesses chats, comments, private messages, personal photos, or feeds outside short video containers.\n" +
                        "• Never alters system settings or restricts your freedom to disable or uninstall Kontre at any time.\n" +
                        "• Zero information observed by the Accessibility Service is ever written to permanent logs or sent off-device.",
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = Color(0xFFE8D0D0)
            )
        }
    }
}

@Composable
private fun PermissionsLinearIndexSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionDividerHeader(title = "DEVICE PERMISSIONS INDEX")

        PermissionLinearRow(
            name = "Display Over Other Apps (SYSTEM_ALERT_WINDOW)",
            purpose = "Required strictly to project the unobtrusive floating reel consumption counter and the full-screen daily limit shield over active video players."
        )

        PermissionLinearRow(
            name = "Foreground Service & Special Use",
            purpose = "Maintains an active background watchdog so aggressive OEM memory killers do not silently terminate your digital wellbeing quota protection."
        )

        PermissionLinearRow(
            name = "Usage Stats (PACKAGE_USAGE_STATS)",
            purpose = "Operates as a secondary local fallback to verify whether a supported short-form video package is active in the foreground."
        )

        PermissionLinearRow(
            name = "Post Notifications (POST_NOTIFICATIONS)",
            purpose = "Delivers milestone badges, daily limit alerts, and background service status entirely on your physical device."
        )
    }
}

@Composable
private fun PermissionLinearRow(
    name: String,
    purpose: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = purpose,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = StitchTextMuted
        )
    }
}

@Composable
private fun DataErasureSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionDividerHeader(title = "USER CONTROL & INSTANT DATA PURGE")

        Text(
            text = "Because your data lives exclusively in local private SharedPreferences on your hardware, you possess unilateral power over it. Daily counters reset automatically at 00:00 local time.",
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = StitchTextMuted
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF181824))
                .border(1.dp, StitchBorder, RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "To permanently erase all history: navigate to Android Settings → Apps → Kontre → Storage → Clear All Data.",
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                color = StitchEmerald,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun VerifiableAuditSection(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14141E))
            .border(1.dp, StitchBorder, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "INDEPENDENT SOURCE CODE AUDIT",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = StitchTextMuted
                )
                Text(
                    text = "Verify our claims directly in the source code.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StitchEmerald)
                .clickable {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/psk22/kontre/blob/main/PRIVACY_POLICY.md")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "VIEW RAW POLICY ON GITHUB →",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFF0D0D12)
            )
        }
    }
}

@Composable
private fun SectionDividerHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = StitchEmerald
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0x1FFFFFFF))
        )
    }
}

