@file:OptIn(ExperimentalMaterial3Api::class)

package com.niko.spesaradar.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.niko.spesaradar.data.Units
import com.niko.spesaradar.data.WatchSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppRoot(vm: AppViewModel, onExit: () -> Unit) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) {
            snack.showSnackbar(m)
            vm.message = null
        }
    }
    BackHandler {
        if (!vm.back()) onExit()
    }
    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = vm.screen) {
                is Screen.Home -> HomeScreen(vm)
                is Screen.Offers -> OffersScreen(vm, s.watchId)
                is Screen.Trend -> TrendScreen(vm, s.watchId, s.offerKey)
                is Screen.SettingsScreen -> SettingsScreen(vm)
                is Screen.Diagnostics -> DiagnosticsScreen(vm)
            }
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    // Permesso notifiche (Android 13+), chiesto una volta all'avvio.
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun submit() {
        if (query.trim().length >= 2) {
            vm.addWatch(query)
            query = ""
            keyboard?.hide()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("SpesaRadar", fontWeight = FontWeight.Medium) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
            ),
            actions = {
                if (vm.refreshing) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp).padding(end = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Filled.Refresh, "Aggiorna tutto") }
                }
                IconButton(onClick = { vm.open(Screen.SettingsScreen) }) { Icon(Icons.Filled.Settings, "Impostazioni") }
            }
        )
        if (vm.refreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                vm.progress,
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Prodotto o marca, es. detersivo piatti") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            IconButton(onClick = { submit() }) { Icon(Icons.Filled.Add, "Osserva") }
        }

        if (vm.summaries.isEmpty()) {
            EmptyHome()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.summaries, key = { it.watch.id }) { s ->
                    WatchCard(s) { vm.open(Screen.Offers(s.watch.id)) }
                }
                item {
                    Text(
                        "${vm.settings.city.replaceFirstChar { it.uppercase() }} · controllo automatico alle " +
                            String.format(Locale.ITALY, "%02d:00", vm.settings.hour),
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHome() {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text("Inizia a osservare un prodotto", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scrivi una tipologia (\"detersivo piatti\") o una marca (\"Svelto\"). " +
                "SpesaRadar cerca le offerte nei volantini della tua città, su Amazon e nei negozi online, " +
                "le ordina per prezzo al litro o al chilo e ogni mattina controlla se ci sono novità.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WatchCard(s: WatchSummary, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.watch.query.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (s.historicLow) Badge("Minimo storico", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }
            val best = s.best
            val line = when {
                s.watch.lastRefresh == 0L -> "In attesa del primo controllo…"
                s.activeCount == 0 -> "Nessuna offerta trovata"
                best != null -> "${s.activeCount} offerte · da ${Units.perUnit(best.unitPrice, best.unit)}"
                else -> "${s.activeCount} offerte"
            }
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (best != null) {
                Text(
                    "${best.store ?: best.source.label} · ${best.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (s.watch.lastRefresh > 0) {
                Text(
                    "Aggiornato " + fmtTime(s.watch.lastRefresh) + if (s.watch.lastError != null) " · con problemi" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (s.watch.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun Badge(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

fun fmtTime(ms: Long): String {
    val d = Date(ms)
    val today = SimpleDateFormat("yyyyMMdd", Locale.ITALY).format(Date())
    val that = SimpleDateFormat("yyyyMMdd", Locale.ITALY).format(d)
    return if (today == that) "oggi alle " + SimpleDateFormat("HH:mm", Locale.ITALY).format(d)
    else SimpleDateFormat("d MMM 'alle' HH:mm", Locale.ITALY).format(d)
}
