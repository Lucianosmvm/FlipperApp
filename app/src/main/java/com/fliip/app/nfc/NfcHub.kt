package com.fliip.app.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NdefFormatable
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App-wide sink for NFC reader-mode results. MainActivity feeds it; UI observes it. */
object NfcHub {
    private val _lastTag = MutableStateFlow<TagInfo?>(null)
    val lastTag: StateFlow<TagInfo?> = _lastTag

    private val _readerActive = MutableStateFlow(false)
    val readerActive: StateFlow<Boolean> = _readerActive

    /** When non-null, the next tapped tag is written instead of read. */
    private val _pendingWrite = MutableStateFlow<NdefMessage?>(null)
    val writeArmed: StateFlow<NdefMessage?> = _pendingWrite

    private val _writeResult = MutableStateFlow<WriteResult?>(null)
    val writeResult: StateFlow<WriteResult?> = _writeResult

    /** When true, the next tapped tag is permanently locked read-only. */
    private val _pendingLock = MutableStateFlow(false)
    val lockArmed: StateFlow<Boolean> = _pendingLock

    private val _lockResult = MutableStateFlow<WriteResult?>(null)
    val lockResult: StateFlow<WriteResult?> = _lockResult

    fun setReaderActive(active: Boolean) { _readerActive.value = active }

    /** Arm an irreversible read-only lock. Cancels any pending write. */
    fun armLock() {
        _pendingWrite.value = null
        _lockResult.value = null
        _pendingLock.value = true
    }

    fun cancelLock() { _pendingLock.value = false }

    fun clearLockResult() { _lockResult.value = null }

    fun armWrite(message: NdefMessage) {
        _pendingLock.value = false
        _writeResult.value = null
        _pendingWrite.value = message
    }

    /** Arm a replay from stored raw NDEF bytes. Returns false if bytes are invalid. */
    fun armWriteRaw(raw: ByteArray): Boolean {
        val msg = runCatching { NdefMessage(raw) }.getOrNull() ?: return false
        armWrite(msg)
        return true
    }

    fun cancelWrite() { _pendingWrite.value = null }

    fun clearWriteResult() { _writeResult.value = null }

    fun onTag(tag: Tag) {
        val pendingWrite = _pendingWrite.value
        when {
            _pendingLock.value -> {
                _pendingLock.value = false
                _lockResult.value = lock(tag)
            }
            pendingWrite != null -> {
                _pendingWrite.value = null
                _writeResult.value = write(tag, pendingWrite)
            }
            else -> _lastTag.value = parse(tag)
        }
    }

    fun clear() { _lastTag.value = null }

    private fun parse(tag: Tag): TagInfo {
        val uid = tag.id.toHex()
        val techs = tag.techList.map { it.substringAfterLast('.') }

        var type = "Unknown"
        var atqa: String? = null
        var sak: String? = null
        var maxSize: Int? = null
        var writable: Boolean? = null
        val records = mutableListOf<NdefRecordInfo>()

        NfcA.get(tag)?.let {
            atqa = it.atqa?.toHex()
            sak = "0x%02X".format(it.sak.toInt() and 0xFF)
        }
        when {
            MifareClassic.get(tag) != null -> type = "MIFARE Classic"
            MifareUltralight.get(tag) != null -> type = "MIFARE Ultralight"
            NfcA.get(tag) != null -> type = "ISO 14443-A (NfcA)"
            NfcB.get(tag) != null -> type = "ISO 14443-B (NfcB)"
            NfcF.get(tag) != null -> type = "FeliCa (NfcF)"
            NfcV.get(tag) != null -> type = "ISO 15693 (NfcV)"
        }

        var ndefRaw: ByteArray? = null
        Ndef.get(tag)?.let { ndef ->
            maxSize = ndef.maxSize
            writable = ndef.isWritable
            val message = ndef.cachedNdefMessage ?: runCatching {
                ndef.connect(); ndef.ndefMessage
            }.getOrNull()
            message?.let { m ->
                ndefRaw = m.toByteArray()
                m.records.forEach { r ->
                    records += NdefRecordInfo(
                        tnf = tnfName(r.tnf),
                        type = String(r.type).ifBlank { r.type.toHex() },
                        payload = decodePayload(r.tnf, r.payload),
                    )
                }
            }
            runCatching { if (ndef.isConnected) ndef.close() }
        }

        return TagInfo(
            uid = uid,
            techList = techs,
            type = type,
            atqa = atqa,
            sak = sak,
            maxSize = maxSize,
            writable = writable,
            ndefRecords = records,
            ndefRaw = ndefRaw,
        )
    }

