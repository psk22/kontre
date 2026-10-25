package com.reelshield.app

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
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelshield.app.data.QuotaManager
import com.reelshield.app.data.rules.DynamicRuleEngine
import com.reelshield.app.service.ResuscitationWorker
import com.reelshield.app.service.WatcherForegroundService
import com.reelshield.app.service.VideoBlockerAccessibilityService
import com.reelshield.app.receiver.KontreDeviceAdminReceiver
import com.reelshield.app.util.BatteryOptimizationHelper
import com.reelshield.app.util.UsageStatsFallbackHelper
import com.reelshield.app.ui.theme.AshText
import com.reelshield.app.ui.theme.AubergineCanvas
import com.reelshield.app.ui.theme.BorderColor
import com.reelshield.app.ui.theme.CoralEmber
import com.reelshield.app.ui.theme.CoralEmberGlow
import com.reelshield.app.ui.theme.CoralEmberMuted
import com.reelshield.app.ui.theme.DarkBackground
import com.reelshield.app.ui.theme.DarkSurface
import com.reelshield.app.ui.theme.DarkSurfaceVariant
import com.reelshield.app.ui.theme.ElevatedPlum
import com.reelshield.app.ui.theme.ElevatedPlumDark
import com.reelshield.app.ui.theme.FogText
import com.reelshield.app.ui.theme.IrisAccent
import com.reelshield.app.ui.theme.KontreTheme
import com.reelshield.app.ui.theme.PureWhite
import com.reelshield.app.ui.theme.StatusGreen
import com.reelshield.app.ui.theme.StatusGreenMuted
import com.reelshield.app.ui.theme.TextMuted
import com.reelshield.app.ui.theme.TextPrimary
import com.reelshield.app.ui.theme.TextSecondary
import com.reelshield.app.ui.theme.WhisperBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var quotaManager: QuotaManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quotaManager = QuotaManager(this)
        WatcherForegroundService.start(this)
        ResuscitationWorker.schedule(this)
        handleNotificationIntent(intent)

        setContent {
            KontreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AubergineCanvas
                ) {
                    DashboardScreen(quotaManager = quotaManager)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("EXTRA_OPEN_ACCESSIBILITY", false)) {
            try {
                val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(settingsIntent)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to launch accessibility settings", e)
            }
        } else if (intent.getBooleanExtra("EXTRA_OPEN_BATTERY_SETTINGS", false)) {
            try {
                BatteryOptimizationHelper.openOemBackgroundSettings(this)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to launch OEM battery settings", e)
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
    var isUsageAccessGranted by remember {
        mutableStateOf(UsageStatsFallbackHelper.hasUsageStatsPermission(context))
    }
    var isDeviceAdminActive by remember {
        mutableStateOf(KontreDeviceAdminReceiver.isDeviceAdminActive(context))
    }
    var isRestrictedBlocked by remember {
        mutableStateOf(isRestrictedSettingsBlocked(context))
    }
    var isBatteryOptIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    val coroutineScope = rememberCoroutineScope()
    val dynamicRuleEngine = remember { DynamicRuleEngine.getInstance(context) }
    var activeRulesVersion by remember { mutableIntStateOf(dynamicRuleEngine.activeRules.version) }
    var isSyncingRules by remember { mutableStateOf(false) }

    // Immediate reactive updates via StateFlow
    LaunchedEffect(Unit) {
        quotaManager.totalCountFlow.collect {
            reelsCount = quotaManager.reelsCount
            shortsCount = quotaManager.shortsCount
            blockedCount = quotaManager.blockedTodayCount
            timeSaved = quotaManager.timeSavedMinutes
        }
    }

    // Live 1-second refresh ticker for clock countdown & permission changes
    LaunchedEffect(Unit) {
        while (true) {
            countdownText = quotaManager.getTimeUntilMidnightFormatted()
            reelsCount = quotaManager.reelsCount
            shortsCount = quotaManager.shortsCount
            blockedCount = quotaManager.blockedTodayCount
            timeSaved = quotaManager.timeSavedMinutes
            isAccessibilityEnabled = isAccessibilityServiceActive(context)
            isUsageAccessGranted = UsageStatsFallbackHelper.hasUsageStatsPermission(context)
            isDeviceAdminActive = KontreDeviceAdminReceiver.isDeviceAdminActive(context)
            isRestrictedBlocked = isRestrictedSettingsBlocked(context)
            isBatteryOptIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
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
        quotaManager.isLimitReached -> CoralEmber
        totalCount >= 40 -> CoralEmber
        else -> StatusGreen
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // --- 1. App Header (Superlist Hero Style) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_kontre_logo),
                    contentDescription = "Kontre Logo",
                    modifier = Modifier
                        .height(38.dp)
                        .padding(end = 12.dp)
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "KONTRE",
                            color = PureWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(6.dp)
                                .background(CoralEmber, CircleShape)
                        )
                    }
                    Text(
                        text = "Midnight focus workspace",
                        color = FogText,
                        fontSize = 12.sp,
                        letterSpacing = (-0.2).sp
                    )
                }
            }

            val isShieldActive = isAccessibilityEnabled
            Box(
                modifier = Modifier
                    .background(
                        color = if (isShieldActive) StatusGreenMuted else CoralEmberMuted,
                        shape = RoundedCornerShape(100.dp)
                    )
                    .border(
                        1.dp,
                        if (isShieldActive) StatusGreen.copy(alpha = 0.5f) else CoralEmber.copy(alpha = 0.5f),
                        RoundedCornerShape(100.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isShieldActive) "GUARD ACTIVE" else "SETUP NEEDED",
                    color = if (isShieldActive) StatusGreen else CoralEmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 2. Focus & Time Saved Stats (20dp Cards) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedPlum),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(WhisperBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "BLOCKED TODAY",
                        color = AshText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$blockedCount",
                        color = CoralEmber,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Reels intercepted", color = FogText, fontSize = 12.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedPlum),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(WhisperBorder))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "TIME SAVED",
                        color = AshText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "~$timeSaved m",
                        color = PureWhite,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Mindless scroll prevented", color = FogText, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. Daily Allowance Progress Card (20dp Plum Card) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedPlum),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(WhisperBorder))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (totalBlock) "TOTAL LOCKOUT ENGAGED" else "DAILY ALLOWANCE",
                        color = if (totalBlock) CoralEmber else AshText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(ElevatedPlumDark, RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "RESETS AT 00:00",
                            color = FogText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$totalCount",
                        color = progressColor,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.0).sp
                    )
                    Text(
                        text = if (totalBlock) " videos (0 allowed)" else " / $dailyLimit videos",
                        color = AshText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp, start = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(100.dp)),
                    color = progressColor,
                    trackColor = ElevatedPlumDark
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Instagram Reels", color = FogText, fontSize = 12.sp)
                        Text(
                            text = "$reelsCount",
                            color = PureWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(text = "YouTube Shorts", color = FogText, fontSize = 12.sp)
                        Text(
                            text = "$shortsCount",
                            color = PureWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(text = "Resets In", color = FogText, fontSize = 12.sp)
                        Text(
                            text = countdownText,
                            color = PureWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- 4. System Permissions Checklist ---
        Text(
            text = "SYSTEM PERMISSIONS",
            color = AshText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Accessibility Service Card
        PermissionWizardCard(
            stepNumber = "1",
            title = "Accessibility Service",
            description = "Traverses view trees to intercept short-form video viewers in real-time.",
            isGranted = isAccessibilityEnabled,
            actionLabel = "Enable Service",
            onAction = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )

        if (!isAccessibilityEnabled && isRestrictedBlocked) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElevatedPlumDark, RoundedCornerShape(20.dp))
                    .border(1.dp, CoralEmber.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .clickable {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CoralEmberMuted, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🔒", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Samsung Restricted Setting Detected",
                        color = CoralEmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap here -> Tap 3 dots (⋮) top-right -> \"Allow restricted settings\" -> Confirm PIN.",
                        color = FogText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Usage Access Fallback Card
        PermissionWizardCard(
            stepNumber = "2",
            title = "Usage Access (Redundant Fallback)",
            description = "Redundant fallback: if Accessibility is killed by OEM deep sleep or revoked, UsageStats detects when Instagram or YouTube is foregrounded and launches the block screen.",
            isGranted = isUsageAccessGranted,
            actionLabel = "Grant Usage Access",
            onAction = {
                UsageStatsFallbackHelper.openUsageAccessSettings(context)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Battery Optimization Exemption Card
        PermissionWizardCard(
            stepNumber = "3",
            title = "Battery Optimization Exemption",
            description = "Prevents aggressive OEM task killers (One UI, HyperOS, ColorOS) from sleeping the focus monitor.",
            isGranted = isBatteryOptIgnored,
            actionLabel = "Request Exemption",
            onAction = {
                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
            }
        )

        if (!isBatteryOptIgnored) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    BatteryOptimizationHelper.openOemBackgroundSettings(context)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FogText),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(WhisperBorder))
            ) {
                Text(
                    text = "⚙️ Open OEM Background / Auto-Start Settings",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Device Administrator Protection Card
        PermissionWizardCard(
            stepNumber = "4",
            title = "Device Administrator Protection",
            description = "Provides high-level anti-kill protection against aggressive OEM background process killers (One UI, HyperOS, ColorOS).",
            isGranted = isDeviceAdminActive,
            actionLabel = "Enable Device Admin",
            onAction = {
                try {
                    context.startActivity(KontreDeviceAdminReceiver.createAddDeviceAdminIntent(context))
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to launch Device Admin intent", e)
                }
            }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // --- 5. Configuration & Platform Toggles ---
        Text(
            text = "PLATFORM TOGGLES",
            color = AshText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedPlum),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(WhisperBorder))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
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

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                // Total Block Toggle
                ToggleRow(
                    title = "Total Block Mode (0 Allowance)",
                    subtitle = "Instantly intercepts every Reel/Short with zero daily quota allowance",
                    checked = totalBlock,
                    onCheckedChange = {
                        totalBlock = it
                        quotaManager.isTotalBlockEnabled = it
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- 6. Protection Scope & Immunities ---
        Text(
            text = "PROTECTION SCOPE & IMMUNITIES",
            color = AshText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedPlum),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(WhisperBorder))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                ScopeItem(
                    icon = "📸",
                    title = "Stories & Shared Reels in Stories",
                    status = "IMMUNE",
                    description = "Watch friends' stories with zero interruptions, even if a Reel is shared in the story."
                )
                Spacer(modifier = Modifier.height(16.dp))
                ScopeItem(
                    icon = "💬",
                    title = "Direct Messages & Chats",
                    status = "IMMUNE",
                    description = "Primary, General, Requests, and active typing are 100% protected."
                )
                Spacer(modifier = Modifier.height(16.dp))
                ScopeItem(
                    icon = "📰",
                    title = "Home Feed & Photos",
                    status = "IMMUNE",
                    description = "Scroll posts, captions, and in-feed media without accidental ejection."
                )
                Spacer(modifier = Modifier.height(16.dp))
                ScopeItem(
                    icon = "⚡",
                    title = "Dedicated Reels & Shorts",
                    status = "BLOCKED",
                    description = "Opening standalone Reels/Shorts triggers instant zero-delay backward exit."
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- 7. Dynamic Anti-Obfuscation Rule Engine ---
        Text(
            text = "DYNAMIC ANTI-OBFUSCATION ENGINE",
            color = AshText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedPlum),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(WhisperBorder))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dynamic Heuristic Rules v$activeRulesVersion",
                            color = PureWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "AES-256 Encrypted Cache • Remote Sync",
                            color = FogText,
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(StatusGreenMuted, RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = StatusGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📺", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "9:16 Video Surface Detection",
                            color = PureWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "SurfaceView, TextureView & PlayerView at 1.77 ± 0.1 ratio",
                            color = AshText,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!isSyncingRules) {
                            isSyncingRules = true
                            coroutineScope.launch {
                                val result = dynamicRuleEngine.fetchRemoteRules()
                                isSyncingRules = false
                                if (result.isSuccess) {
                                    val rules = result.getOrNull()
                                    if (rules != null) {
                                        activeRulesVersion = rules.version
                                    }
                                    Toast.makeText(context, "✅ Dynamic rules updated (v$activeRulesVersion)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "ℹ️ Active encrypted rules loaded (offline)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElevatedPlumDark),
                    enabled = !isSyncingRules
                ) {
                    Text(
                        text = if (isSyncingRules) "⏳ Syncing Rules over HTTPS..." else "🔄 Sync Latest Rules (HTTPS)",
                        color = PureWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- 8. Testing & Simulation Controls (Rich Lab) ---
        Text(
            text = "TESTING & SIMULATION LAB",
            color = AshText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                colors = ButtonDefaults.buttonColors(containerColor = ElevatedPlumDark),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(text = "+1 Reel", color = PureWhite, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    quotaManager.incrementShorts()
                    shortsCount = quotaManager.shortsCount
                    Toast.makeText(context, "Logged +1 Short (${reelsCount + shortsCount}/$dailyLimit)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ElevatedPlumDark),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(text = "+1 Short", color = PureWhite, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: Milestone Presets (25 & 40)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("kontre_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("key_reels_count", 25).putInt("key_shorts_count", 0).apply()
                    reelsCount = 25
                    shortsCount = 0
                    Toast.makeText(context, "Set to 25/50 (Halfway mark)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ElevatedPlumDark),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(text = "Set to 25/50", color = PureWhite, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("kontre_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("key_reels_count", 40).putInt("key_shorts_count", 0).apply()
                    reelsCount = 40
                    shortsCount = 0
                    Toast.makeText(context, "Set to 40/50 (Warning zone)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ElevatedPlumDark),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(text = "Set to 40/50", color = CoralEmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 3: Lock Boundaries (49 & 50)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("kontre_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("key_reels_count", 49).putInt("key_shorts_count", 0).apply()
                    reelsCount = 49
                    shortsCount = 0
                    Toast.makeText(context, "Set to 49/50 (1 video until lock)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ElevatedPlumDark),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(text = "Set to 49/50", color = PureWhite, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("kontre_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("key_reels_count", 50).putInt("key_shorts_count", 0).apply()
                    reelsCount = 50
                    shortsCount = 0
                    Toast.makeText(context, "Set to 50/50 (Lock active)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CoralEmber),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(text = "Lock 50/50 Now", color = PureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))


        // Row 5: Manual Ejection Test
        Button(
            onClick = {
                val ejected = VideoBlockerAccessibilityService.testEjectAction()
                if (ejected) {
                    Toast.makeText(context, "Executed zero-overlay backward ejection!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Enable Kontre Accessibility Service first in Step 1", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElevatedPlumDark),
            shape = RoundedCornerShape(100.dp)
        ) {
            Text(text = "Simulate Instant Reel Ejection", color = PureWhite, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 6: Reset All Counters
        OutlinedButton(
            onClick = {
                val prefs = context.getSharedPreferences("kontre_prefs", Context.MODE_PRIVATE)
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
            shape = RoundedCornerShape(100.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(WhisperBorder))
        ) {
            Text(text = "Reset All Counters to 0/50", color = AshText, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))
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
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, color = PureWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = FogText, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = CoralEmber,
                uncheckedThumbColor = FogText,
                uncheckedTrackColor = ElevatedPlumDark,
                uncheckedBorderColor = WhisperBorder
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedPlum),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isGranted) WhisperBorder else CoralEmber.copy(alpha = 0.4f))
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(if (isGranted) StatusGreenMuted else CoralEmberMuted, CircleShape)
                            .border(1.dp, if (isGranted) StatusGreen.copy(alpha = 0.6f) else CoralEmber.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepNumber,
                            color = if (isGranted) StatusGreen else CoralEmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        color = PureWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isGranted) StatusGreen else CoralEmber, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                color = FogText,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onAction,
                enabled = !isGranted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) ElevatedPlumDark else CoralEmber,
                    contentColor = if (isGranted) FogText else PureWhite
                ),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
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
                    color = PureWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (isImmune) IrisAccent.copy(alpha = 0.2f) else CoralEmberMuted,
                            RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = status,
                        color = if (isImmune) AshText else CoralEmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = FogText,
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
        name.contains("VideoBlocker") || name.contains("Kontre") ||
                service.id.contains("VideoBlocker") || service.id.contains("Kontre")
    }
}

private fun isRestrictedSettingsBlocked(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return false
    return try {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val uid = context.packageManager.getApplicationInfo(context.packageName, 0).uid
        val mode = appOpsManager.unsafeCheckOpNoThrow("android:access_restricted_settings", uid, context.packageName)
        mode != android.app.AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }
}
