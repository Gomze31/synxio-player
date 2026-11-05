# Lumen Vivant 5.8.0 — scène réactive, verre et canvas, mémoire musicale, veille adaptative

*Version majeure 5.8.0. Reprend, en l'avançant, la release identitaire « 6.0 Lumen Vivant » du séquencement de `docs/AUDIT_EVOLUTION_2026-11.md` (§6) : propositions P5, N1 (cœur), N8 et N11. Base : branche `dev-5.5` après la 5.7.1 (commit `bb8e35c`). Date : 5 novembre 2026. Validé par l'utilisateur dans la conversation, section par section.*

## Objectif

Faire écouter la lumière. La scène Lumen cesse d'être une dérive lente : l'aurore respire sur la musique, le verre a une épaisseur, chaque pochette a un canvas animé. En parallèle, l'app se souvient avec l'utilisateur (cartes « Souvenirs » sur l'accueil) et la minuterie de veille devient adaptative. Le tout sans micro, sans réseau, sans refonte de l'architecture, avec un budget batterie explicite.

## Constat vérifié dans le code

- Le visualiseur actuel repose sur `android.media.audiofx.Visualizer` (`playback/EqualizerController.kt:44-127`), ce qui impose la permission `RECORD_AUDIO` (`AndroidManifest.xml:31`). Il publie `fftFlow: StateFlow<ByteArray>`, consommé par `NowPlayingScreen.kt:239`, `EqualizerScreen.kt:132` et le composable `AudioVisualizer` (`ui/components/AudioVisualizer.kt`).
- La chaîne audio d'ExoPlayer est déjà personnalisée : `PlaybackService.kt:244-254` construit un `DefaultAudioSink` avec `VocalRemoverAudioProcessor`. Ni offload ni tunneling ne sont activés : un processeur supplémentaire voit tout le PCM, webradios et podcasts compris.
- L'aurore est `AmbientBackdrop` (`ui/theme/Glass.kt:135`) : trois halos en `Brush.radialGradient` dessinés en Canvas, couleurs de la pochette via `LocalArtworkColors`, dérive animée quand `drifting` est vrai. Une seule instance vit dans `SynxioRoot.kt:265` avec `drifting = isPlaying` ; l'onboarding en a une autre. Elle respecte `LocalReduceMotion` et `LocalGlowEffects` (réglages « Réduire les animations » et « Effets lumineux », `SettingsRepository.kt:177,179`).
- Le verre est `Modifier.glass(shape, strength, border, tint)` (`Glass.kt:66`), un voile translucide avec reflet spéculaire et liseré. Il ne voit pas ce qu'il y a derrière lui : Compose n'offre pas de filtre de fond.
- Le lecteur plein écran expose `expansion` (Animatable 0..1, depuis la 5.7.1) dans `SynxioRoot`, et `ArtworkStage` (`NowPlayingScreen.kt:826`) choisit le rendu de la pochette selon `NowPlayingSkin` (`VINYL` et les autres skins).
- `SleepTimer` (`playback/SleepTimer.kt:54-74`) compte en cumulant des `delay(1_000)` : l'horloge dérive et se perd en Doze. Il expose `fadeMultiplier` pour le fondu de fin.
- `ShakeDetector` (`core/util/ShakeDetector.kt`) est déjà enregistré par `PlaybackService.kt:301` pour la secousse.
- `play_history` (`data/db/Entities.kt:127-138`) stocke `songId`, `songPath`, `playedAt` (secondes), `listenedMs`, `durationMs`, indexé sur `playedAt` et `songId`. `DailyMixEngine` (pur) et `DailyMixRepository` donnent le modèle d'un calcul quotidien exposé à `HomeScreen` (`HomeScreen.kt:305-317`).
- `compileSdk 37`, `minSdk 28`. `RuntimeShader` (AGSL) existe depuis l'API 33.

## 1. Capture et analyse audio (P5)

### 1.1 `AudioTapProcessor`

Nouveau `AudioProcessor` Media3 dans `playback/effects/AudioTapProcessor.kt`, placé après `vocalRemover` dans `setAudioProcessors`. Il laisse passer le tampon d'entrée tel quel (sortie = entrée, aucune copie du flux audio) et recopie, pour chaque tampon traité, les échantillons mixés en mono dans un tampon circulaire de 2048 trames (`FloatArray`, pré-alloué, écriture sans verrou ni allocation). Formats acceptés : PCM 16 bits et float ; tout autre format est déclaré non pris en charge et le processeur devient inactif (le son continue, sans pouls).

La fréquence d'échantillonnage courante est mémorisée pour que l'analyseur convertisse ses bandes en hertz.

### 1.2 `AudioAnalyser`

Kotlin pur, sans dépendance Android, dans `playback/pulse/AudioAnalyser.kt` :

```kotlin
data class AudioPulse(
    val bass: Float = 0f,     // 20–150 Hz
    val mid: Float = 0f,      // 150–2 000 Hz
    val treble: Float = 0f,   // 2 000–16 000 Hz
    val rms: Float = 0f,      // niveau global
    val onset: Float = 0f,    // 1 juste après une attaque, retombe en ~300 ms
    val bands: FloatArray = FloatArray(32),  // spectre lissé, échelle logarithmique
) { companion object { val SILENT = AudioPulse() } }
```

Toutes les valeurs sont dans 0..1. Algorithme : fenêtre de Hann sur 1024 trames, FFT réelle (implémentation maison, radix-2), magnitude en dB bornée sur 60 dB puis normalisée, regroupement en 32 bandes logarithmiques, puis lissage asymétrique par bande (attaque 50 ms, relâchement 400 ms). `onset` vaut la différence positive d'énergie entre deux trames, relâchée sur 300 ms. Le silence (`rms < 0.02`) est signalé tel quel ; c'est la scène qui décide de figer.

### 1.3 `PulseSource`

Singleton Hilt dans `playback/pulse/PulseSource.kt`, injecté dans `PlaybackService` et dans `PlayerConnection`. Il possède le tampon du tap, et expose :

```kotlin
val pulse: StateFlow<AudioPulse>
```

construit par `flow { while (true) { emit(analyse()); delay(33) } }.stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 500), AudioPulse.SILENT)`. Sans abonné, rien ne tourne : ni FFT, ni réveil. `PlayerConnection.pulse` le réexpose, sur le modèle de `position` (5.7.1). En pause (`isPlaying` faux), la source émet `SILENT` sans calculer.

### 1.4 Suppression de l'ancien visualiseur

- `EqualizerController` perd `Visualizer`, `fftFlow`, `setVisualizerEnabled` et l'`audioSessionId` qui ne servait qu'à lui.
- `AppViewModel.fftFlow` et `EqualizerViewModel.fftFlow` deviennent `pulse: StateFlow<AudioPulse>`.
- `AudioVisualizer(fft: ByteArray, …)` devient `AudioVisualizer(pulse: AudioPulse, …)` et dessine `bands` ; même apparence (barres, dégradé signature). Le réglage `showVisualizer` reste.
- `RECORD_AUDIO` est retirée du manifeste, ainsi que toute demande de permission associée.

### 1.5 Tests

`AudioAnalyserTest` : sinus 60 Hz → `bass` > 0,7 et `treble` < 0,1 ; sinus 8 kHz → l'inverse ; bruit blanc → bandes toutes > 0,2 ; silence → `SILENT` ; attaque brusque → `onset` proche de 1 puis < 0,1 après 400 ms. `RingBufferTest` : écriture circulaire, lecture des 1024 dernières trames, mixage stéréo → mono. `PulseGateTest` (logique pure extraite) : pas de calcul sans abonné ni en pause.

## 2. Scène réactive (N1, cœur)

### 2.1 `SceneMapper`

Fonction pure dans `ui/theme/SceneMapper.kt` :

```kotlin
data class SceneParams(
    val haloScale: FloatArray,   // 3 facteurs, 1.0 au repos, ≤ 1.25
    val brightness: Float,       // 0.85..1.15
    val shimmer: Float,          // 0..1, scintillement du troisième halo
    val flash: Float,            // 0..1, éclat d'attaque
    val frozen: Boolean,         // dérive figée
)
fun mapScene(pulse: AudioPulse, silentForMs: Long, previous: SceneParams): SceneParams
```

Règles : `haloScale[i] = 1 + 0.25 * bass` (halo 1), `1 + 0.15 * mid` (halo 2), `1 + 0.10 * treble` (halo 3) ; `brightness = 0.85 + 0.30 * rms` ; `shimmer = treble` ; `flash = onset` ; `frozen = silentForMs ≥ 1 000`. Chaque sortie est lissée vers la précédente (facteur 0,5 par image) pour ne jamais sauter.

### 2.2 `AmbientBackdrop`

Nouveau paramètre `pulse: State<AudioPulse>?` (nul = comportement actuel). Les paramètres de scène sont lus dans le lambda de dessin (`drawWithCache` / `Canvas`), jamais dans la composition : un tick du pouls ne recompose rien. Les trois halos appliquent `haloScale` à leur rayon, `brightness` à leur alpha, `shimmer` à une variation d'alpha rapide du troisième halo, `flash` à une surcouche blanche très faible, et `frozen` arrête la dérive.

`SynxioRoot` passe le pouls quand `expansion > 0` ou que le mini-lecteur est visible (`miniPlayerHidden` faux et un titre chargé, `SynxioRoot.kt:290`), et `null` sinon. Comme l'abonnement au flux est conditionné de la même façon, l'analyse ne tourne que si une de ces surfaces est à l'écran.

### 2.3 Signature qui pulse

- `BrandProgressBar` (`ui/components/Lumen.kt:553`) : la lueur du dégradé braise → magenta suit `rms` (±10 % d'alpha).
- Ligne de paroles chantée (`LyricRow` avec `active = true`, `Sheets.kt:324`, et son équivalent dans `LyricsStage`, `NowPlayingScreen.kt:1207`) : même règle sur la luminosité.
- Mini-lecteur : la teinte du verre (`tint`) gagne une lueur dont l'alpha suit `bass`, bornée à +8 %.

### 2.4 Budget et coupures

La scène est inactive (pouls `null`, abonnement coupé) si l'une des conditions suit : « Réduire les animations », « Effets lumineux » désactivé, nouveau réglage « Scène réactive » désactivé, `PowerManager.isPowerSaveMode`, batterie ≤ 15 % (`BatteryManager`, lue à l'abonnement et sur `ACTION_BATTERY_LOW` / `ACTION_BATTERY_OKAY`), écran éteint (`Lifecycle` de l'activité hors `STARTED`), lecture en pause. Ces règles vivent dans une fonction pure `sceneAllowed(...)` testée.

Cadence : l'analyse émet à 30 Hz ; le dessin suit l'invalidation du Canvas sur chaque émission, donc 30 images par seconde au plus, quel que soit le taux de rafraîchissement de l'écran.

### 2.5 Tests

`SceneMapperTest` : bornes, continuité (pas de saut > 0,2 par image), silence → `frozen` après 1 s. `SceneGateTest` : table de vérité des coupures.

## 3. Verre AGSL et canvas génératif (N1, suite)

### 3.1 Principe

Le verre ne peut pas échantillonner le fond. Mais l'aurore est procédurale : ses trois centres, ses trois couleurs et ses paramètres de scène suffisent à la redessiner. Le verre dessine donc sous son voile une copie déformée de l'aurore, calculée à partir des mêmes uniformes. Les trois shaders partagent un objet `SceneUniforms` (centres, couleurs, `SceneParams`, temps) produit par le même `SceneMapper`.

### 3.2 `AuroraShader` (API 33+)

`RuntimeShader` AGSL dans `ui/theme/shaders/Aurora.agsl` (ressource `raw`), utilisé par `AmbientBackdrop` via `ShaderBrush` quand `Build.VERSION.SDK_INT ≥ 33` et que les shaders ne sont pas marqués indisponibles. Rendu : trois halos gaussiens, grain fin (bruit de valeur, amplitude 1,5 %), dispersion chromatique légère sur les bords (décalage des canaux de 1 px). Sous API 33 : le Canvas actuel, inchangé, avec les mêmes `SceneParams`.

### 3.3 `GlassShader`

`Modifier.glass()` gagne `refract: Boolean = false`. Avec `refract` et API 33+, la surface dessine sous son voile une aurore ondulée (déformation sinusoïdale de 2 px d'amplitude, lente) avec un décalage RVB de 1 à 2 px, avant le voile et le reflet actuels. Seules deux surfaces l'activent : la feuille du lecteur plein écran et le mini-lecteur. Toutes les autres utilisations de `glass()` restent telles quelles.

### 3.4 `ArtworkCanvas`

Composable derrière la pochette dans `ArtworkStage`, pour les skins autres que `VINYL` et le skin minimal (le vinyle garde son disque ; le minimal n'a pas de canvas). Boucle de 8 s : trois nappes qui ondulent, couleurs `vibrant`, `muted` et `dominant` de la palette, vitesse multipliée par `0.5 + rms`, contraste par `0.6 + 0.4 * bass`. Aucune analyse préalable ni stockage : le canvas suit le son en direct. AGSL sur 33+ (`Canvas.agsl`), repli en dégradés linéaires animés par `Brush` en dessous. Nouveau réglage « Canvas animé » sous Effets lumineux, actif par défaut, soumis aux mêmes coupures que la scène.

### 3.5 Garde-fous

`ShaderSupport` (objet dans `ui/theme/shaders/`) compile chaque shader au premier usage dans un `try`. En cas d'exception, il bascule la surface sur le repli Canvas et écrit `shadersUnavailable = true` dans `SettingsRepository` ; au lancement suivant, les shaders ne sont plus essayés. Un bouton « Réessayer les shaders » dans Réglages → Effets lumineux remet le drapeau à faux.

Cadence : les shaders sont redessinés sur chaque émission du pouls (30 Hz) ; hors lecture, l'aurore AGSL ne se redessine que sur sa dérive (comme aujourd'hui).

### 3.6 Tests

JVM seulement : `SceneUniformsTest` (bornes, continuité, dérivés des couleurs), `ShaderSupportTest` (choix du chemin selon version et drapeau, écriture du drapeau sur échec simulé). Vérification visuelle sur le Honor (Android 17) consignée dans la checklist d'appareil des notes de version : aurore, verre du lecteur, verre du mini-lecteur, canvas sur chaque skin, repli forcé par le drapeau.

## 4. Mémoire musicale (N8)

### 4.1 `MemoryEngine`

Kotlin pur dans `data/model/MemoryEngine.kt`, nourri par les lignes de `play_history`, la liste des titres et des albums, la date du jour et un fuseau. Sortie :

```kotlin
sealed interface Memory {
    val key: String          // stable dans la journée, sert de clé LazyRow
    val title: String        // « Ce jour-là, il y a 1 an », « Capsule : mars 2025 »…
    val subtitle: String     // « 2025 », « 4 titres sur 12 »…
    val songs: List<Song>    // ce que lance la carte
    data class ThatDay(val yearsAgo: Int, override val songs: List<Song>) : Memory
    data class TimeCapsule(val month: java.time.YearMonth, override val songs: List<Song>) : Memory
    data class FirstTimes(override val songs: List<Song>) : Memory
    data class UnfinishedAlbum(val album: Album, val playedCount: Int, override val songs: List<Song>) : Memory
}
```

`key`, `title` et `subtitle` sont dérivés dans chaque classe (`"thatday-1"`, `"capsule-2025-03"`, `"firsttimes"`, `"album-<id>"`). `UnfinishedAlbum.songs` contient les titres non joués de l'album.

Règles :
- **Ce jour-là** : titres joués le même jour calendaire il y a 1 an, puis il y a 2 ans (deux cartes possibles). Au moins 3 titres, sinon la carte est absente. Ordre : nombre de lectures ce jour-là, puis heure.
- **Capsule temporelle** : parmi les mois entre 6 et 24 mois en arrière, les trois les plus écoutés (somme de `listenedMs`) ; l'un d'eux est choisi par graine hebdomadaire (numéro de semaine ISO). Ses 25 titres les plus joués. Absente s'il n'y a pas de mois avec au moins 10 lectures.
- **Premières fois** : titres dont la première lecture de tout l'historique date des 30 derniers jours, triés par nombre de relectures décroissant, 20 au plus, au moins 3.
- **Albums jamais terminés** : albums d'au moins 5 titres, dont au moins un titre a été joué, moins de 50 % des titres joués, dernière écoute il y a plus de 30 jours ; 5 au plus, les plus anciens d'abord. Chaque album est une carte « Finir cet album » qui lance les titres non joués.

### 4.2 `MemoryRepository` et accueil

`MemoryRepository` (`data/repo/`) recalcule sur `Dispatchers.Default` au premier abonnement de la journée et à minuit, et expose `memories: StateFlow<List<Memory>>`. `AppViewModel.memories` le relaie. `HomeScreen` affiche la section « Souvenirs » entre « Tes mixs du jour » et « Pour toi » (`HomeSection` existante, cartes horizontales). Chaque carte : titre, sous-titre (année, mois, ou « 4 titres sur 12 »), mosaïque de 4 pochettes, toucher = lecture, appui long = « Enregistrer en playlist ». Section absente si la liste est vide.

### 4.3 Tests

`MemoryEngineTest` sur des historiques fixés et une date injectée : jour exact l'an dernier avec et sans les 3 titres, choix de capsule stable dans la semaine et changeant la semaine suivante, premières fois excluant un titre joué il y a 2 mois, albums terminés exclus, albums de 4 titres exclus, écoute récente exclue.

## 5. Veille adaptative (N11)

### 5.1 Horloge du `SleepTimer`

`start()` calcule une échéance `deadline = clock.elapsedRealtime() + durationMs` et chaque tick recalcule `remaining = deadline - clock.elapsedRealtime()`. L'horloge est injectée (`() -> Long`, `SystemClock::elapsedRealtime` en production). Le reste du comportement (fin de morceau, fin de chapitre, prolongation) est inchangé.

### 5.2 Mode adaptatif

`SleepTimerState` gagne `adaptive: Boolean`. `SleepTimerSheet` ajoute la puce « Adaptative » à côté des durées : la durée choisie reste l'échéance maximale, et trois règles s'ajoutent :
- **Fondu long** : sur les 20 dernières minutes, `fadeMultiplier` descend linéairement de 1 à 0,15, puis le fondu de fin existant termine.
- **Pause sur immobilité** : si le téléphone est immobile depuis N minutes et que l'écran est éteint, la lecture se met en pause et la minuterie reste active 30 s de plus.
- **Secousse** : une secousse dans ces 30 s relance la lecture et ajoute 15 min ; une secousse à tout autre moment de la veille ajoute 15 min. La secousse réutilise le `ShakeDetector` du service.

N vaut 5, 10 ou 20 minutes, réglage « Immobilité avant pause » sous Minuterie, 10 par défaut.

### 5.3 `StillnessDetector`

Classe pure dans `core/util/StillnessDetector.kt` : reçoit des échantillons d'accélération (x, y, z, temps) et répond `stillSince(now): Long?` à partir de la variance glissante sur 10 s (seuil 0,05 m/s²). `PlaybackService` l'alimente avec le même capteur que la secousse, enregistré à `SENSOR_DELAY_NORMAL` uniquement pendant une veille adaptative, et désenregistré à la fin. L'état de l'écran vient de `PowerManager.isInteractive`.

### 5.4 Tests

`SleepTimerTest` avec horloge injectée : échéance exacte malgré des ticks en retard, prolongation, annulation. `AdaptiveFadeTest` : courbe sur 20 min. `StillnessDetectorTest` : immobile → `stillSince` non nul après 10 s, mouvement → remise à zéro. Logique de pause/relance extraite en fonction pure et testée.

## 6. Réglages, manifeste, release

- Réglages → Effets lumineux : « Scène réactive » (défaut : actif), « Canvas animé » (défaut : actif), « Réessayer les shaders » (visible seulement si le drapeau est levé). Réglages → Minuterie : « Immobilité avant pause » (5 / 10 / 20 min).
- `AndroidManifest.xml` : `RECORD_AUDIO` retirée. Les notes de version le disent.
- `versionCode 20261120`, `versionName "5.8.0"`, `docs/release-notes/v5.8.0.md` avec la checklist d'appareil (section 3.6, gestes du lecteur, veille adaptative).
- Aucune nouvelle donnée à sauvegarder : les souvenirs sont recalculés, les réglages passent par `SettingsRepository` comme les autres.

## Hors périmètre

- Mise en page adaptative tablette (I9), widgets étendus (P12), i18n (I8) : restent en 6.0 ou plus tard.
- Pulsation haptique au tempo : dépend de l'analyse BPM (N3, lot Flow).
- Canvas dérivé d'une analyse hors ligne : le canvas suit le son en direct, rien n'est précalculé.
- Shaders sur les verres des listes et des cartes : deux surfaces seulement.
- Export vidéo du récap, séparation de pistes, et tout ce qui est en §2.4 de l'audit.

## Dépendances et contraintes

- Media3 1.11.1, Compose BOM 2026.09, `compileSdk 37`, `minSdk 28`. AGSL sur API 33+ seulement, repli obligatoire en dessous.
- Pas d'offload ni de tunneling audio tant que le tap existe : les activer couperait le pouls. À noter dans `PlaybackService`.
- Tests JVM uniquement (JUnit 4) ; aucun test instrumenté dans le projet, les shaders se vérifient à la main.
- L'historique n'existe que depuis l'installation de l'app : les cartes « Ce jour-là » et « Capsule » n'apparaissent qu'avec le temps, par construction.

## Ordre de réalisation

Trois plans d'exécution, dans cet ordre, une seule release :

1. **Socle et scène** (sections 1, 2, 6 pour la part « Scène réactive ») : tap, analyseur, suppression du micro, aurore réactive, signature qui pulse.
2. **Verre et canvas** (section 3) : shaders, repli, drapeau, réglage « Canvas animé ».
3. **Mémoire et veille** (sections 4, 5, reste de 6) : moteur et cartes Souvenirs, horloge et mode adaptatif, notes de version et version.
