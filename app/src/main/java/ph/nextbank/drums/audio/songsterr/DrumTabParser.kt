package ph.nextbank.drums.audio.songsterr

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ph.nextbank.drums.data.model.DrumToken

private const val TAG = "DrumsSgs"

sealed interface ParseResult {
    data class Success(
        val bpm: Int,
        val timeSig: Pair<Int, Int>,
        val slotsPerBar: Int,
        /** Outer = bars; middle = slots; inner = drum tokens hit at that slot. */
        val bars: List<List<List<DrumToken>>>,
        val warnings: List<String>,
    ) : ParseResult

    data object NoDrumTrack : ParseResult
    data class ParseError(val reason: String) : ParseResult
}

interface DrumTabParser {
    fun parse(revision: RevisionJson): ParseResult
}

class DefaultDrumTabParser : DrumTabParser {

    override fun parse(revision: RevisionJson): ParseResult {
        val root = revision.root
        val warnings = mutableListOf<String>()

        // BPM from automations.tempo[0].bpm
        val bpm = root["automations"]?.jsonObject
            ?.get("tempo")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("bpm")?.jsonPrimitive?.intOrNull
            ?: return ParseResult.ParseError("no automations.tempo[0].bpm in revision JSON")

        val measures = root["measures"]?.jsonArray
            ?: return ParseResult.ParseError("no 'measures' array at root")
        if (measures.isEmpty()) return ParseResult.NoDrumTrack

        val firstMeter = readMeter(measures[0].jsonObject)
            ?: return ParseResult.ParseError("no signature on measures[0]")

        val slotsPerBar = if (hasTripletFeel(measures)) 24 else 16

        val bars = mutableListOf<List<List<DrumToken>>>()
        var sawMeterChange = false
        for ((i, measureEl) in measures.withIndex()) {
            val measure = measureEl.jsonObject
            val barMeter = readMeter(measure)
            if (barMeter != null && barMeter != firstMeter && !sawMeterChange) {
                warnings += "bar ${i + 1}: meter changes to ${barMeter.first}/${barMeter.second} — using first meter"
                sawMeterChange = true
            }
            bars += buildSlotsForBar(measure, slotsPerBar, firstMeter)
        }

        if (bars.all { it.all(List<DrumToken>::isEmpty) }) return ParseResult.NoDrumTrack

        return ParseResult.Success(
            bpm = bpm,
            timeSig = firstMeter,
            slotsPerBar = slotsPerBar,
            bars = bars,
            warnings = warnings,
        )
    }

    /** Read `signature: [num, denom]` from a measure object. Returns null if missing/malformed. */
    private fun readMeter(measure: JsonObject): Pair<Int, Int>? {
        val sig = measure["signature"]?.jsonArray ?: return null
        if (sig.size < 2) return null
        val num = sig[0].jsonPrimitive.intOrNull ?: return null
        val denom = sig[1].jsonPrimitive.intOrNull ?: return null
        return num to denom
    }

    /**
     * Detect triplet feel by scanning beat durations in the first 8 measures.
     * Songsterr encodes triplets as non-power-of-2 denominators (e.g. [1, 12]
     * for triplet eighths, [1, 24] for triplet 16ths).
     */
    private fun hasTripletFeel(measures: JsonArray): Boolean {
        for (mEl in measures.take(8)) {
            val voices = mEl.jsonObject["voices"]?.jsonArray ?: continue
            for (vEl in voices) {
                val beats = vEl.jsonObject["beats"]?.jsonArray ?: continue
                for (bEl in beats) {
                    val dur = bEl.jsonObject["duration"]?.jsonArray ?: continue
                    if (dur.size < 2) continue
                    val denom = dur[1].jsonPrimitive.intOrNull ?: continue
                    // Non-power-of-2 → triplet/tuplet
                    if (denom > 0 && (denom and (denom - 1)) != 0) return true
                }
            }
        }
        return false
    }

    /**
     * Walk the beats in voice 0, computing each beat's slot index from cumulative
     * duration. Each beat with notes contributes its drum tokens at that slot.
     * Rests contribute nothing but still advance the position.
     */
    private fun buildSlotsForBar(
        measure: JsonObject,
        slotsPerBar: Int,
        timeSig: Pair<Int, Int>,
    ): List<List<DrumToken>> {
        val slots = MutableList(slotsPerBar) { mutableListOf<DrumToken>() }
        val voices = measure["voices"]?.jsonArray ?: return slots
        val firstVoice = voices.firstOrNull()?.jsonObject ?: return slots
        val beats = firstVoice["beats"]?.jsonArray ?: return slots

        // Bar holds (numerator / denominator) whole notes worth of beats.
        // Slot index = position * slotsPerBar / barWholeNotes.
        // For 4/4 a bar holds 1 whole note (4/4 = 1.0), so slot = pos * slotsPerBar.
        val barWholeNotes = timeSig.first.toDouble() / timeSig.second.toDouble()
        var position = 0.0

        for (bEl in beats) {
            val beat = bEl.jsonObject
            val dur = beat["duration"]?.jsonArray
            val durFrac = if (dur != null && dur.size >= 2) {
                val num = dur[0].jsonPrimitive.intOrNull ?: 0
                val denom = dur[1].jsonPrimitive.intOrNull ?: 1
                if (denom == 0) 0.0 else num.toDouble() / denom.toDouble()
            } else 0.0

            val isRest = beat["rest"]?.jsonPrimitive?.booleanOrNull == true
            if (!isRest) {
                val slotIdx = ((position / barWholeNotes) * slotsPerBar).toInt().coerceIn(0, slotsPerBar - 1)
                val notes = beat["notes"]?.jsonArray
                if (notes != null) {
                    for (nEl in notes) {
                        val note = nEl.jsonObject
                        if (note["rest"]?.jsonPrimitive?.booleanOrNull == true) continue
                        val midi = note["fret"]?.jsonPrimitive?.intOrNull ?: continue
                        val token = MidiPercussionMap.toToken(midi)
                        if (token == null) {
                            Log.d(TAG, "dropping unmapped MIDI percussion $midi")
                            continue
                        }
                        if (token !in slots[slotIdx]) slots[slotIdx].add(token)
                    }
                }
            }
            position += durFrac
        }
        return slots
    }
}
