package fr.synxio.player.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.synxio.player.data.model.DayMinutes
import fr.synxio.player.data.model.Insights
import fr.synxio.player.data.model.MonthOverMonth
import fr.synxio.player.data.model.Song
import fr.synxio.player.data.model.Streak
import fr.synxio.player.data.model.Tempo
import fr.synxio.player.data.model.TempoBand
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.SectionHeader
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le volet « Insights » de l'écran Statistiques : une année en heatmap, la série de jours,
 * l'horloge des heures, le mois face au précédent, le tempo, les premières fois.
 *
 * Tout vient d'un seul [Insights] calculé hors du thread principal ; ici on ne fait que
 * dessiner. Chaque graphique porte une description qui le résume en mots pour TalkBack.
 */
internal fun LazyListScope.insightsItems(
    insights: Insights?,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (List<Song>, Int) -> Unit,
) {
    // Premier calcul en cours : rien plutôt qu'un squelette qui clignote une demi-seconde.
    if (insights == null) return

    if (insights.isEmpty) {
        item(key = "ins-empty", contentType = "empty") {
            EmptyState(
                title = "Écoute quelques jours…",
                subtitle = "Tes séries, tes heures d'écoute et ton tempo apparaîtront ici au fil des jours.",
                icon = Icons.Rounded.Insights,
                modifier = Modifier.animateItem(),
            )
        }
        return
    }

    item(key = "ins-streak", contentType = "tiles") {
        StreakTiles(streak = insights.streak, weekMinutes = insights.weekMinutes, modifier = Modifier.animateItem())
    }

    item(key = "ins-heatmap", contentType = "chart") {
        Column(Modifier.animateItem()) {
            SectionHeader("Une année d'écoute", eyebrow = "Jour après jour")
            HeatmapCard(insights.heatmap)
        }
    }

    item(key = "ins-clock", contentType = "chart") {
        Column(Modifier.animateItem()) {
            SectionHeader("Tes heures", eyebrow = "Horloge")
            ClockCard(insights.clock, insights.peakHour)
        }
    }

    item(key = "ins-month", contentType = "chart") {
        Column(Modifier.animateItem()) {
            SectionHeader("Ce mois-ci", eyebrow = "Mois contre mois")
            MonthCard(insights.monthOverMonth)
        }
    }

    item(key = "ins-tempo", contentType = "chart") {
        Column(Modifier.animateItem()) {
            SectionHeader("Ton tempo", eyebrow = "30 derniers jours")
            TempoCard(insights.tempo)
        }
    }

    if (insights.firsts.isNotEmpty()) {
        item(key = "ins-firsts-title", contentType = "header") {
            SectionHeader("Premières fois", eyebrow = "Découvertes qui ont pris", modifier = Modifier.animateItem())
        }
        val songs = insights.firsts.map { it.song }
        itemsIndexed(insights.firsts, key = { _, it -> "first-${it.song.id}" }, contentType = { _, _ -> "song" }) { index, first ->
            SongRow(
                song = first.song,
                onClick = { onPlaySong(songs, index) },
                isCurrent = first.song.id == currentSongId,
                isPlaying = first.song.id == currentSongId && isPlaying,
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = Space.sm),
                trailing = {
                    Text(
                        text = "${first.playCount} écoutes",
                        style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Série et semaine
// ---------------------------------------------------------------------------------------

/** Deux tuiles côte à côte : la série de jours (et son record), la semaine en cours. */
@Composable
private fun StreakTiles(streak: Streak, weekMinutes: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        val streakValue = if (streak.current > 0) "${streak.current}" else "—"
        val streakLabel = when {
            streak.current > 0 -> "jour${plural(streak.current)} d'affilée"
            else -> "pas de série en cours"
        }
        val record = when {
            streak.best > streak.current -> "record ${streak.best}"
            streak.current > 0 -> "ton record"
            else -> "5 min par jour suffisent"
        }
        InsightTile(
            value = streakValue,
            label = streakLabel,
            footnote = record,
            description = if (streak.current > 0) "${streak.current} jours d'affilée, record ${streak.best}" else "Pas de série en cours. $record.",
            modifier = Modifier.weight(1f),
        )
        InsightTile(
            value = formatMinutes(weekMinutes),
            label = "cette semaine",
            footnote = "7 derniers jours",
            description = "${formatMinutes(weekMinutes)} d'écoute cette semaine",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InsightTile(value: String, label: String, footnote: String, description: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = footnote,
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Heatmap
// ---------------------------------------------------------------------------------------

private const val HEATMAP_COLUMNS = 53
private const val HEATMAP_ROWS = 7

/**
 * 53 semaines × 7 jours, du lundi en haut au dimanche en bas, la dernière colonne se
 * terminant aujourd'hui. Cinq paliers de la couleur d'accent : vide, puis quarts du jour le
 * plus écouté. Toucher une case affiche « 12 mars · 48 min » sous la grille ; au départ,
 * la case du jour.
 */
@Composable
private fun HeatmapCard(heatmap: List<DayMinutes>) {
    val max = remember(heatmap) { heatmap.maxOfOrNull { it.minutes } ?: 0 }
    val best = remember(heatmap) { heatmap.maxByOrNull { it.minutes } }
    val total = remember(heatmap) { heatmap.sumOf { it.minutes } }
    // Décalage de la première case pour que chaque colonne commence un lundi.
    val offset = remember(heatmap) { heatmap.firstOrNull()?.date?.dayOfWeek?.value?.minus(1) ?: 0 }
    var selected by remember(heatmap) { mutableStateOf(heatmap.lastOrNull()) }

    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val empty = scheme.onSurface.copy(alpha = 0.08f)
    val outline = scheme.onSurface
    val labelColor = scheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    val description = buildString {
        append("${formatMinutes(total)} d'écoute sur l'année.")
        if (best != null && best.minutes > 0) append(" Jour le plus écouté : ${formatDay(best.date)}, ${formatMinutes(best.minutes)}.")
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column {
            val gap = 2.dp
            val labelHeight = 16.dp
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    // Hauteur dérivée de la largeur : 7 lignes de cases carrées + les repères de mois.
                    .heatmapHeight(gap, labelHeight)
                    .pointerInput(heatmap, offset) {
                        detectTapGestures { tap ->
                            val gapPx = gap.toPx()
                            val cell = (size.width - gapPx * (HEATMAP_COLUMNS - 1)) / HEATMAP_COLUMNS
                            val top = labelHeight.toPx()
                            val column = (tap.x / (cell + gapPx)).toInt()
                            val row = ((tap.y - top) / (cell + gapPx)).toInt()
                            if (column !in 0 until HEATMAP_COLUMNS || row !in 0 until HEATMAP_ROWS) return@detectTapGestures
                            val index = column * HEATMAP_ROWS + row - offset
                            heatmap.getOrNull(index)?.let { selected = it }
                        }
                    },
            ) {
                val gapPx = gap.toPx()
                val cell = (size.width - gapPx * (HEATMAP_COLUMNS - 1)) / HEATMAP_COLUMNS
                val top = labelHeight.toPx()
                val radius = CornerRadius(cell * 0.25f)

                var lastMonth: Month? = null
                heatmap.forEachIndexed { i, day ->
                    val gridIndex = i + offset
                    val column = gridIndex / HEATMAP_ROWS
                    val row = gridIndex % HEATMAP_ROWS
                    val x = column * (cell + gapPx)
                    val y = top + row * (cell + gapPx)
                    val level = heatLevel(day.minutes, max)
                    val color = if (level == 0) empty else accent.copy(alpha = 0.25f + 0.25f * level)
                    drawRoundRect(color, Offset(x, y), Size(cell, cell), radius)
                    if (day == selected) {
                        drawRoundRect(outline, Offset(x, y), Size(cell, cell), radius, style = Stroke(width = 1.5.dp.toPx()))
                    }
                    // Repère du mois au-dessus de la colonne où il commence (sauf tout à gauche, tronqué).
                    if (day.date.month != lastMonth) {
                        if (lastMonth != null && column > 0) {
                            val label = measurer.measure(shortMonth(day.date.month), labelStyle)
                            drawText(label, topLeft = Offset(x, 0f))
                        }
                        lastMonth = day.date.month
                    }
                }
            }
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selected?.let { "${formatDay(it.date)} · ${formatMinutes(it.minutes)}" } ?: "",
                    style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HeatLegend(empty = empty, accent = accent)
            }
        }
    }
}

/** Les cinq paliers, du vide au plein, avec « moins » et « plus » aux extrémités. */
@Composable
private fun HeatLegend(empty: Color, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text("moins", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.xxs))
        (0..4).forEach { level ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(SynxioShape.cardSm)
                    .background(if (level == 0) empty else accent.copy(alpha = 0.25f + 0.25f * level)),
            )
        }
        Spacer(Modifier.width(Space.xxs))
        Text("plus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 0 pour un jour vide, puis 1 à 4 par quarts du maximum. */
private fun heatLevel(minutes: Int, max: Int): Int {
    if (minutes <= 0 || max <= 0) return 0
    return ((minutes * 4 + max - 1) / max).coerceIn(1, 4)
}

/** Hauteur = largeur / 53 × 7 (cases carrées) + repères : calculée en mesure, pas en composition. */
private fun Modifier.heatmapHeight(gap: Dp, labelHeight: Dp): Modifier = layout { measurable, constraints ->
    val gapPx = gap.roundToPx()
    val cell = (constraints.maxWidth - gapPx * (HEATMAP_COLUMNS - 1)) / HEATMAP_COLUMNS.toFloat()
    val height = (labelHeight.roundToPx() + cell * HEATMAP_ROWS + gapPx * (HEATMAP_ROWS - 1)).roundToInt()
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/** « Janv. », « Févr. » → « Janv », « Févr » : court, sans point, pour tenir au-dessus d'une colonne. */
private fun shortMonth(month: Month): String =
    month.getDisplayName(DateTextStyle.SHORT, Locale.FRENCH).trimEnd('.').replaceFirstChar { it.uppercase() }

// ---------------------------------------------------------------------------------------
// Horloge polaire
// ---------------------------------------------------------------------------------------

/**
 * Horloge de 24 h : minuit en haut, midi en bas, un rayon par heure dont la longueur suit les
 * minutes écoutées. L'heure de pointe porte la signature ; les autres l'accent atténué.
 */
@Composable
private fun ClockCard(clock: IntArray, peakHour: Int?) {
    val max = remember(clock) { clock.maxOrNull() ?: 0 }
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    val brand = remember(colors.brandStart, colors.brandEnd) {
        Brush.sweepGradient(listOf(colors.brandStart, colors.brandEnd, colors.brandStart))
    }
    val track = scheme.onSurface.copy(alpha = 0.08f)
    val accent = scheme.primary.copy(alpha = 0.55f)
    val labelColor = scheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    val moment = peakHour?.let(::dayPart)
    val description = if (peakHour != null) {
        "Heure de pointe : $peakHour h, ${formatMinutes(clock[peakHour])}. Tu écoutes surtout $moment."
    } else {
        "Pas encore d'écoute."
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(172.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val outer = size.minDimension / 2 - 14.dp.toPx()
                val inner = outer * 0.3f
                // Le cadran : deux cercles fins.
                drawCircle(track, radius = outer, center = center, style = Stroke(width = 1.dp.toPx()))
                drawCircle(track, radius = inner, center = center, style = Stroke(width = 1.dp.toPx()))
                val sweep = 360f / 24
                for (hour in 0 until 24) {
                    val minutes = clock[hour]
                    if (minutes <= 0 || max <= 0) continue
                    val length = (outer - inner) * (minutes.toFloat() / max)
                    val radius = inner + length / 2
                    val style = Stroke(width = length)
                    val start = -90f + hour * sweep + 1.5f
                    val topLeft = Offset(center.x - radius, center.y - radius)
                    val arcSize = Size(radius * 2, radius * 2)
                    if (hour == peakHour) drawArc(brand, start, sweep - 3f, false, topLeft, arcSize, style = style)
                    else drawArc(accent, start, sweep - 3f, false, topLeft, arcSize, style = style)
                }
                // Repères : 0 h en haut, 6 h à droite, 12 h en bas, 18 h à gauche.
                listOf(0, 6, 12, 18).forEach { hour ->
                    val angle = Math.toRadians((-90f + hour * sweep).toDouble())
                    val r = outer + 8.dp.toPx()
                    val text = measurer.measure("$hour h", labelStyle)
                    val x = center.x + r * cos(angle).toFloat() - text.size.width / 2
                    val y = center.y + r * sin(angle).toFloat() - text.size.height / 2
                    drawText(text, topLeft = Offset(x, y))
                }
            }
            Spacer(Modifier.width(Space.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (peakHour != null) {
                    Text(
                        text = "Pointe à $peakHour h",
                        style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "Tu écoutes surtout $moment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatMinutes(clock[peakHour]) + " à cette heure sur l'année",
                        style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                        color = scheme.primary,
                    )
                } else {
                    Text(
                        text = "Pas encore d'écoute à placer sur l'horloge.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun dayPart(hour: Int): String = when (hour) {
    in 5..11 -> "le matin"
    in 12..17 -> "l'après-midi"
    in 18..22 -> "le soir"
    else -> "la nuit"
}

// ---------------------------------------------------------------------------------------
// Mois contre mois
// ---------------------------------------------------------------------------------------

/** Deux colonnes : ce mois, le mois dernier ; puis l'écart d'écoute en une phrase. */
@Composable
private fun MonthCard(m: MonthOverMonth) {
    val scheme = MaterialTheme.colorScheme
    val thisMonth = monthName(m.month)
    val lastMonth = monthName(m.month.minusMonths(1))
    val delta = when {
        m.minutes == 0 && m.previousMinutes == 0 -> "Rien d'écouté ces deux mois-ci."
        m.previousMinutes == 0 -> "Rien d'écouté en $lastMonth : tout est nouveau."
        m.deltaMinutes > 0 -> "+${formatMinutes(m.deltaMinutes)} par rapport à $lastMonth."
        m.deltaMinutes < 0 -> "−${formatMinutes(-m.deltaMinutes)} par rapport à $lastMonth."
        else -> "Autant qu'en $lastMonth."
    }
    val description = "$thisMonth : ${formatMinutes(m.minutes)}, ${m.distinctSongs} titres" +
        (m.topArtist?.let { ", artiste numéro un $it" } ?: "") +
        ". $lastMonth : ${formatMinutes(m.previousMinutes)}, ${m.previousDistinctSongs} titres" +
        (m.previousTopArtist?.let { ", artiste numéro un $it" } ?: "") + ". $delta"

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                MonthColumn(
                    title = thisMonth.replaceFirstChar { it.uppercase() },
                    minutes = m.minutes,
                    songs = m.distinctSongs,
                    artist = m.topArtist,
                    highlight = true,
                    modifier = Modifier.weight(1f),
                )
                MonthColumn(
                    title = lastMonth.replaceFirstChar { it.uppercase() },
                    minutes = m.previousMinutes,
                    songs = m.previousDistinctSongs,
                    artist = m.previousTopArtist,
                    highlight = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = delta,
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthColumn(title: String, minutes: Int, songs: Int, artist: String?, highlight: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (highlight) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatMinutes(minutes),
            style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
            color = scheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = "$songs titre${plural(songs)}",
            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = artist ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Tempo
// ---------------------------------------------------------------------------------------

/**
 * « Tu écoutes à 118 BPM en moyenne, plus rapide que le mois dernier », l'énergie moyenne,
 * et la part du temps passée en lent / moyen / rapide.
 */
@Composable
private fun TempoCard(tempo: Tempo?) {
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors

    if (tempo == null) {
        GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter)) {
            Text(
                text = "Analyse le son de ta bibliothèque dans les Réglages pour voir ton tempo et ton énergie.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        return
    }

    val bpm = tempo.averageBpm.roundToInt()
    val comparison = tempo.previousAverageBpm?.let { previous ->
        val diff = tempo.averageBpm - previous
        when {
            abs(diff) < 2f -> "comme le mois dernier"
            diff > 0 -> "plus rapide que le mois dernier (+${diff.roundToInt()} BPM)"
            else -> "plus lent que le mois dernier (−${(-diff).roundToInt()} BPM)"
        }
    }
    val headline = "Tu écoutes à $bpm BPM en moyenne" + (comparison?.let { ", $it" } ?: "") + "."
    val energyPercent = (tempo.averageEnergy * 100).roundToInt()
    val shares = TempoBand.entries.map { it to ((tempo.distribution[it] ?: 0f) * 100).roundToInt() }
    val description = "$headline Énergie moyenne $energyPercent %. " +
        shares.joinToString(", ") { (band, pct) -> "${band.label} $pct %" } + "."

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                color = scheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Row {
                    Text("Énergie", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("$energyPercent %", style = MaterialTheme.typography.labelLarge.merge(TabularNumbers), color = scheme.onSurface)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(SynxioShape.pill)
                        .background(scheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(tempo.averageEnergy.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .background(colors.brandBrushHorizontal),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Row(Modifier.fillMaxWidth().height(10.dp).clip(SynxioShape.pill)) {
                    shares.forEach { (band, pct) ->
                        if (pct > 0) {
                            Box(
                                Modifier
                                    .weight(pct.toFloat())
                                    .fillMaxSize()
                                    .background(bandColor(band)),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    shares.forEach { (band, pct) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            Box(Modifier.size(8.dp).clip(SynxioShape.pill).background(bandColor(band)))
                            Text(
                                text = "${band.label} $pct %",
                                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun bandColor(band: TempoBand): Color {
    val accent = MaterialTheme.colorScheme.primary
    return when (band) {
        TempoBand.SLOW -> accent.copy(alpha = 0.35f)
        TempoBand.MEDIUM -> accent.copy(alpha = 0.65f)
        TempoBand.FAST -> accent
    }
}

// ---------------------------------------------------------------------------------------
// Formats
// ---------------------------------------------------------------------------------------

/** « 3 h 20 » ou « 48 min » ; « 0 min » sans écoute. */
internal fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) String.format(Locale.FRENCH, "%d h %02d", hours, rest) else "$minutes min"
}

/** « 12 mars », « 1er avril ». */
private fun formatDay(date: LocalDate): String {
    val day = if (date.dayOfMonth == 1) "1er" else "${date.dayOfMonth}"
    return "$day ${date.month.getDisplayName(DateTextStyle.FULL, Locale.FRENCH)}"
}

private fun monthName(month: YearMonth): String = month.month.getDisplayName(DateTextStyle.FULL, Locale.FRENCH)

private fun plural(count: Int): String = if (count > 1) "s" else ""
