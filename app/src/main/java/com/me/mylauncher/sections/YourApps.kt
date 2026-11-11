package com.me.mylauncher.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.me.mylauncher.components.CardContainer
import com.me.mylauncher.data.AppRecord
import com.me.mylauncher.data.IconResolver
import com.me.mylauncher.data.LauncherApp
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Constants & design tokens
// ---------------------------------------------------------------------------

private const val COLUMNS = 2
private const val ROWS = 3
private const val APPS_PER_PAGE = COLUMNS * ROWS
private const val SEARCH_DEBOUNCE_MS = 150L
private const val MAX_DOT_PAGES = 8
private const val PRESSED_SCALE = 0.96f


private object Dimens {
    val ScreenPaddingH = 16.dp
    val ScreenPaddingV = 18.dp
    val SearchHeight = 50.dp
    val SearchRadius = 16.dp
    val CardRadius = 20.dp
    val GridSpacing = 12.dp
    val PageSpacing = 14.dp
    val IconSize = 58.dp
}

// Hoisted so they're allocated once, not on every recomposition.
private val CardShape = RoundedCornerShape(Dimens.CardRadius)
private val SearchShape = RoundedCornerShape(Dimens.SearchRadius)
private val PressSpec = spring<Float>(stiffness = Spring.StiffnessMedium)
private val ReleaseSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

// ---------------------------------------------------------------------------
// Filtering / sorting (pure function, runs off the main thread)
// ---------------------------------------------------------------------------

/** Lower-cased strings are computed once per app, not once per comparison. */
private class Keyed(val app: AppRecord) {
    val label: String = app.label.lowercase()
    val pkg: String = app.packageName.lowercase()
}

/**
 * Empty query  -> most used first, then alphabetical.
 * With a query -> label prefix matches, then label contains, then package
 *                 matches; most used first inside each group.
 */
