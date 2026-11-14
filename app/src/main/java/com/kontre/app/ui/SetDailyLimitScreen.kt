package com.kontre.app.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
 * Features a glowing radial progress dial, dynamic target ceiling slider,
 * quick preset pills, and real-time consumption feedback.
 */
@Composable
fun SetDailyLimitScreen(
    quotaManager: QuotaManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var reelsCount by remember { mutableIntStateOf(quotaManager.reelsCount) }
    var shortsCount by remember { mutableIntStateOf(quotaManager.shortsCount) }
    val totalCount = reelsCount + shortsCount
    var dailyLimit by remember { mutableIntStateOf(quotaManager.dailyLimit) }
    var sliderValue by remember { mutableFloatStateOf(dailyLimit.toFloat().coerceIn(0f, 100f)) }

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
            sliderValue = newLimit.toFloat().coerceIn(0f, 100f)
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
            Spacer(modifier = Modifier.height(4.dp))

            // 3. Radial Progress Dial Section
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Circular Canvas Gauge
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    val center = Offset(size.width / 2, size.height / 2)

                    // Track
                    drawCircle(
                        color = Color(0xFF1F2128),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Arc
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
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = StitchTextMuted
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$totalCount",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = " / $dailyLimit",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

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
                            text = if (isLimitHit) "Limit Reached • Shield Active" else "$reelsRemaining reels left",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLimitHit) Color(0xFFFF6B5B) else StitchEmerald
                        )
                    }
                }
            }

            // 4. Target Ceiling Slider Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TARGET CEILING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = StitchTextMuted
                        )

                        // Value Badge Pill
                        Box(
                            modifier = Modifier
                                .border(1.dp, StitchEmerald, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${sliderValue.roundToInt()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF121215)
                            )
                        }
                    }

                    // Native-feel Smooth Slider
                    Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            sliderValue = newValue
                            val rounded = newValue.roundToInt()
                            quotaManager.setDailyLimit(rounded)
                            dailyLimit = rounded
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = StitchEmerald,
                            inactiveTrackColor = Color(0xFF272733)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Min/Mid/Max Scale
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "0", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StitchTextMuted)
                        Text(text = "25", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StitchTextMuted)
                        Text(text = "50", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StitchTextMuted)
                        Text(text = "75", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StitchTextMuted)
                        Text(text = "MAX: 100", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Quick Preset Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 25, 50, 75, 100).forEach { preset ->
                            val isSelected = sliderValue.roundToInt() == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        1.dp,
                                        if (isSelected) StitchEmerald else Color(0x22FFFFFF),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        sliderValue = preset.toFloat()
                                        quotaManager.setDailyLimit(preset)
                                        dailyLimit = preset
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$preset",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) StitchEmerald else Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

