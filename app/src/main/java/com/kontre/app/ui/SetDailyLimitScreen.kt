package com.kontre.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontre.app.data.QuotaManager
import com.kontre.app.ui.theme.StitchDarkBg
import com.kontre.app.ui.theme.StitchEmerald
import com.kontre.app.ui.theme.StitchEmeraldDark
import com.kontre.app.ui.theme.StitchEmeraldGlow
import com.kontre.app.ui.theme.StitchTextMuted
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Google Stitch "Set Daily Limit" screen (View 2).
 * Features an interactive circular rotary dial knob for seamless, hardware-style
 * limit adjustment with real-time haptic feedback and dynamic consumption tracking.
 */
@Composable
fun SetDailyLimitScreen(
    quotaManager: QuotaManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scrollState = rememberScrollState()

    var reelsCount by remember { mutableIntStateOf(quotaManager.reelsCount) }
    var shortsCount by remember { mutableIntStateOf(quotaManager.shortsCount) }
    val totalCount = reelsCount + shortsCount
    var dailyLimit by remember { mutableIntStateOf(quotaManager.dailyLimit) }

    var isDragging by remember { mutableStateOf(false) }
    var countdownText by remember { mutableStateOf(quotaManager.getTimeUntilMidnightFormatted()) }

    // Live reactive collection
    LaunchedEffect(Unit) {
        quotaManager.totalCountFlow.collect {
            reelsCount = quotaManager.reelsCount
            shortsCount = quotaManager.shortsCount
        }
    }
    LaunchedEffect(Unit) {
        quotaManager.dailyLimitFlow.collect { newLimit ->
            dailyLimit = newLimit
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            countdownText = quotaManager.getTimeUntilMidnightFormatted()
            delay(1000L)
        }
    }

    val reelsRemaining = (dailyLimit - totalCount).coerceAtLeast(0)
    val isLimitHit = quotaManager.isLimitReached

    // Limit fraction (0..100 -> 0f..1f)
    val limitFraction = (dailyLimit.toFloat() / 100f).coerceIn(0f, 1f)
    val animatedLimitFraction by animateFloatAsState(
        targetValue = limitFraction,
        animationSpec = if (isDragging) tween(durationMillis = 0) else tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "DialLimitFraction"
    )

    // Consumption progress fraction against daily limit
    val consumptionFraction = if (dailyLimit > 0) {
        (totalCount.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val animatedConsumption by animateFloatAsState(
        targetValue = consumptionFraction,
        animationSpec = tween(durationMillis = 500),
        label = "DialConsumption"
    )

    // Rotary touch calculation helper
    fun updateFromTouch(touch: Offset, width: Float, height: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        val dx = touch.x - centerX
        val dy = touch.y - centerY

        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        // Ignore touches too close to center
        if (dist < 35f) return

        var angleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angleDeg < 0f) angleDeg += 360f

        // Arc starts at 135° (bottom-left) and sweeps 270° clockwise to 405° (45°, bottom-right)
        // Bottom gap is between 45° and 135°
        val arcAngle = when {
            angleDeg <= 45f -> angleDeg + 360f
            angleDeg < 135f -> if (angleDeg < 90f) 405f else 135f
            else -> angleDeg
        }

        val fraction = ((arcAngle - 135f) / 270f).coerceIn(0f, 1f)
        val newLimit = (fraction * 100f).roundToInt()

        if (newLimit != dailyLimit) {
            if (newLimit % 5 == 0 || newLimit == 0 || newLimit == 100) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            dailyLimit = newLimit
            quotaManager.setDailyLimit(newLimit)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StitchDarkBg)
    ) {
        // 1. Top Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SET DAILY LIMIT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                color = StitchTextMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 2.dp)
            )
        }

        // 2. Scrollable Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 3. Interactive Rotary Dial (Apple Watch / Precision Hardware Style)
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            updateFromTouch(offset, size.width.toFloat(), size.height.toFloat())
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                updateFromTouch(offset, size.width.toFloat(), size.height.toFloat())
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, _ ->
                                change.consume()
                                updateFromTouch(change.position, size.width.toFloat(), size.height.toFloat())
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Rotary Ring Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 16.dp.toPx()
                    val padding = 16.dp.toPx()
                    val radius = (size.minDimension - strokeWidth - padding * 2) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Base Inactive Track (270° arc from 135° to 405°)
                    drawArc(
                        color = Color(0xFF1E2129),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Subtle milestone tick dots along the track
                    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { tickPct ->
                        val tickAngleRad = Math.toRadians((135f + tickPct * 270f).toDouble())
                        val tickX = center.x + radius * cos(tickAngleRad).toFloat()
                        val tickY = center.y + radius * sin(tickAngleRad).toFloat()
                        drawCircle(
                            color = Color(0x38FFFFFF),
                            radius = 2.5.dp.toPx(),
                            center = Offset(tickX, tickY)
                        )
                    }

                    // Active Rotary Limit Arc (Grows with dial rotation)
                    val activeSweep = animatedLimitFraction * 270f
                    if (activeSweep > 1f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    StitchEmeraldDark,
                                    StitchEmerald,
                                    StitchEmeraldGlow,
                                    StitchEmerald
                                )
                            ),
                            startAngle = 135f,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Inner Concentric Consumption Track (Shows usage against limit)
                    val innerRadius = radius - 20.dp.toPx()
                    val innerStrokeWidth = 5.dp.toPx()

                    drawArc(
                        color = Color(0x12FFFFFF),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = innerStrokeWidth, cap = StrokeCap.Round)
                    )

                    if (animatedConsumption > 0f && activeSweep > 0f) {
                        val consumptionSweep = (animatedConsumption * activeSweep).coerceAtMost(activeSweep)
                        drawArc(
                            color = if (isLimitHit) Color(0xFFFF4A36) else StitchEmerald.copy(alpha = 0.85f),
                            startAngle = 135f,
                            sweepAngle = consumptionSweep,
                            useCenter = false,
                            style = Stroke(width = innerStrokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Concentric Glowing Rotary Thumb Knob
                    val knobAngleDeg = 135f + activeSweep
                    val knobAngleRad = Math.toRadians(knobAngleDeg.toDouble())
                    val knobX = center.x + radius * cos(knobAngleRad).toFloat()
                    val knobY = center.y + radius * sin(knobAngleRad).toFloat()
                    val knobCenter = Offset(knobX, knobY)
                    val knobScale = if (isDragging) 1.25f else 1.0f

                    // Outer neon aura glow
                    drawCircle(
                        color = StitchEmeraldGlow.copy(alpha = if (isDragging) 0.6f else 0.35f),
                        radius = (18.dp * knobScale).toPx(),
                        center = knobCenter
                    )

                    // Emerald knob body
                    drawCircle(
                        color = StitchEmerald,
                        radius = (12.dp * knobScale).toPx(),
                        center = knobCenter
                    )

                    // Crisp white core
                    drawCircle(
                        color = Color.White,
                        radius = (5.5.dp * knobScale).toPx(),
                        center = knobCenter
                    )
                }

                // Center Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "DAILY CEILING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = StitchTextMuted
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$dailyLimit",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-1.5).sp
                    )

                    Text(
                        text = "REELS / DAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = StitchEmerald
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isLimitHit) Color(0xFFFF4A36) else StitchEmerald,
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isLimitHit) "Limit Hit • Shield Active"
                                   else if (totalCount == 0) "0 reels consumed"
                                   else "$totalCount used • $reelsRemaining left",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLimitHit) Color(0xFFFF6B5B) else StitchTextMuted
                        )
                    }
                }
            }

            // 4. Quick Preset Pills (Unboxed Clean Row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 25, 50, 75, 100).forEach { preset ->
                    val isSelected = dailyLimit == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                dailyLimit = preset
                                quotaManager.setDailyLimit(preset)
                            }
                            .border(
                                1.dp,
                                if (isSelected) StitchEmerald else Color(0x18FFFFFF),
                                RoundedCornerShape(12.dp)
                            )
                            .background(
                                if (isSelected) StitchEmerald.copy(alpha = 0.14f) else Color(0x06FFFFFF)
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$preset",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) StitchEmerald else Color(0xFFD4D4D8)
                        )
                    }
                }
            }

            // 5. Subtle Guidance Hint
            Text(
                text = "Drag along the ring or tap a preset to set limit",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = StitchTextMuted.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            // 6. Resets in countdown (Clean, Unboxed)
            Text(
                text = "Resets in: $countdownText",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = StitchTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
