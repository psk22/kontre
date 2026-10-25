package com.reelshield.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelshield.app.data.QuotaManager
import com.reelshield.app.ui.theme.AshText
import com.reelshield.app.ui.theme.AubergineCanvas
import com.reelshield.app.ui.theme.CoralEmber
import com.reelshield.app.ui.theme.ElevatedPlum
import com.reelshield.app.ui.theme.FogText
import com.reelshield.app.ui.theme.KontreTheme
import com.reelshield.app.ui.theme.PureWhite
import com.reelshield.app.ui.theme.WhisperBorder

class BlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"

        fun launch(context: Context, blockedPackage: String) {
            val intent = Intent(context, BlockActivity::class.java).apply {
                putExtra(EXTRA_BLOCKED_PACKAGE, blockedPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "Short-Form Video App"
        val quotaManager = QuotaManager(this)

        setContent {
            KontreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AubergineCanvas
                ) {
                    BlockScreen(
                        blockedPackage = blockedPackage,
                        quotaManager = quotaManager,
                        onGoHome = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                            finish()
                        },
                        onOpenAccessibility = {
                            val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(settingsIntent)
                            finish()
                        }
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
fun BlockScreen(
    blockedPackage: String,
    quotaManager: QuotaManager,
    onGoHome: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    val appDisplayName = when {
        blockedPackage.contains("instagram") -> "Instagram"
        blockedPackage.contains("youtube") -> "YouTube"
        else -> "Monitored App"
    }

    val total = quotaManager.totalCount
    val limit = quotaManager.dailyLimit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon Badge
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(ElevatedPlum, CircleShape)
                .border(1.dp, WhisperBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🛡️", fontSize = 32.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "FOCUS SHIELD ENGAGED",
            color = PureWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "$appDisplayName blocked by Kontre",
            color = CoralEmber,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedPlum),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(WhisperBorder))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "DAILY QUOTA STATUS",
                    color = AshText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$total / $limit Videos Used",
                    color = PureWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "UsageStats Fallback active: Accessibility service was killed or disabled by the system.",
                    color = FogText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Primary: Return Home
        Button(
            onClick = onGoHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CoralEmber),
            shape = RoundedCornerShape(100.dp)
        ) {
            Text(
                text = "Return to Home",
                color = PureWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary: Re-Enable Accessibility
        OutlinedButton(
            onClick = onOpenAccessibility,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(100.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(WhisperBorder))
        ) {
            Text(
                text = "Re-Enable Accessibility Guard",
                color = PureWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
