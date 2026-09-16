package com.fliip.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fliip.app.ble.BleController
import com.fliip.app.ble.DeviceType
import com.fliip.app.ble.ScannedDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScanScreen(nav: NavController, ble: BleController) {
    val context = LocalContext.current
    val scanning by ble.scanning.collectAsState()
    val allDevices by ble.devices.collectAsState()
    val connectable = remember(allDevices) { allDevices.filter { it.connectable } }
    var typeFilter by remember { mutableStateOf<DeviceType?>(null) }
    val devices = remember(connectable, typeFilter) {
        if (typeFilter == null) connectable else connectable.filter { it.type == typeFilter }
    }
    val typeCounts = remember(connectable) { connectable.groupingBy { it.type }.eachCount() }
    var granted by remember { mutableStateOf(hasBlePermissions(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
        if (granted) ble.startScan()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("BLE — Scan") },
            navigationIcon = {
                IconButton(onClick = { ble.stopScan(); nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!ble.isBluetoothOn()) {
                    Text("Bluetooth is off — enable it in system settings.", color = MaterialTheme.colorScheme.error)
                } else if (scanning) {
                    TextButton(onClick = { ble.stopScan() }) { Text("Stop") }
                    Text("Scanning…  (${devices.size})")
                } else {
                    TextButton(onClick = {
                        if (granted) ble.startScan() else launcher.launch(blePermissions())
                    }) { Text("Scan") }
                    Text("${devices.size} found")
                }
            }
            if (!granted) {
                Text(
                    "Bluetooth scan/connect permission required.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = { Text("All (${connectable.size})") },
                    )
                }
                // Only offer types actually seen (plus the active one, so it can be cleared).
                items(DeviceType.entries.filter { (typeCounts[it] ?: 0) > 0 || it == typeFilter }) { t ->
                    FilterChip(
                        selected = typeFilter == t,
                        onClick = { typeFilter = if (typeFilter == t) null else t },
                        label = { Text("${t.label} (${typeCounts[t] ?: 0})") },
                        leadingIcon = { Icon(t.icon(), null, Modifier.size(18.dp)) },
                    )
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(devices, key = { it.address }) { d ->
                    DeviceRow(d) { nav.navigate("ble_device/${d.address}") }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: ScannedDevice, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(d.type.icon(), d.type.label)
            Column(Modifier.weight(1f)) {
                Text(d.name ?: "(no name)", fontWeight = FontWeight.SemiBold)
                Text(d.address, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text(d.type.label, style = MaterialTheme.typography.labelSmall)
            }
            Text("${d.rssi} dBm", fontFamily = FontFamily.Monospace)
        }
    }
}

private fun DeviceType.icon(): ImageVector = when (this) {
    DeviceType.TV -> Icons.Default.Tv
    DeviceType.WATCH -> Icons.Default.Watch
    DeviceType.AUDIO -> Icons.Default.Headphones
    DeviceType.PHONE -> Icons.Default.PhoneAndroid
    DeviceType.COMPUTER -> Icons.Default.Computer
    DeviceType.INPUT -> Icons.Default.Keyboard
    DeviceType.HEALTH -> Icons.Default.MonitorHeart
    DeviceType.OTHER -> Icons.Default.DevicesOther
}
