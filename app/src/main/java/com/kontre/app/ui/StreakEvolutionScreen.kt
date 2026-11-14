package com.kontre.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontre.app.R
import com.kontre.app.data.QuotaManager
import com.kontre.app.ui.theme.StitchDarkBg
import com.kontre.app.ui.theme.StitchEmerald
import com.kontre.app.ui.theme.StitchTextMuted
import kotlinx.coroutines.launch

/**
 * Resolves the active DragonStage based on real accumulated streak days.
 */
fun getStageForStreak(streakDays: Int): DragonStage = when {
    streakDays >= 100 -> DragonStage.ANCIENT_DRAGON
    streakDays >= 50 -> DragonStage.ADULT_DRAGON
    streakDays >= 21 -> DragonStage.SMALL_DRAGON
    streakDays >= 7 -> DragonStage.HATCHLING
    else -> DragonStage.EGG
}

/**
 * 5 Evolution Stages for the Dragon mascot.
 * Matches Google Stitch reference data & timeline.
 */
enum class DragonStage(
    val stageNumber: Int,
    val title: String,
    val trackFirstLine: String,
    val trackSecondLine: String?,
    val stageDays: Int,
    val stageLabel: String,
    val nextInfo: String,
    val drawableRes: Int
) {
    EGG(
        stageNumber = 1,
        title = "Stage 1: Egg",
        trackFirstLine = "Egg",
        trackSecondLine = null,
        stageDays = 3,
        stageLabel = "BLOOM STREAK",
        nextInfo = "• Next: Hatchling at 7d",
        drawableRes = R.drawable.dragon_stage_1_egg
    ),
    HATCHLING(
        stageNumber = 2,
        title = "Stage 2: Hatchling",
        trackFirstLine = "Hatchling",
        trackSecondLine = null,
        stageDays = 7,
        stageLabel = "BLOOM STREAK",
        nextInfo = "• Next: Small Dragon at 21d",
        drawableRes = R.drawable.dragon_stage_2_hatchling
    ),
    SMALL_DRAGON(
        stageNumber = 3,
        title = "Stage 3: Small Dragon",
        trackFirstLine = "Small",
        trackSecondLine = "Dragon",
        stageDays = 21,
        stageLabel = "BLOOM STREAK",
        nextInfo = "• Next: Adult Dragon at 50d",
        drawableRes = R.drawable.dragon_stage_3_small
    ),
    ADULT_DRAGON(
        stageNumber = 4,
        title = "Stage 4: Adult Dragon",
        trackFirstLine = "Adult",
        trackSecondLine = "Dragon",
        stageDays = 50,
        stageLabel = "BLOOM STREAK",
        nextInfo = "• Next: Ancient Dragon at 100d",
        drawableRes = R.drawable.dragon_stage_4_adult
    ),
    ANCIENT_DRAGON(
        stageNumber = 5,
        title = "Stage 5: Ancient Dragon",
        trackFirstLine = "Ancient",
        trackSecondLine = "Dragon",
        stageDays = 100,
        stageLabel = "MAX EVOLUTION",
        nextInfo = "• Max Evolution / Ancient Ruler",
        drawableRes = R.drawable.dragon_stage_5_ancient
    )
}

/**
 * Google Stitch "Streak Evolution" screen (Page 4).
 * Matches Google Stitch versions 3 & 8 design exactly:
 * - Unobstructed dragon hero mascot with pure emerald feathered radial aura glow
 * - Floating neon bloom tooltip badge (stage days & bloom status)
 * - Compact single-line clickable stage badge pill with animated pulsing dot
 * - 5-stage evolution timeline fitted cleanly across the width without clipping
 * - Open share streak section with glowing emerald pill button
 * - Clean streak statistics (Starting Date & Highest Streak Record)
 * - Master distraction lock CTA button with Kontre Protocol footnote
 */
