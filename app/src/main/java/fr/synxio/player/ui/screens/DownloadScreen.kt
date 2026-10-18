package fr.synxio.player.ui.screens

import fr.synxio.player.ui.theme.synxioTopBarColors
import androidx.compose.ui.graphics.Color
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.data.repo.YoutubeUrls
import fr.synxio.player.ui.theme.LocalReduceMotion
import fr.synxio.player.ui.theme.Space
import fr.synxio.player.ui.theme.SynxioShape
import fr.synxio.player.ui.viewmodel.AppViewModel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    colors = synxioTopBarColors(),
                    title = { Text("Téléchargeur YouTube") },
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

                // Barre d'adresse et navigation web
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.xs)
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
                                shape = SynxioShape.pill,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                ),
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
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Jauge de chargement de la page web
                        if (webPageProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { webPageProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Space.xs)
                                    .semantics { contentDescription = "Chargement de la page" },
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (isDownloading) {
                Surface(
                    shape = SynxioShape.pill,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .padding(horizontal = Space.xl, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Téléchargement… ${progress.toInt()} %",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Le lien saisi d'abord, sinon la page affichée : toujours validé et canonique.
                        val target = YoutubeUrls.normalizeVideo(inputUrl)
                            ?: YoutubeUrls.normalizeVideo(currentUrl)
                        // downloadYoutube annonce lui-même le démarrage dans la snackbar.
                        if (target != null) {
                            viewModel.downloadYoutube(target)
                        } else {
                            viewModel.showMessage("Ouvre une vidéo YouTube pour la télécharger")
                        }
                    },
                    icon = {
                        Icon(
                            if (isVideoUrl) Icons.Rounded.Download else Icons.Rounded.VideoLibrary,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(if (isVideoUrl) "Télécharger en MP3" else "Ouvre une vidéo")
                    },
                    containerColor = if (isVideoUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isVideoUrl) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
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
                enter = if (reduceMotion) EnterTransition.None else fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Téléchargement et conversion en MP3",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${progress.toInt()} %",
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(Space.xs))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(SynxioShape.pill)
                                .semantics { contentDescription = "Téléchargement ${progress.toInt()} %" },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }

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
