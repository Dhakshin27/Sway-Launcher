package com.me.mylauncher.sections

import android.content.BroadcastReceiver
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH")
private val MINUTE_FORMATTER = DateTimeFormatter.ofPattern("mm")
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, d MMM")

private val quotes = listOf(
    "Eliminate\nthe nonessential.",
    "Precision\nover ornament.",
    "Signal,\nwithout the noise.",
    "Form\nfollows intent."
)

@Composable
fun YourSpace(isActive: Boolean = true) {
    val context = LocalContext.current

    val preferences = remember(context) {
        context.getSharedPreferences("your_space_preferences", Context.MODE_PRIVATE)
    }

    var wallpaperUri by remember {
        mutableStateOf(preferences.getString("wallpaper_uri", null)?.let(Uri::parse))
    }

    val wallpaperBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = wallpaperUri) {
        value = withContext(Dispatchers.IO) {
            wallpaperUri?.let { uri -> decodeWallpaper(context, uri) }
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                wallpaperUri = uri
                preferences.edit().putString("wallpaper_uri", uri.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { 2 })

    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    var batteryLevel by remember { mutableIntStateOf(getBatteryPercentage(context)) }

    // Event-driven system broadcast receiver (zero polling overhead)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> currentTime = LocalDateTime.now()

                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        if (level >= 0 && scale > 0) batteryLevel = (level * 100) / scale
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // Refresh immediately in case we missed ticks while unregistered
        currentTime = LocalDateTime.now()
        onDispose { context.unregisterReceiver(receiver) }
    }

    val hourStr = remember(currentTime) { currentTime.format(HOUR_FORMATTER) }
    val minuteStr = remember(currentTime) { currentTime.format(MINUTE_FORMATTER) }
    val dateStr = remember(currentTime) { currentTime.format(DATE_FORMATTER) }
    val shortDateStr = remember(currentTime) { currentTime.format(SHORT_DATE_FORMATTER) }

    val greeting = remember(currentTime.hour) {
        when (currentTime.hour) {
            in 5..11 -> "Good morning."
            in 12..16 -> "Good afternoon."
            in 17..21 -> "Good evening."
            else -> "Good night."
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 12.dp,
            userScrollEnabled = isActive,
            key = { it }
        ) { page ->
            when (page) {
                0 -> ClockPage(
                    hour = hourStr,
                    minute = minuteStr,
                    date = dateStr,
                    greeting = greeting,
                    batteryLevel = batteryLevel,
                    wallpaperBitmap = wallpaperBitmap,
                    onSetWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                    onTimeClick = { openClockApp(context) },
                    onDateClick = { openCalendarApp(context) }
                )
                1 -> PersonalDashboard()
            }
        }

        // Page indicator
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(2) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (isSelected) 20.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) LauncherColors.DotActive
                            else LauncherColors.TextPrimary.copy(alpha = 0.25f)
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockPage(
    hour: String,
    minute: String,
    date: String,
    greeting: String,
    batteryLevel: Int,
    wallpaperBitmap: ImageBitmap?,
    onSetWallpaper: () -> Unit,
    onTimeClick: () -> Unit,
    onDateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(LauncherColors.OuterFrameShape)
            .background(LauncherColors.Background)
            .border(1.dp, LauncherColors.Border, LauncherColors.OuterFrameShape)
    ) {

        // TOP: wallpaper, inset inside the frame so it reads as a "photo card"
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                .background(LauncherColors.Surface)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onSetWallpaper
                )
        ) {
            if (wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap,
                    contentDescription = "Selected wallpaper",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // BOTTOM: three clear tiers - meta line, time, date
        ClockPanel(
            hour = hour,
            minute = minute,
            date = date,
            greeting = greeting,
            batteryLevel = batteryLevel,
            onTimeClick = onTimeClick,
            onDateClick = onDateClick
        )
    }
}

@Composable
private fun ClockPanel(
    hour: String,
    minute: String,
    date: String,
    greeting: String,
    batteryLevel: Int,
    onTimeClick: () -> Unit,
    onDateClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 40.dp) // bottom clears page indicator
    ) {

        // Tier 1: greeting (left) + battery (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = greeting,
                color = LauncherColors.TextSecondary,
                fontSize = 13.sp,
                letterSpacing = 0.3.sp
            )
            if (batteryLevel in 0..100) {
                BatteryGauge(batteryLevel)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Tier 2: time. One Text with spans, so both parts share the exact same baseline.
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = LauncherColors.TextPrimary)) { append(hour) }
                append("\u2009")
                withStyle(
                    SpanStyle(
                        color = LauncherColors.Accent,
                        fontStyle = FontStyle.Italic
                    )
                ) { append(minute) }
            },
            style = TextStyle(
                fontFamily = LauncherColors.Display,
                fontWeight = FontWeight.Light,
                fontSize = 88.sp,
                lineHeight = 92.sp,
                letterSpacing = (-3).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            ),
            maxLines = 1,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTimeClick
            )
        )

        Spacer(Modifier.height(14.dp))

        // Tier 3: hairline + date
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LauncherColors.Border)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = date,
            color = LauncherColors.TextSecondary,
            fontSize = 14.sp,
            letterSpacing = 0.3.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick
            )
        )
    }
}

@Composable
private fun BatteryGauge(batteryLevel: Int) {
    val low = batteryLevel <= 20
    val fillColor = if (low) LauncherColors.Danger else LauncherColors.Accent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(LauncherColors.TextPrimary.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(batteryLevel / 100f)
                    .clip(CircleShape)
                    .background(fillColor)
            )
        }
        Text(
            text = "$batteryLevel%",
            color = if (low) LauncherColors.Danger else LauncherColors.TextSecondary,
            fontSize = 13.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperPage(
    date: String,
    onSetWallpaper: () -> Unit
) {
    var quoteIndex by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(LauncherColors.OuterFrameShape)
            .background(LauncherColors.Surface)
            .border(1.dp, LauncherColors.Border, LauncherColors.OuterFrameShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { quoteIndex = (quoteIndex + 1) % quotes.size },
                onLongClick = onSetWallpaper
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            QuickContacts()

            Column(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 26.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(1.dp)
                            .background(LauncherColors.Accent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${quoteIndex + 1} / ${quotes.size}",
                        color = LauncherColors.TextSecondary,
                        fontSize = 12.sp,
                        letterSpacing = 0.6.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = quotes[quoteIndex],
                    color = LauncherColors.TextPrimary,
                    fontFamily = LauncherColors.Display,
                    fontWeight = FontWeight.Light,
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-0.8).sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = date,
                    color = LauncherColors.TextTertiary,
                    fontSize = 12.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

private fun decodeWallpaper(context: Context, uri: Uri): ImageBitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val maxDimension = 1600
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }?.asImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun getBatteryPercentage(context: Context): Int {
    return try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) (level * 100) / scale else -1
    } catch (e: Exception) {
        -1
    }
}

private fun openClockApp(context: Context) {
    openDeviceClock(context)
}

private fun openCalendarApp(context: Context) {
    val timeUri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .apply { ContentUris.appendId(this, System.currentTimeMillis()) }
        .build()

    val intents = listOf(
        Intent(Intent.ACTION_VIEW).setData(timeUri),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
        Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI)
    )
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
}