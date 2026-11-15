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
import com.kontre.app.ui.theme.StitchEmeraldMuted
import com.kontre.app.ui.theme.StitchSurface
import com.kontre.app.ui.theme.StitchTextMuted

/**
 * Dedicated Full-Screen Credits & Open Source Licenses Screen.
 */
@Composable
fun CreditsScreen(
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
            title = "CREDITS & LICENSES",
            subtitle = "CREATOR & OPEN SOURCE",
            onBack = onBack
        )

        // Main Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            // App Identity Header
            CreditsHeroBanner()

            // Lead Developer Profile Card
            CreatorProfileCard(context)

            // MIT Project License
            MitLicenseCard()

            // Open Source Suite Attributions
            OpenSourceSuiteSection()

            // Special Thanks & Community Acknowledgments
            CommunityAcknowledgmentsSection()

            // Bottom Breathing Room
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CreditsHeroBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161622))
            .border(1.dp, Color(0x2207D98C), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(Color(0x2207D98C))
                .border(1.5.dp, StitchEmerald, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_kontre_dragon_vector),
                contentDescription = "Kontre Dragon",
                tint = StitchEmerald,
                modifier = Modifier.size(50.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "KONTRE DIGITAL WELLBEING",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White
            )
            Text(
                text = "Version 1.8.5 • Build 24 • Target Android 15 (SDK 35)",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = StitchEmerald
            )
            Text(
                text = "Free & Open-Source Software",
                fontSize = 11.sp,
                color = StitchTextMuted
            )
        }
    }
}

@Composable
private fun CreatorProfileCard(context: Context) {
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
            // Monogram Badge
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
                    text = "Creator & Lead Developer",
                    fontSize = 11.sp,
                    color = StitchEmerald,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "yadampranav@gmail.com",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = StitchTextMuted
                )
            }
        }

        Text(
            text = "Designed and engineered to give every user full control over their dopamine and screen time, without sacrificing digital privacy.",
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
                                putExtra(Intent.EXTRA_SUBJECT, "Kontre App Inquiry")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EMAIL DEVELOPER",
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
private fun MitLicenseCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14141C))
            .border(1.dp, StitchBorder, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROJECT LICENSE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
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
            text = "MIT License\n\n" +
                    "Copyright (c) 2026 Pranava Siva Karthik Yadam\n\n" +
                    "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software.\n\n" +
                    "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.",
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp,
            color = Color(0xFFA0A0B0)
        )
    }
}

@Composable
private fun OpenSourceSuiteSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "OPEN SOURCE DEPENDENCIES",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = StitchTextMuted
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color(0x1FFFFFFF))
            )
        }

        OpenSourceItem(
            name = "Android Jetpack Compose & UI",
            author = "The Android Open Source Project (Google)",
            license = "Apache 2.0",
            description = "Modern declarative UI toolkit for native Android development."
        )

        OpenSourceItem(
            name = "Material Design 3 Components",
            author = "Google LLC",
            license = "Apache 2.0",
            description = "Google's open-source design system foundation and theming."
        )

        OpenSourceItem(
            name = "Kotlin & KotlinX Coroutines",
            author = "JetBrains s.r.o. and Contributors",
            license = "Apache 2.0",
            description = "High-performance asynchronous reactive concurrency framework."
        )

        OpenSourceItem(
            name = "AndroidX WorkManager & Lifecycle",
            author = "The Android Open Source Project (Google)",
            license = "Apache 2.0",
            description = "Reliable background watchdog task scheduling and lifecycle scopes."
        )

        OpenSourceItem(
            name = "AndroidX Core KTX & Activity Compose",
            author = "The Android Open Source Project (Google)",
            license = "Apache 2.0",
            description = "Core Kotlin extensions and modern Activity lifecycle integration."
        )
    }
}

@Composable
private fun OpenSourceItem(
    name: String,
    author: String,
    license: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161622))
            .border(1.dp, StitchBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = license,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = StitchEmerald,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x2207D98C))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text(
            text = author,
            fontSize = 10.5.sp,
            color = StitchTextMuted
        )

        Text(
            text = description,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = Color(0xFFB0B0C0)
        )
    }
}

@Composable
private fun CommunityAcknowledgmentsSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14141C))
            .border(1.dp, StitchBorder, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "SPECIAL THANKS & ACKNOWLEDGMENTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            color = StitchTextMuted
        )

        Text(
            text = "• The Android Open Source Project (AOSP) community for empowering independent developer innovation.\n" +
                    "• Digital Wellbeing researchers illuminating the cognitive impacts of short-form video algorithms.\n" +
                    "• Early community alpha testers who provided critical feedback on heuristic swipe accuracy and tactile haptics.",
            fontSize = 11.5.sp,
            color = Color(0xFFC0C0D0),
            lineHeight = 18.sp
        )
    }
}

