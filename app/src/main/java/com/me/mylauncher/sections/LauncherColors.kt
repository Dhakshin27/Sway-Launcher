package com.me.mylauncher.sections

import androidx.compose.ui.graphics.Color

object LauncherColors {

    // Main surfaces
    val Background = Color(0xFF101512)
    val Surface = Color(0xFF1B231E)
    val Elevated = Color(0xFF303B33)

    // Typography
    val TextPrimary = Color(0xFFE0E9E1)
    val TextSecondary = Color(0xFF87958A)

    // Accent
    val Accent = Color(0xFFA5C7A4)

    // Supporting colours
    val Border = Elevated.copy(alpha = 0.45f)
    val Divider = Elevated.copy(alpha = 0.55f)
    val Hairline = Elevated.copy(alpha = 0.35f)

    // Interactive elements
    val AccentContainer = Elevated
    val Avatar = Elevated

    // Indicators
    val DotActive = Accent
    val DotInactive = TextSecondary.copy(alpha = 0.35f)
}