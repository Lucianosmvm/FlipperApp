package com.fliip.app.ir

/** Ready-to-transmit IR burst: carrier frequency (Hz) + alternating mark/space durations (µs), starting with a mark. */
class IrSignal(val frequency: Int, val pattern: IntArray) {
    val durationMicros: Long get() = pattern.sumOf { it.toLong() }
}

/** A stored IR command, either decoded (protocol + address + command) or a raw timing capture. */
sealed interface IrCode {
    data class Parsed(val protocol: String, val address: Int, val command: Int) : IrCode
    class Raw(val frequency: Int, val data: IntArray) : IrCode
}

/**
 * Encoders for common consumer IR protocols. Address/command semantics follow the
 * Flipper Zero `.ir` file format so built-in codes and imported files share one path.
 */
object IrProtocols {

    private val parsedProtocols = setOf(
        "NEC", "NECext", "NEC42", "NEC42ext", "Samsung32",
        "SIRC", "SIRC15", "SIRC20", "RC5", "RC5X", "RC6",
    )

    fun supports(protocol: String) = protocol in parsedProtocols

    /** [toggle] only matters for RC5/RC6, whose receivers expect it to flip on every new key press. */
    fun encode(code: IrCode, toggle: Boolean): IrSignal? = when (code) {
        is IrCode.Raw -> IrSignal(code.frequency, code.data)
        is IrCode.Parsed -> when (code.protocol) {
            "NEC", "NECext" -> nec(code.address, code.command)
            "NEC42" -> nec42(code.address, code.command)
            "NEC42ext" -> necFrame(lsb(code.address, 26) + lsb(code.command, 16))
            "Samsung32" -> samsung32(code.address, code.command)
            "SIRC" -> sirc(code.address, code.command, 5)
            "SIRC15" -> sirc(code.address, code.command, 8)
            "SIRC20" -> sirc(code.address, code.command, 13)
            "RC5", "RC5X" -> rc5(code.address, code.command, toggle)
            "RC6" -> rc6(code.address, code.command, toggle)
            else -> null
        }
    }

    // ---- NEC family (38 kHz, pulse distance, LSB first) -------------------

    /** 8-bit values get their inverted byte appended; wider values are sent as-is (extended NEC). */
    fun nec(address: Int, command: Int): IrSignal {
        val a = if (address <= 0xFF) lsb(address, 8) + lsb(address.inv(), 8) else lsb(address, 16)
        val c = if (command <= 0xFF) lsb(command, 8) + lsb(command.inv(), 8) else lsb(command, 16)
        return necFrame(a + c)
    }

    private fun nec42(address: Int, command: Int) =
        necFrame(lsb(address, 13) + lsb(address.inv(), 13) + lsb(command, 8) + lsb(command.inv(), 8))

    private fun necFrame(bits: List<Boolean>) =
        IrSignal(38_000, pulseDistance(bits, 9000, 4500, 560, 1690, 560))

    fun samsung32(address: Int, command: Int) = IrSignal(
        38_000,
        pulseDistance(lsb(address, 8) + lsb(address, 8) + lsb(command, 8) + lsb(command.inv(), 8), 4500, 4500, 560, 1690, 560),
    )

    // ---- Sony SIRC (40 kHz, pulse width, command then address, LSB first) --

    fun sirc(address: Int, command: Int, addressBits: Int): IrSignal {
        val frame = ArrayList<Int>()
        frame += 2400
        (lsb(command, 7) + lsb(address, addressBits)).forEach { frame += 600; frame += if (it) 1200 else 600 }
        val frameLen = frame.sum()
        val out = ArrayList<Int>()
        // Sony receivers want the frame at least 3 times, one every 45 ms.
        repeat(3) { i ->
            if (i > 0) out += 45_000 - frameLen
            out += frame
        }
        return IrSignal(40_000, out.toIntArray())
    }

    // ---- Philips RC5 / RC6 (36 kHz, Manchester) ---------------------------

    fun rc5(address: Int, command: Int, toggle: Boolean): IrSignal {
        // Second start bit doubles as inverted 7th command bit (RC5X).
        val bits = listOf(true, command < 64, toggle) + msb(address, 5) + msb(command, 6)
        val levels = bits.flatMap { if (it) listOf(false to 1, true to 1) else listOf(true to 1, false to 1) }
        return IrSignal(36_000, mergeLevels(levels, 889))
    }

    fun rc6(address: Int, command: Int, toggle: Boolean): IrSignal {
        val levels = ArrayList<Pair<Boolean, Int>>()
        levels += true to 6
        levels += false to 2
        fun bit(b: Boolean, width: Int = 1) {
            if (b) { levels += true to width; levels += false to width }
            else { levels += false to width; levels += true to width }
        }
        bit(true)                       // start bit
        repeat(3) { bit(false) }        // mode 0
        bit(toggle, width = 2)          // trailer bit, double width
        msb(address, 8).forEach { bit(it) }
        msb(command, 8).forEach { bit(it) }
        return IrSignal(36_000, mergeLevels(levels, 444))
    }

    // ---- Air conditioners (whole state in every frame) --------------------

    /** LG AC 28-bit frame. [lg2] selects the newer header timing used by most inverter models. */
    fun lgAc(value: Int, lg2: Boolean): IrSignal {
        val bits = msb(value, 28)
        val pattern = if (lg2) pulseDistance(bits, 3200, 9900, 500, 1600, 550)
        else pulseDistance(bits, 8500, 4250, 550, 1600, 550)
        return IrSignal(38_000, pattern)
    }

    /** Coolix (Midea / Springer / Carrier): 3 bytes, each followed by its inverse, whole frame sent twice. */
    fun coolix(value: Int): IrSignal {
        val bits = (2 downTo 0).flatMap { i ->
            val b = (value shr (i * 8)) and 0xFF
            msb(b, 8) + msb(b.inv(), 8)
        }
        val frame = pulseDistance(bits, 4692, 4416, 552, 1656, 552)
        return IrSignal(38_000, frame + intArrayOf(5244) + frame)
    }

    // ---- Helpers ----------------------------------------------------------

    private fun lsb(value: Int, n: Int) = List(n) { (value shr it) and 1 == 1 }
    private fun msb(value: Int, n: Int) = List(n) { (value shr (n - 1 - it)) and 1 == 1 }

    private fun pulseDistance(
        bits: List<Boolean>, hdrMark: Int, hdrSpace: Int, bitMark: Int, oneSpace: Int, zeroSpace: Int,
    ): IntArray {
        val out = IntArray(2 + bits.size * 2 + 1)
        out[0] = hdrMark
        out[1] = hdrSpace
        bits.forEachIndexed { i, b ->
            out[2 + i * 2] = bitMark
            out[3 + i * 2] = if (b) oneSpace else zeroSpace
        }
        out[out.size - 1] = bitMark
        return out
    }

    /** Collapses (level, units) runs into mark/space durations; drops leading/trailing silence. */
    private fun mergeLevels(levels: List<Pair<Boolean, Int>>, unit: Int): IntArray {
        val out = ArrayList<Int>()
        var current: Boolean? = null
        for ((level, units) in levels) {
            if (current == null && !level) continue
            if (level == current) out[out.size - 1] += units * unit
            else { out += units * unit; current = level }
        }
        if (current == false) out.removeAt(out.size - 1)
        return out.toIntArray()
    }
}
