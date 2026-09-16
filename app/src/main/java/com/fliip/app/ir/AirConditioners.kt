package com.fliip.app.ir

enum class AcMode(val label: String) { COOL("Cool"), HEAT("Heat"), DRY("Dry"), FAN("Fan"), AUTO("Auto") }

enum class AcFan(val label: String) { AUTO("Auto"), LOW("Low"), MEDIUM("Medium"), HIGH("High") }

data class AcState(
    val power: Boolean = true,
    val mode: AcMode = AcMode.COOL,
    val temp: Int = 24,
    val fan: AcFan = AcFan.AUTO,
)

/** AC remotes send the full desired state in every frame, so each brand is a state encoder. */
sealed class AcBrand(val id: String, val name: String, val minTemp: Int, val maxTemp: Int) {
    abstract val variants: List<String>
    abstract fun encode(state: AcState, variant: Int): IrSignal

    /** LG: 28-bit frame = 0x88 signature, power, mode, temp-15, fan, nibble checksum. */
    object Lg : AcBrand("lg", "LG", 16, 30) {
        override val variants = listOf("Protocol 1 (newer / Dual Inverter)", "Protocol 2 (older models)")

        override fun encode(state: AcState, variant: Int): IrSignal {
            val value = if (!state.power) 0x88C0051 else {
                val mode = when (state.mode) {
                    AcMode.COOL -> 0; AcMode.DRY -> 1; AcMode.FAN -> 2; AcMode.AUTO -> 3; AcMode.HEAT -> 4
                }
                val fan = when (state.fan) {
                    AcFan.LOW -> 1; AcFan.MEDIUM -> 2; AcFan.HIGH -> 4; AcFan.AUTO -> 5
                }
                val body = (0x88 shl 20) or (mode shl 12) or ((state.temp - 15) shl 8) or (fan shl 4)
                val sum = (1..4).sumOf { (body shr (it * 4)) and 0xF } and 0xF
                body or sum
            }
            return IrProtocols.lgAc(value, lg2 = variant == 0)
        }
    }

    /** Coolix: 0xB2, fan<<5|0x1F, temp-code<<4|mode<<2. */
    object Coolix : AcBrand("coolix", "Midea / Springer / Carrier", 17, 30) {
        override val variants = listOf("Coolix")

        // Temperatures use a Gray-like code, not a plain offset.
        private val tempCodes = intArrayOf(
            0b0000, 0b0001, 0b0011, 0b0010, 0b0110, 0b0111, 0b0101,
            0b0100, 0b1100, 0b1101, 0b1001, 0b1000, 0b1010, 0b1011,
        ) // 17..30 °C

        override fun encode(state: AcState, variant: Int): IrSignal {
            val value = if (!state.power) 0xB27BE0 else {
                var fan = when (state.fan) {
                    AcFan.AUTO -> 0b101; AcFan.LOW -> 0b100; AcFan.MEDIUM -> 0b010; AcFan.HIGH -> 0b001
                }
                var temp = tempCodes[(state.temp - 17).coerceIn(0, tempCodes.lastIndex)]
                val mode = when (state.mode) {
                    AcMode.COOL -> 0b00
                    AcMode.HEAT -> 0b11
                    AcMode.AUTO -> { fan = 0b000; 0b10 }
                    AcMode.DRY -> { fan = 0b000; 0b01 }
                    AcMode.FAN -> { temp = 0b1110; 0b01 }
                }
                (0xB2 shl 16) or (((fan shl 5) or 0x1F) shl 8) or (temp shl 4) or (mode shl 2)
            }
            return IrProtocols.coolix(value)
        }
    }

    companion object {
        val all: List<AcBrand> get() = listOf(Lg, Coolix)
        fun byId(id: String) = all.firstOrNull { it.id == id }
    }
}
