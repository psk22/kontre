package com.kontre.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontre.app.ui.theme.StitchDarkBg
import com.kontre.app.ui.theme.StitchEmerald
import com.kontre.app.ui.theme.StitchTextMuted

enum class StitchTab(val title: String) {
    TOGGLES("Toggles"),
    LIMITS("Limits"),
    STREAKS("Streaks")
}

/**
 * Curved pill Bottom Navigation Bar matching the screenshot's floating pill architecture:
 * - Uses the app background theme color (dark surface) instead of white
 * - Retains original text titles and symbols (Toggles, ⏱ Limits, 🐉 Streaks)
 * - Outline-accent pill highlight for the active tab
 */
@Composable
fun StitchBottomNav(
    selectedTab: StitchTab,
    onTabSelected: (StitchTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color(0x66000000),
                    spotColor = Color(0x66000000)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF181820)) // Dark surface background matching app theme
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (tab in StitchTab.values()) {
                val isSelected = tab == selectedTab

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) StitchEmerald else StitchTextMuted,
                    animationSpec = tween(durationMillis = 200),
                    label = "TabTextColor"
                )

                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0x2207D98C) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "TabPillBg"
                )

                val pillBorderColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0x4407D98C) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "TabPillBorder"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(pillBgColor)
                        .border(1.dp, pillBorderColor, RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(tab)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (tab) {
                            StitchTab.TOGGLES -> tab.title
                            StitchTab.LIMITS -> "⏱ ${tab.title}"
                            StitchTab.STREAKS -> "🐉 ${tab.title}"
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = textColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
