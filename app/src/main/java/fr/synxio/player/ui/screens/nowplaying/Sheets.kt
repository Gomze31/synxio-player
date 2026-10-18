package fr.synxio.player.ui.screens.nowplaying

import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.SheetHandle
import fr.synxio.player.ui.components.pressBounce
import fr.synxio.player.ui.components.shimmer
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.glass
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.DragHandle
import fr.synxio.player.ui.components.reorderHandle
import fr.synxio.player.ui.components.reorderItem
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.synxio.player.core.util.RingtoneHelper
import fr.synxio.player.core.util.asDuration
import fr.synxio.player.data.model.LyricLine
import fr.synxio.player.data.model.Lyrics
import fr.synxio.player.data.model.Song
import fr.synxio.player.playback.SleepTimerState
import fr.synxio.player.ui.components.Artwork
import fr.synxio.player.ui.components.SongRow
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.LyricsTextStyle
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Radius
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Style commun des chiffres qui bougent (durées, vitesses) : chasse fixe, pas de tremblement. */
private const val TabularNums = "tnum"

/** Titre de feuille : `headlineSmall` en Bricolage, annoncé comme en-tête par TalkBack. */
@Composable
private fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Paroles synchronisées. La ligne active grossit et s'éclaire ; toucher une ligne
 * déplace la lecture à cet instant, ce qui rend l'écran utile et pas seulement joli.
 */
@Composable
fun LyricsPane(
    lyrics: Lyrics,
    loading: Boolean,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    offsetMs: Long = 0L,
    offsetLabel: String = "",
    emptyMessage: String = "Aucune parole trouvée pour ce titre.",
    isTranslating: Boolean = false,
    targetLanguage: String? = null,
    onTranslate: (String) -> Unit = {},
    onRevertTranslation: () -> Unit = {},
    onNudgeOffset: (Long) -> Unit = {},
    onResetOffset: () -> Unit = {},
) {
    if (loading) {
        // Squelette de lignes plutôt qu'un rond qui tourne : on voit que du texte arrive.
        Column(modifier.padding(vertical = 120.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            listOf(0.82f, 0.64f, 0.9f, 0.5f, 0.74f).forEach { width ->
                Box(
                    Modifier
                        .fillMaxWidth(width)
                        .height(22.dp)
                        .clip(SynxioShape.pill)
                        .shimmer()
                )
            }
        }
        return
    }

    if (lyrics.lines.isEmpty()) {
        Box(modifier, Alignment.Center) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.xxl),
            )
        }
        return
    }

    val reduceMotion = LocalReduceMotion.current
    val listState = rememberLazyListState()
    // Le décalage déplace la *lecture des paroles*, pas la lecture audio : on interroge
    // la timeline des paroles à une position décalée plutôt que de réécrire les temps.
    val adjustedPosition = positionMs + offsetMs
    val activeIndex = remember(adjustedPosition, lyrics) { lyrics.indexAt(adjustedPosition) }

    LaunchedEffect(activeIndex, reduceMotion) {
        if (activeIndex >= 0) {
            // On garde la ligne active à un tiers de l'écran : on lit ce qui arrive.
            // Animations réduites : on saute directement à la ligne, sans glisser.
            if (reduceMotion) listState.scrollToItem(activeIndex, scrollOffset = -220)
            else listState.animateScrollToItem(activeIndex, scrollOffset = -220)
        }
    }

    Column(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                LyricRow(
                    line = line,
                    active = index == activeIndex,
                    synced = lyrics.synced,
                    // Symétrique du décalage : on retire ce qu'on a ajouté pour retomber
                    // sur l'instant réel du morceau.
                    onClick = { line.timeMs?.let { onSeek(it - offsetMs) } },
                )
            }
        }

        // Le réglage n'a de sens que sur des paroles horodatées.
        if (lyrics.synced) {
            LyricsOffsetBar(
                label = offsetLabel,
                aligned = offsetMs == 0L,
                onNudge = onNudgeOffset,
                onReset = onResetOffset,
            )
        }

        // Traduction : un seul bouton, dont le rôle dépend de l'état.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touchTarget)
                .padding(horizontal = Space.sm, vertical = Space.xs),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isTranslating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Space.sm))
                Text("Traduction en cours…", style = MaterialTheme.typography.labelLarge)
            } else if (targetLanguage != null) {
                TextButton(onClick = onRevertTranslation) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text("Version originale")
                }
            } else {
                TextButton(onClick = { onTranslate(com.google.mlkit.nl.translate.TranslateLanguage.FRENCH) }) {
                    Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text("Traduire en français")
                }
            }
        }
    }
}

