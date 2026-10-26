#!/usr/bin/env bash
# Rotation de la clé de signature release (lignée APK Signature Scheme v3).
# Voir docs/KEY_ROTATION.md. À lancer UNE fois, depuis la racine du dépôt, sur une machine
# de confiance (Git Bash sous Windows convient). Les mots de passe sont demandés par
# keytool / apksigner : ils ne sont ni écrits dans le dépôt, ni affichés.
set -euo pipefail

OUT="${1:-$HOME/synxio-keys}"
NEW_KS="$OUT/synxio-release-2.jks"
LINEAGE="$OUT/synxio.lineage"
OLD_KS="app/synxio-release.keystore"

[ -f "$OLD_KS" ] || { echo "Lance ce script depuis la racine du dépôt ($OLD_KS introuvable)." >&2; exit 1; }
[ -e "$NEW_KS" ] && { echo "$NEW_KS existe déjà : rien n'est écrasé." >&2; exit 1; }
mkdir -p "$OUT"

# apksigner : dernière version des build-tools du SDK (apksigner.bat sous Windows).
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | sed 's/\\\\/\//g; s/\\:/:/')}}"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
APKSIGNER="$BT/apksigner"; [ -x "$APKSIGNER" ] || APKSIGNER="$BT/apksigner.bat"
[ -e "$APKSIGNER" ] || { echo "apksigner introuvable dans $SDK/build-tools." >&2; exit 1; }

echo "1/3 — Nouvelle clé : choisis un mot de passe long (gestionnaire de mots de passe)."
keytool -genkeypair -v -keystore "$NEW_KS" -storetype PKCS12 -alias synxio \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Synxio Player, O=Synxio, C=FR"

echo "2/3 — Lignée ancienne clé → nouvelle clé (ressaisis le mot de passe de la nouvelle clé)."
# Droits par défaut de l'ancienne clé : tout sauf le retour arrière. Un APK signé avec
# l'ancienne clé seule ne pourra plus mettre à jour une appli déjà passée sur la lignée.
"$APKSIGNER" rotate --out "$LINEAGE" \
  --old-signer --ks "$OLD_KS" --ks-key-alias synxio --ks-pass pass:synxioplayer \
  --new-signer --ks "$NEW_KS" --ks-key-alias synxio
"$APKSIGNER" lineage --in "$LINEAGE" --print-certs

echo "3/3 — Empreinte de la nouvelle clé :"
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

À faire seulement APRÈS la publication de la 5.5.0 (voir docs/KEY_ROTATION.md, §0).
EOF
