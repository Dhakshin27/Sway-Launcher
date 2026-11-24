
package com.me.mylauncher.navigation

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.me.mylauncher.sections.LauncherColors
import com.me.mylauncher.sections.LauncherPalettes
import com.me.mylauncher.sections.SystemDeckSection
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
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 22.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
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
