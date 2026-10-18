package fr.synxio.player.ui.screens

import fr.synxio.player.ui.theme.synxioTopBarColors
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.core.util.QRCodeHelper
import fr.synxio.player.playback.PartyServer
import fr.synxio.player.ui.components.EmptyState
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel

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

    val partyUrl by PartyServer.activeUrl.collectAsStateWithLifecycle()
    val activeUrl = partyUrl.takeIf { isEnabled }
    val qrCodeBitmap: Bitmap? = remember(activeUrl) { activeUrl?.let { QRCodeHelper.generate(it) } }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = synxioTopBarColors(),
                title = { Text("Synxio Party") },
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
                .padding(horizontal = Space.gutter, vertical = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Partage la musique en cours avec tes amis sur le même réseau Wi-Fi, sans application à installer de leur côté.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Space.xl))

            // Toute la carte bascule le mode : cible large, annoncée comme interrupteur.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = SynxioShape.card,
            ) {
                Row(
                    modifier = Modifier
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
                        Text(
                            "Crée une page web locale accessible à tes invités",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Space.md))
                    Switch(checked = isEnabled, onCheckedChange = null)
                }
            }

            Spacer(Modifier.height(Space.xxl))

            when {
                !isEnabled -> EmptyState(
                    title = "Le mode Party est éteint",
                    subtitle = "Active-le ci-dessus pour obtenir un QR code à partager.",
                    icon = Icons.Rounded.Celebration,
                    modifier = Modifier.height(320.dp),
                )

                qrCodeBitmap == null || activeUrl == null -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(Space.lg))
                    Text(
                        "Démarrage du serveur local… Une connexion Wi-Fi est nécessaire.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    val serverUrl: String = activeUrl
                    Text(
                        text = "Scanne ce QR code pour rejoindre",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.lg))
                    // Fond blanc imposé : un QR code doit garder un contraste sombre sur clair
                    // pour être lu par les appareils photo, même en thème sombre.
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(SynxioShape.card)
                            .background(Color.White)
                            .padding(Space.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrCodeBitmap.asImageBitmap(),
                            contentDescription = "QR code de la session Synxio Party",
                            modifier = Modifier.fillMaxSize()
                        )
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
                        modifier = Modifier.padding(top = Space.xs, bottom = Space.lg)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Synxio Party", serverUrl))
                                viewModel.showMessage("Lien copié")
                            },
                            shape = SynxioShape.pill,
                            modifier = Modifier.heightIn(min = Space.touchTarget),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.sm))
                            Text("Copier le lien")
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)))
                                }.onFailure { viewModel.showMessage("Aucun navigateur pour ouvrir le lien") }
                            },
                            shape = SynxioShape.pill,
                            modifier = Modifier.heightIn(min = Space.touchTarget),
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.sm))
                            Text("Ouvrir")
                        }
                    }
                }
            }
        }
    }
}
