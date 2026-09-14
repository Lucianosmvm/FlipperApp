package com.fliip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fliip.app.ble.BleController
import com.fliip.app.ble.ConnState
import com.fliip.app.ble.GattCharacteristicInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BleDeviceScreen(nav: NavController, ble: BleController, address: String) {
    DisposableEffect(address) {
        ble.connect(address)
        onDispose { ble.disconnect() }
    }
    val state by ble.connState.collectAsState()
    val services by ble.services.collectAsState()
    val log by ble.log.collectAsState()

    var writeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(address, fontFamily = FontFamily.Monospace) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                SectionCard("Connection") {
                    InfoRow("State", state.name)
                    if (state == ConnState.DISCONNECTED) {
                        TextButton(onClick = { ble.connect(address) }) { Text("Reconnect") }
                    }
                }
            }
            services.forEach { svc ->
                item {
                    SectionCard("${svc.name} — ${short(svc.uuid)}") {
                        if (svc.characteristics.isEmpty()) Text("No characteristics.")
                        svc.characteristics.forEach { ch ->
                            CharRow(
                                ch = ch,
                                onRead = { ble.readCharacteristic(svc.uuid, ch.uuid) },
                                onWrite = { writeTarget = svc.uuid to ch.uuid },
                                onToggleNotify = { ble.toggleNotify(svc.uuid, ch.uuid) },
                            )
                        }
                    }
                }
            }
            item {
                SectionCard("Log") {
                    LogView(log, Modifier.fillMaxWidth().height(200.dp))
                    OutlinedButton(onClick = { ble.clearLog() }) { Text("Clear log") }
                }
            }
        }
    }

    writeTarget?.let { (svcUuid, chUuid) ->
        WriteDialog(
            charUuid = chUuid,
            onDismiss = { writeTarget = null },
            onConfirm = { data, asHex ->
                ble.writeCharacteristic(svcUuid, chUuid, data, asHex)
                writeTarget = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharRow(
    ch: GattCharacteristicInfo,
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onToggleNotify: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "${ch.name} — ${short(ch.uuid)}",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ch.properties.forEach { AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) }) }
        }
        ch.value?.let {
            Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if ("READ" in ch.properties) TextButton(onClick = onRead) { Text("Read") }
            if (ch.properties.any { it.startsWith("WRITE") }) TextButton(onClick = onWrite) { Text("Write") }
            if (ch.properties.any { it == "NOTIFY" || it == "INDICATE" }) {
                TextButton(onClick = onToggleNotify) {
                    Text(if (ch.subscribed) "Unsubscribe" else "Subscribe")
                }
            }
        }
    }
}

@Composable
private fun WriteDialog(charUuid: String, onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var asHex by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write ${short(charUuid)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (asHex) "Hex bytes (e.g. 01 FF A0)" else "Text (UTF-8)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = asHex, onCheckedChange = { asHex = it })
                    Text("Interpret as hex")
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(text, asHex) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun short(uuid: String) = if (uuid.length == 36) uuid.substring(4, 8) else uuid
