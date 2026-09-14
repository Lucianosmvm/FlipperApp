package com.fliip.app.nfc

/** Snapshot of a scanned NFC tag. */
data class TagInfo(
    val uid: String,
    val techList: List<String>,
    val type: String,
    val atqa: String? = null,
    val sak: String? = null,
    val maxSize: Int? = null,
    val writable: Boolean? = null,
    val ndefRecords: List<NdefRecordInfo> = emptyList(),
    /** Full NDEF message bytes, if present — enables byte-exact replay. */
    val ndefRaw: ByteArray? = null,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val hasNdef: Boolean get() = ndefRaw != null && ndefRaw.isNotEmpty()
}

data class NdefRecordInfo(
    val tnf: String,
    val type: String,
    val payload: String,
)
