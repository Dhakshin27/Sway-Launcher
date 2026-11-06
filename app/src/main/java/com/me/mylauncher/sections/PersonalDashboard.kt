package com.me.mylauncher.sections

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val CONTACT_PREFS = "quick_contacts_preferences"
private const val MAX_CONTACTS = 3

private data class DashboardContact(
    val name: String,
    val number: String
)

@Composable
fun PersonalDashboard() {
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
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Your essentials.",
                color = LauncherColors.TextPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Light,
                fontFamily = LauncherColors.Display
            )

            Text(
                text = "PEOPLE, UPDATES & SHORTCUTS",
                color = LauncherColors.TextSecondary,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp
            )
        }

        // Contacts
        DashboardContacts()

        // Priority notifications
        PriorityNotifications()

        // Quick tools
        QuickTools()
    }
}

// --------------------------------------------------
// CONTACTS
// --------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardContacts() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(
            CONTACT_PREFS,
            Context.MODE_PRIVATE
        )
    }

    var contacts by remember {
        mutableStateOf(loadDashboardContacts(preferences))
    }

    var selectedContact by remember {
        mutableStateOf<DashboardContact?>(null)
    }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: "Contact"
                    val number = cursor.getString(1) ?: return@use

                    val updated = contacts.toMutableList()

                    if (updated.none { it.number == number }) {
                        updated.add(DashboardContact(name, number))

                        if (updated.size <= MAX_CONTACTS) {
                            contacts = updated
                            saveDashboardContacts(preferences, contacts)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardSectionTitle("YOUR PEOPLE")

            Text(
                text = "UP TO 3",
                color = LauncherColors.TextTertiary,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            repeat(MAX_CONTACTS) { index ->

                val contact = contacts.getOrNull(index)

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (contact != null) {

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(LauncherColors.Elevated)
                                    .border(
                                        1.dp,
                                        LauncherColors.Border,
                                        CircleShape
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            val intent = Intent(
                                                Intent.ACTION_DIAL,
                                                Uri.fromParts(
                                                    "tel",
                                                    contact.number,
                                                    null
                                                )
                                            )
                                            context.startActivity(intent)
                                        },
                                        onLongClick = {
                                            selectedContact = contact
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name
                                        .firstOrNull()
                                        ?.uppercase() ?: "?",
                                    color = LauncherColors.Accent,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }

                            Text(
                                text = contact.name,
                                color = LauncherColors.TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                    } else {

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    val intent = Intent(
                                        Intent.ACTION_PICK,
                                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                    )
                                    contactPicker.launch(intent)
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .border(
                                        1.dp,
                                        LauncherColors.Border,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+",
                                    color = LauncherColors.Accent,
                                    fontSize = 27.sp
                                )
                            }

                            Text(
                                "Add",
                                color = LauncherColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    selectedContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { selectedContact = null },
            containerColor = LauncherColors.Surface,
            title = {
                Text(
                    contact.name,
                    color = LauncherColors.TextPrimary
                )
            },
            text = {
                Text(
                    "Remove this contact from your favourites?",
                    color = LauncherColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        contacts = contacts.filter {
                            it.number != contact.number
                        }
                        saveDashboardContacts(preferences, contacts)
                        selectedContact = null
                    }
                ) {
                    Text("Remove", color = LauncherColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedContact = null }) {
                    Text("Cancel", color = LauncherColors.TextSecondary)
                }
            }
        )
    }
}

private fun loadDashboardContacts(
    preferences: android.content.SharedPreferences
): List<DashboardContact> {
    return (0 until MAX_CONTACTS).mapNotNull { index ->
        val name = preferences.getString("name_$index", null)
        val number = preferences.getString("number_$index", null)

        if (name != null && number != null) {
            DashboardContact(name, number)
        } else null
    }
}

private fun saveDashboardContacts(
    preferences: android.content.SharedPreferences,
    contacts: List<DashboardContact>
) {
    preferences.edit().apply {
        repeat(MAX_CONTACTS) { index ->
            if (index < contacts.size) {
                putString("name_$index", contacts[index].name)
                putString("number_$index", contacts[index].number)
            } else {
                remove("name_$index")
                remove("number_$index")
            }
        }
    }.apply()
}

// --------------------------------------------------
// PRIORITY NOTIFICATIONS
// --------------------------------------------------

@Composable
private fun PriorityNotifications() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notifications by PriorityNotificationStore.notifications
        .collectAsState()

    val hasAccess = remember {
        mutableStateOf(
            isNotificationAccessEnabled(context)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess.value = isNotificationAccessEnabled(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardSectionTitle("IMPORTANT")

            Text(
                text = "${notifications.size} UPDATES",
                color = LauncherColors.TextTertiary,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }

        when {
            !hasAccess.value -> {
                DashboardInfoCard(
                    title = "Enable notification access",
                    subtitle = "Allow your launcher to display important notifications.",
                    action = "ENABLE"
                ) {
                    context.startActivity(
                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    )
                }
            }

            notifications.isEmpty() -> {
                DashboardInfoCard(
                    title = "All caught up",
                    subtitle = "Important notifications will appear here.",
                    action = null,
                    onClick = {}
                )
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    notifications.take(3).forEach { notification ->
                        DashboardNotificationItem(
                            notification = notification,
                            onClick = {
                                try {
                                    notification.contentIntent?.send()
                                } catch (e: PendingIntent.CanceledException) {
                                    e.printStackTrace()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------
// QUICK TOOLS
// --------------------------------------------------

@Composable
private fun QuickTools() {
    val context = LocalContext.current

    var torchEnabled by remember { mutableStateOf(false) }

    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleTorch(cameraManager, !torchEnabled) {
                torchEnabled = it
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        DashboardSectionTitle("QUICK TOOLS")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickToolItem(
                modifier = Modifier.weight(1f),
                symbol = "◷",
                title = "Alarm"
            ) {
                openDashboardClock(context)
            }

            QuickToolItem(
                modifier = Modifier.weight(1f),
                symbol = "▦",
                title = "Calendar"
            ) {
                openDashboardCalendar(context)
            }

            QuickToolItem(
                modifier = Modifier.weight(1f),
                symbol = "⌗",
                title = "Calculator"
            ) {
                openCalculator(context)
            }

            QuickToolItem(
                modifier = Modifier.weight(1f),
                symbol = if (torchEnabled) "●" else "☼",
                title = if (torchEnabled) "Torch on" else "Torch"
            ) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    toggleTorch(cameraManager, !torchEnabled) {
                        torchEnabled = it
                    }
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }
}

// --------------------------------------------------
// REUSABLE UI
// --------------------------------------------------

@Composable
private fun DashboardSectionTitle(title: String) {
    Text(
        text = title,
        color = LauncherColors.Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp
    )
}

@Composable
private fun DashboardInfoCard(
    title: String,
    subtitle: String,
    action: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LauncherColors.Surface)
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(LauncherColors.Elevated),
            contentAlignment = Alignment.Center
        ) {
            Text("!", color = LauncherColors.Accent)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                color = LauncherColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                color = LauncherColors.TextSecondary,
                fontSize = 11.sp
            )
        }

        if (action != null) {
            Text(
                action,
                color = LauncherColors.Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DashboardNotificationItem(
    notification: PriorityNotification,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(LauncherColors.Surface)
            .clickable(onClick = onClick)
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(LauncherColors.Elevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                notification.appName.firstOrNull()?.uppercase() ?: "•",
                color = LauncherColors.Accent,
                fontSize = 15.sp
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                notification.title,
                color = LauncherColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )

            Text(
                notification.message,
                color = LauncherColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 2
            )
        }

        Text(
            notification.time,
            color = LauncherColors.TextTertiary,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun QuickToolItem(
    modifier: Modifier = Modifier,
    symbol: String,
    title: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(LauncherColors.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            symbol,
            color = LauncherColors.Accent,
            fontSize = 23.sp
        )

        Text(
            title,
            color = LauncherColors.TextSecondary,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

// --------------------------------------------------
// TOOL ACTIONS
// --------------------------------------------------


private fun openDashboardClock(context: Context) {
    openDeviceClock(context)
}

private fun openDashboardCalendar(context: Context) {
    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
        Intent(Intent.ACTION_VIEW).setData(
            android.provider.CalendarContract.CONTENT_URI
        )
    )

    launchFirstAvailable(context, intents)
}

private fun openCalculator(context: Context) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory("android.intent.category.APP_CALCULATOR")

    launchFirstAvailable(context, listOf(intent))
}

private fun launchFirstAvailable(
    context: Context,
    intents: List<Intent>
) {
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Try the next available intent.
        }
    }
}

private fun toggleTorch(
    manager: CameraManager,
    enabled: Boolean,
    onResult: (Boolean) -> Unit
) {
    try {
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            characteristics.get(
                CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) == true &&
                    characteristics.get(
                        CameraCharacteristics.LENS_FACING
                    ) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return

        manager.setTorchMode(cameraId, enabled)
        onResult(enabled)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}



internal fun openDeviceClock(context: Context) {
    val packageName = "com.android.deskclock"

    // First, try opening the alarm screen directly.
    try {
        val alarmIntent = Intent(
            android.provider.AlarmClock.ACTION_SHOW_ALARMS
        ).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(alarmIntent)
        return

    } catch (_: Exception) {
        // Fall back to opening the Clock app.
    }

    // Open the default Clock app itself.
    try {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Last fallback: generic Android clock intent.
    try {
        context.startActivity(
            Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
