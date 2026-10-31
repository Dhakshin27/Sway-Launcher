package com.me.mylauncher.sections

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// --------------------------------------------------
// FORMATTERS
// --------------------------------------------------

private val TIME_FORMATTER =
    DateTimeFormatter.ofPattern("HH:mm")

private val DATE_FORMATTER =
    DateTimeFormatter.ofPattern("EEEE  •  d MMM")

// --------------------------------------------------
// QUOTES
// --------------------------------------------------

private val quotes = listOf(
    "CHANGE\nYOUR\nPERSPECTIVE.",
    "LESS NOISE.\nMORE\nCLARITY.",
    "BE HERE\nRIGHT\nNOW.",
    "SIMPLICITY\nIS THE\nULTIMATE FOCUS."
)

// --------------------------------------------------
// YOUR SPACE
// --------------------------------------------------

@Composable
fun YourSpace(isActive: Boolean = true) {

    val context = LocalContext.current

    // Wallpaper preferences
    val preferences = remember(context) {
        context.getSharedPreferences(
            "your_space_preferences",
            Context.MODE_PRIVATE
        )
    }

    var wallpaperUri by remember {
        mutableStateOf(
            preferences.getString("wallpaper_uri", null)
                ?.let(Uri::parse)
        )
    }

    // Decode wallpaper off the main thread & cache across pager swipes
    val wallpaperBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = wallpaperUri
    ) {
        value = withContext(Dispatchers.IO) {
            wallpaperUri?.let { uri ->
                decodeWallpaper(context, uri)
            }
        }
    }

    // Image picker
    val wallpaperPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    wallpaperUri = uri

                    preferences.edit()
                        .putString("wallpaper_uri", uri.toString())
                        .apply()

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

    // Horizontal pager
    val pagerState = rememberPagerState(
        pageCount = { 2 }
    )

    // Clock
    var currentTime by remember {
        mutableStateOf(LocalDateTime.now())
    }

    // Battery
    var batteryLevel by remember {
        mutableIntStateOf(getBatteryPercentage(context))
    }

    // Update clock and battery at minute boundaries
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            batteryLevel = getBatteryPercentage(context)

            val remaining = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(remaining)
        }
    }

    val time = remember(currentTime) {
        currentTime.format(TIME_FORMATTER)
    }

    val date = remember(currentTime) {
        currentTime.format(DATE_FORMATTER).uppercase()
    }

    val greeting = remember(currentTime.hour) {
        when (currentTime.hour) {
            in 5..11 -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            in 17..21 -> "GOOD EVENING"
            else -> "QUIET HOURS"
        }
    }

    // Main container
    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 14.dp,
            userScrollEnabled = isActive,
            key = { it }
        ) { page ->

            when (page) {

                // CLOCK PAGE
                0 -> ClockPage(
                    time = time,
                    date = date,
                    greeting = greeting,
                    batteryLevel = batteryLevel,
                    wallpaperBitmap = wallpaperBitmap,
                    onSetWallpaper = {
                        wallpaperPicker.launch(arrayOf("image/*"))
                    },
                    onTimeClick = {
                        openClockApp(context)
                    },
                    onDateClick = {
                        openCalendarApp(context)
                    }
                )

                // WALLPAPER PAGE
                1 -> WallpaperPage(
                    date = date,
                    hour = currentTime.hour,
                    wallpaperBitmap = wallpaperBitmap,
                    onSetWallpaper = {
                        wallpaperPicker.launch(
                            arrayOf("image/*")
                        )
                    }
                )
            }
        }

        // Page indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            repeat(2) { index ->

                val isSelected =
                    pagerState.currentPage == index

                Box(
                    modifier = Modifier
                        .height(5.dp)
                        .width(
                            if (isSelected) 18.dp else 5.dp
                        )
                        .clip(CircleShape)
                        .background(
                            if (isSelected)
                                LauncherColors.Accent
                            else
                                LauncherColors.TextSecondary.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

// --------------------------------------------------
// CLOCK PAGE
// --------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockPage(
    time: String,
    date: String,
    greeting: String,
    batteryLevel: Int,
    wallpaperBitmap: ImageBitmap?,
    onSetWallpaper: () -> Unit,
    onTimeClick: () -> Unit,
    onDateClick: () -> Unit
) {

    val cardShape = remember {
        RoundedCornerShape(30.dp)
    }

    val backgroundBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF303B33),
                Color(0xFF1B231E),
                Color(0xFF101512)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(cardShape)
            .background(backgroundBrush)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = cardShape
            )
    ) {

        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = "Clock card wallpaper",
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onSetWallpaper
                    ),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        // Ambient glow
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-50).dp)
                .size(240.dp)
                .clip(CircleShape)
                .background(
                    Color.White.copy(alpha = 0.03f)
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(
                    Color.White.copy(alpha = 0.02f)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(26.dp)
        ) {

            // Greeting and battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = greeting,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.5.sp
                )

                if (batteryLevel in 0..100) {

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                LauncherColors.Elevated.copy(alpha = 0.65f)
                            )
                            .border(
                                width = 1.dp,
                                color = LauncherColors.Border,
                                shape = CircleShape
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            )
                    ) {

                        Text(
                            text = "$batteryLevel%",
                            color =
                                if (batteryLevel <= 20)
                                    Color(0xFFFF6B6B)
                                else
                                    Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            // Clock
            Text(
                text = time,
                color = LauncherColors.TextPrimary,
                fontSize = 92.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-4).sp,
                maxLines = 1,
                modifier = Modifier.clickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = null,
                    onClick = onTimeClick
                )
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            // Date
            Text(
                text = date,
                color = LauncherColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.5.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = null,
                    onClick = onDateClick
                )
            )

            Spacer(
                modifier = Modifier.weight(1.15f)
            )
        }
    }
}

