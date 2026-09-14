package com.fliip.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fliip.app.nfc.NfcHub
import com.fliip.app.nfc.SavedTag
import com.fliip.app.nfc.SavedTagsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedTagsScreen(nav: NavController) {
    // Reader mode stays on so an armed replay can catch a tag.
    DisposableEffect(Unit) {
        NfcHub.setReaderActive(true)
        onDispose {
            NfcHub.cancelWrite()
            NfcHub.clearWriteResult()
            NfcHub.setReaderActive(false)
        }
    }
    val context = LocalContext.current
    val tags by SavedTagsStore.tags.collectAsState()
    val armed by NfcHub.writeArmed.collectAsState()
    val result by NfcHub.writeResult.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("NFC — Saved tags") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (armed != null) {
                SectionCard("Replaying") {
                    Text("Armed — hold a writable tag to the phone…", color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { NfcHub.cancelWrite() }) { Text("Cancel") }
                }
            }
            result?.let { r ->
                SectionCard("Last replay") {
                    Text(
                        r.message,
                        color = if (r.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (tags.isEmpty()) {
                Text(
                    "No saved tags yet. Read a tag, then tap Save.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tags, key = { it.id }) { t ->
                    SavedRow(
                        t = t,
                        onReplay = {
                            val raw = t.ndefRaw
                            if (raw != null && NfcHub.armWriteRaw(raw)) {
                                Toast.makeText(context, "Armed — hold a tag", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Nothing replayable on this tag", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { SavedTagsStore.delete(t.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedRow(t: SavedTag, onReplay: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.label, fontWeight = FontWeight.SemiBold)
            Text("UID ${t.uid}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text(t.type, style = MaterialTheme.typography.bodySmall)
            Text(t.recordSummary, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onReplay, enabled = t.hasNdef) { Text("Replay") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
