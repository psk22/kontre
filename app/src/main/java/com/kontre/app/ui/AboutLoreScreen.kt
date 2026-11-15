package com.kontre.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kontre.app.R
import com.kontre.app.ui.theme.StitchBorder
import com.kontre.app.ui.theme.StitchDarkBg
import com.kontre.app.ui.theme.StitchEmerald
import com.kontre.app.ui.theme.StitchEmeraldGlow
import com.kontre.app.ui.theme.StitchEmeraldMuted
import com.kontre.app.ui.theme.StitchSurface
import com.kontre.app.ui.theme.StitchSurfaceLight
import com.kontre.app.ui.theme.StitchTextMuted

/**
 * Dedicated Full-Screen About & Lore Page.
 * Showcases the Legend of Hero Dragon Kontre, the app's mission,
 * creator credits for Pranava Siva Karthik Yadam, and open-source attributions.
 */
@Composable
fun AboutLoreScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StitchDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Navigation Bar
        SubPageTopBar(
            title = "ABOUT KONTRE",
            subtitle = "THE SENTINEL OF FOCUS",
            onBack = onBack
        )

        // Main Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Hero Dragon Showcase
            HeroDragonSection()

            // Chapter 1: The Infinite Trance
            StorySection(
                chapterTag = "PROLOGUE // THE DIGITAL ABYSS",
                title = "The Age of the Infinite Trance",
                body = "In the golden era of human innovation, a subtle catastrophe enveloped civilization. The Great Feed—an omniscient labyrinth of neural prediction algorithms—learned to whisper to the most vulnerable corners of the human psyche.\n\n" +
                        "Seconds dissolved into hours. Creative fires turned to ash. Ambitions eroded beneath the cold, hypnotic luminescence of vertical glass. Billions found themselves trapped in a waking trance, endlessly scrolling through ephemeral fragments of manufactured reality."
            )

            // Chapter 2: The Awakening
            StorySection(
                chapterTag = "CHAPTER I // EMERALD RESISTANCE",
                title = "The Awakening of Kontre",
                body = "From the quiet rebellion of the sovereign human will arose Kontre—an ancient guardian dragon reborn in cyber-fire and obsidian scale. Kontre is not an instrument of punishment, but a shield of deliberate human power.\n\n" +
                        "Bearing eyes of luminous emerald and wings forged from unshakeable resolve, Kontre cuts through algorithmic illusions. Kontre represents the innate defiance that refuses to have its attention harvested for shareholder dividends."
            )

            // Chapter 3: The Pact
            StorySection(
                chapterTag = "CHAPTER II // THE COVENANT",
                title = "The Pact of Sovereignty",
                body = "Kontre enters into a solemn covenant with you: you define your daily boundary, and the Dragon honors it with uncompromising discipline.\n\n" +
                        "As long as you browse with purpose and intention, Kontre slumbers in silent protection. But the moment compulsive consumption breaches your declared limit, the Dragon ignites—severing the infinite feed, shattering the dopamine loop, and returning you to the physical universe where your real legacy awaits."
            )

            // App Mission & Core Pillars
            MissionPillarsSection()

            // Lead Architect Profile
            ArchitectProfileCard(context)

            // MIT License & Open Source
            OpenSourceLicenseSection(context)

            // Bottom Breathing Room
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Universal Sub-Page Top Navigation Bar with Back Button.
 */
@Composable
fun SubPageTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tactile Back Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StitchSurface)
                    .border(1.dp, StitchBorder, RoundedCornerShape(12.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "←",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = StitchEmerald
                )
            }

            // Title Block
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = StitchEmerald
                )
            }

            // App Version Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(StitchEmeraldMuted)
                    .border(1.dp, Color(0x3307D98C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "v1.8.4",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = StitchEmerald
                )
            }
        }

        // Sub-bar hairline divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x3307D98C),
                        Color.Transparent
                    )
                ))
        )
    }
}

