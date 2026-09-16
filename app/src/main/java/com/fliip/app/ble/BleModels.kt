package com.fliip.app.ble

data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val connectable: Boolean,
    val lastSeenMs: Long,
    val type: DeviceType = DeviceType.OTHER,
)

data class GattCharacteristicInfo(
    val uuid: String,
    val name: String,
    val properties: List<String>,
    val value: String? = null,   // raw bytes as hex
    val decoded: String? = null, // human-readable interpretation, if any
    val subscribed: Boolean = false,
)

data class GattServiceInfo(
    val uuid: String,
    val name: String,
    val characteristics: List<GattCharacteristicInfo>,
)

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, DISCOVERING }
