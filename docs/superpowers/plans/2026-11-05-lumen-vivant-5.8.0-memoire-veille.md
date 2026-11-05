# Lumen Vivant 5.8.0 — plan 3 : mémoire musicale, veille adaptative, version

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** L'app se souvient avec l'utilisateur (cartes « Souvenirs » sur l'accueil), la minuterie de veille devient adaptative (fondu long, pause sur immobilité, secousse pour prolonger) avec une horloge exacte, et la 5.8.0 prend son numéro.

**Architecture:** `MemoryEngine` (pur, même modèle que `DailyMixEngine`) nourri par `play_history`, exposé par `MemoryRepository` puis `AppViewModel.memories` jusqu'à une section `HomeSection` de `MosaicCard`. `SleepTimer` passe à une échéance `elapsedRealtime` injectable et gagne un mode adaptatif piloté par `StillnessDetector` (pur) et le `ShakeDetector` existant, câblés dans `PlaybackService`.

**Tech Stack:** Kotlin 2.4, coroutines + kotlinx-coroutines-test (temps virtuel), Room (DAO existant), Compose BOM 2026.09, DataStore, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-05-lumen-vivant-5.8.0-design.md` (sections 4, 5 et le reste de la section 6)

## Global Constraints

- Aucune nouvelle dépendance Gradle.
- Mémoire : « Ce jour-là » même jour calendaire il y a 1 an puis 2 ans, au moins 3 titres ; « Capsule » : mois entre 6 et 24 mois en arrière, parmi les trois les plus écoutés (somme de `listenedMs`), choisi par graine hebdomadaire (semaine ISO), 25 titres les plus joués, absente sans mois à 10 lectures ; « Premières fois » : première lecture dans les 30 derniers jours, 20 au plus, au moins 3, triées par relectures ; « Albums jamais terminés » : albums d'au moins 5 titres, au moins un joué, moins de 50 % joués, dernière écoute > 30 jours, 5 au plus, les plus anciens d'abord, `songs` = titres non joués.
- Section « Souvenirs » entre « Tes mixs du jour » et « Pour toi » ; carte absente si vide, section absente si aucune ; toucher = lecture, appui long = « Enregistrer en playlist ».
- `SleepTimer` : échéance `deadline = clock() + durationMs`, horloge injectée (`() -> Long`, `SystemClock::elapsedRealtime` en production) ; tick 1 000 ms inchangé ; fin de morceau, fin de chapitre, prolongation inchangées.
- Mode adaptatif : fondu linéaire de 1 à 0,15 sur les 20 dernières minutes puis fondu de fin existant ; pause si immobile depuis N minutes **et** écran éteint (`PowerManager.isInteractive` faux) ; après la pause, 30 s de grâce où une secousse relance et ajoute 15 min ; toute autre secousse pendant la veille ajoute 15 min. N ∈ {5, 10, 20}, réglage `Settings.stillnessPauseMin` (clé `stillness_pause_min`, défaut 10), « Immobilité avant pause » dans le groupe « Minuterie ».
- `StillnessDetector` : variance glissante de la norme d'accélération sur 10 s, seuil 0,05 (m/s²)², `SENSOR_DELAY_NORMAL`, capteur enregistré seulement pendant une veille adaptative.
- `versionCode 20261120`, `versionName "5.8.0"` ; notes `docs/release-notes/v5.8.0.md` complétées (Souvenirs, veille, correction du plantage au démarrage).
- Textes de l'interface en français, tutoiement ; KDoc et commentaires en français, pas de TODO.
- Commandes Gradle lancées depuis PowerShell (`.\gradlew.bat`), jamais depuis Git Bash. Tests JVM : `.\gradlew.bat :app:testDebugUnitTest --tests "<classe>"`.
- Chaque tâche se termine par un commit sur `dev-5.5`. Pas de push.

---

## Structure des fichiers

| Fichier | Rôle |
|---|---|
| `data/model/MemoryEngine.kt` (créer) | `Memory` (scellé) et `MemoryEngine.build(...)`. Pur. |
| `data/repo/MemoryRepository.kt` (créer) | Recalcul quotidien, `memories: StateFlow<List<Memory>>`. |
| `ui/viewmodel/AppViewModel.kt` (modifier) | `memories`, `startSleepTimer(minutes, finish, adaptive)`. |
| `ui/screens/HomeScreen.kt` (modifier) | Section « Souvenirs », `MosaicCard(onLongClick)`. |
| `playback/SleepTimer.kt` (modifier) | Horloge injectée, `adaptive`, `pausedForStillness`, `onStillness()`, `onShake()`, `adaptiveFade()`. |
| `core/util/StillnessDetector.kt` (créer) | Variance glissante, `stillFor(nowMs)`. Pur. |
| `playback/PlaybackService.kt` (modifier) | Capteur d'immobilité, écran, pause/reprise, secousse, `ARG_ADAPTIVE`. |
| `playback/PlayerConnection.kt` (modifier) | `startSleepTimer(durationMs, finish, adaptive)`. |
| `ui/screens/nowplaying/Sheets.kt`, `NowPlayingScreen.kt` (modifier) | Puce « Adaptative » dans `SleepTimerSheet`, passage par `BookSleepTimerSheet`. |
| `core/prefs/SettingsRepository.kt`, `ui/viewmodel/SettingsViewModel.kt`, `ui/screens/SettingsScreen.kt` (modifier) | `stillnessPauseMin`. |
| `app/build.gradle.kts`, `docs/release-notes/v5.8.0.md` (modifier) | Version, notes. |

---

### Task 1 : `Memory` et `MemoryEngine`

**Files:**
- Create: `app/src/main/java/fr/synxio/player/data/model/MemoryEngine.kt`
- Test: `app/src/test/java/fr/synxio/player/data/model/MemoryEngineTest.kt`

**Interfaces:**
- Consumes: `Song`, `Album` (`data/model/Models.kt`), `PlayHistory(song, playedAt: Instant, listenedMs)` (`data/model/SortingExtensions.kt:77`), fabrique de test `fr.synxio.player.song(...)` (`app/src/test/java/fr/synxio/player/TestSongs.kt`, sans paramètre `albumId` : utiliser `.copy(albumId = …)`).
- Produces: `sealed interface Memory { key, title, subtitle, songs, artworkUris }` avec `ThatDay(yearsAgo, songs)`, `TimeCapsule(month: YearMonth, songs)`, `FirstTimes(songs)`, `UnfinishedAlbum(album, playedCount, songs)` ; `object MemoryEngine { fun build(songs: List<Song>, albums: List<Album>, history: List<PlayHistory>, today: LocalDate, zone: ZoneId): List<Memory> }`.

- [ ] **Step 1 : Test**

```kotlin
package fr.synxio.player.data.model

import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class MemoryEngineTest {
    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val today: LocalDate = LocalDate.of(2026, 11, 5)
    private val songs = (1L..40L).map { song(id = it, title = "T$it").copy(albumId = (it - 1) / 10 + 1, track = ((it - 1) % 10 + 1).toInt()) }
    private val albums = (1L..4L).map { a -> Album(id = a, title = "Album $a", artist = "Artiste", artistId = 1, year = 2020, songs = songs.filter { it.albumId == a }) }

    private fun play(id: Long, date: LocalDate, hour: Int = 12, listenedMs: Long = 180_000) =
        PlayHistory(songs[(id - 1).toInt()], date.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant(), listenedMs)

    private fun build(history: List<PlayHistory>, today: LocalDate = this.today) =
        MemoryEngine.build(songs, albums, history, today, zone)

    private inline fun <reified T : Memory> List<Memory>.one(): T? = filterIsInstance<T>().firstOrNull()

    @Test
    fun `ce jour-la - trois titres il y a un an, ordonnes par nombre de lectures`() {
        val d = today.minusYears(1)
        val history = listOf(play(1, d), play(2, d), play(2, d, 13), play(3, d), play(4, d.plusDays(1)))
        val m = build(history).filterIsInstance<Memory.ThatDay>().single()
        assertEquals(1, m.yearsAgo)
        assertEquals(listOf(2L, 1L, 3L), m.songs.map { it.id })
        assertEquals("thatday-1", m.key)
    }

    @Test
    fun `ce jour-la - moins de trois titres, pas de carte`() {
        val d = today.minusYears(2)
        assertNull(build(listOf(play(1, d), play(2, d))).one<Memory.ThatDay>())
    }

    @Test
    fun `capsule - mois le plus ecoute entre 6 et 24 mois, 25 titres max, stable dans la semaine`() {
        val month = YearMonth.of(2025, 9)
        val history = (1L..30L).flatMap { id -> List(2) { play(id, month.atDay(3)) } } +
            (1L..12L).map { play(it, today.minusMonths(2).withDayOfMonth(2)) }  // trop récent
        val a = build(history).one<Memory.TimeCapsule>()!!
        assertEquals(month, a.month)
        assertEquals(25, a.songs.size)
        val b = build(history, today.plusDays(2)).one<Memory.TimeCapsule>()!!
        assertEquals(a.month, b.month)
        assertEquals("capsule-2025-09", a.key)
    }

    @Test
    fun `capsule - absente sans mois a dix lectures`() {
        val history = (1L..9L).map { play(it, today.minusMonths(8).withDayOfMonth(1)) }
        assertNull(build(history).one<Memory.TimeCapsule>())
    }

    @Test
    fun `premieres fois - premiere lecture dans les 30 jours, triees par relectures`() {
        val recent = today.minusDays(10)
        val history = listOf(
            play(5, today.minusDays(60)), play(5, recent),  // déjà connu : exclu
            play(6, recent), play(6, recent, 14), play(6, recent, 15),
            play(7, recent), play(7, recent, 14),
            play(8, recent),
        )
        val m = build(history).one<Memory.FirstTimes>()!!
        assertEquals(listOf(6L, 7L, 8L), m.songs.map { it.id })
    }

    @Test
    fun `albums jamais termines - entames, moins de la moitie joues, pas ecoutes depuis 30 jours`() {
        val old = today.minusDays(45)
        val history = listOf(
            play(1, old), play(2, old),                      // album 1 : 2/10 joués, ancien → carte
            play(11, old), play(12, old), play(13, old), play(14, old), play(15, old), // album 2 : 5/10 → exclu
            play(21, today.minusDays(3)),                     // album 3 : récent → exclu
        )
        val cards = build(history).filterIsInstance<Memory.UnfinishedAlbum>()
        assertEquals(listOf(1L), cards.map { it.album.id })
        assertEquals(2, cards.single().playedCount)
        assertEquals((3L..10L).toList(), cards.single().songs.map { it.id })
        assertTrue(cards.single().subtitle.contains("2 titres sur 10"))
    }

    @Test
    fun `historique vide - aucune carte`() {
        assertTrue(build(emptyList()).isEmpty())
    }
}
```

- [ ] **Step 2 : Vérifier l'échec**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.data.model.MemoryEngineTest"`
Attendu : échec de compilation.

- [ ] **Step 3 : `MemoryEngine.kt`**

```kotlin
package fr.synxio.player.data.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * Une carte « Souvenirs » de l'accueil : une liste de titres à lancer, avec un titre et
 * un sous-titre. Les clés sont stables dans la journée (clés de liste Compose).
 */
sealed interface Memory {
    val key: String
    val title: String
    val subtitle: String
    val songs: List<Song>

    /** Mosaïque : quatre pochettes d'albums distincts, comme les mixs du jour. */
    val artworkUris: List<Any> get() = songs.asSequence().distinctBy { it.albumId }.take(4).map { it.artworkUri }.toList()

    data class ThatDay(val yearsAgo: Int, override val songs: List<Song>) : Memory {
        override val key = "thatday-$yearsAgo"
        override val title = "Ce jour-là, il y a $yearsAgo an" + if (yearsAgo > 1) "s" else ""
        override val subtitle = "${songs.size} titres écoutés ce jour-là"
    }

    data class TimeCapsule(val month: YearMonth, override val songs: List<Song>) : Memory {
        override val key = "capsule-%04d-%02d".format(month.year, month.monthValue)
        override val title = "Capsule : ${month.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)} ${month.year}"
        override val subtitle = "${songs.size} titres les plus écoutés ce mois-là"
    }

    data class FirstTimes(override val songs: List<Song>) : Memory {
        override val key = "firsttimes"
        override val title = "Tes premières fois du mois"
        override val subtitle = "${songs.size} découvertes que tu as réécoutées"
    }

    data class UnfinishedAlbum(val album: Album, val playedCount: Int, override val songs: List<Song>) : Memory {
        override val key = "album-${album.id}"
        override val title = "Finir ${album.title}"
        override val subtitle = "$playedCount titres sur ${album.songCount} écoutés"
    }
}

/**
 * Compose les souvenirs à partir de l'historique. Fonction pure : la date du jour et le
 * fuseau sont passés en argument, rien n'est stocké.
 */
object MemoryEngine {

    fun build(songs: List<Song>, albums: List<Album>, history: List<PlayHistory>, today: LocalDate, zone: ZoneId): List<Memory> {
        if (history.isEmpty()) return emptyList()
        val byId = songs.associateBy { it.id }
        val plays = history.filter { it.song.id in byId }.map { Play(it.song.id, it.playedAt.atZone(zone).toLocalDate(), it.listenedMs) }
        return buildList {
            thatDay(plays, byId, today, 1)?.let(::add)
            thatDay(plays, byId, today, 2)?.let(::add)
            timeCapsule(plays, byId, today)?.let(::add)
            firstTimes(plays, byId, today)?.let(::add)
            addAll(unfinishedAlbums(plays, albums, today))
        }
    }

    private class Play(val songId: Long, val date: LocalDate, val listenedMs: Long)

    private fun thatDay(plays: List<Play>, byId: Map<Long, Song>, today: LocalDate, yearsAgo: Int): Memory.ThatDay? {
        val date = today.minusYears(yearsAgo.toLong())
        val ids = plays.filter { it.date == date }
            .groupingBy { it.songId }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        if (ids.size < MIN_CARD) return null
        return Memory.ThatDay(yearsAgo, ids.mapNotNull(byId::get))
    }

    private fun timeCapsule(plays: List<Play>, byId: Map<Long, Song>, today: LocalDate): Memory.TimeCapsule? {
        val newest = YearMonth.from(today.minusMonths(CAPSULE_MIN_MONTHS))
        val oldest = YearMonth.from(today.minusMonths(CAPSULE_MAX_MONTHS))
        val byMonth = plays.groupBy { YearMonth.from(it.date) }
            .filterKeys { it in oldest..newest }
            .filterValues { it.size >= CAPSULE_MIN_PLAYS }
        if (byMonth.isEmpty()) return null
        val top = byMonth.entries.sortedByDescending { e -> e.value.sumOf { it.listenedMs } }.take(3)
        val week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + 100 * today.get(IsoFields.WEEK_BASED_YEAR)
        val chosen = top[week % top.size]
        val ids = chosen.value.groupingBy { it.songId }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .take(CAPSULE_SIZE).map { it.key }
        return Memory.TimeCapsule(chosen.key, ids.mapNotNull(byId::get))
    }

    private fun firstTimes(plays: List<Play>, byId: Map<Long, Song>, today: LocalDate): Memory.FirstTimes? {
        val since = today.minusDays(FIRST_TIMES_DAYS)
        val firstPlay = plays.groupBy { it.songId }.mapValues { (_, p) -> p.minOf { it.date } }
        val counts = plays.groupingBy { it.songId }.eachCount()
        val ids = firstPlay.filterValues { it >= since }.keys
            .sortedWith(compareByDescending<Long> { counts.getValue(it) }.thenBy { it })
            .take(FIRST_TIMES_SIZE)
        if (ids.size < MIN_CARD) return null
        return Memory.FirstTimes(ids.mapNotNull(byId::get))
    }

    private fun unfinishedAlbums(plays: List<Play>, albums: List<Album>, today: LocalDate): List<Memory.UnfinishedAlbum> {
        val cutoff = today.minusDays(UNFINISHED_IDLE_DAYS)
        val lastPlay = plays.groupBy { it.songId }.mapValues { (_, p) -> p.maxOf { it.date } }
        return albums.asSequence()
            .filter { it.songCount >= UNFINISHED_MIN_SONGS }
            .mapNotNull { album ->
                val played = album.songs.filter { it.id in lastPlay }
                if (played.isEmpty() || played.size * 2 >= album.songCount) return@mapNotNull null
                val last = played.maxOf { lastPlay.getValue(it.id) }
                if (last >= cutoff) return@mapNotNull null
                val remaining = album.songs.filter { it.id !in lastPlay }.sortedWith(compareBy({ it.disc }, { it.track }))
                Triple(album, played.size, remaining) to last
            }
            .sortedBy { it.second }
            .take(UNFINISHED_MAX)
            .map { (t, _) -> Memory.UnfinishedAlbum(t.first, t.second, t.third) }
            .toList()
    }

    private const val MIN_CARD = 3
    private const val CAPSULE_MIN_MONTHS = 6L
    private const val CAPSULE_MAX_MONTHS = 24L
    private const val CAPSULE_MIN_PLAYS = 10
    private const val CAPSULE_SIZE = 25
    private const val FIRST_TIMES_DAYS = 30L
    private const val FIRST_TIMES_SIZE = 20
    private const val UNFINISHED_MIN_SONGS = 5
    private const val UNFINISHED_IDLE_DAYS = 30L
    private const val UNFINISHED_MAX = 5
}
```

`Song.artworkUri` touche `android.net.Uri` : `artworkUris` n'est jamais appelé par les tests JVM.

- [ ] **Step 4 : Vérifier le succès**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.data.model.MemoryEngineTest"`
Attendu : 7 tests verts. Si « capsule » échoue sur `a.month` : vérifier que `today.minusMonths(2)` tombe bien hors de `[oldest, newest]` (septembre 2025 est à 14 mois, dans la fenêtre).

- [ ] **Step 5 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/data/model/MemoryEngine.kt app/src/test/java/fr/synxio/player/data/model/MemoryEngineTest.kt
git commit -m "souvenirs: moteur pur (ce jour-là, capsule, premières fois, albums à finir)"
```

---

### Task 2 : `MemoryRepository` et section « Souvenirs »

**Files:**
- Create: `app/src/main/java/fr/synxio/player/data/repo/MemoryRepository.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/viewmodel/AppViewModel.kt:67-84,280`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/HomeScreen.kt:130,305-335,803-820`

**Interfaces:**
- Consumes: `MemoryEngine.build`, `MusicRepository.library` (`Library.songs`, `Library.albums`), `HistoryRepository.history: StateFlow<List<PlayHistory>>`, `AppViewModel.play(songs)`, `AppViewModel.createPlaylist(name, songs)`.
- Produces: `@Singleton class MemoryRepository` avec `val memories: StateFlow<List<Memory>>` ; `AppViewModel.memories` ; `MosaicCard(..., onLongClick: (() -> Unit)? = null)`.

- [ ] **Step 1 : `MemoryRepository.kt`**

```kotlin
package fr.synxio.player.data.repo

import fr.synxio.player.data.model.Memory
import fr.synxio.player.data.model.MemoryEngine
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Les souvenirs de l'accueil, recalculés quand l'historique ou la bibliothèque change, et à minuit. */
@Singleton
class MemoryRepository @Inject constructor(
    private val musicRepository: MusicRepository,
    private val historyRepository: HistoryRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val memories: StateFlow<List<Memory>> = combine(
        musicRepository.library.map { it.songs to it.albums }.distinctUntilChanged { a, b -> a.first === b.first && a.second === b.second },
        historyRepository.history,
        localDays(),
    ) { (songs, albums), history, _ -> Triple(songs, albums, history) }
        .debounce(500)
        .mapLatest { (songs, albums, history) ->
            if (songs.isEmpty() || history.isEmpty()) emptyList()
            else MemoryEngine.build(songs, albums, history, LocalDate.now(), ZoneId.systemDefault())
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Numéro du jour local, réémis juste après minuit : les cartes changent avec la date. */
    private fun localDays(): Flow<Long> = flow {
        while (true) {
            emit(LocalDate.now().toEpochDay())
            val untilMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay())
            delay(untilMidnight.toMillis() + 1_000)
        }
    }
}
```

(`debounce` et `mapLatest` sont `@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)` : reprendre les annotations de `DailyMixRepository`.)

- [ ] **Step 2 : `AppViewModel`**

Constructeur : ajouter `memoryRepository: fr.synxio.player.data.repo.MemoryRepository,` (sans `private val`, comme `dailyMixRepository`). Après `val dailyMixes` (`:280`) :

```kotlin
    val memories: StateFlow<List<fr.synxio.player.data.model.Memory>> = memoryRepository.memories
```

- [ ] **Step 3 : `HomeScreen`**

Collecte, à côté de `dailyMixes` (`:130`) : `val memories by viewModel.memories.collectAsStateWithLifecycle()`.

Après le bloc `if (dailyMixes.isNotEmpty()) { ... }` (qui se termit avant `if (smartPlaylists.isNotEmpty())`) :

```kotlin
        if (memories.isNotEmpty()) {
            val index = order++
            item(key = "s-memories", contentType = "smartSection") {
                HomeSection(
                    title = "Souvenirs",
                    eyebrow = "L'app se souvient avec toi",
                    modifier = Modifier.enterCascade(index, "home-memories"),
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        itemsIndexed(memories, key = { _, it -> it.key }) { i, memory ->
                            MosaicCard(
                                title = memory.title,
                                subtitle = memory.subtitle,
                                artworkUris = memory.artworkUris,
                                onClickLabel = "Écouter",
                                onClick = { viewModel.play(memory.songs) },
                                // Appui long : la carte devient une playlist, nommée comme elle.
                                onLongClick = { viewModel.createPlaylist(memory.title, memory.songs) },
                                modifier = Modifier
                                    .animateItem()
                                    .enterCascade(i, "memory-${memory.key}"),
                            )
                        }
                    }
                }
            }
        }
```

`MosaicCard` (`:803`) : ajouter `onLongClick: (() -> Unit)? = null` et remplacer `.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)` par `.combinedClickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClick?.let { "Enregistrer en playlist" })` (import `androidx.compose.foundation.combinedClickable` ; l'API est stable depuis Compose 1.8, pas d'`@OptIn` nécessaire — si le compilateur en réclame un, ajouter `@OptIn(ExperimentalFoundationApi::class)` sur `MosaicCard`). Vérifier que `createPlaylist` affiche un message ; sinon ajouter `emit("Playlist « ${memory.title} » créée")` dans le lambda via la fonction `emit` du ViewModel si elle est publique, ou laisser silencieux.

- [ ] **Step 4 : Compiler, tests**

PowerShell : `.\gradlew.bat :app:compileDebugKotlin` puis `.\gradlew.bat :app:testDebugUnitTest`
Attendu : OK.

- [ ] **Step 5 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/data/repo/MemoryRepository.kt app/src/main/java/fr/synxio/player/ui/viewmodel/AppViewModel.kt app/src/main/java/fr/synxio/player/ui/screens/HomeScreen.kt
git commit -m "souvenirs: dépôt quotidien et section de l'accueil"
```

---

### Task 3 : `SleepTimer` à échéance, mode adaptatif, `StillnessDetector`

**Files:**
- Modify: `app/src/main/java/fr/synxio/player/playback/SleepTimer.kt` (fichier entier)
- Create: `app/src/main/java/fr/synxio/player/core/util/StillnessDetector.kt`
- Test: `app/src/test/java/fr/synxio/player/playback/SleepTimerTest.kt`, `app/src/test/java/fr/synxio/player/core/util/StillnessDetectorTest.kt`

**Interfaces:**
- Produces: `SleepTimerState(active, remainingMs, finishCurrentTrack, adaptive: Boolean = false, pausedForStillness: Boolean = false)` avec `fadeMultiplier` ; `fun adaptiveFade(remainingMs: Long): Float` (pure, top niveau) ; `class SleepTimer(scope, clock: () -> Long)` avec `@Inject constructor(scope)`, `start(durationMs, finishCurrentTrack, adaptive = false)`, `extend(extraMs)`, `cancel()`, `shouldStopAtTrackEnd()`, `setCallback(onElapsed)`, `clearCallback`, `setStillnessCallbacks(onPause: () -> Unit, onResume: () -> Unit)`, `onStillness()`, `onShake()` ; `class StillnessDetector` avec `fun sample(x: Float, y: Float, z: Float, nowMs: Long)` et `fun stillFor(nowMs: Long): Long`.

- [ ] **Step 1 : Tests**

```kotlin
package fr.synxio.player.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private var now = 0L
    private var elapsed = 0
    private var paused = 0
    private var resumed = 0

    private fun kotlinx.coroutines.test.TestScope.timer() = SleepTimer(backgroundScope) { now }.apply {
        setCallback { elapsed++ }
        setStillnessCallbacks({ paused++ }, { resumed++ })
    }

    @Test
    fun `echeance exacte malgre des ticks en retard`() = runTest {
        val t = timer()
        t.start(10_000, finishCurrentTrack = false)
        now = 7_000; advanceTimeBy(1_001)
        assertEquals(3_000L, t.state.value.remainingMs)
        now = 10_000; advanceTimeBy(1_001)
        assertEquals(1, elapsed)
        assertFalse(t.state.value.active)
    }

    @Test
    fun `prolongation - repart de l'echeance restante`() = runTest {
        val t = timer()
        t.start(10_000, false)
        now = 4_000; advanceTimeBy(1_001)
        t.extend(5_000)
        assertEquals(11_000L, t.state.value.remainingMs)
    }

    @Test
    fun `fondu adaptatif - 1 a 20 min, 0,15 a 60 s, puis fondu de fin`() {
        assertEquals(1f, adaptiveFade(25 * 60_000L), 1e-4f)
        assertEquals(1f, adaptiveFade(20 * 60_000L), 1e-4f)
        assertEquals(0.15f, adaptiveFade(60_000L), 1e-4f)
        assertEquals(0.075f, adaptiveFade(30_000L), 1e-4f)
        assertEquals(0.575f, adaptiveFade(10 * 60_000L + 30_000L), 1e-3f)
    }

    @Test
    fun `fadeMultiplier - adaptatif suit adaptiveFade, classique inchange`() {
        assertEquals(0.15f, SleepTimerState(active = true, remainingMs = 60_000, adaptive = true).fadeMultiplier, 1e-4f)
        assertEquals(1f, SleepTimerState(active = true, remainingMs = 60_000 + 1, adaptive = false).fadeMultiplier, 1e-4f)
        assertEquals(0.5f, SleepTimerState(active = true, remainingMs = 30_000, adaptive = false).fadeMultiplier, 1e-4f)
    }

    @Test
    fun `immobilite - pause, puis arret apres 30 s de grace`() = runTest {
        val t = timer()
        t.start(60 * 60_000L, false, adaptive = true)
        t.onStillness()
        assertEquals(1, paused)
        assertTrue(t.state.value.pausedForStillness)
        now = 31_000; advanceTimeBy(31_001)
        assertEquals(1, elapsed)
        assertFalse(t.state.value.active)
    }

    @Test
    fun `secousse pendant la grace - reprise et 15 min de plus`() = runTest {
        val t = timer()
        t.start(60 * 60_000L, false, adaptive = true)
        t.onStillness()
        now = 10_000; advanceTimeBy(10_001)
        t.onShake()
        assertEquals(1, resumed)
        assertFalse(t.state.value.pausedForStillness)
        assertEquals(60 * 60_000L - 10_000L + 15 * 60_000L, t.state.value.remainingMs)
        now = 40_000; advanceTimeBy(30_001)
        assertEquals(0, elapsed)
    }

    @Test
    fun `secousse hors grace - 15 min de plus, pas en mode classique`() = runTest {
        val t = timer()
        t.start(10 * 60_000L, false, adaptive = true)
        t.onShake()
        assertEquals(25 * 60_000L, t.state.value.remainingMs)
        t.cancel()
        t.start(10 * 60_000L, false, adaptive = false)
        t.onShake()
        assertEquals(10 * 60_000L, t.state.value.remainingMs)
    }

    @Test
    fun `immobilite en mode classique - ignoree`() = runTest {
        val t = timer()
        t.start(10 * 60_000L, false)
        t.onStillness()
        assertEquals(0, paused)
    }
}
```

```kotlin
package fr.synxio.player.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StillnessDetectorTest {
    @Test
    fun `immobile - stillFor grandit apres la fenetre de 10 s`() {
        val d = StillnessDetector()
        for (s in 0..20) d.sample(0.01f * (s % 2), 0f, 9.81f, s * 1_000L)
        assertTrue(d.stillFor(20_000L) >= 10_000L)
    }

    @Test
    fun `mouvement - remis a zero`() {
        val d = StillnessDetector()
        for (s in 0..20) d.sample(0f, 0f, 9.81f, s * 1_000L)
        // Une vraie secousse : 6 m/s² de plus sur un axe, la variance de la fenêtre dépasse le seuil.
        d.sample(6f, 0f, 9.81f, 21_000L)
        assertEquals(0L, d.stillFor(21_000L))
        for (s in 22..40) d.sample(0f, 0f, 9.81f, s * 1_000L)
        assertTrue(d.stillFor(40_000L) in 8_000L..20_000L)
    }

    @Test
    fun `sans echantillon - zero`() {
        assertEquals(0L, StillnessDetector().stillFor(5_000L))
    }
}
```

- [ ] **Step 2 : Vérifier l'échec**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.playback.SleepTimerTest" --tests "fr.synxio.player.core.util.StillnessDetectorTest"`
Attendu : échec de compilation.

