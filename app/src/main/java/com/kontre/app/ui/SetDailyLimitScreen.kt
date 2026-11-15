package com.kontre.app.ui

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToInt

/**
 * Google Stitch "Set Daily Limit" screen (View 2).
 * Features a Custom Neon Glow Slider with a Floating Live Tooltip that tracks the thumb,
 * physical haptic feedback, a radial consumption dial, and unboxed preset chips.
 */
@Composable
fun SetDailyLimitScreen(
    quotaManager: QuotaManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    var reelsCount by remember { mutableIntStateOf(quotaManager.reelsCount) }
    var shortsCount by remember { mutableIntStateOf(quotaManager.shortsCount) }
    val totalCount = reelsCount + shortsCount
    val minAllowedLimit = totalCount.coerceIn(0, 100)
    var dailyLimit by remember { mutableIntStateOf(maxOf(quotaManager.dailyLimit, minAllowedLimit)) }
    var sliderValue by remember { mutableFloatStateOf(dailyLimit.toFloat().coerceIn(minAllowedLimit.toFloat(), 100f)) }

    var isDragging by remember { mutableStateOf(false) }
    var countdownText by remember { mutableStateOf(quotaManager.getTimeUntilMidnightFormatted()) }

    // Live reactive collection
    LaunchedEffect(Unit) {
        quotaManager.totalCountFlow.collect {
            reelsCount = quotaManager.reelsCount
            shortsCount = quotaManager.shortsCount
            val currentFloor = (quotaManager.reelsCount + quotaManager.shortsCount).coerceIn(0, 100)
            if (sliderValue < currentFloor) {
                sliderValue = currentFloor.toFloat()
            }
            if (dailyLimit < currentFloor) {
                dailyLimit = currentFloor
                quotaManager.setDailyLimit(currentFloor)
            }
        }
    }
    LaunchedEffect(Unit) {
        quotaManager.dailyLimitFlow.collect { newLimit ->
            val currentFloor = (reelsCount + shortsCount).coerceIn(0, 100)
            val effectiveLimit = maxOf(newLimit, currentFloor)
            dailyLimit = effectiveLimit
            if (!isDragging) {
                sliderValue = effectiveLimit.toFloat().coerceIn(currentFloor.toFloat(), 100f)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            countdownText = quotaManager.getTimeUntilMidnightFormatted()
            delay(1000L)
        }
    }

    val progressFraction = if (dailyLimit > 0) {
        (totalCount.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 600),
        label = "RadialProgress"
    )

    val reelsRemaining = (dailyLimit - totalCount).coerceAtLeast(0)
    val isLimitHit = quotaManager.isLimitReached

    val tooltipTextPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 11.5.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    val scaleTextPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.parseColor("#71717A")
            textSize = with(density) { 11.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    val scaleActiveTextPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.parseColor("#07D98C")
            textSize = with(density) { 11.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    val scaleDimmedTextPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.parseColor("#383842")
            textSize = with(density) { 11.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // 3. Radial Progress Consumption Dial
            Box(
                modifier = Modifier
                    .size(205.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 13.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Inactive Background Track
                    drawCircle(
                        color = Color(0xFF1F2128),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active Sweep Arc
                    if (animatedProgress > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    StitchEmeraldDark,
                                    StitchEmerald,
                                    StitchEmeraldGlow,
                                    StitchEmerald
                                )
                            ),
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Center Text Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "TODAY'S CONSUMPTION",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = StitchTextMuted
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$totalCount",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = " / $dailyLimit",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.5.dp)
                                .background(
                                    if (isLimitHit) Color(0xFFFF4A36) else StitchEmerald,
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isLimitHit) "Limit Hit • Shield Active" else "$reelsRemaining reels left",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLimitHit) Color(0xFFFF6B5B) else StitchEmerald
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 4. Target Ceiling Section Header (Unboxed)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "TARGET CEILING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = StitchTextMuted
                )
                Text(
                    text = if (minAllowedLimit > 0) "Locked floor at $minAllowedLimit (already watched)" else "Adjust daily reel allowance",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (minAllowedLimit > 0) StitchEmerald.copy(alpha = 0.85f) else StitchTextMuted.copy(alpha = 0.6f)
                )
            }

            // 5. Custom Neon Glow Slider with Floating Live Tooltip & Mathematically Aligned Scale
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(minAllowedLimit) {
                            detectTapGestures { offset ->
                                val paddingPx = 16.dp.toPx()
                                val availableWidth = size.width - 2 * paddingPx
                                val fraction = ((offset.x - paddingPx) / availableWidth).coerceIn(0f, 1f)
                                val rawInt = (fraction * 100f).roundToInt()
                                val clampedInt = rawInt.coerceIn(minAllowedLimit, 100)
                                if (rawInt < minAllowedLimit) {
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                }
                                if (clampedInt != dailyLimit) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    dailyLimit = clampedInt
                                    sliderValue = clampedInt.toFloat()
                                    quotaManager.setDailyLimit(clampedInt)
                                }
                            }
                        }
                        .pointerInput(minAllowedLimit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val paddingPx = 16.dp.toPx()
                                    val availableWidth = size.width - 2 * paddingPx
                                    val fraction = ((offset.x - paddingPx) / availableWidth).coerceIn(0f, 1f)
                                    val rawInt = (fraction * 100f).roundToInt()
                                    val clampedInt = rawInt.coerceIn(minAllowedLimit, 100)
                                    if (clampedInt != dailyLimit) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        dailyLimit = clampedInt
                                        sliderValue = clampedInt.toFloat()
                                        quotaManager.setDailyLimit(clampedInt)
                                    }
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val paddingPx = 16.dp.toPx()
                                    val availableWidth = size.width - 2 * paddingPx
                                    val fraction = ((change.position.x - paddingPx) / availableWidth).coerceIn(0f, 1f)
                                    val rawInt = (fraction * 100f).roundToInt()
                                    val clampedInt = rawInt.coerceIn(minAllowedLimit, 100)
                                    sliderValue = (fraction * 100f).coerceIn(minAllowedLimit.toFloat(), 100f)
                                    if (clampedInt != dailyLimit) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        dailyLimit = clampedInt
                                        quotaManager.setDailyLimit(clampedInt)
                                    }
                                }
                            )
                        }
                ) {
                    val width = size.width
                    val paddingPx = 16.dp.toPx()
                    val availableTrackWidth = width - 2 * paddingPx
                    val fraction = (sliderValue / 100f).coerceIn(0f, 1f)
                    val thumbX = paddingPx + fraction * availableTrackWidth

                    val trackCenterY = 48.dp.toPx()
                    val trackHeight = 7.dp.toPx()
                    val trackLeft = paddingPx
                    val trackRight = width - paddingPx

                    // Inactive Base Track
                    drawLine(
                        color = Color(0xFF222530),
                        start = Offset(trackLeft, trackCenterY),
                        end = Offset(trackRight, trackCenterY),
                        strokeWidth = trackHeight,
                        cap = StrokeCap.Round
                    )

                    // Consumed Floor Track (already watched reels floor)
                    val floorFraction = minAllowedLimit / 100f
                    val floorX = paddingPx + floorFraction * availableTrackWidth
                    if (floorX > trackLeft) {
                        drawLine(
                            color = Color(0xFF2D313E),
                            start = Offset(trackLeft, trackCenterY),
                            end = Offset(floorX, trackCenterY),
                            strokeWidth = trackHeight,
                            cap = StrokeCap.Round
                        )
                    }

                    // Tick Notch Dots along the Track
                    listOf(0, 25, 50, 75, 100).forEach { notch ->
                        val notchX = paddingPx + (notch / 100f) * availableTrackWidth
                        drawCircle(
                            color = Color(0x35FFFFFF),
                            radius = 1.6.dp.toPx(),
                            center = Offset(notchX, trackCenterY)
                        )
                    }

                    // Active Neon Track
                    if (thumbX > trackLeft) {
                        // Soft Neon Glow Shadow
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(StitchEmeraldDark.copy(alpha = 0.35f), StitchEmeraldGlow.copy(alpha = 0.5f)),
                                startX = trackLeft,
                                endX = thumbX
                            ),
                            start = Offset(trackLeft, trackCenterY),
                            end = Offset(thumbX, trackCenterY),
                            strokeWidth = trackHeight + 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Vibrant Active Line
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(StitchEmeraldDark, StitchEmerald, StitchEmeraldGlow),
                                startX = trackLeft,
                                endX = thumbX
                            ),
                            start = Offset(trackLeft, trackCenterY),
                            end = Offset(thumbX, trackCenterY),
                            strokeWidth = trackHeight,
                            cap = StrokeCap.Round
                        )
                    }

                    // Concentric Glowing Thumb Knob
                    val thumbScale = if (isDragging) 1.25f else 1.0f

                    // Outer glowing aura
                    drawCircle(
                        color = StitchEmeraldGlow.copy(alpha = if (isDragging) 0.55f else 0.3f),
                        radius = (17.dp * thumbScale).toPx(),
                        center = Offset(thumbX, trackCenterY)
                    )

                    // Emerald thumb body
                    drawCircle(
                        color = StitchEmerald,
                        radius = (10.5.dp * thumbScale).toPx(),
                        center = Offset(thumbX, trackCenterY)
                    )

                    // Bright white center core
                    drawCircle(
                        color = Color.White,
                        radius = (5.dp * thumbScale).toPx(),
                        center = Offset(thumbX, trackCenterY)
                    )

                    // Floating Live Tooltip Bubble
                    val tooltipHalfWidth = 34.dp.toPx()
                    val tooltipHeight = 22.dp.toPx()
                    val tooltipCenterY = 15.dp.toPx()
                    val tooltipCenterX = thumbX.coerceIn(tooltipHalfWidth, width - tooltipHalfWidth)

                    val tooltipRect = RectF(
                        tooltipCenterX - tooltipHalfWidth,
                        tooltipCenterY - tooltipHeight / 2f,
                        tooltipCenterX + tooltipHalfWidth,
                        tooltipCenterY + tooltipHeight / 2f
                    )

                    // Tooltip background
                    val bgPaint = Paint().apply {
                        color = android.graphics.Color.parseColor("#15171E")
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawRoundRect(tooltipRect, 7.dp.toPx(), 7.dp.toPx(), bgPaint)

                    // Tooltip border
                    val borderPaint = Paint().apply {
                        color = android.graphics.Color.parseColor(if (isDragging) "#07D98C" else "#4D07D98C")
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5.dp.toPx()
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawRoundRect(tooltipRect, 7.dp.toPx(), 7.dp.toPx(), borderPaint)

                    // Tooltip pointer tail
                    val path = Path().apply {
                        moveTo(thumbX, tooltipRect.bottom + 4.dp.toPx())
                        lineTo(thumbX - 4.dp.toPx(), tooltipRect.bottom)
                        lineTo(thumbX + 4.dp.toPx(), tooltipRect.bottom)
                        close()
                    }
                    drawPath(path, color = Color(0xFF15171E))
                    drawPath(
                        path,
                        color = StitchEmerald.copy(alpha = if (isDragging) 0.9f else 0.4f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Tooltip text readout
                    drawContext.canvas.nativeCanvas.drawText(
                        "${sliderValue.roundToInt()} reels",
                        tooltipCenterX,
                        tooltipCenterY + 4.dp.toPx(),
                        tooltipTextPaint
                    )

                    // Scale Numbers (0, 25, 50, 75, 100) exactly aligned with thumb center
                    val scaleY = 82.dp.toPx()
                    listOf(0, 25, 50, 75, 100).forEach { tickVal ->
                        val tickFraction = tickVal / 100f
                        val tickX = paddingPx + tickFraction * availableTrackWidth
                        val isSelected = sliderValue.roundToInt() == tickVal
                        val isLockedFloor = tickVal < minAllowedLimit
                        val paint = when {
                            isSelected -> scaleActiveTextPaint
                            isLockedFloor -> scaleDimmedTextPaint
                            else -> scaleTextPaint
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            tickVal.toString(),
                            tickX,
                            scaleY,
                            paint
                        )
                    }
                }
            }

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
