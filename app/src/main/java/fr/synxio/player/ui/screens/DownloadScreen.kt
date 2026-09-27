package fr.synxio.player.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigateur YouTube") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour")
                    }
                }
            )
        },
        floatingActionButton = {
            if (isDownloading) {
                CircularProgressIndicator()
            } else {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (currentUrl.contains("watch") || currentUrl.contains("youtu.be")) {
                            viewModel.downloadYoutube(currentUrl)
                            Toast.makeText(context, "Lancement du téléchargement...", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Ouvrez une vidéo avant de télécharger", Toast.LENGTH_SHORT).show()
                        }
                    },
                    icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    text = { Text("Télécharger la vidéo") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                url?.let { currentUrl = it }
                                super.doUpdateVisitedHistory(view, url, isReload)
                            }
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl(currentUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
