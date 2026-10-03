package fr.synxio.player.ui.screens

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.synxio.player.ui.viewmodel.AppViewModel

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
                if (!clipText.isNullOrBlank() && (clipText.contains("youtube.com") || clipText.contains("youtu.be"))) {
                    inputUrl = clipText
                    currentUrl = clipText
                    webViewInstance?.loadUrl(clipText)
                    Toast.makeText(context, "Lien YouTube détecté dans le presse-papier", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun loadTargetUrl(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return
        val finalUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains("youtube.com") || trimmed.contains("youtu.be") -> "https://$trimmed"
            else -> "https://m.youtube.com/results?search_query=${android.net.Uri.encode(trimmed)}"
        }
        currentUrl = finalUrl
        inputUrl = finalUrl
        webViewInstance?.loadUrl(finalUrl)
        focusManager.clearFocus()
    }

    val isVideoUrl = currentUrl.contains("watch") || currentUrl.contains("youtu.be") || currentUrl.contains("shorts")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Téléchargeur YouTube",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
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

                // Barre d'adresse et navigation web
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { webViewInstance?.goBack() },
                                enabled = canGoBackWeb
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Précédent",
                                    tint = if (canGoBackWeb) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }

                            IconButton(
                                onClick = { webViewInstance?.goForward() },
                                enabled = canGoForwardWeb
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Suivant",
                                    tint = if (canGoForwardWeb) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }

                            OutlinedTextField(
                                value = inputUrl,
                                onValueChange = { inputUrl = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                placeholder = {
                                    Text(
                                        "Rechercher ou coller l'URL...",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
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
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Effacer",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            runCatching {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                                if (!clip.isNullOrBlank()) {
                                                    loadTargetUrl(clip)
                                                } else {
                                                    Toast.makeText(context, "Presse-papier vide", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) {
                                            Icon(
                                                Icons.Rounded.ContentPaste,
                                                contentDescription = "Coller",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            )

                            IconButton(onClick = { loadTargetUrl(inputUrl.ifBlank { currentUrl }) }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Aller",
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
                                    .height(2.dp)
                                    .padding(top = 4.dp),
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
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Téléchargement… ${progress.toInt()}%",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = {
                        val target = if (inputUrl.contains("watch") || inputUrl.contains("youtu.be")) inputUrl else currentUrl
                        if (target.contains("watch") || target.contains("youtu.be") || target.contains("shorts")) {
                            viewModel.downloadYoutube(target)
                            Toast.makeText(context, "Extraction et téléchargement audio lancés…", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Ouvrez une vidéo YouTube pour la télécharger", Toast.LENGTH_LONG).show()
                        }
                    },
                    icon = {
                        Icon(
                            if (isVideoUrl) Icons.Rounded.Download else Icons.Rounded.VideoLibrary,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(if (isVideoUrl) "Télécharger en MP3" else "Ouvrez une vidéo")
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
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Téléchargement & conversion MP3",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${progress.toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")

                        webViewClient = object : WebViewClient() {
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
