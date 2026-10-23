package com.reelshield.app.ui

import android.content.Context
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelshield.app.data.QuotaManager
import com.reelshield.app.ui.theme.AshText
import com.reelshield.app.ui.theme.AubergineCanvas
import com.reelshield.app.ui.theme.CoralEmber
import com.reelshield.app.ui.theme.CoralEmberGlow
import com.reelshield.app.ui.theme.CoralEmberMuted
import com.reelshield.app.ui.theme.ElevatedPlum
import com.reelshield.app.ui.theme.FogText
import com.reelshield.app.ui.theme.KontreTheme
import com.reelshield.app.ui.theme.PureWhite
import com.reelshield.app.ui.theme.WhisperBorder
import kotlinx.coroutines.delay

class BlockOverlayActivity : ComponentActivity() {

    private lateinit var quotaManager: QuotaManager
    private var sourceApp: String? = null

    companion object {
        const val EXTRA_SOURCE_APP = "extra_source_app"

        fun launch(context: Context, sourceApp: String? = null) {
            val intent = Intent(context, BlockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_SOURCE_APP, sourceApp)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quotaManager = QuotaManager(this)
        sourceApp = intent.getStringExtra(EXTRA_SOURCE_APP)

        setContent {
            KontreTheme {
                BlockOverlayScreen(
                    quotaManager = quotaManager,
                    sourceApp = sourceApp,
                    onBackToApp = {
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
            .background(AubergineCanvas.copy(alpha = 0.98f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {

            // Glowing Pulsing Indicator Ring with Coral Ember
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(CoralEmberGlow, Color.Transparent)
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, CoralEmber, CircleShape)
            ) {
                Text(
                    text = "${quotaManager.dailyLimit}",
                    color = CoralEmber,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .background(CoralEmberMuted, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "REELS & SHORTS LOCKED",
                    color = CoralEmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${quotaManager.dailyLimit} of ${quotaManager.dailyLimit} Daily Videos Reached",
                color = PureWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Short-form video feeds are locked until midnight. Your chats, Direct Messages, and Stories remain fully available.",
                color = FogText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Midnight Countdown Card (20dp Elevated Plum Card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElevatedPlum, RoundedCornerShape(20.dp))
                    .border(1.dp, WhisperBorder, RoundedCornerShape(20.dp))
                    .padding(vertical = 18.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "RESETS AT MIDNIGHT (00:00)",
                        color = AshText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = countdownText,
                        color = PureWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Button: Return to Instagram / App (Chats & Stories) (100dp Coral Pill)
            Button(
                onClick = onBackToApp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralEmber,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Back to $appLabel (Chats & Stories)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Button: Exit to Home Screen (100dp Outlined Pill)
            OutlinedButton(
                onClick = onReturnHome,
                shape = RoundedCornerShape(100.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(WhisperBorder)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Close & Go to Home Screen",
                    color = AshText,
                    fontSize = 14.sp
                )
            }
        }
    }
}
