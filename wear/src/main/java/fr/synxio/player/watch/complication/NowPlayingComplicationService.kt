package fr.synxio.player.watch.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dagger.hilt.android.AndroidEntryPoint
import fr.synxio.player.watch.MainActivity
import fr.synxio.player.watch.R
import fr.synxio.player.watch.data.WearStateStore
import fr.synxio.player.wear.protocol.WearState
import javax.inject.Inject

/** Complication « en cours » : titre court, titre — artiste, ou pochette. Appui : ouvre l'appli. */
@AndroidEntryPoint
class NowPlayingComplicationService : SuspendingComplicationDataSourceService() {

    @Inject lateinit var store: WearStateStore

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, WearState(mediaId = "preview", title = "Clair de lune", artist = "Debussy"), art = null)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val stored = store.current()
        return build(request.complicationType, stored.state ?: WearState(), stored.art)
    }

    private fun build(type: ComplicationType, state: WearState, art: ByteArray?): ComplicationData? {
        val tap = tapAction()
        val title = if (state.isEmpty) getString(R.string.app_name) else state.title
        val description = PlainComplicationText.Builder(title).build()
        val note = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_tile_note)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(title.take(SHORT_MAX)).build(), description,
            ).setMonochromaticImage(note).setTapAction(tap).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                PlainComplicationText.Builder(if (state.isEmpty) title else "${state.title} — ${state.artist}").build(),
                description,
            ).setMonochromaticImage(note).setTapAction(tap).build()

            ComplicationType.SMALL_IMAGE -> {
                val bitmap = art?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                val image = if (bitmap != null) {
                    SmallImage.Builder(Icon.createWithBitmap(bitmap), SmallImageType.PHOTO).build()
                } else {
                    SmallImage.Builder(Icon.createWithResource(this, R.drawable.ic_launcher_foreground), SmallImageType.ICON).build()
                }
                SmallImageComplicationData.Builder(image, description).setTapAction(tap).build()
            }

            else -> null
        }
    }

    private fun tapAction(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val SHORT_MAX = 7

        /** Pour `WearSurfaces` : redemande toutes les complications de cette source. */
        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, NowPlayingComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
