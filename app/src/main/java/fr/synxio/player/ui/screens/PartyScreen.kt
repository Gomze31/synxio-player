package fr.synxio.player.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.QRCodeHelper
import fr.synxio.player.playback.PartyServer
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.components.GlassCard
import fr.synxio.player.ui.components.shimmer
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalGlowEffects
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

private val QrSize = 232.dp

/** Les trois états du serveur, chacun avec sa propre mise en scène. */
private enum class PartyPhase { Off, Starting, Live }

/**
 * Synxio Party : une page web locale qui diffuse le titre en cours aux invités du même Wi-Fi.
 *
 * L'adresse affichée vient exclusivement de [PartyServer.activeUrl] (adresse Wi-Fi + jeton
 * secret de la session) : on ne la reconstruit jamais ici, sinon on publierait une URL
 * sans jeton.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PartyScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isEnabled = settings.partyEnabled
    val reduce = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme

    val partyUrl by PartyServer.activeUrl.collectAsStateWithLifecycle()
    val activeUrl = partyUrl.takeIf { isEnabled }
    val qrCodeBitmap: Bitmap? = remember(activeUrl) { activeUrl?.let { QRCodeHelper.generate(it) } }

    val phase = when {
        !isEnabled -> PartyPhase.Off
        qrCodeBitmap == null || activeUrl == null -> PartyPhase.Starting
        else -> PartyPhase.Live
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Synxio Party", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter, vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "SOIRÉE",
                style = EyebrowTextStyle,
                color = scheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Ta musique, sur tous les téléphones",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = "Partage la musique en cours avec tes amis sur le même réseau Wi-Fi, sans application à installer de leur côté.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Space.xl))

            // Toute la carte bascule le mode : cible large, annoncée comme interrupteur.
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = isEnabled,
                            role = Role.Switch,
                            onValueChange = { viewModel.togglePartyMode() },
                        )
                        .heightIn(min = Space.touchTarget)
                        .padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Activer Synxio Party", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Space.xs))
                        ServerStatus(phase)
                    }
                    Spacer(Modifier.width(Space.md))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = scheme.primary),
                    )
                }
            }

            Spacer(Modifier.height(Space.xl))

            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    if (reduce) {
                        fadeIn(Motion.enter(Motion.XS)) togetherWith fadeOut(Motion.exit(Motion.XS))
                    } else {
                        (fadeIn(Motion.enter()) + scaleIn(Motion.spatial(), initialScale = 0.94f)) togetherWith
                            fadeOut(Motion.exit())
                    }
                },
                contentAlignment = Alignment.TopCenter,
                label = "partyPhase",
            ) { target ->
                when (target) {
                    PartyPhase.Off -> EmptyState(
                        title = "Le mode Party est éteint",
                        subtitle = "Active-le ci-dessus pour obtenir un QR code à partager.",
                        icon = Icons.Rounded.Celebration,
                        modifier = Modifier.height(320.dp),
                    )

                    PartyPhase.Starting -> StartingPane()

                    PartyPhase.Live -> {
                        // Garde-fou : l'état « Live » n'existe que si les deux sont présents,
                        // mais AnimatedContent peut recomposer l'ancien contenu en sortie.
                        val serverUrl = activeUrl
                        val bitmap = qrCodeBitmap
                        if (serverUrl != null && bitmap != null) {
                            LivePane(
                                serverUrl = serverUrl,
                                qr = bitmap,
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Synxio Party", serverUrl))
                                    viewModel.showMessage("Lien copié")
                                },
                                onOpen = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)))
                                    }.onFailure { viewModel.showMessage("Aucun navigateur pour ouvrir le lien") }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.bottomInset))
        }
    }
}

/**
 * Pastille d'état du serveur : un point coloré (éteint, en démarrage, en ligne) qui
 * pulse doucement tant que le serveur diffuse. Le libellé porte l'état même sans couleur.
 */
