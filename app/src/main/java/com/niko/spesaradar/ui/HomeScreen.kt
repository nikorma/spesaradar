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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val s = vm.screen
    val topLevel = s is Screen.Home || s is Screen.Flyers || s is Screen.SettingsScreen
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (topLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    NavigationBarItem(
                        selected = s is Screen.Home,
                        onClick = { vm.tab(Screen.Home) },
                        icon = { Icon(Icons.Outlined.Visibility, null) },
                        label = { Text("Osservati") }
                    )
                    NavigationBarItem(
                        selected = s is Screen.Flyers,
                        onClick = { vm.tab(Screen.Flyers) },
                        icon = { Icon(Icons.Outlined.ShoppingCart, null) },
                        label = { Text("Volantini") }
                    )
                    NavigationBarItem(
                        selected = s is Screen.SettingsScreen,
                        onClick = { vm.tab(Screen.SettingsScreen) },
                        icon = { Icon(Icons.Outlined.Settings, null) },
                        label = { Text("Impostazioni") }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (s) {
                is Screen.Home -> HomeScreen(vm)
                is Screen.Flyers -> FlyersScreen(vm)
                is Screen.FlyerBrowse -> FlyerBrowseScreen(vm, s.chain)
                is Screen.Offers -> OffersScreen(vm, s.watchId)
                is Screen.Trend -> TrendScreen(vm, s.watchId, s.offerKey)
                is Screen.SettingsScreen -> SettingsScreen(vm)
                is Screen.Diagnostics -> DiagnosticsScreen(vm)
            }
        }
    }
}

/** Barra di avanzamento sottile con la frase "Volantini · Lidl…". */
@Composable
fun ProgressStrip(vm: AppViewModel) {
    if (!vm.refreshing) return
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            vm.progress,
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

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
        BigHeader(
            title = "SpesaRadar",
            subtitle = "${vm.settings.city.replaceFirstChar { it.uppercase() }} · " +
                if (vm.summaries.isEmpty()) "nessun prodotto osservato" else "${vm.summaries.size} prodotti osservati"
        ) {
            if (vm.refreshing) {
                CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Filled.Refresh, "Aggiorna tutto") }
            }
        }
        ProgressStrip(vm)

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Aggiungi prodotto o marca") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { submit() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Osserva") }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )

        if (vm.summaries.isEmpty()) {
            EmptyHome()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.summaries, key = { it.watch.id }) { s ->
                    WatchCard(s) { vm.open(Screen.Offers(s.watch.id)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyHome() {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text("Cosa vuoi tenere d'occhio?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scrivi una tipologia (\"detersivo piatti\") o una marca (\"Chilly\"). SpesaRadar la cerca nei volantini " +
                "completi delle tue catene, su Amazon e online, e ogni mattina ti avvisa se trova un prezzo migliore.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WatchCard(s: WatchSummary, onClick: () -> Unit) {
    val best = s.best
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(best?.image, Modifier.size(72.dp), corner = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.watch.query.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val line = when {
                    s.watch.lastRefresh == 0L -> "In attesa del primo controllo…"
                    s.activeCount == 0 -> "Nessuna offerta in questo momento"
                    s.activeCount == 1 -> "1 offerta"
                    else -> "${s.activeCount} offerte"
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (best != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChainTag(best.store ?: best.source.label, Modifier.weight(1f, fill = false))
                        if (s.historicLow) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Outlined.TrendingDown, "Minimo storico", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (s.watch.lastError != null) {
                    Text("Alcune fonti con problemi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (best != null) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("da", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(Units.euro(best.price), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    if (best.unitPrice != null) {
                        Text(
                            Units.perUnit(best.unitPrice, best.unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, bg: Color, fg: Color) {
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