/**
 * Réglage fin de la synchronisation des paroles.
 *
 * Les fichiers .lrc trouvés en ligne sont souvent calés sur une autre édition du
 * morceau — une intro plus longue suffit à décaler tout le texte. Un pas de 500 ms
 * permet de rattraper ça à l'oreille, en écoutant, sans quitter l'écran.
 */
@Composable
private fun LyricsOffsetBar(
    label: String,
    aligned: Boolean,
    onNudge: (Long) -> Unit,
    onReset: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = { onNudge(-500L) }) { Text("−0,5 s") }

        // Largeur fixe : sans elle, passer de « synchro » à « +2,5 s » décale les deux
        // boutons, et le toucher suivant tombe à côté de celui qu'on visait.
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularNums),
            color = if (aligned) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(88.dp),
        )

        TextButton(onClick = { onNudge(500L) }) { Text("+0,5 s") }

        // Bouton explicite : le libellé lui-même était cliquable pour réinitialiser,
        // sans rien qui l'indique — on remettait le décalage à zéro en croyant lire.
        if (!aligned) {
            IconButton(onClick = onReset) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Réinitialiser le décalage")
            }
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, synced: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val color by animateColorAsState(
        targetValue = when {
            !synced -> scheme.onSurface
            active -> scheme.primary
            else -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        animationSpec = Motion.standardOrSnap(reduceMotion),
        label = "lyricColor",
    )
    // Animations réduites : la ligne active ne grossit plus, seule la couleur la distingue.
    // L'échelle passe par graphicsLayer : changer la taille de police relançait la mise
    // en page de la ligne à chaque image de l'animation.
    val scale by animateFloatAsState(
        targetValue = if (active || reduceMotion) 1f else 0.9f,
        animationSpec = if (reduceMotion) snap() else Motion.spatial(),
        label = "lyricScale",
    )
    val brand = SynxioTheme.colors.brandBrushHorizontal

    Text(
        text = line.text.ifBlank { "♪" },
        // La ligne chantée prend la signature Lumen, comme éclairée par un projecteur.
        style = if (active && synced) LyricsTextStyle.copy(brush = brand) else LyricsTextStyle,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clickable(
                enabled = synced,
                onClickLabel = "Lire à partir de cette ligne",
                onClick = onClick,
            )
            .padding(horizontal = Space.sm),
    )
}

/**
 * File d'attente. Chaque ligne porte un bouton « Plus » (48 dp) qui ouvre Monter,
 * Descendre et Retirer : des cibles franches et accessibles au lecteur d'écran, là
 * où deux flèches de 28 dp empilées se touchaient mutuellement.
 */
