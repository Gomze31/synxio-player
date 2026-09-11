# 🎧 Synxio Player

<div align="center">

![GitHub Release](https://img.shields.io/github/v/release/Gomze31/synxio-player?color=4CAF50&logo=android&style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-blue?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin%20%2F%20Compose-purple?style=for-the-badge&logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Active-brightgreen?style=for-the-badge)

**Un lecteur de musique moderne, fluide et élégant pour Android.**  
*Sans publicité, sans pistage, sans compte : vos fichiers locaux et vos webradios préférées sublimés.*

[📥 Télécharger l'APK](https://github.com/Gomze31/synxio-player/releases/latest) • [✨ Fonctionnalités](#-fonctionnalités) • [📱 Captures](#-aperçu) • [🚀 Démarrage](#-démarrage--compilation) • [🔒 Vie Privée](#-vie-privée--sécurité)

---

</div>

## 🌟 Pourquoi Synxio Player ?

**Synxio Player** a été pensé pour offrir l'expérience d'écoute locale ultime sur Android. Conçu avec **Jetpack Compose** et propulsé par le moteur haute performance **Media3 (ExoPlayer)**, il combine une interface Material 3 réactive, des couleurs dynamiques adaptées à chaque pochette d'album et un ensemble complet d'outils pour audiophiles et mélomanes.

---

## ✨ Fonctionnalités

### 🎵 Expérience Audio Haute Qualité
- **Moteur Media3 (ExoPlayer)** : Lecture continue et stable avec service d'avant-plan persistant.
- **Lecture Gapless & Fondu Enchaîné** : Transitions fluides et personnalisables (0 à 12 s) pour vos albums live et conceptuels.
- **Contrôle Précis du Son** : Réglage indépendant de la **vitesse** (0.5× à 2.5×) et de la **tonalité** (pitch).
- **Égaliseur & Effets** : Compatibilité avec l'égaliseur système, amplification des basses, spatialisation et gain audio.
- **Minuterie de Veille (Sleep Timer)** : Extinction programmée avec fondu audio doux, ou option « Terminer le morceau en cours ».
- **Gestion du Focus Audio** : Reprise intelligente au branchement Bluetooth et mise en pause automatique au débranchement.

### 🎨 Design Moderne & Personnalisation
- **Thèmes Dynamiques Material You** : Couleurs de l'interface extraites en direct de la pochette d'album via *Palette*.
- **Styles de Lecteur Multiples** :
  - *Immersif* : Grand visuel avec arrière-plan flouté artistique.
  - *Vinyle* : Disque vinyle rétro en rotation pendant la lecture.
  - *Carte & Minimaliste* : Pour une navigation épurée.
- **Mode Sombre & Vrai Noir AMOLED** : Optimisé pour économiser la batterie sur les écrans OLED.

### 🚗 Connectivité & Intégrations
- **🚗 Android Auto & Google Assistant** : Arborescence complète (Albums, Artistes, Playlists, Favoris) et contrôle vocal au volant.
- **📱 Widgets Écran d'Accueil** : Widgets modernes et interactifs (Style Glace / Vinyle) avec pochettes et contrôles en temps réel.
- **🎮 Discord Rich Presence** : Partagez en temps réel le titre et l'artiste en cours d'écoute sur votre profil Discord.
- **🪄 Magic Capsule / Dynamic Island** : Intégration native des contrôles d'îlot sur les smartphones compatibles (MagicOS, etc.).
- **📊 Scrobbling Last.fm** : Synchronisation fluide de vos statistiques d'écoute sur Last.fm.

### 📚 Gestion Complète de la Bibliothèque
- **Indexation Scoped Storage MediaStore** : Détection instantanée des nouveaux ajouts sans bloquer le téléphone.
- **Éditeur de Tags ID3** : Modifiez titre, artiste, album et pochette directement dans l'application.
- **Paroles Synchronisées (.LRC)** : Affichage automatique des paroles (fichiers `.lrc` locaux ou via l'API [LRCLIB](https://lrclib.net)).
- **Playlists Intelligentes & Export M3U8** : Créez, triez, dupliquez et exportez vos listes d'écoute facilement.
- **Webradios Intégrées** : Écoutez vos flux radio en direct directement depuis l'application.

---

## 📥 Installation & Téléchargement

### Méthode 1 : Téléchargement direct (Recommandé)
1. Rendez-vous sur la page des [**Dernières Versions (Releases)**](https://github.com/Gomze31/synxio-player/releases/latest).
2. Téléchargez le fichier `app-release.apk`.
3. Ouvrez le fichier sur votre appareil Android et autorisez l'installation depuis des sources inconnues si demandé.

> 💡 **Mises à jour automatiques** : Synxio intègre un vérificateur de mise à jour direct. Vous recevrez une notification dès qu'une nouvelle version est disponible sur GitHub !

---

## 🚀 Démarrage & Compilation

### Prérequis
- **JDK 17**
- **Android Studio Ladybug ou version plus récente**
- **Android SDK Platform 36** (Compatibilité de Android 8.0 `minSdk 26` à Android 16 `targetSdk 36`)

### Cloner et compiler en local

```bash
# 1. Cloner le dépôt
git clone https://github.com/Gomze31/synxio-player.git

# 2. Accéder au dossier du projet
cd synxio-player

# 3. Compiler l'APK de développement
./gradlew assembleDebug

# 4. Installer sur un appareil connecté (ADB USB / Wi-Fi)
./gradlew installDebug
```

---

## 🏗️ Architecture du Projet

Le projet suit les principes de **Clean Architecture** et **MVVM** recommandés par Google pour Android :

```
fr.synxio.player
├── core/
│   ├── prefs/        # DataStore : Paramètres et préférences persistés
│   └── util/         # Utilitaires (formatage, recherche floue, permissions)
├── data/
│   ├── db/           # Room Database : Playlists, favoris, historique, cache
│   ├── media/        # MediaStore Scanner et observateur temps réel
│   ├── model/        # Modèles de données (Song, Album, Artist, Lyrics, Radio)
│   ├── lastfm/       # Client API Last.fm pour le scrobbling
│   └── repo/         # Repositories (MusicRepository, TagEditor, LyricsRepository)
├── playback/
│   ├── PlaybackService     # MediaLibraryService Media3 gérant ExoPlayer
│   ├── PlayerConnection    # MediaController reliant l'UI au Service
│   ├── MediaLibraryTree    # Arborescence multimédia pour Android Auto
│   ├── EqualizerController # Effets audio et égalisation
│   └── SleepTimer          # Minuterie de sommeil programmable
├── ui/
│   ├── theme/        # Système de design Material 3 & Palette
│   ├── components/   # Composants Jetpack Compose réutilisables
│   ├── screens/      # Écrans (Bibliothèque, Playlists, Égaliseur, Radios, etc.)
│   └── viewmodel/    # State Holders (AppViewModel, PlayerViewModel)
└── widget/           # Widgets d'écran d'accueil Glance
```

---

## 🔒 Vie Privée & Sécurité

- **Zéro Pistage** : Aucune donnée de navigation ni identifiant personnel n'est collecté.
- **Zéro Publicité** : Une expérience purement dédiée à la musique.
- **Connexion Réseau Minimale** : Les seules requêtes réseau sont optionnelles et transparentes (téléchargement de paroles via LRCLIB, scrobbling Last.fm si activé, webradios et recherche de mises à jour GitHub).

---

## 📄 Licence

Ce projet est sous licence open-source **MIT**. Consultez le fichier [LICENSE](LICENSE) pour plus de détails.

---

<div align="center">

Développé avec passion pour les amoureux de musique.  
⭐ **N'hésitez pas à laisser une étoile sur GitHub si le projet vous plaît !**

</div>
