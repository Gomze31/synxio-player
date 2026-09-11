# 🎧 Synxio Player

Lecteur de musique **locale** pour Android. Pas de compte, pas de traceur, pas de pub :
il lit ce qu'il y a sur ton téléphone, et il le fait bien.

Écrit en **Kotlin + Jetpack Compose + Media3 (ExoPlayer)**, Material 3 avec couleurs
extraites de la pochette en cours de lecture.

---

## 🚀 Démarrage

Le projet utilise **Kotlin**, **Jetpack Compose** et **Media3 (ExoPlayer)**.

### Dans Android Studio

Ouvrez ce dossier avec Android Studio. Le projet téléchargera automatiquement les dépendances Gradle nécessaires.

**Prérequis** : JDK 17, SDK Android 36, Gradle 8.14.5 / AGP 8.13.2.
`minSdk 26` (Android 8.0) → `targetSdk 36` (Android 16).

> L'API 37 (Android 17) n'est distribuée qu'en canal *preview* du SDK. Cibler un SDK canary
> rendrait l'app impubliable et casserait à chaque révision : on reste sur le dernier SDK
> stable. Sur un appareil Android 17, l'app tourne alors dans les comportements Android 16,
> ce qui est le fonctionnement normal et supporté.

### En ligne de commande

```bash
./gradlew assembleDebug     # construit l'APK
./gradlew installDebug      # installe sur l'appareil branché en débogage USB
```

---

## ✨ Ce que fait l'app

### Lecture
- **Media3 / ExoPlayer** avec service en avant-plan : la lecture survit à la fermeture de l'app.
- **Gapless natif** pour les albums live et les concept-albums.
- **Fondu enchaîné** réglable de 0 à 12 s (fondu sortant / entrant sur un seul lecteur —
  voir « Limites connues »).
- **Vitesse et tonalité** indépendantes, de 0,5× à 2,5×.
- **Ignorer les silences** en début et fin de piste.
- **Minuterie de veille** : durée fixe ou « finir le morceau en cours », avec fondu à l'extinction.
- **File d'attente persistée** : tu retrouves ta file exactement où tu l'avais laissée.
- **Reprise Bluetooth / notification Android 13+** via `onPlaybackResumption`.
- Coupure automatique au débranchement du casque, gestion du focus audio.

### Bibliothèque
- Scan **MediaStore** (le seul qui reste correct sous Scoped Storage), avec observation
  temps réel : ajoute un fichier, il apparaît.
- Vues **Titres / Albums / Artistes / Genres / Dossiers**.
- **Tri** sur 10 critères pour les titres, 5 pour les albums, 3 pour les artistes.
- **Recherche floue** insensible aux accents et tolérante aux fautes (`edith` trouve `Édith Piaf`).
- **Filtre de durée minimale** pour écarter sonneries et notifications.
- **Favoris**, **plus écoutés**, **écoutés récemment**, **ajoutés récemment**.
- **Statistiques d'écoute** honnêtes : un morceau zappé avant la moitié compte comme skip,
  pas comme écoute.
- **Éditeur de tags ID3** (jaudiotagger), avec la demande de consentement système
  qu'exige Android 11+ pour écrire dans un fichier qu'on ne possède pas.

