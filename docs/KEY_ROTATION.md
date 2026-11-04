# Rotation de la clé de signature release

> Statut : **rotation par lignée v3, minSdk 28.** Depuis la 5.6.1, la clé de secours
> publique n'existe plus dans le dépôt ni dans la CI. La nouvelle clé signe seule.

## 0. Ce qui est fait, ce qui reste

Fait dans le code (5.6.1) :

- `app/build.gradle.kts` : plus de clé de secours ; un build release sans
  `keystore.properties` échoue.
- `release.yml` : secrets obligatoires, signature avec la nouvelle clé et sa lignée,
  `--min-sdk-version 28`.
- `UpdateSignatures.PINNED_SIGNER_SHA256` : empreinte de la nouvelle clé exigée sur tout
  APK de mise à jour.

Reste au mainteneur, une seule fois, avant le tag `v5.6.1` :

1. `scripts/rotate-release-key.sh` (clé + lignée, mots de passe saisis à la main).
2. Les cinq secrets GitHub (§4) : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`, `LINEAGE_BASE64`.
3. Renseigner `PINNED_SIGNER_SHA256` avec l'empreinte affichée par le script (hexadécimal
   sans « : »), puis commit.
4. `keystore.properties` à la racine pour les builds release locaux.

Transition : la 5.6.0 (ancienne clé) accepte la 5.6.1 parce que la lignée contient
l'ancienne clé. À partir de la 5.6.1, un APK signé par l'ancienne clé seule est refusé par
l'application et par Android. Android 8.x ne reçoit plus de mise à jour.

## 1. Le risque

- `app/synxio-release.keystore` est versionné dans le dépôt **public**, et son mot de passe
  (`synxioplayer`) est écrit en clair dans `app/build.gradle.kts`.
- Le workflow `.github/workflows/release.yml` n'a **aucun secret** configuré : chaque release
  publiée est donc signée avec cette clé publique.
- Conséquence : n'importe qui peut produire un APK « Synxio Player » signé avec la même clé.
  Android l'installera **par-dessus** l'application existante (même signature), avec accès à
  toutes ses données (bibliothèque, réglages, URL de webhook Discord, session Last.fm…).
  Le contrôle de signature du programme de mise à jour (`UpdateRepository.hasMatchingSignature`)
  ne protège pas non plus : il compare l'APK téléchargé à la signature installée, que
  l'attaquant possède.

## 3. Générer une nouvelle clé

Sur une machine de confiance, **hors du dépôt** :

```bash
keytool -genkeypair -v \
  -keystore synxio-release-2.jks \
  -storetype PKCS12 \
  -alias synxio \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=Synxio Player, O=Synxio, C=FR"
```

- Choisir un mot de passe long et unique (gestionnaire de mots de passe).
- Sauvegarder le fichier `.jks` et le mot de passe **à deux endroits distincts** hors ligne :
  une clé perdue = plus aucune mise à jour possible.
- Relever l'empreinte SHA-256 du certificat (utile au §6) :

```bash
keytool -list -v -keystore synxio-release-2.jks -alias synxio | grep SHA256
```

## 4. Configurer les 4 secrets GitHub

Dépôt → *Settings* → *Secrets and variables* → *Actions* → *New repository secret* :

| Secret              | Valeur                                                        |
|---------------------|---------------------------------------------------------------|
| `KEYSTORE_BASE64`   | `base64 -w0 synxio-release-2.jks` (macOS : `base64 -i …`)     |
| `KEYSTORE_PASSWORD` | mot de passe du keystore                                      |
| `KEY_ALIAS`         | `synxio`                                                      |
| `KEY_PASSWORD`      | mot de passe de la clé (identique au précédent en PKCS12)     |

Ou avec la CLI GitHub :

```bash
base64 -w0 synxio-release-2.jks | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body synxio
gh secret set KEY_PASSWORD
```

En local, créer `keystore.properties` à la racine (non versionné) :

```properties
storeFile=chemin/vers/synxio-release-2.jks
storePassword=…
keyAlias=synxio
keyPassword=…
```

## 6. Mettre à jour le contrôle de signature du programme de mise à jour

`data/repo/UpdateRepository.kt`, `hasMatchingSignature()` (≈ lignes 192-216) compare
l'APK téléchargé à la signature **installée**. Après rotation :

- remplacer (ou compléter) cette comparaison par une **empreinte épinglée** : l'empreinte
  SHA-256 du nouveau certificat (§3), stockée en constante, que l'APK téléchargé doit porter ;
- ne plus accepter l'ancienne empreinte dès que la transition est terminée.

Sans cela, l'ancienne application refusera la nouvelle version (signatures différentes) avec
le message « signature différente », ce qui est le comportement attendu pendant la transition.

## 7. Conséquence pour les utilisateurs — À LIRE

Obsolète depuis la lignée v3 : la mise à jour est transparente sur Android 9+. Conservé pour mémoire.

**Changer de clé oblige chaque utilisateur à désinstaller puis réinstaller l'application
une fois.** Android refuse d'installer une mise à jour signée par une autre clé
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), et le programme de mise à jour in-app la refusera
aussi. La désinstallation **efface les données locales** (playlists, statistiques, réglages) :

- publier la release de transition avec une note explicite ;
- inviter les utilisateurs à faire une **sauvegarde** (Réglages → Sauvegarde/export) avant de
  désinstaller, puis à la réimporter après installation.

Alternative à étudier : la rotation de clé APK Signature Scheme v3
(`apksigner rotate` + `--lineage`) permet une mise à jour transparente sur Android 9+,
mais l'ancienne clé, compromise, reste dans la lignée : à n'utiliser qu'en connaissance de
cause, et pas sur Android 8 (minSdk 26).

