package com.fliip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fliip.app.nfc.FliipHceService
import com.fliip.app.nfc.HceLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HceScreen(nav: NavController) {
    val stored by FliipHceService.message.collectAsState()
    val log by HceLog.lines.collectAsState()
    var draft by remember { mutableStateOf(stored) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("NFC — Emulate (HCE)") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SectionCard("How it works") {
                Text(
                    "The phone registers a custom AID (F0 4C 4C 49 50). A reader that " +
                        "selects that AID gets the message below plus status 0x9000. " +
                        "Demo card only — not a clone of any real credential.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SectionCard("Card message") {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Payload returned to reader") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = { FliipHceService.message.value = draft },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Apply") }
                Text("Active: $stored", style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("APDU log") {
                LogView(log, Modifier.fillMaxWidth().height(220.dp))
                OutlinedButton(onClick = { HceLog.clear() }) { Text("Clear log") }
            }
        }
    }
}