### Playlists
- Création, renommage, duplication, suppression, réordonnancement.
- **Import / export M3U8**, avec correspondance par chemin absolu puis par nom de fichier
  (les playlists venues d'un autre appareil retombent sur leurs pieds).

### Design
- **Material 3** avec accent extrait de la pochette (Palette), transition de couleurs animée
  à chaque changement de morceau.
- Thèmes **Système / Clair / Sombre / AMOLED** (noir pur), ou **Material You** (fond d'écran).
- **4 styles de lecteur** : Immersif, Vinyle (disque qui tourne), Carte, Minimal.
- Fond flouté depuis la pochette, pochettes feuilletables au doigt pour changer de piste.
- Mini-lecteur avec barre de progression, titres défilants, indicateur de lecture animé.

### Extras
- **Paroles synchronisées** : fichier `.lrc` à côté du morceau → tag `LYRICS` embarqué →
  cache local → [LRCLIB](https://lrclib.net) (API publique, sans clé). Toucher une ligne
  déplace la lecture.
- **Égaliseur système** : bandes détectées dynamiquement, préréglages du constructeur,
  bass boost, spatialisation, gain de volume.
- **Android Auto** et **Assistant** : arborescence complète (albums, artistes, genres,
  playlists, favoris) + « Ok Google, joue *tel artiste* ».
- **Widget d'écran d'accueil** (Glance) avec contrôles précédent / lecture / suivant.
- **Scrobbling Last.fm** (optionnel, voir ci-dessous).
- **Magic Capsule (Honor) / Dynamic Island-like** : fonctionne sans une ligne de code
  spécifique — voir ci-dessous.

### 🔮 Magic Capsule

Sur MagicOS, la capsule se nourrit de la `MediaSession` et de la notification média
**standard** : icône de l'app à gauche, onde audio animée à droite, contrôles au déploiement.
Media3 les fournit déjà, donc Synxio y apparaît nativement.

Il n'existe **pas de SDK Honor tiers** à intégrer : vérification faite sur l'appareil, aucun
paquet ni permission capsule n'est exposé aux applications. Le système gère seulement
`capsule_off_pkgs` (liste d'exclusion, vide par défaut) et un jeu de règles serveur
`hsm_capsule_data_version`. Autrement dit : une app média correctement construite est
éligible d'office, et c'est le cas ici.

---

## 📦 Publication Play Store

La signature est configurée avec des clés de test, ou avec vos propres clés si vous remplacez `synxio-upload.jks` et `keystore.properties`. Les artefacts se construisent avec :

```bash
./gradlew bundleRelease    # AAB signé → à téléverser sur le Play Store
./gradlew assembleRelease  # APK signé → pour tester en local
```

Sorties dans `app/build/outputs/` :
`bundle/release/app-release.aab` et `apk/release/app-release.apk`.

> ⚠️ **Sauvegardez soigneusement votre keystore.**
> Perdre la clé de signature rend toute mise à jour de l'app impossible sur le Play Store —
> il faudrait republier sous un nouveau nom de paquet. Les fichiers de clé sont ignorés par Git.

### Avant le premier envoi

- [ ] `versionCode` / `versionName` à incrémenter à chaque envoi (`app/build.gradle.kts`).
- [ ] Fiche Play : captures d'écran, icône 512×512, bannière 1024×500, description.
- [ ] Politique de confidentialité (obligatoire) — l'app ne collecte rien, mais la déclaration
      « Data safety » reste à remplir.
- [ ] Déclarer l'usage de `READ_MEDIA_AUDIO` dans le questionnaire des permissions sensibles.

## 🔑 Scrobbling Last.fm (optionnel)

L'option n'apparaît dans les réglages que si une clé d'API est compilée. Crée-en une sur
[last.fm/api/account/create](https://www.last.fm/api/account/create), puis ajoute dans
`~/.gradle/gradle.properties` :

```properties
lastfmApiKey=ta_cle
lastfmSecret=ton_secret
```

Sans ces valeurs, l'app fonctionne normalement, l'option est simplement masquée.

---

## 🏗 Architecture

```
fr.synxio.player
├── core/
│   ├── prefs/        DataStore : un objet Settings unique observable
│   └── util/         formatage de durées, recherche floue
├── data/
│   ├── db/           Room : playlists, favoris, stats, file d'attente, cache paroles
│   ├── media/        MediaStoreScanner (+ ContentObserver)
│   ├── model/        Song, Album, Artist, Genre, Folder, Playlist, Lyrics
│   ├── lastfm/       scrobbling
│   └── repo/         MusicRepository, PlaylistRepository, LyricsRepository, TagEditor
├── playback/
│   ├── PlaybackService     MediaLibraryService : possède ExoPlayer
│   ├── PlayerConnection    MediaController côté UI
│   ├── MediaLibraryTree    arborescence Android Auto / Assistant
│   ├── EqualizerController effets audio système
│   ├── FadeController      fondu entre morceaux
│   └── SleepTimer
├── ui/
│   ├── theme/        couleurs, typo, formes, extraction Palette
│   ├── components/   Artwork, SongRow, MiniPlayer, feuilles d'options
│   ├── screens/      accueil, bibliothèque, playlists, recherche, réglages,
│   │                 égaliseur, tags, détails, lecteur plein écran
│   └── viewmodel/    AppViewModel (partagé) + VM spécialisés
└── widget/           widget Glance
```

**Principes** :
- L'UI ne touche jamais ExoPlayer directement : tout passe par un `MediaController`.
  Une seule source de vérité, aucune fuite quand l'activité meurt.
- La bibliothèque est **dérivée** d'un unique scan MediaStore : albums, artistes, genres
  et dossiers sont des vues en mémoire, jamais des tables à resynchroniser.
- Room ne stocke que ce que MediaStore ne sait pas : playlists, favoris, stats, file.
  Les morceaux disparus s'évaporent automatiquement des playlists.

---

## ⚠️ Limites connues

- **Le fondu enchaîné ne se chevauche pas.** Un vrai crossfade demande deux instances
  d'ExoPlayer mixées ; ici c'est un fondu sortant suivi d'un fondu entrant. Réglé à 0,
  le gapless natif reprend la main.
- **Pas de visualiseur audio temps réel.** L'API `Visualizer` d'Android exige la permission
  `RECORD_AUDIO`, disproportionnée pour un lecteur local. L'indicateur de lecture est animé,
  pas branché sur le signal.
- **Le réordonnancement de la file** se fait par flèches haut/bas plutôt que par glisser-déposer.
- **Chromecast** n'est pas implémenté (nécessite `media3-cast` + Google Play Services).
- L'égaliseur dépend du constructeur : certains appareils n'exposent aucun effet système.

---

## 🔒 Vie privée

Aucun compte, aucune analytics, aucune publicité. Les seules connexions réseau possibles :
LRCLIB pour les paroles (désactivable) et Last.fm si tu l'as explicitement configuré.
Le reste ne quitte jamais l'appareil.