@Composable
fun QueueSheet(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    // Clés stables malgré les doublons (un même titre peut être deux fois dans la file) :
    // « id#rang d'apparition ». Une clé indexée changerait à chaque déplacement et
    // empêcherait `animateItem` de suivre la ligne.
    val keys = remember(queue) {
        val seen = HashMap<Long, Int>()
        queue.map { song ->
            val n = seen.getOrDefault(song.id, 0)
            seen[song.id] = n + 1
            "${song.id}#$n"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { SheetHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.xl, end = Space.md, top = Space.xs, bottom = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("À SUIVRE", style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
                    SheetTitle("File d'attente")
                    Text(
                        text = "${queue.size} titre" + if (queue.size > 1) "s" else "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TabularNums),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (queue.isNotEmpty()) {
                    GlassButton(text = "Vider", onClick = onClear, icon = Icons.Rounded.Close)
                }
            }

            if (queue.isEmpty()) {
                EmptyState(
                    title = "La file est vide",
                    subtitle = "Ajoute des titres depuis leur menu « Plus ».",
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    modifier = Modifier.heightIn(max = 360.dp),
                )
            }

            // Glisser-déposer par la poignée ; Monter/Descendre restent dans le menu pour
            // le lecteur d'écran, qui ne sait pas faire glisser.
            // Ouverte sur le titre en cours, pas sur le début d'une file de 300 titres.
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (currentIndex - 1).coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
            )
            val keyed = remember(queue, keys) { keys.zip(queue) }
            val reorder = fr.synxio.player.ui.components.rememberReorderState(
                listState = listState,
                source = keyed,
                keyOf = { it.first },
                onCommit = onMove,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 520.dp),
                contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.sm),
            ) {
                itemsIndexed(reorder.items, key = { _, item -> item.first }) { index, (key, song) ->
                    var menuOpen by remember { mutableStateOf(false) }
                    val dragged = reorder.draggingKey == key
                    // L'index affiché suit la copie locale : pendant un glissement, la ligne
                    // en cours de lecture se repère par sa clé, pas par sa position.
                    val isCurrentRow = keys.getOrNull(currentIndex) == key
                    // Balayer une ligne vers la gauche la retire de la file ; le fond rouge
                    // qui se dévoile dit ce qui va se passer avant qu'on lâche.
                    val dismiss = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                keys.indexOf(key).takeIf { it >= 0 }?.let(onRemove)
                                true
                            } else false
                        },
                    )
                    val lift by animateFloatAsState(if (dragged && !reduceMotion) 1.03f else 1f, Motion.spatial(), label = "queueLift")
                    SwipeToDismissBox(
                        state = dismiss,
                        enableDismissFromStartToEnd = false,
                        gesturesEnabled = !dragged,
                        backgroundContent = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(SynxioShape.artworkMd)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = Space.xl),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        },
                        modifier = (if (reduceMotion || dragged) {
                            Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                        } else {
                            Modifier.animateItem()
                        }).reorderItem(reorder, key),
                    ) {
                    SongRow(
                        song = song,
                        onClick = { keys.indexOf(key).takeIf { it >= 0 }?.let(onSelect) },
                        isCurrent = isCurrentRow,
                        isPlaying = isPlaying,
                        // La ligne saisie se soulève : verre dense et léger agrandissement.
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                            }
                            .then(
                                if (dragged) Modifier.glass(SynxioShape.artworkMd, strength = 1f)
                                else Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, SynxioShape.artworkMd)
                            ),
                        trailing = {
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = "Déplacer ${song.title}",
                                tint = if (dragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(Space.touchTarget)
                                    .reorderHandle(reorder, key)
                                    .padding(12.dp),
                            )
                            Box {
                                IconButton(
                                    onClick = { menuOpen = true },
                                    modifier = Modifier.size(Space.touchTarget),
                                ) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options de ${song.title}")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (index > 0) {
                                        DropdownMenuItem(
                                            text = { Text("Monter") },
                                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                                            onClick = { menuOpen = false; onMove(index, index - 1) },
                                        )
                                    }
                                    if (index < queue.lastIndex) {
                                        DropdownMenuItem(
                                            text = { Text("Descendre") },
                                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                                            onClick = { menuOpen = false; onMove(index, index + 1) },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Retirer de la file") },
                                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                                        onClick = { menuOpen = false; onRemove(index) },
                                    )
                                }
                            }
                        },
                    )
                    }
                }
            }
        }
    }
}

