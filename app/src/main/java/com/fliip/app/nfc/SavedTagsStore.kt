package com.fliip.app.nfc

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A persisted snapshot of a scanned tag. */
data class SavedTag(
    val id: String,
    val label: String,
    val uid: String,
    val type: String,
    val techList: List<String>,
    val ndefRawBase64: String?,
    val recordSummary: String,
    val savedAtMs: Long,
) {
    val ndefRaw: ByteArray? get() = ndefRawBase64?.let { Base64.decode(it, Base64.NO_WRAP) }
    val hasNdef: Boolean get() = !ndefRawBase64.isNullOrEmpty()
}

/**
 * File-backed list of saved tags (JSON in filesDir). Call [init] once from the
 * Application/Activity before use; UI observes [tags].
 */
object SavedTagsStore {
    private const val FILE = "saved_tags.json"
    private lateinit var file: File

    private val _tags = MutableStateFlow<List<SavedTag>>(emptyList())
    val tags: StateFlow<List<SavedTag>> = _tags

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.applicationContext.filesDir, FILE)
        _tags.value = load()
    }

    fun saveFrom(tag: TagInfo, label: String): SavedTag {
        val saved = SavedTag(
            id = "${tag.uid}-${tag.timestampMs}",
            label = label.ifBlank { tag.uid },
            uid = tag.uid,
            type = tag.type,
            techList = tag.techList,
            ndefRawBase64 = tag.ndefRaw?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
            recordSummary = tag.ndefRecords.joinToString("; ") { "${it.type}=${it.payload}" }
                .ifBlank { if (tag.hasNdef) "NDEF" else "no NDEF" },
            savedAtMs = System.currentTimeMillis(),
        )
        _tags.value = listOf(saved) + _tags.value.filterNot { it.id == saved.id }
        persist()
        return saved
    }

    fun delete(id: String) {
        _tags.value = _tags.value.filterNot { it.id == id }
        persist()
    }

    fun clear() {
        _tags.value = emptyList()
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _tags.value.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("label", t.label)
                put("uid", t.uid)
                put("type", t.type)
                put("techList", JSONArray(t.techList))
                put("ndefRawBase64", t.ndefRawBase64 ?: JSONObject.NULL)
                put("recordSummary", t.recordSummary)
                put("savedAtMs", t.savedAtMs)
            })
        }
        runCatching { file.writeText(arr.toString()) }
    }

    private fun load(): List<SavedTag> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val techs = o.optJSONArray("techList")?.let { ja ->
                    (0 until ja.length()).map { ja.getString(it) }
                } ?: emptyList()
                add(
                    SavedTag(
                        id = o.getString("id"),
                        label = o.optString("label"),
                        uid = o.optString("uid"),
                        type = o.optString("type"),
                        techList = techs,
                        ndefRawBase64 = if (o.isNull("ndefRawBase64")) null else o.optString("ndefRawBase64"),
                        recordSummary = o.optString("recordSummary"),
                        savedAtMs = o.optLong("savedAtMs"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
