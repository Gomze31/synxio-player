package fr.synxio.player.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.synxio.player.data.db.ALL_MIGRATIONS
import fr.synxio.player.data.db.ArtworkColorDao
import fr.synxio.player.data.db.DeviceProfileDao
import fr.synxio.player.data.db.ExcludedFolderDao
import fr.synxio.player.data.db.FavoriteDao
import fr.synxio.player.data.db.LyricsDao
import fr.synxio.player.data.db.AudioFeatureDao
import fr.synxio.player.data.db.LoudnessDao
import fr.synxio.player.data.db.LyricsOffsetDao
import fr.synxio.player.data.db.PlayHistoryDao
import fr.synxio.player.data.db.PlayStatDao
import fr.synxio.player.data.db.PlaylistDao
import fr.synxio.player.data.db.PodcastProgressDao
import fr.synxio.player.data.db.AudiobookFolderDao
import fr.synxio.player.data.db.QueueDao
import fr.synxio.player.data.db.RulePlaylistDao
import fr.synxio.player.data.db.SynxioDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope qui vit aussi longtemps que le process : scans, persistance de file, scrobbles. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SynxioDatabase =
        Room.databaseBuilder(context, SynxioDatabase::class.java, SynxioDatabase.NAME)
            // Pas de migration destructive : favoris, compteurs d'écoute, historique et
            // playlists ne sont pas reconstructibles. Chaque montée de version doit
            // fournir son script dans Migrations.kt.
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides fun providePlaylistDao(db: SynxioDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideFavoriteDao(db: SynxioDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun providePlayStatDao(db: SynxioDatabase): PlayStatDao = db.playStatDao()
    @Provides fun provideQueueDao(db: SynxioDatabase): QueueDao = db.queueDao()
    @Provides fun provideLyricsDao(db: SynxioDatabase): LyricsDao = db.lyricsDao()
    @Provides fun provideExcludedFolderDao(db: SynxioDatabase): ExcludedFolderDao =
        db.excludedFolderDao()
    @Provides fun providePlayHistoryDao(db: SynxioDatabase): PlayHistoryDao = db.playHistoryDao()
    @Provides fun provideArtworkColorDao(db: SynxioDatabase): ArtworkColorDao = db.artworkColorDao()
    @Provides fun provideDeviceProfileDao(db: SynxioDatabase): DeviceProfileDao =
        db.deviceProfileDao()
    @Provides fun provideLyricsOffsetDao(db: SynxioDatabase): LyricsOffsetDao =
        db.lyricsOffsetDao()
    @Provides fun provideLoudnessDao(db: SynxioDatabase): LoudnessDao = db.loudnessDao()
    @Provides fun provideAudioFeatureDao(db: SynxioDatabase): AudioFeatureDao =
        db.audioFeatureDao()
    @Provides fun provideRulePlaylistDao(db: SynxioDatabase): RulePlaylistDao =
        db.rulePlaylistDao()
    @Provides fun providePodcastProgressDao(db: SynxioDatabase): PodcastProgressDao =
        db.podcastProgressDao()
    @Provides fun provideAudiobookFolderDao(db: SynxioDatabase): AudiobookFolderDao =
        db.audiobookFolderDao()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
