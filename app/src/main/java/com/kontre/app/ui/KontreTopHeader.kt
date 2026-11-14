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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            horizontalArrangement = Arrangement.Start,
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
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Drawer Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
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

                // Close Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0FFFFFFF))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
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
                    label = "PRIVACY POLICY",
                    onClick = { onSelectOption(TogglesDialogType.PRIVACY_POLICY) }
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
                    label = "CREDITS",
                    onClick = { onSelectOption(TogglesDialogType.CREDITS) }
                )
            }
        }

        // Bottom Version Spec
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "— VERSION 2.4.0 —",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = StitchTextMuted
            )
            Text(
                text = "BUILD 108 • PROTOCOL SECURE",
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
 * Privacy Policy Dialog
 */
@Composable
fun StitchPrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16161F),
        title = {
            Text(
                text = "PRIVACY POLICY",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = StitchEmerald
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Kontre is engineered with an uncompromising privacy-first architecture:",
                    fontSize = 12.sp,
                    color = Color.White
                )
                Text(
                    text = "• 100% On-Device Processing: Accessibility events are analyzed entirely in memory and never written to disk.\n" +
                            "• Zero Telemetry: No trackers, analytics, or remote logging libraries.\n" +
                            "• Zero Network Transmissions: The app does not send any usage data or user activity anywhere.\n" +
                            "• Surgical Scope: Detection operates only when short-form video players are active.",
                    fontSize = 11.sp,
                    color = StitchTextMuted,
                    lineHeight = 16.sp
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
                    text = "GOT IT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D0D12)
                )
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
 * Credits Dialog
 */
@Composable
fun StitchCreditsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16161F),
        title = {
            Text(
                text = "CREDITS & SPEC",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = StitchEmerald
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "KONTRE DIGITAL WELLBEING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Engineered for Android 15 & Target SDK 35.\n" +
                            "Design Language: Google Stitch Cyber Obsidian.\n" +
                            "Mascot: Emerald Line-Art Dragon.\n" +
                            "Build: 108 • Release v2.4.0\n" +
                            "All rights reserved.",
                    fontSize = 11.sp,
                    color = StitchTextMuted,
                    lineHeight = 16.sp
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

enum class TogglesDialogType {
    PRIVACY_POLICY,
    PERMISSIONS,
    CREDITS
}

