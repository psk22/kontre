package com.kontre.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontre.app.R
import com.kontre.app.ui.theme.StitchEmerald
import com.kontre.app.ui.theme.StitchTextMuted
import com.kontre.app.util.BatteryOptimizationHelper

/**
 * Universal Top Header with Emerald Dragon Vector and KONTRE wordmark
 * plus Stitch 3-line Hamburger Menu Button.
 * Used consistently across all 3 pages (Toggles, Limits, Streaks).
 */
@Composable
fun KontreTopHeader(
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo + Brand Wordmark (Clean unboxed vector logo)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_kontre_dragon_vector),
                    contentDescription = "Kontre Dragon Logo",
                    tint = StitchEmerald,
                    modifier = Modifier.size(28.dp)
                )

                Text(
                    text = "KONTRE",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    color = StitchEmerald
                )
            }

            // Clean Hamburger Menu Icon (Direct 3 lines, unboxed without bento grid border/background)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onMenuClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }

        // Clean 1px divider beneath the header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x0FFFFFFF))
        )
    }
}

/**
 * Slide-out Navigation Drawer from Stitch Prototype (kontre_navigation_menu_1).
 */
@Composable
fun StitchNavigationDrawer(
    isAccessibilityEnabled: Boolean,
    onClose: () -> Unit,
    onSelectOption: (TogglesDialogType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.82f)
            .background(Color(0xFF101014))
            .border(1.dp, Color(0x2207D98C), RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Bring everything down comfortably from the top
            Spacer(modifier = Modifier.height(20.dp))

            // Drawer Top Row (Clean logo & wordmark)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_kontre_dragon_vector),
                    contentDescription = "Kontre Dragon Logo",
                    tint = StitchEmerald,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "KONTRE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = StitchEmerald
                )
            }

            // Section Subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(StitchEmerald)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(StitchEmerald)
                )
                Text(
                    text = "SETTINGS & INFO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = StitchTextMuted
                )
            }

            // Navigation Options
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DrawerNavigationItem(
                    label = "ABOUT & LORE",
                    onClick = { onSelectOption(TogglesDialogType.ABOUT_LORE) }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x14FFFFFF))
                )
                DrawerNavigationItem(
                    label = "SYSTEM PERMISSIONS",
                    onClick = { onSelectOption(TogglesDialogType.PERMISSIONS) }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x14FFFFFF))
                )
                DrawerNavigationItem(
                    label = "PRIVACY VAULT",
                    onClick = { onSelectOption(TogglesDialogType.PRIVACY_VAULT) }
                )
            }
        }

        // Bottom Version Spec (brought up nicely from the bottom)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "— VERSION 1.8.4 —",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = StitchTextMuted
            )
            Text(
                text = "BUILD 23 • PROTOCOL SECURE",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = Color(0xFF555560)
            )
        }
    }
}

@Composable
private fun DrawerNavigationItem(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "→",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = StitchEmerald
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = Color.White
        )
    }
}

/**
 * Privacy Policy Dialog (Full Google Play Compliant Prominent Disclosure & Details)
 */
@Composable
fun StitchPrivacyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16161F),
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_kontre_dragon_vector),
                        contentDescription = "Privacy Shield",
                        tint = StitchEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "PRIVACY POLICY",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = StitchEmerald
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "100% OFFLINE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StitchEmerald,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x2207D98C))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "ZERO TELEMETRY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StitchEmerald,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x2207D98C))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "NO ACCOUNTS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StitchEmerald,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x2207D98C))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Philosophy
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ZERO-DATA COLLECTION PHILOSOPHY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Kontre is engineered with an uncompromising privacy-first architecture. We do not collect, store, transmit, or monetize any of your personal data. The app has zero remote servers and zero third-party telemetry SDKs.",
                        fontSize = 11.sp,
                        color = StitchTextMuted,
                        lineHeight = 16.sp
                    )
                }

                // Section 2: Prominent Accessibility Disclosure
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF20202A))
                        .border(1.dp, Color(0x3307D98C), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "ACCESSIBILITY API DISCLOSURE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = StitchEmerald,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Kontre uses Android's Accessibility Service API (android.permission.BIND_ACCESSIBILITY_SERVICE) solely as an assistive digital wellbeing engine.\n\n" +
                                    "• Purpose: To detect when YouTube Shorts or Instagram Reels feeds are active, track your scroll count against your daily quota, and execute user-directed navigation (back gesture or blocking shield) when the quota is reached.\n\n" +
                                    "• What it NEVER does: Kontre NEVER reads, collects, or logs keystrokes, passwords, chats, comments, private messages, personal photos, or financial details. No observed data ever leaves your device.",
                            fontSize = 10.5.sp,
                            color = Color(0xFFDDDDDD),
                            lineHeight = 15.sp
                        )
                    }
                }

                // Section 3: Permissions
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "DEVICE PERMISSIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "• Display Over Other Apps (SYSTEM_ALERT_WINDOW): Renders the live floating reel counter and blocking screen.\n" +
                                "• Foreground Service: Keeps wellbeing tracking active and immune to sudden OS kills.\n" +
                                "• Package Usage Stats: Verifies active video player package in foreground as fallback.\n" +
                                "• Notifications: Sends daily limit and streak milestone alerts locally on your device.",
                        fontSize = 11.sp,
                        color = StitchTextMuted,
                        lineHeight = 16.sp
                    )
                }

                // Section 4: Storage & Deletion
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "DATA STORAGE & DELETION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "All counts and streak records are stored exclusively in your device's private, encrypted SharedPreferences. You have 100% control: clearing app cache/storage or uninstalling permanently deletes all data instantly.",
                        fontSize = 11.sp,
                        color = StitchTextMuted,
                        lineHeight = 16.sp
                    )
                }

                // Section 5: Developer Contact
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "DEVELOPER CONTACT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Developer: Pranava Siva Karthik Yadam\n" +
                                "Support Email: yadampranav@gmail.com\n" +
                                "Repository: github.com/psk22/kontre",
                        fontSize = 11.sp,
                        color = StitchTextMuted,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF20202A))
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
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "ONLINE POLICY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(StitchEmerald)
                        .clickable { onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "I UNDERSTAND",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D0D12)
                    )
                }
            }
        }
    )
}

