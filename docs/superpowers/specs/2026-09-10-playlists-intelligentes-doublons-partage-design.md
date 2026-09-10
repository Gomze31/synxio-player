# Playlists intelligentes, détection de doublons et partage

Design validé le 2026-09-10. Cible : Synxio Player 2.0.0 (`fr.synxio.player`).

## Objectif

Trois fonctionnalités indépendantes, livrées ensemble parce qu'elles touchent le même
domaine (la bibliothèque et les playlists) :

1. **Playlists intelligentes** — six playlists automatiques livrées d'office, plus un
   éditeur de règles pour en créer d'autres.
2. **Détection de doublons** — repérer les morceaux présents plusieurs fois et laisser
   l'utilisateur supprimer les exemplaires en trop, un par un.
3. **Partage** — sortir une playlist de l'appli (fichier `.m3u8`, texte, ou fichiers
   audio) et en faire entrer une (ouvrir un `.m3u8` reçu).

Hors périmètre : groupes de règles imbriqués, empreinte audio, nettoyage automatique
des doublons, refonte de l'UI existante.

## Contraintes issues du code existant

- `MusicRepository` expose la bibliothèque complète en mémoire (`library: StateFlow<Library>`,
  `songsById: StateFlow<Map<Long, Song>>`). `Models.kt` pose la règle : le scanner produit
  une liste plate immuable, tout le reste en est dérivé. Les nouveaux moteurs s'y conforment.
- Room est en **version 3**, `exportSchema = true`, schémas dans `app/schemas`.
- `PlaylistRepository` sait déjà `exportM3u(playlist, uri)` et `importM3u(uri, fallbackName)`
  via SAF. Le partage réutilise ces deux fonctions, il ne les réécrit pas.
- `SongOptionsSheet` / `SongMenuHost` portent déjà un partage de fichier unique
  (`ACTION_SEND`) ; il n'y a **pas** de `FileProvider` déclaré au manifeste.
- `minSdk 26`, `targetSdk 36`. `WRITE_EXTERNAL_STORAGE` est déclaré avec
  `maxSdkVersion="29"` — le chemin de suppression hérité pour API 26-29 est donc couvert.
- `kotlinx.serialization` et son plugin Gradle sont déjà des dépendances du module.

## 1. Playlists intelligentes

### 1.1 Modèle

Nouveau fichier `data/model/SmartPlaylist.kt`. Types `@Serializable`, sérialisés en JSON
dans une colonne TEXT.

```kotlin
enum class RuleField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, YEAR, DURATION,
                       PLAY_COUNT, SKIP_COUNT, LAST_PLAYED, DATE_ADDED, FOLDER,
                       FAVORITE, FORMAT, BITRATE }

enum class RuleOperator { CONTAINS, NOT_CONTAINS, EQUALS, NOT_EQUALS, STARTS_WITH,
                          GREATER_THAN, LESS_THAN, IN_LAST_DAYS, NOT_IN_LAST_DAYS,
                          IS_TRUE, IS_FALSE }

@Serializable
data class SmartRule(val field: RuleField, val operator: RuleOperator, val value: String)

enum class SmartSort { TITLE, ARTIST, ALBUM, YEAR, DATE_ADDED, PLAY_COUNT, LAST_PLAYED, RANDOM }

@Serializable
data class SmartPlaylistRules(
    val rules: List<SmartRule>,
    val matchAll: Boolean = true,   // true = ET, false = OU
    val sort: SmartSort = SmartSort.TITLE,
    val descending: Boolean = false,
    val limit: Int? = null,
)
```

Chaque `RuleField` déclare les opérateurs qui lui sont applicables (`RuleField.operators`),
pour que l'éditeur ne propose jamais une combinaison absurde comme « favori contient ».
La valeur est toujours stockée en texte et interprétée selon le type du champ ; une valeur
numérique illisible fait échouer la règle plutôt que planter l'évaluation.

