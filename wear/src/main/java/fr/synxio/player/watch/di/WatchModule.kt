package fr.synxio.player.watch.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.synxio.player.watch.data.DataLayerPhoneLink
import fr.synxio.player.watch.data.DataLayerStateReader
import fr.synxio.player.watch.data.PhoneLink
import fr.synxio.player.watch.data.WearStateStore
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WatchModule {
    @Provides
    @Singleton
    fun provideStore(@ApplicationContext context: Context): WearStateStore =
        WearStateStore(File(context.filesDir, "wear")).also { it.load() }

    @Provides
    @Singleton
    fun provideReader(@ApplicationContext context: Context, store: WearStateStore): DataLayerStateReader =
        DataLayerStateReader(context, store)

    @Provides
    @Singleton
    fun providePhoneLink(@ApplicationContext context: Context, reader: DataLayerStateReader): PhoneLink =
        DataLayerPhoneLink(context, reader)
}
