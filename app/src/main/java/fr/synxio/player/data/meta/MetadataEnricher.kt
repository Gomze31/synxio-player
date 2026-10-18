package fr.synxio.player.data.meta

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.synxio.player.core.prefs.SettingsRepository
import fr.synxio.player.data.db.OnlineLookupDao
import fr.synxio.player.data.db.OnlineLookupEntity
import fr.synxio.player.data.db.TrackMetaDao
import fr.synxio.player.data.db.TrackMetaEntity
import fr.synxio.player.data.meta.MusicBrainzClient.Companion.matchKey
import fr.synxio.player.data.model.Song
import fr.synxio.player.di.ApplicationScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

enum class EnrichPhase(val label: String) {
    IDLE(""),
    LOCAL("Lecture des tags et des pochettes"),
    ONLINE("Recherche sur MusicBrainz"),
}

/** Avancement du traitement, affiché dans les réglages et pendant une récupération manuelle. */
data class EnrichProgress(
    val phase: EnrichPhase = EnrichPhase.IDLE,
    val done: Int = 0,
    val total: Int = 0,
    /** Titre ou album visé par une récupération demandée à la main. */
    val label: String? = null,
) {
    val running: Boolean get() = phase != EnrichPhase.IDLE
    val manual: Boolean get() = label != null
    val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/** État de la bibliothèque après la cascade. */
data class MetadataStats(
    val total: Int,
    val unknownArtist: Int,
    val noArtwork: Int,
    /** Artiste déduit du nom de fichier faute de mieux. */
    val guessed: Int,
    val fromOnline: Int,
)

/** Bilan d'une récupération manuelle. */
data class RefetchResult(
    val total: Int,
    val withArtwork: Int,
    val withArtist: Int,
    val onlineMatches: Int,
    val onlineSkipped: Boolean,
)

/**
 * Cascade de métadonnées et de pochettes, en arrière-plan.
 *
 * Pour chaque fichier, dans l'ordre :
 *  1. tags du fichier (ID3v2/v1, Vorbis, MP4) lus directement, encodages réparés ;
 *  2. pochette intégrée ;
 *  3. image du dossier (cover, folder, front…) ;
 *  4. MediaStore (tags indexés par Android, miniature du fichier ou de l'album) ;
 *  5. déduction depuis le nom de fichier et les dossiers ;
 *  6. en option, MusicBrainz et Cover Art Archive, une requête par seconde au plus.
 *
 * Le résultat est mis en cache (Room) par fichier, et invalidé quand la taille ou la date
 * de modification change. Rien n'est écrit dans les fichiers : la réparation des tags
 * reste l'outil pour ça.
 *
 * La pochette se résout par fichier et non par album : MediaStore range dans un même
 * « album » tous les fichiers sans tag d'album d'un dossier. Sur l'appareil de test,
 * 74 titres de « Son v2 » partageaient ainsi une seule pochette, celle de l'un d'eux.
 */
@Singleton
class MetadataEnricher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackMetaDao: TrackMetaDao,
    private val onlineLookupDao: OnlineLookupDao,
    private val tagReader: FileTagReader,
    private val folderArt: FolderArtFinder,
    private val artworkStore: ArtworkStore,
    private val musicBrainz: MusicBrainzClient,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val cache = ConcurrentHashMap<String, TrackMetaEntity>()
    private val loadMutex = Mutex()
    @Volatile private var loaded = false

    private val _progress = MutableStateFlow(EnrichProgress())
    val progress: StateFlow<EnrichProgress> = _progress.asStateFlow()

    /** Monte à chaque lot d'entrées mises à jour : la bibliothèque se redessine alors. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _stats = MutableStateFlow<MetadataStats?>(null)
    val stats: StateFlow<MetadataStats?> = _stats.asStateFlow()

    @Volatile private var librarySongs: List<Song> = emptyList()
    private val autoRequested = AtomicBoolean(false)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val manualRequests = Channel<ManualRequest>(Channel.UNLIMITED)

    private class ManualRequest(val songs: List<Song>, val label: String, val result: CompletableDeferred<RefetchResult>)

    init {
        // Un seul travailleur : les passes ne se chevauchent jamais, et MusicBrainz ne voit
        // qu'un client à la fois.
        scope.launch(Dispatchers.IO) {
            for (signal in wake) {
                drainManual()
                if (autoRequested.getAndSet(false)) {
                    runCatching { autoPass() }.onFailure { Log.w(TAG, "Passe de métadonnées interrompue", it) }
                }
                drainManual()
                _progress.value = EnrichProgress()
            }
        }
    }

    // --- API -----------------------------------------------------------------------------

    /** Charge le cache depuis Room ; à appeler avant [overlay] lors du premier scan. */
    suspend fun prepare() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                runCatching { trackMetaDao.all() }
                    .onSuccess { list -> list.forEach { cache[it.path] = it } }
                    .onFailure { Log.w(TAG, "Cache de métadonnées illisible", it) }
            }
            loaded = true
        }
    }

    /** Applique le cache aux morceaux lus dans MediaStore. Une lecture en mémoire par titre. */
    fun overlay(songs: List<Song>): List<Song> = songs.map(::overlay)

    fun overlay(song: Song): Song {
        val entry = cache[song.path]?.takeIf { it.isFreshFor(song) } ?: return song
        val resolvedAlbum = entry.album
        val albumChanged = resolvedAlbum != null && !resolvedAlbum.equals(song.album, ignoreCase = true)
        return song.copy(
            title = entry.title ?: song.title,
            artist = entry.artist ?: song.artist,
            // Album introuvable : on n'affiche plus le nom du dossier que MediaStore y avait mis.
            album = resolvedAlbum ?: "",
            albumId = if (resolvedAlbum != null && albumChanged) {
                syntheticAlbumId(resolvedAlbum, entry.albumArtist ?: entry.artist)
            } else {
                song.albumId
            },
            albumArtist = entry.albumArtist ?: song.albumArtist,
            genre = entry.genre ?: song.genre,
            year = entry.year ?: song.year,
            track = entry.track ?: song.track,
            disc = entry.disc ?: song.disc,
            artworkOverride = entry.artUri?.let(Uri::parse),
        )
    }

    /** Source de la pochette affichée pour [song], pour les détails du fichier. */
    fun artSourceOf(song: Song): ArtSource? = cache[song.path]?.takeIf { it.isFreshFor(song) }
        ?.artSource?.let { runCatching { ArtSource.valueOf(it) }.getOrNull() }

    /** Entrée à jour du cache pour [song], et la recherche en ligne qui l'a complétée. */
    suspend fun entryFor(song: Song): Pair<TrackMetaEntity, OnlineLookupEntity?>? {
        prepare()
        val entry = cache[song.path]?.takeIf { it.isFreshFor(song) } ?: return null
        val lookup = entry.onlineKey?.let { runCatching { onlineLookupDao.get(it) }.getOrNull() }?.takeIf { it.found }
        return entry to lookup
    }

    /** Signale un nouveau scan MediaStore : les fichiers nouveaux ou modifiés seront traités. */
    fun onLibraryScanned(songs: List<Song>) {
        librarySongs = songs
        autoRequested.set(true)
        wake.trySend(Unit)
    }

    /**
     * Récupère à nouveau infos et pochette pour [songs], cache ignoré, recherche en ligne
     * comprise (même sur réseau mobile : c'est une demande explicite).
     */
    suspend fun refetch(songs: List<Song>, label: String): RefetchResult {
        val request = ManualRequest(songs, label, CompletableDeferred())
        manualRequests.send(request)
        wake.trySend(Unit)
        return request.result.await()
    }

    /** Oublie tout et recommence : réglage « Tout réanalyser ». */
    fun reanalyseAll() = scope.launch(Dispatchers.IO) {
        runCatching { trackMetaDao.clear() }
        // Les fiches en ligne aussi : elles ont pu être retenues par une version plus
        // permissive de la cascade.
        runCatching { onlineLookupDao.clear() }
        cache.clear()
        _revision.value++
        autoRequested.set(true)
        wake.trySend(Unit)
    }

    // --- Passes --------------------------------------------------------------------------

    private suspend fun drainManual() {
        while (true) {
            val request = manualRequests.tryReceive().getOrNull() ?: return
            val result = runCatching { manualPass(request) }
                .onFailure { Log.w(TAG, "Récupération manuelle échouée", it) }
                .getOrDefault(RefetchResult(request.songs.size, 0, 0, 0, onlineSkipped = true))
            request.result.complete(result)
        }
    }

    private suspend fun autoPass() {
        prepare()
        folderArt.clearCache()
        val songs = librarySongs
        val known = knownArtists(songs)
        val stale = songs.filter { song -> cache[song.path]?.isFreshFor(song) != true }

        if (stale.isNotEmpty()) {
            Log.i(TAG, "Cascade locale : ${stale.size} fichier(s) à traiter sur ${songs.size}")
            processLocal(stale, known, label = null)
        }

        val settings = settingsRepository.settings.first()
        // Sans demande explicite, pas de recherche sur réseau mobile.
        if (settings.onlineMetadataEnabled && isOnline(requireUnmetered = true)) {
            val now = System.currentTimeMillis()
            val candidates = songs.filter { song ->
                val entry = cache[song.path] ?: return@filter false
                entry.needsOnline() && now - entry.onlineCheckedAt > ONLINE_RETRY_MS
            }
            if (candidates.isNotEmpty()) {
                Log.i(TAG, "Recherche en ligne : ${candidates.size} titre(s)")
                processOnline(candidates, known, label = null, force = false)
            }
        }

        prune(songs)
        publishStats(songs)
    }

    private suspend fun manualPass(request: ManualRequest): RefetchResult {
        prepare()
        folderArt.clearCache()
        val songs = request.songs
        val known = knownArtists(librarySongs.ifEmpty { songs })

        val settings = settingsRepository.settings.first()
        val online = settings.onlineMetadataEnabled && isOnline(requireUnmetered = false)
        // Les réponses en ligne déjà en cache, « rien trouvé » compris, sont redemandées.
        if (online) {
            runCatching { onlineLookupDao.deleteAll(songs.mapNotNull { cache[it.path]?.onlineKey }) }
        }
        processLocal(songs, known, label = request.label)
        val matches = if (online) processOnline(songs, known, label = request.label, force = true) else 0
        publishStats(librarySongs)

        val entries = songs.mapNotNull { cache[it.path] }
        return RefetchResult(
            total = songs.size,
            withArtwork = entries.count { it.artSource != ArtSource.NONE.name },
            withArtist = entries.count { it.artist != null },
            onlineMatches = matches,
            onlineSkipped = !online,
        )
    }

    /** Étapes 1 à 5, en parallèle limité : la lecture des tags est surtout de l'E/S. */
    private suspend fun processLocal(songs: List<Song>, known: Set<String>, label: String?) = coroutineScope {
        val semaphore = Semaphore(LOCAL_PARALLELISM)
        var done = 0
        val total = songs.size
        _progress.value = EnrichProgress(EnrichPhase.LOCAL, 0, total, label)
        songs.chunked(BATCH_SIZE).forEach { batch ->
            val entries = batch.map { song ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        runCatching {
                            val local = analyseLocal(song, known)
                            val lookup = local.onlineKey?.let { onlineLookupDao.get(it) }
                            val previous = cache[song.path]?.takeIf { it.isFreshFor(song) && it.onlineKey == local.onlineKey }
                            finish(local, lookup, previous?.onlineCheckedAt ?: 0L)
                        }.onFailure { Log.w(TAG, "Cascade impossible pour ${song.path}", it) }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
            commit(entries)
            done += batch.size
            _progress.value = EnrichProgress(EnrichPhase.LOCAL, done, total, label)
            // Une demande manuelle passe devant le reste de la bibliothèque.
            if (label == null) drainManual()
        }
    }

    /** Étape 6. Renvoie le nombre de titres pour lesquels une fiche a été trouvée. */
    private suspend fun processOnline(songs: List<Song>, known: Set<String>, label: String?, force: Boolean): Int {
        var matches = 0
        val total = songs.size
        songs.forEachIndexed { index, song ->
            _progress.value = EnrichProgress(EnrichPhase.ONLINE, index, total, label)
            val entry = cache[song.path] ?: return@forEachIndexed
            if (!force && !entry.needsOnline()) return@forEachIndexed

            runCatching {
                val local = analyseLocal(song, known)
                val lookup = lookupOnline(local)
                if (lookup?.found == true) matches++
                commit(listOf(finish(local, lookup, onlineCheckedAt = System.currentTimeMillis())))
            }.onFailure { Log.w(TAG, "Recherche en ligne impossible pour ${song.path}", it) }
            if (label == null) drainManual()
        }
        _progress.value = EnrichProgress(EnrichPhase.ONLINE, total, total, label)
        return matches
    }

    // --- Cascade d'un fichier ---------------------------------------------------------

    /** Étapes 1 à 5 : tout ce qui se sait sans réseau. */
    private class LocalAnalysis(
        val song: Song,
        val layers: List<MetaLayer>,
        val local: ResolvedMetadata,
        val inference: Inference,
        val artUri: String,
        val artSource: ArtSource,
        val onlineKey: String?,
    )

    private fun analyseLocal(song: Song, known: Set<String>): LocalAnalysis {
        val tags = tagReader.read(song.path)
        val inference = FileNameInference.infer(
            path = song.path,
            tagTitle = tags?.metadata?.title ?: song.title,
            isKnownArtist = { it.matchKey() in known },
        )
        val layers = buildList {
            tags?.let { add(MetaLayer(MetaSource.FILE_TAGS, it.metadata)) }
            add(MetaLayer(MetaSource.MEDIASTORE, mediaStoreLayer(song, tags)))
            add(MetaLayer(MetaSource.INFERRED, inference.metadata))
        }
        val local = MetadataCascade.resolve(layers)
        val albumTrusted = local.sourceOf(MetaField.ALBUM).let { it == MetaSource.FILE_TAGS || it == MetaSource.MEDIASTORE }
        val (artUri, artSource) = resolveArt(song, tags, albumTrusted)
        return LocalAnalysis(song, layers, local, inference, artUri, artSource, lookupKey(local.data, song))
    }

    /** Ajoute la couche en ligne, s'il y en a une, et produit l'entrée du cache. */
    private fun finish(analysis: LocalAnalysis, lookup: OnlineLookupEntity?, onlineCheckedAt: Long): TrackMetaEntity {
        val song = analysis.song
        val online = lookup?.toLayer()
        val resolved = if (online == null) analysis.local
        else MetadataCascade.resolve(analysis.layers + MetaLayer(MetaSource.ONLINE, online))

        var artUri = analysis.artUri
        var artSource = analysis.artSource
        val onlineArt = lookup?.artPath?.let(::File)?.takeIf { it.exists() }
        if (artSource == ArtSource.NONE && onlineArt != null) {
            artUri = Uri.fromFile(onlineArt).toString()
            artSource = ArtSource.ONLINE
        }

        val data = resolved.data
        return TrackMetaEntity(
            path = song.path,
            mediaId = song.id,
            sizeBytes = song.sizeBytes,
            modifiedSec = song.dateModifiedSec,
            version = ALGORITHM_VERSION,
            title = data.title,
            artist = data.artist,
            album = data.album,
            albumArtist = data.albumArtist,
            year = data.year,
            genre = data.genre,
            track = data.track,
            disc = data.disc,
            durationMs = data.durationMs,
            sources = resolved.sources.entries.joinToString(";") { "${it.key.name}=${it.value.name}" },
            artUri = artUri,
            artSource = artSource.name,
            altTitle = analysis.inference.alternative?.title,
            altArtist = analysis.inference.alternative?.artist,
            onlineKey = analysis.onlineKey,
            onlineCheckedAt = onlineCheckedAt,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Ce que MediaStore apporte, moins ses remplissages : un titre recopié du nom de
     * fichier, ou un album qui n'est que le nom du dossier, ne sont pas des tags.
     */
    private fun mediaStoreLayer(song: Song, tags: FileTags?): TrackMetadata {
        val baseName = song.fileName.substringBeforeLast('.')
        val titleIsFileName = song.title.equals(baseName, ignoreCase = true)
        val albumIsFolder = song.album.equals(song.folderName, ignoreCase = true)
        return TrackMetadata(
            title = song.title.takeUnless { titleIsFileName || (tags != null && tags.metadata.title == null) },
            artist = song.artist.takeUnless { it == Song.UNKNOWN_MEDIASTORE },
            album = song.album.takeUnless {
                it == Song.UNKNOWN_MEDIASTORE ||
                    (tags != null && tags.metadata.album == null) ||
                    (albumIsFolder && FileNameInference.isGeneric(song.folderName))
            },
            albumArtist = song.albumArtist,
            year = song.year,
            genre = song.genre,
            track = song.track,
            disc = song.disc.takeIf { song.track > 0 },
            durationMs = song.durationMs,
        )
    }

    /** Étapes 2, 3 et 4 pour la pochette. */
    private fun resolveArt(song: Song, tags: FileTags?, albumTrusted: Boolean): Pair<String, ArtSource> {
        val version = song.dateModifiedSec
        // Miniature du fichier lui-même, et non de son « album » MediaStore. Le paramètre
        // de version change l'adresse quand le fichier change : Coil ne ressert pas l'ancienne.
        val perSong = Uri.parse("content://media/external/audio/media/${song.id}/albumart?v=$version")

        if (tags?.hasEmbeddedArt == true) {
            if (canOpen(perSong)) return perSong.toString() to ArtSource.EMBEDDED
            tagReader.embeddedPicture(song.path)?.let(artworkStore::save)
                ?.let { return Uri.fromFile(it).toString() to ArtSource.EMBEDDED }
        }
        folderArt.find(song.folderPath)?.let { return Uri.fromFile(it).toString() to ArtSource.FOLDER }

        // MediaStore cherche lui-même une image dans le dossier quand il en a le droit.
        if (tags == null || !tags.hasEmbeddedArt) {
            if (canOpen(perSong)) return perSong.toString() to ArtSource.MEDIASTORE
        }
        if (albumTrusted && song.albumId > 0) {
            val albumArt = Uri.parse("content://media/external/audio/albumart/${song.albumId}?v=$version")
            if (canOpen(albumArt)) return albumArt.toString() to ArtSource.MEDIASTORE
        }
        // Aucune pochette : l'adresse du fichier échoue proprement et l'interface affiche
        // son dégradé, au lieu de la pochette d'un autre titre du même dossier.
        return perSong.toString() to ArtSource.NONE
    }

    private fun canOpen(uri: Uri): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length != 0L } ?: false
    }.getOrDefault(false)

    // --- En ligne -------------------------------------------------------------------------

    private suspend fun lookupOnline(analysis: LocalAnalysis): OnlineLookupEntity? {
        val key = analysis.onlineKey ?: return null
        val needArt = analysis.artSource == ArtSource.NONE
        onlineLookupDao.get(key)?.takeUnless { !it.found && System.currentTimeMillis() - it.fetchedAt > ONLINE_RETRY_MS }?.let { cached ->
            if (!cached.found || cached.artPath != null || !needArt) return cached
            // Fiche connue, pochette jamais obtenue : seul le Cover Art Archive est redemandé.
            if (cached.releaseId != null || cached.releaseGroupId != null) {
                val art = musicBrainz.frontCover(cached.releaseId, cached.releaseGroupId)?.let(artworkStore::save)?.path
                return cached.copy(artPath = art, fetchedAt = System.currentTimeMillis()).also { onlineLookupDao.put(it) }
            }
        }

        val data = analysis.local.data
        val title = data.title ?: return null
        val duration = data.durationMs ?: analysis.song.durationMs
        val artistIsGuess = analysis.local.isGuess(MetaField.ARTIST)
        val alternative = analysis.inference.alternative

        var match: OnlineMatch? = null
        // getOrThrow : une erreur réseau n'est pas un « rien trouvé », elle ne doit pas être
        // mise en cache. Le titre sera retenté à la prochaine passe.
        if (data.artist != null) match = musicBrainz.search(title, data.artist, duration).getOrThrow()
        val altTitle = alternative?.title
        val altArtist = alternative?.artist
        if (match == null && artistIsGuess && altTitle != null && altArtist != null) {
            match = musicBrainz.search(altTitle, altArtist, duration).getOrThrow()
        }
        // Titre seul : seulement sans aucun artiste. Sinon un homonyme de même durée
        // remplaçait l'artiste déduit (« Dingo et Max - Sur la Route » devenait Garou).
        if (match == null && data.artist == null) match = musicBrainz.search(title, null, duration).getOrThrow()

        val artPath = if (match != null && needArt) {
            musicBrainz.frontCover(match.releaseId, match.releaseGroupId)?.let(artworkStore::save)?.path
        } else null

        val lookup = OnlineLookupEntity(
            key = key,
            found = match != null,
            title = match?.metadata?.title,
            artist = match?.metadata?.artist,
            album = match?.metadata?.album,
            albumArtist = match?.metadata?.albumArtist,
            year = match?.metadata?.year,
            genre = match?.metadata?.genre,
            track = match?.metadata?.track,
            disc = match?.metadata?.disc,
            artPath = artPath,
            releaseId = match?.releaseId,
            releaseGroupId = match?.releaseGroupId,
            confidence = match?.confidence ?: 0,
            fetchedAt = System.currentTimeMillis(),
        )
        onlineLookupDao.put(lookup)
        return lookup
    }

    private fun OnlineLookupEntity.toLayer(): TrackMetadata? = if (!found) null else TrackMetadata(
        title = title, artist = artist, album = album, albumArtist = albumArtist,
        year = year, genre = genre, track = track, disc = disc,
    )

    /** Même titre, même artiste, même durée à 5 s près : même réponse en ligne. */
    private fun lookupKey(data: TrackMetadata, song: Song): String? {
        val title = data.title?.matchKey()?.takeIf { it.isNotEmpty() } ?: return null
        val seconds = (data.durationMs ?: song.durationMs) / 1000 / 5
        return "$title|${data.artist?.matchKey().orEmpty()}|$seconds"
    }

    private fun TrackMetaEntity.needsOnline(): Boolean {
        val artistGuess = artist == null || sourceMap()[MetaField.ARTIST] == MetaSource.INFERRED
        return artistGuess || album == null || artSource == ArtSource.NONE.name
    }

    private fun isOnline(requireUnmetered: Boolean): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        !requireUnmetered || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    // --- Cache ----------------------------------------------------------------------------

    private suspend fun commit(entries: List<TrackMetaEntity>) {
        if (entries.isEmpty()) return
        entries.forEach { cache[it.path] = it }
        runCatching { trackMetaDao.putAll(entries) }.onFailure { Log.w(TAG, "Cache non enregistré", it) }
        _revision.value++
    }

    /** Oublie les fichiers disparus et les pochettes que plus rien n'utilise. */
    private suspend fun prune(songs: List<Song>) {
        if (songs.isEmpty()) return
        val present = songs.mapTo(HashSet()) { it.path }
        val gone = cache.keys.filter { it !in present }
        if (gone.isNotEmpty()) {
            gone.forEach(cache::remove)
            runCatching { gone.chunked(500).forEach { trackMetaDao.deleteAll(it) } }
        }
        val keep = HashSet<String>()
        cache.values.forEach { entry ->
            entry.artUri?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path?.let(keep::add)
        }
        val lookupArt = runCatching { onlineLookupDao.artPaths() }.getOrNull() ?: return
        keep += lookupArt
        artworkStore.prune(keep)
    }

    private fun publishStats(songs: List<Song>) {
        if (songs.isEmpty()) return
        val shown = overlay(songs)
        val entries = songs.mapNotNull { cache[it.path] }
        val stats = MetadataStats(
            total = songs.size,
            unknownArtist = shown.count { it.displayArtist == Song.UNKNOWN_ARTIST },
            noArtwork = songs.size - entries.count { it.artSource != ArtSource.NONE.name },
            guessed = entries.count { it.sourceMap()[MetaField.ARTIST] == MetaSource.INFERRED },
            fromOnline = entries.count { e ->
                e.artSource == ArtSource.ONLINE.name || e.sourceMap().values.any { it == MetaSource.ONLINE }
            },
        )
        _stats.value = stats
        Log.i(
            TAG,
            "Bilan : ${stats.total} titres, artiste inconnu ${stats.unknownArtist}, sans pochette ${stats.noArtwork}, " +
                "artiste déduit ${stats.guessed}, complétés en ligne ${stats.fromOnline}",
        )
    }

    private fun knownArtists(songs: List<Song>): Set<String> = songs.asSequence()
        .map { it.artist }
        .filter { it.isNotBlank() && it != Song.UNKNOWN_MEDIASTORE }
        .map { it.matchKey() }
        .filter { it.length >= 2 }
        .toHashSet()

    companion object {
        private const val TAG = "MetadataEnricher"
        /** À monter quand la cascade change : toutes les entrées seront recalculées. */
        const val ALGORITHM_VERSION = 4
        private const val LOCAL_PARALLELISM = 3
        private const val BATCH_SIZE = 24
        /** Un « rien trouvé » est redemandé au bout d'un mois. */
        private const val ONLINE_RETRY_MS = 30L * 24 * 60 * 60 * 1000

        private fun TrackMetaEntity.isFreshFor(song: Song): Boolean =
            version == ALGORITHM_VERSION && sizeBytes == song.sizeBytes && modifiedSec == song.dateModifiedSec

        internal fun TrackMetaEntity.sourceMap(): Map<MetaField, MetaSource> = sources.split(';')
            .mapNotNull { part ->
                val pieces = part.split('=')
                if (pieces.size != 2) return@mapNotNull null
                runCatching { MetaField.valueOf(pieces[0]) to MetaSource.valueOf(pieces[1]) }.getOrNull()
            }.toMap()

        /**
         * Identifiant d'album pour un album que la cascade a corrigé. Négatif : jamais en
         * collision avec ceux de MediaStore.
         */
        internal fun syntheticAlbumId(album: String, albumArtist: String?): Long {
            val key = album.matchKey() + "|" + albumArtist?.matchKey().orEmpty()
            var hash = 1125899906842597L
            for (c in key) hash = 31 * hash + c.code
            return -(hash and Long.MAX_VALUE) - 1
        }
    }
}
