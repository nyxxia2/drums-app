package ph.nextbank.drums.data.samples

import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.DrumToken.*
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song

// ─── primitive helpers ─────────────────────────────────────────
private val __: List<DrumToken> = emptyList()
private fun hit(vararg t: DrumToken): List<DrumToken> = t.toList()

// ─── canonical bars (from drum-staff.jsx) ──────────────────────
private val GROOVE_A = listOf(
    hit(KICK, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
    hit(SNARE, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
    hit(KICK, HIHAT_CLOSED), __, hit(KICK, HIHAT_CLOSED), __,
    hit(SNARE, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
)

private val GROOVE_B = listOf(
    hit(KICK, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
    hit(SNARE, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
    hit(KICK, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
    hit(SNARE, HIHAT_CLOSED), __, hit(SNARE, HIHAT_CLOSED), __,
)

private val FILL = listOf(
    hit(KICK, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
    hit(SNARE, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
    hit(SNARE), hit(SNARE), hit(TOM_HI), hit(TOM_HI),
    hit(TOM_MID), hit(TOM_MID), hit(TOM_FLOOR), hit(TOM_FLOOR),
)

private val CRASH = listOf(
    hit(KICK, CRASH), __, hit(HIHAT_CLOSED), __,
    hit(SNARE, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
    hit(KICK, HIHAT_CLOSED), __, hit(KICK, HIHAT_CLOSED), __,
    hit(SNARE, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
)

// Slower, ride-heavy groove for Tom Sawyer feel
private val RIDE_GROOVE = listOf(
    hit(KICK, RIDE), __, hit(RIDE), __,
    hit(SNARE, RIDE), __, hit(RIDE), __,
    hit(KICK, RIDE), __, hit(KICK, RIDE), __,
    hit(SNARE, RIDE), __, hit(RIDE), __,
)

// Half-time feel for In the Air Tonight
private val HALF_TIME = listOf(
    hit(KICK, HIHAT_CLOSED), __, __, __,
    __, __, hit(HIHAT_CLOSED), __,
    __, __, hit(SNARE), __,
    __, __, hit(HIHAT_CLOSED), __,
)

// Eighth-note open hihat groove for Rosanna feel
private val ROSANNA_FEEL = listOf(
    hit(KICK, HIHAT_CLOSED), __, hit(HIHAT_OPEN), __,
    hit(SNARE, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
    hit(HIHAT_CLOSED), __, hit(KICK, HIHAT_OPEN), __,
    hit(SNARE, HIHAT_CLOSED), __, hit(HIHAT_CLOSED), __,
)

// ─── songs ────────────────────────────────────────────────────
val SAMPLE_SONGS: List<Song> = listOf(
    Song(
        id = "smells-like-teen-spirit",
        title = "Smells Like Teen Spirit",
        artist = "Nirvana",
        bpm = 116,
        timeSig = 4 to 4,
        bars = listOf(CRASH, GROOVE_A, GROOVE_B, FILL, CRASH, GROOVE_A, GROOVE_B, FILL),
        coverInitials = "NV",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "tom-sawyer",
        title = "Tom Sawyer",
        artist = "Rush",
        bpm = 88,
        timeSig = 4 to 4,
        bars = listOf(CRASH, RIDE_GROOVE, RIDE_GROOVE, FILL, CRASH, RIDE_GROOVE, RIDE_GROOVE, FILL),
        coverInitials = "RU",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "rosanna",
        title = "Rosanna",
        artist = "Toto",
        bpm = 86,
        timeSig = 4 to 4,
        bars = listOf(CRASH, ROSANNA_FEEL, ROSANNA_FEEL, FILL, CRASH, ROSANNA_FEEL, ROSANNA_FEEL, FILL),
        coverInitials = "TO",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "in-the-air-tonight",
        title = "In the Air Tonight",
        artist = "Phil Collins",
        bpm = 95,
        timeSig = 4 to 4,
        bars = listOf(HALF_TIME, HALF_TIME, HALF_TIME, HALF_TIME, HALF_TIME, FILL, CRASH, HALF_TIME),
        coverInitials = "PC",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
    Song(
        id = "yyz",
        title = "YYZ",
        artist = "Rush",
        bpm = 144,
        timeSig = 4 to 4,
        bars = listOf(CRASH, GROOVE_A, FILL, GROOVE_B, CRASH, GROOVE_A, FILL, GROOVE_B),
        coverInitials = "RU",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
)
