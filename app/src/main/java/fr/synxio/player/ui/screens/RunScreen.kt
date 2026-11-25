package fr.synxio.player.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import fr.synxio.player.data.model.TextSize
import fr.synxio.player.data.model.ThemeMode
import fr.synxio.player.playback.RunMode
import fr.synxio.player.playback.RunState
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.LumenChip
import fr.synxio.player.ui.haptics.LocalSynxioHaptics
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalArtworkColors
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.ThemePreset
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import java.util.Locale
import kotlin.math.roundToInt

/** Cibles proposées d'un geste, en pas par minute. */
private val RunPresets = listOf(150f, 160f, 170f, 180f)

/**
 * Course au tempo : la cadence mesurée en grand, la cible, le titre en cours et le suivant
 * choisi à ce tempo. Même gabarit que le mode voiture : plein écran, gros caractères,
 * écran allumé, thème sombre forcé.
 *
 * La permission `ACTIVITY_RECOGNITION` est demandée ici seulement, à l'ouverture. Refusée,
 * la cadence ne se mesure pas et la cible se choisit à la main (mode « Verrouillé »).
 * Le bouton Retour laisse la course tourner ; « Arrêter » la termine.
 */
@Composable
fun RunScreen(
    viewModel: AppViewModel,
    onExit: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    SynxioTheme(
        themeMode = ThemeMode.DARK,
        accentSource = settings.accentSource,
        artworkColors = LocalArtworkColors.current,
        themePreset = ThemePreset.fromName(settings.themePreset),
        customAccent = settings.primaryColor,
        reduceAnimations = settings.reduceAnimations,
        glowEffects = settings.glowEffects,
        textScale = TextSize.entries.firstOrNull { it.name == settings.textSize }?.scale ?: 1f,
    ) {
        RunContent(viewModel, onExit)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
private fun RunContent(viewModel: AppViewModel, onExit: () -> Unit) {
    val haptics = LocalSynxioHaptics.current
    val scheme = MaterialTheme.colorScheme
    val state by viewModel.runState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    // Avant Android 10 le podomètre ne demande rien.
    val permission: PermissionState? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) rememberPermissionState(Manifest.permission.ACTIVITY_RECOGNITION) else null
    val granted = permission?.status?.isGranted ?: true
    var asked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.startRun(sensorAllowed = granted)
        if (!granted && !asked) {
            asked = true
            permission?.launchPermissionRequest()
        }
    }
    LaunchedEffect(granted) { viewModel.setRunSensorAllowed(granted) }

    Scaffold(
        containerColor = Color.Transparent,
        // Même voile que le mode voiture : le texte ne lutte pas contre les halos de l'aurore.
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background.copy(alpha = 0.62f)),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = {
                    Column {
                        Text("COURSE AU TEMPO", style = EyebrowTextStyle, color = scheme.primary)
                        Text("Au rythme de tes pas", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit, modifier = Modifier.size(Space.touchTarget)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour, la course continue", tint = scheme.onSurface)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl, vertical = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CadenceDisplay(state, granted)

            Spacer(Modifier.height(Space.xl))

            if (!granted) {
                PermissionCard(
                    permanentlyDenied = asked && permission != null && !permission.status.shouldShowRationale,
                    onAsk = { permission?.launchPermissionRequest() },
                )
                Spacer(Modifier.height(Space.lg))
            } else if (state.sensorMissing) {
                Text(
                    "Pas de podomètre sur cet appareil : choisis ta cadence ci-dessous.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.lg))
            }

            TargetCard(
                state = state,
                followAvailable = granted && !state.sensorMissing,
                onFollow = { haptics.tap(); viewModel.followRun() },
                onLock = { bpm -> haptics.tap(); viewModel.lockRun(bpm) },
                onLockToCadence = { haptics.tap(); viewModel.lockRunToCadence() },
            )

            Spacer(Modifier.height(Space.lg))

            NowAndNext(state, currentTitle = playerState.currentSong?.title, currentArtist = playerState.currentSong?.displayArtist)

            Spacer(Modifier.height(Space.xxl))

            BrandButton(
                text = "Arrêter",
                icon = Icons.Rounded.Stop,
                onClick = {
                    haptics.confirm()
                    viewModel.stopRun()
                    onExit()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.lg))
        }
    }
}

/** La cadence en très grand, et un mot sur son état. */
@Composable
private fun CadenceDisplay(state: RunState, granted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val cadence = state.cadence.roundToInt()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("CADENCE", style = EyebrowTextStyle, color = scheme.primary)
        Text(
            text = if (cadence > 0) "$cadence" else "—",
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold).merge(TabularNumbers),
            color = scheme.onSurface,
            modifier = Modifier.semantics { contentDescription = if (cadence > 0) "$cadence pas par minute" else "Cadence inconnue" },
        )
        Text("pas / min", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface.copy(alpha = 0.78f))
        Spacer(Modifier.height(Space.xs))
        val hint = when {
            !granted -> "Podomètre non autorisé"
            state.sensorMissing -> "Pas de podomètre"
            cadence == 0 -> "Marche ou cours quelques secondes…"
            state.stable -> "Cadence stable"
            else -> "Ça se cale…"
        }
        Text(hint, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface.copy(alpha = 0.78f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun PermissionCard(permanentlyDenied: Boolean, onAsk: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsRun, contentDescription = null, tint = scheme.primary)
                Spacer(Modifier.size(Space.sm))
                Text("Compter tes pas", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                "Synxio lit le podomètre du téléphone pour suivre ta cadence : sans localisation, " +
                    "rien n'est enregistré. Sans cette autorisation, choisis ta cadence à la main.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface.copy(alpha = 0.86f),
            )
            Spacer(Modifier.height(Space.md))
            if (permanentlyDenied) {
                Text(
                    "Autorisation refusée : active « Activité physique » dans les réglages de l'app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface.copy(alpha = 0.78f),
                )
            } else {
                GlassButton(text = "Autoriser", onClick = onAsk, icon = Icons.Rounded.DirectionsRun)
            }
        }
    }
}

/** Cible et ses commandes : Suivre / Verrouillé, préréglages, « ma cadence ». */
@Composable
private fun TargetCard(
    state: RunState,
    followAvailable: Boolean,
    onFollow: () -> Unit,
    onLock: (Float) -> Unit,
    onLockToCadence: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val target = state.target
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("CIBLE", style = EyebrowTextStyle, color = scheme.primary)
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = if (target.hasTarget) "${target.bpm.roundToInt()} BPM" else "en attente",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold).merge(TabularNumbers),
                color = scheme.onSurface,
            )
            Text(
                text = when {
                    target.locked -> "Verrouillée : la musique garde ce tempo"
                    !followAvailable -> "Choisis une cadence ci-dessous"
                    target.hasTarget -> "Suit ta cadence quand elle est stable"
                    else -> "Se fixera sur ta cadence dès qu'elle sera stable"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface.copy(alpha = 0.78f),
            )
            Spacer(Modifier.height(Space.md))
            if (followAvailable) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    LumenChip(label = RunMode.FOLLOW.label, selected = state.mode == RunMode.FOLLOW, onClick = onFollow)
                    LumenChip(label = RunMode.LOCKED.label, selected = state.mode == RunMode.LOCKED, onClick = onLockToCadence)
                }
                Spacer(Modifier.height(Space.sm))
            }
            Text("VERROUILLER À", style = EyebrowTextStyle, color = scheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(Space.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                for (preset in RunPresets) {
                    LumenChip(
                        label = "${preset.roundToInt()}",
                        selected = target.locked && target.bpm == preset,
                        onClick = { onLock(preset) },
                    )
                }
            }
            if (state.cadence > 0f) {
                Spacer(Modifier.height(Space.sm))
                LumenChip(
                    label = "Ma cadence (${state.cadence.roundToInt()})",
                    selected = target.locked && target.bpm == state.cadence.roundToInt().toFloat(),
                    onClick = onLockToCadence,
                    icon = Icons.Rounded.DirectionsRun,
                )
            }
        }
    }
}

