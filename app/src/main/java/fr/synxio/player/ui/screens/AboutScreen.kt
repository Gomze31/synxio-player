package fr.synxio.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.synxio.player.BuildConfig

/**
 * Écran À propos - version simplifiée.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = { Text("À propos") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Retour"
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            ElevatedCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Synxio Player",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Version ${BuildConfig.VERSION_NAME}")
                    Spacer(Modifier.height(8.dp))
                    Text("Lecteur de musique local pour Android")
                    Spacer(Modifier.height(8.dp))
                    Text("Développé avec Kotlin et Jetpack Compose")
                }
            }

            Spacer(Modifier.height(16.dp))

            ElevatedCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Informations",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Cette application est un projet open source.")
                    Text("Toutes les données restent locales sur votre appareil.")
                }
            }
        }
    }
}
