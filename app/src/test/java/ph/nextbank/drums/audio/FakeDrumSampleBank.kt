package ph.nextbank.drums.audio

import ph.nextbank.drums.data.model.DrumToken

class FakeDrumSampleBank : DrumSampleBankApi {
    val plays = mutableListOf<DrumToken>()
    override fun play(token: DrumToken) { plays.add(token) }
}
