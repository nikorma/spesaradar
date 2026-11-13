@file:OptIn(ExperimentalMaterial3Api::class)

package com.niko.spesaradar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.niko.spesaradar.data.Chains
import com.niko.spesaradar.data.Flyer
import com.niko.spesaradar.data.FlyerItem
import com.niko.spesaradar.data.Text as TextUtil

@Composable
fun FlyersScreen(vm: AppViewModel) {
    val flyers = remember(vm.flyerVersion) { vm.flyers() }
    val byChain = flyers.groupBy { it.chain }

    Column(Modifier.fillMaxSize()) {
        BigHeader(
            title = "Volantini",
            subtitle = if (flyers.isEmpty()) "Le offerte complete delle tue catene"
            else "${flyers.size} volantini · ${flyers.sumOf { it.itemCount }} offerte"
        ) {
            if (vm.refreshing) {
                CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                IconButton(onClick = { vm.refreshFlyers(force = true) }) { Icon(Icons.Filled.Refresh, "Aggiorna volantini") }
            }
        }
        ProgressStrip(vm)

        if (flyers.isEmpty() && !vm.refreshing) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Scarica i volantini in corso di Despar, Lidl, Eurospin, Esselunga, Carrefour, Alì, Coop, Conad, MD e C+C. " +
                        "Ci vogliono alcuni minuti la prima volta; poi si aggiornano da soli ogni mattina.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.refreshFlyers(force = true) }) { Text("Scarica i volantini") }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(Chains.MINE, key = { it.name }) { chain ->
                val list = byChain[chain.name].orEmpty()
                ChainCard(chain.name, Color(chain.color), list) {
                    if (list.isNotEmpty()) vm.open(Screen.FlyerBrowse(chain.name))
                }
            }
            if (flyers.isNotEmpty()) {
                item {
                    TextButton(onClick = { vm.open(Screen.Diagnostics) }) { Text("Manca qualche catena? Apri la diagnostica") }
                }
            }
        }
    }
}

@Composable
private fun ChainCard(name: String, color: Color, flyers: List<Flyer>, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(enabled = flyers.isNotEmpty(), onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.height(76.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(2).uppercase(),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val line = if (flyers.isEmpty()) "Nessun volantino letto"
                else {
                    val n = flyers.sumOf { it.itemCount }
                    val end = flyers.mapNotNull { it.end }.maxOrNull()
                    (if (flyers.size == 1) "1 volantino" else "${flyers.size} volantini") +
                        " · $n offerte" + (expiryText(end)?.let { " · $it" } ?: "")
                }
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (flyers.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (flyers.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
fun FlyerBrowseScreen(vm: AppViewModel, chain: String) {
    val ctx = LocalContext.current
    val all = remember(chain, vm.flyerVersion) { vm.flyerItems(chain) }
    var q by rememberSaveable { mutableStateOf("") }
    var onlySale by rememberSaveable { mutableStateOf(false) }
    val color = chainColor(chain)

    val shown = all
        .filter { q.isBlank() || TextUtil.relevant(q, it.title + " " + (it.brand ?: "")) }
        .filter { !onlySale || (it.oldPrice != null && it.oldPrice > it.price) || !it.sale.isNullOrBlank() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(chain, fontWeight = FontWeight.Bold)
                    Text(
                        "${all.size} offerte" + (expiryText(all.mapNotNull { it.end }.maxOrNull())?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = color.copy(alpha = 0.10f))
        )
        TextField(
            value = q,
            onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Cerca nel volantino") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        Row(Modifier.padding(horizontal = 16.dp)) {
            FilterChip(selected = onlySale, onClick = { onlySale = !onlySale }, label = { Text("Solo con sconto") })
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        "Nessuna offerta trovata.",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(shown, key = { it.flyerId + it.itemId }) { item: FlyerItem ->
                GridOfferCard(
                    title = item.title,
                    store = null,
                    image = item.image,
                    price = item.price,
                    oldPrice = item.oldPrice,
                    sale = item.sale,
                    footnote = item.page?.let { "pag. $it" },
                    onClick = {
                        item.url?.let { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } }
                    },
                    onWatch = { vm.addWatch(watchQueryFor(item)) ; vm.message = "Ora osservi \"${watchQueryFor(item)}\"" }
                )
            }
        }
    }
}

/** Dal titolo del volantino ("Chilly - Detergente Intimo Pocket") una ricerca breve da osservare. */
private fun watchQueryFor(i: FlyerItem): String {
    val t = i.title.replace(Regex("\\s+-\\s+"), " ").trim()
    return t.split(' ').filter { it.isNotBlank() }.take(3).joinToString(" ")
}
