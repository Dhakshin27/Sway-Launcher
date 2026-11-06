package com.me.mylauncher.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.me.mylauncher.sections.LauncherColors

@Composable
fun CardContainer(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LauncherColors.Background,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(LauncherColors.OuterFrameShape)
            .background(backgroundColor)
            .border(
                1.dp,
                LauncherColors.Border,
                LauncherColors.OuterFrameShape
            )
            .padding(contentPadding),
        content = content
    )
}