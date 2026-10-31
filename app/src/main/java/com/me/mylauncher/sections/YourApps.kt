package com.me.mylauncher.sections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.me.mylauncher.data.AppRecord
import com.me.mylauncher.data.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Constants & design tokens
// ---------------------------------------------------------------------------

private const val COLUMNS = 2
private const val ROWS = 3
private const val APPS_PER_PAGE = COLUMNS * ROWS
private const val SEARCH_DEBOUNCE_MS = 150L
private const val MAX_DOT_PAGES = 8

private object Palette {

    val Background = LauncherColors.Background
    val SearchField = LauncherColors.Surface

    val TextPrimary = LauncherColors.TextPrimary
    val TextHint = LauncherColors.TextSecondary

    val IconMuted = LauncherColors.TextSecondary

    val Hairline = LauncherColors.Hairline
    val CardBorder = LauncherColors.Border

    val DotActive = LauncherColors.DotActive
    val DotInactive = LauncherColors.DotInactive
}
private object Dimens {
    val ScreenRadius = 30.dp
    val ScreenPaddingH = 16.dp
    val ScreenPaddingV = 18.dp
    val SearchHeight = 50.dp
    val SearchRadius = 16.dp
    val CardRadius = 20.dp
    val GridSpacing = 12.dp
    val PageSpacing = 14.dp
    val IconSize = 58.dp
}

// Built once instead of on every recomposition.
// Restrained graphite surfaces.
// Small tonal differences create depth without looking colourful.
private val cardBrushes: List<Brush> = listOf(

    Brush.linearGradient(
        listOf(
            Color(0xFF303B33),
            Color(0xFF1B231E)
        )
    ),

    Brush.linearGradient(
        listOf(
            Color(0xFF29362D),
            Color(0xFF1B231E)
        )
    ),

    Brush.linearGradient(
        listOf(
            Color(0xFF344137),
            Color(0xFF202A23)
        )
    )
)

private val grayscaleFilter: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

// ---------------------------------------------------------------------------
// Filtering / sorting (pure function, runs off the main thread)
// ---------------------------------------------------------------------------

/**
 * Empty query  -> most used first, then alphabetical.
 * With a query -> label prefix matches, then label contains, then package
 *                 matches; most used first inside each group.
 */
