package com.me.mylauncher.navigation

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.me.mylauncher.sections.LauncherColors
import com.me.mylauncher.sections.LauncherPalettes
import com.me.mylauncher.sections.YourApps
import com.me.mylauncher.sections.YourSpace

private const val CARD_COUNT = 3
private const val TOTAL_PAGES = 3000

@Composable
fun LauncherHome() {
    val context = LocalContext.current

    var selectedPaletteId by remember {
        val savedId = context
            .getSharedPreferences("launcher_appearance", Context.MODE_PRIVATE)
            .getString("selected_palette", "quiet") ?: "quiet"

        LauncherColors.setPalette(LauncherPalettes.getById(savedId))
        mutableStateOf(savedId)
    }

    LaunchedEffect(selectedPaletteId) {
        val palette = LauncherPalettes.getById(selectedPaletteId)
        LauncherColors.setPalette(palette)

        context
            .getSharedPreferences("launcher_appearance", Context.MODE_PRIVATE)
            .edit()
            .putString("selected_palette", selectedPaletteId)
            .apply()
    }

    val initialPage = remember {
        (TOTAL_PAGES / 2) - ((TOTAL_PAGES / 2) % CARD_COUNT)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { TOTAL_PAGES }
    )

    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    val activeSection = pagerState.settledPage % CARD_COUNT

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LauncherColors.Background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        VerticalPager(
            state = pagerState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(start = 12.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
            pageSpacing = 14.dp,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val isPageActive = pagerState.settledPage == page
            when (page % CARD_COUNT) {
                0 -> YourSpace(isActive = isPageActive)
                1 -> YourApps(isActive = isPageActive)
                2 -> SystemDeckSection(
                    selectedPaletteId = selectedPaletteId,
                    onPaletteSelected = { selectedPaletteId = it }
                )
            }
        }

        // Exposed Right-Edge Vertical Telemetry Rail
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(CARD_COUNT) { idx ->
                val isSelected = activeSection == idx
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(if (isSelected) 28.dp else 8.dp)
                        .background(
                            if (isSelected) LauncherColors.Accent
                            else LauncherColors.TextTertiary.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

/**
 * Replaces the empty placeholder with an ultra-clean System Telemetry & Control Deck.
 */

@Composable
private fun SystemDeckSection(
    selectedPaletteId: String,
    onPaletteSelected: (String) -> Unit
) {
    val context = LocalContext.current

    // Read storage information once when this section is created.
    val storageStats = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val gb = 1024L * 1024L * 1024L

            val total = (stat.totalBytes / gb).coerceAtLeast(1L)
            val free = stat.availableBytes / gb
            val used = (total - free).coerceAtLeast(0L)
            val percentage =
                ((used * 100L) / total).toInt().coerceIn(0, 100)

            Triple(used, total, percentage)
        } catch (_: Exception) {
            Triple(0L, 1L, 0)
        }
    }

    val selectedPalette = LauncherPalettes.all
        .firstOrNull { it.id == selectedPaletteId }
        ?: LauncherPalettes.all.first()

    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 720

    val tileHeight = if (compact) 48.dp else 54.dp
    val sectionSpacing = if (compact) 9.dp else 13.dp
    val horizontalPadding = if (compact) 15.dp else 19.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(LauncherColors.OuterFrameShape)
            .background(LauncherColors.Background)
            .border(
                1.dp,
                LauncherColors.Border,
                LauncherColors.OuterFrameShape
            )
            .padding(horizontal = horizontalPadding, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {

        // Header
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "YOUR LAUNCHER",
                    color = LauncherColors.TextTertiary,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    "03 / 03",
                    color = LauncherColors.SecondaryAccent,
                    fontSize = 9.sp
                )
            }

            Text(
                "Your System.",
                color = LauncherColors.TextPrimary,
                fontSize = if (compact) 26.sp else 29.sp,
                fontFamily = LauncherColors.Display,
                fontWeight = FontWeight.Normal
            )

            Text(
                "A FEW THINGS, JUST THE WAY YOU LIKE",
                color = LauncherColors.TextSecondary,
                fontSize = 8.sp,
                letterSpacing = 1.2.sp
            )
        }

        // Appearance
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Appearance",
                    color = LauncherColors.TextPrimary,
                    fontSize = 17.sp,
                    fontFamily = LauncherColors.Display
                )

                Text(
                    selectedPalette.name,
                    color = LauncherColors.Accent,
                    fontSize = 10.sp
                )
            }

            PaletteSelector(
                selectedPaletteId = selectedPaletteId,
                onPaletteSelected = onPaletteSelected
            )
        }

        // Storage
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LauncherColors.ModuleShape)
                .background(LauncherColors.Surface)
                .clickable {
                    openSystemIntent(
                        context,
                        Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                    )
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "Storage",
                        color = LauncherColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        "${storageStats.first} GB of ${storageStats.second} GB used",
                        color = LauncherColors.TextSecondary,
                        fontSize = 10.sp
                    )
                }

                Text(
                    "${storageStats.third}%",
                    color = LauncherColors.Accent,
                    fontSize = 23.sp,
                    fontFamily = LauncherColors.Display
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(LauncherColors.Elevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(storageStats.third / 100f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(LauncherColors.Accent)
                )
            }
        }

        // Device settings
        Column(
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                "Device settings",
                color = LauncherColors.TextPrimary,
                fontSize = 17.sp,
                fontFamily = LauncherColors.Display
            )

            val settings = listOf(
                Triple("Wi-Fi", "⌁", Settings.ACTION_WIFI_SETTINGS),
                Triple("Bluetooth", "ᛒ", Settings.ACTION_BLUETOOTH_SETTINGS),
                Triple("Display", "☼", Settings.ACTION_DISPLAY_SETTINGS),
                Triple("Sound", "♫", Settings.ACTION_SOUND_SETTINGS),
                Triple("Battery", "◒", Settings.ACTION_BATTERY_SAVER_SETTINGS),
                Triple("Applications", "▦", Settings.ACTION_APPLICATION_SETTINGS)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                settings.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            SystemSettingTile(
                                title = item.first,
                                symbol = item.second,
                                height = tileHeight,
                                modifier = Modifier.weight(1f)
                            ) {
                                openSystemIntent(context, item.third)
                            }
                        }

                        // Keeps the final row aligned.
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Android settings shortcut
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LauncherColors.InteractiveTileShape)
                .background(LauncherColors.Accent)
                .clickable {
                    openSystemIntent(context, Settings.ACTION_SETTINGS)
                }
                .padding(horizontal = 15.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "All Android settings",
                color = LauncherColors.OnAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "↗",
                color = LauncherColors.OnAccent,
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun SystemSettingTile(
    title: String,
    symbol: String,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(LauncherColors.InteractiveTileShape)
            .background(LauncherColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = symbol,
            color = LauncherColors.Accent,
            fontSize = 18.sp
        )

        Text(
            text = title,
            color = LauncherColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}



@Composable
private fun SystemSectionHeading(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            color = LauncherColors.TextPrimary,
            fontSize = 19.sp,
            fontFamily = LauncherColors.Display
        )

        Text(
            subtitle,
            color = LauncherColors.TextSecondary,
            fontSize = 11.sp
        )
    }
}


@Composable
private fun PaletteSelector(
    selectedPaletteId: String,
    onPaletteSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LauncherPalettes.all.forEach { palette ->
            val selected = selectedPaletteId == palette.id

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onPaletteSelected(palette.id)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .then(
                            if (selected) {
                                Modifier.border(
                                    1.5.dp,
                                    LauncherColors.TextPrimary,
                                    CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(if (selected) 4.dp else 0.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                )

                Text(
                    text = palette.name,
                    color = if (selected) {
                        LauncherColors.TextPrimary
                    } else {
                        LauncherColors.TextTertiary
                    },
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}



private fun openSystemIntent(context: Context, action: String) {
    try {
        context.startActivity(
            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }
}