package fr.synxio.player.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import fr.synxio.player.ui.theme.SynxioShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
    permanentlyDenied: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(60.dp),
            )
        }

        Spacer(Modifier.height(32.dp))
        Text("Synxio", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Ta musique locale, en beau.",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (permanentlyDenied) {
                "L'accès a été refusé. Ouvre les réglages de l'app, puis Autorisations > " +
                    "Musique et audio, pour que Synxio puisse lire ta bibliothèque."
            } else {
                "Synxio a besoin d'accéder aux fichiers audio de ton appareil pour " +
                    "construire ta bibliothèque. Elle reste sur ton téléphone : pas de " +
                    "compte, pas de traceur, pas de publicité."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (permanentlyDenied) {
                    // La demande système ne s'affiche plus après « Ne plus demander » :
                    // seuls les réglages de l'app permettent encore d'accorder l'accès.
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    onRequest()
                }
            },
            shape = SynxioShape.pill,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(
                if (permanentlyDenied) "Ouvrir les réglages" else "Autoriser l'accès",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
