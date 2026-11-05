# En route 5.9.0 — Android Auto vivant, voix, mode voiture 2

*Version 5.9.0, dédiée à la voiture. Reprend la proposition P13 de `docs/AUDIT_EVOLUTION_2026-11.md` et l'orientation « Voiture : Android Auto enrichi (Flow, Mémoire) et mode voiture 2 » de sa vision (§5.5). Base : branche `dev-5.5` après la 5.8.0 (spec `2026-11-05-lumen-vivant-5.8.0-design.md`). Date : 5 novembre 2026. Validé par l'utilisateur dans la conversation, section par section.*

## Objectif

Faire de la voiture un premier écran, pas un sous-ensemble. Dans Android Auto : un accueil vivant (Mixs du jour, Souvenirs, reprise), des tuiles jouables d'un toucher, des actions sans quitter la liste, la progression des livres et podcasts, des erreurs qui se résolvent, et une voix qui comprend des phrases entières. Sur le téléphone, pour les voitures sans Android Auto : détection de la voiture, lancement automatique et un mode voiture complet.

## Constat vérifié dans le code

- L'arbre Android Auto est `MediaLibraryTree` (`playback/MediaLibraryTree.kt`, mappage vers `MediaItem`) au-dessus de `BrowseTree` (`playback/BrowseTree.kt`, modèle pur `BrowseNode` / `NodeKind` / `NodeArt` / `ChildStyle`). Quatre onglets (`TAB_HOME` « Pour toi », `TAB_LIBRARY`, `TAB_PLAYLISTS`, `TAB_RADIOS`). Les styles liste/grille et les titres de groupe sont déjà transmis par `MediaConstants.EXTRAS_KEY_CONTENT_STYLE_*` et `EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE` (`MediaLibraryTree.kt:179-181`). La recherche est floue et sans accents (`fuzzyScore`, `normalizeForSearch`), avec des résultats groupés (`BrowseTree.kt:257-267`).
- `home()` (`BrowseTree.kt:307-322`) liste : Continuer l'écoute, Lecture aléatoire, Favoris, Écoutés récemment, Les plus écoutés, Ajoutés récemment. Ni mixs du jour ni souvenirs. `BrowseSource` (`BrowseTree.kt:122`) ne voit pas `DailyMixRepository.mixes`.
- `NodeKind(browsable, playable)` ne combine pas les deux pour les albums et playlists : il faut entrer dans l'élément pour le lire.
- `resolveVoice` (`BrowseTree.kt:278`) traite le focus Assistant (`MediaStore.EXTRA_MEDIA_*`), les mots « favoris » et « aléatoire », puis artiste, album, playlist, genre, titre, puis la recherche floue. Pas de période, pas de mix, pas de livre, pas de radio par la voix, pas de requête composée.
- Aucune des clés suivantes n'est utilisée : complétion (`EXTRAS_KEY_COMPLETION_STATUS` / `_PERCENTAGE`), résolution d'erreur (`EXTRAS_KEY_ERROR_RESOLUTION_ACTION_*_COMPAT`), taille des pochettes (`EXTRAS_KEY_MEDIA_ART_SIZE_PIXELS`), réservation d'emplacements (`EXTRAS_KEY_SLOT_RESERVATION_*`). Media3 1.11.1 expose ces clés dans `androidx.media3.session.MediaConstants`, mais pas les actions personnalisées de navigation, qui n'existent que comme chaînes dans `androidx.media.utils.MediaConstants` (bibliothèque non dépendue).
- `LibrarySessionCallback.onConnect` (`PlaybackService.kt:1463`) réserve les commandes maison aux contrôleurs de confiance, Android Auto inclus. `onCustomCommand` existe (`PlaybackService.kt:1504`).
- `DriveModeScreen` (`ui/screens/DriveModeScreen.kt`, 372 lignes) : pochette, titre, précédent / lecture / suivant, barre de progression, gestes de balayage, bouton Quitter. Ni file, ni cœur, ni micro, ni gestion de la luminosité.
- `BudsMonitor` (`buds/BudsMonitor.kt`) écoute déjà les connexions Bluetooth avec `BLUETOOTH_CONNECT` (déclarée, demandée depuis la carte Galaxy Buds). Aucune détection de voiture.
- `Song.year` et `Song.genre` existent (`data/model/Models.kt:26,30`).