- [ ] **Step 3 : `SleepTimer.kt` (fichier entier)**

```kotlin
package fr.synxio.player.playback

import android.os.SystemClock
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class SleepTimerState(
    val active: Boolean = false,
    val remainingMs: Long = 0,
    val finishCurrentTrack: Boolean = false,
    /** Veille adaptative : fondu long, pause sur immobilité, secousse pour prolonger. */
    val adaptive: Boolean = false,
    /** Lecture mise en pause par l'immobilité ; une secousse dans les 30 s relance. */
    val pausedForStillness: Boolean = false,
) {
    /** Multiplicateur de volume. Classique : 1 → 0 dans la dernière minute. Adaptatif : [adaptiveFade]. */
    val fadeMultiplier: Float
        get() = when {
            !active || finishCurrentTrack -> 1f
            adaptive -> adaptiveFade(remainingMs)
            remainingMs <= FINAL_FADE_MS -> (remainingMs / FINAL_FADE_MS.toFloat()).coerceIn(0f, 1f)
            else -> 1f
        }
}

/** Fondu adaptatif : 1 à 20 min, linéaire jusqu'à 0,15 à 60 s, puis le fondu de fin jusqu'à 0. */
fun adaptiveFade(remainingMs: Long): Float = when {
    remainingMs >= ADAPTIVE_FADE_MS -> 1f
    remainingMs > FINAL_FADE_MS -> ADAPTIVE_FLOOR + (1f - ADAPTIVE_FLOOR) * (remainingMs - FINAL_FADE_MS).toFloat() / (ADAPTIVE_FADE_MS - FINAL_FADE_MS)
    else -> ADAPTIVE_FLOOR * (remainingMs / FINAL_FADE_MS.toFloat()).coerceIn(0f, 1f)
}

const val FINAL_FADE_MS = 60_000L
const val ADAPTIVE_FADE_MS = 20 * 60_000L
const val ADAPTIVE_FLOOR = 0.15f

/**
 * Minuterie de veille. Trois modes :
 *  - durée fixe (« dans 30 min ») ;
 *  - « à la fin du morceau » : le service coupe sur l'événement de fin de piste ;
 *  - adaptative : fondu sur 20 min, pause quand le téléphone est immobile écran éteint
 *    (le service appelle [onStillness]), secousse qui relance ou prolonge ([onShake]).
 *
 * L'échéance est un instant `elapsedRealtime` : des ticks en retard (Doze) ne la décalent pas.
 */
@Singleton
class SleepTimer(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    @Inject
    constructor(@ApplicationScope scope: CoroutineScope) : this(scope, SystemClock::elapsedRealtime)

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null
    private var graceJob: Job? = null
    private var deadline = 0L
    private var onElapsed: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onResume: (() -> Unit)? = null

    fun setCallback(callback: () -> Unit) {
        onElapsed = callback
    }

    /** Détache le rappel du service détruit, que ce singleton retenait sinon. */
    fun clearCallback(callback: () -> Unit) {
        if (onElapsed === callback) onElapsed = null
    }

    fun setStillnessCallbacks(onPause: () -> Unit, onResume: () -> Unit) {
        this.onPause = onPause
        this.onResume = onResume
    }

    fun start(durationMs: Long, finishCurrentTrack: Boolean, adaptive: Boolean = false) {
        cancel()
        deadline = clock() + durationMs
        _state.value = SleepTimerState(true, durationMs, finishCurrentTrack, adaptive)
        job = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val remaining = (deadline - clock()).coerceAtLeast(0)
                _state.value = _state.value.copy(remainingMs = remaining)
                if (remaining <= 0) break
            }
            if (isActive && !finishCurrentTrack) {
                // Pause d'abord : réinitialiser l'état remet le gain de veille à 1, ce qui
                // ferait remonter le volume d'un coup avant l'arrêt.
                onElapsed?.invoke()
                _state.value = SleepTimerState()
            }
            // En mode « fin de morceau », on laisse l'état actif : c'est le service
            // qui déclenchera l'arrêt à la transition de piste suivante.
        }
    }

    /** Prolonge la minuterie en cours (bouton « +5 min » de la notification, secousse). */
    fun extend(extraMs: Long) {
        val current = _state.value
        if (!current.active) return
        val remaining = (deadline - clock()).coerceAtLeast(0)
        start(remaining + extraMs, current.finishCurrentTrack, current.adaptive)
    }

    /** Vrai si la minuterie est arrivée à zéro en mode « fin de morceau ». */
    fun shouldStopAtTrackEnd(): Boolean =
        _state.value.active && _state.value.finishCurrentTrack && _state.value.remainingMs <= 0

    /** Le service a constaté l'immobilité écran éteint : pause, puis arrêt après la grâce. */
    fun onStillness() {
        val current = _state.value
        if (!current.active || !current.adaptive || current.pausedForStillness) return
        _state.value = current.copy(pausedForStillness = true)
        onPause?.invoke()
        graceJob = scope.launch {
            delay(GRACE_MS)
            if (_state.value.pausedForStillness) {
                onElapsed?.invoke()
                cancel()
            }
        }
    }

    /** Secousse : relance si la lecture est en pause de grâce, et ajoute 15 min dans tous les cas adaptatifs. */
    fun onShake() {
        val current = _state.value
        if (!current.active || !current.adaptive) return
        if (current.pausedForStillness) {
            graceJob?.cancel()
            graceJob = null
            _state.value = current.copy(pausedForStillness = false)
            onResume?.invoke()
        }
        extend(SHAKE_EXTEND_MS)
    }

    fun cancel() {
        job?.cancel()
        job = null
        graceJob?.cancel()
        graceJob = null
        _state.value = SleepTimerState()
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val GRACE_MS = 30_000L
        const val SHAKE_EXTEND_MS = 15 * 60_000L
    }
}
```

