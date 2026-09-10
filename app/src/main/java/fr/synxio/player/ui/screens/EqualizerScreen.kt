package fr.synxio.player.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.model.EqCategory
import fr.synxio.player.data.model.EqCurves
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.viewmodel.EqualizerViewModel

@Composable
fun EqualizerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: EqualizerViewModel = hiltViewModel()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val bandLevels by viewModel.bandLevels.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeCurveId by viewModel.activeCurveId.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val deviceProfiles by viewModel.deviceProfiles.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshConnectedDevice() }

    Scaffold(
        modifier = modifier,
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
            )
        },
    ) { padding ->
        if (!capabilities.available) {
            EmptyState(
                title = "Égaliseur indisponible",
                subtitle = "Lance d'abord un morceau : les effets audio ne sont créés " +
                    "qu'une fois la session de lecture ouverte. Certains appareils " +
                    "n'exposent pas d'égaliseur système.",
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
                .padding(horizontal = 20.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Activer l'égaliseur", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${capabilities.bands.size} bandes détectées sur cet appareil",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.equalizerEnabled,
                    onCheckedChange = viewModel::setEnabled,
                )
            }

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --- Association profil ↔ casque connecté ---------------------------------
            if (connectedDevice.isNotBlank()) {
                val memorised = deviceProfiles[connectedDevice]
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(connectedDevice, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = memorised
                                ?.let { EqCurves.byId(it)?.name }
                                ?.let { "Profil mémorisé : $it — appliqué automatiquement" }
                                ?: "Aucun profil mémorisé pour ce casque",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::rememberProfileForDevice,
                                enabled = activeCurveId != null,
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
            EqCategory.entries.forEach { category ->
                Text(category.label, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqCurves.byCategory(category).forEach { curve ->
                        FilterChip(
                            selected = activeCurveId == curve.id,
                            onClick = { viewModel.applyCurve(curve) },
                            label = { Text(curve.name) },
                        )
                    }
                }
                EqCurves.byCategory(category)
                    .firstOrNull { it.id == activeCurveId }
                    ?.let { active ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = active.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                Spacer(Modifier.height(18.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            if (capabilities.presets.isNotEmpty()) {
                Text("Préréglages du constructeur", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    capabilities.presets.forEachIndexed { index, preset ->
                        FilterChip(
                            selected = settings.equalizerPreset == index,
                            onClick = { viewModel.applyPreset(index) },
                            label = { Text(preset) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bandes", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = viewModel::resetBands) { Text("Remettre à plat") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Effets", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            if (capabilities.bassBoostSupported) {
                EffectSlider(
                    label = "Renforcement des basses",
                    value = settings.bassBoost,
                    max = 1000,
                    enabled = settings.equalizerEnabled,
                    onChange = viewModel::setBassBoost,
                )
            }
            if (capabilities.virtualizerSupported) {
                EffectSlider(
                    label = "Spatialisation",
                    value = settings.virtualizer,
                    max = 1000,
                    enabled = settings.equalizerEnabled,
                    onChange = viewModel::setVirtualizer,
                )
            }
            if (capabilities.loudnessSupported) {
                EffectSlider(
                    label = "Gain de volume",
                    value = settings.loudnessGain,
                    max = 2000,
                    enabled = settings.equalizerEnabled,
                    suffix = " mB",
                    onChange = viewModel::setLoudness,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

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
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${level / 100} dB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
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
                    .rotate(-90f),
            )
        }

        Text(
            text = if (frequencyHz >= 1000) "${frequencyHz / 1000}k" else "$frequencyHz",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Hz",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Int,
    max: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    suffix: String = " %",
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (suffix == " %") "${value * 100 / max}%" else "$value$suffix",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            enabled = enabled,
        )
    }
}