## 1. L'arbre de navigation

### 1.1 Accueil « Pour toi » par groupes

`home()` produit des nœuds avec `groupTitle`, dans cet ordre :

| Groupe | Éléments | Style |
|---|---|---|
| Reprendre | Continuer l'écoute (livre ou podcast entamé) ; Dernière file (la file sauvegardée, si elle existe) | liste |
| Pour toi | Mixs du jour (`DailyMixRepository.mixes`) ; Souvenirs (`MemoryRepository.memories`, 5.8.0) | grille, mosaïque 2×2 |
| Raccourcis | Lecture aléatoire ; Favoris ; Surprends-moi | liste |
| Récents | Écoutés récemment ; Ajoutés récemment ; Les plus écoutés | liste |

« Surprends-moi » : 50 titres, 60 % de favoris et 40 % de titres joués au plus une fois, graine = numéro du jour, mélangés avec la règle « deux titres max par artiste » de `DailyMixEngine`. Un groupe vide est omis.

`BrowseSource` gagne `val dailyMixes: List<DailyMix>` et `val memories: List<Memory>` (valeurs par défaut vides). Nouveaux identifiants dans `BrowseIds` : `MIX_PREFIX = "synxio://mix/"`, `MEMORY_PREFIX = "synxio://memory/"`, `SURPRISE = "synxio://surprise"`, `LAST_QUEUE = "synxio://last-queue"`.

### 1.2 Tuiles jouables

`NodeKind` gagne `FOLDER_PLAYABLE(browsable = true, playable = true)`, utilisé pour albums, playlists, genres, mixs et souvenirs. `MediaLibraryTree` le mappe sur `isBrowsable = true` et `isPlayable = true` : Android Auto affiche le bouton lecture sur la tuile et un toucher sur le titre ouvre le contenu. `onSetMediaItems` sur un tel identifiant lit le contenu entier dans l'ordre (ou mélangé pour un genre).

### 1.3 Pochettes

`onGetLibraryRoot` lit `EXTRAS_KEY_MEDIA_ART_SIZE_PIXELS` dans les indices de racine du navigateur et le mémorise par contrôleur. Les URI de pochette produites par `MediaLibraryTree` portent ce paramètre (`?size=`) et le fournisseur d'images existant sert cette taille, carrée, au lieu de la pleine résolution. Mixs, souvenirs et playlists reçoivent une mosaïque 2×2 des quatre premières pochettes, produite par un nouveau `MosaicRenderer` (`playback/art/`) qui réutilise le décodage et le recadrage de `WidgetArtRenderer` (`widget/WidgetArtRenderer.kt:120-145`), servie par le même fournisseur d'images et mise en cache par clé et taille.

### 1.4 Actions sur les éléments

Objet `AutoKeys` dans `playback/AutoKeys.kt`, qui fige les chaînes de `androidx.media.utils.MediaConstants` utiles :

```kotlin
object AutoKeys {
    // Valeurs relevées dans androidx.media:media 1.7.0 (dépendance transitive de media3-session).
    const val ROOT_HINT_ACTION_LIMIT = "androidx.media.utils.MediaBrowserCompat.extras.CUSTOM_BROWSER_ACTION_LIMIT"
    const val ROOT_EXTRAS_ACTION_LIST = "androidx.media.utils.extras.CUSTOM_BROWSER_ACTION_ROOT_LIST"
    const val ITEM_ACTION_ID_LIST = "androidx.media.utils.extras.CUSTOM_BROWSER_ACTION_ID_LIST"
    const val ACTION_ID = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_ID"
    const val ACTION_LABEL = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_LABEL"
    const val ACTION_ICON_URI = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_ICON_URI"
    const val ACTION_EXTRAS = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_EXTRAS"
    const val ACTION_MEDIA_ITEM_ID = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_MEDIA_ITEM_ID"
    const val RESULT_MESSAGE = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE"
    const val RESULT_REFRESH_ITEM = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM"
}
```