@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onStart: (Int, Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableIntStateOf(30) }
    var finishTrack by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { SheetHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.xl, vertical = Space.sm)
        ) {
            SheetTitle("Minuterie de veille")
            Spacer(Modifier.height(Space.xs))
            Text(
                "Le volume diminue progressivement 60 secondes avant l'arrêt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.lg))

            if (state.active) {
                Text(
                    text = "Arrêt dans ${state.remainingMs.asDuration()}" +
                        if (state.finishCurrentTrack) " (à la fin du morceau)" else "",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TabularNums),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Space.md))
                GlassButton(
                    text = "Annuler la minuterie",
                    onClick = { onCancel(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.xl))
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                listOf(15, 30, 45, 60).forEach { preset ->
                    LumenChip(
                        label = "$preset min",
                        selected = minutes == preset,
                        onClick = { minutes = preset },
                    )
                }
            }

            Spacer(Modifier.height(Space.md))
            Text(
                "$minutes min",
                style = MaterialTheme.typography.displaySmall.merge(fr.synxio.player.ui.theme.TabularNumbers),
            )
            Slider(
                value = minutes.toFloat(),
                onValueChange = { minutes = it.toInt() },
                valueRange = 5f..180f,
                steps = 34,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .clickable(role = Role.Switch) { finishTrack = !finishTrack },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Finir le morceau en cours",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = finishTrack, onCheckedChange = null)
            }

            Spacer(Modifier.height(Space.lg))
            BrandButton(
                text = "Démarrer",
                onClick = { onStart(minutes, finishTrack); onDismiss() },
                icon = Icons.Rounded.Bedtime,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
fun SpeedSheet(
    speed: Float,
    pitch: Float,
    onApply: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentSpeed by remember { mutableFloatStateOf(speed) }
    var currentPitch by remember { mutableFloatStateOf(pitch) }
    var linkPitch by remember { mutableStateOf(pitch == speed) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { SheetHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.xl, vertical = Space.sm)
        ) {
            SheetTitle("Vitesse et tonalité")
            Spacer(Modifier.height(Space.lg))

            Text(
                "Vitesse : ${"%.2f".format(currentSpeed)}×",
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TabularNums),
            )
            Slider(
                value = currentSpeed,
                onValueChange = {
                    currentSpeed = it
                    if (linkPitch) currentPitch = it
                },
                valueRange = 0.5f..2.5f,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .clickable(role = Role.Switch) {
                        linkPitch = !linkPitch
                        if (linkPitch) currentPitch = currentSpeed
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Lier la tonalité à la vitesse",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = linkPitch, onCheckedChange = null)
            }

            if (!linkPitch) {
                Text(
                    "Tonalité : ${"%.2f".format(currentPitch)}×",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TabularNums),
                )
                Slider(
                    value = currentPitch,
                    onValueChange = { currentPitch = it },
                    valueRange = 0.5f..2f,
                )
            }

            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
                    LumenChip(
                        label = "${preset}×",
                        selected = currentSpeed == preset,
                        onClick = {
                            currentSpeed = preset
                            if (linkPitch) currentPitch = preset
                        },
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(Space.sm)) {
                GlassButton(
                    text = "Réinitialiser",
                    onClick = { onApply(1f, 1f); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
                BrandButton(
                    text = "Appliquer",
                    onClick = { onApply(currentSpeed, currentPitch); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

/** Raccourci de la grille du menu du lecteur. */
private class QuickAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
fun NowPlayingMenuSheet(
    song: Song,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onEditTags: () -> Unit,
    onRefreshLyrics: () -> Unit,
    onShare: () -> Unit,
    onOpenDriveMode: () -> Unit,
    onOpenPartyMode: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowSpeed: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleKaraoke: () -> Unit,
    onToggleAbLoop: () -> Unit,
    onDelete: () -> Unit,
    onTrim: () -> Unit,
    onDismiss: () -> Unit,
) {
    // `skipPartiallyExpanded` : sans lui la feuille s'ouvre à mi-hauteur et le contenu
    // qui dépasse est dessiné hors de ses limites, sous la barre de navigation système
    // qui intercepte alors les touchers. Le menu paraissait fonctionner — la feuille se
    // fermait au toucher du scrim — sans jamais déclencher l'action choisie.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    // Sonnerie sans l'autorisation « Modifier les paramètres système » : plutôt qu'un
    // Toast éphémère lancé pendant qu'on part vers les réglages, on explique dans la
    // feuille et on laisse l'utilisateur choisir d'y aller.
    var ringtoneNeedsPermission by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Complété par le décalage de la barre de navigation, que la feuille ne gère pas.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Space.md)
        ) {
            Row(
                Modifier.padding(horizontal = Space.xl, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song.artworkUri, Modifier.size(52.dp), SynxioShape.artworkSm)
                Spacer(Modifier.width(Space.lg))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "${song.extension} · ~${song.approxBitrateKbps} kb/s",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TabularNums),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(Space.sm))

            // Grille de raccourcis : les réglages d'écoute qu'on touche le plus.
            val actions = listOf(
                QuickAction(Icons.AutoMirrored.Rounded.QueueMusic, "File") { onShowQueue(); onDismiss() },
                QuickAction(Icons.Rounded.Lyrics, "Paroles") { onShowLyrics(); onDismiss() },
                QuickAction(Icons.Rounded.Bedtime, "Minuteur") { onShowSleepTimer(); onDismiss() },
                QuickAction(Icons.Rounded.Speed, "Vitesse") { onShowSpeed(); onDismiss() },
                QuickAction(Icons.Rounded.Equalizer, "Égaliseur") { onOpenEqualizer(); onDismiss() },
                QuickAction(Icons.Rounded.Mic, "Karaoké") { onToggleKaraoke(); onDismiss() },
                QuickAction(Icons.Rounded.Repeat, "Boucle A-B") { onToggleAbLoop(); onDismiss() },
                QuickAction(Icons.Rounded.DirectionsCar, "Voiture") { onOpenDriveMode(); onDismiss() },
            )

            Column(Modifier.fillMaxWidth().padding(horizontal = Space.md)) {
                actions.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { action ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .pressBounce(0.92f)
                                    .clip(RoundedCornerShape(Radius.md))
                                    .clickable(role = Role.Button, onClick = action.onClick)
                                    .padding(vertical = Space.sm, horizontal = Space.xs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .glass(RoundedCornerShape(Radius.sm), strength = 0.9f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(action.icon, contentDescription = null, tint = scheme.primary)
                                }
                                Spacer(Modifier.height(Space.xs))
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.sm))

            MenuRow(Icons.Rounded.Album, "Aller à l'album") { onOpenAlbum(); onDismiss() }
            MenuRow(Icons.Rounded.Person, "Aller à l'artiste") { onOpenArtist(); onDismiss() }
            MenuRow(Icons.Rounded.Edit, "Modifier les tags") { onEditTags(); onDismiss() }
            MenuRow(Icons.Rounded.Share, "Partager une carte") { onShare(); onDismiss() }
            MenuRow(Icons.Rounded.RingVolume, "Définir comme sonnerie") {
                if (RingtoneHelper.hasWriteSettingsPermission(context)) {
                    scope.launch(Dispatchers.IO) { RingtoneHelper.setAsRingtone(context, song) }
                    onDismiss()
                } else {
                    ringtoneNeedsPermission = true
                }
            }
            if (ringtoneNeedsPermission) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg, vertical = Space.xs)
                        .clip(SynxioShape.card)
                        .background(scheme.surfaceContainerHigh)
                        .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.xs),
                ) {
                    Text(
                        text = "Pour définir une sonnerie, autorise Synxio à modifier les paramètres système.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = { ringtoneNeedsPermission = false }) { Text("Plus tard") }
                        TextButton(onClick = {
                            context.startActivity(RingtoneHelper.getWriteSettingsIntent(context))
                            onDismiss()
                        }) { Text("Ouvrir les réglages") }
                    }
                }
            }

            Spacer(Modifier.height(Space.sm))

            MenuRow(Icons.Rounded.ContentCut, "Découper la piste") { onTrim(); onDismiss() }
            MenuRow(Icons.Rounded.Delete, "Supprimer le fichier", destructive = true) {
                // Android 11+ affiche sa propre confirmation (corbeille système). Avant,
                // la suppression est directe et définitive : on confirme nous-mêmes.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    onDelete(); onDismiss()
                } else {
                    confirmDelete = true
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce titre ?") },
            text = { Text("« ${song.title} » sera définitivement supprimé de l'appareil.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                    onDismiss()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

/** Ligne d'action de feuille : 56 dp, icône + libellé, `error` si destructrice. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .pressBounce(0.98f)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(SynxioShape.cardSm)
                .background(
                    if (destructive) scheme.errorContainer.copy(alpha = 0.6f)
                    else scheme.primary.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) scheme.error else scheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) scheme.error else scheme.onSurface,
        )
    }
}
