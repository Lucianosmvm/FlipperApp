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
        "1804" to "Tx Power",
        "1805" to "Current Time",
        "181a" to "Environmental Sensing",
        "2a04" to "Preferred Connection Parameters",
        "2a05" to "Service Changed",
        "2a07" to "Tx Power Level",
        "2a1c" to "Temperature Measurement",
        "2a23" to "System ID",
        "2a27" to "Hardware Revision",
        "2a28" to "Software Revision",
        "2a2b" to "Current Time",
        "2a38" to "Body Sensor Location",
        "2a50" to "PnP ID",
        "2a6d" to "Pressure",
        "2a6f" to "Humidity",
        "2aa6" to "Central Address Resolution",
    )

    fun of(uuid: String): String {
        val u = uuid.lowercase()
        // Short 16-bit form lives at chars 4..7 of the 128-bit base UUID.
        val short = if (u.length == 36) u.substring(4, 8) else u
        return map[short] ?: "Unknown"
    }
}
