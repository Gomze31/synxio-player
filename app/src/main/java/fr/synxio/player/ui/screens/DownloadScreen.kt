package fr.synxio.player.ui.screens

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.repo.YoutubeUrls
import fr.synxio.player.ui.components.BrandButton
import fr.synxio.player.ui.components.BrandProgressBar
import fr.synxio.player.ui.components.GlassButton
import fr.synxio.player.ui.theme.EyebrowTextStyle
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Motion
import fr.synxio.player.ui.theme.Radius
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.theme.SynxioTheme
import fr.synxio.player.ui.theme.TabularNumbers
import fr.synxio.player.ui.theme.glass
import fr.synxio.player.ui.theme.glowShadow
import fr.synxio.player.ui.theme.synxioTopBarColors
import fr.synxio.player.ui.viewmodel.AppViewModel

/** État du bouton flottant : on ne passe de l'un à l'autre que par un fondu enchaîné. */
private enum class DownloadAction { Downloading, Ready, NoVideo }

/**
 * Téléchargeur YouTube : une WebView cantonnée à YouTube et un bouton qui télécharge
 * l'audio de la vidéo affichée.
 *
 * Toute URL passe par [YoutubeUrls] (normalisation https, liste d'hôtes autorisés) : rien
 * d'autre n'est chargé dans la WebView. Les retours passent par la snackbar de l'app.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var currentUrl by remember { mutableStateOf("https://m.youtube.com/") }
    var inputUrl by remember { mutableStateOf("") }
    var webPageProgress by remember { mutableIntStateOf(100) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBackWeb by remember { mutableStateOf(false) }
    var canGoForwardWeb by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val reduceMotion = LocalReduceMotion.current
    val scheme = MaterialTheme.colorScheme

    // Gestion du bouton retour système : reculer dans l'historique web s'il existe
    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onBack()
        }
    }

    // Détection d'un lien YouTube dans le presse-papier à l'ouverture
    LaunchedEffect(Unit) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip() &&
                clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            ) {
                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                // Seul un lien YouTube validé et reconstruit en https:// est repris.
                val safeUrl = YoutubeUrls.normalize(clipText)
                if (safeUrl != null) {
                    inputUrl = safeUrl
                    currentUrl = safeUrl
                    webViewInstance?.loadUrl(safeUrl)
                    viewModel.showMessage("Lien YouTube repris du presse-papier")
                }
            }
        }
    }

    fun loadTargetUrl(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return
        val youtubeUrl = YoutubeUrls.normalize(trimmed)
        val finalUrl = when {
            youtubeUrl != null -> youtubeUrl
            // Lien vers un autre site (ou autre schéma) : jamais chargé dans la WebView.
            // Tout autre texte devient une simple recherche YouTube (encodée), donc inoffensif.
            trimmed.contains("://") || trimmed.startsWith("javascript:", ignoreCase = true) -> {
                viewModel.showMessage("Seuls les liens YouTube sont acceptés")
                return
            }
            else -> "https://m.youtube.com/results?search_query=${android.net.Uri.encode(trimmed)}"
        }
        currentUrl = finalUrl
        inputUrl = finalUrl
        webViewInstance?.loadUrl(finalUrl)
        focusManager.clearFocus()
    }

    val isVideoUrl = YoutubeUrls.normalizeVideo(currentUrl) != null
    val action = when {
        isDownloading -> DownloadAction.Downloading
        isVideoUrl -> DownloadAction.Ready
        else -> DownloadAction.NoVideo
    }
    // La jauge glisse vers la nouvelle valeur au lieu de sauter de palier en palier.
    val downloadFraction by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        animationSpec = Motion.standardOrSnap(reduceMotion),
        label = "downloadProgress",
    )
    val pageFraction by animateFloatAsState(
        targetValue = webPageProgress / 100f,
        animationSpec = Motion.standardOrSnap(reduceMotion, Motion.SHORT),
        label = "pageProgress",
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    colors = synxioTopBarColors(),
                    title = {
                        Column {
                            Text("TÉLÉCHARGEUR", style = EyebrowTextStyle, color = scheme.primary)
                            Text("YouTube en MP3", style = MaterialTheme.typography.headlineSmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (webViewInstance?.canGoBack() == true) {
                                webViewInstance?.goBack()
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val home = "https://m.youtube.com/"
                            currentUrl = home
                            inputUrl = home
                            webViewInstance?.loadUrl(home)
                        }) {
                            Icon(Icons.Rounded.Home, contentDescription = "Accueil")
                        }
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser")
                        }
                    }
                )

                // Barre d'adresse et navigation web : une plaque de verre posée sous la barre.
                Column(
                    Modifier
                        .padding(horizontal = Space.md, vertical = Space.xs)
                        .fillMaxWidth()
                        .clip(SynxioShape.card)
                        .glass(SynxioShape.card, strength = 0.8f)
                        .padding(horizontal = Space.xs, vertical = Space.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xxs)
                    ) {
                        IconButton(
                            onClick = { webViewInstance?.goBack() },
                            enabled = canGoBackWeb
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Page précédente")
                        }

                        IconButton(
                            onClick = { webViewInstance?.goForward() },
                            enabled = canGoForwardWeb
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Page suivante")
                        }

                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Rechercher ou coller un lien",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                            },
                            singleLine = true,
                            shape = SynxioShape.cardSm,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = glassFieldColors(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { loadTargetUrl(inputUrl) }
                            ),
                            trailingIcon = {
                                if (inputUrl.isNotBlank()) {
                                    IconButton(onClick = { inputUrl = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Effacer le champ")
                                    }
                                } else {
                                    IconButton(onClick = {
                                        runCatching {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                            if (!clip.isNullOrBlank()) {
                                                loadTargetUrl(clip)
                                            } else {
                                                viewModel.showMessage("Le presse-papier est vide")
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Coller")
                                    }
                                }
                            }
                        )

                        IconButton(onClick = { loadTargetUrl(inputUrl.ifBlank { currentUrl }) }) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Ouvrir",
                                tint = scheme.primary
                            )
                        }
                    }

                    // Jauge de chargement de la page web : un trait de signature qui
                    // apparaît et disparaît en fondu, sans faire sauter la mise en page.
                    AnimatedVisibility(
                        visible = webPageProgress in 1..99,
                        enter = fadeIn(Motion.enter(Motion.SHORT)),
                        exit = fadeOut(Motion.exit()),
                    ) {
                        BrandProgressBar(
                            progress = pageFraction,
                            height = 2.dp,
                            trackColor = Color.Transparent,
                            modifier = Modifier
                                .padding(horizontal = Space.md, vertical = Space.xxs)
                                .semantics { contentDescription = "Chargement de la page" },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(Motion.enter(Motion.XS)) togetherWith fadeOut(Motion.exit(Motion.XS))
                    } else {
                        (fadeIn(Motion.enter(Motion.SHORT)) + scaleIn(Motion.spatialBouncy(), initialScale = 0.9f)) togetherWith
                            fadeOut(Motion.exit(Motion.XS))
                    }
                },
                contentAlignment = Alignment.BottomEnd,
                label = "downloadAction",
            ) { target ->
                when (target) {
                    DownloadAction.Downloading -> Row(
                        modifier = Modifier
                            .glowShadow(SynxioShape.pill, SynxioTheme.colors.brandEnd, elevation = 12.dp)
                            .clip(SynxioShape.pill)
                            .glass(SynxioShape.pill, strength = 1f)
                            .heightIn(min = 56.dp)
                            .padding(horizontal = Space.xl, vertical = Space.md)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadFraction },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = SynxioTheme.colors.brandEnd,
                            trackColor = scheme.onSurface.copy(alpha = 0.12f),
                        )
                        Text(
                            text = "Téléchargement… ${progress.toInt()} %",
                            style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                            color = scheme.onSurface,
                        )
                    }

                    DownloadAction.Ready -> BrandButton(
                        text = "Télécharger en MP3",
                        icon = Icons.Rounded.Download,
                        modifier = Modifier.heightIn(min = 56.dp),
                        onClick = {
                            // Le lien saisi d'abord, sinon la page affichée : toujours validé et canonique.
                            val target2 = YoutubeUrls.normalizeVideo(inputUrl)
                                ?: YoutubeUrls.normalizeVideo(currentUrl)
                            // downloadYoutube annonce lui-même le démarrage dans la snackbar.
                            if (target2 != null) {
                                viewModel.downloadYoutube(target2)
                            } else {
                                viewModel.showMessage("Ouvre une vidéo YouTube pour la télécharger")
                            }
                        },
                    )

                    // Pas de vidéo à l'écran : bouton secondaire en verre, qui explique au lieu
                    // de rester grisé sans raison.
                    DownloadAction.NoVideo -> GlassButton(
                        text = "Ouvre une vidéo",
                        icon = Icons.Rounded.VideoLibrary,
                        modifier = Modifier.heightIn(min = 56.dp),
                        onClick = {
                            val target2 = YoutubeUrls.normalizeVideo(inputUrl)
                            if (target2 != null) {
                                viewModel.downloadYoutube(target2)
                            } else {
                                viewModel.showMessage("Ouvre une vidéo YouTube pour la télécharger")
                            }
                        },
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Bandeau de statut pendant le téléchargement
            AnimatedVisibility(
                visible = isDownloading,
                enter = if (reduceMotion) fadeIn(Motion.enter(Motion.XS)) else fadeIn(Motion.enter()) + expandVertically(Motion.spatial()),
                exit = if (reduceMotion) fadeOut(Motion.exit(Motion.XS)) else fadeOut(Motion.exit()) + shrinkVertically(Motion.spatial()),
            ) {
                Column(
                    Modifier
                        .padding(horizontal = Space.md, vertical = Space.xs)
                        .fillMaxWidth()
                        .clip(SynxioShape.cardSm)
                        .glass(SynxioShape.cardSm, strength = 0.9f)
                        .padding(horizontal = Space.lg, vertical = Space.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("EN COURS", style = EyebrowTextStyle, color = scheme.primary)
                            Text(
                                text = "Téléchargement et conversion en MP3",
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSurface,
                            )
                        }
                        Spacer(Modifier.width(Space.md))
                        Text(
                            text = "${progress.toInt()} %",
                            style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                            color = scheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(Space.sm))
                    BrandProgressBar(
                        progress = downloadFraction,
                        height = 6.dp,
                        modifier = Modifier.semantics { contentDescription = "Téléchargement ${progress.toInt()} %" },
                    )
                }
            }

            // La page web dans une fenêtre aux coins hauts arrondis : elle se lit comme un
            // panneau posé sur la scène, pas comme un navigateur plaqué dans l'app.
            Box(
                Modifier
                    .padding(top = Space.xs)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg))
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString = settings.userAgentString.replace("; wv", "")

                            webViewClient = object : WebViewClient() {
                                // La WebView reste sur YouTube : les autres liens HTTPS s'ouvrent dans
                                // le navigateur, tout autre schéma (intent://, javascript:, file:…) est bloqué.
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val target = request?.url ?: return true
                                    if (YoutubeUrls.isAllowedInWebView(target.toString())) return false
                                    if (request.isForMainFrame && target.scheme.equals("https", ignoreCase = true)) {
                                        runCatching {
                                            ctx.startActivity(
                                                Intent(Intent.ACTION_VIEW, target)
                                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                    return true
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    url?.let {
                                        currentUrl = it
                                        inputUrl = it
                                    }
                                    canGoBackWeb = view?.canGoBack() == true
                                    canGoForwardWeb = view?.canGoForward() == true
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    webPageProgress = newProgress
                                    canGoBackWeb = view?.canGoBack() == true
                                    canGoForwardWeb = view?.canGoForward() == true
                                    super.onProgressChanged(view, newProgress)
                                }
                            }

                            webViewInstance = this
                            loadUrl(currentUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Champ de saisie en verre : fond translucide, liseré discret, accent au focus. */
@Composable
private fun glassFieldColors() = MaterialTheme.colorScheme.let { scheme ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surfaceContainerHighest.copy(alpha = 0.55f),
        unfocusedContainerColor = scheme.surfaceContainerHighest.copy(alpha = 0.32f),
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.6f),
        cursorColor = scheme.primary,
    )
}
