# Playlists intelligentes, doublons et partage — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter à Synxio Player un moteur de playlists intelligentes (six presets + éditeur de règles), une détection de doublons avec suppression manuelle, et le partage d'une playlist en `.m3u8` / texte / fichiers audio avec import à la réception.

**Architecture:** Les deux moteurs (règles, doublons) sont des objets Kotlin purs, sans dépendance Android, alimentés par la bibliothèque déjà tenue en mémoire par `MusicRepository`. Les repositories les branchent sur les `Flow` Room existants ; l'UI Compose ne fait que consommer des `StateFlow`. Le partage réutilise le format M3U déjà écrit dans `PlaylistRepository`.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (BOM 2024.12.01), Material 3, Room 2.6.1 + KSP, Hilt 2.53.1, kotlinx.serialization 1.7.3, Media3 1.5.1, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-10-playlists-intelligentes-doublons-partage-design.md`

## Global Constraints

- `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`. Tout appel d'API ≥ 30 exige un `Build.VERSION.SDK_INT` avec chemin de repli.
- **Interdiction absolue de `fallbackToDestructiveMigration()`.** Il est présent aujourd'hui dans `AppModule.kt:47` et doit être supprimé en Task 4 : le laisser efface playlists, favoris, statistiques et historique de l'utilisateur.
- **Unités de temps, à ne jamais confondre :** `PlayStatEntity.lastPlayedAt` est en **millisecondes** (`System.currentTimeMillis()`, cf. `MusicRepository.kt:247`). `Song.dateAddedSec` et `Song.dateModifiedSec` sont en **secondes**. `PlayHistoryEntity.playedAt` est en **secondes**.
- Toute la langue visible par l'utilisateur est le **français**, y compris les libellés, messages d'erreur et noms de presets. Les commentaires de code sont en français, comme le reste du dépôt.
- `exportSchema = true` : chaque changement de version Room produit un fichier dans `app/schemas/` qui **doit être committé**.
- Ne pas modifier `gradle.properties`, `build.gradle.kts` (racine) ni `settings.gradle.kts` pour des raisons de poste local — ces fichiers sont partagés avec un autre poste (Task 1 explique où mettre les surcharges).
- Les playlists calculées sont **en lecture seule** : ni réordonnancement, ni suppression d'un titre.

---

## Structure des fichiers

**Créés :**

| Fichier | Responsabilité |
|---|---|
| `data/model/SmartPlaylist.kt` | Types du modèle de règles (champs, opérateurs, tri, id) |
| `data/model/SmartPlaylistEngine.kt` | Évaluation pure : filtrer, trier, limiter |
| `data/model/SmartPlaylistPresets.kt` | Les six presets, en dur |
| `data/model/DuplicateDetector.kt` | Normalisation, regroupement, choix du meilleur exemplaire |
| `data/db/SmartPlaylistDao.kt` | Accès Room aux playlists stockées |
| `data/repo/SmartPlaylistRepository.kt` | Croise règles + bibliothèque + stats + favoris |
| `data/repo/DuplicateRepository.kt` | Scan des doublons et suppression MediaStore |
| `data/repo/ShareRepository.kt` | Construction des intents de partage |
| `ui/screens/SmartPlaylistEditorScreen.kt` | Éditeur de règles |
| `ui/screens/DuplicatesScreen.kt` | Écran de dédoublonnage |
| `ui/viewmodel/SmartPlaylistViewModel.kt` | État de l'éditeur |
| `ui/viewmodel/DuplicatesViewModel.kt` | État du scan et de la sélection |
| `res/xml/file_paths.xml` | Périmètre exposé par le `FileProvider` |
| `test/.../SmartPlaylistEngineTest.kt` | Tests du moteur de règles |
| `test/.../DuplicateDetectorTest.kt` | Tests de la détection |
| `test/.../TestSongs.kt` | Fabrique de `Song` pour les tests |

**Modifiés :** `data/db/Entities.kt`, `data/db/SynxioDatabase.kt`, `di/AppModule.kt`, `ui/SynxioRoot.kt`, `ui/screens/PlaylistsScreen.kt`, `ui/screens/SettingsScreen.kt`, `ui/screens/DetailScreens.kt`, `ui/viewmodel/AppViewModel.kt`, `ui/components/SongMenuHost.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`.

---

### Task 1: Rendre le projet constructible et testable sur ce poste

Le dépôt pointe sur un toolchain absent (`C:\Users\y.gomez\android-dev`) et n'a **aucune** infrastructure de test : pas de dossier `src/test`, pas une seule dépendance de test. Rien d'autre n'est possible tant que ce n'est pas réglé.

Les chemins du poste sont corrigés **hors du dépôt** : Gradle donne la priorité à `~/.gradle/gradle.properties` sur le `gradle.properties` du projet, et `buildDirRoot` est lu via `providers.gradleProperty` (`build.gradle.kts:13`), donc la surcharge fonctionne sans toucher un fichier versionné.