// --------------------------------------------------
// WALLPAPER PAGE
// --------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperPage(
    date: String,
    hour: Int,
    wallpaperBitmap: ImageBitmap?,
    onSetWallpaper: () -> Unit
) {

    val cardShape = remember {
        RoundedCornerShape(30.dp)
    }

    var quoteIndex by remember {
        mutableIntStateOf(0)
    }

    val gradientColors = remember(hour) {

        if (hour in 6..18) {

            listOf(
                Color(0xFF344137),
                Color(0xFF1B231E),
                Color(0xFF101512)
            )

        } else {

            listOf(
                Color(0xFF29362D),
                Color(0xFF171F19),
                Color(0xFF101512)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(cardShape)
            .background(
                Brush.linearGradient(gradientColors)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = cardShape
            )
            .combinedClickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,

                // Normal tap changes quote
                onClick = {
                    quoteIndex =
                        (quoteIndex + 1) % quotes.size
                },

                // Long press opens image picker
                onLongClick = onSetWallpaper
            )
    ) {

        // Selected wallpaper
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = "Selected wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Dark overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = 0.42f)
                )
        )

        // Quote content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(26.dp),
            verticalArrangement = Arrangement.Center
        ) {
            QuickContacts()

            Spacer(Modifier.height(32.dp))
            Text(
                text = quotes[quoteIndex],
                color = LauncherColors.TextPrimary,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = date,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.5.sp
            )
        }
    }
}

// --------------------------------------------------
// WALLPAPER IMAGE DECODER
// --------------------------------------------------

private fun decodeWallpaper(
    context: Context,
    uri: Uri
): ImageBitmap? {

    return try {

        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        if (
            bounds.outWidth <= 0 ||
            bounds.outHeight <= 0
        ) {
            return null
        }

        var sampleSize = 1
        val maxDimension = 1600

        while (
            bounds.outWidth / sampleSize > maxDimension ||
            bounds.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        bitmap?.asImageBitmap()

    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// --------------------------------------------------
// BATTERY
// --------------------------------------------------

private fun getBatteryPercentage(
    context: Context
): Int {

    return try {

        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = intent?.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            -1
        ) ?: -1

        val scale = intent?.getIntExtra(
            BatteryManager.EXTRA_SCALE,
            -1
        ) ?: -1

        if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            -1
        }

    } catch (e: Exception) {
        -1
    }
}

// --------------------------------------------------
// OPEN CLOCK APP
// --------------------------------------------------

private fun openClockApp(context: Context) {

    val intents = listOf(
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
        Intent("android.intent.action.SHOW_TIMERS"),
        Intent(AlarmClock.ACTION_SET_TIMER),
        Intent(Intent.ACTION_MAIN)
            .addCategory("android.intent.category.APP_CLOCK")
    )

    for (intent in intents) {
        try {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Try next available Clock action.
        }
    }
}

// --------------------------------------------------
// OPEN CALENDAR APP
// --------------------------------------------------

private fun openCalendarApp(context: Context) {

    val timeUri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .apply { ContentUris.appendId(this, System.currentTimeMillis()) }
        .build()

    val intents = listOf(
        Intent(Intent.ACTION_VIEW).setData(timeUri),
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR),
        Intent(Intent.ACTION_VIEW)
            .setData(CalendarContract.CONTENT_URI)
    )

    for (intent in intents) {
        try {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Try next available Calendar action.
        }
    }
}