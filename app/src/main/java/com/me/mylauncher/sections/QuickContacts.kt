package com.me.mylauncher.sections

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_CONTACTS = 3
private const val PREFS_NAME = "quick_contacts_preferences"

private data class QuickContact(
    val name: String,
    val number: String
)

private fun loadQuickContacts(context: Context): List<QuickContact> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return (0 until MAX_CONTACTS).mapNotNull { index ->
        val name = prefs.getString("name_$index", null)
        val number = prefs.getString("number_$index", null)
        if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
            QuickContact(name, number)
        } else null
    }
}

private fun saveQuickContacts(context: Context, contacts: List<QuickContact>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().apply {
        (0 until MAX_CONTACTS).forEach { index ->
            remove("name_$index")
            remove("number_$index")
        }
        contacts.take(MAX_CONTACTS).forEachIndexed { index, contact ->
            putString("name_$index", contact.name)
            putString("number_$index", contact.number)
        }
        apply()
    }
}

private fun readSelectedContact(context: Context, uri: Uri): QuickContact? {
    return try {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                val number = cursor.getString(1)
                if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                    QuickContact(name, number)
                } else null
            } else null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
private fun PlusIcon(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawLine(color, Offset(cx, 0f), Offset(cx, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
    }
}

@Composable
private fun PhoneArrow(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(0f, h), Offset(w, 0f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.25f, 0f), Offset(w, 0f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w, 0f), Offset(w, h * 0.75f), stroke, StrokeCap.Round)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickContacts() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var contacts by remember { mutableStateOf<List<QuickContact>>(emptyList()) }
    var contactToRemove by remember { mutableStateOf<QuickContact?>(null) }

    // Load off the main thread to avoid frame drops on initial composition
    LaunchedEffect(context) {
        contacts = withContext(Dispatchers.IO) { loadQuickContacts(context) }
    }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val selected = withContext(Dispatchers.IO) { readSelectedContact(context, uri) }
            if (selected == null) {
                Toast.makeText(context, "Unable to read contact", Toast.LENGTH_SHORT).show()
                return@launch
            }
            when {
                contacts.any { it.number == selected.number } -> {
                    Toast.makeText(context, "Already in your favourites", Toast.LENGTH_SHORT).show()
                }
                contacts.size >= MAX_CONTACTS -> {
                    Toast.makeText(context, "Your favourites are full", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val updated = contacts + selected
                    contacts = updated
                    withContext(Dispatchers.IO) { saveQuickContacts(context, updated) }
                }
            }
        }
    }

    fun openContactPicker() {
        if (contacts.size >= MAX_CONTACTS) return
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPicker.launch(intent)
    }

    contactToRemove?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToRemove = null },
            containerColor = LauncherColors.Elevated,
            shape = LauncherColors.ModuleShape,
            title = {
                Text(
                    "Remove favourite?",
                    color = LauncherColors.TextPrimary,
                    fontFamily = LauncherColors.Display,
                    fontWeight = FontWeight.Normal,
                    fontSize = 22.sp
                )
            },
            text = {
                Text(
                    "${contact.name} will be removed from your favourites.",
                    color = LauncherColors.TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = contacts.filterNot { it.number == contact.number }
                        contacts = updated
                        scope.launch(Dispatchers.IO) { saveQuickContacts(context, updated) }
                        contactToRemove = null
                    }
                ) {
                    Text("Remove", color = LauncherColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToRemove = null }) {
                    Text("Cancel", color = LauncherColors.TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LauncherColors.ModuleShape)
            .background(LauncherColors.Elevated.copy(alpha = 0.94f))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Favourites",
                    color = LauncherColors.TextPrimary,
                    fontFamily = LauncherColors.Display,
                    fontSize = 22.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${contacts.size} of $MAX_CONTACTS",
                    color = LauncherColors.TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            if (contacts.size < MAX_CONTACTS) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.dp, LauncherColors.Hairline, CircleShape)
                        .clickable { openContactPicker() },
                    contentAlignment = Alignment.Center
                ) {
                    PlusIcon(
                        modifier = Modifier.size(12.dp),
                        color = LauncherColors.Accent
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (contacts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LauncherColors.InteractiveTileShape)
                    .clickable { openContactPicker() }
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "No favourites yet",
                    color = LauncherColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Add the people you call most.",
                    color = LauncherColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            Column {
                contacts.forEachIndexed { index, contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.fromParts("tel", contact.number, null)
                                    )
                                    context.startActivity(intent)
                                },
                                onLongClick = { contactToRemove = contact }
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(LauncherColors.Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.firstOrNull()?.uppercase() ?: "?",
                                color = LauncherColors.Accent,
                                fontFamily = LauncherColors.Display,
                                fontSize = 18.sp
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = contact.name,
                                color = LauncherColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = contact.number,
                                color = LauncherColors.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        PhoneArrow(
                            modifier = Modifier.size(10.dp),
                            color = LauncherColors.TextTertiary
                        )
                    }

                    if (index != contacts.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(start = 54.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(LauncherColors.Divider)
                        )
                    }
                }
            }
        }
    }
}