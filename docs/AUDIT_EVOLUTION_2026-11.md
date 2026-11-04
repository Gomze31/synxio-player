# Synxio Player — Audit et propositions d'évolution

*Version auditée : 5.6.0 (branche `dev-5.5`, commit `7953e3c`). Date : 4 novembre 2026.*
*Audit en lecture seule : aucun fichier de code n'a été modifié.*

---

## 0. Résumé exécutif

Synxio Player est, en l'état, un lecteur local **très complet** : 181 fichiers Kotlin, 52 500 lignes, 28 écrans, 23 tables Room, un design system propre (Lumen), Android Auto, Cast, widgets Glance, livres audio avec chapitres, mixs du jour, récap, réveils, Galaxy Buds. Peu de lecteurs open source francophones vont aussi loin.

Mais l'app a grandi par **accumulation de fonctionnalités**, et quatre plafonds apparaissent clairement dans le code :

| Plafond | Symptôme vérifié | Conséquence |
|---|---|---|
| **Moteur audio « grand public »** | Décodage MediaCodec seul, pas de sortie float ni d'offload, « crossfade » = rampe de volume sur un seul lecteur, normalisation RMS non R128 qui n'atténue que, EQ dépendant du constructeur | La promesse « pour audiophiles » n'est pas tenue techniquement |
| **Architecture monolithe** | `AppViewModel` 22 dépendances / 83 fonctions, `PlaybackService` 1 935 lignes / 17 injections, `SettingsScreen` 2 522 lignes, aucun cache persistant de bibliothèque, recomposition racine à 4 Hz | Chaque nouvelle fonction coûte plus cher que la précédente ; démarrage à froid = scan complet |
| **Interface figée au téléphone** | 0 `WindowSizeClass`, 0 `values-xx` (1 220 littéraux français en dur), pas de balayage pour fermer le lecteur, pas de retour prédictif, haptique quasi absente | Pas de tablette, pas de pliable, pas de traduction possible, interactions moins « premium » que le visuel |
| **Sécurité de distribution** | Keystore release versionné avec mot de passe public, yt-dlp mis à jour et exécuté sans contrôle d'intégrité, secrets Last.fm/Discord en clair dans un DataStore sauvegardé dans le cloud | La vérification de signature des mises à jour ne protège rien tant que la rotation n'est pas faite |

Le fait marquant : **« l'écosystème Synxio » n'existe pas encore**. Le dépôt ne contient qu'un seul produit, ce lecteur Android. Aucune app desktop, web ou serveur. C'est une opportunité plus qu'un manque : les moteurs purs (`DailyMixEngine`, `RulePlaylistEngine`, `RecapCalculator`, `FileNameInference`, `AudioFeatureAnalyzer`) sont déjà du Kotlin sans Android, donc portables.

**Les 5 évolutions les plus structurantes** (détail en §4) :

1. **Moteur audio Synxio 2** — extension FFmpeg, sortie float/offload, vrai crossfade à deux lecteurs, EBU R128, EQ paramétrique DSP, visualiseur sans permission micro.
2. **Bibliothèque persistante et instantanée** — cache Room, scan incrémental, WorkManager, flux de position séparé de l'état global.
3. **Lumen Vivant** — scène réactive à l'audio (shaders AGSL), haptique musicale, lecteur qui se ferme au doigt, retour prédictif, mise en page adaptative tablette/pliable.
4. **Synxio Party 2 + Télécommande web** — jukebox collaboratif sur le Wi-Fi local et pilotage complet depuis un navigateur d'ordinateur : le premier pas de l'écosystème.
5. **Flow intelligent** — BPM et tonalité, transitions DJ, mode course au tempo, playlists en langage naturel.

Préalable non négociable à toute release : **la rotation de clé** déjà planifiée dans `docs/KEY_ROTATION.md`.

---

## 1. Phase 1 — Audit

### 1.1 Ce que l'app fait aujourd'hui (inventaire vérifié)

**Lecture** : Media3 1.11.1, service d'avant-plan, gapless natif, fondu 0–12 s, vitesse 0,5×–2,5× et tonalité, vitesse mémorisée par podcast/livre, veille (fondu, fin de morceau, fin de chapitre), limite de volume, retour en arrière à la reprise, file persistante, arrêt après 3 erreurs, boutons casque interprétés (1/2/3 appuis, appui long = avance rapide).

**Audio** : EQ système (`android.media.audiofx`) avec courbes indépendantes du matériel (`EqCurve`), profils par appareil, auto-EQ Galaxy Buds, basses, virtualizer, reverb, loudness enhancer, normalisation (ReplayGain lu ou RMS mesuré), karaoké (annulation de phase), visualiseur (`audiofx.Visualizer`, permission micro).

**Bibliothèque** : scan MediaStore + ContentObserver, exclusion de dossiers, livres audio par dossier ou drapeau, podcasts par drapeau, chapitres intégrés (M4B, MP3 CHAP), signets, éditeur de tags (jaudiotagger), atelier de réparation en lot avec sauvegarde, enrichissement MusicBrainz/CAA/Deezer/iTunes, inférence depuis le nom de fichier, réparation mojibake, doublons, découpe ffmpeg sans ré-encodage, notes 1–5, favoris.

**Découverte** : accueil « Pour toi », 4 familles de mixs du jour, 6 playlists intelligentes prédéfinies, playlists à règles (16 champs, ET/OU, tri, limite), ambiances (Sport/Relaxation/Focus par heuristique spectrale), similarité sonore (vecteur 27-D, distance euclidienne), auto-DJ par genre/artiste, recherche floue (OSA), recherche avancée, stats, historique, récap mensuel/annuel partageable.

**Paroles** : `.lrc` local, tag USLT, LRCLIB, cache Room, décalage réglable, traduction ML Kit on-device.

**Intégrations** : Android Auto (arbre 4 onglets), Google Cast via serveur HTTP local, tuile Réglages rapides, 2 widgets Glance, raccourcis, Last.fm, Discord (webhook + RPC non documenté), Galaxy Buds (batterie, codec, gestes, auto-EQ), Magic Capsule, radios radio-browser.info, téléchargement YouTube (yt-dlp), mode voiture, mode soirée (page web « en cours »), réveils musicaux, sauvegarde JSON, mises à jour GitHub avec lignée de signature v3.

**Interface** : Lumen (scène, verre, signature braise→magenta), 4 skins de lecteur, 9 presets, 4 modes de thème, accent depuis la pochette/Material You/custom, éléments partagés, cascades, réduction des animations, onboarding 4 pages, états vides et squelettes.