**Files:**
- Create: `C:\Users\Forza PC\.gradle\gradle.properties` (hors dépôt, non versionné)
- Create: `local.properties` (déjà dans `.gitignore` d'un projet Android standard — vérifier)
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts` (bloc `dependencies`)
- Create: `app/src/test/java/fr/synxio/player/TestSongs.kt`

**Interfaces:**
- Consumes: rien
- Produces: `fun song(id: Long = 1, title: String = "Titre", artist: String = "Artiste", album: String = "Album", albumArtist: String? = null, genre: String? = null, durationMs: Long = 180_000, track: Int = 1, disc: Int = 1, year: Int = 2020, dateAddedSec: Long = 0, sizeBytes: Long = 5_000_000, mimeType: String = "audio/mpeg", path: String = "/music/$id.mp3"): Song` — utilisée par tous les tests des Tasks 2 et 3.

- [ ] **Step 1: Vérifier que le SDK et le JDK sont là**

```bash
ls "C:/Users/Forza PC/AppData/Local/Android/Sdk/platforms"
```

Attendu : `android-36`. Si absent, s'arrêter et le signaler — rien ne compilera.

- [ ] **Step 2: Écrire les surcharges de poste**

`local.properties` à la racine du dépôt :

```properties
sdk.dir=C\:\\Users\\Forza PC\\AppData\\Local\\Android\\Sdk
```

`C:\Users\Forza PC\.gradle\gradle.properties` (créer le dossier si besoin). Le `tmpdir` du dépôt pointe sur un chemin inexistant ; on le retire en redéfinissant les deux lignes, et on sort les dossiers de build de OneDrive vers un chemin local valable :

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx2048m
buildDirRoot=C:/Users/Forza PC/AppData/Local/synxio-build
```

- [ ] **Step 3: Vérifier que le build passe**

```bash
./gradlew.bat assembleDebug
```

Attendu : `BUILD SUCCESSFUL`. Premier lancement long (téléchargement de Gradle et des dépendances, dont jaudiotagger via jitpack).

Si l'erreur `Unable to establish loopback connection` réapparaît, c'est le problème AF_UNIX décrit dans le README : ajouter `-Djdk.net.unixdomain.tmpdir=C:/Users/Forza PC/AppData/Local/synxio-build/tmp` aux deux lignes `jvmargs` ci-dessus, après avoir créé ce dossier.

- [ ] **Step 4: Ajouter les dépendances de test**

Dans `gradle/libs.versions.toml`, section `[versions]` :

```toml
junit = "4.13.2"
```

Section `[libraries]` :

```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
```

Dans `app/build.gradle.kts`, à la fin du bloc `dependencies` :

```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 5: Écrire la fabrique de morceaux de test**

`app/src/test/java/fr/synxio/player/TestSongs.kt` — `Song` a 17 champs obligatoires ; sans fabrique, chaque test deviendrait illisible.

```kotlin
package fr.synxio.player

import fr.synxio.player.data.model.Song

/**
 * Fabrique de [Song] pour les tests : seuls les champs qui comptent pour le test
 * examiné sont nommés à l'appel, le reste prend une valeur neutre.
 */
fun song(
    id: Long = 1,
    title: String = "Titre",
    artist: String = "Artiste",
    album: String = "Album",
    albumArtist: String? = null,
    genre: String? = null,
    durationMs: Long = 180_000,
    track: Int = 1,
    disc: Int = 1,
    year: Int = 2020,
    dateAddedSec: Long = 0,
    sizeBytes: Long = 5_000_000,
    mimeType: String = "audio/mpeg",
    path: String = "/music/$id.mp3",
): Song = Song(
    id = id,
    title = title,
    artist = artist,
    artistId = 1,
    album = album,
    albumId = 1,
    albumArtist = albumArtist,
    genre = genre,
    durationMs = durationMs,
    track = track,
    disc = disc,
    year = year,
    dateAddedSec = dateAddedSec,
    dateModifiedSec = dateAddedSec,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    path = path,
)
```

- [ ] **Step 6: Écrire un test de fumée pour prouver que l'infra marche**

`app/src/test/java/fr/synxio/player/TestSongsTest.kt` :

```kotlin
package fr.synxio.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TestSongsTest {
    @Test
    fun `la fabrique applique les valeurs par defaut et les surcharges`() {
        val s = song(id = 7, title = "Ainsi bas la vida")
        assertEquals(7L, s.id)
        assertEquals("Ainsi bas la vida", s.title)
        assertEquals("/music/7.mp3", s.path)
        assertEquals(180_000L, s.durationMs)
    }
}
```

- [ ] **Step 7: Lancer les tests**

```bash
./gradlew.bat :app:testDebugUnitTest
```

Attendu : `BUILD SUCCESSFUL`, 1 test passé.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/test
git commit -m "test: mettre en place l'infrastructure de tests unitaires"
```

`local.properties` et `~/.gradle/gradle.properties` ne sont **pas** committés — vérifier avec `git status` qu'ils n'apparaissent pas.

---

### Task 2: Modèle et moteur de règles

Cœur de la fonctionnalité, sans une ligne d'Android : entièrement testable en JVM.

**Files:**
- Create: `app/src/main/java/fr/synxio/player/data/model/SmartPlaylist.kt`
- Create: `app/src/main/java/fr/synxio/player/data/model/SmartPlaylistEngine.kt`
- Test: `app/src/test/java/fr/synxio/player/data/model/SmartPlaylistEngineTest.kt`

**Interfaces:**
- Consumes: `song(...)` (Task 1), `Song`, `PlayStatEntity`
- Produces:
  - `enum class RuleField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, YEAR, DURATION, PLAY_COUNT, SKIP_COUNT, LAST_PLAYED, DATE_ADDED, FOLDER, FAVORITE, FORMAT, BITRATE }` avec `val label: String` et `val operators: List<RuleOperator>`
  - `enum class RuleOperator { CONTAINS, NOT_CONTAINS, EQUALS, NOT_EQUALS, STARTS_WITH, GREATER_THAN, LESS_THAN, IN_LAST_DAYS, NOT_IN_LAST_DAYS, IS_TRUE, IS_FALSE }` avec `val label: String`
  - `data class SmartRule(field, operator, value: String)`
  - `enum class SmartSort` avec `val label: String`
  - `data class SmartPlaylistRules(rules, matchAll, sort, descending, limit)`
  - `sealed interface SmartPlaylistId` + `SmartPlaylistId.Preset(key)` / `SmartPlaylistId.Stored(id)` + `encode()` / `SmartPlaylistId.decode(String)`
  - `SmartPlaylistEngine.evaluate(rules, songs, stats: Map<Long, PlayStatEntity>, favorites: Set<Long>, now: Long): List<Song>`
  - `SmartPlaylistRules.toJson(): String` / `SmartPlaylistRules.Companion.fromJson(String): SmartPlaylistRules`

- [ ] **Step 1: Écrire les tests qui échouent**

`app/src/test/java/fr/synxio/player/data/model/SmartPlaylistEngineTest.kt` :

```kotlin
package fr.synxio.player.data.model

import fr.synxio.player.data.db.PlayStatEntity
import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlaylistEngineTest {

    private val now = 1_700_000_000_000L   // millisecondes
    private val dayMs = 86_400_000L

    private fun rules(
        vararg rule: SmartRule,
        matchAll: Boolean = true,
        sort: SmartSort = SmartSort.TITLE,
        descending: Boolean = false,
        limit: Int? = null,
    ) = SmartPlaylistRules(rule.toList(), matchAll, sort, descending, limit)

    private fun stat(songId: Long, playCount: Int = 0, skipCount: Int = 0, lastPlayedAt: Long = 0) =
        songId to PlayStatEntity(songId, "/music/$songId.mp3", playCount, skipCount, lastPlayedAt, 0)

    @Test
    fun `contains est insensible a la casse`() {
        val songs = listOf(song(id = 1, title = "Ainsi bas la vida"), song(id = 2, title = "Macarena"))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.TITLE, RuleOperator.CONTAINS, "AINSI")),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `matchAll false combine les regles en OU`() {
        val songs = listOf(
            song(id = 1, artist = "Damso"),
            song(id = 2, artist = "Orelsan"),
            song(id = 3, artist = "Nekfeu"),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(
                SmartRule(RuleField.ARTIST, RuleOperator.EQUALS, "Damso"),
                SmartRule(RuleField.ARTIST, RuleOperator.EQUALS, "Orelsan"),
                matchAll = false,
            ),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L, 2L), result.map { it.id }.sorted())
    }

    @Test
    fun `matchAll true exige toutes les regles`() {
        val songs = listOf(
            song(id = 1, artist = "Damso", year = 2020),
            song(id = 2, artist = "Damso", year = 2017),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(
                SmartRule(RuleField.ARTIST, RuleOperator.EQUALS, "Damso"),
                SmartRule(RuleField.YEAR, RuleOperator.GREATER_THAN, "2018"),
            ),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `play count lit les statistiques et traite l'absence comme zero`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val stats = mapOf(stat(1, playCount = 5))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "2")),
            songs, stats, emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `jamais ecoute remonte les morceaux sans statistique`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val stats = mapOf(stat(1, playCount = 3))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.PLAY_COUNT, RuleOperator.LESS_THAN, "1")),
            songs, stats, emptySet(), now,
        )
        assertEquals(listOf(2L), result.map { it.id })
    }

    // lastPlayedAt est en MILLISECONDES (MusicRepository.kt:247)
    @Test
    fun `in last days compare lastPlayedAt en millisecondes`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val stats = mapOf(
            stat(1, playCount = 1, lastPlayedAt = now - 5 * dayMs),
            stat(2, playCount = 1, lastPlayedAt = now - 40 * dayMs),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.LAST_PLAYED, RuleOperator.IN_LAST_DAYS, "30")),
            songs, stats, emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `not in last days ignore les morceaux jamais joues`() {
        // lastPlayedAt = 0 signifie « jamais joué », pas « joué en 1970 » :
        // le preset « Oubliés » ne doit pas se remplir de morceaux jamais écoutés.
        val songs = listOf(song(id = 1), song(id = 2))
        val stats = mapOf(stat(1, playCount = 1, lastPlayedAt = now - 300 * dayMs))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, "180")),
            songs, stats, emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    // dateAddedSec est en SECONDES (Song.kt)
    @Test
    fun `date added compare des secondes`() {
        val nowSec = now / 1000
        val songs = listOf(
            song(id = 1, dateAddedSec = nowSec - 5 * 86_400),
            song(id = 2, dateAddedSec = nowSec - 60 * 86_400),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.DATE_ADDED, RuleOperator.IN_LAST_DAYS, "30")),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `favori lit l'ensemble des favoris`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.FAVORITE, RuleOperator.IS_TRUE, "")),
            songs, emptyMap(), setOf(2L), now,
        )
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `duration compare des secondes`() {
        val songs = listOf(song(id = 1, durationMs = 120_000), song(id = 2, durationMs = 400_000))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.DURATION, RuleOperator.GREATER_THAN, "300")),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `folder compare le dossier parent`() {
        val songs = listOf(
            song(id = 1, path = "/storage/Music/Rap/a.mp3"),
            song(id = 2, path = "/storage/Music/Jazz/b.mp3"),
        )
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.FOLDER, RuleOperator.CONTAINS, "Rap")),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `tri decroissant et limite`() {
        val songs = (1L..5L).map { song(id = it, year = 2000 + it.toInt()) }
        val result = SmartPlaylistEngine.evaluate(
            rules(sort = SmartSort.YEAR, descending = true, limit = 2),
            songs, emptyMap(), emptySet(), now,
        )
        assertEquals(listOf(5L, 4L), result.map { it.id })
    }

    @Test
    fun `aucune regle retourne toute la bibliotheque`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val result = SmartPlaylistEngine.evaluate(rules(), songs, emptyMap(), emptySet(), now)
        assertEquals(2, result.size)
    }

    @Test
    fun `une valeur numerique illisible ne fait echouer que sa regle`() {
        val songs = listOf(song(id = 1), song(id = 2))
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.YEAR, RuleOperator.GREATER_THAN, "pas un nombre")),
            songs, emptyMap(), emptySet(), now,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `bibliotheque vide ne plante pas`() {
        val result = SmartPlaylistEngine.evaluate(
            rules(SmartRule(RuleField.TITLE, RuleOperator.CONTAINS, "x")),
            emptyList(), emptyMap(), emptySet(), now,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `le tri aleatoire est stable pour un meme jour`() {
        val songs = (1L..20L).map { song(id = it) }
        val r = rules(sort = SmartSort.RANDOM)
        val a = SmartPlaylistEngine.evaluate(r, songs, emptyMap(), emptySet(), now)
        val b = SmartPlaylistEngine.evaluate(r, songs, emptyMap(), emptySet(), now + 1000)
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun `les regles survivent a un aller-retour json`() {
        val original = rules(
            SmartRule(RuleField.ARTIST, RuleOperator.CONTAINS, "Damso"),
            SmartRule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "3"),
            matchAll = false, sort = SmartSort.PLAY_COUNT, descending = true, limit = 50,
        )
        assertEquals(original, SmartPlaylistRules.fromJson(original.toJson()))
    }

    @Test
    fun `un json corrompu retombe sur des regles vides`() {
        assertEquals(SmartPlaylistRules(), SmartPlaylistRules.fromJson("{pas du json"))
    }

    @Test
    fun `encode et decode d'un identifiant`() {
        assertEquals(SmartPlaylistId.Preset("recent"), SmartPlaylistId.decode("preset:recent"))
        assertEquals(SmartPlaylistId.Stored(12), SmartPlaylistId.decode("stored:12"))
        assertEquals("preset:recent", SmartPlaylistId.Preset("recent").encode())
        assertEquals("stored:12", SmartPlaylistId.Stored(12).encode())
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*SmartPlaylistEngineTest*"
```

Attendu : ÉCHEC de compilation — `Unresolved reference: SmartPlaylistEngine`.

- [ ] **Step 3: Écrire le modèle**

`app/src/main/java/fr/synxio/player/data/model/SmartPlaylist.kt` :

```kotlin
package fr.synxio.player.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ce sur quoi une règle porte. [operators] borne ce que l'éditeur a le droit de proposer :
 * « favori contient » ne doit jamais pouvoir être composé.
 *
 * `operators` est une propriété **calculée** et non un paramètre du constructeur : des
 * constantes top-level passées aux entrées d'enum seraient encore nulles si `RuleField`
 * est chargée avant les propriétés du fichier.
 */
enum class RuleField(val label: String) {
    TITLE("Titre"),
    ARTIST("Artiste"),
    ALBUM("Album"),
    ALBUM_ARTIST("Artiste de l'album"),
    GENRE("Genre"),
    FOLDER("Dossier"),
    FORMAT("Format"),
    YEAR("Année"),
    DURATION("Durée (s)"),
    PLAY_COUNT("Nombre de lectures"),
    SKIP_COUNT("Nombre de passages"),
    BITRATE("Débit (kbps)"),
    LAST_PLAYED("Dernière écoute"),
    DATE_ADDED("Date d'ajout"),
    FAVORITE("Favori");

    val operators: List<RuleOperator>
        get() = when (this) {
            TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, FOLDER, FORMAT -> TEXT_OPS
            YEAR, DURATION, PLAY_COUNT, SKIP_COUNT, BITRATE -> NUMBER_OPS
            LAST_PLAYED, DATE_ADDED -> DATE_OPS
            FAVORITE -> BOOL_OPS
        }
}

enum class RuleOperator(val label: String) {
    CONTAINS("contient"),
    NOT_CONTAINS("ne contient pas"),
    EQUALS("est"),
    NOT_EQUALS("n'est pas"),
    STARTS_WITH("commence par"),
    GREATER_THAN("est supérieur à"),
    LESS_THAN("est inférieur à"),
    IN_LAST_DAYS("dans les N derniers jours"),
    NOT_IN_LAST_DAYS("pas depuis N jours"),
    IS_TRUE("oui"),
    IS_FALSE("non"),
}

private val TEXT_OPS = listOf(
    RuleOperator.CONTAINS, RuleOperator.NOT_CONTAINS,
    RuleOperator.EQUALS, RuleOperator.NOT_EQUALS, RuleOperator.STARTS_WITH,
)
private val NUMBER_OPS = listOf(
    RuleOperator.EQUALS, RuleOperator.NOT_EQUALS,
    RuleOperator.GREATER_THAN, RuleOperator.LESS_THAN,
)
private val DATE_OPS = listOf(RuleOperator.IN_LAST_DAYS, RuleOperator.NOT_IN_LAST_DAYS)
private val BOOL_OPS = listOf(RuleOperator.IS_TRUE, RuleOperator.IS_FALSE)

@Serializable
data class SmartRule(
    val field: RuleField,
    val operator: RuleOperator,
    val value: String,
)

enum class SmartSort(val label: String) {
    TITLE("Titre"),
    ARTIST("Artiste"),
    ALBUM("Album"),
    YEAR("Année"),
    DATE_ADDED("Date d'ajout"),
    PLAY_COUNT("Nombre de lectures"),
    LAST_PLAYED("Dernière écoute"),
    RANDOM("Aléatoire"),
}

@Serializable
data class SmartPlaylistRules(
    val rules: List<SmartRule> = emptyList(),
    /** true = toutes les règles (ET), false = au moins une (OU). */
    val matchAll: Boolean = true,
    val sort: SmartSort = SmartSort.TITLE,
    val descending: Boolean = false,
    val limit: Int? = null,
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * Un JSON illisible (base éditée à la main, format changé entre deux versions)
         * ne doit pas faire planter l'écran des playlists : on retombe sur des règles
         * vides, l'utilisateur voit une playlist sans critère et peut la corriger.
         */
        fun fromJson(json: String): SmartPlaylistRules =
            runCatching { JSON.decodeFromString(serializer(), json) }
                .getOrDefault(SmartPlaylistRules())
    }
}

/**
 * Les presets ne vivent pas en base mais doivent être navigables comme les playlists
 * stockées : cet identifiant unifie les deux dans les routes.
 */
sealed interface SmartPlaylistId {
    data class Preset(val key: String) : SmartPlaylistId
    data class Stored(val id: Long) : SmartPlaylistId

    fun encode(): String = when (this) {
        is Preset -> "preset:$key"
        is Stored -> "stored:$id"
    }

    companion object {
        fun decode(value: String): SmartPlaylistId = when {
            value.startsWith("stored:") ->
                Stored(value.removePrefix("stored:").toLongOrNull() ?: -1L)
            else -> Preset(value.removePrefix("preset:"))
        }
    }
}

/** Une playlist intelligente prête à afficher : ses règles et son résultat courant. */
data class SmartPlaylist(
    val id: SmartPlaylistId,
    val name: String,
    val rules: SmartPlaylistRules,
    val songs: List<Song>,
) {
    val isPreset: Boolean get() = id is SmartPlaylistId.Preset
    val songCount: Int get() = songs.size
}
```

- [ ] **Step 4: Écrire le moteur**

`app/src/main/java/fr/synxio/player/data/model/SmartPlaylistEngine.kt` :

```kotlin
package fr.synxio.player.data.model

import fr.synxio.player.data.db.PlayStatEntity
import kotlin.random.Random

/**
 * Évaluation d'un jeu de règles contre la bibliothèque.
 *
 * Volontairement sans dépendance Android : c'est la pièce qui mérite le plus d'être
 * testée, autant qu'elle tourne en JVM pure.
 *
 * Attention aux unités : [PlayStatEntity.lastPlayedAt] est en millisecondes,
 * [Song.dateAddedSec] en secondes. Les mélanger décale les comparaisons d'un facteur 1000.
 */
object SmartPlaylistEngine {

    private const val DAY_MS = 86_400_000L

    fun evaluate(
        rules: SmartPlaylistRules,
        songs: List<Song>,
        stats: Map<Long, PlayStatEntity>,
        favorites: Set<Long>,
        now: Long,
    ): List<Song> {
        val matching = when {
            rules.rules.isEmpty() -> songs
            rules.matchAll -> songs.filter { s ->
                rules.rules.all { matches(it, s, stats[s.id], s.id in favorites, now) }
            }
            else -> songs.filter { s ->
                rules.rules.any { matches(it, s, stats[s.id], s.id in favorites, now) }
            }
        }
        val sorted = sort(matching, rules, stats, now)
        return rules.limit?.takeIf { it > 0 }?.let(sorted::take) ?: sorted
    }

    /** Nombre de morceaux correspondants, pour l'aperçu live de l'éditeur. */
    fun count(
        rules: SmartPlaylistRules,
        songs: List<Song>,
        stats: Map<Long, PlayStatEntity>,
        favorites: Set<Long>,
        now: Long,
    ): Int = evaluate(rules.copy(limit = null), songs, stats, favorites, now).size

    private fun matches(
        rule: SmartRule,
        song: Song,
        stat: PlayStatEntity?,
        isFavorite: Boolean,
        now: Long,
    ): Boolean = when (rule.field) {
        RuleField.TITLE -> text(rule, song.title)
        RuleField.ARTIST -> text(rule, song.artist)
        RuleField.ALBUM -> text(rule, song.album)
        RuleField.ALBUM_ARTIST -> text(rule, song.albumArtist.orEmpty())
        RuleField.GENRE -> text(rule, song.genre.orEmpty())
        RuleField.FOLDER -> text(rule, song.folderPath)
        RuleField.FORMAT -> text(rule, song.extension)

        RuleField.YEAR -> number(rule, song.year.toLong())
        RuleField.DURATION -> number(rule, song.durationMs / 1000)
        RuleField.PLAY_COUNT -> number(rule, (stat?.playCount ?: 0).toLong())
        RuleField.SKIP_COUNT -> number(rule, (stat?.skipCount ?: 0).toLong())
        RuleField.BITRATE -> number(rule, song.approxBitrateKbps.toLong())

        RuleField.LAST_PLAYED -> date(rule, stat?.lastPlayedAt ?: 0L, now)
        RuleField.DATE_ADDED -> date(rule, song.dateAddedSec * 1000L, now)

        RuleField.FAVORITE -> when (rule.operator) {
            RuleOperator.IS_TRUE -> isFavorite
            RuleOperator.IS_FALSE -> !isFavorite
            else -> false
        }
    }

    private fun text(rule: SmartRule, actual: String): Boolean {
        val a = actual.lowercase()
        val b = rule.value.trim().lowercase()
        return when (rule.operator) {
            RuleOperator.CONTAINS -> a.contains(b)
            RuleOperator.NOT_CONTAINS -> !a.contains(b)
            RuleOperator.EQUALS -> a == b
            RuleOperator.NOT_EQUALS -> a != b
            RuleOperator.STARTS_WITH -> a.startsWith(b)
            else -> false
        }
    }

    /** Une valeur non numérique ne fait échouer que sa propre règle. */
    private fun number(rule: SmartRule, actual: Long): Boolean {
        val expected = rule.value.trim().toLongOrNull() ?: return false
        return when (rule.operator) {
            RuleOperator.EQUALS -> actual == expected
            RuleOperator.NOT_EQUALS -> actual != expected
            RuleOperator.GREATER_THAN -> actual > expected
            RuleOperator.LESS_THAN -> actual < expected
            else -> false
        }
    }

    /**
     * [timestampMs] à 0 signifie « jamais » : un morceau jamais joué n'est pas
     * « oublié depuis 180 jours », il n'a simplement pas d'historique.
     */
    private fun date(rule: SmartRule, timestampMs: Long, now: Long): Boolean {
        val days = rule.value.trim().toLongOrNull() ?: return false
        if (timestampMs <= 0L) return false
        val threshold = now - days * DAY_MS
        return when (rule.operator) {
            RuleOperator.IN_LAST_DAYS -> timestampMs >= threshold
            RuleOperator.NOT_IN_LAST_DAYS -> timestampMs < threshold
            else -> false
        }
    }

    private fun sort(
        songs: List<Song>,
        rules: SmartPlaylistRules,
        stats: Map<Long, PlayStatEntity>,
        now: Long,
    ): List<Song> {
        if (rules.sort == SmartSort.RANDOM) {
            // Graine sur le jour courant : la playlist reste stable pendant la session
            // au lieu de se réordonner à chaque recomposition.
            return songs.shuffled(Random(now / DAY_MS))
        }
        val comparator: Comparator<Song> = when (rules.sort) {
            SmartSort.TITLE -> compareBy { it.title.lowercase() }
            SmartSort.ARTIST -> compareBy { it.artist.lowercase() }
            SmartSort.ALBUM -> compareBy { it.album.lowercase() }
            SmartSort.YEAR -> compareBy { it.year }
            SmartSort.DATE_ADDED -> compareBy { it.dateAddedSec }
            SmartSort.PLAY_COUNT -> compareBy { stats[it.id]?.playCount ?: 0 }
            SmartSort.LAST_PLAYED -> compareBy { stats[it.id]?.lastPlayedAt ?: 0L }
            SmartSort.RANDOM -> compareBy { it.id }   // inatteignable, exhaustivité du when
        }
        return songs.sortedWith(if (rules.descending) comparator.reversed() else comparator)
    }
}
```

- [ ] **Step 5: Lancer les tests**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*SmartPlaylistEngineTest*"
```

Attendu : PASS, 18 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/fr/synxio/player/data/model/SmartPlaylist.kt app/src/main/java/fr/synxio/player/data/model/SmartPlaylistEngine.kt app/src/test
git commit -m "feat: moteur de regles des playlists intelligentes"
```

---

### Task 3: Détection de doublons

Deuxième moteur pur. La normalisation est le seul endroit qui décide si un fichier peut être proposé à la suppression : elle mérite tous les tests.

**Files:**
- Create: `app/src/main/java/fr/synxio/player/data/model/DuplicateDetector.kt`
- Test: `app/src/test/java/fr/synxio/player/data/model/DuplicateDetectorTest.kt`

**Interfaces:**
- Consumes: `song(...)` (Task 1), `Song`
- Produces:
  - `data class DuplicateGroup(val songs: List<Song>, val keepIndex: Int)` avec `val keeper: Song`, `val removable: List<Song>`, `val reclaimableBytes: Long`
  - `DuplicateDetector.normalize(text: String): String`
  - `DuplicateDetector.findGroups(songs: List<Song>, toleranceMs: Long = 3_000): List<DuplicateGroup>`

- [ ] **Step 1: Écrire les tests qui échouent**

`app/src/test/java/fr/synxio/player/data/model/DuplicateDetectorTest.kt` :

```kotlin
package fr.synxio.player.data.model

import fr.synxio.player.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    @Test
    fun `la normalisation retire accents casse et ponctuation`() {
        assertEquals("elleestdouee", DuplicateDetector.normalize("Elle est douée !"))
        assertEquals("cestlavie", DuplicateDetector.normalize("C'est la vie..."))
    }

    @Test
    fun `la normalisation retire les suffixes parasites`() {
        val base = DuplicateDetector.normalize("Formidable")
        assertEquals(base, DuplicateDetector.normalize("Formidable (Remastered 2011)"))
        assertEquals(base, DuplicateDetector.normalize("Formidable [Live]"))
        assertEquals(base, DuplicateDetector.normalize("Formidable - Radio Edit"))
    }

    @Test
    fun `la normalisation retire les mentions feat`() {
        val base = DuplicateDetector.normalize("Bruxelles")
        assertEquals(base, DuplicateDetector.normalize("Bruxelles feat. Damso"))
        assertEquals(base, DuplicateDetector.normalize("Bruxelles ft. Damso"))
    }

    @Test
    fun `deux copies du meme morceau forment un groupe`() {
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", path = "/a/1.mp3"),
            song(id = 2, title = "Formidable ", artist = "stromae", path = "/b/2.mp3"),
        )
        val groups = DuplicateDetector.findGroups(songs)
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().songs.size)
    }

    @Test
    fun `un morceau unique ne forme pas de groupe`() {
        val groups = DuplicateDetector.findGroups(listOf(song(id = 1), song(id = 2, title = "Autre")))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `un live de duree differente n'est pas un doublon`() {
        // Le garde-fou central : même titre, même artiste, mais 40 s d'écart.
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", durationMs = 210_000),
            song(id = 2, title = "Formidable", artist = "Stromae", durationMs = 250_000),
        )
        assertTrue(DuplicateDetector.findGroups(songs).isEmpty())
    }

    @Test
    fun `un ecart de deux secondes reste un doublon`() {
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", durationMs = 210_000),
            song(id = 2, title = "Formidable", artist = "Stromae", durationMs = 212_000),
        )
        assertEquals(1, DuplicateDetector.findGroups(songs).size)
    }

    @Test
    fun `le meme titre par deux artistes differents n'est pas un doublon`() {
        val songs = listOf(
            song(id = 1, title = "Hello", artist = "Adele"),
            song(id = 2, title = "Hello", artist = "Lionel Richie"),
        )
        assertTrue(DuplicateDetector.findGroups(songs).isEmpty())
    }

    @Test
    fun `le meilleur exemplaire est celui au debit le plus eleve`() {
        // 5 Mo pour 180 s ≈ 222 kbps, 2 Mo ≈ 89 kbps
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", sizeBytes = 2_000_000),
            song(id = 2, title = "Formidable", artist = "Stromae", sizeBytes = 5_000_000),
        )
        val group = DuplicateDetector.findGroups(songs).single()
        assertEquals(2L, group.keeper.id)
        assertEquals(listOf(1L), group.removable.map { it.id })
    }

    @Test
    fun `a debit egal les tags les plus complets l'emportent`() {
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae"),
            song(id = 2, title = "Formidable", artist = "Stromae",
                albumArtist = "Stromae", genre = "Pop", year = 2013, track = 4),
        )
        assertEquals(2L, DuplicateDetector.findGroups(songs).single().keeper.id)
    }

    @Test
    fun `l'espace recuperable exclut l'exemplaire garde`() {
        val songs = listOf(
            song(id = 1, title = "Formidable", artist = "Stromae", sizeBytes = 5_000_000),
            song(id = 2, title = "Formidable", artist = "Stromae", sizeBytes = 3_000_000),
            song(id = 3, title = "Formidable", artist = "Stromae", sizeBytes = 2_000_000),
        )
        val group = DuplicateDetector.findGroups(songs).single()
        assertEquals(1L, group.keeper.id)
        assertEquals(5_000_000L, group.reclaimableBytes)
    }

    @Test
    fun `une bibliotheque vide ne plante pas`() {
        assertTrue(DuplicateDetector.findGroups(emptyList()).isEmpty())
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*DuplicateDetectorTest*"
```

Attendu : ÉCHEC — `Unresolved reference: DuplicateDetector`.

- [ ] **Step 3: Écrire le détecteur**

`app/src/main/java/fr/synxio/player/data/model/DuplicateDetector.kt` :

```kotlin
package fr.synxio.player.data.model

import java.text.Normalizer

/** Un groupe de morceaux jugés identiques. [keepIndex] désigne l'exemplaire recommandé. */
data class DuplicateGroup(
    val songs: List<Song>,
    val keepIndex: Int,
) {
    val keeper: Song get() = songs[keepIndex]
    val removable: List<Song> get() = songs.filterIndexed { i, _ -> i != keepIndex }
    val reclaimableBytes: Long get() = removable.sumOf { it.sizeBytes }
}

/**
 * Détection de doublons sur les tags, sans lire les fichiers : instantané même sur
 * une grosse bibliothèque.
 *
 * Le critère est volontairement prudent — le résultat sert à proposer des suppressions
 * de fichiers. Titre et artiste normalisés doivent être identiques ET les durées ne pas
 * s'écarter de plus de la tolérance : c'est ce qui distingue une vraie copie d'un live,
 * d'un remix ou d'une reprise, qui partagent titre et artiste mais jamais la durée.
 */
object DuplicateDetector {

    private val ACCENTS = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    // Suffixes ajoutés par les sites de téléchargement et les rééditions.
    private val SUFFIXES = listOf(
        "\\((remaster|remastered|live|acoustic|radio edit|single version|album version|" +
            "bonus track|explicit|clean|mono|stereo|version|edit)[^)]*\\)",
        "\\[(remaster|remastered|live|acoustic|radio edit|single version|album version|" +
            "bonus track|explicit|clean|mono|stereo|version|edit)[^\\]]*\\]",
        "-\\s*(remaster|remastered|live|acoustic|radio edit|single version|album version)\\b.*$",
        "\\b(feat|ft|featuring|avec)\\b\\.?.*$",
    ).map { it.toRegex(RegexOption.IGNORE_CASE) }

    private val NON_ALNUM = "[^a-z0-9]".toRegex()

    /**
     * Minuscules → accents retirés → suffixes parasites retirés → tout ce qui n'est ni
     * lettre ni chiffre retiré. « Formidable (Remastered 2011) » et « formidable »
     * donnent la même chaîne.
     */
    fun normalize(text: String): String {
        var s = text.lowercase().trim()
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(ACCENTS, "")
        SUFFIXES.forEach { s = it.replace(s, " ") }
        return s.replace(NON_ALNUM, "")
    }

    fun findGroups(songs: List<Song>, toleranceMs: Long = 3_000): List<DuplicateGroup> =
        songs
            .groupBy { normalize(it.title) to normalize(it.artist) }
            .values
            .filter { it.size > 1 }
            .flatMap { splitByDuration(it, toleranceMs) }
            .filter { it.size > 1 }
            .map { candidates ->
                val ordered = candidates.sortedWith(BEST_FIRST)
                DuplicateGroup(songs = ordered, keepIndex = 0)
            }
            .sortedByDescending { it.reclaimableBytes }

    /**
     * Dans un lot de même titre/artiste, sépare les versions dont les durées s'écartent
     * trop : le studio et le live d'un même titre ne doivent pas finir dans le même groupe.
     */
    private fun splitByDuration(candidates: List<Song>, toleranceMs: Long): List<List<Song>> {
        val buckets = mutableListOf<MutableList<Song>>()
        candidates.sortedBy { it.durationMs }.forEach { song ->
            val bucket = buckets.lastOrNull()
            if (bucket != null && song.durationMs - bucket.first().durationMs <= toleranceMs) {
                bucket += song
            } else {
                buckets += mutableListOf(song)
            }
        }
        return buckets
    }

    /** Complétude des tags : chaque champ renseigné vaut un point. */
    private fun tagScore(song: Song): Int =
        listOf(
            song.albumArtist.isNullOrBlank().not(),
            song.genre.isNullOrBlank().not(),
            song.year > 0,
            song.track > 0,
            song.album.isNotBlank() && song.album != Song.UNKNOWN_MEDIASTORE,
        ).count { it }

    private val BEST_FIRST: Comparator<Song> = compareByDescending<Song> { it.approxBitrateKbps }
        .thenByDescending { it.sizeBytes }
        .thenByDescending { tagScore(it) }
        .thenBy { it.path.length }
}
```

- [ ] **Step 4: Lancer les tests**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*DuplicateDetectorTest*"
```

Attendu : PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/fr/synxio/player/data/model/DuplicateDetector.kt app/src/test
git commit -m "feat: detection de doublons sur tags normalises"
```

---

### Task 4: Persistance Room v4 et suppression de la migration destructive

**Files:**
- Modify: `app/src/main/java/fr/synxio/player/data/db/Entities.kt` (ajout en fin de fichier)
- Create: `app/src/main/java/fr/synxio/player/data/db/SmartPlaylistDao.kt`
- Modify: `app/src/main/java/fr/synxio/player/data/db/SynxioDatabase.kt`
- Modify: `app/src/main/java/fr/synxio/player/di/AppModule.kt:44-48`

**Interfaces:**
- Consumes: rien
- Produces:
  - `SmartPlaylistEntity(id: Long, name: String, rulesJson: String, createdAt: Long, updatedAt: Long)`
  - `SmartPlaylistDao` : `observeAll(): Flow<List<SmartPlaylistEntity>>`, `observeById(id: Long): Flow<SmartPlaylistEntity?>`, `get(id: Long): SmartPlaylistEntity?`, `insert(e): Long`, `update(e)`, `delete(id: Long)`
  - `SynxioDatabase.smartPlaylistDao()`, `SynxioDatabase.MIGRATION_3_4`

- [ ] **Step 1: Ajouter l'entité**

En fin de `app/src/main/java/fr/synxio/player/data/db/Entities.kt` :

```kotlin
/**
 * Une playlist intelligente de l'utilisateur. Les règles sont sérialisées en JSON
 * plutôt qu'éclatées en colonnes : leur nombre est variable et elles ne sont jamais
 * interrogées en SQL, l'évaluation se faisant en mémoire.
 */
@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rulesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 2: Écrire le DAO**

`app/src/main/java/fr/synxio/player/data/db/SmartPlaylistDao.kt` :

```kotlin
package fr.synxio.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartPlaylistDao {

    @Query("SELECT * FROM smart_playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SmartPlaylistEntity>>

    @Query("SELECT * FROM smart_playlists WHERE id = :id")
    fun observeById(id: Long): Flow<SmartPlaylistEntity?>

    @Query("SELECT * FROM smart_playlists WHERE id = :id")
    suspend fun get(id: Long): SmartPlaylistEntity?

    @Insert
    suspend fun insert(playlist: SmartPlaylistEntity): Long

    @Update
    suspend fun update(playlist: SmartPlaylistEntity)

    @Query("DELETE FROM smart_playlists WHERE id = :id")
    suspend fun delete(id: Long)
}
```

- [ ] **Step 3: Passer la base en v4 avec une migration écrite à la main**

Dans `app/src/main/java/fr/synxio/player/data/db/SynxioDatabase.kt` : ajouter `SmartPlaylistEntity::class` à la liste `entities`, passer `version = 4`, ajouter l'accesseur DAO, et ajouter la migration au `companion object`. Ajouter les imports `androidx.room.migration.Migration` et `androidx.sqlite.db.SupportSQLiteDatabase`.

```kotlin
    abstract fun smartPlaylistDao(): SmartPlaylistDao

    companion object {
        const val NAME = "synxio.db"

        /**
         * v3 → v4 : ajout des playlists intelligentes.
         *
         * Écrite à la main et non déléguée à une migration destructive : l'utilisateur
         * perdrait playlists, favoris, statistiques et historique.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `smart_playlists` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `rulesJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
```

- [ ] **Step 4: Brancher la migration et retirer `fallbackToDestructiveMigration`**

Dans `app/src/main/java/fr/synxio/player/di/AppModule.kt`, remplacer `provideDatabase` :

```kotlin
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SynxioDatabase =
        Room.databaseBuilder(context, SynxioDatabase::class.java, SynxioDatabase.NAME)
            .addMigrations(SynxioDatabase.MIGRATION_3_4)
            .build()
```

Ajouter le `@Provides` du DAO auprès des autres :

```kotlin
    @Provides fun provideSmartPlaylistDao(db: SynxioDatabase): SmartPlaylistDao =
        db.smartPlaylistDao()
```

Ajouter l'import `fr.synxio.player.data.db.SmartPlaylistDao`.

> `fallbackToDestructiveMigration()` disparaît ici et ne doit revenir sous aucun prétexte. Toute version ultérieure exige sa `Migration`.

- [ ] **Step 5: Compiler et vérifier le schéma exporté**

```bash
./gradlew.bat :app:assembleDebug
```

Attendu : `BUILD SUCCESSFUL`, et `app/schemas/fr.synxio.player.data.db.SynxioDatabase/4.json` créé.

Si Room signale `Migration didn't properly handle`, c'est que le SQL du `CREATE TABLE` diffère de ce que Room attend : comparer avec la définition de `smart_playlists` dans `4.json` et aligner le SQL au caractère près.

- [ ] **Step 6: Vérifier la migration sur une vraie base v3**

Le téléphone porte une base v3 réelle avec des playlists. C'est le seul test qui compte.

```bash
adb install -r "C:/Users/Forza PC/AppData/Local/synxio-build/app/outputs/apk/debug/app-debug.apk"
adb shell am start -n fr.synxio.player.debug/fr.synxio.player.MainActivity
adb logcat -d -s SQLiteDatabase:E AndroidRuntime:E | tail -30
```

Attendu : l'appli démarre, aucune exception de migration, **les playlists existantes sont toujours là**. Si l'appli n'était jamais installée en debug, installer d'abord la version d'avant cette tâche pour créer une base v3, puis réinstaller.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/fr/synxio/player/data/db app/src/main/java/fr/synxio/player/di/AppModule.kt app/schemas
git commit -m "feat: table des playlists intelligentes et migration Room 3->4 non destructive"
```

---

### Task 5: Repository et presets

**Files:**
- Create: `app/src/main/java/fr/synxio/player/data/model/SmartPlaylistPresets.kt`
- Create: `app/src/main/java/fr/synxio/player/data/repo/SmartPlaylistRepository.kt`

**Interfaces:**
- Consumes: `SmartPlaylistEngine.evaluate/count`, `SmartPlaylistRules`, `SmartPlaylistId`, `SmartPlaylist`, `SmartPlaylistDao`, `MusicRepository.library`, `PlayStatDao.observeAll()`, `FavoriteDao.observeAll()`
- Produces:
  - `SmartPlaylistPresets.all: List<Preset>` où `data class Preset(val key: String, val name: String, val rules: SmartPlaylistRules)`
  - `SmartPlaylistPresets.byKey(key: String): Preset?`
  - `SmartPlaylistRepository.smartPlaylists: StateFlow<List<SmartPlaylist>>`
  - `SmartPlaylistRepository.observe(id: SmartPlaylistId): Flow<SmartPlaylist?>`
  - `SmartPlaylistRepository.countMatching(rules): Int`
  - `SmartPlaylistRepository.save(id: Long, name: String, rules: SmartPlaylistRules): Long`
  - `SmartPlaylistRepository.load(id: Long): Pair<String, SmartPlaylistRules>?`
  - `SmartPlaylistRepository.delete(id: Long)`

- [ ] **Step 1: Écrire les presets**

`app/src/main/java/fr/synxio/player/data/model/SmartPlaylistPresets.kt` :

```kotlin
package fr.synxio.player.data.model

/**
 * Playlists intelligentes livrées d'office. Volontairement **non stockées en base** :
 * corriger un seuil se fait par mise à jour de l'appli, pas par une migration, et
 * l'utilisateur ne peut pas se retrouver avec des presets à moitié supprimés.
 *
 * Un preset n'est pas modifiable ; « Dupliquer en playlist modifiable » en fait une
 * copie stockée que l'utilisateur peut ajuster.
 */
object SmartPlaylistPresets {

    data class Preset(val key: String, val name: String, val rules: SmartPlaylistRules)

    val all: List<Preset> = listOf(
        Preset(
            key = "recent",
            name = "Ajoutés récemment",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.DATE_ADDED, RuleOperator.IN_LAST_DAYS, "30")),
                sort = SmartSort.DATE_ADDED, descending = true, limit = 100,
            ),
        ),
        Preset(
            key = "most_played",
            name = "Les plus écoutés",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "2")),
                sort = SmartSort.PLAY_COUNT, descending = true, limit = 100,
            ),
        ),
        Preset(
            key = "never_played",
            name = "Jamais écoutés",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.PLAY_COUNT, RuleOperator.LESS_THAN, "1")),
                sort = SmartSort.RANDOM, limit = 100,
            ),
        ),
        Preset(
            key = "favorites",
            name = "Coups de cœur",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.FAVORITE, RuleOperator.IS_TRUE, "")),
                sort = SmartSort.DATE_ADDED, descending = true,
            ),
        ),
        Preset(
            key = "forgotten",
            name = "Oubliés",
            rules = SmartPlaylistRules(
                rules = listOf(
                    SmartRule(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, "180"),
                    SmartRule(RuleField.PLAY_COUNT, RuleOperator.GREATER_THAN, "0"),
                ),
                sort = SmartSort.LAST_PLAYED, limit = 100,
            ),
        ),
        Preset(
            key = "discoveries",
            name = "Découvertes",
            rules = SmartPlaylistRules(
                rules = listOf(SmartRule(RuleField.PLAY_COUNT, RuleOperator.LESS_THAN, "2")),
                sort = SmartSort.RANDOM, limit = 50,
            ),
        ),
    )

    fun byKey(key: String): Preset? = all.firstOrNull { it.key == key }
}
```

- [ ] **Step 2: Écrire le repository**

`app/src/main/java/fr/synxio/player/data/repo/SmartPlaylistRepository.kt` — calqué sur `PlaylistRepository` (même `@Singleton`, même `@ApplicationScope`, même `stateIn`) :

```kotlin
package fr.synxio.player.data.repo

