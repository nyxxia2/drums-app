package ph.nextbank.drums.audio.songsterr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.nextbank.drums.data.model.DrumToken
import java.io.File

class DrumTabParserTest {

    private val parser: DrumTabParser = DefaultDrumTabParser()

    private fun rev(root: JsonObject) =
        RevisionJson(songId = 1, revisionId = 1, drumTrackHash = "drums_test", root = root)

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(File("src/test/resources/songsterr/$name").readText())
            .let { it as JsonObject }

    /** Build a one-beat measure where the single beat has [duration] and the given notes. */
    private fun buildMinimalRoot(
        bpm: Int = 95,
        timeSig: Pair<Int, Int> = 4 to 4,
        beats: List<Pair<Pair<Int, Int>, List<Int>>>, // pair: duration -> midi list (empty list means rest)
    ): JsonObject = buildJsonObject {
        putJsonObject("automations") {
            putJsonArray("tempo") {
                addJsonObject {
                    put("measure", 0); put("position", 0); put("bpm", bpm); put("type", 4)
                }
            }
        }
        putJsonArray("measures") {
            addJsonObject {
                putJsonArray("signature") { add(timeSig.first); add(timeSig.second) }
                putJsonArray("voices") {
                    addJsonObject {
                        putJsonArray("beats") {
                            for ((dur, midis) in beats) {
                                addJsonObject {
                                    putJsonArray("duration") { add(dur.first); add(dur.second) }
                                    if (midis.isEmpty()) {
                                        put("rest", true)
                                        putJsonArray("notes") {
                                            addJsonObject { put("rest", true) }
                                        }
                                    } else {
                                        putJsonArray("notes") {
                                            for (midi in midis) {
                                                addJsonObject { put("fret", midi); put("string", 0) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun `IATA real fixture parses without error and returns at least 1 bar`() {
        val result = parser.parse(
            RevisionJson(
                songId = 50420,
                revisionId = 5548296,
                drumTrackHash = "drums_OX64y6oG",
                root = fixture("revision-sample.json"),
            ),
        )
        assertTrue("expected Success, got $result", result is ParseResult.Success)
        val s = result as ParseResult.Success
        assertTrue("bars should be non-empty", s.bars.isNotEmpty())
        assertTrue("bpm should be > 0", s.bpm > 0)
        assertEquals(95, s.bpm)
        assertEquals(4 to 4, s.timeSig)
        assertEquals(16, s.slotsPerBar)
        assertEquals("first bar slot count matches slotsPerBar", s.slotsPerBar, s.bars[0].size)
    }

    @Test fun `kick on beat 1 of a 4-4 bar of 16ths lands on slot 0`() {
        // 16 sixteenth-notes: first has a kick (MIDI 36), rest are rests
        val beats = (0 until 16).map { i ->
            val midis = if (i == 0) listOf(36) else emptyList<Int>()
            (1 to 16) to midis
        }
        val result = parser.parse(rev(buildMinimalRoot(beats = beats)))
        assertTrue("expected Success, got $result", result is ParseResult.Success)
        val s = result as ParseResult.Success
        assertEquals(1, s.bars.size)
        assertEquals(16, s.bars[0].size)
        assertEquals(listOf(DrumToken.KICK), s.bars[0][0])
        // No other slots should have notes
        for (i in 1 until 16) assertTrue("slot $i should be empty", s.bars[0][i].isEmpty())
    }

    @Test fun `snare on beat 3 of a 4-4 bar lands on slot 8`() {
        // 4 quarter-notes, snare on the 3rd
        val beats = listOf(
            (1 to 4) to emptyList(),
            (1 to 4) to emptyList(),
            (1 to 4) to listOf(38),
            (1 to 4) to emptyList(),
        )
        val result = parser.parse(rev(buildMinimalRoot(beats = beats)))
        assertTrue(result is ParseResult.Success)
        val s = result as ParseResult.Success
        assertEquals(listOf(DrumToken.SNARE), s.bars[0][8])
    }

    @Test fun `unmapped MIDI percussion numbers are dropped without crashing`() {
        // MIDI 56 (cowbell — not in our map) + MIDI 36 (kick) on slot 0
        val beats = (0 until 16).map { i ->
            val midis = if (i == 0) listOf(56, 36) else emptyList<Int>()
            (1 to 16) to midis
        }
        val result = parser.parse(rev(buildMinimalRoot(beats = beats)))
        assertTrue(result is ParseResult.Success)
        val s = result as ParseResult.Success
        assertEquals(listOf(DrumToken.KICK), s.bars[0][0])
    }

    @Test fun `triplet-feel input produces slotsPerBar=24`() {
        // 12 triplet-8th notes (duration 1/12) in a 4/4 bar
        val beats = (0 until 12).map { i ->
            val midis = if (i == 0) listOf(36) else emptyList<Int>()
            (1 to 12) to midis
        }
        val result = parser.parse(rev(buildMinimalRoot(beats = beats)))
        assertTrue(result is ParseResult.Success)
        val s = result as ParseResult.Success
        assertEquals(24, s.slotsPerBar)
        assertEquals(24, s.bars[0].size)
        assertEquals(listOf(DrumToken.KICK), s.bars[0][0])
    }

    @Test fun `bar with only rests is preserved as empty-slot bar`() {
        val beats = (0 until 16).map { (1 to 16) to emptyList<Int>() }
        val result = parser.parse(rev(buildMinimalRoot(beats = beats)))
        // Single bar, all rests, ALL_EMPTY_BARS triggers NoDrumTrack
        assertEquals(ParseResult.NoDrumTrack, result)
    }

    @Test fun `empty measures array returns NoDrumTrack`() {
        val root = buildJsonObject {
            putJsonObject("automations") {
                putJsonArray("tempo") {
                    addJsonObject { put("bpm", 120) }
                }
            }
            putJsonArray("measures") { /* empty */ }
        }
        assertEquals(ParseResult.NoDrumTrack, parser.parse(rev(root)))
    }

    @Test fun `missing automations tempo returns ParseError`() {
        val root = buildJsonObject {
            putJsonArray("measures") {
                addJsonObject {
                    putJsonArray("signature") { add(4); add(4) }
                    putJsonArray("voices") { addJsonObject { putJsonArray("beats") { } } }
                }
            }
        }
        val result = parser.parse(rev(root))
        assertTrue("expected ParseError, got $result", result is ParseResult.ParseError)
    }
}
