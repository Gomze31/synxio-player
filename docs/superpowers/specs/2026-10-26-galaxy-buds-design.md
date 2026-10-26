# Galaxy Buds3 Pro / Buds FE — adaptation (5.4.0)

## Objectif

Retirer la commande vocale mains libres (Vosk) et la remplacer par une adaptation
complète aux Galaxy Buds3 Pro et Buds FE, limitée à ce qu'Android expose publiquement.

## Hors de portée (protocole Samsung propriétaire, tenu par Galaxy Wearable)

ANC / mode ambiant, détection de port, batterie gauche / droite / boîtier, appui long.
Un raccourci ouvre Galaxy Wearable pour ces réglages.

## Retrait du mode vocal

- Supprimer `voice/` (VoiceGrammar, VoiceWakeController, VoiceWakeService, VoskModelStore),
  la dépendance `vosk-android`, le service et `FOREGROUND_SERVICE_MICROPHONE` du manifeste,
  la préférence `voice_wake_enabled`, la section « Commande vocale ».
- Garder `RECORD_AUDIO` (visualiseur) et la recherche de l'Assistant (`onPlayFromSearch`).

## Composants (`fr.synxio.player.buds`)

| Unité | Rôle | Testée |
|---|---|---|
| `GalaxyBuds` | Reconnaît le modèle par nom (Buds3 Pro, Buds FE, autres Galaxy Buds), nom du codec | oui |
| `BudsTapAction` + `BudsGestures` | Remappe NEXT (double toucher) / PREVIOUS (triple toucher) quand la sortie est un Galaxy Buds | oui |
| `BudsAlerts` | Décide l'alerte batterie faible (seuils 15 % et 5 %, une fois par seuil) | oui |
| `BudsConnectAction` | Connexion : reprendre la file, lancer une source, ou rien | oui |
| `BudsMonitor` | `StateFlow<BudsState?>` : modèle, nom, batterie, codec | non (Android) |
| `SpatialAudio` | État du Spatializer (dispo, actif, suivi de tête) | non (Android) |

## Données

- Batterie : diffusion `android.bluetooth.device.action.BATTERY_LEVEL_CHANGED`, valeur
  initiale par `BluetoothDevice.getBatteryLevel` (réflexion, au mieux).
- Codec : diffusion `android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED`,
  `BluetoothCodecStatus.EXTRA_CODEC_STATUS` (public).
- Les deux demandent `BLUETOOTH_CONNECT`, demandée depuis la carte Galaxy Buds. Sans elle :
  modèle seulement.

## Réglages (DataStore)

- `buds_auto_eq` (vrai) : sans profil d'appareil mémorisé, applique la courbe du modèle.
- `buds_double_tap`, `buds_triple_tap` (`DEFAULT`) : action du toucher.
- `buds_connect_source` (vide) : `DAILY_MIX`, `FAVORITES`, `SHUFFLE` lancés à la connexion
  quand rien ne joue et que la reprise n'a pas lieu.
- `buds_battery_alert` (vrai), `spatial_audio` (vrai : `SPATIALIZATION_BEHAVIOR_AUTO`).

## Égaliseur

Courbes `galaxy_buds3_pro` et `galaxy_buds_fe` dans `EqCurves` (catégorie HEADPHONES),
estimées d'après les mesures publiées, modifiables comme les autres.

## Interface

- Paramètres › Lecture & audio : groupe « Galaxy Buds » à la place de « Commande vocale ».
- Lecteur : la pastille de sortie affiche le modèle et la batterie.

## Tests

Tests unitaires JVM des unités pures ; build debug ; vérification sur l'appareil.