### 1.2 Points faibles par domaine

#### Architecture et qualité

- **God objects.** `AppViewModel.kt` (856 l.) : 22 dépendances, 83 fonctions, 22 `stateIn`, et il injecte des DAO directement (`:83-86`). `PlaybackService.kt` (1 935 l.) : 93 fonctions, 17 `@Inject lateinit`, 3 classes internes, Cast, Buds, Discord, Last.fm, widgets, loudness. `SettingsScreen.kt` : 2 522 lignes, 41 composables, héberge aussi les écrans Mise à jour et Discord.
- **Pas de couche domaine.** Les ViewModels appellent repos et DAO ; `MusicRepository.kt` mélange repository et fonctions top-level de tri/recherche.
- **Aucun cache persistant de bibliothèque.** Chaque démarrage à froid refait un scan MediaStore complet (`MusicRepository.kt:304, 349-357`). `resolveSong()` peut relancer un scan complet pour un seul id (`:486-489`). Sur Android ≤ 10, N+1 requêtes genre (`MediaStoreScanner.kt:190-212`).
- **Pas de WorkManager.** Analyses loudness et features audio tournent dans l'`ApplicationScope` : interrompues si le processus meurt (`LoudnessRepository.kt:95-142`).
- **Room** : 23 entités, schéma v16, 14 migrations mais pas de `MIGRATION_1_2` ni de fallback destructif (`AppModule.kt:53-58`). Huit tables sont lues entières en `Flow` (`Daos.kt:114, 271, 331, 347, 388, 407, 440`). Index absents hors 5 tables.
- **Tests** : 41 fichiers JVM, tous sur la logique pure. Aucun test ViewModel, service, DAO réel, scanner, Compose UI. Pas de `coroutines-test`, mockk, Turbine, Robolectric.
- **Observabilité** : 224 `runCatching` (71 suivis d'un `.getOrNull()` muet), aucun crash reporting même local, 2 `printStackTrace`.
- **Build** : mono-module, pas de baseline profile, pas de `profileinstaller`, `jniLibs.useLegacyPackaging = true` imposé par yt-dlp (APK plus gros, installation plus lente).
- **Dette** : 0 TODO (bon signe de discipline) mais 3 pipelines Palette distincts (`ArtworkColors.kt:78`, `ArtworkColorRepository.kt:94`, `WidgetArtRenderer.kt:74`), `ArtworkColorRepository` et sa table quasi morts côté UI, M3U8 dupliqué entre `PlaylistRepository` et `BackupRepository`, enum `ThemeColor` doublonnant `ThemePreset`, réglage `secondaryColor` sans effet (`MainActivity.kt:169-177`).

#### Performance

- **Recomposition racine à 4 Hz** : `SynxioRoot.kt:271` et `NowPlayingScreen.kt:178` collectent tout `playerState`, qui contient `positionMs` mis à jour toutes les 250 ms (60 ms en boucle A-B). `remember(..., state.positionMs)` recalcule `BookProgress.live` à chaque tick.
- **FFT collectée à la racine du lecteur** (`NowPlayingScreen.kt:238`), avec `fft.clone()` à chaque capture.
- **Bitmaps pleine résolution** décodés avant Palette (`ArtworkColorRepository.kt:117`, `ShareCardRepository.kt:279`, `WidgetUi.kt:359`).
- **`PcmDecoder` accumule dans un `ArrayList<Short>`** avec boxing par échantillon (`PcmDecoder.kt:118, 173-184`).
- **`playMoodRadio` standardise toute la bibliothèque sur le thread principal** (`AppViewModel.kt:230-232`, `SimilarityRepository.kt:180-184`) ; `similarTo` recalcule les statistiques globales à chaque appel.
- **`runBlocking` dans `onDestroy`** du service (`PlaybackService.kt:413`), borné par un timeout.
- Listes sans `key` : `Sheets.kt:214` (paroles), `RepairScreen.kt:738`, `RulePlaylistEditorScreen.kt:228`, `TagEditorScreen.kt:593`.
- `SettingsRepository` : un seul `Flow<Settings>` sans `distinctUntilChanged`, et `LAST_SCAN` dans le même DataStore : chaque scan fait réémettre tous les réglages.

#### Moteur audio

- **Pas d'extension FFmpeg Media3** (le FFmpeg présent est celui de yt-dlp). ALAC non garanti, DSD/APE/WavPack/TrueHD absents.
- **Sortie float et offload ignorés** (`PlaybackService.kt:245-252`) : AudioFlinger rééchantillonne, pas de bit-perfect sur DAC USB, pas d'économie batterie par offload.
- **Le crossfade n'en est pas un** : fondu sortant puis entrant sur un seul lecteur, avec un creux, polling à 100 ms (`FadeController.kt:20-23, 171-184`).
- **Normalisation** : RMS sur 6 fenêtres, sans pondération K ni gating (pas EBU R128), ne fait qu'atténuer (`LoudnessRepository.kt:72-78`), ignore le gain album et le peak, non appliquée en Cast.
- **Égaliseur à la merci du constructeur** : bandes et presets découverts dynamiquement, tout désactivé si `Equalizer` échoue, `setEnabled` couple EQ/bass/virtualizer/reverb/loudness (`EqualizerController.kt:131-139`). Les courbes casques sont dérivées « à l'oreille » (`EqCurve.kt:66-71`).
- **Visualiseur exige `RECORD_AUDIO`** : permission anxiogène pour un lecteur « sans pistage », alors qu'un `AudioProcessor` custom existe déjà dans la chaîne.
- **Karaoké** : 16 bits stéréo seulement, sortie mono, supprime basse et grosse caisse, actif seulement au morceau suivant.
- **Ambiances** : heuristique fragile ; la planéité spectrale élevée (= bruité) est lue comme « détente » (`MoodRadioRepository.kt:63-67`). Pas de BPM, pas de tonalité.
- **Party** : port 8080 fixe (échec silencieux si occupé), champs lus par les threads NanoHTTPD non `@Volatile`, page qui charge Google Fonts en externe. C'est un simple affichage « en cours », pas une écoute partagée.
- `SleepTimer` compte par `delay(1000)` sans horloge réelle. Boucle A-B pilotée côté UI par polling.

#### Interface et expérience

- **Aucune adaptation grand écran** : 0 `WindowSizeClass`, 0 `NavigationRail`, 0 `ListDetailPaneScaffold`, pas de mise en page paysage, même pour le lecteur.
- **Non traduisible** : une seule langue, 1 220 littéraux français en dur, libellés d'enum en dur.
- **Lecteur** : pas de balayage vers le bas pour fermer, pas de `PredictiveBackHandler` (le manifest active le retour prédictif, mais l'UI ne l'exploite pas).
- **Haptique** uniquement sur le glisser-déposer (`Reorderable.kt:188-197`). Rien sur lecture, cœur, onglets, balayages.
- **Réglages fourre-tout** : ~50 contrôles dans 9 sous-menus, « Lecture & Audio » mêle gapless, veille, Buds et réveils. Pas de recherche dans les réglages.
- **Fonctions profondes enterrées** (EQ, réparation, doublons, stats, sauvegarde, réveils) accessibles seulement par les réglages ou le menu du lecteur.
- **Incohérences** : « Radios » (onglet) vs « Ambiances » (sous-onglet), l'onglet « Musique » contient Podcasts et Playlists, deux modèles d'en-tête (`ScreenHeader` vs `TopAppBar`), 19 `AlertDialog` Material bruts, `Checkbox`/`RadioButton`/`Button` Material résiduels, 11 valeurs de mouvement hors `Motion`.
- **Accessibilité** : 80 `contentDescription = null` sur 186, icônes d'onglets sans description, aucun test TalkBack. Contraste et `fontScale` : corrects.
- **Erreurs** : ~15 messages d'erreur, 3 « Réessayer », pas de composant d'erreur commun.
- **Aucun deep link** de navigation ; les intents sont traités à la main.

#### Données, intégrations, sécurité

- **Keystore release versionné dans le dépôt public** avec mot de passe `synxioplayer` en clair (`app/build.gradle.kts:84-86`). Reconnu dans `KEY_ROTATION.md` §1 ; la nouvelle clé reste à générer.
- **yt-dlp** : binaire téléchargé et exécuté à l'exécution, canal STABLE, sans vérification d'intégrité côté app (`DownloadRepository.kt:43-45, 141-152`).
- **Secrets en clair dans le DataStore** (session Last.fm, webhook Discord, `SettingsRepository.kt:370, 377`) et le DataStore est inclus dans `backup_rules.xml` : ils partent dans la sauvegarde cloud Android.
- **Sauvegarde Synxio** : JSON en clair, 20 Mo max, sans playlists à règles, dossiers exclus, dossiers de livres, décalages de paroles.
- `network_security_config` : HTTP en clair autorisé globalement (nécessaire aux radios) ; musicbrainz.org et coverartarchive.org absents de la liste HTTPS forcée.
- User-Agent radio-browser figé à « SynxioMediaPlayer/4.1.0 ». Favoris radio dans des SharedPreferences, à part de Room.
- **Absents** : WorkManager, stockage chiffré, cue sheets, `.nomedia` explicite, tags multi-valeurs natifs, podcasts RSS, ListenBrainz, AcoustID, sources réseau (Subsonic/Jellyfin/SMB/WebDAV), synchronisation multi-appareils.

### 1.3 Forces à préserver

- Lumen est un vrai système : tokens, motion, composants, document. Aucun écran n'est resté en Material brut.
- Les moteurs purs sont testés et portables (`DailyMixEngine`, `RulePlaylistEngine`, `RecapCalculator`, `BookProgress`, `FileNameInference`, politiques de lecture).
- Sécurité réseau réfléchie : jetons sur les serveurs locaux, validation stricte des URL YouTube, HTTPS forcé, aucun SDK analytics.
- Discipline de release : CI, notes de version soignées, lignée v3 préparée, rotation documentée.
- Positionnement clair : local, privé, francophone, sans compte.

---

## 2. Phase 2 et 3 — Propositions classées

Légende. **Difficulté** : Faible (< 2 j), Moyenne (2–5 j), Élevée (1–3 sem.), Très élevée (> 3 sem.). **Impact UX** : ★ à ★★★★★.

### 2.1 Améliorations indispensables

Ces points conditionnent la fiabilité, la sécurité ou la capacité à faire évoluer l'app. Ils sont peu visibles mais tout le reste s'appuie dessus.

**I1. Rotation de clé et assainissement du dépôt** — Générer la clé v3, activer `SYNXIO_REQUIRE_RELEASE_KEY`, supprimer `app/synxio-release.keystore` et les APK à la racine, épingler l'empreinte, purger l'historique.
*Intérêt :* sans cela, la vérification de signature des mises à jour est décorative. *Difficulté :* Moyenne (procédure déjà écrite). *Impact :* ★★ (invisible, mais vital). *Dépendances :* aucune ; **préalable à tout le reste**.

**I2. Secrets chiffrés et exclus de la sauvegarde cloud** — Session Last.fm et webhook Discord dans un stockage chiffré par Android Keystore ; règles de sauvegarde ajustées.
*Intérêt :* cohérence avec la promesse « sans pistage ». *Difficulté :* Faible. *Impact :* ★★. *Dépendances :* aucune.

**I3. Intégrité de yt-dlp** — Vérifier le SHA-256 du binaire contre la release GitHub de yt-dlp avant exécution, ou figer la version embarquée et ne mettre à jour qu'avec l'app.
*Intérêt :* un binaire téléchargé puis exécuté sans contrôle est le maillon le plus faible de l'app. *Difficulté :* Moyenne. *Impact :* ★★. *Dépendances :* I1.

**I4. Bibliothèque persistante et scan incrémental** — Table Room `songs` alimentée par le scan, scan différentiel par `DATE_MODIFIED`/`GENERATION_MODIFIED`, l'UI lit Room et non le scanner. Suppression du rescan complet dans `resolveSong()`.
*Intérêt :* démarrage instantané même avec 20 000 titres, tri/recherche en SQL, base pour les fonctions suivantes. *Difficulté :* Élevée. *Impact :* ★★★★. *Dépendances :* aucune.

**I5. Tâches de fond fiables (WorkManager)** — Analyses loudness, features audio, enrichissement, couleurs de pochettes, sauvegarde planifiée : dans des `Worker` avec contraintes (batterie, Wi-Fi) et reprise après mort du processus.
*Intérêt :* les analyses finissent vraiment ; batterie respectée. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* I4 souhaitable.

**I6. Flux de position séparé** — Sortir `positionMs` de `playerState` ; exposer un `positionFlow` consommé uniquement par la barre de progression, les paroles et les chapitres.
*Intérêt :* fin de la recomposition de la racine à 4 Hz ; fluidité et batterie. *Difficulté :* Faible à Moyenne. *Impact :* ★★★. *Dépendances :* aucune.

**I7. Découpage des god objects** — `AppViewModel` en ViewModels par fonction (Queue, Books, Playlists, Radios, Tools) ; `PlaybackService` délesté vers des collaborateurs injectés (Cast, Buds, Social, Loudness, Widgets) ; `SettingsScreen` en fichiers par section.
*Intérêt :* chaque fonction future coûte moins ; tests possibles. *Difficulté :* Élevée (mécanique mais longue). *Impact :* ★ direct, ★★★★ indirect. *Dépendances :* aucune, mais à faire avant les grosses fonctions.

**I8. Internationalisation** — Extraction des 1 220 littéraux vers `strings.xml`, libellés d'enum via ressources, anglais en première langue ajoutée, langue par app (API 33+).
*Intérêt :* l'app est aujourd'hui enfermée dans le marché francophone. *Difficulté :* Élevée (volume) mais mécanique ; automatisable en partie. *Impact :* ★★★★ (audience). *Dépendances :* aucune.

**I9. Mise en page adaptative** — `WindowSizeClass`, `NavigationRail` en large, liste-détail sur tablette, lecteur paysage à deux colonnes (pochette | paroles/file), support pliable.
*Intérêt :* tablettes, pliables, Chromebook, mode bureau. *Difficulté :* Élevée. *Impact :* ★★★★ pour ces appareils. *Dépendances :* I7 (écrans plus petits = plus faciles à adapter).

**I10. Gestes manquants du lecteur** — Balayage vers le bas pour réduire, retour prédictif avec aperçu progressif (`PredictiveBackHandler`), rétraction continue vers le mini-lecteur.
*Intérêt :* c'est le geste que tout utilisateur essaie en premier. *Difficulté :* Moyenne. *Impact :* ★★★★. *Dépendances :* aucune.

**I11. Tests de la couche vivante** — `coroutines-test` + Turbine pour les ViewModels, Room in-memory pour les DAO, Robolectric ou Compose UI test pour 5 écrans clés, test d'intégration du scanner.
*Intérêt :* filet de sécurité pour I4, I7 et le moteur audio. *Difficulté :* Moyenne. *Impact :* ★ direct. *Dépendances :* I7 facilite.

**I12. Journal de diagnostic local** — Tampon circulaire de logs + capture des exceptions non gérées dans un fichier, exportable depuis « À propos », sans tiers.
*Intérêt :* comprendre les crashs sans trahir « sans pistage ». *Difficulté :* Faible. *Impact :* ★★. *Dépendances :* aucune.

**I13. Démarrage et taille** — Baseline profile + `profileinstaller`, unification des 3 pipelines Palette sur le cache `artwork_colors`, sous-échantillonnage des bitmaps, `PcmDecoder` sur `ShortArray`.
*Intérêt :* premier écran plus rapide, moins de jank. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* aucune.

**I14. Réglages repensés** — Recherche dans les réglages, regroupement par intention (Écouter / Voir / Bibliothèque / Appareils / Compte & données), section « Appareils » qui réunit Buds, sortie audio, Auto, Cast.
*Intérêt :* 50 contrôles deviennent trouvables. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* I7.

### 2.2 Améliorations premium

Ce qui fait passer l'app de « complète » à « de référence ».

**P1. Moteur audio Synxio 2 : décodage et sortie** — Extension FFmpeg Media3 (ALAC, APE, WavPack, DSD→PCM, TrueHD), sortie float activée, audio offload quand disponible, détection des capacités de sortie (taux, profondeur, codec BT pour tous les casques), indicateur « bit-perfect » dans le lecteur.
*Intérêt :* la promesse audiophile devient réelle ; un badge « 24 bit / 96 kHz · LDAC » est un argument en soi. *Difficulté :* Élevée (build de l'extension FFmpeg, taille APK par ABI). *Impact :* ★★★★★ pour la cible. *Dépendances :* I7 (sortir la config audio du service).

**P2. Vrai crossfade à deux lecteurs** — Deux `ExoPlayer` alternés, chevauchement réel, courbes (linéaire, equal-power, « smart » : détection de silence en fin de piste pour ne pas couper un fade-out naturel), préservation du gapless album.
*Intérêt :* l'actuel « creux » est audible ; c'est une fonctionnalité phare des concurrents. *Difficulté :* Élevée (double session audio, effets à dupliquer). *Impact :* ★★★★. *Dépendances :* P1, P4.

**P3. Normalisation EBU R128 et ReplayGain complet** — Mesure LUFS intégrée (K-weighting, gating) faite par un `AudioProcessor` d'écoute pendant la lecture (gratuit en CPU, pas de second décodage), gain album, respect du peak, préamp réglable, amplification possible via `LoudnessEnhancer`, application aussi en Cast via le gain de file.
*Intérêt :* volume homogène sans distorsion ; standard des audiophiles. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* P4.

**P4. Égaliseur DSP indépendant du constructeur** — `AudioProcessor` paramétrique (10 bandes biquad + shelf grave/aigu, Q réglable), préréglages, import de profils AutoEq (base oratory1990, fichiers ParametricEQ), remplacement des courbes « à l'oreille » par de vraies corrections.
*Intérêt :* même rendu sur tous les téléphones ; auto-EQ pour des centaines de casques. *Difficulté :* Élevée. *Impact :* ★★★★. *Dépendances :* aucune (peut précéder P1).

**P5. Visualiseur sans permission micro** — `TeeAudioProcessor` dans la chaîne ExoPlayer qui alimente la FFT ; suppression de `RECORD_AUDIO`.
*Intérêt :* une permission en moins à justifier, et des données exactes (PCM post-EQ). *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* aucune. *Note :* devient la base de P3 et du Lumen Vivant (N1).

**P6. Paroles de nouvelle génération** — Mode karaoké plein écran mot à mot (format LRC enrichi `<mm:ss.xx>`), éditeur de synchronisation « tape sur chaque ligne », lecture SYLT, paroles dans la notification/écran de verrouillage, bulle flottante par-dessus les autres apps (optionnelle).
*Intérêt :* les paroles sont la fonction la plus utilisée après la lecture ; l'éditeur crée du contenu que LRCLIB n'a pas. *Difficulté :* Moyenne à Élevée. *Impact :* ★★★★. *Dépendances :* I6 (position précise).

**P7. Haptique musicale** — Retour tactile sur lecture/pause, cœur, onglets, balayages du mini-lecteur, fin de glissement de la progression ; option « pulsation au tempo » quand le BPM est connu.
*Intérêt :* le visuel est premium, le toucher ne l'est pas encore. *Difficulté :* Faible (Moyenne pour le tempo). *Impact :* ★★★. *Dépendances :* N3 pour le tempo.

**P8. Podcasts par flux RSS** — Abonnements, import OPML, téléchargement des épisodes, lecture en streaming, reprise, notifications de nouveaux épisodes.
*Intérêt :* aujourd'hui, un podcast n'existe que s'il est déjà sur le téléphone. *Difficulté :* Élevée. *Impact :* ★★★★ pour les auditeurs de podcasts. *Dépendances :* I4, I5.

**P9. Sauvegarde 2** — Chiffrement AES-GCM avec phrase de passe, contenu complet (playlists à règles, dossiers, décalages), sauvegarde planifiée vers un dossier SAF (donc Drive, Nextcloud, carte SD), restauration sélective.
*Intérêt :* confiance ; migrer de téléphone sans rien perdre. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* I5.

**P10. Boîte de réception et triage** — Vue « Nouveaux titres non triés » (téléchargements, ajouts récents sans album) avec triage par balayage : accepter, taguer via l'inférence/MusicBrainz, ajouter à une playlist, supprimer.
*Intérêt :* le flux YouTube → Musique/Synxio crée des fichiers orphelins ; le triage les transforme en bibliothèque propre. *Difficulté :* Moyenne. *Impact :* ★★★★. *Dépendances :* I4.

**P11. Édition de tags en lot et tags multi-valeurs** — Sélection multiple → modifier artiste/album/genre/année, écriture ID3v2.4 multi-valeurs (artistes multiples, genres multiples), lecture native des champs multi-valués.
*Intérêt :* l'inférence `splitArtistTag` devient inutile ; « feat. » enfin propre. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* aucune.

**P12. Widgets étendus** — Tailles 2×2 et 5×1, widget « Paroles en cours », widget « Reprendre ce livre », widget « Mix du jour » avec pochettes, prise en charge de l'écran de verrouillage Android 16+.
*Intérêt :* présence sur l'écran d'accueil = retour fréquent. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* aucune.

**P13. Mode voiture 2 et lancement automatique** — Détection de la voiture (déjà faite par le nom Bluetooth) → proposition d'ouvrir le mode voiture ; boutons énormes, commandes vocales via l'Assistant, luminosité auto, file simplifiée.
*Intérêt :* sécurité et usage quotidien. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* aucune.

**P14. Statistiques « Insights »** — Heatmap annuelle, séries (streaks), comparaison mois/mois, horloge d'écoute, « ce jour-là il y a un an », export CSV.
*Intérêt :* le Récap existe une fois par mois ; Insights vit tous les jours. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* aucune (play_history suffit).

**P15. Composant d'erreur et états unifiés** — `ErrorState` commun avec « Réessayer », messages explicites, et un seul `EmptyState`.
*Intérêt :* cohérence ; moins de snackbars muettes. *Difficulté :* Faible. *Impact :* ★★. *Dépendances :* aucune.

**P16. Accessibilité complète** — Descriptions sur les 80 icônes muettes, ordre de focus, test TalkBack des 5 parcours clés, mode contraste élevé.
*Intérêt :* inclusion ; prérequis à toute boutique d'app. *Difficulté :* Moyenne. *Impact :* ★★ (★★★★★ pour les concernés). *Dépendances :* aucune.

### 2.3 Fonctionnalités innovantes

Ce qui différencie Synxio de tout autre lecteur local.

**N1. Lumen Vivant** — La scène réagit à la musique : l'aurore pulse sur l'énergie des basses, les halos dérivent avec le centroïde spectral, le verre se réfracte (shaders AGSL `RuntimeShader` sur API 33+, repli Canvas en dessous), pochette « canvas » générative (boucle animée dérivée de la palette, calculée localement, façon Spotify Canvas sans réseau). Budget batterie : 30 fps max, coupé si « Réduire les animations » ou batterie faible.
*Intérêt :* signature visuelle unique, cohérente avec l'identité « scène éclairée par la pochette ». *Difficulté :* Élevée. *Impact :* ★★★★★ (effet waouh immédiat). *Dépendances :* P5 (données audio sans micro).

**N2. Synxio Party 2 : jukebox collaboratif** — La page web locale devient un jukebox : les invités parcourent la bibliothèque de l'hôte, cherchent, ajoutent à la file, votent pour faire monter un titre ; l'hôte garde la main (modération, verrouillage). QR d'invitation existant, port dynamique, jeton par invité.
*Intérêt :* usage social réel, aucune app à installer côté invité, aucune donnée hors du Wi-Fi. *Difficulté :* Élevée (SPA légère embarquée + API REST/SSE sur NanoHTTPD). *Impact :* ★★★★★ en soirée. *Dépendances :* I4 (recherche SQL rapide), correctifs Party existants.

**N3. Empreinte sonore 2 : BPM, tonalité, énergie** — Ajout au vecteur existant : tempo (autocorrélation d'enveloppe d'onsets), tonalité (chroma + profils Krumhansl), énergie, « danceabilité ». Stockés dans `audio_features`, affichés dans le lecteur (« 124 BPM · La mineur »), filtrables dans les playlists à règles.
*Intérêt :* fondation de N4, N5, P7 ; les Ambiances deviennent fiables. *Difficulté :* Élevée (DSP) mais isolée dans un moteur pur testable. *Impact :* ★★★ direct, ★★★★★ via dérivés. *Dépendances :* I5.

**N4. Flow : transitions DJ** — Mode « Flow » qui réordonne la file pour enchaîner par BPM et tonalité compatibles (roue de Camelot), crossfade calé sur le tempo, « mix continu » à partir d'un titre.
*Intérêt :* aucune app locale grand public ne le fait ; usage fête, sport, concentration. *Difficulté :* Moyenne une fois N3 et P2 faits. *Impact :* ★★★★. *Dépendances :* N3, P2.

**N5. Mode course au tempo** — Cadence de pas mesurée (capteur de pas, pas de GPS), sélection de titres dont le BPM suit la cadence (±3 %), ajustement fin de la vitesse ±5 % sans changement de tonalité, affichage grand format.
*Intérêt :* fonctionnalité phare des apps de sport, ici sans compte ni abonnement. *Difficulté :* Moyenne. *Impact :* ★★★★ pour les sportifs. *Dépendances :* N3.

**N6. Playlists en langage naturel** — Champ « Décris ta playlist » : « rock énergique des années 90 jamais écouté depuis un an, 1 h max » → règles générées. Analyseur local (grammaire + synonymes) d'abord ; sur les appareils compatibles (Pixel 8+, Galaxy S24+), ML Kit GenAI / Gemini Nano on-device en second niveau, sans réseau.
*Intérêt :* l'éditeur de règles (16 champs) devient accessible à tout le monde. *Difficulté :* Moyenne (local) / Élevée (GenAI). *Impact :* ★★★★. *Dépendances :* `RulePlaylistRules` existant ; N3 pour « énergique ».

**N7. Télécommande web / expérience bureau** — Extension du serveur local : interface web complète (file, recherche, lecture, volume, paroles, EQ) ouverte depuis un ordinateur sur le même réseau, installable en PWA, avec « Écouter ici » qui diffuse le flux vers le navigateur.
*Intérêt :* c'est la première brique de l'« écosystème » : Synxio sur le bureau sans écrire une app desktop. *Difficulté :* Élevée. *Impact :* ★★★★. *Dépendances :* N2 (même socle web).

**N8. Mémoire musicale** — Cartes « Ce jour-là » (il y a 1 an, 2 ans), « Capsule temporelle » (playlist d'une période passée), « Premières fois » (découvertes), rappel doux des albums jamais terminés.
*Intérêt :* émotion et rétention à coût quasi nul ; `play_history` suffit. *Difficulté :* Faible. *Impact :* ★★★★. *Dépendances :* aucune.

**N9. Reconnaissance acoustique hors ligne (AcoustID)** — Empreinte Chromaprint calculée localement, résolution via AcoustID → MusicBrainz, pour les fichiers sans tag ni nom exploitable.
*Intérêt :* résout le dernier cas où l'atelier de tags échoue. *Difficulté :* Élevée (binding natif chromaprint, ou portage Kotlin de l'algorithme). *Impact :* ★★★ pour les grosses bibliothèques sales. *Dépendances :* I5.

**N10. Partage de proximité** — Envoyer une playlist (M3U8 + pochette, ou fichiers) à un autre téléphone Synxio par QR, Quick Share ou Wi-Fi local ; réception = correspondance par métadonnées dans la bibliothèque du receveur.
*Intérêt :* partage sans cloud, dans l'esprit de l'app. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* N2 (serveur) facultatif.

**N11. Veille adaptative** — Au lieu d'un compte à rebours fixe : baisse progressive sur 20 min, pause si le téléphone est immobile depuis N minutes (capteur déjà présent : `ShakeDetector`) et que l'écran est éteint ; secouer pour prolonger.
*Intérêt :* la minuterie devient « magique ». *Difficulté :* Faible. *Impact :* ★★★. *Dépendances :* corriger l'horloge du `SleepTimer`.

**N12. Vue Dossiers réelle et sources externes** — Arbre de dossiers navigable (et non une liste plate), puis client Subsonic/Navidrome/Jellyfin (API stable, auto-hébergé, dans l'esprit privacy) avec cache hors ligne.
*Intérêt :* l'app devient utilisable avec une bibliothèque sur NAS. *Difficulté :* Élevée (Subsonic) ; Faible (arbre). *Impact :* ★★★★ pour les auto-hébergeurs. *Dépendances :* I4 (abstraction de source).

**N13. Intents publics et automatisation** — API d'intents documentée (lire X, playlist Y, régler le volume, lancer un mix, ambiance) pour Tasker/Macrodroid/raccourcis Assistant, plus App Actions.
*Intérêt :* les utilisateurs avancés construisent leurs routines (réveil → mix du jour, voiture → playlist). *Difficulté :* Faible. *Impact :* ★★★. *Dépendances :* aucune.

**N14. ListenBrainz** — Scrobbling vers ListenBrainz (ouvert, sans clé secrète, jeton utilisateur) en plus de Last.fm, import de l'historique.
*Intérêt :* alternative libre cohérente avec le positionnement. *Difficulté :* Faible. *Impact :* ★★. *Dépendances :* aucune.

### 2.4 Fonctionnalités expérimentales

À prototyper derrière un drapeau, sans promesse.

**X1. Séparation de pistes on-device** — Remplacer l'annulation de phase par un modèle de séparation vocal/instrumental embarqué (TFLite/ONNX, type Spleeter réduit), calculé hors ligne par WorkManager, mis en cache.
*Intérêt :* karaoké réel, sans mono ni perte des basses. *Difficulté :* Très élevée (modèle, taille, temps de calcul). *Impact :* ★★★★ si ça marche. *Dépendances :* I5.

**X2. Récap vidéo** — Génération d'une vidéo verticale 15 s (ffmpeg déjà embarqué) à partir des cartes du Récap, avec extraits audio des 3 tops.
*Intérêt :* partage sur les réseaux ; viralité. *Difficulté :* Moyenne. *Impact :* ★★★. *Dépendances :* ffmpeg de youtubedl-android (déjà là).

**X3. Calibration de pièce** — Balayage sinusoïdal joué sur le haut-parleur, capté par le micro, courbe de correction appliquée au haut-parleur du téléphone.
*Intérêt :* gadget audiophile amusant ; nécessite `RECORD_AUDIO` à la demande seulement. *Difficulté :* Élevée. *Impact :* ★★. *Dépendances :* P4.

**X4. Wear OS** — Module montre : commandes, file, favoris, tuile, complication « en cours ».
*Intérêt :* sport et quotidien. *Difficulté :* Élevée (nouveau module, data layer). *Impact :* ★★★ pour les porteurs de montre. *Dépendances :* I7.

**X5. Génération de paroles hors ligne** — Reconnaissance vocale on-device (Whisper tiny quantifié) pour produire un `.lrc` approximatif sur les titres sans paroles.
*Intérêt :* couvre le long tail que LRCLIB ignore. *Difficulté :* Très élevée (qualité sur musique mixée douteuse ; Vosk a déjà été retiré pour sa taille). *Impact :* ★★. *Dépendances :* P6.

**X6. Compose Multiplatform desktop** — App bureau (Windows/macOS/Linux) partageant les moteurs purs et le modèle, lecture locale + télécommande du téléphone.
*Intérêt :* l'écosystème au sens propre. *Difficulté :* Très élevée. *Impact :* ★★★★ à long terme. *Dépendances :* N7 (protocole), extraction d'un module `core` sans Android.

**X7. Commentaire DJ on-device** — Sur appareils Gemini Nano : courte phrase d'introduction d'un mix (« Trois titres que tu n'as pas écoutés depuis 2024… ») affichée, jamais vocalisée.
*Intérêt :* personnalité ; zéro réseau. *Difficulté :* Moyenne mais dépendante du parc. *Impact :* ★★. *Dépendances :* N6.

---

## 3. Récapitulatif par difficulté et impact

| | Faible | Moyenne | Élevée | Très élevée |
|---|---|---|---|---|
| **★★★★★** | | | P1, N1, N2 | |
| **★★★★** | N8 | I10, P6, P10, N4, N5, N6, N12 (arbre) | I4, I8, I9, P2, P4, P8, N3, N7, N12 (Subsonic) | X1, X6 |
| **★★★** | I6, N11, N13 | I5, I13, I14, P3, P5, P7, P9, P11, P12, P13, P14, N10, X2 | I7, N9, X4 | |
| **★★** | I2, I12, P15, N14 | I1, I3, P16, X7 | X3 | X5 |
| **★** | | I11 | | |

Lecture : la colonne « Faible » est à prendre en priorité quelle que soit la ligne (gain net immédiat). Les cases ★★★★ / Moyenne sont le meilleur rapport effort-impact.

---

## 4. Les 5 évolutions à plus fort impact

**1. Moteur audio Synxio 2** (P1 + P2 + P3 + P4 + P5)
Un seul chantier cohérent : extension FFmpeg, sortie float/offload, deux lecteurs pour un vrai crossfade, mesure R128 par écoute en temps réel, EQ DSP paramétrique avec AutoEq, FFT sans micro. C'est ce qui rend crédible la ligne « pour audiophiles » du README. Prérequis technique : sortir la configuration audio de `PlaybackService` (I7 partiel). Estimation : 4 à 6 semaines.

**2. Bibliothèque persistante et instantanée** (I4 + I5 + I6 + I13)
Démarrage immédiat, analyses qui finissent, UI qui ne se recompose plus à chaque tick. Rend possibles P8, P10, N2, N7, N12. Estimation : 3 à 4 semaines.

**3. Lumen Vivant** (N1 + I10 + P7 + I9)
La scène réagit au son, le lecteur se ferme au doigt avec le retour prédictif, le toucher répond, et tout cela s'étire à la tablette. C'est l'évolution la plus visible et la plus « Synxio ». Estimation : 4 à 5 semaines.

**4. Synxio Party 2 + Télécommande web** (N2 + N7)
Jukebox collaboratif et pilotage depuis un navigateur d'ordinateur, sur le même socle web embarqué. C'est la première matérialisation de l'écosystème, sans serveur externe et sans compte. Estimation : 4 à 6 semaines.

**5. Flow intelligent** (N3 + N4 + N5 + N6 + N8)
BPM, tonalité, transitions DJ, course au tempo, playlists décrites en français, mémoire musicale. Cela transforme les mixs du jour en vrai « moteur de découverte local ». Estimation : 5 à 7 semaines.

Et avant tout : **I1 rotation de clé** (1 semaine, procédure déjà écrite). Toute release qui précède la rotation maintient une faille connue.

---

## 5. Phase 4 — Vision : Synxio 6 « Lumen Vivant »

### 5.1 Identité visuelle

Lumen reste. Il n'est pas à refaire, il est à **animer et étendre**.

- **La lumière écoute.** L'aurore n'est plus une dérive lente mais une respiration calée sur la musique : les basses dilatent les halos, les aigus les font scintiller, le silence les fige. Un morceau calme et un morceau violent n'ont pas le même ciel.
- **Le verre a une épaisseur.** Réfraction et dispersion légères par shader (AGSL), de sorte que les surfaces semblent posées *sur* la lumière et non peintes dessus. Repli propre sur Android 12 et moins.
- **Chaque pochette a un canvas.** Une boucle animée de 8 secondes, générée localement depuis la palette et l'empreinte sonore (lent et chaud pour un titre doux, rapide et contrasté pour un titre énergique). Jamais téléchargée, toujours cohérente.
- **La signature braise→magenta s'étend au son.** Le visualiseur, la barre de progression et la ligne de paroles chantée partagent le même dégradé, qui pulse sur le tempo.
- **Typographie et grille inchangées** (Bricolage Grotesque + Manrope, grille 4 dp) : la continuité fait partie de l'identité.

### 5.2 Expérience utilisateur

- **Trois gestes pour tout.** Balayer vers le haut : le lecteur. Vers le bas : le réduire, avec retour prédictif continu. Latéralement : piste suivante/précédente, partout (mini-lecteur, lecteur, widget).
- **L'accueil devient un fil vivant.** Reprendre ce livre, Mix du jour, Ce jour-là il y a un an, Boîte de réception (3 nouveaux titres à trier), Flow proposé pour l'heure (« Soirée : 118–126 BPM »). Chaque carte est une action, pas une décoration.
- **Une seule « Musique », des vues.** Titres, Albums, Artistes, Genres, Dossiers (arbre), Podcasts, Livres, Playlists : des vues de la même bibliothèque, avec un sélecteur de vue persistant et une recherche omniprésente qui comprend les règles (« rock 90 non écouté »).
- **Les outils sortent des réglages.** Un onglet ou une page « Atelier » regroupe EQ, réparation, doublons, découpe, boîte de réception, sauvegarde, stats. Les réglages ne contiennent plus que des préférences.
- **Le lecteur a des couches, pas des feuilles.** Pochette/canvas → paroles → file → chapitres, en balayant verticalement dans le lecteur, chaque couche gardant la scène lumineuse derrière elle.
- **Le toucher répond.** Haptique sur chaque geste significatif, pulsation optionnelle au tempo.
- **Adaptatif de bout en bout.** Téléphone : barre du bas. Pliable ouvert et tablette : rail latéral + liste-détail, lecteur à deux colonnes. Navigateur : la télécommande web reprend les mêmes tokens Lumen (CSS généré depuis `Tokens.kt`).

### 5.3 Fonctionnalités différenciantes

1. **Scène sonore réactive** sans micro ni réseau (N1 + P5).
2. **Flow** : enchaînement DJ par BPM/tonalité et course au tempo, à partir d'une analyse 100 % locale (N3–N5).
3. **Jukebox et télécommande sur le Wi-Fi** sans compte ni cloud (N2 + N7).
4. **Playlists en langage naturel** avec un analyseur local et, quand c'est possible, un modèle on-device (N6).
5. **Mémoire musicale** : l'app se souvient avec vous (N8).
6. **Moteur audio de niveau audiophile** avec badge bit-perfect, R128, EQ AutoEq (P1–P4).
7. **Vie privée démontrable** : visualiseur sans micro, secrets chiffrés, sauvegarde chiffrée, intégrité des binaires, journal local (I2, I3, I12, P5, P9).

### 5.4 Architecture cible

```
synxio/
├── core/
│   ├── model/        Song, Album, Playlist, règles… (Kotlin pur, KMP-ready)
│   ├── engine/       DailyMixEngine, RulePlaylistEngine, RecapCalculator,
│   │                 AudioFeatureAnalyzer, FlowSequencer, NaturalLanguageRules
│   ├── audio/        AudioPipeline : décodage, EQ DSP, R128, crossfade, tap FFT
│   ├── data/         Room (songs persistants), DataStore, DAO, Workers
│   └── design/       Tokens, Lumen, Motion, shaders (+ export CSS)
├── feature/
│   ├── player/       NowPlaying, MiniPlayer, Sheets, Flow
│   ├── library/      Musique, Dossiers, Podcasts, Livres, Playlists, Boîte de réception
│   ├── discover/     Accueil, Mixs, Ambiances, Mémoire, Récap, Insights
│   ├── workshop/     Tags, Réparation, Doublons, Découpe, Sauvegarde
│   ├── social/       Party, Télécommande web, Last.fm, ListenBrainz, Discord
│   ├── devices/      Sortie audio, Buds, Auto, Cast, Wear
│   └── settings/
├── app/              Navigation, DI, service de lecture (orchestrateur mince)
└── web/              SPA de la télécommande et du jukebox (embarquée en assets)
```

Principes :
- **`PlaybackService` devient un orchestrateur** de 300 lignes qui compose des collaborateurs (`AudioPipeline`, `QueueManager`, `CastBridge`, `SocialBroadcaster`, `DeviceWatcher`).
- **Un `PositionClock`** unique, consommé seulement par ce qui bouge.
- **Un `LibrarySource`** abstrait (MediaStore aujourd'hui, Subsonic demain) alimentant Room ; l'UI ne connaît que Room.
- **WorkManager** pour tout ce qui dépasse une seconde et doit survivre.
- **`core/` compile sans Android** : c'est ce qui rend possibles la télécommande web (partage des règles et formats) et, plus tard, un client desktop Compose Multiplatform.
- **Tests** : moteurs (déjà), ViewModels (Turbine), DAO (in-memory), 5 parcours UI, 1 test d'intégration audio (fichier de référence → PCM attendu).

### 5.5 Intégrations possibles

| Intégration | Ce qu'elle apporte | Esprit Synxio |
|---|---|---|
| ListenBrainz | Scrobbling ouvert, sans secret | Oui |
| Subsonic / Navidrome / Jellyfin | Bibliothèque sur NAS, auto-hébergée | Oui |
| Podcasts RSS + OPML | Podcasts sans plateforme | Oui |
| AutoEq (oratory1990) | Corrections casques mesurées | Oui |
| AcoustID / MusicBrainz | Identification acoustique | Oui |
| Tasker / App Actions | Automatisation par intents publics | Oui |
| Quick Share / QR | Partage de playlists sans cloud | Oui |
| Wear OS | Commandes au poignet | Oui |
| Health Connect (lecture cadence) | Mode course | Optionnel, consentement explicite |
| Gemini Nano (ML Kit GenAI) | Langage naturel on-device | Seulement si 100 % local |

À éviter : tout service exigeant un compte Synxio, tout SDK analytics, toute dépendance à un serveur Synxio hébergé. L'écosystème se construit **de téléphone à navigateur à ordinateur sur le réseau local**, pas via le cloud.

### 5.6 Mobile et bureau

- **Téléphone** : l'app complète, avec Lumen Vivant.
- **Tablette et pliable** : mêmes fonctions, rail + liste-détail, lecteur à deux colonnes, paroles grand format.
- **Navigateur d'ordinateur (court terme, N7)** : télécommande PWA servie par le téléphone, mêmes tokens Lumen, recherche, file, paroles, EQ, et « Écouter ici » pour diffuser vers le PC.
- **Application bureau (long terme, X6)** : Compose Multiplatform réutilisant `core/`, lecture locale sur l'ordinateur, synchronisation de l'historique et des playlists avec le téléphone par le réseau local.
- **Voiture** : Android Auto enrichi (Flow, Mémoire) et mode voiture 2 sur l'écran du téléphone.
- **Montre** : X4.

---

## 6. Proposition de séquencement

Ceci n'est pas un engagement, seulement un ordre qui respecte les dépendances.

| Lot | Contenu | Pourquoi dans cet ordre |
|---|---|---|
| **5.6.1** | I1 rotation de clé, I2 secrets, I3 yt-dlp, I12 journal | Sécurité d'abord ; petit, rapide |
| **5.7** | I6 position, I10 gestes du lecteur, P7 haptique, N8 mémoire, N11 veille, N13 intents, P15 erreurs | Gains visibles immédiats, zéro refonte |
| **5.8** | I4 bibliothèque persistante, I5 WorkManager, I13 démarrage, P10 boîte de réception | Fondation données |
| **5.9** | I7 découpage, I14 réglages, I11 tests, P5 FFT sans micro | Fondation code ; supprime `RECORD_AUDIO` |
| **6.0 « Lumen Vivant »** | N1 scène réactive, I9 adaptatif, P6 paroles, P12 widgets, I8 i18n (FR/EN) | La release identitaire |
| **6.1 « Studio »** | P4 EQ DSP, P3 R128, P1 FFmpeg/float/offload, P2 crossfade | Le moteur audio |
| **6.2 « Flow »** | N3 BPM/tonalité, N4 transitions, N5 course, N6 langage naturel, P14 insights | Découverte |
| **6.3 « Ensemble »** | N2 jukebox, N7 télécommande web, N10 partage, N14 ListenBrainz | Écosystème local |
| **6.4+** | P8 podcasts RSS, N12 Subsonic, P9 sauvegarde 2, P11 tags, N9 AcoustID, P13 voiture, P16 a11y | Extension |
| **Labo** | X1–X7 derrière un drapeau | Sans promesse |

---

## 7. Ce que je vous demande de valider

1. Les **5 évolutions prioritaires** (§4) : lesquelles retenir, et dans quel ordre.
2. Le **lot 5.6.1 sécurité** : peut-il partir avant toute autre chose ?
3. Les propositions des §2.1 à §2.4 à écarter d'emblée (par exemple : pas de Subsonic, pas de podcasts RSS, pas d'anglais).
4. L'orientation de la vision §5 : Lumen Vivant et écosystème local, ou une autre direction.

À partir de vos choix, chaque lot retenu fera l'objet d'une spécification de conception détaillée (format `docs/superpowers/specs/`) avant tout code.
