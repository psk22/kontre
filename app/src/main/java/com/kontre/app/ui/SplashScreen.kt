package com.kontre.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontre.app.R
import com.kontre.app.ui.theme.StitchDarkBg
import com.kontre.app.ui.theme.StitchEmerald
import com.kontre.app.ui.theme.StitchEmeraldDark
import com.kontre.app.ui.theme.StitchEmeraldGlow
import kotlinx.coroutines.delay

/**
 * Pixel-perfect Google Stitch SplashScreen for Kontre.
 * Recreates the exact Stitch layout:
 * - Pure seamless obsidian canvas (#121215) with zero box artifacts
 * - Smooth radial gradient ambient aura (Canvas rendered, mathematically borderless)
 * - Breathing dragon line-art vector mascot with subtle emerald luminescence
 * - Bold KONTRE wordmark with Stitch wide letter-tracking (0.28em / ~11sp)
 * - Cyber progress track and home indicator
 */
@Composable
fun SplashScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SplashTransitions")

    // Dragon logo breathing animation (Stitch @keyframes logoBreath: 1.0 to 1.035)
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )

    // Ambient emerald neon aura pulsing (Stitch @keyframes neonPulse: 0.45 to 0.85)
    val auraIntensity by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraIntensity"
    )

    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )

    // Smooth progress bar fill
    var targetProgress by remember { mutableFloatStateOf(0.12f) }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "ProgressAnimation"
    )

    LaunchedEffect(Unit) {
        delay(300)
        targetProgress = 0.55f
        delay(700)
        targetProgress = 0.92f
        delay(650)
        targetProgress = 1.0f
        delay(350)
        onFinish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StitchDarkBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFinish()
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. Seamless Mathematically Smooth Radial Aura (Zero clipping, zero rectangular buffer artifacts)
        Canvas(
            modifier = Modifier
                .size(380.dp)
                .scale(auraScale)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StitchEmeraldGlow.copy(alpha = 0.32f * auraIntensity),
                        StitchEmerald.copy(alpha = 0.14f * auraIntensity),
                        StitchEmeraldDark.copy(alpha = 0.04f * auraIntensity),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f
            )
        }

        // 2. Center Brand Showcase (Exact Stitch Layout)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Dragon Mascot Vector Logo with Breathing Animation
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .scale(logoScale),
                contentAlignment = Alignment.Center
            ) {
                // Subtle soft outer glow layer
                Image(
                    painter = painterResource(id = R.drawable.ic_kontre_dragon_vector),
                    contentDescription = null,
                    modifier = Modifier
                        .size(238.dp)
                        .alpha(0.35f * auraIntensity)
                )
                // Crisp sharp emerald vector strokes
                Image(
                    painter = painterResource(id = R.drawable.ic_kontre_dragon_vector),
                    contentDescription = "Kontre Dragon Vector Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Typography: KONTRE with wide 0.28em letter-spacing
            Text(
                text = "KONTRE",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 11.sp,
                color = StitchEmerald,
                textAlign = TextAlign.Center
            )
        }

        // 3. Cyber Progress Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing emerald progress track
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1E1E26))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    StitchEmeraldDark,
                                    StitchEmerald,
                                    StitchEmeraldGlow
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Home indicator bar
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
            )
        }
    }
}