`extend` relance `start`, qui appelle `cancel()` et remet `pausedForStillness` à faux : c'est voulu (après une secousse, la lecture repart).

- [ ] **Step 4 : `StillnessDetector.kt`**

```kotlin
package fr.synxio.player.core.util

import kotlin.math.sqrt

/**
 * Détecte un téléphone immobile : variance glissante de la norme de l'accélération sur
 * 10 s, sous 0,05 (m/s²)². Pur : les échantillons et l'horloge sont passés en argument.
 * [stillFor] donne depuis combien de millisecondes la variance est sous le seuil.
 */
class StillnessDetector {
    private val times = ArrayDeque<Long>()
    private val mags = ArrayDeque<Float>()
    private var stillSinceMs = -1L

    fun sample(x: Float, y: Float, z: Float, nowMs: Long) {
        times.addLast(nowMs)
        mags.addLast(sqrt(x * x + y * y + z * z))
        while (times.isNotEmpty() && nowMs - times.first() > WINDOW_MS) {
            times.removeFirst()
            mags.removeFirst()
        }
        val still = mags.size >= 2 && variance() < THRESHOLD
        stillSinceMs = when {
            !still -> -1L
            stillSinceMs < 0 -> nowMs
            else -> stillSinceMs
        }
    }

    /** Millisecondes d'immobilité, 0 sans échantillon ou après un mouvement. */
    fun stillFor(nowMs: Long): Long = if (stillSinceMs < 0) 0L else (nowMs - stillSinceMs).coerceAtLeast(0L)

    private fun variance(): Float {
        val mean = mags.sum() / mags.size
        return mags.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / mags.size
    }

    private companion object {
        const val WINDOW_MS = 10_000L
        const val THRESHOLD = 0.05f
    }
}
```