**Combinaison à un seul niveau, assumée.** Pas de groupes imbriqués : c'est exactement ce
qui rend les éditeurs de règles façon iTunes illisibles. Si le besoin apparaît, il fera
l'objet d'une décision séparée.

**Identité d'une playlist intelligente.** Les presets ne vivent pas en base (§1.3) mais
doivent être adressables par la navigation au même titre que celles de l'utilisateur. D'où
un identifiant unifié :

```kotlin
@Serializable
sealed interface SmartPlaylistId {
    @Serializable data class Preset(val key: String) : SmartPlaylistId   // "recent", "forgotten"…
    @Serializable data class Stored(val id: Long) : SmartPlaylistId      // ligne Room
}
```

Encodé en argument de route sous la forme `preset:recent` ou `stored:12`, avec une paire
`SmartPlaylistId.encode()` / `decode()` comme seul endroit qui connaît ce format.

### 1.2 Évaluation

`SmartPlaylistEngine` — objet sans dépendance Android, donc testable en test unitaire pur :

```kotlin
fun evaluate(
    rules: SmartPlaylistRules,
    songs: List<Song>,
    stats: Map<Long, PlayStatEntity>,
    favorites: Set<Long>,
    now: Long,
): List<Song>
```

Filtre, puis trie, puis applique la limite. `RANDOM` utilise un `Random` graine sur le jour
courant : la playlist reste stable pendant une session au lieu de se réordonner à chaque
recomposition.

`SmartPlaylistRepository` combine `musicRepository.library`, `playStatDao.observeAll()` et
`favoriteDao.observeAll()` pour exposer :