Quatre actions déclarées dans les extras de racine (dans la limite annoncée par le navigateur) : `favorite` (« Favori » / « Retirer des favoris », icône cœur selon l'état), `play_next` (« Lire ensuite »), `add_to_queue` (« Ajouter à la file »), `shuffle` (« Aléatoire »). Chaque nœud porte la liste des actions qui s'appliquent : titres (les quatre), albums, playlists, mixs et souvenirs (`play_next`, `add_to_queue`, `shuffle`), radios (aucune).

L'appel arrive dans `onCustomCommand` avec l'identifiant de l'action et `ACTION_MEDIA_ITEM_ID`. Réponse : `RESULT_MESSAGE` court (« Ajouté aux favoris ») et `RESULT_REFRESH_ITEM` pour rafraîchir l'icône cœur. La logique (identifiant → titres concernés → opération sur la file ou les favoris) est une fonction pure testée dans `BrowseTree`.

**Risque identifié.** Le relais de `MediaBrowserCompat.sendCustomAction` vers `MediaLibrarySession.Callback.onCustomCommand` par Media3 1.11.1 doit être confirmé sur le Desktop Head Unit dès la première tâche du plan. S'il manque, le bloc 1.4 est retiré du lot et consigné en dette ; le reste de la section ne dépend pas de lui.

### 1.5 Progression des livres et podcasts

Les nœuds de livre, de chapitre et d'épisode portent `EXTRAS_KEY_COMPLETION_STATUS` (`EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED`, `_PARTIALLY_PLAYED`, `_FULLY_PLAYED`) et `EXTRAS_KEY_COMPLETION_PERCENTAGE` (0..1), calculés par `BrowseTree` depuis `savedPositions` et l'état « marqué comme lu » de la 5.6.0. Android Auto affiche la barre de progression et la coche.

### 1.6 Erreurs résolubles et emplacements

- Permission de lecture absente, ou bibliothèque jamais analysée : `PlaybackService` pose dans les extras de session `EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT` = « Ouvrir Synxio sur le téléphone » et `EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT` = `PendingIntent` vers `MainActivity` avec la route de l'onboarding ou de la bibliothèque. Les extras sont retirés dès que `hasPermission && hasScanned`.
- `EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV` et `_NEXT` sont toujours posés : les boutons précédent/suivant ne disparaissent pas en radio, ils sont simplement inactifs.

### 1.7 Tests

`BrowseTreeTest` étendu : composition de l'accueil (groupes, omissions, « Surprends-moi » stable dans la journée), `FOLDER_PLAYABLE` sur les bons types, statuts et pourcentages de complétion (non commencé, entamé, terminé, marqué lu), liste d'actions par type de nœud, exécution des quatre actions sur une file simulée. `AutoKeysTest` fige les chaînes.

## 2. Voix et recherche

### 2.1 `VoiceInterpreter`

Kotlin pur dans `playback/VoiceInterpreter.kt`, appelé par `resolveVoice` avant les règles actuelles. Entrée : requête normalisée (`normalizeForSearch`) et `VoiceRequest` (focus Assistant). Sortie :

```kotlin
data class VoiceIntent(
    val action: Action,          // PLAY, SHUFFLE, RESUME_BOOK, RESUME_LAST
    val subject: Subject?,       // ARTIST, ALBUM, PLAYLIST, GENRE, TITLE, RADIO, MIX, FAVORITES, MEMORY
    val name: String?,           // « rock », « fip », « mix du jour »
    val years: IntRange?,        // années 90 → 1990..1999 ; « de 2015 » → 2015..2015 ; « de 2015 à 2018 »
    val shuffle: Boolean,
)
```

Grammaire par motifs avec synonymes français : verbes (« mets », « joue », « lance », « écoute », « passe », facultatifs) ; déterminants (« du », « de la », « des », « un peu de ») ; périodes (« années 90 », « 90s », « nineties », « 2010s », « de 2015 », « de 2015 à 2018 », « avant 2000 », « récent ») ; mélange (« en aléatoire », « au hasard », « mélangé ») ; mixs (« mon mix du jour », « ma découverte du jour », « le mix <artiste> ») ; reprise (« continue », « reprends », « mon livre », « mon podcast », « là où j'en étais », « ce que j'écoutais ») ; radio (« la radio <nom> », « <nom> en radio ») ; favoris (liste existante `FAVORITE_WORDS`) ; souvenirs (« ce jour-là », « mes souvenirs », « il y a un an »). Le focus Assistant, s'il est présent, fixe `subject` et `name` ; la grammaire n'ajoute alors que `years` et `shuffle`.

### 2.2 Résolution

Dans `resolveVoice`, par `subject` :
- `ARTIST`, `ALBUM`, `PLAYLIST`, `GENRE`, `TITLE` : règles existantes, puis filtre `years` sur `Song.year` quand il est donné ; un genre inconnu avec une période connue donne tous les titres de la période.
- `MIX` : `DailyMixRepository.byKey` (mix du jour, découverte) ou le mix de l'artiste nommé, sinon le premier mix.
- `MEMORY` : première carte Souvenirs.
- `RESUME_BOOK` : premier élément de `continueListening()`.
- `RESUME_LAST` : file sauvegardée.
- `RADIO` : flou sur le nom des stations.
- `FAVORITES` : favoris, mélangés si `shuffle`.

Repli, dans l'ordre : règles actuelles, recherche floue titres/albums, puis, si la requête portait un genre ou une période sans correspondance, favoris en aléatoire. Résultat vide seulement si la bibliothèque est vide.

### 2.3 Résultats de recherche à l'écran

Groupes existants plus « Mixs » et « Livres et podcasts ». 30 éléments au plus, groupes ordonnés par pertinence (correspondance exacte d'artiste ou de playlist avant les titres). Quand la requête est un genre ou une période, une tuile « Tout lire » (`FOLDER_PLAYABLE`) ouvre le groupe en tête.

### 2.4 Tests

`VoiceInterpreterTest` en table, au moins 30 phrases, dont les pièges : « joue Années 80 » quand une playlist porte ce nom (la playlist gagne), « mix » comme titre de chanson, « radio » dans un nom d'album, « de 2015 » sans sujet, « continue » seul. `BrowseTreeTest` : période sans genre, repli favoris, livre à reprendre, mix par nom d'artiste.

## 3. Mode voiture 2

### 3.1 Détection

`CarDetector` dans `core/car/CarDetector.kt`, construit comme `BudsMonitor` : récepteur `BluetoothDevice.ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED`, actif seulement si `BLUETOOTH_CONNECT` est accordée. Un appareil est une voiture si `bluetoothClass.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO`, ou si son adresse figure dans « Mes voitures ». Décision pure `isCar(deviceClass: Int, address: String, chosen: Set<String>): Boolean`, testée. L'état est exposé par `CarDetector.connected: StateFlow<CarDevice?>`.

Réglages → Voiture : « Mes voitures » (liste des appareils appairés avec cases à cocher ; demande `BLUETOOTH_CONNECT` si besoin, comme la carte Buds).

### 3.2 À la connexion et à la déconnexion

Réglages, tous désactivés par défaut :
- « Proposer le mode voiture » : notification « Voiture détectée » (canal dédié, priorité par défaut, disparaît à la déconnexion) avec deux actions : Mode voiture, Lancer la lecture.
- « Lancer la lecture » : choix parmi Reprendre la file, une playlist choisie, Mix du jour, Favoris en aléatoire. La lecture démarre par le service, sans ouvrir l'écran.
- « Ouvrir le mode voiture automatiquement » : seulement si l'app est au premier plan (`ProcessLifecycleOwner`) ; sinon la notification suffit, Android interdit l'ouverture d'activité depuis l'arrière-plan.
- « Quitter avec la voiture » : à la déconnexion, retour à l'écran précédent si le mode voiture est ouvert.
- « Mettre en pause à la déconnexion » : couvre les cas où la voiture n'était pas la sortie audio active (sinon `handleAudioBecomingNoisy` fait déjà la pause).

La décision (réglages + événement → actions) est une fonction pure `carActions(event, settings, foreground): List<CarAction>` testée.

### 3.3 `DriveModeScreen` 2

- Écran gardé allumé (`FLAG_KEEP_SCREEN_ON`) et luminosité au maximum (`WindowManager.LayoutParams.screenBrightness = 1f`) tant que l'écran est affiché ; valeurs restaurées à la sortie. Thème sombre forcé.
- Sous les commandes : file simplifiée, les trois titres suivants, un toucher saute au titre ; bouton cœur ; bouton aléatoire ; bouton micro qui lance `Intent.ACTION_VOICE_COMMAND` (l'Assistant du téléphone revient par `onSetMediaItems`, section 2).
- Titre et artiste en très grand, gestes de balayage conservés, haptique 5.7.1 sur chaque bouton.

### 3.4 Tests

`CarDetectorTest` (classe, liste choisie, appareil inconnu), `CarActionsTest` (table réglages × événement × premier plan), `DriveQueueTest` (les trois suivants, fin de file, répétition).

## 4. Vérification sur Android Auto

Le Desktop Head Unit (DHU) du SDK Android (`extras/google/auto/desktop-head-unit`) joue l'écran de la voiture sur le PC, téléphone en USB avec le mode développeur d'Android Auto. Checklist consignée dans les notes de version, à passer sur le DHU puis dans la vraie voiture :

1. Accueil par groupes, groupes vides omis, « Surprends-moi » présent.
2. Tuiles albums, playlists, mixs, souvenirs : bouton lecture et ouverture.
3. Actions contextuelles sur un titre et sur un album ; cœur rafraîchi.
4. Progression d'un livre entamé et d'un épisode terminé.
5. Permission révoquée : message et bouton « Ouvrir Synxio sur le téléphone ».
6. Boutons précédent/suivant présents en radio.
7. Quinze phrases vocales de `VoiceInterpreterTest`, dont deux sans correspondance.
8. Pochettes nettes à la taille de l'écran, mosaïques des mixs.

## 5. Réglages, release

- Réglages → Voiture : Mes voitures, Proposer le mode voiture, Lancer la lecture (et son choix), Ouvrir automatiquement, Quitter avec la voiture, Mettre en pause à la déconnexion.
- Nouveau canal de notification « Voiture ».
- `versionCode 20261130`, `versionName "5.9.0"`, `docs/release-notes/v5.9.0.md` avec la checklist du §4.
- Aucune nouvelle table ; « Mes voitures » et les réglages passent par `SettingsRepository`.

## Hors périmètre

- Android Automotive OS (app installée dans la voiture) : autre distribution, autre cycle de validation.
- Car App Library : interdite aux apps de musique par Google, hors écrans de réglages ; non utilisée.
- Flow par BPM dans Auto (« Soirée : 118–126 BPM ») : dépend de N3, lot Flow.
- Commentaires vocaux ou synthèse vocale : rien n'est vocalisé.

## Dépendances et contraintes

- Media3 1.11.1 : clés de complétion, d'erreur, de taille d'art et de réservation disponibles ; actions de navigation par chaînes brutes (`AutoKeys`), relais à confirmer (§1.4).
- 5.8.0 pour Mixs et Souvenirs dans Auto (`DailyMixRepository` existe déjà ; `MemoryRepository` arrive en 5.8.0). Si la 5.9.0 part avant, le groupe « Pour toi » ne contient que les mixs.
- `BLUETOOTH_CONNECT` déjà déclarée ; `POST_NOTIFICATIONS` déjà déclarée.
- Tests JVM uniquement ; le DHU et la voiture pour le rendu.

## Ordre de réalisation

Trois plans d'exécution, une release :

1. **Arbre** (section 1, dont le spike du §1.4 en première tâche).
2. **Voix** (section 2).
3. **Voiture** (section 3, puis §4 et §5, notes et version).
