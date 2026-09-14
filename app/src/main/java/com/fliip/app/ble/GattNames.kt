package com.fliip.app.ble

/** Human names for a handful of common SIG-assigned GATT UUIDs. */
object GattNames {
    private val map = mapOf(
        "1800" to "Generic Access",
        "1801" to "Generic Attribute",
        "180a" to "Device Information",
        "180f" to "Battery Service",
        "1809" to "Health Thermometer",
        "180d" to "Heart Rate",
        "1812" to "HID",
        "2a00" to "Device Name",
        "2a01" to "Appearance",
        "2a19" to "Battery Level",
        "2a29" to "Manufacturer Name",
        "2a24" to "Model Number",
        "2a25" to "Serial Number",
        "2a26" to "Firmware Revision",
        "2a37" to "Heart Rate Measurement",
        "2a6e" to "Temperature",
    )

    fun of(uuid: String): String {
        val u = uuid.lowercase()
        // Short 16-bit form lives at chars 4..7 of the 128-bit base UUID.
        val short = if (u.length == 36) u.substring(4, 8) else u
        return map[short] ?: "Unknown"
    }
}
