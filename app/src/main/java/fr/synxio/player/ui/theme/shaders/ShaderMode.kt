package fr.synxio.player.ui.theme.shaders

/** Chemin de rendu de la scène : shaders AGSL (API 33+) ou Canvas classique. */
enum class ShaderMode { AGSL, CANVAS }

/**
 * AGSL exige l'API 33 et un appareil qui n'a jamais refusé de compiler nos shaders
 * (drapeau `shadersUnavailable`). Pur : la version et le drapeau sont passés en argument.
 */
fun shaderMode(sdkInt: Int, unavailable: Boolean): ShaderMode =
    if (sdkInt >= AGSL_MIN_SDK && !unavailable) ShaderMode.AGSL else ShaderMode.CANVAS

const val AGSL_MIN_SDK = 33
