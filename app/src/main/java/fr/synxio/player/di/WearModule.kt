package fr.synxio.player.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.synxio.player.wear.BitmapWearArtPreparer
import fr.synxio.player.wear.DataLayerStatePublisher
import fr.synxio.player.wear.WearArtPreparer
import fr.synxio.player.wear.WearStatePublisher

/** Liaisons de la passerelle montre : interfaces vers leurs implémentations réelles. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {
    @Binds abstract fun bindStatePublisher(impl: DataLayerStatePublisher): WearStatePublisher
    @Binds abstract fun bindArtPreparer(impl: BitmapWearArtPreparer): WearArtPreparer
}
