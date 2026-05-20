package ph.nextbank.drums

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class LibrarySmokeTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun library_shows_your_kit_and_a_song_row() {
        composeRule.onNodeWithText("Your kit").assertIsDisplayed()
        composeRule.onNodeWithText("Smells Like Teen Spirit").assertIsDisplayed()
    }
}
