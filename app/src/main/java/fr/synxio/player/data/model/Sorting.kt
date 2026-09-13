package fr.synxio.player.data.model

/** Critères de tri disponibles pour la liste des titres. */
enum class SongSort(val label: String) {
    TITLE("Titre"),
    ARTIST("Artiste"),
    ALBUM("Album"),
    DURATION("Durée"),
    DATE_ADDED("Date d'ajout"),
    DATE_MODIFIED("Date de modification"),
    YEAR("Année"),
    TRACK("N° de piste"),
    SIZE("Taille"),
    PLAY_COUNT("Nombre d'écoutes");
}

enum class AlbumSort(val label: String) {
    TITLE("Titre"),
    ARTIST("Artiste"),
    YEAR("Année"),
    SONG_COUNT("Nombre de titres"),
    DATE_ADDED("Date d'ajout");
}

enum class ArtistSort(val label: String) {
    NAME("Nom"),
    ALBUM_COUNT("Nombre d'albums"),
    SONG_COUNT("Nombre de titres");
}

/** Comment la file d'attente réagit en fin de morceau / fin de file. */
enum class RepeatMode { OFF, ALL, ONE }

/** Rendu du plein écran « Lecture en cours ». */
enum class NowPlayingSkin(val label: String) {
    IMMERSIVE("Immersif"),
    VINYL("Vinyle"),
    CARD("Carte"),
    MINIMAL("Minimal");
}

enum class ThemeMode(val label: String) {
    SYSTEM("Système"),
    LIGHT("Clair"),
    DARK("Sombre"),
    AMOLED("AMOLED (noir pur)");
}

enum class AccentSource(val label: String) {
    ARTWORK("Couleurs de la pochette"),
    WALLPAPER("Fond d'écran (Material You)"),
    SYNXIO("Violet Synxio"),
    CUSTOM("Personnalisé");
}

enum class LibraryTab(val label: String) {
    SONGS("Titres"),
    ALBUMS("Albums"),
    ARTISTS("Artistes"),
    GENRES("Genres"),
    FOLDERS("Dossiers"),
    PODCASTS("Podcasts"),
}