- `smartPlaylists: StateFlow<List<SmartPlaylist>>` (presets + celles de l'utilisateur)
- `observeSongs(id: SmartPlaylistId): Flow<List<Song>>`
- `countMatching(rules): Int` pour l'aperçu live de l'éditeur

Le résultat est donc réactif : jouer un morceau ou cocher un favori met la playlist à jour
sans action de l'utilisateur.

### 1.3 Presets

Six règles **virtuelles**, définies en dur dans `SmartPlaylistPresets`, jamais insérées en
base. Identifiées par un id texte (`preset:recent`), là où les playlists utilisateur ont un
id numérique Room.

| Preset | Règles | Tri | Limite |
|---|---|---|---|
| Ajoutés récemment | `DATE_ADDED IN_LAST_DAYS 30` | date d'ajout ↓ | 100 |
| Les plus écoutés | `PLAY_COUNT GREATER_THAN 2` | lectures ↓ | 100 |
| Jamais écoutés | `PLAY_COUNT LESS_THAN 1` | aléatoire | 100 |
| Coups de cœur | `FAVORITE IS_TRUE` | date d'ajout ↓ | — |
| Oubliés | `LAST_PLAYED NOT_IN_LAST_DAYS 180` ET `PLAY_COUNT GREATER_THAN 0` | dernière écoute ↑ | 100 |
| Découvertes | `PLAY_COUNT LESS_THAN 2` | aléatoire | 50 |

Un preset n'est pas modifiable, mais un bouton **« Dupliquer en playlist modifiable »**
ouvre l'éditeur pré-rempli avec ses règles. C'est aussi le chemin d'apprentissage de
l'éditeur. Avantage sur un seed en base : corriger un preset se fait par mise à jour de
l'appli, pas par migration.

### 1.4 Persistance

```kotlin
@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rulesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

`SmartPlaylistDao` : `observeAll`, `observeById`, `insert`, `update`, `delete`.

**Room 3 → 4** avec une `Migration` écrite à la main dans `SynxioDatabase`, enregistrée
dans `AppModule`. Interdiction explicite d'utiliser `fallbackToDestructiveMigration` :
cela effacerait playlists, favoris, statistiques et historique de l'utilisateur.

### 1.5 UI

- **`PlaylistsScreen`** : nouvelle section « Playlists intelligentes » au-dessus des
  playlists manuelles, chaque entrée badgée ✨ avec son nombre de titres. Bouton
  d'ajout distinct de celui des playlists manuelles.
- **Détail d'une playlist intelligente** — route `smart/{id}` où `{id}` est un
  `SmartPlaylistId` encodé (`preset:recent`, `stored:12`).
- **`SmartPlaylistEditorScreen`** (nouveau) — route `smart_editor/{id}?preset={key}`.
  L'éditeur ne travaille que sur des playlists stockées : `{id}` y est donc numérique,
  `-1` pour une création. `?preset=` est renseigné uniquement par l'action
  « Dupliquer en playlist modifiable », pour pré-remplir le formulaire avec les règles du
  preset. Nom, sélecteur ET/OU, liste de critères (ajout, suppression, édition),
  tri, sens, limite, et un compteur live « N titres correspondent » calculé par
  `countMatching`. Enregistrement désactivé tant que le nom est vide ou qu'aucune règle
  n'est posée.
- **Détail** : `PlaylistDetailScreen` réutilisé en mode lecture seule — pas de
  glisser-déposer ni de suppression d'un titre sur une playlist calculée, ce serait
  mensonger. Actions disponibles : lire, lire en aléatoire, mettre en file, partager,
  modifier les règles, supprimer.
- **`SmartPlaylistViewModel`** pour l'éditeur.

## 2. Détection de doublons

### 2.1 Critère

Tags normalisés, sans lecture des fichiers — instantané même sur une grosse bibliothèque.

Normalisation appliquée au titre et à l'artiste :
minuscules → accents retirés (`Normalizer NFD` + suppression des diacritiques) →
suffixes parasites retirés (`(Remastered…)`, `[Live]`, `- Radio Edit`, `feat. …`,
`ft. …`) → ponctuation et espaces multiples retirés.

Deux morceaux sont doublons si `titre normalisé` **et** `artiste normalisé` sont
identiques **et** que les durées ne diffèrent pas de plus de **3 secondes**. La durée est
le garde-fou contre le faux positif classique : une reprise, un live et un remix portent
le même titre et le même artiste mais pas la même durée.

`DuplicateRepository.findDuplicates(): List<DuplicateGroup>`, où un groupe porte la liste
des morceaux et l'index de celui recommandé à garder.

**Choix du « meilleur » exemplaire** — score décroissant :
1. débit approximatif le plus élevé (`Song.approxBitrateKbps`)
2. à débit égal, taille de fichier la plus grande
3. complétude des tags (album, artiste d'album, genre, année, numéro de piste renseignés)
4. chemin le plus court (départage stable)

### 2.2 UI et suppression

`DuplicatesScreen` (route `duplicates`), atteint depuis les Réglages, à côté de
« Réparer la bibliothèque » — même famille d'outils, même endroit.

- En-tête : nombre de groupes, nombre de fichiers en trop, espace récupérable.
- Une carte par groupe. Dans chaque groupe, l'exemplaire recommandé est marqué
  **« À garder »** ; les autres portent une case à cocher, **décochée par défaut**.
- Chaque ligne affiche ce qui permet de décider : format, débit, taille, dossier.
- Bouton « Supprimer la sélection », **désactivé tant que rien n'est coché**, suivi d'un
  dialogue récapitulant le nombre de fichiers et l'espace libéré.
- Suppression via `MediaStore.createDeleteRequest` sur API 30+ (Android affiche sa propre
  confirmation système, l'appli n'a pas le dernier mot) ; repli `contentResolver.delete`
  pour API 26-29.
- Après suppression : rafraîchissement de la bibliothèque et nouveau scan des doublons.

Aucune suppression n'est possible sans action explicite de l'utilisateur. Aucun bouton
« tout nettoyer ».

## 3. Partage

### 3.1 Sortie

`FileProvider` à déclarer au manifeste (`${applicationId}.fileprovider`) avec
`res/xml/file_paths.xml` exposant `cache/shared`.

`ShareRepository` :

- `sharePlaylistFile(playlist)` — écrit le `.m3u8` dans `cacheDir/shared/` en réutilisant
  le format de `exportM3u`, puis `ACTION_SEND` avec l'URI du `FileProvider` et
  `FLAG_GRANT_READ_URI_PERMISSION`.
- `sharePlaylistText(playlist)` — `ACTION_SEND` texte : `« Nom (N titres) »` puis
  `1. Artiste — Titre` par ligne.
- `sharePlaylistAudio(playlist)` — `ACTION_SEND_MULTIPLE` avec les URIs MediaStore des
  morceaux. Avertit avant l'envoi si le total dépasse 100 Mo, et refuse au-delà de
  500 Mo (aucune application de messagerie n'y survit).

Point d'entrée : menu de la playlist (manuelle **et** intelligente) → « Partager » →
feuille de choix entre les trois formes.

Le partage d'un fichier unique existant est migré vers le `FileProvider` : aujourd'hui il
passe l'URI MediaStore directement, ce qui échoue avec certaines applications réceptrices.

### 3.2 Entrée

`intent-filter` `ACTION_VIEW` sur `audio/x-mpegurl`, `audio/mpegurl`,
`application/vnd.apple.mpegurl` et l'extension `.m3u8`. `MainActivity` détecte l'intent
d'ouverture et appelle `PlaylistRepository.importM3u(uri, fallbackName)`, déjà écrit, puis
navigue vers la playlist créée. Message d'erreur clair si aucun titre du fichier n'existe
dans la bibliothèque locale — cas normal quand le `.m3u8` vient d'un autre appareil.

## 4. Toolchain de ce poste

`gradle.properties` versionné pointe sur `C:\Users\y.gomez\android-dev`, qui n'existe pas
sur ce poste. Ce poste a : JDK 21 (Adoptium), SDK dans `%LOCALAPPDATA%\Android\Sdk`
(platform android-36, build-tools 36).

Correction **sans casser l'autre poste** : `local.properties` (non versionné) pour
`sdk.dir`, et les chemins `tmpdir` / `buildDirRoot` rendus conditionnels — utilisés s'ils
existent, comportement Gradle par défaut sinon. Si le blocage OneDrive décrit dans le
README se manifeste réellement ici, il sera traité à ce moment-là, pas par anticipation.

## 5. Tests

**Unitaires** (`test/`, sans Android) :

- `SmartPlaylistEngine` — chaque opérateur, ET vs OU, tri, limite, valeurs invalides,
  bibliothèque vide.
- Normalisation des doublons — accents, suffixes, `feat.`, tolérance de durée, choix du
  meilleur exemplaire.

**Sur le téléphone** (Honor `BKQ-N49`, Android 17 / SDK 37, débogage USB), piloté par adb
avec captures d'écran et surveillance `logcat` :

- Nouveautés : créer une règle et vérifier que le résultat correspond vraiment à la
  bibliothèque, chaque preset, scan des doublons, les trois formes de partage, import
  d'un `.m3u8` reçu.
- Non-régression : lecture, file d'attente, favoris, égaliseur, éditeur de tags, widget,
  notification, recherche, statistiques.

**La suppression effective de doublons n'est menée à son terme que sur accord explicite
de l'utilisateur** : ce sont ses vrais fichiers musicaux. Sans accord, le test s'arrête à
la boîte de confirmation système.

## 6. Risques

| Risque | Traitement |
|---|---|
| Migration Room qui perd des données | Migration écrite à la main, jamais de `fallbackToDestructiveMigration`. Vérifiée sur le téléphone avec une base v3 réelle. |
| Faux positif de doublon → fichier perdu | Tolérance de durée à ±3 s, cases décochées par défaut, double confirmation dont celle du système. |
| `ACTION_SEND_MULTIPLE` trop lourd | Avertissement à 100 Mo, refus à 500 Mo. |
| Appareil en SDK 37 alors que l'app cible 36 | Comportement Android 16 appliqué par le système : c'est nominal et supporté. Confirmé au test. |
| Écrans existants déjà volumineux | L'éditeur et l'écran doublons sont de nouveaux fichiers ; `PlaylistsScreen` ne reçoit qu'une section. |
