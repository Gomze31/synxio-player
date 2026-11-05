#!/usr/bin/env bash
# Rotation de la clé de signature release (lignée APK Signature Scheme v3).
# Voir docs/KEY_ROTATION.md. À lancer UNE fois, depuis la racine du dépôt, sur une machine
# de confiance (Git Bash sous Windows convient). Les mots de passe sont demandés par
# keytool / apksigner : ils ne sont ni écrits dans le dépôt, ni affichés.
#
# Usage : scripts/rotate-release-key.sh <ancien-keystore> [dossier-de-sortie]
#    ou : OLD_KEYSTORE=<ancien-keystore> scripts/rotate-release-key.sh
#
# L'ancien keystore a quitté l'arbre en 5.6.1. Le mainteneur le récupère hors du dépôt,
# depuis un commit où il était encore suivi, avant de lancer le script :
#   mkdir -p ~/synxio-keys
#   git show 4a55d01:app/synxio-release.keystore > ~/synxio-keys/synxio-release-1.jks
# puis : scripts/rotate-release-key.sh ~/synxio-keys/synxio-release-1.jks
set -euo pipefail

OLD_KS="${1:-${OLD_KEYSTORE:-}}"
OUT="${2:-$HOME/synxio-keys}"
NEW_KS="$OUT/synxio-release-2.jks"
LINEAGE="$OUT/synxio.lineage"

if [ -z "$OLD_KS" ]; then
  echo "Ancien keystore non indiqué : passe son chemin en premier argument ou dans OLD_KEYSTORE." >&2
  echo "Pour le récupérer : git show 4a55d01:app/synxio-release.keystore > ~/synxio-keys/synxio-release-1.jks" >&2
  exit 1
fi
[ -f "$OLD_KS" ] || { echo "Ancien keystore introuvable : $OLD_KS" >&2; exit 1; }
[ -e "$NEW_KS" ] && { echo "$NEW_KS existe déjà : rien n'est écrasé." >&2; exit 1; }
mkdir -p "$OUT"

# apksigner : dernière version des build-tools du SDK. On lance le jar avec java plutôt
# que apksigner.bat : le .bat se perd quand le chemin du SDK contient un espace.
# sdk.dir de local.properties est échappé (C\:\\Users\\…) : on le remet en C:/Users/….
if [ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then
  SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
else
  SDK="$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | tr -s '\134' '/' | sed 's|^\([A-Za-z]\)/:|\1:|')"
fi
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
APKSIGNER_JAR="$BT/lib/apksigner.jar"
[ -f "$APKSIGNER_JAR" ] || { echo "apksigner introuvable dans $SDK/build-tools." >&2; exit 1; }
APKSIGNER() { java -jar "$APKSIGNER_JAR" "$@"; }

echo "1/3 — Nouvelle clé : choisis un mot de passe long (gestionnaire de mots de passe)."
keytool -genkeypair -v -keystore "$NEW_KS" -storetype PKCS12 -alias synxio \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Synxio Player, O=Synxio, C=FR"

echo "2/3 — Lignée ancienne clé → nouvelle clé (apksigner demande le mot de passe de chaque clé)."
# Droits par défaut de l'ancienne clé : tout sauf le retour arrière. Un APK signé avec
# l'ancienne clé seule ne pourra plus mettre à jour une appli déjà passée sur la lignée.
APKSIGNER rotate --out "$LINEAGE" \
  --old-signer --ks "$OLD_KS" --ks-key-alias synxio \
  --new-signer --ks "$NEW_KS" --ks-key-alias synxio
APKSIGNER lineage --in "$LINEAGE" --print-certs

echo "3/3 — Empreinte de la nouvelle clé (à reporter sans « : » dans PINNED_SIGNER_SHA256) :"
keytool -list -v -keystore "$NEW_KS" -alias synxio | grep -i "SHA256" || true

cat <<EOF

Fait. Fichiers dans $OUT :
  - $(basename "$NEW_KS")   ← à sauvegarder à DEUX endroits hors ligne, avec son mot de passe
  - $(basename "$LINEAGE")

Secrets GitHub à créer (gh demande les valeurs saisies à la main) :
  base64 -w0 "$NEW_KS"  | gh secret set KEYSTORE_BASE64
  base64 -w0 "$LINEAGE" | gh secret set LINEAGE_BASE64
  gh secret set KEYSTORE_PASSWORD
  gh secret set KEY_PASSWORD
  gh secret set KEY_ALIAS --body synxio

Ensuite, avant le tag v5.6.1 (voir docs/KEY_ROTATION.md, §0 et §6) :
  - reporter l'empreinte ci-dessus, sans « : », dans UpdateSignatures.PINNED_SIGNER_SHA256 ;
  - créer keystore.properties à la racine pour les builds release locaux ;
  - ranger l'ancien keystore avec la nouvelle clé, hors du dépôt.
EOF
