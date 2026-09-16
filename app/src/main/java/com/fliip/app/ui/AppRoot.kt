package com.fliip.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fliip.app.ble.BleController
import com.fliip.app.ir.IrController

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val ble = remember { BleController(context.applicationContext) }
    val ir = remember { IrController(context.applicationContext) }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("diagnostics") { DiagnosticsScreen(nav) }
        composable("ir_home") { IrHomeScreen(nav, ir) }
        composable("ir_finder") { IrFinderScreen(nav, ir) }
        composable("ir_remote/{id}") { entry ->
            IrRemoteScreen(nav, ir, entry.arguments?.getString("id").orEmpty())
        }
        composable("ir_ac/{brand}") { entry ->
            IrAcScreen(nav, ir, entry.arguments?.getString("brand").orEmpty())
        }
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
