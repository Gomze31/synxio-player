package fr.synxio.player.watch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.synxio.player.watch.R
import fr.synxio.player.wear.protocol.WearStartIds

/** Tous les états sans lecture : déconnecté, vide, version, Play Services, connexion. */
@Composable
fun DisconnectedScreen(ui: PlayerUiState, onStart: (String) -> Unit) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (ui is PlayerUiState.Connecting) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.connecting), textAlign = TextAlign.Center)
                return@Column
            }
            Icon(
                painter = painterResource(R.drawable.ic_tile_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            val message = when (ui) {
                PlayerUiState.PlayServicesMissing -> R.string.play_services_required
                PlayerUiState.PhoneUnreachable -> R.string.phone_out_of_range
                is PlayerUiState.VersionMismatch -> if (ui.phoneNewer) R.string.update_watch else R.string.update_phone
                else -> R.string.paused_on_phone
            }
            Text(stringResource(message), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            if (ui is PlayerUiState.Idle) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onStart(WearStartIds.LAST_QUEUE) }, label = { Text(stringResource(R.string.resume)) })
            }
        }
    }
}
