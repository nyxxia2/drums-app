package ph.nextbank.drums.audio.songsterr

import ph.nextbank.drums.data.model.DrumToken

/**
 * Maps MIDI percussion numbers (General MIDI standard) to our DrumToken enum.
 * Numbers not in this map are dropped during parsing — the parser logs them
 * at debug level so we know what we're losing.
 *
 * Source of truth: docs/superpowers/specs/2026-05-23-songsterr-tab-source-design.md
 */
object MidiPercussionMap {
    private val table: Map<Int, DrumToken> = mapOf(
        35 to DrumToken.KICK,         // Acoustic bass drum
        36 to DrumToken.KICK,         // Bass drum 1
        38 to DrumToken.SNARE,        // Acoustic snare
        40 to DrumToken.SNARE,        // Electric snare
        42 to DrumToken.HIHAT_CLOSED, // Closed hi-hat
        44 to DrumToken.HIHAT_CLOSED, // Pedal hi-hat
        46 to DrumToken.HIHAT_OPEN,   // Open hi-hat
        49 to DrumToken.CRASH,        // Crash 1
        57 to DrumToken.CRASH,        // Crash 2
        51 to DrumToken.RIDE,         // Ride 1
        53 to DrumToken.RIDE,         // Ride bell
        59 to DrumToken.RIDE,         // Ride 2
        48 to DrumToken.TOM_HI,       // Hi mid tom
        50 to DrumToken.TOM_HI,       // High tom
        45 to DrumToken.TOM_MID,      // Low tom
        47 to DrumToken.TOM_MID,      // Low-mid tom
        41 to DrumToken.TOM_FLOOR,    // Low floor tom
        43 to DrumToken.TOM_FLOOR,    // High floor tom
    )

    fun toToken(midi: Int): DrumToken? = table[midi]
}
