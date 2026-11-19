package fr.synxio.player.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.ambient.AmbientLifecycleObserver
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.watch.ui.LocalAmbient
import fr.synxio.player.watch.ui.PlayerViewModel
import fr.synxio.player.watch.ui.WatchNav

/** Hôte unique : navigation Compose, mode ambiant, relecture du lien au premier plan. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private var ambient by mutableStateOf(false)

    private val ambientObserver by lazy {
        AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) { ambient = true }
            override fun onExitAmbient() {
                ambient = false
                viewModel.onForeground()
            }
            override fun onUpdateAmbient() = Unit
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        setContent {
            CompositionLocalProvider(LocalAmbient provides ambient) {
                WatchNav(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onForeground()
    }
}
