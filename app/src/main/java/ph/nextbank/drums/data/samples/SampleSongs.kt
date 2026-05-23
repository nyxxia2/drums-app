package ph.nextbank.drums.data.samples

import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.DrumToken.*
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song

// ─── primitive helpers ─────────────────────────────────────────
private val e: List<DrumToken> = emptyList()
private fun hit(vararg t: DrumToken): List<DrumToken> = t.toList()

// ─── canonical bars (from drum-staff.jsx) ──────────────────────
private val GROOVE_A = listOf(
    hit(KICK, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
    hit(SNARE, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
    hit(KICK, HIHAT_CLOSED), e, hit(KICK, HIHAT_CLOSED), e,
    hit(SNARE, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
)

private val GROOVE_B = listOf(
    hit(KICK, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
    hit(SNARE, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
    hit(KICK, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
    hit(SNARE, HIHAT_CLOSED), e, hit(SNARE, HIHAT_CLOSED), e,
)

private val FILL = listOf(
    hit(KICK, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
    hit(SNARE, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
    hit(SNARE), hit(SNARE), hit(TOM_HI), hit(TOM_HI),
    hit(TOM_MID), hit(TOM_MID), hit(TOM_FLOOR), hit(TOM_FLOOR),
)

private val BAR_CRASH = listOf(
    hit(KICK, CRASH), e, hit(HIHAT_CLOSED), e,
    hit(SNARE, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
    hit(KICK, HIHAT_CLOSED), e, hit(KICK, HIHAT_CLOSED), e,
    hit(SNARE, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
)

// Slower, ride-heavy groove for Tom Sawyer feel
private val RIDE_GROOVE = listOf(
    hit(KICK, RIDE), e, hit(RIDE), e,
    hit(SNARE, RIDE), e, hit(RIDE), e,
    hit(KICK, RIDE), e, hit(KICK, RIDE), e,
    hit(SNARE, RIDE), e, hit(RIDE), e,
)

// Half-time feel for In the Air Tonight
private val HALF_TIME = listOf(
    hit(KICK, HIHAT_CLOSED), e, e, e,
    e, e, hit(HIHAT_CLOSED), e,
    e, e, hit(SNARE), e,
    e, e, hit(HIHAT_CLOSED), e,
)

// Eighth-note open hihat groove for Rosanna feel
private val ROSANNA_FEEL = listOf(
    hit(KICK, HIHAT_CLOSED), e, hit(HIHAT_OPEN), e,
    hit(SNARE, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
    hit(HIHAT_CLOSED), e, hit(KICK, HIHAT_OPEN), e,
    hit(SNARE, HIHAT_CLOSED), e, hit(HIHAT_CLOSED), e,
)

// ─── songs ────────────────────────────────────────────────────
// YouTube IDs are best-effort defaults. If a video has been taken down or the
// match is wrong, the YouTubePlayer will show an error overlay — just edit the
// ID here (the 11-char string after `v=` in any YouTube URL) and rebuild.
val SAMPLE_SONGS: List<Song> = listOf(
    Song(
        id = "smells-like-teen-spirit",
        title = "Smells Like Teen Spirit",
        artist = "Nirvana",
        bpm = 116,
        timeSig = 4 to 4,
        bars = listOf(BAR_CRASH, GROOVE_A, GROOVE_B, FILL, BAR_CRASH, GROOVE_A, GROOVE_B, FILL),
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
        bars = listOf(BAR_CRASH, RIDE_GROOVE, RIDE_GROOVE, FILL, BAR_CRASH, RIDE_GROOVE, RIDE_GROOVE, FILL),
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
        bars = listOf(BAR_CRASH, ROSANNA_FEEL, ROSANNA_FEEL, FILL, BAR_CRASH, ROSANNA_FEEL, ROSANNA_FEEL, FILL),
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
        bars = listOf(HALF_TIME, HALF_TIME, HALF_TIME, HALF_TIME, HALF_TIME, FILL, BAR_CRASH, HALF_TIME),
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
        bars = listOf(BAR_CRASH, GROOVE_A, FILL, GROOVE_B, BAR_CRASH, GROOVE_A, FILL, GROOVE_B),
        coverInitials = "RU",
        importedFrom = ImportSource.BUNDLED,
        lastPlayed = null,
    ),
)
