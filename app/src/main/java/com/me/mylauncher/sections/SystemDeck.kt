package com.me.mylauncher.sections

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

private val TIMEOUT_PRESETS_MS = listOf(
    30_000,   // 30s
    60_000,   // 1m
    120_000,  // 2m
    300_000,  // 5m
    600_000   // 10m
)

@Composable
fun SystemDeckSection(
    selectedPaletteId: String,
    onPaletteSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val storageStats = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val gb = 1024L * 1024L * 1024L

            val total = (stat.totalBytes / gb).coerceAtLeast(1L)
            val free = stat.availableBytes / gb
            val used = (total - free).coerceAtLeast(0L)
            val percentage = ((used * 100L) / total).toInt().coerceIn(0, 100)

            Triple(used, total, percentage)
        } catch (_: Exception) {
            Triple(0L, 1L, 0)
        }
    }

    // Flashlight: back camera with a flash unit (null if the device has none)
    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    val torchCameraId = remember { findTorchCameraId(cameraManager) }
    var torchOn by remember { mutableStateOf(false) }

    var currentTimeoutMs by remember {
        mutableIntStateOf(readScreenTimeoutMs(context))
    }

    var lastVoiceNote by remember { mutableStateOf<String?>(null) }

    val voiceRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()

            if (!spokenText.isNullOrEmpty()) {
                lastVoiceNote = spokenText
                copyVoiceNoteToClipboard(context, spokenText)
            }
        }
    }

    // Refresh timeout when returning from system settings or another app.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentTimeoutMs = readScreenTimeoutMs(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep the flashlight tile in sync even if it is toggled from elsewhere.
    DisposableEffect(cameraManager, torchCameraId) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchCameraId) torchOn = enabled
            }
        }
        if (torchCameraId != null) {
            cameraManager.registerTorchCallback(callback, null)
        }
        onDispose {
            if (torchCameraId != null) {
                runCatching { cameraManager.unregisterTorchCallback(callback) }
            }
        }
    }

    val selectedPalette = LauncherPalettes.all
        .firstOrNull { it.id == selectedPaletteId }
        ?: LauncherPalettes.all.first()

    val compact = LocalConfiguration.current.screenHeightDp < 720

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
            .padding(
                horizontal = horizontalPadding,
                vertical = 15.dp
            ),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {

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

        // Media volume
        MediaVolumeControl(context, audioManager)

        // Quick controls
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Quick controls",
                color = LauncherColors.TextPrimary,
                fontSize = 17.sp,
                fontFamily = LauncherColors.Display
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Direct Screen Timeout cycler
                    SystemSettingTile(
                        title = "Timeout · ${formatTimeout(currentTimeoutMs)}",
                        symbol = "◷",
                        height = tileHeight,
                        modifier = Modifier.weight(1f)
                    ) {
                        cycleScreenTimeout(context, currentTimeoutMs)?.let { updated ->
                            currentTimeoutMs = updated
                        }
                    }

                    // 2. Flashlight toggle (no permission needed)
                    SystemSettingTile(
                        title = if (torchOn) "Flashlight · On" else "Flashlight · Off",
                        symbol = if (torchOn) "☀" else "☼",
                        height = tileHeight,
                        highlighted = torchOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        toggleTorch(context, cameraManager, torchCameraId, torchOn)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 3. Quick Voice Memo / Note (tap: speak & copy, long-press: re-copy)
                    SystemSettingTile(
                        title = lastVoiceNote ?: "Voice note",
                        symbol = "◉",
                        height = tileHeight,
                        highlighted = lastVoiceNote != null,
                        modifier = Modifier.weight(1f),
                        onLongClick = lastVoiceNote?.let { savedNote ->
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                copyVoiceNoteToClipboard(context, savedNote)
                            }
                        }
                    ) {
                        launchVoiceNote(context, voiceRecognizerLauncher::launch)
                    }

                    // 4. Battery usage & saver
                    SystemSettingTile(
                        title = "Battery",
                        symbol = "◒",
                        height = tileHeight,
                        modifier = Modifier.weight(1f)
                    ) {
                        openBatteryScreen(context)
                    }
                }
            }
        }

        // All Android settings
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
private fun MediaVolumeControl(
    context: Context,
    audioManager: AudioManager
) {
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            .coerceAtLeast(1)
    }

    var mediaVolume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        )
    }

    // Keep slider in sync when physical volume buttons are pressed.
    DisposableEffect(context, audioManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                mediaVolume = audioManager
                    .getStreamVolume(AudioManager.STREAM_MUSIC)
                    .toFloat()
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val percentage = ((mediaVolume / maxVolume) * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LauncherColors.ModuleShape)
            .background(LauncherColors.Surface)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "♫",
                    color = LauncherColors.Accent,
                    fontSize = 18.sp
                )

                Text(
                    "Media volume",
                    color = LauncherColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                "$percentage%",
                color = LauncherColors.Accent,
                fontSize = 11.sp
            )
        }

        Slider(
            value = mediaVolume,
            onValueChange = { newVolume ->
                mediaVolume = newVolume
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    newVolume.toInt(),
                    0
                )
            },
            valueRange = 0f..maxVolume.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = LauncherColors.Accent,
                activeTrackColor = LauncherColors.Accent,
                inactiveTrackColor = LauncherColors.Elevated
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemSettingTile(
    title: String,
    symbol: String,
    height: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(LauncherColors.InteractiveTileShape)
            .background(if (highlighted) LauncherColors.Accent else LauncherColors.Surface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = symbol,
            color = if (highlighted) LauncherColors.OnAccent else LauncherColors.Accent,
            fontSize = 18.sp
        )

        Text(
            text = title,
            color = if (highlighted) LauncherColors.OnAccent else LauncherColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

// ---------------------------------------------------------------------------
// System helpers
// ---------------------------------------------------------------------------

/** Sends the user to the "Modify system settings" screen if not yet granted. */
private fun ensureWriteSettings(context: Context, message: String): Boolean {
    if (Settings.System.canWrite(context)) return true

    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    val intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
    return false
}

private fun readScreenTimeoutMs(context: Context): Int {
    return try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT
        )
    } catch (_: Exception) {
        60_000
    }
}

