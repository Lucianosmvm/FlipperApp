package com.fliip.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Host Card Emulation service. Emulates a minimal ISO-DEP card:
 *  - SELECT AID  -> returns 0x9000 + the stored message bytes
 *  - any other   -> returns 0x9000 + stored message (demo behaviour)
 *
 * This is a demo/testing card, NOT a clone of any real credential. The AID
 * (F0 4C 4C 49 50) is a custom "F0..." vendor AID so it never collides with
 * real payment/transit applets.
 */
class FliipHceService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        HceLog.push("APDU in:  " + (commandApdu?.toHex() ?: "null"))
        val payload = message.value.toByteArray(Charsets.UTF_8)
        val resp = payload + SW_OK
        HceLog.push("APDU out: " + resp.toHex())
        return resp
    }

    override fun onDeactivated(reason: Int) {
        val why = if (reason == DEACTIVATION_LINK_LOSS) "link loss" else "other AID selected"
        HceLog.push("deactivated ($why)")
    }

    companion object {
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)

        /** Message the emulated card returns. Editable from the HCE screen. */
        val message = MutableStateFlow("FLIIP-DEMO-CARD")
    }
}

/** Rolling APDU exchange log shared with the UI. */
object HceLog {
    private const val MAX = 100
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    fun push(line: String) {
        val ts = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        _lines.value = (_lines.value + "[$ts] $line").takeLast(MAX)
    }
    fun clear() { _lines.value = emptyList() }
}