private fun filterAndSort(apps: List<AppRecord>, query: String): List<AppRecord> {
    if (query.isEmpty()) {
        return apps.sortedWith(
            compareByDescending<AppRecord> { it.launchCount }
                .thenBy { it.label.lowercase() }
        )
    }

    fun rank(app: AppRecord): Int = when {
        app.label.startsWith(query, ignoreCase = true) -> 0
        app.label.contains(query, ignoreCase = true) -> 1
        app.packageName.contains(query, ignoreCase = true) -> 2
        else -> -1
    }

    return apps
        .map { it to rank(it) }
        .filter { it.second >= 0 }
        .sortedWith(
            compareBy<Pair<AppRecord, Int>> { it.second }
                .thenByDescending { it.first.launchCount }
                .thenBy { it.first.label.lowercase() }
        )
        .map { it.first }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun YourApps(isActive: Boolean = true) {
    val context = LocalContext.current
    val repository = remember(context) {
        (context.applicationContext as LauncherApp).appRepository
    }
    val apps by repository.apps.collectAsState()

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    // Debounce typing, but apply clearing instantly.
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = searchQuery.trim()
    }

    val initialApps = remember { filterAndSort(apps, "") }
    val visibleApps by produceState(initialApps, apps, debouncedQuery) {
        value = withContext(Dispatchers.Default) { filterAndSort(apps, debouncedQuery) }
    }

    val appPages = remember(visibleApps) { visibleApps.chunked(APPS_PER_PAGE) }
    val pagerState = rememberPagerState(pageCount = { appPages.size })

    // Jump back to the first page only when the *query* changes, not when
    // icons load or launch counts update.
    LaunchedEffect(debouncedQuery) {
        if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    // Hide keyboard when the user swipes pages or this section goes off-screen.
    LaunchedEffect(pagerState.currentPage) { keyboard?.hide() }
    LaunchedEffect(isActive) {
        if (!isActive) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    val launchApp: (AppRecord) -> Unit = remember(repository) {
        { app ->
            repository.launch(app)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Dimens.ScreenRadius))
            .background(Palette.Background)
            .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV)
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSubmit = {
                visibleApps.firstOrNull()?.let(launchApp)
                keyboard?.hide()
                focusManager.clearFocus()
            }
        )

        Spacer(Modifier.height(14.dp))

        if (appPages.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                pageSpacing = Dimens.PageSpacing,
                userScrollEnabled = isActive,
                beyondViewportPageCount = 1
            ) { page ->
                AppGridPage(
                    pageApps = appPages[page],
                    pageIndex = page,
                    enabled = isActive,
                    onLaunch = launchApp
                )
            }
        } else {
            EmptyState(
                message = when {
                    apps.isEmpty() -> "No apps available"
                    else -> "No results for “$debouncedQuery”"
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        if (appPages.size > 1) {
            Spacer(Modifier.height(14.dp))
            PageIndicator(
                pageCount = appPages.size,
                currentPage = pagerState.currentPage
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Search bar
// ---------------------------------------------------------------------------

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(Dimens.SearchRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.SearchHeight)
            .clip(shape)
            .background(Palette.SearchField)
            .border(1.dp, Palette.Hairline, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchGlyph(
            modifier = Modifier
                .size(18.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focusRequester.requestFocus() },
            color = Palette.IconMuted
        )

        Spacer(Modifier.width(12.dp))

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = TextStyle(
                color = LauncherColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(LauncherColors.Accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search apps",
                            color = Palette.TextHint,
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(LauncherColors.Elevated)
                    .semantics { contentDescription = "Clear search" }
                    .clickable(role = Role.Button) { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                CloseGlyph(
                    modifier = Modifier.size(10.dp),
                    color = LauncherColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SearchGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension * 0.32f
        val center = Offset(radius + stroke / 2f, radius + stroke / 2f)
        drawCircle(color, radius, center, style = Stroke(stroke))

        val d = radius * 0.7071f
        drawLine(
            color = color,
            start = Offset(center.x + d, center.y + d),
            end = Offset(size.width - stroke / 2f, size.height - stroke / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CloseGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------
// Grid page
// ---------------------------------------------------------------------------

@Composable
private fun AppGridPage(
    pageApps: List<AppRecord>,
    pageIndex: Int,
    enabled: Boolean,
    onLaunch: (AppRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.GridSpacing)
    ) {
        // Always render ROWS x COLUMNS slots so partial pages keep identical card sizes.
        repeat(ROWS) { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.GridSpacing)
            ) {
                repeat(COLUMNS) { col ->
                    val indexInPage = row * COLUMNS + col
                    val app = pageApps.getOrNull(indexInPage)
                    val slotModifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()

                    if (app != null) {
                        val globalIndex = pageIndex * APPS_PER_PAGE + indexInPage
                        AppCard(
                            app = app,
                            brush = cardBrushes[globalIndex % cardBrushes.size],
                            enabled = enabled,
                            onClick = { onLaunch(app) },
                            modifier = slotModifier
                        )
                    } else {
                        Spacer(slotModifier)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// App card
// ---------------------------------------------------------------------------

@Composable
private fun AppCard(
    app: AppRecord,
    brush: Brush,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by app.icon.collectAsState()
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )

    val shape = RoundedCornerShape(Dimens.CardRadius)

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(brush)
            .border(
                width = 0.7.dp,
                color = Color.White.copy(alpha = 0.055f),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null, // the label below already names the app
                modifier = Modifier.size(Dimens.IconSize),
                contentScale = ContentScale.Fit,
                colorFilter = grayscaleFilter
            )
        } else {
            Spacer(Modifier.size(Dimens.IconSize))
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = app.label,
            color = Palette.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = Palette.TextHint,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Page indicator
// ---------------------------------------------------------------------------

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pageCount <= MAX_DOT_PAGES) {
            repeat(pageCount) { index ->
                val selected = index == currentPage
                val width by animateDpAsState(
                    targetValue = if (selected) 16.dp else 5.dp,
                    label = "dotWidth"
                )
                val color by animateColorAsState(
                    targetValue = if (selected) Palette.DotActive else Palette.DotInactive,
                    label = "dotColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(5.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        } else {
            // Too many pages for dots to stay meaningful: show a compact counter.
            Text(
                text = "${currentPage + 1} / $pageCount",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}