- [ ] **Step 5 : Vérifier le succès**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest --tests "fr.synxio.player.playback.SleepTimerTest" --tests "fr.synxio.player.core.util.StillnessDetectorTest"` puis `.\gradlew.bat :app:compileDebugKotlin`
Attendu : 11 tests verts ; compilation : `PlaybackService` appelle encore `sleepTimer.start(durationMs, finishCurrentTrack)` (paramètre par défaut) et `SleepTimerState(active, remaining, finish)` : toujours valide.

- [ ] **Step 6 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/playback/SleepTimer.kt app/src/main/java/fr/synxio/player/core/util/StillnessDetector.kt app/src/test/java/fr/synxio/player/playback/SleepTimerTest.kt app/src/test/java/fr/synxio/player/core/util/StillnessDetectorTest.kt
git commit -m "veille: échéance exacte, mode adaptatif, détecteur d'immobilité"
```

---

### Task 4 : Câblage service, commande, feuille, réglage

**Files:**
- Modify: `app/src/main/java/fr/synxio/player/playback/PlaybackService.kt:229-230,288-307,393,427-437,480-491,882-884,1529-1532,1917-1918`
- Modify: `app/src/main/java/fr/synxio/player/playback/PlayerConnection.kt:657-663`
- Modify: `app/src/main/java/fr/synxio/player/ui/viewmodel/AppViewModel.kt:661-664`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/nowplaying/Sheets.kt:590-700` (`SleepTimerSheet`)
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/nowplaying/NowPlayingScreen.kt:1335-1360` (`BookSleepTimerSheet`)
- Modify: `app/src/main/java/fr/synxio/player/core/prefs/SettingsRepository.kt` (après `stopAfterInactivityMin` : `:86,250,377,548`), `app/src/main/java/fr/synxio/player/ui/viewmodel/SettingsViewModel.kt`, `app/src/main/java/fr/synxio/player/ui/screens/SettingsScreen.kt:655-685`

