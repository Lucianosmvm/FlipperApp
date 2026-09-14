package com.fliip.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper over the platform BLE APIs. All permission-gated calls are
 * annotated @SuppressLint("MissingPermission"); the UI must hold
 * BLUETOOTH_SCAN + BLUETOOTH_CONNECT (API 31+) before invoking scan/connect.
 */
@SuppressLint("MissingPermission")
class BleController(context: Context) {

    private val appCtx = context.applicationContext
    private val manager = appCtx.getSystemService(BluetoothManager::class.java)
    private val adapter get() = manager?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices

    private val _connState = MutableStateFlow(ConnState.DISCONNECTED)
    val connState: StateFlow<ConnState> = _connState

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    val services: StateFlow<List<GattServiceInfo>> = _services

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val found = LinkedHashMap<String, ScannedDevice>()
    private var gatt: BluetoothGatt? = null
    // charUuid -> desired subscribed state, pending CCCD-write confirmation.
    private val pendingNotify = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    // ---- Scanning ----------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            val connectable = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || result.isConnectable
            found[dev.address] = ScannedDevice(
                address = dev.address,
                name = result.scanRecord?.deviceName ?: dev.name,
                rssi = result.rssi,
                connectable = connectable,
                lastSeenMs = System.currentTimeMillis(),
            )
            _devices.value = found.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            log("scan failed: $errorCode")
            _scanning.value = false
        }
    }

    fun startScan() {
        if (_scanning.value) return
        val s = scanner ?: run { log("no BLE scanner (bluetooth off?)"); return }
        found.clear()
        _devices.value = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        s.startScan(null, settings, scanCallback)
        _scanning.value = true
        log("scan started")
    }

    fun stopScan() {
        if (!_scanning.value) return
        runCatching { scanner?.stopScan(scanCallback) }
        _scanning.value = false
        log("scan stopped")
    }

    // ---- Connection --------------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connState.value = ConnState.DISCOVERING
                    log("connected (status $status), discovering services")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connState.value = ConnState.DISCONNECTED
                    _services.value = emptyList()
                    log("disconnected (status $status)")
                    g.close()
                    if (gatt === g) gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            _connState.value = ConnState.CONNECTED
            _services.value = g.services.map { svc ->
                GattServiceInfo(
                    uuid = svc.uuid.toString(),
                    name = GattNames.of(svc.uuid.toString()),
                    characteristics = svc.characteristics.map { ch ->
                        GattCharacteristicInfo(
                            uuid = ch.uuid.toString(),
                            name = GattNames.of(ch.uuid.toString()),
                            properties = propsOf(ch),
                        )
                    },
                )
            }
            log("discovered ${g.services.size} services")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            updateCharValue(ch.uuid.toString(), ch.value, status)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int,
        ) {
            updateCharValue(ch.uuid.toString(), value, status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            log("write ${short(ch.uuid.toString())} status=$status")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            pushNotification(ch.uuid.toString(), ch.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray,
        ) {
            pushNotification(ch.uuid.toString(), value)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: android.bluetooth.BluetoothGattDescriptor, status: Int) {
            if (d.uuid != CCCD_UUID) return
            val charUuid = d.characteristic.uuid.toString()
            val desired = pendingNotify.remove(charUuid)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (desired != null) markSubscribed(charUuid, desired)
                log("CCCD ok ${short(charUuid)} -> ${if (desired == true) "subscribed" else "unsubscribed"}")
            } else {
                // Roll back the local notification toggle; descriptor did not take.
                getCharacteristic(charUuid)?.let { g.setCharacteristicNotification(it, isSubscribed(charUuid)) }
                log("CCCD FAILED ${short(charUuid)} status=$status (state unchanged)")
            }
        }
    }

    fun connect(address: String) {
        stopScan()
        val dev = adapter?.getRemoteDevice(address) ?: run { log("bad address"); return }
        _connState.value = ConnState.CONNECTING
        _services.value = emptyList()
        log("connecting to $address")
        gatt = dev.connectGatt(appCtx, false, gattCallback, BluetoothDevice_TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    fun readCharacteristic(serviceUuid: String, charUuid: String) {
        val g = gatt ?: return
        val ch = g.getService(java.util.UUID.fromString(serviceUuid))
            ?.getCharacteristic(java.util.UUID.fromString(charUuid)) ?: return
        if (!g.readCharacteristic(ch)) log("read rejected ${short(charUuid)}")
    }

    fun writeCharacteristic(serviceUuid: String, charUuid: String, hexOrText: String, asHex: Boolean) {
        val g = gatt ?: return
        val ch = g.getService(java.util.UUID.fromString(serviceUuid))
            ?.getCharacteristic(java.util.UUID.fromString(charUuid)) ?: return
        val bytes = if (asHex) hexToBytes(hexOrText) else hexOrText.toByteArray(Charsets.UTF_8)
        val type = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, bytes, type)
        } else {
            @Suppress("DEPRECATION")
            run { ch.value = bytes; ch.writeType = type; g.writeCharacteristic(ch) }
        }
        log("write ${short(charUuid)} <- ${bytes.joinToString("") { "%02X".format(it) }}")
    }

    fun toggleNotify(serviceUuid: String, charUuid: String) {
        val g = gatt ?: return
        val ch = g.getService(java.util.UUID.fromString(serviceUuid))
            ?.getCharacteristic(java.util.UUID.fromString(charUuid)) ?: return
        val cccd = ch.getDescriptor(CCCD_UUID) ?: run {
            log("no CCCD on ${short(charUuid)} — cannot subscribe"); return
        }
        val enable = !isSubscribed(charUuid)

        if (!g.setCharacteristicNotification(ch, enable)) {
            log("setCharacteristicNotification failed ${short(charUuid)}"); return
        }
        // INDICATE wins if the characteristic only supports indicate.
        val value = when {
            !enable -> DISABLE_NOTIFICATION_VALUE
            ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> ENABLE_NOTIFICATION_VALUE
            else -> ENABLE_INDICATION_VALUE
        }
        // Record intent; applied to UI state only once onDescriptorWrite confirms.
        pendingNotify[charUuid] = enable
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run { cccd.value = value; g.writeDescriptor(cccd) }
        }
        if (!ok) {
            pendingNotify.remove(charUuid)
            g.setCharacteristicNotification(ch, isSubscribed(charUuid))
            log("CCCD write rejected ${short(charUuid)}")
            return
        }
        log("${if (enable) "subscribing" else "unsubscribing"} ${short(charUuid)}…")
    }

    private fun getCharacteristic(charUuid: String): BluetoothGattCharacteristic? {
        val g = gatt ?: return null
        val target = java.util.UUID.fromString(charUuid)
        return g.services.firstNotNullOfOrNull { it.getCharacteristic(target) }
    }

    fun clearLog() { _log.value = emptyList() }

    private fun isSubscribed(uuid: String): Boolean =
        _services.value.any { s -> s.characteristics.any { it.uuid == uuid && it.subscribed } }

    private fun markSubscribed(uuid: String, on: Boolean) {
        _services.value = _services.value.map { svc ->
            svc.copy(characteristics = svc.characteristics.map { c ->
                if (c.uuid == uuid) c.copy(subscribed = on) else c
            })
        }
    }

    private fun pushNotification(uuid: String, value: ByteArray) {
        val hex = value.joinToString(" ") { "%02X".format(it) }
        _services.value = _services.value.map { svc ->
            svc.copy(characteristics = svc.characteristics.map { c ->
                if (c.uuid == uuid) c.copy(value = hex) else c
            })
        }
        log("notify ${short(uuid)} -> $hex")
    }

    private fun updateCharValue(uuid: String, value: ByteArray, status: Int) {
        val hex = value.joinToString(" ") { "%02X".format(it) }
        val text = value.toString(Charsets.UTF_8).filter { it.isLetterOrDigit() || it.isWhitespace() || it in "._-:/" }
        val shown = "$hex" + if (text.isNotBlank()) "  ($text)" else ""
        _services.value = _services.value.map { svc ->
            svc.copy(characteristics = svc.characteristics.map { c ->
                if (c.uuid == uuid) c.copy(value = shown) else c
            })
        }
        log("read ${short(uuid)} status=$status -> $shown")
    }

    private fun propsOf(ch: BluetoothGattCharacteristic): List<String> {
        val p = ch.properties
        val out = mutableListOf<String>()
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) out += "READ"
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) out += "WRITE"
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) out += "WRITE_NR"
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) out += "NOTIFY"
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) out += "INDICATE"
        return out
    }

    private fun short(uuid: String) = if (uuid.length == 36) uuid.substring(4, 8) else uuid

    private fun log(msg: String) {
        val ts = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        _log.value = (_log.value + "[$ts] $msg").takeLast(200)
    }

    companion object {
        private const val BluetoothDevice_TRANSPORT_LE = 2 // BluetoothDevice.TRANSPORT_LE
        // Client Characteristic Configuration Descriptor (SIG 0x2902).
        private val CCCD_UUID = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val ENABLE_NOTIFICATION_VALUE = byteArrayOf(0x01, 0x00)
        private val ENABLE_INDICATION_VALUE = byteArrayOf(0x02, 0x00)
        private val DISABLE_NOTIFICATION_VALUE = byteArrayOf(0x00, 0x00)
        fun hexToBytes(s: String): ByteArray {
            val clean = s.filter { it.isLetterOrDigit() }
            if (clean.length % 2 != 0) return ByteArray(0)
            return ByteArray(clean.length / 2) {
                clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
