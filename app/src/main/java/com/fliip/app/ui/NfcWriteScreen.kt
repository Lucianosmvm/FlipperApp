package com.fliip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fliip.app.nfc.NdefBuilder
import com.fliip.app.nfc.NfcHub

private enum class RecKind(val label: String) { TEXT("Text"), URI("URI"), MIME("MIME") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcWriteScreen(nav: NavController) {
    DisposableEffect(Unit) {
        NfcHub.setReaderActive(true)
        onDispose {
            NfcHub.cancelWrite()
            NfcHub.clearWriteResult()
            NfcHub.cancelLock()
            NfcHub.clearLockResult()
            NfcHub.setReaderActive(false)
        }
    }
    val armed by NfcHub.writeArmed.collectAsState()
    val result by NfcHub.writeResult.collectAsState()
    val lockArmed by NfcHub.lockArmed.collectAsState()
    val lockResult by NfcHub.lockResult.collectAsState()
    var showLockConfirm by remember { mutableStateOf(false) }

    var kind by remember { mutableStateOf(RecKind.TEXT) }
    var content by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("en") }
    var mime by remember { mutableStateOf("text/plain") }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("NFC — Write") },
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
            SectionCard("Record type") {
                RecKind.entries.forEach { k ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = kind == k, onClick = { kind = k }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = kind == k, onClick = { kind = k })
                        Text(k.label)
                    }
                }
            }
            SectionCard("Content") {
                when (kind) {
                    RecKind.TEXT -> {
                        OutlinedTextField(
                            value = lang, onValueChange = { lang = it.take(8) },
                            label = { Text("Language code") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = content, onValueChange = { content = it },
                            label = { Text("Text") }, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    RecKind.URI -> OutlinedTextField(
                        value = content, onValueChange = { content = it },
                        label = { Text("URI (e.g. https://…, tel:…, mailto:…)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    RecKind.MIME -> {
                        OutlinedTextField(
                            value = mime, onValueChange = { mime = it },
                            label = { Text("MIME type") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = content, onValueChange = { content = it },
                            label = { Text("Payload (UTF-8)") }, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            SectionCard("Write") {
                if (armed != null) {
                    Text("Armed — hold a tag to the phone…", color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { NfcHub.cancelWrite() }) { Text("Cancel") }
                } else {
                    Button(
                        onClick = {
                            val msg = when (kind) {
                                RecKind.TEXT -> NdefBuilder.text(content, lang.ifBlank { "en" })
                                RecKind.URI -> NdefBuilder.uri(content.trim())
                                RecKind.MIME -> NdefBuilder.mime(mime.trim(), content)
                            }
                            NfcHub.armWrite(msg)
                        },
                        enabled = content.isNotBlank(),
                    ) { Text("Arm write") }
                    Text(
                        "Writing an NDEF record overwrites the tag's current content. " +
                            "Blank NDEF-formatable tags are auto-formatted.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                result?.let { r ->
                    Text(
                        r.message,
                        color = if (r.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SectionCard("Lock read-only  (permanent)") {
                Text(
                    "Makes the next tapped tag permanently read-only. This CANNOT be undone — " +
                        "the tag can never be rewritten again. It does not erase content; it freezes it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (lockArmed) {
                    Text("Armed to LOCK — hold a tag to the phone…", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { NfcHub.cancelLock() }) { Text("Cancel lock") }
                } else {
                    OutlinedButton(
                        onClick = { showLockConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Lock a tag read-only…") }
                }
                lockResult?.let { r ->
                    Text(
                        r.message,
                        color = if (r.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            title = { Text("Lock tag permanently?") },
            text = {
                Text(
                    "The next tag you tap will be made read-only FOREVER. There is no unlock. " +
                        "Only do this on a tag you own and are sure about.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { showLockConfirm = false; NfcHub.armLock() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("I understand — arm lock") }
            },
            dismissButton = { TextButton(onClick = { showLockConfirm = false }) { Text("Cancel") } },
        )
    }
}
