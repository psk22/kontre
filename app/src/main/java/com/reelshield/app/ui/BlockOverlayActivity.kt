package com.reelshield.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelshield.app.data.QuotaManager
import com.reelshield.app.ui.theme.AccentRed
import com.reelshield.app.ui.theme.AccentRedGlow
import com.reelshield.app.ui.theme.BorderColor
import com.reelshield.app.ui.theme.DarkBackground
import com.reelshield.app.ui.theme.DarkSurface
import com.reelshield.app.ui.theme.DarkSurfaceVariant
import com.reelshield.app.ui.theme.ReelShieldTheme
import com.reelshield.app.ui.theme.TextPrimary
import com.reelshield.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

class BlockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SOURCE_APP = "extra_source_app"
    }

    private lateinit var quotaManager: QuotaManager
    private var sourceApp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quotaManager = QuotaManager(this)
        sourceApp = intent.getStringExtra(EXTRA_SOURCE_APP)

        setContent {
            ReelShieldTheme {
                BlockOverlayScreen(
                    quotaManager = quotaManager,
                    sourceApp = sourceApp,
                    onBackToApp = {
                        // Dismiss overlay and return directly to the caller app (Chats / Stories / Feed)
                        finish()
                    },
                    onReturnHome = {
                        returnToHomeScreen()
                    }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back press dismisses overlay and allows user to resume chatting/stories in Instagram
        finish()
    }

    private fun returnToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
fun BlockOverlayScreen(
    quotaManager: QuotaManager,
    sourceApp: String?,
    onBackToApp: () -> Unit,
    onReturnHome: () -> Unit
) {
    var countdownText by remember { mutableStateOf(quotaManager.getTimeUntilMidnightFormatted()) }

    // Live 1-second countdown ticker
    LaunchedEffect(Unit) {
        while (true) {
            countdownText = quotaManager.getTimeUntilMidnightFormatted()
            delay(1000L)
        }
    }

    // Gentle pulsing animation for mindful breathing circle
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val appLabel = when (sourceApp) {
        "com.instagram.android" -> "Instagram"
        "com.google.android.youtube" -> "YouTube"
        else -> "App"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground.copy(alpha = 0.96f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {

            // Glowing Pulsing Indicator Ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(AccentRedGlow, Color.Transparent)
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, AccentRed, CircleShape)
            ) {
                Text(
                    text = "${quotaManager.dailyLimit}",
                    color = AccentRed,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "REELS & SHORTS LOCKED",
                color = AccentRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "50 of 50 Videos Viewed",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Short-form video feeds are locked until midnight. Your chats, Direct Messages, and Stories remain fully available.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Midnight Countdown Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "RESETS AT MIDNIGHT (00:00)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = countdownText,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Button: Return to Instagram (Chats & Stories)
            Button(
                onClick = onBackToApp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Back to $appLabel (Chats & Stories)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Button: Exit to Home Screen
            OutlinedButton(
                onClick = onReturnHome,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Close & Go to Home Screen",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}
