package fr.synxio.player.watch.ui

import fr.synxio.player.watch.data.Connectivity
import fr.synxio.player.watch.data.PhoneLink
import fr.synxio.player.watch.data.WearStateStore
import fr.synxio.player.wear.protocol.WearCommand
import fr.synxio.player.wear.protocol.WearPaths
import fr.synxio.player.wear.protocol.WearState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlayerViewModelTest {

    @get:Rule val folder = TemporaryFolder()

    private class FakeLink : PhoneLink {
        override val connectivity = MutableStateFlow(Connectivity.PHONE_REACHABLE)
        val sent = mutableListOf<WearCommand>()
        var fail = false
        var refreshed = 0
        override suspend fun refresh() { refreshed++ }
        override suspend fun send(command: WearCommand): Result<Unit> {
            sent += command
            return if (fail) Result.failure(IllegalStateException("hors de portée")) else Result.success(Unit)
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: WearStateStore
    private lateinit var link: FakeLink
    private var now = 1_000_000L

    private val playing = WearState(
        mediaId = "42", title = "Titre", artist = "Artiste", isPlaying = true,
        positionMs = 10_000, durationMs = 100_000, capturedAtMs = 1_000_000, volume = 0.5f, hasNext = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = WearStateStore(folder.newFolder())
        link = FakeLink()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = PlayerViewModel(store, link).apply { clock = { now } }

    @Test
    fun `telephone injoignable`() = runTest(dispatcher) {
        link.connectivity.value = Connectivity.PHONE_UNREACHABLE
        val vm = vm()
        advanceUntilIdle()
        assertEquals(PlayerUiState.PhoneUnreachable, vm.uiState.value)
    }

    @Test
    fun `play services absents`() = runTest(dispatcher) {
        link.connectivity.value = Connectivity.PLAY_SERVICES_MISSING
        val vm = vm()
        advanceUntilIdle()
        assertEquals(PlayerUiState.PlayServicesMissing, vm.uiState.value)
    }

    @Test
    fun `joignable sans etat donne Idle`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertEquals(PlayerUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `un etat de lecture donne Playing`() = runTest(dispatcher) {
        store.save(playing, null, null)
        val vm = vm()
        advanceUntilIdle()
        val ui = vm.uiState.value as PlayerUiState.Playing
        assertTrue(ui.isPlaying)
        assertEquals(0.5f, ui.volume, 0.0001f)
        assertEquals("Titre", ui.state.title)
    }

    @Test
    fun `un etat perime est affiche en pause`() = runTest(dispatcher) {
        store.save(playing, null, null)
        now = 1_000_000 + WearState.STALE_MS + 1
        val vm = vm()
        advanceUntilIdle()
        assertFalse((vm.uiState.value as PlayerUiState.Playing).isPlaying)
    }

    @Test
    fun `version de protocole differente`() = runTest(dispatcher) {
        store.save(playing.copy(protocolVersion = WearPaths.PROTOCOL_VERSION + 1), null, null)
        val vm = vm()
        advanceUntilIdle()
        assertEquals(PlayerUiState.VersionMismatch(phoneNewer = true), vm.uiState.value)
    }

    @Test
    fun `onForeground relit le lien`() = runTest(dispatcher) {
        val vm = vm()
        vm.onForeground()
        advanceUntilIdle()
        assertEquals(1, link.refreshed)
    }

    @Test
    fun `lecture pause bascule tout de suite puis attend l etat`() = runTest(dispatcher) {
        store.save(playing, null, null)
        val vm = vm()
        advanceUntilIdle()
        vm.playPause()
        runCurrent()
        assertEquals(listOf(WearCommand.PlayPause), link.sent)
        assertFalse((vm.uiState.value as PlayerUiState.Playing).isPlaying)

        // Le téléphone confirme : l'optimisme laisse la place à l'état reçu.
        store.save(playing.copy(isPlaying = false, capturedAtMs = now + 500), null, null)
        runCurrent()
        assertFalse((vm.uiState.value as PlayerUiState.Playing).isPlaying)
        // Et une fois l'optimisme expiré, l'état reçu fait toujours foi.
        advanceUntilIdle()
        assertFalse((vm.uiState.value as PlayerUiState.Playing).isPlaying)
    }

    @Test
    fun `sans confirmation l optimisme expire apres trois secondes`() = runTest(dispatcher) {
        store.save(playing, null, null)
        val vm = vm()
        advanceUntilIdle()
        vm.toggleFavorite()
        runCurrent()
        assertTrue((vm.uiState.value as PlayerUiState.Playing).favorite)
        advanceTimeBy(3_100)
        runCurrent()
        assertFalse((vm.uiState.value as PlayerUiState.Playing).favorite)
    }

    @Test
    fun `start passe en Connexion puis revient sur l etat recu`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.start("synxio://favorites")
        runCurrent()
        assertEquals(PlayerUiState.Connecting, vm.uiState.value)
        assertEquals(listOf(WearCommand.Start("synxio://favorites")), link.sent)
        store.save(playing.copy(capturedAtMs = now + 1_000), null, null)
        runCurrent()
        assertTrue(vm.uiState.value is PlayerUiState.Playing)
        // Le délai de 8 s écoulé ne doit plus rien changer : la connexion est close.
        advanceUntilIdle()
        assertTrue(vm.uiState.value is PlayerUiState.Playing)
    }

    @Test
    fun `start sans reponse en huit secondes previent et revient`() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val vm = vm()
        val job = launch { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.start("synxio://last-queue")
        advanceTimeBy(8_100)
        advanceUntilIdle()
        assertEquals(PlayerUiState.Idle, vm.uiState.value)
        assertEquals(listOf("Téléphone injoignable"), events)
        job.cancel()
    }

    @Test
    fun `un envoi en echec previent`() = runTest(dispatcher) {
        store.save(playing, null, null)
        link.fail = true
        val events = mutableListOf<String>()
        val vm = vm()
        val job = launch { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.next()
        advanceUntilIdle()
        assertEquals(listOf("Téléphone injoignable"), events)
        job.cancel()
    }

    @Test
    fun `une rafale de crans n envoie qu un pas par 80 ms`() = runTest(dispatcher) {
        store.save(playing, null, null)
        val vm = vm()
        advanceUntilIdle()
        vm.volumeStep(1); vm.volumeStep(1); vm.volumeStep(1)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(WearCommand.VolumeUp), link.sent)
        val ui = vm.uiState.value as PlayerUiState.Playing
        assertTrue(ui.volumeVisible)
        assertEquals(0.5f + 1f / 15f, ui.volume, 0.0001f)
        advanceTimeBy(1_600)
        runCurrent()
        assertFalse((vm.uiState.value as PlayerUiState.Playing).volumeVisible)
    }

    @Test
    fun `des crans de sens opposes s annulent`() = runTest(dispatcher) {
        store.save(playing, null, null)
        val vm = vm()
        advanceUntilIdle()
        vm.volumeStep(1); vm.volumeStep(-1)
        advanceTimeBy(100)
        advanceUntilIdle()
        assertTrue(link.sent.isEmpty())
    }
}