@Composable
private fun HeroDragonSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Glowing Dragon Aura
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x3307D98C),
                            Color(0x1107D98C),
                            Color.Transparent
                        )
                    )
                )
                .border(1.dp, Color(0x2207D98C), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_kontre_dragon_vector),
                contentDescription = "Kontre Dragon Hero",
                tint = StitchEmerald,
                modifier = Modifier.size(54.dp)
            )
        }

        // Wordmark
        Text(
            text = "KONTRE",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 6.sp,
            color = Color.White
        )

        Text(
            text = "RECLAIM YOUR ATTENTION • MASTER YOUR FEED",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = StitchEmerald
        )

        // Spec telemetry chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            SpecChip(text = "AIR-GAPPED SILICON")
            SpecChip(text = "ZERO CLOUD")
            SpecChip(text = "ANTI-INFINITE LOOP")
        }
    }
}

@Composable
private fun SpecChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF181822))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = StitchTextMuted
        )
    }
}

@Composable
private fun StorySection(
    chapterTag: String,
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161620))
            .border(1.dp, Color(0x1F07D98C), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = chapterTag,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = StitchEmerald
        )

        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 0.5.sp
        )

        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(StitchEmerald)
        )

        Text(
            text = body,
            fontSize = 13.5.sp,
            lineHeight = 22.sp,
            color = Color(0xFFD4D4DE),
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun MissionPillarsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "THE THREE PILLARS OF DIGITAL SOVEREIGNTY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = StitchTextMuted
        )

        PillarRow(
            number = "01",
            title = "Intention Over Impulse",
            description = "Technology should be an instrument of your ambitions, not a slot machine tuned to exploit evolutionary neurochemistry."
        )

        PillarRow(
            number = "02",
            title = "The Power of the Hard Wall",
            description = "Gentle screen time reminders are effortlessly swiped away. Kontre imposes a firm, physical threshold that forces conscious re-engagement."
        )

        PillarRow(
            number = "03",
            title = "Zero-Compromise Privacy",
            description = "A guardian that tracks your behaviour is merely another surveillance agent. Kontre computes entirely within your device's memory."
        )
    }
}

@Composable
private fun PillarRow(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF181824))
            .border(1.dp, StitchBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = number,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            color = StitchEmerald
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = StitchTextMuted
            )
        }
    }
}

@Composable
private fun ArchitectProfileCard(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161622))
            .border(1.dp, Color(0x3307D98C), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Architect Monogram Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF20202E))
                    .border(1.5.dp, StitchEmerald, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PSK",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = StitchEmerald
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Pranava Siva Karthik Yadam",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Creator & Systems Architect • Kontre",
                    fontSize = 11.sp,
                    color = StitchEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Kontre was built out of personal necessity—to engineer a digital shield against the hypnotic pull of infinite-scroll algorithms, while preserving absolute data privacy.",
            fontSize = 12.sp,
            color = Color(0xFFC0C0D0),
            lineHeight = 18.sp,
            fontStyle = FontStyle.Italic
        )

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StitchEmerald)
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/psk22/kontre")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "GITHUB REPO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color(0xFF0D0D12)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF242432))
                    .border(1.dp, StitchBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:yadampranav@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Kontre Digital Wellbeing Inquiry")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONTACT ARCHITECT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun OpenSourceLicenseSection(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14141C))
            .border(1.dp, StitchBorder, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OPEN SOURCE & LICENSING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = StitchTextMuted
            )

            Text(
                text = "MIT LICENSE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = StitchEmerald,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(StitchEmeraldMuted)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text(
            text = "Copyright (c) 2026 Pranava Siva Karthik Yadam\n\n" +
                    "Permission is hereby granted, free of charge, to any person obtaining a copy of this software to use, copy, modify, and distribute without restriction.",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF9E9EAA),
            lineHeight = 16.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x14FFFFFF))
        )

        Text(
            text = "Engineered with Android Jetpack Compose, Material Design 3, Kotlin Coroutines, and WorkManager under the Apache License 2.0.",
            fontSize = 10.5.sp,
            color = StitchTextMuted,
            lineHeight = 15.sp
        )
    }
}

