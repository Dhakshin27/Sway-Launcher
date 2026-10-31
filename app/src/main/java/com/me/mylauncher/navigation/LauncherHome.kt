
package com.me.mylauncher.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.me.mylauncher.sections.YourSpace
import com.me.mylauncher.sections.YourApps
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding

private const val CARD_COUNT = 3
private const val TOTAL_PAGES = 3000

@Composable
fun LauncherHome() {

    val initialPage = remember {
        (TOTAL_PAGES / 2) -
                ((TOTAL_PAGES / 2) % CARD_COUNT)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(
                WindowInsets.systemBars
            )
    ){

        VerticalPager(
            state = pagerState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(16.dp),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize()
        ) { page ->

            when (page % CARD_COUNT) {
                0 -> YourSpace()
                1 -> YourApps()
                2 -> PlaceholderSection("QUICK ACCESS")
            }
        }
    }
}

@Composable
private fun PlaceholderSection(title: String) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF1A1A1A),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
            )
    )
}