@Composable
fun StreakEvolutionScreen(
    quotaManager: QuotaManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Real dynamic streak statistics observed from QuotaManager
    val currentStreak by quotaManager.currentStreakFlow.collectAsState()
    val highestStreak by quotaManager.highestStreakFlow.collectAsState()
    val streakStartDate by quotaManager.streakStartDateFlow.collectAsState()

    var isSharing by remember { mutableStateOf(false) }

    // Dynamic current stage based on real streak days
    val actualCurrentStage = remember(currentStreak) { getStageForStreak(currentStreak) }

    // Selected stage allows users to preview other stages by tapping
    var selectedStage by remember(actualCurrentStage) {
        mutableStateOf(actualCurrentStage)
    }

    // Floating dragon mascot animation & dynamic halo aura transitions
    val infiniteTransition = rememberInfiniteTransition(label = "DragonAnimations")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DragonFloatY"
    )
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraAlpha"
    )
    val pulseDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseDotAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StitchDarkBg)
    ) {
        // Section Label matching Toggles and Limits pages
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STREAK EVOLUTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                color = StitchTextMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 2.dp)
            )
        }

        // Scrollable Content Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 3. Creature Hero Section with Pure Emerald Feathered Radial Aura Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(255.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Tap cycles to next dragon evolution stage
                        val nextOrdinal = (selectedStage.ordinal + 1) % DragonStage.values().size
                        selectedStage = DragonStage.values()[nextOrdinal]
                    },
                contentAlignment = Alignment.Center
            ) {
                // Wide ambient feathered radial emerald glow
                Canvas(
                    modifier = Modifier
                        .size(320.dp)
                        .scale(auraScale)
                        .alpha(auraAlpha)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x6607D98C), // 40% vibrant emerald core
                                Color(0x3807D98C), // 22% mid emerald
                                Color(0x1607D98C), // 9% soft outer tint
                                Color(0x0407D98C), // 1.5% delicate edge falloff
                                Color(0x0007D98C)  // 0% pure transparent emerald
                            ),
                            center = center,
                            radius = size.minDimension / 2f
                        )
                    )
                }

                // Inner core back-light moving with the creature
                Canvas(
                    modifier = Modifier
                        .size(230.dp)
                        .offset(y = floatY.dp)
                        .scale(auraScale * 0.98f)
                        .alpha(auraAlpha * 0.85f)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x7507D98C), // Core backlight
                                Color(0x3007D98C),
                                Color(0x0007D98C)
                            ),
                            center = center,
                            radius = size.minDimension / 2f
                        )
                    )
                }

                // Dragon mascot illustration (Unobstructed & Floating)
                AnimatedContent(
                    targetState = selectedStage,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(280)) + scaleIn(initialScale = 0.92f, animationSpec = tween(280))) togetherWith
                                (fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.95f, animationSpec = tween(180)))
                    },
                    label = "DragonCrossfade"
                ) { stage ->
                    Image(
                        painter = painterResource(id = stage.drawableRes),
                        contentDescription = stage.title,
                        modifier = Modifier
                            .size(225.dp)
                            .offset(y = floatY.dp)
                    )
                }
            }

            // Title
            Text(
                text = "Streak Evolution",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // 4. Interactive Stage Indicator (Unboxed Clean Single Line)
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val nextOrdinal = (selectedStage.ordinal + 1) % DragonStage.values().size
                        selectedStage = DragonStage.values()[nextOrdinal]
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Pulsing green dot
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .alpha(pulseDotAlpha)
                        .background(StitchEmerald, CircleShape)
                )

                Spacer(modifier = Modifier.width(7.dp))

                Text(
                    text = selectedStage.title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchEmerald,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = selectedStage.nextInfo,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = StitchTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 5. Interactive 5-Stage Evolution Track Row (Clean Open Layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val stages = DragonStage.values()
                stages.forEachIndexed { index, stage ->
                    val isSelected = stage == selectedStage
                    val isPast = stage.stageNumber < selectedStage.stageNumber

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                selectedStage = stage
                            }
                            .padding(horizontal = 3.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSelected) {
                            // Active stage dot with glowing outer ring
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .border(1.5.dp, Color(0x6607D98C), CircleShape)
                                    .background(StitchEmerald, CircleShape)
                            )
                        } else if (isPast) {
                            // Past stage dot (muted emerald)
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color(0xCC07D98C), CircleShape)
                            )
                        } else {
                            // Future stage dot (zinc)
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color(0xFF52525B), CircleShape)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stage.trackFirstLine,
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected -> StitchEmerald
                                    isPast -> Color(0xFFD4D4D8)
                                    else -> Color(0xFF71717A)
                                }
                            )
                            if (stage.trackSecondLine != null) {
                                Text(
                                    text = stage.trackSecondLine,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isSelected -> StitchEmerald
                                        isPast -> Color(0xFFD4D4D8)
                                        else -> Color(0xFF71717A)
                                    }
                                )
                            }
                        }
                    }

                    if (index < stages.size - 1) {
                        Text(
                            text = "→",
                            fontSize = 9.sp,
                            color = Color(0xFF52525B)
                        )
                    }
                }
            }

            // 6. Share Your Streak Section (Open Row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SHARE YOUR STREAK",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White
                )

                Button(
                    onClick = {
                        if (!isSharing) {
                            isSharing = true
                            Toast.makeText(context, "Generating your Kontre streak card...", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                val success = StreakCardGenerator.generateAndShareCard(
                                    context = context,
                                    stage = selectedStage,
                                    currentStreak = currentStreak,
                                    highestStreak = highestStreak,
                                    startDate = streakStartDate
                                )
                                if (!success) {
                                    Toast.makeText(context, "Could not generate streak card", Toast.LENGTH_SHORT).show()
                                }
                                isSharing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StitchEmerald),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isSharing) "CREATING..." else "SHARE ↗",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        color = Color(0xFF0C1A14)
                    )
                }
            }

            // 7. Streak Statistics Section (Open Clean Rows)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Starting date
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "STARTING DATE OF STREAK",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = StitchTextMuted
                    )
                    Text(
                        text = streakStartDate,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x10FFFFFF))
                )

                // Highest Streak
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "HIGHEST STREAK",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = StitchTextMuted
                        )
                        Text(
                            text = "Personal Record",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$highestStreak",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "days",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchTextMuted,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

