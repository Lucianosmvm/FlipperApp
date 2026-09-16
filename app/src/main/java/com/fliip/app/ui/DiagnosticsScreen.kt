package com.fliip.app.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.nfc.NfcAdapter
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

private enum class Status(val label: String, val color: Color) {
    YES("YES", Color(0xFF2E7D32)),
    OFF("OFF", Color(0xFFF9A825)),
    NO("NO", Color(0xFFC62828)),
    CHECKING("…", Color.Gray),
}

private data class Capability(val name: String, val status: Status, val detail: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(nav: NavController) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val hid = rememberHidDeviceStatus(context, refresh)
    val caps = remember(refresh, hid) { collectCapabilities(context, hid) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Phone diagnostics") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                SectionCard("Device") {
                    InfoRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
                    InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    OutlinedButton(onClick = { refresh++ }) { Text("Re-check") }
                }
            }
            item {
                SectionCard("Capabilities") {
                    caps.forEach { CapabilityRow(it) }
                }
            }
            item {
                Text(
                    "OFF = hardware present but switched off in system settings.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CapabilityRow(c: Capability) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.Medium)
            Text(c.detail, style = MaterialTheme.typography.bodySmall)
        }
        Text(c.status.label, color = c.status.color, fontWeight = FontWeight.Bold)
    }
}

/**
 * The Bluetooth HID Device profile (phone acting as keyboard/remote) can only be
 * detected by asking for its proxy; many OEM builds report it but never connect.
 */
@Composable
private fun rememberHidDeviceStatus(context: Context, refresh: Int): Status {
    var status by remember(refresh) { mutableStateOf(Status.CHECKING) }
    DisposableEffect(refresh) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        var proxy: BluetoothProfile? = null
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P || adapter == null -> status = Status.NO
            !adapter.isEnabled -> status = Status.OFF
            else -> {
                val requested = adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
                        proxy = p
                        status = Status.YES
                    }
                    override fun onServiceDisconnected(profile: Int) {}
                }, BluetoothProfile.HID_DEVICE)
                if (!requested) status = Status.NO
            }
        }
        val timeout = android.os.Handler(android.os.Looper.getMainLooper())
        timeout.postDelayed({ if (status == Status.CHECKING) status = Status.NO }, 3000)
        onDispose {
            timeout.removeCallbacksAndMessages(null)
            proxy?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        }
    }
    return status
}

@SuppressLint("MissingPermission")
private fun collectCapabilities(context: Context, hid: Status): List<Capability> {
    val pm = context.packageManager
    val out = mutableListOf<Capability>()

    // ---- Infrared --------------------------------------------------------
    val ir = context.getSystemService(ConsumerIrManager::class.java)
    val hasIr = ir?.hasIrEmitter() == true
    val irRange = if (hasIr) runCatching {
        ir!!.carrierFrequencies.joinToString { "${it.minFrequency / 1000}–${it.maxFrequency / 1000} kHz" }
    }.getOrNull() else null
    out += Capability(
        "Infrared emitter",
        if (hasIr) Status.YES else Status.NO,
        if (hasIr) "TV / AC / sound-bar remote control" + (irRange?.let { " · $it" } ?: "")
        else "No IR remote control without external hardware",
    )

    // ---- NFC -------------------------------------------------------------
    val nfc = NfcAdapter.getDefaultAdapter(context)
    out += Capability(
        "NFC",
        when { nfc == null -> Status.NO; nfc.isEnabled -> Status.YES; else -> Status.OFF },
        "Read / write tags, NFC shortcuts",
    )
    out += Capability(
        "NFC card emulation (HCE)",
        if (pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) Status.YES else Status.NO,
        "Phone acts as ISO-DEP card (random UID, cannot clone badges)",
    )
    out += Capability(
        "Mifare Classic reading",
        if (pm.hasSystemFeature("com.nxp.mifare")) Status.YES else Status.NO,
        "Older access cards / transport cards (needs NXP NFC chip)",
    )

    // ---- Bluetooth -------------------------------------------------------
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    val hasBle = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter != null
    val btOn = adapter?.isEnabled == true
    out += Capability(
        "Bluetooth LE",
        when { !hasBle -> Status.NO; btOn -> Status.YES; else -> Status.OFF },
        "Scan, GATT, device radar, tracker detection",
    )
    out += Capability(
        "BLE advertising",
        when {
            !hasBle -> Status.NO
            !btOn -> Status.OFF
            adapter!!.isMultipleAdvertisementSupported -> Status.YES
            else -> Status.NO
        },
        "Phone as beacon (iBeacon / Eddystone)",
    )
    if (hasBle && btOn) {
        out += Capability(
            "BLE extended advertising",
            if (adapter!!.isLeExtendedAdvertisingSupported) Status.YES else Status.NO,
            "Longer adverts (Bluetooth 5)",
        )
        out += Capability(
            "BLE long range (Coded PHY)",
            if (adapter!!.isLeCodedPhySupported) Status.YES else Status.NO,
            "Extended range with compatible devices",
        )
    }
    out += Capability(
        "Bluetooth keyboard / remote mode",
        hid,
        "Phone as keyboard / media remote for TV, PC, tablet",
    )

    // ---- Other radios ----------------------------------------------------
    out += Capability(
        "Wi-Fi",
        if (pm.hasSystemFeature(PackageManager.FEATURE_WIFI)) Status.YES else Status.NO,
        "Network scanner, Wi-Fi TV control",
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        out += Capability(
            "UWB",
            if (pm.hasSystemFeature(PackageManager.FEATURE_UWB)) Status.YES else Status.NO,
            "Precise distance / direction to UWB tags",
        )
    }
    return out
}