**Interfaces:**
- Consumes: `SleepTimer.start(durationMs, finish, adaptive)`, `onStillness()`, `onShake()`, `setStillnessCallbacks`, `StillnessDetector`, `Settings.stillnessPauseMin`.
- Produces: `PlaybackService.ARG_ADAPTIVE = "adaptive"` ; `PlayerConnection.startSleepTimer(durationMs, finishCurrentTrack, adaptive = false)` ; `AppViewModel.startSleepTimer(minutes, finishTrack, adaptive = false)` ; `SleepTimerSheet(onStart: (Int, Boolean, Boolean) -> Unit, ...)` ; `Settings.stillnessPauseMin: Int = 10`, `setStillnessPauseMin`.

- [ ] **Step 1 : Réglage**

`SettingsRepository.kt` : après `val stopAfterInactivityMin: Int = 0,` ajouter

```kotlin
    /** Veille adaptative : minutes d'immobilité, écran éteint, avant la pause (5, 10 ou 20). */
    val stillnessPauseMin: Int = 10,
```

clé `val STILLNESS_PAUSE_MIN = intPreferencesKey("stillness_pause_min")`, lecture `stillnessPauseMin = p[Keys.STILLNESS_PAUSE_MIN] ?: 10,`, setter `suspend fun setStillnessPauseMin(value: Int) = put(Keys.STILLNESS_PAUSE_MIN, value)`. `SettingsViewModel` : `fun setStillnessPauseMin(value: Int) = update { settings.setStillnessPauseMin(value) }`.

