package fr.synxio.player.ui.theme.shaders

/**
 * Sources AGSL (Android Graphics Shading Language, API 33+). Les trois shaders partagent
 * le « champ » de l'aurore : trois halos gaussiens aux couleurs de la pochette posés sur
 * la couleur de fond. Constantes Kotlin plutôt que ressources `raw` : testables sur la JVM,
 * et un seul endroit pour le prélude commun.
 */
object ShaderSources {

    /** Uniformes et fonctions partagés par l'aurore et le verre. */
    private val FIELD = """
        uniform float2 c0; uniform float2 c1; uniform float2 c2;
        uniform float r0; uniform float r1; uniform float r2;
        uniform float4 col0; uniform float4 col1; uniform float4 col2;
        uniform float4 base;
        uniform float dispersion;
        uniform float time;

        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float halo(float2 p, float2 c, float r) { float d = distance(p, c) / r; return exp(-d * d * 3.0); }
        float3 field(float2 p) {
            float3 acc = base.rgb;
            acc = mix(acc, col0.rgb, halo(p, c0, r0) * col0.a);
            acc = mix(acc, col1.rgb, halo(p, c1, r1) * col1.a);
            acc = mix(acc, col2.rgb, halo(p, c2, r2) * col2.a);
            return acc;
        }
        float3 dispersed(float2 p) {
            float2 dx = float2(dispersion, 0.0);
            return float3(field(p + dx).r, field(p).g, field(p - dx).b);
        }
    """.trimIndent()

    /** Aurore plein écran : champ dispersé + grain fin. */
    val AURORA: String = FIELD + """

        uniform float2 resolution;
        uniform float grain;

        half4 main(float2 p) {
            float3 c = dispersed(p);
            float n = (hash(p / resolution * 1000.0 + time) - 0.5) * grain;
            return half4(half3(clamp(c + n, 0.0, 1.0)), 1.0);
        }
    """.trimIndent()

    /**
     * Verre réfracté : le champ de l'aurore, échantillonné à la position de la surface dans
     * l'écran (`origin`) et ondulé de `wobble` pixels, dessiné sous le voile du verre.
     */
    val GLASS: String = FIELD + """

        uniform float2 origin;
        uniform float wobble;

        half4 main(float2 p) {
            float2 q = p + origin;
            q += float2(sin(q.y / 40.0 + time * 0.8), cos(q.x / 48.0 + time * 0.6)) * wobble;
            return half4(half3(clamp(dispersed(q), 0.0, 1.0)), 1.0);
        }
    """.trimIndent()

    /** Canvas de pochette : trois nappes sinusoïdales mêlant les couleurs de la palette. */
    val CANVAS: String = """
        uniform float2 resolution;
        uniform float phase;
        uniform float contrast;
        uniform float4 colA; uniform float4 colB; uniform float4 colC;

        half4 main(float2 p) {
            float2 uv = p / resolution;
            float w1 = sin(uv.x * 3.0 + phase) * 0.5 + 0.5;
            float w2 = sin(uv.y * 2.5 - phase * 0.7 + w1 * 1.5) * 0.5 + 0.5;
            float w3 = sin((uv.x + uv.y) * 2.0 + phase * 0.5 + w2) * 0.5 + 0.5;
            float3 c = mix(colA.rgb, colB.rgb, w1);
            c = mix(c, colC.rgb, w2 * 0.6);
            c = mix(float3(0.5), c, contrast) * (0.85 + 0.3 * w3);
            return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
        }
    """.trimIndent()
}
