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

- Jusqu'à la 5.6.0, `app/synxio-release.keystore` était versionné dans le dépôt **public**,
  et son mot de passe était écrit en clair dans `app/build.gradle.kts`.
- Le workflow `.github/workflows/release.yml` n'utilisait aucun secret : chaque release
  publiée était signée avec cette clé publique.
- Conséquence : n'importe qui pouvait produire un APK « Synxio Player » signé avec la même
  clé. Android l'aurait installé **par-dessus** l'application existante (même signature),
  avec accès à toutes ses données (bibliothèque, réglages, URL de webhook Discord, session
  Last.fm…). Le contrôle de signature du programme de mise à jour comparait l'APK
  téléchargé à la signature installée, que l'attaquant possédait aussi.
- La 5.6.1 retire le keystore et le mot de passe de l'arbre, et la CI ne signe plus
  qu'avec la nouvelle clé tenue en secret. Le fichier reste lisible dans l'historique
  Git tant que la purge (§8) n'est pas faite.

## 3. Générer la nouvelle clé et la lignée

Tout passe par `scripts/rotate-release-key.sh`, sur une machine de confiance. Le script
génère la nouvelle clé, signe la lignée ancienne clé → nouvelle clé et affiche
l'empreinte SHA-256 du nouveau certificat. Les mots de passe sont demandés par `keytool`
et `apksigner`, jamais écrits ni affichés.

1. Récupérer l'ancien keystore hors du dépôt (il n'est plus dans l'arbre depuis la 5.6.1) :

   ```bash
   mkdir -p ~/synxio-keys
   git show 4a55d01:app/synxio-release.keystore > ~/synxio-keys/synxio-release-1.jks
   ```

2. Lancer le script depuis la racine du dépôt, avec le chemin de l'ancien keystore en
   premier argument (ou dans la variable `OLD_KEYSTORE`) et, en option, le dossier de
   sortie en second (`~/synxio-keys` par défaut) :

   ```bash
   scripts/rotate-release-key.sh ~/synxio-keys/synxio-release-1.jks
   ```

3. Le dossier de sortie contient alors `synxio-release-2.jks` et `synxio.lineage`.
   Sauvegarder la nouvelle clé et son mot de passe **à deux endroits distincts** hors
   ligne : une clé perdue, c'est plus aucune mise à jour possible.
4. Noter l'empreinte affichée à l'étape 3/3 du script : elle sert au §6.

## 4. Configurer les 5 secrets GitHub

Dépôt → *Settings* → *Secrets and variables* → *Actions* → *New repository secret* :

| Secret              | Valeur                                                        |
|---------------------|---------------------------------------------------------------|
| `KEYSTORE_BASE64`   | `base64 -w0 synxio-release-2.jks` (macOS : `base64 -i …`)     |
| `KEYSTORE_PASSWORD` | mot de passe du keystore                                      |
| `KEY_ALIAS`         | `synxio`                                                      |
| `KEY_PASSWORD`      | mot de passe de la clé (identique au précédent en PKCS12)     |
| `LINEAGE_BASE64`    | `base64 -w0 synxio.lineage`                                   |

Ou avec la CLI GitHub, depuis le dossier de sortie du script :

```bash
base64 -w0 synxio-release-2.jks | gh secret set KEYSTORE_BASE64
base64 -w0 synxio.lineage | gh secret set LINEAGE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body synxio
gh secret set KEY_PASSWORD
```

Les valeurs sont écrites telles quelles, sans guillemets, dans `keystore.properties` par
la CI : elles ne doivent contenir ni barre oblique inverse (`\`), ni espace en début ou
en fin, sous peine d'être lues autrement que saisies.

En local, créer `keystore.properties` à la racine (non versionné) :

```properties
storeFile=chemin/vers/synxio-release-2.jks
storePassword=…
keyAlias=synxio
keyPassword=…
```

## 6. Épingler l'empreinte dans l'application

Le contrôle est implémenté dans `UpdateSignatures.PINNED_SIGNER_SHA256`
(`app/src/main/java/fr/synxio/player/data/repo/UpdateSignatures.kt`) : tout APK de mise à
jour doit être signé par ce certificat, et sa lignée doit contenir le signataire de
l'application installée. Tant que la constante est vide, aucune mise à jour n'est
acceptée, et la CI refuse de publier (étape « Check pinned signer » de `release.yml`).

Format attendu : 64 caractères hexadécimaux, majuscules ou minuscules, sans « : ». La
ligne `SHA256:` de `keytool -list -v` affiche les octets séparés par des « : » ; il
suffit de retirer les « : ».

**La branche ne doit pas être publiée tant que la constante est vide.**

## 7. Conséquence pour les utilisateurs

Obsolète depuis la lignée v3 : la mise à jour est transparente sur Android 9+.

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