`SettingsScreen.kt`, dans `group(3, "sleep", "Minuterie")`, après le bloc « Arrêter après inactivité » et son `Reveal` :

```kotlin
                            GroupDivider()
                            ChipSetting(
                                title = "Immobilité avant pause",
                                options = listOf("5 min", "10 min", "20 min"),
                                selectedIndex = listOf(5, 10, 20).indexOf(settings.stillnessPauseMin).coerceAtLeast(0),
                                onSelect = { settingsViewModel.setStillnessPauseMin(listOf(5, 10, 20)[it]) },
                                icon = Icons.Rounded.Bedtime,
                            )
```

(`ChipSetting` existe, `:1565` ; vérifier l'import de `Icons.Rounded.Bedtime`.)

- [ ] **Step 2 : Commande et connexion**

`PlaybackService.kt` : constante `const val ARG_ADAPTIVE = "adaptive"` à côté de `ARG_FINISH_TRACK` ; `CMD_SLEEP_TIMER` :

```kotlin
                CMD_SLEEP_TIMER -> sleepTimer.start(
                    durationMs = args.getLong(ARG_DURATION_MS, 30 * 60_000L),
                    finishCurrentTrack = args.getBoolean(ARG_FINISH_TRACK, false),
                    adaptive = args.getBoolean(ARG_ADAPTIVE, false),
                )
```

`PlayerConnection.kt:657` :

```kotlin
    fun startSleepTimer(durationMs: Long, finishCurrentTrack: Boolean, adaptive: Boolean = false) = sendCommand(
        PlaybackService.CMD_SLEEP_TIMER,
        Bundle().apply {
            putLong(PlaybackService.ARG_DURATION_MS, durationMs)
            putBoolean(PlaybackService.ARG_FINISH_TRACK, finishCurrentTrack)
            putBoolean(PlaybackService.ARG_ADAPTIVE, adaptive)
        },
    )
```

`AppViewModel.kt:661` :

```kotlin
    fun startSleepTimer(minutes: Int, finishTrack: Boolean, adaptive: Boolean = false) {
        player.startSleepTimer(minutes * 60_000L, finishTrack, adaptive)
        emit(if (adaptive) "Veille adaptative, $minutes min au plus" else "Veille dans $minutes min")
    }
```

- [ ] **Step 3 : Service — immobilité, écran, secousse**

Champs (à côté de `shakeDetector`, `:230`) :

```kotlin
    private val stillness = fr.synxio.player.core.util.StillnessDetector()
    private val stillnessListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            stillness.sample(event.values[0], event.values[1], event.values[2], android.os.SystemClock.elapsedRealtime())
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
    }
    private var stillnessJob: kotlinx.coroutines.Job? = null
```

Dans `onCreate`, après `sleepTimer.setCallback(sleepTimerCallback)` :

```kotlin
        sleepTimer.setStillnessCallbacks(
            onPause = { serviceScope.launch { activePlayer.pause() } },
            onResume = { serviceScope.launch { activePlayer.play() } },
        )
```

Remplacer la collecte `sleepTimer.state.collect { state -> fade.sleepGain = state.fadeMultiplier }` par :

```kotlin
        serviceScope.launch {
            var adaptiveOn = false
            sleepTimer.state.collect { state ->
                fade.sleepGain = state.fadeMultiplier
                val wantSensor = state.active && state.adaptive
                if (wantSensor && !adaptiveOn) startStillnessWatch()
                if (!wantSensor && adaptiveOn) stopStillnessWatch()
                adaptiveOn = wantSensor
            }
        }
```

et les deux fonctions privées :

```kotlin
    /** Veille adaptative : accéléromètre à cadence normale et vérification toutes les 15 s. */
    private fun startStillnessWatch() {
        val sensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager?.registerListener(stillnessListener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        // La secousse prolonge la veille même si « Secouer pour passer » est désactivé.
        sensorManager?.registerListener(shakeDetector, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        val power = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        stillnessJob = serviceScope.launch {
            while (kotlinx.coroutines.isActive) {
                kotlinx.coroutines.delay(STILLNESS_CHECK_MS)
                val limit = settingsRepository.settings.first().stillnessPauseMin * 60_000L
                if (!power.isInteractive && stillness.stillFor(android.os.SystemClock.elapsedRealtime()) >= limit) {
                    sleepTimer.onStillness()
                }
            }
        }
    }

    private fun stopStillnessWatch() {
        stillnessJob?.cancel()
        stillnessJob = null
        sensorManager?.unregisterListener(stillnessListener)
        if (!currentSettings.shakeToSkip) sensorManager?.unregisterListener(shakeDetector)
    }
```

(`currentSettings` : la valeur courante des réglages telle que le service la garde ; si le service n'a qu'un `collect` dans `observeSettings`, y mémoriser le dernier `Settings` dans un champ `private var currentSettings = Settings()` et l'utiliser ici. `settingsRepository.settings` est un `Flow` : `first()` est une lecture ponctuelle.) Constante `private const val STILLNESS_CHECK_MS = 15_000L` dans le `companion object`.

Secousse (`:304`) : le rappel du `ShakeDetector` devient

```kotlin
        shakeDetector = fr.synxio.player.core.util.ShakeDetector {
            val sleep = sleepTimer.state.value
            if (sleep.active && sleep.adaptive) {
                sleepTimer.onShake()
            } else if (activePlayer.hasNextMediaItem()) {
                activePlayer.seekToNextMediaItem()
            }
        }
```

`onDestroy` (`:427-437`) : ajouter `stopStillnessWatch()` avant `sleepTimer.cancel()`.

- [ ] **Step 4 : Feuille**

`SleepTimerSheet` : signature `onStart: (Int, Boolean, Boolean) -> Unit` ; état `var adaptive by remember { mutableStateOf(false) }` ; après la `Row` des presets de durée, une ligne de bascule sur le modèle de « Finir le morceau en cours » :

```kotlin
            Spacer(Modifier.height(Space.md))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .clickable(role = Role.Switch) { adaptive = !adaptive },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Veille adaptative", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Fondu sur 20 min, pause si le téléphone ne bouge plus écran éteint, secoue pour prolonger",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = adaptive, onCheckedChange = null)
            }
```

Le texte d'intro de la feuille devient : `if (adaptive) "La durée choisie est le maximum : la lecture peut s'arrêter avant, dès que tu t'endors." else "Le volume diminue progressivement 60 secondes avant l'arrêt."`. L'état actif affiche « (adaptative) » après la durée quand `state.adaptive`. `BrandButton` : `onClick = { onStart(minutes, finishTrack, adaptive); onDismiss() }`.

`BookSleepTimerSheet` (`NowPlayingScreen.kt:~1350`) : `onStart = { minutes, finishTrack, adaptive -> viewModel.startSleepTimer(minutes, finishTrack, adaptive) }`. Tout autre appel de `SleepTimerSheet` dans le projet (chercher `SleepTimerSheet(`) adapte de même.

- [ ] **Step 5 : Compiler, tests**

PowerShell : `.\gradlew.bat :app:compileDebugKotlin` puis `.\gradlew.bat :app:testDebugUnitTest`
Attendu : OK. Le comportement capteur/écran se vérifie sur l'appareil (checklist, Task 5).

- [ ] **Step 6 : Commit**

```bash
git add app/src/main/java/fr/synxio/player/playback/PlaybackService.kt app/src/main/java/fr/synxio/player/playback/PlayerConnection.kt app/src/main/java/fr/synxio/player/ui/viewmodel/AppViewModel.kt app/src/main/java/fr/synxio/player/ui/screens/nowplaying/Sheets.kt app/src/main/java/fr/synxio/player/ui/screens/nowplaying/NowPlayingScreen.kt app/src/main/java/fr/synxio/player/core/prefs/SettingsRepository.kt app/src/main/java/fr/synxio/player/ui/viewmodel/SettingsViewModel.kt app/src/main/java/fr/synxio/player/ui/screens/SettingsScreen.kt
git commit -m "veille: capteur d'immobilité, secousse, feuille « Veille adaptative », réglage"
```

---

### Task 5 : Version 5.8.0, notes, graphe, vérification finale

**Files:**
- Modify: `app/build.gradle.kts:32-33`
- Modify: `docs/release-notes/v5.8.0.md`

- [ ] **Step 1 : Version**

`versionCode = 20261120` et `versionName = "5.8.0"`.

- [ ] **Step 2 : Notes**

Sous « Nouveautés », après le paragraphe sur les shaders :

```markdown
- **Souvenirs** sur l'accueil : « Ce jour-là, il y a un an », une capsule d'un mois passé,
  tes premières fois du mois, et les albums que tu n'as jamais finis. Toucher lance, un
  appui long enregistre la carte en playlist. Les cartes apparaissent avec le temps : il
  faut un historique.
- **Veille adaptative** : dans la minuterie, un interrupteur. Le volume descend doucement
  sur les 20 dernières minutes, la lecture se met en pause si le téléphone ne bouge plus
  écran éteint (délai réglable : 5, 10 ou 20 min), et une secousse relance ou ajoute
  15 minutes. L'échéance de la minuterie est désormais exacte, même en veille profonde.

### Corrections
- Plantage possible au démarrage depuis la 5.6.1 (migration des secrets lancée trop tôt).
```

Checklist, ajouter :

```markdown
- [ ] Accueil : section « Souvenirs » (au moins « Albums jamais terminés » ou « Premières fois » selon l'historique) ; toucher lance, appui long crée la playlist.
- [ ] Veille adaptative 30 min, téléphone posé écran éteint : pause après le délai réglé ; secousse dans les 30 s : reprise + 15 min ; sans secousse : arrêt. Fondu audible sur les 20 dernières minutes.
- [ ] Veille classique : comportement inchangé, +5 min depuis la notification.
```

- [ ] **Step 3 : Suite, graphe, commit**

PowerShell : `.\gradlew.bat :app:testDebugUnitTest` ; Git Bash : `graphify update .`

```bash
git add app/build.gradle.kts docs/release-notes/v5.8.0.md
git commit -m "chore: version 5.8.0, notes souvenirs et veille"
```

---

## Auto-revue

- **Couverture du spec.** §4.1 règles des quatre cartes → Task 1 (constantes `MIN_CARD`, `CAPSULE_*`, `FIRST_TIMES_*`, `UNFINISHED_*`) ; §4.2 dépôt quotidien, section entre mixs et « Pour toi », cartes jouables et enregistrables → Task 2 ; §4.3 tests → Task 1. §5.1 horloge injectée → Task 3 ; §5.2 puce, fondu long, pause sur immobilité, grâce 30 s, secousse, réglage N → Tasks 3-4 ; §5.3 `StillnessDetector`, capteur pendant la veille seulement, `isInteractive` → Tasks 3-4 ; §5.4 tests → Task 3. §6 réglage d'immobilité, version, notes → Tasks 4-5.
- **Écarts assumés.** Le spec met « Adaptative » en puce ; une ligne à interrupteur est plus lisible avec son explication, même geste. La prolongation par secousse réutilise `extend`, qui redémarre la minuterie (et donc la lecture) : conforme au « relance et ajoute 15 min ».
- **Placeholders.** Aucun. `currentSettings` dans le service est nommé avec sa règle de repli explicite.
- **Cohérence des types.** `SleepTimerState.adaptive/pausedForStillness` utilisés par le service et la feuille ; `adaptiveFade` testé et appelé par `fadeMultiplier` ; `startSleepTimer(minutes, finishTrack, adaptive)` de bout en bout (feuille → ViewModel → `PlayerConnection` → `ARG_ADAPTIVE` → `SleepTimer.start`) ; `Memory.key` utilisé comme clé de `LazyRow` ; `MemoryRepository.memories` → `AppViewModel.memories` → `HomeScreen`.