import fr.synxio.player.data.db.PlayStatDao
import fr.synxio.player.data.db.FavoriteDao
import fr.synxio.player.data.db.SmartPlaylistDao
import fr.synxio.player.data.db.SmartPlaylistEntity
import fr.synxio.player.data.model.SmartPlaylist
import fr.synxio.player.data.model.SmartPlaylistEngine
import fr.synxio.player.data.model.SmartPlaylistId
import fr.synxio.player.data.model.SmartPlaylistPresets
import fr.synxio.player.data.model.SmartPlaylistRules
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Croise les règles avec la bibliothèque, les statistiques et les favoris.
 *
 * Tout est réactif : jouer un morceau ou cocher un favori met les playlists à jour
 * sans que l'utilisateur ait à rafraîchir quoi que ce soit.
 */
@Singleton
class SmartPlaylistRepository @Inject constructor(
    private val dao: SmartPlaylistDao,
    private val musicRepository: MusicRepository,
    private val playStatDao: PlayStatDao,
    private val favoriteDao: FavoriteDao,
    @ApplicationScope private val scope: CoroutineScope,
) {

    val smartPlaylists: StateFlow<List<SmartPlaylist>> = combine(
        musicRepository.library,
        playStatDao.observeAll(),
        favoriteDao.observeAll(),
        dao.observeAll(),
    ) { library, stats, favorites, stored ->
        val statsById = stats.associateBy { it.songId }
        val favoriteIds = favorites.map { it.songId }.toSet()
        val now = System.currentTimeMillis()

        val presets = SmartPlaylistPresets.all.map { preset ->
            SmartPlaylist(
                id = SmartPlaylistId.Preset(preset.key),
                name = preset.name,
                rules = preset.rules,
                songs = SmartPlaylistEngine.evaluate(
                    preset.rules, library.songs, statsById, favoriteIds, now,
                ),
            )
        }

        val custom = stored.map { entity ->
            val rules = SmartPlaylistRules.fromJson(entity.rulesJson)
            SmartPlaylist(
                id = SmartPlaylistId.Stored(entity.id),
                name = entity.name,
                rules = rules,
                songs = SmartPlaylistEngine.evaluate(
                    rules, library.songs, statsById, favoriteIds, now,
                ),
            )
        }

        custom + presets
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun observe(id: SmartPlaylistId): Flow<SmartPlaylist?> =
        smartPlaylists.map { list -> list.firstOrNull { it.id == id } }

    /** Aperçu live de l'éditeur : combien de titres la règle en cours attrape. */
    fun countMatching(rules: SmartPlaylistRules): Int = SmartPlaylistEngine.count(
        rules = rules,
        songs = musicRepository.library.value.songs,
        stats = emptyMap(),
        favorites = musicRepository.favoriteIds.value,
        now = System.currentTimeMillis(),
    )

    suspend fun load(id: Long): Pair<String, SmartPlaylistRules>? =
        dao.get(id)?.let { it.name to SmartPlaylistRules.fromJson(it.rulesJson) }

    /** [id] à 0 ou moins crée une nouvelle playlist ; sinon met à jour l'existante. */
    suspend fun save(id: Long, name: String, rules: SmartPlaylistRules): Long {
        val now = System.currentTimeMillis()
        return if (id > 0) {
            val existing = dao.get(id)
            dao.update(
                SmartPlaylistEntity(
                    id = id,
                    name = name,
                    rulesJson = rules.toJson(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )
            id
        } else {
            dao.insert(
                SmartPlaylistEntity(
                    name = name, rulesJson = rules.toJson(), createdAt = now, updatedAt = now,
                )
            )
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
```

> `countMatching` passe `emptyMap()` pour les statistiques : l'aperçu de l'éditeur reste instantané, au prix d'un compteur approximatif pour les règles portant sur les lectures. Le résultat réel, lui, est toujours calculé avec les vraies statistiques dans `smartPlaylists`.

- [ ] **Step 3: Vérifier que `MusicRepository` expose bien `favoriteIds`**

```bash
grep -n "favoriteIds" app/src/main/java/fr/synxio/player/data/repo/MusicRepository.kt
```

Attendu : une déclaration `val favoriteIds: StateFlow<Set<Long>>`. Si le type diffère, adapter l'appel plutôt que de modifier `MusicRepository`.

- [ ] **Step 4: Compiler**

```bash
./gradlew.bat :app:assembleDebug
```

Attendu : `BUILD SUCCESSFUL`. Hilt doit résoudre `SmartPlaylistRepository` sans module supplémentaire (constructeur `@Inject` + `@Singleton`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/fr/synxio/player/data/model/SmartPlaylistPresets.kt app/src/main/java/fr/synxio/player/data/repo/SmartPlaylistRepository.kt
git commit -m "feat: presets et repository des playlists intelligentes"
```

---

### Task 6: Afficher les playlists intelligentes

**Files:**
- Modify: `app/src/main/java/fr/synxio/player/ui/viewmodel/AppViewModel.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/PlaylistsScreen.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/DetailScreens.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/SynxioRoot.kt`

**Interfaces:**
- Consumes: `SmartPlaylistRepository.smartPlaylists/observe/delete`, `SmartPlaylist`, `SmartPlaylistId`
- Produces:
  - `AppViewModel.smartPlaylists: StateFlow<List<SmartPlaylist>>`
  - `AppViewModel.observeSmartPlaylist(id: SmartPlaylistId): Flow<SmartPlaylist?>`
  - `AppViewModel.deleteSmartPlaylist(id: Long)`
  - `Routes.SMART = "smart/{id}"`, `Routes.smart(id: SmartPlaylistId): String`
  - `SmartPlaylistDetailScreen(smartId, viewModel, onBack, onEdit, onOpenNowPlaying)`

- [ ] **Step 1: Exposer les playlists intelligentes dans `AppViewModel`**

Injecter `SmartPlaylistRepository` dans le constructeur (à côté de `playlistRepository`), puis ajouter auprès des autres `StateFlow` :

```kotlin
    val smartPlaylists: StateFlow<List<SmartPlaylist>> = smartPlaylistRepository.smartPlaylists

    fun observeSmartPlaylist(id: SmartPlaylistId): Flow<SmartPlaylist?> =
        smartPlaylistRepository.observe(id)

    fun deleteSmartPlaylist(id: Long) = viewModelScope.launch {
        smartPlaylistRepository.delete(id)
        _messages.emit("Playlist intelligente supprimée")
    }
```

- [ ] **Step 2: Ajouter la route**

Dans `object Routes` de `SynxioRoot.kt` :

```kotlin
    const val SMART = "smart/{id}"
    const val SMART_EDITOR = "smart_editor/{id}?preset={preset}"

    fun smart(id: SmartPlaylistId) = "smart/${id.encode()}"
    fun smartEditor(id: Long = -1L, preset: String = "") = "smart_editor/$id?preset=$preset"
```

> `encode()` produit `preset:recent` / `stored:12`. Les deux-points passent tels quels dans un segment de route Navigation — pas besoin du Base64 utilisé pour les noms d'artistes, qui, eux, contiennent des `/`.

- [ ] **Step 3: Ajouter la section dans `PlaylistsScreen`**

Ajouter les paramètres `onOpenSmart: (SmartPlaylistId) -> Unit` et `onCreateSmart: () -> Unit` à la signature, collecter `viewModel.smartPlaylists`, et insérer avant les playlists manuelles, dans le `LazyColumn` existant :

```kotlin
        val smartPlaylists by viewModel.smartPlaylists.collectAsStateWithLifecycle()

        if (smartPlaylists.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Playlists intelligentes", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onCreateSmart) { Text("Nouvelle règle") }
                }
            }
            items(smartPlaylists, key = { it.id.encode() }) { smart ->
                ListItem(
                    headlineContent = { Text(smart.name) },
                    supportingContent = { Text("${smart.songCount} titres") },
                    leadingContent = {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onOpenSmart(smart.id) },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        }
```

Imports à ajouter : `androidx.compose.foundation.clickable`, `androidx.compose.material.icons.rounded.AutoAwesome`, `androidx.compose.material3.ListItem`, `androidx.compose.material3.HorizontalDivider`, `androidx.compose.ui.Alignment`, `fr.synxio.player.data.model.SmartPlaylistId`.

- [ ] **Step 4: Écrire l'écran de détail**

En fin de `DetailScreens.kt`, en réutilisant le `SongListScreen` déjà présent dans ce fichier :

```kotlin
/**
 * Détail d'une playlist intelligente. Volontairement en lecture seule : réordonner
 * ou retirer un titre d'une liste recalculée à chaque écoute serait mensonger.
 */
@Composable
fun SmartPlaylistDetailScreen(
    smartId: SmartPlaylistId,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val smart by viewModel.observeSmartPlaylist(smartId).collectAsStateWithLifecycle(null)
    val current = smart

    if (current == null) {
        EmptyState(
            icon = Icons.Rounded.AutoAwesome,
            title = "Playlist introuvable",
            subtitle = "Elle a peut-être été supprimée.",
        )
        return
    }

    SongListScreen(
        title = current.name,
        songs = current.songs,
        viewModel = viewModel,
        onBack = onBack,
        onOpenNowPlaying = onOpenNowPlaying,
        actions = {
            if (!current.isPreset) {
                IconButton(onClick = { onEdit((current.id as SmartPlaylistId.Stored).id) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Modifier les règles")
                }
            }
        },
    )
}
```

> Avant d'écrire ce bloc, ouvrir `DetailScreens.kt` et relever la **vraie** signature de `SongListScreen` : ses paramètres doivent être repris à l'identique. Si elle n'accepte pas de slot `actions`, en ajouter un avec la valeur par défaut `{}` plutôt que de dupliquer l'écran.

- [ ] **Step 5: Câbler la navigation**

Dans le `NavHost` de `SynxioRoot.kt`, auprès des autres `composable` :

```kotlin
        composable(Routes.SMART) { entry ->
            val id = SmartPlaylistId.decode(entry.arguments?.getString("id").orEmpty())
            SmartPlaylistDetailScreen(
                smartId = id,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEdit = { storedId -> navController.navigate(Routes.smartEditor(storedId)) },
                onOpenNowPlaying = { /* même lambda que les autres écrans de détail */ },
            )
        }
```

Et compléter l'appel existant à `PlaylistsScreen` (`SynxioRoot.kt:299`) avec :

```kotlin
                onOpenSmart = { id -> navController.navigate(Routes.smart(id)) },
                onCreateSmart = { navController.navigate(Routes.smartEditor()) },
```

- [ ] **Step 6: Compiler et vérifier sur le téléphone**

```bash
./gradlew.bat :app:installDebug
adb shell am start -n fr.synxio.player.debug/fr.synxio.player.MainActivity
```

Naviguer vers l'onglet Playlists. Attendu : la section « Playlists intelligentes » liste les six presets avec un nombre de titres **non nul** pour au moins « Ajoutés récemment » et « Jamais écoutés ». Ouvrir « Coups de cœur » : le contenu doit correspondre aux favoris.

```bash
adb exec-out screencap -p > /tmp/smart-playlists.png
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui
git commit -m "feat: afficher les playlists intelligentes et leur detail"
```

---

### Task 7: Éditeur de règles

**Files:**
- Create: `app/src/main/java/fr/synxio/player/ui/viewmodel/SmartPlaylistViewModel.kt`
- Create: `app/src/main/java/fr/synxio/player/ui/screens/SmartPlaylistEditorScreen.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/SynxioRoot.kt`

**Interfaces:**
- Consumes: `SmartPlaylistRepository.load/save/countMatching`, `SmartPlaylistPresets.byKey`, `RuleField`, `RuleOperator`, `SmartSort`, `SmartRule`, `SmartPlaylistRules`
- Produces:
  - `data class SmartEditorState(val name: String, val rules: List<SmartRule>, val matchAll: Boolean, val sort: SmartSort, val descending: Boolean, val limit: Int?, val matchCount: Int, val saved: Boolean)`
  - `SmartPlaylistViewModel` : `state: StateFlow<SmartEditorState>`, `load(id: Long, presetKey: String)`, `setName`, `addRule`, `updateRule(index, rule)`, `removeRule(index)`, `setMatchAll`, `setSort`, `setDescending`, `setLimit`, `save()`
  - `SmartPlaylistEditorScreen(id: Long, presetKey: String, onBack: () -> Unit)`

- [ ] **Step 1: Écrire le ViewModel**

`app/src/main/java/fr/synxio/player/ui/viewmodel/SmartPlaylistViewModel.kt` :

```kotlin
package fr.synxio.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.synxio.player.data.model.RuleField
import fr.synxio.player.data.model.RuleOperator
import fr.synxio.player.data.model.SmartPlaylistPresets
import fr.synxio.player.data.model.SmartPlaylistRules
import fr.synxio.player.data.model.SmartRule
import fr.synxio.player.data.model.SmartSort
import fr.synxio.player.data.repo.SmartPlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmartEditorState(
    val name: String = "",
    val rules: List<SmartRule> = emptyList(),
    val matchAll: Boolean = true,
    val sort: SmartSort = SmartSort.TITLE,
    val descending: Boolean = false,
    val limit: Int? = null,
    val matchCount: Int = 0,
    val saved: Boolean = false,
) {
    /** Une playlist sans nom ou sans critère n'a pas de sens : l'enregistrement est bloqué. */
    val canSave: Boolean get() = name.isNotBlank() && rules.isNotEmpty()

    fun toRules() = SmartPlaylistRules(rules, matchAll, sort, descending, limit)
}

@HiltViewModel
class SmartPlaylistViewModel @Inject constructor(
    private val repository: SmartPlaylistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SmartEditorState())
    val state: StateFlow<SmartEditorState> = _state.asStateFlow()

    private var editingId: Long = -1L

    /**
     * [id] > 0 charge une playlist existante ; sinon on crée. [presetKey] non vide
     * pré-remplit avec les règles d'un preset (« Dupliquer en playlist modifiable »).
     */
    fun load(id: Long, presetKey: String) {
        editingId = id
        viewModelScope.launch {
            when {
                id > 0 -> repository.load(id)?.let { (name, rules) ->
                    _state.value = SmartEditorState(
                        name = name,
                        rules = rules.rules,
                        matchAll = rules.matchAll,
                        sort = rules.sort,
                        descending = rules.descending,
                        limit = rules.limit,
                    )
                }
                presetKey.isNotEmpty() -> SmartPlaylistPresets.byKey(presetKey)?.let { preset ->
                    _state.value = SmartEditorState(
                        name = "${preset.name} (copie)",
                        rules = preset.rules.rules,
                        matchAll = preset.rules.matchAll,
                        sort = preset.rules.sort,
                        descending = preset.rules.descending,
                        limit = preset.rules.limit,
                    )
                }
            }
            refreshCount()
        }
    }

    fun setName(value: String) = mutate { it.copy(name = value) }
    fun setMatchAll(value: Boolean) = mutate { it.copy(matchAll = value) }
    fun setSort(value: SmartSort) = mutate { it.copy(sort = value) }
    fun setDescending(value: Boolean) = mutate { it.copy(descending = value) }
    fun setLimit(value: Int?) = mutate { it.copy(limit = value) }

    fun addRule() = mutate {
        it.copy(rules = it.rules + SmartRule(RuleField.TITLE, RuleOperator.CONTAINS, ""))
    }

    fun updateRule(index: Int, rule: SmartRule) = mutate { s ->
        // Changer de champ peut rendre l'opérateur courant inapplicable
        // (« favori contient » n'existe pas) : on retombe sur le premier valable.
        val fixed = if (rule.operator in rule.field.operators) rule
        else rule.copy(operator = rule.field.operators.first())
        s.copy(rules = s.rules.toMutableList().also { it[index] = fixed })
    }

    fun removeRule(index: Int) = mutate { s ->
        s.copy(rules = s.rules.toMutableList().also { it.removeAt(index) })
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.save(editingId, current.name.trim(), current.toRules())
            _state.update { it.copy(saved = true) }
        }
    }

    private fun mutate(block: (SmartEditorState) -> SmartEditorState) {
        _state.update(block)
        refreshCount()
    }

    private fun refreshCount() {
        _state.update { it.copy(matchCount = repository.countMatching(it.toRules())) }
    }
}
```

- [ ] **Step 2: Écrire l'écran**

`app/src/main/java/fr/synxio/player/ui/screens/SmartPlaylistEditorScreen.kt`. Structure : `Scaffold` + `TopAppBar` (« Nouvelle playlist intelligente » ou « Modifier la règle ») avec une action « Enregistrer » désactivée quand `!state.canSave`, puis une `LazyColumn` :

1. `OutlinedTextField` du nom.
2. Un `SegmentedButton` à deux choix : « Toutes les règles » / « Au moins une », lié à `setMatchAll`.
3. Une carte par règle : trois menus déroulants (champ, opérateur — alimenté par `rule.field.operators` et **jamais** par la liste complète —, valeur) plus un bouton de suppression. Pour `FAVORITE`, dont les opérateurs `IS_TRUE` / `IS_FALSE` se suffisent, masquer le champ de valeur.
4. Un `TextButton` « Ajouter un critère » → `addRule()`.
5. Tri : menu `SmartSort` + interrupteur « Décroissant ».
6. Limite : `OutlinedTextField` numérique, vide = pas de limite.
7. Un texte en bas : `"${state.matchCount} titres correspondent"`.

Suivre les composants déjà utilisés par `SettingsScreen.kt` (`SettingRow`, `SynxioDialog`) pour rester cohérent avec le reste de l'appli. Réagir à `state.saved` par un `LaunchedEffect(state.saved) { if (state.saved) onBack() }`.

- [ ] **Step 3: Câbler la route**

Dans `SynxioRoot.kt` :

```kotlin
        composable(
            route = Routes.SMART_EDITOR,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("preset") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            SmartPlaylistEditorScreen(
                id = entry.arguments?.getLong("id") ?: -1L,
                presetKey = entry.arguments?.getString("preset").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
```

Imports : `androidx.navigation.NavType`, `androidx.navigation.navArgument`.

- [ ] **Step 4: Tester sur le téléphone**

```bash
./gradlew.bat :app:installDebug
adb shell am start -n fr.synxio.player.debug/fr.synxio.player.MainActivity
```

Créer une règle vérifiable contre la bibliothèque réelle : *Artiste contient `<un artiste que tu as>`* + *Nombre de lectures est supérieur à 0*. Attendu : le compteur bouge à chaque modification, l'enregistrement est refusé sans nom, et la playlist créée contient bien ces titres et pas d'autres.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/fr/synxio/player/ui
git commit -m "feat: editeur de regles des playlists intelligentes"
```

---

### Task 8: Écran de doublons et suppression

**Files:**
- Create: `app/src/main/java/fr/synxio/player/data/repo/DuplicateRepository.kt`
- Create: `app/src/main/java/fr/synxio/player/ui/viewmodel/DuplicatesViewModel.kt`
- Create: `app/src/main/java/fr/synxio/player/ui/screens/DuplicatesScreen.kt`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/SettingsScreen.kt` (auprès de « Réparer les tags », ligne ~505)
- Modify: `app/src/main/java/fr/synxio/player/ui/SynxioRoot.kt`

**Interfaces:**
- Consumes: `DuplicateDetector.findGroups`, `DuplicateGroup`, `MusicRepository.library/refresh`
- Produces:
  - `DuplicateRepository.scan(): List<DuplicateGroup>`
  - `DuplicateRepository.deleteRequest(songs: List<Song>): IntentSender?` (API 30+, `null` en dessous)
  - `DuplicateRepository.deleteLegacy(songs: List<Song>): Int` (API 26-29)
  - `DuplicatesViewModel` : `state: StateFlow<DuplicatesState>`, `scan()`, `toggle(songId: Long)`, `clearSelection()`, `onDeleted()`
  - `data class DuplicatesState(val groups: List<DuplicateGroup>, val selected: Set<Long>, val scanning: Boolean)`

- [ ] **Step 1: Écrire le repository**

```kotlin
package fr.synxio.player.data.repo

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.model.DuplicateDetector
import fr.synxio.player.data.model.DuplicateGroup
import fr.synxio.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuplicateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
) {

    suspend fun scan(): List<DuplicateGroup> = withContext(Dispatchers.Default) {
        DuplicateDetector.findGroups(musicRepository.library.value.songs)
    }

    /**
     * À partir d'Android 11, l'application ne peut pas supprimer seule un fichier
     * qu'elle n'a pas créé : elle demande au système, qui affiche sa propre
     * confirmation. C'est une garantie de plus pour l'utilisateur, pas une contrainte
     * à contourner.
     */
    fun deleteRequest(songs: List<Song>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, songs.map { it.uri }).intentSender
        } else {
            null
        }

    /** Chemin hérité pour API 26-29, où WRITE_EXTERNAL_STORAGE suffit encore. */
    suspend fun deleteLegacy(songs: List<Song>): Int = withContext(Dispatchers.IO) {
        songs.count { song ->
            runCatching { context.contentResolver.delete(song.uri, null, null) > 0 }
                .getOrDefault(false)
        }
    }

    suspend fun refreshLibrary() = musicRepository.refresh()
}
```

- [ ] **Step 2: Écrire le ViewModel**

`DuplicatesViewModel` : `@HiltViewModel`, expose `DuplicatesState`, lance `scan()` dans un `init`, gère la sélection (un `Set<Long>` d'ids, **vide au départ**), et recharge après suppression via `refreshLibrary()` puis `scan()`.

- [ ] **Step 3: Écrire l'écran**

`DuplicatesScreen(onBack: () -> Unit)`. Structure :

1. En-tête : `"${groups.size} groupes · ${filesInExcess} fichiers en trop · ${formatSize(reclaimable)}"` — réutiliser le formatteur de `core/util/Format.kt` (vérifier son nom exact avant d'écrire l'appel).
2. Une `Card` par groupe. Dans chaque groupe, l'exemplaire `keeper` porte un badge « À garder » et **aucune case** ; les `removable` portent une `Checkbox` **décochée par défaut**, avec en sous-titre `"${song.extension} · ${song.approxBitrateKbps} kbps · ${formatSize(song.sizeBytes)} · ${song.folderName}"`.
3. Un `Button` « Supprimer la sélection (N) » en bas, `enabled = selected.isNotEmpty()`.
4. Un `SynxioDialog` de confirmation rappelant le nombre de fichiers et l'espace libéré, avant de lancer la demande système.

La suppression passe par un `rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult())` :

```kotlin
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // RESULT_OK signifie que l'utilisateur a accepté dans la boîte système.
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onDeleted()
    }
```

- [ ] **Step 4: Ajouter l'entrée dans les réglages et la route**

Dans `SettingsScreen.kt`, auprès de « Réparer les tags » (~ligne 505), ajouter une ligne « Rechercher les doublons » / sous-titre « Repérer les morceaux présents en plusieurs exemplaires » qui appelle un nouveau paramètre `onOpenDuplicates`. Le câbler dans `SynxioRoot.kt:318` à côté de `onOpenRepair`, et déclarer `const val DUPLICATES = "duplicates"` dans `Routes` avec son `composable`.

- [ ] **Step 5: Tester sur le téléphone — SANS SUPPRIMER**

```bash
./gradlew.bat :app:installDebug
adb shell am start -n fr.synxio.player.debug/fr.synxio.player.MainActivity
```

Réglages → Rechercher les doublons. Attendu : le scan aboutit, et **chaque groupe affiché contient réellement le même morceau** — c'est le point à vérifier un par un, un faux positif ici mène à une perte de fichier. Cocher un élément, ouvrir la confirmation, puis **annuler**.

> **Arrêt obligatoire.** Ne pas mener une suppression à son terme sans accord explicite de l'utilisateur : ce sont ses vrais fichiers musicaux. Capturer l'écran des groupes détectés et le lui montrer d'abord.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/fr/synxio/player
git commit -m "feat: ecran de detection et de suppression des doublons"
```

---

### Task 9: Partage d'une playlist

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/java/fr/synxio/player/data/repo/ShareRepository.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/fr/synxio/player/ui/components/SongMenuHost.kt:46-52`
- Modify: `app/src/main/java/fr/synxio/player/ui/screens/PlaylistsScreen.kt` (menu de la playlist)

**Interfaces:**
- Consumes: `Playlist`, `Song`
- Produces:
  - `ShareRepository.playlistFileIntent(playlist: Playlist): Intent`
  - `ShareRepository.playlistTextIntent(playlist: Playlist): Intent`
  - `ShareRepository.playlistAudioIntent(playlist: Playlist): Result<Intent>`
  - `ShareRepository.songFileIntent(song: Song): Intent`

- [ ] **Step 1: Déclarer le FileProvider**

`app/src/main/res/xml/file_paths.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- Seul ce sous-dossier du cache est exposé : rien d'autre de l'appli ne sort. -->
    <cache-path name="shared" path="shared/" />
</paths>
```

Dans `AndroidManifest.xml`, à l'intérieur de `<application>` :

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

> `${applicationId}` vaut `fr.synxio.player.debug` en debug (`applicationIdSuffix`) : l'autorité doit donc être construite à l'exécution avec `context.packageName`, jamais écrite en dur.

- [ ] **Step 2: Écrire le repository**

```kotlin
package fr.synxio.player.data.repo

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.data.model.Playlist
import fr.synxio.player.data.model.Song
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val authority get() = "${context.packageName}.fileprovider"

    /** Au-delà, aucune application de messagerie ne suit. */
    private val warnBytes = 100L * 1024 * 1024
    private val maxBytes = 500L * 1024 * 1024

    fun playlistFileIntent(playlist: Playlist): Intent {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${playlist.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "_")}.m3u8")
        file.bufferedWriter().use { out ->
            out.appendLine("#EXTM3U")
            out.appendLine("#PLAYLIST:${playlist.name}")
            playlist.songs.forEach { song ->
                out.appendLine("#EXTINF:${song.durationMs / 1000},${song.displayArtist} - ${song.title}")
                out.appendLine(song.path)
            }
        }
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, playlist.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun playlistTextIntent(playlist: Playlist): Intent {
        val body = buildString {
            appendLine("${playlist.name} (${playlist.songCount} titres)")
            appendLine()
            playlist.songs.forEachIndexed { i, song ->
                appendLine("${i + 1}. ${song.displayArtist} — ${song.title}")
            }
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, playlist.name)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    fun playlistAudioIntent(playlist: Playlist): Result<Intent> {
        val total = playlist.songs.sumOf { it.sizeBytes }
        if (total > maxBytes) {
            return Result.failure(
                IllegalStateException(
                    "Cette playlist pèse ${total / 1024 / 1024} Mo : c'est trop lourd à " +
                        "envoyer. Partage plutôt le fichier .m3u8 ou la liste en texte."
                )
            )
        }
        return Result.success(
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(playlist.songs.map { it.uri }),
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    fun exceedsWarnThreshold(playlist: Playlist): Boolean =
        playlist.songs.sumOf { it.sizeBytes } > warnBytes

    fun songFileIntent(song: Song): Intent = Intent(Intent.ACTION_SEND).apply {
        type = song.mimeType
        putExtra(Intent.EXTRA_STREAM, song.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
```

- [ ] **Step 3: Ajouter le partage au menu des playlists**

Dans le `DropdownMenu` de playlist de `PlaylistsScreen.kt`, une entrée « Partager » qui ouvre une feuille à trois choix : « Le fichier .m3u8 », « La liste en texte », « Les fichiers audio ». Chacune construit son `Intent` via `ShareRepository` et le lance avec `context.startActivity(Intent.createChooser(intent, "Partager « ${playlist.name} »"))`. Sur `playlistAudioIntent`, afficher le message d'erreur du `Result` dans un snackbar plutôt que de lancer l'intent, et avertir avant envoi si `exceedsWarnThreshold` est vrai.

- [ ] **Step 4: Migrer le partage de fichier unique**

Dans `SongMenuHost.kt:46-52`, remplacer l'`Intent` construit à la main par `shareRepository.songFileIntent(song)` — aujourd'hui il passe une URI MediaStore sans `FLAG_GRANT_READ_URI_PERMISSION`, ce que certaines applications réceptrices refusent.

- [ ] **Step 5: Tester sur le téléphone**

```bash
./gradlew.bat :app:installDebug
```

Pour chacune des trois formes : ouvrir une playlist, Partager, vérifier que le sélecteur Android s'ouvre et que la cible reçoit le bon contenu. Tester le partage vers un explorateur de fichiers ou une note plutôt qu'une messagerie, pour éviter tout envoi réel à un contact.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/fr/synxio/player app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat: partage d'une playlist en m3u8, texte ou fichiers audio"
```

---

### Task 10: Import d'un .m3u8 reçu

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (activité `MainActivity`)
- Modify: `app/src/main/java/fr/synxio/player/MainActivity.kt`

**Interfaces:**
- Consumes: `PlaylistRepository.importM3u(source: Uri, fallbackName: String): Result<Long>` (existant)
- Produces: rien de nouveau

- [ ] **Step 1: Déclarer l'intent-filter**

Dans `<activity android:name=".MainActivity">`, après le filtre `audio/*` existant :

```xml
            <!-- Ouvrir une playlist reçue ou téléchargée -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:mimeType="audio/x-mpegurl" />
                <data android:mimeType="audio/mpegurl" />
                <data android:mimeType="application/vnd.apple.mpegurl" />
                <data android:mimeType="application/x-mpegurl" />
            </intent-filter>
```

- [ ] **Step 2: Traiter l'intent**

Dans `MainActivity`, à la réception d'un `ACTION_VIEW` dont le type correspond à une playlist, appeler `importM3u` et naviguer vers la playlist créée. `launchMode="singleTask"` étant déjà déclaré, il faut traiter **à la fois** `onCreate` et `onNewIntent`, sinon une appli déjà ouverte ignore le fichier.

Le message d'échec doit être explicite : lorsque `importM3u` renvoie une erreur parce qu'aucun chemin ne correspond, afficher *« Aucun titre de cette playlist n'est présent sur cet appareil »* — c'est le cas normal pour un `.m3u8` venu d'un autre téléphone, pas un bug.

- [ ] **Step 3: Tester sur le téléphone**

Pousser un `.m3u8` produit par l'appli elle-même (Task 9) et l'ouvrir :

```bash
adb push playlist-test.m3u8 /sdcard/Download/
adb shell am start -a android.intent.action.VIEW \
  -d "file:///sdcard/Download/playlist-test.m3u8" -t "audio/x-mpegurl"
```

Attendu : Synxio apparaît dans le sélecteur, l'import crée la playlist et l'ouvre. Vérifier aussi le cas appli déjà lancée (`onNewIntent`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/fr/synxio/player/MainActivity.kt
git commit -m "feat: importer une playlist m3u8 recue"
```

---

### Task 11: Recette complète sur le téléphone

Passage final sur le Honor `BKQ-N49` (Android 17 / SDK 37). L'appareil tourne un SDK plus récent que la cible de l'appli : le système applique les comportements Android 16, ce qui est nominal — mais c'est justement ce qui doit être confirmé en vrai.

**Files:** aucun (sauf correctifs découverts)

- [ ] **Step 1: Installer une version propre**

```bash
./gradlew.bat :app:installDebug
adb shell pm clear fr.synxio.player.debug
```

> `pm clear` efface la base : ne le faire que si la migration de la Task 4 a **déjà** été validée sur une base v3 réelle, sinon on perd le seul jeu de données qui la teste.

- [ ] **Step 2: Lancer la surveillance des crashs**

```bash
adb logcat -c
adb logcat -s AndroidRuntime:E fr.synxio.player:E ActivityManager:E > /tmp/synxio-logcat.txt &
```

- [ ] **Step 3: Recette des nouveautés**

Pour chaque point : agir, capturer (`adb exec-out screencap -p > /tmp/<nom>.png`), vérifier.

- Les six presets s'affichent avec un nombre de titres cohérent.
- « Coups de cœur » correspond exactement aux favoris.
- « Jamais écoutés » ne contient aucun titre déjà joué.
- Créer une règle, la modifier, la supprimer.
- « Dupliquer en playlist modifiable » depuis un preset.
- Lire une playlist intelligente, la mettre en file d'attente.
- Scan des doublons : vérifier chaque groupe **un par un**.
- Les trois formes de partage.
- Import d'un `.m3u8`.

- [ ] **Step 4: Recette de non-régression**

Lecture, pause, suivant/précédent, file d'attente (réordonner, retirer), favoris, égaliseur, éditeur de tags, recherche, statistiques, historique, widget écran d'accueil, notification média, sauvegarde/restauration, réglages.

- [ ] **Step 5: Relever les crashs**

```bash
grep -E "FATAL|ANR|Exception" /tmp/synxio-logcat.txt
```

Attendu : rien. Toute trace ouvre un correctif, avec son propre test et son propre commit.

- [ ] **Step 6: Rendre compte**

Récapituler à l'utilisateur ce qui a été vérifié, ce qui a échoué, et ce qui reste en suspens — en particulier la suppression effective de doublons si elle n'a pas été autorisée.

---

## Auto-relecture

**Couverture de la spec :** §1.1 → Task 2 · §1.2 → Tasks 2 et 5 · §1.3 → Task 5 · §1.4 → Task 4 · §1.5 → Tasks 6 et 7 · §2.1 → Task 3 · §2.2 → Task 8 · §3.1 → Task 9 · §3.2 → Task 10 · §4 → Task 1 · §5 → Tasks 2, 3 et 11 · §6 → traité dans les tâches concernées. Aucune section sans tâche.

**Cohérence des types :** `SmartPlaylistId.encode()`/`decode()` (Task 2) sont consommés à l'identique dans les Tasks 5 et 6. `SmartPlaylistRules.toJson()`/`fromJson()` (Task 2) sont utilisés en Task 5. `DuplicateGroup.keeper`/`removable`/`reclaimableBytes` (Task 3) sont utilisés en Task 8. `SmartPlaylistEngine.evaluate` garde la même signature à cinq paramètres partout.

**Points laissés à l'exécutant, volontairement :** les signatures exactes de `SongListScreen` (Task 6) et du formatteur de taille de `Format.kt` (Task 8) doivent être relevées dans le code avant d'écrire l'appel — les inventer ici produirait du code qui ne compile pas.
