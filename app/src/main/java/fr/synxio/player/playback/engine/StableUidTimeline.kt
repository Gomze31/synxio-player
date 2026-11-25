package fr.synxio.player.playback.engine

import androidx.media3.common.Timeline

/**
 * Timeline d'un deck dont les identifiants de fenêtres et de périodes sont remplacés par
 * ceux de la playlist maîtresse.
 *
 * Chaque ExoPlayer attribue ses propres uids à ses éléments : à la bascule d'un deck à
 * l'autre, `SimpleBasePlayer` verrait une playlist entièrement remplacée (et le service
 * réécrirait toute la file). Avec des uids stables par élément, seule la position courante
 * change. L'ordre de lecture (aléatoire compris) reste celui du deck.
 *
 * Les périodes ne sont remplacées que quand il y en a exactement une par fenêtre, ce qui
 * est le cas de tout fichier audio et de toute radio.
 */
class StableUidTimeline(private val base: Timeline, private val uids: List<Any>) : Timeline() {

    private val remapPeriods = base.periodCount == base.windowCount && base.windowCount == uids.size
    private val periodIndexByUid: Map<Any, Int> =
        if (remapPeriods) uids.withIndex().associate { (i, uid) -> uid to i } else emptyMap()

    override fun getWindowCount(): Int = base.windowCount

    override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        base.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)

    override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        base.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = base.getLastWindowIndex(shuffleModeEnabled)

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = base.getFirstWindowIndex(shuffleModeEnabled)

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
        base.getWindow(windowIndex, window, defaultPositionProjectionUs)
        if (windowIndex < uids.size) window.uid = uids[windowIndex]
        return window
    }

    override fun getPeriodCount(): Int = base.periodCount

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        base.getPeriod(periodIndex, period, setIds)
        if (setIds && remapPeriods) period.uid = uids[periodIndex]
        return period
    }

    override fun getIndexOfPeriod(uid: Any): Int =
        periodIndexByUid[uid] ?: base.getIndexOfPeriod(uid)

    override fun getUidOfPeriod(periodIndex: Int): Any =
        if (remapPeriods) uids[periodIndex] else base.getUidOfPeriod(periodIndex)
}
