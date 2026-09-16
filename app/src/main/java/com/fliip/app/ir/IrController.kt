package com.fliip.app.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.Executors

/** Serialises IR transmissions and manages imported `.ir` remote files. */
class IrController(context: Context) {

    private val appCtx = context.applicationContext
    private val ir = appCtx.getSystemService(ConsumerIrManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var toggle = false

    val available: Boolean get() = ir?.hasIrEmitter() == true

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun send(label: String, code: IrCode) {
        toggle = !toggle
        val signal = IrProtocols.encode(code, toggle)
            ?: run { _status.value = "$label: unsupported protocol"; return }
        send(label, signal)
    }

    fun send(label: String, signal: IrSignal) {
        val manager = ir
        if (manager == null || !manager.hasIrEmitter()) {
            _status.value = "No IR emitter on this phone"
            return
        }
        executor.execute {
            _status.value = runCatching {
                // Platform limit: a single transmit may not exceed 2 seconds.
                require(signal.durationMicros <= 2_000_000) { "signal longer than 2 s" }
                manager.transmit(signal.frequency, signal.pattern)
                "Sent: $label"
            }.getOrElse { "Failed: $label (${it.message})" }
        }
    }

    // ---- Imported remotes --------------------------------------------------

    private val dir get() = File(appCtx.filesDir, "ir_remotes").apply { mkdirs() }

    fun importedRemotes(): List<IrRemote> =
        dir.listFiles { f -> f.isFile }.orEmpty().sortedBy { it.name.lowercase() }.map { load(it) }

    fun remote(id: String): IrRemote? = when {
        id.startsWith("builtin:") -> IrLibrary.builtin(id)
        id.startsWith("file:") -> File(dir, id.removePrefix("file:")).takeIf { it.isFile }?.let { load(it) }
        else -> null
    }

    /** Saves a Flipper `.ir` file; returns the parse result so the UI can report what was usable. */
    fun import(fileName: String, text: String): FlipperIrParser.Result {
        val result = FlipperIrParser.parse(text)
        if (result.buttons.isNotEmpty()) {
            val safe = fileName.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "remote.ir" }
            File(dir, safe).writeText(text)
        }
        return result
    }

    fun delete(id: String) {
        if (id.startsWith("file:")) File(dir, id.removePrefix("file:")).delete()
    }

    private fun load(file: File) = IrRemote(
        id = "file:${file.name}",
        name = file.nameWithoutExtension.replace('_', ' '),
        buttons = FlipperIrParser.parse(file.readText()).buttons,
    )
}
