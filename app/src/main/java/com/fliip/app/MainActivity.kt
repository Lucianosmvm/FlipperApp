package com.fliip.app

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.fliip.app.nfc.NfcHub
import com.fliip.app.nfc.SavedTagsStore
import com.fliip.app.ui.AppRoot
import com.fliip.app.ui.theme.FliipTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var readerEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SavedTagsStore.init(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent { FliipTheme { AppRoot() } }

        // Enable NFC reader mode only while a screen asks for it.
        lifecycleScope.launch {
            NfcHub.readerActive.collect { active ->
                if (active) enableReader() else disableReader()
            }
        }
    }

    private fun enableReader() {
        val adapter = nfcAdapter ?: return
        if (readerEnabled) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        adapter.enableReaderMode(this, { tag -> NfcHub.onTag(tag) }, flags, null)
        readerEnabled = true
    }

    private fun disableReader() {
        val adapter = nfcAdapter ?: return
        if (!readerEnabled) return
        adapter.disableReaderMode(this)
        readerEnabled = false
    }

    override fun onPause() {
        super.onPause()
        disableReader()
    }

    override fun onResume() {
        super.onResume()
        if (NfcHub.readerActive.value) enableReader()
    }

    fun nfcSupported(): Boolean = nfcAdapter != null
    fun nfcEnabled(): Boolean = nfcAdapter?.isEnabled == true
}
