# Mixs du jour, Récap et Réveil en musique — conception

Date : 2026-10-25 · Branche : `upgrade-5.3` · Validé par l'utilisateur dans la conversation.

## 1. Mixs du jour

Section « Tes mixs du jour » sur l'accueil, au-dessus de « Pour toi ». Les playlists
intelligentes existantes (« Jamais écoutés », « Favoris délaissés »…) sont des listes
triées et exhaustives ; les mixs sont courts (25 titres), variés et renouvelés chaque jour.

- **Ton mix du jour** : ~60 % de titres familiers (favoris, joués 2 fois ou plus) et
  ~40 % de titres peu ou pas écoutés, mélangés.
- **Découverte du jour** : titres joués au plus une fois, un seul par album.
- **Mix <artiste>** : pour les 3 artistes principaux (avant « ft. », « feat. », « & »)
  les plus écoutés, artiste inconnu exclu :
  jusqu'à 10 de leurs titres, complétés par des titres du même genre.
- **Dans la lignée de <titre>** : titres au son proche du titre le plus joué des 7
  derniers jours, via `SimilarityRepository.similarTo`. Absent sans analyse du son.

Règles communes : graine aléatoire = numéro du jour (stable dans la journée), titres
« souvent zappés » écartés (`withoutDisliked`), 2 titres max par artiste, jamais deux
titres du même artiste d'affilée quand c'est évitable, mix vide ou trop court (< 8) retiré.

Unités : `DailyMixEngine` (pur, testé) ; `DailyMixRepository` (flux, similarité) ;
`AppViewModel.dailyMixes` ; carte dans `HomeScreen`.

## 2. Récap « Wrapped »

Bouton « Ton récap » dans Stats ; écran plein écran de cartes à faire défiler
(`HorizontalPager`), période « Ce mois-ci » ou « Cette année ».

Cartes : temps d'écoute total ; titre n°1 avec pochette ; top 5 artistes ; moment
préféré (jour de la semaine + tranche horaire) ; découvertes (titres écoutés pour la
première fois dans la période) ; carte finale de synthèse, partageable en image.

Source : le journal `play_history` (alimenté depuis le correctif du 2026-10-25). Sous
5 écoutes dans la période, un état vide explique que le récap se remplit en écoutant.

Unités : `RecapCalculator` (pur, testé) ; `RecapViewModel` ; `RecapScreen` ; rendu
d'image de carte réutilisant le mécanisme de `ShareCardRepository`.

## 3. Réveil en musique

Écran « Réveils » (Réglages > Lecture & Audio) : liste, ajout (cadran `TimePicker`), suppression, « Essayer », interrupteur
par réveil. Champs : heure, minute, jours (0 = une seule fois), source (favoris, mix du
jour, aléatoire, playlist), montée du volume en secondes (0–120).

- Table Room `alarms` (migration 13 → 14).
- `AlarmScheduler` : prochain déclenchement (`nextTrigger`, pur, testé) et
  `AlarmManager.setAlarmClock` ; permission `USE_EXACT_ALARM`.
- `AlarmReceiver` : lance la lecture via `PlayerConnection`, notification « Réveil »
  avec « Arrêter » et « Répéter dans 10 min », reprogramme les réveils récurrents.
- `BOOT_COMPLETED` / changement d'heure : reprogrammation.
- Lecture sur le flux alarme (`USAGE_ALARM`) le temps du réveil, montée par le gain du
  lecteur (`FadeController.alarmGain`) ; retour au flux musique à la première pause.
  Android 15+ ignore `setStreamVolume` d'une appli en arrière-plan (« AudioHardening ») :
  agir sur le volume système rendait le réveil muet si le volume musique était à 0.

Risque : démarrage de la lecture depuis l'arrière-plan sur Android 17 — vérifié sur
l'appareil, appli tuée.

## Tests

Unitaires : `DailyMixEngine`, `RecapCalculator`, `nextTrigger`. Puis build release,
`adb install -r`, essai réel des trois fonctions sur le téléphone de test.
