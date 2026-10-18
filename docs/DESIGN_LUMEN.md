# Synxio « Lumen » — design system (v5)

Lumen tient en trois idées :

1. **Une scène.** Fond encre `#0C0A10` (sombre) ou papier chaud `#FAF6F1` (clair), éclairé par la pochette en cours :
   l'aurore (`AmbientBackdrop`) dessine trois halos aux couleurs de la pochette, qui dérivent lentement pendant la lecture
   (un tour en 40 s) et s'immobilisent en pause.
2. **Du verre posé sur la lumière.** `Modifier.glass()` : voile translucide, reflet spéculaire en haut à gauche, liseré
   lumineux. Pas de flou en temps réel derrière les surfaces (batterie, lisibilité) : seul le fond du lecteur floute la
   pochette, une fois, sur une image de 64 px.
3. **Une signature.** Le dégradé braise → magenta (`SynxioTheme.colors.brandBrush`), dérivé de l'accent courant donc de la
   pochette. Réservé aux gestes importants : bouton lecture, progression, onglet actif, action principale, ligne de
   paroles chantée.

## Où vivent les choses

| Fichier | Contenu |
|---|---|
| `ui/theme/Tokens.kt` | `Space`, `Radius`, `SynxioShape`, `Motion` (durées, courbes, ressorts), `SynxioColors`, `LocalReduceMotion`, `LocalGlowEffects` |
| `ui/theme/Type.kt` | Bricolage Grotesque (titres) + Manrope (texte) via Google Fonts, `EyebrowTextStyle`, `TabularNumbers`, `LyricsTextStyle` |
| `ui/theme/Glass.kt` | `glass()`, `glowShadow()`, `bottomScrim()`, `AmbientBackdrop`, `synxioTopBarColors()` |
| `ui/theme/ThemePreset.kt` | Presets ; `SYNXIO` = Lumen, `IRIS` = l'ancien violet |
| `ui/components/Lumen.kt` | `PlayPauseIcon`, `BrandPlayButton`, `HeartButton`, `ToggleIconButton`, `LumenChip`, `BrandButton`, `GlassButton`, `GlassCard`, `ScreenHeader`, `BrandProgressBar`, `shimmer()`, squelettes, `SheetHandle`, `SynxioSnackbar` |
| `ui/components/SharedTransitions.kt` | Éléments partagés (grille → détail, mini-lecteur → lecteur) |
| `ui/components/MotionModifiers.kt` | `pressBounce()`, `enterCascade()` |

## Mouvement

- **Spatial** (position, taille, échelle) : ressorts `Motion.spatial()` / `spatialBouncy()` — interruptibles, ils gardent
  l'élan quand le doigt change d'avis.
- **Effets** (opacité, couleur) : courbes courtes sans rebond (`Motion.standard/enter/exit`, `effects()`).
- Les sorties sont plus brèves que les entrées. Les changements de palette durent `Motion.PALETTE` (900 ms).
- Cascades : `Motion.STAGGER_MS` entre éléments, au plus `MAX_STAGGERED` éléments décalés.
- **Réduire les animations** (réglage Synxio ou animations système coupées) : tout passe en coupe ou fondu bref, aucune
  animation infinie (aurore, vinyle, reflets de chargement, défilement des titres).
- **Effets lumineux** (réglage) : coupe halos, lueurs colorées et aurore animée.

## Signatures d'interaction

- Lecture/pause : le triangle se scinde en deux barres (morph dessiné, interruptible).
- Mini-lecteur : verre teinté par la pochette ; balayer ← suivant, → précédent, ↑ ouvrir ; la pochette grandit jusqu'au
  lecteur (élément partagé).
- Lecteur : pochettes qui pivotent comme des disques feuilletés au changement de piste, projecteur de la couleur du
  morceau derrière la pochette, précédent/suivant qui « donnent un coup » dans leur direction.
- Cœur : éclat de huit étincelles à l'ajout, simple dégonflement au retrait.
- Navigation : la capsule de l'onglet actif glisse d'onglet en onglet.
- File d'attente : glisser-déposer (la ligne se soulève), balayer vers la gauche pour retirer.
