package fr.synxio.player.core.car

/** Un appareil Bluetooth reconnu comme voiture : adresse MAC et nom affiché. */
data class CarDevice(val address: String, val name: String)

/** Décide si un appareil Bluetooth est une voiture. Pur : testé sans Android. */
object CarRules {
    /** `BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO`, recopié pour rester hors d'Android. */
    const val AUDIO_VIDEO_CAR_AUDIO = 0x0420

    /** Classe « audio de voiture », ou adresse cochée dans « Mes voitures ». */
    fun isCar(deviceClass: Int, address: String, chosen: Set<String>): Boolean =
        deviceClass == AUDIO_VIDEO_CAR_AUDIO || (address.isNotEmpty() && address in chosen)
}
