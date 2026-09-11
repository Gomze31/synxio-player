package fr.synxio.player.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RadioStation(
    @SerialName("stationuuid") val stationUuid: String,
    val name: String,
    val url: String,
    @SerialName("url_resolved") val urlResolved: String,
    val homepage: String,
    val favicon: String,
    val tags: String,
    val country: String,
    val language: String,
    val votes: Int,
    val codec: String,
    val bitrate: Int,
    val hls: Int,
)
