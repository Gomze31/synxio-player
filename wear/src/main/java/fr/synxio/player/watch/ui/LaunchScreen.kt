package fr.synxio.player.watch.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.synxio.player.watch.R
import fr.synxio.player.wear.protocol.WearStartIds

private data class Launch(val label: Int, val icon: ImageVector, val mediaId: String)

private val launches = listOf(
    Launch(R.string.resume, Icons.Filled.Replay, WearStartIds.LAST_QUEUE),
    Launch(R.string.favorites, Icons.Filled.Favorite, WearStartIds.FAVORITES),
    Launch(R.string.shuffle, Icons.Filled.Shuffle, WearStartIds.SHUFFLE_ALL),
    Launch(R.string.surprise, Icons.Filled.Star, WearStartIds.SURPRISE),
)

/** Quatre lancements rapides. Un toucher envoie `start:<id>` et revient au lecteur. */
@Composable
fun LaunchScreen(onStart: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(stringResource(R.string.app_name)) } }
            items(launches.size) { index ->
                val launch = launches[index]
                Button(
                    onClick = { onStart(launch.mediaId) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(launch.icon, contentDescription = null) },
                    label = { Text(stringResource(launch.label)) },
                )
            }
        }
    }
}
