package com.kontre.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontre.app.R

enum class StitchTab(val title: String, val iconRes: Int) {
    TOGGLES("Home", R.drawable.ic_nav_home),
    LIMITS("Analytics", R.drawable.ic_nav_limits),
    STREAKS("History", R.drawable.ic_nav_streaks)
}

/**
 * Curved pill Bottom Navigation Bar matching the exact design in screenshot:
 * - Floating white card with curved top corners and gentle drop shadow
 * - Outline icons with clean labels below
 * - Selected active tab in vibrant royal indigo/purple (#5E35B1 / #6200EE)
 * - Inactive tabs in sleek dark charcoal (#222226)
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
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color(0x33000000),
                    spotColor = Color(0x33000000)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFFCFCFD))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeColor = Color(0xFF673AB7) // Vibrant Indigo / Violet from screenshot
            val inactiveColor = Color(0xFF1E1E24) // Deep minimal dark outline from screenshot

            for (tab in StitchTab.values()) {
                val isSelected = tab == selectedTab

                val tintColor by animateColorAsState(
                    targetValue = if (isSelected) activeColor else inactiveColor,
                    animationSpec = tween(durationMillis = 200),
                    label = "NavTint"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(tab)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = tab.iconRes),
                        contentDescription = tab.title,
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = tintColor,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}
