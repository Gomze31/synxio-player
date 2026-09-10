# Système visuel « Nuit sonore »

Design validé le 2026-09-10. Cible : Synxio Player 2.0.0 (`fr.synxio.player`).
Premier des quatre chantiers de la refonte.

## Objectif

Donner à Synxio une identité visuelle propre et un socle commun, pour que les écrans
cessent chacun de redéfinir ses marges et ses couleurs.

Direction retenue : **Nuit sonore** — sombre par défaut, la pochette en cours teinte
l'application entière, chrome minimal, grandes typographies, mouvement marqué mais
raisonné.

Ce chantier ne réécrit **aucun** écran. Il produit la couche de tokens, la dérivation de
palette et les composants partagés, puis migre les écrans un par un. L'application reste
utilisable après chaque étape.

## Découpage de la refonte

Les quatre chantiers, dans l'ordre. Chacun aura sa spec et son plan.

1. **Système visuel** — le présent document.
2. **Navigation et architecture de l'information** — les 5 onglets, les écrans enterrés
   dans les réglages (Égaliseur, Stats, Historique, Sauvegarde, Réparation).
3. **Le lecteur** — plein écran et mini-lecteur.
4. **Les réglages** — découpe de `SettingsScreen.kt` (1354 lignes).

## Contraintes issues du code existant

- `ArtworkColors.kt` (144 lignes) extrait déjà une palette depuis la pochette et la met en
  cache dans la table `artwork_colors`. La dérivation décrite ici s'y branche, elle ne la
  réécrit pas.
- `ThemePreset.kt` (199 lignes) porte les presets, avec `accentDark`, `accentLight`,
  `baseDark`, `flat` et un pas de luminosité (0.055f, ou 0.025f en variante plate).
  L'accent de repli vient de `ThemePreset.SYNXIO`.
- `Theme.kt` (134 lignes) assemble le `ColorScheme` Material 3. C'est le point d'entrée à
  étendre.
- `Type.kt` (127 lignes) définit la typographie actuelle et le réglage de taille de texte,
  qui doit continuer de fonctionner.
- `minSdk 26` : aucun effet réservé à une API supérieure ne peut être requis.

## 1. Couleur

### 1.1 Les cinq rôles

La palette d'un morceau se dérive de sa pochette en cinq rôles. Aucun écran ne choisit
plus une couleur à la main.

| Rôle | Dérivation | Usage |
|---|---|---|
| `base` | dominante désaturée à ~8 %, luminosité ramenée à ~6 % | fond d'écran |
| `surface` | `base` + un pas de luminosité | cartes, lignes de liste |
| `surfaceRaised` | `base` + deux pas | élément courant, sélection |
| `accent` | dominante, saturation et luminosité remontées | actions, progression, mise en avant |
| `glow` | `accent` à 32 % d'opacité | dégradés et halos |

Le pas de luminosité reprend celui du preset courant (0.055f, ou 0.025f en variante plate)
pour que les modes plats restent plats.

### 1.2 Les deux garde-fous

Ils ne sont pas optionnels : sans eux, une pochette mal choisie rend l'application
illisible.

- **Pochette inexploitable** — saturation de la dominante < 12 %, ou pochette absente :
  on retombe sur `ThemePreset.SYNXIO.accentDark`.
- **Contraste** — `accent` est remonté en luminosité jusqu'à atteindre un ratio de
  **4,5:1 minimum** sur `base`, et `onBase` / `onBaseMuted` (blanc à 92 % / 58 %) sont
  vérifiés de la même façon. Une pochette sombre ne produit jamais du texte illisible.

### 1.3 Mode clair

Le mode clair reste disponible mais **en second** : mêmes rôles, mêmes composants, sans
les dégradés ni les halos. Il est sobre et lisible en plein jour, il n'est pas la vitrine.
AMOLED est conservé — c'est un usage réel — et force `base` à noir pur.

## 2. Typographie

