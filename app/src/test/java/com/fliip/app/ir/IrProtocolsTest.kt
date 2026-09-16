package com.fliip.app.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Decodes generated patterns back into bits and compares them with widely published
 * codes (e.g. Samsung power "E0E040BF", LG power "20DF10EF").
 */
class IrProtocolsTest {

    /** Reads a pulse-distance pattern (header + mark/space pairs) as an MSB-first bit string. */
    private fun pulseDistanceBits(pattern: IntArray, bits: Int, threshold: Int, offset: Int = 2): Long {
        var v = 0L
        repeat(bits) { i -> v = (v shl 1) or if (pattern[offset + 1 + i * 2] > threshold) 1L else 0L }
        return v
    }

    @Test fun samsungPower() {
        val s = IrProtocols.samsung32(0x07, 0x02)
        assertEquals(38_000, s.frequency)
        assertEquals(4500, s.pattern[0])
        assertEquals(0xE0E040BFL, pulseDistanceBits(s.pattern, 32, 1000))
    }

    @Test fun samsungVolumeUp() {
        assertEquals(0xE0E0E01FL, pulseDistanceBits(IrProtocols.samsung32(0x07, 0x07).pattern, 32, 1000))
    }

    @Test fun lgTvPower() {
        val s = IrProtocols.nec(0x04, 0x08)
        assertEquals(9000, s.pattern[0])
        assertEquals(0x20DF10EFL, pulseDistanceBits(s.pattern, 32, 1000))
    }

    @Test fun necExtendedFromFlipperFile() {
        // Flipper NECext: 16-bit address, command with explicit inverse byte.
        val s = IrProtocols.encode(IrCode.Parsed("NECext", 0xBF00, 0xFD02), false)!!
        // LSB-first bytes 00 BF 02 FD -> MSB-first bit string 00 FD 40 BF
        assertEquals(0x00FD40BFL, pulseDistanceBits(s.pattern, 32, 1000))
    }

    @Test fun sonyPowerIsRepeatedThreeTimesIn45msFrames() {
        val s = IrProtocols.sirc(0x01, 21, 5)
        assertEquals(40_000, s.frequency)
        assertEquals(3 * 25 + 2, s.pattern.size) // 3 frames of (header + 12 bits), 2 gaps
        assertEquals(2 * 45_000L + s.pattern.take(25).sum(), s.durationMicros)
        // command 21 = 1010100 LSB first -> 1,0,1,0,1,0,0 ; address 1 -> 1,0,0,0,0
        val marks = (0 until 12).map { s.pattern[2 + it * 2] > 900 }
        assertEquals(listOf(true, false, true, false, true, false, false, true, false, false, false, false), marks)
    }

    @Test fun rc5StartsWithMarkAndHasCorrectLength() {
        val s = IrProtocols.rc5(0x00, 12, toggle = false)
        assertEquals(36_000, s.frequency)
        // 14 bits * 2 half-bits * 889 µs, minus the leading half-bit space that is dropped.
        val total = s.durationMicros
        assert(total in (12 * 889L)..(28 * 889L)) { "unexpected RC5 length $total" }
        s.pattern.forEach { assert(it % 889 == 0) }
    }

    @Test fun lgAcCool18FanHigh() {
        val v = AcBrand.Lg.encode(AcState(power = true, mode = AcMode.COOL, temp = 18, fan = AcFan.HIGH), variant = 0)
        assertEquals(3200, v.pattern[0])
        assertEquals(0x8800347L, pulseDistanceBits(v.pattern, 28, 1000))
    }

    @Test fun lgAcOff() {
        val v = AcBrand.Lg.encode(AcState(power = false), variant = 1)
        assertEquals(8500, v.pattern[0])
        assertEquals(0x88C0051L, pulseDistanceBits(v.pattern, 28, 1000))
    }

    @Test fun coolixCool24FanAuto() {
        val v = AcBrand.Coolix.encode(AcState(power = true, mode = AcMode.COOL, temp = 24, fan = AcFan.AUTO), 0)
        // Each byte is followed by its inverse: B2 4D BF 40 40 BF
        assertEquals(0xB24DBF4040BFL, pulseDistanceBits(v.pattern, 48, 1000))
        // Frame sent twice
        val frameLen = 2 + 48 * 2 + 1
        assertEquals(frameLen * 2 + 1, v.pattern.size)
    }

    @Test fun flipperParserReadsParsedAndRaw() {
        val file = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: Samsung32
            address: 07 00 00 00
            command: 02 00 00 00
            #
            name: Vol_up
            type: raw
            frequency: 38000
            duty_cycle: 0.330000
            data: 9000 4500 560 560 560
            #
            name: Weird
            type: parsed
            protocol: Kaseikyo
            address: 01 02 03 04
            command: 05 00 00 00
        """.trimIndent()
        val r = FlipperIrParser.parse(file)
        assertEquals(listOf("Power", "Vol_up"), r.buttons.map { it.name })
        assertEquals(IrCode.Parsed("Samsung32", 7, 2), r.buttons[0].code)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560, 560), (r.buttons[1].code as IrCode.Raw).data)
        assertEquals(1, r.skipped.size)
    }
}
