package com.fliip.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.le.ScanResult

enum class DeviceType(val label: String) {
    TV("TV"),
    WATCH("Watch / Band"),
    AUDIO("Audio"),
    PHONE("Phone"),
    COMPUTER("Computer"),
    INPUT("Keyboard / Mouse"),
    HEALTH("Health / Fitness"),
    OTHER("Other"),
}

/**
 * Best-effort guess of what kind of device is advertising, from (in order):
 * the advertised GAP Appearance, the system's Bluetooth class, advertised
 * service UUIDs, and finally keywords in the device name. Many devices
 * expose none of these, so OTHER is common.
 */
@SuppressLint("MissingPermission")
object DeviceClassifier {

    fun classify(result: ScanResult, name: String?): DeviceType =
        fromAppearance(result.scanRecord?.bytes)
            ?: fromBluetoothClass(runCatching { result.device.bluetoothClass }.getOrNull())
            ?: fromServices(result.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() })
            ?: fromName(name)
            ?: DeviceType.OTHER

    // ---- GAP Appearance (AD type 0x19) -------------------------------------

    private fun fromAppearance(raw: ByteArray?): DeviceType? {
        val appearance = findAd(raw, 0x19)?.takeIf { it.size >= 2 }
            ?.let { (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8) } ?: return null
        return when (appearance shr 6) {
            0x01 -> DeviceType.PHONE
            0x02 -> DeviceType.COMPUTER
            0x03 -> DeviceType.WATCH
            0x05, 0x28 -> DeviceType.TV // Display, Display Equipment
            0x0F -> DeviceType.INPUT // HID
            0x21, 0x22, 0x25, 0x27 -> DeviceType.AUDIO // Audio sink/source, wearable audio, AV equipment
            0x0C, 0x0D, 0x0E, 0x10, 0x11, 0x12, 0x31 -> DeviceType.HEALTH
            else -> null
        }
    }

    /** Returns the payload of the first advertising-data field of [type], if present. */
    private fun findAd(raw: ByteArray?, type: Int): ByteArray? {
        raw ?: return null
        var i = 0
        while (i < raw.size) {
            val len = raw[i].toInt() and 0xFF
            if (len == 0 || i + len >= raw.size) return null
            if ((raw[i + 1].toInt() and 0xFF) == type) return raw.copyOfRange(i + 2, i + 1 + len)
            i += len + 1
        }
        return null
    }

    // ---- Bluetooth class (mostly dual-mode / previously seen devices) ------

    private fun fromBluetoothClass(cls: BluetoothClass?): DeviceType? {
        cls ?: return null
        return when (cls.deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR,
            BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX -> DeviceType.TV
            else -> when (cls.majorDeviceClass) {
                BluetoothClass.Device.Major.WEARABLE -> DeviceType.WATCH
                BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceType.AUDIO
                BluetoothClass.Device.Major.PHONE -> DeviceType.PHONE
                BluetoothClass.Device.Major.COMPUTER -> DeviceType.COMPUTER
                BluetoothClass.Device.Major.PERIPHERAL -> DeviceType.INPUT
                BluetoothClass.Device.Major.HEALTH -> DeviceType.HEALTH
                else -> null
            }
        }
    }

    // ---- Advertised services -----------------------------------------------

    private val healthServices = setOf("180d", "1809", "1810", "1808", "1822", "1816", "1814", "181d")
    private val audioServices = setOf("184e", "184f", "1850", "1853", "fe2c") // LE Audio, Google Fast Pair

    private fun fromServices(uuids: List<String>?): DeviceType? {
        val shorts = uuids?.map { if (it.length == 36) it.substring(4, 8) else it } ?: return null
        return when {
            shorts.any { it in healthServices } -> DeviceType.HEALTH
            shorts.any { it == "1812" } -> DeviceType.INPUT
            shorts.any { it in audioServices } -> DeviceType.AUDIO
            else -> null
        }
    }

    // ---- Name keywords (checked in order; first match wins) ----------------

    private val nameRules: List<Pair<DeviceType, Regex>> = listOf(
        DeviceType.TV to Regex("""\btv\b|\[tv]|bravia|webos|roku|chromecast|firetv|fire tv"""),
        DeviceType.WATCH to Regex("""watch|\bband\b|amazfit|fitbit|garmin|forerunner|fenix|huawei gt|\bgt[rs]\b|whoop|oura"""),
        DeviceType.AUDIO to Regex("""buds|airpods|beats|headphone|headset|earbud|speaker|soundbar|soundcore|\bjbl\b|bose|wh-|wf-|qcy|edifier|marshall"""),
        DeviceType.INPUT to Regex("""keyboard|mouse|mx master|gamepad|controller|dualsense|xbox wireless"""),
        DeviceType.COMPUTER to Regex("""macbook|imac|laptop|desktop-|thinkpad|\bpc\b"""),
        DeviceType.PHONE to Regex("""iphone|pixel|galaxy|redmi|\bmoto|phone"""),
    )

    private fun fromName(name: String?): DeviceType? {
        val n = name?.lowercase() ?: return null
        return nameRules.firstOrNull { (_, rx) -> rx.containsMatchIn(n) }?.first
    }
}
