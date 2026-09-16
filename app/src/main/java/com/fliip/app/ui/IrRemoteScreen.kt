package com.fliip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fliip.app.ir.IrButton
import com.fliip.app.ir.IrController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IrRemoteScreen(nav: NavController, ir: IrController, id: String) {
    val remote = remember(id) { ir.remote(id) }
    val status by ir.status.collectAsState()
    val haptic = LocalHapticFeedback.current
    val (keys, extras) = remember(remote) { mapButtons(remote?.buttons.orEmpty()) }
    val press: (IrButton) -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        ir.send(it.name, it.code)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(remote?.name ?: "Remote") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
    }) { pad ->
        if (remote == null) {
            Text("Remote not found.", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                status ?: if (ir.available) "Point the top of the phone at the device." else "No IR emitter on this phone.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key(keys["power"], press, container = Color(0xFFC62828)) { Icon(Icons.Default.PowerSettingsNew, "Power") }
                Key(keys["source"], press) { Label("SOURCE") }
                Key(keys["mute"], press) { Label("MUTE") }
            }
            if ("power_on" in keys || "power_off" in keys) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Key(keys["power_on"], press) { Label("ON") }
                    Key(keys["power_off"], press) { Label("OFF") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RockerColumn("VOL", keys["vol_up"], keys["vol_dn"], press, Modifier.weight(1f))
                Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Spacer(Modifier.weight(1f))
                        Key(keys["up"], press) { Icon(Icons.Default.KeyboardArrowUp, "Up") }
                        Spacer(Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Key(keys["left"], press) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left") }
                        Key(keys["ok"], press, container = MaterialTheme.colorScheme.primary) { Label("OK") }
                        Key(keys["right"], press) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Spacer(Modifier.weight(1f))
                        Key(keys["down"], press) { Icon(Icons.Default.KeyboardArrowDown, "Down") }
                        Spacer(Modifier.weight(1f))
                    }
                }
                RockerColumn("CH", keys["ch_up"], keys["ch_dn"], press, Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key(keys["back"], press) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Key(keys["home"], press) { Icon(Icons.Default.Home, "Home") }
                Key(keys["menu"], press) { Label("MENU") }
                Key(keys["exit"], press) { Label("EXIT") }
            }
            if ("info" in keys || "guide" in keys) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Key(keys["info"], press) { Label("INFO") }
                    Key(keys["guide"], press) { Label("GUIDE") }
                }
            }
            if (listOf("play", "pause", "stop", "rewind", "ffwd").any { it in keys }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Key(keys["rewind"], press) { Icon(Icons.Default.FastRewind, "Rewind") }
                    Key(keys["play"], press) { Icon(Icons.Default.PlayArrow, "Play") }
                    Key(keys["pause"], press) { Icon(Icons.Default.Pause, "Pause") }
                    Key(keys["stop"], press) { Icon(Icons.Default.Stop, "Stop") }
                    Key(keys["ffwd"], press) { Icon(Icons.Default.FastForward, "Fast forward") }
                }
            }
            if ((0..9).any { "$it" in keys }) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { d -> Key(keys[d], press) { Label(d) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    Key(keys["0"], press) { Label("0") }
                    Spacer(Modifier.weight(1f))
                }
            }

            if (extras.isNotEmpty()) {
                Text("Other buttons", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    extras.forEach { b -> OutlinedButton(onClick = { press(b) }) { Text(b.name.replace('_', ' ')) } }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(
    button: IrButton?,
    onPress: (IrButton) -> Unit,
    container: Color? = null,
    content: @Composable () -> Unit,
) {
    KeyButton(button, onPress, Modifier.weight(1f), container, content)
}

@Composable
private fun KeyButton(
    button: IrButton?,
    onPress: (IrButton) -> Unit,
    modifier: Modifier,
    container: Color? = null,
    content: @Composable () -> Unit,
) {
    FilledTonalButton(
        onClick = { button?.let(onPress) },
        enabled = button != null,
        modifier = modifier.height(52.dp),
        contentPadding = PaddingValues(0.dp),
        colors = if (container != null) {
            ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = Color.White)
        } else ButtonDefaults.filledTonalButtonColors(),
    ) { content() }
}

@Composable
private fun RockerColumn(label: String, up: IrButton?, down: IrButton?, onPress: (IrButton) -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyButton(up, onPress, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, "$label up") }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        KeyButton(down, onPress, Modifier.fillMaxWidth()) { Icon(Icons.Default.Remove, "$label down") }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

// ---- Button-name mapping ----------------------------------------------------

private val keyAliases: Map<String, List<String>> = mapOf(
    "power" to listOf("power", "pwr", "onoff", "standby", "powertoggle"),
    "power_on" to listOf("poweron", "on", "discreteon"),
    "power_off" to listOf("poweroff", "off", "discreteoff"),
    "source" to listOf("source", "input", "inputs", "src", "tvav", "av"),
    "mute" to listOf("mute"),
    "vol_up" to listOf("volup", "volumeup", "volplus", "volumeplus"),
    "vol_dn" to listOf("voldn", "voldown", "volumedown", "volumedn", "volminus", "volumeminus"),
    "ch_up" to listOf("chnext", "chup", "channelup", "chplus", "channelplus"),
    "ch_dn" to listOf("chprev", "chdn", "chdown", "channeldown", "chminus", "channelminus"),
    "up" to listOf("up", "cursorup", "arrowup"),
    "down" to listOf("down", "cursordown", "arrowdown"),
    "left" to listOf("left", "cursorleft", "arrowleft"),
    "right" to listOf("right", "cursorright", "arrowright"),
    "ok" to listOf("ok", "enter", "select", "confirm"),
    "back" to listOf("back", "return", "ret"),
    "home" to listOf("home", "smarthub"),
    "menu" to listOf("menu", "settings", "setup"),
    "exit" to listOf("exit"),
    "info" to listOf("info", "display"),
    "guide" to listOf("guide", "epg"),
    "play" to listOf("play"),
    "pause" to listOf("pause"),
    "stop" to listOf("stop"),
    "rewind" to listOf("rewind", "rew"),
    "ffwd" to listOf("fastfwd", "ff", "ffwd", "fastforward", "forward"),
) + (0..9).associate { "$it" to listOf("$it", "num$it", "key$it", "digit$it", "btn$it") }

private val aliasToKey: Map<String, String> =
    keyAliases.flatMap { (key, aliases) -> aliases.map { it to key } }.toMap()

private fun normalize(name: String): String {
    var n = name.trim().lowercase()
    if (n.endsWith("+")) n = n.dropLast(1) + "plus"
    if (n.endsWith("-")) n = n.dropLast(1) + "minus"
    return n.filter { it.isLetterOrDigit() }
}

/** Places buttons on the standard layout by name; anything unrecognised goes to "Other buttons". */
private fun mapButtons(buttons: List<IrButton>): Pair<Map<String, IrButton>, List<IrButton>> {
    val keys = LinkedHashMap<String, IrButton>()
    val extras = mutableListOf<IrButton>()
    buttons.forEach { b ->
        val key = aliasToKey[normalize(b.name)]
        if (key != null && key !in keys) keys[key] = b else extras += b
    }
    return keys to extras
}
