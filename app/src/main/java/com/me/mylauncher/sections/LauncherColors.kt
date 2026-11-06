
package com.me.mylauncher.sections

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

data class LauncherPalette(
    val id: String,
    val name: String,
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val secondaryAccent: Color,
    val danger: Color
) {
    val accentSoft get() = accent.copy(alpha = 0.14f)
    val border get() = textPrimary.copy(alpha = 0.07f)
    val divider get() = textPrimary.copy(alpha = 0.07f)
    val hairline get() = textPrimary.copy(alpha = 0.09f)
    val accentContainer get() = elevated
    val avatar get() = elevated
    val dotActive get() = accent
    val dotInactive get() = textPrimary.copy(alpha = 0.18f)
}

object LauncherPalettes {

    val QuietEditorial = LauncherPalette(
        id = "quiet",
        name = "Quiet",
        background = Color(0xFF100F0C),
        surface = Color(0xFF1B1914),
        elevated = Color(0xFF29261E),
        textPrimary = Color(0xFFEAE1CF),
        textSecondary = Color(0xFFA49A83),
        textTertiary = Color(0xFF655F52),
        accent = Color(0xFFC5A66D),
        onAccent = Color(0xFF16130D),
        secondaryAccent = Color(0xFF9B7661),
        danger = Color(0xFFE5766A)
    )

    val Crimson = LauncherPalette(
        id = "crimson",
        name = "Crimson",
        background = Color(0xFF120D0E),
        surface = Color(0xFF211719),
        elevated = Color(0xFF332124),
        textPrimary = Color(0xFFF0E2E0),
        textSecondary = Color(0xFFB49A99),
        textTertiary = Color(0xFF755C5D),
        accent = Color(0xFFD66B70),
        onAccent = Color(0xFF1A0D0E),
        secondaryAccent = Color(0xFFAA555D),
        danger = Color(0xFFFF817C)
    )

    val Violet = LauncherPalette(
        id = "violet",
        name = "Violet",
        background = Color(0xFF100E17),
        surface = Color(0xFF1D1929),
        elevated = Color(0xFF2B2540),
        textPrimary = Color(0xFFE8E2F4),
        textSecondary = Color(0xFFA99FBD),
        textTertiary = Color(0xFF6E6485),
        accent = Color(0xFFA995E8),
        onAccent = Color(0xFF171020),
        secondaryAccent = Color(0xFF8C79C2),
        danger = Color(0xFFEF7F91)
    )

    val Ember = LauncherPalette(
        id = "ember",
        name = "Ember",
        background = Color(0xFF15100C),
        surface = Color(0xFF241B14),
        elevated = Color(0xFF38291D),
        textPrimary = Color(0xFFF0E4D5),
        textSecondary = Color(0xFFB7A18A),
        textTertiary = Color(0xFF796550),
        accent = Color(0xFFE49A58),
        onAccent = Color(0xFF211308),
        secondaryAccent = Color(0xFFB87348),
        danger = Color(0xFFE87963)
    )

    val Sage = LauncherPalette(
        id = "sage",
        name = "Sage",
        background = Color(0xFF101512),
        surface = Color(0xFF1B231E),
        elevated = Color(0xFF303B33),
        textPrimary = Color(0xFFE0E9E1),
        textSecondary = Color(0xFF87958A),
        textTertiary = Color(0xFF5E6C61),
        accent = Color(0xFFA5C7A4),
        onAccent = Color(0xFF142017),
        secondaryAccent = Color(0xFF789C80),
        danger = Color(0xFFE5766A)
    )

    val all = listOf(
        QuietEditorial,
        Crimson,
        Violet,
        Ember,
        Sage
    )

    fun getById(id: String): LauncherPalette {
        return all.firstOrNull { it.id == id } ?: QuietEditorial
    }
}

/**
 * Central reactive colour provider.
 * Existing LauncherColors.Background references continue working.
 */
object LauncherColors {

    var current by mutableStateOf(LauncherPalettes.QuietEditorial)
        private set

    fun setPalette(palette: LauncherPalette) {
        current = palette
    }

    val Background get() = current.background
    val Surface get() = current.surface
    val Elevated get() = current.elevated

    val TextPrimary get() = current.textPrimary
    val TextSecondary get() = current.textSecondary
    val TextTertiary get() = current.textTertiary

    val Accent get() = current.accent
    val OnAccent get() = current.onAccent
    val AccentSoft get() = current.accentSoft

    val SecondaryAccent get() = current.secondaryAccent
    val Danger get() = current.danger

    val Border get() = current.border
    val Divider get() = current.divider
    val Hairline get() = current.hairline

    val AccentContainer get() = current.accentContainer
    val Avatar get() = current.avatar

    val DotActive get() = current.dotActive
    val DotInactive get() = current.dotInactive

    val Display = FontFamily.Serif

    val OuterFrameShape = RoundedCornerShape(32.dp)
    val ModuleShape = RoundedCornerShape(22.dp)
    val InteractiveTileShape = RoundedCornerShape(16.dp)
}
