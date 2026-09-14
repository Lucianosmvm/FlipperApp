package com.fliip.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fliip.app.ble.BleController

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val ble = remember { BleController(context.applicationContext) }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("nfc_read") { NfcReaderScreen(nav) }
        composable("nfc_write") { NfcWriteScreen(nav) }
        composable("nfc_saved") { SavedTagsScreen(nav) }
        composable("nfc_hce") { HceScreen(nav) }
        composable("ble_scan") { BleScanScreen(nav, ble) }
        composable("ble_device/{address}") { entry ->
            BleDeviceScreen(nav, ble, entry.arguments?.getString("address").orEmpty())
        }
    }
}