private fun filterAndSort(apps: List<AppRecord>, query: String): List<AppRecord> {
    val keyed = apps.map(::Keyed)

    if (query.isEmpty()) {
        return keyed
            .sortedWith(
                compareByDescending<Keyed> { it.app.launchCount }
                    .thenBy { it.label }
            )
            .map { it.app }
    }

    val q = query.lowercase()

    fun rank(k: Keyed): Int = when {
        k.label.startsWith(q) -> 0
        k.label.contains(q) -> 1
        k.pkg.contains(q) -> 2
        else -> -1
    }

    return keyed
        .mapNotNull { k -> rank(k).takeIf { it >= 0 }?.let { k to it } }
        .sortedWith(
            compareBy<Pair<Keyed, Int>> { it.second }
                .thenByDescending { it.first.app.launchCount }
                .thenBy { it.first.label }
        )
        .map { it.first.app }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun YourApps(isActive: Boolean = true) {
    val density = LocalDensity.current
    // Grabbing the insets object does not read the animating value; we read it
    // later inside graphicsLayer so keyboard animation never recomposes this screen.
    val imeInsets = WindowInsets.ime

    val context = LocalContext.current
    val repository = remember(context) {
        (context.applicationContext as LauncherApp).appRepository
    }
    val apps by repository.apps.collectAsState()

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Held as a State object and only read inside SearchBar / snapshotFlow, so
    // typing doesn't recompose the pager or the grid.
    val searchQuery = remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    // Debounce typing, but apply clearing instantly.
    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery.value }
            .distinctUntilChanged()
            .collectLatest { q ->
                if (q.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
                debouncedQuery = q.trim()
            }
    }

    val initialApps = remember { filterAndSort(apps, "") }
    val visibleApps by produceState(initialApps, apps, debouncedQuery) {
        value = withContext(Dispatchers.Default) { filterAndSort(apps, debouncedQuery) }
    }

    val appPages = remember(visibleApps) { visibleApps.chunked(APPS_PER_PAGE) }
    val pagerState = rememberPagerState(pageCount = { appPages.size })

    // Jump back to the first page only when the *query* changes.
    LaunchedEffect(debouncedQuery) {
        if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    // Hide keyboard on page swipes (read via snapshotFlow: no recomposition per page).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { keyboard?.hide() }
    }
    LaunchedEffect(isActive) {
        if (!isActive) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    val launchApp: (AppRecord) -> Unit = remember(repository) {
        { app -> repository.launch(app) }
    }

    CardContainer(
        backgroundColor = LauncherColors.Background,
        contentPadding = PaddingValues(
            horizontal = Dimens.ScreenPaddingH,
            vertical = Dimens.ScreenPaddingV
        )
    ) {

        // App grid / empty state takes all available space
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
                    enabled = isActive,
                    onLaunch = launchApp,
                    // Subtle depth while swiping. Read in the draw phase only.
                    modifier = Modifier.graphicsLayer {
                        val offset = ((pagerState.currentPage - page) +
                                pagerState.currentPageOffsetFraction)
                            .absoluteValue
                            .coerceIn(0f, 1f)
                        alpha = 1f - 0.45f * offset
                        val s = 1f - 0.05f * offset
                        scaleX = s
                        scaleY = s
                    }
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

        // Page indicator (animates in/out as search results change)
        AnimatedVisibility(
            visible = appPages.size > 1,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                PageIndicator(
                    pageCount = appPages.size,
                    pagerState = pagerState
                )
            }
        }

        // Bottom search bar
        Spacer(Modifier.height(14.dp))

        SearchBar(
            query = searchQuery,
            onSubmit = {
                visibleApps.firstOrNull()?.let(launchApp)
                keyboard?.hide()
                focusManager.clearFocus()
            },
            modifier = Modifier.graphicsLayer {
                translationY = -imeInsets.getBottom(density).toFloat()
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Search bar
// ---------------------------------------------------------------------------

@Composable
private fun SearchBar(
    query: MutableState<String>,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val focusProgress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(180),
        label = "searchFocus"
    )

    val text = query.value

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.SearchHeight)
            .clip(SearchShape)
            .background(LauncherColors.Surface)
            // Focus ring is drawn in the draw phase, so the animation costs no recomposition.
            .drawBehind {
                val stroke = 1.dp.toPx()
                if (focusProgress > 0.01f) {
                    drawRoundRect(
                        color = LauncherColors.Accent.copy(alpha = 0.55f * focusProgress),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(Dimens.SearchRadius.toPx()),
                        style = Stroke(stroke)
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusRequester.requestFocus() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchGlyph(
            modifier = Modifier.size(18.dp),
            color = LauncherColors.TextSecondary
        )

        Spacer(Modifier.width(12.dp))

        BasicTextField(
            value = text,
            onValueChange = { query.value = it },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
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
                    if (text.isEmpty()) {
                        Text(
                            text = "Search apps",
                            color = LauncherColors.TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        AnimatedVisibility(
            visible = text.isNotEmpty(),
            enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.7f),
            exit = fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.7f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(LauncherColors.Elevated)
                    .semantics { contentDescription = "Clear search" }
                    .clickable(role = Role.Button) { query.value = "" },
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
                    val app = pageApps.getOrNull(row * COLUMNS + col)
                    val slotModifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()

                    if (app != null) {
                        AppCard(
                            app = app,
                            enabled = enabled,
                            onLaunch = onLaunch,
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
    enabled: Boolean,
    onLaunch: (AppRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by app.icon.collectAsState()
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    val interactionSource = remember { MutableInteractionSource() }

    // Press feedback driven by an Animatable and read in graphicsLayer:
    // pressing a card no longer recomposes it, it only re-runs the draw phase.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(PRESSED_SCALE, PressSpec)
                is PressInteraction.Release,
                is PressInteraction.Cancel -> scale.animateTo(1f, ReleaseSpec)
            }
        }
    }

    // Icons that arrive late fade in; icons already loaded appear instantly.
    val iconAlpha = remember { Animatable(if (imageBitmap != null) 1f else 0f) }
    LaunchedEffect(imageBitmap != null) {
        if (imageBitmap != null && iconAlpha.value < 1f) {
            iconAlpha.animateTo(1f, tween(200))
        }
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            // background(shape) draws the rounded fill directly, no extra clip layer needed.
            .background(LauncherColors.Elevated, CardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { onLaunch(app) }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(Dimens.IconSize)
                    .graphicsLayer {
                        alpha = iconAlpha.value
                    },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(
                    color = LauncherColors.Accent,
                    blendMode = BlendMode.SrcIn
                )
            )
        } else {
            Spacer(Modifier.size(Dimens.IconSize))
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = app.label,
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
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
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(220)) }

    Box(
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = LauncherColors.TextSecondary,
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

/**
 * Takes the PagerState and reads currentPage here, so a page change only
 * recomposes the indicator, not the whole screen.
 */
@Composable
private fun PageIndicator(
    pageCount: Int,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val currentPage = pagerState.currentPage

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pageCount <= MAX_DOT_PAGES) {
            repeat(pageCount) { index ->
                Dot(selected = index == currentPage)
            }
        } else {
            // Too many pages for dots to stay meaningful: show a compact counter.
            Text(
                text = "${currentPage + 1} / $pageCount",
                color = LauncherColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun Dot(selected: Boolean) {
    val width by animateDpAsState(
        targetValue = if (selected) 16.dp else 5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dotWidth"
    )
    val color by animateColorAsState(
        targetValue = if (selected) LauncherColors.DotActive else LauncherColors.DotInactive,
        animationSpec = tween(180),
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