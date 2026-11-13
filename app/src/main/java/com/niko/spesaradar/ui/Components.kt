package com.niko.spesaradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.niko.spesaradar.data.Chains
import com.niko.spesaradar.data.Units
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Colore della catena (rosso Despar, blu Lidl…), grigio per i negozi sconosciuti. */
fun chainColor(store: String?): Color =
    Chains.byName(store)?.let { Color(it.color) } ?: Palette.Gray

/** Pastiglia con pallino colorato e nome del negozio. */
@Composable
fun ChainTag(store: String?, modifier: Modifier = Modifier) {
    val c = chainColor(store)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(5.dp))
        Text(
            store ?: "Negozio",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Foto del prodotto, con un'icona al posto della foto se manca. */
@Composable
fun ProductImage(url: String?, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            Placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                loading = { Placeholder() },
                error = { Placeholder() }
            )
        }
    }
}

@Composable
private fun Placeholder() {
    Icon(
        Icons.Outlined.ShoppingBasket,
        null,
        tint = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.size(28.dp)
    )
}

/** Etichetta sconto ("-33%") in ambra. */
@Composable
fun SaleBadge(price: Double, oldPrice: Double?, sale: String? = null, modifier: Modifier = Modifier) {
    val txt = sale?.takeIf { it.isNotBlank() }
        ?: oldPrice?.takeIf { it > price }?.let { "-" + Math.round((1 - price / it) * 100) + "%" }
        ?: return
    Surface(modifier, color = Palette.Amber, shape = RoundedCornerShape(8.dp)) {
        Text(
            txt,
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

/** "scade oggi", "ancora 3 giorni", "fino al 21 ott". */
fun expiryText(end: String?): String? {
    if (end == null) return null
    return try {
        val d = LocalDate.parse(end.take(10))
        when (val days = ChronoUnit.DAYS.between(LocalDate.now(), d)) {
            in Long.MIN_VALUE..-1 -> "scaduta"
            0L -> "scade oggi"
            1L -> "scade domani"
            in 2..6 -> "ancora $days giorni"
            else -> "fino al " + d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALY))
        }
    } catch (e: Exception) {
        null
    }
}

fun expiryUrgent(end: String?): Boolean = try {
    end != null && ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(end.take(10))) <= 1
} catch (e: Exception) {
    false
}

/** Prezzo grande con prezzo pieno barrato sotto. */
@Composable
fun PriceColumn(price: Double, oldPrice: Double?, big: Boolean = true) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            Units.euro(price),
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 20.sp else 17.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (oldPrice != null && oldPrice > price) {
            Text(
                Units.euro(oldPrice),
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** Scheda offerta a griglia (sfoglia volantino). */
@Composable
fun GridOfferCard(
    title: String,
    store: String?,
    image: String?,
    price: Double,
    oldPrice: Double?,
    sale: String?,
    footnote: String?,
    onClick: () -> Unit,
    onWatch: (() -> Unit)? = null
) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                ProductImage(image, Modifier.fillMaxWidth().aspectRatio(1f))
                SaleBadge(price, oldPrice, sale, Modifier.align(Alignment.TopStart).padding(6.dp))
                if (onWatch != null) {
                    Surface(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).clickable(onClick = onWatch),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Outlined.LocalOffer, "Osserva",
                            Modifier.padding(6.dp).size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Units.euro(price), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                if (oldPrice != null && oldPrice > price) {
                    Text(
                        Units.euro(oldPrice),
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
            if (store != null || footnote != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (store != null) ChainTag(store, Modifier.weight(1f, fill = false))
                    if (footnote != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            footnote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Intestazione grande delle schede principali. */
@Composable
fun BigHeader(title: String, subtitle: String?, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}
