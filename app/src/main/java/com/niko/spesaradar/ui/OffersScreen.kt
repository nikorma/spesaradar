@file:OptIn(ExperimentalMaterial3Api::class)

package com.niko.spesaradar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.niko.spesaradar.data.Chains
import com.niko.spesaradar.data.Offer
import com.niko.spesaradar.data.Source
import com.niko.spesaradar.data.Units
import kotlin.math.roundToInt

private enum class Filter(val label: String) { ALL("Tutte"), SHOPS("Volantini"), SITES("Siti catene"), AMAZON("Amazon"), ONLINE("Altri online") }

@Composable
fun OffersScreen(vm: AppViewModel, watchId: Long) {
    val ctx = LocalContext.current
    val watch = vm.watch(watchId)
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var onlyMine by rememberSaveable { mutableStateOf(vm.settings.onlyMyChains) }
    var askDelete by remember { mutableStateOf(false) }

    // Ricarica la lista quando cambia lo stato di aggiornamento (fine refresh).
    val all = remember(watchId, vm.refreshing, vm.summaries.size) { vm.offers(watchId) }
    val mainUnit = AppViewModel.mainUnit(all)

    val shown = all
        .filter {
            when (filter) {
                Filter.ALL -> true
                Filter.SHOPS -> it.source == Source.PROMOQUI
                Filter.SITES -> it.source == Source.SITES
                Filter.AMAZON -> it.source == Source.AMAZON
                Filter.ONLINE -> it.source == Source.TROVAPREZZI
            }
        }
        .filter { !(onlyMine && it.source == Source.PROMOQUI && !Chains.isMine(it.store)) }
        .sortedWith(
            compareBy<Offer>(
                { if (it.unitPrice == null) 2 else if (it.unit == mainUnit) 0 else 1 },
                { it.unit ?: "" },
                { it.unitPrice ?: it.price },
                { it.price }
            )
        )
    val hiddenOthers = all.count { it.source == Source.PROMOQUI && !Chains.isMine(it.store) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    watch?.query?.replaceFirstChar { it.uppercase() } ?: "Offerte",
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = {
                IconButton(onClick = { vm.open(Screen.Trend(watchId)) }) { Icon(Icons.Filled.ShowChart, "Andamento") }
                IconButton(onClick = { vm.refreshOne(watchId) }, enabled = !vm.refreshing) { Icon(Icons.Filled.Refresh, "Aggiorna") }
                IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, "Elimina") }
            }
        )
        if (vm.refreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(vm.progress, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Filter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
            FilterChip(
                selected = onlyMine,
                onClick = { onlyMine = !onlyMine },
                label = { Text(if (onlyMine) "Solo le mie catene" else "Tutti i negozi") }
            )
        }

        if (shown.isEmpty()) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    when {
                        watch?.lastRefresh == 0L -> "Primo controllo in corso o non ancora fatto."
                        all.isEmpty() -> "Nessuna offerta trovata per ora."
                        else -> "Nessuna offerta con questi filtri."
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                if (onlyMine && hiddenOthers > 0) {
                    Text(
                        "$hiddenOthers offerte di altri negozi sono nascoste.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                watch?.lastError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.open(Screen.Diagnostics) }) { Text("Apri diagnostica") }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.key }) { o ->
                    OfferRow(
                        o,
                        isBest = o == shown.firstOrNull(),
                        onOpen = {
                            o.url?.let { u ->
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                            }
                        },
                        onHistory = { vm.open(Screen.Trend(watchId, o.key)) }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                if (onlyMine && hiddenOthers > 0 && (filter == Filter.ALL || filter == Filter.SHOPS)) {
                    item {
                        Text(
                            "$hiddenOthers offerte di altri negozi nascoste · tocca \"Solo le mie catene\" per vederle",
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Smettere di osservare?") },
            text = { Text("Verrà cancellato anche lo storico prezzi di \"${watch?.query}\".") },
            confirmButton = { TextButton(onClick = { askDelete = false; vm.deleteWatch(watchId) }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun OfferRow(o: Offer, isBest: Boolean, onOpen: () -> Unit, onHistory: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    o.title,
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Units.euro(o.price), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                    o.oldPrice?.let {
                        Text(
                            Units.euro(it),
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val where = buildString {
                    append(o.store ?: o.source.label)
                    if (o.source != Source.PROMOQUI && o.store != o.source.label) append(" · online")
                    o.qtyText?.let { append(" · ").append(it) }
                    o.distanceKm?.let { append(" · ").append(String.format(java.util.Locale.ITALY, "%.1f km", it)) }
                }
                Text(
                    where,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (o.unitPrice != null) {
                    Text(
                        Units.perUnit(o.unitPrice, o.unit),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                        color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val extra = buildList {
                o.oldPrice?.let { old -> if (old > o.price) add("-" + ((1 - o.price / old) * 100).roundToInt() + "%") }
                o.validity?.let { add(it) }
                if (o.sponsored) add("sponsorizzato")
            }
            if (extra.isNotEmpty()) {
                Text(
                    extra.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        IconButton(onClick = onHistory) {
            Icon(Icons.Filled.Timeline, "Storico prezzo", tint = MaterialTheme.colorScheme.outline)
        }
    }
}
