# Rotation de la clé de signature release

> Statut : **préparé, non appliqué.** Rien dans ce document n'a encore été exécuté.
> La clé de secours reste en place tant que la rotation n'est pas décidée, car les
> mises à jour in-app des utilisateurs actuels en dépendent.

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

## 2. Garde-fous déjà en place

- `app/build.gradle.kts` affiche un avertissement Gradle (`logger.warn`) à chaque build release
  signé avec la clé de secours.
- Interrupteur **`synxio.requireReleaseKey`** : quand il vaut `true`, un build release
  **échoue** au lieu d'utiliser la clé de secours.
  - en ligne de commande : `./gradlew assembleRelease -Psynxio.requireReleaseKey=true`
  - ou variable d'environnement : `SYNXIO_REQUIRE_RELEASE_KEY=true`
- `release.yml` émet un `::warning::` GitHub quand le secret `KEYSTORE_BASE64` est absent
  (le build continue).

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

## 5. Activer l'interrupteur

Une fois les secrets en place, dans `release.yml`, étape *Build Release APK* :

```yaml
      - name: Build Release APK
        env:
          SYNXIO_REQUIRE_RELEASE_KEY: "true"
        run: ./gradlew assembleRelease --no-daemon --configuration-cache -x lintVitalRelease
```

Dès lors, une release sans secret échoue au lieu de partir signée avec la clé publique.
Ensuite seulement : supprimer le bloc `else { … }` de secours dans `signingConfigs` et le
fichier `app/synxio-release.keystore`.

## 6. Mettre à jour le contrôle de signature du programme de mise à jour

`data/repo/UpdateRepository.kt`, `hasMatchingSignature()` (≈ lignes 192-216) compare
l'APK téléchargé à la signature **installée**. Après rotation :

- remplacer (ou compléter) cette comparaison par une **empreinte épinglée** : l'empreinte
  SHA-256 du nouveau certificat (§3), stockée en constante, que l'APK téléchargé doit porter ;
- ne plus accepter l'ancienne empreinte dès que la transition est terminée.

Sans cela, l'ancienne application refusera la nouvelle version (signatures différentes) avec
le message « signature différente », ce qui est le comportement attendu pendant la transition.

## 7. Conséquence pour les utilisateurs — À LIRE

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

## 8. Purger l'historique Git

Retirer le fichier de l'arbre ne suffit pas : il reste dans tous les commits passés.
Avec [`git filter-repo`](https://github.com/newren/git-filter-repo) :

```bash
# Sur un clone miroir frais
git clone --mirror https://github.com/Gomze31/synxio-player.git
cd synxio-player.git
git filter-repo --invert-paths --path app/synxio-release.keystore
# Le mot de passe en clair dans build.gradle.kts : remplacer le texte
echo 'synxioplayer==>REMOVED' > ../replacements.txt
git filter-repo --replace-text ../replacements.txt
git push --force --mirror
```

Avertissements :

- la réécriture change **tous** les identifiants de commit : tous les clones et forks doivent
  être re-clonés, les PR ouvertes deviennent inutilisables ;
- les forks publics et les caches (GitHub, archives des releases, miroirs) conservent l'ancienne
  clé : la purge **ne la rend pas secrète à nouveau**. Seule la rotation (§3 à §7) neutralise
  le risque ; la purge évite seulement de continuer à la diffuser ;
- contacter le support GitHub pour purger les vues en cache si nécessaire.
