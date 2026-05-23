package ph.nextbank.drums.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ph.nextbank.drums.data.model.DrumToken
import ph.nextbank.drums.data.model.ImportSource
import ph.nextbank.drums.data.model.Song
import java.time.Instant

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val beatsPerBar: Int,
    val beatUnit: Int,
    val barsEncoded: String,
    val coverInitials: String,
    val importedFrom: ImportSource,
    val lastPlayedEpochMs: Long?,
    val youtubeVideoId: String?,
    val youtubeOffsetMs: Int,
    /** Comma-separated YouTube IDs. Empty string = no blocklist. */
    val youtubeBlocklist: String,
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        bpm = bpm,
        timeSig = beatsPerBar to beatUnit,
        bars = decodeBars(barsEncoded),
        coverInitials = coverInitials,
        importedFrom = importedFrom,
        lastPlayed = lastPlayedEpochMs?.let(Instant::ofEpochMilli),
        youtubeVideoId = youtubeVideoId,
        youtubeOffsetMs = youtubeOffsetMs,
        youtubeBlocklist = decodeBlocklist(youtubeBlocklist),
    )

    companion object {
        fun fromSong(s: Song): SongEntity = SongEntity(
            id = s.id,
            title = s.title,
            artist = s.artist,
            bpm = s.bpm,
            beatsPerBar = s.timeSig.first,
            beatUnit = s.timeSig.second,
            barsEncoded = encodeBars(s.bars),
            coverInitials = s.coverInitials,
            importedFrom = s.importedFrom,
            lastPlayedEpochMs = s.lastPlayed?.toEpochMilli(),
            youtubeVideoId = s.youtubeVideoId,
            youtubeOffsetMs = s.youtubeOffsetMs,
            youtubeBlocklist = encodeBlocklist(s.youtubeBlocklist),
        )

        internal fun encodeBars(bars: List<List<List<DrumToken>>>): String =
            bars.joinToString("|") { bar ->
                bar.joinToString(",") { slot -> slot.joinToString("+") { it.code } }
            }

        internal fun decodeBars(s: String): List<List<List<DrumToken>>> =
            s.split("|").map { bar ->
                bar.split(",").map { slot ->
                    if (slot.isEmpty()) emptyList()
                    else slot.split("+").map(DrumToken::fromCode)
                }
            }

        internal fun encodeBlocklist(ids: List<String>): String = ids.joinToString(",")
        internal fun decodeBlocklist(s: String): List<String> =
            if (s.isEmpty()) emptyList() else s.split(",")
    }
}
