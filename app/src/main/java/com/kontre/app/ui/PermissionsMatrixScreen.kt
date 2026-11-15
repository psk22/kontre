package com.kontre.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kontre.app.isAccessibilityServiceActive
import com.kontre.app.R
import com.kontre.app.ui.theme.StitchBorder
import com.kontre.app.ui.theme.StitchDarkBg
import com.kontre.app.ui.theme.StitchEmerald
import com.kontre.app.ui.theme.StitchEmeraldMuted
import com.kontre.app.ui.theme.StitchSurface
import com.kontre.app.ui.theme.StitchTextMuted
import com.kontre.app.util.BatteryOptimizationHelper

/**
 * Dedicated Full-Screen Permissions Matrix & Live Diagnostics HUD.
 * Provides real-time status telemetry of device shields (Accessibility, Overlay, Battery).
 */
@Composable
fun PermissionsMatrixScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isAccessibilityEnabled by remember {
        mutableStateOf(isAccessibilityServiceActive(context))
    }
    var isOverlayEnabled by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var isBatteryIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    // Refresh states when returning from Android Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceActive(context)
                isOverlayEnabled = Settings.canDrawOverlays(context)
                isBatteryIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activeCount = listOf(isAccessibilityEnabled, isOverlayEnabled, isBatteryIgnored).count { it }
    val isFullyOperational = activeCount == 3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StitchDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Navigation Bar
        SubPageTopBar(
            title = "SECURITY MATRIX",
            subtitle = "LIVE DIAGNOSTICS & PERMISSIONS",
            onBack = onBack
        )

        // Main Scrollable Diagnostics Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Live Status Hero Banner
            DiagnosticsHeroBanner(
                activeCount = activeCount,
                isFullyOperational = isFullyOperational
            )

            // Diagnostic Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ACTIVE DEFENSE SHIELDS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = StitchTextMuted
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(0x1FFFFFFF))
                )
            }

            // Shield 01: Accessibility Service
            DiagnosticShieldCard(
                shieldNumber = "SHIELD 01",
                name = "Accessibility Service Engine",
                specId = "BIND_ACCESSIBILITY_SERVICE",
                description = "Inspects short-form video UI boundaries in YouTube Shorts & Instagram Reels to count reels and trigger blocking when your daily quota is exhausted.",
                isActive = isAccessibilityEnabled,
                actionLabel = if (isAccessibilityEnabled) "OPERATIONAL" else "ENABLE SERVICE",
                onAction = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (e: Exception) {
                        Toast.makeText(context, "Open Settings → Accessibility", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // Shield 02: Screen Overlay
            DiagnosticShieldCard(
                shieldNumber = "SHIELD 02",
                name = "Screen Overlay Engine",
                specId = "SYSTEM_ALERT_WINDOW",
                description = "Permits Kontre to draw the real-time floating consumption counter and render the impenetrable full-screen block shield over active video players.",
                isActive = isOverlayEnabled,
                actionLabel = if (isOverlayEnabled) "OPERATIONAL" else "GRANT OVERLAY",
                onAction = {
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

            // Shield 03: Battery Optimization
            DiagnosticShieldCard(
                shieldNumber = "SHIELD 03",
                name = "Watchdog Persistence Shield",
                specId = "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                description = "Prevents aggressive Android OEM battery savers from putting the background watchdog service to sleep while you are browsing.",
                isActive = isBatteryIgnored,
                actionLabel = if (isBatteryIgnored) "UNRESTRICTED" else "EXEMPT BATTERY",
                onAction = {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                }
            )

            // Telemetry Footer
            WatchdogTelemetryCard()

            // Bottom Breathing Room
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiagnosticsHeroBanner(
    activeCount: Int,
    isFullyOperational: Boolean
) {
    val accentColor = if (isFullyOperational) StitchEmerald else Color(0xFFFF9F0A)
    val containerBg = if (isFullyOperational) Color(0xFF14241E) else Color(0xFF261C14)
    val borderColor = if (isFullyOperational) Color(0x3307D98C) else Color(0x33FF9F0A)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                    contentDescription = "Kontre Sentinel Status",
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = if (isFullyOperational) "STATUS // FULL PROTECTION" else "STATUS // DEGRADED",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = accentColor
                )
            }

            Text(
                text = "$activeCount / 3 ACTIVE",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        Text(
            text = if (isFullyOperational)
                "All 3 core security shields are operational. Your daily reel limit is actively protected against compulsive over-consumption."
            else
                "${3 - activeCount} required defense shield(s) are offline. Configure the missing shields below to ensure uninterrupted automated blocking.",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Color(0xFFE0E0EC)
        )
    }
}

@Composable
private fun DiagnosticShieldCard(
    shieldNumber: String,
    name: String,
    specId: String,
    description: String,
    isActive: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val statusColor = if (isActive) StitchEmerald else Color(0xFFFF9F0A)
    val cardBg = Color(0xFF161622)
    val borderCol = if (isActive) Color(0x2207D98C) else Color(0x33FF9F0A)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Meta Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = shieldNumber,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = StitchTextMuted
            )

            // Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = if (isActive) "ONLINE" else "ACTION REQUIRED",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = statusColor
                )
            }
        }

        // Title and Spec
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = specId,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = StitchTextMuted
            )
        }

        // Description
        Text(
            text = description,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Color(0xFFC0C0D0)
        )

        // Action Trigger Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) Color(0x1A07D98C) else Color(0x33FF9F0A))
                .border(
                    1.dp,
                    if (isActive) Color(0x4407D98C) else Color(0x66FF9F0A),
                    RoundedCornerShape(10.dp)
                )
                .clickable { onAction() }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actionLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = if (isActive) StitchEmerald else Color(0xFFFF9F0A)
            )
        }
    }
}

@Composable
private fun WatchdogTelemetryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF12121A))
            .border(1.dp, StitchBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "SYSTEM TELEMETRY SPECIFICATIONS",
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = StitchTextMuted
        )

        TelemetryMetricRow(label = "Heartbeat Polling Cadence", value = "250ms High-Res Watchdog")
        TelemetryMetricRow(label = "Data Pipeline", value = "100% On-Device In-Memory")
        TelemetryMetricRow(label = "Target Architecture", value = "Android 15 (Target SDK 35)")
    }
}

@Composable
private fun TelemetryMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = StitchTextMuted
        )
        Text(
            text = value,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD4D4E0)
        )
    }
}