Six niveaux, **une seule famille : celle du système**. Aucune fonte embarquée, pour ne pas
alourdir l'APK ; le caractère vient de l'échelle, du contraste et du mouvement.

| Niveau | Taille / interligne / approche | Usage |
|---|---|---|
| Display | 34 / 1.05 / −0.022em / 700 | nom d'album en plein écran |
| Titre | 22 / 1.15 / −0.015em / 680 | titre du morceau en cours |
| Section | 17 / 1.25 / −0.01em / 640 | en-tête de section |
| Corps | 15 / 1.4 | texte courant, descriptions |
| Libellé | 13 / 1.35 | artiste, sous-titres |
| Méta | 11 / 1.3 / +0.07em / majuscules | format, débit, durée |

Le réglage de taille de texte existant continue de s'appliquer, en facteur multiplicatif
sur l'échelle entière.

## 3. Espacement et formes

**Sept valeurs, pas une de plus :** 4, 8, 12, 16, 24, 32, 48. Marge d'écran : 20.

**Rayons :** 8 (petit), 14 (pochette), 24 (feuille modale), plein arrondi (pilules).

C'est la partie la plus ingrate et la plus responsable de l'actuelle impression de
platitude : les écrans mélangent aujourd'hui des marges arbitraires.

## 4. Composants partagés

Écrits une fois dans `ui/design/`, utilisés partout. Leur absence est la cause directe de
la divergence entre écrans.

- `SongRow` — pochette, titre, artiste, puce de format, état « en cours de lecture ».
- `ArtworkTile` — pochette avec repli, rayon et ombre normalisés.
- `SectionHeader` — titre de section plus action optionnelle.
- `SynxioSheet` — feuille modale avec poignée, marges et rayons normalisés.
- `PillButton` / `GhostButton` — action principale et secondaire.
- `MetaChip` — puce de métadonnée technique (FLAC, 320, durée).

## 5. Mouvement

Trois gestes seulement, réutilisés partout — un langage, pas un feu d'artifice. Tout en
API Compose standard, **sans flou temps réel** : tient à 60 fps sur une grosse
bibliothèque, et ne demande aucun repli pour Android 8-11.

- **Cascade à l'ouverture** — les éléments d'une liste apparaissent décalés de 30 ms,
  translation de 9 dp, plafonnée aux 8 premiers éléments visibles.
- **La pochette vole vers le lecteur** — transition d'élément partagé entre la liste et
  l'écran de lecture.
- **Ressort au tap** — sur les contrôles de lecture, `spring(dampingRatio = 0.55f)`.

Le réglage système « animations réduites » doit désactiver les trois.

## 6. Migration

Ordre de migration des écrans, du plus visible au moins visible : lecteur plein écran et
mini-lecteur, Accueil, Bibliothèque, Playlists, détails, recherche, puis le reste. Chaque
écran migré est un commit ; l'application reste utilisable entre chaque.

Les écrans non encore migrés continuent de fonctionner avec l'ancien style : les tokens
s'ajoutent, ils ne cassent rien.

## 7. Décisions prises sans arbitrage explicite

Prises sur consigne « fais au mieux », à revoir si elles déplaisent :

- **Accent de repli** : le violet existant de `ThemePreset.SYNXIO`, plutôt qu'une nouvelle
  couleur — c'est déjà l'identité de l'application, et ça évite un troisième violet.
- **Police système**, pas de fonte embarquée : sur un lecteur où la pochette occupe
  l'écran, le gain d'une fonte propre ne justifie pas le poids ajouté.

## 8. Risques

| Risque | Traitement |
|---|---|
| Pochettes produisant des palettes illisibles | Les deux garde-fous du §1.2, vérifiés sur la bibliothèque réelle de l'utilisateur |
| Cascade coûteuse sur une grosse liste | Plafonnée aux 8 premiers éléments visibles |
| Migration à mi-chemin, application incohérente | Ordre du §6 : du plus visible au moins visible, un commit par écran |
| Régression du réglage de taille de texte | Facteur multiplicatif conservé sur l'échelle entière |
