package ph.nextbank.drums.data.model

/**
 * Drum-staff tokens. The string code matches the design-reference (drum-staff.jsx)
 * so the canonical patterns transfer 1:1.
 */
enum class DrumToken(val code: String) {
    KICK("k"),
    SNARE("s"),
    HIHAT_CLOSED("h"),
    HIHAT_OPEN("o"),
    CRASH("c"),
    RIDE("r"),
    TOM_HI("t1"),
    TOM_MID("t2"),
    TOM_FLOOR("t3");

    companion object {
        private val byCode = values().associateBy { it.code }
        fun fromCode(code: String): DrumToken =
            byCode[code] ?: error("Unknown drum token: $code")
    }
}