@Composable
private fun ServerStatus(phase: PartyPhase) {
    val scheme = MaterialTheme.colorScheme
    val colors = SynxioTheme.colors
    val reduce = LocalReduceMotion.current
    val label = when (phase) {
        PartyPhase.Off -> "Serveur arrêté"
        PartyPhase.Starting -> "Démarrage du serveur…"
        PartyPhase.Live -> "En ligne sur le Wi-Fi"
    }
    val dot by animateColorAsState(
        when (phase) {
            PartyPhase.Off -> scheme.onSurfaceVariant
            PartyPhase.Starting -> colors.warning
            PartyPhase.Live -> colors.success
        },
        Motion.standardOrSnap(reduce),
        label = "partyDot",
    )
    // La pulsation n'existe que pendant la diffusion, et jamais en animations réduites.
    val pulse = if (phase != PartyPhase.Off && !reduce) {
        val t = rememberInfiniteTransition(label = "partyPulse")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "partyPulseValue",
        ).value
    } else {
        -1f
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            stateDescription = label
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Box(
            Modifier
                .size(18.dp)
                .drawBehind {
                    if (pulse >= 0f) {
                        drawCircle(
                            color = dot,
                            radius = size.minDimension / 2f * (0.4f + 0.6f * pulse),
                            alpha = (1f - pulse) * 0.5f,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
        }
        Spacer(Modifier.width(Space.xs))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** Le serveur démarre : la place du QR code scintille, la raison d'une attente est dite. */
@Composable
private fun StartingPane() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(QrSize + Space.xxl)
                .clip(SynxioShape.card)
                .glass(SynxioShape.card, strength = 0.5f)
                .padding(Space.lg),
        ) {
            Box(Modifier.fillMaxSize().clip(SynxioShape.cardSm).shimmer())
        }
        Spacer(Modifier.height(Space.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Space.sm))
            Text(
                "Démarrage du serveur local… Une connexion Wi-Fi est nécessaire.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Serveur en ligne : QR code dans sa carte de verre auréolée, adresse et partage. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LivePane(
    serverUrl: String,
    qr: Bitmap,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = SynxioTheme.colors
    val glow = LocalGlowEffects.current
    val image = remember(qr) { qr.asImageBitmap() }
    // Confirmation sur le bouton lui-même : « Lien copié » s'affiche deux secondes, puis
    // le libellé revient. Pas besoin de chercher la snackbar du regard.
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Scanne ce QR code pour rejoindre",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.lg))

        Box(contentAlignment = Alignment.Center) {
            // Halo à la signature derrière la carte : la pièce maîtresse de l'écran.
            if (glow) {
                Box(
                    Modifier
                        .size(QrSize + 120.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(colors.brandEnd.copy(alpha = 0.34f), colors.brandStart.copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                )
            }
            Box(
                Modifier
                    .glowShadow(SynxioShape.card, colors.brandEnd, elevation = 24.dp)
                    .clip(SynxioShape.card)
                    .glass(SynxioShape.card, strength = 1f)
                    .padding(Space.md),
            ) {
                // Fond blanc imposé : un QR code doit garder un contraste sombre sur clair
                // pour être lu par les appareils photo, même en thème sombre.
                Box(
                    modifier = Modifier
                        .size(QrSize)
                        .clip(SynxioShape.cardSm)
                        .background(Color.White)
                        .padding(Space.md),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = "QR code de la session Synxio Party",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xl))

        Text(
            text = "ou partage cette adresse :",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = serverUrl,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = Space.sm, bottom = Space.lg)
                .clip(SynxioShape.pill)
                .glass(SynxioShape.pill, strength = 0.6f)
                .padding(horizontal = Space.lg, vertical = Space.sm)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            BrandButton(
                text = if (copied) "Lien copié" else "Copier le lien",
                icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                onClick = {
                    onCopy()
                    copied = true
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            GlassButton(
                text = "Ouvrir",
                icon = Icons.Rounded.OpenInBrowser,
                onClick = onOpen,
            )
        }
    }
}
