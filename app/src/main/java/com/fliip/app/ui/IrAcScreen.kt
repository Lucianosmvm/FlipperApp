package com.fliip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fliip.app.ir.AcBrand
import com.fliip.app.ir.AcFan
import com.fliip.app.ir.AcMode
import com.fliip.app.ir.AcState
import com.fliip.app.ir.IrController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IrAcScreen(nav: NavController, ir: IrController, brandId: String) {
    val brand = remember(brandId) { AcBrand.byId(brandId) }
    val status by ir.status.collectAsState()
    val haptic = LocalHapticFeedback.current
    var state by remember { mutableStateOf(AcState(power = false)) }
    var variant by remember { mutableIntStateOf(0) }

    fun send(new: AcState) {
        state = new
        brand ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val label = if (new.power) "${new.temp}°C ${new.mode.label}, fan ${new.fan.label}" else "Off"
        ir.send("${brand.name} AC — $label", brand.encode(new, variant))
    }

    /** Settings changed while off are only remembered; they go out with the next "On". */
    fun update(new: AcState) {
        if (state.power) send(new) else state = new
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(brand?.let { "${it.name} AC" } ?: "Air conditioner") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
    }) { pad ->
        if (brand == null) {
            Text("Unknown brand.", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionCard("State") {
                Text(
                    if (state.power) "${state.temp}°C" else "OFF",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${state.mode.label} · Fan ${state.fan.label}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    status ?: if (ir.available) "Point the top of the phone at the AC." else "No IR emitter on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { send(state.copy(power = true)) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    ) { Text("ON") }
                    Button(
                        onClick = { send(state.copy(power = false)) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    ) { Text("OFF") }
                }
            }

            SectionCard("Temperature") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { update(state.copy(temp = (state.temp - 1).coerceAtLeast(brand.minTemp))) },
                        enabled = state.temp > brand.minTemp,
                    ) { Icon(Icons.Default.Remove, "Colder") }
                    Text("${state.temp}°C", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    FilledTonalIconButton(
                        onClick = { update(state.copy(temp = (state.temp + 1).coerceAtMost(brand.maxTemp))) },
                        enabled = state.temp < brand.maxTemp,
                    ) { Icon(Icons.Default.Add, "Warmer") }
                }
            }

            SectionCard("Mode") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AcMode.entries.forEach { m ->
                        FilterChip(selected = state.mode == m, onClick = { update(state.copy(mode = m)) }, label = { Text(m.label) })
                    }
                }
            }

            SectionCard("Fan") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AcFan.entries.forEach { f ->
                        FilterChip(selected = state.fan == f, onClick = { update(state.copy(fan = f)) }, label = { Text(f.label) })
                    }
                }
            }

            if (brand.variants.size > 1) {
                SectionCard("Remote protocol") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        brand.variants.forEachIndexed { i, name ->
                            FilterChip(selected = variant == i, onClick = { variant = i }, label = { Text(name) })
                        }
                    }
                    Text("If the AC does not beep, switch protocol and press ON again.", style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionCard("Note") {
                Text(
                    "AC remotes send the whole state at once. The app cannot know what the AC is currently set to, " +
                        "so each press applies everything shown on this screen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { send(state) }) { Text("Send again") }
            }
        }
    }
}
