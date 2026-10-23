package com.reelshield.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Superlist Midnight Workspace Design Tokens (DESIGN.md)
// Theme: "midnight workspace with coral embers"
// ============================================================================

// Canvas & Surfaces (Warm-tinted darks, avoiding pure black)
val AubergineCanvas = Color(0xFF181824)    // Page background, primary canvas
val ElevatedPlum = Color(0xFF26253B)       // Cards, input fields, containers
val ElevatedPlumDark = Color(0xFF1E1D30)   // Recessed surfaces, track backgrounds
val SurfacePop = Color(0xFF2E2D45)         // Hover / lifted elements

// Typography Neutrals
val PureWhite = Color(0xFFFFFFFF)          // Headings, active icons, maximum contrast
val FogText = Color(0xFF696F81)            // Body text, secondary headings (7:1 contrast)
val AshText = Color(0xFF8E8DA0)            // Supporting neutral, labels, muted subheads
val MistHighlight = Color(0xFFA3A3A7)      // Faint neutral, subtle dividers
val WhisperBorder = Color(0xFF32314A)      // Hairline container strokes on dark canvas

// Ignition Accent (Single high-contrast spark)
val CoralEmber = Color(0xFFFF4A36)         // Primary action, CTA, warning, spark
val CoralEmberGlow = Color(0x33FF4A36)     // Ambient glow for coral elements
val CoralEmberMuted = Color(0x26FF4A36)    // 15% opacity tint for badges and alerts

// Complementary Mood Accent
val IrisAccent = Color(0xFF535676)         // Secondary headings, subtle violet text
val IrisAccentMuted = Color(0x26535676)    // Soft violet chip background

// Status Indicators
val StatusGreen = Color(0xFF34C759)        // Active protection green
val StatusGreenMuted = Color(0x2634C759)   // Green chip background
val StatusYellow = CoralEmber              // In bichromatic discipline, Coral Ember sparks warm warnings

// Aliases for compatibility with existing components
val DarkBackground = AubergineCanvas
val DarkSurface = ElevatedPlum
val DarkSurfaceVariant = ElevatedPlumDark
val AccentRed = CoralEmber
val AccentRedGlow = CoralEmberGlow
val TextPrimary = PureWhite
val TextSecondary = AshText
val TextMuted = FogText
val BorderColor = WhisperBorder
val SuccessGreen = StatusGreen
