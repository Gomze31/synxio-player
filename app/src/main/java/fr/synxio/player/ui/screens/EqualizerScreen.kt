package fr.synxio.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.EqCategory
import fr.synxio.player.data.model.EqCurves
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.components.VisualizerView
import fr.synxio.player.ui.components.enterCascade
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Radius
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.EqualizerViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Égaliseur : profils d'écoute et préréglages en puces, bandes en pistes verticales
 * reliées par la courbe de réponse, effets (basses, spatialisation, réverbération, gain).
 *
 * La courbe passe exactement par les poignées : on lit d'un coup d'œil la forme du son,
 * et elle se déforme en douceur quand on change de profil (les niveaux sont animés, sauf
 * la bande que le doigt tient, qui suit sans retard).
 *
 * Chaque curseur annonce sa valeur en mots (« plus 3,5 décibels ») : TalkBack lirait
 * sinon une valeur brute en millibels. Le visualiseur, animation ambiante, n'est pas
 * démarré quand les animations sont réduites.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: EqualizerViewModel = hiltViewModel()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val bandLevels by viewModel.bandLevels.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeCurveId by viewModel.activeCurveId.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val deviceProfiles by viewModel.deviceProfiles.collectAsStateWithLifecycle()
    val fftFlow by viewModel.fftFlow.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(Unit) { viewModel.refreshConnectedDevice() }

    DisposableEffect(reduceMotion) {
        viewModel.setVisualizerEnabled(!reduceMotion)
        onDispose { viewModel.setVisualizerEnabled(false) }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Égaliseur", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (!capabilities.available) {
            EmptyState(
                title = "Égaliseur indisponible",
                subtitle = "Lance d'abord un morceau : les effets audio ne sont créés " +
                    "qu'une fois la session de lecture ouverte. Certains appareils " +
                    "n'exposent pas d'égaliseur système.",
                icon = Icons.Rounded.Equalizer,
                modifier = Modifier.padding(padding),
                actionLabel = "Réessayer",
                onAction = viewModel::refresh,
            )
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
        ) {
            // --- Interrupteur + visualiseur -----------------------------------------
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm)
                    .enterCascade(0, key = "eq-toggle"),
                contentPadding = Space.xs,
            ) {
                Column {
                    // Toute la ligne bascule l'égaliseur : cible large, annoncée comme interrupteur.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Space.touchTarget)
                            .clip(SynxioShape.cardSm)
                            .toggleable(
                                value = settings.equalizerEnabled,
                                role = Role.Switch,
                                onValueChange = viewModel::setEnabled,
                            )
                            .padding(horizontal = Space.md, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EqIconTile(Icons.Rounded.Equalizer)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text("Activer l'égaliseur", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${capabilities.bands.size} bandes détectées sur cet appareil",
                                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(Space.md))
                        Switch(
                            checked = settings.equalizerEnabled,
                            onCheckedChange = null,
                            colors = eqSwitchColors(),
                        )
                    }

                    AnimatedVisibility(
                        visible = settings.equalizerEnabled && !reduceMotion,
                        enter = expandVertically(Motion.spatial()) + fadeIn(Motion.enter()),
                        exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.exit()),
                    ) {
                        VisualizerView(
                            fftBytes = fftFlow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.md)
                                .padding(bottom = Space.md)
                                .height(96.dp)
                        )
                    }
                }
            }

            // --- Bandes et courbe de réponse ------------------------------------------
            EqSectionTitle(
                eyebrow = "Courbe",
                title = "Bandes",
                actionLabel = "Remettre à plat",
                onAction = viewModel::resetBands,
            )
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .enterCascade(1, key = "eq-bands"),
                contentPadding = Space.sm,
            ) {
                BandsPanel(
                    frequencies = capabilities.bands.map { it.centerFreqHz },
                    levels = capabilities.bands.indices.map { i ->
                        bandLevels.getOrElse(i) { capabilities.bands[i].levelMillibel }
                    },
                    minLevel = capabilities.minLevel,
                    maxLevel = capabilities.maxLevel,
                    enabled = settings.equalizerEnabled,
                    onChange = viewModel::setBand,
                )
            }

            // --- Association profil ↔ casque connecté ---------------------------------
            if (connectedDevice.isNotBlank()) {
                val memorised = deviceProfiles[connectedDevice]
                EqSectionTitle(eyebrow = "Casque connecté", title = connectedDevice)
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EqIconTile(Icons.Rounded.Headphones)
                            Spacer(Modifier.width(Space.md))
                            Text(
                                text = memorised
                                    ?.let { EqCurves.byId(it)?.name }
                                    ?.let { "Profil mémorisé : $it — appliqué automatiquement" }
                                    ?: "Aucun profil mémorisé pour ce casque",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(Space.md))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            if (activeCurveId != null) {
                                GlassButton(
                                    text = "Mémoriser le profil actuel",
                                    onClick = viewModel::rememberProfileForDevice,
                                )
                            } else {
                                // Rien à mémoriser tant qu'aucun profil n'est choisi : on le dit
                                // plutôt que d'afficher un bouton grisé muet.
                                Text(
                                    "Choisis un profil d'écoute pour pouvoir le mémoriser.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (memorised != null) {
                                TextButton(
                                    onClick = viewModel::forgetProfileForDevice,
                                    modifier = Modifier.heightIn(min = Space.touchTarget),
                                ) { Text("Oublier") }
                            }
                        }
                    }
                }
            }

            // --- Profils d'écoute (courbes interpolées sur les bandes réelles) ---------
            EqSectionTitle(eyebrow = "Profils", title = "Profils d'écoute")
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .enterCascade(2, key = "eq-curves"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    EqCategory.entries.forEach { category ->
                        val curves = EqCurves.byCategory(category)
                        Column {
                            Text(
                                category.label.uppercase(),
                                style = EyebrowTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = Space.sm),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                verticalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                curves.forEach { curve ->
                                    LumenChip(
                                        label = curve.name,
                                        selected = activeCurveId == curve.id,
                                        onClick = { viewModel.applyCurve(curve) },
                                    )
                                }
                            }
                            val active = curves.firstOrNull { it.id == activeCurveId }
                            AnimatedContent(
                                targetState = active,
                                transitionSpec = {
                                    fadeIn(Motion.enter(Motion.SHORT)) togetherWith fadeOut(Motion.exit(Motion.XS))
                                },
                                label = "curveDescription",
                            ) { curve ->
                                if (curve != null) {
                                    Text(
                                        text = curve.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Space.sm),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (capabilities.presets.isNotEmpty()) {
                EqSectionTitle(eyebrow = "Appareil", title = "Préréglages du constructeur")
                GlassCard(Modifier.fillMaxWidth()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        capabilities.presets.forEachIndexed { index, preset ->
                            LumenChip(
                                label = preset,
                                selected = settings.equalizerPreset == index,
                                onClick = { viewModel.applyPreset(index) },
                            )
                        }
                    }
                }
            }

            val hasEffects = capabilities.bassBoostSupported || capabilities.virtualizerSupported ||
                capabilities.reverbSupported || capabilities.loudnessSupported
            if (hasEffects) {
                EqSectionTitle(eyebrow = "Espace", title = "Effets")
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        if (capabilities.bassBoostSupported) {
                            EffectSlider(
                                label = "Renforcement des basses",
                                value = settings.bassBoost,
                                max = 1000,
                                enabled = settings.equalizerEnabled,
                                format = { "${it * 100 / 1000} %" },
                                onChange = viewModel::setBassBoost,
                            )
                        }
                        if (capabilities.virtualizerSupported) {
                            EffectSlider(
                                label = "Spatialisation",
                                value = settings.virtualizer,
                                max = 1000,
                                enabled = settings.equalizerEnabled,
                                format = { "${it * 100 / 1000} %" },
                                onChange = viewModel::setVirtualizer,
                            )
                        }
                        if (capabilities.reverbSupported) {
                            EffectSlider(
                                label = "Réverbération (pièce → salle)",
                                value = settings.reverbPreset,
                                max = 6,
                                enabled = settings.equalizerEnabled,
                                steps = 5,
                                format = { if (it == 0) "Aucune" else "$it / 6" },
                                onChange = viewModel::setReverbPreset,
                            )
                        }
                        if (capabilities.loudnessSupported) {
                            EffectSlider(
                                label = "Gain de volume",
                                value = settings.loudnessGain,
                                max = 2000,
                                enabled = settings.equalizerEnabled,
                                format = { formatDb(it) },
                                onChange = viewModel::setLoudness,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.xxxl))
        }
    }
}

/** Titre de section : surtitre, titre et action optionnelle à droite. */
@Composable
private fun EqSectionTitle(
    eyebrow: String,
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.xl, bottom = Space.sm, start = Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Space.xxs))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, shape = SynxioShape.pill) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EqIconTile(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.sm))
            .background(scheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun eqSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

/** Millibels → « +3,5 dB » (signe explicite, une décimale si utile). */
private fun formatDb(millibel: Int): String {
    val db = millibel / 100f
    val sign = if (db > 0f) "+" else ""
    val text = if (millibel % 100 == 0) "${millibel / 100}" else String.format(Locale.FRENCH, "%.1f", db)
    return "$sign$text dB"
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) {
        val khz = hz / 1000f
        (if (hz % 1000 == 0) "${hz / 1000}" else String.format(Locale.FRENCH, "%.1f", khz)) + " kHz"
    } else "$hz Hz"

// Gabarit vertical d'une bande : fixe, pour que la courbe dessinée derrière passe
// exactement par le centre des poignées.
private val BandLabelHeight = 24.dp
private val BandTrackHeight = 208.dp
private val BandHandleSize = 22.dp
/** Au-delà, les bandes défilent à largeur fixe au lieu de se partager la largeur. */
private const val MAX_FITTED_BANDS = 7
private val ScrollingBandWidth = 52.dp

/**
 * Panneau des bandes : pistes verticales, poignées lumineuses, et la courbe de réponse
 * tracée à la signature qui relie les poignées, avec son halo et un voile dégradé
 * jusqu'à la ligne 0 dB.
 *
 * Les niveaux affichés sont animés vers les valeurs du modèle : un changement de profil
 * fait glisser toutes les poignées ensemble. La bande saisie au doigt, elle, suit sans
 * ressort, sinon elle traînerait derrière le geste.
 */
@Composable
private fun BandsPanel(
    frequencies: List<Int>,
    levels: List<Short>,
    minLevel: Short,
    maxLevel: Short,
    enabled: Boolean,
    onChange: (Int, Short) -> Unit,
) {
    val count = frequencies.size
    if (count == 0) return
    val reduce = LocalReduceMotion.current
    val glow = LocalGlowEffects.current
    val colors = SynxioTheme.colors
    val scheme = MaterialTheme.colorScheme
    val min = minLevel.toFloat()
    val max = maxLevel.toFloat()
    val span = (max - min).coerceAtLeast(1f)

    val animated = remember(count) { List(count) { i -> Animatable(levels[i].toFloat()) } }
    var dragging by remember { mutableIntStateOf(-1) }
    levels.forEachIndexed { i, level ->
        LaunchedEffect(animated, i, level) {
            val target = level.toFloat()
            if (reduce || dragging == i) animated[i].snapTo(target)
            else animated[i].animateTo(target, Motion.spatial())
        }
    }

    val enabledAlpha by animateFloatAsState(
        if (enabled) 1f else 0.45f,
        Motion.standardOrSnap(reduce, Motion.SHORT),
        label = "bandsEnabled",
    )
    val fitted = count <= MAX_FITTED_BANDS
    val rowScroll = rememberScrollState()
    val lineColor = scheme.onSurface.copy(alpha = 0.16f)
    val brandH = colors.brandBrushHorizontal
    val brandEnd = colors.brandEnd
    val brandStart = colors.brandStart

    Row(
        Modifier
            .fillMaxWidth()
            .then(if (fitted) Modifier else Modifier.horizontalScroll(rowScroll))
            .alpha(enabledAlpha)
            .drawBehind {
                val labelH = BandLabelHeight.toPx()
                val trackH = BandTrackHeight.toPx()
                val pad = BandHandleSize.toPx() / 2f
                val colW = size.width / count
                fun yOf(level: Float): Float {
                    val norm = ((level - min) / span).coerceIn(0f, 1f)
                    return labelH + pad + (1f - norm) * (trackH - 2 * pad)
                }
                val zeroY = yOf(0f.coerceIn(min, max))

                // Ligne 0 dB en pointillés : la référence « son d'origine ».
                drawLine(
                    color = lineColor,
                    start = Offset(colW * 0.25f, zeroY),
                    end = Offset(size.width - colW * 0.25f, zeroY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                )

                // Courbe lissée (Catmull-Rom → Bézier) passant par le centre des poignées.
                val pts = List(count) { i -> Offset(colW * (i + 0.5f), yOf(animated[i].value)) }
                val curve = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 0 until pts.size - 1) {
                        val p0 = pts.getOrElse(i - 1) { pts[i] }
                        val p1 = pts[i]
                        val p2 = pts[i + 1]
                        val p3 = pts.getOrElse(i + 2) { p2 }
                        cubicTo(
                            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                            p2.x, p2.y,
                        )
                    }
                }
                // Voile sous la courbe, refermé sur la ligne 0 dB.
                val area = Path().apply {
                    addPath(curve)
                    lineTo(pts.last().x, zeroY)
                    lineTo(pts.first().x, zeroY)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(
                        listOf(brandStart.copy(alpha = 0.22f), brandEnd.copy(alpha = 0.04f)),
                        startY = labelH,
                        endY = labelH + trackH,
                    ),
                )
                if (glow) {
                    drawPath(curve, brandH, alpha = 0.28f, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                }
                drawPath(curve, brandH, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            },
    ) {
        for (i in 0 until count) {
            BandColumn(
                frequencyHz = frequencies[i],
                displayLevel = animated[i].value,
                level = levels[i],
                min = min,
                max = max,
                enabled = enabled,
                onDragStart = { dragging = i },
                onDragEnd = { if (dragging == i) dragging = -1 },
                onChange = { onChange(i, it) },
                modifier = if (fitted) Modifier.weight(1f) else Modifier.width(ScrollingBandWidth),
            )
        }
    }
}

/**
 * Une bande : valeur en haut, piste verticale (remplissage à la signature depuis 0 dB
 * jusqu'au niveau, poignée lumineuse), fréquence en bas.
 *
 * Dessinée à la main plutôt qu'avec un Slider pivoté : la piste reste parfaitement alignée
 * sur la courbe, et le toucher n'importe où sur la colonne place directement le niveau.
 * Accessibilité : la colonne est un curseur réglable (`setProgress`) qui annonce la
 * valeur en décibels.
 */
@Composable
private fun BandColumn(
    frequencyHz: Int,
    displayLevel: Float,
    level: Short,
    min: Float,
    max: Float,
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onChange: (Short) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SynxioTheme.colors
    val scheme = MaterialTheme.colorScheme
    val glow = LocalGlowEffects.current
    val value = formatDb(level.toInt())
    val frequency = formatFrequency(frequencyHz)
    val span = (max - min).coerceAtLeast(1f)
    val currentOnChange by rememberUpdatedState(onChange)
    val trackColor = scheme.onSurface.copy(alpha = 0.10f)
    val handleFill = scheme.surfaceContainerLowest
    val brandV = Brush.verticalGradient(listOf(colors.brandStart, colors.brandEnd))
    val brandEnd = colors.brandEnd

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(BandLabelHeight), contentAlignment = Alignment.Center) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
                color = scheme.primary,
                maxLines = 1,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(BandTrackHeight)
                .semantics {
                    contentDescription = "Bande $frequency"
                    stateDescription = value
                    progressBarRangeInfo = ProgressBarRangeInfo(level.toFloat(), min..max)
                    if (enabled) {
                        setProgress { target ->
                            currentOnChange(target.coerceIn(min, max).roundToInt().toShort())
                            true
                        }
                    } else {
                        disabled()
                    }
                }
                .then(
                    if (!enabled) Modifier else Modifier
                        .pointerInput(min, max) {
                            val pad = BandHandleSize.toPx() / 2f
                            fun levelAt(y: Float): Short {
                                val norm = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                                return (min + norm * span).roundToInt().toShort()
                            }
                            detectTapGestures { currentOnChange(levelAt(it.y)) }
                        }
                        .pointerInput(min, max) {
                            val pad = BandHandleSize.toPx() / 2f
                            fun levelAt(y: Float): Short {
                                val norm = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                                return (min + norm * span).roundToInt().toShort()
                            }
                            detectVerticalDragGestures(
                                onDragStart = { onDragStart(); currentOnChange(levelAt(it.y)) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                            ) { change, _ ->
                                change.consume()
                                currentOnChange(levelAt(change.position.y))
                            }
                        }
                )
                .drawBehind {
                    val pad = BandHandleSize.toPx() / 2f
                    val trackW = 6.dp.toPx()
                    val cx = size.width / 2f
                    fun yOf(l: Float): Float {
                        val norm = ((l - min) / span).coerceIn(0f, 1f)
                        return pad + (1f - norm) * (size.height - 2 * pad)
                    }
                    val r = CornerRadius(trackW / 2f, trackW / 2f)
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(cx - trackW / 2f, pad),
                        size = Size(trackW, size.height - 2 * pad),
                        cornerRadius = r,
                    )
                    val y = yOf(displayLevel)
                    val zero = yOf(0f.coerceIn(min, max))
                    val top = minOf(y, zero)
                    val h = kotlin.math.abs(zero - y)
                    if (h > 0.5f) {
                        drawRoundRect(
                            brush = brandV,
                            topLeft = Offset(cx - trackW / 2f, top),
                            size = Size(trackW, h),
                            cornerRadius = r,
                        )
                    }
                    val handleR = BandHandleSize.toPx() / 2f
                    if (glow) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(brandEnd.copy(alpha = 0.45f), Color.Transparent),
                                center = Offset(cx, y),
                                radius = handleR * 2.4f,
                            ),
                            radius = handleR * 2.4f,
                            center = Offset(cx, y),
                        )
                    }
                    drawCircle(handleFill, radius = handleR, center = Offset(cx, y))
                    drawCircle(
                        brandV,
                        radius = handleR - 1.5.dp.toPx(),
                        center = Offset(cx, y),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
        )

        Box(Modifier.height(BandLabelHeight), contentAlignment = Alignment.Center) {
            Text(
                text = frequency,
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Int,
    max: Int,
    enabled: Boolean,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
    steps: Int = 0,
) {
    val valueText = format(value)
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = Space.xs)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Space.md))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                color = scheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.onSurface.copy(alpha = 0.12f),
                activeTickColor = scheme.onPrimary.copy(alpha = 0.6f),
                inactiveTickColor = scheme.onSurface.copy(alpha = 0.3f),
            ),
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueText
            },
        )
    }
}
