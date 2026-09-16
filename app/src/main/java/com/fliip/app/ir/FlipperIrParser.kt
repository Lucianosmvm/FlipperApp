package com.fliip.app.ir

/**
 * Parser for Flipper Zero `.ir` files (as found in the Flipper-IRDB community database):
 *
 *     name: Power
 *     type: parsed
 *     protocol: NEC
 *     address: 04 00 00 00
 *     command: 08 00 00 00
 *     #
 *     name: Vol_up
 *     type: raw
 *     frequency: 38000
 *     duty_cycle: 0.330000
 *     data: 9024 4512 579 552 ...
 */
object FlipperIrParser {

    data class Result(val buttons: List<IrButton>, val skipped: List<String>)

    fun parse(text: String): Result {
        val buttons = mutableListOf<IrButton>()
        val skipped = mutableListOf<String>()
        var current: MutableMap<String, String>? = null

        fun flush() {
            val block = current ?: return
            val name = block["name"] ?: return
            val code = toCode(block)
            if (code != null) buttons += IrButton(name, code)
            else skipped += "$name (${block["protocol"] ?: block["type"] ?: "?"})"
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val idx = line.indexOf(':').takeIf { it > 0 } ?: return@forEach
            val key = line.substring(0, idx).trim().lowercase()
            val value = line.substring(idx + 1).trim()
            if (key == "name") {
                flush()
                current = mutableMapOf("name" to value)
            } else current?.let { block ->
                block[key] = if (key == "data" && key in block) block[key] + " " + value else value
            }
        }
        flush()
        return Result(buttons, skipped)
    }

    private fun toCode(block: Map<String, String>): IrCode? = when (block["type"]?.lowercase()) {
        "parsed" -> {
            val protocol = block["protocol"]
            val address = littleEndian(block["address"])
            val command = littleEndian(block["command"])
            if (protocol != null && IrProtocols.supports(protocol) && address != null && command != null) {
                IrCode.Parsed(protocol, address, command)
            } else null
        }
        "raw" -> {
            val frequency = block["frequency"]?.toDoubleOrNull()?.toInt() ?: 38_000
            val data = block["data"]?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }?.toIntArray()
            if (data != null && data.isNotEmpty()) IrCode.Raw(frequency, data) else null
        }
        else -> null
    }

    /** "07 00 00 00" -> 0x00000007 */
    private fun littleEndian(hexBytes: String?): Int? {
        val bytes = hexBytes?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.map { it.toIntOrNull(16) ?: return null }
            ?: return null
        return bytes.take(4).foldIndexed(0) { i, acc, b -> acc or (b shl (8 * i)) }
    }
}
