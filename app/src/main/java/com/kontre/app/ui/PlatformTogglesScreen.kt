package com.kontre.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontre.app.R
import com.kontre.app.data.QuotaManager
import com.kontre.app.service.ContentTrackerService
import com.kontre.app.ui.theme.StitchDarkBg
import com.kontre.app.ui.theme.StitchEmerald
import com.kontre.app.ui.theme.StitchInstagramPink
import com.kontre.app.ui.theme.StitchTextMuted
import com.kontre.app.ui.theme.StitchYoutubeRed

/**
 * Google Stitch "Platform Toggles" screen (Page 2).
 * Clean, unboxed minimal list design with platform icons:
 * - Direct typography rows with clean vector icons (without layout blocks, borders, or badge boxes).
 * - Section header "PLATFORM TOGGLES"
 * - Instant reactive preference syncing with QuotaManager.
 */
@Composable
fun PlatformTogglesScreen(
    quotaManager: QuotaManager,
    isAccessibilityEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var blockYt by remember { mutableStateOf(quotaManager.isYouTubeBlockEnabled) }
    var blockIg by remember { mutableStateOf(quotaManager.isInstagramBlockEnabled) }
    var showFloatingBadge by remember { mutableStateOf(quotaManager.isFloatingCounterEnabled) }
    var totalBlock by remember { mutableStateOf(quotaManager.isTotalBlockEnabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StitchDarkBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        // Section Label
        Text(
            text = "PLATFORM TOGGLES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.5.sp,
            color = StitchTextMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 2.dp)
        )

        // 1. YouTube Shorts Toggle
        StitchToggleRow(
            title = "BLOCK YOUTUBE SHORTS",
            subtitle = "Filter short-form feeds",
            iconRes = R.drawable.ic_youtube_shorts,
            iconColor = StitchYoutubeRed,
            isChecked = blockYt,
            checkedColor = StitchYoutubeRed,
            onCheckedChange = { checked ->
                blockYt = checked
                quotaManager.isYouTubeBlockEnabled = checked
            }
        )

        HorizontalDivider(
            color = Color(0x14FFFFFF),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // 2. Instagram Reels Toggle
        StitchToggleRow(
            title = "BLOCK INSTAGRAM REELS",
            subtitle = "Restrict Reels & Discovery tab",
            iconRes = R.drawable.ic_instagram_reels,
            iconColor = StitchInstagramPink,
            isChecked = blockIg,
            checkedColor = StitchInstagramPink,
            onCheckedChange = { checked ->
                blockIg = checked
                quotaManager.isInstagramBlockEnabled = checked
            }
        )

        HorizontalDivider(
            color = Color(0x14FFFFFF),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // 3. Live Counter Toggle
        StitchToggleRow(
            title = "LIVE COUNTER",
            subtitle = "Real-time floating consumption tracker",
            iconRes = R.drawable.ic_live_counter,
            iconColor = StitchEmerald,
            isChecked = showFloatingBadge,
            checkedColor = StitchEmerald,
            onCheckedChange = { checked ->
                showFloatingBadge = checked
                quotaManager.isFloatingCounterEnabled = checked
                if (!checked) {
                    ContentTrackerService.hideOverlayIfActive()
                }
            }
        )

        HorizontalDivider(
            color = Color(0x14FFFFFF),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // 4. Total Block Mode Toggle
        StitchToggleRow(
            title = "TOTAL BLOCK MODE",
            subtitle = "Zero-allowance immediate lockout",
            iconRes = R.drawable.ic_shield_lock,
            iconColor = Color(0xFFFF9F0A),
            isChecked = totalBlock,
            checkedColor = Color(0xFFFF9F0A),
            onCheckedChange = { checked ->
                totalBlock = checked
                quotaManager.isTotalBlockEnabled = checked
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Clean unboxed toggle row with vector icon and without container borders or badge blocks.
 */
@Composable
private fun StitchToggleRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    iconColor: Color,
    isChecked: Boolean,
    checkedColor: Color,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = StitchTextMuted
                )
            }
        }

        // Switch with pure white thumb & vibrant platform-tinted track
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = checkedColor,
                uncheckedThumbColor = Color(0xFFAAAAAA),
                uncheckedTrackColor = Color(0xFF282832),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

