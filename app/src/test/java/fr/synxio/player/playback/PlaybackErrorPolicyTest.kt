package fr.synxio.player.playback

import fr.synxio.player.playback.PlaybackErrorPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorPolicyTest {

    @Test
    fun `un fichier abime isole est saute`() {
        val policy = PlaybackErrorPolicy()
        assertEquals(Action.SKIP, policy.onError(itemCount = 20, hasNext = true))
    }

    @Test
    fun `echec general - on s'arrete au lieu de faire defiler toute la file`() {
        val policy = PlaybackErrorPolicy()
        val actions = (1..50).map {
            // Chaque titre démarre puis échoue tout de suite : 200 ms écoutées seulement.
            policy.onListened(200)
            policy.onError(itemCount = 500, hasNext = true)
        }
        assertEquals(Action.STOP, actions[PlaybackErrorPolicy.MAX_CONSECUTIVE_ERRORS - 1])
        assertEquals(PlaybackErrorPolicy.MAX_CONSECUTIVE_ERRORS - 1, actions.takeWhile { it == Action.SKIP }.size)
    }

    @Test
    fun `un titre ecoute normalement remet le compteur a zero`() {
        val policy = PlaybackErrorPolicy()
        policy.onError(itemCount = 20, hasNext = true)
        policy.onError(itemCount = 20, hasNext = true)
        policy.onListened(30_000)
        assertEquals(0, policy.consecutiveErrors)
        assertEquals(Action.SKIP, policy.onError(itemCount = 20, hasNext = true))
    }

    @Test
    fun `dernier titre de la file - arret`() {
        assertEquals(Action.STOP, PlaybackErrorPolicy().onError(itemCount = 20, hasNext = false))
    }

    @Test
    fun `petite file - la limite ne depasse pas sa taille`() {
        val policy = PlaybackErrorPolicy()
        assertEquals(Action.SKIP, policy.onError(itemCount = 2, hasNext = true))
        assertEquals(Action.STOP, policy.onError(itemCount = 2, hasNext = true))
    }

    @Test
    fun `nouvelle lecture lancee par l'utilisateur - nouvelle chance`() {
        val policy = PlaybackErrorPolicy()
        repeat(3) { policy.onError(itemCount = 20, hasNext = true) }
        policy.onUserAction()
        assertEquals(Action.SKIP, policy.onError(itemCount = 20, hasNext = true))
    }
}
