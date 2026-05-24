package ph.nextbank.drums.audio.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the scoring helpers. The actual NewPipe-backed search isn't tested here;
 * those are covered by manual smoke tests on a device.
 */
class NewPipeYouTubeSearchServiceTest {

    @Test fun `karaoke uploads get heavily penalized`() {
        val score = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Hell is Forever — KARAOKE Version",
            channel = "KaraTube",
        )
        assertEquals(300, score)
    }

    @Test fun `drumless tracks get heavily penalized`() {
        val score = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Smells Like Teen Spirit (drumless backing track)",
            channel = "DrumBackingTracks",
        )
        // drumless (300) + backing track (200) = 500
        assertEquals(500, score)
    }

    @Test fun `drum-minus channels and titles are detected`() {
        // Channel "Drum Minus Tracks" + ordinary title.
        val byChannel = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Contradanza Vanessa-Mae",
            channel = "Drum Minus Tracks",
        )
        assertEquals(300, byChannel)

        // Title "(drums minus" — also drumless.
        val byTitle = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Contradanza (drums minus)",
            channel = "Random",
        )
        assertEquals(300, byTitle)

        // "no drums" variant.
        val noDrums = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Some Song - No Drums",
            channel = "Backing",
        )
        assertEquals(300, noDrums)
    }

    @Test fun `drumstick is not detected as drumless`() {
        // "drum" appears in "drumstick" but it's a word boundary check; "minus" not present.
        val score = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Drumstick Reviews Episode 12",
            channel = "GearChannel",
        )
        assertEquals(0, score)
    }

    @Test fun `instrumental gets penalized`() {
        val score = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Song Title - Instrumental",
            channel = "Music",
        )
        assertEquals(200, score)
    }

    @Test fun `cover gets penalized`() {
        val score = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Hell is Forever Cover by Jane Doe",
            channel = "JaneDoeCovers",
        )
        assertEquals(150, score)
    }

    @Test fun `lyric video gets small penalty`() {
        val score = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Hell is Forever (Lyric Video)",
            channel = "OfficialHazbinHotel",
        )
        // 50 for the lyric-video pattern; the conditions are `||`-joined so they add once.
        assertEquals(50, score)
    }

    @Test fun `clean title with no bad keywords scores zero on content`() {
        val score = NewPipeYouTubeSearchService.contentMismatchScore(
            title = "Hell is Forever - From Hazbin Hotel",
            channel = "PrimeVideoMusicals",
        )
        assertEquals(0, score)
    }

    @Test fun `cover regex does not match incidental letters`() {
        // "covered in glory" should not penalize, but `cover` as a word should.
        assertEquals(0, NewPipeYouTubeSearchService.contentMismatchScore("Discoveries", "Channel"))
        assertEquals(0, NewPipeYouTubeSearchService.contentMismatchScore("Recovery", "Channel"))
        assertEquals(150, NewPipeYouTubeSearchService.contentMismatchScore("Song Title - Cover", "Channel"))
    }

    @Test fun `embed restriction unchanged by refactor`() {
        assertEquals(100, NewPipeYouTubeSearchService.embedRestrictionScore("ArtistVEVO"))
        assertEquals(100, NewPipeYouTubeSearchService.embedRestrictionScore("Artist - Topic"))
        assertEquals(80, NewPipeYouTubeSearchService.embedRestrictionScore("Hazbin Hotel Official"))
        assertEquals(60, NewPipeYouTubeSearchService.embedRestrictionScore("Atlantic Records"))
        assertEquals(0, NewPipeYouTubeSearchService.embedRestrictionScore("Random User"))
    }

    @Test fun `combined score makes VEVO beat karaoke`() {
        val vevo = NewPipeYouTubeSearchService.rankScore(
            title = "Smells Like Teen Spirit (Official Music Video)",
            channel = "NirvanaVEVO",
        )
        val karaoke = NewPipeYouTubeSearchService.rankScore(
            title = "Smells Like Teen Spirit - Karaoke Version",
            channel = "KaraokeChannel",
        )
        assertTrue("VEVO ($vevo) should beat karaoke ($karaoke)", vevo < karaoke)
    }

    @Test fun `combined score makes user upload of original beat VEVO`() {
        val userUpload = NewPipeYouTubeSearchService.rankScore(
            title = "Smells Like Teen Spirit (HD)",
            channel = "RandomUploader",
        )
        val vevo = NewPipeYouTubeSearchService.rankScore(
            title = "Smells Like Teen Spirit (Official Music Video)",
            channel = "NirvanaVEVO",
        )
        // The clean user upload (0) beats VEVO (100) so we still prefer playable mirrors first.
        assertTrue("user upload ($userUpload) should beat VEVO ($vevo)", userUpload < vevo)
    }
}
