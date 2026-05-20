package ph.nextbank.drums.ui.nav

sealed class Route(val path: String) {
    data object Library : Route("library")
    data object Upload : Route("upload")
    data class Player(val songId: String) : Route("player/$songId") {
        companion object { const val PATH = "player/{songId}" }
    }
    data class SongDetail(val songId: String) : Route("song/$songId") {
        companion object { const val PATH = "song/{songId}" }
    }
    data class Practice(val songId: String) : Route("practice/$songId") {
        companion object { const val PATH = "practice/{songId}" }
    }
}
