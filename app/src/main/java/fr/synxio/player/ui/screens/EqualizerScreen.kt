package fr.synxio.player.ui.screens

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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.EqCategory
import fr.synxio.player.data.model.EqCurves
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.VisualizerView
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.EqualizerViewModel
import java.util.Locale

/**
 * Égaliseur : profils d'écoute et préréglages en puces, bandes en curseurs verticaux,
 * effets (basses, spatialisation, réverbération, gain).
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
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Égaliseur") },
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
            // Toute la ligne bascule l'égaliseur : cible large, annoncée comme interrupteur.
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .toggleable(
                        value = settings.equalizerEnabled,
                        role = Role.Switch,
                        onValueChange = viewModel::setEnabled,
                    )
                    .padding(vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Activer l'égaliseur", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${capabilities.bands.size} bandes détectées sur cet appareil",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Space.md))
                Switch(checked = settings.equalizerEnabled, onCheckedChange = null)
            }

            if (settings.equalizerEnabled && !reduceMotion) {
                Spacer(Modifier.height(Space.sm))
                VisualizerView(
                    fftBytes = fftFlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }

            // --- Association profil ↔ casque connecté ---------------------------------
            if (connectedDevice.isNotBlank()) {
                val memorised = deviceProfiles[connectedDevice]
                Spacer(Modifier.height(Space.lg))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SynxioShape.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(Space.lg)) {
                        Text(connectedDevice, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = memorised
                                ?.let { EqCurves.byId(it)?.name }
                                ?.let { "Profil mémorisé : $it — appliqué automatiquement" }
                                ?: "Aucun profil mémorisé pour ce casque",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Space.md))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Button(
                                onClick = viewModel::rememberProfileForDevice,
                                enabled = activeCurveId != null,
                                shape = SynxioShape.pill,
                            ) { Text("Mémoriser le profil actuel") }
                            if (memorised != null) {
                                TextButton(onClick = viewModel::forgetProfileForDevice) {
                                    Text("Oublier")
                                }
                            }
                        }
                    }
                }
            }

            // --- Profils d'écoute (courbes interpolées sur les bandes réelles) ---------
            EqSectionTitle("Profils d'écoute")
            EqCategory.entries.forEach { category ->
                val curves = EqCurves.byCategory(category)
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.sm, bottom = Space.xs),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    curves.forEach { curve ->
                        FilterChip(
                            selected = activeCurveId == curve.id,
                            onClick = { viewModel.applyCurve(curve) },
                            label = { Text(curve.name) },
                            shape = SynxioShape.pill,
                        )
                    }
                }
                curves.firstOrNull { it.id == activeCurveId }?.let { active ->
                    Text(
                        text = active.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }

            if (capabilities.presets.isNotEmpty()) {
                EqSectionTitle("Préréglages du constructeur")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    capabilities.presets.forEachIndexed { index, preset ->
                        FilterChip(
                            selected = settings.equalizerPreset == index,
                            onClick = { viewModel.applyPreset(index) },
                            label = { Text(preset) },
                            shape = SynxioShape.pill,
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xl),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Bandes",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                TextButton(onClick = viewModel::resetBands) { Text("Remettre à plat") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                capabilities.bands.forEachIndexed { index, band ->
                    BandSlider(
                        frequencyHz = band.centerFreqHz,
                        level = bandLevels.getOrElse(index) { band.levelMillibel },
                        minLevel = capabilities.minLevel,
                        maxLevel = capabilities.maxLevel,
                        enabled = settings.equalizerEnabled,
                        onChange = { viewModel.setBand(index, it) },
                    )
                }
            }

            EqSectionTitle("Effets")

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

            Spacer(Modifier.height(Space.xxxl))
        }
    }
}

@Composable
private fun EqSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(top = Space.xl, bottom = Space.sm)
            .semantics { heading() },
    )
}

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

/** Curseur vertical d'une bande : on fait pivoter un Slider horizontal de -90°. */
@Composable
private fun BandSlider(
    frequencyHz: Int,
    level: Short,
    minLevel: Short,
    maxLevel: Short,
    enabled: Boolean,
    onChange: (Short) -> Unit,
) {
    val value = formatDb(level.toInt())
    val frequency = formatFrequency(frequencyHz)
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )

        Box(
            modifier = Modifier
                .height(200.dp)
                .width(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            // requiredWidth ignore la contrainte du parent : sans lui, le Slider était
            // mesuré à 56 dp puis pivoté, d'où des curseurs réduits à des moignons.
            Slider(
                value = level.toFloat(),
                onValueChange = { onChange(it.toInt().toShort()) },
                valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                enabled = enabled,
                modifier = Modifier
                    .requiredWidth(200.dp)
                    .rotate(-90f)
                    .semantics {
                        contentDescription = "Bande $frequency"
                        stateDescription = value
                    },
            )
        }

        Text(
            text = frequency,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
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
    Column(Modifier.padding(vertical = Space.sm)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            steps = steps,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueText
            },
        )
    }
}
