@file:OptIn(ExperimentalMaterial3Api::class)

package com.niko.spesaradar.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.niko.spesaradar.data.Chains
import com.niko.spesaradar.data.Source
import com.niko.spesaradar.work.RefreshWorker
import java.io.File
import java.time.Year
import java.util.Locale

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    var city by remember { mutableStateOf(s.city.replace('-', ' ')) }
    var hour by remember { mutableIntStateOf(s.hour) }
    var tick by remember { mutableIntStateOf(0) } // forza il ridisegno degli interruttori

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Impostazioni") },
            navigationIcon = {
                IconButton(onClick = {
                    s.city = city
                    vm.back()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
            }
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Section("Città")
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Usata per i volantini dei negozi vicini") }
            )
            TextButton(onClick = { s.city = city; vm.message = "Città salvata: ${s.city}" }) { Text("Salva città") }

            Section("Fonti")
            key(tick) {
                Source.entries.forEach { src ->
                    SwitchRow(
                        title = when (src) {
                            Source.PROMOQUI -> "Volantini dei negozi (PromoQui)"
                            Source.SITES -> "Siti delle catene (Carrefour, Esselunga, Conad, Coop, Lidl, Eurospin)"
                            Source.AMAZON -> "Amazon.it"
                            Source.TROVAPREZZI -> "Altri negozi online (Trovaprezzi)"
                        },
                        checked = s.sourceEnabled(src),
                        onChange = { s.setSourceEnabled(src, it); tick++ }
                    )
                }
                SwitchRow("Mostra solo le mie catene", s.onlyMyChains) { s.onlyMyChains = it; tick++ }
                Text(
                    Chains.MINE.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SwitchRow("Nascondi risultati sponsorizzati", s.hideSponsored) { s.hideSponsored = it; tick++ }

                Section("Notifiche")
                SwitchRow("Avvisami anche delle nuove offerte", s.notifyNew) { s.notifyNew = it; tick++ }
                Text(
                    "Minimo storico e prezzo sotto soglia arrivano sempre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Controllo automatico alle", Modifier.weight(1f))
                IconButton(onClick = { hour = (hour + 23) % 24 }) { Icon(Icons.Filled.Remove, "Prima") }
                Text(String.format(Locale.ITALY, "%02d:00", hour), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { hour = (hour + 1) % 24 }) { Icon(Icons.Filled.Add, "Dopo") }
            }
            if (hour != s.hour) {
                Button(onClick = {
                    s.hour = hour
                    RefreshWorker.schedule(ctx, replace = true)
                    vm.message = "Controllo spostato alle " + String.format(Locale.ITALY, "%02d:00", hour)
                }) { Text("Applica orario") }
            }

            Section("Problemi")
            OutlinedButton(onClick = { vm.open(Screen.Diagnostics) }) {
                Icon(Icons.Filled.BugReport, null)
                Spacer(Modifier.padding(4.dp))
                Text("Diagnostica delle fonti")
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "SpesaRadar 0.3 · © ${Year.now().value} nikorma — Orma Studio",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        Modifier.padding(top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun DiagnosticsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val logs = remember(vm.refreshing) { vm.logs() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Diagnostica") },
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } }
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "Qui vedi cosa ha letto l'app da ogni sito nell'ultimo controllo. Se una fonte non trova offerte, " +
                    "tocca \"Condividi pagina\" e manda il file: serve a tarare il lettore su quel sito.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.refreshAll() }, enabled = !vm.refreshing) {
                Text(if (vm.refreshing) vm.progress.ifEmpty { "Aggiornamento…" } else "Aggiorna tutto ora")
            }
            Spacer(Modifier.height(12.dp))
            if (logs.isEmpty()) {
                Text("Nessun controllo ancora eseguito.", style = MaterialTheme.typography.bodyMedium)
            }
            logs.forEach { l ->
                val srcLabel = Source.labelOf(l.source)
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(srcLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(fmtTime(l.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(
                            "${l.count} offerte lette · metodo: ${l.method ?: "—"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        l.error?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        l.url?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 2)
                        }
                        l.htmlFile?.let { name ->
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            TextButton(onClick = { shareHtml(ctx, name, srcLabel) }) {
                                Icon(Icons.Filled.Share, null)
                                Spacer(Modifier.padding(4.dp))
                                Text("Condividi pagina")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareHtml(ctx: android.content.Context, name: String, label: String) {
    val f = File(File(ctx.cacheDir, "diagnostica"), name)
    if (!f.exists()) return
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "SpesaRadar - pagina $label")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Condividi pagina $label"))
}
