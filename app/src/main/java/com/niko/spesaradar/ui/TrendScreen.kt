@file:OptIn(ExperimentalMaterial3Api::class)

package com.niko.spesaradar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.niko.spesaradar.data.Source
import com.niko.spesaradar.data.TrendPoint
import com.niko.spesaradar.data.Units
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TrendFilter(val label: String, val sources: Set<Source>?) {
    ALL("Tutte le fonti", null),
    SHOPS("Volantini", setOf(Source.PROMOQUI)),
    ONLINE("Online", setOf(Source.SITES, Source.AMAZON, Source.TROVAPREZZI))
}

@Composable
fun TrendScreen(vm: AppViewModel, watchId: Long, offerKey: String?) {
    val watch = vm.watch(watchId)
    val offers = remember(watchId) { vm.offers(watchId) }
    val offer = offerKey?.let { k -> offers.firstOrNull { it.key == k } }
    val unit = AppViewModel.mainUnit(offers)
    var tf by remember { mutableStateOf(TrendFilter.ALL) }

    val points: List<TrendPoint> = remember(watchId, offerKey, tf, unit) {
        when {
            offerKey != null -> vm.offerHistory(watchId, offerKey)
            unit != null -> vm.trend(watchId, unit, tf.sources)
            else -> emptyList()
        }
    }
    val unitLabel = if (offerKey != null) "a confezione" else "€/${Units.unitLabel(unit)}"
    val fmt: (Double) -> String = { v -> if (offerKey != null) Units.euro(v) else Units.perUnit(v, unit) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (offer != null) "Storico prezzo" else "Andamento") },
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } }
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                if (offer != null) offer.title else (watch?.query?.replaceFirstChar { it.uppercase() } ?: ""),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (offer != null) "${offer.store ?: offer.source.label} · prezzo $unitLabel"
                else "Miglior prezzo $unitLabel del giorno",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (offerKey == null) {
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrendFilter.entries.forEach { f ->
                        FilterChip(selected = tf == f, onClick = { tf = f }, label = { Text(f.label) })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (points.isEmpty()) {
                Text(
                    "Nessun dato ancora. Lo storico si costruisce a ogni controllo, un giorno alla volta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                PriceChart(points, threshold = if (offerKey == null) watch?.threshold else null)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(dayLabel(points.first().day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.weight(1f))
                    Text(dayLabel(points.last().day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                if (points.size == 1) {
                    Text(
                        "C'è un solo giorno di dati: il grafico prende forma dai prossimi controlli.",
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Oggi", fmt(points.last().value), Modifier.weight(1f))
                    Metric("Media", fmt(points.map { it.value }.average()), Modifier.weight(1f))
                    Metric("Minimo", fmt(points.minOf { it.value }), Modifier.weight(1f))
                }
            }

            if (offerKey == null && unit != null) {
                Spacer(Modifier.height(20.dp))
                ThresholdBox(
                    current = watch?.threshold,
                    unit = unit,
                    onSave = { vm.setThreshold(watchId, it) }
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ThresholdBox(current: Double?, unit: String, onSave: (Double?) -> Unit) {
    var text by remember(current) { mutableStateOf(current?.let { String.format(Locale.ITALY, "%.2f", it) } ?: "") }
    Text("Avvisami sotto", style = MaterialTheme.typography.titleSmall)
    Text(
        "Ricevi una notifica quando il miglior prezzo scende sotto questa cifra (€/${Units.unitLabel(unit)}).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            modifier = Modifier.weight(1f),
            singleLine = true,
            suffix = { Text("€/${Units.unitLabel(unit)}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.padding(4.dp))
        Button(onClick = { onSave(Units.toNum(text)?.takeIf { it > 0 }) }) { Text("Salva") }
    }
    if (current != null) {
        TextButton(onClick = { onSave(null) }) { Text("Togli la soglia") }
    }
}

@Composable
private fun PriceChart(points: List<TrendPoint>, threshold: Double?) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val low = Palette.Amber
    val thr = MaterialTheme.colorScheme.error
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val values = points.map { it.value }
        var minV = values.min()
        var maxV = values.max()
        if (threshold != null) {
            minV = minOf(minV, threshold)
            maxV = maxOf(maxV, threshold)
        }
        if (maxV - minV < 0.01) {
            maxV += 0.05
            minV -= 0.05
        }
        val pad = (maxV - minV) * 0.12
        minV -= pad
        maxV += pad
        val w = size.width
        val h = size.height
        fun x(i: Int) = if (points.size == 1) w / 2 else w * i / (points.size - 1).toFloat()
        fun y(v: Double) = (h - (v - minV) / (maxV - minV) * h).toFloat()

        for (k in 0..3) {
            val gy = h * k / 3f
            drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
        }
        if (threshold != null) {
            drawLine(
                thr, Offset(0f, y(threshold)), Offset(w, y(threshold)), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            )
        }
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(i), y(p.value)) else path.lineTo(x(i), y(p.value))
        }
        if (points.size > 1) drawPath(path, line, style = Stroke(width = 5f, cap = StrokeCap.Round))
        points.forEachIndexed { i, p -> drawCircle(line, radius = 5f, center = Offset(x(i), y(p.value))) }
        val iMin = values.indexOf(values.min())
        drawCircle(low, radius = 10f, center = Offset(x(iMin), y(values[iMin])))
        drawCircle(Color.White, radius = 4f, center = Offset(x(iMin), y(values[iMin])))
    }
}

private fun dayLabel(day: String): String = try {
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALY))
} catch (e: Exception) {
    day
}
