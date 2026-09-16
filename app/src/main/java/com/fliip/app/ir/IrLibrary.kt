package com.fliip.app.ir

data class IrButton(val name: String, val code: IrCode)

data class IrRemote(val id: String, val name: String, val buttons: List<IrButton>)

/**
 * Built-in TV remotes. Button names follow Flipper-IRDB conventions (Power, Vol_up, Ch_next…)
 * so the remote layout can place both built-in and imported buttons the same way.
 */
object IrLibrary {

    private fun remote(id: String, name: String, protocol: String, address: Int, vararg keys: Pair<String, Int>) =
        IrRemote("builtin:$id", name, keys.map { (n, cmd) -> IrButton(n, IrCode.Parsed(protocol, address, cmd)) })

    private fun digits(vararg cmds: Int) = cmds.mapIndexed { i, c -> "$i" to c }.toTypedArray()

    val samsungTv = remote(
        "samsung_tv", "Samsung TV", "Samsung32", 0x07,
        "Power" to 0x02, "Power_on" to 0x99, "Power_off" to 0x98,
        "Source" to 0x01, "Mute" to 0x0F, "Vol_up" to 0x07, "Vol_dn" to 0x0B,
        "Ch_next" to 0x12, "Ch_prev" to 0x10,
        "Up" to 0x60, "Down" to 0x61, "Left" to 0x65, "Right" to 0x62, "Ok" to 0x68,
        "Back" to 0x58, "Home" to 0x79, "Menu" to 0x1A, "Exit" to 0x2D, "Info" to 0x1F, "Guide" to 0x4F,
        "Play" to 0x47, "Pause" to 0x4A, "Stop" to 0x46, "Rewind" to 0x45, "Fast_fwd" to 0x48,
        *digits(0x11, 0x04, 0x05, 0x06, 0x08, 0x09, 0x0A, 0x0C, 0x0D, 0x0E),
    )

    val lgTv = remote(
        "lg_tv", "LG TV", "NEC", 0x04,
        "Power" to 0x08, "Source" to 0x0B, "Mute" to 0x09, "Vol_up" to 0x02, "Vol_dn" to 0x03,
        "Ch_next" to 0x00, "Ch_prev" to 0x01,
        "Up" to 0x40, "Down" to 0x41, "Left" to 0x07, "Right" to 0x06, "Ok" to 0x44,
        "Back" to 0x28, "Home" to 0x7C, "Menu" to 0x43, "Exit" to 0x5B,
        "Play" to 0xB0, "Pause" to 0xBA, "Stop" to 0xB1, "Rewind" to 0x8F, "Fast_fwd" to 0x8E,
        *digits(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19),
    )

    val sonyTv = remote(
        "sony_tv", "Sony TV", "SIRC", 0x01,
        "Power" to 21, "Power_on" to 46, "Power_off" to 47,
        "Source" to 37, "Mute" to 20, "Vol_up" to 18, "Vol_dn" to 19, "Ch_next" to 16, "Ch_prev" to 17,
        "Up" to 116, "Down" to 117, "Left" to 52, "Right" to 51, "Ok" to 101, "Home" to 96, "Info" to 58,
        *digits(9, 0, 1, 2, 3, 4, 5, 6, 7, 8),
    )

    val philipsRc5Tv = remote(
        "philips_rc5_tv", "Philips TV (older, RC5)", "RC5", 0x00,
        "Power" to 12, "Mute" to 13, "Vol_up" to 16, "Vol_dn" to 17, "Ch_next" to 32, "Ch_prev" to 33,
        "Up" to 80, "Down" to 81, "Left" to 85, "Right" to 86, "Ok" to 87,
        *digits(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
    )

    val philipsRc6Tv = remote(
        "philips_rc6_tv", "Philips TV (newer, RC6)", "RC6", 0x00,
        "Power" to 0x0C, "Source" to 0x38, "Mute" to 0x0D, "Vol_up" to 0x10, "Vol_dn" to 0x11,
        "Ch_next" to 0x4C, "Ch_prev" to 0x4D,
        "Up" to 0x58, "Down" to 0x59, "Left" to 0x5A, "Right" to 0x5B, "Ok" to 0x5C,
        "Back" to 0x0A, "Home" to 0x54,
        *digits(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
    )

    val tvs = listOf(samsungTv, lgTv, sonyTv, philipsRc5Tv, philipsRc6Tv)

    fun builtin(id: String) = tvs.firstOrNull { it.id == id }

    /** One power code per built-in brand, for the "find my TV" sweep. */
    val powerCodes: List<Pair<IrRemote, IrCode>> = tvs.map { r -> r to r.buttons.first { it.name == "Power" }.code }
}
