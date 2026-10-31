
package com.me.mylauncher.sections

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

private const val MAX_CONTACTS = 3
private const val PREFS_NAME = "quick_contacts_preferences"

// --------------------------------------------------
// DESIGN SYSTEM
// --------------------------------------------------

private object ContactColors {
    val Surface = Color(0xFF191B20)
    val Border = Color(0xFF2B2E35)
    val Primary = Color(0xFFF3F3F3)
    val Secondary = Color(0xFF92959E)
    val Accent = Color(0xFFB9D8C4)
    val AccentDark = Color(0xFF263D30)
    val Avatar = Color(0xFF292D35)
    val Divider = Color(0xFF292C32)
}

// --------------------------------------------------
// MODEL
// --------------------------------------------------

private data class QuickContact(
    val name: String,
    val number: String
)

// --------------------------------------------------
// STORAGE
// --------------------------------------------------

private fun loadQuickContacts(context: Context): List<QuickContact> {

    val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    return (0 until MAX_CONTACTS).mapNotNull { index ->

        val name = prefs.getString("name_$index", null)
        val number = prefs.getString("number_$index", null)

        if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
            QuickContact(name, number)
        } else {
            null
        }
    }
}

private fun saveQuickContacts(
    context: Context,
    contacts: List<QuickContact>
) {
    val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

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

// --------------------------------------------------
// READ CONTACT
// --------------------------------------------------

private fun readSelectedContact(
    context: Context,
    uri: Uri
): QuickContact? {

    return try {

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->

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

// --------------------------------------------------
// MAIN COMPONENT
// --------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickContacts() {

    val context = LocalContext.current

    var contacts by remember {
        mutableStateOf(loadQuickContacts(context))
    }

    var contactToRemove by remember {
        mutableStateOf<QuickContact?>(null)
    }

    // Contact picker

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        val selected = readSelectedContact(context, uri)

        if (selected == null) {

            Toast.makeText(
                context,
                "Unable to read contact",
                Toast.LENGTH_SHORT
            ).show()

            return@rememberLauncherForActivityResult
        }

        when {

            contacts.any { it.number == selected.number } -> {
                Toast.makeText(
                    context,
                    "This contact is already added",
                    Toast.LENGTH_SHORT
                ).show()
            }

            contacts.size >= MAX_CONTACTS -> {
                Toast.makeText(
                    context,
                    "Your quick contacts list is full",
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                contacts = contacts + selected
                saveQuickContacts(context, contacts)
            }
        }
    }

    fun openContactPicker() {

        if (contacts.size >= MAX_CONTACTS) return

        val intent = Intent(
            Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )

        contactPicker.launch(intent)
    }

    // Remove confirmation

    contactToRemove?.let { contact ->

        AlertDialog(
            onDismissRequest = {
                contactToRemove = null
            },
            containerColor = LauncherColors.Surface,
            title = {
                Text(
                    "Remove contact?",
                    color = LauncherColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "Remove ${contact.name} from your quick contacts?",
                    color = LauncherColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        contacts = contacts.filterNot {
                            it.number == contact.number
                        }

                        saveQuickContacts(context, contacts)
                        contactToRemove = null
                    }
                ) {
                    Text(
                        "Remove",
                        color = Color(0xFFE58D8D)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        contactToRemove = null
                    }
                ) {
                    Text(
                        "Cancel",
                        color =LauncherColors.TextSecondary
                    )
                }
            }
        )
    }

    // --------------------------------------------------
    // CARD
    // --------------------------------------------------

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LauncherColors.Surface)
            .border(
                1.dp,
                LauncherColors.Border,
                RoundedCornerShape(18.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {

        // Header

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                Text(
                    text = "Quick contacts",
                    color = LauncherColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                )

                Text(
                    text = "Your important people, one tap away",
                    color =LauncherColors.TextSecondary,
                    fontSize = 11.sp
                )
            }

            // Add button

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LauncherColors.AccentContainer)
                    .clickable {
                        openContactPicker()
                    },
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "+",
                    color = LauncherColors.Accent,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Contact list

        if (contacts.isEmpty()) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LauncherColors.Background)
                    .clickable {
                        openContactPicker()
                    }
                    .padding(vertical = 24.dp, horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(LauncherColors.Avatar),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = LauncherColors.Accent,
                        fontSize = 25.sp
                    )
                }

                Text(
                    text = "Add your first contact",
                    color = LauncherColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Keep the people you call most within reach.",
                    color = LauncherColors.TextSecondary,
                    fontSize = 10.sp
                )
            }

        } else {

            Column {

                contacts.forEachIndexed { index, contact ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
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
                                    contactToRemove = contact
                                }
                            )
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        // Avatar

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(LauncherColors.Avatar),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = contact.name
                                    .firstOrNull()
                                    ?.uppercase() ?: "?",
                                color = LauncherColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Contact details

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {

                            Text(
                                text = contact.name,
                                color = LauncherColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = contact.number,
                                color = LauncherColors.TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Call action

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(LauncherColors.AccentContainer),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "↗",
                                color = LauncherColors.Accent,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (index != contacts.lastIndex) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(LauncherColors.Divider)
                        )
                    }
                }
            }

            // Footer

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "${contacts.size} of $MAX_CONTACTS contacts",
                    color = LauncherColors.TextSecondary,
                    fontSize = 10.sp
                )

                if (contacts.size < MAX_CONTACTS) {

                    Text(
                        text = "Add another +",
                        color = LauncherColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            openContactPicker()
                        }
                    )
                } else {

                    Text(
                        text = "List full",
                        color = LauncherColors.TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