    /** Write an NDEF message to a tag: existing NDEF tag, or format a blank one. */
    private fun write(tag: Tag, msg: NdefMessage): WriteResult {
        val size = msg.toByteArray().size
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return runCatching {
                ndef.connect()
                if (!ndef.isWritable) return WriteResult(false, "Tag is read-only")
                if (ndef.maxSize < size) {
                    return WriteResult(false, "Too big: $size B > ${ndef.maxSize} B capacity")
                }
                ndef.writeNdefMessage(msg)
                WriteResult(true, "Wrote $size B to ${tag.id.toHex()}")
            }.getOrElse { WriteResult(false, "Write failed: ${it.message}") }
                .also { runCatching { if (ndef.isConnected) ndef.close() } }
        }
        val formatable = NdefFormatable.get(tag)
            ?: return WriteResult(false, "Tag is not NDEF-capable")
        return runCatching {
            formatable.connect()
            formatable.format(msg)
            WriteResult(true, "Formatted + wrote $size B to ${tag.id.toHex()}")
        }.getOrElse { WriteResult(false, "Format failed: ${it.message}") }
            .also { runCatching { if (formatable.isConnected) formatable.close() } }
    }

    /** Permanently lock a tag read-only via Ndef.makeReadOnly(). IRREVERSIBLE. */
    private fun lock(tag: Tag): WriteResult {
        val ndef = Ndef.get(tag)
            ?: return WriteResult(false, "Tag is not NDEF — cannot lock via NDEF")
        return runCatching {
            ndef.connect()
            when {
                !ndef.isWritable -> WriteResult(true, "Already read-only: ${tag.id.toHex()}")
                !ndef.canMakeReadOnly() -> WriteResult(false, "This tag type cannot be locked")
                ndef.makeReadOnly() -> WriteResult(true, "Locked read-only: ${tag.id.toHex()}")
                else -> WriteResult(false, "Lock command rejected by tag")
            }
        }.getOrElse { WriteResult(false, "Lock failed: ${it.message}") }
            .also { runCatching { if (ndef.isConnected) ndef.close() } }
    }

    private fun tnfName(tnf: Short): String = when (tnf.toInt()) {
        0 -> "EMPTY"; 1 -> "WELL_KNOWN"; 2 -> "MIME"; 3 -> "ABSOLUTE_URI"
        4 -> "EXTERNAL"; 5 -> "UNKNOWN"; 6 -> "UNCHANGED"; else -> "RESERVED"
    }

    private fun decodePayload(tnf: Short, payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        // Well-known URI record: first byte is a URI-prefix code.
        if (tnf.toInt() == 1) {
            val prefixes = URI_PREFIXES
            val idx = payload[0].toInt() and 0xFF
            if (idx in prefixes.indices) {
                return prefixes[idx] + String(payload, 1, payload.size - 1, Charsets.UTF_8)
            }
        }
        return runCatching { String(payload, Charsets.UTF_8) }
            .getOrDefault(payload.toHex())
    }
}

data class WriteResult(val success: Boolean, val message: String)

/** Builders for the NDEF record kinds the UI can write. */
object NdefBuilder {
    fun text(value: String, lang: String = "en"): NdefMessage =
        NdefMessage(NdefRecord.createTextRecord(lang, value))

    fun uri(value: String): NdefMessage =
        NdefMessage(NdefRecord.createUri(value))

    fun mime(mimeType: String, value: String): NdefMessage =
        NdefMessage(NdefRecord.createMime(mimeType, value.toByteArray(Charsets.UTF_8)))
}

internal fun ByteArray.toHex(): String =
    joinToString("") { "%02X".format(it.toInt() and 0xFF) }

private val URI_PREFIXES = arrayOf(
    "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
    "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
    "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://",
    "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
    "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
    "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
)