/**
 * System Permissions Dialog
 */
@Composable
fun StitchPermissionsDialog(
    context: Context,
    isAccessibilityEnabled: Boolean,
    onDismiss: () -> Unit
) {
    val isOverlayEnabled = Settings.canDrawOverlays(context)
    val isBatteryIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16161F),
        title = {
            Text(
                text = "SYSTEM PERMISSIONS",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = StitchEmerald
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Permission 1: Accessibility
                PermissionStatusRow(
                    title = "ACCESSIBILITY SERVICE",
                    subtitle = "Required to detect Shorts & Reels triggers",
                    isActive = isAccessibilityEnabled,
                    onConfigure = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        } catch (e: Exception) {
                            Toast.makeText(context, "Open Android Settings → Accessibility", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Permission 2: Overlay
                PermissionStatusRow(
                    title = "DRAW OVER OTHER APPS",
                    subtitle = "Required for the floating live counter",
                    isActive = isOverlayEnabled,
                    onConfigure = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Open Settings → Display over other apps", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Permission 3: Battery Optimization
                PermissionStatusRow(
                    title = "BATTERY OPTIMIZATION",
                    subtitle = "Prevents background service termination",
                    isActive = isBatteryIgnored,
                    onConfigure = {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    }
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(StitchEmerald)
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "CLOSE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D0D12)
                )
            }
        }
    )
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onConfigure: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF20202A))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isActive) StitchEmerald else Color(0xFFFF9F0A))
                )
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = StitchTextMuted
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isActive) Color(0x2207D98C) else Color(0x33FF9F0A))
                .border(
                    1.dp,
                    if (isActive) Color(0x4407D98C) else Color(0x66FF9F0A),
                    RoundedCornerShape(8.dp)
                )
                .clickable { onConfigure() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isActive) "ACTIVE" else "FIX",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) StitchEmerald else Color(0xFFFF9F0A)
            )
        }
    }
}

/**
 * Credits & About Dialog
 */
@Composable
fun StitchCreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16161F),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_kontre_dragon_vector),
                    contentDescription = "Kontre Logo",
                    tint = StitchEmerald,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "KONTRE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = StitchEmerald
                    )
                    Text(
                        text = "CREDITS & SPECIFICATIONS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = StitchTextMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // App Mission
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF20202A))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MISSION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = StitchEmerald,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Kontre empowers individuals to overcome algorithmic short-form video addiction through on-device quota enforcement, mindful intervention, and zero-compromise privacy.",
                            fontSize = 11.sp,
                            color = Color.White,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Lead Developer
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CREATOR & LEAD DEVELOPER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "• Lead Developer: Pranava Siva Karthik Yadam\n" +
                                "• Support / Contact: yadampranav@gmail.com\n" +
                                "• GitHub: @psk22\n" +
                                "• Repository: github.com/psk22/kontre",
                        fontSize = 11.sp,
                        color = StitchTextMuted,
                        lineHeight = 16.sp
                    )
                }

                // License (MIT)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "PROJECT LICENSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Kontre is free and open-source software released under the MIT License.\n\n" +
                                "Copyright (c) 2026 Pranava Siva Karthik Yadam\n" +
                                "Permission is hereby granted, free of charge, to any person obtaining a copy of this software to deal in the Software without restriction.",
                        fontSize = 10.5.sp,
                        color = StitchTextMuted,
                        lineHeight = 15.sp
                    )
                }

                // Open Source Libraries
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "OPEN SOURCE ATTRIBUTIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )

                    OpenSourceLibItem(
                        name = "Jetpack Compose & Material 3",
                        author = "Google / AOSP",
                        license = "Apache 2.0"
                    )
                    OpenSourceLibItem(
                        name = "Kotlin & Coroutines",
                        author = "JetBrains & Contributors",
                        license = "Apache 2.0"
                    )
                    OpenSourceLibItem(
                        name = "AndroidX WorkManager & Lifecycle",
                        author = "The Android Open Source Project",
                        license = "Apache 2.0"
                    )
                    OpenSourceLibItem(
                        name = "AndroidX Core KTX",
                        author = "The Android Open Source Project",
                        license = "Apache 2.0"
                    )
                }

                // Build Spec
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "SPECIFICATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF777785),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Version 1.8.4 (Build 23) • Target Android 15 (SDK 35)\n" +
                                "Architecture: Clean Compose MVI + SharedPreferences\n" +
                                "Design Language: Cyber Obsidian Emerald",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF777785),
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF20202A))
                        .clickable {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/psk22/kontre")
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "GITHUB REPO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(StitchEmerald)
                        .clickable { onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "CLOSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D0D12)
                    )
                }
            }
        }
    )
}

@Composable
private fun OpenSourceLibItem(
    name: String,
    author: String,
    license: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF20202A))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = author,
                fontSize = 9.5.sp,
                color = StitchTextMuted
            )
        }
        Text(
            text = license,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = StitchEmerald,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x2207D98C))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

enum class TogglesDialogType {
    ABOUT_LORE,
    PERMISSIONS,
    PRIVACY_VAULT
}

