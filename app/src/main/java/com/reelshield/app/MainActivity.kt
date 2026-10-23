package com.reelshield.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelshield.app.data.QuotaManager
import com.reelshield.app.service.ReelShieldForegroundService
import com.reelshield.app.service.VideoBlockerAccessibilityService
import com.reelshield.app.ui.theme.AccentRed
import com.reelshield.app.ui.theme.BorderColor
import com.reelshield.app.ui.theme.DarkBackground
import com.reelshield.app.ui.theme.DarkSurface
import com.reelshield.app.ui.theme.DarkSurfaceVariant
import com.reelshield.app.ui.theme.ReelShieldTheme
import com.reelshield.app.ui.theme.StatusGreen
import com.reelshield.app.ui.theme.StatusYellow
import com.reelshield.app.ui.theme.TextMuted
import com.reelshield.app.ui.theme.TextPrimary
import com.reelshield.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var quotaManager: QuotaManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quotaManager = QuotaManager(this)
        ReelShieldForegroundService.start(this)

        setContent {
            ReelShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    DashboardScreen(quotaManager = quotaManager)
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(quotaManager: QuotaManager) {
    val context = LocalContext.current

    var reelsCount by remember { mutableIntStateOf(quotaManager.reelsCount) }
    var shortsCount by remember { mutableIntStateOf(quotaManager.shortsCount) }
    var blockedCount by remember { mutableIntStateOf(quotaManager.blockedTodayCount) }
    var timeSaved by remember { mutableIntStateOf(quotaManager.timeSavedMinutes) }

    val totalCount = reelsCount + shortsCount
    val dailyLimit = quotaManager.dailyLimit

    // Toggle States
    var blockIg by remember { mutableStateOf(quotaManager.isInstagramBlockEnabled) }
    var blockYt by remember { mutableStateOf(quotaManager.isYouTubeBlockEnabled) }
    var totalBlock by remember { mutableStateOf(quotaManager.isTotalBlockEnabled) }

    var countdownText by remember { mutableStateOf(quotaManager.getTimeUntilMidnightFormatted()) }
    var isAccessibilityEnabled by remember {
        mutableStateOf(isAccessibilityServiceActive(context))
    }
    var isOverlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    // Live 1-second refresh ticker
    LaunchedEffect(Unit) {
        while (true) {
            countdownText = quotaManager.getTimeUntilMidnightFormatted()
            reelsCount = quotaManager.reelsCount
            shortsCount = quotaManager.shortsCount
            blockedCount = quotaManager.blockedTodayCount
            timeSaved = quotaManager.timeSavedMinutes
            isAccessibilityEnabled = isAccessibilityServiceActive(context)
            isOverlayGranted = Settings.canDrawOverlays(context)
            delay(1000L)
        }
    }

    val progressFraction = if (dailyLimit > 0) {
        (totalCount.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f)
    } else 1f

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 600),
        label = "progress"
    )

    val progressColor = when {
        quotaManager.isLimitReached -> AccentRed
        totalCount >= 40 -> StatusYellow
        else -> StatusGreen
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // --- 1. App Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "KONTRE",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Short-Form Video Blocker & Focus Guard",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            val isShieldActive = isAccessibilityEnabled && isOverlayGranted
            Box(
                modifier = Modifier
                    .background(
                        color = if (isShieldActive) StatusGreen.copy(alpha = 0.15f) else StatusYellow.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        if (isShieldActive) StatusGreen else StatusYellow,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isShieldActive) "ACTIVE" else "SETUP NEEDED",
                    color = if (isShieldActive) StatusGreen else StatusYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 2. Focus & Time Saved Stats ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderColor))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "BLOCKED TODAY",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$blockedCount",
                        color = AccentRed,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(text = "Reels intercepted", color = TextMuted, fontSize = 11.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderColor))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "TIME SAVED",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "~$timeSaved m",
                        color = StatusGreen,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(text = "Mindless scroll saved", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. Daily Allowance Progress Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderColor))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (totalBlock) "TOTAL BLOCK MODE ACTIVE" else "DAILY ALLOWANCE",
                        color = if (totalBlock) AccentRed else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "RESET AT 00:00",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$totalCount",
                        color = progressColor,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (totalBlock) " videos (0 allowed)" else " / $dailyLimit videos",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = progressColor,
                    trackColor = DarkSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Instagram Reels", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = "$reelsCount",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column {
                        Text(text = "YouTube Shorts", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = "$shortsCount",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column {
                        Text(text = "Resets In", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = countdownText,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // --- 4. System Permissions Checklist (Step 1 & Step 2 Only) ---
        Text(
            text = "SYSTEM PERMISSIONS CHECKLIST",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 1. Accessibility Service Card
        PermissionWizardCard(
            stepNumber = "1",
            title = "Accessibility Service",
            description = "Traverses view hierarchies to intercept short-form video viewers in real-time.",
            isGranted = isAccessibilityEnabled,
            actionLabel = "Enable Service",
            onAction = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )

        if (!isAccessibilityEnabled) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                    .clickable {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔒",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column {
                    Text(
                        text = "Samsung / Android 13+ \"Restricted setting\"?",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "If greyed out: tap here -> tap 3 dots (⋮) top right -> select \"Allow restricted settings\" -> then turn on.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Draw Over Other Apps Card
        PermissionWizardCard(
            stepNumber = "2",
            title = "Draw Over Other Apps",
            description = "Required for the floating top reel counter pill to display over video players.",
            isGranted = isOverlayGranted,
            actionLabel = "Grant Overlay",
            onAction = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- 5. Configuration & Platform Toggles ---
        Text(
            text = "BLOCKING RULES & TOGGLES",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderColor))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Toggle Instagram
                ToggleRow(
                    title = "Block Instagram Reels",
                    subtitle = "Keeps Direct Messages, Chats, and Stories 100% accessible",
                    checked = blockIg,
                    onCheckedChange = {
                        blockIg = it
                        quotaManager.isInstagramBlockEnabled = it
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle YouTube Shorts
                ToggleRow(
                    title = "Block YouTube Shorts",
                    subtitle = "Blocks short-form videos while preserving normal YouTube video playback",
                    checked = blockYt,
                    onCheckedChange = {
                        blockYt = it
                        quotaManager.isYouTubeBlockEnabled = it
                    }
                )



                Spacer(modifier = Modifier.height(12.dp))

                // Total Block Toggle
                ToggleRow(
                    title = "Total Block Mode (0 Allowance)",
                    subtitle = "Instantly blocks every Reel/Short with zero daily quota allowance",
                    checked = totalBlock,
                    onCheckedChange = {
                        totalBlock = it
                        quotaManager.isTotalBlockEnabled = it
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 6. Protection Scope & Immunities ---
        Text(
            text = "PROTECTION SCOPE & IMMUNITIES",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderColor))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ScopeItem(
                    icon = "📸",
                    title = "Stories & Shared Reels in Stories",
                    status = "IMMUNE",
                    description = "Watch friends' stories with zero interruptions, even if a Reel is shared in the story."
                )
                Spacer(modifier = Modifier.height(12.dp))
                ScopeItem(
                    icon = "💬",
                    title = "Direct Messages & Chats",
                    status = "IMMUNE",
                    description = "Primary, General, Requests, and active typing are 100% protected."
                )
                Spacer(modifier = Modifier.height(12.dp))
                ScopeItem(
                    icon = "📰",
                    title = "Home Feed & Photos",
                    status = "IMMUNE",
                    description = "Scroll posts, captions, and in-feed media without accidental ejection."
                )
                Spacer(modifier = Modifier.height(12.dp))
                ScopeItem(
                    icon = "⚡",
                    title = "Dedicated Reels & Shorts",
                    status = "BLOCKED",
                    description = "Opening standalone Reels/Shorts triggers instant zero-delay backward exit."
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 7. Testing & Simulation Controls (Rich Lab) ---
        Text(
            text = "TESTING & SIMULATION LAB",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Row 1: Quick Increments
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    quotaManager.incrementReels()
                    reelsCount = quotaManager.reelsCount
                    Toast.makeText(context, "Logged +1 Reel (${reelsCount + shortsCount}/$dailyLimit)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "+1 Reel", color = TextPrimary, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    quotaManager.incrementShorts()
                    shortsCount = quotaManager.shortsCount
                    Toast.makeText(context, "Logged +1 Short (${reelsCount + shortsCount}/$dailyLimit)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "+1 Short", color = TextPrimary, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Milestone Presets (25 & 40)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("reelshield_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("key_reels_count", 25).putInt("key_shorts_count", 0).apply()
                    reelsCount = 25
                    shortsCount = 0
                    Toast.makeText(context, "Set to 25/50 (Halfway mark)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Set to 25/50", color = TextPrimary, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("reelshield_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("key_reels_count", 40).putInt("key_shorts_count", 0).apply()
                    reelsCount = 40
                    shortsCount = 0
                    Toast.makeText(context, "Set to 40/50 (Warning zone)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Set to 40/50 (Warn)", color = StatusYellow, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 3: Lock Boundaries (49 & 50)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("reelshield_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("key_reels_count", 49).putInt("key_shorts_count", 0).apply()
                    reelsCount = 49
                    shortsCount = 0
                    Toast.makeText(context, "Set to 49/50 (1 video until lock)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Set to 49/50", color = TextPrimary, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("reelshield_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("key_reels_count", 50).putInt("key_shorts_count", 0).apply()
                    reelsCount = 50
                    shortsCount = 0
                    Toast.makeText(context, "Set to 50/50 (Lock active)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Lock 50/50 Now", color = AccentRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 4: Pill Previews (Normal, Warning, Locked)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    VideoBlockerAccessibilityService.previewFloatingPill(previewState = 0)
                    Toast.makeText(context, "Showing normal counter pill [12/50] at top for 4s", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Pill Normal", color = TextPrimary, fontSize = 11.sp)
            }

            Button(
                onClick = {
                    VideoBlockerAccessibilityService.previewFloatingPill(previewState = 1)
                    Toast.makeText(context, "Showing warning counter pill [45/50] at top for 4s", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Pill Warn ⚠️", color = StatusYellow, fontSize = 11.sp)
            }

            Button(
                onClick = {
                    VideoBlockerAccessibilityService.previewFloatingPill(previewState = 2)
                    Toast.makeText(context, "Showing locked counter pill [50/50] at top for 4s", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Pill Lock 🔒", color = AccentRed, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 5: Action Simulations (Eject & Dwell Simulation)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val success = VideoBlockerAccessibilityService.testEjectAction()
                    if (success) {
                        Toast.makeText(context, "Executed GLOBAL_ACTION_BACK (Eject)!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Accessibility service not active - enable it in Step 1", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Test Eject (Back)", color = TextPrimary, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    quotaManager.incrementReels()
                    reelsCount = quotaManager.reelsCount
                    VideoBlockerAccessibilityService.previewFloatingPill(previewState = if (quotaManager.isLimitReached) 2 else 0)
                    Toast.makeText(context, "Simulated 1.8s reel watch (${reelsCount + shortsCount}/$dailyLimit)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Simulate Watch", color = TextPrimary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 6: Reset All Counters
        OutlinedButton(
            onClick = {
                val prefs = context.getSharedPreferences("reelshield_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("key_reels_count", 0)
                    .putInt("key_shorts_count", 0)
                    .putInt("key_blocked_count", 0)
                    .apply()
                reelsCount = 0
                shortsCount = 0
                blockedCount = 0
                timeSaved = 0
                Toast.makeText(context, "All counters reset to 0/50", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = "Reset All Counters to 0/50", color = TextSecondary, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentRed,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}

@Composable
fun PermissionWizardCard(
    stepNumber: String,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isGranted) BorderColor else StatusYellow.copy(alpha = 0.5f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(if (isGranted) StatusGreen.copy(alpha = 0.2f) else StatusYellow.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, if (isGranted) StatusGreen else StatusYellow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepNumber,
                            color = if (isGranted) StatusGreen else StatusYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (isGranted) StatusGreen else StatusYellow, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onAction,
                enabled = !isGranted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) DarkSurfaceVariant else AccentRed,
                    contentColor = if (isGranted) TextMuted else Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isGranted) "Configured ✓" else actionLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ScopeItem(
    icon: String,
    title: String,
    status: String,
    description: String
) {
    val isImmune = status == "IMMUNE"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = icon,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 12.dp, top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (isImmune) StatusGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = status,
                        color = if (isImmune) StatusGreen else AccentRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

private fun isAccessibilityServiceActive(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any { service ->
        val name = service.resolveInfo?.serviceInfo?.name ?: ""
        name.contains("VideoBlocker") || name.contains("ReelShield") || name.contains("Kontre") ||
                service.id.contains("VideoBlocker") || service.id.contains("ReelShield") || service.id.contains("Kontre")
    }
}
