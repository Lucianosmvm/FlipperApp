package com.fliip.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fliip.app.ir.AcBrand
import com.fliip.app.ir.IrController
import com.fliip.app.ir.IrLibrary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrHomeScreen(nav: NavController, ir: IrController) {
    val context = LocalContext.current
    var imported by remember { mutableStateOf(ir.importedRemotes()) }
    var message by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayName(context, uri) ?: "remote.ir"
        val text = runCatching {
            context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        }.getOrNull()
        message = if (text == null) "Could not read $name" else {
            val result = ir.import(name, text)
            when {
                result.buttons.isEmpty() -> "No usable buttons in $name (is it a Flipper .ir file?)"
                result.skipped.isEmpty() -> "Imported $name: ${result.buttons.size} buttons"
                else -> "Imported $name: ${result.buttons.size} buttons, ${result.skipped.size} skipped (unsupported protocol)"
            }
        }
        imported = ir.importedRemotes()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("IR — Remote control") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!ir.available) {
                item {
                    SectionCard("No IR emitter") {
                        Text("This phone has no infrared emitter, so nothing will be transmitted.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                SectionCard("TV") {
                    IrLibrary.tvs.forEach { r ->
                        NavRow(r.name, "${r.buttons.size} buttons", Icons.Default.Tv) { nav.navigate("ir_remote/${Uri.encode(r.id)}") }
                    }
                    NavRow("Find my TV", "Sends each brand's power code in turn", Icons.Default.Search) { nav.navigate("ir_finder") }
                }
            }
            item {
                SectionCard("Air conditioner") {
                    AcBrand.all.forEach { b ->
                        NavRow(b.name, "Power, mode, temperature, fan", Icons.Default.AcUnit) { nav.navigate("ir_ac/${b.id}") }
                    }
                }
            }
            item {
                SectionCard("Imported (Flipper .ir files)") {
                    if (imported.isEmpty()) {
                        Text(
                            "Any other device: download its .ir file from the Flipper-IRDB project " +
                                "(github.com/Lucaslhm/Flipper-IRDB) and import it here.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    imported.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                NavRow(r.name, "${r.buttons.size} buttons", Icons.Default.SettingsRemote) {
                                    nav.navigate("ir_remote/${Uri.encode(r.id)}")
                                }
                            }
                            IconButton(onClick = { ir.delete(r.id); imported = ir.importedRemotes() }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FileOpen, null)
                        Text("  Import .ir file")
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

// ---- Find my TV ------------------------------------------------------------

private const val FINDER_STEP_MS = 2500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrFinderScreen(nav: NavController, ir: IrController) {
    val codes = IrLibrary.powerCodes
    var running by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(-1) }
    val status by ir.status.collectAsState()

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        for (i in codes.indices) {
            index = i
            val (remote, code) = codes[i]
            ir.send("${remote.name} — Power", code)
            delay(FINDER_STEP_MS)
        }
        running = false
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Find my TV") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                SectionCard("How it works") {
                    Text(
                        "Point the top of the phone at the TV (1–3 m). Tap Start: the power code of each brand " +
                            "is sent every ${FINDER_STEP_MS / 1000.0} s. As soon as the TV turns on or off, tap \"It worked\".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (running) OutlinedButton(onClick = { running = false }) { Text("Stop") }
                        else Button(onClick = { index = -1; running = true }) { Text("Start") }
                        if (index >= 0) {
                            Button(onClick = {
                                running = false
                                nav.navigate("ir_remote/${Uri.encode(codes[index].first.id)}")
                            }) { Text("It worked") }
                        }
                    }
                    if (index >= 0) {
                        Text(
                            "Last sent: ${codes[index].first.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                SectionCard("Or test one brand") {
                    codes.forEach { (remote, code) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(remote.name, Modifier.weight(1f))
                            OutlinedButton(onClick = { ir.send("${remote.name} — Power", code) }) { Text("Power") }
                        }
                    }
                }
            }
        }
    }
}