/** Titre en cours avec son BPM, puis le suivant choisi (ou pourquoi il n'y en a pas). */
@Composable
private fun NowAndNext(state: RunState, currentTitle: String?, currentArtist: String?) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text("EN COURS", style = EyebrowTextStyle, color = scheme.primary)
        Spacer(Modifier.height(Space.xs))
        if (currentTitle == null) {
            Text(
                "Aucune lecture : la course lancera un titre dès qu'une cible sera connue.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface.copy(alpha = 0.78f),
            )
        } else {
            TrackLine(currentTitle, currentArtist, state.currentBpm, state.currentSpeed)
        }
        Spacer(Modifier.height(Space.lg))
        Text("ENSUITE", style = EyebrowTextStyle, color = scheme.primary)
        Spacer(Modifier.height(Space.xs))
        val nextTitle = state.nextTitle
        when {
            nextTitle != null -> TrackLine(nextTitle, state.nextArtist, state.nextBpm, state.nextSpeed)
            !state.target.hasTarget -> Text(
                "Dès que la cible sera connue.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface.copy(alpha = 0.78f),
            )
            state.noMatch -> Text(
                "Aucun titre à ce tempo dans ta bibliothèque analysée : la file continue normalement.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface.copy(alpha = 0.78f),
            )
            else -> Text(
                "Recherche d'un titre…",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun TrackLine(title: String, artist: String?, bpm: Float?, speed: Float) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist != null) {
                Text(artist, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.size(Space.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = bpm?.let { "${it.roundToInt()} BPM" } ?: "BPM inconnu",
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                color = scheme.onSurface,
            )
            if (speed != 1f) {
                Text(
                    text = String.format(Locale.FRANCE, "×%.2f", speed),
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    color = scheme.primary,
                )
            }
        }
    }
}