private fun formatTimeout(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(1)
    return if (totalSeconds < 60) {
        "${totalSeconds}s"
    } else {
        "${totalSeconds / 60}m"
    }
}

private fun cycleScreenTimeout(context: Context, currentMs: Int): Int? {
    if (!ensureWriteSettings(
            context,
            "Allow 'Modify system settings' to toggle screen timeout"
        )
    ) return null

    val nextMs = TIMEOUT_PRESETS_MS.firstOrNull { it > currentMs }
        ?: TIMEOUT_PRESETS_MS.first()

    return try {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            nextMs
        )
        nextMs
    } catch (_: Exception) {
        null
    }
}

// --- Flashlight -------------------------------------------------------------

private fun findTorchCameraId(cameraManager: CameraManager): String? {
    return try {
        cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (_: Exception) {
        null
    }
}

private fun toggleTorch(
    context: Context,
    cameraManager: CameraManager,
    cameraId: String?,
    currentlyOn: Boolean
) {
    if (cameraId == null) {
        Toast.makeText(context, "No flashlight on this device", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        cameraManager.setTorchMode(cameraId, !currentlyOn)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Flashlight unavailable (camera in use?)",
            Toast.LENGTH_SHORT
        ).show()
    }
}

// --- Quick Voice Memo / Note ------------------------------------------------

private fun launchVoiceNote(
    context: Context,
    launchIntent: (Intent) -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a quick note…")
    }

    try {
        launchIntent(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Speech recognition unavailable on this device",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun copyVoiceNoteToClipboard(context: Context, note: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Voice Note", note))
    Toast.makeText(
        context,
        "Copied: \"$note\"",
        Toast.LENGTH_SHORT
    ).show()
}

// --- Navigation helpers -----------------------------------------------------

private fun openBatteryScreen(context: Context) {
    val candidates = listOf(
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
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
        } catch (_: Exception) {
        }
    }
}