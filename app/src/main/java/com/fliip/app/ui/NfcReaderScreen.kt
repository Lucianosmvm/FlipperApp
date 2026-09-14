package com.fliip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.widget.Toast
import com.fliip.app.nfc.NfcHub
import com.fliip.app.nfc.SavedTagsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcReaderScreen(nav: NavController) {
    // Turn reader mode on while this screen is shown.
    DisposableEffect(Unit) {
        NfcHub.setReaderActive(true)
        onDispose { NfcHub.setReaderActive(false) }
    }
    val tag by NfcHub.lastTag.collectAsState()
    val context = LocalContext.current
    var showSave by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("NFC — Read") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (tag == null) {
                Text(
                    "Hold an NFC tag to the back of the phone…",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                val t = tag!!
                SectionCard("Identity") {
                    InfoRow("UID", t.uid)
                    InfoRow("Type", t.type)
                    t.atqa?.let { InfoRow("ATQA", it) }
                    t.sak?.let { InfoRow("SAK", it) }
                }
                SectionCard("Technologies") {
                    Text(t.techList.joinToString(", "), fontFamily = FontFamily.Monospace)
                }
                if (t.maxSize != null || t.writable != null) {
                    SectionCard("NDEF") {
                        t.maxSize?.let { InfoRow("Capacity", "$it bytes") }
                        t.writable?.let { InfoRow("Writable", if (it) "yes" else "no") }
                        if (t.ndefRecords.isEmpty()) Text("No records.")
                        t.ndefRecords.forEachIndexed { i, r ->
                            InfoRow("#$i TNF", r.tnf)
                            InfoRow("   Type", r.type)
                            Text(r.payload, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                androidx.compose.foundation.layout.Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = { showSave = true }) { Text("Save") }
                    TextButton(onClick = { NfcHub.clear() }) { Text("Clear") }
                }
            }
        }
    }

    if (showSave && tag != null) {
        val t = tag!!
        var label by remember(t) { mutableStateOf(t.uid) }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save tag") },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label") },
                        singleLine = true,
                    )
                    Text(
                        if (t.hasNdef) "Includes NDEF — can be replayed to a tag."
                        else "No NDEF — saved as a reference only (UID can't be replayed).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    SavedTagsStore.saveFrom(t, label)
                    showSave = false
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } },
        )
    }
}
